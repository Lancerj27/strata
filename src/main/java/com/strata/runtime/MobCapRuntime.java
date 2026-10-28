package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import net.minecraft.world.entity.MobCategory;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Puts the user's mob caps over the game's. */
public final class MobCapRuntime {

    private MobCapRuntime() {
    }

    /** The cap keys Strata can set, to the MobCategory field that holds each. */
    public static final Map<String, String> FIELDS = Map.of(
            "max", "max", "despawn_distance", "despawnDistance",
            "friendly", "isFriendly", "persistent", "isPersistent");

    /** The game's values, kept the first time Strata changes a category. */
    private static final Map<MobCategory, Map<String, Object>> ORIGINALS = new HashMap<>();

    public static synchronized void load(Path csv) {
        ORIGINALS.forEach((category, values) -> values.forEach((field, value) -> set(category, field, value)));
        ORIGINALS.clear();
        int changed = 0;
        for (Map<String, String> row : CsvTable.read(csv)) {
            MobCategory category = category(row.getOrDefault(Schema.MobCaps.CATEGORY, "").strip());
            String field = FIELDS.get(row.getOrDefault(Schema.MobCaps.KEY, "").strip());
            if (category == null || field == null) {
                continue;
            }
            Object value = value(field, row.getOrDefault(Schema.MobCaps.VALUE, "").strip());
            if (value == null) {
                Strata.LOGGER.warn("mob_caps.csv: {} {} cannot take '{}' - skipped", category.getName(), field,
                        row.get(Schema.MobCaps.VALUE));
                continue;
            }
            ORIGINALS.computeIfAbsent(category, k -> new HashMap<>()).putIfAbsent(field, get(category, field));
            set(category, field, value);
            changed++;
        }
        Strata.LOGGER.info("Mob caps: {} value(s) of your own", changed);
    }

    /** The category with this name ("monster"), or null. */
    public static MobCategory category(String name) {
        for (MobCategory c : MobCategory.values()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** A written value as the field's type: a whole number 0 or more, or a flag. */
    public static Object value(String field, String text) {
        if (field.startsWith("is")) {
            return text.equals("true") ? Boolean.TRUE : text.equals("false") ? Boolean.FALSE : null;
        }
        try {
            int v = Integer.parseInt(text);
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object get(MobCategory category, String field) {
        try {
            Field f = MobCategory.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(category);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Mob caps: cannot read MobCategory.{}: {}", field, e.toString());
            return null;
        }
    }

    private static void set(MobCategory category, String field, Object value) {
        if (value == null) {
            return;
        }
        try {
            Field f = MobCategory.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(category, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Mob caps: cannot set MobCategory.{}: {}", field, e.toString());
        }
    }
}
