package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.Schema;
import com.strata.data.StrataConfig;
import com.strata.data.TableWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A REPLACE TIER'S SPAWNS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class SpawnGameTests {

    private SpawnGameTests() {
    }

    /** The skeleton also fills the passive and the other empty slots ("Hostile mobs spawn in passive slots"). */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void replaceSkeletonTakesTheEmptySlots(GameTestHelper helper) {
        MobSpawnSettings desert = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(Biomes.DESERT).value().getMobSettings();
        List<String> wrong = new ArrayList<>();
        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MISC) {
                continue;
            }
            List<EntityType<?>> types = desert.getMobs(category).unwrap().stream()
                    .<EntityType<?>>map(d -> d.type).toList();
            if (!types.equals(List.of(EntityType.SKELETON))) {
                wrong.add(category.getName() + " " + types);
            }
        }
        helper.assertTrue(wrong.isEmpty(), "The Replace desert should spawn only the skeleton, in every slot: " + wrong);
        helper.succeed();
    }

    /**
     * The skeleton spawns in daylight in a Replace biome ("Hostile mobs spawn outside of pure darkness"), and
     * not with the option off.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "replacelight")
    public static void replaceSkeletonSpawnsInDaylight(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        String biome = helper.getLevel().getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        Map<String, String> key = Map.of(Schema.TierBiomes.BIOME, biome);
        TableWriter.upsert(Schema.TierBiomes.FILE, key, Map.of(Schema.TierBiomes.TIER, "gt_replace"));
        Config.reload();
        helper.getLevel().setDayTime(6000);
        helper.getLevel().setBlock(at.above(), net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            boolean before = StrataConfig.MONSTERS_OUTSIDE_DARKNESS.get();
            List<String> wrong = new ArrayList<>();
            int light = helper.getLevel().getMaxLocalRawBrightness(at);
            try {
                StrataConfig.MONSTERS_OUTSIDE_DARKNESS.set(true);
                if (!placement(helper, at) || !position(helper, at)) {
                    wrong.add("with the option on, placement " + placement(helper, at) + ", position "
                            + position(helper, at));
                }
                StrataConfig.MONSTERS_OUTSIDE_DARKNESS.set(false);
                if (placement(helper, at) || position(helper, at)) {
                    wrong.add("with the option off, the skeleton may still spawn in the light");
                }
            } finally {
                StrataConfig.MONSTERS_OUTSIDE_DARKNESS.set(before);
                helper.getLevel().removeBlock(at.above(), false);
                TableWriter.delete(Schema.TierBiomes.FILE, Map.of(Schema.TierBiomes.BIOME, biome,
                        Schema.TierBiomes.TIER, "gt_replace"));
                Config.reload();
            }
            helper.assertTrue(wrong.isEmpty(), "In the Replace " + biome + " at light " + light + ": " + wrong);
            helper.succeed();
        });
    }

    /** A NEW CHUNK'S HORSE DOES NOT SPAWN IN A REPLACE BIOME. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "replacehorse")
    public static void replaceRefusesANewChunksHorse(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        String biome = level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        Map<String, String> key = Map.of(Schema.TierBiomes.BIOME, biome);
        TableWriter.upsert(Schema.TierBiomes.FILE, key, Map.of(Schema.TierBiomes.TIER, "gt_replace"));
        Config.reload();
        level.setBlock(at.below(), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            List<String> wrong = new ArrayList<>();
            try {
                boolean newChunkRule = SpawnPlacements.checkSpawnRules(EntityType.HORSE, level,
                        MobSpawnType.CHUNK_GENERATION, at, level.getRandom());
                boolean naturalRule = SpawnPlacements.checkSpawnRules(EntityType.HORSE, level,
                        MobSpawnType.NATURAL, at, level.getRandom());
                boolean eggRule = SpawnPlacements.checkSpawnRules(EntityType.HORSE, level,
                        MobSpawnType.SPAWN_EGG, at, level.getRandom());
                var horse = EntityType.HORSE.create(level);
                horse.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                boolean newChunkPosition = EventHooks.checkSpawnPosition(horse, level, MobSpawnType.CHUNK_GENERATION);
                boolean eggPosition = EventHooks.checkSpawnPosition(horse, level, MobSpawnType.SPAWN_EGG);
                if (newChunkRule || naturalRule || newChunkPosition) {
                    wrong.add("a world spawn of a horse passed: new chunk rule " + newChunkRule + ", natural rule "
                            + naturalRule + ", new chunk position " + newChunkPosition);
                }
                if (!eggRule || !eggPosition) {
                    wrong.add("the control failed (the spot is not valid for a horse): egg rule " + eggRule
                            + ", egg position " + eggPosition);
                }
            } finally {
                level.removeBlock(at.above(), false);
                TableWriter.delete(Schema.TierBiomes.FILE, Map.of(Schema.TierBiomes.BIOME, biome,
                        Schema.TierBiomes.TIER, "gt_replace"));
                Config.reload();
            }
            helper.assertTrue(wrong.isEmpty(), "In the Replace " + biome + ": " + wrong);
            helper.succeed();
        });
    }

    /** WEIGHT, THEN SPAWN CHANCE, THEN GROUP SIZE. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "spawngroups")
    public static void spawnGroupsRollChanceAndSize(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        String biome = level.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        TableWriter.upsert(Schema.TierBiomes.FILE, Map.of(Schema.TierBiomes.BIOME, biome),
                Map.of(Schema.TierBiomes.TIER, "gt_replace"));
        Map<String, String> row = new java.util.LinkedHashMap<>();
        row.put(Schema.MobSpawns.SCOPE, "tier");
        row.put(Schema.MobSpawns.SCOPE_ID, "gt_replace");
        row.put(Schema.MobSpawns.ENTITY, "minecraft:skeleton");
        java.util.function.BiConsumer<String, String> set = (group, chance) -> {
            TableWriter.upsert(Schema.MobSpawns.FILE, row, Map.of(Schema.MobSpawns.MIN_COUNT, group,
                    Schema.MobSpawns.MAX_COUNT, group, Schema.MobSpawns.SPAWN_CHANCE, chance));
            Config.reload();
        };
        level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
        helper.runAfterDelay(5, () -> {
            List<String> wrong = new ArrayList<>();
            var area = new net.minecraft.world.phys.AABB(at).inflate(8);
            java.util.function.Supplier<Integer> count = () -> level.getEntitiesOfClass(Skeleton.class, area).size();
            java.util.function.Supplier<Boolean> check = () -> {
                var skeleton = EntityType.SKELETON.create(level);
                skeleton.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                return EventHooks.checkSpawnPosition(skeleton, level, MobSpawnType.NATURAL);
            };
            try {
                set.accept("3", "100");
                int before = count.get();
                boolean three = check.get();
                int placed = count.get() - before;
                if (!three || placed != 2) {
                    wrong.add("a group of 3: passed " + three + ", placed " + placed + " more (want 2)");
                }
                level.getEntitiesOfClass(Skeleton.class, area).forEach(net.minecraft.world.entity.Entity::discard);
                set.accept("0", "100");
                if (check.get() || count.get() != 0) {
                    wrong.add("a group of 0 still spawned");
                }
                set.accept("3", "0");
                if (check.get() || count.get() != 0) {
                    wrong.add("a spawn chance of 0 still spawned");
                }
            } finally {
                level.getEntitiesOfClass(Skeleton.class, area).forEach(net.minecraft.world.entity.Entity::discard);
                level.removeBlock(at.above(), false);
                TableWriter.upsert(Schema.MobSpawns.FILE, row, Map.of(Schema.MobSpawns.MIN_COUNT, "1",
                        Schema.MobSpawns.MAX_COUNT, "4", Schema.MobSpawns.SPAWN_CHANCE, ""));
                TableWriter.delete(Schema.TierBiomes.FILE, Map.of(Schema.TierBiomes.BIOME, biome,
                        Schema.TierBiomes.TIER, "gt_replace"));
                Config.reload();
            }
            helper.assertTrue(wrong.isEmpty(), "In the Replace " + biome + ": " + wrong);
            helper.succeed();
        });
    }

    /** THE DEBUG PLAYER SPAWN STANDS ON THE GROUND. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void playerSpawnStandsOnTheGround(GameTestHelper helper) {
        BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(30000, 0, 30000);
        boolean loaded = helper.getLevel().hasChunk(far.getX() >> 4, far.getZ() >> 4);
        int y = com.strata.runtime.PlayerSpawnBiome.surfaceY(helper.getLevel(), far.getX(), far.getZ());
        int bottom = helper.getLevel().getMinBuildHeight();
        helper.assertTrue(y > bottom, "The spawn height far away is " + y + ", the bottom of the world is " + bottom
                + " (chunk loaded before: " + loaded + ")");
        helper.succeed();
    }

    /**
     * A DROP ROW LEAVES A MOB'S EQUIPMENT ALONE: a REPLACE row cleared every drop, and a sword the
     * zombie picked up was lost.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "loot")
    public static void replaceLootKeepsAPickedUpSword(GameTestHelper helper) {
        var saved = com.strata.domain.LootOps.save("minecraft:zombie", "",
                new com.strata.domain.LootOps.EntryTarget("global", "", 0, null),
                new com.strata.domain.LootOps.LootForm("minecraft:bone", "1", "1", "100", "replace",
                        com.strata.domain.LootOps.NO_CONDITION, ""));
        if (!saved.ok()) {
            helper.fail("Could not write the replace row: " + (saved.refusal() != null ? saved.refusal() : saved.first()));
            return;
        }
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        zombie.setGuaranteedDrop(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        net.minecraft.world.phys.AABB around = zombie.getBoundingBox().inflate(4);
        zombie.kill();
        helper.runAfterDelay(2, () -> {
            List<String> dropped = helper.getLevel().getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class, around).stream()
                    .map(e -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getItem().getItem())
                            .toString()).toList();
            com.strata.domain.LootOps.restoreDefaults("global", "", "minecraft:zombie", "");
            helper.assertTrue(dropped.contains("minecraft:diamond_sword"),
                    "The zombie's picked-up sword was lost to the replace row: dropped " + dropped);
            helper.assertTrue(dropped.contains("minecraft:bone"), "The replace row's bone did not drop: " + dropped);
            helper.succeed();
        });
    }

    /**
     * A CLOSED WORLD IS LET GO: when a server stops, Strata binds the startup registries again and
     * holds no server.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "unbind")
    public static void aStoppedServerIsLetGo(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var startup = Config.startupRegistries();
        new com.strata.runtime.StrataLoadEvents().onServerStopped(
                new net.neoforged.neoforge.event.server.ServerStoppedEvent(server));
        boolean letGo = Config.boundServer() == null;
        boolean startupBound = Config.boundRegistries() == startup;
        Config.bind(server.registryAccess(), server);
        helper.assertTrue(letGo && startupBound, "After the server stopped: server let go " + letGo
                + ", startup registries bound " + startupBound + " (startup built: " + (startup != null) + ")");
        helper.succeed();
    }

    /**
     * A GAME ARMOR PIECE IS EDITED AS ITS OWN ROW: saving the skeleton's golden helmet with its own chance
     * and enchant writes it as the instance's row, keeps the other game helmets with their numbers, and
     * Restore all gives the game's pieces back.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "gearroll")
    public static void aGameArmorPieceIsEditedAsItsOwnRow(GameTestHelper helper) {
        var made = com.strata.domain.MobOps.ensureDefaultVariant("minecraft:skeleton");
        String owner = made.name();
        var equipment = com.strata.domain.DefaultEquipmentOps.gameEquipment("minecraft:skeleton");
        var rows = com.strata.domain.EquipmentOps.equipmentFor(owner, "minecraft:skeleton", "global", "", equipment);
        long helmets = rows.stream().filter(r -> r.slot().equals("head")).count();
        var saved = com.strata.domain.EquipmentOps.saveEquipment(owner, "global", "", null, "head",
                "minecraft:golden_helmet", "5", "", "", "30", true, "", rows);
        var after = com.strata.domain.EquipmentOps.equipmentFor(owner, "minecraft:skeleton", "global", "", equipment);
        var gold = after.stream().filter(r -> r.item().equals("minecraft:golden_helmet")).findFirst().orElse(null);
        var head = after.stream().filter(r -> r.slot().equals("head")).toList();
        var restored = com.strata.domain.EquipmentOps.restoreEquipment(owner, "global", "");
        var back = com.strata.domain.EquipmentOps.equipmentFor(owner, "minecraft:skeleton", "global", "", equipment);
        helper.assertTrue(helmets > 1 && saved.ok() && gold != null && !gold.inherited() && gold.chancePct() == 5
                        && gold.enchant().equals("30") && gold.scaled() && head.size() == helmets
                        && head.stream().allMatch(r -> !r.inherited() && r.scaled()),
                "Save: ok " + saved.ok() + ", helmets " + helmets + ", head rows " + head + ", gold " + gold);
        helper.assertTrue(restored.ok() && back.stream().filter(r -> r.slot().equals("head")).allMatch(r -> r.inherited()),
                "Restore all: ok " + restored.ok() + ", rows " + back);
        helper.succeed();
    }
    /**
     * THE ORES LIST BY DIMENSION, FROM THE GAME: some ore is in the Overworld, some in the Nether, and not
     * every one is "Other".
     */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void oresListByDimension(GameTestHelper helper) {
        List<String> ores = com.strata.game.RegistryQuery.ores().stream()
                .map(com.strata.game.RegistryQuery.Option::id).toList();
        var by = com.strata.domain.OreOps.dimensionsOf(ores);
        boolean overworld = by.values().stream().anyMatch(d -> d.contains("Overworld"));
        boolean nether = by.values().stream().anyMatch(d -> d.contains("The Nether"));
        helper.assertTrue(!ores.isEmpty() && overworld && nether, "Ores by dimension: " + by);
        helper.succeed();
    }

    /** VEGETATION RULES REACH THE WORLD. */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void vegetationRulesReachTheDesert(GameTestHelper helper) {
        var steps = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(Biomes.DESERT).value().getGenerationSettings().features();
        int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
        List<String> plants = new ArrayList<>();
        steps.get(step).forEach(f -> f.unwrapKey().ifPresent(k -> plants.add(k.location().toString())));
        helper.assertTrue(plants.contains("minecraft:trees_plains") && !plants.contains("minecraft:patch_cactus_desert"),
                "The desert's plants: " + plants + " (game layer: "
                        + com.strata.data.GameSnapshot.vegetation().get("minecraft:desert") + ")");
        helper.succeed();
    }

    private static boolean placement(GameTestHelper helper, BlockPos at) {
        return SpawnPlacements.checkSpawnRules(EntityType.SKELETON, helper.getLevel(), MobSpawnType.NATURAL, at,
                helper.getLevel().getRandom());
    }

    private static boolean position(GameTestHelper helper, BlockPos at) {
        Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
        skeleton.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        boolean ok = EventHooks.checkSpawnPosition(skeleton, helper.getLevel(), MobSpawnType.NATURAL);
        skeleton.discard();
        return ok;
    }
}
