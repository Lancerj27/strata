package com.strata.runtime;

import com.strata.Strata;

import java.util.ArrayList;
import java.util.List;

/** WHAT A PLAYER NEEDS STRATA FOR ON THIS SERVER. */
public final class ServerOnly {

    private ServerOnly() {
    }

    /**
     * What a player's game without Strata cannot have on this server, said once as a dedicated server starts.
     */
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        if (!event.getServer().isDedicatedServer()) {
            return;
        }
        List<String> needs = new ArrayList<>();
        if (!VariantEntityRegistry.registeredBases().isEmpty()) {
            needs.add(VariantEntityRegistry.registeredBases().size() + " own-entity mob variant(s)");
        }
        if (!BlockVariants.all().isEmpty()) {
            needs.add(BlockVariants.all().size() + " block variant(s)");
        }
        if (BlockStatsRuntime.predictedByPlayers()) {
            needs.add("block stats a player's game works out itself (hardness, correct tool, friction, speed, jump)");
        }
        if (needs.isEmpty()) {
            Strata.LOGGER.info("Strata runs on this server alone: players join without Strata (their games show "
                    + "the game's own textures)");
        } else {
            Strata.LOGGER.warn("Players need Strata to join or to play right on this server, for: {}. "
                    + "Everything else runs on the server alone.", String.join("; ", needs));
        }
    }

}
