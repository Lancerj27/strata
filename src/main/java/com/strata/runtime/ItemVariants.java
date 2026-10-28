package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.ItemStats;
import com.strata.model.ItemVariant;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** ITEM VARIANTS IN THE GAME. */
public final class ItemVariants {

    private ItemVariants() {
    }

    /** The custom_data keys a variant's stack carries. */
    public static final String TAG_VARIANT = "strata_variant";
    public static final String TAG_HASH = "strata_hash";

    /** One loaded variant. */
    public record Def(String name, Item base, String baseId, Map<String, String> values, int hash) {
    }

    private static volatile Map<String, Def> byName = Map.of();

    /** The components a variant sets, which a refresh puts back to the base item's first. */
    private static final List<DataComponentType<?>> MANAGED = List.of(DataComponents.ITEM_NAME, DataComponents.LORE,
            DataComponents.RARITY, DataComponents.ENCHANTMENT_GLINT_OVERRIDE, DataComponents.CUSTOM_MODEL_DATA,
            DataComponents.UNBREAKABLE, DataComponents.MAX_STACK_SIZE, DataComponents.MAX_DAMAGE,
            DataComponents.FIRE_RESISTANT, DataComponents.FOOD, DataComponents.TOOL, DataComponents.ATTRIBUTE_MODIFIERS);

    /** Reads the two tables (Config#reload). */
    public static void load() {
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ItemVariantValues.FILE))) {
            String v = row.getOrDefault(Schema.ItemVariantValues.VARIANT, "").strip();
            String key = row.getOrDefault(Schema.ItemVariantValues.KEY, "").strip();
            String value = row.getOrDefault(Schema.ItemVariantValues.VALUE, "").strip();
            if (!v.isEmpty() && !key.isEmpty() && !value.isEmpty()) {
                values.computeIfAbsent(v, k -> new LinkedHashMap<>()).put(key, value);
            }
        }
        Map<String, Def> out = new LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ItemVariants.FILE))) {
            String name = row.getOrDefault(Schema.ItemVariants.NAME, "").strip();
            String baseId = row.getOrDefault(Schema.ItemVariants.BASE_ITEM, "").strip();
            ResourceLocation id = ResourceLocation.tryParse(baseId);
            if (name.isEmpty() || id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                Strata.LOGGER.warn("item_variants.csv: {} has no base item {} in this game - skipped", name, baseId);
                continue;
            }
            Map<String, String> own = Map.copyOf(values.getOrDefault(name, Map.of()));
            // The base item's item stats are in the hash too: a change to them (at a game start) is an edit.
            Map<String, String> hashed = new LinkedHashMap<>(own);
            ItemStatsRuntime.appliedTo(baseId).forEach((k, v) -> hashed.put("base." + k, v));
            out.put(name, new Def(name, BuiltInRegistries.ITEM.get(id), baseId, own, ItemVariant.hash(baseId, hashed)));
        }
        byName = Map.copyOf(out);
        Strata.LOGGER.info("Loaded {} item variant(s)", out.size());
    }

    /** The loaded variant, or null. */
    public static Def get(String name) {
        return name == null ? null : byName.get(name);
    }

    /** Every loaded variant's name, sorted. */
    public static List<String> names() {
        return byName.keySet().stream().sorted().toList();
    }

    /**
     * The stack an item column names: a variant's for "variant:name", else the plain item's; EMPTY when it
     * names nothing.
     */
    public static ItemStack stack(String itemColumn, int count, HolderLookup.Provider registries) {
        String name = ItemVariant.nameOf(itemColumn);
        if (name != null) {
            return make(name, count, registries);
        }
        ResourceLocation id = ResourceLocation.tryParse(itemColumn == null ? "" : itemColumn.strip());
        return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(id), Math.max(1, count));
    }

    /** A new stack of the variant, with its enchantments; EMPTY when there is no such variant. */
    public static ItemStack make(String name, int count, HolderLookup.Provider registries) {
        Def def = get(name);
        if (def == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(def.base(), Math.max(1, count));
        apply(stack, def);
        if (registries != null) {
            enchant(stack, def, registries);
        }
        return stack;
    }

    /** The variant a stack is, or null. */
    public static String variantOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_VARIANT)) {
            return null;
        }
        return data.copyTag().getString(TAG_VARIANT);
    }

    /** Makes a stack made before an edit current. */
    public static boolean refresh(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(TAG_VARIANT)) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        Def def = get(tag.getString(TAG_VARIANT));
        if (def == null || tag.getInt(TAG_HASH) == def.hash() || stack.getItem() != def.base()) {
            return false;
        }
        for (DataComponentType<?> type : MANAGED) {
            reset(stack, type);
        }
        apply(stack, def);
        return true;
    }

    /** Refreshes what an entity wears and holds, and a player's inventory. */
    public static void refreshAll(LivingEntity entity) {
        if (byName.isEmpty()) {
            return;
        }
        if (entity instanceof Player player) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                refresh(inventory.getItem(i));
            }
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            refresh(entity.getItemBySlot(slot));
        }
    }

    // ---- the components

    /** Sets the variant's components and its tag on a stack of its base item. */
    private static void apply(ItemStack stack, Def def) {
        Map<String, String> v = def.values();
        Map<String, String> stats = new LinkedHashMap<>();
        v.forEach((key, value) -> {
            if (!ItemVariant.LOOK_KEYS.contains(key) && ItemVariant.enchantmentOf(key) == null) {
                stats.put(key, value);
            }
        });
        String rarity = v.get(ItemStats.RARITY);
        if (rarity != null) {
            stats.put(ItemStats.RARITY, rarity);
        }
        if (!stats.isEmpty()) {
            DataComponentPatch.Builder b = DataComponentPatch.builder();
            ItemStatsRuntime.patch(b, def.base(), stats);
            stack.applyComponents(b.build());
        }
        String name = v.get(ItemVariant.DISPLAY_NAME);
        if (name != null) {
            stack.set(DataComponents.ITEM_NAME, Component.literal(name));
        }
        List<String> lore = ItemVariant.loreLines(v.get(ItemVariant.LORE));
        if (!lore.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(l -> (Component) Component.literal(l)).toList()));
        }
        String glint = v.getOrDefault(ItemVariant.GLINT, "default").toLowerCase(Locale.ROOT);
        if (!"default".equals(glint)) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, "on".equals(glint));
        }
        Integer model = v.containsKey(ItemVariant.CUSTOM_MODEL_DATA) ? ItemStats.whole(v.get(ItemVariant.CUSTOM_MODEL_DATA)) : null;
        if (model != null) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model));
        }
        if (Boolean.parseBoolean(v.getOrDefault(ItemVariant.UNBREAKABLE, "false"))) {
            stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        }
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_VARIANT, def.name());
        tag.putInt(TAG_HASH, def.hash());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
    }

    /** Adds the variant's enchantments to a new stack. */
    private static void enchant(ItemStack stack, Def def, HolderLookup.Provider registries) {
        var lookup = registries.lookup(Registries.ENCHANTMENT).orElse(null);
        if (lookup == null) {
            return;
        }
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        boolean any = false;
        for (Map.Entry<String, String> e : def.values().entrySet()) {
            String enchantment = ItemVariant.enchantmentOf(e.getKey());
            Integer level = enchantment == null ? null : ItemStats.whole(e.getValue());
            ResourceLocation id = enchantment == null ? null : ResourceLocation.tryParse(enchantment);
            if (id == null || level == null) {
                continue;
            }
            var holder = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
            if (holder == null) {
                Strata.LOGGER.warn("Item variant {}: no enchantment {} in this game - skipped", def.name(), enchantment);
                continue;
            }
            enchantments.set(holder, level);
            any = true;
        }
        if (any) {
            stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        }
    }

    /** Puts one component back to the base item's own (or takes it away when the item has none). */
    private static <T> void reset(ItemStack stack, DataComponentType<T> type) {
        T base = stack.getItem().components().get(type);
        if (base == null) {
            stack.remove(type);
        } else {
            stack.set(type, base);
        }
    }

    /**
     * When stacks are made current: when a mob or a player puts one on or takes it off, when a
     * player joins, every 2 seconds for each player's inventory, and when a mob loads.
     */
    public static final class Events {

        /** The player inventory check's interval, in ticks. */
        private static final int INVENTORY_TICKS = 40;

        @net.neoforged.bus.api.SubscribeEvent
        public void onEquipmentChange(net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent event) {
            if (!event.getEntity().level().isClientSide()) {
                refresh(event.getTo());
            }
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            refreshAll(event.getEntity());
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
            Player player = event.getEntity();
            if (!player.level().isClientSide() && player.tickCount % INVENTORY_TICKS == 0) {
                refreshAll(player);
            }
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living
                    && !(living instanceof Player)) {
                refreshAll(living);
            }
        }
    }
}
