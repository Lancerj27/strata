package com.strata.runtime;

import com.strata.data.MobPropertyLayers;
import com.strata.model.VariantProperties;
import com.strata.network.StrataPayloads;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EACH MOB'S PROPERTIES, from its layers: the eight property tables stacked for the mob in hand - its base
 * mob at Global, tier and biome, then its variant at each - so a base mob's row and a variant's tier or biome
 * row apply, not only a variant's Global row.
 */
public final class MobProperties {

    private MobProperties() {
    }

    private static volatile MobPropertyLayers layers = MobPropertyLayers.EMPTY;
    /** Stacked values by entity, variant and scope; the same for every mob of that kind there. */
    private static final Map<String, Map<String, Map<String, String>>> STACKED = new ConcurrentHashMap<>();
    private static final Map<String, VariantProperties.Resolved> RESOLVED = new ConcurrentHashMap<>();
    /** Each mob's, so a tick does not build its key again. */
    private static final Map<Entity, VariantProperties.Resolved> PER_MOB = Collections.synchronizedMap(new WeakHashMap<>());
    /** What each client was last sent for a mob, so a reload sends only what changed. */
    private static final Map<Entity, Map<String, String>> SENT = Collections.synchronizedMap(new WeakHashMap<>());
    /** What this client was sent, by mob. */
    private static final Map<Entity, VariantProperties.Resolved> CLIENT = Collections.synchronizedMap(new WeakHashMap<>());

    /** The layers (Config#reload), and every mob's values afresh. */
    public static void load(MobPropertyLayers loaded) {
        layers = loaded;
        STACKED.clear();
        RESOLVED.clear();
        PER_MOB.clear();
    }

    /** The mob's variant or spawn scope changed: its values are worked out again on the next ask. */
    public static void forget(Entity mob) {
        PER_MOB.remove(mob);
    }

    /** The mob's properties; empty when nothing is set for it. */
    public static VariantProperties.Resolved forMob(LivingEntity mob) {
        if (mob.level().isClientSide()) {
            VariantProperties.Resolved sent = CLIENT.get(mob);
            return sent == null ? VariantProperties.Resolved.EMPTY : sent;
        }
        // Per mob first: flags and combat ask on every tick, and a key is a string built each time.
        VariantProperties.Resolved mine = PER_MOB.get(mob);
        if (mine == null) {
            String key = keyOf(mob);
            mine = RESOLVED.computeIfAbsent(key, k -> MobPropertyLayers.build(stacked(mob, k)));
            PER_MOB.put(mob, mine);
        }
        return mine;
    }

    private static Map<String, Map<String, String>> stacked(LivingEntity mob, String key) {
        return STACKED.computeIfAbsent(key, k -> {
            String packed = mob.getExistingData(MobLevel.VARIANT_SCOPE).orElse("");
            return layers.stacked(entityId(mob), mob.getExistingData(MobLevel.VARIANT_ID).orElse(""),
                    ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
        });
    }

    private static String keyOf(LivingEntity mob) {
        return entityId(mob) + '\u001F' + mob.getExistingData(MobLevel.VARIANT_ID).orElse("")
                + '\u001F' + mob.getExistingData(MobLevel.VARIANT_SCOPE).orElse("");
    }

    /** The base mob's id: a promoted variant's own type reads as the mob it copies. */
    static String entityId(LivingEntity mob) {
        return VariantEntityRegistry.baseEntityIdForType(mob.getType())
                .orElse(BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType())
                        .map(k -> k.location().toString()).orElse(""));
    }

    // ---- to the client ----

    /** A mob's stacked values flattened to group.column keys, for the wire. */
    static Map<String, String> flat(LivingEntity mob) {
        Map<String, String> out = new LinkedHashMap<>();
        stacked(mob, keyOf(mob)).forEach((group, values) ->
                values.forEach((column, value) -> out.put(group + "." + column, value)));
        return out;
    }

    /** The other way: group.column keys back into groups. */
    public static Map<String, Map<String, String>> grouped(Map<String, String> flat) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        flat.forEach((key, value) -> {
            int dot = key.indexOf('.');
            if (dot > 0) {
                out.computeIfAbsent(key.substring(0, dot), k -> new LinkedHashMap<>()).put(key.substring(dot + 1), value);
            }
        });
        return out;
    }

    /** A player starts to see a mob: send its values, when it has any. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity mob && event.getEntity() instanceof ServerPlayer player) {
            Map<String, String> values = flat(mob);
            if (!values.isEmpty()) {
                send(player, new StrataPayloads.MobProps(mob.getId(), values));
                SENT.put(mob, values);
            }
        }
    }

    /** After a reload: every loaded mob whose values changed, to every player who sees it. */
    public static void resend(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!(e instanceof LivingEntity mob) || e instanceof ServerPlayer) {
                    continue;
                }
                Map<String, String> values = flat(mob);
                Map<String, String> before = SENT.getOrDefault(mob, Map.of());
                if (!values.equals(before)) {
                    var payload = new StrataPayloads.MobProps(mob.getId(), values);
                    for (ServerPlayer p : level.players()) {
                        if (p.connection.hasChannel(payload.type())) {
                            PacketDistributor.sendToPlayersTrackingEntity(mob, payload);
                            break;
                        }
                    }
                    SENT.put(mob, values);
                }
            }
        }
    }

    private static void send(ServerPlayer player, StrataPayloads.MobProps payload) {
        if (player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** The client is told a mob's values (ClientNetwork, on the client thread). */
    public static void received(Entity mob, Map<String, String> values) {
        if (mob == null) {
            return;
        }
        if (values.isEmpty()) {
            CLIENT.remove(mob);
        } else {
            CLIENT.put(mob, MobPropertyLayers.build(grouped(values)));
        }
    }
}
