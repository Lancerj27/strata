package com.strata.runtime;

import com.strata.Strata;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.CustomSpawner;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * DEFAULT IS THE GAME'S OWN (gradlew runVanillaCheck): on a fresh install, so on Default, each place Strata
 * could touch the game is checked as the game left it. Writes "Vanilla check: PASS" or the failures, then
 * stops the server. Off unless -Dstrata.vanillacheck=true.
 */
public final class VanillaCheck {

    private VanillaCheck() {}

    public static void onServerStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("strata.vanillacheck")) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<String> failed = failures(server);
        if (failed.isEmpty()) {
            Strata.LOGGER.info("Vanilla check: PASS");
        } else {
            failed.forEach(f -> Strata.LOGGER.error("Vanilla check: FAIL {}", f));
        }
        server.halt(false);
    }

    static List<String> failures(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        if (!com.strata.data.ProfileManager.DEFAULT.equals(com.strata.data.ProfileManager.activeProfile())) {
            out.add("the active profile is " + com.strata.data.ProfileManager.activeProfile() + ", not Default");
        }
        // Extra mob attributes: mobs the game gives neither keep neither.
        for (EntityType<? extends net.minecraft.world.entity.LivingEntity> type : List.of(EntityType.COW,
                EntityType.SHEEP, EntityType.VILLAGER, EntityType.PIG, EntityType.SQUID)) {
            var supplier = DefaultAttributes.getSupplier(type);
            if (supplier.hasAttribute(Attributes.ATTACK_DAMAGE) || supplier.hasAttribute(Attributes.FLYING_SPEED)) {
                out.add(EntityType.getKey(type) + " has an attribute the game does not give it");
            }
        }
        // Special spawners: the game's own objects in every level.
        for (ServerLevel level : server.getAllLevels()) {
            for (CustomSpawner s : spawners(level)) {
                if (s.getClass().getName().startsWith("com.strata")) {
                    out.add(level.dimension().location() + " runs " + s.getClass().getSimpleName());
                }
            }
        }
        // Trades: the game's own listings.
        boolean slots = Stream.concat(
                        VillagerTrades.TRADES.values().stream().flatMap(m -> m.values().stream()),
                        VillagerTrades.WANDERING_TRADER_TRADES.values().stream())
                .flatMap(Arrays::stream).anyMatch(l -> l instanceof TradesRuntime.Slot);
        if (slots) {
            out.add("a trade list holds Strata's slots");
        }
        // Override sheets: none in the working config.
        java.nio.file.Path sheets = com.strata.data.Paths.ROOT.resolve("overrides");
        if (Files.isDirectory(sheets)) {
            try (var files = Files.list(sheets)) {
                files.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> out.add("an override sheet is active: " + p.getFileName()));
            } catch (java.io.IOException e) {
                out.add("the override sheets could not be read: " + e);
            }
        }
        // The generated pack: no data at all (no recipe, loot table, tag, loot modifier or override).
        try (var files = Files.walk(com.strata.data.StrataDatapack.PACK_DIR)) {
            files.filter(Files::isRegularFile)
                    .map(p -> com.strata.data.StrataDatapack.PACK_DIR.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> p.startsWith("data/"))
                    .forEach(p -> out.add("the generated pack has " + p));
        } catch (java.io.IOException e) {
            out.add("the generated pack could not be read: " + e);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<CustomSpawner> spawners(ServerLevel level) {
        for (var f : ServerLevel.class.getDeclaredFields()) {
            if (f.getType() == List.class && f.getName().equals("customSpawners")) {
                try {
                    f.setAccessible(true);
                    return (List<CustomSpawner>) f.get(level);
                } catch (ReflectiveOperationException e) {
                    break;
                }
            }
        }
        throw new IllegalStateException("ServerLevel#customSpawners not found");
    }
}
