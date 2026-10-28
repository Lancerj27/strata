package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.domain.SpecialSpawnsOps;
import com.strata.model.SpecialSpawners;
import com.strata.runtime.ReplaceSpawnRules;
import com.strata.runtime.SpecialSpawnsRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SPECIAL MOBS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class SpecialSpawnGameTests {

    private SpecialSpawnGameTests() {
    }

    /** The world runs Strata's five spawners in place of the game's. */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void strataRunsTheSpecialSpawners(GameTestHelper helper) {
        List<String> names = new ArrayList<>();
        try {
            var field = net.minecraft.server.level.ServerLevel.class.getDeclaredField("customSpawners");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<CustomSpawner> spawners = (List<CustomSpawner>) field.get(helper.getLevel());
            spawners.forEach(s -> names.add(s.getClass().getSimpleName()));
        } catch (ReflectiveOperationException e) {
            helper.fail("Could not read the world's spawners: " + e);
            return;
        }
        List<String> want = List.of("StrataPhantomSpawner", "StrataPatrolSpawner", "StrataCatSpawner",
                "StrataVillageSiege", "StrataWanderingTraderSpawner");
        helper.assertTrue(names.containsAll(want), "The world should run Strata's spawners: " + names);
        helper.succeed();
    }

    /** A Replace tier with no mobs gives its biomes no spawn list at all. */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void emptyReplaceTierSpawnsNoMobs(GameTestHelper helper) {
        MobSpawnSettings badlands = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(Biomes.BADLANDS).value().getMobSettings();
        List<String> left = new ArrayList<>();
        for (MobCategory category : MobCategory.values()) {
            badlands.getMobs(category).unwrap().forEach(d -> left.add(category.getName() + " " + d.type.toShortString()));
        }
        helper.assertTrue(left.isEmpty(), "An empty Replace tier's badlands should spawn nothing: " + left);
        helper.succeed();
    }

    /**
     * Special mob spawns Off in a biome: a Special mob (the phantom, a cat, a villager's golem) is refused
     * there, a Normal one (a skeleton) is not, and the spawners stop; On again: they run.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "specialspawns")
    public static void specialOffStopsSpecialMobs(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        String biome = helper.getLevel().getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        var special = SpecialSpawners.setting(SpecialSpawners.SPECIAL, "enabled");
        var level = helper.getLevel();
        try {
            boolean before = SpecialSpawnsRuntime.runs(SpecialSpawners.PHANTOMS, level, at, EntityType.PHANTOM)
                    && !ReplaceSpawnRules.blocked(level, at, EntityType.CAT, MobSpawnType.NATURAL);
            SpecialSpawnsOps.save("biome", biome, special, "Off");
            boolean off = !SpecialSpawnsRuntime.runs(SpecialSpawners.PHANTOMS, level, at, EntityType.PHANTOM)
                    && !SpecialSpawnsRuntime.runs(SpecialSpawners.PATROLS, level, at, EntityType.PILLAGER)
                    && ReplaceSpawnRules.blocked(level, at, EntityType.PHANTOM, MobSpawnType.NATURAL)
                    && ReplaceSpawnRules.blocked(level, at, EntityType.WANDERING_TRADER, MobSpawnType.EVENT)
                    && !ReplaceSpawnRules.blocked(level, at, EntityType.SKELETON, MobSpawnType.NATURAL);
            IronGolem golem = EntityType.IRON_GOLEM.create(level);
            golem.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(golem, level, level.getCurrentDifficultyAt(at),
                    MobSpawnType.MOB_SUMMONED, null);
            boolean golemRefused = golem.isSpawnCancelled();
            golem.discard();
            helper.assertTrue(before && off && golemRefused, "Special mob spawns Off in " + biome
                    + ": before " + before + ", off " + off + ", golem refused " + golemRefused);
        } finally {
            SpecialSpawnsOps.restore("biome", biome, special);
        }
        helper.assertTrue(SpecialSpawnsRuntime.runs(SpecialSpawners.PHANTOMS, level, at, EntityType.PHANTOM),
                "Restored, the phantom spawner should run again");
        helper.succeed();
    }

    /**
     * A phantom set to Normal: its spawner stops making it, it gets no spawn row of its own (its lists decide
     * where it spawns), and a night rule beside the game's days-awake rule.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "phantomnormal")
    public static void phantomSetToNormalLeavesItsSpawner(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        String phantom = SpecialSpawners.PHANTOM;
        try {
            SpecialSpawnsOps.setSpawnAs(phantom, "Normal");
            boolean stopped = !SpecialSpawnsRuntime.runs(SpecialSpawners.PHANTOMS, helper.getLevel(), at,
                    EntityType.PHANTOM);
            boolean noRow = CsvTable.read(Paths.table(Schema.MobSpawns.FILE)).stream().noneMatch(r ->
                    "global".equals(r.get(Schema.MobSpawns.SCOPE)) && phantom.equals(r.get(Schema.MobSpawns.ENTITY)));
            List<String> rules = CsvTable.read(Paths.table(Schema.SpawnRules.FILE)).stream()
                    .filter(r -> phantom.equals(r.get(Schema.SpawnRules.ENTITY)))
                    .map(r -> r.get(Schema.SpawnRules.RULE_TYPE) + " " + r.get(Schema.SpawnRules.VALUE)).toList();
            helper.assertTrue(stopped && noRow && rules.contains("time_since_rest 3+") && rules.contains("time dark")
                            && rules.contains("surface surface"),
                    "A Normal phantom: spawner stopped " + stopped + ", no row " + noRow + ", rules " + rules);
        } finally {
            SpecialSpawnsOps.setSpawnAs(phantom, "Special");
            TableWriter.delete(Schema.SpecialSpawns.FILE, Map.of(Schema.SpecialSpawns.SCOPE, "",
                    Schema.SpecialSpawns.SPAWNER, SpecialSpawners.MOBS, Schema.SpecialSpawns.SETTING, phantom));
            for (String[] rule : SpecialSpawnsOps.PHANTOM_NORMAL_RULES) {
                TableWriter.delete(Schema.SpawnRules.FILE, Map.of(Schema.SpawnRules.SCOPE, "global",
                        Schema.SpawnRules.ENTITY, phantom, Schema.SpawnRules.RULE_TYPE, rule[0]));
            }
            Config.reload();
        }
        helper.succeed();
    }

    /** THE GAME'S OWN CONDITION, FROM ITS CODE, AND THE DEFAULT PROFILE. */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void gameConditionsAndDefaultProfile(GameTestHelper helper) {
        List<Integer> constants = com.strata.runtime.GameCode.constants(
                "net/minecraft/world/level/levelgen/PhantomSpawner", "tick");
        int days = com.strata.runtime.GameCode.phantomRestDays();
        boolean seeded = CsvTable.read(Paths.table(Schema.SpawnRules.FILE)).stream().anyMatch(r ->
                "global".equals(r.get(Schema.SpawnRules.SCOPE)) && SpecialSpawners.PHANTOM.equals(r.get(Schema.SpawnRules.ENTITY))
                        && "time_since_rest".equals(r.get(Schema.SpawnRules.RULE_TYPE)) && "3+".equals(r.get(Schema.SpawnRules.VALUE)));
        java.nio.file.Path profile = Paths.profile(com.strata.data.ProfileManager.DEFAULT);
        String rules = "";
        String settings = "";
        try {
            rules = java.nio.file.Files.readString(profile.resolve(Schema.SpawnRules.FILE));
            settings = java.nio.file.Files.readString(profile.resolve("strata-common.toml"));
        } catch (java.io.IOException e) {
            helper.fail("The Default profile could not be read: " + e);
        }
        boolean vanilla = rules.contains("global,,minecraft:phantom,,time_since_rest,3+")
                // Vanilla by having no rows, not by switches Off: an edit forked from Default must work.
                && !settings.replace(" ", "").contains("enableLoot=false")
                && !settings.replace(" ", "").contains("enableMobScaling=false")
                // The shipped defaults: the game's own spawners, trade lists and attributes, untouched.
                && settings.replace(" ", "").contains("enableSpecialSpawners=false")
                && settings.replace(" ", "").contains("enableTrades=false")
                && settings.replace(" ", "").contains("enableExtraMobAttributes=false")
                && java.nio.file.Files.isRegularFile(profile.resolve(Schema.MobSpawns.FILE));
        boolean protectedProfile = com.strata.data.ProfileManager.exportProfile("Default")
                == com.strata.data.ProfileManager.Result.PROTECTED
                && com.strata.data.ProfileManager.deleteProfile("Default") == com.strata.data.ProfileManager.Result.PROTECTED;
        helper.assertTrue(constants.contains(72000) && days == 3 && seeded && vanilla && protectedProfile,
                "Read " + constants + " -> " + days + " day(s); seeded " + seeded + "; Default vanilla " + vanilla
                        + "; protected " + protectedProfile);
        helper.succeed();
    }

    /** A PHANTOM ON A LIST WAITS FOR ITS CONDITION. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "phantomlist")
    public static void listedPhantomWaitsForItsCondition(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        Map<String, String> row = Map.of(Schema.MobSpawns.SCOPE, "global", Schema.MobSpawns.SCOPE_ID, "",
                Schema.MobSpawns.ENTITY, SpecialSpawners.PHANTOM, Schema.MobSpawns.VARIANT_NAME, "");
        TableWriter.upsert(Schema.MobSpawns.FILE, row, Map.of(Schema.MobSpawns.CATEGORY, "monster",
                Schema.MobSpawns.WEIGHT, "10", Schema.MobSpawns.MIN_COUNT, "1", Schema.MobSpawns.MAX_COUNT, "1",
                Schema.MobSpawns.ENABLED, "true", Schema.MobSpawns.IS_VARIANT, "false",
                Schema.MobSpawns.ORIGIN, "designer"));
        Config.reload();
        try {
            List<String> placed = new ArrayList<>();
            for (MobSpawnType type : List.of(MobSpawnType.CHUNK_GENERATION, MobSpawnType.NATURAL)) {
                var phantom = EntityType.PHANTOM.create(helper.getLevel());
                phantom.moveTo(at.getX() + 0.5, at.getY() + 10, at.getZ() + 0.5);
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(phantom, helper.getLevel(),
                        helper.getLevel().getCurrentDifficultyAt(at), type, null);
                if (!phantom.isSpawnCancelled()) {
                    placed.add(type.name());
                }
                phantom.discard();
            }
            helper.assertTrue(placed.isEmpty(), "A listed phantom should wait for a player awake 3 days: placed " + placed);
        } finally {
            TableWriter.delete(Schema.MobSpawns.FILE, row);
            Config.reload();
        }
        helper.succeed();
    }

    /** THE GAME'S DAY AND THE PATROLS' CONDITIONS. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "daylight")
    public static void gameDaylightAndPatrolRules(GameTestHelper helper) {
        var level = helper.getLevel();
        long before = level.getDayTime();
        int[] window = com.strata.model.SpawnRule.gameDaylight;
        List<String> wrong = new ArrayList<>();
        try {
            level.setWeatherParameters(6000, 0, false, false);
            for (int[] probe : new int[][]{{window[0] - 1, 0}, {window[0], 1}, {window[1], 1}, {window[1] + 1, 0},
                    {6000, 1}, {18000, 0}}) {
                level.setDayTime(Math.floorMod(probe[0], 24000));
                level.updateSkyBrightness();
                if (level.isDay() != (probe[1] == 1)) {
                    wrong.add(probe[0] + (level.isDay() ? " is day" : " is night"));
                }
            }
        } finally {
            level.setDayTime(before);
            level.updateSkyBrightness();
        }
        int day = com.strata.runtime.GameCode.patrolFromDay();
        List<String> rules = CsvTable.read(Paths.table(Schema.SpawnRules.FILE)).stream()
                .filter(r -> SpecialSpawners.PILLAGER.equals(r.get(Schema.SpawnRules.ENTITY)))
                .map(r -> r.get(Schema.SpawnRules.RULE_TYPE) + " " + r.get(Schema.SpawnRules.VALUE)).toList();
        helper.assertTrue(wrong.isEmpty() && day == 5 && rules.contains("world_day 5+") && rules.contains("time daylight"),
                "Daylight " + window[0] + " to " + window[1] + ", wrong " + wrong + "; patrol day " + day
                        + "; pillager rules " + rules);
        helper.succeed();
    }

    /** THE GAME'S OWN SPAWN CHECKS HOLD FOR A MOB ON A STRATA LIST. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "gamechecks")
    public static void gameChecksHoldOnStrataLists(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        String biome = level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        List<Map<String, String>> rows = new ArrayList<>();
        for (String[] mob : new String[][]{{"minecraft:zombie", "monster"}, {"minecraft:cow", "creature"}}) {
            Map<String, String> row = Map.of(Schema.MobSpawns.SCOPE, "biome", Schema.MobSpawns.SCOPE_ID, biome,
                    Schema.MobSpawns.ENTITY, mob[0], Schema.MobSpawns.VARIANT_NAME, "");
            TableWriter.upsert(Schema.MobSpawns.FILE, row, Map.of(Schema.MobSpawns.CATEGORY, mob[1],
                    Schema.MobSpawns.WEIGHT, "50", Schema.MobSpawns.MIN_COUNT, "1", Schema.MobSpawns.MAX_COUNT, "1",
                    Schema.MobSpawns.ENABLED, "true", Schema.MobSpawns.IS_VARIANT, "false",
                    Schema.MobSpawns.ORIGIN, "designer"));
            rows.add(row);
        }
        Config.reload();
        List<String> wrong = new ArrayList<>();
        try {
            var random = level.getRandom();
            level.setBlock(at.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
            if (net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityType.ZOMBIE, level,
                    MobSpawnType.NATURAL, at, random)) wrong.add("a zombie in full light");
            if (net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityType.COW, level,
                    MobSpawnType.NATURAL, at, random)) wrong.add("a cow on stone");
            level.setBlock(at.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            if (!net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityType.COW, level,
                    MobSpawnType.NATURAL, at, random)) wrong.add("no cow on lit grass");
        } finally {
            level.removeBlock(at.above(), false);
            rows.forEach(r -> TableWriter.delete(Schema.MobSpawns.FILE, r));
            Config.reload();
        }
        helper.assertTrue(wrong.isEmpty(), "The game's checks in " + biome + ": " + wrong);
        helper.succeed();
    }

    /** EVERY MOB KEEPS THE GAME'S SPAWN CHECK. */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void everyMobKeepsTheGameSpawnCheck(GameTestHelper helper) {
        List<com.strata.runtime.GameCode.SpawnCheck> checks = com.strata.runtime.GameCode.spawnChecks();
        List<String> wrong = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        java.util.Set<net.minecraft.world.entity.EntityType<?>> registered = new java.util.HashSet<>();
        var rules = com.strata.data.Tables.loadSpawnRulesByRow(Paths.table(Schema.SpawnRules.FILE).getParent());
        for (var c : checks) {
            try {
                var type = (net.minecraft.world.entity.EntityType<?>) net.minecraft.world.entity.EntityType.class
                        .getField(c.typeField()).get(null);
                registered.add(type);
                var placement = net.minecraft.world.entity.SpawnPlacementTypes.class.getField(c.placement()).get(null);
                var height = net.minecraft.world.level.levelgen.Heightmap.Types.valueOf(c.heightmap());
                String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                var ownPlacement = com.strata.runtime.SpawnPlacementEdits.originalPlacement(type);
                var ownHeight = com.strata.runtime.SpawnPlacementEdits.originalHeightmap(type);
                if ((ownPlacement != null ? ownPlacement : net.minecraft.world.entity.SpawnPlacements.getPlacementType(type)) != placement
                        || (ownHeight != null ? ownHeight : net.minecraft.world.entity.SpawnPlacements.getHeightmapType(type)) != height) {
                    wrong.add(id);
                }
                lines.add(String.join("\t", id, type.getCategory().getName(), c.placement(), c.heightmap(), c.check(),
                        String.valueOf(rules.getOrDefault("global||" + id, List.of())),
                        SpecialSpawnsOps.special(id) ? "Special" : "Normal"));
            } catch (ReflectiveOperationException | RuntimeException e) {
                wrong.add(c.typeField() + " (" + e + ")");
            }
        }
        for (var type : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
            var sample = type.create(helper.getLevel());
            boolean mob = sample instanceof net.minecraft.world.entity.Mob;
            if (sample != null) {
                sample.discard();
            }
            if (mob && !registered.contains(type)) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                if (id.equals("strata:gt_own_zombie") && (!net.minecraft.world.entity.SpawnPlacements.hasPlacement(type)
                        || net.minecraft.world.entity.SpawnPlacements.getPlacementType(type)
                        != net.minecraft.world.entity.SpawnPlacements.getPlacementType(EntityType.ZOMBIE))) {
                    wrong.add(id + " lacks the zombie's check");
                }
                lines.add(String.join("\t", id, type.getCategory().getName(), "-", "-", "(none)",
                        String.valueOf(rules.getOrDefault("global||" + id, List.of())),
                        SpecialSpawnsOps.special(id) ? "Special" : "Normal"));
            }
        }
        try {
            java.nio.file.Files.write(com.strata.data.Paths.ROOT.resolve("spawn-checks-report.txt"), lines);
        } catch (java.io.IOException e) {
            wrong.add("the report (" + e + ")");
        }
        helper.assertTrue(checks.size() > 50 && wrong.isEmpty(),
                "Read " + checks.size() + " spawn checks; not the game's: " + wrong);
        helper.succeed();
    }

    /** A MOB THE GAME GAVE NO SPAWN CHECK GETS ITS CLASS CHAIN'S, ON A LIST ONLY. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "classchecks")
    public static void mobsWithNoGameCheckGetTheirKinds(GameTestHelper helper) {
        var level = helper.getLevel();
        var random = level.getRandom();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        List<String> wrong = new ArrayList<>();
        List<String> mobs = List.of("minecraft:zoglin", "minecraft:bee", "minecraft:tadpole");
        try {
            level.setBlock(at.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
            java.util.function.BiPredicate<EntityType<?>, MobSpawnType> check = (type, spawnType) ->
                    net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(type, level, spawnType, at, random);
            // No Strata row: the game decides alone, and it has no check for these.
            if (!check.test(EntityType.ZOGLIN, MobSpawnType.NATURAL)) wrong.add("a zoglin with no row was checked");
            // A Strata row spawns them: each gets the check of its kind.
            for (String mob : mobs) {
                TableWriter.upsert(Schema.MobSpawns.FILE, Map.of(Schema.MobSpawns.SCOPE, "global", Schema.MobSpawns.SCOPE_ID, "",
                        Schema.MobSpawns.ENTITY, mob, Schema.MobSpawns.VARIANT_NAME, ""),
                        Map.of(Schema.MobSpawns.CATEGORY, "monster", Schema.MobSpawns.WEIGHT, "1",
                                Schema.MobSpawns.ENABLED, "true", Schema.MobSpawns.IS_VARIANT, "false"));
            }
            Config.reload();
            if (check.test(EntityType.ZOGLIN, MobSpawnType.NATURAL)) wrong.add("a zoglin in full light");
            if (check.test(EntityType.BEE, MobSpawnType.NATURAL)) wrong.add("a bee on stone");
            if (check.test(EntityType.TADPOLE, MobSpawnType.NATURAL)) wrong.add("a tadpole on land");
            if (!check.test(EntityType.BREEZE, MobSpawnType.TRIAL_SPAWNER)) wrong.add("no breeze from a trial spawner");
            level.setBlock(at.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            if (!check.test(EntityType.BEE, MobSpawnType.NATURAL)) wrong.add("no bee on lit grass");
        } finally {
            level.removeBlock(at.above(), false);
            for (String mob : mobs) {
                TableWriter.delete(Schema.MobSpawns.FILE, Map.of(Schema.MobSpawns.SCOPE, "global",
                        Schema.MobSpawns.SCOPE_ID, "", Schema.MobSpawns.ENTITY, mob, Schema.MobSpawns.VARIANT_NAME, ""));
            }
            Config.reload();
        }
        helper.assertTrue(wrong.isEmpty(), "Mobs with no game check: " + wrong);
        helper.succeed();
    }
}