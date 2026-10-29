package com.strata.data;

import com.strata.SelfTestHarness;
import com.strata.domain.AssetRules;
import com.strata.domain.AssetSync;
import com.strata.model.AbilityField;
import com.strata.model.AbilityGuide;
import com.strata.model.EffectClass;
import com.strata.model.LootCondition;
import com.strata.model.Resolution;
import com.strata.model.Resolver;
import com.strata.model.Spawn;
import com.strata.model.SpawnCategory;
import com.strata.model.SpawnRule;
import com.strata.model.Source;
import com.strata.model.Variant;
import com.strata.runtime.VariantHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Headless data-flow tests: prove persistence ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ runtime wiring by
 * driving {@link Tables#loadAll(Path)} against a real temp-directory CSV set, and table mutation helpers
 * ({@link TableWriter}) against the same files.
 */
public final class DataFlowSelfTest {

    private DataFlowSelfTest() {}

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];
        try {
            Path dir = Files.createTempDirectory("strata-dataflow");
            try {
                runTests(dir, lines, tally);
            } finally {
                deleteRec(dir);
            }
        } catch (Exception e) {
            lines.add("ERROR setup ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â " + e);
            tally[1]++;
        }
        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

        /** Shared by more than one part of runTests. */
        private static Tables.Loaded loaded;

        /** Shared by more than one part of runTests. */
        private static java.util.Map<String, String> index;

        /** Shared by more than one part of runTests. */
        private static Resolution plains;

        /** Shared by more than one part of runTests. */
        private static List<Variant> candidates;

    private static void runTests(Path dir, List<String> lines, int[] tally) throws Exception {
        runTestsPart01(dir, lines, tally);
        runTestsPart02(dir, lines, tally);
        runTestsPart03(dir, lines, tally);
        runTestsPart04(dir, lines, tally);
        runTestsPart05(dir, lines, tally);
        runTestsPart06(dir, lines, tally);
        runTestsPart07(dir, lines, tally);
        runTestsPart08(dir, lines, tally);
        runTestsPart09(dir, lines, tally);
        runTestsPart10(dir, lines, tally);
        runTestsPart11(dir, lines, tally);
        runTestsPart12(dir, lines, tally);
        runTestsPart13(dir, lines, tally);
        runTestsPart14(dir, lines, tally);
    }

    private static void runTestsPart01(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "FA3 the Behaviors menu: tidy groups, who uses each, and what changed", () -> {
            var fam = (java.util.function.BiFunction<String, List<String>, com.strata.domain.GoalFamilies.Family>)
                    (name, path) -> new com.strata.domain.GoalFamilies.Family(name, path.isEmpty() ? "Other"
                            : path.getLast(), path, List.of("minecraft:" + name), List.of("h|1|chain|FloatGoal|"));
            List<com.strata.domain.GoalFamilies.Family> families = List.of(
                    fam.apply("pillager", List.of("Pathfinder Mob", "Monster", "Patrolling Monster", "Raider", "Illager")),
                    fam.apply("vindicator", List.of("Pathfinder Mob", "Monster", "Patrolling Monster", "Raider",
                            "Illager")),
                    fam.apply("witch", List.of("Pathfinder Mob", "Monster", "Patrolling Monster", "Raider")),
                    fam.apply("creeper", List.of("Pathfinder Mob", "Monster")),
                    fam.apply("cow", List.of("Pathfinder Mob", "Animal")),
                    fam.apply("pig", List.of("Pathfinder Mob", "Animal")));
            var paths = com.strata.domain.GoalFamilies.displayPaths(families);
            boolean tidy = paths.get("pillager").equals(List.of("Monster", "Raider", "Illager"))
                    && paths.get("witch").equals(List.of("Monster", "Raider"))
                    && paths.get("creeper").equals(List.of("Monster"))
                    && paths.get("cow").equals(List.of("Animal"));
            var gameRows = com.strata.domain.GoalFamilies.rows(families.get(0));
            var edited = List.of(gameRows.get(0).withPriority(5));
            Map<String, List<com.strata.model.GoalRow>> library = new java.util.LinkedHashMap<>();
            library.put("pillager", gameRows);
            library.put("cow", edited);

            library.put("my_own", gameRows);
            var rows = com.strata.domain.GoalsOps.goalSetRows(library, families,
                    Map.of("pillager", List.of("big_pillager in t1"), "my_own", List.of("minecraft:pig")),
                    com.strata.model.Attacks.NONE);
            java.util.Map<String, com.strata.domain.GoalsOps.GoalSetRow> byName = new java.util.HashMap<>();
            rows.forEach(r -> byName.put(r.name(), r));
            var pillager = byName.get("pillager");
            boolean described = pillager.kind() == com.strata.domain.GoalsOps.Kind.FAMILY
                    && pillager.users().equals(List.of("minecraft:pillager", "big_pillager in t1"))
                    && pillager.path().equals(List.of("Monster", "Raider", "Illager")) && !pillager.changed()
                    && "no".equals(pillager.fights())
                    && byName.get("cow").changed()

                    && byName.get("my_own").kind() == com.strata.domain.GoalsOps.Kind.OWN
                    && byName.get("my_own").users().equals(List.of("minecraft:pig"))
                    && rows.getFirst().kind() == com.strata.domain.GoalsOps.Kind.FAMILY
                    && rows.getLast().kind() != com.strata.domain.GoalsOps.Kind.FAMILY;
            if (!(tidy && described)) {
                lines.add("    FA3 paths=" + paths + " rows=" + rows);
            }
            return tidy && described;
        });


        SelfTestHarness.check(lines, tally,
                "FA2 family archetypes: seeded once, lossless, and applied only once edited", () -> {
            String hunt = "t|2|chain|NearestAttackableTargetGoal|target=minecraft:player;interval=10;must_see=true";
            String walk = "h|7|chain|WaterAvoidingRandomStrollGoal|speed=1.0";
            String own = "h|3|chain|net.minecraft.world.entity.monster.Zombie$ZombieAttackGoal|";
            List<GameLayer.Row> mobs = List.of(
                    chain("minecraft:zombie", "Zombie>Monster>PathfinderMob>Mob"),
                    chain("minecraft:husk", "Husk>Zombie>Monster>PathfinderMob>Mob"),
                    chain("minecraft:creeper", "Creeper>Monster>PathfinderMob>Mob"));
            List<GameLayer.Row> goals = List.of(
                    goal("minecraft:zombie", 0, hunt), goal("minecraft:zombie", 1, own),
                    goal("minecraft:zombie", 2, walk),
                    goal("minecraft:husk", 0, hunt), goal("minecraft:husk", 1, own), goal("minecraft:husk", 2, walk),
                    goal("minecraft:creeper", 0, walk));
            var families = com.strata.domain.GoalFamilies.families(goals, mobs);
            var zombie = families.stream().filter(f -> f.name().equals("zombie")).findFirst().orElseThrow();
            var zombieRows = com.strata.domain.GoalFamilies.rows(zombie);
            var missing = com.strata.domain.GoalFamilies.missing(families, List.of("zombie"));
            boolean once = !missing.containsKey("zombie") && missing.containsKey("creeper");
            boolean lossless = GoalSetTable.fromRows(GoalSetTable.toRows("zombie", zombieRows)).get("zombie")
                    .equals(zombieRows) && zombieRows.stream().anyMatch(com.strata.model.GoalRow::game);
            com.strata.runtime.GoalsRuntime.useFamilies(families);
            com.strata.runtime.GoalsRuntime.useGoalSets(Map.of("zombie", zombieRows));
            boolean untouched = com.strata.runtime.GoalsRuntime.familyList("minecraft:husk") == null;
            List<com.strata.model.GoalRow> edited = new ArrayList<>(zombieRows);
            edited.set(0, edited.get(0).withPriority(1));
            com.strata.runtime.GoalsRuntime.useGoalSets(Map.of("zombie", List.copyOf(edited)));
            boolean applied = edited.equals(com.strata.runtime.GoalsRuntime.familyList("minecraft:husk"))
                    && com.strata.runtime.GoalsRuntime.familyList("minecraft:villager") == null
                    && com.strata.runtime.GoalsRuntime.pick(Map.of(), Map.of(com.strata.runtime.ScopeIndex.key("", "",
                            "husk_default"), com.strata.model.GoalSets.NONE), Map.of(), "husk_default", "",
                            com.strata.runtime.GoalsRuntime.layers("", "")).rows() == null
                    && edited.equals(com.strata.runtime.GoalsRuntime.listFor("husk_default", "", "", "",
                            "minecraft:husk"));
            com.strata.runtime.GoalsRuntime.useGoalSets(Map.of());
            com.strata.runtime.GoalsRuntime.useFamilies(List.of());
            Path table = dir.resolve("append_probe.csv");
            Files.writeString(table, "# a note\na,b,c\n1,2,3\n");
            boolean appended = TableWriter.appendRows(table, List.of(Map.of("a", "4", "c", "6"), Map.of("b", "8")))
                    && Files.readAllLines(table).equals(List.of("# a note", "a,b,c", "1,2,3", "4,,6", ",8,"));
            if (!(once && lossless && untouched && applied && appended)) {
                lines.add("    FA2 once=" + once + " lossless=" + lossless + " untouched=" + untouched
                        + " applied=" + applied + " appended=" + appended);
            }
            return once && lossless && untouched && applied && appended;
        });


        SelfTestHarness.check(lines, tally,
                "FA1 the mob families: the same AI together, named and headed by the classes they share", () -> {
            String walk = "h|6|chain|WaterAvoidingRandomStrollGoal|speed=1.0";
            String hunt = "t|2|chain|NearestAttackableTargetGoal|target=minecraft:player";
            List<GameLayer.Row> mobs = List.of(
                    chain("minecraft:zombie", "Zombie>Monster>PathfinderMob>Mob"),
                    chain("minecraft:husk", "Husk>Zombie>Monster>PathfinderMob>Mob"),
                    chain("minecraft:drowned", "Drowned>Zombie>Monster>PathfinderMob>Mob"),
                    chain("minecraft:pillager", "Pillager>AbstractIllager>Raider>Monster>PathfinderMob>Mob"),
                    chain("minecraft:vindicator", "Vindicator>AbstractIllager>Raider>Monster>PathfinderMob>Mob"),
                    chain("minecraft:creeper", "Creeper>Monster>PathfinderMob>Mob"),
                    chain("minecraft:cow", "Cow>Animal>PathfinderMob>Mob"),
                    chain("minecraft:horse", "Horse>AbstractHorse>Animal>PathfinderMob>Mob"),
                    chain("minecraft:donkey", "Donkey>AbstractChestedHorse>AbstractHorse>Animal>PathfinderMob>Mob"),
                    chain("minecraft:mule", "Mule>AbstractChestedHorse>AbstractHorse>Animal>PathfinderMob>Mob"),
                    chain("minecraft:skeleton_horse", "SkeletonHorse>AbstractHorse>Animal>PathfinderMob>Mob"),
                    chain("minecraft:zombie_horse", "ZombieHorse>AbstractHorse>Animal>PathfinderMob>Mob"),
                    chain("minecraft:guardian", "Guardian>Monster>PathfinderMob>Mob"),
                    chain("minecraft:villager", "Villager>AbstractVillager>AgeableMob>PathfinderMob>Mob"));
            List<GameLayer.Row> goals = new ArrayList<>(List.of(
                    goal("minecraft:zombie", 0, hunt), goal("minecraft:zombie", 1, walk),
                    goal("minecraft:husk", 0, hunt), goal("minecraft:husk", 1, walk),
                    goal("minecraft:drowned", 0, hunt), goal("minecraft:drowned", 1, "h|7|chain|FloatGoal|"),
                    goal("minecraft:pillager", 0, "h|4|chain|RangedCrossbowAttackGoal|speed=1.0"),
                    goal("minecraft:vindicator", 0, "h|4|chain|MeleeAttackGoal|speed=1.0"),
                    goal("minecraft:creeper", 0, "h|2|chain|SwellGoal|"),
                    goal("minecraft:cow", 0, "h|1|chain|PanicGoal|speed=2.0"),
                    goal("minecraft:horse", 0, "h|1|chain|PanicGoal|speed=1.2"),
                    goal("minecraft:donkey", 0, "h|1|chain|PanicGoal|speed=1.3"),
                    goal("minecraft:mule", 0, "h|1|chain|PanicGoal|speed=1.3"),
                    goal("minecraft:skeleton_horse", 0, "h|1|chain|FloatGoal|"),
                    goal("minecraft:zombie_horse", 0, "h|1|chain|FloatGoal|"),
                    goal("minecraft:guardian", 0, "h|4|chain|RandomStrollGoal|speed=1.0")));
            List<com.strata.domain.GoalFamilies.Family> families = com.strata.domain.GoalFamilies.families(goals, mobs);
            java.util.Map<String, com.strata.domain.GoalFamilies.Family> byName = new java.util.HashMap<>();
            families.forEach(f -> byName.put(f.name(), f));
            var zombie = byName.get("zombie");
            var pillager = byName.get("pillager");
            boolean ok = families.size() == 10
                    && byName.containsKey("guardian") && !byName.containsKey("guardian_mob")
                    && zombie != null && zombie.mobs().equals(List.of("minecraft:husk", "minecraft:zombie"))
                    && zombie.goals().equals(List.of(hunt, walk)) && zombie.heading().equals("Zombie")
                    && zombie.path().equals(List.of("Monster", "Zombie"))
                    && byName.containsKey("drowned") && byName.get("drowned").heading().equals("Zombie")
                    && pillager != null && pillager.path().equals(List.of("Monster", "Raider", "Illager"))
                    && byName.containsKey("vindicator") && byName.get("vindicator").heading().equals("Illager")
                    && byName.containsKey("creeper") && byName.get("creeper").heading().equals("Monster")
                    && byName.containsKey("cow") && byName.get("cow").path().equals(List.of("Animal"))
                    && byName.containsKey("horse") && byName.get("horse").mobs().equals(List.of("minecraft:horse"))
                    && byName.containsKey("chested_horse")
                    && byName.containsKey("skeleton_horse")
                    && byName.get("skeleton_horse").mobs().equals(List.of("minecraft:skeleton_horse",
                            "minecraft:zombie_horse"))
                    && !byName.containsKey("villager")
                    && com.strata.domain.GoalFamilies.snake("AbstractSkeleton").equals("skeleton")
                    && com.strata.domain.GoalFamilies.snake("ZombieVillager").equals("zombie_villager");
            if (!ok) {
                families.forEach(f -> lines.add("    FA1 " + f));
            }
            return ok;
        });

        write(dir, "mob_spawns.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                        + "global,,minecraft:skeleton,10,1,2,true,1.5,false,,\n"
                        + "tier,TEMP,minecraft:husk,5,1,1,true,,false,,\n"
                        + "tier,TEMP,minecraft:husk,3,1,1,true,,true,husk_frosty,replace\n"
                        + "tier,TEMP2,minecraft:husk,5,1,1,true,,false,,\n"
                        + "global,,minecraft:zombie,4,1,1,true,,false,,\n"
                        + "biome,minecraft:plains,minecraft:zombie,9,1,1,true,,false,,\n");
        write(dir, "tiers.csv",
                "name,level,spawn_mode,coefficient\nTEMP,2,add,1.0\nTEMP2,5,add,1.0\n");
        write(dir, "tier_biomes.csv", "tier,biome\nTEMP,minecraft:plains\n");
        write(dir, "spawn_rules.csv",
                "scope,scope_id,entity,rule_type,value\n"
                        + "tier,TEMP,minecraft:husk,time,night\n"
                        + "global,,minecraft:skeleton,y_level,10-40\n"
                        + "biome,minecraft:plains,minecraft:zombie,weather,clear\n");
        write(dir, "ores.csv",
                "scope,scope_id,ore_id,enabled,frequency_pct\n"
                        + "tier,TEMP,iron,true,50\n"
                        + "biome,minecraft:plains,copper,true,30\n"
                        + "biome,minecraft:plains,gold,false,100\n");
        write(dir, "loot.csv",
                "scope,scope_id,entity,mode,pool_index,entry_index,item,count_min,count_max,chance_pct,condition_type,condition_value\n"
                        + "tier,TEMP,minecraft:husk,add,0,0,diamond,1,1,50,,\n"
                        + "tier,TEMP2,minecraft:husk,add,0,0,emerald,1,1,50,,\n");

        loaded = Tables.loadAll(dir);
        SelfTestHarness.check(lines, tally, "12a global spawn loaded at global scope", () ->
                loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().anyMatch(s -> s.entity().equals("minecraft:skeleton")));
        SelfTestHarness.check(lines, tally, "12b global-spawn conditions attach to the spawn", () ->
                loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().filter(s -> s.entity().equals("minecraft:skeleton"))
                        .findFirst()
                        .map(s -> !s.spawnRules().isEmpty()
                                && s.spawnRules().get(0).type() == SpawnRule.Type.Y_LEVEL)
                        .orElse(false));
        SelfTestHarness.check(lines, tally, "12c tier-scoped condition attaches to the tier's husk", () ->
                loaded.tiers().get("TEMP").mobs().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().filter(s -> s.entity().equals("minecraft:husk") && !s.isVariant())
                        .findFirst()
                        .map(s -> !s.spawnRules().isEmpty()
                                && s.spawnRules().get(0).type() == SpawnRule.Type.TIME)
                        .orElse(false));
        SelfTestHarness.check(lines, tally, "12d biome-scoped condition attaches to the biome's zombie", () ->
                loaded.biomes().get("minecraft:plains").mobs()
                        .getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().filter(s -> s.entity().equals("minecraft:zombie"))
                        .findFirst()
                        .map(s -> s.spawnRules().size() == 1)
                        .orElse(false));
        SelfTestHarness.check(lines, tally, "12e variant row inherits its base entity's conditions", () ->
                loaded.tiers().get("TEMP").mobs().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().filter(s -> s.entity().equals("minecraft:husk") && s.isVariant())
                        .findFirst()
                        .map(s -> !s.spawnRules().isEmpty()
                                && s.spawnRules().get(0).type() == SpawnRule.Type.TIME)
                        .orElse(false));

        SelfTestHarness.check(lines, tally, "2a global scope rows present in Loaded.globalSpawns", () ->
                loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of()).size() == 2);
        SelfTestHarness.check(lines, tally, "2b global spawns carry Source.GLOBAL provenance", () ->
                loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().allMatch(s -> s.source().layer() == Source.Layer.GLOBAL));
        SelfTestHarness.check(lines, tally, "2c non-global tier rows NOT mixed into global layer", () ->
                loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().noneMatch(s -> s.entity().equals("minecraft:husk")));


        TableWriter.updateColumn(dir.resolve("tier_biomes.csv"), "tier", "TEMP", "tier", "RENAMED");
        TableWriter.updateColumnIf(dir.resolve("mob_spawns.csv"), "scope_id", "TEMP", "scope_id", "RENAMED", "scope", "tier");
        TableWriter.updateColumnIf(dir.resolve("ores.csv"), "scope_id", "TEMP", "scope_id", "RENAMED", "scope", "tier");
        TableWriter.updateColumnIf(dir.resolve("spawn_rules.csv"), "scope_id", "TEMP", "scope_id", "RENAMED", "scope", "tier");
        TableWriter.updateColumnIf(dir.resolve("loot.csv"), "scope_id", "TEMP", "scope_id", "RENAMED", "scope", "tier");
        SelfTestHarness.check(lines, tally, "7a renamed tier's condition row follows the rename", () ->
                CsvTable.read(dir.resolve("spawn_rules.csv")).stream()
                        .anyMatch(r -> r.get("scope_id").equals("RENAMED")
                                && r.get("entity").equals("minecraft:husk")));
        SelfTestHarness.check(lines, tally, "7b renamed tier's loot row follows the rename", () ->
                CsvTable.read(dir.resolve("loot.csv")).stream()
                        .anyMatch(r -> r.get("scope_id").equals("RENAMED")
                                && r.get("entity").equals("minecraft:husk")
                                && r.get("item").equals("diamond")));
        SelfTestHarness.check(lines, tally, "7c other tier's condition/loot rows left untouched", () ->
                CsvTable.read(dir.resolve("spawn_rules.csv")).stream()
                        .noneMatch(r -> r.get("scope_id").equals("TEMP"))
                        && CsvTable.read(dir.resolve("loot.csv")).stream()
                        .noneMatch(r -> r.get("scope_id").equals("TEMP")));

        SelfTestHarness.check(lines, tally, "6a global skeleton carries per-mob coefficient 1.5", () ->
                loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of())
                        .stream().filter(s -> s.entity().equals("minecraft:skeleton"))
                        .findFirst().map(s -> s.coefficient() != null && s.coefficient() == 1.5)
                        .orElse(false));
        SelfTestHarness.check(lines, tally, "6b coefficientFor finds the override on the resolved entity", () ->
                Spawn.coefficientFor(
                        loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of()),
                        "minecraft:skeleton") == 1.5);
        SelfTestHarness.check(lines, tally, "6c coefficientFor returns null when no override", () ->
                Spawn.coefficientFor(
                        loaded.globalSpawns().getOrDefault(SpawnCategory.MONSTER, List.of()),
                        "minecraft:creeper") == null);

        TableWriter.delete(dir.resolve("loot.csv"), Map.of(
                "scope", "tier", "scope_id", "TEMP2", "entity", "minecraft:husk",
                "pool_index", "0", "entry_index", "0", "item", "emerald"));
        SelfTestHarness.check(lines, tally, "9a delete by entry_index removes the exact row", () ->
                CsvTable.read(dir.resolve("loot.csv")).stream()
                        .noneMatch(r -> r.get("item").equals("emerald")));

        write(dir, "mob_spawns_scope.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                + "global,,minecraft:husk,3,1,1,true,,true,husk_frosty,replace\n"
                + "tier,TEMP,minecraft:husk,3,1,1,true,,true,husk_frosty,replace\n");
        TableWriter.delete(dir.resolve("mob_spawns_scope.csv"), Map.of(
                "scope", "tier", "scope_id", "TEMP", "entity", "minecraft:husk",
                "variant_name", "husk_frosty"));
        SelfTestHarness.check(lines, tally, "10a tier-scoped variant delete removes only that tier's row", () ->
                CsvTable.read(dir.resolve("mob_spawns_scope.csv")).stream()
                        .noneMatch(r -> "tier".equals(r.get("scope"))
                                && "husk_frosty".equals(r.get("variant_name"))));
        SelfTestHarness.check(lines, tally, "10b the global mobs-list entry survives the tier delete", () ->
                CsvTable.read(dir.resolve("mob_spawns_scope.csv")).stream()
                        .anyMatch(r -> "global".equals(r.get("scope"))
                                && "husk_frosty".equals(r.get("variant_name"))));

        write(dir, "mob_spawns_add.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                + "global,,minecraft:husk,3,1,1,true,,true,husk_frosty,add\n");
        for (var prior : CsvTable.read(dir.resolve("mob_spawns_add.csv"))) {
            if (com.strata.domain.VariantOps.clearsPriorSpawnRow(
                    "tier", "TEMP", "husk_frosty", "minecraft:husk",
                    prior.get("scope"), prior.get("scope_id"), prior.get("variant_name"),
                    prior.get("entity"))) {
                TableWriter.delete(dir.resolve("mob_spawns_add.csv"), prior);
            }
        }
        TableWriter.upsert(dir.resolve("mob_spawns_add.csv"),
                Map.of("scope", "tier", "scope_id", "TEMP",
                        "entity", "minecraft:husk", "variant_name", "husk_frosty"),
                Map.of("weight", "3", "min_count", "1", "max_count", "1",
                        "enabled", "true", "is_variant", "true", "variant_mode", "add"));
        SelfTestHarness.check(lines, tally, "10c adding a global variant to a tier keeps the global row", () ->
                CsvTable.read(dir.resolve("mob_spawns_add.csv")).stream()
                        .anyMatch(r -> "global".equals(r.get("scope"))
                                && "husk_frosty".equals(r.get("variant_name"))));
        SelfTestHarness.check(lines, tally, "10d the tier gets its own instance of that variant", () ->
                CsvTable.read(dir.resolve("mob_spawns_add.csv")).stream()
                        .anyMatch(r -> "tier".equals(r.get("scope"))
                                && "TEMP".equals(r.get("scope_id"))
                                && "husk_frosty".equals(r.get("variant_name"))));

        var scopeRows = java.util.List.of(
                java.util.Map.of("scope", "global", "scope_id", "",
                        "entity", "minecraft:creeper", "variant_name", "grass_creeper"),
                java.util.Map.of("scope", "tier", "scope_id", "TEMP",
                        "entity", "minecraft:creeper", "variant_name", "grass_creeper"));
        SelfTestHarness.check(lines, tally, "10e opened from a tier, the editor adopts the TIER row", () ->
                com.strata.domain.VariantOps.spawnRowForScope(
                        scopeRows, "grass_creeper", null, "tier", "TEMP") == 1);
        SelfTestHarness.check(lines, tally, "10f opened with no scope, it falls back to the first row", () ->
                com.strata.domain.VariantOps.spawnRowForScope(
                        scopeRows, "grass_creeper", null, "", "") == 0);
        SelfTestHarness.check(lines, tally, "10z a named scope with no row does not borrow another scope's", () ->
                com.strata.domain.VariantOps.spawnRowForScope(
                        scopeRows, "grass_creeper", null, "biome", "minecraft:plains") == -1);

        write(dir, "mob_spawns_plain.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                + "tier,TEMP,minecraft:zombie,10,1,4,true,,false,,\n"
                + "tier,TEMP,minecraft:skeleton,20,1,4,true,,false,,\n"
                + "tier,TEMP,minecraft:creeper,5,1,4,true,,false,,\n");
        for (var prior : CsvTable.read(dir.resolve("mob_spawns_plain.csv"))) {
            if (com.strata.domain.VariantOps.clearsPriorSpawnRow(
                    "tier", "TEMP", "", "minecraft:skeleton",
                    prior.get("scope"), prior.get("scope_id"), prior.get("variant_name"),
                    prior.get("entity"))) {
                TableWriter.delete(dir.resolve("mob_spawns_plain.csv"), prior);
            }
        }
        SelfTestHarness.check(lines, tally, "B13a saving one plain mob keeps the other plain mobs in its scope", () -> {
            var left = CsvTable.read(dir.resolve("mob_spawns_plain.csv"));
            return left.size() == 2
                    && left.stream().anyMatch(r -> "minecraft:zombie".equals(r.get("entity")))
                    && left.stream().anyMatch(r -> "minecraft:creeper".equals(r.get("entity")));
        });
        var plainRows = java.util.List.of(
                java.util.Map.of("scope", "tier", "scope_id", "TEMP",
                        "entity", "minecraft:zombie", "variant_name", ""),
                java.util.Map.of("scope", "tier", "scope_id", "TEMP",
                        "entity", "minecraft:skeleton", "variant_name", ""));
        SelfTestHarness.check(lines, tally, "B13b a plain mob opens on its own spawn row", () ->
                com.strata.domain.VariantOps.spawnRowForScope(
                        plainRows, "", "minecraft:skeleton", "tier", "TEMP") == 1);

        write(dir, "variants_scope.csv",
                "name,scope,scope_id,base_entity,display_name,coefficient\n"
                + "husk_frosty,,,minecraft:husk,Frosty,1.0\n");
        TableWriter.upsert(dir.resolve("variants_scope.csv"),
                Map.of("name", "husk_frosty", "scope", "tier", "scope_id", "TEMP"),
                Map.of("base_entity", "minecraft:husk", "display_name", "Frostier",
                        "coefficient", "2.0"));
        SelfTestHarness.check(lines, tally, "10g saving in a tier leaves the global variant untouched", () ->
                CsvTable.read(dir.resolve("variants_scope.csv")).stream()
                        .anyMatch(r -> "husk_frosty".equals(r.get("name"))
                                && r.getOrDefault("scope", "").isBlank()
                                && "Frosty".equals(r.get("display_name"))
                                && "1.0".equals(r.get("coefficient"))));
        SelfTestHarness.check(lines, tally, "10h the tier holds its own instance of that variant", () ->
                CsvTable.read(dir.resolve("variants_scope.csv")).stream()
                        .anyMatch(r -> "husk_frosty".equals(r.get("name"))
                                && "tier".equals(r.get("scope"))
                                && "TEMP".equals(r.get("scope_id"))
                                && "Frostier".equals(r.get("display_name"))
                                && "2.0".equals(r.get("coefficient"))));

        write(dir, "loot_variants.csv",
                "scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct\n"
                + "tier,TEMP,minecraft:creeper,,add,0,0,gunpowder,1,1,100\n"
                + "tier,TEMP,minecraft:creeper,grass_creeper,add,0,0,emerald,1,1,100\n"
                + "tier,TEMP,minecraft:creeper,fire_creeper,add,0,0,blaze_rod,1,1,100\n");
        TableWriter.delete(dir.resolve("loot_variants.csv"), Map.of(
                "scope", "tier", "scope_id", "TEMP", "entity", "minecraft:creeper",
                "variant_name", "grass_creeper", "pool_index", "0", "item", "emerald"));
        SelfTestHarness.check(lines, tally, "10i deleting one variant's drop leaves the other variant and the base mob", () -> {
            var rows = CsvTable.read(dir.resolve("loot_variants.csv"));
            boolean goneForGrass = rows.stream().noneMatch(r ->
                    "grass_creeper".equals(r.get("variant_name")));
            boolean baseKept = rows.stream().anyMatch(r ->
                    r.getOrDefault("variant_name", "").isBlank()
                            && "gunpowder".equals(r.get("item")));
            boolean otherKept = rows.stream().anyMatch(r ->
                    "fire_creeper".equals(r.get("variant_name"))
                            && "blaze_rod".equals(r.get("item")));
            return goneForGrass && baseKept && otherKept;
        });

        write(dir, "equipment_scope.csv",
                "variant_name,scope,scope_id,slot,item,chance_pct\n"
                + "grass_creeper,,,mainhand,iron_sword,100\n");
        TableWriter.upsert(dir.resolve("equipment_scope.csv"),
                Map.of("variant_name", "grass_creeper", "scope", "tier",
                        "scope_id", "TEMP", "slot", "mainhand", "item", "diamond_sword"),
                Map.of("chance_pct", "100"));
        SelfTestHarness.check(lines, tally, "10j a tier's equipment stands beside the global variant's, not over it", () -> {
            var rows = CsvTable.read(dir.resolve("equipment_scope.csv"));
            boolean globalKept = rows.stream().anyMatch(r ->
                    "grass_creeper".equals(r.get("variant_name"))
                            && r.getOrDefault("scope", "").isBlank()
                            && "iron_sword".equals(r.get("item")));
            boolean tierOwn = rows.stream().anyMatch(r ->
                    "tier".equals(r.get("scope")) && "TEMP".equals(r.get("scope_id"))
                            && "diamond_sword".equals(r.get("item")));
            return globalKept && tierOwn;
        });

        index = new java.util.LinkedHashMap<>();
        index.put(com.strata.runtime.ScopeIndex.key("", "", "grass_creeper"), "global_equipment");
        index.put(com.strata.runtime.ScopeIndex.key("tier", "TEMP", "grass_creeper"), "tier_equipment");
        index.put(com.strata.runtime.ScopeIndex.key("biome", "minecraft:plains", "grass_creeper"), "biome_equipment");
        SelfTestHarness.check(lines, tally, "10k biome wins over tier and global", () ->
                "biome_equipment".equals(com.strata.runtime.ScopeIndex.resolve(
                        index, "grass_creeper", "TEMP", "minecraft:plains")));
        SelfTestHarness.check(lines, tally, "10l tier wins when the biome defines none", () ->
                "tier_equipment".equals(com.strata.runtime.ScopeIndex.resolve(
                        index, "grass_creeper", "TEMP", "minecraft:desert")));
        SelfTestHarness.check(lines, tally, "10m global is inherited when neither layer defines any", () ->
                "global_equipment".equals(com.strata.runtime.ScopeIndex.resolve(
                        index, "grass_creeper", "OTHER", "minecraft:desert")));
        SelfTestHarness.check(lines, tally, "10n a row with no scope columns is the global instance", () ->
                com.strata.runtime.ScopeIndex.key("", "", "grass_creeper")
                        .equals(com.strata.runtime.ScopeIndex.key("global", "ignored", "grass_creeper")));

        SelfTestHarness.check(lines, tally, "10o a second build takes the first free number", () -> {
            var taken = new java.util.LinkedHashSet<>(java.util.List.of("grassland_creeper"));
            String second = com.strata.domain.VariantOps.uniqueName("grassland_creeper", taken);
            taken.add(second);
            String third = com.strata.domain.VariantOps.uniqueName("grassland_creeper", taken);
            return "grassland_creeper2".equals(second) && "grassland_creeper3".equals(third);
        });
        SelfTestHarness.check(lines, tally, "10p an unused name is handed back unchanged", () ->
                "husk_frosty".equals(com.strata.domain.VariantOps.uniqueName(
                        "husk_frosty", java.util.Set.of("creeper_plains"))));

        write(dir, "loot_deny.csv",
                "scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct\n");
        TableWriter.upsert(dir.resolve("loot_deny.csv"),
                Map.of("scope", "tier", "scope_id", "TEMP", "entity", "minecraft:skeleton",
                        "variant_name", "", "pool_index", "0", "item", "arrow"),
                Map.of("mode", "deny", "entry_index", "0",
                        "count_min", "0", "count_max", "0", "chance_pct", "0"));
        SelfTestHarness.check(lines, tally, "10q denying a vanilla drop writes a deny row", () ->
                CsvTable.read(dir.resolve("loot_deny.csv")).stream()
                        .anyMatch(r -> "arrow".equals(r.get("item"))
                                && "deny".equals(r.get("mode"))));
        for (var r : CsvTable.read(dir.resolve("loot_deny.csv"))) {
            TableWriter.delete(dir.resolve("loot_deny.csv"), r);
        }
        SelfTestHarness.check(lines, tally, "10r restoring defaults leaves no deny behind", () ->
                CsvTable.read(dir.resolve("loot_deny.csv")).isEmpty());

        write(dir, "equipment_deny.csv",
                "variant_name,scope,scope_id,slot,item,chance_pct,mode\n"
                + "grass_creeper,tier,TEMP,mainhand,iron_sword,100,add\n"
                + "grass_creeper,tier,TEMP,head,leather_helmet,0,deny\n");
        SelfTestHarness.check(lines, tally, "10s an equipment deny is a row, distinct from a skipped one", () -> {
            var rows = CsvTable.read(dir.resolve("equipment_deny.csv"));
            boolean denyKept = rows.stream().anyMatch(r ->
                    "deny".equals(r.get("mode")) && "leather_helmet".equals(r.get("item")));
            boolean equipmentKept = rows.stream().anyMatch(r ->
                    "add".equals(r.get("mode")) && "iron_sword".equals(r.get("item")));
            return denyKept && equipmentKept;
        });

        write(dir, "mob_spawns_same_base.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,is_variant,variant_name\n"
                + "tier,TEMP,minecraft:creeper,10,1,1,true,false,\n");
        TableWriter.upsert(dir.resolve("mob_spawns_same_base.csv"),
                Map.of("scope", "tier", "scope_id", "TEMP",
                        "entity", "minecraft:creeper", "variant_name", ""),
                Map.of("weight", "99"));
        SelfTestHarness.check(lines, tally, "10t an unnamed second entry collides with the first", () ->
                CsvTable.read(dir.resolve("mob_spawns_same_base.csv")).stream()
                        .filter(r -> "minecraft:creeper".equals(r.get("entity")))
                        .count() == 1);
        TableWriter.upsert(dir.resolve("mob_spawns_same_base.csv"),
                Map.of("scope", "tier", "scope_id", "TEMP",
                        "entity", "minecraft:creeper", "variant_name", "big_creeper"),
                Map.of("weight", "5", "is_variant", "true"));
        SelfTestHarness.check(lines, tally, "10u a named entry stands beside the base mob's", () -> {
            var rows = CsvTable.read(dir.resolve("mob_spawns_same_base.csv")).stream()
                    .filter(r -> "minecraft:creeper".equals(r.get("entity"))).toList();
            boolean base = rows.stream().anyMatch(r ->
                    r.getOrDefault("variant_name", "").isBlank());
            boolean named = rows.stream().anyMatch(r ->
                    "big_creeper".equals(r.get("variant_name")));
            return rows.size() == 2 && base && named;
        });

        write(dir, "equipment_dup.csv",
                "variant_name,scope,scope_id,slot,item,chance_pct,mode\n"
                + "big_creeper,,,mainhand,iron_sword,100,add\n"
                + "big_creeper,tier,TEMP,mainhand,diamond_sword,100,add\n");
        for (var row : CsvTable.read(dir.resolve("equipment_dup.csv"))) {
            if (!"big_creeper".equals(row.get("variant_name"))) continue;
            if (!row.getOrDefault("scope", "").isBlank()) continue;  // base only
            var copy = new java.util.LinkedHashMap<>(row);
            copy.put("variant_name", "big_creeper2");
            TableWriter.upsert(dir.resolve("equipment_dup.csv"), copy, copy);
        }
        SelfTestHarness.check(lines, tally, "10v the duplicate is seeded from the BASE variant", () ->
                CsvTable.read(dir.resolve("equipment_dup.csv")).stream()
                        .anyMatch(r -> "big_creeper2".equals(r.get("variant_name"))
                                && r.getOrDefault("scope", "").isBlank()
                                && "iron_sword".equals(r.get("item"))));
        SelfTestHarness.check(lines, tally, "10w it does NOT inherit the tier instance's equipment", () ->
                CsvTable.read(dir.resolve("equipment_dup.csv")).stream()
                        .noneMatch(r -> "big_creeper2".equals(r.get("variant_name"))
                                && "diamond_sword".equals(r.get("item"))));

        write(dir, "loot_restore.csv",
                "scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct\n"
                + "tier,TEMP,minecraft:skeleton,,add,0,0,emerald,1,1,100\n"
                + "tier,TEMP,minecraft:skeleton,,deny,1,0,arrow,0,0,0\n");
        for (var r : CsvTable.read(dir.resolve("loot_restore.csv"))) {
            if (!"deny".equals(r.get("mode"))) {
                TableWriter.delete(dir.resolve("loot_restore.csv"), r);
            }
        }
        SelfTestHarness.check(lines, tally, "10x clearing only the visible rows leaves the deny behind", () ->
                CsvTable.read(dir.resolve("loot_restore.csv")).stream()
                        .anyMatch(r -> "deny".equals(r.get("mode"))));
        for (var r : CsvTable.read(dir.resolve("loot_restore.csv"))) {
            if ("deny".equals(r.get("mode"))) {
                TableWriter.delete(dir.resolve("loot_restore.csv"), r);
            }
        }
        SelfTestHarness.check(lines, tally, "10y restore leaves the table empty so the game's drops return", () ->
                CsvTable.read(dir.resolve("loot_restore.csv")).isEmpty());

        Resolver resolver = resolverFor(loaded);
        plains = resolver.resolve("minecraft:plains");
        SelfTestHarness.check(lines, tally, "14a frequency_pct parsed into tier/biome OreRules", () -> {
            boolean tierIron = loaded.tiers().get("TEMP").ores().stream()
                    .anyMatch(r -> r.oreId().equals("iron") && r.frequencyPct() == 50.0);
            boolean biomeCopper = loaded.biomes().get("minecraft:plains").ores().stream()
                    .anyMatch(r -> r.oreId().equals("copper") && r.frequencyPct() == 30.0);
            return tierIron && biomeCopper;
        });
        SelfTestHarness.check(lines, tally, "14b resolution carries effective frequencies", () ->
                plains.oreFrequencies().getOrDefault("iron", -1.0) == 50.0
                        && plains.oreFrequencies().getOrDefault("copper", -1.0) == 30.0);
        SelfTestHarness.check(lines, tally, "14c disabled ore row becomes DENY, dropped from resolution", () ->
                !plains.oreFrequencies().containsKey("gold")
                        && !plains.ores().contains("gold"));
        SelfTestHarness.check(lines, tally, "14d unmentioned ores keep normal density (map stays empty)", () ->
                resolverFor(loaded).resolve("minecraft:desert").oreFrequencies().isEmpty());

        write(dir, "variants.csv",
                "name,base_entity,display_name,coefficient,texture_path\n"
                        + "husk_frosty,minecraft:husk,Frosty Husk,1.2,\n"
                        + "fire_zombie,minecraft:zombie,,1.0,\n");
        write(dir, "variant_stats.csv", "variant_name,attribute,multiplier,absolute\n");
        write(dir, "abilities.csv", "variant_name,ability,parameter,amount,interval_ticks,radius,enabled\n");
        write(dir, "equipment.csv", "variant_name,slot,item,chance_pct\n");
        var reloaded = Tables.loadAll(dir);
        SelfTestHarness.check(lines, tally, "G4a display_name loads from CSV when present", () ->
                reloaded.variants().get("husk_frosty").displayName().equals("Frosty Husk"));
        SelfTestHarness.check(lines, tally, "G4b display_name defaults to id when blank", () ->
                reloaded.variants().get("fire_zombie").displayName().equals("fire_zombie"));

        write(dir, "variants.csv",
                "name,base_entity,display_name,coefficient,texture_path,is_own_entity\n"
                        + "husk_frosty,minecraft:husk,Frosty Husk,1.2,,true\n"
                        + "fire_zombie,minecraft:zombie,,1.0,,false\n");
        write(dir, "tiers.csv",
                "name,level,spawn_mode,coefficient\nTEMP,2,add,1.0\n");
    }

    private static void runTestsPart02(Path dir, List<String> lines, int[] tally) throws Exception {
        write(dir, "tier_biomes.csv", "tier,biome\nTEMP,minecraft:plains\n");
        write(dir, "mob_spawns.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                        + "tier,TEMP,minecraft:husk,5,1,1,true,2.5,false,,\n"
                        + "tier,TEMP,minecraft:husk,3,1,1,true,,true,husk_frosty,replace\n"
                        + "global,,minecraft:creeper,1,1,1,true,,false,,\n");
        var stepped = Tables.loadAll(dir);
        var steppedResolved = resolverFor(stepped).resolve("minecraft:plains");
        candidates = steppedResolved.variants();
        SelfTestHarness.check(lines, tally, "G5a is_own_entity true/false load straight from CSV", () -> {
            boolean husk = stepped.variants().get("husk_frosty").isOwnEntity();
            boolean zombie = !stepped.variants().get("fire_zombie").isOwnEntity();
            return husk && zombie;
        });
        SelfTestHarness.check(lines, tally, "G5b promotion seam picks the promoted candidate, else null", () -> {
            Variant hit = VariantHandler.rollPromoted(candidates, Optional.of("husk_frosty"));
            boolean matched = hit != null && hit.id().equals("husk_frosty");
            boolean absent = VariantHandler.rollPromoted(candidates, Optional.empty()) == null;
            boolean noRosterSlot = VariantHandler.rollPromoted(candidates, Optional.of("fire_zombie")) == null;
            return matched && absent && noRosterSlot;
        });
        SelfTestHarness.check(lines, tally, "G5c promoted variant takes the BASE row's coefficient", () -> {
            List<Spawn> roster = steppedResolved.mobs(SpawnCategory.MONSTER);
            boolean baseCarries = Spawn.coefficientFor(roster, "minecraft:husk") == 2.5;
            boolean variantIdCarriesNil = Spawn.coefficientFor(roster, "husk_frosty") == null;
            return baseCarries && variantIdCarriesNil;
        });

        SelfTestHarness.check(lines, tally, "L1 parse 'looting' -> LOOTING", () ->
                LootCondition.Type.parse("looting") == LootCondition.Type.LOOTING);
        SelfTestHarness.check(lines, tally, "L2 parse 'minecraft:looting' -> LOOTING (alias)", () ->
                LootCondition.Type.parse("minecraft:looting") == LootCondition.Type.LOOTING);
        SelfTestHarness.check(lines, tally, "L3 checkLooting threshold: pass at level, fail below", () ->
                LootCondition.checkLooting("3", 3)
                        && !LootCondition.checkLooting("3", 2));
        SelfTestHarness.check(lines, tally, "L4 checkLooting >=N normalizes same as bare N", () ->
                LootCondition.checkLooting(">=3", 3)
                        && !LootCondition.checkLooting(">=3", 2));
        SelfTestHarness.check(lines, tally, "L5 checkLooting empty value -> threshold 1", () ->
                LootCondition.checkLooting("", 1)
                        && !LootCondition.checkLooting("", 0));
        SelfTestHarness.check(lines, tally, "L6 parse 'death_cause' -> DEATH_CAUSE", () ->
                LootCondition.Type.parse("death_cause") == LootCondition.Type.DEATH_CAUSE);
        SelfTestHarness.check(lines, tally, "L7 the cause is the damage type id, not a word", () ->
                LootCondition.checkDeathCause("minecraft:drown", "minecraft:drown")
                        && !LootCondition.checkDeathCause("minecraft:drown", "minecraft:lava"));
        SelfTestHarness.check(lines, tally, "L8 lava and the sunlight burn (on_fire) match only their own id", () ->
                LootCondition.checkDeathCause("minecraft:lava", "minecraft:lava")
                        && LootCondition.checkDeathCause("minecraft:on_fire", "minecraft:on_fire")
                        && !LootCondition.checkDeathCause("minecraft:on_fire", "minecraft:drown"));
        SelfTestHarness.check(lines, tally, "L9 blank and any pass on any death; a bare path matches", () ->
                LootCondition.checkDeathCause("", "minecraft:fall")
                        && LootCondition.checkDeathCause("any", "minecraft:fall")
                        && LootCondition.checkDeathCause("drown", "minecraft:drown")
                        && !LootCondition.checkDeathCause("drown", "minecraft:lava"));
        SelfTestHarness.check(lines, tally, "L10 the death cause saves only non-blank; any passes, an id passes before a registry binds", () ->
                com.strata.domain.LootOps.conditionValueProblem("death_cause", "") != null
                        && com.strata.domain.LootOps.conditionValueProblem("death_cause", "any") == null
                        && com.strata.domain.LootOps.conditionValueProblem("death_cause", "minecraft:drown") == null
                        && com.strata.domain.LootOps.conditionValueProblem("looting", "x") == null
                        && com.strata.domain.LootOps.conditionValueProblem("death_cause", "sunlight") == null
                        && com.strata.domain.LootOps.deathCauseLabel("minecraft:on_fire").startsWith("Burning")
                        && com.strata.domain.LootOps.deathCauseLabel("sunlight").equals("Sunlight"));
        SelfTestHarness.check(lines, tally, "L11 a cause of death covers its group; sunlight is only the sun's fire; player is any player kill", () -> {
            var sun = new LootCondition.DeathFacts("minecraft:on_fire", false, true);
            var torch = new LootCondition.DeathFacts("minecraft:on_fire", false, false);
            var arrow = new LootCondition.DeathFacts("minecraft:arrow", true, false);
            var melee = new LootCondition.DeathFacts("minecraft:player_attack", true, false);
            var cactus = new LootCondition.DeathFacts("minecraft:cactus", false, false);
            return LootCondition.checkDeathCause("sunlight", sun) && !LootCondition.checkDeathCause("sunlight", torch)
                    && LootCondition.checkDeathCause("fire", torch) && !LootCondition.checkDeathCause("fire", sun)
                    && LootCondition.checkDeathCause("player", arrow) && LootCondition.checkDeathCause("player", melee)
                    && !LootCondition.checkDeathCause("player_melee", arrow) && LootCondition.checkDeathCause("player_melee", melee)
                    && LootCondition.checkDeathCause("other", cactus) && !LootCondition.checkDeathCause("other", torch)
                    && !LootCondition.checkDeathCause("other", sun) && LootCondition.checkDeathCause("minecraft:cactus", cactus)
                    && LootCondition.checkDeathCause("any", cactus);
        });
        SelfTestHarness.check(lines, tally, "RC2 a written row of any list is on top in Recent, newest first, and stays in its group", () -> {
            com.strata.client.util.RecentEdits.touchRow("TestList", "b");
            com.strata.client.util.RecentEdits.touchRow("TestList", "a");
            com.strata.client.util.RecentEdits.touchRow("Other", "c");
            var rows = List.of(new com.strata.client.widget.BrowseList.Entry("g", "G", "", 0, "g", null, 0),
                    new com.strata.client.widget.BrowseList.Entry("a", "A", "", 0, null, "g", 1),
                    new com.strata.client.widget.BrowseList.Entry("b", "B", "", 0, null, "g", 1),
                    new com.strata.client.widget.BrowseList.Entry("c", "C", "", 0, null, "g", 1));
            var out = com.strata.client.widget.BrowseList.withRecent(rows,
                    com.strata.client.util.RecentEdits.recentRows("TestList"));
            var none = com.strata.client.widget.BrowseList.withRecent(rows, List.of("zzz"));
            return out.size() == 7 && out.get(0).label.equals("Recent (2)") && out.get(1).id.equals("a")
                    && out.get(2).id.equals("b") && "__recent".equals(out.get(1).parentGroup)
                    && none == rows;
        });
        SelfTestHarness.check(lines, tally, "MB1 Off in a mob's Biomes writes the biome's own spawn row, which the biome's mob list reads", () -> {
            java.nio.file.Path t = java.nio.file.Files.createTempFile("strata-mb", ".csv");
            try {
                java.nio.file.Files.writeString(t, String.join(",", Schema.MobSpawns.SCOPE, Schema.MobSpawns.SCOPE_ID,
                        Schema.MobSpawns.ENTITY, Schema.MobSpawns.VARIANT_NAME, Schema.MobSpawns.CATEGORY,
                        Schema.MobSpawns.WEIGHT, Schema.MobSpawns.MIN_COUNT, Schema.MobSpawns.MAX_COUNT,
                        Schema.MobSpawns.ENABLED, Schema.MobSpawns.IS_VARIANT) + "\n");
                var row = new com.strata.domain.MobBiomesOps.Row("minecraft:plains", "minecraft:overworld", "monster",
                        "vanilla", 100, 4, 4, true);
                boolean off = com.strata.domain.MobBiomesOps.write(t, "minecraft:zombie", row, false);
                var rows = CsvTable.read(t);
                boolean listed = com.strata.domain.MobOps.listedInScope("biome", "minecraft:plains", rows)
                        .contains("minecraft:zombie");
                boolean stored = rows.size() == 1 && "false".equals(rows.get(0).get(Schema.MobSpawns.ENABLED))
                        && "100".equals(rows.get(0).get(Schema.MobSpawns.WEIGHT));
                boolean on = com.strata.domain.MobBiomesOps.write(t, "minecraft:zombie", row, true);
                var again = CsvTable.read(t);
                return off && listed && stored && on && again.size() == 1
                        && "true".equals(again.get(0).get(Schema.MobSpawns.ENABLED));
            } finally {
                java.nio.file.Files.deleteIfExists(t);
            }
        });
        SelfTestHarness.check(lines, tally, "L12 an old killed_by_player row reads as the cause of death player", () -> {
            LootCondition c = LootCondition.of(LootCondition.Type.parse("killed_by_player"), "killed_by_player", "");
            return c.type() == LootCondition.Type.DEATH_CAUSE && c.value().equals("player")
                    && !com.strata.domain.LootOps.CONDITION_CHOICES.contains("killed_by_player");
        });


        write(dir, "mob_goal_sets.csv",
                "variant_name,scope,scope_id,goal_set\n"
                        + "husk_frosty,,,skeleton-melee\n"
                        + "fire_zombie,,,\n");
        com.strata.runtime.GoalsRuntime.loadChoices(dir.resolve("mob_goal_sets.csv"));
        SelfTestHarness.check(lines, tally, "P2 a goal set choice loads from mob_goal_sets.csv for the named variant", () ->
                com.strata.runtime.GoalsRuntime.goalSetFor("husk_frosty", "", "", "")
                        .equals("skeleton-melee"));
        SelfTestHarness.check(lines, tally, "P4 a variant with a blank choice -> goalSetFor returns empty", () ->
                com.strata.runtime.GoalsRuntime.goalSetFor("fire_zombie", "", "", "").isEmpty());

        SelfTestHarness.check(lines, tally, "M1 updateColumn writes goal_set, loadChoices reads it back", () -> {
            Path choices = dir.resolve("mob_goal_sets.csv");
            TableWriter.updateColumn(choices, "variant_name", "fire_zombie",
                    "goal_set", "skeleton-melee");
            com.strata.runtime.GoalsRuntime.loadChoices(choices);
            return com.strata.runtime.GoalsRuntime.goalSetFor("fire_zombie", "", "", "")
                    .equals("skeleton-melee");
        });
        SelfTestHarness.check(lines, tally, "M2 clearing goal_set sets goalSetFor to empty", () -> {
            Path choices = dir.resolve("mob_goal_sets.csv");
            TableWriter.updateColumn(choices, "variant_name", "fire_zombie",
                    "goal_set", "");
            com.strata.runtime.GoalsRuntime.loadChoices(choices);
            return com.strata.runtime.GoalsRuntime.goalSetFor("fire_zombie", "", "", "").isEmpty();
        });
        SelfTestHarness.check(lines, tally, "EQ1 two equal weights in one slot split the draw", () -> {
            double[] weights = {50, 50};
            return com.strata.model.WeightedPick.indexWithRemainder(weights, 0.0, 100.0) == 0
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.49, 100.0) == 0
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.5, 100.0) == 1
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.99, 100.0) == 1;
        });
        SelfTestHarness.check(lines, tally, "EQ2 weights under 100 leave an empty share", () -> {
            double[] weights = {30};
            return com.strata.model.WeightedPick.indexWithRemainder(weights, 0.29, 100.0) == 0
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.31, 100.0) == -1;
        });
        SelfTestHarness.check(lines, tally, "EQ3 weights over 100 are relative odds with no empty share", () -> {
            double[] weights = {150, 50};
            return com.strata.model.WeightedPick.indexWithRemainder(weights, 0.0, 100.0) == 0
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.74, 100.0) == 0
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.76, 100.0) == 1
                    && com.strata.model.WeightedPick.indexWithRemainder(weights, 0.999, 100.0) == 1;
        });
        SelfTestHarness.check(lines, tally, "EQ4 empty / zero-weight slot picks nothing", () ->
                com.strata.model.WeightedPick.indexWithRemainder(new double[0], 0.5, 100.0) == -1
                        && com.strata.model.WeightedPick.indexWithRemainder(null, 0.5, 100.0) == -1
                        && com.strata.model.WeightedPick.indexWithRemainder(new double[]{0}, 0.0, 100.0) == -1);
        SelfTestHarness.check(lines, tally, "WP1 integer draw: every index is reachable, in order", () -> {
            int[] weights = {10, 20, 30};
            return com.strata.model.WeightedPick.index(weights, 0) == 0
                    && com.strata.model.WeightedPick.index(weights, 9) == 0
                    && com.strata.model.WeightedPick.index(weights, 10) == 1
                    && com.strata.model.WeightedPick.index(weights, 29) == 1
                    && com.strata.model.WeightedPick.index(weights, 30) == 2
                    && com.strata.model.WeightedPick.index(weights, 59) == 2;
        });
        SelfTestHarness.check(lines, tally, "WP2 integer draw: a zero-weight row is never drawn", () -> {
            int[] weights = {5, 0, 5};
            for (int roll = 0; roll < 10; roll++) {
                if (com.strata.model.WeightedPick.index(weights, roll) == 1) {
                    return false;
                }
            }
            return com.strata.model.WeightedPick.index(weights, 4) == 0
                    && com.strata.model.WeightedPick.index(weights, 5) == 2;
        });
        SelfTestHarness.check(lines, tally, "WP3 integer draw: negatives clamp to zero, empty picks nothing", () ->
                com.strata.model.WeightedPick.index(new int[]{-5, 10}, 0) == 1
                        && com.strata.model.WeightedPick.index(new int[]{0, 0}, 0) == -1
                        && com.strata.model.WeightedPick.index(new int[0], 0) == -1
                        && com.strata.model.WeightedPick.index(null, 0) == -1);
        SelfTestHarness.check(lines, tally, "EQ5 upsert keyed on slot+item keeps both rows in one slot", () -> {
            Path eqPath = dir.resolve("equipment.csv");
            for (String item : new String[]{"minecraft:bow", "minecraft:iron_sword"}) {
                TableWriter.upsert(eqPath,
                        new java.util.LinkedHashMap<>(Map.of(
                                "variant_name", "slot_share",
                                "slot", "mainhand",
                                "item", item)),
                        new java.util.LinkedHashMap<>(Map.of("chance_pct", "50")));
            }
            long mainhandRows = CsvTable.read(eqPath).stream()
                    .filter(r -> "slot_share".equals(r.get("variant_name"))
                            && "mainhand".equals(r.get("slot")))
                    .count();
            return mainhandRows == 2;
        });

        SelfTestHarness.check(lines, tally, "PD1 a decimal weight reads the same for editor and loader", () ->
                Parse.integer("10.7", 1) == 10
                        && Parse.integer("", 1) == 1
                        && Parse.integer(null, 1) == 1
                        && Parse.integer("not a number", 7) == 7
                        && Parse.integer("  12  ", 1) == 12);
        SelfTestHarness.check(lines, tally, "PD2 a non-boolean enabled cell is NOT read as enabled", () ->
                !Parse.flag("yes", true)
                        && !Parse.flag("1", true)
                        && !Parse.flag("FALSE", true)
                        && Parse.flag("TRUE", false)
                        && Parse.flag("", true)
                        && Parse.flag(null, true));

        SelfTestHarness.check(lines, tally, "N5 CSV with goal_set=passive loads correctly", () -> {
            Path choices = dir.resolve("mob_goal_sets.csv");
            write(dir, "mob_goal_sets.csv",
                    "variant_name,scope,scope_id,goal_set\n"
                            + "calm_zombie,,,passive\n");
            com.strata.runtime.GoalsRuntime.loadChoices(choices);
            return com.strata.runtime.GoalsRuntime.goalSetFor("calm_zombie", "", "", "")
                    .equals("passive");
        });

        SelfTestHarness.check(lines, tally, "V1 validate on clean data returns empty", () -> {
            Path cleanDir = Files.createTempDirectory("strata-validate-clean");
            try {
                write(cleanDir, "tiers.csv", "name,level,spawn_mode,coefficient\nT1,1,add,1.0\n");
                write(cleanDir, "tier_biomes.csv", "tier,biome\nT1,minecraft:plains\n");
                write(cleanDir, "variants.csv", "name,base_entity,display_name\nv1,minecraft:zombie,V1\n");
                write(cleanDir, "variant_stats.csv", "variant_name,attribute,multiplier,absolute\nv1,generic.max_health,2.0,\n");
                write(cleanDir, "equipment.csv", "variant_name,slot,item,chance_pct\n");
                write(cleanDir, "mob_spawns.csv", "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n");
                var cleanLoaded = Tables.loadAll(cleanDir);
                return ConfigValidator.validate(cleanLoaded, cleanDir).isEmpty();
            } finally {
                deleteRec(cleanDir);
            }
        });
        SelfTestHarness.check(lines, tally, "V2 invalid tier in tier_biomes produces finding", () -> {
            write(dir, "tier_biomes.csv", "tier,biome\nTEMP,minecraft:plains\nGHOST_TIER,minecraft:desert\n");
            var loaded2 = Tables.loadAll(dir);
            var findings = ConfigValidator.validate(loaded2, dir);
            write(dir, "tier_biomes.csv", "tier,biome\nTEMP,minecraft:plains\n");
            return findings.stream().anyMatch(f ->
                    f.category().equals("invalid-tier") && f.message().contains("GHOST_TIER"));
        });
        SelfTestHarness.check(lines, tally, "V3 an unknown goal set produces a finding", () -> {
            write(dir, "mob_goal_sets.csv",
                    "variant_name,scope,scope_id,goal_set\n"
                            + "test_mob,,,nonexistent-preset\n");
            var loaded3 = Tables.loadAll(dir);
            var findings = ConfigValidator.validate(loaded3, dir);
            write(dir, "mob_goal_sets.csv", "variant_name,scope,scope_id,goal_set\n");
            return findings.stream().anyMatch(f ->
                    f.category().equals("unknown-goal-set") && f.message().contains("nonexistent-preset")
                            && f.message().contains("unknown goal set"));
        });
        SelfTestHarness.check(lines, tally, "V4 orphaned variant in variant_stats produces finding", () -> {
            write(dir, "variant_stats.csv",
                    "variant_name,attribute,multiplier,absolute\n"
                            + "ghost_variant,generic.max_health,2.0,\n");
            var loaded4 = Tables.loadAll(dir);
            var findings = ConfigValidator.validate(loaded4, dir);
            write(dir, "variant_stats.csv",
                    "variant_name,attribute,multiplier,absolute\n");
            return findings.stream().anyMatch(f ->
                    f.category().equals("orphaned-variant") && f.message().contains("ghost_variant"));
        });
        SelfTestHarness.check(lines, tally, "V5 knownRegistryKeys contains all 7 expected keys", () -> {
            var keys = ConfigValidator.knownRegistryKeys();
            return keys.size() == 7
                    && keys.contains("entity") && keys.contains("item")
                    && keys.contains("attribute") && keys.contains("biome")
                    && keys.contains("structure") && keys.contains("ore")
                    && keys.contains("goal_set") && !keys.contains("behavior");
        });

        SelfTestHarness.check(lines, tally,
                "M3 a goal set choice is per instance: the nearest scope, else the base mob's", () -> {
            Path choices = dir.resolve("mob_goal_sets.csv");
            write(dir, "mob_goal_sets.csv",
                    "variant_name,scope,scope_id,goal_set\n"
                            + "fire_zombie,,,wander\n"
                            + "fire_zombie,tier,TEMP,passive\n"
                            + "other,,,\n");
            com.strata.runtime.GoalsRuntime.loadChoices(choices);
            boolean ok = com.strata.runtime.GoalsRuntime.goalSetFor("fire_zombie", "", "TEMP", "").equals("passive")
                    && com.strata.runtime.GoalsRuntime.goalSetFor("fire_zombie", "", "", "").equals("wander")
                    && com.strata.runtime.GoalsRuntime.goalSetFor("hot_zombie", "fire_zombie", "", "").equals("wander")
                    && com.strata.runtime.GoalsRuntime.goalSetFor("other", "", "", "").isEmpty()
                    && com.strata.runtime.GoalsRuntime.chosenGoalSets().size() == 2;
            write(dir, "mob_goal_sets.csv", "variant_name,scope,scope_id,goal_set\n");
            com.strata.runtime.GoalsRuntime.loadChoices(choices);
            return ok;
        });

        SelfTestHarness.check(lines, tally, "SE2 seeded mob_spawns CSV parses via Tables.loadAll", () -> {
            Path seedDir = Files.createTempDirectory("strata-seed");
            try {
                write(seedDir, "tiers.csv", "name,level,spawn_mode,coefficient\n");
                write(seedDir, "tier_biomes.csv", "tier,biome\n");
                write(seedDir, "biome_overrides.csv", "biome,level,scale_mobs,restrict_ores,substitute_spawns,coefficient\n");
                write(seedDir, "mob_spawns.csv",
                        "scope,scope_id,entity,category,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                                + "biome,minecraft:plains,minecraft:pig,creature,10,4,4,true,,false,,\n"
                                + "biome,minecraft:plains,minecraft:cow,creature,8,4,4,true,,false,,\n"
                                + "biome,minecraft:forest,minecraft:wolf,creature,5,2,4,true,,false,,\n");
                write(seedDir, "mob_base_stats.csv", "entity,attribute,value\n");
                write(seedDir, "spawn_rules.csv", "scope,scope_id,entity,rule_type,value\n");
                write(seedDir, "scaling_mobs.csv", "attribute,per_level,exponent,enabled\n");
                write(seedDir, "scaling_player.csv", "attribute,per_level,exponent,enabled\n");
                write(seedDir, "ores.csv",
                        "scope,scope_id,ore_id,enabled,frequency_pct\n"
                                + "biome,minecraft:plains,coal,true,100\n"
                                + "biome,minecraft:plains,iron,true,100\n");
                write(seedDir, "variants.csv", "name,base_entity,display_name,coefficient,texture_path,is_own_entity\n");
                write(seedDir, "variant_stats.csv", "variant_name,attribute,multiplier,absolute\n");
                write(seedDir, "abilities.csv", "variant_name,ability,parameter,amount,interval_ticks,radius,enabled\n");
                write(seedDir, "equipment.csv", "variant_name,slot,item,chance_pct\n");
                write(seedDir, "loot.csv", "scope,scope_id,entity,mode,pool_index,entry_index,item,count_min,count_max,chance_pct,condition_type,condition_value\n");

                Tables.Loaded seeded = Tables.loadAll(seedDir);
                return seeded.biomes().containsKey("minecraft:plains")
                        && seeded.biomes().containsKey("minecraft:forest")
                        && seeded.biomes().size() == 2;
            } finally {
                deleteRec(seedDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "R1 negative range bounds match (Y below sea level)", () -> {
            if (!com.strata.model.SpawnRule.parseRange("-64-0", -30)) {
                lines.add("    R1 '-64-0' did not match y=-30");
                return false;
            }
            return com.strata.model.SpawnRule.parseRange("-64-0", -64)
                    && com.strata.model.SpawnRule.parseRange("-64-0", 0)
                    && !com.strata.model.SpawnRule.parseRange("-64-0", 1);
        });

        SelfTestHarness.check(lines, tally,
                "R2 positive ranges and single values still work", () ->
                com.strata.model.SpawnRule.parseRange("10-20", 15)
                        && !com.strata.model.SpawnRule.parseRange("10-20", 21)
                        && com.strata.model.SpawnRule.parseRange("7", 7)
                        && !com.strata.model.SpawnRule.parseRange("7", 8));

        SelfTestHarness.check(lines, tally,
                "R3 a negative single value and a wholly-negative range", () ->
                com.strata.model.SpawnRule.parseRange("-10", -10)
                        && !com.strata.model.SpawnRule.parseRange("-10", 10)
                        && com.strata.model.SpawnRule.parseRange("-30--10", -20)
                        && !com.strata.model.SpawnRule.parseRange("-30--10", -5));

        SelfTestHarness.check(lines, tally,
                "R4 max health is recognised however the CSV spells it", () -> {
            if (!com.strata.runtime.VariantHandler.isMaxHealth("generic.max_health")) {
                lines.add("    R4 unqualified 'generic.max_health' not recognised");
                return false;
            }
            return com.strata.runtime.VariantHandler.isMaxHealth("minecraft:generic.max_health")
                    && !com.strata.runtime.VariantHandler.isMaxHealth("generic.movement_speed")
                    && !com.strata.runtime.VariantHandler.isMaxHealth("");
        });

        SelfTestHarness.check(lines, tally,
                "S1 every Schema table names a table Defaults ships", () -> {
            List<String> hits = new ArrayList<>();
            for (Class<?> table : Schema.class.getDeclaredClasses()) {
                for (java.lang.reflect.Field f : table.getDeclaredFields()) {
                    if (!f.getName().endsWith("FILE")) {
                        continue;
                    }
                    try {
                        String file = (String) f.get(null);
                        if (!Defaults.templates().containsKey(file)) {
                            hits.add(table.getSimpleName() + "." + f.getName()
                                    + " = " + file + ", which Defaults never writes");
                        }
                    } catch (IllegalAccessException e) {
                        hits.add(table.getSimpleName() + "." + f.getName() + " unreadable");
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    S1 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "S3 origin tells a seeded biome row from a designer's", () -> {
            record Case(String scope, String origin, boolean designer) {}
            List<Case> cases = List.of(
                    new Case("biome", Schema.MobSpawns.Origin.SEEDED, false),
                    new Case("biome", Schema.MobSpawns.Origin.DESIGNER, true),
                    new Case("biome", "", false),
                    new Case("biome", "  ", false),
                    new Case("global", "", true),
                    new Case("tier", "", true),
                    new Case("tier", Schema.MobSpawns.Origin.DESIGNER, true));
            List<String> hits = new ArrayList<>();
            for (Case c : cases) {
                Map<String, String> row = Map.of(
                        Schema.MobSpawns.SCOPE, c.scope(),
                        Schema.MobSpawns.ORIGIN, c.origin());
                if (Schema.MobSpawns.isDesignerRow(row) != c.designer()) {
                    hits.add("scope=" + c.scope() + " origin='" + c.origin()
                            + "' expected designer=" + c.designer());
                }
            }
            if (Schema.MobSpawns.isDesignerRow(
                    Map.of(Schema.MobSpawns.SCOPE, "biome"))) {
                hits.add("a biome row with no origin column read as the designer's");
            }
            Map<String, String> stamped = new java.util.LinkedHashMap<>(
                    com.strata.domain.MobOps.stampDesigner(
                            Map.of(Schema.MobSpawns.WEIGHT, "10")));
            stamped.put(Schema.MobSpawns.SCOPE, "biome");
            if (!Schema.MobSpawns.isDesignerRow(stamped)) {
                hits.add("stampDesigner produced a row isDesignerRow rejects");
            }
            if (!"10".equals(stamped.get(Schema.MobSpawns.WEIGHT))) {
                hits.add("stampDesigner dropped a value it was given");
            }
            for (String hit : hits) {
                lines.add("    S3 " + hit);
            }
            return hits.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "S2 every Schema column exists in that table's shipped header", () -> {
            List<String> hits = new ArrayList<>();
            Map<String, String> templates = Defaults.templates();
            for (Class<?> table : Schema.class.getDeclaredClasses()) {
                if (table.isEnum() || table.isRecord() || table.isInterface()
                        || java.lang.reflect.Modifier.isAbstract(table.getModifiers())) {
                    continue;
                }
                List<String> files = new ArrayList<>();
                List<String> columns = new ArrayList<>();
                for (java.lang.reflect.Field f : table.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    try {
                        String value = (String) f.get(null);
                        if (f.getName().endsWith("FILE")) {
                            files.add(value);
                        } else {
                            columns.add(value);
                        }
                    } catch (IllegalAccessException | ClassCastException e) {
                        hits.add(table.getSimpleName() + "." + f.getName() + " unreadable");
                    }
                }
                for (String file : files) {
                    String template = templates.get(file);
                    if (template == null) {
                        continue;  // S1 owns that failure
                    }
                    String header = template.lines()
                            .map(String::strip)
                            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                            .findFirst().orElse("");
                    List<String> actual = List.of(header.split(","));
                    for (String column : columns) {
                        if (Schema.SCALING_OPTIONAL_COLUMNS.contains(column)) {
                            continue;
                        }
                        if (!actual.contains(column)) {
                            hits.add(table.getSimpleName() + " names '" + column
                                    + "' but " + file + " ships [" + header + "]");
                        }
                    }
                }
            }
            for (String hit : hits) {
                lines.add("    S2 " + hit);
            }
            return hits.isEmpty();
        });

        write(dir, "equipment_move.csv",
                "variant_name,scope,scope_id,slot,item,chance_pct,mode\n"
                        + "husk_frosty,tier,TEMP,mainhand,iron_sword,60,\n");
        TableWriter.upsert(dir.resolve("equipment_move.csv"), Map.of(
                "variant_name", "husk_frosty", "scope", "tier", "scope_id", "TEMP",
                "slot", "head", "item", "iron_sword"), Map.of("chance_pct", "30"));
        TableWriter.delete(dir.resolve("equipment_move.csv"), Map.of(
                "variant_name", "husk_frosty", "scope", "tier", "scope_id", "TEMP",
                "slot", "mainhand", "item", "iron_sword"));
        SelfTestHarness.check(lines, tally,
                "B1 changing the slot deletes the old key, one row remains", () -> {
            boolean moved = com.strata.domain.EquipmentOps.equipmentRowMoved(
                    "mainhand", "iron_sword", "head", "iron_sword");
            boolean sameKey = !com.strata.domain.EquipmentOps.equipmentRowMoved(
                    "mainhand", "iron_sword", "mainhand", "iron_sword");
            var rows = CsvTable.read(dir.resolve("equipment_move.csv"));
            return moved && sameKey && rows.size() == 1
                    && rows.get(0).get("slot").equals("head")
                    && rows.get(0).getOrDefault("item", "").equals("iron_sword");
        });

        write(dir, "loot_variant_scope.csv",
                "scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct,condition_type,condition_value\n"
                        + "tier,TEMP,minecraft:husk,,replace,0,0,iron_ingot,1,1,50,,\n"
                        + "tier,TEMP,minecraft:husk,husk_frosty,replace,0,0,frost_sword,1,1,50,,\n"
                        + "tier,TEMP,minecraft:husk,husk_snowy,replace,0,0,snow_sword,1,1,50,,\n");
        TableWriter.delete(dir.resolve("loot_variant_scope.csv"), Map.of(
                "scope", "tier", "scope_id", "TEMP", "entity", "minecraft:husk",
                "variant_name", "husk_frosty", "item", "frost_sword"));
        SelfTestHarness.check(lines, tally,
                "B2 deleting one variant's scoped rows keeps base and sibling rows", () -> {
            boolean frosty = com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "husk_frosty",
                    "tier", "TEMP", "minecraft:husk", "husk_frosty");
            boolean baseMiss = !com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "husk_frosty",
                    "tier", "TEMP", "minecraft:husk", "");
            boolean siblingMiss = !com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "husk_frosty",
                    "tier", "TEMP", "minecraft:husk", "husk_snowy");
            var rows = CsvTable.read(dir.resolve("loot_variant_scope.csv"));
            boolean base = rows.stream().anyMatch(r -> r.get("item").equals("iron_ingot"));
            boolean sibling = rows.stream().anyMatch(r -> r.get("item").equals("snow_sword"));
            boolean gone = rows.stream().noneMatch(r -> r.get("item").equals("frost_sword"));
            return frosty && baseMiss && siblingMiss
                    && rows.size() == 2 && base && sibling && gone;
        });

        SelfTestHarness.check(lines, tally,
                "RM1 a plain mob can be removed only from a named tier or biome", () ->
                com.strata.domain.MobOps.removableFromScope("tier", "TEMP")
                        && com.strata.domain.MobOps.removableFromScope("biome", "minecraft:plains")
                        && !com.strata.domain.MobOps.removableFromScope("global", "")
                        && !com.strata.domain.MobOps.removableFromScope("tier", "")
                        && !com.strata.domain.MobOps.removableFromScope("tier", " ")
                        && !com.strata.domain.MobOps.removableFromScope(null, "TEMP"));
        SelfTestHarness.check(lines, tally,
                "RM2 removing a plain mob keeps its variants and the other mobs", () -> {
            boolean plain = com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "",
                    "tier", "TEMP", "minecraft:husk", "");
            boolean variantKept = !com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "",
                    "tier", "TEMP", "minecraft:husk", "husk_frosty");
            boolean otherMobKept = !com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "",
                    "tier", "TEMP", "minecraft:zombie", "");
            boolean otherTierKept = !com.strata.domain.VariantOps.deletesScopedRow(
                    "tier", "TEMP", "minecraft:husk", "",
                    "tier", "OTHER", "minecraft:husk", "");
            return plain && variantKept && otherMobKept && otherTierKept;
        });

        SelfTestHarness.check(lines, tally,
                "CAT1 one category mapping: MISC is CREATURE, the rest map to themselves", () -> {
            if (com.strata.game.EntityCategory.forVanillaCategory("MISC")
                    != com.strata.model.SpawnCategory.CREATURE) {
                return false;
            }
            for (var category : com.strata.model.SpawnCategory.values()) {
                if (com.strata.game.EntityCategory.forVanillaCategory(category.name()) != category) {
                    lines.add("    CAT1 " + category.name() + " did not map to itself");
                    return false;
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "AB1 the editor refuses exactly the ability rows the loader skips", () -> {
            var p = (java.util.function.Function<String[], String>) c ->
                    com.strata.runtime.MobAbilities.rowProblem(c[0], c[1], c[2], c[3], c[4]);
            boolean refused = p.apply(new String[]{"heal", "", "2", "", ""}) != null
                    && p.apply(new String[]{"aura_players", "minecraft:slowness", "1", "0", "8"}) != null
                    && p.apply(new String[]{"effect", "", "1", "", ""}) != null
                    && p.apply(new String[]{"damage_immunity", "", "", "", ""}) != null
                    && p.apply(new String[]{"heal", "", "abc", "20", ""}) != null
                    && p.apply(new String[]{"nonsense", "", "", "", ""}) != null;
            boolean passed = p.apply(new String[]{"heal", "", "2", "20", ""}) == null
                    && p.apply(new String[]{"aura_mobs", "minecraft:speed", "1", "40", "6"}) == null
                    && p.apply(new String[]{"sun_immune", "", "", "", ""}) == null
                    && p.apply(new String[]{"effect", "minecraft:regeneration", "0", "", ""}) == null
                    && p.apply(new String[]{"damage_immunity", "#minecraft:is_fire", "", "", ""}) == null;
            return refused && passed;
        });

        SelfTestHarness.check(lines, tally,
                "SC1 a condition set with a duplicate type or a blank value is refused", () -> {
            var ops = (java.util.function.Function<String[][], String>) set ->
                    com.strata.domain.SpawnRulesOps.problemWith(java.util.Arrays.asList(set));
            boolean refused = ops.apply(new String[][]{{"time", "day"}, {"time", "night"}}) != null
                    && ops.apply(new String[][]{{"moon", "1"}, {"moon_phase", "3"}}) != null
                    && ops.apply(new String[][]{{"light", " "}}) != null
                    && ops.apply(new String[][]{{"colour", "red"}}) != null;
            boolean passed = ops.apply(new String[][]{{"time", "night"}, {"light", "0-7"}}) == null
                    && ops.apply(new String[][]{}) == null;
            return refused && passed;
        });

    }

    private static void runTestsPart03(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "SC2 a condition value the evaluator cannot read is refused on save", () -> {
            var ops = (java.util.function.Function<String[], String>) c ->
                    com.strata.domain.SpawnRulesOps.problemWith(
                            java.util.List.<String[]>of(c));
            boolean refused = ops.apply(new String[]{"y_level", "60+"}) != null
                    && ops.apply(new String[]{"light", "dark"}) != null
                    && ops.apply(new String[]{"time", "noon"}) != null
                    && ops.apply(new String[]{"weather", "snow"}) != null
                    && ops.apply(new String[]{"moon_phase", "1,x"}) != null
                    && ops.apply(new String[]{"distance", "far"}) != null
                    && ops.apply(new String[]{"structure", "Not An Id"}) != null;
            boolean passed = ops.apply(new String[]{"y_level", "-64-0"}) == null
                    && ops.apply(new String[]{"light", "0-7"}) == null
                    && ops.apply(new String[]{"time", "night"}) == null
                    && ops.apply(new String[]{"time", "13000-23000"}) == null
                    && ops.apply(new String[]{"weather", "thunder"}) == null
                    && ops.apply(new String[]{"moon_phase", "1,3"}) == null
                    && ops.apply(new String[]{"distance", "500+"}) == null
                    && ops.apply(new String[]{"structure", "minecraft:stronghold"}) == null
                    && ops.apply(new String[]{"surface", "underground"}) == null;
            return refused && passed;
        });

        SelfTestHarness.check(lines, tally,
                "EQ1 an equipment chance the runtime would skip is refused, any positive one is kept", () ->
                com.strata.domain.EquipmentOps.equipmentChanceProblem(-5) != null
                        && com.strata.domain.EquipmentOps.equipmentChanceProblem(0) != null
                        && com.strata.domain.EquipmentOps.equipmentChanceProblem(1) == null
                        && com.strata.domain.EquipmentOps.equipmentChanceProblem(100) == null
                        && com.strata.domain.EquipmentOps.equipmentChanceProblem(250) == null);

        SelfTestHarness.check(lines, tally,
                "EQ6 a new item in the game's slot keeps the game's equipment as a candidate", () -> {
            var bow = new com.strata.domain.EquipmentOps.EquipmentRow("mainhand", "minecraft:bow", 100, true);
            var boots = new com.strata.domain.EquipmentOps.EquipmentRow("feet", "minecraft:iron_boots", 100, true);
            var own = new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 50, false);
            var rows = List.of(bow, boots, own);
            var keep = com.strata.domain.EquipmentOps.gameEquipmentToKeep(
                    null, "mainhand", "minecraft:iron_sword", rows);
            boolean keepsBow = keep.size() == 1 && keep.getFirst().equals(bow);
            boolean editKeepsNone = com.strata.domain.EquipmentOps.gameEquipmentToKeep(
                    own, "mainhand", "minecraft:iron_sword", rows).isEmpty();
            boolean sameItemNone = com.strata.domain.EquipmentOps.gameEquipmentToKeep(
                    null, "mainhand", "minecraft:bow", rows).isEmpty();
            boolean otherSlotNone = com.strata.domain.EquipmentOps.gameEquipmentToKeep(
                    null, "head", "minecraft:gold_helmet", rows).isEmpty();
            return keepsBow && editKeepsNone && sameItemNone && otherSlotNone;
        });

        SelfTestHarness.check(lines, tally,
                "EQ7 every equipment field is explained, and the typed chance is judged as the save judges it", () -> {
            var tips = java.util.List.of(
                    com.strata.domain.EquipmentOps.EquipmentField.SLOT,
                    com.strata.domain.EquipmentOps.EquipmentField.ITEM,
                    com.strata.domain.EquipmentOps.EquipmentField.CHANCE)
                    .stream().map(com.strata.domain.EquipmentOps::equipmentTip).toList();
            boolean explained = tips.stream().noneMatch(String::isBlank)
                    && tips.stream().distinct().count() == 3;
            boolean chanceSays = tips.get(2).contains("relative odds")
                    && tips.get(2).contains("empty-handed")
                    && tips.get(1).contains("registry");
            var own = new com.strata.domain.EquipmentOps.EquipmentRow(
                    "mainhand", "minecraft:iron_sword", 25, false);
            var problem = (java.util.function.Function<String, String>) typed ->
                    com.strata.domain.EquipmentOps.equipmentChanceProblem(own, "mainhand",
                            "minecraft:iron_sword", typed);
            boolean judged = problem.apply("0") != null
                    && problem.apply("-5") != null
                    && problem.apply("") == null
                    && problem.apply("abc") == null
                    && problem.apply("60") == null
                    && problem.apply("250") == null;
            boolean fresh = com.strata.domain.EquipmentOps.equipmentChanceProblem(
                    own, "head", "minecraft:iron_sword", "") != null
                    && com.strata.domain.EquipmentOps.equipmentChanceProblem(
                            null, "mainhand", "minecraft:iron_sword", "0") != null
                    && com.strata.domain.EquipmentOps.equipmentChanceProblem(
                            null, "mainhand", "minecraft:iron_sword", "25") == null;
            return explained && chanceSays && judged && fresh;
        });

        SelfTestHarness.check(lines, tally,
                "EQ8 the equipment form opens on the row it was given, and on nothing of its own", () -> {
            var nullForm = com.strata.domain.EquipmentOps.equipmentForm(null);
            boolean fresh = nullForm.equals(com.strata.domain.EquipmentOps.NEW_GEAR_FORM)
                    && nullForm.editing() == null
                    && nullForm.from().isEmpty()
                    && nullForm.slot().equals(
                            com.strata.domain.EquipmentOps.SLOT_CHOICES.getFirst());
            var own = new com.strata.domain.EquipmentOps.EquipmentRow(
                    "head", "minecraft:diamond_helmet", 40, false);
            var ownForm = com.strata.domain.EquipmentOps.equipmentForm(own);
            boolean editing = ownForm.editing() != null
                    && ownForm.editing().equals(own)
                    && ownForm.slot().equals("head")
                    && ownForm.chance().equals("40")
                    && ownForm.from().isEmpty();
            var game = new com.strata.domain.EquipmentOps.EquipmentRow(
                    "mainhand", "minecraft:trident", 6.25,
                    com.strata.domain.ScopeLayers.FROM_GAME);
            var gameForm = com.strata.domain.EquipmentOps.equipmentForm(game);
            boolean inherited = gameForm.editing() == null
                    && gameForm.from().equals("vanilla")
                    && gameForm.item().equals("minecraft:trident")
                    && gameForm.slot().equals("mainhand");
            var base = new com.strata.domain.EquipmentOps.EquipmentRow(
                    "chest", "minecraft:elytra", 100,
                    com.strata.domain.ScopeLayers.FROM_BASE);
            boolean fromBase = com.strata.domain.EquipmentOps.equipmentForm(base)
                    .from().equals("base mob");
            var varied = new com.strata.domain.EquipmentOps.EquipmentRow(
                    "head", "minecraft:iron_helmet", 0.36,
                    com.strata.domain.ScopeLayers.FROM_GAME, "", "0.52", "8.5", "50", true);
            var variedForm = com.strata.domain.EquipmentOps.equipmentForm(varied);
            boolean variedOk = variedForm.scaled() && variedForm.chance().equals("0.36")
                    && variedForm.hard().equals("0.52") && variedForm.drop().equals("8.5")
                    && variedForm.enchant().equals("50") && variedForm.item().equals("minecraft:iron_helmet");
            return fresh && editing && inherited && fromBase && variedOk;
        });

        SelfTestHarness.check(lines, tally,
                "LT1 loot numbers the runtime would not use are refused", () ->
                com.strata.domain.LootOps.entryProblem("add", "5", "2", "50") != null
                        && com.strata.domain.LootOps.entryProblem("add", "1", "1", "150") != null
                        && com.strata.domain.LootOps.entryProblem("replace", "1", "1", "0") != null
                        && com.strata.domain.LootOps.entryProblem("add", "1", "3", "100") == null
                        && com.strata.domain.LootOps.entryProblem("add", "", "", "") == null
                        && com.strata.domain.LootOps.entryProblem("deny", "0", "0", "0") == null);

        SelfTestHarness.check(lines, tally,
                "AM1 a blank name is a plain mob, any other name is a variant", () ->
                !com.strata.domain.VariantOps.isVariantName("")
                        && !com.strata.domain.VariantOps.isVariantName("  ")
                        && !com.strata.domain.VariantOps.isVariantName(null)
                        && com.strata.domain.VariantOps.isVariantName("fire_zombie"));
        write(dir, "mob_spawns_addmob.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n");
        for (String added : List.of("minecraft:skeleton", "minecraft:zombie", "minecraft:skeleton")) {
            for (var prior : CsvTable.read(dir.resolve("mob_spawns_addmob.csv"))) {
                if (com.strata.domain.VariantOps.clearsPriorSpawnRow(
                        "tier", "TEMP", "", null,
                        prior.get("scope"), prior.get("scope_id"), prior.get("variant_name"),
                        prior.get("entity"))) {
                    TableWriter.delete(dir.resolve("mob_spawns_addmob.csv"), prior);
                }
            }
            TableWriter.upsert(dir.resolve("mob_spawns_addmob.csv"),
                    Map.of("scope", "tier", "scope_id", "TEMP", "entity", added, "variant_name", ""),
                    Map.of("weight", "10", "min_count", "1", "max_count", "4",
                            "enabled", "true", "is_variant", "false", "variant_mode", ""));
        }
        SelfTestHarness.check(lines, tally,
                "AM2 adding skeleton, zombie, then skeleton again leaves two plain rows", () -> {
            var rows = CsvTable.read(dir.resolve("mob_spawns_addmob.csv"));
            return rows.size() == 2
                    && rows.stream().anyMatch(r -> "minecraft:skeleton".equals(r.get("entity")))
                    && rows.stream().anyMatch(r -> "minecraft:zombie".equals(r.get("entity")));
        });

        write(dir, "mob_spawns_named.csv",
                "scope,scope_id,entity,weight,min_count,max_count,enabled,coefficient,is_variant,variant_name,variant_mode\n"
                        + "tier,TEMP,minecraft:skeleton,10,1,4,true,1.0,false,,\n"
                        + "tier,TEMP,minecraft:zombie,10,1,4,true,1.0,false,,\n");
        for (String[] save : List.of(
                new String[]{null, "skel_archer"},
                new String[]{"", "bone_lord"})) {
            for (var prior : CsvTable.read(dir.resolve("mob_spawns_named.csv"))) {
                if (com.strata.domain.VariantOps.clearsOnSave("tier", "TEMP", save[0], save[1],
                        "minecraft:skeleton", prior.get("scope"), prior.get("scope_id"),
                        prior.get("variant_name"), prior.get("entity"))) {
                    TableWriter.delete(dir.resolve("mob_spawns_named.csv"), prior);
                }
            }
            TableWriter.upsert(dir.resolve("mob_spawns_named.csv"),
                    Map.of("scope", "tier", "scope_id", "TEMP", "entity", "minecraft:skeleton",
                            "variant_name", save[1]),
                    Map.of("weight", "10", "min_count", "1", "max_count", "4", "enabled", "true",
                            "is_variant", "true", "variant_mode", "add"));
        }
        SelfTestHarness.check(lines, tally,
                "PN1 a name typed on a plain mob replaces its plain row, a new variant does not", () -> {
            var rows = CsvTable.read(dir.resolve("mob_spawns_named.csv"));
            boolean plainSkeletonGone = rows.stream().noneMatch(r ->
                    "minecraft:skeleton".equals(r.get("entity")) && r.get("variant_name").isBlank());
            boolean zombieKept = rows.stream().anyMatch(r ->
                    "minecraft:zombie".equals(r.get("entity")) && r.get("variant_name").isBlank());
            boolean both = rows.stream().anyMatch(r -> "skel_archer".equals(r.get("variant_name")))
                    && rows.stream().anyMatch(r -> "bone_lord".equals(r.get("variant_name")));
            if (rows.size() != 3) {
                lines.add("    PN1 expected 3 rows, found " + rows.size());
            }
            return rows.size() == 3 && plainSkeletonGone && zombieKept && both;
        });

        SelfTestHarness.check(lines, tally,
                "PC1 a second plain copy in one scope is named creeper_1, then creeper_2", () -> {
            var rows = CsvTable.read(dir.resolve("mob_spawns_named.csv"));
            boolean zombieThere = com.strata.domain.VariantOps.hasPlainRow(
                    rows, "tier", "TEMP", "minecraft:zombie");
            boolean otherTier = !com.strata.domain.VariantOps.hasPlainRow(
                    rows, "tier", "OTHER", "minecraft:zombie");
            boolean variantIsNotPlain = !com.strata.domain.VariantOps.hasPlainRow(
                    rows, "tier", "TEMP", "minecraft:skeleton");
            boolean names = "creeper_1".equals(com.strata.domain.VariantOps.copyName(
                            "minecraft:creeper", java.util.Set.of()))
                    && "creeper_2".equals(com.strata.domain.VariantOps.copyName(
                            "minecraft:creeper", java.util.Set.of("creeper_1")));
            return zombieThere && otherTier && variantIsNotPlain && names;
        });

        SelfTestHarness.check(lines, tally,
                "IN1 a variant's drops apply to that variant only, not the plain mob", () -> {
            Path lootDir = Files.createTempDirectory("strata-loot-instance");
            try {
                write(lootDir, "loot.csv",
                        "scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct\n"
                                + "global,,minecraft:zombie,,add,0,0,minecraft:bone,1,1,100\n"
                                + "global,,minecraft:zombie,fire_zombie,add,0,0,minecraft:blaze_rod,1,1,100\n");
                var loot = Tables.loadAll(lootDir).globalLoot();
                if (loot.size() != 2) {
                    lines.add("    IN1 expected 2 overrides, found " + loot.size());
                    return false;
                }
                var plain = loot.stream().filter(o -> o.variantName().isEmpty()).findFirst().orElseThrow();
                var fire = loot.stream().filter(o -> o.variantName().equals("fire_zombie")).findFirst().orElseThrow();
                String defaultTag = com.strata.model.Variant.defaultNameFor("minecraft:zombie");
                return plain.appliesTo("minecraft:zombie", "")
                        && plain.appliesTo("minecraft:zombie", defaultTag)
                        && !plain.appliesTo("minecraft:zombie", "fire_zombie")
                        && fire.appliesTo("minecraft:zombie", "fire_zombie")
                        && !fire.appliesTo("minecraft:zombie", "");
            } finally {
                deleteRec(lootDir);
            }
        });



        SelfTestHarness.check(lines, tally,
                "PS1 the goal set choice has its own table; equipment.csv holds only equipment", () -> {
            boolean own = Schema.MobGoalSets.FILE.equals("mob_goal_sets.csv")
                    && Schema.MobGoalSets.GOAL_SET.equals("goal_set");
            boolean noColumn = java.util.Arrays.stream(Schema.Equipment.class.getFields())
                    .noneMatch(f -> f.getName().equals("GOAL_SET"));
            boolean carried = com.strata.domain.TierOps.INSTANCE_TABLES.contains(Schema.MobGoalSets.FILE);
            return own && noColumn && carried;
        });

        SelfTestHarness.check(lines, tally,
                "AP1 every launch loads the active profile, shipped by default", () -> {
            Path profRoot = Files.createTempDirectory("strata-active-profile");
            Path configDir = Files.createTempDirectory("strata-active-config");
            try {
                write(profRoot, "tiers.csv", "name,level\nEDITED,9\n");
                boolean noShippedLeavesAlone = ProfileManager.loadActiveAtLaunch(profRoot, configDir).result()
                        == ProfileManager.Result.NOT_FOUND
                        && Files.readString(profRoot.resolve("tiers.csv")).contains("EDITED")
                        && !ProfileManager.hasUnsavedEdits(profRoot);
                Path shipped = profRoot.resolve("profiles").resolve(ProfileManager.SHIPPED);
                Files.createDirectories(shipped);
                write(shipped, "tiers.csv", "name,level\nSHIPPED,1\n");
                boolean unsavedSeen = ProfileManager.hasUnsavedEdits(profRoot);
                var shippedLoad = ProfileManager.loadActiveAtLaunch(profRoot, configDir);
                boolean shippedLoaded = shippedLoad.result() == ProfileManager.Result.OK
                        && ProfileManager.SHIPPED.equals(shippedLoad.profile())
                        && Files.readString(profRoot.resolve("tiers.csv")).contains("SHIPPED")
                        && !ProfileManager.hasUnsavedEdits(profRoot);
                Path pvp = profRoot.resolve("profiles").resolve("pvp");
                Files.createDirectories(pvp);
                write(pvp, "tiers.csv", "name,level\nPVP,5\n");
                Files.writeString(profRoot.resolve("active-profile.txt"), "pvp\n");
                var pvpLoad = ProfileManager.loadActiveAtLaunch(profRoot, configDir);
                boolean activeWins = "pvp".equals(pvpLoad.profile())
                        && Files.readString(profRoot.resolve("tiers.csv")).contains("PVP");
                return noShippedLeavesAlone && unsavedSeen && shippedLoaded && activeWins;
            } finally {
                deleteRec(profRoot);
                deleteRec(configDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "AP2 a save asks only when it replaces a profile that is not active", () -> {
            Path profRoot = Files.createTempDirectory("strata-save-profile");
            try {
                Files.createDirectories(profRoot.resolve("profiles").resolve("pvp"));
                Files.createDirectories(profRoot.resolve("profiles").resolve(ProfileManager.SHIPPED));
                boolean activeSilent = !ProfileManager.saveReplacesOther(profRoot, ProfileManager.SHIPPED);
                boolean otherAsks = ProfileManager.saveReplacesOther(profRoot, "pvp");
                boolean newSilent = !ProfileManager.saveReplacesOther(profRoot, "fresh")
                        && !ProfileManager.saveReplacesOther(profRoot, "../pvp");
                Files.writeString(profRoot.resolve("active-profile.txt"), "pvp\n");
                boolean follows = !ProfileManager.saveReplacesOther(profRoot, "pvp")
                        && ProfileManager.saveReplacesOther(profRoot, ProfileManager.SHIPPED);
                return activeSilent && otherAsks && newSilent && follows;
            } finally {
                deleteRec(profRoot);
            }
        });

        SelfTestHarness.check(lines, tally,
                "GC1 a goal condition holds exactly when it should", () -> {
            var night = new com.strata.model.GoalCondition.State(false, 80, false, false, -1);
            var hurt = new com.strata.model.GoalCondition.State(true, 30, true, true, 5);
            var c = (java.util.function.BiFunction<String, String, com.strata.model.GoalCondition>)
                    com.strata.model.GoalCondition::of;
            boolean tests = c.apply("night", "").test(night) && !c.apply("night", "").test(hurt)
                    && c.apply("health_below", "50").test(hurt) && !c.apply("health_below", "50").test(night)
                    && c.apply("health_above", "50").test(night)
                    && c.apply("raining", "").test(hurt) && c.apply("in_water", "").test(hurt)
                    && c.apply("target_within", "8").test(hurt) && !c.apply("target_within", "8").test(night)
                    && c.apply("", "").test(night) && c.apply("", "").test(hurt);
            boolean problems = com.strata.model.GoalCondition.problem("health_below", "150") != null
                    && com.strata.model.GoalCondition.problem("target_within", "0") != null
                    && com.strata.model.GoalCondition.problem("bogus", "") != null
                    && com.strata.model.GoalCondition.problem("health_below", "x") != null
                    && com.strata.model.GoalCondition.problem("night", "anything") == null;
            boolean badReadsAlways = c.apply("health_below", "x").type()
                    == com.strata.model.GoalCondition.Type.ALWAYS;
            return tests && problems && badReadsAlways;
        });



        SelfTestHarness.check(lines, tally,
                "GE1 a slot with a row is decided by it, and the list shows no inherited equipment there", () -> {
            var bowGame = List.of(new com.strata.domain.EquipmentOps.GameEquipment("mainhand", "minecraft:bow"),
                    new com.strata.domain.EquipmentOps.GameEquipment("head", "minecraft:iron_helmet"));
            var bow25 = List.of(Map.of("slot", "mainhand", "item", "minecraft:bow", "chance_pct", "25"));
            var rows = com.strata.domain.EquipmentOps.equipmentRows(bow25, List.of(), List.of(), bowGame);
            boolean decided = rows.size() == 2
                    && rows.get(0).item().equals("minecraft:bow") && rows.get(0).chancePct() == 25
                    && !rows.get(0).inherited()
                    && rows.get(1).slot().equals("head") && rows.get(1).inherited()
                    && com.strata.domain.EquipmentOps.availableForSlot(25, 0) == 75;
            var denyBow = List.of(Map.of("slot", "mainhand", "item", "minecraft:bow", "mode", "deny",
                    "chance_pct", "0"));
            boolean denied = com.strata.domain.EquipmentOps.equipmentRows(denyBow, List.of(), List.of(),
                    bowGame).stream().noneMatch(r -> r.slot().equals("mainhand"));
            java.util.function.Function<Integer, com.strata.runtime.MobEquipment.Loadout> only = slot -> {
                List<List<com.strata.runtime.MobEquipment.Choice>> slots = new java.util.ArrayList<>();
                for (int i = 0; i < 6; i++) slots.add(List.of());
                return new com.strata.runtime.MobEquipment.Loadout(slots, java.util.Set.of(slot));
            };
            var denyOnly = only.apply(0);
            boolean empties = denyOnly.decides(0) && !denyOnly.decides(1)
                    && denyOnly.slots().get(0).isEmpty();
            return decided && denied && empties;
        });

        SelfTestHarness.check(lines, tally,
                "GE2 a variant inherits its base mob's equipment, behavior, and abilities where it sets none", () -> {
            var skeleton = com.strata.model.Variant.of("minecraft_skeleton_default", "minecraft:skeleton");
            var frosty = com.strata.model.Variant.of("frosty", "minecraft:skeleton");
            boolean names = skeleton.inheritsFrom().isEmpty()
                    && "minecraft_skeleton_default".equals(frosty.inheritsFrom());
            Map<String, String> chosenGoalSets = Map.of(
                    com.strata.runtime.ScopeIndex.key("", "", "minecraft_skeleton_default"), "attack-players",
                    com.strata.runtime.ScopeIndex.key("", "", "brute"), "no-panic");
            boolean resolve = "attack-players".equals(com.strata.runtime.ScopeIndex.resolveOrBase(
                            chosenGoalSets, "frosty", "minecraft_skeleton_default", "T", "b"))
                    && "no-panic".equals(com.strata.runtime.ScopeIndex.resolveOrBase(
                            chosenGoalSets, "brute", "minecraft_skeleton_default", "", ""))
                    && com.strata.runtime.ScopeIndex.resolveOrBase(chosenGoalSets, "frosty", "", "", "") == null;
            java.util.function.Function<Integer, com.strata.runtime.MobEquipment.Loadout> only = slot -> {
                List<List<com.strata.runtime.MobEquipment.Choice>> slots = new java.util.ArrayList<>();
                for (int i = 0; i < 6; i++) slots.add(List.of());
                return new com.strata.runtime.MobEquipment.Loadout(slots, java.util.Set.of(slot));
            };
            var merged = com.strata.runtime.MobEquipment.merged(only.apply(2), only.apply(0));
            boolean equipment = merged.decides(0) && merged.decides(2) && !merged.decides(1)
                    && com.strata.runtime.MobEquipment.merged(null, only.apply(0)).decides(0);
            var sword = List.of(Map.of("slot", "mainhand", "item", "minecraft:iron_sword", "chance_pct", "100"));
            var helmet = List.of(Map.of("slot", "head", "item", "minecraft:gold_helmet", "chance_pct", "50"));
            var game = List.of(new com.strata.domain.EquipmentOps.GameEquipment("mainhand", "minecraft:bow"));
            var listed = com.strata.domain.EquipmentOps.equipmentRows(helmet, List.of(), sword, game);
            boolean list = listed.size() == 2
                    && listed.get(1).item().equals("minecraft:iron_sword")
                    && com.strata.domain.ScopeLayers.FROM_BASE.equals(listed.get(1).from())
                    && listed.stream().noneMatch(r -> r.item().equals("minecraft:bow"));
            return names && resolve && equipment && list;
        });

        SelfTestHarness.check(lines, tally,
                "BH1 every join of a mob with a variant gets its behavior, a fresh spawn its equipment too", () -> {
            return com.strata.runtime.MobEquipment.joinAction(true, "frosty")
                        == com.strata.runtime.MobEquipment.JoinAction.EQUIPMENT_AND_GOALS
                    && com.strata.runtime.MobEquipment.joinAction(false, "frosty")
                        == com.strata.runtime.MobEquipment.JoinAction.GOALS
                    && com.strata.runtime.MobEquipment.joinAction(false, "")
                        == com.strata.runtime.MobEquipment.JoinAction.NOTHING;
        });


        SelfTestHarness.check(lines, tally,
                "VB1 a variant can keep, replace, or turn off what it inherits", () -> {
            boolean variant = "".equals(com.strata.domain.VariantOps.goalSetOfChoice(
                            com.strata.domain.VariantOps.FROM_BASE_MOB, false))
                    && com.strata.model.GoalSets.NONE.equals(com.strata.domain.VariantOps.goalSetOfChoice(
                            com.strata.domain.VariantOps.GAME_DEFAULT, false))
                    && "leap-attack".equals(com.strata.domain.VariantOps.goalSetOfChoice("leap-attack", false))
                    && com.strata.domain.VariantOps.FROM_BASE_MOB.equals(
                            com.strata.domain.VariantOps.choiceOfGoalSet("", false))
                    && com.strata.domain.VariantOps.GAME_DEFAULT.equals(
                            com.strata.domain.VariantOps.choiceOfGoalSet(com.strata.model.GoalSets.NONE, false));
            boolean plain = "".equals(com.strata.domain.VariantOps.goalSetOfChoice(
                            com.strata.domain.VariantOps.FAMILY_DEFAULT, true))
                    && com.strata.model.GoalSets.NONE.equals(com.strata.domain.VariantOps.goalSetOfChoice(
                            com.strata.domain.VariantOps.GAME_DEFAULT, true))
                    && com.strata.domain.VariantOps.FAMILY_DEFAULT.equals(
                            com.strata.domain.VariantOps.choiceOfGoalSet("", true))
                    && com.strata.domain.VariantOps.defaultChoices(true).getFirst()
                            .equals(com.strata.domain.VariantOps.FAMILY_DEFAULT);
            boolean reserved = com.strata.domain.GoalsOps.nameProblem(
                    com.strata.model.GoalSets.NONE, "", List.of()) != null;
            var heal = new com.strata.domain.AbilitiesOps.AbilityRow("heal", "", "2", "20", "", true, true);
            var aura = new com.strata.domain.AbilitiesOps.AbilityRow("aura_players", "minecraft:slowness",
                    "0", "40", "6", true, true);
            var keep = com.strata.domain.AbilitiesOps.inheritedToKeep(List.of(heal, aura), heal);
            boolean abilities = keep.size() == 1 && keep.getFirst().ability().equals("aura_players")
                    && com.strata.domain.AbilitiesOps.inheritedToKeep(List.of(heal, aura), null).size() == 2
                    && com.strata.domain.AbilitiesOps.removeTurnsOff(heal)
                    && !com.strata.domain.AbilitiesOps.removeTurnsOff(
                            new com.strata.domain.AbilitiesOps.AbilityRow("heal", "", "2", "20", "", true));
            return variant && plain && reserved && abilities;
        });

        SelfTestHarness.check(lines, tally,
                "UP2 an upload that would leave out edits is caught, and the common config counts", () -> {
            Path configDir = Files.createTempDirectory("strata-upload-edits");
            Path root = configDir.resolve("strata");
            try {
                Path shipped = root.resolve("profiles").resolve(ProfileManager.SHIPPED);
                Files.createDirectories(shipped);
                write(root, "tiers.csv", "name,level\nA,1\n");
                write(shipped, "tiers.csv", "name,level\nA,1\n");
                boolean clean = !ProfileManager.uploadMissesEdits(root, ProfileManager.SHIPPED);
                write(root, "tiers.csv", "name,level\nA,2\n");
                boolean caught = ProfileManager.uploadMissesEdits(root, ProfileManager.SHIPPED)
                        && !ProfileManager.uploadMissesEdits(root, "pvp");
                write(root, "tiers.csv", "name,level\nA,1\n");
                Files.writeString(shipped.resolve("strata-common.toml"), "# note\nscaling = true\n");
                Files.writeString(configDir.resolve("strata-common.toml"), "#changed note\n\nscaling=true\n");
                boolean commentsIgnored = !ProfileManager.hasUnsavedEdits(root);
                Files.writeString(configDir.resolve("strata-common.toml"), "scaling = false\n");
                boolean settingSeen = ProfileManager.hasUnsavedEdits(root);
                return clean && caught && commentsIgnored && settingSeen;
            } finally {
                deleteRec(configDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "BT2 the clock tells day from night where the sky cannot", () -> {
            boolean clock = com.strata.model.GoalCondition.isDayTime(1000)
                    && !com.strata.model.GoalCondition.isDayTime(18000)
                    && com.strata.model.GoalCondition.isDayTime(23500)
                    && com.strata.model.GoalCondition.isDayTime(24000L * 5 + 6000)
                    && com.strata.model.GoalCondition.isDayTime(-500);
            return clock;
        });



        SelfTestHarness.check(lines, tally,
                "EX3 the goal set button reads the goal set in effect", () -> {
            var family = new com.strata.domain.GoalsOps.Effective(List.of(), "its family's goal set 'skeleton'",
                    false, "skeleton");
            var own = new com.strata.domain.GoalsOps.Effective(List.of(), "this mob's own list", true,
                    com.strata.domain.GoalsOps.OWN_LIST);
            var game = new com.strata.domain.GoalsOps.Effective(null, "zombie's own goals", false,
                    com.strata.domain.GoalsOps.GAME_GOALS);
            return "Goal set: skeleton (default)".equals(com.strata.domain.GoalsOps.goalSetLabel(family, ""))
                    && "Goal set: leap-attack".equals(com.strata.domain.GoalsOps.goalSetLabel(
                            new com.strata.domain.GoalsOps.Effective(List.of(), "goal set", false, "leap-attack"),
                            "leap-attack"))
                    && "Goal set: own list".equals(com.strata.domain.GoalsOps.goalSetLabel(own, ""))
                    && "Goal set: game goals".equals(com.strata.domain.GoalsOps.goalSetLabel(game,
                            com.strata.model.GoalSets.NONE));
        });

        SelfTestHarness.check(lines, tally,
                "FA4 the mob editor's AI box: the game's two lists, each goal with who and when", () -> {
            var hunt = com.strata.model.GoalRow.fromGame(com.strata.model.GameGoal.decode(
                    "t|2|NearestAttackableTargetGoal|net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal"
                            + "|target=minecraft:player"));
            var walk = com.strata.model.GoalRow.fromGame(com.strata.model.GameGoal.decode(
                    "g|7|WaterAvoidingRandomStrollGoal|net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal"
                            + "|speed=1.0"));
            var text = com.strata.domain.GoalsOps.goalsText(List.of(walk, hunt));
            boolean lists = text.get(0).startsWith("Target goals") && text.get(1).contains("2")
                    && text.get(1).contains("player") && text.get(2).startsWith("Goals")
                    && text.get(3).contains("7") && text.size() == 4;
            var off = com.strata.domain.GoalsOps.goalsText(List.of(walk.withEnabled(false)));
            boolean none = off.get(1).strip().equals("none") && off.get(3).endsWith("[off]");
            boolean brain = com.strata.domain.GoalsOps.goalsText(null).size() == 2
                    && com.strata.domain.GoalsOps.goalsText(null).get(0).startsWith("A Brain runs this mob");
            if (!(lists && none && brain)) {
                lines.add("    FA4 " + text + " " + off);
            }
            return lists && none && brain;
        });

        SelfTestHarness.check(lines, tally,
                "FA5 a goal a mob cannot run says why (a bat given a phantom's AI)", () -> {
            var walk = com.strata.model.GoalRow.fromGame(com.strata.model.GameGoal.decode(
                    "g|7|WaterAvoidingRandomStrollGoal|net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal"
                            + "|speed=1.0"));
            String sweep = "net.minecraft.world.entity.monster.Phantom$PhantomSweepAttackGoal";
            var phantom = com.strata.model.GoalRow.fromGame(com.strata.model.GameGoal.decode(
                    "g|1|PhantomSweepAttackGoal|" + sweep + "|"));
            java.util.Set<String> bat = java.util.Set.of("ANY");
            java.util.Set<String> zombie = java.util.Set.of("ANY", "PATHFINDER");
            String batWalk = com.strata.domain.GoalsOps.whyNot(walk, bat, java.util.Set.of());
            String batSweep = com.strata.domain.GoalsOps.whyNot(phantom, bat, java.util.Set.of());
            boolean ok = !walk.game() && phantom.game()
                    && batWalk != null && batWalk.contains("walks paths")
                    && com.strata.domain.GoalsOps.whyNot(walk, zombie, java.util.Set.of()) == null
                    && batSweep != null && batSweep.contains("another mob's")
                    && com.strata.domain.GoalsOps.whyNot(phantom, bat, java.util.Set.of(sweep)) == null
                    && com.strata.domain.GoalsOps.whyNot(walk, null, java.util.Set.of()) == null;
            if (!ok) {
                lines.add("    FA5 " + batWalk + " / " + batSweep);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "FA6 a mob's model, texture, and sounds: variant over base, tier and biome rows left out", () -> {
            List<Map<String, String>> rows = List.of(
                    Map.of("entity", "minecraft:phantom", "variant_name", "", "scope", "", "group", "model",
                            "key", "model", "value", "minecraft:bat"),
                    Map.of("entity", "minecraft:phantom", "variant_name", "", "scope", "", "group", "model",
                            "key", "sound_hurt", "value", "minecraft:entity.bat.hurt"),
                    Map.of("entity", "minecraft:phantom", "variant_name", "night_bat", "scope", "", "group", "model",
                            "key", "sound_hurt", "value", "minecraft:entity.vex.hurt"),
                    Map.of("entity", "minecraft:phantom", "variant_name", "night_bat", "scope", "tier",
                            "group", "model", "key", "model", "value", "minecraft:vex"),
                    Map.of("entity", "minecraft:phantom", "variant_name", "", "scope", "", "group", "type",
                            "key", "width", "value", "0.5"));
            var models = com.strata.runtime.MobModels.index(rows, Map.of("night_bat", "Night Bat"));
            boolean ok = "minecraft:bat".equals(com.strata.runtime.MobModels.serverValue(models,
                            "minecraft:phantom", "", "model"))
                    && "minecraft:entity.vex.hurt".equals(com.strata.runtime.MobModels.serverValue(models,
                            "minecraft:phantom", "night_bat", "sound_hurt"))
                    && "minecraft:bat".equals(com.strata.runtime.MobModels.serverValue(models,
                            "minecraft:phantom", "night_bat", "model"))
                    && models.size() == 4
                    && com.strata.runtime.MobModels.clientIndex(models).equals(
                            Map.of("minecraft:phantom|model", "minecraft:bat"));
            if (!ok) {
                lines.add("    FA6 " + models);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "FA7 the texture follows the model: a variant with its own model drops its base mob's texture", () -> {
            Map<String, String> idx = Map.of(
                    "minecraft:phantom|texture", "strata:textures/red_phantom.png",
                    "@Night Bat|model", "minecraft:bat",
                    "@Red Phantom|sound_hurt", "minecraft:entity.vex.hurt",
                    "@Painted Bat|model", "minecraft:bat",
                    "@Painted Bat|texture", "strata:textures/painted_bat.png");
            boolean runtime = com.strata.runtime.MobModels.texture(idx, "@Night Bat", "minecraft:phantom") == null
                    && "strata:textures/painted_bat.png".equals(
                            com.strata.runtime.MobModels.texture(idx, "@Painted Bat", "minecraft:phantom"))
                    && "strata:textures/red_phantom.png".equals(
                            com.strata.runtime.MobModels.texture(idx, "@Red Phantom", "minecraft:phantom"))
                    && "strata:textures/red_phantom.png".equals(
                            com.strata.runtime.MobModels.texture(idx, null, "minecraft:phantom"));
            var V = com.strata.domain.MobDataOps.Value.class;
            List<com.strata.domain.MobDataOps.Value> sheet = List.of(
                    new com.strata.domain.MobDataOps.Value("model", "model", "minecraft:phantom", "", "minecraft:bat"),
                    new com.strata.domain.MobDataOps.Value("model", "texture", "minecraft:textures/entity/phantom.png",
                            "strata:textures/red_phantom.png", ""));
            var shown = com.strata.domain.MobDataOps.textureFollowsModel(sheet, "minecraft:phantom",
                    Map.of("minecraft:bat", "minecraft:textures/entity/bat.png"));
            boolean editor = "minecraft:textures/entity/bat.png".equals(shown.get(1).effective())
                    && "game".equals(shown.get(1).source())
                    && com.strata.domain.MobDataOps.textureFollowsModel(sheet.subList(1, 2), "minecraft:phantom",
                            Map.of()).equals(sheet.subList(1, 2));
            if (!(runtime && editor)) {
                lines.add("    FA7 " + runtime + " " + shown + " " + V.getSimpleName());
            }
            return runtime && editor;
        });

    }

    private static void runTestsPart04(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "FA8 misc is creature, one coefficient, folds per view", () -> {
            boolean misc = com.strata.model.SpawnCategory.parse("misc") == com.strata.model.SpawnCategory.CREATURE
                    && com.strata.game.EntityCategory.forVanillaCategory("MISC")
                            == com.strata.model.SpawnCategory.parse("misc");
            var monster = new com.strata.model.Spawn(com.strata.model.SpawnCategory.MONSTER, "minecraft:bat", "",
                    20, 1, 1, true, 2.0, List.of(), com.strata.model.Source.tier("t"));
            var ambient = new com.strata.model.Spawn(com.strata.model.SpawnCategory.AMBIENT, "minecraft:bat", "",
                    20, 1, 1, true, 2.0, List.of(), com.strata.model.Source.tier("t"));
            Double coeff = com.strata.model.Spawn.coefficientIn(c -> c == com.strata.model.SpawnCategory.MONSTER
                    ? List.of(monster) : c == com.strata.model.SpawnCategory.AMBIENT ? List.of(ambient) : List.of(),
                    "minecraft:bat");
            boolean one = coeff != null && coeff == 2.0
                    && com.strata.model.Spawn.coefficientIn(c -> List.of(), "minecraft:bat") == null;
            String global = com.strata.client.screen.MobListScreen.foldKey("MobListScreen", "", "");
            boolean folds = global.equals("MobListScreen")
                    && !com.strata.client.screen.MobListScreen.foldKey("MobListScreen", "tier", "Creature").equals(global)
                    && !com.strata.client.screen.MobListScreen.foldKey("MobListScreen", "tier", "A")
                            .equals(com.strata.client.screen.MobListScreen.foldKey("MobListScreen", "biome", "A"));
            boolean loot = com.strata.model.LootEntry.MAX_COUNT == 1000;
            if (!(misc && one && folds && loot)) {
                lines.add("    FA8 " + misc + " " + coeff + " " + folds + " " + loot);
            }
            return misc && one && folds && loot;
        });

        SelfTestHarness.check(lines, tally,
                "FA9 help files: a mark, line ends, and a missing heading read safely; a config file overrides", () -> {
            var plain = com.strata.data.HelpText.parse("a.txt", "# Title\nOne\n\nTwo\n\n");
            var marked = com.strata.data.HelpText.parse("a.txt", "\uFEFF# Title\r\nOne\r\n\r\nTwo\r\n");
            boolean same = plain != null && plain.equals(marked)
                    && plain.text().equals(List.of("# Title", "One", "", "Two"))
                    && plain.heading().equals("Title");
            boolean refused = com.strata.data.HelpText.parse("b.txt", "No heading here\n# Late") == null
                    && com.strata.data.HelpText.parse("b.txt", "") == null
                    && com.strata.data.HelpText.parse("b.txt", "#  \nbody") == null
                    && com.strata.data.HelpText.parse("b.txt", null) == null;
            boolean override;
            Path help = null;
            try {
                help = Files.createTempDirectory("strata-help");
                Files.writeString(help.resolve("07-equipment.txt"), "# Equipment\nEdited.\n");
                Files.writeString(help.resolve("99-house-rules.txt"), "# House rules\nNo griefing.\n");
                Files.writeString(help.resolve("01-getting-started.txt"), "broken, no heading\n");
                com.strata.data.HelpText.useDir(help);
                List<String> secs = com.strata.ui.screen.HelpLayout.sections();
                override = com.strata.ui.screen.HelpLayout.sectionText("Equipment").equals(List.of("# Equipment", "Edited."))
                        && secs.getLast().equals("House rules")
                        && com.strata.ui.screen.HelpLayout.sectionText("Getting started").size() > 2;
            } catch (java.io.IOException e) {
                override = false;
            } finally {
                com.strata.data.HelpText.useDir(null);
            }
            if (!(same && refused && override)) {
                lines.add("    FA9 " + same + " " + refused + " " + override + " " + plain);
            }
            return same && refused && override;
        });

        SelfTestHarness.check(lines, tally,
                "FA10 a save that fails partway puts every table it wrote back; a good one keeps them", () -> {
            try {
                Path d = Files.createTempDirectory("strata-rollback");
                Path a = d.resolve("a.csv");
                Path b = d.resolve("b.csv");
                Files.writeString(a, "k,v\nx,1\n");
                Files.writeString(b, "k,v\nx,1\n");
                com.strata.data.Rollback.close(true);
                new com.strata.domain.WriteReport();
                TableWriter.updateColumn(a, "k", "x", "v", "2");
                TableWriter.updateColumn(b, "k", "x", "v", "2");
                boolean wrote = Files.readString(a).contains("x,2");
                var stuck = com.strata.data.Rollback.close(false);
                boolean back = stuck.isEmpty() && Files.readString(a).equals("k,v\nx,1\n")
                        && Files.readString(b).equals("k,v\nx,1\n");
                new com.strata.domain.WriteReport();
                TableWriter.updateColumn(a, "k", "x", "v", "3");
                com.strata.data.Rollback.close(true);
                boolean kept = Files.readString(a).contains("x,3");
                TableWriter.updateColumn(b, "k", "x", "v", "4");
                com.strata.data.Rollback.close(false);
                boolean outside = Files.readString(b).contains("x,4");
                if (!(wrote && back && kept && outside)) {
                    lines.add("    FA10 " + wrote + " " + back + " " + kept + " " + outside);
                }
                return wrote && back && kept && outside;
            } catch (java.io.IOException e) {
                lines.add("    FA10 " + e);
                return false;
            }
        });

        SelfTestHarness.check(lines, tally,
                "FA11 a window another thread left open does not take this thread's rollback away", () -> {
            try {
                Path d = Files.createTempDirectory("strata-rollback2");
                Path a = d.resolve("a.csv");
                Files.writeString(a, "k,v\nx,1\n");
                com.strata.data.Rollback.close(true);
                // A server-side import (AssetSync#importFolder, as /strata assets reload runs it) makes a
                // report on the server thread. It must close its own window, or the editor's next failed
                // save on another thread has no rollback.
                Path server = d.resolve("server");
                Path in = AssetSync.importDir(server).resolve("entity");
                Files.createDirectories(in);
                Files.write(in.resolve("wolf.png"), pngNoise(64, 64));
                var imported = new java.util.concurrent.atomic.AtomicReference<List<String>>(List.of());
                Thread other = new Thread(() -> imported.set(AssetSync.importFolder(server)));
                other.start();
                other.join();
                boolean importedOk = imported.get().size() == 1 && imported.get().getFirst().endsWith("imported");
                new com.strata.domain.WriteReport();
                TableWriter.updateColumn(a, "k", "x", "v", "2");
                var stuck = com.strata.data.Rollback.close(false);
                boolean back = stuck.isEmpty() && Files.readString(a).equals("k,v\nx,1\n");
                if (!importedOk || !back) {
                    lines.add("    FA11 imported=" + imported.get() + " back=" + back + " file="
                            + Files.readString(a).strip());
                }
                return importedOk && back;
            } catch (java.io.IOException | InterruptedException e) {
                lines.add("    FA11 " + e);
                return false;
            }
        });

        SelfTestHarness.check(lines, tally,
                "FA11 a profile's tables replace the target's: one it lacks is removed, a stranger kept", () -> {
            try {
                Path from = Files.createTempDirectory("strata-profile-from");
                Path to = Files.createTempDirectory("strata-profile-to");
                Files.writeString(from.resolve("tiers.csv"), "name\nfrom\n");
                Files.writeString(to.resolve("tiers.csv"), "name\nold\n");
                Files.writeString(to.resolve("mob_data.csv"), "entity\nold\n");
                Files.writeString(to.resolve("my_notes.csv"), "mine\n");
                ProfileManager.copyTables(from, to);
                boolean ok = Files.readString(to.resolve("tiers.csv")).equals("name\nfrom\n")
                        && !Files.exists(to.resolve("mob_data.csv"))
                        && Files.exists(to.resolve("my_notes.csv"))
                        && Files.list(to).noneMatch(f -> f.toString().endsWith(".incoming"));
                if (!ok) {
                    lines.add("    FA11 " + Files.list(to).toList());
                }
                return ok;
            } catch (java.io.IOException e) {
                lines.add("    FA11 " + e);
                return false;
            }
        });

        SelfTestHarness.check(lines, tally,
                "FA12 comments between rows survive every save, with their row; a removed row's pass on", () -> {
            try {
                Path f = Files.createTempDirectory("strata-notes").resolve("t.csv");
                Files.writeString(f, "# The file's own notes\nk,v\n# about a\na,1\n# section two\nb,1\nc,1\n# the end\n");
                TableWriter.updateColumn(f, "k", "a", "v", "2");
                String one = Files.readString(f).replace("\r\n", "\n");
                boolean update = one.equals("# The file's own notes\nk,v\n# about a\na,2\n# section two\nb,1\nc,1\n# the end\n");
                TableWriter.delete(f, Map.of("k", "b"));
                String two = Files.readString(f).replace("\r\n", "\n");
                boolean delete = two.equals("# The file's own notes\nk,v\n# about a\na,2\n# section two\nc,1\n# the end\n");
                TableWriter.addColumn(f, "w");
                String three = Files.readString(f).replace("\r\n", "\n");
                boolean column = three.contains("# about a\na,2,\n# section two\nc,1,\n# the end");
                if (!(update && delete && column)) {
                    lines.add("    FA12 [" + one + "] [" + two + "] [" + three + "]");
                }
                return update && delete && column;
            } catch (java.io.IOException e) {
                lines.add("    FA12 " + e);
                return false;
            }
        });

        SelfTestHarness.check(lines, tally,
                "FA13 a help file's @screen lines hook a screen's ? and are not shown; an edit keeps them", () -> {
            var sec = com.strata.data.HelpText.parse("x.txt", "# Title\n@screen AScreen\n@screen BScreen\nBody\n");
            boolean parsed = sec != null && sec.screens().equals(List.of("AScreen", "BScreen"))
                    && sec.text().equals(List.of("# Title", "Body"));
            boolean shipped = "Equipment".equals(com.strata.data.HelpText.sectionFor("EquipmentScreen"))
                    && com.strata.data.HelpText.sectionFor("RegistryPickerScreen") == null;
            boolean kept;
            try {
                Path help = Files.createTempDirectory("strata-help-screens");
                Files.writeString(help.resolve("07-equipment.txt"), "# Equipment\nReworded.\n");
                Files.writeString(help.resolve("98-mine.txt"), "# Mine\n@screen TradesScreen\nMine.\n");
                com.strata.data.HelpText.useDir(help);
                kept = "Equipment".equals(com.strata.data.HelpText.sectionFor("EquipmentScreen"))
                        && "Trades".equals(com.strata.data.HelpText.sectionFor("TradesScreen"));
            } catch (java.io.IOException e) {
                kept = false;
            } finally {
                com.strata.data.HelpText.useDir(null);
            }
            if (!(parsed && shipped && kept)) {
                lines.add("    FA13 " + parsed + " " + shipped + " " + kept);
            }
            return parsed && shipped && kept;
        });

        SelfTestHarness.check(lines, tally,
                "FA14 a variant of a Special mob has its own Spawn as; unset, it follows its base mob", () -> {
            String phantom = "minecraft:phantom";
            String key = "||" + com.strata.model.SpecialSpawners.MOBS + "|" + phantom
                    + com.strata.model.SpecialSpawners.VARIANT_SEP;
            Map<String, String> none = Map.of();
            Map<String, String> normal = Map.of(key + "night_bat", "Normal");
            boolean follows = com.strata.model.SpecialSpawners.special(none, phantom, "night_bat")
                    == com.strata.model.SpecialSpawners.special(none, phantom);
            boolean own = !com.strata.model.SpecialSpawners.special(normal, phantom, "night_bat")
                    && com.strata.model.SpecialSpawners.special(normal, phantom)
                    && com.strata.model.SpecialSpawners.special(normal, phantom, "other")
                            == com.strata.model.SpecialSpawners.special(normal, phantom);
            boolean plain = !com.strata.model.SpecialSpawners.special(
                    Map.of("||" + com.strata.model.SpecialSpawners.MOBS + "|minecraft:zombie#z", "Special"),
                    "minecraft:zombie", "z");
            if (!(follows && own && plain)) {
                lines.add("    FA14 " + follows + " " + own + " " + plain);
            }
            return follows && own && plain;
        });

        SelfTestHarness.check(lines, tally,
                "FA15 the Spawning tab: a base mob may pick Special, a variant only a category; switches are words", () -> {
            var base = new com.strata.domain.MobDataOps.Target("minecraft:phantom", "", "", "");
            var variant = new com.strata.domain.MobDataOps.Target("minecraft:phantom", "night_bat", "", "");
            String cat = com.strata.domain.MobDataOps.CATEGORY;
            boolean ok = com.strata.domain.MobDataOps.spawningChoices(base, cat).contains("Special")
                    && !com.strata.domain.MobDataOps.spawningChoices(variant, cat).contains("Special")
                    && com.strata.domain.MobDataOps.spawningChoices(variant, com.strata.domain.MobDataOps.SCALING)
                            .equals(List.of("Default", "On", "Off"))
                    && com.strata.domain.MobDataOps.spawningChoices(base, com.strata.domain.MobDataOps.SPAWN_AS)
                            .equals(List.of("Normal", "Special"))
                    && com.strata.domain.MobDataOps.section(com.strata.domain.MobDataOps.SPAWNING, cat).equals("Spawning")
                    && com.strata.domain.CategoryOps.setCategory("minecraft:phantom", "night_bat", "Special")
                            .refusal() != null;
            if (!ok) {
                lines.add("    FA15 " + com.strata.domain.MobDataOps.spawningChoices(variant, cat));
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "PK1 an ability's parameter picker offers what that ability reads: effects, damage types, or none", () -> {
            boolean ok = true;
            for (var a : com.strata.runtime.MobAbilities.Ability.values()) {
                var kind = com.strata.domain.AbilitiesOps.parameterKind(a.name().toLowerCase());
                var want = a.needsEffect ? com.strata.domain.AbilitiesOps.ParameterKind.EFFECT
                        : a == com.strata.runtime.MobAbilities.Ability.DAMAGE_IMMUNITY
                        ? com.strata.domain.AbilitiesOps.ParameterKind.DAMAGE
                        : com.strata.domain.AbilitiesOps.ParameterKind.NONE;
                if (kind != want) {
                    lines.add("    PK1 " + a + " gave " + kind + ", want " + want);
                    ok = false;
                }
            }
            boolean unknown = com.strata.domain.AbilitiesOps.parameterKind("no_such_ability")
                    == com.strata.domain.AbilitiesOps.ParameterKind.NONE
                    && com.strata.domain.AbilitiesOps.parameterKind(null)
                    == com.strata.domain.AbilitiesOps.ParameterKind.NONE;
            boolean damage = com.strata.domain.AbilitiesOps.parameterKind("damage_immunity")
                    == com.strata.domain.AbilitiesOps.ParameterKind.DAMAGE;
            return ok && unknown && damage;
        });

        SelfTestHarness.check(lines, tally,
                "SV1 the scaling preview in real values: base x (1 + bonus), and no bonus the game skips", () -> {
            var linear = new com.strata.domain.ScalingOps.CurveForm("minecraft:generic.max_health",
                    "0.1", "1", "1", true);
            List<Double> b = com.strata.domain.ScalingOps.bonuses(linear);
            List<Double> v = com.strata.domain.ScalingOps.realValues(20, b, 1.0);
            boolean real = near(b, List.of(0.1, 0.5, 1.0, 2.0, 5.0))
                    && near(v, List.of(22.0, 30.0, 40.0, 60.0, 120.0));
            List<Double> neg = com.strata.domain.ScalingOps.bonuses(
                    new com.strata.domain.ScalingOps.CurveForm("x", "-0.1", "1", "1", true));
            boolean skipped = near(neg, List.of(0.0, 0.0, 0.0, 0.0, 0.0))
                    && near(com.strata.domain.ScalingOps.realValues(20, List.of(-0.5), 1.0), List.of(20.0));
            List<Double> late = com.strata.domain.ScalingOps.bonuses(
                    new com.strata.domain.ScalingOps.CurveForm("x", "0.1", "1", "5", true));
            boolean start = late.get(0) == 0.0 && Math.abs(late.get(1) - 0.1) < 1e-9;
            boolean coefficient = near(com.strata.domain.ScalingOps.realValues(20, List.of(0.5), 2.0),
                    List.of(40.0));
            boolean halfTyped = com.strata.domain.ScalingOps.bonuses(
                    new com.strata.domain.ScalingOps.CurveForm("x", "0.1", "abc", "1", true)) == null;
            if (!(real && skipped && start && coefficient && halfTyped)) {
                lines.add("    SV1 " + b + " " + v + " " + neg + " " + late);
            }
            return real && skipped && start && coefficient && halfTyped;
        });

        SelfTestHarness.check(lines, tally,
                "WS1 a tier's or biome's mob shows the equipment it inherits from a wider scope, and edits keep it", () -> {
            var biome = com.strata.domain.ScopeLayers.scopeLayers("biome", "minecraft:plains", "t1");
            boolean chain = biome.size() == 3 && biome.get(0).from().isEmpty()
                    && biome.get(1).scope().equals("tier") && biome.get(1).scopeId().equals("t1")
                    && biome.get(1).from().equals(com.strata.domain.ScopeLayers.FROM_TIER)
                    && biome.get(2).scope().isEmpty()
                    && biome.get(2).from().equals(com.strata.domain.ScopeLayers.FROM_GLOBAL)
                    && com.strata.domain.ScopeLayers.scopeLayers("tier", "t1", "").size() == 2
                    && com.strata.domain.ScopeLayers.scopeLayers("global", "", "").size() == 1
                    && com.strata.domain.ScopeLayers.scopeLayers("biome", "minecraft:plains", "").size() == 2;
            Map<String, String> helmet = Map.of("slot", "head", "item", "minecraft:iron_helmet", "chance_pct", "100");
            List<Map<String, String>> none = List.of();
            boolean first = com.strata.domain.ScopeLayers.firstWithRows(List.of(none, List.of(helmet))) == 1
                    && com.strata.domain.ScopeLayers.firstWithRows(List.of(none, none)) == -1;
            var game = List.of(new com.strata.domain.EquipmentOps.GameEquipment("head", "minecraft:leather_helmet"));
            var rows = com.strata.domain.EquipmentOps.equipmentRows(List.of(helmet),
                    com.strata.domain.ScopeLayers.FROM_GLOBAL, none, none, game);
            boolean shown = rows.size() == 1 && rows.get(0).item().equals("minecraft:iron_helmet")
                    && rows.get(0).inherited() && rows.get(0).sourceLabel().equals("from global");
            boolean adopt = com.strata.domain.EquipmentOps.toAdopt(List.of(none, List.of(helmet)))
                    .equals(List.of(helmet))
                    && com.strata.domain.EquipmentOps.toAdopt(List.of(List.of(helmet), List.of(helmet))).isEmpty()
                    && com.strata.domain.EquipmentOps.toAdopt(List.of(none, none)).isEmpty();
            if (!(chain && first && shown && adopt)) {
                lines.add("    WS1 chain " + chain + " first " + first + " shown " + rows + " adopt " + adopt);
            }
            return chain && first && shown && adopt;
        });

        SelfTestHarness.check(lines, tally,
                "SS1 structure spawns: extracted, read back, and each mob's structures heaviest first", () -> {
            var blaze = new GameSnapshot.StructureSpawn("minecraft:fortress",
                    new GameSnapshot.SpawnData("minecraft:blaze", "monster", 10, 2, 3), "piece");
            var wither = new GameSnapshot.StructureSpawn("minecraft:fortress",
                    new GameSnapshot.SpawnData("minecraft:wither_skeleton", "monster", 8, 5, 5), "piece");
            var guardian = new GameSnapshot.StructureSpawn("minecraft:monument",
                    new GameSnapshot.SpawnData("minecraft:guardian", "monster", 1, 2, 4), "full");
            var small = new GameSnapshot.StructureSpawn("minecraft:bastion",
                    new GameSnapshot.SpawnData("minecraft:blaze", "monster", 3, 1, 1), "full");
            List<GameLayer.Row> rows = new ArrayList<>(GameSnapshot.structureSpawnRows(List.of(blaze, wither, small)));
            rows.addAll(GameSnapshot.structureSpawnRows(List.of(guardian)));
            rows.add(new GameLayer.Row("minecraft:bad", GameLayer.STRUCTURE_SPAWN, "0", "monster|x|1"));
            var back = GameSnapshot.structureSpawnsFromRows(rows);
            boolean round = back.equals(List.of(blaze, wither, small, guardian))
                    && GameSnapshot.fromRows(rows).isEmpty();
            var places = com.strata.game.MobCatalog.structurePlaces(back);
            boolean heaviest = places.get("minecraft:blaze").equals(List.of(
                    new com.strata.game.MobCatalog.StructurePlace("minecraft:fortress", 10),
                    new com.strata.game.MobCatalog.StructurePlace("minecraft:bastion", 3)))
                    && places.get("minecraft:guardian").size() == 1 && !places.containsKey("x");
            if (!(round && heaviest)) {
                lines.add("    SS1 " + back + " " + places);
            }
            return round && heaviest;
        });

        SelfTestHarness.check(lines, tally,
                "RS1 reset to game: every edit of the plain mob, its default variant last; places and variants kept", () -> {
            String own = com.strata.model.Variant.defaultNameFor("minecraft:zombie");
            Map<String, List<Map<String, String>>> tables = new java.util.HashMap<>();
            tables.put(Schema.MobBaseStats.FILE, List.of(
                    Map.of("entity", "minecraft:zombie", "attribute", "minecraft:generic.max_health", "value", "30"),
                    Map.of("entity", "minecraft:husk", "attribute", "minecraft:generic.max_health", "value", "30")));
            tables.put(Schema.MobData.FILE, List.of(
                    Map.of("entity", "minecraft:zombie", "variant_name", "", "key", "a"),
                    Map.of("entity", "minecraft:zombie", "variant_name", "big_zombie", "key", "a")));
            tables.put(Schema.LootTables.FILE, List.of(
                    Map.of("table", "minecraft:entities/zombie", "path", "p", "value", "1"),
                    Map.of("table", "minecraft:entities/husk", "path", "p", "value", "1")));
            tables.put(Schema.Equipment.FILE, List.of(
                    Map.of("variant_name", own, "slot", "head", "item", "minecraft:iron_helmet"),
                    Map.of("variant_name", "big_zombie", "slot", "head", "item", "minecraft:iron_helmet")));
            tables.put(Schema.MobGoalSets.FILE, List.of(Map.of("variant_name", own, "goal_set", "tank")));
            tables.put(Schema.Variants.FILE, List.of(Map.of("name", own, "base_entity", "minecraft:zombie"),
                    Map.of("name", "big_zombie", "base_entity", "minecraft:zombie")));
            tables.put(Schema.MobSpawns.FILE, List.of(Map.of("entity", "minecraft:zombie", "scope", "tier")));
            var parts = com.strata.domain.MobOps.resetParts("minecraft:zombie", "minecraft:entities/zombie",
                    f -> tables.getOrDefault(f, List.of()));
            List<String> files = parts.stream().map(com.strata.domain.MobOps.ResetPart::file).toList();
            boolean which = files.equals(List.of(Schema.MobBaseStats.FILE, Schema.MobData.FILE, Schema.LootTables.FILE,
                    Schema.Equipment.FILE, Schema.MobGoalSets.FILE, Schema.Variants.FILE))
                    && parts.stream().allMatch(p -> p.rows().size() == 1)
                    && parts.get(1).rows().getFirst().get("variant_name").isEmpty()
                    && parts.getLast().rows().getFirst().get("name").equals(own);
            boolean summary = com.strata.domain.MobOps.resetSummary(parts).equals("1 Mob data attribute(s), 1 Mob data"
                    + " value(s), 1 loot table value(s), 1 equipment item(s), 1 goal set choice(s)");
            boolean none = com.strata.domain.MobOps.resetParts("", "", f -> tables.getOrDefault(f, List.of())).isEmpty()
                    && com.strata.domain.MobOps.resetParts("minecraft:pig", "",
                            f -> tables.getOrDefault(f, List.of())).isEmpty();
            if (!(which && summary && none)) {
                lines.add("    RS1 " + files + " " + com.strata.domain.MobOps.resetSummary(parts));
            }
            return which && summary && none;
        });

        SelfTestHarness.check(lines, tally,
                "FL1 lists start folded, every group, a lone one too; a search shows every match", () -> {
            List<com.strata.client.widget.BrowseList.Node> nodes = List.of(
                    new com.strata.client.widget.BrowseList.Node("gA", "Alpha", "A", null),  // 0
                    new com.strata.client.widget.BrowseList.Node("a1", "apple", null, "A"),  // 1
                    new com.strata.client.widget.BrowseList.Node("a2", "apricot", null, "A"),  // 2
                    new com.strata.client.widget.BrowseList.Node("gC", "Cherry", "C", "A"),  // 3
                    new com.strata.client.widget.BrowseList.Node("c1", "cress", null, "C"),  // 4
                    new com.strata.client.widget.BrowseList.Node("c2", "cumin", null, "C"),  // 5
                    new com.strata.client.widget.BrowseList.Node("gB", "Beta", "B", null),  // 6
                    new com.strata.client.widget.BrowseList.Node("b1", "banana", null, "B"));  // 7
            var start = com.strata.client.widget.BrowseList.shown(nodes, "", java.util.Set.of());
            boolean folded = start.rows().equals(List.of(0, 6)) && start.openShown().isEmpty();
            var openA = com.strata.client.widget.BrowseList.shown(nodes, "", java.util.Set.of("A"));
            boolean click = openA.rows().equals(List.of(0, 1, 2, 3, 6))
                    && com.strata.client.widget.BrowseList.shown(nodes, "", java.util.Set.of("B")).rows()
                            .equals(List.of(0, 6, 7));
            var search = com.strata.client.widget.BrowseList.shown(nodes, "cress", java.util.Set.of());
            boolean found = search.rows().equals(List.of(0, 3, 4)) && search.openShown().containsAll(List.of("A", "C"));
            boolean filtered = com.strata.client.widget.BrowseList.shown(nodes, "", java.util.Set.of(), true).rows()
                    .size() == nodes.size();
            List<com.strata.client.widget.BrowseList.Node> lone = List.of(
                    new com.strata.client.widget.BrowseList.Node("gM", "Minecraft", "M", null),
                    new com.strata.client.widget.BrowseList.Node("m1", "zombie", null, "M"),
                    new com.strata.client.widget.BrowseList.Node("m2", "husk", null, "M"));
            boolean loneOpen = com.strata.client.widget.BrowseList.shown(lone, "", java.util.Set.of()).rows().size() == 1;
            if (!(folded && click && found && filtered && loneOpen)) {
                lines.add("    FL1 start " + start + " openA " + openA + " search " + search);
            }
            return folded && click && found && filtered && loneOpen;
        });

        SelfTestHarness.check(lines, tally,
                "RC1 mob pickers lead with the recent mobs: each once, newest first, a variant as its base mob", () -> {
            String sep = "\u0000";
            List<String> keys = List.of("minecraft:husk" + sep + "frosty", "minecraft:zombie" + sep,
                    "minecraft:husk" + sep, "somemod:gone" + sep);
            boolean entities = com.strata.client.util.RecentEdits.entitiesOf(keys)
                    .equals(List.of("minecraft:husk", "minecraft:zombie", "somemod:gone"));
            List<com.strata.game.RegistryQuery.Option> all = List.of(
                    new com.strata.game.RegistryQuery.Option("minecraft:zombie", "Zombie", "minecraft"),
                    new com.strata.game.RegistryQuery.Option("minecraft:husk", "Husk", "minecraft"));
            var with = com.strata.client.screen.RegistryPickerScreen.withRecent(all,
                    List.of("minecraft:husk", "somemod:gone", "minecraft:zombie"));
            boolean picker = with.size() == 4 && with.get(0).id().equals("minecraft:husk")
                    && with.get(0).namespace().equals(com.strata.client.screen.RegistryPickerScreen.RECENT_GROUP)
                    && with.get(1).id().equals("minecraft:zombie") && with.subList(2, 4).equals(all)
                    && com.strata.client.screen.RegistryPickerScreen.orderGroups(List.of("minecraft", "variant",
                            com.strata.client.screen.RegistryPickerScreen.RECENT_GROUP)).getFirst()
                            .equals(com.strata.client.screen.RegistryPickerScreen.RECENT_GROUP);
            if (!(entities && picker)) {
                lines.add("    RC1 " + com.strata.client.util.RecentEdits.entitiesOf(keys) + " " + with);
            }
            return entities && picker;
        });

        SelfTestHarness.check(lines, tally,
                "AS1 assigning biomes to a tier: which are added, which move from another tier, which are there", () -> {
            Map<String, String> tiers = Map.of("minecraft:desert", "t2", "minecraft:forest", "t1");
            var plan = com.strata.domain.BiomeOps.assignPlan("t1",
                    List.of("minecraft:plains", "minecraft:desert", "minecraft:forest", "minecraft:plains"),
                    tiers::get);
            boolean ok = plan.add().equals(List.of("minecraft:plains"))
                    && plan.move().equals(Map.of("minecraft:desert", "t2"))
                    && plan.already().equals(List.of("minecraft:forest")) && plan.changes() == 2
                    && com.strata.domain.BiomeOps.assignPlan("t1", List.of("minecraft:forest"), tiers::get)
                            .changes() == 0;
            if (!ok) {
                lines.add("    AS1 " + plan);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "MS1 many rows: Ctrl and Shift pick as a desktop list does; removing them together keeps each slot's rest", () -> {
            List<String> order = List.of("0", "1", "2", "3", "4");
            java.util.Set<String> none = java.util.Set.of();
            var one = com.strata.client.widget.BrowseList.pick(order, java.util.Set.of("3"), "3", "1", false, false);
            var ctrl = com.strata.client.widget.BrowseList.pick(order, java.util.Set.of("1"), "1", "3", true, false);
            var ctrlOff = com.strata.client.widget.BrowseList.pick(order, ctrl.ids(), "3", "1", true, false);
            var shift = com.strata.client.widget.BrowseList.pick(order, java.util.Set.of("3"), "3", "1", false, true);
            var both = com.strata.client.widget.BrowseList.pick(order, java.util.Set.of("0", "4"), "4", "3", true, true);
            boolean picks = one.ids().equals(java.util.Set.of("1")) && one.anchor().equals("1")
                    && ctrl.ids().equals(java.util.Set.of("1", "3")) && ctrl.anchor().equals("3")
                    && ctrlOff.ids().equals(java.util.Set.of("3"))
                    && shift.ids().equals(java.util.Set.of("1", "2", "3")) && shift.anchor().equals("3")
                    && both.ids().equals(java.util.Set.of("0", "3", "4"))
                    && com.strata.client.widget.BrowseList.pick(order, none, null, "2", false, true).ids()
                            .equals(java.util.Set.of("2"));
            var bow = new com.strata.domain.EquipmentOps.EquipmentRow("mainhand", "minecraft:bow", 50,
                    com.strata.domain.ScopeLayers.FROM_GAME);
            var sword = new com.strata.domain.EquipmentOps.EquipmentRow("mainhand", "minecraft:iron_sword", 25,
                    com.strata.domain.ScopeLayers.FROM_GAME);
            var axe = new com.strata.domain.EquipmentOps.EquipmentRow("mainhand", "minecraft:iron_axe", 25,
                    com.strata.domain.ScopeLayers.FROM_GAME);
            var helmet = new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 100, "");
            var plan = com.strata.domain.EquipmentOps.removalPlan(List.of(bow, sword, helmet),
                    List.of(bow, sword, axe, helmet));
            boolean together = plan.deny().equals(List.of(bow, sword)) && plan.keep().equals(List.of(axe))
                    && plan.delete().equals(List.of(helmet));
            boolean label = com.strata.ui.screen.EquipmentLayout.removeLabel(2, 1).equals("Remove / Deny (3)")
                    && com.strata.ui.screen.EquipmentLayout.removeLabel(1, 0).equals("Deny")
                    && com.strata.ui.screen.EquipmentLayout.removeLabel(0, 4).equals("Remove (4)");
            if (!(picks && together && label)) {
                lines.add("    MS1 picks " + picks + " plan " + plan + " label " + label);
            }
            return picks && together && label;
        });

        SelfTestHarness.check(lines, tally,
                "MS2 many rows in Abilities and the mob list: what goes, what is kept, what is left alone", () -> {
            var heal = new com.strata.domain.AbilitiesOps.AbilityRow("heal", "", "1", "20", "", true, true);
            var aura = new com.strata.domain.AbilitiesOps.AbilityRow("aura_players", "minecraft:slowness", "1",
                    "20", "4", true, true);
            var sun = new com.strata.domain.AbilitiesOps.AbilityRow("sun_immune", "", "", "", "", true, true);
            var off = new com.strata.domain.AbilitiesOps.AbilityRow("effect", "minecraft:speed", "1", "", "",
                    false, true);
            var plan = com.strata.domain.AbilitiesOps.abilityRemovalPlan(List.of(heal, aura, off),
                    List.of(heal, aura, sun, off));
            boolean abilities = plan.turnOff().equals(List.of(heal, aura)) && plan.keep().equals(List.of(sun))
                    && plan.delete().isEmpty();
            var own = new com.strata.domain.AbilitiesOps.AbilityRow("heal", "", "1", "20", "", true, false);
            var ownOnly = com.strata.domain.AbilitiesOps.abilityRemovalPlan(List.of(own), List.of(own));
            boolean deletes = ownOnly.delete().equals(List.of(own)) && ownOnly.keep().isEmpty();
            var zombie = new com.strata.domain.MobOps.ScopedMob("tier", "t1", "minecraft:zombie", "");
            var husk = new com.strata.domain.MobOps.ScopedMob("biome", "minecraft:desert", "minecraft:husk", "");
            var big = new com.strata.domain.MobOps.ScopedMob("tier", "t1", "minecraft:zombie", "big_zombie");
            var global = new com.strata.domain.MobOps.ScopedMob("", "", "minecraft:cow", "");
            var split = com.strata.domain.MobOps.splitRemovable(List.of(zombie, husk, big, global));
            boolean mobs = split.get(true).equals(List.of(zombie, husk)) && split.get(false).equals(List.of(big, global));
            boolean label = com.strata.ui.screen.ListLayout.manyLabel("Remove", "Turn off", 1, 2)
                    .equals("Remove / Turn off (3)")
                    && com.strata.ui.screen.ListLayout.manyLabel("Remove", "Deny Drop", 0, 1).equals("Deny Drop");
            if (!(abilities && deletes && mobs && label)) {
                lines.add("    MS2 " + plan + " " + ownOnly + " " + split + " " + label);
            }
            return abilities && deletes && mobs && label;
        });

        SelfTestHarness.check(lines, tally,
                "FM1 each list remembers its folds for the session, under its own key", () -> {
            String key = "FM1-test:" + System.nanoTime();
            boolean fresh = com.strata.client.util.FoldMemory.toggled(key).isEmpty();
            java.util.Set<String> toggled = new java.util.HashSet<>(java.util.Set.of("dim:Overworld", "cat:MONSTER"));
            com.strata.client.util.FoldMemory.remember(key, toggled);
            toggled.add("later");
            var back = com.strata.client.util.FoldMemory.toggled(key);
            back.add("changed");
            boolean kept = com.strata.client.util.FoldMemory.toggled(key)
                    .equals(java.util.Set.of("dim:Overworld", "cat:MONSTER"));
            boolean separate = com.strata.client.util.FoldMemory.toggled(key + ":other").isEmpty();
            com.strata.client.util.FoldMemory.remember(null, java.util.Set.of("x"));
            boolean nullKey = com.strata.client.util.FoldMemory.toggled(null).isEmpty();
            return fresh && kept && separate && nullKey;
        });

        SelfTestHarness.check(lines, tally,
                "TB1 a tier lists its biomes: every name once, in lines, and an exact count of the rest", () -> {
            com.strata.ui.TextFit.Measure m = String::length;
            List<String> names = List.of("aa", "bb", "cc", "dd", "ee", "ff", "gg");
            var all = com.strata.ui.screen.FormScreenLayouts.biomeLines(names, 8, 10, m);
            boolean fits = all.equals(List.of("aa, bb,", "cc, dd,", "ee, ff,", "gg"));
            var capped = com.strata.ui.screen.FormScreenLayouts.biomeLines(names, 8, 3, m);
            boolean more = capped.equals(List.of("aa, bb,", "cc, dd,", "... and 3 more"));
            boolean none = com.strata.ui.screen.FormScreenLayouts.biomeLines(List.of(), 8, 3, m).isEmpty();
            if (!(fits && more && none)) {
                lines.add("    TB1 " + all + " " + capped);
            }
            return fits && more && none;
        });

        SelfTestHarness.check(lines, tally,
                "OP1 Strata opens on Client, and Options holds the debug tools (Server is on Profiles, Help is the ?)", () -> {
            boolean client = !com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.contains("Options");
            boolean options = com.strata.ui.screen.ListScreenLayouts.menuTools()
                    .isEmpty()
                    && com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.equals(List.of("World",
                            "Items", "Blocks"))
                    && com.strata.ui.screen.Sections.ENTRIES.get("World").subList(0, 3).equals(List.of("Tiers", "Biomes", "Mobs"))
                    && com.strata.ui.screen.Sections.ENTRIES.get("Scaling").equals(List.of("Mob scaling", "Player scaling", "Formulas"))
                    && !com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.contains("Settings")
                    && !com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.contains("Test mode");
            return client && options;
        });

        SelfTestHarness.check(lines, tally,
                "CA1 custom assets: a name is 1 to 32 of a-z 0-9 _; a PNG and an Ogg Vorbis file are known by their bytes", () -> {
            var ops = com.strata.domain.CustomAssetsOps.class;
            boolean names = com.strata.domain.CustomAssetsOps.nameProblem("boss_roar") == null
                    && com.strata.domain.CustomAssetsOps.nameProblem("x".repeat(32)) == null
                    && com.strata.domain.CustomAssetsOps.nameProblem("mobs/zombie.growl-2") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("Boss") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("a b") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("../x") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("/x") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("x/") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("x.ogg") != null
                    && com.strata.domain.CustomAssetsOps.nameProblem("x".repeat(33)) != null
                    && com.strata.domain.CustomAssetsOps.suggestName("My Roar (final).OGG").equals("my_roar_final_")
                    && com.strata.domain.CustomAssetsOps.pathOf("  \"C:\\a b\\x.png\" ") != null
                    && com.strata.domain.CustomAssetsOps.pathOf("  ") == null;
            var png = com.strata.domain.AssetRules.texture(pngBytes(64, 32), true);
            boolean textures = png.ok() && png.summary().equals("PNG, 64 x 32 pixels")
                    && !com.strata.domain.AssetRules.texture(oggBytes(1, 44100, 0), true).ok()
                    && !com.strata.domain.AssetRules.texture(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0}, true)
                            .problem().isBlank()
                    && !com.strata.domain.AssetRules.texture(new byte[3], true).ok()
                    && !com.strata.domain.AssetRules.texture(pngBuild(20, 16, 8, 6, 0, 16 * 81, List.of(), false), true).ok();
            byte[] ogg = oggBytes(1, 44100, 88200);
            var mono = com.strata.domain.AssetRules.sound(ogg);
            var stereo = com.strata.domain.AssetRules.sound(oggBytes(2, 48000, 48000));
            boolean sounds = mono.ok() && mono.summary().startsWith("Ogg Vorbis, mono, 44100 Hz, 2.0 s")
                    && stereo.ok() && stereo.summary().contains("stereo")
                    && stereo.summary().contains("mono for sounds");
            byte[] wav = "RIFF....WAVEfmt ".getBytes(StandardCharsets.US_ASCII);
            byte[] mp3 = {'I', 'D', '3', 3, 0, 0, 0, 0};
            byte[] opusHead = new byte[30];
            System.arraycopy("OpusHead".getBytes(StandardCharsets.US_ASCII), 0, opusHead, 0, 8);
            byte[] opus = cat(oggPage(2, 0, 0, opusHead, false), oggPage(4, 48000, 1, new byte[4], false));
            boolean refused = com.strata.domain.AssetRules.sound(wav).problem().contains("WAV")
                    && com.strata.domain.AssetRules.sound(wav).problem().contains(".ogg")
                    && com.strata.domain.AssetRules.sound(mp3).problem().contains("MP3")
                    && com.strata.domain.AssetRules.sound(mp3).problem().contains("convert")
                    && com.strata.domain.AssetRules.sound(opus).problem().contains("Opus")
                    && !com.strata.domain.AssetRules.sound(png.summary().getBytes()).ok();
            if (!(names && textures && sounds && refused)) {
                lines.add("    CA1 names=" + names + " textures=" + textures + " sounds=" + sounds + " refused=" + refused);
            }
            return names && textures && sounds && refused && ops != null;
        });

        SelfTestHarness.check(lines, tally,
                "CA2 custom assets: add copies the file and indexes it, a duplicate is refused, re-import replaces, remove deletes", () -> {
            Path root = Files.createTempDirectory("strata-custom-assets");
            Path src = Files.createTempDirectory("strata-custom-src");
            try {
                Path png = src.resolve("Skin One.png");
                Files.write(png, pngBytes(16, 16));
                Path ogg = src.resolve("roar.ogg");
                Files.write(ogg, oggBytes(1, 44100, 44100));
                Path wav = src.resolve("roar.wav");
                Files.write(wav, "RIFF....WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
                var ops = com.strata.domain.CustomAssetsOps.class;
                String T = com.strata.domain.CustomAssetsOps.TEXTURE;
                String S = com.strata.domain.CustomAssetsOps.SOUND;
                var addPng = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, png, "skin_one", "entity", "ignored", false));
                var addOgg = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        S, ogg, "roar", "entity", "Roars, loudly", false));
                var all = com.strata.domain.CustomAssetsOps.all(root);
                Path pack = com.strata.domain.CustomAssetsOps.packDir(root);
                boolean added = addPng.ok() && addOgg.ok() && all.size() == 2
                        && all.get(0).resource().equals("strata_custom:textures/entity/skin_one.png")
                        && all.get(0).subtitle().isEmpty()
                        && all.get(1).resource().equals("strata_custom:roar")
                        && all.get(1).subtitle().equals("Roars, loudly")
                        && all.get(1).source().equals("roar.ogg")
                        && all.get(0).label().equals("Custom · skin_one")
                        && Files.isRegularFile(pack.resolve("assets/strata_custom/textures/entity/skin_one.png"))
                        && Files.isRegularFile(pack.resolve("assets/strata_custom/sounds/roar.ogg"))
                        && Files.isRegularFile(pack.resolve("pack.mcmeta"))
                        && Files.readString(pack.resolve("pack.mcmeta")).contains("\"pack_format\": 34")
                        && Files.isRegularFile(root.resolve("custom").resolve("custom_assets.csv"))
                        && !Files.exists(root.resolve("custom_assets.csv"));
                var dup = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        S, ogg, "roar", "", "", false));
                var badFile = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        S, wav, "roar2", "", "", false));
                var badKindFile = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, ogg, "notpng", "misc", "", false));
                var badName = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, png, "Bad Name", "misc", "", false));
                var badCategory = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, png, "okname", "items", "", false));
                var missing = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, src.resolve("nothing.png"), "okname", "misc", "", false));
                boolean refused = dup.refusal() != null && dup.refusal().contains("already")
                        && badFile.refusal() != null && badFile.refusal().contains("WAV")
                        && badKindFile.refusal() != null && badName.refusal() != null && badCategory.refusal() != null
                        && missing.refusal() != null
                        && com.strata.domain.CustomAssetsOps.all(root).size() == 2
                        && !Files.exists(pack.resolve("assets/strata_custom/sounds/roar2.ogg"));
                var both = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, png, "roar", "misc", "", false));
                var again = com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        T, png, "skin_one", "block", "", true));
                boolean replaced = both.ok() && again.ok() && com.strata.domain.CustomAssetsOps.all(root).size() == 3
                        && com.strata.domain.CustomAssetsOps.find(root, T, "skin_one").category().equals("block")
                        && Files.isRegularFile(pack.resolve("assets/strata_custom/textures/block/skin_one.png"))
                        && !Files.exists(pack.resolve("assets/strata_custom/textures/entity/skin_one.png"));
                var removed = com.strata.domain.CustomAssetsOps.remove(root, S, "roar");
                var removeMissing = com.strata.domain.CustomAssetsOps.remove(root, S, "roar");
                boolean gone = removed.ok() && removeMissing.refusal() != null
                        && com.strata.domain.CustomAssetsOps.find(root, S, "roar") == null
                        && com.strata.domain.CustomAssetsOps.find(root, T, "roar") != null
                        && !Files.exists(pack.resolve("assets/strata_custom/sounds/roar.ogg"))
                        && com.strata.domain.CustomAssetsOps.ofKind(root, T).size() == 2
                        && com.strata.domain.CustomAssetsOps.ofKind(root, S).isEmpty();
                if (!(added && refused && replaced && gone)) {
                    lines.add("    CA2 added=" + added + " refused=" + refused + " replaced=" + replaced + " gone=" + gone + " " + addPng.refusal() + " / " + addOgg.refusal() + " / " + dup.refusal() + " / " + badFile.refusal() + " / " + all);
                }
                return added && refused && replaced && gone && ops != null;
            } finally {
                deleteRec(root);
                deleteRec(src);
            }
        });

    }

    private static void runTestsPart05(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "CA3 custom assets: sounds.json holds an event for each sound, with its subtitle line; a removal and a stray file leave it", () -> {
            Path root = Files.createTempDirectory("strata-custom-sounds");
            Path src = Files.createTempDirectory("strata-custom-src");
            try {
                Path ogg = src.resolve("a.ogg");
                Files.write(ogg, oggBytes(1, 44100, 44100));
                String S = com.strata.domain.CustomAssetsOps.SOUND;
                com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        S, ogg, "growl", "", "A \"deep\" growl", false));
                com.strata.domain.CustomAssetsOps.add(root, new com.strata.domain.CustomAssetsOps.Request(
                        S, ogg, "hiss", "", "", false));
                Path ns = com.strata.domain.CustomAssetsOps.packDir(root).resolve("assets/strata_custom");
                var json = com.google.gson.JsonParser.parseString(Files.readString(ns.resolve("sounds.json"))).getAsJsonObject();
                var lang = com.google.gson.JsonParser.parseString(Files.readString(ns.resolve("lang/en_us.json"))).getAsJsonObject();
                boolean events = json.keySet().equals(Set.of("growl", "hiss"))
                        && json.getAsJsonObject("growl").getAsJsonArray("sounds").get(0).getAsString().equals("strata_custom:growl")
                        && json.getAsJsonObject("growl").get("subtitle").getAsString().equals("strata_custom.subtitle.growl")
                        && lang.get("strata_custom.subtitle.growl").getAsString().equals("A \"deep\" growl")
                        && json.getAsJsonObject("hiss").getAsJsonArray("sounds").get(0).getAsString()
                                .equals("strata_custom:hiss")
                        && !json.getAsJsonObject("hiss").has("subtitle")
                        && Files.isRegularFile(ns.resolve("sounds/hiss.ogg"));
                Files.write(ns.resolve("sounds/stray.ogg"), oggBytes(1, 44100, 0));
                com.strata.domain.CustomAssetsOps.remove(root, S, "growl");
                var after = com.google.gson.JsonParser.parseString(Files.readString(ns.resolve("sounds.json"))).getAsJsonObject();
                boolean left = after.keySet().equals(Set.of("hiss"))
                        && com.google.gson.JsonParser.parseString(Files.readString(ns.resolve("lang/en_us.json")))
                                .getAsJsonObject().size() == 0
                        && !Files.exists(ns.resolve("sounds/stray.ogg"))
                        && !Files.exists(ns.resolve("sounds/growl.ogg"))
                        && Files.isRegularFile(ns.resolve("sounds/hiss.ogg"));
                com.strata.domain.CustomAssetsOps.remove(root, S, "hiss");
                boolean empty = com.strata.domain.CustomAssetsOps.all(root).isEmpty()
                        && com.google.gson.JsonParser.parseString(Files.readString(ns.resolve("sounds.json")))
                                .getAsJsonObject().size() == 0;
                if (!(events && left && empty)) {
                    lines.add("    CA3 events=" + events + " left=" + left + " empty=" + empty);
                }
                return events && left && empty;
            } finally {
                deleteRec(root);
                deleteRec(src);
            }
        });

        SelfTestHarness.check(lines, tally,
                "CA4 custom assets: the screens are under Options, and a path reads as Strata / Options / Custom textures & sounds", () -> {
            return "Options".equals(com.strata.ui.TitlePath.rootOfClass("CustomAssetsScreen"))
                    && "Options".equals(com.strata.ui.TitlePath.rootOfClass("CustomAssetScreen"))
                    && com.strata.ui.TitlePath.compose("Options", "Strata — Custom textures & sounds")
                            .equals("Strata / Options / Custom textures & sounds")
                    && com.strata.ui.screen.Sections.ENTRIES.get("Options").contains("Custom textures & sounds");
        });

        SelfTestHarness.check(lines, tally,
                "AS1 PNG rules: chunk whitelist, IHDR limits, decompression-bomb guard, CRC, file size, clean re-encode", () -> {
            var small = AssetRules.texture(pngBytes(16, 16), true);
            boolean sizes = small.ok() && AssetRules.texture(pngBytes(512, 512), true).ok()
                    && AssetRules.texture(pngBytes(32, 48), true).ok()
                    && !AssetRules.texture(pngBytes(528, 16), true).ok()
                    && !AssetRules.texture(pngBytes(8, 16), true).ok()
                    && !AssetRules.texture(pngBytes(24, 16), true).ok()
                    && !AssetRules.texture(pngBuild(100000, 100000, 8, 6, 0, 16, List.of(), false), true).ok();
            byte[] text = pngChunk("tEXt", "a\0b".getBytes(StandardCharsets.ISO_8859_1), false);
            byte[] withText = pngBuild(16, 16, 8, 6, 0, 16 * 65, List.of(text), false);
            var strict = AssetRules.texture(withText, true);
            var lenient = AssetRules.texture(withText, false);
            boolean chunks = !strict.ok() && strict.problem().contains("tEXt") && lenient.ok()
                    && new String(lenient.clean(), StandardCharsets.ISO_8859_1).indexOf("tEXt") < 0
                    && !AssetRules.texture(pngBuild(16, 16, 8, 6, 0, 16 * 65,
                            List.of(pngChunk("XYZW", new byte[2], false)), false), false).ok()
                    && AssetRules.texture(small.clean(), true).ok();
            boolean header = !AssetRules.texture(pngBuild(16, 16, 16, 6, 0, 16 * 129, List.of(), false), true).ok()
                    && !AssetRules.texture(pngBuild(16, 16, 8, 0, 0, 16 * 17, List.of(), false), true).ok()
                    && !AssetRules.texture(pngBuild(16, 16, 8, 4, 0, 16 * 33, List.of(), false), true).ok()
                    && !AssetRules.texture(pngBuild(16, 16, 8, 6, 1, 16 * 65, List.of(), false), true).ok()
                    && AssetRules.texture(pngBuild(16, 16, 8, 2, 0, 16 * 49, List.of(), false), true).ok()
                    && AssetRules.texture(pngBuild(16, 16, 8, 3, 0, 16 * 17, List.of(), false), true).ok();
            var bomb = AssetRules.texture(pngBuild(16, 16, 8, 6, 0, 8 << 20, List.of(), false), true);
            var shortData = AssetRules.texture(pngBuild(16, 16, 8, 6, 0, 16 * 65 - 5, List.of(), false), true);
            var crc = AssetRules.texture(pngBuild(16, 16, 8, 6, 0, 16 * 65, List.of(), true), true);
            byte[] trailing = cat(pngBytes(16, 16), new byte[]{1, 2, 3});
            byte[] huge = new byte[300 * 1024];
            System.arraycopy(pngBytes(16, 16), 0, huge, 0, 40);
            boolean attacks = !bomb.ok() && bomb.problem().contains("more pixel data")
                    && !shortData.ok() && !crc.ok() && crc.problem().contains("check value")
                    && !AssetRules.texture(trailing, true).ok() && !AssetRules.texture(huge, true).ok()
                    && huge.length > AssetRules.MAX_TEXTURE_FILE;
            if (!(sizes && chunks && header && attacks)) {
                lines.add("    AS1 sizes=" + sizes + " chunks=" + chunks + " header=" + header + " attacks=" + attacks);
            }
            return sizes && chunks && header && attacks;
        });

        SelfTestHarness.check(lines, tally,
                "AS2 Ogg Vorbis rules: Vorbis header, mono or stereo, 22050/44100/48000 Hz, 20 s from the last granule, page CRCs, 1 MiB", () -> {
            boolean rates = AssetRules.sound(oggBytes(1, 22050, 22050)).ok() && AssetRules.sound(oggBytes(2, 44100, 44100)).ok()
                    && AssetRules.sound(oggBytes(1, 48000, 48000)).ok()
                    && !AssetRules.sound(oggBytes(1, 32000, 32000)).ok() && !AssetRules.sound(oggBytes(1, 8000, 8000)).ok()
                    && !AssetRules.sound(oggBytes(3, 44100, 44100)).ok() && !AssetRules.sound(oggBytes(0, 44100, 44100)).ok();
            boolean duration = AssetRules.sound(oggBytes(1, 44100, 20L * 44100)).ok()
                    && !AssetRules.sound(oggBytes(1, 44100, 20L * 44100 + 4410)).ok()
                    && AssetRules.sound(oggBytes(1, 44100, 20L * 44100 + 4410)).problem().contains("seconds");
            byte[] good = oggBytes(1, 44100, 44100);
            byte[] badCrc = cat(oggPage(2, 0, 0, vorbisIdent(1, 44100), false), oggPage(4, 44100, 1, new byte[4], true));
            byte[] noEnd = cat(oggPage(2, 0, 0, vorbisIdent(1, 44100), false), oggPage(0, 44100, 1, new byte[4], false));
            byte[] cut = java.util.Arrays.copyOf(good, good.length - 3);
            byte[] oversize = cat(good, new byte[AssetRules.MAX_SOUND_FILE]);
            byte[] afterEnd = cat(good, oggPage(4, 44100, 2, new byte[4], false));
            byte[] flac = "fLaC....".getBytes(StandardCharsets.US_ASCII);
            boolean broken = !AssetRules.sound(badCrc).ok() && AssetRules.sound(badCrc).problem().contains("check value")
                    && !AssetRules.sound(noEnd).ok() && !AssetRules.sound(cut).ok() && !AssetRules.sound(oversize).ok()
                    && !AssetRules.sound(afterEnd).ok() && AssetRules.sound(flac).problem().contains("FLAC")
                    && !AssetRules.sound(new byte[0]).ok();
            if (!(rates && duration && broken)) {
                lines.add("    AS2 rates=" + rates + " duration=" + duration + " broken=" + broken);
            }
            return rates && duration && broken;
        });

        SelfTestHarness.check(lines, tally,
                "AS3 names, categories, subtitles and derived paths: a traversal cannot be made", () -> {
            String h = "a".repeat(64);
            AssetSync.Entry tex = new AssetSync.Entry("Texture", "entity", "wolf", h, 100, "");
            AssetSync.Entry snd = new AssetSync.Entry("Sound", "", "roar", h, 100, "Roars");
            boolean entries = tex.problem() == null && snd.problem() == null
                    && new AssetSync.Entry("Texture", "entity", "../../x", h, 100, "").problem() != null
                    && new AssetSync.Entry("Texture", "entity", "a/b", h, 100, "").problem() != null
                    && new AssetSync.Entry("Texture", "entity", "A", h, 100, "").problem() != null
                    && new AssetSync.Entry("Texture", "../entity", "x", h, 100, "").problem() != null
                    && new AssetSync.Entry("Texture", "", "x", h, 100, "").problem() != null
                    && new AssetSync.Entry("Sound", "entity", "x", h, 100, "").problem() != null
                    && new AssetSync.Entry("Video", "", "x", h, 100, "").problem() != null
                    && new AssetSync.Entry("Texture", "entity", "x", "zz", 100, "").problem() != null
                    && new AssetSync.Entry("Texture", "entity", "x", h, 0, "").problem() != null
                    && new AssetSync.Entry("Texture", "entity", "x", h, AssetRules.MAX_TEXTURE_FILE + 1, "").problem() != null
                    && new AssetSync.Entry("Texture", "entity", "x", h, AssetRules.MAX_TEXTURE_FILE, "").problem() == null
                    && new AssetSync.Entry("Sound", "", "x", h, AssetRules.MAX_SOUND_FILE + 1, "").problem() != null;
            Path root = Path.of("some", "root").toAbsolutePath();
            Path pack = com.strata.domain.CustomAssetsOps.packDir(root).toAbsolutePath().normalize();
            boolean traversal;
            try {
                AssetSync.fileFor(root, new AssetSync.Entry("Texture", "entity", "..", h, 1, ""));
                traversal = false;
            } catch (IllegalArgumentException e) {
                traversal = true;
            }
            boolean paths = traversal && AssetSync.fileFor(root, tex).startsWith(pack)
                    && AssetSync.fileFor(root, tex).endsWith(Path.of("textures", "entity", "wolf.png"))
                    && AssetSync.fileFor(root, snd).endsWith(Path.of("sounds", "roar.ogg"));
            Path cacheA = AssetSync.cacheDir(root, "Example.com:25565");
            Path cacheB = AssetSync.cacheDir(root, "evil/../../x:25565");
            boolean cache = cacheA.equals(AssetSync.cacheDir(root, " example.COM:25565 "))
                    && !cacheA.equals(cacheB) && cacheB.getParent().equals(root.resolve(AssetSync.CACHE_FOLDER))
                    && cacheB.getFileName().toString().matches("[0-9a-f]{16}");
            boolean subtitles = AssetRules.cleanSubtitle("§cHi\u0007 there").equals("Hi there")
                    && AssetRules.cleanSubtitle("a\nb\tc").equals("a b c") && AssetRules.cleanSubtitle(null).isEmpty()
                    && AssetRules.subtitleProblem("x".repeat(49)) != null && AssetRules.subtitleProblem("x".repeat(48)) == null
                    && new AssetSync.Entry("Sound", "", "x", h, 5, "§4Red\u0000" + "y".repeat(100)).cleaned()
                            .subtitle().length() <= AssetRules.MAX_SUBTITLE
                    && tex.cleaned().subtitle().isEmpty();
            boolean noPath = java.util.Arrays.stream(AssetSync.Entry.class.getRecordComponents())
                    .noneMatch(c -> c.getName().toLowerCase().contains("path") || c.getName().toLowerCase().contains("file"));
            if (!(entries && paths && cache && subtitles && noPath)) {
                lines.add("    AS3 entries=" + entries + " paths=" + paths + " cache=" + cache + " subtitles=" + subtitles
                        + " noPath=" + noPath);
            }
            return entries && paths && cache && subtitles && noPath;
        });

        SelfTestHarness.check(lines, tally,
                "AS4 a manifest: invalid and repeated entries are dropped, nothing else is capped, the cache says what is needed", () -> {
            List<AssetSync.Entry> many = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                many.add(new AssetSync.Entry("Sound", "", "s" + i, String.format("%064x", i), AssetRules.MAX_SOUND_FILE, ""));
            }
            boolean uncapped = AssetSync.accept(many).entries().size() == 200
                    && AssetSync.totalBytes(many) == 200L * AssetRules.MAX_SOUND_FILE;
            List<AssetSync.Entry> mixed = new ArrayList<>(List.of(
                    new AssetSync.Entry("Sound", "", "ok", "b".repeat(64), 10, "§aHi"),
                    new AssetSync.Entry("Sound", "", "ok", "c".repeat(64), 10, ""),
                    new AssetSync.Entry("Sound", "", "Bad Name", "b".repeat(64), 10, ""),
                    new AssetSync.Entry("Texture", "nope", "x", "b".repeat(64), 10, ""),
                    new AssetSync.Entry("Texture", "item", "tex", "d".repeat(64), 10, "")));
            mixed.add(null);
            var accepted = AssetSync.accept(mixed);
            boolean dropped = accepted.entries().size() == 2 && accepted.notices().size() == 4
                    && accepted.entries().get(0).subtitle().equals("Hi");
            Path cache = Files.createTempDirectory("strata-server-cache");
            try {
                AssetSync.Entry a = accepted.entries().get(0);
                AssetSync.Entry b = accepted.entries().get(1);
                boolean allNeeded = AssetSync.needed(cache, accepted.entries()).size() == 2;
                AssetSync.store(cache, a, new byte[]{1});
                boolean one = AssetSync.needed(cache, accepted.entries()).equals(List.of(b));
                AssetSync.Entry changed = new AssetSync.Entry(a.kind(), a.category(), a.name(), "e".repeat(64), 10, "");
                boolean again = AssetSync.needed(cache, List.of(changed)).equals(List.of(changed));
                Files.delete(AssetSync.fileFor(cache, a));
                boolean lost = AssetSync.needed(cache, List.of(a)).equals(List.of(a));
                boolean hashes = AssetSync.neededHashes(List.of(a, a, b)).equals(List.of(a.sha256(), b.sha256()));
                if (!(uncapped && dropped && allNeeded && one && again && lost && hashes)) {
                    lines.add("    AS4 uncapped=" + uncapped + " dropped=" + dropped + " allNeeded=" + allNeeded + " one=" + one
                            + " again=" + again + " lost=" + lost + " hashes=" + hashes);
                }
                return uncapped && dropped && allNeeded && one && again && lost && hashes;
            } finally {
                deleteRec(cache);
            }
        });

        SelfTestHarness.check(lines, tally,
                "AS5 a transfer: 32 KiB chunks in order, never over the size, hash then format checked, one violation drops the file", () -> {
            byte[] data = pngNoise(128, 128);
            String sha = AssetSync.sha256(data);
            int count = AssetSync.chunkCount(data.length, AssetRules.CHUNK_BYTES);
            AssetSync.Receiver r = new AssetSync.Receiver("Texture", sha, data.length, AssetRules.CHUNK_BYTES);
            boolean fed = true;
            for (int i = 0; i < count; i++) {
                byte[] chunk = AssetSync.chunk(data, i, AssetRules.CHUNK_BYTES);
                fed &= (i == count - 1 || chunk.length == AssetRules.CHUNK_BYTES) && r.accept(i, chunk) == null;
            }
            var done = r.finish();
            boolean whole = count >= 2 && fed && r.complete() && done.ok()
                    && r.accept(count, new byte[]{1}) != null && AssetRules.CHUNK_BYTES == 32 * 1024;
            AssetSync.Receiver order = new AssetSync.Receiver("Texture", sha, data.length, AssetRules.CHUNK_BYTES);
            AssetSync.Receiver over = new AssetSync.Receiver("Texture", sha, data.length, AssetRules.CHUNK_BYTES);
            AssetSync.Receiver shortChunk = new AssetSync.Receiver("Texture", sha, data.length, AssetRules.CHUNK_BYTES);
            boolean violations = order.accept(1, AssetSync.chunk(data, 1, AssetRules.CHUNK_BYTES)) != null
                    && over.accept(0, new byte[AssetRules.CHUNK_BYTES + 1]) != null
                    && shortChunk.accept(0, new byte[100]) != null && shortChunk.accept(0, new byte[0]) != null
                    && order.got() == 0 && over.got() == 0;
            byte[] other = pngNoise(64, 64);
            AssetSync.Receiver wrongHash = new AssetSync.Receiver("Texture", sha, other.length, AssetRules.CHUNK_BYTES);
            for (int i = 0; i < AssetSync.chunkCount(other.length, AssetRules.CHUNK_BYTES); i++) {
                wrongHash.accept(i, AssetSync.chunk(other, i, AssetRules.CHUNK_BYTES));
            }
            byte[] junk = cat("RIFF".getBytes(StandardCharsets.US_ASCII), new byte[100]);
            AssetSync.Receiver notPng = new AssetSync.Receiver("Texture", AssetSync.sha256(junk), junk.length,
                    AssetRules.CHUNK_BYTES);
            notPng.accept(0, junk);
            AssetSync.Receiver partial = new AssetSync.Receiver("Texture", sha, data.length, AssetRules.CHUNK_BYTES);
            partial.accept(0, AssetSync.chunk(data, 0, AssetRules.CHUNK_BYTES));
            boolean rejected = !wrongHash.finish().ok() && wrongHash.finish().problem().contains("hash")
                    && !notPng.finish().ok() && !partial.finish().ok();
            boolean limits;
            try {
                new AssetSync.Receiver("Texture", sha, AssetRules.MAX_TEXTURE_FILE + 1, AssetRules.CHUNK_BYTES);
                limits = false;
            } catch (IllegalArgumentException e) {
                limits = true;
            }
            if (!(whole && violations && rejected && limits)) {
                lines.add("    AS5 whole=" + whole + " violations=" + violations + " rejected=" + rejected + " limits=" + limits);
            }
            return whole && violations && rejected && limits;
        });

        SelfTestHarness.check(lines, tally,
                "AS6 server library to client cache: the manifest, the stored files, a pack made locally, the rate limits, the import folder", () -> {
            Path server = Files.createTempDirectory("strata-as-server");
            Path client = Files.createTempDirectory("strata-as-client");
            try {
                String T = com.strata.domain.CustomAssetsOps.TEXTURE;
                String S = com.strata.domain.CustomAssetsOps.SOUND;
                var addT = com.strata.domain.CustomAssetsOps.addBytes(server, T, "wolf", "entity", "", pngNoise(64, 64),
                        "wolf.png", false);
                var addS = com.strata.domain.CustomAssetsOps.addBytes(server, S, "roar", "", "§cA roar",
                        oggBytes(1, 44100, 44100), "roar.ogg", false);
                AssetSync.Built built = AssetSync.buildManifest(server);
                boolean offered = addT.ok() && addS.ok() && built.entries().size() == 2 && built.notices().isEmpty()
                        && built.entries().get(1).subtitle().equals("A roar")
                        && built.files().size() == 2;
                var accepted = AssetSync.accept(built.entries());
                boolean sent = true;
                for (AssetSync.Entry e : AssetSync.needed(client, accepted.entries())) {
                    byte[] file = Files.readAllBytes(built.files().get(e.sha256()));
                    AssetSync.Receiver r = new AssetSync.Receiver(e.kind(), e.sha256(), e.size(), AssetRules.CHUNK_BYTES);
                    for (int i = 0; i < AssetSync.chunkCount(file.length, AssetRules.CHUNK_BYTES); i++) {
                        sent &= r.accept(i, AssetSync.chunk(file, i, AssetRules.CHUNK_BYTES)) == null;
                    }
                    var checked = r.finish();
                    sent &= checked.ok();
                    AssetSync.store(client, e, checked.clean());
                }
                Path stray = com.strata.domain.CustomAssetsOps.packDir(client).resolve("assets/strata_custom/sounds/stray.ogg");
                Files.createDirectories(stray.getParent());
                Files.write(stray, new byte[]{1});
                var have = AssetSync.finishSet(client, accepted.entries());
                Path ns = com.strata.domain.CustomAssetsOps.packDir(client).resolve("assets/strata_custom");
                var json = com.google.gson.JsonParser.parseString(Files.readString(ns.resolve("sounds.json"))).getAsJsonObject();
                boolean cached = sent && have.size() == 2 && AssetSync.needed(client, accepted.entries()).isEmpty()
                        && Files.isRegularFile(ns.resolve("textures/entity/wolf.png"))
                        && Files.isRegularFile(ns.resolve("sounds/roar.ogg")) && !Files.exists(stray)
                        && json.getAsJsonObject("roar").get("subtitle").getAsString().equals("strata_custom.subtitle.roar")
                        && Files.isRegularFile(com.strata.domain.CustomAssetsOps.packDir(client).resolve("pack.mcmeta"))
                        && !Files.exists(server.resolve(AssetSync.CACHE_FOLDER));
                Files.write(built.files().get(built.entries().get(1).sha256()), new byte[]{1, 2, 3});
                AssetSync.Built spoiled = AssetSync.buildManifest(server);
                boolean left = spoiled.entries().size() == 1 && spoiled.notices().size() == 1;
                AssetSync.Throttle throttle = new AssetSync.Throttle(1000);
                boolean rate = throttle.delayMs(0, 500) == 0 && throttle.delayMs(0, 500) == 500
                        && throttle.delayMs(0, 500) == 1000 && throttle.delayMs(5000, 10) == 0;
                AssetSync.UploadLimiter limiter = new AssetSync.UploadLimiter();
                boolean uploads = limiter.allow(1_000_000, 1000) == null && limiter.allow(1_000_500, 10) != null
                        && limiter.allow(1_002_500, 4L << 20) != null && limiter.allow(1_002_500, 1000) == null
                        && limiter.allow(1_005_000, (4L << 20) - 2000) == null && limiter.allow(1_008_000, 2) != null
                        && limiter.allow(1_070_000, 4L << 20) == null && AssetRules.UPLOAD_GAP_MS == 2000;
                Path imp = AssetSync.importDir(server);
                Files.createDirectories(imp.resolve("item"));
                Files.createDirectories(imp.resolve("sounds"));
                Files.write(imp.resolve("item/gem.png"), pngBytes(16, 16));
                Files.write(imp.resolve("item/Bad Name.png"), pngBytes(16, 16));
                Files.write(imp.resolve("sounds/pop.ogg"), oggBytes(2, 48000, 4800));
                Files.write(imp.resolve("sounds/fake.ogg"), "RIFF....WAVE".getBytes(StandardCharsets.US_ASCII));
                List<String> report = AssetSync.importFolder(server);
                boolean imported = com.strata.domain.CustomAssetsOps.find(server, T, "gem") != null
                        && com.strata.domain.CustomAssetsOps.find(server, S, "pop") != null
                        && com.strata.domain.CustomAssetsOps.find(server, S, "fake") == null
                        && report.stream().anyMatch(l -> l.startsWith("gem.png: imported"))
                        && report.stream().noneMatch(l -> l.startsWith("Bad Name") && l.contains("imported"));
                if (!(offered && cached && left && rate && uploads && imported)) {
                    lines.add("    AS6 offered=" + offered + " cached=" + cached + " left=" + left + " rate=" + rate
                            + " uploads=" + uploads + " imported=" + imported + " " + report);
                }
                return offered && cached && left && rate && uploads && imported;
            } finally {
                deleteRec(server);
                deleteRec(client);
            }
        });

        SelfTestHarness.check(lines, tally,
                "AS9 a block or item model: one JSON object, no loader, written again; it replaces a block's model", () -> {
            String M = com.strata.domain.CustomAssetsOps.MODEL;
            byte[] good = "{ \"parent\": \"minecraft:block/cube_all\", \"textures\": {\"all\": \"minecraft:block/gold_block\"} }"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var ok = AssetRules.check(M, good, false);
            boolean checks = ok.ok() && new String(ok.clean(), java.nio.charset.StandardCharsets.UTF_8).startsWith("{\"parent\"")
                    && !AssetRules.check(M, "[1,2]".getBytes(), false).ok()
                    && !AssetRules.check(M, "{ not json".getBytes(), false).ok()
                    && !AssetRules.check(M, "{\"loader\":\"neoforge:obj\"}".getBytes(), false).ok();
            Path root = Files.createTempDirectory("strata-as-model");
            try {
                boolean added = com.strata.domain.CustomAssetsOps.addBytes(root, M, "gold_stone", "", "", good,
                        "gold_stone.json", false).ok();
                String target = "minecraft:models/block/stone.json";
                boolean refusedPath = !com.strata.domain.CustomAssetsOps.replace(root, M,
                        "minecraft:models/entity/zombie.json", "gold_stone").ok();
                boolean replaced = com.strata.domain.CustomAssetsOps.replace(root, M, target, "gold_stone").ok();
                Path dest = com.strata.domain.CustomAssetsOps.packDir(root).resolve("assets/minecraft/models/block/stone.json");
                boolean written = Files.isRegularFile(dest) && Files.readString(dest).contains("gold_block");
                boolean restored = com.strata.domain.CustomAssetsOps.restoreGame(root, M, target).ok() && !Files.exists(dest);
                if (!(checks && added && refusedPath && replaced && written && restored)) {
                    lines.add("    AS9 checks=" + checks + " added=" + added + " refusedPath=" + refusedPath
                            + " replaced=" + replaced + " written=" + written + " restored=" + restored);
                }
                return checks && added && refusedPath && replaced && written && restored;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "AS8 a game sound and texture replaced by custom ones of the same kind, then restored", () -> {
            Path root = Files.createTempDirectory("strata-as-replace");
            try {
                String T = com.strata.domain.CustomAssetsOps.TEXTURE;
                String S = com.strata.domain.CustomAssetsOps.SOUND;
                boolean added = com.strata.domain.CustomAssetsOps.addBytes(root, T, "skin", "entity", "",
                        pngNoise(64, 64), "skin.png", false).ok()
                        && com.strata.domain.CustomAssetsOps.addBytes(root, S, "hiss", "", "",
                        oggBytes(1, 44100, 44100), "hiss.ogg", false).ok();
                String tex = "minecraft:textures/entity/zombie/zombie.png";
                String snd = "minecraft:entity.creeper.primed";
                boolean wrongKind = !com.strata.domain.CustomAssetsOps.replace(root, S, snd, "skin").ok()
                        && !com.strata.domain.CustomAssetsOps.replace(root, T, "minecraft:entity.zombie", "skin").ok();
                boolean replaced = com.strata.domain.CustomAssetsOps.replace(root, T, tex, "skin").ok()
                        && com.strata.domain.CustomAssetsOps.replace(root, S, snd, "hiss").ok();
                Path mc = com.strata.domain.CustomAssetsOps.packDir(root).resolve("assets/minecraft");
                Path png = mc.resolve("textures/entity/zombie/zombie.png");
                boolean written = Files.isRegularFile(png) && Files.isRegularFile(mc.resolve("sounds.json"))
                        && com.google.gson.JsonParser.parseString(Files.readString(mc.resolve("sounds.json")))
                        .getAsJsonObject().getAsJsonObject("entity.creeper.primed").get("replace").getAsBoolean();
                // A server's manifest carries the replacements; a player's cache builds the same pack from them.
                Path cache = Files.createTempDirectory("strata-as-cache");
                boolean synced;
                try {
                    var built = AssetSync.buildManifest(root);
                    List<String> sent = AssetSync.replacementLines(root, built.entries());
                    for (AssetSync.Entry e : built.entries()) {
                        AssetSync.store(cache, e, Files.readAllBytes(built.files().get(e.sha256())));
                    }
                    com.strata.domain.CustomAssetsOps.writeReplacementLines(cache, sent);
                    AssetSync.finishSet(cache, built.entries());
                    Path cmc = com.strata.domain.CustomAssetsOps.packDir(cache).resolve("assets/minecraft");
                    synced = sent.size() == 2 && Files.isRegularFile(cmc.resolve("textures/entity/zombie/zombie.png"))
                            && Files.isRegularFile(cmc.resolve("sounds.json"));
                } finally {
                    deleteRec(cache);
                }
                boolean restored = synced && com.strata.domain.CustomAssetsOps.restoreGame(root, T, tex).ok()
                        && com.strata.domain.CustomAssetsOps.restoreGame(root, S, snd).ok()
                        && !Files.exists(png) && !Files.exists(mc.resolve("sounds.json"))
                        && com.strata.domain.CustomAssetsOps.replacements(root).isEmpty();
                if (!(added && wrongKind && replaced && written && restored)) {
                    lines.add("    AS8 added=" + added + " wrongKind=" + wrongKind + " replaced=" + replaced
                            + " written=" + written + " restored=" + restored);
                }
                return added && wrongKind && replaced && written && restored;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "AS7 the client option is Ask, Always or Never (default Ask) in Options, and the payloads round-trip", () -> {
            boolean option = com.strata.domain.OptionsOps.SERVER_ASSETS_CHOICES.equals(List.of("Ask", "Always", "Never"))
                    && com.strata.data.StrataConfig.SERVER_ASSETS.getDefault() == com.strata.data.StrataConfig.ServerAssets.ASK
                    && com.strata.data.StrataConfig.ServerAssets.ofWord("Never") == com.strata.data.StrataConfig.ServerAssets.NEVER
                    && com.strata.domain.OptionsOps.OPTION_KEYS.contains(com.strata.domain.OptionsOps.SERVER_ASSETS);
            io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
            var fb = new net.minecraft.network.FriendlyByteBuf(buf);
            var manifest = new com.strata.network.StrataPayloads.AssetManifest(List.of(
                    new AssetSync.Entry("Sound", "", "roar", "a".repeat(64), 1234, "Roars")),
                    List.of("Sound,minecraft:entity.creeper.primed,roar"));
            com.strata.network.StrataPayloads.AssetManifest.CODEC.encode(fb, manifest);
            boolean m = com.strata.network.StrataPayloads.AssetManifest.CODEC.decode(fb).equals(manifest);
            var chunk = new com.strata.network.StrataPayloads.AssetChunk("b".repeat(64), 3, new byte[]{1, 2, 3});
            com.strata.network.StrataPayloads.AssetChunk.CODEC.encode(fb, chunk);
            var back = com.strata.network.StrataPayloads.AssetChunk.CODEC.decode(fb);
            boolean c = back.index() == 3 && java.util.Arrays.equals(back.data(), chunk.data());
            io.netty.buffer.ByteBuf big = io.netty.buffer.Unpooled.buffer();
            var bf = new net.minecraft.network.FriendlyByteBuf(big);
            bf.writeUtf("b".repeat(64));
            bf.writeVarInt(0);
            bf.writeVarInt(AssetRules.CHUNK_BYTES + 1);
            bf.writeBytes(new byte[AssetRules.CHUNK_BYTES + 1]);
            boolean refused;
            try {
                com.strata.network.StrataPayloads.AssetChunk.CODEC.decode(bf);
                refused = false;
            } catch (RuntimeException e) {
                refused = true;
            }
            var need = new com.strata.network.StrataPayloads.AssetNeed(true, List.of("c".repeat(64)));
            com.strata.network.StrataPayloads.AssetNeed.CODEC.encode(fb, need);
            boolean n = com.strata.network.StrataPayloads.AssetNeed.CODEC.decode(fb).equals(need);
            var begin = new com.strata.network.StrataPayloads.AssetUploadBegin("Texture", "item", "gem", "", 99,
                    "d".repeat(64), false);
            com.strata.network.StrataPayloads.AssetUploadBegin.CODEC.encode(fb, begin);
            boolean u = com.strata.network.StrataPayloads.AssetUploadBegin.CODEC.decode(fb).equals(begin);
            if (!(option && m && c && refused && n && u)) {
                lines.add("    AS7 option=" + option + " m=" + m + " c=" + c + " refused=" + refused + " n=" + n + " u=" + u);
            }
            return option && m && c && refused && n && u;
        });

        SelfTestHarness.check(lines, tally,
                "OP2 screen titles are paths from the sub-root, cut at the start so the end stays", () -> {
            com.strata.ui.TextFit.Measure m = String::length;
            return com.strata.ui.TitlePath.compose("World", "Strata — Mob: zombie").equals("Strata / World / Mobs / zombie")
                    && com.strata.ui.TitlePath.compose("World", "Strata — Biomes").equals("Strata / World / Biomes")
                    && com.strata.ui.TitlePath.compose("Items", "Strata — Items").equals("Strata / Items")
                    && com.strata.ui.TitlePath.compose("Items", "Strata — Item: sword").equals("Strata / Items / sword")
                    && com.strata.ui.TitlePath.compose("Items", "Strata — Recipes").equals("Strata / Items / Recipes")
                    && com.strata.ui.TitlePath.compose("Options", "Strata — Options").equals("Strata / Options")
                    && com.strata.ui.TitlePath.compose("World", "Strata / World / Mobs / x").equals("Strata / World / Mobs / x")
                    && com.strata.ui.TitlePath.rootOfKind("block_tag").equals("Blocks") && com.strata.ui.TitlePath.rootOfKind("advancement").equals("Options")
                    && com.strata.ui.TitlePath.rootOfKind("recipe").equals("Items")
                    && com.strata.ui.TitlePath.rootOfKind("loot_table").equals("World")
                    && com.strata.ui.TitlePath.clipKeepEnd("Strata / World / Mobs / zombie", 22, m).equals("... / Mobs / zombie")
                    && com.strata.ui.TitlePath.clipKeepEnd("Strata / World", 40, m).equals("Strata / World");
        });

        SelfTestHarness.check(lines, tally,
                "OV1 a feature is listed under every biome that places it, a structure finds its spacing set, no entry is lost", () -> {
            var parse = new com.google.gson.JsonParser();
            java.util.function.Function<String, com.google.gson.JsonObject> j = s -> parse.parse(s).getAsJsonObject();
            java.util.Map<String, com.google.gson.JsonObject> biomes = new java.util.LinkedHashMap<>();
            biomes.put("minecraft:plains", j.apply("{\"features\":[[\"minecraft:ore_coal\"],[\"minecraft:ore_coal\",\"oak\"]]}"));
            biomes.put("minecraft:desert", j.apply("{\"features\":[[\"minecraft:ore_coal\"],[]]}"));
            biomes.put("foo:glade", j.apply("{\"features\":[]}"));
            var where = com.strata.domain.OverrideViews.biomesOfFeatures(biomes);
            boolean biomeList = where.get("minecraft:ore_coal").equals(List.of("minecraft:desert", "minecraft:plains"))
                    && where.get("minecraft:oak").equals(List.of("minecraft:plains")) && !where.containsKey("minecraft:lake");
            java.util.Map<String, com.google.gson.JsonObject> sets = new java.util.LinkedHashMap<>();
            sets.put("minecraft:villages", j.apply("{\"structures\":[{\"structure\":\"minecraft:village_plains\",\"weight\":1},"
                    + "{\"structure\":\"village_desert\",\"weight\":1}],\"placement\":{\"spacing\":34}}"));
            sets.put("somemod:lonely", j.apply("{\"structures\":[{\"structure\":\"somemod:gone\",\"weight\":1}]}"));
            boolean spacing = com.strata.domain.OverrideViews.setsOfStructure("minecraft:village_desert", sets)
                    .equals(List.of("minecraft:villages"))
                    && com.strata.domain.OverrideViews.setsOfStructure("minecraft:igloo", sets).isEmpty();
            var tied = com.strata.domain.OverrideViews.tiedSets(sets, java.util.Set.of("minecraft:village_plains"));
            boolean loose = tied.equals(java.util.Set.of("minecraft:villages"));
            java.util.Map<String, com.google.gson.JsonObject> placed = new java.util.LinkedHashMap<>();
            placed.put("minecraft:ore_coal", j.apply("{\"feature\":\"minecraft:ore_coal\"}"));
            placed.put("minecraft:inline", j.apply("{\"feature\":{\"type\":\"x\"}}"));
            boolean settings = com.strata.domain.OverrideViews.settingsOf(placed.get("minecraft:ore_coal")).equals("minecraft:ore_coal")
                    && com.strata.domain.OverrideViews.settingsOf(placed.get("minecraft:inline")).isEmpty()
                    && com.strata.domain.OverrideViews.usedSettings(placed).equals(java.util.Set.of("minecraft:ore_coal"));
            var rows = List.of(
                    new com.strata.domain.OverrideViews.Row(com.strata.data.JsonOverrides.PLACED_FEATURE, null, "Desert"),
                    new com.strata.domain.OverrideViews.Row(com.strata.data.JsonOverrides.PLACED_FEATURE, null, "Plains"),
                    new com.strata.domain.OverrideViews.Row(com.strata.data.JsonOverrides.PLACED_FEATURE, null, "Desert"));
            var groups = com.strata.domain.OverrideViews.groups(rows);
            boolean grouped = groups.size() == 2 && groups.get(0).name().equals("Desert")
                    && groups.get(0).rows().equals(List.of(0, 2)) && groups.get(1).rows().equals(List.of(1));
            boolean words = com.strata.domain.OverrideViews.biomeGroup("minecraft:plains").equals("Plains")
                    && com.strata.domain.OverrideViews.biomeGroup("foo:dark_glade").equals("Dark Glade (foo)");
            boolean views = com.strata.domain.OverrideViews.of(com.strata.data.JsonOverrides.STRUCTURE)
                    == com.strata.domain.OverrideViews.STRUCTURES
                    && "Spacing".equals(com.strata.domain.OverrideViews.STRUCTURES.sideLabel())
                    && "Settings".equals(com.strata.domain.OverrideViews.FEATURES.sideLabel())
                    && com.strata.domain.OverrideViews.DAMAGE_TYPES.also().equals(List.of(com.strata.data.JsonOverrides.DAMAGE_TAG))
                    // Mob tags group by theme, as the Mobs list groups mobs.
                    && com.strata.domain.OverrideViews.of(com.strata.data.JsonOverrides.ENTITY_TAG)
                    == com.strata.domain.OverrideViews.MOB_TAGS
                    && com.strata.domain.OverrideViews.mobTheme("minecraft:aquatic").equals("Aquatic")
                    && com.strata.domain.OverrideViews.mobTheme("minecraft:undead").equals("Undead")
                    && com.strata.domain.OverrideViews.mobTheme("minecraft:illager").equals("Raiders")
                    && com.strata.domain.OverrideViews.mobTheme("foo:qux").equals("Other");
            if (!(biomeList && spacing && loose && settings && grouped && words && views)) {
                lines.add("    OV1 biomeList=" + biomeList + " spacing=" + spacing + " loose=" + loose + " settings="
                        + settings + " grouped=" + grouped + " words=" + words + " views=" + views);
            }
            return biomeList && spacing && loose && settings && grouped && words && views;
        });
        SelfTestHarness.check(lines, tally,
                "OV2 every setting of Options is listed, Options has no tool buttons, each kind roots at its owner or Options, the sections have no duplicates", () -> {
            // 12: the nine of before, and the three switches that ship Off (special spawners, trades, extra
            // mob attributes), plain settings here since the Pass-throughs screen went.
            boolean all = com.strata.domain.OptionsOps.OPTION_KEYS.size() == 12
                    && new java.util.HashSet<>(com.strata.domain.OptionsOps.OPTION_KEYS).size() == 12;
            boolean tools = com.strata.ui.screen.ListScreenLayouts.menuTools().isEmpty();
            boolean tagMenu = com.strata.data.JsonOverrides.KINDS.stream()
                    .filter(k -> com.strata.ui.TitlePath.rootOfKind(k.id()).equals("Options"))
                    .map(com.strata.data.JsonOverrides.Kind::id).sorted().toList()
                    .equals(List.of("advancement", "chat_type", "damage_tag", "damage_type", "flat_preset", "flat_preset_tag",
                            "game_rule", "world_preset", "world_preset_tag"));
            var world = com.strata.ui.screen.Sections.ENTRIES.get("World");
            boolean worldOk = new java.util.HashSet<>(world).size() == world.size() && world.size() == 8
                    && !world.contains("World types") && com.strata.ui.screen.Sections.ENTRIES.get("Options").contains("World types");
            boolean roots = com.strata.ui.TitlePath.rootOfKind("entity_tag").equals("World")
                    && com.strata.ui.TitlePath.rootOfKind("cat_variant_tag").equals("World")
                    && com.strata.ui.TitlePath.rootOfKind("painting_variant_tag").equals("Items")
                    && com.strata.ui.TitlePath.rootOfKind("poi_tag").equals("Blocks")
                    && com.strata.ui.TitlePath.rootOfKind("chat_type").equals("Options")
                    && com.strata.ui.TitlePath.rootOfKind("damage_type").equals("Options")
                    && com.strata.ui.TitlePath.rootOfKind("enchantment").equals("Items");
            if (!(all && tools && tagMenu && worldOk && roots)) {
                lines.add("    OV2 all=" + all + " tools=" + tools + " tagMenu=" + tagMenu + " world=" + worldOk
                        + " roots=" + roots);
            }
            return all && tools && tagMenu && worldOk && roots;
        });

        SelfTestHarness.check(lines, tally,
                "OV3 a biome's Structures lists each structure that names it, by id, by tag, or by a tag inside a tag", () -> {
            java.util.function.Function<String, com.google.gson.JsonObject> j =
                    s -> com.google.gson.JsonParser.parseString(s).getAsJsonObject();
            java.util.Map<String, com.google.gson.JsonObject> structures = new java.util.HashMap<>();
            structures.put("minecraft:village_plains", j.apply("{\"biomes\":\"#minecraft:has_structure/village_plains\"}"));
            structures.put("minecraft:mineshaft", j.apply("{\"biomes\":\"#minecraft:has_structure/mineshaft\"}"));
            structures.put("minecraft:igloo", j.apply("{\"biomes\":[\"snowy_plains\"]}"));
            structures.put("somemod:hut", j.apply("{\"biomes\":\"minecraft:plains\"}"));
            structures.put("somemod:none", j.apply("{}"));
            java.util.Map<String, com.google.gson.JsonObject> tags = new java.util.HashMap<>();
            tags.put("minecraft:has_structure/village_plains", j.apply("{\"values\":[\"minecraft:plains\",\"meadow\"]}"));
            tags.put("minecraft:has_structure/mineshaft", j.apply("{\"values\":[\"#minecraft:is_overworld\"]}"));
            tags.put("minecraft:is_overworld", j.apply("{\"values\":[{\"id\":\"minecraft:plains\",\"required\":false},"
                    + "\"#minecraft:is_overworld\"]}"));
            var inPlains = com.strata.domain.OverrideViews.structuresInBiome("minecraft:plains", structures, tags);
            var inSnowy = com.strata.domain.OverrideViews.structuresInBiome("snowy_plains", structures, tags);
            boolean ok = inPlains.equals(java.util.Set.of("minecraft:village_plains", "minecraft:mineshaft", "somemod:hut"))
                    && inSnowy.equals(java.util.Set.of("minecraft:igloo"));
            if (!ok) {
                lines.add("    OV3 inPlains=" + inPlains + " inSnowy=" + inSnowy);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "PRF9 with no profile picked Default is active (shipped when the pack has one); a change makes Profile N", () -> {
            Path root = Files.createTempDirectory("strata-active-default");
            boolean fallback = ProfileManager.activeProfile(root).equals(ProfileManager.DEFAULT);
            Files.createDirectories(root.resolve("profiles").resolve(ProfileManager.SHIPPED));
            boolean shipped = ProfileManager.activeProfile(root).equals(ProfileManager.SHIPPED);
            Path profiles = root.resolve("profiles");
            boolean first = ProfileManager.freeName(profiles).equals("Profile 1");
            Files.createDirectories(profiles.resolve("Profile 1"));
            boolean next = ProfileManager.freeName(profiles).equals("Profile 2");
            boolean ok = fallback && shipped && first && next;
            if (!ok) {
                lines.add("    PRF9 fallback=" + fallback + " shipped=" + shipped + " first=" + first + " next=" + next);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "BS1 a block replacement: the biome's rule wins over its tier's, the tier's over Global; other places keep Global", () -> {
            List<com.strata.model.BlockSwap.Rule> rules = com.strata.model.BlockSwap.rules(List.of(
                    new String[]{"global", "", "minecraft:grass_block", "minecraft:dirt"},
                    new String[]{"tier", "Deep", "minecraft:grass_block", "minecraft:podzol"},
                    new String[]{"biome", "minecraft:plains", "minecraft:grass_block", "disabled"},
                    new String[]{"global", "", "minecraft:air", "minecraft:stone"},
                    new String[]{"global", "", "minecraft:stone", "minecraft:stone"}));
            boolean dropped = rules.size() == 3;
            boolean swapPlains = "disabled".equals(com.strata.model.BlockSwap.rulesFor(rules, "Deep", "minecraft:plains")
                    .get("minecraft:grass_block"));
            boolean deep = "minecraft:podzol".equals(com.strata.model.BlockSwap.rulesFor(rules, "Deep", "minecraft:forest")
                    .get("minecraft:grass_block"));
            boolean global = "minecraft:dirt".equals(com.strata.model.BlockSwap.rulesFor(rules, "", "minecraft:forest")
                    .get("minecraft:grass_block"));
            boolean ok = dropped && swapPlains && deep && global;
            if (!ok) {
                lines.add("    BS1 dropped=" + dropped + " swapPlains=" + swapPlains + " deep=" + deep + " global=" + global);
            }
            return ok;
        });
        SelfTestHarness.check(lines, tally,
                "BS2 a replacement is refused as a chain or a swap, and a section without the blocks is skipped", () -> {
            List<com.strata.model.BlockSwap.Rule> rules = com.strata.model.BlockSwap.rules(List.<String[]>of(
                    new String[]{"global", "", "minecraft:grass_block", "minecraft:dirt"}));
            boolean chain = com.strata.model.BlockSwap.problem("minecraft:dirt", "minecraft:sand", rules, "global", "") != null;
            boolean swap = com.strata.model.BlockSwap.problem("minecraft:stone", "minecraft:grass_block", rules, "biome",
                    "minecraft:plains") != null;
            boolean self = com.strata.model.BlockSwap.problem("minecraft:stone", "minecraft:stone", rules, "global", "") != null;
            boolean fine = com.strata.model.BlockSwap.problem("minecraft:stone", "disabled", rules, "global", "") == null;
            java.util.Set<String> sources = com.strata.model.BlockSwap.sources(rules);
            boolean skip = !com.strata.model.BlockSwap.needsScan(sources, List.of("minecraft:air", "minecraft:stone"))
                    && com.strata.model.BlockSwap.needsScan(sources, List.of("minecraft:air", "minecraft:grass_block"));
            boolean ok = chain && swap && self && fine && skip;
            if (!ok) {
                lines.add("    BS2 chain=" + chain + " swap=" + swap + " self=" + self + " fine=" + fine + " skip=" + skip);
            }
            return ok;
        });
        SelfTestHarness.check(lines, tally,
                "BS3 a block replacement is saved, read back by its place, replaced in place, and removed", () -> {
            Path swapDir = Files.createTempDirectory("strata-blockswap");
            Path table = swapDir.resolve(com.strata.data.Schema.BlockReplacements.FILE);
            Files.writeString(table, String.join(",", com.strata.data.Schema.BlockReplacements.SCOPE,
                    com.strata.data.Schema.BlockReplacements.SCOPE_ID, com.strata.data.Schema.BlockReplacements.FROM_BLOCK,
                    com.strata.data.Schema.BlockReplacements.TO_BLOCK) + "\n");
            boolean saved = com.strata.domain.BlockReplacementOps.save(table, "biome", "minecraft:plains",
                    "minecraft:grass_block", "minecraft:dirt").ok()
                    && com.strata.domain.BlockReplacementOps.save(table, "global", "ignored", "minecraft:sand",
                    "disabled").ok()
                    && com.strata.domain.BlockReplacementOps.save(table, "biome", "minecraft:plains",
                    "minecraft:grass_block", "minecraft:coarse_dirt").ok();
            var swapPlains = com.strata.domain.BlockReplacementOps.rows(table, "biome", "minecraft:plains");
            var global = com.strata.domain.BlockReplacementOps.rows(table, "global", "");
            boolean read = swapPlains.size() == 1 && swapPlains.get(0).to().equals("minecraft:coarse_dirt")
                    && global.size() == 1 && global.get(0).scopeId().isEmpty();
            boolean gone = read && com.strata.domain.BlockReplacementOps.remove(table, swapPlains.get(0)).ok()
                    && com.strata.domain.BlockReplacementOps.rows(table, "biome", "minecraft:plains").isEmpty()
                    && com.strata.domain.BlockReplacementOps.all(table).size() == 1;
            boolean refused = !com.strata.domain.BlockReplacementOps.save(table, "tier", "", "minecraft:stone",
                    "minecraft:dirt").ok();
            boolean ok = saved && read && gone && refused;
            if (!ok) {
                lines.add("    BS3 saved=" + saved + " read=" + read + " gone=" + gone + " refused=" + refused);
            }
            return ok;
        });
        SelfTestHarness.check(lines, tally,
                "RS1 a furnace, stonecutter and smithing recipe read onto the grid with their result; a crafting one is no station's", () -> {
            var smelt = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"ingredient\":"
                    + "{\"item\":\"minecraft:iron_ore\"},\"result\":{\"id\":\"minecraft:iron_ingot\"},\"cookingtime\":200}")
                    .getAsJsonObject();
            var cut = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:stonecutting\",\"ingredient\":"
                    + "[{\"tag\":\"minecraft:stone_crafting_materials\"}],\"result\":{\"id\":\"minecraft:stone_slab\",\"count\":2}}")
                    .getAsJsonObject();
            var smith = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:smithing_transform\",\"template\":"
                    + "{\"item\":\"minecraft:netherite_upgrade_smithing_template\"},\"base\":{\"item\":\"minecraft:diamond_sword\"},"
                    + "\"addition\":{\"item\":\"minecraft:netherite_ingot\"},\"result\":{\"id\":\"minecraft:netherite_sword\"}}")
                    .getAsJsonObject();
            var shaped = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:crafting_shaped\"}").getAsJsonObject();
            var src = com.strata.domain.RecipeOps.Source.GAME;
            var a = com.strata.domain.RecipeOps.station("minecraft:iron_ingot", smelt, src);
            var b = com.strata.domain.RecipeOps.station("minecraft:stone_slab_from_stonecutting", cut, src);
            var c = com.strata.domain.RecipeOps.station("minecraft:netherite_sword_smithing", smith, src);
            var mek = com.strata.domain.RecipeOps.station("mekanism:crushing/x", com.google.gson.JsonParser.parseString(
                    "{\"type\":\"mekanism:crushing\",\"input\":{\"tag\":\"c:ores/iron\",\"count\":1},"
                    + "\"output\":{\"id\":\"mekanism:dirty_dust_iron\",\"count\":2}}").getAsJsonObject(), src);
            boolean ok = a != null && a.result().equals("minecraft:iron_ingot") && a.cells().get(4).equals("minecraft:iron_ore")
                    && com.strata.domain.RecipeOps.isStation(a)
                    && b != null && b.count().equals("2") && b.cells().get(4).equals("#minecraft:stone_crafting_materials")
                    && c != null && c.cells().subList(3, 6).equals(List.of("minecraft:netherite_upgrade_smithing_template",
                    "minecraft:diamond_sword", "minecraft:netherite_ingot"))
                    && com.strata.domain.RecipeOps.station("x", shaped, src) == null
                    && mek != null && mek.result().equals("mekanism:dirty_dust_iron") && mek.count().equals("2")
                    && mek.cells().get(4).equals("#c:ores/iron") && com.strata.domain.RecipeOps.isStation(mek)
                    && com.strata.domain.RecipeOps.stationName("mekanism:crushing").equals("Crushing (mekanism)")
                    && com.strata.domain.RecipeOps.stationCells("mekanism:crushing").isEmpty()
                    && machinesRead(lines)
                    && com.strata.domain.RecipeOps.editProblem(a) == null
                    && com.strata.domain.RecipeOps.stationCells("minecraft:smithing_transform").equals(List.of(3, 4, 5));
            if (!ok) {
                lines.add("    RS1 smelt=" + a + " cut=" + b + " smith=" + c);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "VT1 a value type knows its control and checks a typed value against its range", () -> {
            var bool = com.strata.model.ValueType.BOOL;
            var whole = com.strata.model.ValueType.whole(1, 99);
            var number = com.strata.model.ValueType.number(0, 1);
            var pick = com.strata.model.ValueType.choice(List.of("a", "b"));
            var reg = com.strata.model.ValueType.registry(com.strata.model.ValueType.Registry.ITEM);
            return bool.shape() == com.strata.model.ValueType.Shape.BOOL && bool.choices().equals(List.of("true", "false"))
                    && bool.isChosen() && pick.isChosen() && reg.isChosen() && !whole.isChosen() && !number.isChosen()
                    && com.strata.model.ValueType.TEXT.shape() == com.strata.model.ValueType.Shape.TEXT
                    && reg.kind() == com.strata.model.ValueType.Registry.ITEM
                    && whole.problem("5") == null && whole.problem("0") != null && whole.problem("100") != null
                    && whole.problem("1.5") != null && whole.problem("x") != null
                    && number.problem("0.5") == null && number.problem("2") != null && number.problem("") != null
                    && bool.problem("true") == null && bool.problem("yes") != null
                    && pick.problem("a") == null && pick.problem("c") != null
                    && com.strata.model.ValueType.ofSwitchWord("true").shape() == com.strata.model.ValueType.Shape.BOOL
                    && com.strata.model.ValueType.ofSwitchWord("5").shape() == com.strata.model.ValueType.Shape.TEXT;
        });

        SelfTestHarness.check(lines, tally,
                "VT2 the block stats: tool, level, push reaction, instrument are words; the switches true or false", () -> {
            var tool = com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.TOOL);
            var inst = com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.INSTRUMENT);
            var req = com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.REQUIRES_TOOL);
            var light = com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.LIGHT_LEVEL);
            var fric = com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.FRICTION);
            boolean allBlockKeys = com.strata.model.BlockStats.KEYS.stream().allMatch(k -> {
                var t = com.strata.model.BlockStats.typeOf(k);
                return t.isChosen() ? t.choices().stream().allMatch(w -> com.strata.model.BlockStats.problem(k, w) == null)
                        : t.shape() != com.strata.model.ValueType.Shape.TEXT;
            });
            return tool.shape() == com.strata.model.ValueType.Shape.ENUM && tool.choices().equals(com.strata.model.BlockStats.TOOLS)
                    && inst.shape() == com.strata.model.ValueType.Shape.ENUM && inst.choices().contains("harp")
                    && com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.TOOL_LEVEL).choices()
                    .equals(com.strata.model.BlockStats.TOOL_LEVELS)
                    && com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.PUSH_REACTION).choices()
                    .equals(com.strata.model.BlockStats.PUSH_REACTIONS)
                    && req.shape() == com.strata.model.ValueType.Shape.BOOL
                    && com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.REPLACEABLE).shape() == com.strata.model.ValueType.Shape.BOOL
                    && com.strata.model.BlockStats.typeOf(com.strata.model.BlockStats.IGNITED_BY_LAVA).shape() == com.strata.model.ValueType.Shape.BOOL
                    && light.shape() == com.strata.model.ValueType.Shape.WHOLE && light.max() == 15
                    && fric.shape() == com.strata.model.ValueType.Shape.NUMBER && fric.max() == 1
                    && allBlockKeys;
        });

        SelfTestHarness.check(lines, tally,
                "VT3 the item stats: rarity is a word, the switches true or false, the stack a whole number 1 to 99", () -> {
            var rarity = com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.RARITY);
            var stack = com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.MAX_STACK_SIZE);
            var attr = com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.attributeKey("minecraft:generic.attack_damage", "mainhand"));
            boolean agrees = List.of(com.strata.model.ItemStats.RARITY, com.strata.model.ItemStats.FIRE_RESISTANT,
                    com.strata.model.ItemStats.CAN_ALWAYS_EAT).stream().allMatch(k ->
                    com.strata.model.ItemStats.typeOf(k).choices().equals(com.strata.model.ItemStats.choices(k))
                            && com.strata.model.ItemStats.choices(k).stream().allMatch(w ->
                            com.strata.model.ItemStats.problem(k, w, false) == null));
            var glint = com.strata.model.ItemVariant.typeOf(com.strata.model.ItemVariant.GLINT);
            var ench = com.strata.model.ItemVariant.typeOf(com.strata.model.ItemVariant.enchantKey("minecraft:sharpness"));
            return rarity.shape() == com.strata.model.ValueType.Shape.ENUM && rarity.choices().equals(com.strata.model.ItemStats.RARITIES)
                    && com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.FIRE_RESISTANT).shape() == com.strata.model.ValueType.Shape.BOOL
                    && com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.CAN_ALWAYS_EAT).shape() == com.strata.model.ValueType.Shape.BOOL
                    && stack.shape() == com.strata.model.ValueType.Shape.WHOLE && stack.min() == 1 && stack.max() == 99
                    && attr.shape() == com.strata.model.ValueType.Shape.NUMBER
                    && com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.NUTRITION).shape() == com.strata.model.ValueType.Shape.WHOLE
                    && com.strata.model.ItemStats.typeOf(com.strata.model.ItemStats.EAT_SECONDS).shape() == com.strata.model.ValueType.Shape.NUMBER
                    && agrees
                    && glint.shape() == com.strata.model.ValueType.Shape.ENUM && glint.choices().equals(com.strata.model.ItemVariant.GLINTS)
                    && com.strata.model.ItemVariant.typeOf(com.strata.model.ItemVariant.UNBREAKABLE).shape() == com.strata.model.ValueType.Shape.BOOL
                    && ench.shape() == com.strata.model.ValueType.Shape.WHOLE && ench.max() == 255
                    && com.strata.model.ItemVariant.typeOf(com.strata.model.ItemStats.RARITY).shape() == com.strata.model.ValueType.Shape.ENUM;
        });

    }

    private static void runTestsPart06(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "VT4 mob data rows: tags and flags are true or false, the model a living mob, sounds and particles picked, numbers typed", () -> {
            java.util.function.Function<String[], com.strata.domain.MobDataOps.Value> row = a ->
                    new com.strata.domain.MobDataOps.Value(a[0], a[1], a[2], "", "");
            var tag = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.TAG, "minecraft:undead", "true"}));
            var flag = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{"flags", Schema.MobFlags.FIRE_IMMUNE, ""}));
            var model = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.MODEL, "model", "minecraft:zombie"}));
            var texture = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.MODEL, "texture", "x"}));
            var particle = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{"particles", Schema.MobParticles.PARTICLE_TYPE, ""}));
            var sound = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.SOUNDS, Schema.MobSounds.AMBIENT_SOUND, ""}));
            var suppress = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{"visuals", Schema.MobVisuals.SUPPRESS_BASE_PARTICLES, ""}));
            var health = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "20.0"}));
            var whole = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.FIELD, "Mob.xpReward", "5"}));
            var word = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.FIELD, "Mob.name", "abc"}));
            var gameFlag = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.FIELD, "Mob.noAi", "false"}));
            var count = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{"particles", Schema.MobParticles.COUNT, ""}));
            var vol = com.strata.domain.MobDataOps.typeOf(row.apply(new String[]{GameLayer.SOUNDS, Schema.MobSounds.AMBIENT_VOLUME, ""}));
            return tag.shape() == com.strata.model.ValueType.Shape.BOOL && flag.shape() == com.strata.model.ValueType.Shape.BOOL
                    && model.kind() == com.strata.model.ValueType.Registry.LIVING_MOB
                    && texture.kind() == com.strata.model.ValueType.Registry.TEXTURE
                    && particle.kind() == com.strata.model.ValueType.Registry.PARTICLE
                    && sound.kind() == com.strata.model.ValueType.Registry.SOUND
                    && suppress.shape() == com.strata.model.ValueType.Shape.BOOL
                    && health.shape() == com.strata.model.ValueType.Shape.NUMBER
                    && whole.shape() == com.strata.model.ValueType.Shape.WHOLE
                    && word.shape() == com.strata.model.ValueType.Shape.TEXT
                    && gameFlag.shape() == com.strata.model.ValueType.Shape.BOOL
                    && count.shape() == com.strata.model.ValueType.Shape.WHOLE
                    && vol.shape() == com.strata.model.ValueType.Shape.NUMBER;
        });

        SelfTestHarness.check(lines, tally,
                "VT5 mob caps, options, special spawns: switches and choices are dropdowns, a mob is picked, counts are fields", () -> {
            var friendly = com.strata.domain.MobCapOps.typeOf(new com.strata.domain.MobCapOps.Value("monster", "friendly", "false", ""));
            var persistent = com.strata.domain.MobCapOps.typeOf(new com.strata.domain.MobCapOps.Value("monster", "persistent", "false", ""));
            var max = com.strata.domain.MobCapOps.typeOf(new com.strata.domain.MobCapOps.Value("monster", "max", "70", ""));
            var dist = com.strata.domain.MobCapOps.typeOf(new com.strata.domain.MobCapOps.Value("monster", "despawn_distance", "128", ""));
            boolean caps = friendly.shape() == com.strata.model.ValueType.Shape.BOOL
                    && persistent.shape() == com.strata.model.ValueType.Shape.BOOL
                    && max.shape() == com.strata.model.ValueType.Shape.WHOLE && dist.shape() == com.strata.model.ValueType.Shape.WHOLE;
            java.util.function.BiFunction<com.strata.domain.OptionsOps.Kind, List<String>, com.strata.model.ValueType> of =
                    (kind, words) -> com.strata.domain.OptionsOps.typeOf(new com.strata.domain.OptionsOps.Setting("k", "K", "", "",
                    kind, "", "", 0.5, 4, words));
            var choice = of.apply(com.strata.domain.OptionsOps.Kind.CHOICE, List.of("auto", "1", "2"));
            var num = of.apply(com.strata.domain.OptionsOps.Kind.NUMBER, List.of());
            boolean options = choice.shape() == com.strata.model.ValueType.Shape.ENUM
                    && choice.choices().equals(List.of("auto", "1", "2"))
                    && num.shape() == com.strata.model.ValueType.Shape.NUMBER && num.min() == 0.5 && num.max() == 4
                    && of.apply(com.strata.domain.OptionsOps.Kind.SWITCH, List.of()).choices().equals(List.of("On", "Off"))
                    && of.apply(com.strata.domain.OptionsOps.Kind.SPAWN, List.of()).shape() == com.strata.model.ValueType.Shape.TEXT;
            boolean spawns = com.strata.model.SpecialSpawners.SETTINGS.stream().allMatch(s -> {
                if (s.kind() == com.strata.model.SpecialSpawners.Kind.VILLAGER_TYPE
                        || s.kind() == com.strata.model.SpecialSpawners.Kind.PROFESSION) {
                    return com.strata.domain.SpecialSpawnsOps.picked(s);
                }
                var t = com.strata.domain.SpecialSpawnsOps.typeOf(s);
                return switch (s.kind()) {
                    case SWITCH -> t.choices().equals(List.of("On", "Off"));
                    case MOB -> t.kind() == com.strata.model.ValueType.Registry.ENTITY;
                    case WHOLE -> t.shape() == com.strata.model.ValueType.Shape.WHOLE;
                    case PERCENT -> t.shape() == com.strata.model.ValueType.Shape.WHOLE && t.max() == 100;
                    default -> true;
                };
            });
            return caps && options && spawns;
        });

        SelfTestHarness.check(lines, tally,
                "VT6 loot and override fields: a boolean is true or false, an entry's item is picked, other values are typed", () -> {
            java.util.List<com.strata.domain.LootTableOps.Value> all = List.of(
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.type", "\"minecraft:item\"", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.name", "\"minecraft:bone\"", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.1.type", "\"minecraft:tag\"", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.1.name", "\"minecraft:logs\"", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.weight", "3", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.functions.0.add", "false", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.functions.0.chance", "0.5", ""));
            java.util.function.Function<Integer, com.strata.model.ValueType> t = i ->
                    com.strata.domain.LootTableOps.typeOf(all.get(i), all);
            boolean loot = t.apply(1).kind() == com.strata.model.ValueType.Registry.ITEM
                    && t.apply(3).shape() == com.strata.model.ValueType.Shape.TEXT
                    && t.apply(0).shape() == com.strata.model.ValueType.Shape.TEXT
                    && t.apply(4).shape() == com.strata.model.ValueType.Shape.WHOLE
                    && t.apply(5).shape() == com.strata.model.ValueType.Shape.BOOL
                    && t.apply(6).shape() == com.strata.model.ValueType.Shape.NUMBER;
            boolean paths = com.strata.model.JsonPaths.typeOf("cookingtime", "200").shape() == com.strata.model.ValueType.Shape.TEXT
                    && com.strata.model.JsonPaths.typeOf("ignore", "true").shape() == com.strata.model.ValueType.Shape.BOOL
                    && com.strata.model.JsonPaths.typeOf("ingredient.item", "minecraft:iron_ore").kind() == com.strata.model.ValueType.Registry.ITEM
                    && com.strata.model.JsonPaths.typeOf("result.id", "minecraft:iron_ingot").kind() == com.strata.model.ValueType.Registry.ITEM
                    && com.strata.model.JsonPaths.typeOf("key.X.item", "minecraft:stick").kind() == com.strata.model.ValueType.Registry.ITEM
                    && com.strata.model.JsonPaths.typeOf("ingredient.tag", "c:ores").shape() == com.strata.model.ValueType.Shape.TEXT
                    && com.strata.model.JsonPaths.typeOf("ingredient.item", "#c:ores").shape() == com.strata.model.ValueType.Shape.TEXT;
            return loot && paths;
        });

        SelfTestHarness.check(lines, tally,
                "VT7 a goal's item list comes off one item at a time; Mob caps is under World / Mobs, Player base stats under Options", () -> {
            return com.strata.model.GoalType.withoutLastItem("a:b+c:d+e:f").equals("a:b+c:d")
                    && com.strata.model.GoalType.withoutLastItem("a:b").isEmpty()
                    && com.strata.model.GoalType.withoutLastItem("").isEmpty()
                    && "World".equals(com.strata.ui.TitlePath.rootOfClass("MobCapScreen"))
                    && "Options".equals(com.strata.ui.TitlePath.rootOfClass("PlayerBaseStatsScreen"))
                    && com.strata.ui.TitlePath.compose("World", "Strata — Mobs: Mob caps").equals("Strata / World / Mobs / Mob caps")
                    && com.strata.ui.TitlePath.compose("Scaling", "Strata — Player scaling: Player base stats")
                    .equals("Strata / Scaling / Player scaling / Player base stats");
        });

        SelfTestHarness.check(lines, tally,
                "OG1 override entries sort by namespace then path, and big namespaces group by folder", () -> {
            List<String> ids = new ArrayList<>(List.of("zeta:b", "c:ingots", "minecraft:z", "minecraft:a", "alpha:x", "c:dyes"));
            ids.sort(com.strata.domain.OverrideOps::compareIds);
            boolean sorted = ids.equals(List.of("minecraft:a", "minecraft:z", "c:dyes", "c:ingots", "alpha:x", "zeta:b"));
            List<com.strata.domain.OverrideOps.Entry> rows = new ArrayList<>();
            for (int i = 0; i < 90; i++) {
                rows.add(new com.strata.domain.OverrideOps.Entry("minecraft:" + (i < 50 ? "mineable/t" : "wool/w") + i,
                        "", "", com.strata.domain.OverrideOps.State.GAME));
            }
            rows.add(new com.strata.domain.OverrideOps.Entry("minecraft:root", "", "", com.strata.domain.OverrideOps.State.GAME));
            rows.add(new com.strata.domain.OverrideOps.Entry("mod:a/b", "", "", com.strata.domain.OverrideOps.State.GAME));
            rows.add(new com.strata.domain.OverrideOps.Entry("mod:c", "", "", com.strata.domain.OverrideOps.State.GAME));
            rows.sort(java.util.Comparator.comparing(com.strata.domain.OverrideOps.Entry::id,
                    com.strata.domain.OverrideOps::compareIds));
            var sections = com.strata.domain.OverrideOps.sections(rows);
            List<String> names = sections.stream().map(s -> s.group() + "/" + s.sub()).toList();
            int total = sections.stream().mapToInt(s -> s.indexes().size()).sum();
            List<com.strata.domain.OverrideOps.Entry> one = new ArrayList<>();
            for (int i = 0; i < 34; i++) {
                one.add(new com.strata.domain.OverrideOps.Entry("minecraft:e" + i, "", "",
                        com.strata.domain.OverrideOps.State.GAME));
            }
            var flat = com.strata.domain.OverrideOps.sections(one);
            boolean oneFlat = flat.size() == 1 && flat.get(0).group().isEmpty() && flat.get(0).sub().isEmpty()
                    && flat.get(0).indexes().size() == 34;
            return sorted && total == rows.size() && oneFlat
                    && names.equals(List.of("minecraft/mineable", "minecraft/wool", "minecraft/(top level)", "/"));
        });

        SelfTestHarness.check(lines, tally,
                "OG2 Items splits Other and Blocks into sub-groups, Misc and Other last", () -> {
            List<String> kinds = List.of("Weapons", "Blocks", "Blocks", "Blocks", "Other", "Other", "Other", "Other", "Other");
            List<String> ids = List.of("minecraft:sword", "minecraft:acacia_stairs", "minecraft:red_wool",
                    "minecraft:zz_thing", "minecraft:cow_spawn_egg", "minecraft:red_dye", "minecraft:stick",
                    "minecraft:iron_ingot", "minecraft:zombie_spawn_egg");
            var sections = com.strata.domain.ItemGroupOps.sections(kinds, ids);
            List<String> names = sections.stream().map(s -> s.kind() + "/" + s.sub()).toList();
            return names.equals(List.of("Weapons/", "Blocks/Colored", "Blocks/Building", "Blocks/Other",
                    "Other/Spawn eggs", "Other/Dyes", "Other/Ingots and nuggets", "Other/Misc"))
                    && sections.get(4).indexes().equals(List.of(4, 8))
                    && com.strata.domain.ItemGroupOps.sub("Blocks", "minecraft:iron_ore").equals("Ores")
                    && com.strata.domain.ItemGroupOps.sub("Blocks", "minecraft:oak_planks").equals("Wood")
                    && com.strata.domain.ItemGroupOps.sub("Food", "minecraft:bread").isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "OB1 an ore row names every block it places, stone first", () -> {
            Map<net.minecraft.resources.ResourceLocation, com.strata.game.OreDetector.OreBlock> blocks =
                    new java.util.LinkedHashMap<>();
            blocks.put(net.minecraft.resources.ResourceLocation.parse("minecraft:deepslate_redstone_ore"),
                    new com.strata.game.OreDetector.OreBlock("redstone", "tag:minecraft:deepslate_ore_replaceables"));
            blocks.put(net.minecraft.resources.ResourceLocation.parse("minecraft:iron_ore"),
                    new com.strata.game.OreDetector.OreBlock("iron", "tag:minecraft:stone_ore_replaceables"));
            blocks.put(net.minecraft.resources.ResourceLocation.parse("minecraft:redstone_ore"),
                    new com.strata.game.OreDetector.OreBlock("redstone", "tag:minecraft:stone_ore_replaceables"));
            boolean redstone = com.strata.domain.OreOps.blocksOf("redstone", blocks)
                    .equals(List.of("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"));
            boolean none = com.strata.domain.OreOps.blocksOf("gold", blocks).isEmpty();
            return redstone && none;
        });

        SelfTestHarness.check(lines, tally,
                "NB1 a mob's behavior screen names its list; the archetype editor has its own name field", () -> {
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            var frame = new com.strata.ui.Frame(640, 360);
            var mob = com.strata.ui.screen.ListScreenLayouts.goalsList(frame, m, "Strata", false, "", "");
            var goalSet = com.strata.ui.screen.ListScreenLayouts.goalsList(frame, m, "Strata", true, "", "");
            boolean mobHas = mob.get("name") != null && mob.get("saveAs") != null && mob.get("clear") == null
                    && mob.get("name").kind() == com.strata.ui.Layout.Kind.FIELD;
            boolean goalSetHas = goalSet.get("name") != null && goalSet.get("saveAs") == null;
            return mobHas && goalSetHas;
        });

        SelfTestHarness.check(lines, tally,
                "ST1 every setting the editor offers checks its value; a tier offers only the modes that work", () -> {
            var toggle = new com.strata.domain.OptionsOps.Setting("enablePlayerScaling", "Player scaling", "", "",
                    com.strata.domain.OptionsOps.Kind.SWITCH, "Off", "Off", 0, 0, List.of());
            var number = new com.strata.domain.OptionsOps.Setting("mobXpExponent", "Mob XP exponent", "", "",
                    com.strata.domain.OptionsOps.Kind.NUMBER, "0.8", "0.8", 0.1, 10.0, List.of());
            var choice = new com.strata.domain.OptionsOps.Setting("editorKey", "Editor key", "", "",
                    com.strata.domain.OptionsOps.Kind.CHOICE, "NONE", "NONE", 0, 0, List.of("NONE", "K", "P", "O"));
            boolean switches = com.strata.domain.OptionsOps.problem(toggle, "on") == null
                    && com.strata.domain.OptionsOps.problem(toggle, "False") == null
                    && com.strata.domain.OptionsOps.problem(toggle, "maybe") != null
                    && Boolean.TRUE.equals(com.strata.domain.OptionsOps.parseSwitch("Yes"));
            boolean numbers = com.strata.domain.OptionsOps.problem(number, "1.5") == null
                    && com.strata.domain.OptionsOps.problem(number, "0.05") != null
                    && com.strata.domain.OptionsOps.problem(number, "NaN") != null
                    && com.strata.domain.OptionsOps.problem(number, "x") != null;
            boolean choices = com.strata.domain.OptionsOps.problem(choice, "k") == null
                    && com.strata.domain.OptionsOps.problem(choice, "Q") != null;
            boolean modes = com.strata.domain.TierOps.SPAWN_MODES.equals(List.of("add", "replace"));
            return switches && numbers && choices && modes;
        });

        SelfTestHarness.check(lines, tally,
                "CW1 every code a dropdown offers shows as a word; a data value shows as it is", () -> {
            List<String> codes = new ArrayList<>();
            codes.addAll(com.strata.domain.LootOps.MODE_CHOICES);
            codes.addAll(com.strata.domain.LootOps.CONDITION_CHOICES);
            codes.addAll(com.strata.domain.SpawnRulesOps.TYPE_CHOICES);
            codes.addAll(com.strata.model.SpawnRule.TIME_CHOICES);
            codes.addAll(com.strata.model.SpawnRule.WEATHER_CHOICES);
            codes.addAll(com.strata.model.SpawnRule.SURFACE_CHOICES);
            codes.addAll(com.strata.model.GoalCondition.CHOICES);
            codes.addAll(com.strata.model.GoalType.DOOR_DIFFICULTIES);
            codes.addAll(com.strata.domain.TierOps.SPAWN_MODES);
            codes.addAll(com.strata.domain.EquipmentOps.SLOT_CHOICES);
            codes.addAll(com.strata.domain.AbilitiesOps.abilityChoices());
            List<String> missing = codes.stream().filter(c -> !com.strata.ui.ChoiceWords.has(c)).distinct().toList();
            boolean shown = com.strata.ui.Layout.choiceText("Mode", "add").equals("Mode: Add")
                    && com.strata.ui.Layout.choiceText("", "health_below").equals("Health below %")
                    && com.strata.ui.Layout.choiceText("Show", "biomesoplenty").equals("Show: biomesoplenty");
            if (!missing.isEmpty() || !shown) {
                lines.add("    CW1 no word for " + missing + " shown " + shown);
            }
            return missing.isEmpty() && shown;
        });

        SelfTestHarness.check(lines, tally,
                "FW1 a refused field says why; every condition and every default equipment number says what it is", () -> {
            var number = com.strata.client.widget.EditField.numericValidator();
            var atLeastZero = com.strata.client.widget.EditField.nonNegativeIntegerValidator();
            var text = com.strata.client.widget.EditField.csvTextValidator();
            boolean reasons = number.problem("1.5") == null && number.problem("abc") != null
                    && number.problem("NaN") != null && !number.test("abc") && number.test("")
                    && atLeastZero.problem("3") == null && atLeastZero.problem("-1") != null
                    && text.problem("a, b") == null && text.problem("a\nb") != null;
            List<String> noHelp = new ArrayList<>();
            for (var type : com.strata.model.SpawnRule.Type.values()) {
                if (com.strata.model.SpawnRule.help(type).isBlank()) {
                    noHelp.add(type.name());
                }
            }
            for (var field : com.strata.domain.EquipmentOps.EquipmentField.values()) {
                if (com.strata.domain.EquipmentOps.equipmentTip(field).isBlank()) {
                    noHelp.add(field.name());
                }
            }
            if (!reasons || !noHelp.isEmpty()) {
                lines.add("    FW1 reasons " + reasons + " no help: " + noHelp);
            }
            return reasons && noHelp.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "KY1 Enter saves, Ctrl+Z undoes, no redo; a dialog's button names its action", () -> {
            boolean keys = "save".equals(com.strata.ui.Keys.action(com.strata.ui.Keys.ENTER, false, false))
                    && "save".equals(com.strata.ui.Keys.action(com.strata.ui.Keys.KEYPAD_ENTER, false, false))
                    && "undo".equals(com.strata.ui.Keys.action(com.strata.ui.Keys.Z, true, false))
                    && "undo".equals(com.strata.ui.Keys.action(com.strata.ui.Keys.Z, true, true))
                    && com.strata.ui.Keys.action(89, true, false) == null
                    && com.strata.ui.Keys.action(com.strata.ui.Keys.Z, false, false) == null
                    && "remove".equals(com.strata.ui.Keys.action(com.strata.ui.Keys.DELETE, false, false))
                    && com.strata.ui.Keys.action(65, true, false) == null
                    && com.strata.ui.Keys.buttonsFor("save").equals(List.of("save", "set"))
                    && com.strata.ui.Keys.buttonsFor("undo").equals(List.of("undo"))
                    && com.strata.ui.Keys.buttonsFor(null).isEmpty();
            boolean words = com.strata.ui.screen.ConfirmLayout.actionWord("Delete the archetype 'x'?").equals("Delete")
                    && com.strata.ui.screen.ConfirmLayout.actionWord("Remove 3 drops?").equals("Remove")
                    && com.strata.ui.screen.ConfirmLayout.actionWord("Restore all of this mob's default equipment?")
                            .equals("Restore")
                    && com.strata.ui.screen.ConfirmLayout.actionWord("Your edits are not saved.").equals("Confirm");
            return keys && words;
        });
        SelfTestHarness.check(lines, tally,
                "CL1 a dropdown's options open under its button, or over it at the bottom, and a click finds one", () -> {
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            List<String> words = List.of("Add", "Replace", "Deny");
            com.strata.ui.Box top = new com.strata.ui.Box(40, 30, 100, 20);
            com.strata.ui.Layout under = com.strata.ui.screen.ChoiceListLayout.build(f, m, top, words, 0);
            com.strata.ui.Box p1 = under.get("panel").box();
            boolean below = p1.y() > top.bottom() - 1 && p1.w() >= top.w()
                    && under.get("option2") != null && under.get("option3") == null;
            com.strata.ui.Box low = new com.strata.ui.Box(40, 215, 100, 20);
            com.strata.ui.Box p2 = com.strata.ui.screen.ChoiceListLayout.build(f, m, low, words, 0)
                    .get("panel").box();
            boolean above = p2.bottom() <= low.y() && p2.inside(f.screen());
            com.strata.ui.Box row1 = com.strata.ui.screen.ChoiceListLayout.rowBox(under, 1);
            boolean hit = com.strata.ui.screen.ChoiceListLayout.optionAt(under, row1.x() + 5, row1.y() + 5) == 1
                    && com.strata.ui.screen.ChoiceListLayout.optionAt(under, 5, 5) == -1;
            List<String> many = java.util.stream.IntStream.range(0, 40).mapToObj(i -> "o" + i).toList();
            int rows = com.strata.ui.screen.ChoiceListLayout.rows(f, 40);
            com.strata.ui.Layout end = com.strata.ui.screen.ChoiceListLayout.build(f, m, top, many, 99);
            boolean scrolls = rows < 40 && end.get("option39") != null && end.get("option" + (39 - rows)) == null
                    && end.get("panel").box().inside(f.screen());
            int[] open = com.strata.ui.screen.ChoiceListLayout.mouseForScreen(true, 50, 60);
            int[] shut = com.strata.ui.screen.ChoiceListLayout.mouseForScreen(false, 50, 60);
            boolean hover = open[0] < 0 && open[1] < 0 && shut[0] == 50 && shut[1] == 60;
            return below && above && hit && scrolls && hover;
        });
        SelfTestHarness.check(lines, tally,
                "GS1 a group may be 0 to Max; Strata rolls it, and the game's entry is a group of one", () -> {
            boolean fields = com.strata.domain.VariantOps.groupProblem("", "") == null
                    && com.strata.domain.VariantOps.groupProblem("2", "2") == null
                    && com.strata.domain.VariantOps.groupProblem("0", "4") == null
                    && com.strata.domain.VariantOps.groupProblem("0", "0") == null
                    && com.strata.domain.VariantOps.groupProblem("5", "") != null
                    && com.strata.domain.VariantOps.groupProblem("5", "2") != null
                    && com.strata.domain.VariantOps.groupProblem("1", "-3") != null
                    && com.strata.domain.VariantOps.groupProblem("x", "4") != null
                    && com.strata.domain.VariantOps.weightProblem("0") == null
                    && com.strata.domain.VariantOps.weightProblem("-1") != null;
            boolean sizes = com.strata.model.SpawnPlan.groupSize(0, 2, 0.0) == 0
                    && com.strata.model.SpawnPlan.groupSize(0, 2, 0.34) == 1
                    && com.strata.model.SpawnPlan.groupSize(0, 2, 0.99) == 2
                    && com.strata.model.SpawnPlan.groupSize(0, 0, 0.5) == 0
                    && com.strata.model.SpawnPlan.groupSize(3, 3, 0.7) == 3
                    && com.strata.model.SpawnPlan.groupSize(5, 2, 0.1) == 2;
            var row = new com.strata.model.Spawn(com.strata.model.SpawnCategory.MONSTER, "minecraft:skeleton", "",
                    20, 0, 4, true, null, List.of(), com.strata.model.Source.tier("t"));
            var entry = com.strata.model.SpawnPlan.plan(List.of(row), s -> s.entity(), k -> null)
                    .get("minecraft:skeleton");
            boolean one = entry.weight() == 20 && entry.min() == 1 && entry.max() == 1;
            boolean table = row.minCount() == 0;
            return fields && sizes && one && table;
        });
        SelfTestHarness.check(lines, tally,
                "A50.2 weight picks the row, then its spawn chance decides; the game's own rows are left alone", () -> {
            var tierSource = com.strata.model.Source.tier("t");
            var plain = new com.strata.model.Spawn(com.strata.model.SpawnCategory.MONSTER, "minecraft:skeleton", "",
                    30, 1, 4, true, null, List.of(), tierSource, 25);
            var variant = new com.strata.model.Spawn(com.strata.model.SpawnCategory.MONSTER, "minecraft:skeleton",
                    "big", 10, 1, 1, true, null, List.of(), tierSource);
            var off = new com.strata.model.Spawn(com.strata.model.SpawnCategory.MONSTER, "minecraft:skeleton",
                    "gone", 90, 1, 1, false, null, List.of(), tierSource);
            var rows = List.of(plain, off, variant);
            boolean pick = com.strata.model.SpawnPlan.pickRow(rows, 0.0) == plain
                    && com.strata.model.SpawnPlan.pickRow(rows, 0.74) == plain
                    && com.strata.model.SpawnPlan.pickRow(rows, 0.76) == variant
                    && com.strata.model.SpawnPlan.pickRow(List.of(off), 0.5) == null;
            boolean chance = plain.chance() == 25 && variant.chance() == 100
                    && com.strata.model.SpawnPlan.passesChance(25, 0.24)
                    && !com.strata.model.SpawnPlan.passesChance(25, 0.25)
                    && !com.strata.model.SpawnPlan.passesChance(0, 0.0)
                    && com.strata.model.SpawnPlan.passesChance(100, 0.999)
                    && new com.strata.model.Spawn(com.strata.model.SpawnCategory.MONSTER, "a", "", 1, 1, 1, true,
                    null, List.of(), tierSource, 150).chance() == 100
                    && plain.withSource(com.strata.model.Source.GLOBAL).chance() == 25;
            boolean form = com.strata.domain.VariantOps.chanceProblem("") == null
                    && com.strata.domain.VariantOps.chanceProblem("0") == null
                    && com.strata.domain.VariantOps.chanceProblem("37.5") == null
                    && com.strata.domain.VariantOps.chanceProblem("101") != null
                    && com.strata.domain.VariantOps.chanceProblem("x") != null;
            var game = new com.strata.model.Spawn(com.strata.model.SpawnCategory.CREATURE, "minecraft:cow", "",
                    8, 4, 4, true, null, List.of(), com.strata.model.Source.GAME);
            boolean whose = com.strata.model.SpawnPlan.groupsByStrata(rows)
                    && !com.strata.model.SpawnPlan.groupsByStrata(List.of(game));
            if (!(pick && chance && form && whose)) {
                lines.add("    A50.2 pick=" + pick + " chance=" + chance + " form=" + form + " whose=" + whose);
                return false;
            }
            return true;
        });
        SelfTestHarness.check(lines, tally,
                "CR1 the crash audit's values the game cannot run are refused or made safe", () -> {
            var stroll = com.strata.model.GoalType.of("RandomStrollGoal");
            var swim = com.strata.model.GoalType.of("RandomSwimmingGoal");
            var hunt = com.strata.model.GoalType.of("NearestAttackableTargetGoal");
            boolean ai = com.strata.model.GoalType.problem(stroll, Map.of("interval", "0")) != null
                    && com.strata.model.GoalType.problem(swim, Map.of("interval", "0")) != null
                    && com.strata.model.GoalType.problem(stroll, Map.of("interval", "1")) == null
                    && com.strata.model.GoalType.problem(hunt, Map.of("interval", "0")) == null;
            boolean drops = com.strata.model.LootEntry.stacks(150, 64).equals(List.of(64, 64, 22))
                    && com.strata.model.LootEntry.stacks(3, 1).equals(List.of(1, 1, 1))
                    && com.strata.model.LootEntry.stacks(0, 64).isEmpty();
            boolean weight = com.strata.domain.VariantOps.weightProblem(
                    String.valueOf(com.strata.model.Spawn.MAX_WEIGHT)) == null
                    && com.strata.domain.VariantOps.weightProblem(
                    String.valueOf(com.strata.model.Spawn.MAX_WEIGHT + 1)) != null;
            boolean ores = com.strata.model.OreFrequency.rarityFor(0) == Integer.MAX_VALUE
                    && com.strata.model.OreFrequency.rarityFor(50) == 2
                    && com.strata.runtime.OreConfig.generating(List.of("coal", "iron", "gold"),
                    Map.of("coal", 0.0, "iron", 50.0)).equals(List.of("iron", "gold"));
            return ai && drops && weight && ores;
        });
        SelfTestHarness.check(lines, tally,
                "CM1 a control matches its choice: unused fields grey, the open tab greys, the goal says Done", () -> {
            boolean loot = !com.strata.domain.LootOps.conditionTakesValue(com.strata.domain.LootOps.NO_CONDITION)
                    && com.strata.domain.LootOps.conditionTakesValue("death_cause")
                    && com.strata.domain.LootOps.conditionTakesValue("random_chance")
                    && com.strata.domain.LootOps.conditionTakesValue("looting");
            var amount = com.strata.model.AbilityField.AMOUNT;
            var ticks = com.strata.model.AbilityField.TICKS;
            var radius = com.strata.model.AbilityField.RADIUS;
            boolean ability = !com.strata.domain.AbilitiesOps.usesField("sun_immune", amount)
                    && !com.strata.domain.AbilitiesOps.usesField("sun_immune", ticks)
                    && com.strata.domain.AbilitiesOps.usesField("heal", amount)
                    && com.strata.domain.AbilitiesOps.usesField("heal", ticks)
                    && !com.strata.domain.AbilitiesOps.usesField("heal", radius)
                    && com.strata.domain.AbilitiesOps.usesField("aura_players", radius);
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            com.strata.ui.Layout mobs = com.strata.ui.screen.ListScreenLayouts.scaling(f, m, "t", true, "s", "Chunk scaling: Off", "", "",
                    "");
            com.strata.ui.Layout players = com.strata.ui.screen.ListScreenLayouts.scaling(f, m, "t", false, "s", null, "",
                    "", "");
            boolean tabs = mobs.get("mobs") == null && players.get("players") == null
                    && mobs.get("base") == null && players.get("base") == null;
            com.strata.ui.Layout goal = com.strata.ui.screen.MenuLayouts.goal(f, m, "t", List.of(), "");
            boolean done = "Done".equals(goal.get("save").text());
            boolean scope = com.strata.ui.ChoiceWords.has("global") && com.strata.ui.ChoiceWords.has("tier")
                    && com.strata.ui.ChoiceWords.has("biome");
            return loot && ability && tabs && done && scope;
        });
        SelfTestHarness.check(lines, tally,
                "SR1 a search finds a row by the name it shows or by its id, and switch codes have words", () -> {
            boolean search = com.strata.ui.Search.matches("zombie villager", "minecraft:zombie_villager")
                    && com.strata.ui.Search.matches("Dark Forest", "minecraft:dark_forest", "Dark Forest")
                    && com.strata.ui.Search.matches("dark_for", "x", "Dark Forest")
                    && com.strata.ui.Search.matches("", "anything")
                    && !com.strata.ui.Search.matches("husk", "minecraft:zombie", "Zombie");
            boolean words = "Inherit".equals(com.strata.ui.ChoiceWords.word("inherit"))
                    && "On".equals(com.strata.ui.ChoiceWords.word("on"))
                    && "Off".equals(com.strata.ui.ChoiceWords.word("off"));
            return search && words;
        });
        SelfTestHarness.check(lines, tally,
                "WD1 a switch shows On or Off, and a refused profile name says why", () -> {
            boolean words = "Own entity: On".equals(com.strata.ui.Layout.toggleText("Own entity", true))
                    && "Off".equals(com.strata.ui.Layout.toggleText("", false));
            boolean name = com.strata.data.ProfileManager.nameProblem("my pack") == null
                    && com.strata.data.ProfileManager.nameProblem("") != null
                    && com.strata.data.ProfileManager.nameProblem("a/b") != null
                    && com.strata.data.ProfileManager.nameProblem("end.") != null;
            return words && name;
        });
        SelfTestHarness.check(lines, tally,
                "DB1 Options lists the debug HUD, and the debug HUD says the biome, tier, and levels", () -> {
            boolean menu = com.strata.domain.OptionsOps.OPTION_KEYS.contains(com.strata.domain.OptionsOps.DEBUG_HUD)
                    && !com.strata.ui.screen.ListScreenLayouts.menuTools().contains("Debug");
            List<String> tiered = com.strata.ui.screen.DebugHudLayout.lines("Plains (minecraft:plains)", "t1", 5,
                    1.5, 12, true);
            List<String> plain = com.strata.ui.screen.DebugHudLayout.lines("Plains (minecraft:plains)", null, 0,
                    1.0, 3, false);
            boolean hud = tiered.contains("Tier: t1") && tiered.contains("Area level: 5, coefficient 1.50")
                    && tiered.contains("Your XP level: 12")
                    && plain.contains("Tier: none") && plain.contains("Area level: 0 (mobs are the game's own)")
                    && plain.contains("Your XP level: 3 (player scaling is Off)");
            com.strata.ui.Layout l = com.strata.ui.screen.DebugHudLayout.build(new com.strata.ui.Frame(427, 240),
                    s -> s.length() * 6, tiered);
            com.strata.ui.Box p = com.strata.ui.screen.DebugHudLayout.panel(l);
            boolean panel = l.elements().stream().allMatch(e -> e.box().inside(p));
            return menu && hud && panel;
        });
        SelfTestHarness.check(lines, tally,
                "NB1 a behavior saved by name becomes the mob editor's behavior, and nothing else in its form changes", () -> {
            var form = new com.strata.domain.VariantOps.VariantForm("", "", "minecraft:zombie", "1.5", "", false,
                    "zombie", "tier", "t1", "10", "1", "4", true, "off");
            var named = form.withGoalSet("tank_zombie");
            boolean goalSet = "tank_zombie".equals(named.goalSet());
            boolean kept = named.withGoalSet("zombie").equals(form);
            boolean blank = "".equals(form.withGoalSet(null).goalSet());
            return goalSet && kept && blank;
        });
        SelfTestHarness.check(lines, tally,
                "PB1 the Goal sets screen has + Add goal set on top, and for a mob Use and Edit this mob's goals; recent goal sets lead", () -> {
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            List<String> groups = List.of("All", "goal_set");
            com.strata.ui.Layout client = com.strata.ui.screen.ListScreenLayouts.goalSetsList(f, m, "t", false,
                    false, false, "");
            com.strata.ui.Layout forMob = com.strata.ui.screen.ListScreenLayouts.goalSetsList(f, m, "t", false,
                    true, false, "");
            boolean button = forMob.get("editGoals") != null && forMob.get("use") != null
                    && forMob.get("editGoals").text().equals("Edit this mob's goals")
                    && !forMob.get("use").active() && client.get("editGoals") == null
                    && client.get("add") != null && client.get("add").box().y() < client.get("edit").box().y()
                    && client.get("use") == null
                    && forMob.get("add") != null && forMob.get("edit") != null && forMob.get("delete") != null
                    && forMob.get("search") != null
                    && com.strata.ui.screen.ListScreenLayouts.picker(f, m, "t", groups).get("action") == null;
            var recent = com.strata.client.screen.RegistryPickerScreen.withRecent(
                    com.strata.client.screen.RegistryPickerScreen.plainOptions("goal_set", List.of("zombie", "tank")),
                    List.of("tank"));
            boolean first = recent.getFirst().id().equals("tank")
                    && recent.getFirst().namespace().equals(com.strata.client.screen.RegistryPickerScreen.RECENT_GROUP);
            return button && first;
        });
        SelfTestHarness.check(lines, tally,
                "PS1 the switches that change the game with no rows ship Off; mob scaling ships On", () -> {
            // Default's settings are the shipped defaults, so these must be the game's own: player scaling
            // (its curves ship as rows), Strata's spawners, trade lists and extra attributes.
            return Boolean.FALSE.equals(com.strata.data.StrataConfig.ENABLE_PLAYER_SCALING.getDefault())
                    && Boolean.FALSE.equals(com.strata.data.StrataConfig.ENABLE_SPECIAL_SPAWNERS.getDefault())
                    && Boolean.FALSE.equals(com.strata.data.StrataConfig.ENABLE_TRADES.getDefault())
                    && Boolean.FALSE.equals(com.strata.data.StrataConfig.ENABLE_EXTRA_MOB_ATTRIBUTES.getDefault())
                    && Boolean.TRUE.equals(com.strata.data.StrataConfig.ENABLE_MOB_SCALING.getDefault())
                    && Boolean.FALSE.equals(com.strata.data.StrataConfig.ENABLE_DEBUG_HUD.getDefault());
        });
        SelfTestHarness.check(lines, tally,
                "OP2 each setting is edited on one screen: Options, Scaling, or Player spawn", () -> {
            List<String> all = new ArrayList<>();
            all.addAll(com.strata.domain.OptionsOps.OPTION_KEYS);
            all.addAll(com.strata.domain.OptionsOps.SCALING_KEYS);
            all.addAll(com.strata.domain.OptionsOps.SPAWN_KEYS);
            // The three switches that ship Off are plain settings on Options > Settings.
            boolean shippedOff = com.strata.domain.OptionsOps.OPTION_KEYS.containsAll(List.of(
                    com.strata.domain.OptionsOps.SPECIAL_SPAWNERS, com.strata.domain.OptionsOps.TRADES,
                    com.strata.domain.OptionsOps.EXTRA_MOB_ATTRIBUTES));
            boolean once = all.size() == new java.util.HashSet<>(all).size();
            boolean scaling = com.strata.domain.OptionsOps.SCALING_KEYS.contains("enablePlayerScaling")
                    && !com.strata.domain.OptionsOps.OPTION_KEYS.contains("enablePlayerScaling")
                    && com.strata.domain.OptionsOps.SCALING_KEYS.contains("mobXpPerLevel");
            return once && scaling && shippedOff
                    && !com.strata.domain.OptionsOps.OPTION_KEYS.contains("blockOtherSpawns")
                    && com.strata.domain.OptionsOps.SPAWN_KEYS.contains("debugPlayerSpawn");
        });
        SelfTestHarness.check(lines, tally,
                "MC1 every mob cap value and every mob category says what it is; a fixed value is not listed", () -> {
            boolean values = java.util.stream.Stream.of("max", "despawn_distance", "no_despawn_distance", "friendly",
                    "persistent").allMatch(k -> !com.strata.domain.MobCapOps.what(
                    new com.strata.domain.MobCapOps.Value("monster", k, "1", "")).isEmpty());
            boolean categories = java.util.Arrays.stream(net.minecraft.world.entity.MobCategory.values())
                    .allMatch(c -> com.strata.domain.MobCapOps.CATEGORIES.containsKey(c.getName()));
            var all = List.of(new com.strata.domain.MobCapOps.Value("monster", "max", "70", ""),
                    new com.strata.domain.MobCapOps.Value("monster", "no_despawn_distance", "32", ""));
            boolean listed = com.strata.domain.MobCapOps.editable(all).size() == 1
                    && com.strata.domain.MobCapOps.editable(all).getFirst().key().equals("max");
            return values && categories && listed;
        });
        SelfTestHarness.check(lines, tally,
                "LX1 a loot table row names its entry's item and says what the value does", () -> {
            var all = List.of(
                    new com.strata.domain.LootTableOps.Value("pools.0.rolls", "1.0", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.name", "\"minecraft:rotten_flesh\"", ""),
                    new com.strata.domain.LootTableOps.Value("pools.0.entries.0.functions.0.count.max", "2.0", ""));
            String label = com.strata.domain.LootTableOps.label("pools.0.entries.0.functions.0.count.max", all,
                    id -> id.equals("minecraft:rotten_flesh") ? "Rotten Flesh" : id);
            boolean named = label.startsWith("Pool 1, Rotten Flesh") && label.contains("count max");
            boolean words = com.strata.domain.LootTableOps.what("pools.0.rolls").contains("picks")
                    && com.strata.domain.LootTableOps.what("pools.0.entries.0.name").contains("item")
                    && com.strata.domain.LootTableOps.what("pools.0.entries.0.functions.0.count.max").contains("items")
                    && com.strata.domain.LootTableOps.what("pools.0.entries.0.weight").contains("share");
            return named && words;
        });
        SelfTestHarness.check(lines, tally,
                "CO1 each coefficient says how it combines: a biome's replaces its tier's, a mob's multiplies", () -> {
            String tier = String.join(" ", com.strata.domain.ScalingOps.COEFFICIENT_TIER);
            String biome = String.join(" ", com.strata.domain.ScalingOps.COEFFICIENT_BIOME);
            String mob = String.join(" ", com.strata.domain.ScalingOps.COEFFICIENT_MOB);
            return tier.contains("replaces") && tier.contains("multiplies on top")
                    && biome.contains("replaces the tier's") && mob.contains("multiplies on top");
        });
        SelfTestHarness.check(lines, tally,
                "FG1 Mob data: settings have words, running state is locked, type values have words", () -> {
            boolean state = com.strata.model.FieldGuide.runningState("LivingEntity.swingTime")
                    && com.strata.model.FieldGuide.runningState("Mob.lootTableSeed")
                    && com.strata.model.FieldGuide.runningState("Chicken.oFlap")
                    && com.strata.model.FieldGuide.runningState("Wolf.shakeAnimO")
                    && com.strata.model.FieldGuide.runningState("Allay.holdingItemAnimationTicks0")
                    && !com.strata.model.FieldGuide.runningState("TamableAnimal.orderedToSit")
                    && !com.strata.model.FieldGuide.runningState("Mob.xpReward")
                    && !com.strata.model.FieldGuide.runningState("Villager.foodLevel");
            boolean locked = com.strata.data.GameLayer.locked(GameLayer.FIELD, "LivingEntity.hurtTime")
                    && !com.strata.data.GameLayer.locked(GameLayer.FIELD, "Zombie.canBreakDoors");
            boolean words = !com.strata.model.FieldGuide.what(GameLayer.FIELD, "Mob.xpReward").isEmpty()
                    && !com.strata.model.FieldGuide.what(GameLayer.TAG, "minecraft:undead").isEmpty()
                    && java.util.stream.Stream.of("category", "width", "height", "eye_height", "fixed_size",
                            "fire_immune", "summonable", "spawn_far_from_player", "tracking_range", "update_interval",
                            "spawn_placement", "spawn_heightmap")
                    .allMatch(k -> !com.strata.model.FieldGuide.what(GameLayer.TYPE, k).isEmpty());
            return state && locked && words;
        });
        SelfTestHarness.check(lines, tally,
                "GR1 every piece has its own numbers: Hard, Drop, Enchant, local difficulty, else the game's", () -> {
            var roll = new com.strata.model.DefaultEquipment(15, 25, 10, com.strata.model.EquipmentFamilies.vanillaMaterials(),
                    List.of(), 25, 50, 8.5);
            boolean fallback = roll.enchantFor("chest") == 50 && roll.enchantFor("mainhand") == 25
                    && roll.enchantFor("offhand") == 0 && roll.dropChance() == 8.5;
            var cells = com.strata.domain.EquipmentOps.equipmentValues(2, "", "8.5", "90", true, "");
            boolean table = "yes".equals(cells.get(Schema.Equipment.LOCAL_DIFFICULTY))
                    && com.strata.runtime.MobEquipment.scaled("yes")
                    && !com.strata.runtime.MobEquipment.scaled("")
                    && com.strata.domain.EquipmentOps.equipmentValues(2, "", "", "", false, "")
                    .get(Schema.Equipment.LOCAL_DIFFICULTY).isEmpty()
                    && com.strata.domain.EquipmentOps.equipmentValues(2, "", "", "", false, "5")
                            .get(Schema.Equipment.PLAYER_LEVEL).equals("5");
            boolean checks = com.strata.domain.EquipmentOps.pieceProblem("", "", "") == null
                    && com.strata.domain.EquipmentOps.pieceProblem("0", "100", "0") == null
                    && com.strata.domain.EquipmentOps.pieceProblem("-1", "", "") != null
                    && com.strata.domain.EquipmentOps.pieceProblem("", "101", "") != null
                    && com.strata.domain.EquipmentOps.pieceProblem("", "", "x") != null
                    && com.strata.runtime.MobEquipment.optional("") == null
                    && com.strata.runtime.MobEquipment.optional("150") == 100.0;
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            var list = com.strata.ui.screen.EquipmentLayout.build(f, m, "t",
                    com.strata.ui.screen.EquipmentLayout.REMOVE, "", "", null, "", false);
            var row = com.strata.ui.screen.EquipmentItemLayout.build(f, m, "t", List.of("head"),
                    "Diamond Helmet", "", null, com.strata.domain.EquipmentOps.SCALED_CHOICES);
            boolean screens = list.get("list") != null && list.get("value") == null && list.get("set") == null
                    && row.get("chance") != null && row.get("chanceHard") != null && row.get("drop") != null
                    && row.get("enchant") != null && row.get("scaled") != null;
            return fallback && table && checks && screens;
        });
    }

    private static void runTestsPart07(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "SB1 the player spawn is a biome or a tier, picked, and a stored value reads back as it was", () -> {
            var spawn = new com.strata.domain.OptionsOps.Setting("debugPlayerSpawn", "Player spawn", "", "",
                    com.strata.domain.OptionsOps.Kind.SPAWN, "", "", 0, 0, List.of());
            boolean checks = com.strata.domain.OptionsOps.problem(spawn, "biome:minecraft:plains") == null
                    && com.strata.domain.OptionsOps.problem(spawn, "tier:forest_tier") == null
                    && com.strata.domain.OptionsOps.problem(spawn, "") == null
                    && com.strata.domain.OptionsOps.problem(spawn, "Plains") != null;
            var biome = com.strata.domain.PlayerSpawnOps.parse("biome:minecraft:plains");
            var tier = com.strata.domain.PlayerSpawnOps.parse("tier:forest_tier");
            boolean read = biome.kind() == com.strata.domain.PlayerSpawnOps.Kind.BIOME
                    && biome.id().equals("minecraft:plains")
                    && tier.kind() == com.strata.domain.PlayerSpawnOps.Kind.TIER && tier.id().equals("forest_tier")
                    && com.strata.domain.PlayerSpawnOps.parse("").kind() == com.strata.domain.PlayerSpawnOps.Kind.OFF
                    && com.strata.domain.PlayerSpawnOps.stored(tier).equals("tier:forest_tier")
                    && "".equals(com.strata.data.StrataConfig.DEBUG_PLAYER_SPAWN.getDefault());
            return checks && read;
        });
        SelfTestHarness.check(lines, tally,
                "TG1 the Tiers view groups mobs by their tier, never into Recent", () -> {
            var zombieT1 = new com.strata.game.MobCatalog.MobEntry("minecraft:zombie", "", 10, true,
                    com.strata.model.Source.tier("t1"), com.strata.model.SpawnCategory.MONSTER, true);
            var zombieT2 = new com.strata.game.MobCatalog.MobEntry("minecraft:zombie", "", 10, true,
                    com.strata.model.Source.tier("forest_tier"), com.strata.model.SpawnCategory.MONSTER, true);
            boolean tiers = com.strata.client.screen.MobListScreen.groupOf(true, true, zombieT1).equals("Tier: t1")
                    && com.strata.client.screen.MobListScreen.groupOf(true, true, zombieT2).equals("Tier: forest_tier");
            boolean global = com.strata.client.screen.MobListScreen.groupOf(false, true, zombieT1).equals("Recent")
                    && com.strata.client.screen.MobListScreen.groupOf(false, false, true, zombieT1).equals("Special")
                    && com.strata.client.screen.MobListScreen.groupOf(false, true, true, zombieT1).equals("Recent");
            return tiers && global;
        });
        SelfTestHarness.check(lines, tally,
                "IN1 the mob editor shows what a mob inherits, and an own-entity variant takes its base mob's stats", () -> {
            var equipment = List.of(
                    new com.strata.domain.EquipmentOps.EquipmentRow("mainhand", "minecraft:iron_sword", 100,
                            com.strata.domain.ScopeLayers.FROM_GLOBAL),
                    new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 50, ""));
            String g = com.strata.domain.EquipmentOps.equipmentSummary(equipment, s -> s.equals("mainhand") ? "Main hand" : s,
                    id -> id.substring(id.indexOf(':') + 1));
            boolean equipmentLine = g.equals("Main hand iron_sword (from global), head iron_helmet")
                    && com.strata.domain.EquipmentOps.equipmentSummary(List.of(), s -> s, s -> s).equals("none");
            var own = new com.strata.domain.LootOps.LootRow("tier", "t1", "minecraft:zombie", "",
                    com.strata.model.OverrideMode.ADD, 0, 0, "minecraft:carrot", 1, 1, 50, "", "", false, "");
            var inherited = new com.strata.domain.LootOps.LootRow("global", "", "minecraft:zombie", "",
                    com.strata.model.OverrideMode.ADD, 0, 0, "minecraft:rotten_flesh", 0, 2, 100, "", "", true, "global");
            var denied = new com.strata.domain.LootOps.LootRow("tier", "t1", "minecraft:zombie", "",
                    com.strata.model.OverrideMode.DENY, 0, 0, "minecraft:iron_ingot", 1, 1, 0, "", "", false, "");
            String d = com.strata.domain.LootOps.lootSummary(List.of(own, inherited, denied),
                    id -> id.substring(id.indexOf(':') + 1));
            boolean lootLine = d.equals("carrot x1, rotten_flesh x0-2 (global)");
            var zombie = net.minecraft.resources.ResourceLocation.parse("minecraft:zombie");
            var variantType = net.minecraft.resources.ResourceLocation.parse("strata:big_zombie");
            boolean stats = com.strata.runtime.EntityBaseAttributes.statOwner(variantType,
                    java.util.Optional.of("minecraft:zombie")).equals(zombie)
                    && com.strata.runtime.EntityBaseAttributes.statOwner(zombie, java.util.Optional.empty()).equals(zombie);
            return equipmentLine && lootLine && stats;
        });
        SelfTestHarness.check(lines, tally,
                "GM1 a mob added to a tier starts as the global mob: its spawn values and its conditions", () -> {
            var rows = List.<Map<String, String>>of(Map.of("scope", "global", "scope_id", "", "entity", "minecraft:zombie",
                    "variant_name", "", "weight", "40", "min_count", "2", "max_count", "6", "enabled", "true",
                    "coefficient", "1.5"));
            var game = List.of(new com.strata.data.GameSnapshot.SpawnData("minecraft:husk", "monster", 80, 4, 4),
                    new com.strata.data.GameSnapshot.SpawnData("minecraft:husk", "monster", 20, 1, 1));
            var zombie = com.strata.domain.VariantOps.startFor(rows, game, "minecraft:zombie");
            var husk = com.strata.domain.VariantOps.startFor(rows, game, "minecraft:husk");
            var pig = com.strata.domain.VariantOps.startFor(rows, game, "minecraft:pig");
            boolean start = zombie.weight().equals("40") && zombie.maxCount().equals("6")
                    && zombie.coefficient().equals("1.5")
                    && husk.weight().equals("80") && husk.minCount().equals("4") && husk.coefficient().equals("1.0")
                    && pig.weight().equals("10") && pig.maxCount().equals("4");
            var night = List.of(new com.strata.model.SpawnRule(com.strata.model.SpawnRule.Type.parse("time"), "night"));
            Map<String, List<com.strata.model.SpawnRule>> byRow = Map.of("global||minecraft:zombie", night);
            boolean conditions = com.strata.data.Tables.spawnRulesFor(byRow, "tier", "t1", "minecraft:zombie", "",
                    false).equals(night)
                    && com.strata.data.Tables.spawnRulesFor(Map.of(), "tier", "t1", "minecraft:zombie", "", false)
                    .isEmpty();
            return start && conditions;
        });
        SelfTestHarness.check(lines, tally,
                "GB1 the nearest scope decides a mob's AI, and a picked behavior replaces the own list there", () -> {
            var lib = sampleGoalSets();
            var list = List.of(builtRow(false, 0, "FloatGoal"));
            String globalKey = com.strata.runtime.ScopeIndex.key("", "", "sk");
            String tierKey = com.strata.runtime.ScopeIndex.key("tier", "t1", "sk");
            var tierLayers = com.strata.runtime.GoalsRuntime.layers("t1", "");
            boolean inherits = lib.get("predator").equals(com.strata.runtime.GoalsRuntime.pick(Map.of(),
                    Map.of(globalKey, "predator"), lib, "sk", "", tierLayers).rows());
            var tierWins = com.strata.runtime.GoalsRuntime.pick(Map.of(globalKey, list),
                    Map.of(tierKey, "herbivore"), lib, "sk", "", tierLayers);
            boolean nearest = tierWins.rows().equals(lib.get("herbivore")) && tierWins.layer() == 0
                    && !tierWins.ownList();
            boolean sameScope = com.strata.runtime.GoalsRuntime.pick(Map.of(globalKey, list),
                    Map.of(globalKey, "herbivore"), lib, "sk", "", com.strata.runtime.GoalsRuntime.layers("", ""))
                    .ownList();
            boolean replaces = com.strata.domain.VariantOps.replacesOwnList("", "", true)
                    && com.strata.domain.VariantOps.replacesOwnList("herbivore", "", false)
                    && !com.strata.domain.VariantOps.replacesOwnList("herbivore", "herbivore", false)
                    && !com.strata.domain.VariantOps.replacesOwnList("", "herbivore", false);
            var tierScope = com.strata.domain.ScopeLayers.scopeLayers("tier", "t1", "");
            var fromGlobal = com.strata.domain.GoalsOps.effective(Map.of(globalKey, list), Map.of(), lib, "sk",
                    "minecraft:skeleton", tierScope, "", false, null);
            var picked = com.strata.domain.GoalsOps.effective(Map.of(tierKey, list), Map.of(), lib, "sk",
                    "minecraft:skeleton", tierScope, "herbivore", true, null);
            boolean editor = !fromGlobal.own() && fromGlobal.source().equals("the global mob's list")
                    && !picked.own() && picked.name().equals("herbivore");
            return inherits && nearest && sameScope && replaces && editor;
        });
        SelfTestHarness.check(lines, tally,
                "EQ5 an add over a denied piece clears the deny, and game chances are the game's", () -> {
            Path denyDir = Files.createTempDirectory("strata-deny-add");
            try {
                Path file = denyDir.resolve("equipment.csv");
                Files.writeString(file, "variant_name,scope,scope_id,slot,item,chance_pct,mode\n"
                        + "minecraft_zombie_default,,,chest,minecraft:iron_chestplate,0,deny\n");
                Map<String, String> keys = new java.util.LinkedHashMap<>();
                keys.put("variant_name", "minecraft_zombie_default");
                keys.put("scope", "");
                keys.put("scope_id", "");
                keys.put("slot", "chest");
                keys.put("item", "minecraft:iron_chestplate");
                TableWriter.upsert(file, keys, com.strata.domain.EquipmentOps.equipmentValues(50, "", "", "", false, ""));
                var rows = CsvTable.read(file);
                boolean denyCleared = rows.size() == 1 && rows.getFirst().get("mode").isEmpty()
                        && "50".equals(rows.getFirst().get("chance_pct"));
                var listed = com.strata.domain.EquipmentOps.equipmentRows(rows, List.of(), List.of(), List.of());
                boolean shows = listed.size() == 1 && listed.getFirst().chancePct() == 50;
                var zombie = List.of(
                        new com.strata.domain.EquipmentOps.GameEquipment("mainhand", "minecraft:iron_sword",
                                0.3333, 1.6667, "", "8.5", "25", false),
                        new com.strata.domain.EquipmentOps.GameEquipment("chest", "minecraft:iron_chestplate",
                                0.36, 0.52, "", "8.5", "50", true),
                        new com.strata.domain.EquipmentOps.GameEquipment("offhand", "minecraft:nautilus_shell",
                                3, "3%"));
                var game = com.strata.domain.EquipmentOps.equipmentRows(List.of(), List.of(), List.of(), zombie);
                boolean honest = !game.get(0).scaled() && game.get(1).scaled() && game.get(1).chancePct() == 0.36
                        && game.get(1).chanceHard().equals("0.52") && game.get(1).enchant().equals("50")
                        && game.get(2).chancePct() == 3
                        && com.strata.domain.EquipmentOps.gameEquipmentToKeep(null, "mainhand",
                                "minecraft:diamond_sword", game).size() == 1
                        && com.strata.domain.EquipmentOps.gameEquipmentToKeep(null, "chest",
                                "minecraft:diamond_chestplate", game).getFirst().scaled()
                        && com.strata.domain.EquipmentOps.gameEquipmentToKeep(null, "offhand",
                                "minecraft:shield", game).size() == 1;
                var denyArmor = List.<Map<String, String>>of(Map.of("slot", "chest", "item", "minecraft:iron_chestplate",
                        "mode", "deny"));
                boolean armorDenied = com.strata.domain.EquipmentOps.equipmentRows(denyArmor, List.of(),
                        List.of(), zombie).stream().noneMatch(r -> r.slot().equals("chest"));
                boolean vanillaTable = com.strata.model.EquipmentFamilies.of("zombie").items().stream()
                        .noneMatch(g -> g.pct() == 100)
                        && com.strata.model.EquipmentFamilies.of("skeleton").items().stream()
                        .anyMatch(g -> g.item().equals("minecraft:bow") && g.pct() == 100)
                        && "6.25".equals(com.strata.domain.EquipmentOps.chanceText(6.25));
                return denyCleared && shows && honest && armorDenied && vanillaTable;
            } finally {
                deleteRec(denyDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "TT1 abilities, classes and stats are explained, ticks in seconds", () -> {
            boolean abilities = com.strata.runtime.MobAbilities.abilityNames().stream().allMatch(a ->
                    !com.strata.model.AbilityGuide.of(a).what().startsWith("An ability Strata does not know"));
            boolean classes = java.util.Arrays.stream(com.strata.model.EffectClass.values()).allMatch(c ->
                    !com.strata.model.AbilityGuide.ofClass(c.id()).what()
                            .startsWith(com.strata.model.AbilityGuide.UNKNOWN_CLASS)
                    && com.strata.model.AbilityGuide.ofClass(c.id()).reads(com.strata.model.AbilityField.EFFECT_CLASS)
                    && !com.strata.model.AbilityGuide.ofClass(c.id()).reads(com.strata.model.AbilityField.ABILITY));
            boolean stats = com.strata.domain.StatColumns.DEFAULT.stream()
                    .allMatch(com.strata.model.AttributeGuide::knows)
                    && com.strata.model.AttributeGuide.of("minecraft:generic.armor_toughness").what().contains("big hits");
            boolean ticks = com.strata.model.AbilityGuide.TICK.contains("20 ticks are 1 second")
                    && "40 ticks (2 seconds)".equals(com.strata.model.AbilityGuide.ticksWords("40"))
                    && "Heals 2 health every 20 ticks (1 second).".equals(
                            com.strata.model.AbilityGuide.sentence("heal", "", "2", "20", ""))
                    && "Has minecraft:speed level II for good.".equals(
                            com.strata.model.AbilityGuide.sentence("effect", "minecraft:speed", "1", "", ""))
                    && com.strata.model.AbilityGuide.sentence("on_hit_effect", "minecraft:poison", "0", "", "")
                            .endsWith("for 100 ticks (5 seconds).");
            if (!(classes && abilities && stats && ticks)) {
                lines.add("    TT1 classes=" + classes + " abilities=" + abilities
                        + " stats=" + stats + " ticks=" + ticks);
            }
            return abilities && classes && stats && ticks;
        });

        SelfTestHarness.check(lines, tally,
                "ST2 the mob lists show the stats the user chose, a variant's as the game applies them", () -> {
            Path statRoot = Files.createTempDirectory("strata-stat-columns");
            try {
                boolean defaults = com.strata.domain.GlanceColumns.chosen(statRoot, com.strata.domain.GlanceColumns.MOBS)
                        .equals(com.strata.domain.StatColumns.DEFAULT);
                boolean saved = com.strata.domain.GlanceColumns.replace(statRoot, com.strata.domain.GlanceColumns.MOBS,
                                2, "minecraft:generic.armor").ok()
                        && com.strata.domain.GlanceColumns.chosen(statRoot, com.strata.domain.GlanceColumns.MOBS).get(2)
                                .equals("minecraft:generic.armor")
                        && com.strata.domain.GlanceColumns.chosen(statRoot, com.strata.domain.GlanceColumns.MOBS).get(0)
                                .equals("minecraft:generic.max_health")
                        && !com.strata.domain.GlanceColumns.replace(statRoot, com.strata.domain.GlanceColumns.MOBS,
                                5, "x").ok();
                var base = Map.of("minecraft:generic.max_health", 20.0, "minecraft:generic.movement_speed", 0.23);
                var columns = List.of("minecraft:generic.max_health", "minecraft:generic.movement_speed",
                        "minecraft:generic.armor", "");
                boolean plain = com.strata.domain.StatColumns.values(columns, base, Map.of(), Map.of())
                        .equals(List.of("20", "0.23", "-", "-"));
                boolean variant = com.strata.domain.StatColumns.values(columns, base,
                                Map.of("generic.max_health", 2.0), Map.of("minecraft:generic.movement_speed", 0.3))
                        .equals(List.of("40", "0.3", "-", "-"));
                boolean notTables = !com.strata.domain.StatColumns.class.getSimpleName().isEmpty()
                        && !Files.exists(statRoot.resolve("ui-stat-columns.csv"));
                return defaults && saved && plain && variant && notTables;
            } finally {
                deleteRec(statRoot);
            }
        });

        SelfTestHarness.check(lines, tally,
                "GC1 each list keeps its own at-a-glance columns: defaults, add, remove, persist, limits", () -> {
            Path root = Files.createTempDirectory("strata-glance");
            try {
                var gc = com.strata.domain.GlanceColumns.class;
                String mobs = com.strata.domain.GlanceColumns.MOBS;
                String items = com.strata.domain.GlanceColumns.ITEMS;
                String blocks = com.strata.domain.GlanceColumns.BLOCKS;
                boolean defaults = com.strata.domain.GlanceColumns.chosen(root, mobs)
                                .equals(com.strata.domain.StatColumns.DEFAULT)
                        && com.strata.domain.GlanceColumns.chosen(root, items)
                                .equals(List.of("max_stack_size", "max_damage", "attribute|minecraft:generic.attack_damage|mainhand"))
                        && com.strata.domain.GlanceColumns.chosen(root, blocks)
                                .equals(List.of("hardness", "blast_resistance", "tool"));
                boolean added = com.strata.domain.GlanceColumns.add(root, items, "food.nutrition").ok()
                        && com.strata.domain.GlanceColumns.chosen(root, items).size() == 4
                        && com.strata.domain.GlanceColumns.chosen(root, items).get(3).equals("food.nutrition")
                        && com.strata.domain.GlanceColumns.chosen(root, blocks).size() == 3
                        && com.strata.domain.GlanceColumns.chosen(root, mobs).equals(com.strata.domain.StatColumns.DEFAULT);
                boolean removed = com.strata.domain.GlanceColumns.remove(root, items, 0).ok()
                        && com.strata.domain.GlanceColumns.chosen(root, items)
                                .equals(List.of("max_damage", "attribute|minecraft:generic.attack_damage|mainhand", "food.nutrition"));
                boolean refused = com.strata.domain.GlanceColumns.add(root, items, "food.nutrition").refusal() != null
                        && com.strata.domain.GlanceColumns.add(root, items, " ").refusal() != null
                        && com.strata.domain.GlanceColumns.remove(root, items, 9).refusal() != null
                        && com.strata.domain.GlanceColumns.replace(root, items, 0, "food.nutrition").refusal() != null;
                boolean none = true;
                for (int i = 0; i < 3; i++) {
                    none &= com.strata.domain.GlanceColumns.remove(root, blocks, 0).ok();
                }
                none &= com.strata.domain.GlanceColumns.chosen(root, blocks).isEmpty();
                boolean full = true;
                for (int i = 0; i < com.strata.domain.GlanceColumns.MAX; i++) {
                    full &= com.strata.domain.GlanceColumns.add(root, blocks, "k" + i).ok();
                }
                full &= com.strata.domain.GlanceColumns.add(root, blocks, "one_more").refusal() != null
                        && com.strata.domain.GlanceColumns.chosen(root, blocks).size() == com.strata.domain.GlanceColumns.MAX;
                boolean changed = com.strata.domain.GlanceColumns.replace(root, mobs, 1, "minecraft:generic.armor").ok()
                        && com.strata.domain.GlanceColumns.chosen(root, mobs)
                                .equals(List.of("minecraft:generic.max_health", "minecraft:generic.armor",
                                        "minecraft:generic.movement_speed"));
                boolean notTable = Files.isRegularFile(root.resolve("ui-glance-columns.txt")) && gc != null;
                if (!(defaults && added && removed && refused && none && full && changed && notTable)) {
                    lines.add("    GC1 defaults=" + defaults + " added=" + added + " removed=" + removed
                            + " refused=" + refused + " none=" + none + " full=" + full + " changed=" + changed);
                }
                return defaults && added && removed && refused && none && full && changed && notTable;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "GC2 the old mob stat file becomes the mob columns once, and the new file wins after", () -> {
            Path root = Files.createTempDirectory("strata-glance-old");
            try {
                String mobs = com.strata.domain.GlanceColumns.MOBS;
                Files.writeString(root.resolve("ui-stat-columns.txt"),
                        "minecraft:generic.armor\n\nminecraft:generic.max_health\n");
                boolean migrated = com.strata.domain.GlanceColumns.chosen(root, mobs)
                        .equals(List.of("minecraft:generic.armor", "minecraft:generic.max_health"));
                boolean otherLists = com.strata.domain.GlanceColumns.chosen(root, com.strata.domain.GlanceColumns.ITEMS)
                        .equals(com.strata.domain.GlanceColumns.defaults(com.strata.domain.GlanceColumns.ITEMS));
                boolean kept = com.strata.domain.GlanceColumns.add(root, mobs, "minecraft:generic.luck").ok()
                        && com.strata.domain.GlanceColumns.chosen(root, mobs)
                                .equals(List.of("minecraft:generic.armor", "minecraft:generic.max_health", "minecraft:generic.luck"));
                boolean removedAll = true;
                for (int i = 0; i < 3; i++) {
                    removedAll &= com.strata.domain.GlanceColumns.remove(root, mobs, 0).ok();
                }
                removedAll &= com.strata.domain.GlanceColumns.chosen(root, mobs).isEmpty();
                return migrated && otherLists && kept && removedAll;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "GC3 item and block columns show the effective value: the user's over the game's, '-' for none", () -> {
            var sheet = List.of(
                    new com.strata.data.GameLayer.Row("minecraft:stick", "component", "max_stack_size", "64"),
                    new com.strata.data.GameLayer.Row("minecraft:diamond_sword", "component", "max_stack_size", "1"),
                    new com.strata.data.GameLayer.Row("minecraft:diamond_sword", "component", "max_damage", "1561"),
                    new com.strata.data.GameLayer.Row("minecraft:diamond_sword", "component",
                            "attribute|minecraft:generic.attack_damage|mainhand", "7.0"),
                    new com.strata.data.GameLayer.Row("minecraft:diamond_sword", "component", "tool.rule.0.speed", "8"),
                    new com.strata.data.GameLayer.Row("minecraft:diamond_sword", "component", "rarity", "minecraft:rare"));
            var own = Map.of("minecraft:diamond_sword|max_damage", "2000");
            var glance = com.strata.domain.ItemStatsOps.glance(sheet, own);
            var cols = com.strata.domain.GlanceColumns.defaults(com.strata.domain.GlanceColumns.ITEMS);
            boolean sword = com.strata.domain.GlanceColumns.valuesFor(cols, glance.get("minecraft:diamond_sword"))
                    .equals(List.of("1", "2000", "7"));
            boolean stick = com.strata.domain.GlanceColumns.valuesFor(cols, glance.get("minecraft:stick"))
                    .equals(List.of("64", "-", "-"));
            boolean unknown = com.strata.domain.GlanceColumns.valuesFor(cols, glance.get("minecraft:nothing"))
                    .equals(List.of("-", "-", "-"));
            boolean word = com.strata.domain.GlanceColumns.valuesFor(List.of("rarity"), glance.get("minecraft:diamond_sword"))
                    .equals(List.of("rare"));
            var keys = com.strata.domain.ItemStatsOps.glanceKeys(sheet);
            boolean keyList = keys.size() == 4 && keys.contains("rarity") && keys.stream().noneMatch(k -> k.startsWith("tool.rule."));
            var blockSheet = List.of(
                    new com.strata.data.GameLayer.Row("minecraft:stone", "component", "hardness", "1.5"),
                    new com.strata.data.GameLayer.Row("minecraft:stone", "component", "tool", "pickaxe"),
                    new com.strata.data.GameLayer.Row("minecraft:stone", "component", "tags", "c:stones"));
            var blockGlance = com.strata.domain.BlockStatsOps.glance(blockSheet, Map.of("minecraft:stone|hardness", "3"));
            boolean block = com.strata.domain.GlanceColumns.valuesFor(
                            com.strata.domain.GlanceColumns.defaults(com.strata.domain.GlanceColumns.BLOCKS),
                            blockGlance.get("minecraft:stone")).equals(List.of("3", "-", "pickaxe"))
                    && com.strata.domain.BlockStatsOps.glanceKeys(blockSheet).equals(List.of("hardness", "tool"));
            boolean headings = com.strata.domain.GlanceColumns.heading(com.strata.domain.GlanceColumns.ITEMS, "max_damage")
                            .equals("Durability")
                    && com.strata.domain.GlanceColumns.heading(com.strata.domain.GlanceColumns.BLOCKS, "hardness")
                            .equals("Hardness");
            boolean room = com.strata.ui.screen.GlanceLayout.visible(300, 6) == 6
                    && com.strata.ui.screen.GlanceLayout.visible(120, 6) == 3
                    && com.strata.ui.screen.GlanceLayout.visible(10, 3) == 0
                    && com.strata.ui.screen.GlanceLayout.maxAdd(300) == 4
                    && com.strata.ui.screen.GlanceLayout.maxAdd(1000) == com.strata.ui.screen.GlanceLayout.MAX
                    && com.strata.ui.screen.GlanceLayout.maxAdd(50) == 0;
            if (!(sword && stick && unknown && word && keyList && block && headings && room)) {
                lines.add("    GC3 sword=" + sword + " stick=" + stick + " unknown=" + unknown + " word=" + word
                        + " keys=" + keyList + " block=" + block + " headings=" + headings + " room=" + room);
            }
            return sword && stick && unknown && word && keyList && block && headings && room;
        });

        SelfTestHarness.check(lines, tally,
                "VG1 the game's equipment odds are exact, and every vanilla goal is explained", () -> {
            var zombieRoll = com.strata.model.EquipmentFamilies.of("zombie");
            boolean armor = "15".equals(com.strata.model.VanillaEquipmentMath.pct(zombieRoll.armorSlotPct(0, false, 1)))
                    && "11.25".equals(com.strata.model.VanillaEquipmentMath.pct(zombieRoll.armorSlotPct(1, false, 1)))
                    && "8.44".equals(com.strata.model.VanillaEquipmentMath.pct(zombieRoll.armorSlotPct(2, false, 1)))
                    && "10.94".equals(com.strata.model.VanillaEquipmentMath.pct(zombieRoll.armorSlotPct(3, true, 1)))
                    && zombieRoll.armorSlotPct(0, false, 0) == 0;
            double total = 0;
            for (int t = 0; t <= 4; t++) {
                total += com.strata.model.VanillaEquipmentMath.materialPct(t);
            }
            boolean material = Math.abs(total - 100) < 1e-9
                    && "37.06".equals(com.strata.model.VanillaEquipmentMath.pct(
                            com.strata.model.VanillaEquipmentMath.materialPct(0)))
                    && "0.04".equals(com.strata.model.VanillaEquipmentMath.pct(
                            com.strata.model.VanillaEquipmentMath.materialPct(4)));
            boolean zombie = zombieRoll.items().stream().anyMatch(i -> i.item().equals("minecraft:iron_sword")
                    && "0.33".equals(com.strata.model.VanillaEquipmentMath.pct(i.pct()))
                    && "1.67".equals(com.strata.model.VanillaEquipmentMath.pct(i.pctHard())));
            List<String> vanillaGoals = List.of("AvoidEntityGoal", "BegGoal", "BreakDoorGoal", "BreathAirGoal",
                    "BreedGoal", "CatLieOnBedGoal", "CatSitOnBlockGoal", "ClimbOnTopOfPowderSnowGoal",
                    "DefendVillageTargetGoal", "DolphinJumpGoal", "EatBlockGoal", "FleeSunGoal", "FloatGoal",
                    "FollowBoatGoal", "FollowFlockLeaderGoal", "FollowMobGoal", "FollowOwnerGoal",
                    "FollowParentGoal", "GolemRandomStrollInVillageGoal", "HurtByTargetGoal", "InteractGoal",
                    "LandOnOwnersShoulderGoal", "LeapAtTargetGoal", "LlamaFollowCaravanGoal", "LookAtPlayerGoal",
                    "LookAtTradingPlayerGoal", "MeleeAttackGoal", "MoveBackToVillageGoal", "MoveThroughVillageGoal",
                    "MoveTowardsRestrictionGoal", "MoveTowardsTargetGoal", "NearestAttackableTargetGoal",
                    "NearestAttackableWitchTargetGoal", "NearestHealableRaiderTargetGoal",
                    "NonTameRandomTargetGoal", "OcelotAttackGoal", "OfferFlowerGoal", "OpenDoorGoal",
                    "OwnerHurtByTargetGoal", "OwnerHurtTargetGoal", "PanicGoal", "PathfindToRaidGoal",
                    "RandomLookAroundGoal", "RandomStandGoal", "RandomStrollGoal", "RandomSwimmingGoal",
                    "RangedAttackGoal", "RangedBowAttackGoal", "RangedCrossbowAttackGoal", "RemoveBlockGoal",
                    "ResetUniversalAngerTargetGoal", "RestrictSunGoal", "RunAroundLikeCrazyGoal",
                    "SitWhenOrderedToGoal", "StrollThroughVillageGoal", "SwellGoal", "TemptGoal",
                    "TradeWithPlayerGoal", "TryFindWaterGoal", "UseItemGoal", "WaterAvoidingRandomFlyingGoal",
                    "WaterAvoidingRandomStrollGoal", "ZombieAttackGoal");
            boolean goals = vanillaGoals.stream().allMatch(com.strata.model.GoalGuide::knows)
                    && com.strata.model.GoalGuide.of("OwnerHurtByTargetGoal").role()
                            == com.strata.model.GoalGuide.Role.TARGET
                    && com.strata.model.GoalGuide.of("SwellGoal").role() == com.strata.model.GoalGuide.Role.GOAL;
            return armor && material && zombie && goals;
        });

        SelfTestHarness.check(lines, tally,
                "AI1 a goal's settings: the game's defaults, the table round trip, and the checks", () -> {
            var melee = com.strata.model.GoalType.of("MeleeAttackGoal");
            boolean defaults = melee != null && "1.0".equals(melee.defaults().get("speed"))
                    && "false".equals(melee.defaults().get("keep_chasing"));
            var settings = Map.of("speed", "1.4", "keep_chasing", "true");
            boolean codec = com.strata.model.GoalType.decode(com.strata.model.GoalType.encode(settings)).equals(settings);
            var hunt = com.strata.model.GoalType.of("NearestAttackableTargetGoal");
            boolean checks = com.strata.model.GoalType.problem(melee, Map.of("speed", "x")) != null
                    && com.strata.model.GoalType.problem(hunt, Map.of("target", "minecraft:villager")) == null
                    && com.strata.model.GoalType.problem(hunt,
                            Map.of("target", "class:net.minecraft.world.entity.animal.Turtle")) == null
                    && com.strata.model.GoalType.problem(hunt, Map.of("target", "dragon")) != null
                    && com.strata.model.GoalType.problem(com.strata.model.GoalType.of("BreakDoorGoal"),
                            Map.of("difficulty", "sometimes")) != null
                    && com.strata.model.GoalType.problem(com.strata.model.GoalType.of("TemptGoal"),
                            Map.of("items", "")) != null;
            var row = new com.strata.model.GoalRow(false, 3, "MeleeAttackGoal", false, settings,
                    com.strata.model.GoalCondition.of("night", ""), true);
            boolean table = GoalTable.row(GoalTable.cells(row)).equals(row);
            boolean rows = row.problem() == null
                    && row.withPriority(150).problem() != null
                    && new com.strata.model.GoalRow(false, 1, "TeleportGoal", false, Map.of(),
                            com.strata.model.GoalCondition.ALWAYS, true).problem() != null
                    && new com.strata.model.GoalRow(false, 1, "NearestAttackableTargetGoal", false, Map.of(),
                            com.strata.model.GoalCondition.ALWAYS, true).problem() != null;
            var game = new com.strata.model.GoalRow(false, 4, "net.minecraft.world.entity.monster.Spider$SpiderAttackGoal",
                    true, Map.of(), com.strata.model.GoalCondition.ALWAYS, true);
            boolean lists = com.strata.domain.GoalsOps.listProblem(List.of(row, game)) == null;
            boolean names = com.strata.domain.GoalsOps.nameProblem("none", "", List.of()) != null
                    && com.strata.domain.GoalsOps.nameProblem("Bad Name", "", List.of()) != null
                    && com.strata.domain.GoalsOps.nameProblem("hunter", "", List.of("hunter")) != null
                    && com.strata.domain.GoalsOps.nameProblem("hunter", "hunter", List.of("hunter")) == null;
            return defaults && codec && checks && table && rows && lists && names;
        });

        SelfTestHarness.check(lines, tally,
                "AI2 an archetype of built goals is buildable, survives the table, and fights or not", () -> {
            var builtins = sampleGoalSets();
            boolean buildable = builtins.values().stream().flatMap(List::stream)
                    .allMatch(r -> !r.game() && r.problem() == null);
            List<Map<String, String>> cells = new ArrayList<>();
            builtins.forEach((name, rows) -> cells.addAll(GoalSetTable.toRows(name, rows)));
            boolean table = GoalSetTable.fromRows(cells).equals(builtins)
                    && GoalSetTable.load(Path.of("no-such-dir", "goal_sets.csv")).isEmpty();
            var herbivore = builtins.get("herbivore");
            boolean values = herbivore.stream().anyMatch(r -> r.goal().equals("PanicGoal")
                    && "2.0".equals(r.settings().get("speed")))
                    && herbivore.stream().anyMatch(r -> r.goal().equals("TemptGoal")
                    && "minecraft:wheat".equals(r.settings().get("items")));
            boolean fights = com.strata.model.Attacks.NONE.fights(builtins.get("predator"))
                    && !com.strata.model.Attacks.NONE.fights(herbivore);
            var choices = com.strata.domain.VariantOps.goalSetChoices(builtins, false, com.strata.model.Attacks.NONE);
            boolean filter = choices.contains("herbivore") && !choices.contains("predator");
            return buildable && table && values && fights && filter;
        });

        SelfTestHarness.check(lines, tally,
                "AI3 a mob runs its own list, else its archetype, else its base mob's, else the game's", () -> {
            Path goalsDir = Files.createTempDirectory("strata-mob-ai");
            try {
                Files.writeString(goalsDir.resolve("mob_goals.csv"), GoalTable.HEADER + "\n"
                        + "frosty,,,0,goal,1,PanicGoal,built,speed=3.0,,,true\n"
                        + "frosty,tier,T1,0,goal,2,FloatGoal,built,,,,true\n"
                        + "minecraft_skeleton_default,,,0,goal,5,RandomLookAroundGoal,built,,,,true\n");
                com.strata.runtime.GoalsRuntime.load(goalsDir.resolve("mob_goals.csv"));
                com.strata.runtime.GoalsRuntime.useGoalSets(sampleGoalSets());
                String base = "minecraft_skeleton_default";
                var own = com.strata.runtime.GoalsRuntime.listFor("frosty", base, "", "", "");
                var tier = com.strata.runtime.GoalsRuntime.listFor("frosty", base, "T1", "", "");
                java.util.function.Function<String, List<com.strata.model.GoalRow>> choosing = p ->
                        com.strata.runtime.GoalsRuntime.pick(Map.of(),
                                Map.of(com.strata.runtime.ScopeIndex.key("", "", "brute"), p),
                                sampleGoalSets(), "brute", "", com.strata.runtime.GoalsRuntime.layers("", "")).rows();
                var goalSet = choosing.apply("herbivore");
                var none = choosing.apply("none");
                var inherited = com.strata.runtime.GoalsRuntime.listFor("brute", base, "", "", "");
                var game = com.strata.runtime.GoalsRuntime.listFor("brute", "", "", "", "");
                return own != null && own.size() == 1 && "3.0".equals(own.getFirst().settings().get("speed"))
                        && tier != null && tier.getFirst().goal().equals("FloatGoal")
                        && goalSet != null && goalSet.equals(sampleGoalSets().get("herbivore"))
                        && none == null
                        && inherited != null && inherited.getFirst().goal().equals("RandomLookAroundGoal")
                        && game == null;
            } finally {
                com.strata.runtime.GoalsRuntime.load(goalsDir.resolve("missing.csv"));
                deleteRec(goalsDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "AI4 seeded from the game: real values kept, a mob's own goal kept whole", () -> {
            var panic = new com.strata.model.GameGoal(false, 1, List.of("PanicGoal"),
                    "net.minecraft.world.entity.ai.goal.PanicGoal", Map.of("speed", "2.0"));
            var skeletonMelee = new com.strata.model.GameGoal(false, 4, List.of("MeleeAttackGoal"),
                    "net.minecraft.world.entity.monster.AbstractSkeleton$1", Map.of());
            var spiderHunt = new com.strata.model.GameGoal(true, 2,
                    List.of("SpiderTargetGoal", "NearestAttackableTargetGoal", "TargetGoal"),
                    "net.minecraft.world.entity.monster.Spider$SpiderTargetGoal", Map.of());
            boolean kinds = panic.buildable() && !skeletonMelee.buildable() && !spiderHunt.buildable();
            var p = com.strata.model.GoalRow.fromGame(panic);
            var m = com.strata.model.GoalRow.fromGame(skeletonMelee);
            boolean rows = !p.game() && p.goal().equals("PanicGoal") && "2.0".equals(p.settings().get("speed"))
                    && m.game() && m.goal().endsWith("AbstractSkeleton$1") && m.priority() == 4;
            boolean codec = com.strata.model.GameGoal.decode(panic.encode()).equals(panic);
            var list = com.strata.domain.GoalsOps.fromGame(List.of(panic, skeletonMelee, spiderHunt));
            boolean order = list.getFirst().target() && list.get(1).goal().equals("PanicGoal");
            var added = com.strata.domain.GoalsOps.newRow("MeleeAttackGoal", list);
            boolean next = added.priority() == 5 && !added.target()
                    && com.strata.domain.GoalsOps.settingsLine(added).contains("speed");
            return kinds && rows && codec && order && next;
        });

        SelfTestHarness.check(lines, tally,
                "GR1 default equipment rolls: the game's families, the table round trip, and one row per piece", () -> {
            boolean families = com.strata.model.EquipmentFamilies.ALL.stream().map(com.strata.model.EquipmentFamilies::of)
                    .allMatch(r -> r.armorChance() <= 0
                            || r.materials().values().stream().mapToDouble(Double::doubleValue).sum() > 0)
                    && com.strata.model.EquipmentFamilies.of("cow") == null;
            var zombie = com.strata.model.EquipmentFamilies.of("zombie");
            var cells = DefaultEquipmentTable.toRows("minecraft:zombie", zombie);
            boolean table = DefaultEquipmentTable.fromRows(cells).get("minecraft:zombie").equals(zombie)
                    && zombie.armorChance() == 15 && zombie.armorStopHard() == 10;
            boolean names = "minecraft:golden_helmet".equals(com.strata.model.DefaultEquipment.armorItem("gold", "head"))
                    && "minecraft:chainmail_boots".equals(com.strata.model.DefaultEquipment.armorItem("chainmail", "feet"));
            var pieces = zombie.pieces();
            var helmet = pieces.stream().filter(p -> p.item().equals("minecraft:golden_helmet")).findFirst().orElseThrow();
            double feet = pieces.stream().filter(p -> p.slot().equals("feet")).mapToDouble(p -> p.pct()).sum();
            boolean perPiece = pieces.subList(0, zombie.items().size()).stream().noneMatch(p -> p.scaled())
                    && pieces.getFirst().enchant() == zombie.weaponEnchant()
                    && helmet.scaled() && helmet.enchant() == zombie.armorEnchant() && helmet.drop() == zombie.dropChance()
                    && Math.abs(helmet.pct() - zombie.armorPiecePct(3, "gold", false, 1)) < 1e-4
                    && Math.abs(helmet.pctHard() - zombie.armorPiecePct(3, "gold", true, 1)) < 1e-4
                    && Math.abs(feet - zombie.armorChance()) < 1e-3
                    && new com.strata.model.DefaultEquipment(0, 25, 10, com.strata.model.EquipmentFamilies.vanillaMaterials(),
                            List.of(), 25, 50, 8.5).pieces().isEmpty();
            var listed = com.strata.domain.DefaultEquipmentOps.gameEquipment(zombie);
            boolean rows = listed.size() == pieces.size()
                    && listed.stream().filter(g -> g.item().equals("minecraft:golden_helmet")).allMatch(g -> g.scaled()
                            && g.enchant().equals(DefaultEquipmentTable.number(zombie.armorEnchant()))
                            && g.drop().equals(DefaultEquipmentTable.number(zombie.dropChance())))
                    && com.strata.domain.DefaultEquipmentOps.gameEquipment((com.strata.model.DefaultEquipment) null).isEmpty();
            if (!(families && table && names && perPiece && rows)) {
                lines.add("    GR1 families=" + families + " table=" + table + " names=" + names + " perPiece=" + perPiece
                        + " rows=" + rows);
                return false;
            }
            return true;
        });
        SelfTestHarness.check(lines, tally,
                "LT2 loot tables: rows and back, the default weight, the edited table, and the checks", () -> {
            String json = "{\"type\":\"minecraft:entity\",\"pools\":[{\"rolls\":1.0,\"bonus_rolls\":0.0,"
                    + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:rotten_flesh\",\"functions\":["
                    + "{\"function\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":0.0,\"max\":2.0}}]}],"
                    + "\"conditions\":[]}],\"random_sequence\":\"minecraft:entities/zombie\",\"odd.key\":{}}";
            var parsed = com.google.gson.JsonParser.parseString(json);
            var rows = LootJson.flatten(parsed);
            boolean weight = "1".equals(rows.get("pools.0.entries.0.weight"));
            var withoutWeight = new java.util.LinkedHashMap<>(rows);
            withoutWeight.remove("pools.0.entries.0.weight");
            boolean round = LootJson.unflatten(withoutWeight).equals(parsed)
                    && "2.0".equals(rows.get("pools.0.entries.0.functions.0.count.max"))
                    && "[]".equals(rows.get("pools.0.conditions")) && "{}".equals(rows.get("odd%2Ekey"))
                    && "\"minecraft:rotten_flesh\"".equals(rows.get("pools.0.entries.0.name"));
            List<GameLayer.Row> sheet = new ArrayList<>();
            sheet.add(new GameLayer.Row("minecraft:zombie", GameLayer.LOOT_TABLE, GameLayer.LOOT_TABLE,
                    "minecraft:entities/zombie"));
            rows.forEach((p, v) -> sheet.add(new GameLayer.Row("minecraft:entities/zombie", GameLayer.LOOT_VALUE, p, v)));
            var edited = LootJson.editedTables(sheet, List.of(
                    Map.of("table", "minecraft:entities/zombie", "path", "pools.0.entries.0.functions.0.count.max",
                            "value", "5.0"),
                    Map.of("table", "minecraft:entities/zombie", "path", "pools.0.invented", "value", "1"),
                    Map.of("table", "minecraft:entities/cow", "path", "pools.0.rolls", "value", "3.0")));
            var zombie = edited.get("minecraft:entities/zombie").getAsJsonObject();
            var entry = zombie.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject();
            boolean pack = edited.size() == 1
                    && entry.getAsJsonArray("functions").get(0).getAsJsonObject().getAsJsonObject("count")
                            .get("max").getAsDouble() == 5.0
                    && entry.get("weight").getAsInt() == 1
                    && !zombie.getAsJsonArray("pools").get(0).getAsJsonObject().has("invented");
            String table = com.strata.domain.LootTableOps.tableOf(sheet, "minecraft:zombie");
            var values = com.strata.domain.LootTableOps.values(sheet, table,
                    Map.of("pools.0.rolls", "2.0"));
            var rolls = values.stream().filter(v -> v.path().equals("pools.0.rolls")).findFirst().orElseThrow();
            var name = values.stream().filter(v -> v.path().equals("pools.0.entries.0.name")).findFirst().orElseThrow();
            var type = values.stream().filter(v -> v.path().equals("type")).findFirst().orElseThrow();
            var w = values.stream().filter(v -> v.path().equals("pools.0.entries.0.weight")).findFirst().orElseThrow();
            boolean ops = table.equals("minecraft:entities/zombie") && rolls.effective().equals("2.0")
                    && com.strata.domain.LootTableOps.lockReason(type) != null
                    && com.strata.domain.LootTableOps.lockReason(rolls) == null
                    && com.strata.domain.LootTableOps.problem(rolls, "3.5") == null
                    && com.strata.domain.LootTableOps.problem(rolls, "lots") != null
                    && com.strata.domain.LootTableOps.problem(w, "4") == null
                    && com.strata.domain.LootTableOps.problem(w, "4.5") != null
                    && com.strata.domain.LootTableOps.problem(name, "minecraft:bone") == null
                    && com.strata.domain.LootTableOps.problem(name, "a bone") != null
                    && LootJson.literal(name.game(), "minecraft:bone").equals("\"minecraft:bone\"")
                    && LootJson.literal(rolls.game(), "3").equals("3")
                    && LootJson.shown(name.game()).equals("minecraft:rotten_flesh")
                    && com.strata.domain.LootTableOps.label("pools.0.entries.0.functions.0.count.max")
                            .equals("Pool 1, Entry 1, Function 1: count max")
                    && com.strata.domain.LootTableOps.label("pools.0.bonus_rolls").equals("Pool 1: bonus rolls");
            return weight && round && pack && ops;
        });

        SelfTestHarness.check(lines, tally,
                "SP1 spawning game layer: group sizes kept, ores, and the mob caps", () -> {
            var wolves = new GameSnapshot.SpawnData("minecraft:wolf", "creature", 8, 4, 4);
            var bats = new GameSnapshot.SpawnData("minecraft:bat", "ambient", 10, 8, 8);
            List<GameLayer.Row> sheet = new ArrayList<>(GameSnapshot.spawnRows("minecraft:taiga", List.of(wolves, bats)));
            sheet.add(new GameLayer.Row("minecraft:taiga", GameLayer.ORE, "coal", "true"));
            sheet.add(new GameLayer.Row("minecraft:deep_dark", GameLayer.ORE, "diamond", "true"));
            sheet.add(new GameLayer.Row("minecraft:taiga", GameLayer.SPAWN, "9", "creature|minecraft:cow|x"));
            sheet.add(new GameLayer.Row("monster", GameLayer.CAP, "max", "70"));
            sheet.add(new GameLayer.Row("monster", GameLayer.CAP, "no_despawn_distance", "32"));
            var read = GameSnapshot.fromRows(sheet);
            boolean spawns = read.get("minecraft:taiga").spawns().equals(List.of(wolves, bats))
                    && read.get("minecraft:taiga").ores().equals(List.of("coal"))
                    && read.get("minecraft:deep_dark").spawns().isEmpty()
                    && read.get("minecraft:deep_dark").ores().equals(List.of("diamond"));
            var caps = com.strata.domain.MobCapOps.merge(sheet, Map.of("monster|max", "100"));
            var max = caps.get(0);
            var fixed = caps.get(1);
            boolean capValues = caps.size() == 2 && max.effective().equals("100") && max.game().equals("70")
                    && fixed.effective().equals("32")
                    && com.strata.domain.MobCapOps.lockReason(max) == null
                    && com.strata.domain.MobCapOps.lockReason(fixed) != null
                    && com.strata.domain.MobCapOps.problem(max, "120") == null
                    && com.strata.domain.MobCapOps.problem(max, "-1") != null
                    && com.strata.domain.MobCapOps.problem(max, "1.5") != null
                    && com.strata.domain.MobCapOps.problem(
                            new com.strata.domain.MobCapOps.Value("creature", "persistent", "true", ""), "false") == null
                    && com.strata.domain.MobCapOps.problem(
                            new com.strata.domain.MobCapOps.Value("creature", "persistent", "true", ""), "7") != null;
            boolean runtime = com.strata.runtime.MobCapRuntime.category("monster")
                    == net.minecraft.world.entity.MobCategory.MONSTER
                    && com.strata.runtime.MobCapRuntime.category("boss") == null
                    && Integer.valueOf(5).equals(com.strata.runtime.MobCapRuntime.value("max", "5"))
                    && Boolean.TRUE.equals(com.strata.runtime.MobCapRuntime.value("isFriendly", "true"));
            return spawns && capValues && runtime;
        });

        SelfTestHarness.check(lines, tally,
                "OR1 ores from the game: the ore name of a block, and what it replaced", () -> {
            var json = new com.google.gson.JsonObject();
            json.addProperty("predicate_type", "minecraft:block_match");
            json.addProperty("block", "minecraft:netherrack");
            var tagRule = new com.google.gson.JsonObject();
            tagRule.addProperty("predicate_type", "minecraft:tag_match");
            tagRule.addProperty("tag", "minecraft:deepslate_ore_replaceables");
            var other = new com.google.gson.JsonObject();
            other.addProperty("predicate_type", "minecraft:always_true");
            return com.strata.game.OreDetector.oreName(net.minecraft.resources.ResourceLocation.parse("minecraft:iron_ore")).equals("iron")
                    && com.strata.game.OreDetector.oreName(net.minecraft.resources.ResourceLocation.parse("minecraft:deepslate_iron_ore")).equals("iron")
                    && com.strata.game.OreDetector.oreName(net.minecraft.resources.ResourceLocation.parse("minecraft:nether_quartz_ore")).equals("nether_quartz")
                    && com.strata.game.OreDetector.oreName(net.minecraft.resources.ResourceLocation.parse("minecraft:ancient_debris")).equals("ancient_debris")
                    && com.strata.game.OreDetector.oreName(net.minecraft.resources.ResourceLocation.parse("othermod:tin_ore")).equals("tin")
                    && com.strata.game.OreDetector.hostOf(json).equals("block:minecraft:netherrack")
                    && com.strata.game.OreDetector.hostOf(tagRule).equals("tag:minecraft:deepslate_ore_replaceables")
                    && com.strata.game.OreDetector.hostOf(other).isEmpty();
        });

    }

    private static void runTestsPart08(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "BD1 biome values: probability and spawn costs, the user's over the game's", () -> {
            List<GameLayer.Row> sheet = List.of(
                    new GameLayer.Row("minecraft:soul_sand_valley", GameLayer.BIOME, "creature_probability", "0.1"),
                    new GameLayer.Row("minecraft:soul_sand_valley", GameLayer.COST, "minecraft:ghast", "0.7|0.15"),
                    new GameLayer.Row("minecraft:plains", GameLayer.BIOME, "creature_probability", "0.1"));
            var values = com.strata.domain.BiomeDataOps.values(sheet, "minecraft:soul_sand_valley",
                    Map.of("cost|minecraft:ghast|energy_budget", "0.3"));
            boolean read = values.size() == 3
                    && values.get(0).key().equals("creature_probability") && values.get(0).effective().equals("0.1")
                    && values.get(1).key().equals("minecraft:ghast|charge") && values.get(1).effective().equals("0.7")
                    && values.get(2).effective().equals("0.3") && values.get(2).game().equals("0.15");
            double[] cost = com.strata.runtime.BiomeValues.cost(Map.of("cost|minecraft:ghast|charge", "1.2"),
                    "minecraft:ghast", 0.7, 0.15);
            boolean costs = cost != null && cost[0] == 1.2 && cost[1] == 0.15
                    && com.strata.runtime.BiomeValues.cost(Map.of("cost|minecraft:pig|charge", "1"), "minecraft:pig",
                            null, null) == null
                    && com.strata.runtime.BiomeValues.cost(Map.of("cost|minecraft:ghast|charge", "-1"),
                            "minecraft:ghast", 0.7, 0.15)[0] == 0.7;
            boolean checks = com.strata.domain.BiomeDataOps.problem(values.get(0), "0.5") == null
                    && com.strata.domain.BiomeDataOps.problem(values.get(0), "1") != null
                    && com.strata.domain.BiomeDataOps.problem(values.get(1), "3") == null
                    && com.strata.domain.BiomeDataOps.problem(values.get(1), "x") != null
                    && com.strata.domain.BiomeDataOps.name(values.get(2)).equals("Spawn cost: energy budget")
                    && com.strata.domain.BiomeDataOps.entityOf(values.get(2)).equals("minecraft:ghast")
                    && com.strata.domain.BiomeDataOps.entityOf(values.get(0)).isEmpty()
                    && values.stream().noneMatch(v -> com.strata.domain.BiomeDataOps.what(v).isEmpty());
            return read && costs && checks;
        });

        SelfTestHarness.check(lines, tally,
                "TG1 entity tags: the edited member lists the data pack writes", () -> {
            List<GameLayer.Row> sheet = List.of(
                    new GameLayer.Row("minecraft:zombie", GameLayer.TAG, "minecraft:undead", "true"),
                    new GameLayer.Row("minecraft:skeleton", GameLayer.TAG, "minecraft:undead", "true"),
                    new GameLayer.Row("minecraft:cow", GameLayer.TAG, "minecraft:undead", "false"),
                    new GameLayer.Row("minecraft:cow", GameLayer.TAG, "minecraft:raiders", "false"),
                    new GameLayer.Row("minecraft:zombie", GameLayer.TAG, "minecraft:raiders", "false"));
            List<Map<String, String>> edits = List.of(
                    Map.of("entity", "minecraft:cow", "variant_name", "", "group", "tag", "key", "minecraft:undead", "value", "true"),
                    Map.of("entity", "minecraft:zombie", "variant_name", "", "group", "tag", "key", "minecraft:undead", "value", "false"),
                    Map.of("entity", "minecraft:zombie", "variant_name", "big", "group", "tag", "key", "minecraft:raiders", "value", "true"),
                    Map.of("entity", "minecraft:cow", "variant_name", "", "group", "tag", "key", "minecraft:invented", "value", "true"),
                    Map.of("entity", "minecraft:cow", "variant_name", "", "group", "field", "key", "Cow.x", "value", "1"));
            var tags = GameLayer.editedTags(sheet, edits);
            return tags.size() == 1
                    && tags.get("minecraft:undead").equals(List.of("minecraft:cow", "minecraft:skeleton"))
                    && !tags.containsKey("minecraft:raiders") && !tags.containsKey("minecraft:invented");
        });

        SelfTestHarness.check(lines, tally,
                "UX1 the mob's own goals named and summed up, the data sections, goal sets by user", () -> {
            boolean name = com.strata.domain.GoalsOps.gameGoalsName("minecraft:cave_spider").equals("cave spider's own goals")
                    && com.strata.domain.GoalsOps.gameGoalsName("").equals("the game's own goals");
            var hunt = new com.strata.model.GoalRow(true, 2, "NearestAttackableTargetGoal", false,
                    Map.of("target", "minecraft:player"), com.strata.model.GoalCondition.ALWAYS, true);
            var group = new com.strata.model.GoalRow(true, 3, "NearestAttackableTargetGoal", false,
                    Map.of("target", "class:net.minecraft.world.entity.npc.AbstractVillager"),
                    com.strata.model.GoalCondition.ALWAYS, true);
            var back = new com.strata.model.GoalRow(true, 1, "HurtByTargetGoal", false, Map.of(),
                    com.strata.model.GoalCondition.ALWAYS, true);
            var melee = new com.strata.model.GoalRow(false, 2, "MeleeAttackGoal", false, Map.of("speed", "1.0"),
                    com.strata.model.GoalCondition.ALWAYS, true);
            var off = new com.strata.model.GoalRow(false, 3, "FloatGoal", false, Map.of(),
                    com.strata.model.GoalCondition.ALWAYS, false);
            String sum = com.strata.domain.GoalsOps.summary(List.of(hunt, melee, group, back, off));
            boolean summary = sum.equals("Hunts: whoever hurts it, player, any villager. Does: melee attack.")
                    && com.strata.domain.GoalsOps.readable("minecraft:iron_golem").equals("iron golem")
                    && com.strata.domain.GoalsOps.readable("class:net.minecraft.world.entity.monster.Monster").equals("any monster")
                    && com.strata.model.GoalGuide.of("Zombie$ZombieAttackTurtleEggGoal").name().equals("zombie attack turtle egg")
                    && com.strata.domain.GoalsOps.summary(List.of()).equals("Hunts nothing.");
            boolean sections = com.strata.domain.MobDataOps.section(GameLayer.ATTRIBUTE, "minecraft:generic.max_health")
                            .equals("minecraft:generic")
                    && com.strata.domain.MobDataOps.section(GameLayer.ATTRIBUTE, "neoforge:swim_speed").equals("neoforge")
                    && com.strata.domain.MobDataOps.section(GameLayer.FIELD, "LivingEntity.jumping")
                            .equals(com.strata.domain.MobDataOps.STATE_SECTION)
                    && com.strata.domain.MobDataOps.section(GameLayer.FIELD, "Blaze.allowedHeightOffset").equals("Blaze")
                    && com.strata.domain.MobDataOps.section(GameLayer.TAG, "c:bosses").equals("c")
                    && com.strata.domain.MobDataOps.section(GameLayer.TYPE, "width").equals("size");
            var values = List.of(
                    new com.strata.domain.MobDataOps.Value(GameLayer.FIELD, "Zombie.canBreakDoors", "false", "", ""),
                    new com.strata.domain.MobDataOps.Value(GameLayer.FIELD, "Mob.xpReward", "5", "", ""),
                    new com.strata.domain.MobDataOps.Value(GameLayer.FIELD, "Zombie.inWaterTime", "0", "", ""));
            var grouped = com.strata.domain.MobDataOps.sections(values);
            boolean order = new ArrayList<>(grouped.keySet()).equals(List.of(com.strata.domain.MobDataOps.SETTINGS_SECTION,
                    com.strata.domain.MobDataOps.STATE_SECTION))
                    && grouped.get(com.strata.domain.MobDataOps.SETTINGS_SECTION).size() == 2;
            var equip = List.<Map<String, String>>of(
                    Map.of("variant_name", "minecraft_zombie_default", "scope_id", "", "goal_set", "predator"),
                    Map.of("variant_name", "big_zombie", "scope_id", "t1", "goal_set", "predator"),
                    Map.of("variant_name", "big_zombie", "scope_id", "t1", "goal_set", "predator"),
                    Map.of("variant_name", "cow_x", "scope_id", "", "goal_set", "none"),
                    Map.of("variant_name", "cow_y", "scope_id", "", "goal_set", "gone"));
            var usersOf = com.strata.domain.GoalsOps.usersOf(equip,
                    v -> v.equals("minecraft_zombie_default") ? "minecraft:zombie" : v);
            boolean users = usersOf.get("predator").equals(List.of("big_zombie in t1", "minecraft:zombie"))
                    && !usersOf.containsKey("none") && usersOf.get("gone").equals(List.of("cow_y"));
            return name && summary && sections && order && users;
        });

        SelfTestHarness.check(lines, tally,
                "MC1 the Global mob list: every game mob, the designer's row first", () -> {
            var own = new com.strata.game.MobCatalog.MobEntry("minecraft:zombie", "", 40, true,
                    com.strata.model.Source.GLOBAL, com.strata.model.SpawnCategory.MONSTER, false);
            var variant = new com.strata.game.MobCatalog.MobEntry("minecraft:cow", "big_cow", 5, true,
                    com.strata.model.Source.GLOBAL, com.strata.model.SpawnCategory.CREATURE, false);
            Map<String, Integer> game = new java.util.TreeMap<>(Map.of(
                    "minecraft:zombie", 100, "minecraft:cow", 8, "minecraft:iron_golem", 0));
            var rows = com.strata.game.MobCatalog.withGameMobs(List.of(own, variant), game,
                    java.util.Set.of("minecraft:cow\u0000"), e -> com.strata.model.SpawnCategory.CREATURE);
            var cow = rows.stream().filter(r -> r.entity().equals("minecraft:cow") && r.variantName().isEmpty())
                    .findFirst().orElse(null);
            var golem = rows.stream().filter(r -> r.entity().equals("minecraft:iron_golem")).findFirst().orElse(null);
            return rows.size() == 4 && rows.get(0) == own
                    && rows.stream().filter(r -> r.entity().equals("minecraft:zombie")).count() == 1
                    && cow != null && cow.weight() == 8 && cow.source().layer() == com.strata.model.Source.Layer.GAME
                    && cow.changed()
                    && golem != null && golem.weight() == 0 && !golem.changed();
        });

        SelfTestHarness.check(lines, tally,
                "A74.36 VariantEntityRegistry.isPromotedEntityId filters promoted IDs", () -> {
            com.strata.runtime.VariantEntityRegistry.testAddPromotedEntityId("strata:test_variant");
            try {
                return com.strata.runtime.VariantEntityRegistry.isPromotedEntityId("strata:test_variant")
                        && !com.strata.runtime.VariantEntityRegistry.isPromotedEntityId("minecraft:zombie");
            } finally {
                com.strata.runtime.VariantEntityRegistry.testRemovePromotedEntityId("strata:test_variant");
            }
        });

        SelfTestHarness.check(lines, tally,
                "A74.37 the variant row under its base mob is present", () -> {
            var variantRow = new com.strata.game.MobCatalog.MobEntry(
                    "minecraft:skeleton", "big_guy", 5, true,
                    com.strata.model.Source.GLOBAL, com.strata.model.SpawnCategory.MONSTER, false);
            Map<String, Integer> game = new java.util.TreeMap<>(Map.of(
                    "strata:big_guy", 50, "minecraft:skeleton", 100));
            var rows = com.strata.game.MobCatalog.withGameMobs(List.of(variantRow), game,
                    java.util.Set.of("minecraft:skeleton\u0000big_guy"), e -> com.strata.model.SpawnCategory.MONSTER);
            return rows.stream().anyMatch(r -> r.entity().equals("minecraft:skeleton")
                    && r.variantName().equals("big_guy"));
        });

        SelfTestHarness.check(lines, tally,
                "A74.38 the fix does not remove the base mob itself", () -> {
            Map<String, Integer> game = new java.util.TreeMap<>(Map.of(
                    "strata:big_guy", 50, "minecraft:skeleton", 100));
            var rows = com.strata.game.MobCatalog.withGameMobs(List.of(), game,
                    java.util.Set.of(), e -> com.strata.model.SpawnCategory.MONSTER);
            return rows.stream().anyMatch(r -> r.entity().equals("minecraft:skeleton")
                    && r.variantName().isEmpty());
        });

        SelfTestHarness.check(lines, tally,
                "GR3 equipment as numbers: each armor piece's chance, and a row's own numbers", () -> {
            var zombie = com.strata.model.EquipmentFamilies.of("zombie");
            boolean sums = true;
            for (int s = 0; s < 4; s++) {
                for (boolean hard : new boolean[]{false, true}) {
                    double pieces = 0;
                    for (String m : com.strata.model.DefaultEquipment.MATERIALS) {
                        pieces += zombie.armorPiecePct(s, m, hard, 1);
                    }
                    sums &= Math.abs(pieces - zombie.armorSlotPct(s, hard, 1)) < 1e-9;
                }
            }
            boolean share = zombie.armorPiecePct(0, "leather", false, 1) > zombie.armorPiecePct(0, "diamond", false, 1)
                    && zombie.armorPiecePct(0, "diamond", false, 1) > 0;
            var none = new com.strata.model.DefaultEquipment(15, 25, 10, com.strata.model.DefaultEquipment.noMaterials(),
                    List.of(), 25, 50, 8.5);
            boolean empty = none.armorPiecePct(0, "iron", false, 1) == 0;
            var own = new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 40, "", "",
                    "60", "5", "10");
            var scaledOwn = new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 40, "", "",
                    "", "", "", true);
            var ownForm = com.strata.domain.EquipmentOps.equipmentForm(own);
            boolean edit = !ownForm.scaled() && ownForm.hard().equals("60") && ownForm.enchant().equals("10")
                    && com.strata.domain.EquipmentOps.pieceText(own).equals(", Hard 60%, drops 5%, enchant 10%")
                    && com.strata.domain.EquipmentOps.pieceText(scaledOwn).equals(", by local difficulty")
                    && com.strata.domain.EquipmentOps.scaledOf(com.strata.domain.EquipmentOps.scaledWord(true))
                    && !com.strata.domain.EquipmentOps.scaledOf(com.strata.domain.EquipmentOps.scaledWord(false));
            return sums && share && empty && edit;
        });

        SelfTestHarness.check(lines, tally,
                "GR2 a slot's draw: Hard weights, and local difficulty scales only a marked row", () -> {
            var normalOnly = List.of(new com.strata.runtime.MobEquipment.Choice(null, 100, 0, 2.0, null, false));
            boolean hard = com.strata.runtime.MobEquipment.pick(normalOnly, 0.5, false, 1) != null
                    && com.strata.runtime.MobEquipment.pick(normalOnly, 0.5, true, 1) == null
                    && com.strata.runtime.MobEquipment.pick(normalOnly, 0.5, false, 1).drop() == 2.0;
            var armor = List.of(new com.strata.runtime.MobEquipment.Choice(null, 40, 40, null, null, true));
            var typed = List.of(new com.strata.runtime.MobEquipment.Choice(null, 40, 40, null, null, false));
            boolean scaled = com.strata.runtime.MobEquipment.pick(armor, 0.01, false, 0) == null
                    && com.strata.runtime.MobEquipment.pick(armor, 0.19, false, 0.5) != null
                    && com.strata.runtime.MobEquipment.pick(armor, 0.21, false, 0.5) == null
                    && com.strata.runtime.MobEquipment.pick(typed, 0.39, false, 0) != null;
            return hard && scaled;
        });
        SelfTestHarness.check(lines, tally,
                "GL1 game layer: which fields are values, the value text, and the sheet round trip", () -> {
            record Probe(int size, double speed, String name, Boolean flag, List<String> list) {
                static int shared = 1;
            }
            List<String> kept = new ArrayList<>();
            for (var f : Probe.class.getDeclaredFields()) {
                if (GameLayer.wanted(f)) {
                    kept.add(f.getName());
                }
            }
            boolean fields = kept.equals(List.of("size", "speed", "name", "flag"));
            boolean text = GameLayer.text(20.0).equals("20.0") && GameLayer.text(0.25).equals("0.25")
                    && GameLayer.text(net.minecraft.world.entity.MobCategory.MONSTER).equals("monster")
                    && GameLayer.text(null).isEmpty() && GameLayer.text(true).equals("true");
            Path layerDir = Files.createTempDirectory("strata-game-layer");
            try {
                Path file = layerDir.resolve("game").resolve(GameLayer.MOBS);
                List<GameLayer.Row> rows = List.of(
                        new GameLayer.Row("minecraft:zombie", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "20.0"),
                        new GameLayer.Row("minecraft:zombie", GameLayer.FIELD, "Mob.name", "Frosty, the Husk"),
                        new GameLayer.Row("minecraft:cow", GameLayer.TYPE, "category", "creature"));
                boolean wrote = GameLayer.write(file, "line one\nline two", rows);
                boolean again = GameLayer.write(file, "line one", rows.subList(0, 1));
                boolean sheet = wrote && GameLayer.read(file).equals(rows.subList(0, 1))
                        && Files.readString(file).startsWith("# line one")
                        && !Files.exists(file.resolveSibling(GameLayer.MOBS + ".tmp"));
                GameLayer.write(file, "c", rows);
                var grouped = GameLayer.byEntity(GameLayer.read(file));
                boolean round = again && grouped.get("minecraft:zombie").equals(rows.subList(0, 2))
                        && grouped.get("minecraft:cow").equals(rows.subList(2, 3));
                return fields && text && sheet && round;
            } finally {
                deleteRec(layerDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "MD1 mob data: the three layers, the group order, the search, and the value check", () -> {
            List<GameLayer.Row> game = List.of(
                    new GameLayer.Row("minecraft:creeper", GameLayer.FIELD, "Creeper.explosionRadius", "3"),
                    new GameLayer.Row("minecraft:creeper", GameLayer.TYPE, "width", "0.6"),
                    new GameLayer.Row("minecraft:creeper", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "20.0"),
                    new GameLayer.Row("minecraft:creeper", GameLayer.FIELD, "Creeper.maxSwell", "30"));
            var values = com.strata.domain.MobDataOps.merge(game,
                    Map.of("field|Creeper.explosionRadius", "5", "field|Creeper.maxSwell", "20"),
                    Map.of("field|Creeper.explosionRadius", "8"));
            boolean order = values.stream().map(v -> v.key()).toList().equals(List.of(
                    "minecraft:generic.max_health", "width", "Creeper.explosionRadius", "Creeper.maxSwell"));
            var radius = values.get(2);
            var swell = values.get(3);
            var health = values.get(0);
            boolean layers = radius.effective().equals("8") && radius.source().equals("edited")
                    && swell.effective().equals("20") && swell.source().equals("inherited")
                    && health.effective().equals("20.0") && health.source().equals("game");
            boolean search = com.strata.domain.MobDataOps.filter(values, GameLayer.FIELD, "swell").equals(List.of(swell))
                    && com.strata.domain.MobDataOps.filter(values, GameLayer.FIELD, "").size() == 2
                    && com.strata.domain.MobDataOps.filter(values, GameLayer.TAG, "").isEmpty();
            boolean check = com.strata.domain.MobDataOps.problem("3", "4") == null
                    && com.strata.domain.MobDataOps.problem("3", "4.5") != null
                    && com.strata.domain.MobDataOps.problem("20.0", "4.5") == null
                    && com.strata.domain.MobDataOps.problem("20.0", "lots") != null
                    && com.strata.domain.MobDataOps.problem("true", "false") == null
                    && com.strata.domain.MobDataOps.problem("true", "yes") != null
                    && com.strata.domain.MobDataOps.problem("monster", "creature") == null
                    && com.strata.domain.MobDataOps.problem("monster", " ") != null;
            var base = new com.strata.domain.MobDataOps.Target("minecraft:creeper", "", "tier", "t1");
            var big = new com.strata.domain.MobDataOps.Target("minecraft:creeper", "big", "global", "t1");
            boolean target = base.baseMob() && base.scope().isEmpty()
                    && !big.baseMob() && big.scope().isEmpty() && big.scopeId().isEmpty() && !big.scoped()
                    && new com.strata.domain.MobDataOps.Target("minecraft:creeper", "big", "tier", "t1").scoped();
            var typeValue = new com.strata.domain.MobDataOps.Value(GameLayer.TYPE, "width", "0.6", "", "");
            var entityField = new com.strata.domain.MobDataOps.Value(GameLayer.FIELD, "Entity.id", "0", "", "");
            boolean locks = com.strata.domain.MobDataOps.lockReason(base, typeValue) == null
                    && com.strata.domain.MobDataOps.lockReason(big, typeValue) != null
                    && com.strata.domain.MobDataOps.lockReason(base, entityField) != null
                    && com.strata.domain.MobDataOps.lockReason(base, radius) == null
                    && GameLayer.locked(GameLayer.TYPE, "class") && !GameLayer.locked(GameLayer.TAG, "minecraft:raiders")
                    && com.strata.domain.MobDataOps.lockReason(big,
                            new com.strata.domain.MobDataOps.Value(GameLayer.TAG, "minecraft:undead", "true", "", "")) != null
                    && com.strata.domain.MobDataOps.typeProblem("category", "creature") == null
                    && com.strata.domain.MobDataOps.typeProblem("category", "boss") != null
                    && com.strata.domain.MobDataOps.typeProblem("width", "0") != null;
            var tierBig = new com.strata.domain.MobDataOps.Target("minecraft:creeper", "big", "tier", "t1");
            Map<String, String> b0 = new java.util.HashMap<>();
            Map<String, String> g0 = new java.util.HashMap<>();
            Map<String, String> o0 = new java.util.HashMap<>();
            boolean rows = com.strata.domain.MobDataOps.layer(tierBig, "", "", "", b0, g0, o0) == b0
                    && com.strata.domain.MobDataOps.layer(tierBig, "big", "global", "", b0, g0, o0) == g0
                    && com.strata.domain.MobDataOps.layer(tierBig, "big", "tier", "t1", b0, g0, o0) == o0
                    && com.strata.domain.MobDataOps.layer(tierBig, "big", "tier", "t2", b0, g0, o0) != o0
                    && com.strata.domain.MobDataOps.layer(tierBig, "small", "tier", "t1", b0, g0, o0) != o0
                    && com.strata.domain.MobDataOps.layer(big, "big", "", "", b0, g0, o0) == o0
                    && com.strata.domain.MobDataOps.layer(base, "big", "", "", b0, g0, o0) != o0;
            return order && layers && search && check && target && locks && rows;
        });

        SelfTestHarness.check(lines, tally,
                "MR1 mob data injection: the layer order, the class chain, and the value types", () -> {
            Map<String, Map<String, String>> baseEdits = Map.of("minecraft:creeper",
                    Map.of("Creeper.explosionRadius", "5", "Creeper.maxSwell", "20", "Mob.xpReward", "9"));
            Map<String, Map<String, String>> variantEdits = Map.of(
                    com.strata.runtime.ScopeIndex.key("", "", "big"),
                    Map.of("Creeper.explosionRadius", "6", "Creeper.maxSwell", "10"),
                    com.strata.runtime.ScopeIndex.key("tier", "t1", "big"), Map.of("Creeper.explosionRadius", "7"),
                    com.strata.runtime.ScopeIndex.key("biome", "minecraft:desert", "big"),
                    Map.of("Creeper.explosionRadius", "8"));
            var plain = com.strata.runtime.MobDataRuntime.fieldsFor(baseEdits, variantEdits,
                    "minecraft:creeper", "", "t1", "");
            var inTier = com.strata.runtime.MobDataRuntime.fieldsFor(baseEdits, variantEdits,
                    "minecraft:creeper", "big", "t1", "");
            var inBiome = com.strata.runtime.MobDataRuntime.fieldsFor(baseEdits, variantEdits,
                    "minecraft:creeper", "big", "t1", "minecraft:desert");
            boolean layers = plain.get("Creeper.explosionRadius").equals("5")
                    && inTier.get("Creeper.explosionRadius").equals("7") && inTier.get("Creeper.maxSwell").equals("10")
                    && inTier.get("Mob.xpReward").equals("9")
                    && inBiome.get("Creeper.explosionRadius").equals("8");
            class Top {
                int speed = 1;
            }
            class Low extends Top {
                float power;
                List<String> names;
            }
            boolean chain = com.strata.runtime.MobDataRuntime.field(Low.class, "Top.speed") != null
                    && com.strata.runtime.MobDataRuntime.field(Low.class, "Low.power") != null
                    && com.strata.runtime.MobDataRuntime.field(Low.class, "Low.names") == null
                    && com.strata.runtime.MobDataRuntime.field(Low.class, "Top.missing") == null
                    && com.strata.runtime.MobDataRuntime.field(Low.class, "speed") == null;
            boolean types = Integer.valueOf(4).equals(com.strata.runtime.MobDataRuntime.convert("4", int.class))
                    && com.strata.runtime.MobDataRuntime.convert("4.5", int.class) == null
                    && Float.valueOf(2.5f).equals(com.strata.runtime.MobDataRuntime.convert("2.5", float.class))
                    && Boolean.FALSE.equals(com.strata.runtime.MobDataRuntime.convert("false", boolean.class))
                    && com.strata.runtime.MobDataRuntime.convert("no", boolean.class) == null
                    && net.minecraft.world.entity.MobCategory.CREATURE.equals(com.strata.runtime.MobDataRuntime
                            .convert("creature", net.minecraft.world.entity.MobCategory.class))
                    && "hi".equals(com.strata.runtime.MobDataRuntime.convert(" hi ", String.class));
            return layers && chain && types;
        });

        SelfTestHarness.check(lines, tally,
                "AT1 AI from the game layer: targets both ways, a mob's goals, and the choices", () -> {
            List<GameLayer.Row> mobs = List.of(
                    new GameLayer.Row("minecraft:turtle", GameLayer.TYPE, "class",
                            "net.minecraft.world.entity.animal.Turtle"),
                    new GameLayer.Row("minecraft:turtle", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "30.0"),
                    new GameLayer.Row("minecraft:arrow", GameLayer.TYPE, "class",
                            "net.minecraft.world.entity.projectile.Arrow"),
                    new GameLayer.Row("minecraft:zombie", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "20.0"),
                    new GameLayer.Row("minecraft:zombie", GameLayer.TYPE, "class",
                            "net.minecraft.world.entity.monster.Zombie"));
            var byId = com.strata.runtime.GoalTargets.classes(mobs);
            boolean both = "minecraft:turtle".equals(com.strata.runtime.GoalTargets.targetFor(byId,
                            "net.minecraft.world.entity.animal.Turtle"))
                    && com.strata.model.GoalType.PLAYER.equals(com.strata.runtime.GoalTargets.targetFor(byId,
                            net.minecraft.world.entity.player.Player.class.getName()))
                    && "class:net.minecraft.world.entity.monster.Monster".equals(com.strata.runtime.GoalTargets
                            .targetFor(byId, "net.minecraft.world.entity.monster.Monster"));
            String hunt = "t|2|chain|NearestAttackableTargetGoal|target=class:net.minecraft.world.entity.npc.AbstractVillager;must_see=false";
            String look = "h|8|chain|LookAtPlayerGoal|target=minecraft:player;range=8.0";
            List<GameLayer.Row> goals = List.of(
                    new GameLayer.Row("minecraft:zombie", GameLayer.GOAL, "0", hunt),
                    new GameLayer.Row("minecraft:cow", GameLayer.GOAL, "0", "h|0|chain|FloatGoal|"),
                    new GameLayer.Row("minecraft:zombie", GameLayer.GOAL, "1", look));
            var zombie = com.strata.domain.GoalsOps.goalsOf(goals, "minecraft:zombie");
            boolean read = zombie.size() == 2 && zombie.get(0).target() && zombie.get(0).priority() == 2
                    && zombie.get(1).encode().equals(com.strata.model.GameGoal.decode(look).encode())
                    && com.strata.domain.GoalsOps.goalsOf(goals, "minecraft:pig").isEmpty();
            boolean choices = com.strata.domain.GoalsOps.targetChoices(mobs, goals).equals(List.of(
                    "minecraft:player", "minecraft:turtle", "minecraft:zombie",
                    "class:net.minecraft.world.entity.npc.AbstractVillager"));
            boolean words = com.strata.model.GoalType.isTarget("minecraft:turtle")
                    && com.strata.model.GoalType.isTarget("somemod:big_crab")
                    && com.strata.model.GoalType.isTarget("class:net.minecraft.world.entity.monster.Monster")
                    && !com.strata.model.GoalType.isTarget("villager") && !com.strata.model.GoalType.isTarget("class:")
                    && com.strata.domain.GoalsOps.targetLabel("class:net.minecraft.world.entity.monster.Monster")
                            .equals("any Monster")
                    && com.strata.domain.GoalsOps.targetLabel("minecraft:turtle").equals("minecraft:turtle");
            boolean built = sampleGoalSets().values().stream().allMatch(list ->
                    com.strata.domain.GoalsOps.listProblem(list) == null);
            return both && read && choices && words && built;
        });

        SelfTestHarness.check(lines, tally,
                "VS1 variant stats keep their scope: global, then tier, then biome", () -> {
            Path statsDir = Files.createTempDirectory("strata-variant-stats");
            try {
                Files.writeString(statsDir.resolve(Schema.Variants.FILE),
                        "name,base_entity\nbig,minecraft:zombie\n");
                Files.writeString(statsDir.resolve(Schema.VariantStats.FILE),
                        "variant_name,scope,scope_id,attribute,multiplier,absolute\n"
                                + "big,,,minecraft:generic.max_health,2,\n"
                                + "big,tier,t1,minecraft:generic.max_health,3,40\n"
                                + "big,biome,minecraft:desert,minecraft:generic.max_health,,60\n");
                var big = Tables.loadAll(statsDir).variants().get("big");
                String hp = "minecraft:generic.max_health";
                return big != null
                        && big.multipliersIn("", "").get(hp) == 2.0 && big.absolutesIn("", "").isEmpty()
                        && big.multipliersIn("t1", "").get(hp) == 3.0 && big.absolutesIn("t1", "").get(hp) == 40.0
                        && big.multipliersIn("t1", "minecraft:desert").get(hp) == 3.0
                        && big.absolutesIn("t1", "minecraft:desert").get(hp) == 60.0
                        && big.absolutesIn("t2", "").isEmpty() && big.multipliers().get(hp) == 2.0;
            } finally {
                deleteRec(statsDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "NW1 every Strata message round-trips, a long table included", () -> {
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            String longTable = "a,b\n" + "x,y\n".repeat(20000);
            var upload = new com.strata.network.StrataPayloads.ProfileUpload("pvp",
                    Map.of("mob_spawns.csv", longTable, "strata-common.toml", "k=1"));
            com.strata.network.StrataPayloads.ProfileUpload.CODEC.encode(buf, upload);
            boolean uploadOk = com.strata.network.StrataPayloads.ProfileUpload.CODEC.decode(buf).equals(upload);
            var own = new com.strata.network.StrataPayloads.OwnEntities(List.of(
                    new com.strata.network.StrataPayloads.OwnEntity("fire_zombie", "minecraft:zombie")));
            com.strata.network.StrataPayloads.OwnEntities.CODEC.encode(buf, own);
            boolean ownOk = com.strata.network.StrataPayloads.OwnEntities.CODEC.decode(buf).equals(own);
            var sync = new com.strata.network.StrataPayloads.ServerSync("shipped", true,
                    Map.of("Fire Zombie", "strata:textures/fire.png"), Map.of("minecraft:phantom|model", "minecraft:bat"));
            com.strata.network.StrataPayloads.ServerSync.CODEC.encode(buf, sync);
            boolean syncOk = com.strata.network.StrataPayloads.ServerSync.CODEC.decode(buf).equals(sync);
            var reply = new com.strata.network.StrataPayloads.GoalsReply("minecraft:cow",
                    List.of("PanicGoal", "BreedGoal"));
            com.strata.network.StrataPayloads.GoalsReply.CODEC.encode(buf, reply);
            boolean goalsOk = com.strata.network.StrataPayloads.GoalsReply.CODEC.decode(buf).equals(reply);
            var result = new com.strata.network.StrataPayloads.UploadResult(false, "no");
            com.strata.network.StrataPayloads.UploadResult.CODEC.encode(buf, result);
            boolean resultOk = com.strata.network.StrataPayloads.UploadResult.CODEC.decode(buf).equals(result);
            return uploadOk && ownOk && syncOk && goalsOk && resultOk && buf.readableBytes() == 0;
        });

        SelfTestHarness.check(lines, tally, "FL1 every table in the schema has its default file, so a new world and every profile get it", () -> {
            List<String> missing = new ArrayList<>();
            for (Class<?> c : Schema.class.getDeclaredClasses()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType() == String.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())
                            && f.getName().endsWith("FILE")) {
                        String file = (String) f.get(null);
                        if (file.endsWith(".csv") && !Defaults.templates().containsKey(file)) {
                            missing.add(file);
                        }
                    }
                }
            }
            missing.forEach(m -> lines.add("    FL1 no default: " + m));
            return missing.isEmpty();
        });
        SelfTestHarness.check(lines, tally, "MB2 a biome's dimension that is a word, or none, makes a heading and no crash", () ->
                com.strata.util.Labels.pretty((net.minecraft.resources.ResourceLocation) null).isEmpty()
                        && com.strata.util.Labels.pretty("Overworld").equals("Overworld")
                        && new com.strata.domain.MobBiomesOps.Place("minecraft:plains", null).dimension().isEmpty());
        SelfTestHarness.check(lines, tally, "OD1 a profile with every switch Off from the old Default gets them On; one with any On is left", () -> {
            Path odDir = Files.createTempDirectory("strata-od");
            try {
                Path all = odDir.resolve("a.toml");
                Path some = odDir.resolve("b.toml");
                String off = "[scaling]\nenableMobScaling = false\nenablePlayerScaling = false\n"
                        + "enableOreRestriction = false\nenableSpawnSubstitution = false\nenableLoot = false\n";
                Files.writeString(all, off);
                Files.writeString(some, off.replace("enableLoot = false", "enableLoot = true"));
                boolean fixed = ProfileManager.undoOldDefaultSwitches(all)
                        && !Files.readString(all).contains("false");
                boolean left = !ProfileManager.undoOldDefaultSwitches(some)
                        && Files.readString(some).contains("enableMobScaling = false");
                return fixed && left;
            } finally {
                deleteRec(odDir);
            }
        });
        SelfTestHarness.check(lines, tally, "OD2 player scaling ships Off, so Default needs no switch of its own; a profile forked with only it Off is left", () -> {
            boolean path = com.strata.data.StrataConfig.SPEC.getSpec().contains("systems.enablePlayerScaling")
                    && !com.strata.data.StrataConfig.ENABLE_PLAYER_SCALING.getDefault();
            Path odDir = Files.createTempDirectory("strata-od2");
            try {
                Path one = odDir.resolve("c.toml");
                Files.writeString(one, "[systems]\nenableMobScaling = true\nenablePlayerScaling = false\n");
                boolean left = !ProfileManager.undoOldDefaultSwitches(one)
                        && Files.readString(one).contains("enablePlayerScaling = false");
                return path && left;
            } finally {
                deleteRec(odDir);
            }
        });
        SelfTestHarness.check(lines, tally, "UE1 on Default nothing is unsaved: Strata's own files there are not edits", () -> {
            Path root = Files.createTempDirectory("strata-default");
            try {
                Files.createDirectories(root.resolve("profiles/Default"));
                write(root.resolve("profiles/Default"), "tiers.csv", "name\n");
                write(root, "tiers.csv", "name,level\nseeded,1\n");
                return !ProfileManager.hasUnsavedEdits(root);
            } finally {
                deleteRec(root);
            }
        });
        SelfTestHarness.check(lines, tally,
                "UP2 a profile carries its override sheets: upload, store, switch and export keep them", () -> {
            boolean names = ProfileManager.uploadProblem("pvp", Map.of("tiers.csv", "a", "overrides/recipe.json", "{}")) == null
                    && ProfileManager.uploadProblem("pvp", Map.of("tiers.csv", "a", "overrides/../x.json", "{}")) != null
                    && ProfileManager.uploadProblem("pvp", Map.of("tiers.csv", "a", "overrides/a/b.json", "{}")) != null
                    && ProfileManager.uploadProblem("pvp", Map.of("tiers.csv", "a", "other/recipe.json", "{}")) != null;
            Path root = Files.createTempDirectory("strata-sheets");
            try {
                Path profiles = root.resolve("profiles");
                boolean stored = ProfileManager.storeUpload(profiles, "pvp",
                        Map.of("tiers.csv", "name\n", "overrides/recipe.json", "{\"a\":1}")) == ProfileManager.Result.OK
                        && Files.readString(profiles.resolve("pvp/overrides/recipe.json")).equals("{\"a\":1}");
                Path working = root.resolve("working");
                Files.createDirectories(working.resolve("overrides"));
                write(working.resolve("overrides"), "old.json", "{}");
                ProfileManager.copyTables(profiles.resolve("pvp"), working);
                boolean switched = Files.exists(working.resolve("overrides/recipe.json"))
                        && !Files.exists(working.resolve("overrides/old.json"));
                Files.createDirectories(profiles.resolve("old"));
                write(profiles.resolve("old"), "tiers.csv", "name\n");
                ProfileManager.copyTables(profiles.resolve("old"), working);
                boolean kept = Files.exists(working.resolve("overrides/recipe.json"));
                // An empty overrides folder (Default's) clears them.
                Files.createDirectories(profiles.resolve("old/overrides"));
                ProfileManager.copyTables(profiles.resolve("old"), working);
                boolean cleared = !Files.exists(working.resolve("overrides/recipe.json"));
                // The stored profile's files, as the upload would read them back.
                var back = ProfileManager.filesIn(profiles.resolve("pvp"), "pvp");
                boolean zipped = "{\"a\":1}".equals(back.get("overrides/recipe.json"))
                        && ProfileManager.uploadProblem("pvp", back) == null;
                return names && stored && switched && kept && cleared && zipped;
            } finally {
                deleteRec(root);
            }
        });
        SelfTestHarness.check(lines, tally,
                "UP1 the server refuses an unsafe upload, and a stored one replaces the old", () -> {
            boolean refused = ProfileManager.uploadProblem("../etc", Map.of("tiers.csv", "a")) != null
                    && ProfileManager.uploadProblem("pvp", Map.of("strata-common.toml", "a")) != null
                    && ProfileManager.uploadProblem("pvp", Map.of("tiers.csv", "a", "run.bat", "x")) != null
                    && ProfileManager.uploadProblem("pvp", Map.of("../tiers.csv", "a")) != null
                    && ProfileManager.uploadProblem("pvp",
                            Map.of("tiers.csv", "x".repeat(ProfileManager.MAX_UPLOAD_CHARS + 1))) != null;
            boolean accepted = ProfileManager.uploadProblem("pvp",
                    Map.of("tiers.csv", "a", "strata-common.toml", "b")) == null;
            Path profiles = Files.createTempDirectory("strata-upload");
            try {
                Files.createDirectories(profiles.resolve("pvp"));
                write(profiles.resolve("pvp"), "stale.csv", "old\n");
                boolean stored = ProfileManager.storeUpload(profiles, "pvp", Map.of("tiers.csv", "new\n"))
                        == ProfileManager.Result.OK
                        && Files.exists(profiles.resolve("pvp").resolve("tiers.csv"))
                        && !Files.exists(profiles.resolve("pvp").resolve("stale.csv"));
                return refused && accepted && stored;
            } finally {
                deleteRec(profiles);
            }
        });

        SelfTestHarness.check(lines, tally,
                "UP3 an upload with too many files or a long name is refused on decode", () -> {
            var codec = com.strata.network.StrataPayloads.ProfileUpload.CODEC;
            Map<String, String> many = new java.util.LinkedHashMap<>();
            for (int i = 0; i <= com.strata.network.StrataPayloads.MAX_FILES; i++) {
                many.put("t" + i + ".csv", "a");
            }
            String longName = "p".repeat(com.strata.network.StrataPayloads.MAX_NAME_CHARS + 1);
            boolean manyRefused = refusedOnDecode(codec,
                    new com.strata.network.StrataPayloads.ProfileUpload("pvp", many));
            boolean nameRefused = refusedOnDecode(codec,
                    new com.strata.network.StrataPayloads.ProfileUpload(longName, Map.of("tiers.csv", "a")));
            return manyRefused && nameRefused;
        });

        SelfTestHarness.check(lines, tally,
                "CS1 a custom structure needs a name, biomes, a spacing over its separation; a file at most 48 a side", () -> {
            Map<String, String> ok = new java.util.HashMap<>(com.strata.model.CustomStructure.DEFAULTS);
            ok.put("name", "ruined_tower");
            java.util.function.Function<Map<String, String>, String> with = change -> {
                Map<String, String> r = new java.util.HashMap<>(ok);
                r.putAll(change);
                return com.strata.model.CustomStructure.problem(r);
            };
            boolean good = com.strata.model.CustomStructure.problem(ok) == null
                    && with.apply(Map.of("biomes", "minecraft:plains, minecraft:desert")) == null
                    && with.apply(Map.of("height", "-40")) == null;
            boolean bad = with.apply(Map.of("name", "Ruined Tower")) != null
                    && with.apply(Map.of("biomes", "")) != null
                    && with.apply(Map.of("biomes", "#minecraft:is_forest, minecraft:plains")) != null
                    && with.apply(Map.of("separation", "34")) != null
                    && with.apply(Map.of("step", "sideways")) != null
                    && with.apply(Map.of("height", "sky")) != null;
            boolean shape = com.strata.model.CustomStructure.shapeProblem(12, 9, 14, 840) == null
                    && com.strata.model.CustomStructure.shapeProblem(49, 9, 14, 840) != null
                    && com.strata.model.CustomStructure.shapeProblem(0, 9, 14, 0) != null;
            if (!(good && bad && shape)) {
                lines.add("    CS1 good=" + good + " bad=" + bad + " shape=" + shape);
            }
            return good && bad && shape;
        });

        SelfTestHarness.check(lines, tally,
                "BS9 a block's sound, collision, offset and the rest are edited; what it registers as is shown", () -> {
            boolean edited = com.strata.model.BlockStats.KEYS.containsAll(List.of("sound", "map_color", "random_ticks",
                    "solid", "collision", "occludes", "redstone_conductor", "terrain_particles", "emissive",
                    "mobs_spawn_on", "suffocating", "view_blocking", "offset"));
            boolean shown = com.strata.model.BlockStats.SHOWN.equals(List.of("block_entity", "item", "states",
                    "loot_table", "tags"));
            boolean words = com.strata.model.BlockStats.problem("collision", "maybe") != null
                    && com.strata.model.BlockStats.problem("collision", "False") == null
                    && com.strata.model.BlockStats.problem("offset", "xyz") == null
                    && com.strata.model.BlockStats.problem("offset", "sideways") != null
                    && com.strata.model.BlockStats.problem("states", "x") != null
                    && com.strata.model.BlockStats.typeOf("emissive") == com.strata.model.ValueType.BOOL;
            if (!(edited && shown && words)) {
                lines.add("    BS9 edited=" + edited + " shown=" + shown + " words=" + words);
            }
            return edited && shown && words;
        });

        SelfTestHarness.check(lines, tally,
                "IC1 an item component is a value of its own key, named in words; the plain seven keep theirs", () -> {
            String key = com.strata.model.ItemStats.componentKey("minecraft:repair_cost");
            boolean keys = key.equals("component|minecraft:repair_cost")
                    && "minecraft:repair_cost".equals(com.strata.model.ItemStats.componentId(key))
                    && com.strata.model.ItemStats.componentId("food.nutrition") == null
                    && com.strata.model.ItemStats.componentId("attribute|minecraft:generic.armor|head") == null;
            boolean words = com.strata.model.ItemStats.componentWords("minecraft:repair_cost").equals("Repair cost")
                    && com.strata.model.ItemStats.componentWords("somemod:mana_cost").equals("Mana cost (somemod)")
                    && com.strata.model.ItemStats.name(key, "").equals("Repair cost");
            boolean plain = com.strata.model.ItemStats.FLATTENED.contains("minecraft:food")
                    && com.strata.model.ItemStats.FLATTENED.contains("minecraft:attribute_modifiers")
                    && !com.strata.model.ItemStats.FLATTENED.contains("minecraft:lore");
            boolean passes = com.strata.model.ItemStats.problem(key, "5", false) == null
                    && com.strata.model.ItemStats.problem(key, "", false) != null
                    && com.strata.model.ItemStats.typeOf(key) == com.strata.model.ValueType.TEXT;
            if (!(keys && words && plain && passes)) {
                lines.add("    IC1 keys=" + keys + " words=" + words + " plain=" + plain + " passes=" + passes);
            }
            return keys && words && plain && passes;
        });

        SelfTestHarness.check(lines, tally, "CRUMB1 titles are breadcrumbs of where a screen was opened", () -> {
            java.util.function.BinaryOperator<String> c = (parent, ownShort) -> com.strata.ui.TitlePath.crumb(parent,
                    ownShort);
            String mobs = com.strata.ui.TitlePath.compose("World", "Strata — Mobs");
            String zombie = c.apply(mobs, com.strata.ui.TitlePath.compose("World", "Strata — Mob: Zombie"));
            String goals = c.apply(zombie, com.strata.ui.TitlePath.compose("World", "Strata — Goal sets"));
            String data = c.apply(zombie, com.strata.ui.TitlePath.compose("World", "Strata — Mob data: Zombie"));
            String tier = c.apply(com.strata.ui.TitlePath.compose("World", "Strata — Tiers"),
                    com.strata.ui.TitlePath.compose("World", "Strata — Tier: T1"));
            String ores = c.apply(tier, com.strata.ui.TitlePath.compose("Blocks", "Strata — Ores: Tier: T1"));
            String proj = c.apply(mobs, com.strata.ui.TitlePath.compose("World", "Strata — Projectiles"));
            String chest = c.apply("Strata / Blocks / Chest", com.strata.ui.TitlePath.compose("Items", "Strata — Chest loot"));
            Map<String, String> want = new java.util.LinkedHashMap<>();
            want.put(zombie, "Strata / World / Mobs / Zombie");
            want.put(goals, "Strata / World / Mobs / Zombie / Goal sets");
            want.put(data, "Strata / World / Mobs / Zombie / Mob data");
            want.put(tier, "Strata / World / Tiers / T1");
            want.put(ores, "Strata / World / Tiers / T1 / Ores");
            want.put(proj, "Strata / World / Mobs / Projectiles");
            want.put(chest, "Strata / Blocks / Chest / Chest loot");
            boolean ok = true;
            for (var e : want.entrySet()) {
                if (!e.getKey().equals(e.getValue())) {
                    lines.add("    CRUMB1 got '" + e.getKey() + "' want '" + e.getValue() + "'");
                    ok = false;
                }
            }
            return ok && c.apply(null, mobs).equals(mobs) && c.apply("Mods", mobs).equals(mobs);
        });

        SelfTestHarness.check(lines, tally,
                "PJ1 a projectile row reads blank as its base's own, and a bad one is refused", () -> {
            Map<String, String> ok = Map.of("name", "spider_egg", "base", "minecraft:snowball", "damage", "4",
                    "gravity", "0", "keep_base", "false");
            var p = com.strata.model.ProjectilePreset.of(ok);
            boolean read = p.name().equals("spider_egg") && p.id().equals("strata:spider_egg")
                    && p.damage() == 4 && p.gravity() == 0 && Double.isNaN(p.speed()) && p.explosion() == 0
                    && !p.keepBase() && p.enabled()
                    && com.strata.model.ProjectilePreset.problem(ok) == null;
            boolean names = "spider_egg".equals(com.strata.model.ProjectilePreset.nameOf("strata:spider_egg"))
                    && com.strata.model.ProjectilePreset.nameOf("minecraft:arrow") == null;
            java.util.function.Function<Map<String, String>, Boolean> refused = row -> {
                Map<String, String> r = new java.util.HashMap<>(ok);
                r.putAll(row);
                return com.strata.model.ProjectilePreset.problem(r) != null;
            };
            boolean bad = refused.apply(Map.of("name", "Spider Egg")) && refused.apply(Map.of("base", ""))
                    && refused.apply(Map.of("base", "strata:other")) && refused.apply(Map.of("damage", "lots"))
                    && refused.apply(Map.of("explosion", "50")) && refused.apply(Map.of("speed", "0"))
                    && !refused.apply(Map.of("damage", ""))
                    && !refused.apply(Map.of("look", "minecraft:fire_charge")) && refused.apply(Map.of("look", "Fire Charge"))
                    && !refused.apply(Map.of("texture", "minecraft:textures/entity/projectiles/spectral_arrow.png"))
                    && refused.apply(Map.of("texture", "minecraft:entity/arrow.png"))
                    && refused.apply(Map.of("texture", "minecraft:textures/../x.png"))
                    && refused.apply(Map.of("texture", "minecraft:textures/x.jpg"));
            boolean summary = com.strata.domain.ProjectileOps.summary(p, 2).contains("damage 4")
                    && com.strata.domain.ProjectileOps.summary(p, 2).contains("2 abilities");
            if (!(read && names && bad && summary)) {
                lines.add("    PJ1 read=" + read + " names=" + names + " bad=" + bad + " summary=" + summary);
            }
            return read && names && bad && summary;
        });

    }

    private static void runTestsPart09(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "WSET1 world settings: bad values are refused, and day length converts both ways", () -> {
            var ok = new com.strata.domain.WorldSettingsOps.Form("0", "0", "59999968", "0.2", "5", "5", "15",
                    "Normal", "No", "6000", "20", "Clear", "6000", "0");
            java.util.function.Function<java.util.function.UnaryOperator<com.strata.domain.WorldSettingsOps.Form>, Boolean> bad =
                    edit -> com.strata.domain.WorldSettingsOps.formProblem(edit.apply(ok)) != null;
            boolean accepted = com.strata.domain.WorldSettingsOps.formProblem(ok) == null;
            boolean refused = bad.apply(f -> new com.strata.domain.WorldSettingsOps.Form("x", f.centerZ(), f.size(),
                    f.damagePerBlock(), f.safeZone(), f.warningBlocks(), f.warningSeconds(), f.difficulty(), f.locked(),
                    f.time(), f.dayMinutes(), f.weather(), f.weatherTicks(), f.spawnAngle()))
                    && bad.apply(f -> new com.strata.domain.WorldSettingsOps.Form(f.centerX(), f.centerZ(), "0",
                    f.damagePerBlock(), f.safeZone(), f.warningBlocks(), f.warningSeconds(), f.difficulty(), f.locked(),
                    f.time(), f.dayMinutes(), f.weather(), f.weatherTicks(), f.spawnAngle()))
                    && bad.apply(f -> new com.strata.domain.WorldSettingsOps.Form(f.centerX(), f.centerZ(), f.size(),
                    f.damagePerBlock(), f.safeZone(), f.warningBlocks(), f.warningSeconds(), "Impossible", f.locked(),
                    f.time(), f.dayMinutes(), f.weather(), f.weatherTicks(), f.spawnAngle()))
                    && bad.apply(f -> new com.strata.domain.WorldSettingsOps.Form(f.centerX(), f.centerZ(), f.size(),
                    f.damagePerBlock(), f.safeZone(), f.warningBlocks(), f.warningSeconds(), f.difficulty(), f.locked(),
                    "24000", f.dayMinutes(), f.weather(), f.weatherTicks(), f.spawnAngle()))
                    && bad.apply(f -> new com.strata.domain.WorldSettingsOps.Form(f.centerX(), f.centerZ(), f.size(),
                    f.damagePerBlock(), f.safeZone(), f.warningBlocks(), f.warningSeconds(), f.difficulty(), f.locked(),
                    f.time(), "0", f.weather(), f.weatherTicks(), f.spawnAngle()))
                    && bad.apply(f -> new com.strata.domain.WorldSettingsOps.Form(f.centerX(), f.centerZ(), f.size(),
                    f.damagePerBlock(), f.safeZone(), "2.5", f.warningSeconds(), f.difficulty(), f.locked(),
                    f.time(), f.dayMinutes(), f.weather(), f.weatherTicks(), f.spawnAngle()));
            boolean convert = com.strata.domain.WorldSettingsOps.dayTimePerTickFor(20) == 1.0f
                    && com.strata.domain.WorldSettingsOps.dayTimePerTickFor(10) == 2.0f
                    && com.strata.domain.WorldSettingsOps.minutesFor(0.5f) == 40.0
                    && com.strata.domain.WorldSettingsOps.minutesFor(-1f) == 20.0
                    && com.strata.domain.WorldSettingsOps.num(5.0).equals("5")
                    && com.strata.domain.WorldSettingsOps.num(0.2).equals("0.2");
            if (!(accepted && refused && convert)) {
                lines.add("    WSET1 accepted=" + accepted + " refused=" + refused + " convert=" + convert);
            }
            return accepted && refused && convert;
        });

        SelfTestHarness.check(lines, tally,
                "PJ2 a projectile's abilities offer On impact only, and no other list offers it", () -> {
            var impact = com.strata.domain.TriggerOps.IMPACT;
            boolean projectile = com.strata.domain.ProjectileOps.effectsStore("x").triggers().equals(List.of(impact));
            boolean others = !com.strata.domain.AbilitiesOps.flatStore(Schema.ItemVariantEffects.FILE,
                    Schema.ItemVariantEffects.VARIANT, "x").triggers().contains(impact)
                    && !com.strata.domain.AbilitiesOps.mobStore("x", "minecraft:zombie", "", "").triggers().contains(impact)
                    && com.strata.domain.TriggerOps.creatureTriggers().contains("on_hit");
            boolean named = com.strata.domain.TriggerOps.displayName("on_hit").equals("On deal damage")
                    && com.strata.domain.TriggerOps.displayName(impact).equals("On impact")
                    && com.strata.domain.TriggerOps.problem(impact).isEmpty();
            return projectile && others && named;
        });

        SelfTestHarness.check(lines, tally,
                "MP1 property rows stack base, Global, tier, biome; another tier's stays out", () -> {
            Path root = Files.createTempDirectory("strata-props");
            try {
                java.util.function.BiConsumer<Map<String, String>, Map<String, String>> sound = (keys, values) ->
                        TableWriter.upsert(root.resolve(Schema.MobSounds.FILE), keys, values);
                write(root, Schema.MobSounds.FILE, "scope,scope_id,entity,variant_name,is_variant,"
                        + "ambient_sound,hurt_sound,death_sound,ambient_volume,enabled\n");
                sound.accept(Map.of("scope", "", "scope_id", "", "entity", "minecraft:zombie", "variant_name", "",
                        "is_variant", "false"), Map.of("ambient_sound", "minecraft:base", "hurt_sound", "minecraft:base"));
                sound.accept(Map.of("scope", "global", "scope_id", "", "entity", "minecraft:zombie", "variant_name", "grim",
                        "is_variant", "true"), Map.of("hurt_sound", "minecraft:grim", "death_sound", "minecraft:grim"));
                sound.accept(Map.of("scope", "tier", "scope_id", "hard", "entity", "minecraft:zombie", "variant_name", "grim",
                        "is_variant", "true"), Map.of("death_sound", "minecraft:hard", "ambient_volume", "2"));
                sound.accept(Map.of("scope", "tier", "scope_id", "easy", "entity", "minecraft:zombie", "variant_name", "grim",
                        "is_variant", "true"), Map.of("ambient_sound", "minecraft:easy"));
                sound.accept(Map.of("scope", "biome", "scope_id", "minecraft:plains", "entity", "minecraft:zombie",
                        "variant_name", "grim", "is_variant", "true"), Map.of("ambient_volume", "3"));
                MobPropertyLayers layers = MobPropertyLayers.load(root);
                var baseOnly = layers.resolve("minecraft:zombie", "", "", "");
                var grimHard = layers.resolve("minecraft:zombie", "grim", "hard", "");
                var grimHardPlains = layers.resolve("minecraft:zombie", "grim", "hard", "minecraft:plains");
                var husk = layers.resolve("minecraft:husk", "", "", "");
                boolean base = "minecraft:base".equals(baseOnly.ambientSound) && "minecraft:base".equals(baseOnly.hurtSound)
                        && baseOnly.deathSound == null;
                boolean stacked = "minecraft:base".equals(grimHard.ambientSound)
                        && "minecraft:grim".equals(grimHard.hurtSound)
                        && "minecraft:hard".equals(grimHard.deathSound)
                        && Float.valueOf(2f).equals(grimHard.ambientVolume)
                        && Float.valueOf(3f).equals(grimHardPlains.ambientVolume);
                boolean apart = husk.isEmpty();
                var flat = new java.util.LinkedHashMap<String, String>();
                layers.stacked("minecraft:zombie", "grim", "hard", "").forEach((g, vs) ->
                        vs.forEach((c, v) -> flat.put(g + "." + c, v)));
                boolean wire = com.strata.runtime.MobProperties.grouped(flat)
                        .equals(layers.stacked("minecraft:zombie", "grim", "hard", ""));
                if (!(base && stacked && apart && wire)) {
                    lines.add("    MP1 base=" + base + " stacked=" + stacked + " apart=" + apart + " wire=" + wire);
                }
                return base && stacked && apart && wire;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "MPB the boss group stacks base and variant rows; a bad colour or range is unset", () -> {
            Path root = Files.createTempDirectory("strata-boss");
            try {
                write(root, Schema.MobBoss.FILE, "scope,scope_id,entity,variant_name,is_variant,bar_color,bar_style,"
                        + "bar_name,bar_range,darken_sky,fog,boss_music,never_despawn,enabled\n");
                write(root, Schema.MobFlags.FILE, "scope,scope_id,entity,variant_name,is_variant,boss,enabled\n");
                TableWriter.upsert(root.resolve(Schema.MobFlags.FILE), Map.of("scope", "", "scope_id", "",
                        "entity", "minecraft:ravager", "variant_name", "", "is_variant", "false"), Map.of("boss", "true"));
                TableWriter.upsert(root.resolve(Schema.MobBoss.FILE), Map.of("scope", "", "scope_id", "",
                        "entity", "minecraft:ravager", "variant_name", "", "is_variant", "false"),
                        Map.of("bar_color", "red", "bar_range", "20", "boss_music", "true"));
                TableWriter.upsert(root.resolve(Schema.MobBoss.FILE), Map.of("scope", "global", "scope_id", "",
                        "entity", "minecraft:ravager", "variant_name", "titan", "is_variant", "true"),
                        Map.of("bar_color", "teal", "bar_style", "notched_10", "bar_range", "-3", "fog", "true"));
                MobPropertyLayers layers = MobPropertyLayers.load(root);
                var base = layers.resolve("minecraft:ravager", "", "", "");
                var titan = layers.resolve("minecraft:ravager", "titan", "", "");
                boolean baseOk = Boolean.TRUE.equals(base.boss) && "red".equals(base.barColor)
                        && Double.valueOf(20).equals(base.barRange) && Boolean.TRUE.equals(base.bossMusic)
                        && base.barStyle == null && base.fog == null;
                boolean layered = "red".equals(titan.barColor) == false && titan.barColor == null
                        && "notched_10".equals(titan.barStyle) && titan.barRange == null
                        && Boolean.TRUE.equals(titan.fog) && Boolean.TRUE.equals(titan.bossMusic)
                        && Boolean.TRUE.equals(titan.boss);
                if (!(baseOk && layered)) {
                    lines.add("    MPB base=" + baseOk + " layered=" + layered);
                }
                return baseOk && layered;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "MPC pathfinding, drop chances and tags stack base and variant rows; a bad number is unset", () -> {
            Path root = Files.createTempDirectory("strata-step3");
            try {
                write(root, Schema.MobPathfinding.FILE, Defaults.MOB_PATHFINDING_HEADER.replace(",\n", "\n"));
                write(root, Schema.MobLootXp.FILE, Defaults.MOB_LOOT_XP_HEADER.replace(",\n", "\n"));
                write(root, Schema.MobFlags.FILE, Defaults.MOB_FLAGS_HEADER.replace(",\n", "\n"));
                Map<String, String> baseKey = Map.of("scope", "", "scope_id", "", "entity", "minecraft:zoglin",
                        "variant_name", "", "is_variant", "false");
                Map<String, String> variantKey = Map.of("scope", "global", "scope_id", "", "entity", "minecraft:zoglin",
                        "variant_name", "mud", "is_variant", "true");
                TableWriter.upsert(root.resolve(Schema.MobPathfinding.FILE), baseKey, Map.of("water", "-1", "lava", "4"));
                TableWriter.upsert(root.resolve(Schema.MobPathfinding.FILE), variantKey, Map.of("water", "0", "fence", "x"));
                TableWriter.upsert(root.resolve(Schema.MobLootXp.FILE), baseKey,
                        Map.of("drop_chance_mainhand", "1", "drop_chance_head", "-2", "drop_chance_body", "0.5"));
                TableWriter.upsert(root.resolve(Schema.MobFlags.FILE), baseKey, Map.of("scoreboard_tags", "a, b"));
                MobPropertyLayers layers = MobPropertyLayers.load(root);
                var base = layers.resolve("minecraft:zoglin", "", "", "");
                var mud = layers.resolve("minecraft:zoglin", "mud", "", "");
                boolean baseOk = base.pathfinding != null && Float.valueOf(-1f).equals(base.pathfinding.get("water"))
                        && Float.valueOf(4f).equals(base.pathfinding.get("lava"))
                        && Float.valueOf(1f).equals(base.dropChanceMainhand) && base.dropChanceHead == null
                        && Float.valueOf(0.5f).equals(base.dropChanceBody) && "a, b".equals(base.scoreboardTags);
                boolean layered = Float.valueOf(0f).equals(mud.pathfinding.get("water"))
                        && Float.valueOf(4f).equals(mud.pathfinding.get("lava")) && !mud.pathfinding.containsKey("fence")
                        && Float.valueOf(1f).equals(mud.dropChanceMainhand) && "a, b".equals(mud.scoreboardTags);
                boolean columns = Schema.PATH_TYPES.size() == 26 && Schema.mobProperties("pathfinding").size() == 26
                        && Schema.mobProperties("loot_xp").stream().filter(p -> p.column().startsWith("drop_chance_")).count() == 7
                        && Schema.mobProperty("flags", "scoreboard_tags") != null;
                if (!(baseOk && layered && columns)) {
                    lines.add("    MPC base=" + baseOk + " layered=" + layered + " columns=" + columns);
                }
                return baseOk && layered && columns;
            } finally {
                deleteRec(root);
            }
        });

        SelfTestHarness.check(lines, tally,
                "MP2 a tint reads RRGGBB or AARRGGBB, with # or 0x; anything else is no tint", () ->
                        Integer.valueOf(0xFF8800).equals(MobPropertyLayers.colour("#ff8800"))
                                && Integer.valueOf(0xFF8800).equals(MobPropertyLayers.colour("FF8800"))
                                && Integer.valueOf(0x80FF8800).equals(MobPropertyLayers.colour("0x80ff8800"))
                                && MobPropertyLayers.colour("orange") == null
                                && MobPropertyLayers.colour("12345") == null
                                && MobPropertyLayers.colour("") == null);

        SelfTestHarness.check(lines, tally,
                "MP3 Projectile falls reads true as 1 and false as 0, and keeps a number", () ->
                        MobPropertyLayers.gravity("true") == 1 && MobPropertyLayers.gravity("FALSE") == 0
                                && MobPropertyLayers.gravity("0.5") == 0.5);

        SelfTestHarness.check(lines, tally,
                "CA9 a replacement target cannot climb out of the pack with . or ..", () ->
                        com.strata.domain.CustomAssetsOps.targetProblem(com.strata.domain.CustomAssetsOps.TEXTURE,
                                "minecraft:textures/../../../evil.png") != null
                                && com.strata.domain.CustomAssetsOps.targetProblem(com.strata.domain.CustomAssetsOps.TEXTURE, "..:textures/a.png") != null
                                && com.strata.domain.CustomAssetsOps.targetProblem(com.strata.domain.CustomAssetsOps.TEXTURE,
                                "minecraft:textures/entity/zombie/zombie.png") == null);

        SelfTestHarness.check(lines, tally,
                "VP1 a Resolved with nothing set is empty, one with a value is not", () -> {
            var none = new com.strata.model.VariantProperties.Builder().build();
            var set = new com.strata.model.VariantProperties.Builder();
            set.reach(3.0);
            return none != com.strata.model.VariantProperties.Resolved.EMPTY && none.isEmpty()
                    && com.strata.model.VariantProperties.Resolved.EMPTY.isEmpty()
                    && !set.build().isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "EC1 a server's missing own-entity types are found and stored", () -> {
            Map<String, String> fromServer = new java.util.LinkedHashMap<>();
            fromServer.put("fire_zombie", "minecraft:zombie");
            fromServer.put("frost_skeleton", "minecraft:skeleton");
            fromServer.put("../evil", "minecraft:zombie");
            var missing = ServerEntityCache.missing(fromServer, java.util.Set.of("fire_zombie"));
            boolean found = missing.size() == 1 && missing.containsKey("frost_skeleton");
            Path cacheRoot = Files.createTempDirectory("strata-entity-cache");
            try {
                ServerEntityCache.store(cacheRoot, fromServer);
                var read = ServerEntityCache.read(cacheRoot);
                boolean stored = read.size() == 2 && "minecraft:skeleton".equals(read.get("frost_skeleton"))
                        && !read.containsKey("../evil");
                return found && stored;
            } finally {
                deleteRec(cacheRoot);
            }
        });

        SelfTestHarness.check(lines, tally,
                "RG1 regenerate updates old things once, removes turned-off ores, adds only new ones", () -> {
            boolean stamps = !com.strata.runtime.Regeneration.needsUpdate(0, 0)
                    && com.strata.runtime.Regeneration.needsUpdate(0, 1)
                    && !com.strata.runtime.Regeneration.needsUpdate(1, 1)
                    && com.strata.runtime.Regeneration.needsUpdate(1, 2);
            boolean removal = com.strata.runtime.Regeneration.removesOre("emerald", java.util.Set.of("iron"))
                    && !com.strata.runtime.Regeneration.removesOre("iron", java.util.Set.of("iron"))
                    && !com.strata.runtime.Regeneration.removesOre("emerald", null);
            var add = com.strata.runtime.Regeneration.oresToAdd(java.util.Set.of("iron", "emerald", "diamond"),
                    java.util.Set.of("iron", "diamond"), java.util.Set.of());
            var again = com.strata.runtime.Regeneration.oresToAdd(java.util.Set.of("emerald"),
                    java.util.Set.of(), java.util.Set.of("emerald"));
            return stamps && removal && add.equals(java.util.Set.of("emerald")) && again.isEmpty()
                    && com.strata.runtime.Regeneration.oresToAdd(null, java.util.Set.of(), java.util.Set.of()).isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "RD1 remove denies an inherited or game row, and deletes an own row", () -> {
            var own = new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 50, false);
            var game = new com.strata.domain.EquipmentOps.EquipmentRow("head", "minecraft:iron_helmet", 50, true);
            var ownLoot = new com.strata.domain.LootOps.LootRow("tier", "TEMP", "minecraft:zombie", "",
                    com.strata.model.OverrideMode.ADD, 0, 0, "minecraft:bone", 1, 1, 100, "", "", false, "");
            var gameLootRows = new com.strata.domain.LootOps.LootRow("tier", "TEMP", "minecraft:zombie", "",
                    com.strata.model.OverrideMode.ADD, 0, 0, "minecraft:bone", 1, 1, 100, "", "", true, "game");
            return !com.strata.domain.EquipmentOps.removeDenies(own)
                    && com.strata.domain.EquipmentOps.removeDenies(game)
                    && !com.strata.domain.LootOps.removeDenies(ownLoot)
                    && com.strata.domain.LootOps.removeDenies(gameLootRows);
        });

        write(dir, "variant_abilities_ab2.csv",
                "variant_name,scope,scope_id,ability,parameter,amount,interval_ticks,radius,enabled\n");
        for (String[] add : List.of(
                new String[]{"minecraft:regeneration", "0"},
                new String[]{"minecraft:speed", "1"},
                new String[]{"minecraft:regeneration", "2"})) {
            TableWriter.upsert(dir.resolve("variant_abilities_ab2.csv"),
                    com.strata.domain.AbilitiesOps.abilityKey(
                            "fire_zombie", "tier", "TEMP", "effect", add[0]),
                    Map.of("amount", add[1], "interval_ticks", "", "radius", "", "enabled", "true"));
        }
        SelfTestHarness.check(lines, tally,
                "AB2 two effects on one variant both stay, a repeat replaces its own row", () -> {
            var rows = CsvTable.read(dir.resolve("variant_abilities_ab2.csv"));
            if (rows.size() != 2) {
                lines.add("    AB2 expected 2 ability rows, found " + rows.size());
                return false;
            }
            boolean regenUpdated = rows.stream().anyMatch(r ->
                    "minecraft:regeneration".equals(r.get("parameter")) && "2".equals(r.get("amount")));
            var row = new com.strata.domain.AbilitiesOps.AbilityRow(
                    "effect", "minecraft:speed", "1", "", "", true);
            boolean match = com.strata.domain.AbilitiesOps.sameAbility(row, "effect", " minecraft:speed ")
                    && !com.strata.domain.AbilitiesOps.sameAbility(row, "effect", "minecraft:regeneration");
            return regenUpdated && match;
        });

        SelfTestHarness.check(lines, tally,
                "UI1 the tier and biome back ends check names and values", () -> {
            var existing = List.of("Tier 1", "Tier 2");
            boolean names = com.strata.domain.TierOps.nameProblem("", "", existing) != null
                    && com.strata.domain.TierOps.nameProblem("Tier 2", "Tier 1", existing) != null
                    && com.strata.domain.TierOps.nameProblem("Tier 1", "Tier 1", existing) == null
                    && com.strata.domain.TierOps.nameProblem("Tier 3", "", existing) == null;
            boolean rename = com.strata.domain.TierOps.isRename("Tier 1", "Tier 9")
                    && !com.strata.domain.TierOps.isRename("Tier 1", " Tier 1 ")
                    && !com.strata.domain.TierOps.isRename("", "Tier 9");
            boolean tierValues = com.strata.domain.TierOps.valuesProblem("x", "1") != null
                    && com.strata.domain.TierOps.valuesProblem("2", "NaN") != null
                    && com.strata.domain.TierOps.valuesProblem("", "") == null
                    && com.strata.domain.TierOps.valuesProblem("-1", "0.5") == null;
            boolean biomeValues = com.strata.domain.BiomeOps.valuesProblem("-1", "") != null
                    && com.strata.domain.BiomeOps.valuesProblem("", "1.") != null
                    && com.strata.domain.BiomeOps.valuesProblem("3", "1.5") == null
                    && com.strata.domain.BiomeOps.valuesProblem("", "") == null;
            return names && rename && tierValues && biomeValues;
        });

        SelfTestHarness.check(lines, tally,
                "UI2 condition choices come from the model and the evaluator accepts them", () -> {
            boolean words = com.strata.model.SpawnRule.WEATHER_WORDS
                    .containsAll(com.strata.model.SpawnRule.WEATHER_CHOICES)
                    && com.strata.model.SpawnRule.TIME_WORDS
                    .containsAll(com.strata.model.SpawnRule.TIME_CHOICES)
                    && com.strata.model.SpawnRule.SURFACE_WORDS
                    .containsAll(com.strata.model.SpawnRule.SURFACE_CHOICES);
            boolean types = com.strata.domain.SpawnRulesOps.TYPE_CHOICES.size()
                    == com.strata.model.SpawnRule.Type.values().length
                    && com.strata.domain.SpawnRulesOps.TYPE_CHOICES.stream().allMatch(
                            t -> com.strata.model.SpawnRule.Type.parse(t) != null);
            String[] fresh = com.strata.domain.SpawnRulesOps.newCondition(List.of());
            boolean newRowValid = com.strata.domain.SpawnRulesOps.problemWith(
                    java.util.List.<String[]>of(fresh)) == null;
            return words && types && newRowValid;
        });

        SelfTestHarness.check(lines, tally,
                "CN1 each added condition takes a new type, and a value is checked as typed", () -> {
            List<String[]> set = new ArrayList<>();
            java.util.Set<String> types = new java.util.HashSet<>();
            String[] next;
            while ((next = com.strata.domain.SpawnRulesOps.newCondition(set)) != null) {
                if (!types.add(next[0])) {
                    lines.add("    CN1 type offered twice: " + next[0]);
                    return false;
                }
                set.add(next);
                if (set.size() > com.strata.domain.SpawnRulesOps.TYPE_CHOICES.size()) {
                    return false;
                }
            }
            boolean allTypes = types.size() == com.strata.domain.SpawnRulesOps.TYPE_CHOICES.size();
            boolean values = com.strata.domain.SpawnRulesOps.valueProblem("light", "dark") != null
                    && com.strata.domain.SpawnRulesOps.valueProblem("light", " ") != null
                    && com.strata.domain.SpawnRulesOps.valueProblem("nonsense", "1") != null
                    && com.strata.domain.SpawnRulesOps.valueProblem("light", "0-7") == null
                    && com.strata.domain.SpawnRulesOps.valueProblem("time", "night") == null;
            return allTypes && values;
        });

        SelfTestHarness.check(lines, tally,
                "UI3 slot and ability choices are the names the runtime accepts", () -> {
            boolean slots = com.strata.domain.EquipmentOps.SLOT_CHOICES.equals(
                    List.of("mainhand", "offhand", "head", "chest", "legs", "feet"));
            var abilities = com.strata.domain.AbilitiesOps.abilityChoices();
            boolean abilityNames = abilities.stream().allMatch(a -> {
                if (com.strata.model.EffectClass.of(a) != null) {
                    return true;
                }
                String problem = com.strata.runtime.MobAbilities.rowProblem(
                        a, "x:y", "1", "20", "8");
                return problem == null || !problem.startsWith("unknown ability");
            });
            boolean odds = com.strata.domain.EquipmentOps.oddsPct(50, 50) == 50
                    && com.strata.domain.EquipmentOps.oddsPct(50, 200) == 25;
            return slots && abilityNames && odds;
        });

        SelfTestHarness.check(lines, tally,
                "UI4 the editor's open mode and the attribute check live in the back end", () -> {
            var addMob = com.strata.domain.VariantOps.EditorOpen.of(null, null, "tier", "T", true);
            var plainRow = com.strata.domain.VariantOps.EditorOpen.of(null, "minecraft:zombie", "tier", "T", true);
            var newVariant = com.strata.domain.VariantOps.EditorOpen.of(null, "minecraft:zombie", "tier", "T", false);
            var variantRow = com.strata.domain.VariantOps.EditorOpen.of("frosty", null, "tier", "T", false);
            boolean modes = addMob.isNew() && addMob.baseMobRow()
                    && !plainRow.isNew() && plainRow.baseMobRow()
                    && newVariant.isNew() && !newVariant.baseMobRow()
                    && !variantRow.isNew() && "frosty".equals(variantRow.originalName());
            boolean values = com.strata.domain.MobStatsOps.valueProblem("") != null
                    && com.strata.domain.MobStatsOps.valueProblem("NaN") != null
                    && com.strata.domain.MobStatsOps.valueProblem("x") != null
                    && com.strata.domain.MobStatsOps.valueProblem("20.5") == null;
            return modes && values;
        });

        SelfTestHarness.check(lines, tally,
                "UI5 curve shape follows the exponent, and the curve form is checked", () -> {
            boolean shape = "diminishing".equals(com.strata.domain.ScalingOps.shapeOf(0.9))
                    && "linear".equals(com.strata.domain.ScalingOps.shapeOf(1.0))
                    && "accelerating".equals(com.strata.domain.ScalingOps.shapeOf(1.2));
            var ok = new com.strata.domain.ScalingOps.CurveForm(
                    "minecraft:generic.max_health", "0.1", "0.6", "", true);
            boolean form = com.strata.domain.ScalingOps.formProblem(ok) == null
                    && com.strata.domain.ScalingOps.formProblem(
                            com.strata.domain.ScalingOps.NEW_CURVE) != null
                    && com.strata.domain.ScalingOps.formProblem(new com.strata.domain.ScalingOps.CurveForm(
                            "a:b", "NaN", "0.6", "", true)) != null
                    && com.strata.domain.ScalingOps.formProblem(new com.strata.domain.ScalingOps.CurveForm(
                            "a:b", "0.1", "0.6", "0", true)) != null;
            return shape && form;
        });

        write(dir, "variant_abilities_enabled.csv",
                "variant_name,scope,scope_id,ability,parameter,amount,interval_ticks,radius,enabled\n"
                        + "blank_on,,,sun_immune,,,,,\n"
                        + "set_off,,,sun_immune,,,,,false\n");
        com.strata.runtime.MobAbilities.load(dir.resolve("variant_abilities_enabled.csv"));
        boolean blankLoads = com.strata.runtime.MobAbilities.forVariant("blank_on").size() == 1;
        boolean offSkips = com.strata.runtime.MobAbilities.forVariant("set_off").isEmpty();
        write(dir, "variant_abilities_empty.csv",
                "variant_name,scope,scope_id,ability,parameter,amount,interval_ticks,radius,enabled\n");
        com.strata.runtime.MobAbilities.load(dir.resolve("variant_abilities_empty.csv"));
        SelfTestHarness.check(lines, tally,
                "EN1 a blank enabled cell is on in the abilities loader, false is off", () ->
                blankLoads && offSkips);

        SelfTestHarness.check(lines, tally,
                "PRF1 every failed profile Result has its own message, and OK has none", () -> {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (var result : ProfileManager.Result.values()) {
                String text = result.failureText();
                if (result == ProfileManager.Result.OK) {
                    if (text != null) return false;
                } else if (text == null || text.isBlank() || !seen.add(text)) {
                    lines.add("    PRF1 " + result + " has no distinct message");
                    return false;
                }
            }
            return true;
        });

        write(dir, "loot_variant_rename.csv",
                "scope,scope_id,entity,variant_name,mode,pool_index,entry_index,item,count_min,count_max,chance_pct,condition_type,condition_value\n"
                        + ",,minecraft:husk,husk_frosty,replace,0,0,ice_sword,1,1,50,,\n"
                        + "tier,TEMP,minecraft:husk,husk_frosty,replace,0,0,frost_sword,1,1,50,,\n");
        TableWriter.updateColumnWhere(dir.resolve("loot_variant_rename.csv"), Map.of(
                "variant_name", "husk_frosty", "scope", "tier", "scope_id", "TEMP"),
                "variant_name", "husk_northern");
        SelfTestHarness.check(lines, tally,
                "B3 a tier-scoped rename leaves global rows keyed under the old name", () -> {
            var rows = CsvTable.read(dir.resolve("loot_variant_rename.csv"));
            boolean globalKept = rows.stream().anyMatch(r ->
                    r.get("scope").isEmpty()
                            && r.get("variant_name").equals("husk_frosty"));
            boolean tierMoved = rows.stream().anyMatch(r ->
                    r.get("scope").equals("tier")
                            && r.get("variant_name").equals("husk_northern"));
            boolean tierOldGone = rows.stream().noneMatch(r ->
                    r.get("scope").equals("tier")
                            && r.get("variant_name").equals("husk_frosty"));
            return globalKept && tierMoved && tierOldGone && rows.size() == 2;
        });

        SelfTestHarness.check(lines, tally,
                "C2 condition keys keep variant rows isolated from base rows", () -> {
            Map<String, String> variant = com.strata.domain.SpawnRulesOps
                    .ruleKeys("tier", "Cold Steppe", "minecraft:creeper",
                            "frosty_creeper", "time");
            Map<String, String> base = com.strata.domain.SpawnRulesOps
                    .ruleKeys("tier", "Cold Steppe", "minecraft:creeper",
                            "", "time");
            return "frosty_creeper".equals(
                            variant.get(Schema.SpawnRules.VARIANT_NAME))
                    && "time".equals(variant.get(Schema.SpawnRules.RULE_TYPE))
                    && "".equals(base.get(Schema.SpawnRules.VARIANT_NAME))
                    && base.containsKey(Schema.SpawnRules.VARIANT_NAME);
        });

        SelfTestHarness.check(lines, tally,
                "C6 isSafeName bans Windows-illegal names and path escapes", () -> {
            if (!ProfileManager.isSafeName("cold_steppe")
                    || !ProfileManager.isSafeName("Frosty the Husk")) {
                return false;
            }
            String[] bad = {
                    "", null, ".", "..", "a/b", "a\\b",
                    "a<b", "a>b", "a:b", "a\"b", "a|b", "a?b", "a*b",
                    "trailing.", "trailing ",
                    "ctrl\u0001"};
            for (String n : bad) {
                if (ProfileManager.isSafeName(n)) {
                    return false;
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "A36.1 a variant name that would crash the registry is refused", () -> {
            String id = com.strata.runtime.VariantEntityRegistry.registryName("Frosty Husk!");
            var taken = List.of("frosty_husk");
            return id.equals("frosty_husk")
                    && com.strata.domain.VariantOps.nameProblem("Frosty Husk", taken) != null
                    && com.strata.domain.VariantOps.nameProblem("", List.of()) != null
                    && com.strata.domain.VariantOps.nameProblem("bad/name", List.of()) != null
                    && com.strata.domain.VariantOps.nameProblem("Frosty Husk", List.of("other")) == null;
        });

        SelfTestHarness.check(lines, tally,
                "A36.2 the spawns modifier sets a weight once, and can lower it", () -> {
            java.util.function.Function<String, int[]> biome = k -> k.equals("minecraft:zombie")
                    ? new int[]{100, 4, 4} : null;
            Spawn game = new Spawn(SpawnCategory.MONSTER, "minecraft:zombie", "", 100, 4, 4, true, null,
                    List.of(), com.strata.model.Source.GAME);
            Spawn lowered = new Spawn(SpawnCategory.MONSTER, "minecraft:zombie", "", 10, 2, 3, true, null,
                    List.of(), com.strata.model.Source.GLOBAL);
            Spawn frosty = new Spawn(SpawnCategory.MONSTER, "minecraft:zombie", "frosty", 5, 1, 6, true, null,
                    List.of(), com.strata.model.Source.GLOBAL);
            Spawn off = new Spawn(SpawnCategory.MONSTER, "minecraft:husk", "", 80, 1, 1, false, null,
                    List.of(), com.strata.model.Source.GLOBAL);
            var onlyGame = com.strata.model.SpawnPlan.plan(List.of(game), Spawn::entity, biome);
            var plan = com.strata.model.SpawnPlan.plan(List.of(game, lowered, frosty, off),
                    Spawn::entity, biome);
            var variantOnly = com.strata.model.SpawnPlan.plan(List.of(game, frosty), Spawn::entity, biome);
            return onlyGame.isEmpty()
                    && plan.get("minecraft:zombie").equals(new com.strata.model.SpawnPlan.Entry(15, 1, 1))
                    && plan.get("minecraft:husk").weight() == 0
                    && variantOnly.get("minecraft:zombie").equals(new com.strata.model.SpawnPlan.Entry(105, 1, 1));
        });

        SelfTestHarness.check(lines, tally,
                "A36.11 the game's spawn weights read without Strata's change", () -> {
            java.util.function.Function<String, String> key = s -> s.substring(0, s.indexOf('#'));
            List<String> live = List.of("zombie#15", "husk#80", "modmob#5");
            Map<String, List<String>> replaced = new java.util.LinkedHashMap<>();
            replaced.put("zombie", List.of("zombie#95", "zombie#5"));
            replaced.put("frosty", List.of());
            List<String> game = com.strata.model.SpawnPlan.unmodified(live, key, replaced);
            return game.equals(List.of("husk#80", "modmob#5", "zombie#95", "zombie#5"))
                    && com.strata.model.SpawnPlan.unmodified(live, key, Map.of()).equals(live);
        });

        SelfTestHarness.check(lines, tally,
                "A36.12 Save on a mob's AI edits the behavior it runs, never an own list", () -> {
            List<String> library = List.of("zombie", "tank");
            var family = new com.strata.domain.GoalsOps.Effective(List.of(), "its family's behavior 'zombie'", false,
                    "zombie");
            var chosen = new com.strata.domain.GoalsOps.Effective(List.of(), "the tier's behavior 'tank'", false,
                    "tank");
            var own = new com.strata.domain.GoalsOps.Effective(List.of(), "this mob's own list", true,
                    com.strata.domain.GoalsOps.OWN_LIST);
            var game = new com.strata.domain.GoalsOps.Effective(List.of(), "husk's own AI, from the game", false,
                    com.strata.domain.GoalsOps.GAME_GOALS);
            boolean intoGoalSet = com.strata.domain.GoalsOps.saveTarget(family, library, "zombie")
                    .equals(new com.strata.domain.GoalsOps.SaveTarget("zombie", false))
                    && com.strata.domain.GoalsOps.saveTarget(chosen, library, "zombie")
                    .equals(new com.strata.domain.GoalsOps.SaveTarget("tank", false));
            boolean named = com.strata.domain.GoalsOps.saveTarget(own, library, "zombie")
                    .equals(new com.strata.domain.GoalsOps.SaveTarget("zombie_2", true))
                    && com.strata.domain.GoalsOps.saveTarget(game, library, "husk")
                    .equals(new com.strata.domain.GoalsOps.SaveTarget("husk", true))
                    && com.strata.domain.GoalsOps.freeName("Frosty Husk!", library).equals("frosty_husk_")
                    && com.strata.domain.GoalsOps.nameProblem(
                            com.strata.domain.GoalsOps.freeName("Frosty Husk!", library), "", library) == null
                    && com.strata.domain.GoalsOps.mobName(Variant.defaultNameFor("minecraft:zombie"), "minecraft:zombie")
                    .equals("zombie")
                    && com.strata.domain.GoalsOps.mobName("frosty", "minecraft:zombie").equals("frosty");
            if (!(intoGoalSet && named)) {
                lines.add("    A36.12 into=" + intoGoalSet + " named=" + named + " free="
                        + com.strata.domain.GoalsOps.freeName("Frosty Husk!", library));
            }
            return intoGoalSet && named;
        });

        SelfTestHarness.check(lines, tally,
                "A37.1 Ctrl or Shift on a group picks every row under it, nested groups too", () -> {
            List<com.strata.client.widget.BrowseList.Node> nodes = List.of(
                    new com.strata.client.widget.BrowseList.Node("g:Monster", "Monster", "g:Monster", null),
                    new com.strata.client.widget.BrowseList.Node("0", "creeper", null, "g:Monster"),
                    new com.strata.client.widget.BrowseList.Node("g:Monster/Raider", "Raider", "g:Monster/Raider",
                            "g:Monster"),
                    new com.strata.client.widget.BrowseList.Node("1", "pillager", null, "g:Monster/Raider"),
                    new com.strata.client.widget.BrowseList.Node("g:Animal", "Animal", "g:Animal", null),
                    new com.strata.client.widget.BrowseList.Node("2", "cow", null, "g:Animal"));
            return com.strata.client.widget.BrowseList.rowsUnder(nodes, "g:Monster").equals(List.of("0", "1"))
                    && com.strata.client.widget.BrowseList.rowsUnder(nodes, "g:Monster/Raider").equals(List.of("1"))
                    && com.strata.client.widget.BrowseList.rowsUnder(nodes, "g:Animal").equals(List.of("2"));
        });

        SelfTestHarness.check(lines, tally,
                "A38.1 a Replace tier's biome keeps only its roster, in every category", () -> {
            List<String> plainsKeys = List.of("minecraft:cow", "minecraft:sheep", "minecraft:zombie", "minecraft:cow",
                    "minecraft:bat");
            java.util.Set<String> roster = java.util.Set.of("minecraft:zombie");
            boolean replaced = com.strata.model.SpawnPlan.dropped(plainsKeys, roster, true)
                    .equals(List.of("minecraft:cow", "minecraft:sheep", "minecraft:bat"));
            boolean added = com.strata.model.SpawnPlan.dropped(plainsKeys, roster, false).isEmpty();
            Spawn frosty = new Spawn(SpawnCategory.MONSTER, "minecraft:zombie", "frosty", 5, 1, 2, true, null,
                    List.of(), com.strata.model.Source.tier("t1"));
            boolean fromZero = com.strata.model.SpawnPlan.plan(List.of(frosty), Spawn::entity, k -> null)
                    .get("minecraft:zombie").weight() == 5;
            return replaced && added && fromZero;
        });

        SelfTestHarness.check(lines, tally,
                "A38.4 a Replace biome lets only its list's mobs spawn on their own; a player's spawn goes ahead", () -> {
            Spawn husk = Spawn.of(SpawnCategory.MONSTER, "minecraft:husk", 50, 1, 4,
                    com.strata.model.Source.tier("t1"));
            return !com.strata.model.SpawnPlan.allowedInReplace(true, true, List.of())
                    && com.strata.model.SpawnPlan.allowedInReplace(true, true, List.of(husk))
                    && com.strata.model.SpawnPlan.allowedInReplace(true, false, List.of())
                    && com.strata.model.SpawnPlan.allowedInReplace(false, true, List.of());
        });

        SelfTestHarness.check(lines, tally,
                "A41.5 substitution rolls only a natural spawn of a listed mob, in a configured biome", () -> {
            return com.strata.model.SpawnPlan.substitutes(true, true, true)
                    && !com.strata.model.SpawnPlan.substitutes(false, true, true)
                    && !com.strata.model.SpawnPlan.substitutes(true, false, true)
                    && !com.strata.model.SpawnPlan.substitutes(true, true, false);
        });

        SelfTestHarness.check(lines, tally,
                "A43.4 vegetation rules: On adds a missing plant, Off removes one, a biome's rule beats its tier's", () -> {
            Map<String, List<String>> game = Map.of("minecraft:desert", List.of("cactus", "dead_bush"),
                    "minecraft:plains", List.of("trees_plains", "grass"));
            var plan = com.strata.model.VegetationPlan.plan(Map.of("minecraft:desert", "t1", "minecraft:plains", "t1"),
                    Map.of("t1", Map.of("cactus", false, "trees_plains", true)),
                    Map.of("minecraft:plains", Map.of("trees_plains", false)), game);
            boolean desertPlan = plan.get("minecraft:desert").add().equals(List.of("trees_plains"))
                    && plan.get("minecraft:desert").remove().equals(List.of("cactus"));
            boolean plainsPlan = plan.get("minecraft:plains").add().isEmpty()
                    && plan.get("minecraft:plains").remove().equals(List.of("trees_plains"));
            List<Map<String, String>> table = List.of(
                    Map.of("scope", "tier", "scope_id", "t1", "feature", "cactus", "enabled", "false"),
                    Map.of("scope", "biome", "scope_id", "minecraft:desert", "feature", "flower", "enabled", "true"));
            var rows = com.strata.domain.VegetationOps.rowsFor("biome", "minecraft:desert",
                    List.of("cactus", "dead_bush", "flower"), List.of("cactus", "dead_bush"), table, "t1");
            var cactus = rows.stream().filter(r -> r.feature().equals("cactus")).findFirst().orElseThrow();
            var flower = rows.stream().filter(r -> r.feature().equals("flower")).findFirst().orElseThrow();
            boolean read = cactus.inherited() && !cactus.enabled() && flower.hasRule() && !flower.inherited()
                    && Boolean.FALSE.equals(flower.inGame());
            boolean write = com.strata.domain.VegetationOps.toWrite(rows, com.strata.domain.VegetationOps.VegetationState.DEFAULT)
                    .stream().map(com.strata.domain.VegetationOps.VegetationRow::feature).toList().equals(List.of("flower"));
            return desertPlan && plainsPlan && read && write;
        });

        SelfTestHarness.check(lines, tally,
                "A43.3 ores list by the dimensions they generate in; a biome shows its own", () -> {
            Map<String, List<String>> oresByBiome = Map.of(
                    "minecraft:plains", List.of("iron", "gold"),
                    "minecraft:nether_wastes", List.of("gold", "quartz"),
                    "somemod:cave", List.of("tin"));
            Map<String, String> dims = Map.of("minecraft:plains", "Overworld",
                    "minecraft:nether_wastes", "The Nether");
            java.util.Comparator<String> order = java.util.Comparator.comparing((String d) ->
                    List.of("Overworld", "The Nether", "Other").indexOf(d));
            var by = com.strata.domain.OreOps.dimensionsOf(List.of("iron", "gold", "quartz", "tin", "ruby"),
                    oresByBiome, dims, order);
            boolean each = by.get("iron").equals(List.of("Overworld"))
                    && by.get("gold").equals(List.of("Overworld", "The Nether"))
                    && by.get("quartz").equals(List.of("The Nether"))
                    && by.get("tin").equals(List.of("Other")) && by.get("ruby").equals(List.of("Other"));
            boolean tier = com.strata.domain.OreOps.headings(by, null, order)
                    .equals(List.of("Overworld", "The Nether", "Other"));
            boolean biome = com.strata.domain.OreOps.headings(by, "The Nether", order).equals(List.of("The Nether"));
            return each && tier && biome;
        });

    }

    private static void runTestsPart10(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "A45.1 a game armor piece keeps every number as a Equipment row: chance, Hard, drop, enchant, scaling", () -> {
            var zombie = com.strata.model.EquipmentFamilies.of("zombie");
            var rows = com.strata.domain.EquipmentOps.equipmentRows(List.of(), List.of(), List.of(),
                    com.strata.domain.DefaultEquipmentOps.gameEquipment(zombie));
            var gold = rows.stream().filter(r -> r.item().equals("minecraft:golden_helmet")).findFirst().orElseThrow();
            var cells = com.strata.domain.EquipmentOps.equipmentValues(gold);
            boolean row = gold.inherited() && gold.scaled() && gold.chancePct() > 0
                    && cells.get(Schema.Equipment.CHANCE_PCT).equals(
                            com.strata.domain.EquipmentOps.chanceText(gold.chancePct()))
                    && cells.get(Schema.Equipment.DROP_PCT).equals(DefaultEquipmentTable.number(zombie.dropChance()))
                    && cells.get(Schema.Equipment.ENCHANT_PCT).equals(DefaultEquipmentTable.number(zombie.armorEnchant()))
                    && "yes".equals(cells.get(Schema.Equipment.LOCAL_DIFFICULTY));
            var kept = com.strata.domain.EquipmentOps.gameEquipmentToKeep(null, "head", "minecraft:turtle_helmet", rows);
            boolean keep = kept.size() == rows.stream().filter(r -> r.slot().equals("head")).count()
                    && kept.stream().allMatch(r -> r.scaled() && !r.enchant().isBlank());
            return row && keep;
        });
        SelfTestHarness.check(lines, tally,
                "A46.1 Help splits into its sections whole, and a screen's section is found by its class", () -> {
            List<String> joined = new ArrayList<>();
            List<String> sections = com.strata.ui.screen.HelpLayout.sections();
            for (int i = 0; i < sections.size(); i++) {
                joined.addAll(com.strata.ui.screen.HelpLayout.sectionText(sections.get(i)));
                if (i < sections.size() - 1) {
                    joined.add("");
                }
            }
            boolean whole = sections.contains("Equipment")
                    && sections.getFirst().equals("Getting started")
                    && joined.stream().filter(l -> l.startsWith("# ")).count() == sections.size()
                    && sections.stream().allMatch(sec -> com.strata.ui.screen.HelpLayout.sectionText(sec).size() > 1);
            boolean found = "Equipment".equals(com.strata.ui.screen.HelpLayout.sectionFor("EquipmentScreen"))
                    && com.strata.ui.screen.HelpLayout.sectionFor("RegistryPickerScreen") == null
                    && com.strata.ui.screen.HelpLayout.sectionText("Equipment").getFirst().equals("# Equipment");
            var header = com.strata.ui.Layout.framed(new com.strata.ui.Frame(427, 240), s -> s.length() * 6,
                    "Strata ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â " + "a very long title ".repeat(10));
            var help = header.get("help");
            boolean corner = help != null && help.text().equals("?")
                    // Help at the far right, Exit left of it.
                    && help.box().x() + help.box().w() == new com.strata.ui.Frame(427, 240).contentRight()
                    && new com.strata.ui.Frame(427, 240).exit().x() + new com.strata.ui.Frame(427, 240).exit().w()
                    == help.box().x() - com.strata.ui.Grid.GAP
                    && header.get("title").box().x() + header.get("title").box().w() < help.box().x();
            return whole && found && corner;
        });
        SelfTestHarness.check(lines, tally,
                "A47.1 Remove and On / Off act on every picked row; several are named", () -> {
            java.util.function.Function<String, com.strata.model.GoalRow> row = goal ->
                    new com.strata.model.GoalRow(false, 1, goal, false, Map.of(),
                            com.strata.model.GoalCondition.ALWAYS, true);
            List<com.strata.model.GoalRow> rows = List.of(row.apply("FloatGoal"), row.apply("PanicGoal"),
                    row.apply("MeleeAttackGoal").withEnabled(false), row.apply("RandomStrollGoal"));
            var left = com.strata.domain.GoalsOps.withoutRows(rows, List.of(1, 3));
            boolean removed = left.size() == 2 && left.get(0).goal().equals("FloatGoal")
                    && left.get(1).goal().equals("MeleeAttackGoal");
            var off = com.strata.domain.GoalsOps.switchedRows(rows, List.of(1, 2));
            var on = com.strata.domain.GoalsOps.switchedRows(off, List.of(1, 2));
            boolean switched = !off.get(1).enabled() && !off.get(2).enabled() && off.get(0).enabled()
                    && on.get(1).enabled() && on.get(2).enabled()
                    && com.strata.domain.Picks.switchedOn(List.of(false))
                    && !com.strata.domain.Picks.switchedOn(List.of(true));
            boolean named = com.strata.domain.Picks.listed(List.of("a", "b")).equals("a, b")
                    && com.strata.domain.Picks.listed(List.of("1", "2", "3", "4", "5", "6", "7", "8"))
                    .equals("1, 2, 3, 4, 5, 6, and 2 more");
            boolean del = com.strata.ui.Keys.buttonsFor(com.strata.ui.Keys.action(com.strata.ui.Keys.DELETE,
                    false, false)).equals(List.of("remove"));
            return removed && switched && named && del;
        });

        SelfTestHarness.check(lines, tally,
                "A47.2 a double-click on a row presses Edit: two clicks on one row, close together", () -> {
            long at = 1_000;
            long gap = com.strata.ui.Keys.DOUBLE_CLICK_MS;
            return com.strata.ui.Keys.isDoubleClick("3", at, "3", at + gap)
                    && !com.strata.ui.Keys.isDoubleClick("3", at, "3", at + gap + 1)
                    && !com.strata.ui.Keys.isDoubleClick("3", at, "4", at + 10)
                    && !com.strata.ui.Keys.isDoubleClick(null, at, "3", at + 10)
                    && !com.strata.ui.Keys.isDoubleClick("3", at, "3", at - 10)
                    && "edit".equals(com.strata.ui.Keys.DOUBLE_CLICK);
        });

        SelfTestHarness.check(lines, tally,
                "A48.4 a table is parsed once while it stays the same, and every change is read", () -> {
            Path cacheDir = Files.createTempDirectory("strata-csv-cache");
            try {
                Path file = cacheDir.resolve("t.csv");
                Files.writeString(file, "a,b\n1,2\n");
                var first = CsvTable.read(file);
                first.get(0).put("a", "changed by a caller");
                boolean ownMaps = "1".equals(CsvTable.read(file).get(0).get("a"));
                TableWriter.upsert(file, Map.of("a", "1"), Map.of("b", "3"));
                boolean written = "3".equals(CsvTable.read(file).get(0).get("b"));
                String before = Files.readString(file);
                String same = before.replace("1,3", "1,4");
                Files.writeString(file, same);
                Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(file).toMillis() + 5_000));
                boolean outside = same.length() == before.length()
                        && "4".equals(CsvTable.read(file).get(0).get("b"));
                var a = CsvTable.read(file);
                var b = CsvTable.read(file);
                boolean cached = a.equals(b) && a.get(0) != b.get(0);
                if (!(ownMaps && written && outside && cached)) {
                    lines.add("    A48.4 ownMaps=" + ownMaps + " written=" + written + " outside=" + outside
                            + " cached=" + cached);
                    return false;
                }
                return true;
            } finally {
                deleteRec(cacheDir);
            }
        });

        SelfTestHarness.check(lines, tally,
                "A42.7 the editor key opens Strata in a world only for its host or an operator", () ->
                        com.strata.command.StrataUi.keyAllowed(true, false, false)
                                && com.strata.command.StrataUi.keyAllowed(false, true, false)
                                && com.strata.command.StrataUi.keyAllowed(false, false, true)
                                && !com.strata.command.StrataUi.keyAllowed(false, false, false));

        SelfTestHarness.check(lines, tally,
                "A42.5 a failed undo leaves the change whole; unrecorded work nests", () -> {
            EditHistory.clear();
            EditHistory.setActor(() -> "test");
            boolean[] reapplied = {false};
            EditHistory.record(new EditHistory.Step() {
                public boolean unchangedSince() { return true; }
                public boolean noChange() { return false; }
                public void restore() throws java.io.IOException { throw new java.io.IOException("disk"); }
            });
            EditHistory.record(new EditHistory.Step() {
                public boolean unchangedSince() { return true; }
                public boolean noChange() { return false; }
                public void restore() { }
                public void reapply() { reapplied[0] = true; }
            });
            EditHistory.close();
            String failed = EditHistory.undo();
            boolean whole = failed != null && reapplied[0] && EditHistory.canUndo();
            EditHistory.clear();
            EditHistory.whileRestoring(() -> {
                EditHistory.whileRestoring(() -> { });
                EditHistory.record(new EditHistory.Step() {
                    public boolean unchangedSince() { return true; }
                    public boolean noChange() { return false; }
                    public void restore() { }
                });
            });
            EditHistory.close();
            boolean nested = !EditHistory.canUndo();
            EditHistory.setActor(null);
            EditHistory.clear();
            return whole && nested;
        });

        SelfTestHarness.check(lines, tally,
                "A42.6 a scaling curve with no start level starts at level 2: level 1 is the game's values", () -> {
            var blank = new com.strata.domain.ScalingOps.CurveForm("minecraft:generic.max_health", "0.15", "0.6",
                    "", true);
            List<Double> b = com.strata.domain.ScalingOps.bonuses(blank);
            boolean preview = b != null && b.get(0) == 0.0 && b.get(1) > 0.0;
            boolean form = com.strata.domain.ScalingOps.NEW_CURVE.startLevel().equals("2");
            boolean runtime = com.strata.runtime.AttributeCurve.DEFAULT_START_LEVEL == 2
                    && com.strata.runtime.AttributeCurve.bonus(0.15, 0.6, 1, 2) == 0.0
                    && com.strata.runtime.AttributeCurve.bonus(0.15, 0.6, 2, 2) == 0.15;
            return preview && form && runtime;
        });

        SelfTestHarness.check(lines, tally,
                "A41.7 the AI editor says when a list cannot fight: no attack, or only a bow or crossbow", () -> {
            java.util.function.BiFunction<Boolean, String, com.strata.model.GoalRow> row = (who, goal) ->
                    new com.strata.model.GoalRow(who, 1, goal, false, Map.of(),
                            com.strata.model.GoalCondition.ALWAYS, true);
            var a = com.strata.model.Attacks.NONE;
            var hunts = row.apply(true, "NearestAttackableTargetGoal");
            var bow = row.apply(false, "RangedBowAttackGoal");
            var melee = row.apply(false, "MeleeAttackGoal");
            var none = a.line(List.of(hunts), true, 2.0);
            boolean missing = none.warning() && none.text().startsWith("This mob is missing an attack");
            var bowOnly = a.line(List.of(hunts, bow), true, 2.0);
            boolean needsBow = bowOnly.text().startsWith("Fights with: bow.") && bowOnly.text().contains("add melee attack");
            boolean offMelee = a.line(List.of(hunts, bow, melee.withEnabled(false)), true, 2.0).text()
                    .contains("add melee attack");
            var both = a.line(List.of(hunts, bow, melee), true, 2.0);
            boolean fights = !both.warning() && both.text().equals("Fights with: melee, bow. Can melee: attack damage 2.");
            var peaceful = a.line(List.of(row.apply(false, "PanicGoal")), true, null);
            boolean calm = !peaceful.warning() && peaceful.text().contains("Fights: no")
                    && peaceful.text().contains("Cannot melee: no attack damage");
            return missing && needsBow && offMelee && fights && calm;
        });

        SelfTestHarness.check(lines, tally,
                "A44.1 how a list fights reads the class chain: own attacks, the Fights cell, attack damage", () -> {
            java.util.function.BiFunction<String, String, GameLayer.Row> goal = (mob, value) ->
                    new GameLayer.Row(mob, GameLayer.GOAL, "0", value);
            String hunt = "t|2|NearestAttackableTargetGoal<TargetGoal|net.x.NearestAttackableTargetGoal|";
            String hold = "g|2|HoldGroundAttackGoal|net.x.Raider$HoldGroundAttackGoal|";
            String zombieHit = "g|2|ZombieAttackGoal<MeleeAttackGoal|net.x.ZombieAttackGoal|";
            String blazeHit = "g|4|BlazeAttackGoal|net.x.Blaze$BlazeAttackGoal|";
            String crossbow = "g|3|RangedCrossbowAttackGoal|net.x.RangedCrossbowAttackGoal|";
            List<GameLayer.Row> sheet = List.of(
                    goal.apply("minecraft:pillager", hunt), goal.apply("minecraft:pillager", hold),
                    goal.apply("minecraft:pillager", crossbow),
                    goal.apply("minecraft:evoker", hunt), goal.apply("minecraft:evoker", hold),
                    goal.apply("minecraft:zombie", hunt), goal.apply("minecraft:zombie", zombieHit),
                    goal.apply("minecraft:blaze", hunt), goal.apply("minecraft:blaze", blazeHit));
            var attacks = com.strata.domain.GoalsOps.attacks(sheet);
            java.util.function.Function<String, com.strata.model.GoalRow> game = text ->
                    com.strata.model.GoalRow.fromGame(com.strata.model.GameGoal.decode(text));
            var who = game.apply(hunt);
            boolean holdIsNot = attacks.column(List.of(who, game.apply(hold))).equals("missing")
                    && attacks.line(List.of(who, game.apply(hold)), true, 5.0).warning();
            boolean chain = attacks.column(List.of(who, game.apply(zombieHit))).equals("melee");
            boolean own = attacks.column(List.of(who, game.apply(blazeHit))).equals("own");
            boolean cell = attacks.column(List.of(who, game.apply(zombieHit), game.apply(crossbow)))
                    .equals("melee+crossbow")
                    && attacks.column(List.of(game.apply("g|1|FloatGoal|net.x.FloatGoal|"))).equals("no");
            boolean noDamage = attacks.line(List.of(who, game.apply(zombieHit)), true, null).warning();
            boolean noTarget = attacks.line(List.of(game.apply(zombieHit)), true, 3.0).warning();
            List<GameLayer.Row> mobs = List.of(
                    new GameLayer.Row("minecraft:zombie", GameLayer.ATTRIBUTE, com.strata.domain.GoalsOps.ATTACK_DAMAGE, "3.0"),
                    new GameLayer.Row("minecraft:cow", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "10.0"));
            boolean damage = Double.valueOf(3.0).equals(com.strata.domain.GoalsOps.attackDamage(mobs, "minecraft:zombie"))
                    && com.strata.domain.GoalsOps.attackDamage(mobs, "minecraft:cow") == null;
            if (!(holdIsNot && chain && own && cell && noDamage && noTarget && damage)) {
                lines.add("    A44.1 hold=" + holdIsNot + " chain=" + chain + " own=" + own + " cell=" + cell
                        + " noDamage=" + noDamage + " noTarget=" + noTarget + " damage=" + damage);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "A41.8 a Replace biome's hostile mobs take its empty slots and spawn in any light, by option", () -> {
            Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            counts.put("monster", 1);
            counts.put("creature", 0);
            counts.put("ambient", 0);
            counts.put("water_creature", 2);
            boolean open = com.strata.model.SpawnPlan.openSlots(c -> true, true, counts, "monster")
                    .equals(List.of("creature", "ambient"));
            boolean each = com.strata.model.SpawnPlan.openSlots(c -> !c.equals("creature"), true, counts, "monster")
                    .equals(List.of("ambient"));
            boolean shut = com.strata.model.SpawnPlan.openSlots(c -> false, true, counts, "monster").isEmpty()
                    && com.strata.model.SpawnPlan.openSlots(c -> true, false, counts, "monster").isEmpty();
            boolean light = com.strata.model.SpawnPlan.anyLight(true, true, true, true, true)
                    && !com.strata.model.SpawnPlan.anyLight(false, true, true, true, true)
                    && !com.strata.model.SpawnPlan.anyLight(true, false, true, true, true)
                    && !com.strata.model.SpawnPlan.anyLight(true, true, false, true, true)
                    && !com.strata.model.SpawnPlan.anyLight(true, true, true, false, true)
                    && !com.strata.model.SpawnPlan.anyLight(true, true, true, true, false);
            boolean options = java.util.Collections.disjoint(com.strata.domain.OptionsOps.OPTION_KEYS, List.of(
                    com.strata.domain.OptionsOps.MONSTERS_IN_CREATURE_SLOTS,
                    com.strata.domain.OptionsOps.MONSTERS_IN_AMBIENT_SLOTS,
                    com.strata.domain.OptionsOps.MONSTERS_IN_WATER_SLOTS,
                    com.strata.domain.OptionsOps.MONSTERS_OUTSIDE_DARKNESS))
                    && com.strata.data.StrataConfig.MONSTERS_IN_CREATURE_SLOTS.getDefault()
                    && com.strata.data.StrataConfig.MONSTERS_IN_AMBIENT_SLOTS.getDefault()
                    && com.strata.data.StrataConfig.MONSTERS_IN_WATER_SLOTS.getDefault()
                    && com.strata.data.StrataConfig.MONSTERS_OUTSIDE_DARKNESS.getDefault();
            return open && each && shut && light && options;
        });

        SelfTestHarness.check(lines, tally,
                "A39.2 an equipment item's real chances by difficulty follow the game's area factor", () -> {
            var G = com.strata.model.EquipmentOdds.class;
            boolean factor = com.strata.model.EquipmentOdds.factor(1.5) == 0 && com.strata.model.EquipmentOdds.factor(3) == 0.5
                    && com.strata.model.EquipmentOdds.factor(6.75) == 1;
            double[] normal = com.strata.model.EquipmentOdds.scaled(25, com.strata.model.EquipmentOdds.Level.NORMAL);
            double[] hard = com.strata.model.EquipmentOdds.scaled(25, com.strata.model.EquipmentOdds.Level.HARD);
            double[] easy = com.strata.model.EquipmentOdds.scaled(25, com.strata.model.EquipmentOdds.Level.EASY);
            boolean ranges = normal[0] == 0 && normal[1] == 25 && hard[0] == 3.125 && hard[1] == 25
                    && easy[1] == 0 && com.strata.model.EquipmentOdds.range(easy).equals("never")
                    && com.strata.model.EquipmentOdds.range(hard).equals("3.13% to 25%");
            List<String> piece = com.strata.model.EquipmentOdds.lines(false, 100, 100, 25, 8.5);
            boolean text = piece.get(0).equals("Carried: Easy 100%, Normal 100%, Hard 100%.")
                    && piece.get(1).equals("Enchanted when carried: Easy never, Normal 0% to 25%, Hard 3.13% to 25%.")
                    && piece.get(3).startsWith("Drops on death: 8.5%");
            if (!(factor && ranges && text)) {
                lines.add("    A39.2 " + piece);
            }
            return factor && ranges && text;
        });

        SelfTestHarness.check(lines, tally,
                "A39.4 every player attribute says what a higher number does, and a curve says what it scales by", () -> {
            List<String> player = List.of("attack_speed", "luck", "block_interaction_range",
                    "entity_interaction_range", "block_break_speed", "mining_efficiency", "sneaking_speed",
                    "submerged_mining_speed", "sweeping_damage_ratio");
            boolean words = player.stream().allMatch(a -> com.strata.model.AttributeGuide.knows("minecraft:player." + a)
                    && com.strata.model.AttributeGuide.of("minecraft:player." + a).what().length() > 60);
            boolean attackSpeed = com.strata.model.AttributeGuide.of("minecraft:generic.attack_speed").what()
                    .contains("attack bar");
            boolean curve = com.strata.model.AttributeGuide.scaled("minecraft:generic.max_health", true)
                    .contains("XP level")
                    && com.strata.model.AttributeGuide.scaled("minecraft:generic.max_health", false)
                    .contains("area's level");
            return words && attackSpeed && curve;
        });

        SelfTestHarness.check(lines, tally,
                "A38.6 Replace blocking other spawns is a global option a tier and a biome can each set", () -> {
            boolean order = com.strata.runtime.OtherSpawns.decide(true, null, null)
                    && !com.strata.runtime.OtherSpawns.decide(false, null, null)
                    && !com.strata.runtime.OtherSpawns.decide(true, false, null)
                    && com.strata.runtime.OtherSpawns.decide(false, true, null)
                    && com.strata.runtime.OtherSpawns.decide(false, false, true)
                    && !com.strata.runtime.OtherSpawns.decide(true, true, false);
            var tiers = com.strata.runtime.OtherSpawns.index(List.of(
                    Map.of(Schema.Tiers.NAME, "hell", Schema.Tiers.BLOCK_OTHER_SPAWNS, "false"),
                    Map.of(Schema.Tiers.NAME, "t2", Schema.Tiers.BLOCK_OTHER_SPAWNS, "")),
                    Schema.Tiers.NAME, Schema.Tiers.BLOCK_OTHER_SPAWNS);
            boolean indexed = tiers.equals(Map.of("hell", false));
            boolean words = com.strata.domain.TierOps.OTHER_SPAWN_CHOICES.stream().allMatch(w ->
                    com.strata.domain.TierOps.otherSpawnsWord(com.strata.domain.TierOps.otherSpawnsCell(w)).equals(w))
                    && com.strata.domain.TierOps.otherSpawnsCell("default").isEmpty();
            Map<String, Boolean> rules = com.strata.runtime.AreaRules.index(List.of(
                    Map.of(Schema.AreaRules.SCOPE, "tier", Schema.AreaRules.SCOPE_ID, "hell",
                            Schema.AreaRules.RULE, "block_other_spawns", Schema.AreaRules.VALUE, "false"),
                    Map.of(Schema.AreaRules.SCOPE, "biome", Schema.AreaRules.SCOPE_ID, "minecraft:plains",
                            Schema.AreaRules.RULE, "block_other_spawns", Schema.AreaRules.VALUE, "true")));
            boolean option = !com.strata.domain.OptionsOps.OPTION_KEYS
                    .contains(com.strata.domain.OptionsOps.BLOCK_OTHER_SPAWNS)
                    && !com.strata.runtime.AreaRules.decide(rules, "block_other_spawns", true, "hell", "minecraft:desert")
                    && com.strata.runtime.AreaRules.decide(rules, "block_other_spawns", false, "hell", "minecraft:plains")
                    && com.strata.runtime.AreaRules.decide(rules, "block_other_spawns", true, "other", null);
            return order && indexed && words && option;
        });

        SelfTestHarness.check(lines, tally,
                "A38.2 a dragged goal takes its new slot's priority, ties kept, and stays in its own list", () -> {
            List<com.strata.model.GoalRow> skeleton = List.of(builtRow(true, 1, "HurtByTargetGoal"),
                    builtRow(false, 4, "RangedBowAttackGoal"), builtRow(false, 5, "WaterAvoidingRandomStrollGoal"),
                    builtRow(false, 6, "LookAtPlayerGoal"), builtRow(false, 6, "RandomLookAroundGoal"));
            var up = com.strata.domain.GoalsOps.moved(skeleton, 2, 1);
            java.util.function.BiFunction<List<com.strata.model.GoalRow>, String, Integer> prio = (rs, g) ->
                    rs.stream().filter(r -> r.goal().equals(g)).findFirst().orElseThrow().priority();
            boolean swapped = up != null && prio.apply(up, "WaterAvoidingRandomStrollGoal") == 4
                    && prio.apply(up, "RangedBowAttackGoal") == 5 && prio.apply(up, "LookAtPlayerGoal") == 6;
            var down = com.strata.domain.GoalsOps.moved(skeleton, 1, 4);
            boolean last = down != null && prio.apply(down, "WaterAvoidingRandomStrollGoal") == 4
                    && prio.apply(down, "LookAtPlayerGoal") == 5 && prio.apply(down, "RangedBowAttackGoal") == 6
                    && down.getLast().goal().equals("RangedBowAttackGoal");
            boolean ownList = com.strata.domain.GoalsOps.moved(skeleton, 0, 1) == null
                    && com.strata.domain.GoalsOps.moved(skeleton, 2, 2) == null;
            if (!(swapped && last && ownList)) {
                lines.add("    A38.2 swapped=" + swapped + " last=" + last + " ownList=" + ownList + " down=" + down);
            }
            return swapped && last && ownList;
        });

        SelfTestHarness.check(lines, tally,
                "A38.3 the mob list's Batch takes every mob or any one of its groups, and says how many", () -> {
            var M = com.strata.domain.MobOps.class;
            List<com.strata.domain.MobOps.BatchMob> mobs = List.of(
                    new com.strata.domain.MobOps.BatchMob("global", "", "minecraft:cow", "", SpawnCategory.CREATURE),
                    new com.strata.domain.MobOps.BatchMob("global", "", "minecraft:zombie", "",
                            SpawnCategory.MONSTER),
                    new com.strata.domain.MobOps.BatchMob("global", "", "minecraft:bat", "", SpawnCategory.AMBIENT),
                    new com.strata.domain.MobOps.BatchMob("global", "", "minecraft:zombie", "frosty",
                            SpawnCategory.MONSTER));
            var groups = com.strata.domain.MobOps.batchGroups();
            java.util.function.Function<String, com.strata.domain.MobOps.BatchGroup> group = label ->
                    groups.stream().filter(g -> g.label().equals(label)).findFirst().orElseThrow();
            var passive = com.strata.domain.MobOps.inGroup(mobs, group.apply("Creature"));
            var ambient = com.strata.domain.MobOps.inGroup(mobs, group.apply("Ambient"));
            var hostile = com.strata.domain.MobOps.inGroup(mobs, group.apply("Monster"));
            var all = com.strata.domain.MobOps.inGroup(mobs, group.apply("All mobs"));
            boolean split = passive.stream().map(com.strata.domain.MobOps.BatchMob::entity).toList()
                    .equals(List.of("minecraft:cow")) && ambient.size() == 1 && hostile.size() == 2
                    && all.size() == 4;
            boolean everyGroup = groups.size() == SpawnCategory.values().length + 1
                    && groups.stream().skip(1).allMatch(g -> g.label().equals(g.category().word()));
            boolean empty = com.strata.domain.MobOps.batch(List.of(),
                    com.strata.domain.MobOps.BatchAction.SPAWN_OFF).refusal() != null;
            return split && everyGroup && empty;
        });

        SelfTestHarness.check(lines, tally,
                "A39.3 the Batch screen changes spawning, scaling, loot, conditions, abilities, or equipment, checked first", () -> {
            var F = com.strata.domain.BatchOps.Field.class;
            boolean fields = com.strata.domain.BatchOps.FIELDS.equals(List.of("Spawning", "Scaling", "Mob griefing",
                    "Category", "Loot", "Spawn rules", "Abilities", "Equipment", "Attributes", "Coefficient",
                    "Start level", "Passive", "Converts", "Boss"))
                    && com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.LOOT)
                    .equals(List.of("Add", "Remove"))
                    && com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.SPAWN_RULES)
                    .equals(List.of("Set", "Remove"))
                    && com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.ATTRIBUTES)
                    .equals(List.of("Set", "Remove"))
                    && com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.COEFFICIENT)
                    .equals(List.of("Set"))
                    && com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.START_LEVEL)
                    .equals(List.of("Set"))
                    && com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.BOSS)
                    .equals(List.of("On", "Off", "Default"))
                    && com.strata.domain.BatchOps.Field.of("Equipment") == com.strata.domain.BatchOps.Field.EQUIPMENT
                    && com.strata.domain.BatchOps.Field.of("Boss") == com.strata.domain.BatchOps.Field.BOSS;
            java.util.function.BiFunction<com.strata.domain.BatchOps.Field, String,
                    com.strata.domain.BatchOps.Request> req = (f, a) -> new com.strata.domain.BatchOps.Request(
                    f, a, "", "1", "2", "50", "mainhand", "time", "night", "", "", "1", "", "");
            var dropNoItem = req.apply(com.strata.domain.BatchOps.Field.LOOT, "Add");
            var drop = new com.strata.domain.BatchOps.Request(com.strata.domain.BatchOps.Field.LOOT, "Add",
                    "minecraft:bone", "1", "2", "50", "", "", "", "", "", "", "", "");
            var badCount = new com.strata.domain.BatchOps.Request(com.strata.domain.BatchOps.Field.LOOT, "Add",
                    "minecraft:bone", "3", "2", "50", "", "", "", "", "", "", "", "");
            var night = req.apply(com.strata.domain.BatchOps.Field.SPAWN_RULES, "Set");
            var badLight = new com.strata.domain.BatchOps.Request(com.strata.domain.BatchOps.Field.SPAWN_RULES,
                    "Set", "", "", "", "", "", "light", "dark", "", "", "", "", "");
            boolean checks = com.strata.domain.BatchOps.problem(dropNoItem) != null
                    && com.strata.domain.BatchOps.problem(drop) == null
                    && com.strata.domain.BatchOps.problem(badCount) != null
                    && com.strata.domain.BatchOps.problem(night) == null
                    && com.strata.domain.BatchOps.problem(badLight) != null
                    && com.strata.domain.BatchOps.problem(req.apply(com.strata.domain.BatchOps.Field.SPAWNING,
                    "Add")) != null;
            boolean words = com.strata.domain.BatchOps.question(drop, 3, "Monster", "Global")
                    .startsWith("Add the loot entry minecraft:bone (1 to 2, 50%) for 3 mob(s): Monster in Global?")
                    && com.strata.domain.BatchOps.describe(night).equals("set the spawn rule time to night");
            boolean refusesEmpty = com.strata.domain.BatchOps.apply(List.of(), drop).refusal() != null;
            if (!(fields && checks && words && refusesEmpty)) {
                lines.add("    A39.3 fields=" + fields + " checks=" + checks + " words=" + words
                        + " empty=" + refusesEmpty + " q=" + com.strata.domain.BatchOps.question(drop, 3, "Monster",
                        "Global"));
            }
            return fields && checks && words && refusesEmpty;
        });

        SelfTestHarness.check(lines, tally,
                "A37.4 a mob's own scaling switch overrides every other rule, and a variant inherits it", () -> {
            String zombie = Variant.defaultNameFor("minecraft:zombie");
            List<Map<String, String>> rows = List.of(
                    Map.of(Schema.Variants.NAME, zombie, Schema.Variants.SCOPE, "", Schema.Variants.SCOPE_ID, "",
                            Schema.Variants.SCALING, "off"),
                    Map.of(Schema.Variants.NAME, "frosty", Schema.Variants.SCOPE, "tier",
                            Schema.Variants.SCOPE_ID, "t1", Schema.Variants.SCALING, "ON "),
                    Map.of(Schema.Variants.NAME, "husk_x", Schema.Variants.SCOPE, "",
                            Schema.Variants.SCOPE_ID, "", Schema.Variants.SCALING, "maybe"));
            var switches = com.strata.runtime.MobScalingSwitch.index(rows);
            boolean off = !com.strata.runtime.MobScalingSwitch.scales(switches, true, zombie, "", "t1", null);
            boolean on = com.strata.runtime.MobScalingSwitch.scales(switches, false, "frosty", zombie, "t1", null);
            boolean inherited = !com.strata.runtime.MobScalingSwitch.scales(switches, true, "frosty", zombie, null, null);
            boolean defaults = com.strata.runtime.MobScalingSwitch.scales(switches, true, "husk_x", "", null, null)
                    && !com.strata.runtime.MobScalingSwitch.scales(switches, false, "husk_x", "", null, null)
                    && com.strata.runtime.MobScalingSwitch.own(switches, "husk_x", "", null, null) == null;
            boolean words = com.strata.domain.VariantOps.SWITCH_CHOICES.stream()
                    .allMatch(w -> com.strata.domain.VariantOps.switchWord(
                            com.strata.domain.VariantOps.switchStored(w)).equals(w));
            if (!(off && on && inherited && defaults && words)) {
                lines.add("    A37.4 off=" + off + " on=" + on + " inherited=" + inherited + " defaults=" + defaults
                        + " words=" + words);
            }
            return off && on && inherited && defaults && words;
        });

        SelfTestHarness.check(lines, tally,
                "A52.1 a mob's own Mob griefing overrides the game's rule both ways; a variant inherits it", () -> {
            String creeper = Variant.defaultNameFor("minecraft:creeper");
            List<Map<String, String>> rows = List.of(
                    Map.of(Schema.Variants.NAME, creeper, Schema.Variants.SCOPE, "", Schema.Variants.SCOPE_ID, "",
                            Schema.Variants.MOB_GRIEFING, "off", Schema.Variants.SCALING, "on"),
                    Map.of(Schema.Variants.NAME, "blaster", Schema.Variants.SCOPE, "tier",
                            Schema.Variants.SCOPE_ID, "t1", Schema.Variants.MOB_GRIEFING, "On"),
                    Map.of(Schema.Variants.NAME, "sheep_x", Schema.Variants.SCOPE, "",
                            Schema.Variants.SCOPE_ID, "", Schema.Variants.MOB_GRIEFING, ""));
            var switches = com.strata.runtime.MobGriefingSwitch.index(rows);
            boolean off = !com.strata.runtime.MobGriefingSwitch.canGrief(switches, true, creeper, "", null, null);
            boolean on = com.strata.runtime.MobGriefingSwitch.canGrief(switches, false, "blaster", creeper, "t1", null);
            boolean inherited = !com.strata.runtime.MobGriefingSwitch.canGrief(switches, true, "blaster", creeper,
                    null, null);
            boolean defaults = com.strata.runtime.MobGriefingSwitch.canGrief(switches, true, "sheep_x", "", null, null)
                    && !com.strata.runtime.MobGriefingSwitch.canGrief(switches, false, "sheep_x", "", null, null)
                    && switches.size() == 2
                    && com.strata.runtime.MobScalingSwitch.index(rows).equals(Map.of(
                    com.strata.runtime.ScopeIndex.key("", "", creeper), "on"));
            boolean batch = com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.MOB_GRIEFING)
                    .equals(com.strata.domain.VariantOps.SWITCH_CHOICES)
                    && com.strata.domain.BatchOps.Field.of("Mob griefing") == com.strata.domain.BatchOps.Field.MOB_GRIEFING;
            if (!(off && on && inherited && defaults && batch)) {
                lines.add("    A52.1 off=" + off + " on=" + on + " inherited=" + inherited + " defaults=" + defaults
                        + " batch=" + batch);
            }
            return off && on && inherited && defaults && batch;
        });

        SelfTestHarness.check(lines, tally,
                "A53.1 a mob's category: the list's words, stored as the type value, moved in the spawn lists", () -> {
            var C = com.strata.domain.CategoryOps.class;
            boolean words = com.strata.domain.CategoryOps.CHOICES.size() == com.strata.model.SpawnCategory.values().length + 2
                    && com.strata.domain.CategoryOps.CHOICES.get(0).equals(com.strata.domain.CategoryOps.SPECIAL_WORD)
                    && com.strata.domain.CategoryOps.CHOICES.stream().skip(1).allMatch(w ->
                    com.strata.domain.CategoryOps.word(com.strata.domain.CategoryOps.id(w)).equals(w))
                    && "monster".equals(com.strata.domain.CategoryOps.id("Monster"))
                    && "misc".equals(com.strata.domain.CategoryOps.id("Misc"))
                    && com.strata.domain.CategoryOps.id("Boss") == null
                    && com.strata.domain.CategoryOps.problem("minecraft:zombie", "Boss") != null
                    && com.strata.domain.CategoryOps.problem("minecraft:zombie", "Creature") == null;
            Map<String, String> edited = GameLayer.editedCategories(List.of(
                    Map.of("entity", "minecraft:zombie", "variant_name", "", "group", "type", "key", "category",
                            "value", "creature"),
                    Map.of("entity", "minecraft:husk", "variant_name", "hot_husk", "group", "type", "key", "category",
                            "value", "ambient"),
                    Map.of("entity", "minecraft:bat", "variant_name", "", "group", "type", "key", "width",
                            "value", "2"),
                    Map.of("entity", "minecraft:villager", "variant_name", "", "group", "type", "key", "category",
                            "value", "Misc")));
            boolean read = edited.equals(Map.of("minecraft:zombie", "creature", "minecraft:villager", "misc"));
            boolean moves = com.strata.model.SpawnPlan.listFor("minecraft:zombie", "monster", edited).equals("creature")
                    && com.strata.model.SpawnPlan.listFor("minecraft:zombie", "creature", edited).equals("creature")
                    && com.strata.model.SpawnPlan.listFor("minecraft:villager", "creature", edited).isEmpty()
                    && com.strata.model.SpawnPlan.listFor("minecraft:cow", "creature", edited).equals("creature");
            boolean batch = com.strata.domain.BatchOps.actionsFor(com.strata.domain.BatchOps.Field.CATEGORY)
                    .equals(com.strata.domain.CategoryOps.CHOICES);
            var target = new com.strata.domain.MobDataOps.Target("minecraft:zombie", "", "", "");
            boolean onePlace = com.strata.domain.MobDataOps.lockReason(target,
                    new com.strata.domain.MobDataOps.Value(GameLayer.TYPE, "category", "monster", "", "")) != null
                    && com.strata.domain.MobDataOps.lockReason(target,
                    new com.strata.domain.MobDataOps.Value(GameLayer.TYPE, "width", "0.6", "", "")) == null;
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            var in = new com.strata.ui.screen.MobEditLayout.Input("t", false, true, true, true, true, "", "", "",
                    "", "", List.of(), "", false, true, "", "", false, "Scaling: Default");
            boolean fits = com.strata.ui.screen.MobEditLayout.build(f, m, in).get("category") == null;
            if (!(words && read && moves && batch && onePlace && fits)) {
                lines.add("    A53.1 words=" + words + " read=" + read + " moves=" + moves + " batch=" + batch
                        + " onePlace=" + onePlace + " fits=" + fits + " edited=" + edited);
            }
            return words && read && moves && batch && onePlace && fits;
        });

        SelfTestHarness.check(lines, tally,
                "A63.1 item stats: the game's values by kind, yours on top, each typed value checked", () -> {
            String dmg = com.strata.model.ItemStats.attributeKey("minecraft:generic.attack_damage", "mainhand");
            List<GameLayer.Row> sheet = List.of(
                    new GameLayer.Row("minecraft:iron_sword", GameLayer.COMPONENT, "max_stack_size", "1"),
                    new GameLayer.Row("minecraft:iron_sword", GameLayer.COMPONENT, "max_damage", "250"),
                    new GameLayer.Row("minecraft:iron_sword", GameLayer.COMPONENT, dmg, "5"),
                    new GameLayer.Row("minecraft:iron_sword", GameLayer.NOTE, dmg, "add_value"),
                    new GameLayer.Row("minecraft:iron_sword", GameLayer.KIND, GameLayer.KIND, "Weapons"),
                    new GameLayer.Row("minecraft:bread", GameLayer.COMPONENT, "max_stack_size", "64"),
                    new GameLayer.Row("minecraft:bread", GameLayer.COMPONENT, "food.nutrition", "5"),
                    new GameLayer.Row("minecraft:bread", GameLayer.KIND, GameLayer.KIND, "Food"),
                    new GameLayer.Row("minecraft:stone", GameLayer.COMPONENT, "max_stack_size", "64"),
                    new GameLayer.Row("minecraft:stone", GameLayer.KIND, GameLayer.KIND, "Blocks"));
            Map<String, String> own = Map.of("minecraft:iron_sword|" + dmg, "7");
            var items = com.strata.domain.ItemStatsOps.items(sheet, own);
            boolean listed = items.size() == 3 && items.get(0).id().equals("minecraft:iron_sword")
                    && items.get(0).edited() == 1 && items.get(0).values() == 3
                    && items.get(1).kind().equals("Food") && items.get(2).kind().equals("Blocks");
            var sword = com.strata.domain.ItemStatsOps.values(sheet, own, "minecraft:iron_sword");
            var damage = sword.stream().filter(v -> v.key().equals(dmg)).findFirst().orElseThrow();
            var stack = sword.get(0);
            boolean layered = damage.effective().equals("7") && damage.game().equals("5")
                    && damage.note().equals("add_value")
                    && com.strata.domain.ItemStatsOps.name(damage).equals("Attack damage (mainhand)")
                    && com.strata.domain.ItemStatsOps.what(damage).contains("player's own attack damage");
            boolean checked = com.strata.domain.ItemStatsOps.problem(stack, "16", sword) != null
                    && com.strata.domain.ItemStatsOps.problem(stack, "1", sword) == null
                    && com.strata.model.ItemStats.problem("max_stack_size", "16", false) == null
                    && com.strata.model.ItemStats.problem("max_stack_size", "100", false) != null
                    && com.strata.model.ItemStats.problem("max_damage", "0", true) != null
                    && com.strata.model.ItemStats.problem("rarity", "Epic", false) == null
                    && com.strata.model.ItemStats.problem("rarity", "legendary", false) != null
                    && com.strata.model.ItemStats.problem("fire_resistant", "yes", false) != null
                    && com.strata.model.ItemStats.problem("food.eat_seconds", "0", false) != null
                    && com.strata.model.ItemStats.problem(dmg, "-2.5", false) == null
                    && com.strata.model.ItemStats.problem("tool.rule.0.speed", "12", false) == null
                    && com.strata.model.ItemStats.problem("food.nutrition", "", false) != null;
            boolean kinds = com.strata.model.ItemStats.kind(true, true, true, true, true).equals("Armor")
                    && com.strata.model.ItemStats.kind(false, true, true, false, false).equals("Weapons")
                    && com.strata.model.ItemStats.kind(false, false, true, false, false).equals("Tools")
                    && com.strata.model.ItemStats.kind(false, false, false, false, false).equals("Other")
                    && com.strata.model.ItemStats.ruleIndex("tool.rule.3.speed") == 3
                    && java.util.Arrays.equals(com.strata.model.ItemStats.attributeParts(dmg + "#2"),
                    new String[]{"minecraft:generic.attack_damage", "mainhand"});
            boolean menu = com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.contains("Items")
                    && com.strata.ui.screen.ListScreenLayouts.ITEM_HINT.contains("recipes");
            if (!(listed && layered && checked && kinds && menu)) {
                lines.add("    A63.1 listed=" + listed + " layered=" + layered + " checked=" + checked + " kinds=" + kinds
                        + " menu=" + menu);
            }
            return listed && layered && checked && kinds && menu;
        });

        SelfTestHarness.check(lines, tally,
                "A67.1 block stats: the game's values by kind, yours on top, each typed value checked", () -> {
            List<GameLayer.Row> sheet = List.of(
                    new GameLayer.Row("minecraft:stone", GameLayer.COMPONENT, "hardness", "1.5"),
                    new GameLayer.Row("minecraft:stone", GameLayer.COMPONENT, "blast_resistance", "6"),
                    new GameLayer.Row("minecraft:stone", GameLayer.KIND, GameLayer.KIND, "Stone and metal"),
                    new GameLayer.Row("minecraft:redstone_lamp", GameLayer.COMPONENT, "light_level", "15"),
                    new GameLayer.Row("minecraft:redstone_lamp", GameLayer.NOTE, "light_level", "LIT"),
                    new GameLayer.Row("minecraft:redstone_lamp", GameLayer.KIND, GameLayer.KIND, "Other"),
                    new GameLayer.Row("minecraft:iron_ore", GameLayer.COMPONENT, "hardness", "3"),
                    new GameLayer.Row("minecraft:iron_ore", GameLayer.KIND, GameLayer.KIND, "Ores"));
            Map<String, String> own = Map.of("minecraft:stone|hardness", "50");
            var blocks = com.strata.domain.BlockStatsOps.blocks(sheet, own);
            boolean listed = blocks.size() == 3 && blocks.get(0).id().equals("minecraft:iron_ore")
                    && blocks.get(1).id().equals("minecraft:stone") && blocks.get(1).edited() == 1
                    && blocks.get(1).values() == 2 && blocks.get(2).kind().equals("Other");
            var stone = com.strata.domain.BlockStatsOps.values(sheet, own, "minecraft:stone");
            var lamp = com.strata.domain.BlockStatsOps.values(sheet, own, "minecraft:redstone_lamp").get(0);
            boolean layered = stone.get(0).effective().equals("50") && stone.get(0).game().equals("1.5")
                    && stone.get(1).own().isEmpty()
                    && com.strata.domain.BlockStatsOps.name(stone.get(0)).equals("Hardness")
                    && com.strata.domain.BlockStatsOps.what(lamp).contains("lit states only");
            boolean checked = com.strata.model.BlockStats.problem("hardness", "-1") == null
                    && com.strata.model.BlockStats.problem("hardness", "-2") != null
                    && com.strata.model.BlockStats.problem("friction", "0.98") == null
                    && com.strata.model.BlockStats.problem("friction", "1.5") != null
                    && com.strata.model.BlockStats.problem("light_level", "15") == null
                    && com.strata.model.BlockStats.problem("light_level", "16") != null
                    && com.strata.model.BlockStats.problem("light_level", "7.5") != null
                    && com.strata.model.BlockStats.problem("requires_tool", "True") == null
                    && com.strata.model.BlockStats.problem("requires_tool", "yes") != null
                    && com.strata.model.BlockStats.problem("flammability", "301") != null
                    && com.strata.model.BlockStats.problem("speed_factor", "") != null
                    && com.strata.model.BlockStats.problem("color", "1") != null;
            boolean kinds = com.strata.model.BlockStats.kind(true, true, true, true, true).equals("Liquids")
                    && com.strata.model.BlockStats.kind(false, true, true, true, true).equals("Ores")
                    && com.strata.model.BlockStats.kind(false, false, false, true, true).equals("Wood")
                    && com.strata.model.BlockStats.kind(false, false, false, false, true).equals("Stone and metal")
                    && com.strata.model.BlockStats.kind(false, false, false, false, false).equals("Other");
            boolean menu = com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.contains("Blocks");
            if (!(listed && layered && checked && kinds && menu)) {
                lines.add("    A67.1 listed=" + listed + " layered=" + layered + " checked=" + checked + " kinds=" + kinds
                        + " menu=" + menu);
            }
            return listed && layered && checked && kinds && menu;
        });

        SelfTestHarness.check(lines, tally,
                "A68.1 block variants: listed by base kind, the base block's values under the variant's, names checked", () -> {
            List<Map<String, String>> variants = List.of(
                    Map.of("name", "reinforced_oak_door", "base_block", "minecraft:oak_door"),
                    Map.of("name", "hard_ore", "base_block", "minecraft:iron_ore"));
            List<Map<String, String>> values = List.of(
                    Map.of("variant", "reinforced_oak_door", "key", "hardness", "value", "10"),
                    Map.of("variant", "reinforced_oak_door", "key", "display_name", "value", "Reinforced Door"));
            var list = com.strata.domain.BlockVariantOps.variants(variants, values,
                    Map.of("minecraft:oak_door", "Wood", "minecraft:iron_ore", "Ores"), java.util.Set.of("hard_ore"));
            boolean listed = list.size() == 2 && list.get(0).name().equals("hard_ore") && list.get(0).registered()
                    && list.get(1).kind().equals("Wood") && list.get(1).values() == 2 && !list.get(1).registered();
            List<com.strata.domain.BlockStatsOps.Value> base = List.of(
                    new com.strata.domain.BlockStatsOps.Value("minecraft:oak_door", "hardness", "3", "", ""),
                    new com.strata.domain.BlockStatsOps.Value("minecraft:oak_door", "blast_resistance", "3", "5", ""));
            var sheet = com.strata.domain.BlockVariantOps.values("reinforced_oak_door", base, Map.of("hardness", "10"));
            boolean inherited = sheet.get(0).effective().equals("10") && sheet.get(0).game().equals("3")
                    && sheet.get(1).own().isEmpty() && sheet.get(1).effective().equals("5")
                    && sheet.get(0).block().equals("reinforced_oak_door");
            java.util.Set<String> blocks = java.util.Set.of("minecraft:oak_door", "minecraft:chest");
            boolean checked = com.strata.domain.BlockVariantOps.addProblem("strong_door", "minecraft:oak_door",
                    List.of("reinforced_oak_door"), blocks, null) == null
                    && com.strata.domain.BlockVariantOps.addProblem("reinforced_oak_door", "minecraft:oak_door",
                    List.of("reinforced_oak_door"), blocks, null) != null
                    && com.strata.domain.BlockVariantOps.addProblem("Bad Name", "minecraft:oak_door", List.of(), blocks, null) != null
                    && com.strata.domain.BlockVariantOps.addProblem("x", "", List.of(), blocks, null) != null
                    && com.strata.domain.BlockVariantOps.addProblem("x", "minecraft:nope", List.of(), blocks, null) != null
                    && com.strata.domain.BlockVariantOps.addProblem("x", "minecraft:chest", List.of(), blocks,
                    "a block with a block entity") != null
                    && com.strata.model.BlockVariant.problem("display_name", "x".repeat(51)) != null
                    && com.strata.model.BlockVariant.problem("hardness", "-1") == null
                    && com.strata.model.BlockVariant.words("reinforced_oak_door").equals("Reinforced Oak Door")
                    && com.strata.domain.BlockVariantOps.giveCommand("reinforced_oak_door").equals("give @s strata:reinforced_oak_door");
            if (!(listed && inherited && checked)) {
                lines.add("    A68.1 listed=" + listed + " inherited=" + inherited + " checked=" + checked);
            }
            return listed && inherited && checked;
        });

    }

    private static void runTestsPart11(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "A70.1 crafting recipes: the 3x3 grid as the game's recipe JSON, the game's recipes read with yours over them", () -> {
            String plank = "minecraft:oak_planks";
            String iron = "minecraft:iron_ingot";
            List<String> door = List.of(plank, iron, "", plank, iron, "", plank, plank, "");
            var shaped = com.strata.model.CraftingRecipe.json("shaped", door, "strata:reinforced_oak_door", 3);
            var pattern = shaped.getAsJsonArray("pattern");
            boolean trimmed = shaped.get("type").getAsString().equals("minecraft:crafting_shaped")
                    && pattern.size() == 3 && pattern.get(0).getAsString().equals("AB")
                    && pattern.get(2).getAsString().equals("AA")
                    && shaped.getAsJsonObject("key").getAsJsonObject("A").get("item").getAsString().equals(plank)
                    && shaped.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString().equals(iron)
                    && shaped.getAsJsonObject("result").get("id").getAsString().equals("strata:reinforced_oak_door")
                    && shaped.getAsJsonObject("result").get("count").getAsInt() == 3;
            var one = com.strata.model.CraftingRecipe.json("shaped", List.of("", "", "", "", iron, "", "", "", ""),
                    "minecraft:iron_nugget", 9);
            boolean single = one.getAsJsonArray("pattern").size() == 1
                    && one.getAsJsonArray("pattern").get(0).getAsString().equals("A");
            var loose = com.strata.model.CraftingRecipe.json("shapeless", door, "minecraft:stick", 1);
            boolean shapeless = loose.get("type").getAsString().equals("minecraft:crafting_shapeless")
                    && loose.getAsJsonArray("ingredients").size() == 6;
            List<String> empty = List.of("", "", "", "", "", "", "", "", "");
            boolean checked = com.strata.model.CraftingRecipe.problem("shaped", door, "minecraft:stick", "1") == null
                    && com.strata.model.CraftingRecipe.problem("shaped", empty, "minecraft:stick", "1") != null
                    && com.strata.model.CraftingRecipe.problem("shaped", door, "", "1") != null
                    && com.strata.model.CraftingRecipe.problem("shaped", door, "minecraft:stick", "0") != null
                    && com.strata.model.CraftingRecipe.problem("shaped", door, "minecraft:stick", "100") != null
                    && com.strata.model.CraftingRecipe.problem("round", door, "minecraft:stick", "1") != null
                    && com.strata.model.CraftingRecipe.nameProblem("reinforced_door", List.of()) == null
                    && com.strata.model.CraftingRecipe.nameProblem("Door!", List.of()) != null
                    && com.strata.model.CraftingRecipe.nameProblem("a", List.of("a")) != null;
            Map<String, String> row = new java.util.HashMap<>(Map.of("name", "b_recipe", "type", "shapeless",
                    "result", "minecraft:stick", "count", "4", "s4", plank));
            List<GameLayer.Row> game = List.of(
                    new GameLayer.Row("minecraft:stick", GameLayer.RECIPE, "type", "shaped"),
                    new GameLayer.Row("minecraft:stick", GameLayer.RECIPE, "result", "minecraft:stick"),
                    new GameLayer.Row("minecraft:stick", GameLayer.RECIPE, "count", "4"),
                    new GameLayer.Row("minecraft:stick", GameLayer.RECIPE, "s0", "#minecraft:planks"),
                    new GameLayer.Row("minecraft:stick", GameLayer.RECIPE, "s3", "#minecraft:planks"),
                    new GameLayer.Row("minecraft:torch", GameLayer.RECIPE, "type", "shaped"),
                    new GameLayer.Row("minecraft:firework_rocket", GameLayer.RECIPE, "type", "special"),
                    new GameLayer.Row("othermod:gear", GameLayer.RECIPE, "type", "shapeless"));
            Map<String, String> edited = new java.util.HashMap<>(Map.of("name", "minecraft:stick", "type", "shaped",
                    "result", "minecraft:stick", "count", "8", "s4", plank));
            var read = com.strata.domain.RecipeOps.recipes(List.of(row,
                    Map.of("name", "a_recipe", "type", "shaped", "result", "minecraft:torch", "count", "1"),
                    edited, Map.of("name", "minecraft:torch", "type", "off")), game);
            var byName = new java.util.HashMap<String, com.strata.domain.RecipeOps.Recipe>();
            read.forEach(r -> byName.put(r.name(), r));
            boolean listed = read.size() == 6 && read.get(0).name().equals("a_recipe")
                    && read.get(1).name().equals("b_recipe") && read.get(1).cells().get(4).equals(plank)
                    && read.get(1).cells().size() == 9 && read.get(1).cells().get(0).isEmpty()
                    && read.get(1).group().equals("Your recipes")
                    && byName.get("minecraft:stick").source() == com.strata.domain.RecipeOps.Source.EDITED
                    && byName.get("minecraft:stick").count().equals("8")
                    && byName.get("minecraft:torch").source() == com.strata.domain.RecipeOps.Source.OFF
                    && byName.get("othermod:gear").group().equals("othermod")
                    && com.strata.domain.RecipeOps.editProblem(byName.get("minecraft:firework_rocket")) != null
                    && com.strata.domain.RecipeOps.editProblem(byName.get("othermod:gear")) == null;
            var gameOnly = com.strata.domain.RecipeOps.recipes(List.of(), game);
            var stick = gameOnly.stream().filter(r -> r.name().equals("minecraft:stick")).findFirst().orElseThrow();
            boolean gameRead = stick.source() == com.strata.domain.RecipeOps.Source.GAME
                    && stick.cells().get(0).equals("#minecraft:planks") && stick.cells().get(3).equals("#minecraft:planks")
                    && stick.count().equals("4");
            var tagged = com.strata.model.CraftingRecipe.json("shaped", stick.cells(), "minecraft:stick", 4);
            boolean tagJson = tagged.getAsJsonObject("key").getAsJsonObject("A").get("tag").getAsString()
                    .equals("minecraft:planks")
                    && com.strata.model.CraftingRecipe.offJson().getAsJsonArray("neoforge:conditions").get(0)
                    .getAsJsonObject().get("type").getAsString().equals("neoforge:false")
                    && com.strata.model.CraftingRecipe.id("minecraft:stick", "strata").equals("minecraft:stick")
                    && com.strata.model.CraftingRecipe.id("reinforced_door", "strata").equals("strata:reinforced_door");
            listed = listed && gameRead && tagJson;
            if (!(trimmed && single && shapeless && checked && listed)) {
                lines.add("    A70.1 trimmed=" + trimmed + " single=" + single + " shapeless=" + shapeless
                        + " checked=" + checked + " listed=" + listed);
            }
            return trimmed && single && shapeless && checked && listed;
        });

        SelfTestHarness.check(lines, tally,
                "A73.1 the Special flag: Category's Special, its defaults with the villager, Spawn as only when flagged", () -> {
            Map<String, String> none = Map.of();
            boolean defaults = com.strata.model.SpecialSpawners.flagged(none, "minecraft:villager")
                    && com.strata.model.SpecialSpawners.flagged(none, "minecraft:phantom")
                    && !com.strata.model.SpecialSpawners.flagged(none, "minecraft:zombie")
                    && com.strata.model.SpecialSpawners.special(none, "minecraft:phantom")
                    && !com.strata.model.SpecialSpawners.special(none, "minecraft:villager");
            Map<String, String> phantomOff = Map.of("||flagged|minecraft:phantom", "false");
            Map<String, String> zombie = Map.of("||flagged|minecraft:zombie", "true", "||mobs|minecraft:zombie", "Special");
            boolean gated = !com.strata.model.SpecialSpawners.special(phantomOff, "minecraft:phantom")
                    && com.strata.model.SpecialSpawners.special(zombie, "minecraft:zombie")
                    && !com.strata.model.SpecialSpawners.special(Map.of("||mobs|minecraft:zombie", "Special"),
                    "minecraft:zombie")
                    && !com.strata.domain.SpecialSpawnsOps.specialMobs(phantomOff).contains("minecraft:phantom")
                    && com.strata.domain.SpecialSpawnsOps.flaggedMobs(zombie, List.of("minecraft:zombie",
                    "minecraft:skeleton")).equals(java.util.Set.of("minecraft:zombie"));
            boolean choices = com.strata.domain.CategoryOps.CHOICES.get(0).equals("Special")
                    && com.strata.domain.CategoryOps.problem("minecraft:zombie", "Special") == null
                    && com.strata.domain.CategoryOps.id("Special") == null;
            if (!(defaults && gated && choices)) {
                lines.add("    A73.1 defaults=" + defaults + " gated=" + gated + " choices=" + choices);
            }
            return defaults && gated && choices;
        });

        SelfTestHarness.check(lines, tally,
                "A74.1 a duplicate tier's name rules: a fresh name is taken, a blank and a taken one are refused", () -> {
            java.util.Collection<String> saved = java.util.List.of("overworld", "nether");
            String blank = com.strata.domain.TierOps.nameProblem("", "", saved);
            String spaces = com.strata.domain.TierOps.nameProblem("   ", "", saved);
            String taken = com.strata.domain.TierOps.nameProblem("overworld", "", saved);
            String fresh = com.strata.domain.TierOps.nameProblem("the_end", "", saved);
            String unchanged = com.strata.domain.TierOps.nameProblem("overworld", "overworld", saved);
            boolean bad = blank != null && spaces != null && taken != null && fresh == null && unchanged == null;
            if (!bad) {
                lines.add("    A74.1 blank=" + blank + " spaces=" + spaces + " taken=" + taken
                        + " fresh=" + fresh + " unchanged=" + unchanged);
            }
            return bad;
        });

        SelfTestHarness.check(lines, tally,
                "A74.2 a duplicate's name: _copy, then _copy_2, and on past every name already taken", () -> {
            java.util.List<String> one = java.util.List.of("overworld");
            java.util.List<String> two = java.util.List.of("overworld", "overworld_copy");
            java.util.List<String> three = java.util.List.of("overworld", "overworld_copy", "overworld_copy_2");
            String first = com.strata.domain.TierOps.copyName("overworld", one);
            String second = com.strata.domain.TierOps.copyName("overworld", two);
            String third = com.strata.domain.TierOps.copyName("overworld", three);
            boolean skipsSource = !java.util.List.of(first, second, third).contains("overworld");
            String lone = com.strata.domain.TierOps.copyName("the_end", java.util.List.of());
            boolean right = "overworld_copy".equals(first) && "overworld_copy_2".equals(second)
                    && "overworld_copy_3".equals(third) && "the_end_copy".equals(lone) && skipsSource;
            if (!right) {
                lines.add("    A74.2 first=" + first + " second=" + second + " third=" + third + " lone=" + lone);
            }
            return right;
        });

        SelfTestHarness.check(lines, tally,
                "A64.1 the player's base stats: the game's, yours on top; one home, the Options section", () -> {
            List<GameLayer.Row> sheet = List.of(
                    new GameLayer.Row("minecraft:player", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "20.0"),
                    new GameLayer.Row("minecraft:player", GameLayer.ATTRIBUTE, "minecraft:generic.movement_speed", "0.1"),
                    new GameLayer.Row("minecraft:zombie", GameLayer.ATTRIBUTE, "minecraft:generic.max_health", "20.0"));
            var values = com.strata.domain.PlayerStatsOps.merge(sheet, Map.of("minecraft:generic.max_health", "30.0"));
            boolean merged = values.size() == 2 && values.get(0).effective().equals("30.0")
                    && values.get(0).game().equals("20.0") && values.get(1).own().isEmpty()
                    && com.strata.domain.PlayerStatsOps.name(values.get(0)).equals("Health");
            boolean checked = com.strata.domain.PlayerStatsOps.problem(values.get(0), "0") != null
                    && com.strata.domain.PlayerStatsOps.problem(values.get(0), "40") == null
                    && com.strata.domain.PlayerStatsOps.problem(values.get(1), "0") == null
                    && com.strata.domain.PlayerStatsOps.problem(values.get(1), "fast") != null;
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            var mobTab = com.strata.ui.screen.ListScreenLayouts.scaling(f, m, "t", true, "Mob scaling: On", "Chunk scaling: Off", "", "", "");
            var playerTab = com.strata.ui.screen.ListScreenLayouts.scaling(f, m, "t", false, "Player scaling: On", null, "", "", "");
            boolean button = playerTab.get("base") == null && mobTab.get("base") == null
                    && com.strata.ui.screen.Sections.ENTRIES.get("Options").contains("Player base stats");
            if (!(merged && checked && button)) {
                lines.add("    A64.1 merged=" + merged + " checked=" + checked + " button=" + button);
            }
            return merged && checked && button;
        });


        SelfTestHarness.check(lines, tally,
                "A74.3 a group: made from its name, one kind, and the names refused", () -> {
            Path root = Files.createDirectories(dir.resolve("a743"));
            write(root, Schema.Groups.FILE, GROUPS_HEADER);
            boolean made = com.strata.domain.GroupOps.create("Undead Rot", "abilities", root).ok();
            boolean loot = com.strata.domain.GroupOps.create("Rich Haul", "loot", root).ok();
            List<String> abilities = com.strata.domain.GroupOps.names("abilities", root);
            List<String> loots = com.strata.domain.GroupOps.names("loot", root);
            boolean apart = abilities.equals(List.of("Undead Rot"))
                    && loots.equals(List.of("Rich Haul"));
            boolean ids = "undead_rot".equals(com.strata.domain.GroupOps.idFor("Undead Rot"))
                    && "undead_rot".equals(com.strata.domain.GroupOps.idFor("  Undead   Rot  "))
                    && "rich_haul".equals(com.strata.domain.GroupOps.idFor("Rich Haul"))
                    && "frosty_husks_2".equals(com.strata.domain.GroupOps.idFor("Frosty Husks (2)"))
                    && "".equals(com.strata.domain.GroupOps.idFor("!!!"));
            boolean refused = com.strata.domain.GroupOps.create("   ", "abilities", root).refusal() != null
                    && com.strata.domain.GroupOps.create("Whatever", "quests", root).refusal() != null
                    && com.strata.domain.GroupOps.create("!!!", "abilities", root).refusal() != null
                    && com.strata.domain.GroupOps.create("Undead Rot", "abilities", root).refusal() != null
                    && com.strata.domain.GroupOps.create("undead rot", "abilities", root).refusal() != null;
            boolean onlyGroups = !Files.exists(root.resolve(Schema.Abilities.FILE));
            if (!(made && loot && apart && ids && refused && onlyGroups)) {
                lines.add("    A74.3 made=" + made + " loot=" + loot + " apart=" + apart
                        + " ids=" + ids + " refused=" + refused + " onlyGroups=" + onlyGroups
                        + " abilities=" + abilities);
            }
            return made && loot && apart && ids && refused && onlyGroups;
        });

        SelfTestHarness.check(lines, tally,
                "A74.4 a group applied: the target's list grows by exactly the group's rows", () -> {
            Path root = groupFixture(dir, "a744", "Undead Rot");
            List<String> before = ownAbilities(root, "frost_husk");
            boolean applied = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "frost_husk", "global", "", root).ok();
            List<String> after = ownAbilities(root, "frost_husk");
            List<String> stillGroups = ownAbilities(root, "undead_rot");
            boolean sourceHeld = stillGroups.equals(List.of("effect/poison=4", "aura_mobs/slime=8"));
            boolean addedTwo = after.size() == before.size() + 2 && before.size() == 1
                    && after.containsAll(before)
                    && after.contains("effect/poison=4") && after.contains("aura_mobs/slime=8");
            if (!(applied && sourceHeld && addedTwo)) {
                lines.add("    A74.4 applied=" + applied + " sourceHeld=" + sourceHeld
                        + " addedTwo=" + addedTwo + " before=" + before + " after=" + after
                        + " group=" + stillGroups);
            }
            return applied && sourceHeld && addedTwo;
        });

        SelfTestHarness.check(lines, tally,
                "A74.5 the target has priority: changing a row in the target leaves the group alone", () -> {
            Path root = groupFixture(dir, "a745", "Undead Rot");
            com.strata.domain.GroupOps.applyAbilities("undead_rot", "frost_husk", "global", "", root);
            TableWriter.upsert(root.resolve(Schema.Abilities.FILE),
                    com.strata.domain.AbilitiesOps.abilityKey("frost_husk", "global", "", "effect", "poison"),
                    Map.of(Schema.Abilities.AMOUNT, "99"));
            boolean targetChanged = ownAbilities(root, "frost_husk").contains("effect/poison=99");
            boolean groupHeld = ownAbilities(root, "undead_rot")
                    .equals(List.of("effect/poison=4", "aura_mobs/slime=8"));
            if (!(targetChanged && groupHeld)) {
                lines.add("    A74.5 targetChanged=" + targetChanged + " groupHeld=" + groupHeld
                        + " group=" + ownAbilities(root, "undead_rot"));
            }
            return targetChanged && groupHeld;
        });

        SelfTestHarness.check(lines, tally,
                "A74.6 the copy is not a link: changing the group leaves an applied target alone", () -> {
            Path root = groupFixture(dir, "a746", "Undead Rot");
            com.strata.domain.GroupOps.applyAbilities("undead_rot", "frost_husk", "global", "", root);
            TableWriter.upsert(root.resolve(Schema.Abilities.FILE),
                    com.strata.domain.AbilitiesOps.abilityKey("undead_rot", "global", "", "effect", "poison"),
                    Map.of(Schema.Abilities.AMOUNT, "40"));
            boolean groupChanged = ownAbilities(root, "undead_rot").contains("effect/poison=40");
            boolean targetHeld = ownAbilities(root, "frost_husk").contains("effect/poison=4")
                    && !ownAbilities(root, "frost_husk").contains("effect/poison=40");
            boolean freshTakesNew = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "hollow_husk", "global", "", root).ok()
                    && ownAbilities(root, "hollow_husk").contains("effect/poison=40");
            if (!(groupChanged && targetHeld && freshTakesNew)) {
                lines.add("    A74.6 groupChanged=" + groupChanged + " targetHeld=" + targetHeld
                        + " freshTakesNew=" + freshTakesNew);
            }
            return groupChanged && targetHeld && freshTakesNew;
        });

        SelfTestHarness.check(lines, tally,
                "A74.7 applying twice: the row the list already has is kept, not overwritten", () -> {
            Path root = groupFixture(dir, "a747", "Undead Rot");
            TableWriter.upsert(root.resolve(Schema.Abilities.FILE),
                    com.strata.domain.AbilitiesOps.abilityKey("frost_husk", "global", "", "effect", "poison"),
                    Map.of(Schema.Abilities.AMOUNT, "99"));
            List<com.strata.domain.AbilitiesOps.AbilityRow> mine = List.of(
                    new com.strata.domain.AbilitiesOps.AbilityRow("effect", "poison", "99", "", "", true));
            List<com.strata.domain.AbilitiesOps.AbilityRow> source = List.of(
                    new com.strata.domain.AbilitiesOps.AbilityRow("effect", "poison", "4", "", "", true),
                    new com.strata.domain.AbilitiesOps.AbilityRow("aura_mobs", "slime", "8", "", "", true));
            com.strata.domain.GroupOps.Added said =
                    com.strata.domain.GroupOps.counts(source, mine);
            boolean counted = said.toAdd() == 1 && said.alreadyThere() == 1
                    && said.said().contains("1 ability") && said.said().contains("keeps your 1");
            boolean applied = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "frost_husk", "global", "", root).ok();
            List<String> after = ownAbilities(root, "frost_husk");
            boolean kept = after.contains("effect/poison=99") && !after.contains("effect/poison=4")
                    && after.contains("aura_mobs/slime=8");
            boolean twice = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "frost_husk", "global", "", root).ok()
                    && ownAbilities(root, "frost_husk").equals(after);
            if (!(counted && applied && kept && twice)) {
                lines.add("    A74.7 counted=" + counted + " said=" + said.said() + " kept=" + kept
                        + " twice=" + twice + " after=" + after);
            }
            return counted && applied && kept && twice;
        });

        SelfTestHarness.check(lines, tally,
                "A74.8 a group applied to itself is refused (was drift.40)", () -> {
            Path root = groupFixture(dir, "a748", "Undead Rot");
            var refused = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "undead_rot", "global", "", root);
            boolean said = refused.refusal() != null
                    && refused.refusal().contains("cannot be applied to itself");
            boolean blankScope = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "undead_rot", "", "", root).refusal() != null;
            boolean allowed = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "frost_husk", "global", "", root).ok();
            boolean scoped = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "undead_rot", "tier", "T1", root).ok();
            boolean noRows = ownAbilitiesIn(root, "undead_rot", "").size() == 2
                    && ownAbilities(root, "frost_husk").size() == 3;
            if (!(said && blankScope && allowed && scoped && noRows)) {
                lines.add("    A74.8 said=" + said + " blankScope=" + blankScope + " allowed=" + allowed
                        + " scoped=" + scoped + " noRows=" + noRows + " refusal=" + refused.refusal());
            }
            return said && blankScope && allowed && scoped && noRows;
        });

        SelfTestHarness.check(lines, tally,
                "A74.9 a group of the wrong kind is refused, naming both (was drift.41)", () -> {
            Path root = Files.createDirectories(dir.resolve("a749"));
            write(root, Schema.Groups.FILE, GROUPS_HEADER);
            write(root, Schema.Abilities.FILE, ABILITIES_HEADER);
            com.strata.domain.GroupOps.create("Rich Haul", "loot", root);
            com.strata.domain.GroupOps.create("Undead Rot", "abilities", root);
            var refused = com.strata.domain.GroupOps
                    .applyAbilities("rich_haul", "frost_husk", "global", "", root);
            boolean said = refused.refusal() != null && refused.refusal().contains("Rich Haul")
                    && refused.refusal().contains("loot") && refused.refusal().contains("abilities");
            boolean clean = ownAbilities(root, "frost_husk").isEmpty();
            boolean allowed = com.strata.domain.GroupOps
                    .applyAbilities("undead_rot", "frost_husk", "global", "", root).ok()
                    && ownAbilities(root, "frost_husk").isEmpty();
            if (!(said && clean && allowed)) {
                lines.add("    A74.9 said=" + said + " clean=" + clean + " allowed=" + allowed
                        + " refusal=" + refused.refusal());
            }
            return said && clean && allowed;
        });

        SelfTestHarness.check(lines, tally,
                "A74.10 a name that names no group is refused (was drift.39)", () -> {
            Path root = groupFixture(dir, "a7410", "Undead Rot");
            var refused = com.strata.domain.GroupOps
                    .applyAbilities("No Such Group", "frost_husk", "global", "", root);
            boolean said = refused.refusal() != null
                    && refused.refusal().contains("No Such Group");
            boolean blank = com.strata.domain.GroupOps
                    .applyAbilities("   ", "frost_husk", "global", "", root).refusal() != null;
            boolean byName = com.strata.domain.GroupOps
                    .applyAbilities("Undead Rot", "frost_husk", "global", "", root).refusal() != null
                    && com.strata.domain.GroupOps.countsAbilities("Undead Rot", "frost_husk",
                    "global", "", root).toAdd() == 0;
            boolean clean = ownAbilities(root, "frost_husk").equals(List.of("effect/speed=1"));
            if (!(said && blank && byName && clean)) {
                lines.add("    A74.10 said=" + said + " blank=" + blank + " byName=" + byName
                        + " clean=" + clean + " refusal=" + refused.refusal());
            }
            return said && blank && byName && clean;
        });

        SelfTestHarness.check(lines, tally,
                "A74.11 renaming a group keeps its id, so the rows it holds stay reachable", () -> {
            Path root = groupFixture(dir, "a7411", "Undead Rot");
            boolean renamed = com.strata.domain.GroupOps.rename("undead_rot", "Rotting Ones", root).ok();
            var group = com.strata.domain.GroupOps.find("undead_rot", root);
            boolean idHeld = group != null && "Rotting Ones".equals(group.name())
                    && "abilities".equals(group.kind())
                    && com.strata.domain.GroupOps.findByName("Rotting Ones", root) != null
                    && com.strata.domain.GroupOps.findByName("Undead Rot", root) == null;
            boolean rowsHeld = ownAbilities(root, "undead_rot").size() == 2
                    && com.strata.domain.GroupOps.applyAbilities("undead_rot", "frost_husk",
                    "global", "", root).ok()
                    && ownAbilities(root, "frost_husk").size() == 3
                    && ownAbilities(root, "frost_husk").containsAll(
                    List.of("effect/poison=4", "aura_mobs/slime=8"));
            boolean sameName = com.strata.domain.GroupOps.rename("undead_rot", "Rotting Ones", root).ok();
            com.strata.domain.GroupOps.create("Rich Haul", "loot", root);
            boolean clash = com.strata.domain.GroupOps.rename("undead_rot", "Rich Haul", root).refusal() != null;
            boolean missing = com.strata.domain.GroupOps.rename("nothing", "Anything", root).refusal() != null;
            if (!(renamed && idHeld && rowsHeld && sameName && clash && missing)) {
                lines.add("    A74.11 renamed=" + renamed + " idHeld=" + idHeld + " rowsHeld=" + rowsHeld
                        + " sameName=" + sameName + " clash=" + clash + " missing=" + missing);
            }
            return renamed && idHeld && rowsHeld && sameName && clash && missing;
        });

        SelfTestHarness.check(lines, tally,
                "A74.12 deleting a group takes the rows it holds, and only those", () -> {
            Path root = groupFixture(dir, "a7412", "Undead Rot");
            com.strata.domain.GroupOps.create("Rich Haul", "loot", root);
            TableWriter.upsert(root.resolve(Schema.Abilities.FILE),
                    com.strata.domain.AbilitiesOps.abilityKey("rich_haul", "global", "", "effect", "hunger"),
                    Map.of(Schema.Abilities.AMOUNT, "2"));
            TableWriter.upsert(root.resolve(Schema.Abilities.FILE),
                    com.strata.domain.AbilitiesOps.abilityKey("frost_husk", "global", "", "effect", "speed"),
                    Map.of(Schema.Abilities.AMOUNT, "1"));
            boolean deleted = com.strata.domain.GroupOps.delete("undead_rot", root).ok();
            boolean gone = com.strata.domain.GroupOps.find("undead_rot", root) == null
                    && ownAbilities(root, "undead_rot").isEmpty();
            boolean othersHeld = ownAbilities(root, "rich_haul").size() == 1
                    && ownAbilities(root, "frost_husk").size() == 1
                    && com.strata.domain.GroupOps.find("rich_haul", root) != null;
            boolean missing = com.strata.domain.GroupOps.delete("undead_rot", root).refusal() != null;
            if (!(deleted && gone && othersHeld && missing)) {
                lines.add("    A74.12 deleted=" + deleted + " gone=" + gone
                        + " othersHeld=" + othersHeld + " missing=" + missing);
            }
            return deleted && gone && othersHeld && missing;
        });

        SelfTestHarness.check(lines, tally,
                "A74.13 a drop group applied: the variant's drops grow, retargeted, on fresh pools", () -> {
            Path root = lootGroupFixture(dir, "a7413", "Rot Drops");
            String id = com.strata.domain.GroupOps.idFor("Rot Drops");
            com.strata.domain.GroupOps.Added before = com.strata.domain.GroupOps
                    .countsLoot(id, "minecraft:strider", "frost_husk", "global", "", root);
            boolean applied = com.strata.domain.GroupOps
                    .applyLoot(id, "minecraft:strider", "frost_husk", "global", "", root).ok();
            List<String> after = ownLoot(root, "frost_husk");
            List<String> group = ownLoot(root, id);
            boolean grew = after.size() == 3 && before.toAdd() == 1 && before.alreadyThere() == 1;
            boolean retargeted = after.contains("minecraft:gunpowder@2@minecraft:strider")
                    && after.contains("minecraft:string@0@minecraft:strider")
                    && after.contains("minecraft:rotten_flesh@1@minecraft:strider");
            boolean sourceHeld = group.size() == 2
                    && group.containsAll(List.of("minecraft:rotten_flesh@0@", "minecraft:gunpowder@1@"));
            com.strata.domain.GroupOps.Added after2 = com.strata.domain.GroupOps
                    .countsLoot(id, "minecraft:strider", "frost_husk", "global", "", root);
            boolean settled = after2.toAdd() == 0 && after2.alreadyThere() == 2;
            if (!(applied && grew && retargeted && sourceHeld && settled)) {
                lines.add("    A74.13 applied=" + applied + " grew=" + grew + " retargeted=" + retargeted
                        + " sourceHeld=" + sourceHeld + " settled=" + settled
                        + " before=" + before.toAdd() + "/" + before.alreadyThere()
                        + " after=" + after2.toAdd() + "/" + after2.alreadyThere()
                        + " rows=" + after + " group=" + group);
            }
            return applied && grew && retargeted && sourceHeld && settled;
        });

        SelfTestHarness.check(lines, tally,
                "A74.14 an equipment group applied: the variant gains the pieces it does not wear", () -> {
            Path root = equipmentGroupFixture(dir, "a7414", "Rot Gear");
            String id = com.strata.domain.GroupOps.idFor("Rot Gear");
            com.strata.domain.GroupOps.Added before = com.strata.domain.GroupOps
                    .countsEquipment(id, "frost_husk", "global", "", root);
            boolean applied = com.strata.domain.GroupOps
                    .applyEquipment(id, "frost_husk", "global", "", List.of(), root).ok();
            List<String> after = ownEquipment(root, "frost_husk");
            List<String> group = ownEquipment(root, id);
            boolean grew = after.size() == 3 && before.toAdd() == 1 && before.alreadyThere() == 1;
            boolean added = after.contains("head:minecraft:diamond_helmet")
                    && after.contains("body:minecraft:leather_chestplate")
                    && after.contains("feet:minecraft:diamond_boots");
            boolean keptItsNumbers = chanceOf(root, "frost_husk", "feet").equals("40");
            boolean sourceHeld = group.equals(List.of("head:minecraft:diamond_helmet",
                    "feet:minecraft:diamond_boots"));
            com.strata.domain.GroupOps.Added after2 = com.strata.domain.GroupOps
                    .countsEquipment(id, "frost_husk", "global", "", root);
            boolean settled = after2.toAdd() == 0 && after2.alreadyThere() == 2;
            if (!(applied && grew && added && keptItsNumbers && sourceHeld && settled)) {
                lines.add("    A74.14 applied=" + applied + " grew=" + grew + " added=" + added
                        + " keptItsNumbers=" + keptItsNumbers + " sourceHeld=" + sourceHeld
                        + " settled=" + settled + " before=" + before.toAdd() + "/" + before.alreadyThere()
                        + " after=" + after2.toAdd() + "/" + after2.alreadyThere()
                        + " rows=" + after + " group=" + group);
            }
            return applied && grew && added && keptItsNumbers && sourceHeld && settled;
        });

        SelfTestHarness.check(lines, tally,
                "A74.15 a group of the wrong kind is refused on drops and equipment, naming both", () -> {
            Path root = kindFixture(dir, "a7415");
            String abilities = com.strata.domain.GroupOps.idFor("Rot Abilities");
            String loot = com.strata.domain.GroupOps.idFor("Rot Drops");
            com.strata.domain.WriteReport lootSaid = com.strata.domain.GroupOps
                    .applyLoot(abilities, "minecraft:strider", "frost_husk", "global", "", root);
            com.strata.domain.WriteReport gearSaid = com.strata.domain.GroupOps
                    .applyEquipment(abilities, "frost_husk", "global", "", List.of(), root);
            com.strata.domain.WriteReport backSaid = com.strata.domain.GroupOps
                    .applyEquipment(loot, "frost_husk", "global", "", List.of(), root);
            com.strata.domain.GroupOps.Added lootAdds = com.strata.domain.GroupOps
                    .countsLoot(abilities, "minecraft:strider", "frost_husk", "global", "", root);
            com.strata.domain.GroupOps.Added gearAdds = com.strata.domain.GroupOps
                    .countsEquipment(abilities, "frost_husk", "global", "", root);
            boolean untouched = ownLoot(root, "frost_husk").isEmpty()
                    && ownEquipment(root, "frost_husk").isEmpty();
            boolean refused = lootSaid.refusal() != null && gearSaid.refusal() != null
                    && backSaid.refusal() != null;
            boolean named = refused
                    && lootSaid.refusal().contains("abilities") && lootSaid.refusal().contains("loot")
                    && gearSaid.refusal().contains("abilities") && gearSaid.refusal().contains("equipment")
                    && backSaid.refusal().contains("loot") && backSaid.refusal().contains("equipment");
            boolean silent = lootAdds.toAdd() == 0 && lootAdds.alreadyThere() == 0
                    && gearAdds.toAdd() == 0 && gearAdds.alreadyThere() == 0;
            if (!(untouched && refused && named && silent)) {
                lines.add("    A74.15 untouched=" + untouched + " refused=" + refused
                        + " named=" + named + " silent=" + silent
                        + " loot=" + lootSaid.refusal() + " gear=" + gearSaid.refusal()
                        + " back=" + backSaid.refusal());
            }
            return untouched && refused && named && silent;
        });

        SelfTestHarness.check(lines, tally,
                "A74.16 the effect model: a full row parses, and every field has its column", () -> {
            Path root = Files.createDirectories(dir.resolve("a7416"));
            write(root, Schema.Abilities.FILE, ABILITIES_HEADER
                    + "frost_husk,global,,,minecraft:poison,2,20,8,true,damage_over_time,on_hit,60,"
                    + "4.5,100,true,minecraft:bleeding,"
                    + "minecraft:critical_hit,minecraft:potion,minecraft:milk_bucket,minecraft:zombie\n");
            var read = CsvTable.read(root.resolve(Schema.Abilities.FILE));
            com.strata.domain.EffectOps.EffectRow row = read.isEmpty() ? null
                    : com.strata.domain.EffectOps.parse(read.get(0));
            boolean parsed = row != null
                    && row.effectClass() == com.strata.model.EffectClass.DAMAGE_OVER_TIME;
            boolean fired = parsed && "on_hit".equals(row.trigger()) && row.chance() == 60;
            boolean readBack = parsed
                    && "minecraft:poison".equals(row.parameter())
                    && row.amount() == 2 && row.interval() == 20 && row.radius() == 8
                    && row.damage() == 4.5 && row.duration() == 100 && row.canKill()
                    && "minecraft:bleeding".equals(row.damageType())
                    && "minecraft:critical_hit".equals(row.particle())
                    && "minecraft:potion".equals(row.hudIcon())
                    && "minecraft:milk_bucket".equals(row.cure())
                    && "minecraft:zombie".equals(row.entity());

            List<String> columns = new ArrayList<>();
            for (java.lang.reflect.Field each : Schema.Abilities.class.getFields()) {
                if (each.getType() == String.class && !each.getName().equals("FILE")) {
                    columns.add((String) each.get(null));
                }
            }
            List<String> identity = List.of(Schema.Abilities.VARIANT_NAME, Schema.Abilities.SCOPE,
                    Schema.Abilities.SCOPE_ID, Schema.Abilities.ENABLED);
            boolean everyField = true;
            for (com.strata.model.AbilityField field : com.strata.model.AbilityField.values()) {
                everyField &= columns.contains(field.key());
            }
            boolean everyColumn = true;
            for (String column : columns) {
                everyColumn &= identity.contains(column)
                        || com.strata.model.AbilityField.of(column) != null;
            }
            boolean width = read.size() == 1 && read.get(0).size() == columns.size();
            if (!(parsed && fired && readBack && everyField && everyColumn && width)) {
                lines.add("    A74.16 parsed=" + parsed + " fired=" + fired
                        + " readBack=" + readBack + " everyField=" + everyField
                        + " everyColumn=" + everyColumn + " width=" + width
                        + " columns=" + columns + " row=" + row);
            }
            return parsed && fired && readBack && everyField && everyColumn && width;
        });

    }

    private static void runTestsPart12(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "A74.17 a blank chance reads as 100, a named one as itself", () -> {
            double blank = com.strata.domain.EffectOps.chance("");
            double spaces = com.strata.domain.EffectOps.chance("   ");
            double missing = com.strata.domain.EffectOps.chance(null);
            double named = com.strata.domain.EffectOps.chance("50");
            double typo = com.strata.domain.EffectOps.chance("often");
            double above = com.strata.domain.EffectOps.chance("150");
            double below = com.strata.domain.EffectOps.chance("-10");
            boolean ok = blank == 100 && spaces == 100 && missing == 100 && typo == 100
                    && named == 50 && above == 100 && below == 0;
            if (!ok) {
                lines.add("    A74.17 blank=" + blank + " spaces=" + spaces + " missing=" + missing
                        + " named=" + named + " typo=" + typo + " above=" + above + " below=" + below);
            }
            return ok;
        });


        SelfTestHarness.check(lines, tally,
                "A74.19 a row naming a class that does not exist is refused, one that does is not", () -> {
            Path root = Files.createDirectories(dir.resolve("a7419"));
            write(root, Schema.Abilities.FILE, ABILITIES_HEADER
                    + String.join(",", "frost_husk", "global", "", "", "minecraft:poison", "4",
                            "", "", "", "destroy_world", "on_hit",
                            "", "", "", "", "", "", "", "")
                    + "\n"
                    + String.join(",", "frost_husk", "global", "", "", "minecraft:poison", "4",
                            "", "", "", "damage_over_time", "on_hit",
                            "", "", "", "", "", "", "", "")
                    + "\n"
                    + String.join(",", "frost_husk", "global", "", "effect", "poison", "4",
                            "", "", "", "", "", "", "", "", "", "", "", "", "")
                    + "\n");
            var read = CsvTable.read(root.resolve(Schema.Abilities.FILE));
            boolean three = read.size() == 3;
            String unknown = three ? com.strata.domain.EffectOps.problem(read.get(0)) : "no rows";
            String known = three ? com.strata.domain.EffectOps.problem(read.get(1)) : "no rows";
            String legacy = three ? com.strata.domain.EffectOps.problem(read.get(2)) : "no rows";
            boolean refused = three && "unknown effect class 'destroy_world'".equals(unknown)
                    && com.strata.domain.EffectOps.parse(read.get(0)) == null;
            boolean accepted = three && known.isEmpty()
                    && com.strata.domain.EffectOps.parse(read.get(1)) != null;
            boolean legacySays = three && "this row names no effect class".equals(legacy);
            if (!(refused && accepted && legacySays)) {
                lines.add("    A74.19 refused=" + refused + " accepted=" + accepted
                        + " legacySays=" + legacySays + " rows=" + read.size()
                        + " unknown=" + unknown + " known=" + known + " legacy=" + legacy);
            }
            return refused && accepted && legacySays;
        });

        SelfTestHarness.check(lines, tally,
                "A74.20 apply_effect reads exactly these attributes", () -> {
            return readsExactly("apply_effect", Set.of(
                    AbilityField.EFFECT_CLASS, AbilityField.TRIGGER, AbilityField.CHANCE,
                    AbilityField.PARTICLE, AbilityField.HUD_ICON, AbilityField.TICKS,
                    AbilityField.PARAMETER, AbilityField.AMOUNT, AbilityField.DURATION),
                    lines, "A74.20");
        });

        SelfTestHarness.check(lines, tally,
                "A74.21 damage_over_time reads exactly these attributes", () -> {
            return readsExactly("damage_over_time", Set.of(
                    AbilityField.EFFECT_CLASS, AbilityField.TRIGGER, AbilityField.CHANCE,
                    AbilityField.PARTICLE, AbilityField.HUD_ICON,
                    AbilityField.DAMAGE, AbilityField.TICKS, AbilityField.DURATION,
                    AbilityField.CAN_KILL, AbilityField.DAMAGE_TYPE),
                    lines, "A74.21");
        });

        SelfTestHarness.check(lines, tally,
                "A74.22 life_steal reads exactly these attributes", () -> {
            return readsExactly("life_steal", Set.of(
                    AbilityField.EFFECT_CLASS, AbilityField.TRIGGER, AbilityField.CHANCE,
                    AbilityField.PARTICLE, AbilityField.HUD_ICON, AbilityField.TICKS,
                    AbilityField.AMOUNT),
                    lines, "A74.22");
        });

        SelfTestHarness.check(lines, tally,
                "A74.23 attribute_change reads exactly these attributes", () -> {
            return readsExactly("attribute_change", Set.of(
                    AbilityField.EFFECT_CLASS, AbilityField.TRIGGER, AbilityField.CHANCE,
                    AbilityField.PARTICLE, AbilityField.HUD_ICON, AbilityField.TICKS,
                    AbilityField.PARAMETER, AbilityField.AMOUNT, AbilityField.DURATION,
                    AbilityField.CURE),
                    lines, "A74.23");
        });

        SelfTestHarness.check(lines, tally,
                "A74.24 explode reads exactly these attributes", () -> {
            return readsExactly("explode", Set.of(
                    AbilityField.EFFECT_CLASS, AbilityField.TRIGGER, AbilityField.CHANCE,
                    AbilityField.PARTICLE, AbilityField.HUD_ICON, AbilityField.TICKS,
                    AbilityField.DAMAGE, AbilityField.RADIUS, AbilityField.DAMAGE_TYPE, AbilityField.CAN_KILL),
                    lines, "A74.24");
        });

        SelfTestHarness.check(lines, tally,
                "A74.25 spawn reads exactly these attributes", () -> {
            return readsExactly("spawn", Set.of(
                    AbilityField.EFFECT_CLASS, AbilityField.TRIGGER, AbilityField.CHANCE,
                    AbilityField.PARTICLE, AbilityField.HUD_ICON, AbilityField.TICKS,
                    AbilityField.ENTITY, AbilityField.AMOUNT),
                    lines, "A74.25");
        });

        SelfTestHarness.check(lines, tally,
                "A74.26 TriggerOps.all() lists every trigger in order", () -> {
            var triggers = com.strata.domain.TriggerOps.all();
            return triggers.size() == 9
                    && triggers.get(0).id().equals("on_spawn")
                    && triggers.get(1).id().equals("on_death")
                    && triggers.get(2).id().equals("on_take_damage")
                    && triggers.get(3).id().equals("on_flee")
                    && triggers.get(4).id().equals("on_hit")
                    && triggers.get(5).id().equals("on_hurt")
                    && triggers.get(6).id().equals("in_sunlight")
                    && triggers.get(7).id().equals("permanent")
                    && triggers.get(8).id().equals("on_impact");
        });

        SelfTestHarness.check(lines, tally,
                "A74.27 On flee fires: every trigger the editor offers is hostable", () -> {
            var flee = com.strata.domain.TriggerOps.byId("on_flee");
            return flee != null && flee.hostable()
                    && com.strata.domain.TriggerOps.unhostable().isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "A74.28 chance 0 prevents firing, 100 always fires", () -> {
            double zero = com.strata.domain.EffectOps.chance("0");
            double hundred = com.strata.domain.EffectOps.chance("100");
            double blank = com.strata.domain.EffectOps.chance("");
            return zero == 0.0 && hundred == 100.0 && blank == 100.0;
        });

        SelfTestHarness.check(lines, tally,
                "A74.29 seeded roll is deterministic", () -> {
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "A65.1 item variants: a base item and its own values, the rest inherited; names, keys, and checks", () -> {
            String dmg = com.strata.model.ItemStats.attributeKey("minecraft:generic.attack_damage", "mainhand");
            String speed = com.strata.model.ItemStats.attributeKey("minecraft:generic.movement_speed", "mainhand");
            var list = com.strata.domain.ItemVariantOps.variants(
                    List.of(Map.of("name", "frost_blade", "base_item", "minecraft:iron_sword"),
                            Map.of("name", "ember_cap", "base_item", "minecraft:iron_helmet")),
                    List.of(Map.of("variant", "frost_blade", "key", "display_name", "value", "Frost Blade"),
                            Map.of("variant", "frost_blade", "key", dmg, "value", "7")),
                    List.of(Map.of("variant", "ember_cap", "ability", "EFFECT")),
                    Map.of("minecraft:iron_sword", "Weapons", "minecraft:iron_helmet", "Armor"));
            boolean listed = list.size() == 2 && list.get(0).name().equals("frost_blade") && list.get(0).values() == 2
                    && list.get(1).kind().equals("Armor") && list.get(1).effects() == 1;
            List<com.strata.domain.ItemStatsOps.Value> base = List.of(
                    new com.strata.domain.ItemStatsOps.Value("minecraft:iron_sword", "max_stack_size", "1", "", ""),
                    new com.strata.domain.ItemStatsOps.Value("minecraft:iron_sword", "max_damage", "250", "300", ""),
                    new com.strata.domain.ItemStatsOps.Value("minecraft:iron_sword", "rarity", "common", "", ""),
                    new com.strata.domain.ItemStatsOps.Value("minecraft:iron_sword", dmg, "5.0", "", "add_value"));
            var values = com.strata.domain.ItemVariantOps.values("frost_blade", base,
                    new java.util.LinkedHashMap<>(Map.of(dmg, "7", speed, "0.1", "enchantment|minecraft:sharpness", "3")));
            java.util.function.Function<String, com.strata.domain.ItemVariantOps.Value> at = k ->
                    values.stream().filter(v -> v.key().equals(k)).findFirst().orElse(null);
            boolean inherits = at.apply("max_damage").effective().equals("300") && at.apply("max_damage").own().isEmpty()
                    && at.apply(dmg).effective().equals("7") && at.apply(dmg).base().equals("5.0")
                    && at.apply(speed).base().isEmpty() && at.apply("enchantment|minecraft:sharpness") != null
                    && values.get(0).key().equals("display_name") && at.apply("rarity").base().equals("common")
                    && com.strata.domain.ItemVariantOps.what(at.apply(speed)).contains("variant adds it")
                    && com.strata.domain.ItemVariantOps.name(at.apply("enchantment|minecraft:sharpness"))
                    .equals("Enchantment Sharpness");
            boolean checked = com.strata.model.ItemVariant.nameProblem("Frost Blade", List.of()) != null
                    && com.strata.model.ItemVariant.nameProblem("frost_blade", List.of("frost_blade")) != null
                    && com.strata.model.ItemVariant.nameProblem("frost_blade", List.of()) == null
                    && com.strata.model.ItemVariant.problem("enchantment|minecraft:sharpness", "256", false) != null
                    && com.strata.model.ItemVariant.problem("enchantment|minecraft:sharpness", "10", false) == null
                    && com.strata.model.ItemVariant.problem("glint", "On", false) == null
                    && com.strata.model.ItemVariant.problem(com.strata.model.ItemStats.attributeKey("x:y", "pocket"), "1", false) != null
                    && com.strata.model.ItemVariant.problem("max_stack_size", "16", true) != null
                    && com.strata.domain.ItemVariantOps.problem(at.apply("max_stack_size"), "16", values) != null;
            boolean refs = "frost_blade".equals(com.strata.model.ItemVariant.nameOf("variant:frost_blade"))
                    && com.strata.model.ItemVariant.nameOf("minecraft:iron_sword") == null
                    && com.strata.model.ItemVariant.ref("frost_blade").equals("variant:frost_blade")
                    && com.strata.model.ItemVariant.loreLines("Cold | to the touch").equals(List.of("Cold", "to the touch"))
                    && com.strata.model.ItemVariant.hash("minecraft:iron_sword", Map.of("a", "1", "b", "2"))
                    == com.strata.model.ItemVariant.hash("minecraft:iron_sword", new java.util.TreeMap<>(Map.of("b", "2", "a", "1")))
                    && com.strata.model.ItemVariant.hash("minecraft:iron_sword", Map.of("a", "1"))
                    != com.strata.model.ItemVariant.hash("minecraft:iron_sword", Map.of("a", "2"));
            if (!(listed && inherits && checked && refs)) {
                lines.add("    A65.1 listed=" + listed + " inherits=" + inherits + " checked=" + checked + " refs=" + refs);
            }
            return listed && inherits && checked && refs;
        });

        SelfTestHarness.check(lines, tally,
                "A65.5 the item variant editor: blank is the base item's; an added attribute takes the base item's slot", () -> {
            var blank = new com.strata.domain.ItemVariantOps.Form("frost_blade", "minecraft:iron_sword", "", "",
                    "Base item", "default", "No", "");
            var full = new com.strata.domain.ItemVariantOps.Form("frost_blade", "minecraft:iron_sword", "Frost Blade",
                    "Cold", "rare", "on", "Yes", "7");
            var none = com.strata.domain.ItemVariantOps.lookWrites(blank);
            var all = com.strata.domain.ItemVariantOps.lookWrites(full);
            boolean writes = none.values().stream().allMatch(String::isEmpty) && none.size() == 6
                    && all.get("display_name").equals("Frost Blade") && all.get("rarity").equals("rare")
                    && all.get("glint").equals("on") && all.get("unbreakable").equals("true")
                    && all.get("custom_model_data").equals("7");
            boolean model = com.strata.domain.ItemVariantOps.formProblem(
                    new com.strata.domain.ItemVariantOps.Form("x", "y", "", "", "Base item", "default", "No", "big"), false) != null
                    && com.strata.domain.ItemVariantOps.formProblem(full, false) == null;
            String dmg = com.strata.model.ItemStats.attributeKey("minecraft:generic.armor", "chest");
            var chest = List.of(new com.strata.domain.ItemVariantOps.Value("v", "display_name", "", "", ""),
                    new com.strata.domain.ItemVariantOps.Value("v", dmg, "8.0", "", "add_value"));
            boolean slot = com.strata.domain.ItemVariantOps.naturalSlot(chest).equals("chest")
                    && com.strata.domain.ItemVariantOps.naturalSlot(List.of()).equals("mainhand")
                    && com.strata.domain.ItemVariantOps.attributeTaken("minecraft:generic.armor", chest) != null
                    && com.strata.domain.ItemVariantOps.attributeTaken("minecraft:generic.luck", chest) == null
                    && com.strata.command.StrataCommands.giveVariantCommand("frost_blade").equals("strata item give @s frost_blade");
            if (!(writes && model && slot)) {
                lines.add("    A65.5 writes=" + writes + " model=" + model + " slot=" + slot);
            }
            return writes && model && slot;
        });

        SelfTestHarness.check(lines, tally,
                "A66.1 the owner's second report: vegetation by biome, special mob breakdowns, the mob switches' row", () -> {
            var sections = com.strata.domain.VegetationOps.sections(List.of(),
                    Map.of("minecraft:plains", List.of("minecraft:tree_b", "minecraft:flower_a"),
                            "minecraft:crimson_forest", List.of("minecraft:fungus"),
                            "minecraft:forest", List.of("minecraft:tree_b")),
                    Map.of("minecraft:plains", "Overworld", "minecraft:forest", "Overworld",
                            "minecraft:crimson_forest", "The Nether"),
                    java.util.Comparator.comparingInt(d -> List.of("Overworld", "The Nether").indexOf(d)),
                    List.of("minecraft:tree_b", "minecraft:flower_a", "minecraft:fungus", "mod:lonely"));
            boolean veg = sections.size() == 4
                    && sections.get(0).biome().equals("minecraft:forest")
                    && sections.get(1).biome().equals("minecraft:plains")
                    && sections.get(1).features().equals(List.of("minecraft:flower_a", "minecraft:tree_b"))
                    && sections.get(2).dimension().equals("The Nether")
                    && sections.get(3).dimension().equals(com.strata.domain.VegetationOps.OTHER_PLANTS)
                    && sections.get(3).features().equals(List.of("mod:lonely"))
                    && com.strata.domain.VegetationOps.sections(List.of("minecraft:forest"),
                    Map.of("minecraft:forest", List.of("minecraft:tree_b")), Map.of(), String::compareTo,
                    List.of("minecraft:tree_b")).size() == 1;
            var values = com.strata.domain.SpecialSpawnsOps.values(Map.of("||phantoms|enabled", "Off"), "", "", "");
            String phantoms = com.strata.domain.SpecialSpawnsOps.summary("phantoms", values);
            boolean special = phantoms.startsWith("Off") && phantoms.contains("(yours)")
                    && !com.strata.domain.SpecialSpawnsOps.spawners().contains("special")
                    && com.strata.domain.SpecialSpawnsOps.mainSwitch(values).effective().equals("On");
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            var mob = com.strata.ui.screen.MobEditLayout.build(new com.strata.ui.Frame(854, 480), m,
                    new com.strata.ui.screen.MobEditLayout.Input("Strata ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Mob: Bat", false, false, true, false, true,
                            "Scope: Global", "", "Global", "Bat", "Goal set: Bat", List.of(), "Variant name", true,
                            true, "", "", false, "Scaling: Default"));
            int topY = mob.get("undo").box().y();
            int toolsY = mob.get("mobdata").box().y();
            boolean row = java.util.stream.Stream.of("mobGriefing", "category", "spawnAs", "texture", "own")
                    .allMatch(id -> mob.get(id) == null)
                    && mob.get("reset").box().y() == topY
                    && mob.get("mobdata").box().x() < mob.get("spawnRules").box().x()
                    && mob.get("enabled").box().y() == toolsY && mob.get("enabled").box().x() > mob.get("abilities").box().x()
                    && mob.get("scaling").box().y() == mob.get("coefficient").box().y()
                    && mob.get("display").box().y() - mob.get("name").box().y()
                            == new com.strata.ui.Frame(854, 480).rowH()
                    && mob.get("display").box().y() < mob.get("goalSet").box().y();
            if (!(veg && special && row)) {
                lines.add("    A66.1 veg=" + veg + " special=" + special + " row=" + row + " phantoms=" + phantoms);
            }
            return veg && special && row;
        });

        SelfTestHarness.check(lines, tally,
                "A65.2 item variants are given by command: /strata item give <targets> <variant> [count], /strata item list", () -> {
            var root = new com.strata.command.StrataCommands().tree().build();
            var item = root.getChild("item");
            var give = item == null ? null : item.getChild("give");
            var targets = give == null ? null : give.getChild("targets");
            var variant = targets == null ? null : targets.getChild("variant");
            boolean ok = item != null && item.getChild("list") != null && variant != null
                    && variant.getCommand() != null && variant.getChild("count") != null
                    && com.strata.runtime.ItemVariantLootFunction.ID.equals("strata:item_variant");
            if (!ok) {
                lines.add("    A65.2 the item command tree is incomplete");
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "A54.1 chest loot: grouped by structure; an item added or removed makes a whole table", () -> {
            List<String> ids = List.of("minecraft:chests/shipwreck_map", "minecraft:chests/shipwreck_supply",
                    "minecraft:chests/village/village_armorer", "minecraft:chests/ancient_city",
                    "minecraft:chests/ancient_city_ice_box", "minecraft:chests/simple_dungeon",
                    "minecraft:chests/bastion_hoglin_stable", "minecraft:chests/bastion_other",
                    "somemod:chests/tower_top");
            var CL = com.strata.domain.ChestsOps.class;
            boolean groups = com.strata.domain.ChestsOps.chestGroup(ids.get(0), ids).equals("Shipwreck")
                    && com.strata.domain.ChestsOps.chestGroup(ids.get(2), ids).equals("Village")
                    && com.strata.domain.ChestsOps.chestGroup(ids.get(3), ids).equals("Ancient city")
                    && com.strata.domain.ChestsOps.chestGroup(ids.get(4), ids).equals("Ancient city")
                    && com.strata.domain.ChestsOps.chestGroup(ids.get(5), ids).equals("Simple dungeon")
                    && com.strata.domain.ChestsOps.chestGroup(ids.get(6), ids).equals("Bastion")
                    && com.strata.domain.ChestsOps.chestGroup(ids.get(8), ids).equals("somemod: Tower top")
                    && com.strata.domain.ChestsOps.chestName("minecraft:chests/shipwreck_supply").equals("Shipwreck supply")
                    && com.strata.domain.ChestsOps.chestName(ids.get(2)).equals("Village armorer");
            String table = "minecraft:chests/test";
            Map<String, String> game = LootJson.flatten(com.google.gson.JsonParser.parseString(
                    "{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:bone\"},"
                            + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:string\",\"weight\":3}]}]}"));
            boolean paths = LootJson.addedEntryPath("pools.0.entries.2.name", game.keySet())
                    && !LootJson.addedEntryPath("pools.0.entries.1.name", game.keySet())
                    && !LootJson.addedEntryPath("pools.3.entries.0.name", game.keySet())
                    && !LootJson.addedEntryPath("pools.0.rolls", game.keySet());
            List<GameLayer.Row> sheetRows = new ArrayList<>();
            game.forEach((path, value) -> sheetRows.add(new GameLayer.Row(table, GameLayer.LOOT_VALUE, path, value)));
            Map<String, String> own = new java.util.LinkedHashMap<>(
                    com.strata.domain.LootTableOps.itemRows(0, 2, "minecraft:diamond"));
            own.put("pools.0.entries.0.weight", "0");
            own.put("pools.0.entries.9.name", "\"minecraft:stale\"");
            List<Map<String, String>> edits = new ArrayList<>();
            own.forEach((path, value) -> edits.add(Map.of("table", table, "path", path, "value", value)));
            var json = LootJson.editedTables(sheetRows, edits).get(table).getAsJsonObject();
            var entries = json.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
            own.remove("pools.0.entries.9.name");
            boolean whole = entries.size() == 3
                    && entries.get(0).getAsJsonObject().get("weight").getAsInt() == 0
                    && entries.get(2).getAsJsonObject().get("name").getAsString().equals("minecraft:diamond")
                    && entries.get(2).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                    .get("function").getAsString().equals("minecraft:set_count");
            var values = com.strata.domain.LootTableOps.values(sheetRows, table, own);
            own.putAll(com.strata.domain.LootTableOps.itemRows(0, 3, "minecraft:emerald"));
            var sheet = new com.strata.domain.LootTableOps.Sheet(table,
                    com.strata.domain.LootTableOps.values(sheetRows, table, own));
            var diamond = sheet.values().stream().filter(v -> v.path().equals("pools.0.entries.2.name")).findFirst().orElseThrow();
            var bone = sheet.values().stream().filter(v -> v.path().equals("pools.0.entries.0.name")).findFirst().orElseThrow();
            var plan = com.strata.domain.LootTableOps.removal(sheet, diamond);
            var gamePlan = com.strata.domain.LootTableOps.removal(sheet, bone);
            boolean items = values.stream().anyMatch(v -> v.path().equals("pools.0.entries.2.name")
                            && com.strata.domain.LootTableOps.added(v))
                    && com.strata.domain.LootTableOps.nextEntry(sheet.values(), 0) == 4
                    && plan.delete().contains("pools.0.entries.2.name") && plan.delete().contains("pools.0.entries.3.name")
                    && "\"minecraft:emerald\"".equals(plan.write().get("pools.0.entries.2.name"))
                    && gamePlan.delete().isEmpty() && "0".equals(gamePlan.write().get("pools.0.entries.0.weight"))
                    && com.strata.domain.LootTableOps.addProblem(sheet, 5, "minecraft:dirt") != null
                    && com.strata.domain.LootTableOps.addProblem(sheet, 0, "minecraft:dirt") == null
                    && com.strata.domain.LootTableOps.restore(table, diamond).refusal() != null;
            if (!(groups && paths && whole && items)) {
                lines.add("    A54.1 groups=" + groups + " paths=" + paths + " whole=" + whole + " items=" + items
                        + " json=" + json);
            }
            return groups && paths && whole && items;
        });

        SelfTestHarness.check(lines, tally,
                "A54.2 a chest's loot entries as a list: every count shape read and written, and the layouts fit", () -> {
            String table = "minecraft:chests/test2";
            Map<String, String> game = LootJson.flatten(com.google.gson.JsonParser.parseString("{\"pools\":["
                    + "{\"rolls\":2,\"entries\":["
                    + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:bone\"},"
                    + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:string\",\"functions\":[{\"function\":"
                    + "\"minecraft:set_count\",\"count\":3}]},"
                    + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:gunpowder\",\"weight\":2,\"functions\":["
                    + "{\"function\":\"minecraft:set_count\",\"count\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":4}}]},"
                    + "{\"type\":\"minecraft:alternatives\",\"children\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:dirt\"}]}]},"
                    + "{\"rolls\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3},\"entries\":["
                    + "{\"type\":\"minecraft:item\",\"name\":\"minecraft:apple\"}]}]}"));
            List<GameLayer.Row> sheetRows = new ArrayList<>();
            game.forEach((path, value) -> sheetRows.add(new GameLayer.Row(table, GameLayer.LOOT_VALUE, path, value)));
            var sheet = new com.strata.domain.LootTableOps.Sheet(table,
                    com.strata.domain.LootTableOps.values(sheetRows, table, Map.of()));
            var es = com.strata.domain.ChestsOps.entries(sheet);
            var ps = com.strata.domain.ChestsOps.pools(sheet);
            var bone = es.get(0);
            var string = es.get(1);
            var powder = es.get(2);
            var alt = es.get(3);
            boolean read = es.size() == 5 && bone.item().equals("minecraft:bone") && bone.countMin().equals("1")
                    && string.countMin().equals("3") && string.countMax().equals("3")
                    && powder.countMin().equals("1") && powder.countMax().equals("4") && powder.weight() == 2
                    && !alt.single() && ps.size() == 2 && ps.get(0).rollsMin().equals("2") && ps.get(0).rollsSet()
                    && ps.get(1).rollsMax().equals("3")
                    && com.strata.domain.ChestsOps.share(powder, ps).equals("50%");
            Map<String, String> writes = new java.util.LinkedHashMap<>();
            writes.putAll(com.strata.domain.ChestsOps.entryWrites(sheet, bone, 0,
                    new com.strata.domain.ChestsOps.EntryForm("minecraft:bone", "1", "2", "5")));
            writes.putAll(com.strata.domain.ChestsOps.entryWrites(sheet, string, 0,
                    new com.strata.domain.ChestsOps.EntryForm("minecraft:string", "1", "4", "6")));
            writes.putAll(com.strata.domain.ChestsOps.entryWrites(sheet, powder, 0,
                    new com.strata.domain.ChestsOps.EntryForm("minecraft:gunpowder", "5", "2", "2")));
            writes.putAll(com.strata.domain.ChestsOps.entryWrites(sheet, null, 1,
                    new com.strata.domain.ChestsOps.EntryForm("minecraft:emerald", "3", "1", "2")));
            writes.putAll(com.strata.domain.ChestsOps.rollsWrites(sheet, ps.get(0), 1, 2));
            writes.putAll(com.strata.domain.ChestsOps.rollsWrites(sheet, ps.get(1), 4, 4));
            List<Map<String, String>> edits = new ArrayList<>();
            writes.forEach((path, value) -> edits.add(Map.of("table", table, "path", path, "value", value)));
            var json = LootJson.editedTables(sheetRows, edits).get(table).getAsJsonObject();
            var pool0 = json.getAsJsonArray("pools").get(0).getAsJsonObject();
            var pool1 = json.getAsJsonArray("pools").get(1).getAsJsonObject();
            var e0 = pool0.getAsJsonArray("entries");
            boolean written = e0.get(0).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                    .getAsJsonObject("count").get("max").getAsInt() == 5
                    && e0.get(1).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                    .getAsJsonObject("count").get("min").getAsInt() == 4
                    && e0.get(2).getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject()
                    .getAsJsonObject("count").get("max").getAsInt() == 2
                    && e0.get(2).getAsJsonObject().get("weight").getAsInt() == 5
                    && pool0.getAsJsonObject("rolls").get("max").getAsInt() == 2
                    && pool1.getAsJsonObject("rolls").get("min").getAsInt() == 4
                    && pool1.getAsJsonArray("entries").size() == 2
                    && pool1.getAsJsonArray("entries").get(1).getAsJsonObject().get("weight").getAsInt() == 3;
            boolean checks = com.strata.domain.ChestsOps.entryProblem(bone,
                    new com.strata.domain.ChestsOps.EntryForm("minecraft:bone", "1", "5", "2")) != null
                    && com.strata.domain.ChestsOps.entryProblem(alt, com.strata.domain.ChestsOps.formFor(alt)) != null
                    && com.strata.domain.ChestsOps.entryProblem(null,
                    new com.strata.domain.ChestsOps.EntryForm("", "1", "1", "1")) != null
                    && com.strata.domain.ChestsOps.rollsProblem(ps.get(0), "3", "1") != null;
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            java.util.function.Predicate<com.strata.ui.Layout> fits = l -> l.elements().stream()
                    .allMatch(el -> el.box().x() >= 0 && el.box().x() + el.box().w() <= f.width());
            var list = com.strata.ui.screen.ListScreenLayouts.chestLoot(f, m, "Strata ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Chest loot: Shipwreck ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Shipwreck"
                    + " treasure", "Remove 12", true, "8 loot entries in 3 pool(s). From the next world load.", null);
            var form = com.strata.ui.screen.FormScreenLayouts.chestEntry(f, m, "t", "Enchanted golden apple",
                    "Pool 1. From the next world load, to chests not yet opened.", "The count's min must not be above its max");
            var rolls = com.strata.ui.screen.FormScreenLayouts.chestPool(f, m, "t", "From the next world load.", null);
            boolean layouts = fits.test(list) && fits.test(form) && fits.test(rolls)
                    && List.of("add", "edit", "remove", "restore", "restoreAll", "table").stream().allMatch(id -> list.get(id) != null)
                    && List.of("item", "weight", "min", "max").stream().allMatch(id -> form.get(id) != null)
                    && rolls.get("min") != null && rolls.get("max") != null;
            if (!(read && written && checks && layouts)) {
                lines.add("    A54.2 read=" + read + " written=" + written + " checks=" + checks + " layouts=" + layouts
                        + " entries=" + es + " json=" + json);
            }
            return read && written && checks && layouts;
        });

        SelfTestHarness.check(lines, tally,
                "A56.1 a Brain waits only while a Strata goal moves the mob", () -> {
            var F = net.minecraft.world.entity.ai.goal.Goal.Flag.class;
            var move = java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
            var moveLook = java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE,
                    net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK);
            var look = java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK);
            var target = java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.TARGET);
            var jump = java.util.EnumSet.of(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP);
            return com.strata.runtime.BrainPause.pauses(List.of(move))
                    && com.strata.runtime.BrainPause.pauses(List.of(look, moveLook))
                    && !com.strata.runtime.BrainPause.pauses(List.of(look, target, jump))
                    && !com.strata.runtime.BrainPause.pauses(List.of());
        });

        SelfTestHarness.check(lines, tally,
                "A56.3 trades: the game's list, then the global, tier, and biome edits; the editor's rows", () -> {
            var wheat = com.strata.model.Trade.plain("minecraft:wheat", 20, "", 0, "minecraft:emerald", 1, 16, 2, 0.05f);
            var bread = com.strata.model.Trade.plain("minecraft:emerald", 1, "", 0, "minecraft:bread", 6, 16, 1, 0.05f);
            var book = new com.strata.model.Trade("", 0, "", 0, "", 0, 12, 1, 0f, "EnchantBookForEmeralds",
                    "an enchanted book | a random enchantment");
            var back = com.strata.model.Trade.decode(wheat.encode());
            var bookBack = com.strata.model.Trade.decode(book.encode());
            boolean codec = wheat.equals(back) && bookBack != null && !bookBack.plain()
                    && bookBack.note().equals("an enchanted book / a random enchantment")
                    && com.strata.model.Trade.decode("plain|x") == null;
            List<Map<String, String>> rows = new ArrayList<>();
            java.util.function.BiConsumer<String, String[]> row = (scope, v) -> {
                Map<String, String> r = new java.util.HashMap<>();
                r.put(Schema.Trades.PROFESSION, "minecraft:farmer");
                r.put(Schema.Trades.LEVEL, "1");
                r.put(Schema.Trades.SCOPE, scope.substring(0, scope.indexOf('|')));
                r.put(Schema.Trades.SCOPE_ID, scope.substring(scope.indexOf('|') + 1));
                r.put(Schema.Trades.ROW, v[0]);
                r.put(Schema.Trades.MODE, v[1]);
                r.put(Schema.Trades.COST_A, v.length > 2 ? v[2] : "");
                r.put(Schema.Trades.COST_A_COUNT, v.length > 2 ? v[3] : "");
                r.put(Schema.Trades.RESULT, v.length > 2 ? v[4] : "");
                r.put(Schema.Trades.RESULT_COUNT, v.length > 2 ? v[5] : "");
                rows.add(r);
            };
            row.accept("|", new String[]{"g0", "edit", "minecraft:wheat", "10", "minecraft:emerald", "1"});
            row.accept("tier|t1", new String[]{"a1", "add", "minecraft:emerald", "5", "minecraft:diamond", "1"});
            row.accept("biome|minecraft:desert", new String[]{"g1", "remove"});
            row.accept("biome|minecraft:desert", new String[]{"a1@tier", "edit", "minecraft:emerald", "5", "minecraft:diamond", "2"});
            var tradeIndex = com.strata.runtime.TradesRuntime.index(rows);
            var byScope = tradeIndex.get("minecraft:farmer|1");
            List<com.strata.model.Trade> game = List.of(wheat, bread, book);
            var global = com.strata.runtime.TradesRuntime.effective(game, byScope,
                    com.strata.runtime.TradesRuntime.layers("", ""));
            var tier = com.strata.runtime.TradesRuntime.effective(game, byScope,
                    com.strata.runtime.TradesRuntime.layers("t1", ""));
            var desert = com.strata.runtime.TradesRuntime.effective(game, byScope,
                    com.strata.runtime.TradesRuntime.layers("t1", "minecraft:desert"));
            boolean layered = global.size() == 3 && global.get(0).trade().costACount() == 10 && global.get(0).edited()
                    && global.get(1).trade().equals(bread) && !global.get(1).edited()
                    && tier.size() == 4 && tier.get(3).key().equals("a1@tier") && tier.get(3).trade().result().equals("minecraft:diamond")
                    && desert.size() == 3 && desert.stream().noneMatch(e -> e.trade().equals(bread))
                    && desert.get(2).trade().resultCount() == 2 && desert.get(2).layer().equals("biome");
            var edRows = com.strata.domain.TradesOps.rows(List.of("minecraft:farmer|1"),
                    Map.of("minecraft:farmer|1", game), tradeIndex,
                    com.strata.runtime.TradesRuntime.layers("t1", "minecraft:desert"), "biome", "biome|minecraft:desert");
            var diamond = edRows.stream().filter(r -> r.trade().result().equals("minecraft:diamond")).findFirst().orElseThrow();
            var removed = edRows.stream().filter(com.strata.domain.TradesOps.TradeRow::removed).toList();
            var tierRows = com.strata.domain.TradesOps.rows(List.of("minecraft:farmer|1"),
                    Map.of("minecraft:farmer|1", game), tradeIndex,
                    com.strata.runtime.TradesRuntime.layers("t1", ""), "tier", "tier|t1");
            boolean editor = edRows.size() == 4 && removed.size() == 1 && removed.get(0).trade().equals(bread)
                    && diamond.here() && !diamond.addedHere()
                    && tierRows.stream().filter(com.strata.domain.TradesOps.TradeRow::addedHere).count() == 1;
            var ok = com.strata.domain.TradesOps.formFor(null);
            boolean checks = com.strata.domain.TradesOps.problem(ok) != null
                    && com.strata.domain.TradesOps.problem(com.strata.domain.TradesOps.formFor(wheat)) == null
                    && com.strata.domain.TradesOps.problem(new com.strata.domain.TradesOps.TradeForm("minecraft:emerald",
                    "1", "", "2", "minecraft:bread", "1", "12", "1", "0.05")) != null
                    && com.strata.domain.TradesOps.problem(new com.strata.domain.TradesOps.TradeForm("minecraft:emerald",
                    "1", "", "0", "minecraft:bread", "1", "0", "1", "0.05")) != null
                    && com.strata.domain.TradesOps.listName("minecraft:farmer", 1).equals("Farmer, Novice")
                    && com.strata.domain.TradesOps.listName(com.strata.runtime.TradesRuntime.WANDERER, 2)
                    .equals("Wandering trader, rare (1 picked)");
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            java.util.function.Predicate<com.strata.ui.Layout> fits = l -> l.elements().stream()
                    .allMatch(el -> el.box().x() >= 0 && el.box().x() + el.box().w() <= f.width());
            var list = com.strata.ui.screen.ListScreenLayouts.tradeList(f, m, "Strata ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Trades: Villager (Biome: Desert)",
                    "Remove 12", true, "From the next time a trader reaches a level. Trades a trader has stay.", null);
            var form = com.strata.ui.screen.FormScreenLayouts.tradeEntry(f, m, "t", "Enchanted golden apple",
                    "None (pick an item...)", "Emerald", "From the next time a trader reaches this level.",
                    "The second item needs an item, or a count of 0 for none");
            boolean layouts = fits.test(list) && fits.test(form)
                    && List.of("add", "edit", "remove", "restore", "restoreAll").stream().allMatch(id -> list.get(id) != null)
                    && List.of("costA", "costACount", "costB", "costBCount", "result", "resultCount", "maxUses", "xp",
                    "priceMultiplier").stream().allMatch(id -> form.get(id) != null);
            if (!(codec && layered && editor && checks && layouts)) {
                lines.add("    A56.3 codec=" + codec + " layered=" + layered + " editor=" + editor + " checks=" + checks
                        + " layouts=" + layouts + " desert=" + desert + " rows=" + edRows);
            }
            return codec && layered && editor && checks && layouts;
        });

        SelfTestHarness.check(lines, tally,
                "A56.5 a villager's type and profession: the biome's, else the tier's, else Global, each alone", () -> {
            List<Map<String, String>> table = List.of(
                    Map.of(Schema.SpecialSpawns.SCOPE, "", Schema.SpecialSpawns.SCOPE_ID, "",
                            Schema.SpecialSpawns.SPAWNER, "villagers", Schema.SpecialSpawns.SETTING, "villager_type",
                            Schema.SpecialSpawns.VALUE, "minecraft:snow"),
                    Map.of(Schema.SpecialSpawns.SCOPE, "", Schema.SpecialSpawns.SCOPE_ID, "",
                            Schema.SpecialSpawns.SPAWNER, "villagers", Schema.SpecialSpawns.SETTING, "profession",
                            Schema.SpecialSpawns.VALUE, "minecraft:farmer"),
                    Map.of(Schema.SpecialSpawns.SCOPE, "tier", Schema.SpecialSpawns.SCOPE_ID, "t1",
                            Schema.SpecialSpawns.SPAWNER, "villagers", Schema.SpecialSpawns.SETTING, "profession",
                            Schema.SpecialSpawns.VALUE, "minecraft:librarian"),
                    Map.of(Schema.SpecialSpawns.SCOPE, "biome", Schema.SpecialSpawns.SCOPE_ID, "minecraft:desert",
                            Schema.SpecialSpawns.SPAWNER, "villagers", Schema.SpecialSpawns.SETTING, "villager_type",
                            Schema.SpecialSpawns.VALUE, "minecraft:desert"));
            var rows = com.strata.runtime.SpecialSpawnsRuntime.index(table);
            var type = com.strata.model.SpecialSpawners.setting("villagers", "villager_type");
            var job = com.strata.model.SpecialSpawners.setting("villagers", "profession");
            java.util.function.BiFunction<String, String, String> at = (tier, biome) ->
                    com.strata.model.SpecialSpawners.value(rows, type, tier, biome) + " "
                            + com.strata.model.SpecialSpawners.value(rows, job, tier, biome);
            boolean chosen = at.apply("t1", "minecraft:desert").equals("minecraft:desert minecraft:librarian")
                    && at.apply("t1", "minecraft:plains").equals("minecraft:snow minecraft:librarian")
                    && at.apply("", "minecraft:plains").equals("minecraft:snow minecraft:farmer")
                    && com.strata.model.SpecialSpawners.value(Map.of(), type, "t1", "minecraft:desert").isEmpty();
            List<String> ids = List.of("minecraft:none", "minecraft:farmer", "somemod:tower_guard");
            boolean words = com.strata.domain.TradesOps.choices(ids)
                    .equals(List.of("Default", "None", "Farmer", "somemod: Tower guard"))
                    && com.strata.domain.TradesOps.idOf("somemod: Tower guard", ids).equals("somemod:tower_guard")
                    && com.strata.domain.TradesOps.idOf("Default", ids).isEmpty()
                    && com.strata.domain.TradesOps.word("").equals("Default");
            if (!(chosen && words)) {
                lines.add("    A56.5 chosen=" + chosen + " words=" + words);
            }
            return chosen && words;
        });

        SelfTestHarness.check(lines, tally,
                "A58.1 special spawners: the game's numbers by scope, Normal or Special mobs, Time since rest", () -> {
            var days = com.strata.model.SpecialSpawners.setting("phantoms", "count_min");
            var clock = com.strata.model.SpecialSpawners.setting("phantoms", "check_seconds");
            var special = com.strata.model.SpecialSpawners.setting("special", "enabled");
            Map<String, String> rows = com.strata.runtime.SpecialSpawnsRuntime.index(List.of(
                    Map.of("scope", "", "scope_id", "", "spawner", "phantoms", "setting", "count_min", "value", "5"),
                    Map.of("scope", "", "scope_id", "", "spawner", "phantoms", "setting", "check_seconds", "value", "30"),
                    Map.of("scope", "tier", "scope_id", "t1", "spawner", "phantoms", "setting", "check_seconds", "value", "5"),
                    Map.of("scope", "biome", "scope_id", "minecraft:desert", "spawner", "phantoms", "setting", "count_min",
                            "value", "1"),
                    Map.of("scope", "tier", "scope_id", "t1", "spawner", "special", "setting", "enabled", "value", "Off"),
                    Map.of("scope", "", "scope_id", "", "spawner", "mobs", "setting", "minecraft:phantom", "value", "Normal"),
                    Map.of("scope", "", "scope_id", "", "spawner", "mobs", "setting", "minecraft:zombie", "value", "Special"),
                    Map.of("scope", "", "scope_id", "", "spawner", "flagged", "setting", "minecraft:zombie", "value", "true")));
            boolean layered = com.strata.model.SpecialSpawners.value(rows, days, "t1", "minecraft:desert").equals("1")
                    && com.strata.model.SpecialSpawners.value(rows, days, "t1", "minecraft:plains").equals("5")
                    && com.strata.model.SpecialSpawners.value(Map.of(), days, "", "").equals("1")
                    && com.strata.model.SpecialSpawners.value(rows, clock, "t1", "").equals("30")
                    && !com.strata.model.SpecialSpawners.on(com.strata.model.SpecialSpawners.value(rows, special, "t1", ""))
                    && com.strata.model.SpecialSpawners.on(com.strata.model.SpecialSpawners.value(rows, special, "", ""));
            boolean mobs = !com.strata.model.SpecialSpawners.special(rows, "minecraft:phantom")
                    && com.strata.model.SpecialSpawners.special(rows, "minecraft:zombie")
                    && com.strata.model.SpecialSpawners.special(rows, "minecraft:cat")
                    && !com.strata.model.SpecialSpawners.special(rows, "minecraft:skeleton")
                    && com.strata.domain.SpecialSpawnsOps.specialMobs(rows).contains("minecraft:zombie")
                    && !com.strata.domain.SpecialSpawnsOps.specialMobs(rows).contains("minecraft:phantom");
            var tierRows = com.strata.domain.SpecialSpawnsOps.values(rows, "biome", "minecraft:desert", "t1");
            var rest = tierRows.stream().filter(v -> v.setting() == days).findFirst().orElseThrow();
            var sw = tierRows.stream().filter(v -> v.setting() == special).findFirst().orElseThrow();
            boolean editor = tierRows.stream().noneMatch(v -> v.setting().globalOnly())
                    && rest.from().equals("yours") && rest.effective().equals("1")
                    && sw.from().equals("tier") && sw.effective().equals("Off")
                    && com.strata.domain.SpecialSpawnsOps.values(rows, "", "", "").stream()
                    .anyMatch(v -> v.setting() == clock && v.from().equals("yours"));
            boolean seeded = com.strata.model.SpecialSpawners.SETTINGS.stream().allMatch(s ->
                    com.strata.model.SpecialSpawners.problem(s, s.game()) == null && !s.what().isEmpty()
                            && com.strata.model.SpecialSpawners.SPAWNERS.containsKey(s.spawner()))
                    && com.strata.model.SpecialSpawners.problem(days, "-1") != null
                    && com.strata.model.SpecialSpawners.problem(special, "maybe") != null
                    && com.strata.model.SpecialSpawners.problem(
                    com.strata.model.SpecialSpawners.setting("patrols", "chance"), "101") != null;
            boolean restRoll = com.strata.model.SpecialSpawners.restDays("3+") == 3
                    && com.strata.model.SpecialSpawners.restDays("2-5") == 2
                    && com.strata.model.SpecialSpawners.restDays("x") == -1
                    && com.strata.model.SpecialSpawners.restRoll(96000, 3, 80000)
                    && !com.strata.model.SpecialSpawners.restRoll(96000, 3, 60000)
                    && com.strata.model.SpecialSpawners.restRoll(1, 0, 0);
            var type = com.strata.model.SpawnRule.Type.parse("time_since_rest");
            boolean rule = type == com.strata.model.SpawnRule.Type.TIME_SINCE_REST
                    && com.strata.domain.SpawnRulesOps.TYPE_CHOICES.contains("time_since_rest")
                    && com.strata.model.SpawnRule.valueProblem(type, "3+") == null
                    && com.strata.model.SpawnRule.valueProblem(type, "-2") != null
                    && com.strata.domain.SpecialSpawnsOps.gameRules(3, 5).stream().map(r -> r[2] + " " + r[4] + " " + r[5])
                    .toList().equals(List.of("minecraft:phantom time_since_rest 3+", "minecraft:pillager world_day 5+",
                            "minecraft:pillager time daylight"))
                    && com.strata.domain.SpecialSpawnsOps.gameRules(3, 5).stream().allMatch(r ->
                    com.strata.domain.SpawnRulesOps.valueProblem(r[4], r[5]) == null)
                    && com.strata.domain.SpawnRulesOps.TYPE_CHOICES.contains("world_day")
                    && com.strata.model.SpawnRule.valueProblem(com.strata.model.SpawnRule.Type.WORLD_DAY, "-1") != null
                    && com.strata.model.SpawnRule.TIME_CHOICES.containsAll(List.of("daylight", "dark"))
                    && com.strata.model.SpawnRule.daylight(Math::cos)[0] > 22000
                    && com.strata.model.SpawnRule.daylight(Math::cos)[1] > 11500
                    && com.strata.model.SpawnRule.daylight(Math::cos)[1] < 13000
                    && com.strata.model.SpecialSpawners.setting("phantoms", "rest_days") == null
                    && com.strata.model.SpecialSpawners.setting("patrols", "from_day") == null
                    && com.strata.model.SpecialSpawners.setting("patrols", "day_only") == null
                    && com.strata.model.SpawnRule.needPlayer(List.of(List.of(
                    new com.strata.model.SpawnRule(type, "3+"))))
                    && !com.strata.model.SpawnRule.needPlayer(List.of(List.of(new com.strata.model.SpawnRule(type, "3+")),
                    List.of()))
                    && !com.strata.model.SpawnRule.needPlayer(List.of())
                    && com.strata.runtime.SpecialSpawnsRuntime.rulesAt(Map.of(
                    "global||minecraft:phantom", List.of(new com.strata.model.SpawnRule(type, "3+")),
                    "tier|t1|minecraft:phantom", List.of(new com.strata.model.SpawnRule(type, "1+"))),
                    "t1", "minecraft:plains", "minecraft:phantom").get(0).value().equals("1+")
                    && com.strata.runtime.SpecialSpawnsRuntime.rulesAt(Map.of(
                    "global||minecraft:phantom", List.of(new com.strata.model.SpawnRule(type, "3+"))),
                    "t2", "minecraft:plains", "minecraft:phantom").get(0).value().equals("3+");
            com.strata.ui.TextFit.Measure m = s -> s.length() * 6;
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            java.util.function.Predicate<com.strata.ui.Layout> fits = l -> l.elements().stream()
                    .allMatch(el -> el.box().x() >= 0 && el.box().x() + el.box().w() <= f.width());
            var sheet = com.strata.ui.screen.ListScreenLayouts.specialSpawns(f, m, "Strata ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Special mobs: Tier t1",
                    "Days awake: Days since the player last slept before phantoms can come.",
                    "Yellow values are this scope's. They apply at once.", null);
            var tierForm = com.strata.ui.screen.FormScreenLayouts.tierEdit(f, m, "t", true, List.of("add", "replace"),
                    List.of("Default", "On", "Off"), List.of("minecraft:plains"), null, null);
            var biomeForm = com.strata.ui.screen.FormScreenLayouts.biomeEdit(f, m, "t", "Tier: t1", "Level",
                    List.of("Default", "On", "Off"), "");
            var mob = com.strata.ui.screen.MobEditLayout.build(f, m, new com.strata.ui.screen.MobEditLayout.Input(
                    "Strata ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Mob: Phantom", false, false, true, false, true, "Scope: Global", "", "Global", "Phantom",
                    "Goal set: Phantom", List.of(), "Variant name (blank = plain mob)", false, true, "", "", false,
                    "Scaling: Default"));
            boolean layouts = fits.test(sheet) && fits.test(tierForm) && fits.test(biomeForm) && fits.test(mob)
                    && List.of("value", "set", "restore", "restoreAll").stream().allMatch(id -> sheet.get(id) != null)
                    && mob.get("spawnAs") == null
                    && tierForm.get("rules") != null && biomeForm.get("rules") != null
                    && tierForm.get("blocks") != null && biomeForm.get("blocks") != null
                    && tierForm.get("otherSpawns") == null && biomeForm.get("otherSpawns") == null
                    && !com.strata.ui.screen.ListScreenLayouts.menuTools().contains("Special mobs");
            if (!(layered && mobs && editor && seeded && restRoll && rule && layouts)) {
                lines.add("    A58.1 layered=" + layered + " mobs=" + mobs + " editor=" + editor + " seeded=" + seeded
                        + " restRoll=" + restRoll + " rule=" + rule + " layouts=" + layouts);
            }
            return layered && mobs && editor && seeded && restRoll && rule && layouts;
        });

    }

    private static void runTestsPart13(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "A37.6 Undo puts the editor's newest change back, only the editor's, and not over a later one", () -> {
            Path file = dir.resolve("a37-undo.csv");
            write(dir, "a37-undo.csv", "a,b\n1,2\n");
            EditHistory.clear();
            try {
                EditHistory.setActor(() -> null);
                TableWriter.addColumn(file, "c");
                EditHistory.close();
                boolean ignored = !EditHistory.canUndo();
                EditHistory.setActor(() -> "Test list");
                TableWriter.addColumn(file, "d");
                EditHistory.close();
                boolean recorded = EditHistory.canUndo() && EditHistory.lastWhere().equals("Test list");
                boolean back = EditHistory.undo() == null
                        && Files.readAllLines(file).get(0).equals("a,b,c") && !EditHistory.canUndo();
                TableWriter.addColumn(file, "e");
                EditHistory.close();
                Files.writeString(file, "x,y\n");
                boolean refused = EditHistory.undo() != null && EditHistory.canUndo();
                if (!(ignored && recorded && back && refused)) {
                    lines.add("    A37.6 ignored=" + ignored + " recorded=" + recorded + " back=" + back
                            + " refused=" + refused);
                }
                return ignored && recorded && back && refused;
            } finally {
                EditHistory.clear();
                EditHistory.setActor(null);
            }
        });

        SelfTestHarness.check(lines, tally,
                "A36.3 a variant with no drops of its own runs and shows its base mob's", () -> {
            var plain = new com.strata.model.MobLoot("global", "", "minecraft:zombie", "",
                    com.strata.model.OverrideMode.ADD, List.of());
            var own = new com.strata.model.MobLoot("global", "", "minecraft:zombie", "red",
                    com.strata.model.OverrideMode.ADD, List.of());
            boolean runtime = com.strata.model.MobLoot.lootOwner(List.of(plain), "minecraft:zombie", "frosty")
                    .isEmpty()
                    && com.strata.model.MobLoot.lootOwner(List.of(plain, own), "minecraft:zombie", "red")
                    .equals("red");
            Map<String, String> row = new java.util.HashMap<>(Map.of(Schema.Loot.SCOPE, "global",
                    Schema.Loot.SCOPE_ID, "", Schema.Loot.ENTITY, "minecraft:zombie",
                    Schema.Loot.ITEM, "minecraft:bone", Schema.Loot.MODE, "add"));
            var shown = com.strata.domain.LootOps.rowsFor("global", "", "minecraft:zombie", "frosty",
                    List.of(), List.of(row), null);
            boolean editor = shown.size() == 1
                    && com.strata.domain.LootOps.BASE_MOB.equals(shown.get(0).inheritedFrom())
                    && shown.get(0).item().equals("minecraft:bone");
            return runtime && editor;
        });

        SelfTestHarness.check(lines, tally,
                "A36.4 a tier rename or delete carries every per-mob edit table", () -> {
            return com.strata.domain.TierOps.INSTANCE_TABLES.containsAll(List.of(Schema.Variants.FILE,
                    Schema.Equipment.FILE, Schema.Abilities.FILE,
                    Schema.VariantStats.FILE, Schema.MobGoals.FILE, Schema.MobData.FILE));
        });

        SelfTestHarness.check(lines, tally,
                "A36.10 a variant's spawn conditions stay its own, and gate a natural spawn", () -> {
            Path root = dir.resolve("a36");
            Files.createDirectories(root);
            write(root, Schema.SpawnRules.FILE, "scope,scope_id,entity,variant_name,rule_type,value\n"
                    + "global,,minecraft:zombie,frosty,time,night\n");
            var byRow = Tables.loadSpawnRulesByRow(root);
            boolean keyed = Tables.spawnRulesFor(byRow, "global", "", "minecraft:zombie", "", false).isEmpty()
                    && Tables.spawnRulesFor(byRow, "global", "", "minecraft:zombie", "frosty", true).size() == 1;
            Spawn a = Spawn.of(SpawnCategory.MONSTER, "minecraft:zombie", 10, 1, 1, com.strata.model.Source.GLOBAL);
            boolean gated = com.strata.runtime.MobScalingHandler.anyAllowed(List.of(), s -> false)
                    && !com.strata.runtime.MobScalingHandler.anyAllowed(List.of(a), s -> false)
                    && com.strata.runtime.MobScalingHandler.anyAllowed(List.of(a, a), s -> s == a);
            return keyed && gated;
        });

        SelfTestHarness.check(lines, tally,
                "A36.7 bad hand-edited values are refused, not thrown", () -> {
            var nullValue = new com.strata.domain.LootTableOps.Value("pools.0.rolls", "null", "");
            boolean loot = com.strata.domain.LootTableOps.problem(nullValue, "2") != null
                    && LootJson.shown("{bad").equals("{bad");
            var sheet = List.of(new GameLayer.Row("t:a", GameLayer.LOOT_VALUE, "pools.0.rolls", "1"),
                    new GameLayer.Row("t:b", GameLayer.LOOT_VALUE, "pools.0.rolls", "1"));
            var edited = LootJson.editedTables(sheet, List.of(
                    Map.of(Schema.LootTables.TABLE, "t:a", Schema.LootTables.PATH, "pools.0.rolls",
                            Schema.LootTables.VALUE, "{bad"),
                    Map.of(Schema.LootTables.TABLE, "t:b", Schema.LootTables.PATH, "pools.0.rolls",
                            Schema.LootTables.VALUE, "3")));
            boolean onePack = !edited.containsKey("t:a") && edited.containsKey("t:b");
            boolean curve = com.strata.domain.ScalingOps.formProblem(new com.strata.domain.ScalingOps.CurveForm(
                    "minecraft:generic.max_health", "-1", "1", "", true)) != null;
            Path file = dir.resolve("a36-wide.csv");
            write(dir, "a36-wide.csv", "a,b\n1,2,3\n");
            TableWriter.addColumn(file, "c");
            var read = CsvTable.read(file);
            boolean column = read.size() == 1 && read.get(0).getOrDefault("c", "").isEmpty();
            if (!(loot && onePack && curve && column)) {
                lines.add("    A36.7 loot=" + loot + " onePack=" + onePack + " curve=" + curve + " column=" + column);
            }
            return loot && onePack && curve && column;
        });

        SelfTestHarness.check(lines, tally,
                "A75.1 Fill from default adds the game's own spawns to a tier or biome, and only the missing ones", () -> {
                    var cat = net.minecraft.world.entity.MobCategory.CREATURE;
                    var mon = net.minecraft.world.entity.MobCategory.MONSTER;
                    java.util.function.BiFunction<String, Integer, com.strata.game.GameData.SpawnEntry> make =
                            (id, weight) -> new com.strata.game.GameData.SpawnEntry(
                                    net.minecraft.resources.ResourceLocation.tryParse(id), cat, weight, 2, 4);
                    java.util.function.BiFunction<String, Integer, com.strata.game.GameData.SpawnEntry> hostile =
                            (id, weight) -> new com.strata.game.GameData.SpawnEntry(
                                    net.minecraft.resources.ResourceLocation.tryParse(id), mon, weight, 1, 1);

                    var grassPlains = List.of(make.apply("minecraft:cow", 12), make.apply("minecraft:sheep", 8));
                    var murkySwamp = List.of(make.apply("minecraft:cow", 12), hostile.apply("minecraft:slime", 100));
                    var tierSpawns = com.strata.domain.MobOps.unionSpawns(List.of(grassPlains, murkySwamp));
                    boolean union = tierSpawns.size() == 3
                            && tierSpawns.stream().map(e -> e.entity().toString()).toList()
                            .equals(List.of("minecraft:cow", "minecraft:sheep", "minecraft:slime"))
                            && tierSpawns.stream().filter(e -> e.entity().toString().equals("minecraft:cow"))
                            .allMatch(e -> e.weight() == 12 && e.minCount() == 2 && e.maxCount() == 4)
                            && com.strata.domain.MobOps.unionSpawns(List.of()).isEmpty()
                            && com.strata.domain.MobOps.unionSpawns(java.util.Arrays.asList(grassPlains, null)).size() == 2;

                    var all = com.strata.domain.MobOps.fillPlan("biome", "minecraft:plains", List.of(), grassPlains);
                    boolean full = all.size() == 2
                            && all.get(0).entity().equals("minecraft:cow") && all.get(0).weight().equals("12")
                            && all.get(0).minCount().equals("2") && all.get(0).maxCount().equals("4")
                            && all.get(0).category().equals("creature")
                            && all.get(1).entity().equals("minecraft:sheep");

                    var listed = com.strata.domain.MobOps.fillPlan("biome", "minecraft:plains",
                            List.of("minecraft:cow"), grassPlains);
                    boolean unshown = listed.size() == 1 && listed.get(0).entity().equals("minecraft:sheep")
                            && com.strata.domain.MobOps.fillPlan("biome", "minecraft:plains",
                            List.of("minecraft:cow", "minecraft:sheep"), grassPlains).isEmpty()
                            && com.strata.domain.MobOps.fillPlan("global", "", List.of(), grassPlains).isEmpty()
                            && com.strata.domain.MobOps.fillPlan("tier", "", List.of(), grassPlains).isEmpty()
                            && com.strata.domain.MobOps.fillPlan("tier", "t1", List.of(), List.of()).isEmpty();

                    var rows = List.<Map<String, String>>of(
                            Map.of(Schema.MobSpawns.SCOPE, "biome", Schema.MobSpawns.SCOPE_ID, "minecraft:plains",
                                    Schema.MobSpawns.ENTITY, "minecraft:cow", Schema.MobSpawns.VARIANT_NAME, ""),
                            Map.of(Schema.MobSpawns.SCOPE, "biome", Schema.MobSpawns.SCOPE_ID, "minecraft:plains",
                                    Schema.MobSpawns.ENTITY, "minecraft:pig", Schema.MobSpawns.VARIANT_NAME, "pink"),
                            Map.of(Schema.MobSpawns.SCOPE, "tier", Schema.MobSpawns.SCOPE_ID, "t1",
                                    Schema.MobSpawns.ENTITY, "minecraft:horse", Schema.MobSpawns.VARIANT_NAME, ""));
                    var have = com.strata.domain.MobOps.listedInScope("biome", "minecraft:plains", rows);
                    boolean listedHere = have.equals(List.of("minecraft:cow"))
                            && com.strata.domain.MobOps.listedInScope("tier", "t2", rows).isEmpty();

                    Path root = Files.createDirectories(dir.resolve("a44"));
                    Path table = root.resolve(Schema.MobSpawns.FILE);
                    write(root, Schema.MobSpawns.FILE, String.join(",", Schema.MobSpawns.SCOPE,
                            Schema.MobSpawns.SCOPE_ID, Schema.MobSpawns.ENTITY, Schema.MobSpawns.VARIANT_NAME,
                            Schema.MobSpawns.CATEGORY, Schema.MobSpawns.WEIGHT, Schema.MobSpawns.MIN_COUNT,
                            Schema.MobSpawns.MAX_COUNT, Schema.MobSpawns.ENABLED, Schema.MobSpawns.IS_VARIANT,
                            Schema.MobSpawns.ORIGIN) + "\n");
                    var wrote = com.strata.domain.MobOps.fillFromGame(table, "biome", "minecraft:plains", all);
                    var filled = CsvTable.read(table);
                    boolean stamped = wrote.ok() && filled.size() == 2
                            && filled.stream().allMatch(r -> "biome".equals(r.get(Schema.MobSpawns.SCOPE))
                            && "minecraft:plains".equals(r.get(Schema.MobSpawns.SCOPE_ID))
                            && "true".equals(r.get(Schema.MobSpawns.ENABLED))
                            && "false".equals(r.get(Schema.MobSpawns.IS_VARIANT))
                            && Schema.MobSpawns.Origin.DESIGNER.equals(r.get(Schema.MobSpawns.ORIGIN)))
                            && filled.stream().anyMatch(r -> "minecraft:cow".equals(r.get(Schema.MobSpawns.ENTITY))
                            && "12".equals(r.get(Schema.MobSpawns.WEIGHT)));
                    var again = com.strata.domain.MobOps.fillPlan("biome", "minecraft:plains",
                            com.strata.domain.MobOps.listedInScope("biome", "minecraft:plains",
                                    CsvTable.read(table)), grassPlains);
                    var repeat = com.strata.domain.MobOps.fillFromGame(table, "biome", "minecraft:plains", again);
                    boolean idempotent = again.isEmpty() && repeat.refusal() != null
                            && CsvTable.read(table).size() == 2;

                    if (!(union && full && unshown && listedHere && stamped && idempotent)) {
                        lines.add("    A75.1 union=" + union + " full=" + full + " unshown=" + unshown
                                + " listedHere=" + listedHere + " stamped=" + stamped + " idempotent=" + idempotent
                                + " tierUnion=" + tierSpawns + " have=" + have + " filled=" + filled);
                    }
                    return union && full && unshown && listedHere && stamped && idempotent;
                });

        SelfTestHarness.check(lines, tally,
                "A75.2 every declared property routes to the table the loader reads, and names itself once", () -> {
                    List<String> groups = List.of("sounds", "visuals", "physics", "flags",
                            "combat", "particles", "loot_xp", "boss", "pathfinding");
                    boolean files = Schema.MOB_PROPERTY_FILES.size() == groups.size()
                            && groups.stream().allMatch(g -> Schema.mobPropertyFile(g) != null
                            && Schema.mobPropertyFile(g).endsWith(".csv"))
                            && List.copyOf(Schema.MOB_PROPERTY_FILES.values()).size() == groups.size()
                            && groups.stream().noneMatch(g -> "model".equals(g) || "attribute".equals(g) || "attributes".equals(g))
                            && Schema.mobPropertyFile("model") == null
                            && Schema.mobPropertyFile("attribute") == null
                            && Schema.mobPropertyFile("") == null;

                    boolean owned = Schema.mobPropertyFile("sounds").equals(Schema.MobSounds.FILE)
                            && Schema.mobPropertyFile("visuals").equals(Schema.MobVisuals.FILE)
                            && Schema.mobPropertyFile("physics").equals(Schema.MobPhysics.FILE)
                            && Schema.mobPropertyFile("flags").equals(Schema.MobFlags.FILE)
                            && Schema.mobPropertyFile("attributes") == null
                            && Schema.mobPropertyFile("combat").equals(Schema.MobCombat.FILE)
                            && Schema.mobPropertyFile("particles").equals(Schema.MobParticles.FILE)
                            && Schema.mobPropertyFile("loot_xp").equals(Schema.MobLootXp.FILE)
                            && Schema.mobPropertyFile("pathfinding").equals(Schema.MobPathfinding.FILE);

                    boolean unique = Schema.MOB_PROPERTIES.stream().map(Schema.Prop::sheetKey).distinct().count()
                            == Schema.MOB_PROPERTIES.size()
                            && Schema.MOB_PROPERTIES.stream().allMatch(p ->
                            p.column().equals(p.sheetKey().substring(p.group().length() + 1)))
                            && Schema.MOB_PROPERTIES.stream().noneMatch(p -> p.column().isBlank()
                            || p.label().isBlank() || p.kind() == null)
                            && Schema.MOB_PROPERTIES.stream().allMatch(p ->
                            Schema.mobProperty(p.group(), p.column()) == p)
                            && Schema.mobProperty("sounds", Schema.MobFlags.FIRE_IMMUNE) == null
                            && Schema.mobProperty("sounds", "no_such_column") == null
                            && Schema.mobProperties("sounds").stream().noneMatch(p -> !p.group().equals("sounds"))
                            && Schema.mobProperties("not_a_group").isEmpty();

                    Map<String, String> templates = Defaults.templates();
                    boolean inFile = Schema.MOB_PROPERTIES.stream().allMatch(p -> {
                        String header = templates.get(Schema.mobPropertyFile(p.group()));
                        return header != null && java.util.Arrays.asList(header.split(",")).contains(p.column());
                    });

                    var target = new com.strata.domain.MobDataOps.Target("minecraft:cow", "", "", "");
                    var keys = com.strata.domain.MobDataOps.propertyKeys(target);
                    boolean keyCols = Schema.MOB_PROPERTY_KEY_COLUMNS.size() == 5
                            && keys.keySet().equals(new java.util.HashSet<>(Schema.MOB_PROPERTY_KEY_COLUMNS))
                            && "minecraft:cow".equals(keys.get(Schema.MobSounds.ENTITY))
                            && "".equals(keys.get(Schema.MobSounds.VARIANT_NAME))
                            && "false".equals(keys.get(Schema.MobSounds.IS_VARIANT))
                            && "true".equals(com.strata.domain.MobDataOps.propertyKeys(
                            new com.strata.domain.MobDataOps.Target("minecraft:cow", "frosty", "", ""))
                            .get(Schema.MobSounds.IS_VARIANT));

                    boolean extracted = List.of(Schema.MobSounds.AMBIENT_SOUND, Schema.MobSounds.HURT_SOUND,
                            Schema.MobSounds.DEATH_SOUND, Schema.MobSounds.STEP_SOUND,
                            Schema.MobSounds.SWIM_SOUND, Schema.MobSounds.FALL_SOUND).stream()
                            .allMatch(c -> Schema.mobProperty("sounds", c) != null)
                            && Schema.mobProperty("sounds", Schema.MobSounds.ATTACK_SOUND) != null;

                    if (!(files && owned && unique && inFile && keyCols && extracted)) {
                        lines.add("    A75.2 files=" + files + " owned=" + owned + " unique=" + unique
                                + " inFile=" + inFile + " keyCols=" + keyCols + " extracted=" + extracted
                                + " properties=" + Schema.MOB_PROPERTIES.size());
                    }
                    return files && owned && unique && inFile && keyCols && extracted;
                });

        SelfTestHarness.check(lines, tally,
                "A75.3 a property edit lands in its own table, and clearing one leaves its neighbours", () -> {
                    Path root = Files.createDirectories(dir.resolve("a44-write"));
                    var target = new com.strata.domain.MobDataOps.Target("minecraft:cow", "frosty", "tier", "t1");
                    var prop = Schema.mobProperty("sounds", Schema.MobSounds.HURT_SOUND);
                    Path table = root.resolve(Schema.mobPropertyFile("sounds"));
                    write(root, Schema.mobPropertyFile("sounds"), Defaults.MOB_SOUNDS_HEADER);
                    TableWriter.upsert(table, com.strata.domain.MobDataOps.propertyKeys(target),
                            Map.of(Schema.MobSounds.AMBIENT_SOUND, "minecraft:entity.cow.hurt",
                                    prop.column(), "minecraft:entity.cow.hurt"));
                    boolean row = CsvTable.read(table).size() == 1;
                    boolean cleared = TableWriter.updateColumnWhere(table,
                            com.strata.domain.MobDataOps.propertyKeys(target), prop.column(), "");
                    var after = CsvTable.read(table);
                    boolean neighbour = cleared && after.size() == 1
                            && "minecraft:entity.cow.hurt".equals(after.getFirst().get(Schema.MobSounds.AMBIENT_SOUND))
                            && "".equals(after.getFirst().get(prop.column()));
                    boolean keyed = after.size() == 1
                            && "tier".equals(after.getFirst().get(Schema.MobSounds.SCOPE))
                            && "t1".equals(after.getFirst().get(Schema.MobSounds.SCOPE_ID))
                            && "minecraft:cow".equals(after.getFirst().get(Schema.MobSounds.ENTITY))
                            && "frosty".equals(after.getFirst().get(Schema.MobSounds.VARIANT_NAME))
                            && "true".equals(after.getFirst().get(Schema.MobSounds.IS_VARIANT));
                    if (!(row && neighbour && keyed)) {
                        lines.add("    A75.3 row=" + row + " neighbour=" + neighbour + " keyed=" + keyed
                                + " after=" + after);
                    }
                    return row && neighbour && keyed;
                });

        SelfTestHarness.check(lines, tally,
                "A75.4 variance is gone from the column, the field, and the shipped header", () -> {
                    List<String> columns = new ArrayList<>();
                    for (java.lang.reflect.Field each : Schema.Abilities.class.getFields()) {
                        if (each.getType() == String.class && !each.getName().equals("FILE")) {
                            columns.add((String) each.get(null));
                        }
                    }
                    boolean noColumn = !columns.contains("variance");
                    List<String> fields = new ArrayList<>();
                    for (com.strata.model.AbilityField each : com.strata.model.AbilityField.values()) {
                        fields.add(each.key());
                    }
                    boolean noField = !fields.contains("variance");
                    List<String> identity = List.of(Schema.Abilities.VARIANT_NAME, Schema.Abilities.SCOPE,
                            Schema.Abilities.SCOPE_ID, Schema.Abilities.ENABLED);
                    boolean everyColumnNamed = true;
                    for (String column : columns) {
                        everyColumnNamed &= identity.contains(column)
                                || com.strata.model.AbilityField.of(column) != null;
                    }
                    String header = Defaults.templates().entrySet().stream()
                            .filter(e -> e.getKey().equals(Schema.Abilities.FILE))
                            .map(Map.Entry::getValue)
                            .findFirst().orElse("");
                    String columns1 = "";
                    for (String line : header.split("\n", -1)) {
                        String text = line.strip();
                        if (!text.isEmpty() && !text.startsWith("#")) {
                            columns1 = text;
                        }
                    }
                    boolean noHeader = !columns1.isEmpty() && !columns1.contains("variance");
                    boolean sameWidth = !columns1.isEmpty()
                            && columns1.split(",", -1).length == columns.size();
                    if (!(noColumn && noField && everyColumnNamed && noHeader && sameWidth)) {
                        lines.add("    A75.4 noColumn=" + noColumn + " noField=" + noField
                                + " everyColumnNamed=" + everyColumnNamed + " noHeader=" + noHeader
                                + " sameWidth=" + sameWidth
                                + " headerLine=" + columns1
                                + " columns=" + columns + " fields=" + fields);
                    }
                    return noColumn && noField && everyColumnNamed && noHeader && sameWidth;
                });

        SelfTestHarness.check(lines, tally,
                "A75.5 the particle drop is a column, and blank keeps the behaviour it always had", () -> {
                    Path root = Files.createDirectories(dir.resolve("a755-drop"));
                    write(root, Schema.MobVisuals.FILE, Defaults.MOB_VISUALS_HEADER);
                    TableWriter.upsert(root.resolve(Schema.MobVisuals.FILE),
                            Map.of(Schema.MobVisuals.ENTITY, "minecraft:cow",
                                    Schema.MobVisuals.VARIANT_NAME, "frosty",
                                    Schema.MobVisuals.SCOPE, "global",
                                    Schema.MobVisuals.SCOPE_ID, "",
                                    Schema.MobVisuals.IS_VARIANT, "true"),
                            Map.of(Schema.MobVisuals.MODEL, "minecraft:pig",
                                    Schema.MobVisuals.SUPPRESS_BASE_PARTICLES, "false"));
                    var drops = MobPropertyLayers.load(root);
                    var resolved = drops.resolve("minecraft:zombie", "frosty", "", "");
                    boolean read = resolved != null && !resolved.dropsBaseParticles()
                            && Boolean.FALSE.equals(resolved.suppressBaseParticles)
                            && "minecraft:pig".equals(resolved.model);
                    Path root2 = Files.createDirectories(dir.resolve("a755-unsaid"));
                    write(root2, Schema.MobVisuals.FILE, Defaults.MOB_VISUALS_HEADER);
                    TableWriter.upsert(root2.resolve(Schema.MobVisuals.FILE),
                            Map.of(Schema.MobVisuals.ENTITY, "minecraft:sheep",
                                    Schema.MobVisuals.VARIANT_NAME, "silent",
                                    Schema.MobVisuals.SCOPE, "global",
                                    Schema.MobVisuals.SCOPE_ID, "",
                                    Schema.MobVisuals.IS_VARIANT, "true"),
                            Map.of(Schema.MobVisuals.MODEL, "minecraft:cow"));
                    var named = MobPropertyLayers.load(root2).resolve("minecraft:sheep", "silent", "", "");
                    var absent = MobPropertyLayers.load(root2).resolve("minecraft:sheep", "never_written", "", "");
                    boolean blankIsDrop = named != null && named.dropsBaseParticles();
                    boolean noRowIsDrop = absent != null && absent.dropsBaseParticles();
                    boolean staysUnsaid = named != null && named.suppressBaseParticles == null
                            && absent != null && absent.suppressBaseParticles == null;
                    Path root3 = Files.createDirectories(dir.resolve("a755-true"));
                    write(root3, Schema.MobVisuals.FILE, Defaults.MOB_VISUALS_HEADER);
                    TableWriter.upsert(root3.resolve(Schema.MobVisuals.FILE),
                            Map.of(Schema.MobVisuals.ENTITY, "minecraft:pig",
                                    Schema.MobVisuals.VARIANT_NAME, "said_yes",
                                    Schema.MobVisuals.SCOPE, "global",
                                    Schema.MobVisuals.SCOPE_ID, "",
                                    Schema.MobVisuals.IS_VARIANT, "true"),
                            Map.of(Schema.MobVisuals.SUPPRESS_BASE_PARTICLES, "true"));
                    var saidYes = MobPropertyLayers.load(root3).resolve("minecraft:pig", "said_yes", "", "");
                    boolean saidIsDrop = saidYes != null && saidYes.dropsBaseParticles()
                            && Boolean.TRUE.equals(saidYes.suppressBaseParticles);
                    if (!(read && blankIsDrop && noRowIsDrop && staysUnsaid && saidIsDrop)) {
                        lines.add("    A75.5 read=" + read + " blankIsDrop=" + blankIsDrop
                                + " noRowIsDrop=" + noRowIsDrop + " staysUnsaid=" + staysUnsaid
                                + " saidIsDrop=" + saidIsDrop
                                + " resolved=" + resolved + " named=" + named
                                + " absent=" + absent + " saidYes=" + saidYes);
                    }
                    return read && blankIsDrop && noRowIsDrop && staysUnsaid && saidIsDrop;
                });

        SelfTestHarness.check(lines, tally,
                "A75.6 two class rows with no parameter are two rows, and keep their class when copied", () -> {
            var sun = new com.strata.domain.AbilitiesOps.AbilityRow("", "", "", "", "", true, false,
                    "explode", "in_sunlight", "", "", "", "", "", "", "", "", "");
            var hit = new com.strata.domain.AbilitiesOps.AbilityRow("", "", "", "", "", true, false,
                    "explode", "on_hit", "", "", "", "", "", "", "", "", "");
            boolean twoRows = !com.strata.domain.AbilitiesOps.sameRow(sun, hit)
                    && !com.strata.domain.AbilitiesOps.abilityKey("v", "global", "", sun)
                            .equals(com.strata.domain.AbilitiesOps.abilityKey("v", "global", "", hit));
            var copied = sun.with(false, true);
            boolean keepsClass = "explode".equals(copied.effectClass()) && "in_sunlight".equals(copied.trigger())
                    && !copied.enabled() && copied.inherited();
            var heal = new com.strata.domain.AbilitiesOps.AbilityRow("heal", "", "2", "20", "", true);
            boolean legacySame = com.strata.domain.AbilitiesOps.sameRow(heal,
                    new com.strata.domain.AbilitiesOps.AbilityRow("heal", "", "4", "40", "", false));
            if (!(twoRows && keepsClass && legacySame)) {
                lines.add("    A75.6 twoRows=" + twoRows + " keepsClass=" + keepsClass + " legacySame=" + legacySame);
            }
            return twoRows && keepsClass && legacySame;
        });

        SelfTestHarness.check(lines, tally,
                "A75.7 the Entity field is its own column, not the cure", () -> {
            var spawn = new com.strata.domain.AbilitiesOps.AbilityRow("", "", "1", "", "", true, false,
                    "spawn", "on_spawn", "", "", "", "", "", "", "", "", "minecraft:zombie");
            var written = new java.util.LinkedHashMap<String, String>(
                    com.strata.domain.AbilitiesOps.abilityKey("v", "global", "", spawn));
            written.putAll(com.strata.domain.AbilitiesOps.rowValues(spawn));
            boolean ownColumn = "minecraft:zombie".equals(written.get(Schema.Abilities.ENTITY))
                    && written.get(Schema.Abilities.CURE).isEmpty();
            var back = com.strata.domain.AbilitiesOps.rowFrom(written, false);
            boolean roundTrip = "minecraft:zombie".equals(back.entity()) && "spawn".equals(back.effectClass())
                    && "on_spawn".equals(back.trigger());
            boolean okWith = com.strata.domain.AbilitiesOps.classProblem(spawn) == null;
            var noEntity = new com.strata.domain.AbilitiesOps.AbilityRow("", "", "1", "", "", true, false,
                    "spawn", "on_spawn", "", "", "", "", "", "", "", "", "");
            boolean refusedWithout = "needs an entity".equals(com.strata.domain.AbilitiesOps.classProblem(noEntity));
            var noTrigger = new com.strata.domain.AbilitiesOps.AbilityRow("", "", "1", "", "", true, false,
                    "spawn", "", "", "", "", "", "", "", "", "", "minecraft:zombie");
            boolean needsTrigger = "needs a trigger".equals(com.strata.domain.AbilitiesOps.classProblem(noTrigger));
            if (!(ownColumn && roundTrip && okWith && refusedWithout && needsTrigger)) {
                lines.add("    A75.7 ownColumn=" + ownColumn + " roundTrip=" + roundTrip + " okWith=" + okWith
                        + " refusedWithout=" + refusedWithout + " needsTrigger=" + needsTrigger
                        + " written=" + written);
            }
            return ownColumn && roundTrip && okWith && refusedWithout && needsTrigger;
        });

        SelfTestHarness.check(lines, tally,
                "A75.8 a mob's start level is found by scope, and a typed one is checked", () -> {
            List<Map<String, String>> rows = List.of(
                    Map.of(Schema.Variants.NAME, "big_guy", Schema.Variants.SCOPE, "global",
                            Schema.Variants.SCOPE_ID, "", Schema.Variants.START_LEVEL, "5"),
                    Map.of(Schema.Variants.NAME, "big_guy", Schema.Variants.SCOPE, "tier",
                            Schema.Variants.SCOPE_ID, "deep", Schema.Variants.START_LEVEL, "9"));
            var levels = com.strata.runtime.MobScalingSwitch.startLevelIndex(rows);
            boolean global = com.strata.runtime.MobScalingSwitch.startLevel(levels, "big_guy", "", "", "") == 5;
            boolean tier = com.strata.runtime.MobScalingSwitch.startLevel(levels, "big_guy", "", "deep", "") == 9;
            boolean none = com.strata.runtime.MobScalingSwitch.startLevel(levels, "other", "", "", "") == 1;
            boolean checked = com.strata.domain.VariantOps.startLevelProblem("") == null
                    && com.strata.domain.VariantOps.startLevelProblem("3") == null
                    && com.strata.domain.VariantOps.startLevelProblem("0") != null
                    && com.strata.domain.VariantOps.startLevelProblem("x") != null;
            if (!(global && tier && none && checked)) {
                lines.add("    A75.8 global=" + global + " tier=" + tier + " none=" + none + " checked=" + checked);
            }
            return global && tier && none && checked;
        });

        SelfTestHarness.check(lines, tally,
                "A75.9 an effect row's chance is written as typed and refused out of range", () -> {
            var half = new com.strata.domain.AbilitiesOps.AbilityRow("", "minecraft:speed", "", "", "", true, false,
                    "apply_effect", "on_hit", "50", "", "", "", "", "", "", "", "");
            boolean written = "50".equals(com.strata.domain.AbilitiesOps.rowValues(half).get(Schema.Abilities.CHANCE))
                    && com.strata.domain.AbilitiesOps.classProblem(half) == null;
            var over = new com.strata.domain.AbilitiesOps.AbilityRow("", "minecraft:speed", "", "", "", true, false,
                    "apply_effect", "on_hit", "150", "", "", "", "", "", "", "", "");
            boolean refused = com.strata.domain.AbilitiesOps.classProblem(over) != null;
            boolean live = com.strata.domain.AbilitiesOps.usesField("apply_effect", com.strata.model.AbilityField.CHANCE);
            if (!(written && refused && live)) {
                lines.add("    A75.9 written=" + written + " refused=" + refused + " live=" + live);
            }
            return written && refused && live;
        });

        SelfTestHarness.check(lines, tally,
                "A75.10 item, block and batch effects are class rows on the new system", () -> {
            Map<String, String> itemRow = new java.util.HashMap<>(Map.of(
                    Schema.ItemVariantEffects.VARIANT, "frost_blade",
                    Schema.ItemVariantEffects.PARAMETER, "minecraft:slowness",
                    Schema.Abilities.EFFECT_CLASS, "apply_effect",
                    Schema.Abilities.TRIGGER, "permanent"));
            var parsed = com.strata.domain.EffectOps.parse(itemRow);
            boolean itemParses = parsed != null && "permanent".equals(parsed.trigger());
            boolean everySecond = parsed != null && com.strata.runtime.TriggeredEffects.interval(parsed) == 20;
            var batch = new com.strata.domain.BatchOps.Request(com.strata.domain.BatchOps.Field.ABILITIES, "Add",
                    "", "", "", "50", "", "", "", "spawn", "", "1", "", "", "on_spawn", "minecraft:zombie", "");
            var row = batch.abilityRow();
            boolean batchClass = row.isClass() && "spawn".equals(row.effectClass()) && row.ability().isEmpty()
                    && "50".equals(row.chance()) && "minecraft:zombie".equals(row.entity())
                    && com.strata.domain.BatchOps.problem(batch) == null;
            boolean shipsColumns = Defaults.templates().get(Schema.ItemVariantEffects.FILE).contains("effect_class")
                    && Defaults.templates().get(Schema.BlockVariantEffects.FILE).contains("entity");
            if (!(itemParses && everySecond && batchClass && shipsColumns)) {
                lines.add("    A75.10 itemParses=" + itemParses + " everySecond=" + everySecond
                        + " batchClass=" + batchClass + " shipsColumns=" + shipsColumns
                        + " batchProblem=" + com.strata.domain.BatchOps.problem(batch));
            }
            return itemParses && everySecond && batchClass && shipsColumns;
        });

        SelfTestHarness.check(lines, tally,
                "A75.11 old heal, effect, aura and on hit rows read as the class rows they were", () -> {
            java.util.function.Function<Map<String, String>, Map<String, String>> fold =
                    r -> com.strata.domain.EffectOps.legacyAsClass(r, false);
            var heal = fold.apply(Map.of(Schema.Abilities.ABILITY, "heal", Schema.Abilities.AMOUNT, "2",
                    Schema.Abilities.INTERVAL_TICKS, "40"));
            boolean healOk = heal != null && "heal".equals(heal.get(Schema.Abilities.EFFECT_CLASS))
                    && "permanent".equals(heal.get(Schema.Abilities.TRIGGER))
                    && "40".equals(heal.get(Schema.Abilities.INTERVAL_TICKS));
            var forGood = fold.apply(Map.of(Schema.Abilities.ABILITY, "effect", Schema.Abilities.PARAMETER, "minecraft:speed"));
            boolean effectOk = forGood != null && "on_spawn".equals(forGood.get(Schema.Abilities.TRIGGER))
                    && "apply_effect".equals(forGood.get(Schema.Abilities.EFFECT_CLASS));
            var worn = com.strata.domain.EffectOps.legacyAsClass(
                    Map.of(Schema.Abilities.ABILITY, "effect", Schema.Abilities.PARAMETER, "minecraft:speed"), true);
            boolean wornOk = worn != null && "permanent".equals(worn.get(Schema.Abilities.TRIGGER))
                    && "40".equals(worn.get(Schema.Abilities.DURATION));
            var hit = fold.apply(Map.of(Schema.Abilities.ABILITY, "on_hit_effect", Schema.Abilities.PARAMETER, "minecraft:slowness"));
            boolean hitOk = hit != null && "on_hit".equals(hit.get(Schema.Abilities.TRIGGER))
                    && "100".equals(hit.get(Schema.Abilities.DURATION));
            var players = fold.apply(Map.of(Schema.Abilities.ABILITY, "aura_players", Schema.Abilities.PARAMETER, "minecraft:slowness"));
            var spiders = fold.apply(Map.of(Schema.Abilities.ABILITY, "aura_mobs", Schema.Abilities.PARAMETER, "minecraft:haste",
                    Schema.Abilities.CURE, "minecraft:spider"));
            boolean auraOk = players != null && "minecraft:player".equals(players.get(Schema.Abilities.ENTITY))
                    && spiders != null && "minecraft:spider".equals(spiders.get(Schema.Abilities.ENTITY))
                    && spiders.get(Schema.Abilities.CURE).isEmpty();
            boolean kept = fold.apply(Map.of(Schema.Abilities.ABILITY, "sun_immune")) == null
                    && fold.apply(Map.of(Schema.Abilities.ABILITY, "damage_immunity")) == null
                    && fold.apply(Map.of(Schema.Abilities.EFFECT_CLASS, "kill")) == null;
            var choices = com.strata.domain.AbilitiesOps.abilityChoices();
            boolean offered = choices.containsAll(List.of("heal", "aura", "kill"))
                    && !choices.contains("effect") && !choices.contains("aura_players")
                    && !choices.contains("sun_immune") && !choices.contains("sun_weakness")
                    && !choices.contains("damage_immunity");
            if (!(healOk && effectOk && wornOk && hitOk && auraOk && kept && offered)) {
                lines.add("    A75.11 heal=" + healOk + " effect=" + effectOk + " worn=" + wornOk + " hit=" + hitOk
                        + " aura=" + auraOk + " kept=" + kept + " offered=" + offered + " choices=" + choices);
            }
            return healOk && effectOk && wornOk && hitOk && auraOk && kept && offered;
        });

        SelfTestHarness.check(lines, tally,
                "A75.12 every mob property shows in exactly one view, and a row's look is read", () -> {
            var model = new com.strata.domain.MobDataOps.Value(GameLayer.MODEL, "texture", "x", "", "");
            var modelSound = new com.strata.domain.MobDataOps.Value(GameLayer.MODEL, "sound_ambient", "x", "", "");
            var glow = new com.strata.domain.MobDataOps.Value(GameLayer.VISUALS, Schema.MobVisuals.GLOW_TEXTURE, "", "", "");
            var hurt = new com.strata.domain.MobDataOps.Value(GameLayer.SOUNDS, Schema.MobSounds.HURT_SOUND, "x", "", "");
            var volume = new com.strata.domain.MobDataOps.Value(GameLayer.SOUNDS, Schema.MobSounds.HURT_VOLUME, "", "", "");
            boolean visuals = com.strata.domain.MobDataOps.inView(GameLayer.VISUALS, model)
                    && !com.strata.domain.MobDataOps.inView(GameLayer.VISUALS, modelSound)
                    && com.strata.domain.MobDataOps.inView(GameLayer.VISUALS, glow);
            boolean sounds = com.strata.domain.MobDataOps.inView(GameLayer.SOUNDS, hurt)
                    && com.strata.domain.MobDataOps.inView(GameLayer.SOUNDS, volume)
                    && !com.strata.domain.MobDataOps.inView(GameLayer.SOUNDS, modelSound);
            boolean unset = com.strata.domain.MobDataOps.unsetInView(GameLayer.VISUALS, List.of(model)).stream()
                    .anyMatch(v -> v.group().equals(GameLayer.PARTICLES) && v.key().equals(Schema.MobParticles.PARTICLE_TYPE));
            boolean views = com.strata.domain.MobDataOps.SUB_VIEWS.equals(List.of(GameLayer.TYPE, GameLayer.FIELD,
                    GameLayer.TAG, GameLayer.VISUALS, GameLayer.SOUNDS, com.strata.domain.MobDataOps.BODY,
                    GameLayer.COMBAT, GameLayer.LOOT_XP, GameLayer.BOSS))
                    && Schema.MOB_PROPERTIES.stream().allMatch(p -> com.strata.domain.MobDataOps.SUB_VIEWS.stream()
                            .filter(view -> com.strata.domain.MobDataOps.inView(view,
                                    new com.strata.domain.MobDataOps.Value(p.group(), p.column(), "", "", "")))
                            .count() == 1);
            boolean icon = !com.strata.runtime.HudIcons.shows("none") && com.strata.runtime.HudIcons.shows("")
                    && com.strata.runtime.HudIcons.iconOf("") == null && com.strata.runtime.HudIcons.iconOf("none") == null;
            if (!(visuals && sounds && unset && views && icon)) {
                lines.add("    A75.12 visuals=" + visuals + " sounds=" + sounds + " unset=" + unset
                        + " views=" + views + " icon=" + icon);
            }
            return visuals && sounds && unset && views && icon;
        });

    }

    private static void runTestsPart14(Path dir, List<String> lines, int[] tally) throws Exception {
        SelfTestHarness.check(lines, tally,
                "A75.13 a replacement hands a mob's spawn to another, by id, biome over tier over global", () -> {
            var global = com.strata.model.ReplacementPlan.rule("global", "", "minecraft:zombie", "minecraft:husk", "", "");
            var tier = com.strata.model.ReplacementPlan.rule("tier", "deep", "minecraft:zombie", "somemod:ghoul", "50", "all");
            var biome = com.strata.model.ReplacementPlan.rule("biome", "minecraft:desert", "minecraft:zombie",
                    "minecraft:drowned", "", "");
            var all = List.of(global, tier, biome);
            boolean layers = com.strata.model.ReplacementPlan.rulesFor(all, "", "minecraft:plains")
                            .get("minecraft:zombie").to().equals("minecraft:husk")
                    && com.strata.model.ReplacementPlan.rulesFor(all, "deep", "minecraft:plains")
                            .get("minecraft:zombie").to().equals("somemod:ghoul")
                    && com.strata.model.ReplacementPlan.rulesFor(all, "deep", "minecraft:desert")
                            .get("minecraft:zombie").to().equals("minecraft:drowned");
            var whole = com.strata.model.ReplacementPlan.split("minecraft:zombie", 95, global);
            var half = com.strata.model.ReplacementPlan.split("minecraft:zombie", 100, tier);
            boolean split = whole.size() == 1 && whole.getFirst().entity().equals("minecraft:husk")
                    && whole.getFirst().weight() == 95
                    && half.size() == 2 && half.stream().mapToInt(com.strata.model.ReplacementPlan.Piece::weight).sum() == 100
                    && tier.everySource() && !global.everySource();
            boolean refused = com.strata.model.ReplacementPlan.rule("global", "", "minecraft:zombie", "", "", "") == null
                    && com.strata.model.ReplacementPlan.rule("global", "", "minecraft:zombie", "minecraft:zombie", "", "") == null;
            boolean reset = com.strata.domain.MobOps.allResetToGame(List.of(new com.strata.domain.MobOps.ScopedMob(
                    "biome", "minecraft:plains", "minecraft:cow", "")), java.util.Set.of("minecraft:cow"))
                    && !com.strata.domain.MobOps.allResetToGame(List.of(new com.strata.domain.MobOps.ScopedMob(
                    "biome", "minecraft:plains", "minecraft:zombie", "big_guy")), java.util.Set.of("minecraft:zombie"));
            if (!(layers && split && refused && reset)) {
                lines.add("    A75.13 layers=" + layers + " split=" + split + " refused=" + refused + " reset=" + reset
                        + " half=" + half);
            }
            return layers && split && refused && reset;
        });

        SelfTestHarness.check(lines, tally,
                "A75.14 the override sheet edits any JSON by path, keeps its types, and switches an entry off", () -> {
            var json = com.google.gson.JsonParser.parseString("{\"type\":\"somemod:grinding\",\"time\":200,"
                    + "\"ingredients\":[{\"item\":\"minecraft:cobblestone\"}],\"result\":{\"id\":\"minecraft:gravel\","
                    + "\"count\":1},\"loud\":false}").getAsJsonObject();
            var flat = com.strata.model.JsonPaths.flatten(json);
            boolean listed = "200".equals(flat.get("time")) && "minecraft:cobblestone".equals(flat.get("ingredients[0].item"))
                    && "1".equals(flat.get("result.count"));
            var set = com.strata.model.JsonPaths.set(json, "time", "100");
            var flag = com.strata.model.JsonPaths.set(json, "loud", "true");
            boolean typed = set != null && set.get("time").getAsJsonPrimitive().isNumber() && set.get("time").getAsInt() == 100
                    && flag != null && flag.get("loud").getAsBoolean()
                    && com.strata.model.JsonPaths.set(json, "time", "slow") == null
                    && com.strata.model.JsonPaths.set(json, "no_such", "1") == null
                    && json.get("time").getAsInt() == 200;
            var applied = com.strata.data.JsonOverrides.applied(json,
                    new com.strata.data.JsonOverrides.Override(java.util.Map.of("result.count", "4"), true));
            boolean off = applied.getAsJsonObject("result").get("count").getAsInt() == 4
                    && applied.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("type").getAsString()
                            .equals("neoforge:false");
            boolean gone = com.strata.data.JsonOverrides.applied(null, com.strata.data.JsonOverrides.Override.NONE) == null;
            boolean words = com.strata.domain.OverrideOps.typeLabel("minecraft:smelting").equals("Smelting (furnace)")
                    && com.strata.domain.OverrideOps.typeLabel("somemod:grinding").equals("Somemod: Grinding")
                    && com.strata.domain.OverrideOps.summary(json).equals("Gravel from Cobblestone");
            if (!(listed && typed && off && gone && words)) {
                lines.add("    A75.14 listed=" + listed + " typed=" + typed + " off=" + off + " gone=" + gone
                        + " words=" + words + " summary=" + com.strata.domain.OverrideOps.summary(json));
            }
            return listed && typed && off && gone && words;
        });

        SelfTestHarness.check(lines, tally,
                "A75.15 world entries go off the safe way for their kind, and a disabled block's ores go with it", () -> {
            var structure = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:jigsaw\","
                    + "\"biomes\":\"#minecraft:has_structure/village_plains\"}").getAsJsonObject();
            var placed = com.google.gson.JsonParser.parseString("{\"feature\":\"minecraft:ore_iron\",\"placement\":"
                    + "[{\"type\":\"minecraft:count\",\"count\":90}]}").getAsJsonObject();
            var offOverride = new com.strata.data.JsonOverrides.Override(java.util.Map.of(), true);
            var s = com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.STRUCTURE, structure, offOverride);
            var p = com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.PLACED_FEATURE, placed, offOverride);
            boolean safe = s.getAsJsonArray("biomes").isEmpty() && !s.has("neoforge:conditions")
                    && p.getAsJsonArray("placement").get(0).getAsJsonObject().get("count").getAsInt() == 0
                    && p.getAsJsonArray("placement").size() == 2 && !p.has("neoforge:conditions");
            boolean settings = !com.strata.domain.OverrideOps.setOff(com.strata.data.JsonOverrides.STRUCTURE_SET,
                    "minecraft:villages", true).ok();
            var ore = com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:ore\",\"config\":{\"size\":9,"
                    + "\"targets\":[{\"state\":{\"Name\":\"minecraft:iron_ore\"}},{\"state\":{\"Name\":"
                    + "\"minecraft:deepslate_iron_ore\"}}]}}").getAsJsonObject();
            boolean places = com.strata.data.JsonOverrides.placesAny(ore, java.util.Set.of("minecraft:deepslate_iron_ore"))
                    && !com.strata.data.JsonOverrides.placesAny(ore, java.util.Set.of("minecraft:gold_ore"));
            boolean words = com.strata.domain.OverrideOps.fieldName("placement[1].height.min_inclusive.absolute").equals("Lowest Y")
                    && com.strata.domain.OverrideOps.fieldName("config.size").equals("Vein size")
                    && com.strata.domain.OverrideOps.fieldName("placement.spacing").equals("Spacing (chunks)");
            if (!(safe && settings && places && words)) {
                lines.add("    A75.15 safe=" + safe + " settings=" + settings + " places=" + places + " words=" + words);
            }
            return safe && settings && places && words;
        });

        SelfTestHarness.check(lines, tally,
                "A75.16 lists grow and shrink by element, and tags, maps, loot and advancements go off safely", () -> {
            var spawns = com.google.gson.JsonParser.parseString("{\"spawn_overrides\":{\"monster\":{\"spawns\":"
                    + "[{\"type\":\"minecraft:witch\",\"weight\":1}]}}}").getAsJsonObject();
            String el = com.strata.model.JsonPaths.elementOf("spawn_overrides.monster.spawns[0].weight");
            var two = com.strata.model.JsonPaths.duplicate(spawns, el, "");
            var none = two == null ? null : com.strata.model.JsonPaths.remove(two, "spawn_overrides.monster.spawns[0]");
            boolean list = "spawn_overrides.monster.spawns[0]".equals(el) && two != null
                    && two.getAsJsonObject("spawn_overrides").getAsJsonObject("monster").getAsJsonArray("spawns").size() == 2
                    && none != null && none.getAsJsonObject("spawn_overrides").getAsJsonObject("monster")
                            .getAsJsonArray("spawns").size() == 1;
            var fuels = com.google.gson.JsonParser.parseString("{\"values\":{\"minecraft:coal\":{\"burn_time\":1600}}}")
                    .getAsJsonObject();
            var more = com.strata.model.JsonPaths.duplicate(fuels, "values.minecraft:coal", "somemod:peat");
            boolean map = more != null && more.getAsJsonObject("values").has("somemod:peat")
                    && com.strata.model.JsonPaths.duplicate(fuels, "values.minecraft:coal", "minecraft:coal") == null
                    && com.strata.domain.OverrideOps.element("values.minecraft:coal.burn_time").equals("values.minecraft:coal")
                    && com.strata.domain.OverrideOps.element("config.size") == null;
            var off = new com.strata.data.JsonOverrides.Override(java.util.Map.of(), true);
            var tag = com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.ITEM_TAG,
                    com.google.gson.JsonParser.parseString("{\"values\":[\"minecraft:coal\"]}").getAsJsonObject(), off);
            var loot = com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.LOOT_TABLE,
                    com.google.gson.JsonParser.parseString("{\"pools\":[{\"rolls\":1}]}").getAsJsonObject(), off);
            var adv = com.strata.data.JsonOverrides.applied(com.strata.data.JsonOverrides.ADVANCEMENT,
                    com.google.gson.JsonParser.parseString("{\"parent\":\"minecraft:story/root\",\"criteria\":{\"a\":{}}}")
                            .getAsJsonObject(), off);
            boolean offs = tag.getAsJsonArray("values").isEmpty() && tag.get("replace").getAsBoolean()
                    && loot.getAsJsonArray("pools").isEmpty()
                    && adv.getAsJsonObject("criteria").has("never") && adv.has("parent");
            if (!(list && map && offs)) {
                lines.add("    A75.16 list=" + list + " map=" + map + " offs=" + offs);
            }
            return list && map && offs;
        });

        SelfTestHarness.check(lines, tally,
                "WL1 world level: stages climb in order by any/all players and all/any conditions; locks hold below their stage", () -> {
            var s1 = new com.strata.runtime.WorldLevel.Stage(1, "Iron", false, true, 2, 0.25, "add");
            var s2 = new com.strata.runtime.WorldLevel.Stage(2, "Diamond", true, false, 0.5, 0.5, "multiply");
            var s3 = new com.strata.runtime.WorldLevel.Stage(3, "End", false, true, 0.5, 0.01, "scale_level");
            var s4 = new com.strata.runtime.WorldLevel.Stage(4, "Peak", false, true, 1, 0, "add", "while_met", "");
            var stages = new java.util.TreeMap<Integer, com.strata.runtime.WorldLevel.Stage>();
            stages.put(1, s1);
            stages.put(2, s2);
            stages.put(3, s3);
            stages.put(4, s4);
            var rules = new com.strata.runtime.WorldLevel.Rules(stages, List.of(
                    new com.strata.runtime.WorldLevel.Rule(1, "advancement", "minecraft:story/iron_tools", ""),
                    new com.strata.runtime.WorldLevel.Rule(1, "xp_level", "10", ""),
                    new com.strata.runtime.WorldLevel.Rule(2, "mob_kill_level", "20", ""),
                    new com.strata.runtime.WorldLevel.Rule(2, "tier", "Mountains", ""),
                    new com.strata.runtime.WorldLevel.Rule(2, "lock_recipe", "minecraft:diamond_pickaxe", ""),
                    new com.strata.runtime.WorldLevel.Rule(1, "lock_block", "minecraft:iron_ore", ""),
                    new com.strata.runtime.WorldLevel.Rule(3, "days", "1", ""),
                    new com.strata.runtime.WorldLevel.Rule(4, "xp_level", "30", "")));
            var a = new com.strata.runtime.WorldLevel.Facts(List.of("advancement:minecraft:story/iron_tools"), 3, 0);
            var b = new com.strata.runtime.WorldLevel.Facts(List.of(), 12, 25, 40, 3, 9);
            var c = new com.strata.runtime.WorldLevel.Rule(9, "area_level", "30", "");
            var d = new com.strata.runtime.WorldLevel.Rule(9, "kill_count", "5", "");
            var e = new com.strata.runtime.WorldLevel.Rule(9, "days", "9", "");
            boolean facts = b.meets(c) && !a.meets(c) && !b.meets(d) && b.meets(e)
                    && com.strata.runtime.WorldLevel.apply("add", 10, 2) == 12
                    && com.strata.runtime.WorldLevel.apply("subtract", 10, 2) == 8
                    && com.strata.runtime.WorldLevel.apply("multiply", 10, 0.5) == 5
                    && com.strata.runtime.WorldLevel.KINDS.size() == 20;
            // Stage 1: any player, all conditions: a has the advancement, b the level.
            boolean one = rules.reached(0, List.of(a, b)) && !rules.reached(0, List.of(a)) && !rules.reached(0, List.of());
            // Stage 2: all players, any condition: b alone reaches it, a and b together do not.
            boolean two = rules.reached(1, List.of(b)) && !rules.reached(1, List.of(a, b));
            // Stage 4 holds while met: b at XP 35 now reaches it, b back at 12 falls to 3; a kept stage stays.
            b.now(35, 0, List.of());
            int up = rules.settle(3, List.of(b));
            b.now(12, 0, List.of());
            int down = rules.settle(up, List.of(b));
            boolean hold = up == 4 && down == 3 && rules.settle(3, List.of(b)) == 3
                    && rules.levelSource().equals("highest_ever");
            two &= hold;
            // Totals: the start 0/0; stage 1 adds 2 and 0.25; stage 2 multiplies both by 0.5; stage 3 scales levels.
            var t3 = rules.totals(3, 10);
            boolean offsets = rules.mobLevelOffset(0) == 0 && rules.mobLevelOffset(1) == 2 && rules.mobLevelOffset(2) == 1
                    && rules.mobLevelOffset(9) == 2 && rules.playerLevelCoefficient(0) == 0
                    && rules.playerLevelCoefficient(1) == 0.25 && rules.playerLevelCoefficient(2) == 0.125
                    && rules.totals(2, 10).factor() == 1 && Math.abs(t3.factor() - 1.6) < 1e-9
                    && Math.abs(t3.extra(10) - 2.25) < 1e-9 && facts;
            boolean locks = rules.locks("lock_recipe", 1).size() == 1 && rules.locks("lock_recipe", 2).isEmpty()
                    && rules.locks("lock_block", 0).size() == 1 && rules.locks("lock_block", 1).isEmpty();
            var parsed = com.strata.domain.WorldLevelOps.parseRule(3, "lock_tier Mountains minecraft:weakness 1");
            var ops = com.strata.domain.WorldLevelOps.class;
            boolean parse = parsed != null && parsed.kind().equals("lock_tier") && parsed.value().equals("Mountains")
                    && parsed.extra().equals("minecraft:weakness 1")
                    && com.strata.domain.WorldLevelOps.ruleProblem(com.strata.domain.WorldLevelOps.rule(3, "xp_level", "ten", "", "")) != null
                    && com.strata.domain.WorldLevelOps.ruleProblem(com.strata.domain.WorldLevelOps.rule(3, "xp_level", "20", "", "")) == null
                    && com.strata.domain.WorldLevelOps.ruleProblem(com.strata.domain.WorldLevelOps.rule(3, "bogus", "x", "", "")) != null
                    && com.strata.domain.WorldLevelOps.ruleProblem(com.strata.domain.WorldLevelOps.rule(3, "lock_biome", "minecraft:plains", "", "0")) != null
                    && com.strata.domain.WorldLevelOps.ruleProblem(com.strata.domain.WorldLevelOps.rule(3, "lock_biome", "minecraft:plains", "minecraft:weakness", "1")) == null
                    && com.strata.domain.WorldLevelOps.rule(3, "lock_biome", "minecraft:plains", "minecraft:weakness", "1").extra().equals("minecraft:weakness 1")
                    && com.strata.domain.WorldLevelOps.valueType("xp_level").shape() == com.strata.model.ValueType.Shape.WHOLE
                    && com.strata.domain.WorldLevelOps.valueType("lock_recipe").kind() == com.strata.model.ValueType.Registry.RECIPE
                    && com.strata.domain.WorldLevelOps.stageProblem(new com.strata.domain.WorldLevelOps.StageForm("Iron", "any", "all", "2", "0.25", "add")) == null
                    && com.strata.domain.WorldLevelOps.stageProblem(new com.strata.domain.WorldLevelOps.StageForm("Iron", "any", "all", "2", "x", "add")) != null
                    && com.strata.game.RegistryQuery.forKind(com.strata.model.ValueType.Registry.RULE_KIND, null).size() == 20
                    && ops != null;
            if (!(one && two && offsets && locks && parse)) {
                lines.add("    WL1 one=" + one + " two=" + two + " offsets=" + offsets + " locks=" + locks + " parse=" + parse);
            }
            return one && two && offsets && locks && parse;
        });
    }

    private static Resolver resolverFor(Tables.Loaded loaded) {
        return new Resolver(game(), loaded.tiers(), loaded.biomes(), loaded.variants(),
                List.of(), Map.of(), 1.0);
    }

    private static Resolver.GameFacts game() {
        return new Resolver.GameFacts() {
            @Override
            public List<Spawn> spawns(String biome, SpawnCategory category) {
                return List.of();
            }

            @Override
            public List<String> ores(String biome) {
                return biome.equals("minecraft:plains")
                        ? List.of("iron", "coal", "gold")
                        : List.of("iron");
            }
        };
    }

    private static void write(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    /**
     * What ONE effect class reads, compared against a literal set.
     */
    private static boolean readsExactly(String effectClass, Set<AbilityField> expected,
                                        List<String> lines, String caseId) {
        AbilityGuide.ClassEntry guide = AbilityGuide.ofClass(effectClass);
        Set<AbilityField> reads = new HashSet<>();
        for (AbilityField field : AbilityField.values()) {
            if (guide.reads(field)) {
                reads.add(field);
            }
        }
        boolean ok = EffectClass.of(effectClass) != null
                && reads.equals(expected)
                && !reads.contains(AbilityField.ABILITY);
        if (!ok) {
            lines.add("    " + caseId + " " + effectClass + " reads=" + reads
                    + " expected=" + expected + " what=" + guide.what());
        }
        return ok;
    }

    /** groups.csv's header, from the constants rather than a literal, so drift.3 and this agree. */
    private static final String GROUPS_HEADER = String.join(",",
            Schema.Groups.GROUP_ID, Schema.Groups.GROUP_NAME, Schema.Groups.KIND) + "\n";

    /** abilities.csv's header, same reason. */
    private static final String ABILITIES_HEADER = String.join(",",
            Schema.Abilities.VARIANT_NAME, Schema.Abilities.SCOPE, Schema.Abilities.SCOPE_ID,
            Schema.Abilities.ABILITY, Schema.Abilities.PARAMETER, Schema.Abilities.AMOUNT,
            Schema.Abilities.INTERVAL_TICKS, Schema.Abilities.RADIUS, Schema.Abilities.ENABLED,
            Schema.Abilities.EFFECT_CLASS, Schema.Abilities.TRIGGER, Schema.Abilities.CHANCE,
            Schema.Abilities.DAMAGE, Schema.Abilities.DURATION,
            Schema.Abilities.CAN_KILL, Schema.Abilities.DAMAGE_TYPE, Schema.Abilities.PARTICLE,
            Schema.Abilities.HUD_ICON, Schema.Abilities.CURE, Schema.Abilities.ENTITY) + "\n";

    /** One group holding two ability rows, and a variant holding one of its own. */
    private static Path groupFixture(Path dir, String name, String groupName) throws Exception {
        Path root = Files.createDirectories(dir.resolve(name));
        write(root, Schema.Groups.FILE, GROUPS_HEADER);
        write(root, Schema.Abilities.FILE, ABILITIES_HEADER);
        com.strata.domain.GroupOps.create(groupName, "abilities", root);
        String id = com.strata.domain.GroupOps.idFor(groupName);
        write(root, Schema.Abilities.FILE, ABILITIES_HEADER
                + id + ",,,effect,poison,4,,,true,,,,,,,,,,\n"
                + id + ",,,aura_mobs,slime,8,,8,true,,,,,,,,,,\n"
                + "frost_husk,,,effect,speed,1,,,true,,,,,,,,,,\n");
        return root;
    }

    /**
     * The ability rows a file holds for one owner, as ability/parameter=amount, read straight from the file.
     */
    private static List<String> ownAbilities(Path root, String owner) {
        return ownAbilitiesIn(root, owner, null);
    }

    /** The same, for one scope only when a scope is named. */
    private static List<String> ownAbilitiesIn(Path root, String owner, String scope) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(root.resolve(Schema.Abilities.FILE))) {
            if (!owner.equals(row.getOrDefault(Schema.Abilities.VARIANT_NAME, "").strip())) {
                continue;
            }
            if (scope != null
                    && !scope.equals(com.strata.domain.VariantOps.normalScope(
                    row.getOrDefault(Schema.Abilities.SCOPE, "")))) {
                continue;
            }
            out.add(row.getOrDefault(Schema.Abilities.ABILITY, "").strip() + "/"
                    + row.getOrDefault(Schema.Abilities.PARAMETER, "").strip() + "="
                    + row.getOrDefault(Schema.Abilities.AMOUNT, "").strip());
        }
        return out;
    }

    /** loot.csv's header, from the constants rather than a literal, so drift.3 and this agree. */
    private static final String LOOT_HEADER = String.join(",",
            Schema.Loot.SCOPE, Schema.Loot.SCOPE_ID, Schema.Loot.ENTITY, Schema.Loot.VARIANT_NAME,
            Schema.Loot.MODE, Schema.Loot.POOL_INDEX, Schema.Loot.ENTRY_INDEX, Schema.Loot.ITEM,
            Schema.Loot.COUNT_MIN, Schema.Loot.COUNT_MAX, Schema.Loot.CHANCE_PCT,
            Schema.Loot.CONDITION_TYPE, Schema.Loot.CONDITION_VALUE) + "\n";

    /** equipment.csv's header, same reason. */
    private static final String EQUIPMENT_HEADER = String.join(",",
            Schema.Equipment.VARIANT_NAME, Schema.Equipment.SCOPE, Schema.Equipment.SCOPE_ID,
            Schema.Equipment.SLOT, Schema.Equipment.ITEM, Schema.Equipment.CHANCE_PCT,
            Schema.Equipment.MODE, Schema.Equipment.CHANCE_HARD, Schema.Equipment.DROP_PCT,
            Schema.Equipment.ENCHANT_PCT, Schema.Equipment.LOCAL_DIFFICULTY) + "\n";

    /** A loot group of two drops and a variant that already makes one of them. */
    private static Path lootGroupFixture(Path dir, String name, String groupName) throws Exception {
        Path root = Files.createDirectories(dir.resolve(name));
        write(root, Schema.Groups.FILE, GROUPS_HEADER);
        write(root, Schema.Loot.FILE, LOOT_HEADER);
        com.strata.domain.GroupOps.create(groupName, com.strata.domain.GroupOps.KIND_LOOT, root);
        String id = com.strata.domain.GroupOps.idFor(groupName);
        write(root, Schema.Loot.FILE, LOOT_HEADER
                + "global,,," + id + ",add,0,0,minecraft:rotten_flesh,1,1,100,,\n"
                + "global,,," + id + ",add,1,0,minecraft:gunpowder,1,1,100,,\n"
                + "global,,minecraft:strider,frost_husk,add,0,0,minecraft:string,1,1,100,,\n"
                + "global,,minecraft:strider,frost_husk,add,1,0,minecraft:rotten_flesh,2,2,100,,\n");
        return root;
    }

    /** An equipment group of two pieces and a variant that already wears one of them. */
    private static Path equipmentGroupFixture(Path dir, String name, String groupName) throws Exception {
        Path root = Files.createDirectories(dir.resolve(name));
        write(root, Schema.Groups.FILE, GROUPS_HEADER);
        write(root, Schema.Equipment.FILE, EQUIPMENT_HEADER);
        com.strata.domain.GroupOps.create(groupName, com.strata.domain.GroupOps.KIND_EQUIPMENT, root);
        String id = com.strata.domain.GroupOps.idFor(groupName);
        write(root, Schema.Equipment.FILE, EQUIPMENT_HEADER
                + id + ",global,,head,minecraft:diamond_helmet,100,,,,,\n"
                + id + ",global,,feet,minecraft:diamond_boots,100,,,,,\n"
                + "frost_husk,global,,body,minecraft:leather_chestplate,100,,,,,\n"
                + "frost_husk,global,,feet,minecraft:diamond_boots,40,,,,,\n");
        return root;
    }

    /**
     * All THREE kinds of group side by side, each holding its own rows, so a case about which kind is being
     * offered has all three on the table at once.
     */
    private static Path kindFixture(Path dir, String name) throws Exception {
        Path root = Files.createDirectories(dir.resolve(name));
        write(root, Schema.Groups.FILE, GROUPS_HEADER);
        write(root, Schema.Abilities.FILE, ABILITIES_HEADER);
        write(root, Schema.Loot.FILE, LOOT_HEADER);
        write(root, Schema.Equipment.FILE, EQUIPMENT_HEADER);
        com.strata.domain.GroupOps.create("Rot Abilities", com.strata.domain.GroupOps.KIND_ABILITIES, root);
        com.strata.domain.GroupOps.create("Rot Drops", com.strata.domain.GroupOps.KIND_LOOT, root);
        com.strata.domain.GroupOps.create("Rot Gear", com.strata.domain.GroupOps.KIND_EQUIPMENT, root);
        write(root, Schema.Abilities.FILE, ABILITIES_HEADER
                + com.strata.domain.GroupOps.idFor("Rot Abilities") + ",,,effect,poison,4,,,true,,,,,,,,,,\n");
        write(root, Schema.Loot.FILE, LOOT_HEADER
                + "global,,," + com.strata.domain.GroupOps.idFor("Rot Drops")
                + ",add,0,0,minecraft:rotten_flesh,1,1,100,,\n");
        write(root, Schema.Equipment.FILE, EQUIPMENT_HEADER
                + com.strata.domain.GroupOps.idFor("Rot Gear")
                + ",global,,head,minecraft:diamond_helmet,100,,,,,\n");
        return root;
    }

    /** The loot rows one variant holds, as "item@pool@entity", read straight from the file. */
    private static List<String> ownLoot(Path root, String variant) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(root.resolve(Schema.Loot.FILE))) {
            if (!variant.equals(row.getOrDefault(Schema.Loot.VARIANT_NAME, "").strip())) {
                continue;
            }
            out.add(row.getOrDefault(Schema.Loot.ITEM, "").strip() + "@"
                    + row.getOrDefault(Schema.Loot.POOL_INDEX, "").strip() + "@"
                    + row.getOrDefault(Schema.Loot.ENTITY, "").strip());
        }
        return out;
    }

    /** The equipment rows one variant holds, as "slot:item", read straight from the file. */
    private static List<String> ownEquipment(Path root, String variant) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(root.resolve(Schema.Equipment.FILE))) {
            if (!variant.equals(row.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip())) {
                continue;
            }
            out.add(row.getOrDefault(Schema.Equipment.SLOT, "").strip() + ":"
                    + row.getOrDefault(Schema.Equipment.ITEM, "").strip());
        }
        return out;
    }

    /** The chance one variant's piece in one slot is filed at, read straight from the file. */
    private static String chanceOf(Path root, String variant, String slot) {
        for (Map<String, String> row : CsvTable.read(root.resolve(Schema.Equipment.FILE))) {
            if (variant.equals(row.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip())
                    && slot.equals(row.getOrDefault(Schema.Equipment.SLOT, "").strip())) {
                return row.getOrDefault(Schema.Equipment.CHANCE_PCT, "").strip();
            }
        }
        return "";
    }

    /**
     * Writes an upload's bytes WITHOUT the codec's caps, as a hostile client could, and reports whether the
     * capped codec refuses to decode them.
     */
    private static boolean refusedOnDecode(
            net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf,
                    com.strata.network.StrataPayloads.ProfileUpload> codec,
            com.strata.network.StrataPayloads.ProfileUpload upload) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeUtf(upload.name(), 1 << 20);
        buf.writeVarInt(upload.files().size());
        upload.files().forEach((k, v) -> {
            buf.writeUtf(k, 1 << 20);
            buf.writeUtf(v, 1 << 20);
        });
        try {
            codec.decode(buf);
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void deleteRec(Path dir) throws Exception {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }

    // ---- custom asset test files: REAL PNG and Ogg Vorbis bytes (checksums and all), since the rules read
    // them ----

    private static byte[] pngChunk(String type, byte[] data, boolean badCrc) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(12 + data.length);
        b.putInt(data.length).put(type.getBytes(StandardCharsets.ISO_8859_1)).put(data);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(type.getBytes(StandardCharsets.ISO_8859_1));
        crc.update(data);
        b.putInt((int) crc.getValue() ^ (badCrc ? 1 : 0));
        return b.array();
    }

    private static byte[] deflate(byte[] raw) {
        java.util.zip.Deflater d = new java.util.zip.Deflater(9);
        d.setInput(raw);
        d.finish();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    /** A PNG built by hand. */
    private static byte[] pngBuild(int w, int h, int depth, int colour, int interlace, int rawLength,
                                   List<byte[]> extra, boolean badCrc) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        byte[] ihdr = java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) depth).put((byte) colour)
                .put((byte) 0).put((byte) 0).put((byte) interlace).array();
        out.writeBytes(pngChunk("IHDR", ihdr, false));
        if (colour == 3) {
            byte[] pal = new byte[12];
            out.writeBytes(pngChunk("PLTE", pal, false));
        }
        for (byte[] e : extra) {
            out.writeBytes(e);
        }
        out.writeBytes(pngChunk("IDAT", deflate(new byte[rawLength]), badCrc));
        out.writeBytes(pngChunk("IEND", new byte[0], false));
        return out.toByteArray();
    }

    /** A true RGBA PNG of a size (16 to 512, multiples of 16). */
    private static byte[] pngBytes(int w, int h) {
        return pngBuild(w, h, 8, 6, 0, h * (1 + w * 4), List.of(), false);
    }

    /** A true RGBA PNG of random pixels, so that it does not compress away: several 32 KiB chunks long. */
    private static byte[] pngNoise(int w, int h) {
        byte[] raw = new byte[h * (1 + w * 4)];
        new java.util.Random(7).nextBytes(raw);
        for (int y = 0; y < h; y++) {
            raw[y * (1 + w * 4)] = 0;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.writeBytes(pngChunk("IHDR", java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 6)
                .put((byte) 0).put((byte) 0).put((byte) 0).array(), false));
        out.writeBytes(pngChunk("IDAT", deflate(raw), false));
        out.writeBytes(pngChunk("IEND", new byte[0], false));
        return out.toByteArray();
    }

    /** An Ogg page: type flags, granule, serial 7, a sequence number, one body of up to 255 bytes. */
    private static byte[] oggPage(int type, long granule, int sequence, byte[] body, boolean badCrc) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(27 + 1 + body.length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'O', 'g', 'g', 'S', 0, (byte) type}).putLong(granule).putInt(7).putInt(sequence).putInt(0);
        b.put((byte) 1).put((byte) body.length).put(body);
        byte[] page = b.array();
        int crc = com.strata.domain.AssetRules.oggCrc(page, 0, page.length) ^ (badCrc ? 1 : 0);
        page[22] = (byte) crc;
        page[23] = (byte) (crc >>> 8);
        page[24] = (byte) (crc >>> 16);
        page[25] = (byte) (crc >>> 24);
        return page;
    }

    private static byte[] vorbisIdent(int channels, int rate) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(30).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 1).put(new byte[]{'v', 'o', 'r', 'b', 'i', 's'}).putInt(0).put((byte) channels).putInt(rate);
        b.putInt(0).putInt(0).putInt(0).put((byte) 0xB8).put((byte) 1);
        return b.array();
    }

    private static byte[] cat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    /** A small, true Ogg Vorbis file: the identification page, then a last page whose granule is the length. */
    private static byte[] oggBytes(int channels, int rate, long granule) {
        return cat(oggPage(2, 0, 0, vorbisIdent(channels, rate), false), oggPage(4, granule, 1, new byte[4], false));
    }

    /** A mob sheet row holding a class chain. */
    private static GameLayer.Row chain(String mob, String chain) {
        return new GameLayer.Row(mob, GameLayer.TYPE, com.strata.domain.GoalFamilies.CLASS_CHAIN, chain);
    }

    /** Two archetypes of built goals, with the game's values: a cow's AI and a hunting wolf's. */
    private static Map<String, List<com.strata.model.GoalRow>> sampleGoalSets() {
        Map<String, List<com.strata.model.GoalRow>> out = new java.util.LinkedHashMap<>();
        out.put("herbivore", List.of(
                builtRow(false, 0, "FloatGoal"),
                builtRow(false, 1, "PanicGoal", "speed", "2.0"),
                builtRow(false, 3, "TemptGoal", "speed", "1.25", "items", "minecraft:wheat"),
                builtRow(false, 5, "WaterAvoidingRandomStrollGoal", "speed", "1.0")));
        out.put("predator", List.of(
                builtRow(true, 1, "HurtByTargetGoal", "alert_others", "true"),
                builtRow(true, 2, "NearestAttackableTargetGoal", "target",
                        com.strata.model.GoalType.CLASS + "net.minecraft.world.entity.animal.Animal"),
                builtRow(false, 4, "MeleeAttackGoal", "speed", "1.0", "keep_chasing", "true")));
        return out;
    }

    /** A built goal with its type's defaults, then the given settings. */
    private static com.strata.model.GoalRow builtRow(boolean target, int priority, String goal, String... settings) {
        Map<String, String> values = com.strata.model.GoalType.of(goal).defaults();
        for (int i = 0; i + 1 < settings.length; i += 2) {
            values.put(settings[i], settings[i + 1]);
        }
        return new com.strata.model.GoalRow(target, priority, goal, false, Map.copyOf(values),
                com.strata.model.GoalCondition.ALWAYS, true);
    }
    /** Two lists of numbers equal to 1e-9. */
    private static boolean near(List<Double> a, List<Double> b) {
        if (a == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i) - b.get(i)) > 1e-9) {
                return false;
            }
        }
        return true;
    }
    /** A goal sheet row. */
    private static GameLayer.Row goal(String mob, int order, String value) {
        return new GameLayer.Row(mob, GameLayer.GOAL, String.valueOf(order), value);
    }

    /** RS1: Create's and Mekanism's own recipe shapes, as their mods write them. */
    private static boolean machinesRead(List<String> lines) {
        var crush = com.google.gson.JsonParser.parseString("{\"type\":\"create:crushing\",\"ingredients\":[{\"item\":\"minecraft:iron_ore\"}],\"processing_time\":250,\"results\":[{\"id\":\"create:crushed_raw_iron\"},{\"chance\":0.75,\"id\":\"create:experience_nugget\"}]}").getAsJsonObject();
        var mix = com.google.gson.JsonParser.parseString("{\"type\":\"create:mixing\",\"heat_requirement\":\"heated\",\"ingredients\":[{\"tag\":\"c:ingots/copper\"},{\"tag\":\"c:ingots/zinc\"},{\"type\":\"fluid_stack\",\"amount\":250,\"fluid\":\"minecraft:water\"}],\"results\":[{\"count\":2,\"id\":\"create:brass_ingot\"}]}").getAsJsonObject();
        var wheel = com.google.gson.JsonParser.parseString("{\"type\":\"create:mechanical_crafting\",\"key\":{\"A\":{\"item\":\"create:andesite_alloy\"},\"P\":{\"tag\":\"minecraft:planks\"}},\"pattern\":[\" A \",\"APA\",\" A \"],\"result\":{\"count\":2,\"id\":\"create:crushing_wheel\"}}").getAsJsonObject();
        var seq = com.google.gson.JsonParser.parseString("{\"type\":\"create:sequenced_assembly\",\"ingredient\":{\"tag\":\"c:plates/gold\"},\"loops\":5,\"results\":[{\"chance\":120,\"id\":\"create:precision_mechanism\"},{\"chance\":8,\"id\":\"create:golden_sheet\"}],\"sequence\":[{\"type\":\"create:deploying\",\"ingredients\":[{\"item\":\"create:incomplete_precision_mechanism\"},{\"item\":\"create:cogwheel\"}],\"results\":[{\"id\":\"create:incomplete_precision_mechanism\"}]}]}").getAsJsonObject();
        var purify = com.google.gson.JsonParser.parseString("{\"type\":\"mekanism:purifying\",\"chemical_input\":{\"amount\":1,\"chemical\":\"mekanism:oxygen\"},\"item_input\":{\"count\":1,\"tag\":\"c:ores/iron\"},\"output\":{\"count\":3,\"id\":\"mekanism:clump_iron\"}}").getAsJsonObject();
        var src = com.strata.domain.RecipeOps.Source.GAME;
        var nugget = com.strata.domain.RecipeOps.machine("c", "create:crushing", crush, src, "create:experience_nugget");
        var brass = com.strata.domain.RecipeOps.machine("m", "create:mixing", mix, src, "create:brass_ingot");
        var w = com.strata.domain.RecipeOps.machine("w", "create:mechanical_crafting", wheel, src, "");
        var p = com.strata.domain.RecipeOps.machine("s", "create:sequenced_assembly", seq, src, "create:precision_mechanism");
        var clump = com.strata.domain.RecipeOps.machine("p", "mekanism:purifying", purify, src, "");
        String seqWords = com.strata.domain.RecipeOps.machineWords(seq, "create:precision_mechanism");
        String purifyWords = com.strata.domain.RecipeOps.machineWords(purify, "mekanism:clump_iron");
        String mixWords = com.strata.domain.RecipeOps.machineWords(mix, "create:brass_ingot");
        boolean ok = nugget != null && nugget.result().equals("create:experience_nugget")
                && com.strata.domain.RecipeOps.machineWords(crush, "create:experience_nugget").contains("75.0% create:experience_nugget")
                && brass != null && brass.count().equals("2") && brass.cells().subList(0, 2).equals(List.of("#c:ingots/copper", "#c:ingots/zinc"))
                && mixWords.contains("250 mB minecraft:water") && mixWords.contains("heated")
                && w != null && w.cells().equals(List.of("", "create:andesite_alloy", "", "create:andesite_alloy", "#minecraft:planks",
                        "create:andesite_alloy", "", "create:andesite_alloy", ""))
                && p != null && p.cells().subList(0, 2).equals(List.of("#c:plates/gold", "create:cogwheel"))
                && seqWords.contains("5 loops") && seqWords.contains("93.8% create:precision_mechanism")
                && clump != null && clump.cells().get(4).equals("#c:ores/iron") && clump.count().equals("3")
                && purifyWords.contains("takes 1 mekanism:oxygen")
                && otherMods(lines);
        if (!ok) {
            lines.add("    RS1 machines nugget=" + nugget + " brass=" + brass + " wheel=" + w + " seq=" + p + " [" + seqWords
                    + "] clump=" + clump + " [" + purifyWords + "] [" + mixWords + "]");
        }
        return ok;
    }

    /** RS1: AE2's, Immersive Engineering's and Farmer's Delight's recipe shapes. */
    private static boolean otherMods(List<String> lines) {
        var press = com.google.gson.JsonParser.parseString("{\"type\":\"ae2:inscriber\",\"ingredients\":{\"bottom\":\"ae2:printed_silicon\",\"middle\":\"minecraft:redstone\",\"top\":\"ae2:printed_logic_processor\"},\"mode\":\"press\",\"result\":{\"id\":\"ae2:logic_processor\"}}").getAsJsonObject();
        var entropy = com.google.gson.JsonParser.parseString("{\"type\":\"ae2:entropy\",\"input\":{\"fluid\":{\"id\":\"minecraft:water\"}},\"mode\":\"cool\",\"output\":{\"block\":{\"id\":\"minecraft:ice\"}}}").getAsJsonObject();
        var crusher = com.google.gson.JsonParser.parseString("{\"type\":\"immersiveengineering:crusher\",\"energy\":6000,\"input\":{\"tag\":\"c:ores/iron\"},\"result\":{\"basePredicate\":{\"tag\":\"c:dusts/iron\"},\"count\":2},\"secondaries\":[{\"chance\":0.1,\"conditions\":[],\"output\":{\"tag\":\"c:dusts/nickel\"}}]}").getAsJsonObject();
        var arc = com.google.gson.JsonParser.parseString("{\"type\":\"immersiveengineering:arc_furnace\",\"additives\":[{\"tag\":\"c:dusts/coal_coke\"}],\"energy\":204800,\"input\":{\"tag\":\"c:ingots/iron\"},\"results\":[{\"tag\":\"c:ingots/steel\"}],\"slag\":{\"tag\":\"c:slag\"},\"time\":400}").getAsJsonObject();
        var cut = com.google.gson.JsonParser.parseString("{\"type\":\"farmersdelight:cutting\",\"ingredients\":[{\"item\":\"farmersdelight:cabbage\"}],\"result\":[{\"item\":{\"count\":2,\"id\":\"farmersdelight:cabbage_leaf\"}}],\"tool\":[{\"type\":\"farmersdelight:item_ability\",\"action\":\"knife_dig\"},{\"tag\":\"c:tools/knife\"}]}").getAsJsonObject();
        var m = com.strata.domain.MachineRecipes.read(press);
        var e = com.strata.domain.MachineRecipes.read(entropy);
        var c = com.strata.domain.MachineRecipes.read(crusher);
        var a = com.strata.domain.MachineRecipes.read(arc);
        var k = com.strata.domain.MachineRecipes.read(cut);
        String cw = com.strata.domain.MachineRecipes.words(c);
        String aw = com.strata.domain.MachineRecipes.words(a);
        String kw = com.strata.domain.MachineRecipes.words(k);
        var steel = com.strata.domain.MachineRecipes.resolve(a, t -> "#c:ingots/steel".equals(t) ? "x:steel_ingot" : "");
        boolean ok = m.ins().stream().map(com.strata.domain.MachineRecipes.In::id).toList()
                        .equals(List.of("ae2:printed_logic_processor", "minecraft:redstone", "ae2:printed_silicon"))
                && m.outs().get(0).id().equals("ae2:logic_processor") && com.strata.domain.MachineRecipes.words(m).contains("mode press")
                && e.outs().get(0).id().equals("minecraft:ice") && com.strata.domain.MachineRecipes.words(e).contains("minecraft:water")
                && c.outs().get(0).id().equals("#c:dusts/iron") && c.outs().get(0).count().equals("2")
                && cw.contains("10.0% #c:dusts/nickel") && cw.contains("6000 energy")
                && a.ins().stream().map(com.strata.domain.MachineRecipes.In::id).toList().equals(List.of("#c:dusts/coal_coke", "#c:ingots/iron"))
                && aw.contains("#c:slag") && aw.contains("20.0 s")
                && steel.outs().get(0).id().equals("x:steel_ingot")
                && k.outs().get(0).id().equals("farmersdelight:cabbage_leaf") && k.outs().get(0).count().equals("2")
                && kw.contains("needs #c:tools/knife") && k.ins().size() == 1;
        if (!ok) {
            lines.add("    RS1 other mods press=" + m + " entropy=" + e + " crusher=" + c + " [" + cw + "] arc=" + a + " [" + aw
                    + "] cut=" + k + " [" + kw + "]");
        }
        return ok;
    }
}
