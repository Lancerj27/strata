package com.strata.runtime;

import com.strata.data.GameLayer;
import com.strata.model.GoalType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The "who" of a goal setting (GoalType.Kind#TARGET), both ways, from the game layer. */
public final class GoalTargets {

    private GoalTargets() {
    }

    /** Entity id to the class name the game makes for it. */
    private static volatile Map<String, String> classById = Map.of(GoalType.PLAYER, Player.class.getName());

    /** Reads the id-to-class pairs from the mob sheet. */
    public static void load(List<GameLayer.Row> mobSheet) {
        classById = classes(mobSheet);
    }

    /** Entity id to class name, from the mob sheet's type/class rows, the player first. */
    public static Map<String, String> classes(List<GameLayer.Row> mobSheet) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(GoalType.PLAYER, Player.class.getName());
        for (GameLayer.Row r : mobSheet) {
            if (GameLayer.TYPE.equals(r.group()) && "class".equals(r.key()) && !r.value().isEmpty()) {
                out.putIfAbsent(r.entity(), r.value());
            }
        }
        return Map.copyOf(out);
    }

    /** The id whose class is exactly this one, or "class:" and its name for a group. */
    public static String targetFor(Map<String, String> byId, String className) {
        for (Map.Entry<String, String> e : byId.entrySet()) {
            if (e.getValue().equals(className)) {
                return e.getKey();
            }
        }
        return GoalType.CLASS + className;
    }

    /** The target a goal's class setting reads as. */
    public static String word(Class<?> type) {
        return type == null ? GoalType.PLAYER : targetFor(classById, type.getName());
    }

    /** The class for a target; Player when it cannot be found. */
    @SuppressWarnings("unchecked")
    public static Class<? extends LivingEntity> type(String target) {
        String t = target == null ? "" : target.strip();
        String name = t.startsWith(GoalType.CLASS) ? t.substring(GoalType.CLASS.length()) : classById.get(t);
        if (name != null) {
            try {
                Class<?> c = Class.forName(name);
                if (LivingEntity.class.isAssignableFrom(c)) {
                    return (Class<? extends LivingEntity>) c;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // Logged below.
            }
        }
        com.strata.Strata.LOGGER.warn("Goal target '{}' is not a living entity the game has - players instead", t);
        return Player.class;
    }
}
