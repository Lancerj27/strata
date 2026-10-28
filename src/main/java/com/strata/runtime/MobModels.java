package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Schema;
import com.strata.model.Variant;
import com.strata.model.VariantProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MobModels {

    private MobModels() {}

    private static final String SEP = "|";
    private static final String VARIANT = "var:";
    private static final String NAME = "@";

    private static volatile Map<String, String> local = Map.of();
    private static volatile Map<String, String> active = Map.of();
    private static volatile Map<String, Map<String, String>> gameSounds = Map.of();
    private static final Map<String, Optional<SoundEvent>> SOUNDS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<EntityType<?>>> TYPES = new ConcurrentHashMap<>();

    public static void load(Path mobData, Collection<Variant> variants) {
        Map<String, String> names = new HashMap<>();
        variants.forEach(v -> names.put(v.id(), v.displayName()));
        local = Map.copyOf(index(CsvTable.read(mobData), names));
        active = local;
        Map<String, Map<String, String>> sounds = new HashMap<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS))) {
            if (GameLayer.MODEL.equals(r.group()) && r.key().startsWith("sound_")) {
                sounds.computeIfAbsent(r.entity(), k -> new HashMap<>()).put(r.key(), r.value());
            }
        }
        gameSounds = Map.copyOf(sounds);
        SOUNDS.clear();
        TYPES.clear();
        onChange.run();
        Strata.LOGGER.info("Mob models: {} model, texture, or sound edit(s)", local.size());
    }

    public static Map<String, String> index(List<Map<String, String>> rows, Map<String, String> displayNames) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            if (!GameLayer.MODEL.equals(cell(row, Schema.MobData.GROUP))) {
                continue;
            }
            String entity = cell(row, Schema.MobData.ENTITY);
            String variant = cell(row, Schema.MobData.VARIANT_NAME);
            String key = cell(row, Schema.MobData.KEY);
            String value = cell(row, Schema.MobData.VALUE);
            if (entity.isEmpty() || key.isEmpty() || value.isEmpty()
                    || !cell(row, Schema.MobData.SCOPE).isEmpty()) {
                continue;
            }
            if (variant.isEmpty()) {
                out.put(entity + SEP + key, value);
            } else {
                out.put(VARIANT + variant + SEP + key, value);
                String name = displayNames.get(variant);
                if (name != null && !name.isBlank()) {
                    out.put(NAME + name + SEP + key, value);
                }
            }
        }
        return out;
    }

    public static Map<String, String> clientIndex(Map<String, String> index) {
        Map<String, String> out = new LinkedHashMap<>();
        index.forEach((k, v) -> {
            if (!k.startsWith(VARIANT) && (k.endsWith(SEP + "model") || k.endsWith(SEP + "texture"))) {
                out.put(k, v);
            }
        });
        return out;
    }

    public static Map<String, String> clientIndex() {
        return clientIndex(local);
    }

    public static void useServerIndex(Map<String, String> index) {
        active = Map.copyOf(index);
        onChange.run();
    }

    public static void useLocalIndex() {
        active = local;
        onChange.run();
    }

    public static String clientValue(LivingEntity entity, String key) {
        // The mob's own properties, stacked for it (MobProperties): they win over the mob data index.
        var props = MobProperties.forMob(entity);
        String propVal = getPropertyValue(props, key);
        if (propVal != null) {
            return propVal;
        }
        // A model of its own with no texture of its own: the model's, not the base mob's.
        if (key.equals("texture") && props.model != null) {
            return null;
        }

        Map<String, String> index = active;
        if (index.isEmpty()) {
            return null;
        }
        Component name = entity.getCustomName();
        String entityId = entityId(entity);
        if (key.equals("texture")) {
            return texture(index, name == null ? null : NAME + name.getString(), entityId);
        }
        if (name != null) {
            String v = index.get(NAME + name.getString() + SEP + key);
            if (v != null) {
                return v;
            }
        }
        return index.get(entityId + SEP + key);
    }

    /** The particle this mob's own particles become, or null to keep them. */
    /** A particle id as a particle the game can draw with no settings, or null. */
    public static net.minecraft.core.particles.SimpleParticleType particleOf(String raw) {
        String id = raw == null ? "" : raw.strip();
        var rl = id.isEmpty() ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        var type = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(rl);
        return type instanceof net.minecraft.core.particles.SimpleParticleType simple ? simple : null;
    }

    public static net.minecraft.core.particles.ParticleOptions particleReplacement(LivingEntity entity) {
        var props = MobProperties.forMob(entity);
        String id = props.particleType == null ? "" : props.particleType.strip();
        if (id.isEmpty()) {
            return null;
        }
        var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        var type = rl == null ? null : net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(rl);
        return type instanceof net.minecraft.core.particles.SimpleParticleType simple ? simple : null;
    }

    /** True when the mob's own particles are switched off (mob_particles.csv particle_enabled = false). */
    public static boolean particlesOff(LivingEntity entity) {
        return Boolean.FALSE.equals(MobProperties.forMob(entity).particleEnabled);
    }

    /** The model and texture the mob's own properties name; the rest apply where they act (MobPropertyEffects). */
    private static String getPropertyValue(VariantProperties.Resolved props, String key) {
        return switch (key) {
            case "texture" -> props.texture;
            case "model" -> props.model;
            default -> null;
        };
    }

    /** Whether this mob, drawn as another model, drops the particles the base mob's own tick placed. */
    public static boolean dropsBaseParticles(LivingEntity entity) {
        return MobProperties.forMob(entity).dropsBaseParticles();
    }

    public static String texture(Map<String, String> index, String variant, String entity) {
        if (variant != null) {
            String own = index.get(variant + SEP + "texture");
            if (own != null) {
                return own;
            }
            String model = index.get(variant + SEP + "model");
            if (model != null && !model.equals(index.getOrDefault(entity + SEP + "model", entity))) {
                return null;
            }
        }
        return index.get(entity + SEP + "texture");
    }

    public static String serverValue(Map<String, String> index, String entity, String variant, String key) {
        if (variant != null && !variant.isEmpty()) {
            String v = index.get(VARIANT + variant + SEP + key);
            if (v != null) {
                return v;
            }
        }
        return index.get(entity + SEP + key);
    }

    public static SoundEvent sound(Entity entity, SoundEvent played) {
        if (played == null || !(entity instanceof LivingEntity living)
                || entity.level().isClientSide) {
            return played;
        }
        String id = entityId(living);
        String variant = living.getExistingData(MobLevel.VARIANT_ID).orElse("");
        if (variant.isEmpty()) {
            variant = Variant.defaultNameFor(id);
        }

        var props = MobProperties.forMob(living);
        {
            SoundEvent swap = findSoundOverride(props, played);
            if (swap != null) {
                return swap;
            }
        }

        if (local.isEmpty() || gameSounds.isEmpty()) {
            return played;
        }
        Map<String, String> own = gameSounds.get(id);
        if (own == null) {
            return played;
        }
        String playedId = played.getLocation().toString();
        for (Map.Entry<String, String> e : own.entrySet()) {
            if (e.getValue().equals(playedId)) {
                String swap = serverValue(local, id, variant, e.getKey());
                return swap == null ? played : resolve(swap).orElse(played);
            }
        }
        return played;
    }


    /**
     * A mob's own volume for this sound's slot (mob_sounds.csv ambient, hurt, death and step volume),
     * else the game's.
     */
    public static float volume(Entity entity, SoundEvent played, float game) {
        Float own = scale(entity, played, true);
        return own == null ? game : own;
    }

    /** The same for pitch. */
    public static float pitch(Entity entity, SoundEvent played, float game) {
        Float own = scale(entity, played, false);
        return own == null ? game : own;
    }

    private static Float scale(Entity entity, SoundEvent played, boolean volume) {
        if (!(entity instanceof LivingEntity living) || entity.level().isClientSide || played == null) {
            return null;
        }
        var p = MobProperties.forMob(living);
        String id = played.getLocation().getPath().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("hurt")) {
            return volume ? p.hurtVolume : p.hurtPitch;
        }
        if (id.contains("death")) {
            return volume ? p.deathVolume : p.deathPitch;
        }
        if (id.contains("step") || id.contains("walk") || id.contains("foot")) {
            return volume ? p.stepVolume : p.stepPitch;
        }
        if (id.contains("ambient") || id.contains("idle")) {
            return volume ? p.ambientVolume : p.ambientPitch;
        }
        return null;
    }

    private static SoundEvent findSoundOverride(VariantProperties.Resolved props, SoundEvent played) {
        String playedId = played.getLocation().toString();
        if (props.ambientSound != null && matchesGameSound(props.ambientSound, playedId, "ambient")) {
            return resolve(props.ambientSound).orElse(null);
        }
        if (props.hurtSound != null && matchesGameSound(props.hurtSound, playedId, "hurt")) {
            return resolve(props.hurtSound).orElse(null);
        }
        if (props.deathSound != null && matchesGameSound(props.deathSound, playedId, "death")) {
            return resolve(props.deathSound).orElse(null);
        }
        if (props.stepSound != null && matchesGameSound(props.stepSound, playedId, "step")) {
            return resolve(props.stepSound).orElse(null);
        }
        if (props.attackSound != null && matchesGameSound(props.attackSound, playedId, "attack")) {
            return resolve(props.attackSound).orElse(null);
        }
        if (props.swimSound != null && matchesGameSound(props.swimSound, playedId, "swim")) {
            return resolve(props.swimSound).orElse(null);
        }
        if (props.fallSound != null && matchesGameSound(props.fallSound, playedId, "fall")) {
            return resolve(props.fallSound).orElse(null);
        }
        return null;
    }

    private static boolean matchesGameSound(String overrideId, String playedId, String type) {
        if (overrideId.equals(playedId)) {
            return true;
        }
        String lower = playedId.toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "ambient" -> lower.contains("ambient") || lower.contains("idle");
            case "hurt" -> lower.contains("hurt");
            case "death" -> lower.contains("death");
            case "step" -> lower.contains("step") || lower.contains("walk") || lower.contains("foot");
            case "attack" -> lower.contains("attack") || lower.contains("shoot");
            case "swim" -> lower.contains("swim");
            case "fall" -> lower.contains("fall");
            default -> false;
        };
    }

    private static Optional<SoundEvent> resolve(String id) {
        return SOUNDS.computeIfAbsent(id, k -> {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            if (rl == null) {
                return Optional.empty();
            }
            // A custom sound is not in the registry: its event is made from its id, and the game plays it as
            // it plays any sound, from the pack's sounds.json (a direct holder on the wire).
            if (com.strata.domain.CustomAssetsOps.NAMESPACE.equals(rl.getNamespace())) {
                return Optional.of(SoundEvent.createVariableRangeEvent(rl));
            }
            return BuiltInRegistries.SOUND_EVENT.getOptional(rl);
        });
    }

    public static String entityId(LivingEntity entity) {
        return VariantEntityRegistry.baseEntityIdForType(entity.getType())
                .orElse(BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType())
                        .map(k -> k.location().toString()).orElse(""));
    }

    public static volatile Runnable onChange = () -> { };

    /** The server's model for a mob: its own property, else the mob data index. */
    private static String serverModel(LivingEntity living) {
        String own = MobProperties.forMob(living).model;
        return own != null ? own : serverValue(local, entityId(living),
                living.getExistingData(MobLevel.VARIANT_ID).orElse(""), "model");
    }

    public static EntityType<?> modelType(LivingEntity living) {
        String model = living.level().isClientSide ? clientValue(living, "model")
                : serverModel(living);
        if (model == null) {
            return null;
        }
        EntityType<?> type = TYPES.computeIfAbsent(model, m -> {
            ResourceLocation id = ResourceLocation.tryParse(m);
            return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        }).orElse(null);
        return type == living.getType() ? null : type;
    }

    public static void onSize(net.neoforged.neoforge.event.entity.EntityEvent.Size event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || event.getPose() == Pose.SLEEPING
                || living instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        try {
            if (MobDataRuntime.sizeEdited(living.getType())) {
                return;
            }
            EntityType<?> type = modelType(living);
            if (type != null) {
                event.setNewSize(type.getDimensions().scale(living.getScale()));
                return;
            }

            // ONLY A SIZE OF ITS OWN resizes it: a mob with a sound swap alone keeps its own size.
            var props = MobProperties.forMob(living);
            if (props.width != null || props.height != null || props.eyeHeight != null) {
                EntityDimensions own = living.getType().getDimensions();
                double width = props.width != null ? props.width : own.width();
                double height = props.height != null ? props.height : own.height();
                double eyeHeight = props.eyeHeight != null ? props.eyeHeight
                        : props.height != null ? height * 0.85 : own.eyeHeight();

                EntityDimensions dims = EntityDimensions.fixed((float) width, (float) height).withEyeHeight((float) eyeHeight);
                event.setNewSize(dims.scale(living.getScale()));
            }
        } catch (RuntimeException e) {
            if (SIZE_WARNED.add(living.getType())) {
                Strata.LOGGER.warn("Could not apply the custom size of {}: {}", living.getType(), e.toString());
            }
        }
    }

    private static final java.util.Set<EntityType<?>> SIZE_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static String cell(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.strip();
    }
}




