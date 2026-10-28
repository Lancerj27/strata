package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.domain.DisabledOps;
import com.strata.game.OreDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * REGENERATE: after a config is loaded and the server restarts, everything Strata affects is brought in line
 * with the new config, including land that already exists.
 */
public final class Regeneration {

    private Regeneration() {
    }

    /** The generation this run of the server updates to. */
    private static volatile int current;

    /** The block-rules generation this run of the server sweeps to. */
    private static volatile int blockCurrent;

    /** Chunks waiting for their update, per dimension. */
    private static final Map<ResourceKey<net.minecraft.world.level.Level>, Set<ChunkPos>> PENDING = new HashMap<>();

    /** How many chunks one server tick updates, so a big backlog cannot stall a tick. */
    private static final int CHUNKS_PER_TICK = 2;

    /** The generation now, for stamping what spawns or generates. */
    public static int current() {
        return current;
    }

    /** The block-rules generation now: what a chunk must be stamped with to have swept the current rules. */
    public static int blockCurrent() {
        return blockCurrent;
    }

    /**
     * Re-checks the block rules (the disabled set, the replacement rules) after a {@code Config.reload} made
     * with a world open, and re-arms the existing-land sweep when either changed.
     */
    public static void rearmBlockRules(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return;
        }
        try {
            State state = state(server);
            int hash = DisabledOps.ids(DisabledOps.BLOCK).hashCode() * 31 + BlockSwapRuntime.rulesHash();
            if (hash != state.blockHash) {
                state.blockHash = hash;
                state.blockGeneration++;
                state.setDirty();
                Strata.LOGGER.info("Strata: the block rules changed; existing land comes in line as it loads");
            }
            blockCurrent = state.blockGeneration;
            // A chunk already in memory never fires ChunkEvent.Load again, so the bump alone would leave
            // every loaded chunk out: put the ones now stale on the sweep queue.
            for (ServerLevel level : server.getAllLevels()) {
                ServerChunkCache source = (ServerChunkCache) level.getChunkSource();
                for (ChunkHolder holder : source.chunkMap.getChunks()) {
                    LevelChunk chunk = holder.getTickingChunk();
                    if (chunk != null && needsUpdate(chunk.getData(MobLevel.BLOCK_GENERATION), blockCurrent)) {
                        synchronized (PENDING) {
                            PENDING.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(chunk.getPos());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Strata.LOGGER.warn("Strata: could not re-arm the block-rules sweep: {}", e.toString());
        }
    }

    // ---- the saved state ----

    /** The world's regenerate state: its generation, a pending request, and the disabled-block sweep. */
    public static final class State extends SavedData {
        int generation;
        boolean pending;
        /** Bumps whenever the replacement rules or the disabled block set change, so existing chunks sweep them once. */
        int blockGeneration;
        /** The last block rules seen (their hash), so a change is known. */
        int blockHash;

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.generation = tag.getInt("generation");
            state.pending = tag.getBoolean("pending");
            state.blockGeneration = tag.getInt("block_generation");
            state.blockHash = tag.getInt("block_hash");
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("generation", generation);
            tag.putBoolean("pending", pending);
            tag.putInt("block_generation", blockGeneration);
            tag.putInt("block_hash", blockHash);
            return tag;
        }
    }

    private static final SavedData.Factory<State> FACTORY =
            new SavedData.Factory<>(State::new, State::load, null);

    private static State state(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "strata_regenerate");
    }

    /** Queues a regenerate for the next start. */
    public static void request(MinecraftServer server) {
        State state = state(server);
        state.pending = true;
        state.setDirty();
    }

    /** What a player is told after a profile is loaded on a running server. */
    public static final String RESTART_NOTICE =
            "Restart the server to apply it: spawn lists, ores, and mobs update at the next start.";

    /**
     * Loads a profile on this server: it becomes the active profile (every start loads it), and a regenerate
     * is queued for the next start.
     */
    public static com.strata.data.ProfileManager.Result loadProfile(MinecraftServer server, String name) {
        var result = com.strata.data.ProfileManager.activateProfile(name);
        if (result == com.strata.data.ProfileManager.Result.OK) {
            request(server);
            // The profile's sound and texture replacements reach the players now.
            com.strata.network.AssetServer.libraryChanged(server);
        }
        return result;
    }

    /** True when a regenerate waits for the next start. */
    public static boolean pending(MinecraftServer server) {
        return state(server).pending;
    }

    /** At start: a pending request becomes the new generation. */
    static void begin(ServerLevel overworld) {
        State state = overworld.getDataStorage().computeIfAbsent(FACTORY, "strata_regenerate");
        if (state.pending) {
            state.generation++;
            state.pending = false;
            state.setDirty();
            Strata.LOGGER.info("Strata regenerate {}: mobs and existing chunks update as they load",
                    state.generation);
        }
        // A CHANGE TO THE BLOCK RULES - a disabled block, or a replacement rule - sweeps existing chunks on
        // its own generation, with no /strata regenerate and no reprocessing of mobs.
        int blockHash = DisabledOps.ids(DisabledOps.BLOCK).hashCode() * 31 + BlockSwapRuntime.rulesHash();
        if (blockHash != state.blockHash) {
            state.blockHash = blockHash;
            state.blockGeneration++;
            state.setDirty();
            Strata.LOGGER.info("Strata: the block rules changed; existing land comes in line as it loads");
        }
        current = state.generation;
        blockCurrent = state.blockGeneration;
    }

    /** At stop: forget this world's run, so the next world starts clean. */
    public static void onServerStopped() {
        current = 0;
        blockCurrent = 0;
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    // ---- the decisions, pure so the gate can pin them ----

    /** True when a mob or chunk stamped {@code stamped} needs the update to {@code now}. */
    public static boolean needsUpdate(int stamped, int now) {
        return now > 0 && stamped < now;
    }

    /** True when an ore block of {@code ore} goes: the biome restricts ores, and not this one. */
    public static boolean removesOre(String ore, Set<String> allowed) {
        return allowed != null && !allowed.contains(ore);
    }

    /**
     * The ores to place into an existing chunk of a biome: the ones the config allows there that the biome
     * did not have, and that the chunk has none of yet (so a second regenerate does not double them).
     */
    public static Set<String> oresToAdd(Set<String> allowed, Set<String> original, Set<String> inChunk) {
        Set<String> out = new LinkedHashSet<>();
        if (allowed == null) {
            return out;
        }
        for (String ore : allowed) {
            if (!original.contains(ore) && !inChunk.contains(ore)) {
                out.add(ore);
            }
        }
        return out;
    }

    // ---- ore blocks ----

    /** An ore block: its ore name, and the block it replaced, which it turns back into. */
    private record OreBlock(String ore, BlockState host) {
    }

    private static volatile Map<Block, OreBlock> oreBlocks;

    /**
     * The blocks of the managed ores, from the game (OreDetector): each with the block its
     * feature's rule replaced, so a nether ore turns back into netherrack and a deepslate ore into deepslate.
     */
    private static Map<Block, OreBlock> oreBlocks() {
        Map<Block, OreBlock> map = oreBlocks;
        if (map != null) {
            return map;
        }
        map = new HashMap<>();
        for (var e : OreDetector.oreBlocks().entrySet()) {
            if (BuiltInRegistries.BLOCK.containsKey(e.getKey())) {
                map.put(BuiltInRegistries.BLOCK.get(e.getKey()),
                        new OreBlock(e.getValue().ore(), host(e.getValue().host())));
            }
        }
        oreBlocks = Map.copyOf(map);
        return oreBlocks;
    }

    /** Drops the ore-block map, so a world's ore detection takes effect (OreData). */
    static void resetOreBlocks() {
        oreBlocks = null;
    }

    /** The block a "block:<id>" or "tag:<id>" host names; stone when it names none. */
    private static BlockState host(String host) {
        ResourceLocation id = ResourceLocation.tryParse(host.substring(host.indexOf(':') + 1));
        if (id != null && host.startsWith("block:") && BuiltInRegistries.BLOCK.containsKey(id)) {
            return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        }
        if (id != null && host.startsWith("tag:")) {
            var tag = BuiltInRegistries.BLOCK.getTag(net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK, id));
            if (tag.isPresent() && tag.get().size() > 0) {
                return tag.get().get(0).value().defaultBlockState();
            }
        }
        return Blocks.STONE.defaultBlockState();
    }
    // ---- the per-chunk update ----

    /**
     * Brings one existing chunk in line: the block rules (disabled blocks, replacements) sweep it if either
     * set changed, and the ores are updated if a regenerate is due.
     */
    static void updateChunk(ServerLevel level, LevelChunk chunk) {
        if (needsUpdate(chunk.getData(MobLevel.BLOCK_GENERATION), blockCurrent)) {
            BlockSwapRuntime.stripExisting(level, chunk);
            chunk.setData(MobLevel.BLOCK_GENERATION, blockCurrent);
            chunk.setUnsaved(true);
        }
        if (needsUpdate(chunk.getData(MobLevel.GENERATION), current)) {
            updateOres(level, chunk);
            chunk.setData(MobLevel.GENERATION, current);
        }
    }

    /** Updates one existing chunk's ores to the current config. */
    private static void updateOres(ServerLevel level, LevelChunk chunk) {
        Map<Block, OreBlock> ores = oreBlocks();
        Map<Holder<Biome>, Set<String>> allowedCache = new HashMap<>();
        Set<String> inChunk = new HashSet<>();
        Set<Holder<Biome>> biomes = new LinkedHashSet<>();
        ChunkPos pos = chunk.getPos();
        int removed = 0;

        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            section.getBiomes().getAll(biomes::add);
            if (section.hasOnlyAir() || !section.maybeHas(state -> ores.containsKey(state.getBlock()))) {
                continue;
            }
            int baseY = chunk.getMinBuildHeight() + si * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        OreBlock ore = ores.get(state.getBlock());
                        if (ore == null) {
                            continue;
                        }
                        int bx = pos.getMinBlockX() + x;
                        int by = baseY + y;
                        int bz = pos.getMinBlockZ() + z;
                        Holder<Biome> biome = chunk.getNoiseBiome(bx >> 2, by >> 2, bz >> 2);
                        Set<String> allowed = allowedCache.computeIfAbsent(biome, Regeneration::allowedIn);
                        if (removesOre(ore.ore(), allowed)) {
                            level.setBlock(new BlockPos(bx, by, bz), ore.host(), Block.UPDATE_CLIENTS);
                            removed++;
                        } else {
                            inChunk.add(ore.ore());
                        }
                    }
                }
            }
        }

        int added = 0;
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        BlockPos origin = new BlockPos(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ());
        Set<String> done = new HashSet<>();
        for (Holder<Biome> biome : biomes) {
            Set<String> allowed = allowedCache.computeIfAbsent(biome, Regeneration::allowedIn);
            Set<String> original = biome.unwrapKey()
                    .map(k -> OreRestrictionModifier.originalOres(k.location())).orElse(Set.of());
            for (String ore : oresToAdd(allowed, original, inChunk)) {
                if (!done.add(ore)) {
                    continue;
                }
                RandomSource random = RandomSource.create(level.getSeed() ^ pos.toLong() ^ ore.hashCode());
                for (ResourceLocation id : OreDetector.placedFeaturesFor(ore).orElse(java.util.List.of())) {
                    var holder = placed.getHolder(ResourceKey.create(Registries.PLACED_FEATURE, id));
                    if (holder.isPresent() && holder.get().value().place(
                            level, level.getChunkSource().getGenerator(), random, origin)) {
                        added++;
                    }
                }
            }
        }

        chunk.setUnsaved(true);
        if (removed > 0 || added > 0) {
            Strata.LOGGER.debug("Regenerate: chunk {} - {} ore block(s) removed, {} ore feature(s) placed",
                    pos, removed, added);
        }
    }

    /** The ores a biome allows, or null when its ores are not restricted. */
    private static Set<String> allowedIn(Holder<Biome> biome) {
        OreConfig.AllowedOres allowed = OreConfig.allowedInWithFrequencies(biome);
        return allowed == null ? null : Set.copyOf(allowed.names());
    }

    /** True for a mob that would despawn on its own: not named, tamed, or otherwise kept. */
    private static boolean despawnable(Mob mob) {
        return !mob.isPersistenceRequired() && !mob.requiresCustomPersistence()
                && mob.removeWhenFarAway(Double.MAX_VALUE);
    }

    // ---- events ----

    /** The game-bus listeners. */
    public static final class Events {

        /** The overworld loads before any chunk of any dimension. */
        @SubscribeEvent
        public void onLevelLoad(LevelEvent.Load event) {
            if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
                begin(level);
            }
        }

        @SubscribeEvent
        public void onEntityJoin(EntityJoinLevelEvent event) {
            if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                    || !(event.getEntity() instanceof Mob mob)) {
                return;
            }
            if (!needsUpdate(mob.getData(MobLevel.GENERATION), current)) {
                return;
            }
            if (despawnable(mob)) {
                event.setCanceled(true);
                return;
            }
            Config.reapply(mob, level);
            mob.setData(MobLevel.GENERATION, current);
        }

        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load event) {
            if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
                return;
            }
            if (event.isNewChunk()) {
                chunk.setData(MobLevel.GENERATION, current);
                chunk.setData(MobLevel.BLOCK_GENERATION, blockCurrent);
                chunk.setUnsaved(true);
                return;
            }
            boolean stale = needsUpdate(chunk.getData(MobLevel.GENERATION), current)
                    || needsUpdate(chunk.getData(MobLevel.BLOCK_GENERATION), blockCurrent);
            if (stale) {
                synchronized (PENDING) {
                    PENDING.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(chunk.getPos());
                }
            }
        }

        @SubscribeEvent
        public void onChunkUnload(ChunkEvent.Unload event) {
            if (event.getLevel() instanceof ServerLevel level) {
                synchronized (PENDING) {
                    Set<ChunkPos> set = PENDING.get(level.dimension());
                    if (set != null) {
                        set.remove(event.getChunk().getPos());
                    }
                }
            }
        }

        /** Updates a few waiting chunks each tick. */
        @SubscribeEvent
        public void onServerTick(ServerTickEvent.Post event) {
            if (current == 0 && blockCurrent == 0) {
                return;
            }
            int budget = CHUNKS_PER_TICK;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                synchronized (PENDING) {
                    Set<ChunkPos> set = PENDING.get(level.dimension());
                    if (set == null) {
                        continue;
                    }
                    for (Iterator<ChunkPos> it = set.iterator(); it.hasNext() && budget > 0; ) {
                        ChunkPos pos = it.next();
                        if (!neighboursLoaded(level, pos)) {
                            continue;
                        }
                        it.remove();
                        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
                        if (chunk != null) {
                            // ONE CHUNK CANNOT STOP THE SERVER.
                            try {
                                updateChunk(level, chunk);
                            } catch (RuntimeException e) {
                                Strata.LOGGER.warn("Regenerate: chunk {} in {} skipped: {}",
                                        pos, level.dimension().location(), e.toString(), e);
                            }
                            budget--;
                        }
                    }
                }
                if (budget == 0) {
                    return;
                }
            }
        }

        private static boolean neighboursLoaded(ServerLevel level, ChunkPos pos) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getChunkSource().getChunkNow(pos.x + dx, pos.z + dz) == null) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
