package com.strata.client;

import com.strata.Strata;
import com.strata.data.GameLayer;
import com.strata.runtime.MobExtractor;
import com.strata.runtime.MobModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.WalkAnimationState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** A mob's edited model and texture, on the client (MobModels). */
public final class ModelSwap {

    private ModelSwap() {
    }

    /** Each swapped mob's puppet. */
    private static final Map<LivingEntity, LivingEntity> PUPPETS = new WeakHashMap<>();
    /** Each puppet's mob, weakly both ways, so the two never hold each other. */
    private static final Map<LivingEntity, WeakReference<LivingEntity>> OWNERS = new WeakHashMap<>();
    private static final Map<String, Optional<ResourceLocation>> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<EntityType<?>>> TYPES = new ConcurrentHashMap<>();

    /** The renderers, kept from their building for the texture read. */
    private static volatile Map<EntityType<?>, EntityRenderer<?>> renderers = Map.of();

    // ------------------------------------------------------------- drawing

    /** The texture a mob is drawn with instead of its own, or null. */
    public static ResourceLocation texture(LivingEntity entity) {
        WeakReference<LivingEntity> owner = OWNERS.get(entity);
        LivingEntity who = owner == null ? entity : owner.get();
        if (who == null) {
            return null;
        }
        String value = MobModels.clientValue(who, "texture");
        return value == null ? null : TEXTURES.computeIfAbsent(value,
                v -> Optional.ofNullable(ResourceLocation.tryParse(v))).orElse(null);
    }

    /** Draws a mob as its edited model (game bus, client). */
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (OWNERS.containsKey(entity)) {
            return;
        }
        String model = MobModels.clientValue(entity, "model");
        if (model == null) {
            return;
        }
        EntityType<?> type = TYPES.computeIfAbsent(model, m -> {
            ResourceLocation id = ResourceLocation.tryParse(m);
            return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        }).orElse(null);
        if (type == null || type == entity.getType()) {
            return;
        }
        LivingEntity puppet = puppet(entity, type);
        if (puppet == null) {
            return;
        }
        copyPose(entity, puppet);
        EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(puppet);
        float partialTick = event.getPartialTick();
        event.setCanceled(true);
        renderer.render(puppet, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()), partialTick,
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    private static LivingEntity puppet(LivingEntity entity, EntityType<?> type) {
        LivingEntity puppet = PUPPETS.get(entity);
        if (puppet != null && puppet.getType() == type && puppet.level() == entity.level()) {
            return puppet;
        }
        Entity made;
        try {
            made = type.create(entity.level());
        } catch (RuntimeException e) {
            made = null;
        }
        if (!(made instanceof LivingEntity living)) {
            Strata.LOGGER.debug("Model: {} cannot be drawn as {}", entity.getType(), type);
            return null;
        }
        if (living instanceof Mob mob) {
            mob.setNoAi(true);
        }
        PUPPETS.put(entity, living);
        OWNERS.put(living, new WeakReference<>(entity));
        // The hitbox of the model (MobModels#onSize): a variant is known by its name, which a mob is sent
        // after it joins, so it is sized again here.
        entity.refreshDimensions();
        return living;
    }

    /** The fields the renderers animate from, mob to puppet. */
    private static void copyPose(LivingEntity from, LivingEntity to) {
        to.setPosRaw(from.getX(), from.getY(), from.getZ());
        to.xo = from.xo;
        to.yo = from.yo;
        to.zo = from.zo;
        to.xOld = from.xOld;
        to.yOld = from.yOld;
        to.zOld = from.zOld;
        to.setYRot(from.getYRot());
        to.yRotO = from.yRotO;
        to.setXRot(from.getXRot());
        to.xRotO = from.xRotO;
        to.yBodyRot = from.yBodyRot;
        to.yBodyRotO = from.yBodyRotO;
        to.yHeadRot = from.yHeadRot;
        to.yHeadRotO = from.yHeadRotO;
        to.tickCount = from.tickCount;
        to.hurtTime = from.hurtTime;
        to.hurtDuration = from.hurtDuration;
        to.deathTime = from.deathTime;
        to.attackAnim = from.attackAnim;
        to.oAttackAnim = from.oAttackAnim;
        to.swinging = from.swinging;
        to.swingTime = from.swingTime;
        to.setOnGround(from.onGround());
        to.setPose(from.getPose());
        to.setInvisible(from.isInvisible());
        to.setShiftKeyDown(from.isShiftKeyDown());
        to.setCustomName(from.getCustomName());
        to.setCustomNameVisible(from.isCustomNameVisible());
        if (from instanceof AgeableMob a && to instanceof AgeableMob b) {
            b.setBaby(a.isBaby());
        }
        copyWalk(from.walkAnimation, to.walkAnimation);
        animate(to);
    }

    // ------------------------------------------- its own particles, dropped

    /** The mobs ticking now on the client, innermost last (a rider ticks inside its mount). */
    private static final java.util.ArrayDeque<Boolean> TICKING_SWAPPED = new java.util.ArrayDeque<>();

    /** A mob starts its client tick (ClientLevelParticlesMixin). */
    public static void ticking(Entity entity) {
        boolean live = entity instanceof LivingEntity && !OWNERS.containsKey((LivingEntity) entity);
        LivingEntity living = live ? (LivingEntity) entity : null;
        TICKING_SWAPPED.push(live && MobModels.clientValue(living, "model") != null
                && com.strata.runtime.MobModels.modelType(living) != null
                && com.strata.runtime.MobModels.dropsBaseParticles(living));
        // THE MOB'S OWN PARTICLES, CHANGED OR OFF, whatever model it is drawn as.
        TICKING_PARTICLES.push(!live ? KEEP
                : com.strata.runtime.MobModels.particleReplacement(living) != null
                        ? com.strata.runtime.MobModels.particleReplacement(living)
                : com.strata.runtime.MobModels.particlesOff(living) ? OFF : KEEP);
    }

    /** Its tick ends. */
    public static void ticked() {
        TICKING_SWAPPED.poll();
        TICKING_PARTICLES.poll();
    }

    /** Markers on the particle stack: keep the mob's own, or drop them. */
    private static final net.minecraft.core.particles.ParticleOptions KEEP = net.minecraft.core.particles.ParticleTypes.NOTE;
    private static final net.minecraft.core.particles.ParticleOptions OFF = net.minecraft.core.particles.ParticleTypes.ASH;
    private static final java.util.ArrayDeque<net.minecraft.core.particles.ParticleOptions> TICKING_PARTICLES =
            new java.util.ArrayDeque<>();
    /** True while a replacement is being added, so it is not replaced again. */
    private static boolean replacing;

    /** What ClientLevelParticlesMixin does with a particle the ticking mob adds: true cancels it. */
    public static boolean intercept(net.minecraft.core.particles.ParticleOptions particle,
                                    java.util.function.Consumer<net.minecraft.core.particles.ParticleOptions> add) {
        if (replacing || particle.getType() == net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT) {
            return false;
        }
        net.minecraft.core.particles.ParticleOptions plan = TICKING_PARTICLES.peek();
        if (plan != null && plan != KEEP) {
            if (plan != OFF) {
                replacing = true;
                try {
                    add.accept(plan);
                } finally {
                    replacing = false;
                }
            }
            return true;
        }
        return dropsParticle(particle);
    }

    /**
     * True for a particle a mob drawn as another model adds in its own tick: it was placed for the mob's own
     * model.
     */
    public static boolean dropsParticle(net.minecraft.core.particles.ParticleOptions particle) {
        Boolean swapped = TICKING_SWAPPED.peek();
        return swapped != null && swapped
                && particle.getType() != net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT;
    }

    /** Each class's own animation step, or none: looked up once per class. */
    private static final Map<Class<?>, Optional<java.lang.reflect.Method>> ANIMATE = new ConcurrentHashMap<>();

    /** THE MODEL'S OWN ANIMATION. */
    private static void animate(LivingEntity puppet) {
        ANIMATE.computeIfAbsent(puppet.getClass(), c -> {
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                try {
                    java.lang.reflect.Method m = k.getDeclaredMethod("setupAnimationStates");
                    m.setAccessible(true);
                    return Optional.of(m);
                } catch (NoSuchMethodException | RuntimeException e) {
                    // Not on this class: try its parent.
                }
            }
            return Optional.empty();
        }).ifPresent(m -> {
            try {
                m.invoke(puppet);
            } catch (ReflectiveOperationException | RuntimeException e) {
                ANIMATE.put(puppet.getClass(), Optional.empty());
            }
        });
    }

    private static final Field[] WALK = walkFields();

    private static Field[] walkFields() {
        List<Field> out = new ArrayList<>();
        for (Field f : WalkAnimationState.class.getDeclaredFields()) {
            if (f.getType() == float.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out.toArray(new Field[0]);
    }

    private static void copyWalk(WalkAnimationState from, WalkAnimationState to) {
        try {
            for (Field f : WALK) {
                f.setFloat(to, f.getFloat(from));
            }
        } catch (IllegalAccessException e) {
            // Leaves the puppet's legs still; never stops the drawing.
        }
    }

    /** True for a stand-in: a texture made for its mob's model is not drawn on it (VariantTextureLayer). */
    public static boolean isPuppet(LivingEntity entity) {
        return OWNERS.containsKey(entity);
    }

    /** Sizes every loaded mob again after the models change (MobModels#onChange). */
    public static void resizeLoaded() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.level == null) {
                return;
            }
            for (Entity e : minecraft.level.entitiesForRendering()) {
                if (e instanceof LivingEntity) {
                    e.refreshDimensions();
                }
            }
        });
    }

    // ------------------------------------------------- each mob's own texture

    /** Keeps the renderers as they are built, then reads the textures if the mobs are in (mod bus). */
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        Map<EntityType<?>, EntityRenderer<?>> out = new HashMap<>();
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?> r = event.getRenderer(type);
            if (r != null) {
                out.put(type, r);
            }
        }
        renderers = out;
        readTextures();
    }

    /**
     * Writes each extracted mob's own texture to game/textures.csv, once the renderers and the mobs both
     * exist; MobExtractor calls it too, after it makes the mobs.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized void readTextures() {
        Map<EntityType<?>, EntityRenderer<?>> byType = renderers;
        if (byType.isEmpty() || MobExtractor.MADE.isEmpty()) {
            return;
        }
        List<GameLayer.Row> rows = new ArrayList<>();
        MobExtractor.MADE.forEach((id, mob) -> {
            EntityRenderer renderer = byType.get(mob.getType());
            if (renderer == null) {
                return;
            }
            try {
                ResourceLocation texture = renderer.getTextureLocation(mob);
                if (texture != null) {
                    rows.add(new GameLayer.Row(id, GameLayer.MODEL, "texture", texture.toString()));
                }
            } catch (RuntimeException e) {
                Strata.LOGGER.debug("Model: could not read the texture of {}: {}", id, e.toString());
            }
        });
        if (GameLayer.write(GameLayer.dir().resolve(GameLayer.TEXTURES), """
                The GAME LAYER: each mob's own texture, read from its renderer at every client start. Do not edit.""",
                rows)) {
            Strata.LOGGER.info("Game layer: read {} mob texture(s)", rows.size());
        }
        MobExtractor.MADE.clear();
    }
}
