package com.strata.runtime;

import com.strata.Strata;
import com.strata.model.SpawnRule;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** Evaluates spawn conditions against the current world state. */
public final class SpawnRuleEvaluator {

    private SpawnRuleEvaluator() {}

    public static boolean allMatch(List<SpawnRule> rules, ServerLevel level, BlockPos pos) {
        return allMatch(rules, level, pos, null);
    }

    /**
     * The rules at a place, with Time since rest read from this player (a special mob's spawner names its
     * player), or from the nearest player when null.
     */
    public static boolean allMatch(List<SpawnRule> rules, ServerLevel level, BlockPos pos,
                                   net.minecraft.server.level.ServerPlayer player) {
        for (SpawnRule rule : rules) {
            boolean ok = player != null
                    ? switch (rule.type()) {
                        case TIME_SINCE_REST -> matchesRest(rule.value(), player);
                        case PLAYER_LEVEL -> matchesPlayerLevel(rule.value(), player.experienceLevel);
                        default -> matches(rule, level, pos);
                    }
                    : matches(rule, level, pos);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(SpawnRule rule, ServerLevel level, BlockPos pos) {
        return switch (rule.type()) {
            case TIME -> matchesTime(rule.value(), level);
            case LIGHT -> matchesLight(rule.value(), level, pos);
            case Y_LEVEL -> matchesYLevel(rule.value(), pos);
            case WEATHER -> matchesWeather(rule.value(), level);
            case MOON_PHASE -> matchesMoonPhase(rule.value(), level);
            case STRUCTURE -> matchesStructure(rule.value(), level, pos);
            case SURFACE -> matchesSurface(rule.value(), level, pos);
            case DISTANCE -> matchesDistance(rule.value(), level, pos);
            case TIME_SINCE_REST -> matchesRest(rule.value(), level, pos);
            case WORLD_DAY -> matchesWorldDay(rule.value(), level);
            case PLAYER_LEVEL -> matchesPlayerLevel(rule.value(), level, pos);
        };
    }

    private static boolean matchesTime(String value, ServerLevel level) {
        long dayTime = level.getDayTime() % 24000;
        return switch (value.strip().toLowerCase()) {
            case "day" -> dayTime >= 0 && dayTime < 13000;
            case "night" -> dayTime >= 13000;
            case "dusk" -> dayTime >= 11500 && dayTime < 13500;
            case "dawn" -> dayTime >= 22500 || dayTime < 500;
            // The game's own day and night: its sky's brightness.
            case "daylight" -> level.isDay();
            case "dark" -> level.isNight();
            default -> SpawnRule.parseRange(value, (int) dayTime);
        };
    }

    private static boolean matchesLight(String value, ServerLevel level, BlockPos pos) {
        int light = level.getMaxLocalRawBrightness(pos);
        return SpawnRule.parseRange(value, light);
    }

    private static boolean matchesYLevel(String value, BlockPos pos) {
        return SpawnRule.parseRange(value, pos.getY());
    }

    private static boolean matchesWeather(String value, ServerLevel level) {
        return switch (value.strip().toLowerCase()) {
            case "rain", "raining" -> level.isRaining() && !level.isThundering();
            case "thunder", "thundering" -> level.isThundering();
            case "clear" -> !level.isRaining();
            case "not_clear", "precipitation" -> level.isRaining();
            default -> false;
        };
    }

    private static boolean matchesMoonPhase(String value, ServerLevel level) {
        int phase = level.getMoonPhase();
        String stripped = value.strip();
        if (stripped.contains(",")) {
            for (String part : stripped.split(",")) {
                try {
                    if (Integer.parseInt(part.strip()) == phase) return true;
                } catch (NumberFormatException e) {
                    // One malformed entry in a comma list must not void the whole condition - the other parts
                    // still get their turn.
                    Strata.LOGGER.warn("moon_phase: \"{}\" is not a number - that entry "
                            + "is ignored: {}", part, e.toString());
                }
            }
            return false;
        }
        return SpawnRule.parseRange(stripped, phase);
    }

    private static boolean matchesStructure(String value, ServerLevel level, BlockPos pos) {
        ResourceLocation structId = ResourceLocation.tryParse(value.strip());
        if (structId == null) return false;
        var regOpt = level.registryAccess().lookup(net.minecraft.core.registries.Registries.STRUCTURE);
        if (regOpt.isEmpty()) return false;
        var registry = regOpt.get();
        var structKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, structId);
        var holder = registry.get(structKey);
        if (holder.isEmpty()) return false;
        return level.structureManager().getStructureWithPieceAt(pos,
                structure -> structure.is(structKey)).isValid();
    }

    private static boolean matchesSurface(String value, ServerLevel level, BlockPos pos) {
        return switch (value.strip().toLowerCase()) {
            case "surface", "above" -> level.canSeeSky(pos);
            case "underground", "below" -> !level.canSeeSky(pos);
            default -> false;
        };
    }

    /**
     * Days since the nearest player (in 128 blocks) last slept, the game's Time Since Last Rest statistic in
     * whole days.
     */
    private static boolean matchesRest(String value, ServerLevel level, BlockPos pos) {
        var player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 128, false);
        return player instanceof net.minecraft.server.level.ServerPlayer sp && matchesRest(value, sp);
    }

    /** The rule against one player's Time Since Last Rest, in whole days. */
    private static boolean matchesRest(String value, net.minecraft.server.level.ServerPlayer sp) {
        int days = sp.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST))
                / com.strata.model.SpecialSpawners.DAY_TICKS;
        String v = value.strip();
        if (v.endsWith("+")) {
            int least = com.strata.model.SpecialSpawners.restDays(v);
            return least >= 0 && days >= least;
        }
        return SpawnRule.parseRange(v, days);
    }

    /** The nearest player's experience level (within 128 blocks, like a Time since rest rule). */
    private static boolean matchesPlayerLevel(String value, ServerLevel level, BlockPos pos) {
        var player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 128, false);
        return player != null && matchesPlayerLevel(value, player.experienceLevel);
    }

    /** The rule against one player's experience level, with the shared grammar. */
    private static boolean matchesPlayerLevel(String value, int level) {
        com.strata.model.LevelRange range = com.strata.model.LevelRange.parse(value);
        return range != null && range.matches(level);
    }
    /** The world's day count, as the game counts it for patrols (day time / 24000). */
    private static boolean matchesWorldDay(String value, ServerLevel level) {
        long day = level.getDayTime() / com.strata.model.SpecialSpawners.DAY_TICKS;
        String v = value.strip();
        if (v.endsWith("+")) {
            int least = com.strata.model.SpecialSpawners.restDays(v);
            return least >= 0 && day >= least;
        }
        return SpawnRule.parseRange(v, (int) Math.min(Integer.MAX_VALUE, day));
    }

    private static boolean matchesDistance(String value, ServerLevel level, BlockPos pos) {
        BlockPos spawn = level.getSharedSpawnPos();
        double dist = Math.sqrt(pos.distSqr(spawn));
        String v = value.strip();
        if (v.endsWith("+")) {
            try {
                double d = Double.parseDouble(v.substring(0, v.length() - 1));
                if (!Double.isFinite(d)) return false;
                return dist >= d;
            } catch (NumberFormatException e) { return false; }
        }
        return SpawnRule.parseRange(v, (int) dist);
    }

}
