package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Schema;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** INJECTION. */
public final class MobDataRuntime {

    private MobDataRuntime() {
    }

    /** entity id to (field key to value): the base mob's field edits. */
    private static volatile Map<String, Map<String, String>> baseFields = Map.of();
    /** ScopeIndex key to (field key to value): each variant instance's field edits. */
    private static volatile Map<String, Map<String, String>> variantFields = Map.of();
    /** The game's type values, kept the first time Strata changes a type. */
    private static final Map<EntityType<?>, Map<String, Object>> ORIGINALS = new ConcurrentHashMap<>();
    /** Class to (Class.field key to field), so a join does not search the class chain again. */
    private static final Map<Class<?>, Map<String, Field>> FIELDS = new ConcurrentHashMap<>();

    /** The flag type values, to the EntityType field that holds each. */
    private static final Map<String, String> FLAG_FIELDS = Map.of(
            "summonable", "summon", "fire_immune", "fireImmune", "spawn_far_from_player", "canSpawnFarFromPlayer");

    public static void load(Path csv) {
        Map<String, Map<String, String>> base = new HashMap<>();
        Map<String, Map<String, String>> variants = new HashMap<>();
        Map<String, Map<String, String>> types = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(csv)) {
            String entity = cell(row, Schema.MobData.ENTITY);
            String variant = cell(row, Schema.MobData.VARIANT_NAME);
            String group = cell(row, Schema.MobData.GROUP);
            String key = cell(row, Schema.MobData.KEY);
            String value = cell(row, Schema.MobData.VALUE);
            if (entity.isEmpty() || key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (GameLayer.FIELD.equals(group)) {
                (variant.isEmpty() ? base.computeIfAbsent(entity, k -> new LinkedHashMap<>())
                        : variants.computeIfAbsent(ScopeIndex.key(cell(row, Schema.MobData.SCOPE),
                                cell(row, Schema.MobData.SCOPE_ID), variant), k -> new LinkedHashMap<>()))
                        .put(key, value);
            } else if (GameLayer.TYPE.equals(group) && variant.isEmpty()) {
                types.computeIfAbsent(entity, k -> new LinkedHashMap<>()).put(key, value);
            } else if (GameLayer.TYPE.equals(group) && "category".equals(key)
                    && cell(row, Schema.MobData.SCOPE).isEmpty()) {
                // A VARIANT'S OWN CATEGORY is its own mob type's.
                VariantEntityRegistry.getRegistered(variant)
                        .flatMap(t -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(t))
                        .ifPresent(k -> types.computeIfAbsent(k.location().toString(), x -> new LinkedHashMap<>())
                                .put(key, value));
            }
        }
        baseFields = Map.copyOf(base);
        variantFields = Map.copyOf(variants);
        applyTypes(types);
        Strata.LOGGER.info("Mob data: {} mob(s) and {} variant instance(s) with field edits, {} type(s) changed",
                base.size(), variants.size(), types.size());
    }

    /**
     * The field edits for a mob, layer over layer: the base mob, then the variant's global, tier, and biome
     * instance.
     */
    public static Map<String, String> fieldsFor(Map<String, Map<String, String>> base,
                                                Map<String, Map<String, String>> variants,
                                                String entity, String variant, String tier, String biome) {
        Map<String, String> out = new LinkedHashMap<>(base.getOrDefault(entity, Map.of()));
        if (variant != null && !variant.isEmpty()) {
            out.putAll(variants.getOrDefault(ScopeIndex.key("", "", variant), Map.of()));
            if (tier != null && !tier.isEmpty()) {
                out.putAll(variants.getOrDefault(ScopeIndex.key("tier", tier, variant), Map.of()));
            }
            if (biome != null && !biome.isEmpty()) {
                out.putAll(variants.getOrDefault(ScopeIndex.key("biome", biome, variant), Map.of()));
            }
        }
        return out;
    }

    /** Sets the mob's edited fields. */
    public static void apply(LivingEntity mob) {
        String entityId = VariantEntityRegistry.baseEntityIdForType(mob.getType())
                .orElse(BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType())
                        .map(k -> k.location().toString()).orElse(""));
        String variant = mob.getExistingData(MobLevel.VARIANT_ID).orElse("");
        String packed = mob.getExistingData(MobLevel.VARIANT_SCOPE).orElse("");
        Map<String, String> edits = fieldsFor(baseFields, variantFields, entityId, variant,
                ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
        for (Map.Entry<String, String> e : edits.entrySet()) {
            Field f = field(mob.getClass(), e.getKey());
            Object value = f == null ? null : convert(e.getValue(), f.getType());
            if (value == null) {
                Strata.LOGGER.debug("Mob data: {} has no field {} that takes {}", entityId, e.getKey(), e.getValue());
                continue;
            }
            try {
                f.set(mob, value);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                Strata.LOGGER.warn("Mob data: could not set {} on {}: {}", e.getKey(), entityId, ex.toString());
            }
        }
        // A changed size, or a model whose hitbox the mob takes (MobModels#onSize).
        if (ORIGINALS.containsKey(mob.getType()) || MobModels.modelType(mob) != null) {
            mob.refreshDimensions();
        }
    }

    /** The field a "Class.field" key names in the class chain, or null. */
    public static Field field(Class<?> type, String key) {
        return FIELDS.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(key, k -> {
            int dot = k.indexOf('.');
            if (dot <= 0 || GameLayer.locked(GameLayer.FIELD, k)) {
                return null;
            }
            String owner = k.substring(0, dot);
            String name = k.substring(dot + 1);
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                if (!c.getSimpleName().equals(owner)) {
                    continue;
                }
                try {
                    Field f = c.getDeclaredField(name);
                    if (!GameLayer.wanted(f)) {
                        return null;
                    }
                    f.setAccessible(true);
                    return f;
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
            return null;
        });
    }

    /** A written value as the field's own type, or null when it does not fit. */
    public static Object convert(String text, Class<?> type) {
        String s = text == null ? "" : text.strip();
        try {
            if (type == int.class || type == Integer.class) return Integer.parseInt(s);
            if (type == long.class || type == Long.class) return Long.parseLong(s);
            if (type == short.class || type == Short.class) return Short.parseShort(s);
            if (type == byte.class || type == Byte.class) return Byte.parseByte(s);
            if (type == double.class || type == Double.class) return Double.parseDouble(s);
            if (type == float.class || type == Float.class) return Float.parseFloat(s);
            if (type == boolean.class || type == Boolean.class) {
                return s.equals("true") ? Boolean.TRUE : s.equals("false") ? Boolean.FALSE : null;
            }
            if (type == char.class || type == Character.class) return s.length() == 1 ? s.charAt(0) : null;
            if (type == String.class) return s;
            if (type.isEnum()) {
                for (Object constant : type.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(s)) {
                        return constant;
                    }
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /** True when the Type tab set this type's size: it wins over a model's hitbox. */
    public static boolean sizeEdited(EntityType<?> type) {
        Map<String, Object> changed = ORIGINALS.get(type);
        return changed != null && changed.containsKey("dimensions");
    }

    /** Puts every changed type back to the game's values, then sets the current edits. */
    private static void applyTypes(Map<String, Map<String, String>> types) {
        for (Map.Entry<EntityType<?>, Map<String, Object>> e : ORIGINALS.entrySet()) {
            e.getValue().forEach((name, value) -> setTypeField(e.getKey(), name, value));
        }
        ORIGINALS.clear();
        for (Map.Entry<String, Map<String, String>> e : types.entrySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                    net.minecraft.resources.ResourceLocation.tryParse(e.getKey())).orElse(null);
            if (type == null) {
                continue;
            }
            Map<String, Object> changed = typeValues(type, e.getValue());
            if (changed.isEmpty()) {
                continue;
            }
            Map<String, Object> originals = new HashMap<>();
            for (String name : changed.keySet()) {
                originals.put(name, getTypeField(type, name));
            }
            ORIGINALS.put(type, originals);
            changed.forEach((name, value) -> setTypeField(type, name, value));
        }
    }

    /** The EntityType field values an entity's type edits come to. */
    private static Map<String, Object> typeValues(EntityType<?> type, Map<String, String> edits) {
        Map<String, Object> out = new LinkedHashMap<>();
        EntityDimensions d = type.getDimensions();
        float width = d.width();
        float height = d.height();
        float eye = d.eyeHeight();
        boolean fixed = d.fixed();
        boolean size = false;
        for (Map.Entry<String, String> e : edits.entrySet()) {
            String v = e.getValue();
            switch (e.getKey()) {
                case "category" -> {
                    Object c = convert(v, MobCategory.class);
                    if (c != null) out.put("category", c);
                }
                case "summonable", "fire_immune", "spawn_far_from_player" -> {
                    Object b = convert(v, boolean.class);
                    if (b != null) out.put(FLAG_FIELDS.get(e.getKey()), b);
                }
                case "width", "height", "eye_height" -> {
                    Object n = convert(v, float.class);
                    if (n instanceof Float f && f > 0) {
                        size = true;
                        if (e.getKey().equals("width")) width = f;
                        else if (e.getKey().equals("height")) height = f;
                        else eye = f;
                    }
                }
                case "fixed_size" -> {
                    Object b = convert(v, boolean.class);
                    if (b instanceof Boolean f) {
                        size = true;
                        fixed = f;
                    }
                }
                // PLAN C STEP 3.
                case "tracking_range" -> {
                    if (convert(v, int.class) instanceof Integer n && n >= 0) {
                        out.put("trackingRangeSupplier", (java.util.function.ToIntFunction<EntityType<?>>) t -> n);
                    }
                }
                case "update_interval" -> {
                    if (convert(v, int.class) instanceof Integer n && n >= 1) {
                        out.put("updateIntervalSupplier", (java.util.function.ToIntFunction<EntityType<?>>) t -> n);
                    }
                }
                case "immune_to" -> {
                    com.google.common.collect.ImmutableSet<net.minecraft.world.level.block.Block> blocks = blocksOf(v);
                    // "none": it ignores no block at all (a wither skeleton hurt by a wither rose).
                    if (!blocks.isEmpty() || "none".equalsIgnoreCase(v.strip())) out.put("immuneTo", blocks);
                }
                // Set when the game starts, in the spawn placement event (SpawnPlacementEdits), not here.
                case "spawn_placement", "spawn_heightmap" -> {
                }
                default -> Strata.LOGGER.debug("Mob data: type value {} cannot be set", e.getKey());
            }
        }
        if (size) {
            out.put("dimensions", new EntityDimensions(width, height, eye, d.attachments(), fixed));
        }
        return out;
    }

    /** The blocks a comma list of ids names; an id the game has no block for is left out. */
    static com.google.common.collect.ImmutableSet<net.minecraft.world.level.block.Block> blocksOf(String list) {
        var out = com.google.common.collect.ImmutableSet.<net.minecraft.world.level.block.Block>builder();
        for (String raw : list.split(",")) {
            String id = raw.strip();
            var rl = id.isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                out.add(BuiltInRegistries.BLOCK.get(rl));
            }
        }
        return out.build();
    }

    /** The blocks a type ignores (EntityType#immuneTo), as a sorted comma list of ids; "" when none. */
    public static String immuneToOf(EntityType<?> type) {
        Object set = getTypeField(type, "immuneTo");
        if (!(set instanceof Iterable<?> blocks)) {
            return "";
        }
        java.util.TreeSet<String> ids = new java.util.TreeSet<>();
        for (Object b : blocks) {
            if (b instanceof net.minecraft.world.level.block.Block block) {
                var key = BuiltInRegistries.BLOCK.getKey(block);
                if (key != null) {
                    ids.add(key.toString());
                }
            }
        }
        return String.join(",", ids);
    }

    private static Object getTypeField(EntityType<?> type, String name) {
        try {
            Field f = EntityType.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Mob data: cannot read EntityType.{}: {}", name, e.toString());
            return null;
        }
    }

    private static void setTypeField(EntityType<?> type, String name, Object value) {
        if (value == null) {
            return;
        }
        try {
            Field f = EntityType.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(type, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Mob data: cannot set EntityType.{}: {}", name, e.toString());
        }
    }

    private static String cell(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.strip();
    }
}
