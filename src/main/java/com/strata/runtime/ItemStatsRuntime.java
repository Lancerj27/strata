package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.ItemStats;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** EVERY ITEM'S STATS, READ AND SET AT GAME START. */
public final class ItemStatsRuntime {

    private ItemStatsRuntime() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every item's stats, rewritten at every game start. Do not edit: your values go in item_stats.csv.
            group component: key the game's component name, value the game's. group note: a tool rule's blocks, a modifier's operation. group kind: the list's group.""";

    /** Reads every item into the game layer, then sets the user's values (mod bus, lowest priority). */
    public static void onModifyDefaults(ModifyDefaultComponentsEvent event) {
        List<GameLayer.Row> rows = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = id(item);
            if (id.isEmpty()) {
                continue;
            }
            try {
                rows.addAll(rows(id, item));
            } catch (RuntimeException | LinkageError e) {
                Strata.LOGGER.debug("Item stats: could not read {}: {}", id, e.toString());
            }
        }
        if (GameLayer.write(GameLayer.dir().resolve(GameLayer.ITEMS), COMMENT, rows)) {
            Strata.LOGGER.info("Game layer: extracted the stats of {} item(s)", BuiltInRegistries.ITEM.size());
        }
        Map<String, Map<String, String>> edits = edits();
        applied = Map.copyOf(edits);
        int set = 0;
        for (Map.Entry<String, Map<String, String>> e : edits.entrySet()) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                Strata.LOGGER.warn("Item stats: no item {} in this game; its row is skipped", e.getKey());
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            try {
                event.modify(item, b -> patch(b, item, e.getValue()));
                changed.put(item, types(e.getValue()));
                set++;
            } catch (RuntimeException ex) {
                Strata.LOGGER.warn("Item stats: could not set {}: {}", e.getKey(), ex.toString());
            }
        }
        if (set > 0) {
            Strata.LOGGER.info("Item stats: set your values on {} item(s)", set);
        }
    }

    /** The components Strata changed on each item at this game start. */
    private static final Map<Item, List<DataComponentType<?>>> changed = new java.util.IdentityHashMap<>();

    /** The component types a set of item stats keys changes. */
    private static List<DataComponentType<?>> types(Map<String, String> values) {
        java.util.LinkedHashSet<DataComponentType<?>> out = new java.util.LinkedHashSet<>();
        for (String key : values.keySet()) {
            if (ItemStats.MAX_STACK_SIZE.equals(key)) out.add(DataComponents.MAX_STACK_SIZE);
            else if (ItemStats.MAX_DAMAGE.equals(key)) out.add(DataComponents.MAX_DAMAGE);
            else if (ItemStats.RARITY.equals(key)) out.add(DataComponents.RARITY);
            else if (ItemStats.FIRE_RESISTANT.equals(key)) out.add(DataComponents.FIRE_RESISTANT);
            else if (key.startsWith("food.")) out.add(DataComponents.FOOD);
            else if (key.startsWith("tool.")) out.add(DataComponents.TOOL);
            else if (key.startsWith(ItemStats.ATTRIBUTE_PREFIX)) out.add(DataComponents.ATTRIBUTE_MODIFIERS);
        }
        out.addAll(ItemComponents.types(values));
        return List.copyOf(out);
    }

    /**
     * The patch a stack is sent with: its own patch, plus the item's Strata-changed components as the server
     * has them, where the stack's patch does not set them already.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static DataComponentPatch forSending(ItemStack stack, DataComponentPatch own) {
        List<DataComponentType<?>> types = changed.isEmpty() ? null : changed.get(stack.getItem());
        if (types == null || types.isEmpty()) {
            return own;
        }
        DataComponentPatch.Builder b = DataComponentPatch.builder();
        DataComponentMap defaults = stack.getItem().components();
        for (DataComponentType type : types) {
            if (own.get(type) != null) {
                continue;
            }
            Object value = defaults.get(type);
            if (value != null) {
                b.set(type, value);
            } else {
                b.remove(type);
            }
        }
        for (Map.Entry<DataComponentType<?>, Optional<?>> e : own.entrySet()) {
            DataComponentType type = e.getKey();
            if (e.getValue().isPresent()) {
                b.set(type, e.getValue().get());
            } else {
                b.remove(type);
            }
        }
        return b.build();
    }

    /** The item stats set at this game start, item to key to value (a variant's stack hash reads them). */
    private static volatile Map<String, Map<String, String>> applied = Map.of();

    /** The item stats set on one item at this game start, or none. */
    public static Map<String, String> appliedTo(String item) {
        return applied.getOrDefault(item, Map.of());
    }

    /** The user's values, item to key to value, from item_stats.csv. */
    private static Map<String, Map<String, String>> edits() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ItemStats.FILE))) {
            String item = row.getOrDefault(Schema.ItemStats.ITEM, "").strip();
            String key = row.getOrDefault(Schema.ItemStats.KEY, "").strip();
            String value = row.getOrDefault(Schema.ItemStats.VALUE, "").strip();
            if (!item.isEmpty() && !key.isEmpty() && !value.isEmpty()) {
                out.computeIfAbsent(item, k -> new LinkedHashMap<>()).put(key, value);
            }
        }
        return out;
    }

    /** One item's game layer rows. */
    public static List<GameLayer.Row> rows(String id, Item item) {
        List<GameLayer.Row> out = new ArrayList<>();
        DataComponentMap c = item.components();
        out.add(value(id, ItemStats.MAX_STACK_SIZE, String.valueOf(c.getOrDefault(DataComponents.MAX_STACK_SIZE, 1))));
        Integer maxDamage = c.get(DataComponents.MAX_DAMAGE);
        if (maxDamage != null) {
            out.add(value(id, ItemStats.MAX_DAMAGE, String.valueOf(maxDamage)));
        }
        out.add(value(id, ItemStats.RARITY, c.getOrDefault(DataComponents.RARITY, Rarity.COMMON).getSerializedName()));
        out.add(value(id, ItemStats.FIRE_RESISTANT, String.valueOf(c.has(DataComponents.FIRE_RESISTANT))));
        FoodProperties food = c.get(DataComponents.FOOD);
        if (food != null) {
            out.add(value(id, ItemStats.NUTRITION, String.valueOf(food.nutrition())));
            out.add(value(id, ItemStats.SATURATION, decimal(food.saturation())));
            out.add(value(id, ItemStats.CAN_ALWAYS_EAT, String.valueOf(food.canAlwaysEat())));
            out.add(value(id, ItemStats.EAT_SECONDS, decimal(food.eatSeconds())));
        }
        Tool tool = c.get(DataComponents.TOOL);
        if (tool != null) {
            out.add(value(id, ItemStats.MINING_SPEED, decimal(tool.defaultMiningSpeed())));
            out.add(value(id, ItemStats.DAMAGE_PER_BLOCK, String.valueOf(tool.damagePerBlock())));
            for (int i = 0; i < tool.rules().size(); i++) {
                Tool.Rule rule = tool.rules().get(i);
                if (rule.speed().isPresent()) {
                    String key = ItemStats.ruleKey(i);
                    out.add(value(id, key, decimal(rule.speed().get())));
                    out.add(new GameLayer.Row(id, GameLayer.NOTE, key, blocks(rule)));
                }
            }
        }
        boolean armor = item instanceof ArmorItem;
        Map<String, Integer> seen = new HashMap<>();
        for (ItemAttributeModifiers.Entry entry : modifiers(item, c).modifiers()) {
            String attribute = entry.attribute().unwrapKey().map(k -> k.location().toString()).orElse("");
            if (attribute.isEmpty()) {
                continue;
            }
            armor |= attribute.endsWith("armor");
            String key = key(attribute, entry.slot().getSerializedName(), seen);
            out.add(value(id, key, Parse.number(entry.modifier().amount())));
            out.add(new GameLayer.Row(id, GameLayer.NOTE, key, entry.modifier().operation().getSerializedName()));
        }
        boolean weapon = item instanceof SwordItem || item instanceof ProjectileWeaponItem
                || item instanceof TridentItem || item instanceof MaceItem;
        boolean digs = item instanceof DiggerItem || item instanceof ShearsItem || tool != null;
        out.add(new GameLayer.Row(id, GameLayer.KIND, GameLayer.KIND,
                ItemStats.kind(armor, weapon, digs, food != null, item instanceof BlockItem)));
        // Every other component it has, as the JSON its codec writes.
        out.addAll(ItemComponents.rows(id, item));
        return out;
    }

    /** The item's attribute modifiers: its component, else the ones its class keeps in code (armor). */
    @SuppressWarnings("deprecation")
    private static ItemAttributeModifiers modifiers(Item item, DataComponentMap c) {
        ItemAttributeModifiers own = c.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return own.modifiers().isEmpty() ? item.getDefaultAttributeModifiers() : own;
    }

    /** An attribute modifier's key, with "#n" on the second and later of one attribute and slot. */
    private static String key(String attribute, String slot, Map<String, Integer> seen) {
        String base = ItemStats.attributeKey(attribute, slot);
        int n = seen.merge(base, 1, Integer::sum);
        return n == 1 ? base : base + "#" + n;
    }

    /** A tool rule's blocks, in words: its tag, or how many blocks it names. */
    private static String blocks(Tool.Rule rule) {
        return rule.blocks().unwrapKey().map(t -> "#" + t.location())
                .orElse(com.strata.ui.Words.count(rule.blocks().size(), "block", "blocks"));
    }

    private static GameLayer.Row value(String id, String key, String value) {
        return new GameLayer.Row(id, GameLayer.COMPONENT, key, value);
    }

    /** Puts one item's values into a component patch. */
    @SuppressWarnings("deprecation")
    public static void patch(DataComponentPatch.Builder b, Item item, Map<String, String> values) {
        DataComponentMap c = item.components();
        String id = id(item);
        String v;
        if ((v = values.get(ItemStats.MAX_STACK_SIZE)) != null && ItemStats.whole(v) != null) {
            b.set(DataComponents.MAX_STACK_SIZE, ItemStats.whole(v));
        }
        if ((v = values.get(ItemStats.MAX_DAMAGE)) != null && ItemStats.whole(v) != null && c.has(DataComponents.MAX_DAMAGE)) {
            b.set(DataComponents.MAX_DAMAGE, ItemStats.whole(v));
        }
        if ((v = values.get(ItemStats.RARITY)) != null && ItemStats.RARITIES.contains(v.toLowerCase(Locale.ROOT))) {
            b.set(DataComponents.RARITY, Rarity.valueOf(v.toUpperCase(Locale.ROOT)));
        }
        if ((v = values.get(ItemStats.FIRE_RESISTANT)) != null) {
            if (Boolean.parseBoolean(v)) {
                b.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
            } else {
                b.remove(DataComponents.FIRE_RESISTANT);
            }
        }
        FoodProperties food = c.get(DataComponents.FOOD);
        if (food != null && (values.containsKey(ItemStats.NUTRITION) || values.containsKey(ItemStats.SATURATION)
                || values.containsKey(ItemStats.CAN_ALWAYS_EAT) || values.containsKey(ItemStats.EAT_SECONDS))) {
            b.set(DataComponents.FOOD, new FoodProperties(
                    whole(values.get(ItemStats.NUTRITION), food.nutrition()),
                    (float) number(values.get(ItemStats.SATURATION), food.saturation()),
                    values.containsKey(ItemStats.CAN_ALWAYS_EAT)
                            ? Boolean.parseBoolean(values.get(ItemStats.CAN_ALWAYS_EAT)) : food.canAlwaysEat(),
                    (float) number(values.get(ItemStats.EAT_SECONDS), food.eatSeconds()),
                    food.usingConvertsTo(), food.effects()));
        }
        Tool tool = c.get(DataComponents.TOOL);
        if (tool != null && values.keySet().stream().anyMatch(k -> k.startsWith("tool."))) {
            List<Tool.Rule> rules = new ArrayList<>();
            for (int i = 0; i < tool.rules().size(); i++) {
                Tool.Rule r = tool.rules().get(i);
                String speed = values.get(ItemStats.ruleKey(i));
                rules.add(speed == null || r.speed().isEmpty() ? r
                        : new Tool.Rule(r.blocks(), Optional.of((float) number(speed, r.speed().get())), r.correctForDrops()));
            }
            b.set(DataComponents.TOOL, new Tool(rules,
                    (float) number(values.get(ItemStats.MINING_SPEED), tool.defaultMiningSpeed()),
                    whole(values.get(ItemStats.DAMAGE_PER_BLOCK), tool.damagePerBlock())));
        }
        if (values.keySet().stream().anyMatch(k -> k.startsWith(ItemStats.ATTRIBUTE_PREFIX))) {
            ItemAttributeModifiers game = modifiers(item, c);
            List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
            Map<String, Integer> seen = new HashMap<>();
            for (ItemAttributeModifiers.Entry entry : game.modifiers()) {
                String attribute = entry.attribute().unwrapKey().map(k -> k.location().toString()).orElse("");
                String amount = attribute.isEmpty() ? null
                        : values.get(key(attribute, entry.slot().getSerializedName(), seen));
                if (amount == null || ItemStats.number(amount) == null) {
                    entries.add(entry);
                    continue;
                }
                AttributeModifier m = entry.modifier();
                Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder = entry.attribute();
                entries.add(new ItemAttributeModifiers.Entry(holder,
                        new AttributeModifier(m.id(), ItemStats.number(amount), m.operation()), entry.slot()));
            }
            // An attribute the item does not have, which a variant adds.
            for (Map.Entry<String, String> e : values.entrySet()) {
                String[] parts = ItemStats.attributeParts(e.getKey());
                if (parts == null || e.getKey().contains("#") || ItemStats.number(e.getValue()) == null
                        || seen.containsKey(e.getKey())) {
                    continue;
                }
                added(parts[0], parts[1], ItemStats.number(e.getValue())).ifPresent(entries::add);
            }
            b.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(entries, game.showInTooltip()));
        }
        // Every other component: set from its JSON, or taken off.
        ItemComponents.patch(b, id, values);
        for (String key : values.keySet()) {
            if (!known(key, c)) {
                Strata.LOGGER.warn("Item stats: {} has no value {}; the row is skipped", id, key);
            }
        }
    }

    /**
     * A new modifier for an attribute the item lacks: its id names the attribute and the slot, so two of one
     * variant never share one.
     */
    private static Optional<ItemAttributeModifiers.Entry> added(String attribute, String slot, double amount) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(attribute);
        net.minecraft.world.entity.EquipmentSlotGroup group = java.util.Arrays.stream(
                        net.minecraft.world.entity.EquipmentSlotGroup.values())
                .filter(g -> g.getSerializedName().equals(slot)).findFirst().orElse(null);
        if (id == null || group == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ATTRIBUTE.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.ATTRIBUTE, id))
                .map(h -> new ItemAttributeModifiers.Entry(h, new AttributeModifier(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID,
                                "item_variant/" + id.getPath() + "/" + slot),
                        amount, AttributeModifier.Operation.ADD_VALUE), group));
    }

    /** The item has this value to set. */
    private static boolean known(String key, DataComponentMap c) {
        if (key.startsWith(ItemStats.ATTRIBUTE_PREFIX) || ItemStats.componentId(key) != null) return true;
        if (key.startsWith("food.")) return c.has(DataComponents.FOOD);
        if (key.startsWith("tool.")) return c.has(DataComponents.TOOL);
        if (ItemStats.MAX_DAMAGE.equals(key)) return c.has(DataComponents.MAX_DAMAGE);
        return ItemStats.MAX_STACK_SIZE.equals(key) || ItemStats.RARITY.equals(key) || ItemStats.FIRE_RESISTANT.equals(key);
    }

    /** The item's id, from its registry key. */
    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item).map(k -> k.location().toString()).orElse("");
    }

    /** A float as it reads (1.6, not 1.600000023841858). */
    private static String decimal(float f) {
        return Parse.number(Double.parseDouble(Float.toString(f)));
    }

    private static int whole(String v, int fallback) {
        Integer n = v == null ? null : ItemStats.whole(v);
        return n == null ? fallback : n;
    }

    private static double number(String v, double fallback) {
        Double d = v == null ? null : ItemStats.number(v);
        return d == null ? fallback : d;
    }
}
