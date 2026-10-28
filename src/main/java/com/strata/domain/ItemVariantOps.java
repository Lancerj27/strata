package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.ItemStats;
import com.strata.model.ItemVariant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Everything Client > Items > Item variants reads and does. */
public final class ItemVariantOps {

    private ItemVariantOps() {
    }

    /** One variant in the list: its base item's kind, and how many values and effects it sets. */
    public record Variant(String name, String baseItem, String kind, int values, int effects) {
    }

    /** One value of a variant: the base item's (inherited), and the variant's own or "". */
    public record Value(String variant, String key, String base, String own, String note) {
        public String effective() {
            return own.isEmpty() ? base : own;
        }
    }

    // ---- reads

    /** Every variant, by its base item's kind, then by name. */
    public static List<Variant> variants() {
        Map<String, String> kinds = new HashMap<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.ITEMS))) {
            if (GameLayer.KIND.equals(r.group())) kinds.put(r.entity(), r.value());
        }
        return variants(rows(Schema.ItemVariants.FILE), rows(Schema.ItemVariantValues.FILE),
                rows(Schema.ItemVariantEffects.FILE), kinds);
    }

    /** The variants of the three tables. */
    public static List<Variant> variants(List<Map<String, String>> variants, List<Map<String, String>> values,
                                         List<Map<String, String>> effects, Map<String, String> kinds) {
        Map<String, Integer> valueCount = count(values, Schema.ItemVariantValues.VARIANT);
        Map<String, Integer> effectCount = count(effects, Schema.ItemVariantEffects.VARIANT);
        List<Variant> out = new ArrayList<>();
        for (Map<String, String> row : variants) {
            String name = cell(row, Schema.ItemVariants.NAME);
            String base = cell(row, Schema.ItemVariants.BASE_ITEM);
            if (!name.isEmpty()) {
                out.add(new Variant(name, base, kinds.getOrDefault(base, ItemStats.OTHER),
                        valueCount.getOrDefault(name, 0), effectCount.getOrDefault(name, 0)));
            }
        }
        out.sort(Comparator.comparingInt((Variant v) -> kindOrder(v.kind())).thenComparing(Variant::name));
        return out;
    }

    /** One variant, or null. */
    public static Variant find(String name) {
        return variants().stream().filter(v -> v.name().equals(name)).findFirst().orElse(null);
    }

    /** A variant's base item, or "" for no such variant. */
    public static String baseOf(String name) {
        for (Map<String, String> row : rows(Schema.ItemVariants.FILE)) {
            if (name.equals(cell(row, Schema.ItemVariants.NAME))) {
                return cell(row, Schema.ItemVariants.BASE_ITEM);
            }
        }
        return "";
    }

    /** A variant's own values, key to value, in file order. */
    public static Map<String, String> own(String name) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> row : rows(Schema.ItemVariantValues.FILE)) {
            if (name.equals(cell(row, Schema.ItemVariantValues.VARIANT))) {
                out.put(cell(row, Schema.ItemVariantValues.KEY), cell(row, Schema.ItemVariantValues.VALUE));
            }
        }
        return out;
    }

    /**
     * A variant's values: the look values, then every stat of its base item (the base item's number under the
     * variant's), then an attribute or an enchantment the variant adds.
     */
    public static List<Value> values(String name) {
        Variant v = find(name);
        return v == null ? List.of() : values(name, ItemStatsOps.values(v.baseItem()), own(name));
    }

    /** The values of a variant from its base item's values and its own. */
    public static List<Value> values(String name, List<ItemStatsOps.Value> base, Map<String, String> own) {
        List<Value> out = new ArrayList<>();
        Map<String, String> baseLook = Map.of(ItemStats.RARITY, base.stream()
                .filter(b -> ItemStats.RARITY.equals(b.key())).map(ItemStatsOps.Value::effective).findFirst().orElse("common"),
                ItemVariant.GLINT, "default", ItemVariant.UNBREAKABLE, "false");
        for (String key : ItemVariant.LOOK_KEYS) {
            out.add(new Value(name, key, baseLook.getOrDefault(key, ""), own.getOrDefault(key, ""), ""));
        }
        Set<String> listed = new java.util.HashSet<>(ItemVariant.LOOK_KEYS);
        for (ItemStatsOps.Value b : base) {
            if (!listed.add(b.key())) continue;
            out.add(new Value(name, b.key(), b.effective(), own.getOrDefault(b.key(), ""), b.note()));
        }
        own.forEach((key, value) -> {
            if (listed.add(key)) out.add(new Value(name, key, "", value, ""));
        });
        return out;
    }

    /** A value's name. */
    public static String name(Value v) {
        String enchantment = ItemVariant.enchantmentOf(v.key());
        if (enchantment != null) {
            return "Enchantment " + words(enchantment);
        }
        if (ItemVariant.LOOK_KEYS.contains(v.key()) && !ItemStats.RARITY.equals(v.key())) {
            return ItemVariant.name(v.key());
        }
        return ItemStatsOps.name(new ItemStatsOps.Value(v.variant(), v.key(), v.base(), v.own(), v.note()));
    }

    /** What a value does. */
    public static String what(Value v) {
        if (ItemVariant.enchantmentOf(v.key()) != null) {
            return "The enchantment's level on a new stack. After that the enchantments are the player's.";
        }
        if (ItemVariant.LOOK_KEYS.contains(v.key()) && !ItemStats.RARITY.equals(v.key())) {
            return ItemVariant.what(v.key());
        }
        String what = ItemStats.what(v.key(), v.note());
        return v.base().isEmpty() && ItemStats.attributeParts(v.key()) != null
                ? what + " The base item has none: the variant adds it." : what;
    }

    // ---- writes

    /** Why a variant cannot be added, or null. */
    public static String addProblem(String name, String baseItem, Set<String> items) {
        String problem = ItemVariant.nameProblem(name, variants().stream().map(Variant::name).toList());
        if (problem != null) return problem;
        String base = baseItem == null ? "" : baseItem.strip();
        return base.isEmpty() ? "Pick the base item" : !items.contains(base) ? "No item " + base + " in this game" : null;
    }

    /** Adds a variant of a base item. */
    public static WriteReport add(String name, String baseItem) {
        Set<String> items = new java.util.HashSet<>();
        ItemStatsOps.items().forEach(i -> items.add(i.id()));
        String problem = addProblem(name, baseItem, items);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return Commit.reloadIfOk(new WriteReport().record("the variant", TableWriter.upsert(Schema.ItemVariants.FILE,
                Map.of(Schema.ItemVariants.NAME, name.strip()), Map.of(Schema.ItemVariants.BASE_ITEM, baseItem.strip()))));
    }

    /** Removes variants with their values and effects (answer A2: their stacks stay; their effects stop). */
    public static WriteReport remove(List<String> names) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.record("the variant " + name, TableWriter.delete(Schema.ItemVariants.FILE,
                        Map.of(Schema.ItemVariants.NAME, name)));
                report.cascaded("its values", TableWriter.delete(Schema.ItemVariantValues.FILE,
                        Map.of(Schema.ItemVariantValues.VARIANT, name)));
                report.cascaded("its effects", TableWriter.delete(Schema.ItemVariantEffects.FILE,
                        Map.of(Schema.ItemVariantEffects.VARIANT, name)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Why a typed value cannot be saved, or null. */
    public static String problem(Value v, String typed, List<Value> all) {
        boolean durability = all.stream().anyMatch(x -> ItemStats.MAX_DAMAGE.equals(x.key()) && !x.effective().isEmpty());
        String problem = ItemVariant.problem(v.key(), typed, durability);
        // A component's JSON through the component's own codec.
        return problem == null && ItemStats.componentId(v.key()) != null
                ? com.strata.runtime.ItemComponents.problem(v.key(), typed) : problem;
    }

    /** A component picked to add to a variant, as an unset row of its sheet. */
    public static Value pendingComponent(String variant, String typeId) {
        return new Value(variant, ItemStats.componentKey(typeId), "", "", "");
    }

    /** Saves the variant's own value; a word in lower case. */
    public static WriteReport save(Value v, String typed, List<Value> all) {
        String problem = problem(v, typed, all);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return set(v.variant(), v.key(), normal(v.key(), typed.strip()));
    }

    /** Takes the variant's own value away: the base item's applies (for an added value, it goes). */
    public static WriteReport restore(Value v) {
        return Commit.reloadIfOk(new WriteReport().cascaded("the variant's value",
                TableWriter.delete(Schema.ItemVariantValues.FILE, Map.of(Schema.ItemVariantValues.VARIANT, v.variant(),
                        Schema.ItemVariantValues.KEY, v.key()))));
    }

    /** Adds an attribute modifier the base item does not have. */
    public static WriteReport addAttribute(String variant, String attribute, String slot, String amount) {
        String key = ItemStats.attributeKey(attribute == null ? "" : attribute.strip(), slot == null ? "" : slot.strip());
        if (attribute == null || attribute.isBlank()) {
            return WriteReport.refused("Pick the attribute");
        }
        String problem = ItemVariant.problem(key, amount, false);
        return problem != null ? WriteReport.refused(problem) : set(variant, key, amount.strip());
    }

    /** Adds an enchantment, or sets its level. */
    public static WriteReport setEnchantment(String variant, String enchantment, String level) {
        if (enchantment == null || enchantment.isBlank()) {
            return WriteReport.refused("Pick the enchantment");
        }
        String key = ItemVariant.enchantKey(enchantment.strip());
        String problem = ItemVariant.problem(key, level, false);
        return problem != null ? WriteReport.refused(problem) : set(variant, key, level.strip());
    }

    private static WriteReport set(String variant, String key, String value) {
        return Commit.reloadIfOk(new WriteReport().record("the variant's value", TableWriter.upsert(
                Schema.ItemVariantValues.FILE, Map.of(Schema.ItemVariantValues.VARIANT, variant,
                        Schema.ItemVariantValues.KEY, key), Map.of(Schema.ItemVariantValues.VALUE, value))));
    }

    /** A word value in the case the tables keep. */
    private static String normal(String key, String value) {
        return switch (key) {
            case ItemStats.RARITY, ItemStats.FIRE_RESISTANT, ItemStats.CAN_ALWAYS_EAT, ItemVariant.GLINT,
                 ItemVariant.UNBREAKABLE -> value.toLowerCase(Locale.ROOT);
            default -> value;
        };
    }

    // ---- the editor's form

    /** The rarity choice that keeps the base item's. */
    public static final String BASE_RARITY = "Base item";
    /** The rarity choices, in the game's order after the base item's. */
    public static final List<String> RARITY_CHOICES = List.of(BASE_RARITY, "common", "uncommon", "rare", "epic");
    /** The unbreakable choices. */
    public static final List<String> YES_NO = List.of("No", "Yes");

    /** The editor's fields, as typed. */
    public record Form(String name, String baseItem, String displayName, String lore, String rarity, String glint,
                       String unbreakable, String customModelData) {
    }

    /** The form of a variant, or a new one's (blank, base item unpicked). */
    public static Form formFor(String name) {
        if (name == null || name.isEmpty()) {
            return new Form("", "", "", "", BASE_RARITY, "default", "No", "");
        }
        Map<String, String> own = own(name);
        return new Form(name, baseOf(name), own.getOrDefault(ItemVariant.DISPLAY_NAME, ""),
                own.getOrDefault(ItemVariant.LORE, ""), own.getOrDefault(ItemStats.RARITY, BASE_RARITY),
                own.getOrDefault(ItemVariant.GLINT, "default"),
                "true".equals(own.get(ItemVariant.UNBREAKABLE)) ? "Yes" : "No",
                own.getOrDefault(ItemVariant.CUSTOM_MODEL_DATA, ""));
    }

    /** Why the form cannot be saved, or null. */
    public static String formProblem(Form f, boolean isNew) {
        if (isNew) {
            Set<String> items = new java.util.HashSet<>();
            ItemStatsOps.items().forEach(i -> items.add(i.id()));
            String problem = addProblem(f.name(), f.baseItem(), items);
            if (problem != null) return problem;
        }
        String model = f.customModelData() == null ? "" : f.customModelData().strip();
        return model.isEmpty() || ItemStats.whole(model) != null ? null : "Custom model data must be a whole number";
    }

    /** The look rows a form writes: key to value, "" for a row to remove (the base item's then applies). */
    public static Map<String, String> lookWrites(Form f) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(ItemVariant.DISPLAY_NAME, strip(f.displayName()));
        out.put(ItemVariant.LORE, strip(f.lore()));
        out.put(ItemStats.RARITY, BASE_RARITY.equals(f.rarity()) ? "" : strip(f.rarity()));
        out.put(ItemVariant.GLINT, "default".equals(f.glint()) ? "" : strip(f.glint()));
        out.put(ItemVariant.UNBREAKABLE, "Yes".equals(f.unbreakable()) ? "true" : "");
        out.put(ItemVariant.CUSTOM_MODEL_DATA, strip(f.customModelData()));
        return out;
    }

    /** Saves the form: a new variant is added first; each look value is set, or removed when blank. */
    public static WriteReport saveForm(String originalName, Form f) {
        boolean isNew = originalName == null || originalName.isEmpty();
        String problem = formProblem(f, isNew);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String name = isNew ? f.name().strip() : originalName;
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            if (isNew) {
                report.record("the variant", TableWriter.upsert(Schema.ItemVariants.FILE,
                        Map.of(Schema.ItemVariants.NAME, name), Map.of(Schema.ItemVariants.BASE_ITEM, f.baseItem().strip())));
            }
            lookWrites(f).forEach((key, value) -> {
                Map<String, String> keys = Map.of(Schema.ItemVariantValues.VARIANT, name, Schema.ItemVariantValues.KEY, key);
                if (value.isEmpty()) {
                    report.cascaded("the variant's " + key, TableWriter.delete(Schema.ItemVariantValues.FILE, keys));
                } else {
                    report.record("the variant's " + key, TableWriter.upsert(Schema.ItemVariantValues.FILE, keys,
                            Map.of(Schema.ItemVariantValues.VALUE, value)));
                }
            });
            return Commit.reloadIfOk(report);
        });
    }

    /**
     * The slot an added attribute works in: the base item's own, from the slot of its first attribute (a
     * chestplate's chest), else the main hand.
     */
    public static String naturalSlot(List<Value> values) {
        for (Value v : values) {
            String[] parts = ItemStats.attributeParts(v.key());
            if (parts != null && !v.base().isEmpty()) {
                return parts[1];
            }
        }
        return "mainhand";
    }

    /** Adds an attribute the base item lacks, in the base item's own slot, at 0 (the designer then sets it). */
    public static WriteReport addAttribute(String variant, String attribute, List<Value> values) {
        return addAttribute(variant, attribute, naturalSlot(values), "0");
    }

    /** "Pick an attribute" when the variant has it in that slot already, or null. */
    public static String attributeTaken(String attribute, List<Value> values) {
        String key = ItemStats.attributeKey(attribute, naturalSlot(values));
        return values.stream().anyMatch(v -> v.key().equals(key)) ? "It has that attribute: select it and Set value" : null;
    }

    // ---- effects: the abilities list of one item variant

    /** A variant's effects, for AbilitiesScreen: its own rows only (an item variant inherits no effects). */
    public static AbilitiesOps.Store effectsStore(String variant) {
        // The columns are abilities.csv's, so the mob editor's store serves.
        return AbilitiesOps.flatStore(Schema.ItemVariantEffects.FILE, Schema.ItemVariantEffects.VARIANT, variant);
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    // ---- helpers

    private static List<Map<String, String>> rows(String file) {
        return CsvTable.read(Paths.table(file));
    }

    private static String cell(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").strip();
    }

    private static Map<String, Integer> count(List<Map<String, String>> rows, String column) {
        Map<String, Integer> out = new HashMap<>();
        for (Map<String, String> row : rows) {
            out.merge(cell(row, column), 1, Integer::sum);
        }
        return out;
    }

    private static int kindOrder(String kind) {
        int i = ItemStats.KINDS.indexOf(kind);
        return i < 0 ? ItemStats.KINDS.size() : i;
    }

    /** An id in words: "minecraft:fire_aspect" -> "Fire aspect". */
    private static String words(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
