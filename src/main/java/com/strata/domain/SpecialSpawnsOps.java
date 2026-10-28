package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.SpecialSpawners;
import com.strata.runtime.SpecialSpawnsRuntime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the Special mobs screen and the mob editor's Spawn as read and do. */
public final class SpecialSpawnsOps {

    private SpecialSpawnsOps() {
    }

    private static Map<String, String> table() {
        return SpecialSpawnsRuntime.index(CsvTable.read(Paths.table(Schema.SpecialSpawns.FILE)));
    }

    /** "" for Global, else "tier" or "biome". */
    private static String scopeOf(String scope) {
        String s = scope == null ? "" : scope.strip();
        return s.isEmpty() || "global".equals(s) ? "" : s;
    }

    /**
     * One value as the screen lists it.
     *
     * @param own this scope's value, or ""
     * @param effective what applies here: its own, else the wider scope's, else the game's
     * @param from where the effective value comes from: "yours", "tier", "Global", or "game"
     */
    public record Value(SpecialSpawners.Setting setting, String own, String effective, String from) {
    }

    /** The values a scope edits: at Global every one, at a tier or biome all but the clock's (Global only). */
    public static List<Value> values(String scope, String scopeId) {
        return values(table(), scopeOf(scope), scopeId == null ? "" : scopeId.strip(),
                "biome".equals(scopeOf(scope)) ? tierOf(scopeId) : "");
    }

    private static String tierOf(String biome) {
        String t = com.strata.data.Config.resolve(biome).tier();
        return t == null ? "" : t;
    }

    /** The values, from the rows. */
    public static List<Value> values(Map<String, String> rows, String scope, String scopeId, String biomeTier) {
        List<Value> out = new ArrayList<>();
        for (SpecialSpawners.Setting s : SpecialSpawners.SETTINGS) {
            if (!scope.isEmpty() && s.globalOnly()) {
                continue;
            }
            String own = rows.getOrDefault(scope + "|" + scopeId + "|" + s.spawner() + "|" + s.key(), "");
            String tierValue = "biome".equals(scope) && !biomeTier.isEmpty()
                    ? rows.getOrDefault("tier|" + biomeTier + "|" + s.spawner() + "|" + s.key(), "") : "";
            String global = scope.isEmpty() ? "" : rows.getOrDefault("||" + s.spawner() + "|" + s.key(), "");
            String effective = !own.isEmpty() ? own : !tierValue.isEmpty() ? tierValue : !global.isEmpty() ? global : s.game();
            String from = !own.isEmpty() ? "yours" : !tierValue.isEmpty() ? "tier" : !global.isEmpty() ? "Global" : "game";
            out.add(new Value(s, own, effective, from));
        }
        return out;
    }

    /**
     * A spawner's breakdown for the Special mobs list: On or Off, then its first values, "Name value";
     * "(yours)" when this scope sets one.
     */
    public static String summary(String spawner, List<Value> values) {
        List<Value> own = values.stream().filter(v -> v.setting().spawner().equals(spawner)).toList();
        StringBuilder sb = new StringBuilder();
        Value enabled = own.stream().filter(v -> v.setting().key().equals("enabled")).findFirst().orElse(null);
        if (enabled != null) {
            sb.append(enabled.effective());
        }
        int shownCount = 0;
        for (Value v : own) {
            if (v.setting().key().equals("enabled") || shownCount >= 3) {
                continue;
            }
            String word = shown(v.setting(), v.effective());
            sb.append(sb.isEmpty() ? "" : "  ").append(v.setting().name()).append(" ")
                    .append(word.isEmpty() ? "default" : word);
            shownCount++;
        }
        if (own.stream().anyMatch(v -> v.from().equals("yours"))) {
            sb.append("  (yours)");
        }
        return sb.toString();
    }

    /** The spawners the list shows, in order: every one but the top switch. */
    public static List<String> spawners() {
        return SpecialSpawners.SPAWNERS.keySet().stream().filter(s -> !s.equals(SpecialSpawners.SPECIAL)).toList();
    }

    /** The top switch's value at a scope. */
    public static Value mainSwitch(List<Value> values) {
        return values.stream().filter(v -> v.setting().spawner().equals(SpecialSpawners.SPECIAL)).findFirst().orElse(null);
    }

    /** A value in words: On, Off, a whole number, a mob's id, or Default. */
    public static String shown(SpecialSpawners.Setting s, String value) {
        return switch (s.kind()) {
            case VILLAGER_TYPE, PROFESSION -> TradesOps.word(value);
            default -> value;
        };
    }

    /** The list of ids a picked value chooses from (a mob is picked from the registry: null). */
    public static List<String> choices(SpecialSpawners.Setting s) {
        return switch (s.kind()) {
            case VILLAGER_TYPE -> TradesOps.choices(TradesOps.villagerTypes());
            case PROFESSION -> TradesOps.choices(TradesOps.professions());
            default -> null;
        };
    }

    /**
     * What a setting's value is, for the sheet's control: a switch is On or Off, a mob is picked from the
     * entity registry, a villager type or a profession is one of the game's (shown in words, Default first:
     * {@link #idOf} gives the id the save takes), a count is a whole number, a chance a whole number from 0
     * to 100.
     */
    public static com.strata.model.ValueType typeOf(SpecialSpawners.Setting s) {
        return switch (s.kind()) {
            case SWITCH -> com.strata.model.ValueType.choice(List.of(SpecialSpawners.ON, SpecialSpawners.OFF));
            case MOB -> com.strata.model.ValueType.registry(com.strata.model.ValueType.Registry.ENTITY);
            case VILLAGER_TYPE, PROFESSION -> com.strata.model.ValueType.choice(choices(s));
            case WHOLE -> com.strata.model.ValueType.whole(0, Integer.MAX_VALUE);
            case PERCENT -> com.strata.model.ValueType.whole(0, 100);
        };
    }

    /** True for a value picked from a list, not typed. */
    public static boolean picked(SpecialSpawners.Setting s) {
        return s.kind() == SpecialSpawners.Kind.MOB || s.kind() == SpecialSpawners.Kind.VILLAGER_TYPE
                || s.kind() == SpecialSpawners.Kind.PROFESSION;
    }

    /** The id a picked word names (Default is ""), for a villager type or a profession. */
    public static String idOf(SpecialSpawners.Setting s, String word) {
        return switch (s.kind()) {
            case VILLAGER_TYPE -> TradesOps.idOf(word, TradesOps.villagerTypes());
            case PROFESSION -> TradesOps.idOf(word, TradesOps.professions());
            default -> word;
        };
    }

    private static Map<String, String> keys(String scope, String scopeId, String spawner, String setting) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.SpecialSpawns.SCOPE, scopeOf(scope));
        keys.put(Schema.SpecialSpawns.SCOPE_ID, scopeOf(scope).isEmpty() ? "" : scopeId.strip());
        keys.put(Schema.SpecialSpawns.SPAWNER, spawner);
        keys.put(Schema.SpecialSpawns.SETTING, setting);
        return keys;
    }

    /** Sets a value at this scope. */
    public static WriteReport save(String scope, String scopeId, SpecialSpawners.Setting s, String typed) {
        String problem = SpecialSpawners.problem(s, typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        if (!scopeOf(scope).isEmpty() && s.globalOnly()) {
            return WriteReport.refused("This value is the spawner's clock: set it on Global");
        }
        String v = typed.strip();
        if (s.kind() == SpecialSpawners.Kind.SWITCH) {
            v = SpecialSpawners.on(v) ? SpecialSpawners.ON : SpecialSpawners.OFF;
        }
        switchOn();
        return Commit.reloadIfOk(new WriteReport().record("the value", TableWriter.upsert(Schema.SpecialSpawns.FILE,
                keys(scope, scopeId, s.spawner(), s.key()), Map.of(Schema.SpecialSpawns.VALUE, v))));
    }

    /** An edit here needs Strata's spawners (Options > Special spawners), which ship Off: it goes On. */
    private static void switchOn() {
        OptionsOps.turnOn(OptionsOps.SPECIAL_SPAWNERS, com.strata.data.StrataConfig.ENABLE_SPECIAL_SPAWNERS);
    }

    /** A switch the other way at this scope: the double-click on a switch row. */
    public static WriteReport toggle(String scope, String scopeId, Value v) {
        return save(scope, scopeId, v.setting(), SpecialSpawners.on(v.effective()) ? SpecialSpawners.OFF : SpecialSpawners.ON);
    }

    /** Takes this scope's value away: the wider scope's, or the game's, applies again. */
    public static WriteReport restore(String scope, String scopeId, SpecialSpawners.Setting s) {
        return Commit.reloadIfOk(new WriteReport().cascaded("your value", TableWriter.delete(Schema.SpecialSpawns.FILE,
                keys(scope, scopeId, s.spawner(), s.key()))));
    }

    /** Takes every value of this scope away (the mobs' Spawn as stays: it is the mob editor's). */
    public static WriteReport restoreAll(String scope, String scopeId) {
        WriteReport report = new WriteReport();
        for (SpecialSpawners.Setting s : SpecialSpawners.SETTINGS) {
            report.cascaded("a value", TableWriter.delete(Schema.SpecialSpawns.FILE,
                    keys(scope, scopeId, s.spawner(), s.key())));
        }
        return Commit.reloadIfOk(report);
    }

    // ---- the scope's switch on the tier and biome editors ----

    /** The tier or biome editor's switch text: "Special spawns here: On" or "...: Off". */
    public static String switchText(String scope, String scopeId) {
        Value main = mainSwitch(values(scope, scopeId));
        return "Special spawns here: " + (main == null ? SpecialSpawners.ON : main.effective());
    }

    /** Turns this scope's Special mob spawns switch over. */
    public static WriteReport toggleSwitch(String scope, String scopeId) {
        Value main = mainSwitch(values(scope, scopeId));
        return main == null ? WriteReport.refused("No Special mob spawns switch here") : toggle(scope, scopeId, main);
    }

    // ---- a mob's Spawn as (the mob editor, Global) ----

    /** True for a mob set to Spawn as Special (its own value, else the game's way). */
    public static boolean special(String entity) {
        return SpecialSpawners.special(table(), entity);
    }

    /** Every mob set to Spawn as Special: the game's special ones, and the ones set so. */
    public static java.util.Set<String> specialMobs() {
        return specialMobs(table());
    }

    /** The Special mobs, from the rows: set to Spawn as Special, and flagged Special. */
    public static java.util.Set<String> specialMobs(Map<String, String> rows) {
        java.util.Set<String> out = new java.util.TreeSet<>(SpecialSpawners.SPECIAL_BY_DEFAULT);
        String prefix = "||" + SpecialSpawners.MOBS + "|";
        rows.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                if ("Special".equalsIgnoreCase(value)) {
                    out.add(key.substring(prefix.length()));
                } else {
                    out.remove(key.substring(prefix.length()));
                }
            }
        });
        out.removeIf(entity -> !SpecialSpawners.flagged(rows, entity));
        return out;
    }

    // ---- a mob's Special flag (the Category dropdown's Special) ----

    /** True for a mob flagged Special: it sits in the mob list's Special group. */
    public static boolean flagged(String entity) {
        return SpecialSpawners.flagged(table(), entity);
    }

    /** Every mob flagged Special. */
    public static java.util.Set<String> flaggedMobs() {
        return flaggedMobs(table(), allMobs());
    }

    /** The mobs flagged Special among these, from the rows. */
    public static java.util.Set<String> flaggedMobs(Map<String, String> rows, java.util.Collection<String> mobs) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String entity : mobs) {
            if (SpecialSpawners.flagged(rows, entity)) out.add(entity);
        }
        return out;
    }

    /** The mobs the flag can name: every flagged-by-default mob and every mob with a flag row. */
    private static java.util.Set<String> allMobs() {
        java.util.Set<String> out = new java.util.TreeSet<>(SpecialSpawners.FLAGGED_BY_DEFAULT);
        String prefix = "||" + SpecialSpawners.FLAGGED + "|";
        table().keySet().forEach(k -> {
            if (k.startsWith(prefix)) out.add(k.substring(prefix.length()));
        });
        return out;
    }

    /** Flags a mob Special, or takes the flag away (Global, no reload). */
    public static WriteReport setFlagged(String entity, boolean flagged) {
        WriteReport report = new WriteReport();
        if (!flagged && special(entity)) {
            report = setSpawnAs(entity, "Normal");
            if (!report.ok()) return report;
        }
        boolean byDefault = SpecialSpawners.FLAGGED_BY_DEFAULT.contains(entity);
        Map<String, String> k = keys("", "", SpecialSpawners.FLAGGED, entity);
        if (flagged != byDefault) {
            switchOn();
        }
        return flagged == byDefault
                ? report.cascaded("the mob's Special flag", TableWriter.delete(Schema.SpecialSpawns.FILE, k))
                : report.record("the mob's Special flag", TableWriter.upsert(Schema.SpecialSpawns.FILE, k,
                        Map.of(Schema.SpecialSpawns.VALUE, String.valueOf(flagged))));
    }

    /** The mob editor's word: Normal or Special. */
    public static String spawnAsWord(String entity) {
        return special(entity) ? "Special" : "Normal";
    }

    /** A variant's word: its own Spawn as, else its base mob's. */
    public static String spawnAsWord(String entity, String variant) {
        return special(entity, variant) ? "Special" : "Normal";
    }

    /** True when a variant spawns as Special (its own setting, else its base mob's). */
    public static boolean special(String entity, String variant) {
        return SpecialSpawners.special(table(), entity, variant);
    }

    /** Sets a VARIANT'S Spawn as, on Global. */
    public static WriteReport setSpawnAs(String entity, String variant, String word) {
        if (variant == null || variant.isBlank()) {
            return setSpawnAs(entity, word);
        }
        if (!SpecialSpawners.SPAWN_AS.contains(word)) {
            return WriteReport.refused("Pick Normal or Special");
        }
        switchOn();
        WriteReport report = new WriteReport().record("the variant's Spawn as", TableWriter.upsert(
                Schema.SpecialSpawns.FILE,
                keys("", "", SpecialSpawners.MOBS, entity + SpecialSpawners.VARIANT_SEP + variant.strip()),
                Map.of(Schema.SpecialSpawns.VALUE, word)));
        if (report.ok() && "Normal".equals(word) && SpecialSpawners.PHANTOM.equals(entity)) {
            for (String[] rule : PHANTOM_NORMAL_RULES) {
                Map<String, String> k = new LinkedHashMap<>();
                k.put(Schema.SpawnRules.SCOPE, "global");
                k.put(Schema.SpawnRules.SCOPE_ID, "");
                k.put(Schema.SpawnRules.ENTITY, entity);
                k.put(Schema.SpawnRules.VARIANT_NAME, variant.strip());
                k.put(Schema.SpawnRules.RULE_TYPE, rule[0]);
                report.record("the variant's " + rule[0] + " rule", TableWriter.upsert(Schema.SpawnRules.FILE, k,
                        Map.of(Schema.SpawnRules.VALUE, rule[1])));
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** What Spawn as does, for the dropdown's tooltip. */
    public static final String SPAWN_AS_HELP = "Special: a tier or biome can turn its spawns off (Spawn special"
            + " mobs). Normal: it spawns as any mob; a phantom, wandering trader, or cat then comes only from the"
            + " biome spawn lists, not its special spawner. For every scope (Global).";

    // ---- the game's own spawn conditions ----

    /** The game's spawn conditions as spawn rule rows (scope, scope id, entity, variant, type, value). */
    public static List<String[]> gameRules(int phantomDays, int patrolDay) {
        return List.of(
                new String[]{"global", "", SpecialSpawners.PHANTOM, "", "time_since_rest", Math.max(0, phantomDays) + "+"},
                new String[]{"global", "", SpecialSpawners.PILLAGER, "", "world_day", Math.max(0, patrolDay) + "+"},
                new String[]{"global", "", SpecialSpawners.PILLAGER, "", "time", "daylight"});
    }

    /** The file that names each game condition already seeded, so one the user removes stays removed. */
    public static final String SEEDED_FILE = "seeded-rules.txt";

    /**
     * Seeds the game's spawn conditions into spawn_rules.csv, once each (SEED FROM THE GAME: a value is
     * seeded once, and an existing row is never overwritten).
     */
    public static WriteReport seedGameRules(int phantomDays, int patrolDay) {
        WriteReport report = new WriteReport();
        java.nio.file.Path marker = Paths.ROOT.resolve(SEEDED_FILE);
        java.util.Set<String> seeded = new java.util.HashSet<>();
        try {
            if (java.nio.file.Files.isRegularFile(marker)) {
                java.nio.file.Files.readAllLines(marker).forEach(s -> seeded.add(s.strip()));
            }
        } catch (java.io.IOException e) {
            return report.record("the seeded rules list", false);
        }
        List<String> added = new ArrayList<>();
        for (String[] r : gameRules(phantomDays, patrolDay)) {
            String id = r[0] + "|" + r[2] + "|" + r[4];
            if (seeded.contains(id)) {
                continue;
            }
            Map<String, String> k = new LinkedHashMap<>();
            k.put(Schema.SpawnRules.SCOPE, r[0]);
            k.put(Schema.SpawnRules.SCOPE_ID, r[1]);
            k.put(Schema.SpawnRules.ENTITY, r[2]);
            k.put(Schema.SpawnRules.VARIANT_NAME, r[3]);
            k.put(Schema.SpawnRules.RULE_TYPE, r[4]);
            boolean has = CsvTable.read(Paths.table(Schema.SpawnRules.FILE)).stream().anyMatch(row -> k.entrySet()
                    .stream().allMatch(e -> e.getValue().equals(row.getOrDefault(e.getKey(), "").strip())));
            if (!has) {
                report.record("the game's spawn rule", TableWriter.upsert(Schema.SpawnRules.FILE, k,
                        Map.of(Schema.SpawnRules.VALUE, r[5])));
            }
            added.add(id);
        }
        if (!added.isEmpty() && report.ok()) {
            try {
                java.nio.file.Files.createDirectories(marker.getParent());
                java.nio.file.Files.write(marker, added, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException e) {
                report.record("the seeded rules list", false);
            }
        }
        return report;
    }

    /** Sets a mob's Spawn as, for every scope. */
    public static WriteReport setSpawnAs(String entity, String word) {
        if (!SpecialSpawners.SPAWN_AS.contains(word)) {
            return WriteReport.refused("Pick Normal or Special");
        }
        switchOn();
        WriteReport report = new WriteReport().record("the mob's Spawn as", TableWriter.upsert(
                Schema.SpecialSpawns.FILE, keys("", "", SpecialSpawners.MOBS, entity),
                Map.of(Schema.SpecialSpawns.VALUE, word)));
        if (report.ok() && "Normal".equals(word) && SpecialSpawners.PHANTOM.equals(entity)) {
            // The game's spawn check for the phantom is only a block to stand on (no restriction on place or
            // light: it only ever comes from its spawner), so on a list it would spawn in daylight and in
            // caves.
            for (String[] rule : PHANTOM_NORMAL_RULES) {
                if (hasRule(entity, rule[0])) {
                    continue;
                }
                Map<String, String> k = new LinkedHashMap<>();
                k.put(Schema.SpawnRules.SCOPE, "global");
                k.put(Schema.SpawnRules.SCOPE_ID, "");
                k.put(Schema.SpawnRules.ENTITY, entity);
                k.put(Schema.SpawnRules.VARIANT_NAME, "");
                k.put(Schema.SpawnRules.RULE_TYPE, rule[0]);
                report.record("the phantom's " + rule[0] + " rule", TableWriter.upsert(Schema.SpawnRules.FILE, k,
                        Map.of(Schema.SpawnRules.VALUE, rule[1])));
            }
        }
        return Commit.reloadIfOk(report);
    }

    /** The rules a phantom set to Normal gets, when it has none of that type: its spawner's night and open sky. */
    public static final List<String[]> PHANTOM_NORMAL_RULES = List.of(new String[]{"time", "dark"},
            new String[]{"surface", "surface"});

    private static boolean hasRule(String entity, String type) {
        for (Map<String, String> r : CsvTable.read(Paths.table(Schema.SpawnRules.FILE))) {
            if ("global".equals(r.getOrDefault(Schema.SpawnRules.SCOPE, "").strip())
                    && entity.equals(r.getOrDefault(Schema.SpawnRules.ENTITY, "").strip())
                    && type.equals(r.getOrDefault(Schema.SpawnRules.RULE_TYPE, "").strip())) {
                return true;
            }
        }
        return false;
    }
}
