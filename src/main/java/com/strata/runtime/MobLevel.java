package com.strata.runtime;

import com.strata.Strata;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A mob's level, assigned exactly once at spawn from the biome's resolved level (see MobScalingHandler) and
 * stored so it never needs recomputing.
 */
public record MobLevel(int level) {

    public static final MobLevel UNSET = new MobLevel(-1);

    public static final Codec<MobLevel> CODEC = Codec.INT.xmap(MobLevel::new, MobLevel::level);

    public boolean isSet() {
        return level >= 0;
    }

    // ---- Registration ----

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Strata.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<MobLevel>> MOB_LEVEL =
            ATTACHMENT_TYPES.register("mob_level", () ->
                    AttachmentType.builder(() -> UNSET)
                            .serialize(CODEC)
                            .build());

    /** The regenerate generation a mob or a chunk was last made or updated under. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> GENERATION =
            ATTACHMENT_TYPES.register("generation", () ->
                    AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    /** The generation of the block rules a chunk was last swept under. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> BLOCK_GENERATION =
            ATTACHMENT_TYPES.register("block_generation", () ->
                    AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    /** The variant this mob rolled at spawn, or "" for none. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> VARIANT_ID =
            ATTACHMENT_TYPES.register("variant_id", () ->
                    AttachmentType.<String>builder(() -> "")
                            .serialize(Codec.STRING)
                            .build());

    /** The tier and biome this creature SPAWNED in, packed by ScopeIndex. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> VARIANT_SCOPE =
            ATTACHMENT_TYPES.register("variant_scope", () ->
                    AttachmentType.<String>builder(() -> "")
                            .serialize(com.mojang.serialization.Codec.STRING)
                            .build());

    /**
     * The scoreboard tags Strata itself added to this mob (comma-separated), so a tag later taken out of its
     * row is taken off again and a tag the mob had of its own is never touched.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> STRATA_TAGS =
            ATTACHMENT_TYPES.register("strata_tags", () ->
                    AttachmentType.<String>builder(() -> "")
                            .serialize(com.mojang.serialization.Codec.STRING)
                            .build());

    /** True from the spawn until the mob joins its level, when its equipment and behavior go on. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> EQUIPMENT_PENDING =
            ATTACHMENT_TYPES.register("equipment_pending", () ->
                    AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    /** SET AT SPAWN, CLEARED AT THE FIRST JOIN: the mob's on_spawn effect rows have yet to fire. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> SPAWN_TRIGGER_PENDING =
            ATTACHMENT_TYPES.register("spawn_trigger_pending", () ->
                    AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL)
                            .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Double>> COEFFICIENT =
            ATTACHMENT_TYPES.register("coefficient", () ->
                    AttachmentType.<Double>builder(() -> 1.0)
                            .serialize(Codec.DOUBLE)
                            .build());
}
