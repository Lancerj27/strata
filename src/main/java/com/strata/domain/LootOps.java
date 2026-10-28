package com.strata.domain;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BiomeConfig;
import com.strata.model.LootCondition;
import com.strata.model.OverrideMode;
import com.strata.util.Labels;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/** Everything the loot editor reads and does for a scope's drops. */
public final class LootOps {

    private LootOps() {
    }

    /** The condition dropdown word for "no condition". */
    public static final String NO_CONDITION = "(none)";

    /** The condition choices: none, then every type the loot model parses. */
    public static final List<String> CONDITION_CHOICES = Stream.concat(
            Stream.of(NO_CONDITION),
            Arrays.stream(LootCondition.Type.values()).map(t -> t.name().toLowerCase(Locale.ROOT)))
            .toList();

    /** True when a condition choice uses the Value field: None does not, so the field greys. */
    public static boolean conditionTakesValue(String choice) {
        return LootCondition.Type.parse(choice) != null;
    }

    /**
     * What the mob drops, in one line, from the rows the Drops list shows: its own and what it inherits, each
     * marked where it comes from; a denied drop is left out.
     *
     * @param words an item id in words
     */
    public static String lootSummary(List<LootRow> rows, java.util.function.Function<String, String> words) {
        List<String> parts = new ArrayList<>();
        for (LootRow r : rows) {
            if (r.mode() == OverrideMode.DENY) {
                continue;
            }
            String count = r.countMin() == r.countMax() ? " x" + r.countMin() : " x" + r.countMin() + "-" + r.countMax();
            String from = r.inherited() ? " (" + r.inheritedFrom() + ")" : "";
            parts.add(words.apply(r.item()) + count + from);
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /** The mode choices: Add and Replace. Remove on a game drop stops it; an old deny row still loads. */
    public static final List<String> MODE_CHOICES =
            List.of(OverrideMode.ADD.lower(), OverrideMode.REPLACE.lower());

    /** A drop the game itself gives the entity, passed in by the caller. */
    public record GameLootItem(String item, int countMin, int countMax) {
    }

    /** One row of the loot list. */
    public record LootRow(String scope, String scopeId, String entity, String variantName,
                          OverrideMode mode, int poolIndex, int entryIndex,
                          String item, int countMin, int countMax,
                          int chancePct, String conditionType, String conditionValue,
                          boolean inherited, String inheritedFrom) {
    }

    /** What the entry editor shows and saves, as typed. */
    public record LootForm(String item, String countMin, String countMax, String chancePct,
                           String mode, String conditionType, String conditionValue) {
    }

    /** Where an entry is saved: its scope, its pool, and the item it had. */
    public record EntryTarget(String scope, String scopeId, int poolIndex, String originalItem) {
    }

    /** The starting form for a new entry. */
    public static final LootForm NEW_ENTRY =
            new LootForm("", "1", "1", "100", OverrideMode.ADD.lower(), NO_CONDITION, "");

    /**
     * The loot list for one mob in one scope: this scope's own entries, the entries it inherits (a biome from
     * its tier, any scope from global), and the game's own drops.
     */
    public static List<LootRow> rowsFor(String scope, String scopeId, String entity,
                                        String variantName, List<GameLootItem> gameLoot) {
        return rowsFor(scope, scopeId, entity, variantName, gameLoot,
                CsvTable.read(Paths.table(Schema.Loot.FILE)), biomeTier(scope, scopeId));
    }

    /** What an inherited base mob row is marked with. */
    public static final String BASE_MOB = "base mob";

    /**
     * As above, over the table's rows.
     *
     * @param biomeTier the biome's tier, for a biome scope, else null
     */
    public static List<LootRow> rowsFor(String scope, String scopeId, String entity, String variantName,
                                        List<GameLootItem> gameLoot, List<Map<String, String>> table,
                                        String biomeTier) {
        String variant = variantName == null ? "" : variantName.strip();
        List<Map<String, String>> chain = chainRows(table, scope, scopeId, entity, variant, biomeTier);
        boolean fromBase = !variant.isEmpty() && chain.isEmpty();
        if (fromBase) {
            chain = chainRows(table, scope, scopeId, entity, "", biomeTier);
        }
        List<LootRow> rows = new ArrayList<>();
        Set<String> denied = new LinkedHashSet<>();
        for (Map<String, String> csv : chain) {
            OverrideMode mode = OverrideMode.parse(
                    csv.getOrDefault(Schema.Loot.MODE, "add").strip());
            if (mode == OverrideMode.DENY) {
                denied.add(csv.getOrDefault(Schema.Loot.ITEM, "").strip());
                continue;
            }
            String rowScope = csv.getOrDefault(Schema.Loot.SCOPE, "global").strip();
            String rowScopeId = csv.getOrDefault(Schema.Loot.SCOPE_ID, "").strip();
            boolean own = scope.equals(rowScope) && scopeId.equals(rowScopeId);
            String inheritedFrom = fromBase ? BASE_MOB : own ? null : "tier".equals(rowScope) ? rowScopeId : "global";
            rows.add(new LootRow(rowScope, rowScopeId, entity, variant, mode,
                    Parse.integer(csv.get(Schema.Loot.POOL_INDEX), 0),
                    Parse.integer(csv.get(Schema.Loot.ENTRY_INDEX), 0),
                    csv.getOrDefault(Schema.Loot.ITEM, "").strip(),
                    Parse.integer(csv.get(Schema.Loot.COUNT_MIN), 1),
                    Parse.integer(csv.get(Schema.Loot.COUNT_MAX), 1),
                    Parse.integer(csv.get(Schema.Loot.CHANCE_PCT), 100),
                    csv.getOrDefault(Schema.Loot.CONDITION_TYPE, "").strip(),
                    csv.getOrDefault(Schema.Loot.CONDITION_VALUE, "").strip(),
                    fromBase || !own, inheritedFrom));
        }
        for (GameLootItem drop : gameLoot) {
            // A denied game drop is gone from the list.
            if (denied.contains(drop.item())) continue;
            rows.add(new LootRow("vanilla", "", entity, variant, OverrideMode.ADD, 0, 0,
                    drop.item(), drop.countMin(), drop.countMax(), 100, "", "",
                    true, "vanilla"));
        }
        return rows;
    }

    /** A biome scope's tier, or null. */
    static String biomeTier(String scope, String scopeId) {
        if (!"biome".equals(scope)) {
            return null;
        }
        BiomeConfig biome = Config.tables().biomes().get(scopeId);
        return biome == null ? null : biome.tier();
    }

    /**
     * One instance's drop rows in its scope chain, denies included: its own scope, a biome's tier, and
     * global, as the runtime layers them.
     */
    static List<Map<String, String>> chainRows(List<Map<String, String>> table, String scope, String scopeId,
                                               String entity, String variant, String biomeTier) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> csv : table) {
            if (!entity.equals(csv.getOrDefault(Schema.Loot.ENTITY, "").strip())
                    || !variant.equals(csv.getOrDefault(Schema.Loot.VARIANT_NAME, "").strip())) {
                continue;
            }
            String rowScope = csv.getOrDefault(Schema.Loot.SCOPE, "global").strip();
            String rowScopeId = csv.getOrDefault(Schema.Loot.SCOPE_ID, "").strip();
            boolean inChain = (scope.equals(rowScope) && scopeId.equals(rowScopeId))
                    || ("biome".equals(scope) && "tier".equals(rowScope) && biomeTier != null
                    && biomeTier.equals(rowScopeId))
                    || (!"global".equals(scope) && "global".equals(rowScope));
            if (inChain) {
                out.add(csv);
            }
        }
        return out;
    }

    /**
     * Makes the base mob's drop set this variant's own in this scope, before the variant's first change of
     * it: a variant runs its own set whole, so one own row would drop every inherited one.
     */
    private static void adoptBaseLoot(WriteReport report, String scope, String scopeId, String entity,
                                       String variant) {
        if (variant.isEmpty()) {
            return;
        }
        adoptBaseLoot(report, scope, scopeId, entity, variant, biomeTier(scope, scopeId), Paths.ROOT);
    }

    /** The same, PATH-ROOTED, with the biome's tier passed in. */
    static void adoptBaseLoot(WriteReport report, String scope, String scopeId, String entity,
                              String variant, String tier, Path root) {
        List<Map<String, String>> table = CsvTable.read(root.resolve(Schema.Loot.FILE));
        if (!chainRows(table, scope, scopeId, entity, variant, tier).isEmpty()) {
            return;
        }
        for (Map<String, String> row : chainRows(table, scope, scopeId, entity, "", tier)) {
            if (!report.ok()) {
                return;
            }
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(Schema.Loot.SCOPE, scope);
            keys.put(Schema.Loot.SCOPE_ID, scopeId);
            keys.put(Schema.Loot.ENTITY, entity);
            keys.put(Schema.Loot.VARIANT_NAME, variant);
            keys.put(Schema.Loot.POOL_INDEX, row.getOrDefault(Schema.Loot.POOL_INDEX, "0").strip());
            keys.put(Schema.Loot.ITEM, row.getOrDefault(Schema.Loot.ITEM, "").strip());
            Map<String, String> values = new LinkedHashMap<>();
            for (String column : List.of(Schema.Loot.ENTRY_INDEX, Schema.Loot.COUNT_MIN,
                    Schema.Loot.COUNT_MAX, Schema.Loot.CHANCE_PCT, Schema.Loot.MODE,
                    Schema.Loot.CONDITION_TYPE, Schema.Loot.CONDITION_VALUE)) {
                values.put(column, row.getOrDefault(column, "").strip());
            }
            report.record("the base mob's loot", TableWriter.upsert(root.resolve(Schema.Loot.FILE), keys, values));
        }
    }

    /** The first pool index this scope's own entries do not use. */
    public static int nextPoolIndex(List<LootRow> rows) {
        int max = -1;
        for (LootRow row : rows) {
            if (!row.inherited() && row.poolIndex() > max) max = row.poolIndex();
        }
        return max + 1;
    }

    /** Where an edit of this row is saved. */
    public static EntryTarget targetFor(LootRow row, String scope, String scopeId,
                                        List<LootRow> rows) {
        if (row != null && BASE_MOB.equals(row.inheritedFrom())) {
            // The base mob's set becomes this variant's own first, the row with it: the edit changes that
            // copy.
            return new EntryTarget(scope, scopeId, row.poolIndex(), row.item());
        }
        if (row == null || row.inherited()) {
            return new EntryTarget(scope, scopeId, nextPoolIndex(rows), null);
        }
        return new EntryTarget(row.scope(), row.scopeId(), row.poolIndex(), row.item());
    }

    /** The editor form for a row. */
    public static LootForm formFor(LootRow row) {
        if (row == null) {
            return NEW_ENTRY;
        }
        return new LootForm(row.item(), String.valueOf(row.countMin()),
                String.valueOf(row.countMax()), String.valueOf(row.chancePct()),
                row.inherited() ? OverrideMode.ADD.lower() : row.mode().lower(),
                row.conditionType().isEmpty() ? NO_CONDITION : row.conditionType(),
                row.conditionValue());
    }

    /** Why these loot numbers cannot be saved, or null when they can. */
    public static String entryProblem(String mode, String countMin, String countMax,
                                      String chancePct) {
        if (OverrideMode.parse(mode == null ? "add" : mode.strip()) == OverrideMode.DENY) {
            return null;
        }
        int min = Parse.integer(countMin, 1);
        int max = Parse.integer(countMax, 1);
        int chance = Parse.integer(chancePct, 100);
        if (min < 0 || max < 0) {
            return "Counts must be 0 or more";
        }
        if (min > max) {
            return "The min count is above the max count";
        }
        if (chance > 100) {
            return "The chance must be 100 or less";
        }
        if (chance <= 0) {
            return "The chance must be above 0, or the entry never drops";
        }
        return null;
    }

    /** Why a condition value cannot be saved, or null when it can. */
    public static String conditionValueProblem(String conditionType, String conditionValue) {
        if (LootCondition.Type.parse(conditionType) != LootCondition.Type.DEATH_CAUSE) {
            return null;
        }
        String v = conditionValue == null ? "" : conditionValue.strip();
        if (v.isEmpty()) {
            return "Pick a cause of death, or a damage type";
        }
        if (LootCondition.isCause(v)) {
            return null;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        List<String> ids = com.strata.game.RegistryQuery.damageTypeIds();
        if (!ids.isEmpty() && !knownDamageType(ids, lower)) {
            return "Not a cause of death or a damage type the game has (pick from the list)";
        }
        return null;
    }

    /** True when a lowercased value is a damage type id, or its bare path. */
    private static boolean knownDamageType(List<String> ids, String lower) {
        for (String id : ids) {
            if (id.equals(lower)) {
                return true;
            }
            int slash = id.lastIndexOf(':');
            if (slash >= 0 && id.substring(slash + 1).equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The death cause's word for the lists: the friendly names asked for for the three causes named, the
     * game's own words for every other.
     */
    public static String deathCauseLabel(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String lower = id.strip().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "any" -> "Any death";
            case "player" -> "Player (any kill)";
            case "player_melee" -> "Player melee";
            case "mob" -> "Mob";
            case "fire" -> "Fire (not sunlight)";
            case "lava" -> "Lava";
            case "sunlight" -> "Sunlight";
            case "drowning" -> "Drowning";
            case "fall" -> "Fall";
            case "explosion" -> "Explosion";
            case "other" -> "Other";
            case "minecraft:on_fire", "on_fire" -> "Burning (any fire)";
            default -> "Damage type: " + Labels.pretty(ResourceLocation.tryParse(lower));
        };
    }

    /** Saves an entry from the editor, then reloads. */
    public static WriteReport save(String entity, String variantName, EntryTarget target,
                                   LootForm form) {
        String item = form.item() == null ? "" : form.item().strip();
        if (item.isEmpty()) {
            return WriteReport.refused("Pick an item first");
        }
        String problem = entryProblem(form.mode(), form.countMin(), form.countMax(), form.chancePct());
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        problem = conditionValueProblem(form.conditionType(), form.conditionValue());
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String variant = variantName == null ? "" : variantName.strip();
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Loot.SCOPE, target.scope());
        keys.put(Schema.Loot.SCOPE_ID, target.scopeId());
        keys.put(Schema.Loot.ENTITY, entity);
        // Part of the key: two variants of one creature in the same scope each own their drops, and neither
        // owns the base mob's.
        keys.put(Schema.Loot.VARIANT_NAME, variant);
        keys.put(Schema.Loot.POOL_INDEX, String.valueOf(target.poolIndex()));
        keys.put(Schema.Loot.ITEM, item);

        String condType = form.conditionType();
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Loot.ENTRY_INDEX, "0");
        values.put(Schema.Loot.COUNT_MIN, form.countMin().strip());
        values.put(Schema.Loot.COUNT_MAX, form.countMax().strip());
        values.put(Schema.Loot.CHANCE_PCT, form.chancePct().strip());
        values.put(Schema.Loot.MODE, OverrideMode.parse(form.mode()).lower());
        values.put(Schema.Loot.CONDITION_TYPE,
                condType == null || NO_CONDITION.equals(condType) ? "" : condType);
        values.put(Schema.Loot.CONDITION_VALUE, form.conditionValue().strip());

        WriteReport report = new WriteReport();
        adoptBaseLoot(report, target.scope(), target.scopeId(), entity, variant);
        if (!report.ok()) return report;
        String originalItem = target.originalItem();
        if (originalItem != null && !originalItem.equals(item)) {
            report.record("the previous loot entry", TableWriter.delete(Schema.Loot.FILE,
                    Map.of(Schema.Loot.SCOPE, target.scope(),
                            Schema.Loot.SCOPE_ID, target.scopeId(),
                            Schema.Loot.ENTITY, entity,
                            Schema.Loot.VARIANT_NAME, variant,
                            Schema.Loot.POOL_INDEX, String.valueOf(target.poolIndex()),
                            Schema.Loot.ITEM, originalItem)));
            if (!report.ok()) return report;
        }
        return Commit.reloadIfOk(report.record("the loot entry",
                TableWriter.upsert(Schema.Loot.FILE, keys, values)));
    }

    /** True when {@link #remove} on this row writes a deny, false when it deletes the row. */
    public static boolean removeDenies(LootRow row) {
        return row.inherited() && !BASE_MOB.equals(row.inheritedFrom());
    }

    /**
     * Removes this scope's own entry, or DENIES a drop that is inherited or that the game supplies, then
     * reloads.
     */
    public static WriteReport remove(String scope, String scopeId, String entity,
                                     String variantName, LootRow row, List<LootRow> rows) {
        return Commit.reloadIfOk(removeInto(new WriteReport(), scope, scopeId, entity, variantName, row, rows));
    }

    /** Removes or denies several drops together: each as {@link #remove} does, then one reload. */
    public static WriteReport removeAll(String scope, String scopeId, String entity,
                                        String variantName, List<LootRow> removing, List<LootRow> rows) {
        if (removing.isEmpty()) {
            return WriteReport.refused("Pick a loot entry first");
        }
        WriteReport report = new WriteReport();
        for (LootRow row : removing) {
            removeInto(report, scope, scopeId, entity, variantName, row, rows);
            if (!report.ok()) {
                return report;
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** {@link #remove}'s write, into a report, with no reload. */
    private static WriteReport removeInto(WriteReport report, String scope, String scopeId, String entity,
                                          String variantName, LootRow row, List<LootRow> rows) {
        String variant = variantName == null ? "" : variantName.strip();
        // A variant's first change to the base mob's set makes the set its own first; a base mob row is then
        // this copy, removed.
        adoptBaseLoot(report, scope, scopeId, entity, variant);
        if (!report.ok()) {
            return report;
        }
        if (BASE_MOB.equals(row.inheritedFrom())) {
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(Schema.Loot.SCOPE, scope);
            keys.put(Schema.Loot.SCOPE_ID, scopeId);
            keys.put(Schema.Loot.ENTITY, entity);
            keys.put(Schema.Loot.VARIANT_NAME, variant);
            keys.put(Schema.Loot.POOL_INDEX, String.valueOf(row.poolIndex()));
            keys.put(Schema.Loot.ITEM, row.item());
            return report.record("the loot", TableWriter.delete(Schema.Loot.FILE, keys));
        }
        if (removeDenies(row)) {
            Map<String, String> keys = new LinkedHashMap<>();
            keys.put(Schema.Loot.SCOPE, scope);
            keys.put(Schema.Loot.SCOPE_ID, scopeId);
            keys.put(Schema.Loot.ENTITY, entity);
            keys.put(Schema.Loot.VARIANT_NAME, variant);
            keys.put(Schema.Loot.POOL_INDEX, String.valueOf(nextPoolIndex(rows)));
            keys.put(Schema.Loot.ITEM, row.item());
            Map<String, String> values = new LinkedHashMap<>();
            values.put(Schema.Loot.MODE, OverrideMode.DENY.lower());
            values.put(Schema.Loot.ENTRY_INDEX, "0");
            // A deny is about the ITEM, not an amount, but the columns are not nullable - zeroes read as
            // "nothing of this".
            values.put(Schema.Loot.COUNT_MIN, "0");
            values.put(Schema.Loot.COUNT_MAX, "0");
            values.put(Schema.Loot.CHANCE_PCT, "0");
            return report.record("the deny", TableWriter.upsert(Schema.Loot.FILE, keys, values));
        }
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Loot.SCOPE, row.scope());
        keys.put(Schema.Loot.SCOPE_ID, row.scopeId());
        keys.put(Schema.Loot.ENTITY, row.entity());
        keys.put(Schema.Loot.VARIANT_NAME, variant);
        keys.put(Schema.Loot.POOL_INDEX, String.valueOf(row.poolIndex()));
        keys.put(Schema.Loot.ENTRY_INDEX, String.valueOf(row.entryIndex()));
        keys.put(Schema.Loot.ITEM, row.item());
        // The row came from the list, so it exists and false means the write failed.
        return report.record("the loot", TableWriter.delete(Schema.Loot.FILE, keys));
    }

    /**
     * Clears every row this mob owns in this scope - its entries AND its denies - so the game's own drops
     * apply again, then reloads.
     */
    public static WriteReport restoreDefaults(String scope, String scopeId, String entity,
                                              String variantName) {
        String variant = variantName == null ? "" : variantName.strip();
        WriteReport report = new WriteReport();
        for (var row : CsvTable.read(Paths.table(Schema.Loot.FILE))) {
            if (!scope.equals(row.getOrDefault(Schema.Loot.SCOPE, "").strip())
                    || !scopeId.equals(row.getOrDefault(Schema.Loot.SCOPE_ID, "").strip())
                    || !entity.equals(row.getOrDefault(Schema.Loot.ENTITY, "").strip())
                    || !variant.equals(row.getOrDefault(Schema.Loot.VARIANT_NAME, "").strip())) {
                continue;
            }
            Map<String, String> keys = new LinkedHashMap<>(row);
            keys.replaceAll((k, v) -> v == null ? "" : v.strip());
            report.record("a loot row", TableWriter.delete(Schema.Loot.FILE, keys));
        }
        return Commit.reloadIfOk(report);
    }
}
