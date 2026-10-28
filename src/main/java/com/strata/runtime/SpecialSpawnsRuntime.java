package com.strata.runtime;

import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.model.SpecialSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** STRATA RUNS THE GAME'S SPECIAL SPAWNERS. */
public class SpecialSpawnsRuntime {

    /** "scope|scope id|spawner|setting" to the value; scope "" is Global. */
    private static volatile Map<String, String> rows = Map.of();

    /** The phantom's days awake as the running game's code has it (GameCode, read at game start). */
    private static volatile int gameRestDays = SpecialSpawners.GAME_REST_DAYS;

    public static int gameRestDays() {
        return gameRestDays;
    }

    /** The patrols' first day, from the game's code at start. */
    private static volatile int gamePatrolDay = 5;

    public static int gamePatrolDay() {
        return gamePatrolDay;
    }

    /** The game's own spawn conditions as spawn_rules.csv rows (scope, scope_id, entity, variant, type, value). */
    public static java.util.List<String[]> gameRuleRows() {
        return com.strata.domain.SpecialSpawnsOps.gameRules(gameRestDays, gamePatrolDay);
    }

    /**
     * At game start: the phantom's days awake from the game's code (GameCode), the game's spawn conditions
     * seeded once into spawn_rules.csv, and the Default profile written again.
     */
    public static void seedAtStart() {
        gameRestDays = GameCode.phantomRestDays();
        int patrolDay = GameCode.patrolFromDay();
        gamePatrolDay = patrolDay;
        // The game's day, by its own sky math and cosine.
        com.strata.model.SpawnRule.gameDaylight = com.strata.model.SpawnRule.daylight(
                d -> net.minecraft.util.Mth.cos((float) d));
        var seeded = com.strata.domain.SpecialSpawnsOps.seedGameRules(gameRestDays, patrolDay);
        if (!seeded.ok()) {
            com.strata.Strata.LOGGER.warn("Could not seed the game's spawn conditions into spawn_rules.csv");
        }
        com.strata.data.ProfileManager.writeDefaultProfile(
                com.strata.domain.SpecialSpawnsOps.gameRules(gameRestDays, patrolDay));
        com.strata.data.ProfileManager.syncAfterLaunch();
        int[] day = com.strata.model.SpawnRule.gameDaylight;
        com.strata.Strata.LOGGER.info("Game's spawn conditions: phantoms after {} day(s) awake; patrols from day {},"
                + " in daylight ({} to {} in clear weather); Default profile written", gameRestDays, patrolDay,
                day[0], day[1]);
    }

    /** Every spawn rule, keyed as Tables#loadSpawnRulesByRow keys them. */
    private static volatile Map<String, List<com.strata.model.SpawnRule>> rules = Map.of();

    /** Reads special_spawns.csv and the spawn rules. */
    public static void load(Path csv) {
        rows = index(CsvTable.read(csv));
        rules = com.strata.data.Tables.loadSpawnRulesByRow(csv.getParent());
    }

    /**
     * A plain mob's spawn rules at a place: the biome's, else its tier's, else Global's, even where the mob
     * has no spawn row.
     */
    public static List<com.strata.model.SpawnRule> rulesAt(ServerLevelAccessor level, BlockPos pos, String entity) {
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        String tier = biome.isEmpty() ? "" : Config.resolve(biome).tier();
        return rulesAt(rules, tier == null ? "" : tier, biome, entity);
    }

    /** The rules, from the loaded map. */
    public static List<com.strata.model.SpawnRule> rulesAt(Map<String, List<com.strata.model.SpawnRule>> byRow,
                                                           String tier, String biome, String entity) {
        for (String key : List.of("biome|" + biome + "|" + entity, "tier|" + tier + "|" + entity,
                "global||" + entity)) {
            List<com.strata.model.SpawnRule> hit = byRow.get(key);
            if (hit != null) {
                return hit;
            }
        }
        return List.of();
    }

    /** The rows as model.SpecialSpawners#value reads them. */
    public static Map<String, String> index(List<Map<String, String>> table) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> r : table) {
            String scope = r.getOrDefault(Schema.SpecialSpawns.SCOPE, "").strip();
            boolean global = scope.isEmpty() || "global".equals(scope);
            out.put((global ? "" : scope) + "|" + (global ? "" : r.getOrDefault(Schema.SpecialSpawns.SCOPE_ID, "").strip())
                    + "|" + r.getOrDefault(Schema.SpecialSpawns.SPAWNER, "").strip()
                    + "|" + r.getOrDefault(Schema.SpecialSpawns.SETTING, "").strip(),
                    r.getOrDefault(Schema.SpecialSpawns.VALUE, "").strip());
        }
        return out;
    }

    /** A setting's value at a place. */
    /** True when the designer set a special spawner value: with none, each spawner runs the game's own code. */
    public static boolean hasRows() {
        return !rows.isEmpty();
    }

    public static String value(String spawner, String key, ServerLevelAccessor level, BlockPos pos) {
        SpecialSpawners.Setting s = SpecialSpawners.setting(spawner, key);
        if (s == null) {
            return "";
        }
        String biome = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("");
        String tier = biome.isEmpty() ? "" : Config.resolve(biome).tier();
        return SpecialSpawners.value(rows, s, tier == null ? "" : tier, biome);
    }

    /** A Global only setting's value (the spawner's clock). */
    public static String global(String spawner, String key) {
        SpecialSpawners.Setting s = SpecialSpawners.setting(spawner, key);
        return s == null ? "" : SpecialSpawners.value(rows, s, "", "");
    }

    public static int whole(String spawner, String key, ServerLevelAccessor level, BlockPos pos) {
        return Math.max(0, SpecialSpawners.whole(value(spawner, key, level, pos)));
    }

    public static boolean on(String spawner, String key, ServerLevelAccessor level, BlockPos pos) {
        return SpecialSpawners.on(value(spawner, key, level, pos));
    }

    /** A Global only whole number. */
    public static int globalWhole(String spawner, String key) {
        return Math.max(0, SpecialSpawners.whole(global(spawner, key)));
    }

    /** A mob's Spawn as: Special (true) or Normal, set in the mob editor on Global. */
    public static boolean special(String entity) {
        return SpecialSpawners.special(rows, entity);
    }

    /** A variant's Spawn as: its own, else its base mob's. */
    public static boolean special(String entity, String variant) {
        return SpecialSpawners.special(rows, entity, variant);
    }

    /**
     * True when the spawner may make this mob here: special mob spawns are On here, the spawner is Enabled
     * here, the mob it makes only as Special is still Special, and a Replace biome does not refuse the mob.
     */
    public static boolean runs(String spawner, ServerLevelAccessor level, BlockPos pos, EntityType<?> type) {
        String only = SpecialSpawners.MOB_OF.get(spawner);
        return on(SpecialSpawners.SPECIAL, "enabled", level, pos) && on(spawner, "enabled", level, pos)
                && (only == null || special(only))
                && !ReplaceSpawnRules.blocked(level, pos, type, MobSpawnType.NATURAL);
    }

    /**
     * The spawns the world makes on its own, which Special mob spawns Off refuses for a Special mob: natural,
     * new chunk, patrol, structure, an event (a trader, a siege), a summon (a villager's golem).
     */
    public static final java.util.Set<MobSpawnType> SPECIAL_SPAWNS = java.util.EnumSet.of(MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION, MobSpawnType.PATROL, MobSpawnType.STRUCTURE, MobSpawnType.EVENT,
            MobSpawnType.MOB_SUMMONED);

    /** True when a Special mob's spawn is refused here: Special mob spawns are Off. */
    public static boolean refuses(ServerLevelAccessor level, BlockPos pos, EntityType<?> type, MobSpawnType spawnType) {
        if (!SPECIAL_SPAWNS.contains(spawnType) || rows.isEmpty() && !SpecialSpawners.SPECIAL_BY_DEFAULT.contains(key(type))) {
            return false;
        }
        return special(key(type)) && !on(SpecialSpawners.SPECIAL, "enabled", level, pos);
    }

    private static String key(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).map(k -> k.location().toString()).orElse("");
    }

    /** The mob a MOB setting names, or the fallback when it names none the game has. */
    public static EntityType<?> mob(String id, EntityType<?> fallback) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        return key != null && BuiltInRegistries.ENTITY_TYPE.containsKey(key) ? BuiltInRegistries.ENTITY_TYPE.get(key)
                : fallback;
    }

    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onCustomSpawners(ModifyCustomSpawnersEvent event) {
        // Off: the game's own spawner objects stay in the list.
        if (!com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.ENABLE_SPECIAL_SPAWNERS)) {
            return;
        }
        List<CustomSpawner> list = event.getCustomSpawners();
        ServerLevel level = event.getLevel();
        for (int i = 0; i < list.size(); i++) {
            CustomSpawner s = list.get(i);
            // The game's own, by class: a mod's subclass is left as it is.
            if (s.getClass() == PhantomSpawner.class) {
                list.set(i, new StrataPhantomSpawner());
            } else if (s.getClass() == PatrolSpawner.class) {
                list.set(i, new StrataPatrolSpawner());
            } else if (s.getClass() == CatSpawner.class) {
                list.set(i, new StrataCatSpawner());
            } else if (s.getClass() == VillageSiege.class) {
                list.set(i, new StrataVillageSiege());
            } else if (s.getClass() == WanderingTraderSpawner.class
                    && level.getLevelData() instanceof ServerLevelData data) {
                list.set(i, new StrataWanderingTraderSpawner(data));
            }
        }
    }

    /** A villager's iron golem: summoned only where Iron golem summoning is On, and Replace does not refuse it. */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onGolem(FinalizeSpawnEvent event) {
        if (event.getSpawnType() == MobSpawnType.MOB_SUMMONED && event.getEntity().getType() == EntityType.IRON_GOLEM
                && event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getEntity().blockPosition();
            if (!on(SpecialSpawners.VILLAGERS, "golems", level, pos)
                    || ReplaceSpawnRules.blocked(level, pos, EntityType.IRON_GOLEM, MobSpawnType.NATURAL)) {
                event.setSpawnCancelled(true);
            }
        }
    }

    /** Villagers' babies: only where Breeding is On. */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onBaby(BabyEntitySpawnEvent event) {
        if (event.getParentA() instanceof Villager parent && parent.level() instanceof ServerLevel level
                && !on(SpecialSpawners.VILLAGERS, "breeding", level, parent.blockPosition())) {
            event.setCanceled(true);
        }
    }
}
