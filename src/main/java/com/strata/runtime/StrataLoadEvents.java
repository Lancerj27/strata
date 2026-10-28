package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.data.Defaults;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Loads the configuration when a world starts. */
public class StrataLoadEvents {

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        Defaults.writeIfMissing();

        Config.bind(event.getServer().registryAccess(), event.getServer());

        Config.reload();

        Strata.LOGGER.info("Strata ready: {}", Config.tables().tiers().isEmpty()
                ? "no tiers configured, so nothing is scaled"
                : Config.tables().tiers().size() + " tier(s) active");
    }

    /** Builds the ore index once levels exist. */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        OreData.buildIndex(event.getServer().overworld());
        // The override sheet's game values: every recipe (and later kind) as the game and its mods left them,
        // below Strata's own pack.
        com.strata.data.JsonOverrides.extract(event.getServer().getResourceManager());
        // Game rules per profile: every rule's default read, the profile's set.
        GameRulesData.read();
        GameRulesData.apply(event.getServer());
        // The debug spawn biome, when one is set.
        PlayerSpawnBiome.onServerStarted(event.getServer());
        // The game layer is not filled here: it is filled at game start, before any world (MobExtractor).
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Regeneration.onServerStopped();
        // Back to the startup registries.
        Config.serverStopped();
    }
}
