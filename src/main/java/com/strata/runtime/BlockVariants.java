package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.BlockVariant;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** BLOCK VARIANTS ARE REAL BLOCKS. */
public final class BlockVariants {

    private BlockVariants() {
    }

    /** One registered variant. */
    public record Def(String name, Block block, Block base, Item item) {
    }

    /** The variants this game registered, by name. */
    private static final Map<String, Def> BY_NAME = new LinkedHashMap<>();
    /** The same, by the variant's block (the mixins and the drops ask by block). */
    private static final Map<Block, Def> BY_BLOCK = new IdentityHashMap<>();
    /** The rows read at construction: name to base block id. */
    private static final Map<String, String> PLANNED = new LinkedHashMap<>();
    /** The display names read at construction: name to the name its item shows. */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    /** Reads block_variants.csv raw, at mod construction (before Config). */
    public static void scan() {
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockVariants.FILE))) {
            String name = row.getOrDefault(Schema.BlockVariants.NAME, "").strip();
            String base = row.getOrDefault(Schema.BlockVariants.BASE_BLOCK, "").strip();
            if (!name.isEmpty() && !base.isEmpty() && BlockVariant.nameProblem(name, PLANNED.keySet()) == null) {
                PLANNED.put(name, base);
            }
        }
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockVariantValues.FILE))) {
            if (BlockVariant.DISPLAY_NAME.equals(row.getOrDefault(Schema.BlockVariantValues.KEY, "").strip())) {
                String value = row.getOrDefault(Schema.BlockVariantValues.VALUE, "").strip();
                if (!value.isEmpty()) {
                    NAMES.put(row.getOrDefault(Schema.BlockVariantValues.VARIANT, "").strip(), value);
                }
            }
        }
    }

    /**
     * Registers the variants' blocks, then their items (mod bus, lowest priority: every mod's blocks are in,
     * so a variant of a mod's block works).
     */
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            for (Map.Entry<String, String> e : PLANNED.entrySet()) {
                ResourceLocation baseId = ResourceLocation.tryParse(e.getValue());
                if (baseId == null || !BuiltInRegistries.BLOCK.containsKey(baseId)) {
                    Strata.LOGGER.warn("Block variant {}: no base block {} in this game; it is not registered",
                            e.getKey(), e.getValue());
                    continue;
                }
                Block base = BuiltInRegistries.BLOCK.get(baseId);
                String refused = refusal(base);
                if (refused != null) {
                    Strata.LOGGER.warn("Block variant {}: {}; it is not registered", e.getKey(), refused);
                    continue;
                }
                try {
                    Block block = copy(base);
                    if (block == null) {
                        Strata.LOGGER.warn("Block variant {}: {} cannot be copied (no constructor Strata can fill);"
                                + " it is not registered", e.getKey(), e.getValue());
                        continue;
                    }
                    helper.register(ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, e.getKey()), block);
                    Def def = new Def(e.getKey(), block, base, null);
                    BY_NAME.put(e.getKey(), def);
                    BY_BLOCK.put(block, def);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                    Strata.LOGGER.warn("Block variant {}: could not copy {}: {}", e.getKey(), e.getValue(), ex.toString());
                }
            }
            if (!BY_NAME.isEmpty()) {
                Strata.LOGGER.info("Registered {} block variant(s)", BY_NAME.size());
            }
        });
        event.register(Registries.ITEM, helper -> {
            for (Def def : List.copyOf(BY_NAME.values())) {
                Item.Properties props = new Item.Properties().component(DataComponents.ITEM_NAME,
                        Component.literal(NAMES.getOrDefault(def.name(), BlockVariant.words(def.name()))));
                Item item = def.base().asItem() instanceof DoubleHighBlockItem
                        ? new DoubleHighBlockItem(def.block(), props) : new BlockItem(def.block(), props);
                helper.register(ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, def.name()), item);
                Def full = new Def(def.name(), def.block(), def.base(), item);
                BY_NAME.put(def.name(), full);
                BY_BLOCK.put(def.block(), full);
            }
        });
    }

    /** Why the block of this id cannot be copied, or null (null too for an id this game does not have). */
    public static String refusal(String baseBlockId) {
        ResourceLocation id = ResourceLocation.tryParse(baseBlockId == null ? "" : baseBlockId.strip());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        return refusal(BuiltInRegistries.BLOCK.get(id));
    }

    /** Why a base block cannot be copied, or null. */
    public static String refusal(Block base) {
        if (base instanceof EntityBlock) {
            return "a block with a block entity (a chest, a furnace, a sign) cannot be a base block yet";
        }
        if (base.defaultBlockState().isAir()) {
            return "air cannot be a base block";
        }
        return constructor(base.getClass()) == null ? "Strata cannot copy this block's kind" : null;
    }

    /** A copy of the base block: its class, its properties, and its constructor's other values. */
    private static Block copy(Block base) throws ReflectiveOperationException {
        Constructor<?> k = constructor(base.getClass());
        if (k == null) {
            return null;
        }
        BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(base).dropsLike(base);
        Class<?>[] types = k.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = types[i] == BlockBehaviour.Properties.class ? props : fieldOfType(base, types[i]);
        }
        k.setAccessible(true);
        return (Block) k.newInstance(args);
    }

    /**
     * The constructor Strata can fill: it takes the properties, and every other value is a field of that
     * exact type the block's own classes hold once.
     */
    private static Constructor<?> constructor(Class<?> c) {
        Constructor<?>[] all = c.getDeclaredConstructors();
        java.util.Arrays.sort(all, java.util.Comparator.comparingInt(Constructor::getParameterCount));
        for (Constructor<?> k : all) {
            Class<?>[] types = k.getParameterTypes();
            boolean props = false;
            boolean ok = true;
            for (Class<?> t : types) {
                if (t == BlockBehaviour.Properties.class) {
                    props = true;
                } else if (fields(c, t).size() != 1) {
                    ok = false;
                    break;
                }
            }
            if (ok && props) {
                return k;
            }
        }
        return null;
    }

    /** The instance fields of exactly this type in the class and its parents below Block. */
    private static List<Field> fields(Class<?> c, Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Block.class && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    private static Object fieldOfType(Block base, Class<?> type) throws IllegalAccessException {
        Field f = fields(base.getClass(), type).get(0);
        f.setAccessible(true);
        return f.get(base);
    }

    // ---- what the rest of Strata asks

    /** A block's id from its registry key, or "" for a block not registered. */
    public static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getResourceKey(block).map(k -> k.location().toString()).orElse("");
    }

    /** The variant a block is, or null. */
    public static Def of(Block block) {
        return BY_BLOCK.isEmpty() ? null : BY_BLOCK.get(block);
    }

    /** Every registered variant. */
    public static List<Def> all() {
        return List.copyOf(BY_NAME.values());
    }

    /** The variant registered under this name, or null (the editor says a new one waits for a restart). */
    public static Def named(String name) {
        return BY_NAME.get(name);
    }

    /** The base block's state with the same property values: a variant state's twin. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState twin(Block base, BlockState variant) {
        BlockState out = base.defaultBlockState();
        for (Property p : variant.getProperties()) {
            if (out.hasProperty(p)) {
                out = out.setValue(p, variant.getValue(p));
            }
        }
        return out;
    }

    /**
     * A variant drops what its base block drops, with its own item in place of the base block's (game bus).
     */
    public static void onDrops(BlockDropsEvent event) {
        Def def = of(event.getState().getBlock());
        if (def == null || def.item() == null) {
            return;
        }
        Item baseItem = def.base().asItem();
        if (baseItem == Items.AIR) {
            return;
        }
        event.getDrops().forEach(entity -> {
            ItemStack stack = entity.getItem();
            if (stack.is(baseItem)) {
                entity.setItem(new ItemStack(def.item(), stack.getCount()));
            }
        });
    }

    /**
     * Records the base blocks' tags when the server's tags load (game bus), in game/block_tags.csv, so the
     * generated data pack puts each variant in its base block's block and item tags.
     */
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD || BY_NAME.isEmpty()) {
            return;
        }
        List<GameLayer.Row> rows = new ArrayList<>();
        for (Def def : BY_NAME.values()) {
            String baseId = idOf(def.base());
            def.base().builtInRegistryHolder().tags().forEach(t ->
                    rows.add(new GameLayer.Row(baseId, GameLayer.BLOCK_TAG, t.location().toString(), "")));
            Item baseItem = def.base().asItem();
            if (baseItem != Items.AIR) {
                baseItem.builtInRegistryHolder().tags().forEach(t ->
                        rows.add(new GameLayer.Row(baseId, GameLayer.ITEM_TAG, t.location().toString(), "")));
            }
        }
        List<GameLayer.Row> before = GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCK_TAGS));
        if (!new java.util.HashSet<>(before).equals(new java.util.HashSet<>(rows))
                && GameLayer.write(GameLayer.dir().resolve(GameLayer.BLOCK_TAGS), TAGS_COMMENT, rows)) {
            com.strata.data.StrataDatapack.regenerate();
            Strata.LOGGER.info("Block variants: their base blocks' tags are written; they apply at the next world load or /reload");
        }
    }

    private static final String TAGS_COMMENT = """
            The GAME LAYER: the block and item tags of every block variant's base block, written when a world's tags load. Do not edit.
            group block_tag or item_tag: key the tag.""";

    /** The names of the variants this game registered, for the editor's "restart to add" note. */
    public static List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(BY_NAME.keySet()));
    }
}
