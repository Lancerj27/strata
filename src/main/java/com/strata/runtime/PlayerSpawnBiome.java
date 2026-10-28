package com.strata.runtime;

import com.mojang.datafixers.util.Pair;
import com.strata.Strata;
import com.strata.data.StrataConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** THE PLAYER SPAWN, A DEBUG TOOL. */
public final class PlayerSpawnBiome {

    private PlayerSpawnBiome() {
    }

    /** How far the search goes, in blocks. */
    static int radius() {
        try {
            return com.strata.data.StrataConfig.PLAYER_SPAWN_RADIUS.get();
        } catch (RuntimeException e) {
            return 6400;
        }
    }

    /** The search's steps (as /locate biome). */
    private static final int STEP = 32;
    private static final int STEP_Y = 64;

    /** At server start: moves the world spawn when a biome is set. */
    public static void onServerStarted(MinecraftServer server) {
        apply(server, false);
    }

    /** After the setting changed: moves the world spawn now, and the players with it. */
    public static void applyNow() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isRunning()) {
            return;
        }
        // On the server thread and finished before this returns: a task left to run later (server.execute) can
        // land after the world has begun to close, writing the spawn through a save that is already under way.
        try {
            if (server.isSameThread()) {
                apply(server, true);
            } else {
                server.submit(() -> apply(server, true)).join();
            }
        } catch (RuntimeException e) {
            Strata.LOGGER.error("Could not move the world spawn: {}", e.toString(), e);
        }
    }

    private static void apply(MinecraftServer server, boolean movePlayers) {
        com.strata.domain.PlayerSpawnOps.Target target =
                com.strata.domain.PlayerSpawnOps.parse(StrataConfig.DEBUG_PLAYER_SPAWN.get());
        if (target.kind() == com.strata.domain.PlayerSpawnOps.Kind.OFF) {
            return;
        }
        ServerLevel level = server.overworld();
        var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        // The biomes to look for: the one picked, or every biome of the tier picked.
        java.util.List<String> ids = target.kind() == com.strata.domain.PlayerSpawnOps.Kind.BIOME
                ? java.util.List.of(target.id())
                : com.strata.data.Config.tables().biomes().values().stream()
                        .filter(b -> target.id().equals(b.tier())).map(com.strata.model.BiomeConfig::biome).toList();
        java.util.Set<ResourceKey<Biome>> keys = new java.util.HashSet<>();
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && registry.get(rl) != null) {
                keys.add(ResourceKey.create(Registries.BIOME, rl));
            }
        }
        String id = target.kind() == com.strata.domain.PlayerSpawnOps.Kind.BIOME ? target.id()
                : "the tier " + target.id();
        if (keys.isEmpty()) {
            tell(server, "Strata debug: " + id + " has no biome of this world. The spawn is not moved.");
            return;
        }
        java.util.function.Predicate<Holder<Biome>> wanted = h -> keys.stream().anyMatch(h::is);
        BlockPos spawn = level.getSharedSpawnPos();
        if (wanted.test(level.getBiome(spawn))) {
            if (movePlayers) {
                moveAll(server, level, spawn);
            }
            return;
        }
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(wanted, spawn, radius(), STEP, STEP_Y);
        if (found == null) {
            tell(server, "Strata debug: no " + id + " within " + radius() + " blocks of the spawn. It is not moved.");
            return;
        }
        BlockPos at = found.getFirst();
        BlockPos spot = spawnSpot(level, at, wanted);
        if (spot == null) {
            // The biome was found under an ocean: the spawn stays rather than go to the sea floor.
            tell(server, "Strata debug: " + id + " was found only under water near " + at.toShortString()
                    + ". The spawn is not moved.");
            return;
        }
        level.setDefaultSpawnPos(spot, 0f);
        String where = found.getSecond().unwrapKey().map(k -> k.location().toString()).orElse(id);
        tell(server, "Strata debug: world spawn moved to " + where + " at " + spot.toShortString() + ".");
        if (movePlayers) {
            moveAll(server, level, spot);
        }
    }

    /**
     * The height to stand on at x, z: the top of the ground, from the generator's noise. No chunk is loaded or
     * made: a search that loaded every column it looked at froze the game for the whole search.
     */
    public static int surfaceY(ServerLevel level, int x, int z) {
        return baseHeight(level, x, z, Heightmap.Types.WORLD_SURFACE_WG);
    }

    private static int baseHeight(ServerLevel level, int x, int z, Heightmap.Types type) {
        var source = level.getChunkSource();
        return source.getGenerator().getBaseHeight(x, z, type, level, source.randomState());
    }

    /**
     * Where to stand for a biome the search found, or null when there is none. The found point can lie below
     * an ocean whose water hides the biome, so the surface there is under water: then the nearest surface of
     * the same biome that is not under water is used (a coastline is a few chunks away).
     */
    private static BlockPos spawnSpot(ServerLevel level, BlockPos at, java.util.function.Predicate<Holder<Biome>> wanted) {
        BlockPos surface = new BlockPos(at.getX(), surfaceY(level, at.getX(), at.getZ()), at.getZ());
        if (dry(level, at.getX(), at.getZ()) && wanted.test(level.getBiome(surface))) {
            return surface;
        }
        int cap = STEP * 8;
        for (int d = STEP; d <= cap; d += STEP) {
            for (int dx = -d; dx <= d; dx += STEP) {
                for (int dz = -d; dz <= d; dz += STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    int x = at.getX() + dx;
                    int z = at.getZ() + dz;
                    BlockPos p = new BlockPos(x, surfaceY(level, x, z), z);
                    if (dry(level, x, z) && wanted.test(level.getBiome(p))) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** True when the column's surface is solid ground, not water: the ground is as high as the surface. */
    private static boolean dry(ServerLevel level, int x, int z) {
        return baseHeight(level, x, z, Heightmap.Types.OCEAN_FLOOR_WG) >= surfaceY(level, x, z);
    }

    private static void moveAll(MinecraftServer server, ServerLevel level, BlockPos to) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
    }

    private static void tell(MinecraftServer server, String text) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal(text));
        }
        Strata.LOGGER.info(text);
    }
}
