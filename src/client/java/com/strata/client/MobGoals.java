package com.strata.client;

import com.strata.game.GameData;
import com.strata.model.GameGoal;
import com.strata.network.StrataNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** A mob's own AI goals, for the editor's explanations. */
public final class MobGoals {

    private MobGoals() {
    }

    /**
     * The answer so far.
     *
     * @param goals the goals, or null while there are none to show
     * @param note why there are none: "reading", no world, no permission
     * @param pending true while an answer is on its way, so the caller asks again
     */
    public record Answer(List<GameGoal> goals, String note, boolean pending) {
    }

    private static final Map<String, CompletableFuture<List<String>>> LOCAL = new ConcurrentHashMap<>();
    private static volatile MinecraftServer localServer;

    /** The mob's own goals, or why they are not known yet. */
    public static Answer lookup(String entity) {
        Minecraft minecraft = Minecraft.getInstance();
        String id = entity == null ? "" : entity.strip();
        // THE GAME LAYER FIRST: every mob's goals were read when the game started, so no world is needed to
        // show them.
        List<GameGoal> extracted = com.strata.domain.GoalsOps.extractedGoals(id);
        if (!extracted.isEmpty()) {
            return new Answer(extracted, "", false);
        }
        if (minecraft.player == null) {
            return new Answer(null, "The game's Brain runs this mob. Goals you add run beside it; while one moves the mob, the Brain waits.", false);
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        List<String> raw;
        if (server != null) {
            if (server != localServer) {
                LOCAL.clear();
                localServer = server;
            }
            CompletableFuture<List<String>> future = LOCAL.computeIfAbsent(id,
                    k -> server.submit(() -> GameData.defaultGoals(k, server)));
            if (!future.isDone()) {
                return new Answer(null, "Reading this mob's own goals...", true);
            }
            raw = future.getNow(List.of());
        } else if (minecraft.player.hasPermissions(StrataNetwork.OPERATOR)) {
            raw = ClientNetwork.goalsFor(id);
            if (raw == null) {
                return new Answer(null, "Asking the server for this mob's own goals...", true);
            }
        } else {
            return new Answer(null, "Only a server operator can read a mob's own goals.", false);
        }
        return new Answer(raw.stream().map(GameGoal::decode).toList(), "", false);
    }
}
