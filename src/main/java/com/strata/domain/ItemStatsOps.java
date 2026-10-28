package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.ItemStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything Client > Items > Item stats reads and does: every item's values from the game layer
 * (game/items.csv), with the user's from item_stats.csv on top.
 */
public final class ItemStatsOps {

    private ItemStatsOps() {
    }

    /** When an edit applies, said on both screens. */
    public static final String APPLIES = "Changes apply at the next game start, on the server and on each player's game.";

    /** One item in the list: its kind, how many values it has, how many are the user's. */
    public record Item(String id, String kind, int values, int edited) {
    }

    /** One value of an item: the game's, and the user's own or "". */
    public record Value(String item, String key, String game, String own, String note) {
        public String effective() {
            return own.isEmpty() ? game : own;
        }
    }

    /** Every item, by kind in ItemStats#KINDS order, then by id. */
    public static List<Item> items() {
        return items(GameLayer.read(GameLayer.dir().resolve(GameLayer.ITEMS)), own());
    }

    /** The items of the sheet with the counts of the user's values. */
    public static List<Item> items(List<GameLayer.Row> sheet, Map<String, String> own) {
        Map<String, String> kinds = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> edited = new HashMap<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.KIND.equals(r.group())) {
                kinds.put(r.entity(), r.value());
            } else if (GameLayer.COMPONENT.equals(r.group())) {
                counts.merge(r.entity(), 1, Integer::sum);
                if (own.containsKey(r.entity() + "|" + r.key())) {
                    edited.merge(r.entity(), 1, Integer::sum);
                }
            }
        }
        List<Item> out = new ArrayList<>();
        kinds.forEach((id, kind) -> out.add(new Item(id, kind, counts.getOrDefault(id, 0), edited.getOrDefault(id, 0))));
        out.sort(Comparator.comparingInt((Item i) -> kindOrder(i.kind())).thenComparing(Item::id));
        return out;
    }

    private static int kindOrder(String kind) {
        int i = ItemStats.KINDS.indexOf(kind);
        return i < 0 ? ItemStats.KINDS.size() : i;
    }

    /** One item's values, the user's over the game's, in sheet order. */
    public static List<Value> values(String item) {
        return values(GameLayer.read(GameLayer.dir().resolve(GameLayer.ITEMS)), own(), item);
    }

    /** One item's values from the sheet, with the edits laid over them. */
    public static List<Value> values(List<GameLayer.Row> sheet, Map<String, String> own, String item) {
        Map<String, String> notes = new HashMap<>();
        List<GameLayer.Row> values = new ArrayList<>();
        for (GameLayer.Row r : sheet) {
            if (!r.entity().equals(item)) continue;
            if (GameLayer.NOTE.equals(r.group())) notes.put(r.key(), r.value());
            if (GameLayer.COMPONENT.equals(r.group())) values.add(r);
        }
        List<Value> out = new ArrayList<>();
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (GameLayer.Row r : values) {
            keys.add(r.key());
            out.add(new Value(item, r.key(), r.value(), own.getOrDefault(item + "|" + r.key(), ""),
                    notes.getOrDefault(r.key(), "")));
        }
        // AN ATTRIBUTE THE ITEM DOES NOT HAVE, added here: no game value, the user's own.
        own.forEach((k, v) -> {
            String key = k.startsWith(item + "|") ? k.substring(item.length() + 1) : null;
            // And a component it does not have, set here.
            if (key != null && !keys.contains(key)
                    && (ItemStats.attributeParts(key) != null || ItemStats.componentId(key) != null)) {
                out.add(new Value(item, key, "", v, ""));
            }
        });
        return out;
    }

    /**
     * Adds an attribute modifier the item does not have (scrub: armour on a sword), at 0 in the item's own
     * slot - an armour piece's, else the main hand - for the user to set.
     */
    public static WriteReport addAttribute(String item, String attribute, List<Value> itemValues) {
        if (attribute == null || attribute.isBlank()) {
            return WriteReport.refused("Pick the attribute");
        }
        String slot = "mainhand";
        for (Value v : itemValues) {
            String[] parts = ItemStats.attributeParts(v.key());
            if (parts != null) {
                slot = parts[1];
                break;
            }
        }
        String key = ItemStats.attributeKey(attribute.strip(), slot);
        if (itemValues.stream().anyMatch(v -> v.key().equals(key))) {
            return WriteReport.refused("It has that attribute: select it and Set value");
        }
        return Commit.reloadIfOk(new WriteReport().record("the item's value", com.strata.data.TableWriter.upsert(
                Schema.ItemStats.FILE, java.util.Map.of(Schema.ItemStats.ITEM, item, Schema.ItemStats.KEY, key),
                java.util.Map.of(Schema.ItemStats.VALUE, "0"))))
                .needsReload("Added at 0: select it and Set value. It applies at the next game start");
    }

    /**
     * THE AT-A-GLANCE VALUES: every item's effective values, the user's over the game's, by item then key.
     */
    public static Map<String, Map<String, String>> glance() {
        return glance(GameLayer.read(GameLayer.dir().resolve(GameLayer.ITEMS)), own());
    }

    /** The effective values of the sheet with the edits laid over them. */
    public static Map<String, Map<String, String>> glance(List<GameLayer.Row> sheet, Map<String, String> own) {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.COMPONENT.equals(r.group())) {
                out.computeIfAbsent(r.entity(), k -> new HashMap<>())
                        .put(r.key(), own.getOrDefault(r.entity() + "|" + r.key(), r.value()));
            }
        }
        // A modifier the user added to an item that lacks it has no game value, only the user's.
        own.forEach((k, v) -> {
            int bar = k.indexOf('|');
            if (bar > 0 && ItemStats.attributeParts(k.substring(bar + 1)) != null) {
                out.computeIfAbsent(k.substring(0, bar), x -> new HashMap<>()).putIfAbsent(k.substring(bar + 1), v);
            }
        });
        return out;
    }

    /** The keys a column can show: every value key the sheet has but a tool's per-rule speeds, in sheet order. */
    public static List<String> glanceKeys(List<GameLayer.Row> sheet) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (GameLayer.Row r : sheet) {
            if (GameLayer.COMPONENT.equals(r.group()) && ItemStats.ruleIndex(r.key()) < 0) {
                keys.add(r.key());
            }
        }
        return List.copyOf(keys);
    }

    /** The keys the item columns can show, from the game layer. */
    public static List<String> glanceKeys() {
        return glanceKeys(GameLayer.read(GameLayer.dir().resolve(GameLayer.ITEMS)));
    }

    /** A column's heading: the value's name; an attribute without its slot when that is the main hand. */
    public static String glanceHeading(String key) {
        String[] parts = ItemStats.attributeParts(key);
        if (parts == null) {
            return ItemStats.name(key, "");
        }
        String attribute = com.strata.model.AttributeGuide.of(parts[0]).name();
        return "mainhand".equals(parts[1]) ? attribute : attribute + " (" + parts[1] + ")";
    }

    /** The user's values, "item|key" to value. */
    private static Map<String, String> own() {
        Map<String, String> own = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ItemStats.FILE))) {
            own.put(row.getOrDefault(Schema.ItemStats.ITEM, "").strip() + "|"
                    + row.getOrDefault(Schema.ItemStats.KEY, "").strip(), row.getOrDefault(Schema.ItemStats.VALUE, "").strip());
        }
        return own;
    }

    /** A value's name, with the attribute in words. */
    public static String name(Value v) {
        String[] parts = ItemStats.attributeParts(v.key());
        return ItemStats.name(v.key(), parts == null ? "" : com.strata.model.AttributeGuide.of(parts[0]).name());
    }

    /** What a value does. */
    public static String what(Value v) {
        return ItemStats.what(v.key(), v.note());
    }

    /** Why a typed value cannot be saved, or null. */
    public static String problem(Value v, String typed, List<Value> itemValues) {
        boolean durability = itemValues.stream().anyMatch(x -> ItemStats.MAX_DAMAGE.equals(x.key()));
        String problem = ItemStats.problem(v.key(), typed, durability);
        // A component's JSON goes through the component's own codec, so a typo is refused here.
        return problem == null && ItemStats.componentId(v.key()) != null
                ? com.strata.runtime.ItemComponents.problem(v.key(), typed) : problem;
    }

    /**
     * The components an item can be given: every one the game and its mods register that is saved with an
     * item, less those it shows already.
     */
    public static List<String> addableComponents(List<Value> itemValues) {
        java.util.Set<String> has = new java.util.HashSet<>();
        itemValues.forEach(v -> has.add(v.key()));
        return com.strata.runtime.ItemComponents.settable().stream()
                .filter(id -> !has.contains(ItemStats.componentKey(id))).toList();
    }

    /** A component picked to add, as an unset row of the item's sheet. */
    public static Value pendingComponent(String item, String typeId) {
        return new Value(item, ItemStats.componentKey(typeId), "", "", "");
    }

    /** Saves the user's value, lower case for a word. */
    public static WriteReport save(Value v, String typed, List<Value> itemValues) {
        String problem = problem(v, typed, itemValues);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String value = typed.strip();
        if (ItemStats.RARITY.equals(v.key()) || ItemStats.FIRE_RESISTANT.equals(v.key())
                || ItemStats.CAN_ALWAYS_EAT.equals(v.key())) {
            value = value.toLowerCase(java.util.Locale.ROOT);
        }
        return Commit.reloadIfOk(new WriteReport().record("the item value", TableWriter.upsert(Schema.ItemStats.FILE,
                keys(v), Map.of(Schema.ItemStats.VALUE, value))));
    }

    /** Removes the user's value, so the game's applies again at the next start. */
    public static WriteReport restore(Value v) {
        return Commit.reloadIfOk(new WriteReport().cascaded("your value",
                TableWriter.delete(Schema.ItemStats.FILE, keys(v))));
    }

    private static Map<String, String> keys(Value v) {
        return Map.of(Schema.ItemStats.ITEM, v.item(), Schema.ItemStats.KEY, v.key());
    }
}
