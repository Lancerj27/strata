package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** NAMED LISTS THE DESIGNER STAMPS ONTO OTHER LISTS. */
public final class GroupOps {

    private GroupOps() {
    }

    /** The kinds a group can hold, in the order the picker offers them. */
    public static final String KIND_ABILITIES = "abilities";

    public static final String KIND_LOOT = "loot";

    public static final String KIND_EQUIPMENT = "equipment";

    public static final List<String> KINDS = List.of(KIND_ABILITIES, KIND_LOOT, KIND_EQUIPMENT);

    /** How each kind is named to the designer. */
    private static final Map<String, String> KIND_WORDS = Map.of(
            KIND_ABILITIES, "abilities",
            KIND_LOOT, "loot",
            KIND_EQUIPMENT, "equipment");

    /** One group. */
    public record Group(String id, String name, String kind) {
    }

    /**
     * How an apply would land: how many rows the target does not have yet, and how many it already holds and
     * would keep.
     *
     * @param unit what ONE row of this kind is called, in the designer's words: an ability, a drop, a piece
     * @param units the same for several, so one kind's sentence is right without every caller having to
     *     rephrase it
     */
    public record Added(int toAdd, int alreadyThere, String unit, String units) {

        /** A group of abilities, which is where these two words began. */
        public Added(int toAdd, int alreadyThere) {
            this(toAdd, alreadyThere, "ability", "abilities");
        }

        /** The sentence for a footer, in the designer's terms. */
        public String said() {
            if (toAdd == 0 && alreadyThere == 0) {
                return "That group holds nothing";
            }
            if (toAdd == 0) {
                return "Your list already has all " + (alreadyThere == 1 ? "of it" : alreadyThere + " of them");
            }
            String adds = toAdd == 1 ? "1 " + unit : toAdd + " " + units;
            return alreadyThere == 0 ? "Adds " + adds : "Adds " + adds + ", keeps your " + alreadyThere;
        }
    }

    // ---- what a kind is called ----

    /** A kind in the designer's words, so "loot" never reaches a screen raw. */
    public static String kindWord(String kind) {
        return KIND_WORDS.getOrDefault(kind == null ? "" : kind, kind);
    }

    /** The table a kind's rows are filed in, or "" for a kind with no table yet. */
    public static String tableOf(String kind) {
        return switch (kind == null ? "" : kind) {
            case KIND_ABILITIES -> Schema.Abilities.FILE;
            case KIND_LOOT -> Schema.Loot.FILE;
            case KIND_EQUIPMENT -> Schema.Equipment.FILE;
            default -> "";
        };
    }

    /** The column in a kind's table that names the owner a row belongs to. */
    public static String ownerColumn(String kind) {
        return switch (kind == null ? "" : kind) {
            case KIND_ABILITIES -> Schema.Abilities.VARIANT_NAME;
            case KIND_LOOT -> Schema.Loot.VARIANT_NAME;
            case KIND_EQUIPMENT -> Schema.Equipment.VARIANT_NAME;
            default -> "";
        };
    }

    // ---- reading ----

    public static List<Group> all() {
        return all(Paths.ROOT);
    }

    /** Every group, in the file's order. */
    public static List<Group> all(Path root) {
        List<Group> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(root.resolve(Schema.Groups.FILE))) {
            String id = row.getOrDefault(Schema.Groups.GROUP_ID, "").strip();
            if (id.isEmpty()) {
                continue;
            }
            out.add(new Group(id,
                    row.getOrDefault(Schema.Groups.GROUP_NAME, "").strip(),
                    row.getOrDefault(Schema.Groups.KIND, "").strip()));
        }
        return out;
    }

    public static List<Group> ofKind(String kind) {
        return ofKind(kind, Paths.ROOT);
    }

    /** The groups of one kind, which is what a picker on that kind's editor offers. */
    public static List<Group> ofKind(String kind, Path root) {
        return all(root).stream().filter(g -> g.kind().equals(kind == null ? "" : kind)).toList();
    }

    /** The names of a kind's groups, for a picker. */
    public static List<String> names(String kind) {
        return names(kind, Paths.ROOT);
    }

    public static List<String> names(String kind, Path root) {
        return ofKind(kind, root).stream().map(Group::name).toList();
    }

    public static Group find(String id) {
        return find(id, Paths.ROOT);
    }

    /** The group with this id, or null when nothing has it. */
    public static Group find(String id, Path root) {
        String want = id == null ? "" : id.strip();
        for (Group g : all(root)) {
            if (g.id().equals(want)) {
                return g;
            }
        }
        return null;
    }

    /** The group with this name, or null. */
    public static Group findByName(String name, Path root) {
        String want = name == null ? "" : name.strip();
        for (Group g : all(root)) {
            if (g.name().equals(want)) {
                return g;
            }
        }
        return null;
    }

    // ---- the rules, pure ----

    /**
     * Why this group cannot be made or renamed, in the designer's words, or null when it can.
     *
     * @param originalId the group being renamed, or "" when making a new one, so it does not collide with
     *     itself
     */
    public static String problem(String name, String kind, String originalId, List<Group> existing) {
        String shown = name == null ? "" : name.strip();
        if (shown.isEmpty()) {
            return "A group needs a name";
        }
        if (!KINDS.contains(kind)) {
            return "\"" + kind + "\" is not a kind of group";
        }
        String id = idFor(shown);
        if (id.isEmpty()) {
            return "That name has no letters or numbers to make an id from";
        }
        String original = originalId == null ? "" : originalId.strip();
        if (id.equals(original)) {
            // The same name: there is nothing to change, so there is nothing that can go wrong.
            return null;
        }
        for (Group g : existing == null ? List.<Group>of() : existing) {
            // The group being renamed is skipped by IDENTITY, not by comparing the new id with the old one.
            if (g.id().equals(original)) {
                continue;
            }
            if (g.id().equals(id)) {
                return "Another group already uses that id";
            }
            if (g.name().equals(shown)) {
                return "Another group is already called " + shown;
            }
        }
        return null;
    }

    /**
     * The owner key a group's rows are filed under: the name in lower case, with everything that is not a
     * letter or a digit folded to an underscore and runs of them closed up.
     */
    public static String idFor(String name) {
        StringBuilder out = new StringBuilder();
        String lower = name == null ? "" : name.strip().toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (!out.isEmpty() && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }
        // A TRAILING gap is dropped for the same reason a leading one is.
        return out.isEmpty() || out.charAt(out.length() - 1) == '_'
                ? out.substring(0, Math.max(0, out.length() - 1))
                : out.toString();
    }

    // ---- writing the list of groups ----

    public static WriteReport create(String name, String kind) {
        return Commit.reloadIfOk(create(name, kind, Paths.ROOT));
    }

    /** Makes a group. */
    public static WriteReport create(String name, String kind, Path root) {
        String bad = problem(name, kind, "", all(root));
        if (bad != null) {
            return WriteReport.refused(bad);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Groups.GROUP_NAME, name.strip());
        values.put(Schema.Groups.KIND, kind);
        WriteReport report = new WriteReport();
        report.record("the group", TableWriter.upsert(root.resolve(Schema.Groups.FILE),
                Map.of(Schema.Groups.GROUP_ID, idFor(name)), values));
        return report;
    }

    public static WriteReport rename(String id, String newName) {
        return Commit.reloadIfOk(rename(id, newName, Paths.ROOT));
    }

    /** Renames a group. */
    public static WriteReport rename(String id, String newName, Path root) {
        Group group = find(id, root);
        if (group == null) {
            return WriteReport.refused("There is no group called " + id);
        }
        String bad = problem(newName, group.kind(), group.id(), all(root));
        if (bad != null) {
            return WriteReport.refused(bad);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Groups.GROUP_NAME, newName.strip());
        values.put(Schema.Groups.KIND, group.kind());
        WriteReport report = new WriteReport();
        report.record("the group's name", TableWriter.upsert(root.resolve(Schema.Groups.FILE),
                Map.of(Schema.Groups.GROUP_ID, group.id()), values));
        return report;
    }

    public static WriteReport delete(String id) {
        return Commit.reloadIfOk(delete(id, Paths.ROOT));
    }

    /** Removes a group and the rows it holds. */
    public static WriteReport delete(String id, Path root) {
        Group group = find(id, root);
        if (group == null) {
            return WriteReport.refused("There is no group called " + id);
        }
        WriteReport report = new WriteReport();
        report.record("the group", TableWriter.delete(root.resolve(Schema.Groups.FILE),
                Map.of(Schema.Groups.GROUP_ID, group.id())));
        String table = tableOf(group.kind());
        if (!table.isEmpty()) {
            // Cascaded, not recorded: dropping rows the designer never sees in this operation is a side
            // effect of removing the thing that held them, and it is reported as such rather than as its own
            // edit.
            report.cascaded("the group's own rows", clearOwnRows(root, table, group));
        }
        return report;
    }

    // ---- applying: the feature ----

    public static WriteReport applyAbilities(String groupId, String targetVariant,
                                              String scope, String scopeId) {
        return Commit.reloadIfOk(applyAbilities(groupId, targetVariant, scope, scopeId, Paths.ROOT));
    }

    /** What applying a group to a target would do, BEFORE it does it: the picker's footer sentence. */
    public static Added countsAbilities(String groupId, String targetVariant,
                                        String scope, String scopeId) {
        return countsAbilities(groupId, targetVariant, scope, scopeId, Paths.ROOT);
    }

    /** Path-rooted {@link #countsAbilities}, for the self-test. */
    public static Added countsAbilities(String groupId, String targetVariant, String scope,
                                        String scopeId, Path root) {
        if (why(groupId, KIND_ABILITIES, targetVariant, scope, scopeId, root) != null) {
            return new Added(0, 0);
        }
        String id = find(groupId, root).id();
        return counts(ownAbilitiesAll(root, Schema.Abilities.FILE, id),
                ownAbilitiesIn(root, Schema.Abilities.FILE, targetVariant == null ? "" : targetVariant.strip(),
                        VariantOps.normalScope(scope), VariantOps.normalScopeId(scope, scopeId)));
    }

    /** Why this apply cannot happen, or null when it can. */
    private static String why(String groupId, String wantedKind, String targetVariant,
                              String scope, String scopeId, Path root) {
        Group group = find(groupId, root);
        if (group == null) {
            return "There is no group called " + groupId;
        }
        if (!wantedKind.equals(group.kind())) {
            return "\"" + group.name() + "\" is a group of "
                    + kindWord(group.kind()) + ", not of " + kindWord(wantedKind);
        }
        return refusalFor(group, wantedKind, targetVariant, scope, scopeId);
    }

    /** Copies a group's ability rows into a target's own list. */
    public static WriteReport applyAbilities(String groupId, String targetVariant, String scope,
                                         String scopeId, Path root) {
        String bad = why(groupId, KIND_ABILITIES, targetVariant, scope, scopeId, root);
        if (bad != null) {
            return WriteReport.refused(bad);
        }
        Group group = find(groupId, root);

        String target = targetVariant == null ? "" : targetVariant.strip();
        // The target's rows are read ONCE, before the loop: the set of rows it owns is what decides every
        // skip, and re-reading the table per row would both cost a parse each time and make the answer depend
        // on rows this operation had already written.
        List<AbilitiesOps.AbilityRow> source =
                ownAbilitiesAll(root, Schema.Abilities.FILE, group.id());
        List<AbilitiesOps.AbilityRow> own =
                ownAbilitiesIn(root, Schema.Abilities.FILE, target,
                        VariantOps.normalScope(scope), VariantOps.normalScopeId(scope, scopeId));
        if (counts(source, own).toAdd() == 0) {
            // Nothing to do, and nothing said: this list already holds every row the group holds.
            return new WriteReport();
        }
        WriteReport report = new WriteReport();
        for (AbilitiesOps.AbilityRow row : source) {
            if (own.stream().anyMatch(o -> AbilitiesOps.sameRow(o, row))) {
                continue;
            }
            report.record("the ability", TableWriter.upsert(root.resolve(Schema.Abilities.FILE),
                    AbilitiesOps.abilityKey(target, scope, scopeId, row), AbilitiesOps.rowValues(row)));
        }
        return report;
    }

    /** What an apply would do, before it does it: the picker's footer sentence. */
    public static Added counts(List<AbilitiesOps.AbilityRow> source,
                               List<AbilitiesOps.AbilityRow> targetOwn) {
        int toAdd = 0;
        int already = 0;
        for (AbilitiesOps.AbilityRow row : source) {
            if (targetOwn.stream().anyMatch(o -> AbilitiesOps.sameRow(o, row))) {
                already++;
            } else {
                toAdd++;
            }
        }
        return new Added(toAdd, already);
    }

    /** Why this group cannot be stamped onto this target, or null when it can. */
    static String refusalFor(Group group, String wantedKind, String targetVariant,
                             String scope, String scopeId) {
        if (tableOf(wantedKind).isEmpty()) {
            return "Nothing can be stamped onto " + kindWord(wantedKind) + " yet";
        }
        String target = targetVariant == null ? "" : targetVariant.strip();
        boolean global = VariantOps.normalScope(scope).isEmpty()
                && VariantOps.normalScopeId(scope, scopeId).isEmpty();
        if (group.id().equals(target) && global) {
            return "A group cannot be applied to itself";
        }
        return null;
    }

    // ---- applying: loot and equipment ----

    /** Copies a group's drop rows onto a mob's own loot list, then reloads. */
    public static WriteReport applyLoot(String groupId, String entity, String targetVariant,
                                        String scope, String scopeId) {
        return Commit.reloadIfOk(applyLoot(groupId, entity, targetVariant, scope, scopeId, Paths.ROOT));
    }

    /** Path-rooted {@link #applyLoot}, for the self-test. */
    public static WriteReport applyLoot(String groupId, String entity, String targetVariant, String scope,
                                        String scopeId, Path root) {
        String bad = why(groupId, KIND_LOOT, targetVariant, scope, scopeId, root);
        if (bad != null) {
            return WriteReport.refused(bad);
        }
        String target = targetVariant == null ? "" : targetVariant.strip();
        String mob = entity == null ? "" : entity.strip();
        String at = lootScope(scope);
        String tier = LootOps.biomeTier(at, scopeId);

        WriteReport report = new WriteReport();
        // ADOPT FIRST, exactly as a save does.
        LootOps.adoptBaseLoot(report, at, scopeId, mob, target, tier, root);
        if (!report.ok()) {
            return report;
        }
        List<Map<String, String>> table = CsvTable.read(root.resolve(Schema.Loot.FILE));
        Set<String> mine = lootItems(table, at, scopeId, mob, target, tier);
        int pool = lootPool(table, at, scopeId, mob, target, tier);
        for (Map<String, String> row : groupRows(root, KIND_LOOT, groupId)) {
            String item = row.getOrDefault(Schema.Loot.ITEM, "").strip();
            // The target has priority: a drop it already makes - in this scope, in a wider one, or by
            // adopting its base mob - is left alone.
            if (item.isEmpty() || !mine.add(item)) {
                continue;
            }
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(Schema.Loot.SCOPE, at);
            keys.put(Schema.Loot.SCOPE_ID, scopeId == null ? "" : scopeId.strip());
            keys.put(Schema.Loot.ENTITY, mob);
            keys.put(Schema.Loot.VARIANT_NAME, target);
            keys.put(Schema.Loot.POOL_INDEX, String.valueOf(pool));
            keys.put(Schema.Loot.ITEM, item);
            pool++;
            report.record("the drop", TableWriter.upsert(root.resolve(Schema.Loot.FILE), keys,
                    copied(row, List.of(Schema.Loot.ENTRY_INDEX, Schema.Loot.COUNT_MIN, Schema.Loot.COUNT_MAX,
                            Schema.Loot.CHANCE_PCT, Schema.Loot.MODE, Schema.Loot.CONDITION_TYPE,
                            Schema.Loot.CONDITION_VALUE))));
            if (!report.ok()) {
                return report;
            }
        }
        return report;
    }

    /** What a loot group would add, asked before the write: the picker's footer sentence. */
    public static Added countsLoot(String groupId, String entity, String targetVariant, String scope,
                                   String scopeId) {
        return countsLoot(groupId, entity, targetVariant, scope, scopeId, Paths.ROOT);
    }

    /** Path-rooted {@link #countsLoot}, for the self-test. */
    public static Added countsLoot(String groupId, String entity, String targetVariant, String scope,
                                   String scopeId, Path root) {
        if (why(groupId, KIND_LOOT, targetVariant, scope, scopeId, root) != null) {
            return new Added(0, 0, "drop", "drops");
        }
        String at = lootScope(scope);
        String tier = LootOps.biomeTier(at, scopeId);
        Set<String> mine = lootItems(CsvTable.read(root.resolve(Schema.Loot.FILE)), at, scopeId,
                entity == null ? "" : entity.strip(), targetVariant == null ? "" : targetVariant.strip(), tier);
        int toAdd = 0;
        int already = 0;
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> row : groupRows(root, KIND_LOOT, groupId)) {
            String item = row.getOrDefault(Schema.Loot.ITEM, "").strip();
            // Counted once per ITEM: the write makes an item the group holds twice only once, and a footer
            // saying "Adds 2" over one row is a sentence a designer cannot check against their own list.
            if (item.isEmpty() || !seen.add(item)) {
                continue;
            }
            if (mine.contains(item)) {
                already++;
            } else {
                toAdd++;
            }
        }
        return new Added(toAdd, already, "drop", "drops");
    }

    /**
     * Copies a group's equipment rows onto a mob's own equipment list, then reloads.
     *
     * @param shown the list as the screen shows it, inherited pieces included. Asked for from the caller
     *     because it is the only place the game's own equipment is available without running the game.
     */
    public static WriteReport applyEquipment(String groupId, String targetVariant, String scope, String scopeId,
                                             List<EquipmentOps.EquipmentRow> shown) {
        return Commit.reloadIfOk(applyEquipment(groupId, targetVariant, scope, scopeId, shown, Paths.ROOT));
    }

    /** Path-rooted {@link #applyEquipment}, for the self-test. */
    public static WriteReport applyEquipment(String groupId, String targetVariant, String scope, String scopeId,
                                             List<EquipmentOps.EquipmentRow> shown, Path root) {
        String bad = why(groupId, KIND_EQUIPMENT, targetVariant, scope, scopeId, root);
        if (bad != null) {
            return WriteReport.refused(bad);
        }
        String target = targetVariant == null ? "" : targetVariant.strip();
        String tier = EquipmentOps.biomeTier(scope, scopeId);
        WriteReport report = new WriteReport();
        List<Map<String, String>> source = groupRows(root, KIND_EQUIPMENT, groupId);
        Map<String, Set<String>> itemsBySlot = new LinkedHashMap<>();
        for (Map<String, String> row : source) {
            String slot = row.getOrDefault(Schema.Equipment.SLOT, "").strip();
            String item = row.getOrDefault(Schema.Equipment.ITEM, "").strip();
            if (slot.isEmpty() || item.isEmpty()) {
                continue;
            }
            itemsBySlot.computeIfAbsent(slot, s -> new LinkedHashSet<>()).add(item);
        }

        // (b) keep what the instance already wears in the slots this group fills.
        Set<String> kept = new LinkedHashSet<>();
        for (String slot : itemsBySlot.keySet()) {
            for (EquipmentOps.EquipmentRow worn : EquipmentOps.gameEquipmentToKeep(null, slot,
                    itemsBySlot.get(slot), shown == null ? List.of() : shown)) {
                if (!kept.add(worn.slot() + "\u0000" + worn.item())) {
                    continue;
                }
                report.record("the equipment it already wore", TableWriter.upsert(
                        root.resolve(Schema.Equipment.FILE),
                        EquipmentOps.equipmentKeys(target, scope, scopeId, worn.slot(), worn.item()),
                        EquipmentOps.equipmentValues(worn)));
                if (!report.ok()) {
                    return report;
                }
            }
        }

        // (a) adopt this scope's own set from the wider scope that has one.
        EquipmentOps.adoptWider(report, target, scope, scopeId, tier, root);
        if (!report.ok()) {
            return report;
        }

        Set<String> mine = equipmentOwned(root, target, scope, scopeId, tier);
        for (Map<String, String> row : source) {
            String slot = row.getOrDefault(Schema.Equipment.SLOT, "").strip();
            String item = row.getOrDefault(Schema.Equipment.ITEM, "").strip();
            if (slot.isEmpty() || !mine.add(slot + "\u0000" + item)) {
                continue;
            }
            report.record("the equipment", TableWriter.upsert(root.resolve(Schema.Equipment.FILE),
                    EquipmentOps.equipmentKeys(target, scope, scopeId, slot, item),
                    copied(row, List.of(Schema.Equipment.CHANCE_PCT, Schema.Equipment.MODE,
                            Schema.Equipment.CHANCE_HARD, Schema.Equipment.DROP_PCT,
                            Schema.Equipment.ENCHANT_PCT, Schema.Equipment.LOCAL_DIFFICULTY,
                            Schema.Equipment.PLAYER_LEVEL))));
            if (!report.ok()) {
                return report;
            }
        }
        return report;
    }

    /** What an equipment group would add, asked before the write. */
    public static Added countsEquipment(String groupId, String targetVariant, String scope, String scopeId) {
        return countsEquipment(groupId, targetVariant, scope, scopeId, Paths.ROOT);
    }

    /** Path-rooted {@link #countsEquipment}, for the self-test. */
    public static Added countsEquipment(String groupId, String targetVariant, String scope, String scopeId,
                                        Path root) {
        if (why(groupId, KIND_EQUIPMENT, targetVariant, scope, scopeId, root) != null) {
            return new Added(0, 0, "piece", "pieces");
        }
        String target = targetVariant == null ? "" : targetVariant.strip();
        Set<String> mine = equipmentOwned(root, target, scope, scopeId,
                EquipmentOps.biomeTier(scope, scopeId));
        int toAdd = 0;
        int already = 0;
        for (Map<String, String> row : groupRows(root, KIND_EQUIPMENT, groupId)) {
            String slot = row.getOrDefault(Schema.Equipment.SLOT, "").strip();
            String item = row.getOrDefault(Schema.Equipment.ITEM, "").strip();
            if (slot.isEmpty() || item.isEmpty()) {
                continue;
            }
            if (mine.contains(slot + "\u0000" + item)) {
                already++;
            } else {
                toAdd++;
            }
        }
        return new Added(toAdd, already, "piece", "pieces");
    }

    /** Loot is stored under the scope name the editor shows; a blank one is the global set. */
    private static String lootScope(String scope) {
        String at = scope == null ? "" : scope.strip();
        return at.isEmpty() ? "global" : at;
    }

    /** The rows a group holds, in file order, for the one kind it is of. */
    private static List<Map<String, String>> groupRows(Path root, String kind, String groupId) {
        String table = tableOf(kind);
        if (table.isEmpty()) {
            return List.of();
        }
        Group group = find(groupId, root);
        return group == null ? List.of()
                : ownRows(table, ownerColumn(kind), group.id(), root);
    }

    /**
     * What the target's loot list will hold after the apply, as the items it makes: its own rows in the scope
     * chain, or - when it has none - the base mob's, which the apply is about to adopt as its own.
     */
    private static Set<String> lootItems(List<Map<String, String>> table, String scope, String scopeId,
                                         String entity, String variant, String tier) {
        List<Map<String, String>> chain = LootOps.chainRows(table, scope, scopeId, entity, variant, tier);
        if (chain.isEmpty() && !variant.isEmpty()) {
            chain = LootOps.chainRows(table, scope, scopeId, entity, "", tier);
        }
        Set<String> out = new LinkedHashSet<>();
        for (Map<String, String> row : chain) {
            String item = row.getOrDefault(Schema.Loot.ITEM, "").strip();
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out;
    }

    /** The first pool index the target's loot list does not use, over the same rows. */
    private static int lootPool(List<Map<String, String>> table, String scope, String scopeId,
                                String entity, String variant, String tier) {
        List<Map<String, String>> chain = LootOps.chainRows(table, scope, scopeId, entity, variant, tier);
        if (chain.isEmpty() && !variant.isEmpty()) {
            chain = LootOps.chainRows(table, scope, scopeId, entity, "", tier);
        }
        int max = -1;
        for (Map<String, String> row : chain) {
            max = Math.max(max, Parse.integer(row.get(Schema.Loot.POOL_INDEX), -1));
        }
        return max + 1;
    }

    /** The (slot, item) pairs this instance owns in this scope after the apply. */
    private static Set<String> equipmentOwned(Path root, String variant, String scope, String scopeId,
                                              String tier) {
        List<List<Map<String, String>>> layers = EquipmentOps.decidingLayers(variant,
                ScopeLayers.scopeLayers(scope, scopeId, tier), root);
        int at = ScopeLayers.firstWithRows(layers);
        Set<String> out = new LinkedHashSet<>();
        if (at < 0) {
            return out;
        }
        for (Map<String, String> row : layers.get(at)) {
            String slot = row.getOrDefault(Schema.Equipment.SLOT, "").strip();
            String item = row.getOrDefault(Schema.Equipment.ITEM, "").strip();
            if (!slot.isEmpty() && !item.isEmpty()) {
                out.add(slot + "\u0000" + item);
            }
        }
        return out;
    }

    /** The cells a copied row carries, blank where the source had no cell. */
    private static Map<String, String> copied(Map<String, String> row, List<String> columns) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : columns) {
            values.put(column, row.getOrDefault(column, "").strip());
        }
        return values;
    }

    // ---- reading rows filed under one owner ----

    /** The rows a kind's table holds for one owner id, in file order. */
    private static List<Map<String, String>> ownRows(String table, String ownerColumn,
                                                     String ownerId, Path root) {
        List<Map<String, String>> out = new ArrayList<>();
        if (table.isEmpty() || ownerColumn == null || ownerColumn.isEmpty()) {
            return out;
        }
        for (Map<String, String> row : CsvTable.read(root.resolve(table))) {
            if (ownerId.equals(row.getOrDefault(ownerColumn, "").strip())) {
                out.add(row);
            }
        }
        return out;
    }

    /** Every ability row one owner holds, whatever scope it was filed in. */
    private static List<AbilitiesOps.AbilityRow> ownAbilitiesAll(Path root, String table, String owner) {
        List<AbilitiesOps.AbilityRow> out = new ArrayList<>();
        for (Map<String, String> row : ownRows(table, ownerColumn(KIND_ABILITIES), owner, root)) {
            out.add(abilityRow(row));
        }
        return out;
    }

    /**
     * The ability rows one owner holds in one scope, with no inherited set folded in - an apply must not
     * mistake what a list inherits for what it owns, or it would skip a row it is about to write and then
     * refuse to write the ones it has not seen.
     */
    private static List<AbilitiesOps.AbilityRow> ownAbilitiesIn(Path root, String table, String owner,
                                                                  String scope, String scopeId) {
        List<AbilitiesOps.AbilityRow> out = new ArrayList<>();
        for (Map<String, String> row : ownRows(table, ownerColumn(KIND_ABILITIES), owner, root)) {
            String rowScope = VariantOps.normalScope(row.getOrDefault(Schema.Abilities.SCOPE, ""));
            String rowScopeId = VariantOps.normalScopeId(row.get(Schema.Abilities.SCOPE),
                    row.getOrDefault(Schema.Abilities.SCOPE_ID, ""));
            if (rowScope.equals(scope) && rowScopeId.equals(scopeId)) {
                out.add(abilityRow(row));
            }
        }
        return out;
    }

    /** One abilities.csv row as the editor's row, with enabled defaulted the way AbilitiesOps does. */
    private static AbilitiesOps.AbilityRow abilityRow(Map<String, String> row) {
        // Every column, so a group's class rows keep their class, trigger and entity.
        return AbilitiesOps.rowFrom(row, false);
    }

    /** Drops every row of a table filed under one owner id. */
    private static boolean clearOwnRows(Path root, String table, Group group) {
        boolean all = true;
        for (Map<String, String> row : ownRows(table, ownerColumn(group.kind()), group.id(), root)) {
            // THE WHOLE ROW IS THE KEY.
            Map<String, String> key = new LinkedHashMap<>(row);
            key.replaceAll((column, value) -> value == null ? "" : value.strip());
            all &= TableWriter.delete(root.resolve(table), key);
        }
        return all;
    }
}
