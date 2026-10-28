package com.strata.runtime;

import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** THE PLAYER'S BASE STATS. */
public final class PlayerBaseStats {

    private PlayerBaseStats() {
    }

    /** The player's entity id in mob_base_stats.csv. */
    public static final ResourceLocation PLAYER = ResourceLocation.withDefaultNamespace("player");
    /** The list of attributes Strata set, in the player's PlayerPersisted data. */
    private static final String TAG = "strata_base_stats";

    /** Sets the player's base values: the user's, and the game's for any Strata set before. */
    public static void apply(ServerPlayer player) {
        Map<Holder<Attribute>, Double> overrides = EntityBaseAttributes.overridesOf(PLAYER);
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag had = persisted.getList(TAG, Tag.TAG_STRING);
        AttributeSupplier defaults = DefaultAttributes.getSupplier(EntityType.PLAYER);
        Set<String> now = new HashSet<>();
        overrides.keySet().forEach(h -> h.unwrapKey().ifPresent(k -> now.add(k.location().toString())));
        for (int i = 0; i < had.size(); i++) {
            String id = had.getString(i);
            if (now.contains(id)) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Holder<Attribute> attribute = rl == null ? null
                    : BuiltInRegistries.ATTRIBUTE.getHolder(ResourceKey.create(
                            net.minecraft.core.registries.Registries.ATTRIBUTE, rl)).orElse(null);
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance != null && defaults.hasAttribute(attribute)) {
                instance.setBaseValue(defaults.getBaseValue(attribute));
            }
        }
        ListTag set = new ListTag();
        overrides.forEach((attribute, value) -> {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.setBaseValue(value);
                attribute.unwrapKey().ifPresent(k -> set.add(StringTag.valueOf(k.location().toString())));
            }
        });
        persisted.put(TAG, set);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** Sets every online player's base values again, after an edit (on the server thread). */
    public static void applyOnline(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                apply(player);
            } catch (RuntimeException e) {
                Strata.LOGGER.warn("Player base stats: could not set {}: {}", player.getName().getString(), e.toString());
            }
        }
    }
}
