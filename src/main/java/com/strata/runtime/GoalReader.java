package com.strata.runtime;

import com.strata.Strata;
import com.strata.model.GoalType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Reads a live goal's settings, so a mob's AI list starts from its REAL values, not from defaults: a cow's
 * panic speed, a zombie's hunt interval, a skeleton's bow range.
 */
public final class GoalReader {

    private GoalReader() {
    }

    /** The class a goal is read as: its own, or for an anonymous class of a GoalType, that GoalType. */
    public static Class<?> namedClass(Goal goal) {
        Class<?> c = goal.getClass();
        return c.isAnonymousClass() && GoalType.of(c.getSuperclass().getSimpleName()) != null
                ? c.getSuperclass() : c;
    }

    /** The goal's settings when its class ({@link #namedClass}) is a GoalType, else empty. */
    public static Map<String, String> settings(Goal goal) {
        Class<?> named = namedClass(goal);
        GoalType.Type type = GoalType.of(named.getSimpleName());
        if (type == null || named.isAnonymousClass() || !named.getSimpleName().equals(type.goal())) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (GoalType.Param param : type.params()) {
            out.put(param.key(), value(goal, param));
        }
        return out;
    }

    private static String value(Goal goal, GoalType.Param param) {
        Object raw = field(goal, param.field());
        if (raw == null) {
            return param.dflt();
        }
        try {
            return switch (param.kind()) {
                case NUMBER -> {
                    double d = ((Number) raw).doubleValue();
                    yield number(param.squared() ? Math.sqrt(d) : d);
                }
                case WHOLE -> String.valueOf(((Number) raw).intValue());
                case FLAG -> raw instanceof BooleanSupplier supplier
                        ? String.valueOf(supplier.getAsBoolean()) : String.valueOf(raw);
                case TARGET -> GoalTargets.word((Class<?>) raw);
                case ITEMS -> items(raw, param);
                case CHOICE -> doorChoice(raw, param);
                case PROJECTILE -> String.valueOf(raw);
            };
        } catch (RuntimeException e) {
            Strata.LOGGER.debug("Goal setting {} of {} unreadable - default: {}", param.key(),
                    goal.getClass().getName(), e.toString());
            return param.dflt();
        }
    }

    /** The field's value, found on the class or a class it extends, or null. */
    private static Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                // Declared higher up: keep walking.
            } catch (ReflectiveOperationException | RuntimeException e) {
                Strata.LOGGER.debug("Goal field {} unreadable on {}: {}", name, c.getName(), e.toString());
                return null;
            }
        }
        return null;
    }

    /** Every item the goal's item test accepts, joined with "+". */
    @SuppressWarnings("unchecked")
    private static String items(Object raw, GoalType.Param param) {
        Predicate<ItemStack> test = (Predicate<ItemStack>) raw;
        List<String> ids = new ArrayList<>();
        BuiltInRegistries.ITEM.forEach(item -> {
            if (test.test(new ItemStack(item))) {
                BuiltInRegistries.ITEM.getResourceKey(item).ifPresent(key -> ids.add(key.location().toString()));
            }
        });
        return ids.isEmpty() ? param.dflt() : String.join("+", ids);
    }

    /** The door choice from the difficulty test: hard, normal_and_hard, or any. */
    @SuppressWarnings("unchecked")
    private static String doorChoice(Object raw, GoalType.Param param) {
        Predicate<Difficulty> test = (Predicate<Difficulty>) raw;
        if (test.test(Difficulty.EASY)) {
            return "any";
        }
        if (test.test(Difficulty.NORMAL)) {
            return "normal_and_hard";
        }
        return test.test(Difficulty.HARD) ? "hard" : param.dflt();
    }

    private static String number(double value) {
        double r = Math.round(value * 10000.0) / 10000.0;
        return r == Math.rint(r) ? String.valueOf((long) r) + ".0" : String.valueOf(r);
    }
}
