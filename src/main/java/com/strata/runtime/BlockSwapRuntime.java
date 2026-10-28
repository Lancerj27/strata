package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.model.BlockSwap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** BLOCK REPLACEMENT IN NEWLY GENERATED CHUNKS. */
public final class BlockSwapRuntime {

    private BlockSwapRuntime() {
    }

    /** How far into each neighbouring chunk the sweep reaches, in blocks. */
    public static final int MARGIN = 8;

    /** What a replaced block becomes: a block, or null for air. */
    private record Target(Block to) {
    }

    /** The target every disabled block takes: air. */
    private static final Target AIR = new Target(null);

    /** The rules as loaded, with each biome's resolved map built on first use. */
    private static final class Snapshot {
        final List<BlockSwap.Rule> rules;
        final Map<String, Map<Block, Target>> byBiome = new ConcurrentHashMap<>();
        private volatile Set<Block> sources;

        Snapshot(List<BlockSwap.Rule> rules) {
            this.rules = rules;
        }

        /** Every block any rule replaces, or that is disabled, resolved on first use (the registry is full by then). */
        Set<Block> sources() {
            Set<Block> s = sources;
            if (s == null) {
                Set<Block> out = new HashSet<>();
                for (String id : BlockSwap.sources(rules)) {
                    Block b = block(id);
                    if (b != null) {
                        out.add(b);
                    }
                }
                // A DISABLED BLOCK IS OUT OF NEW LAND TOO: it is swept as if every scope replaced it with
                // air, so a structure's water goes with the terrain's.
                out.addAll(DisabledContent.blocks());
                sources = s = Set.copyOf(out);
            }
            return s;
        }

        Map<Block, Target> forBiome(String biome) {
            return byBiome.computeIfAbsent(biome, id -> {
                Map<String, String> ids = BlockSwap.rulesFor(rules, Config.resolve(id).tier(), id);
                Map<Block, Target> out = new HashMap<>();
                for (Map.Entry<String, String> e : ids.entrySet()) {
                    Block from = block(e.getKey());
                    if (from == null) {
                        continue;
                    }
                    if (BlockSwap.DISABLED.equals(e.getValue())) {
                        out.put(from, new Target(null));
                    } else {
                        Block to = block(e.getValue());
                        if (to != null) {
                            out.put(from, new Target(to));
                        }
                    }
                }
                return out.isEmpty() ? Map.of() : Map.copyOf(out);
            });
        }
    }

    private static volatile Snapshot snapshot = new Snapshot(List.of());
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Reads block_replacements.csv (Config#reload). */
    public static void load(Path file) {
        List<String[]> rows = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(file)) {
            rows.add(new String[]{row.get(Schema.BlockReplacements.SCOPE), row.get(Schema.BlockReplacements.SCOPE_ID),
                    row.get(Schema.BlockReplacements.FROM_BLOCK), row.get(Schema.BlockReplacements.TO_BLOCK)});
        }
        snapshot = new Snapshot(List.copyOf(BlockSwap.rules(rows)));
    }

    /** The block an id names, or null (warned once) when the game has none. */
    private static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
            return BuiltInRegistries.BLOCK.get(rl);
        }
        if (WARNED.add(id)) {
            Strata.LOGGER.warn("Block replacements: no block {} in this game - its rule is skipped", id);
        }
        return null;
    }

    /** True when any rule is loaded, or a block is disabled: what the hook asks before any work. */
    public static boolean active() {
        return !snapshot.rules.isEmpty() || DisabledContent.blocksActive();
    }

    /**
     * The state a replaced one becomes: the target's default state with every property both blocks share
     * carried over (a staircase's facing and half), or air.
     */
    static BlockState become(BlockState old, Block to) {
        if (to == null) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState out = to.defaultBlockState();
        for (Property<?> p : old.getProperties()) {
            if (out.hasProperty(p)) {
                out = carry(out, old, p);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState carry(BlockState out, BlockState old, Property<T> p) {
        return out.setValue(p, old.getValue(p));
    }

    /**
     * The hook's call: sweeps the chunk just decorated, and the band of each neighbour that its features may
     * have reached.
     */
    public static void apply(WorldGenLevel level, ChunkAccess chunk) {
        Snapshot snap = snapshot;
        if (snap.rules.isEmpty() && !DisabledContent.blocksActive()) {
            return;
        }
        try {
            Set<Block> sources = snap.sources();
            if (sources.isEmpty()) {
                return;
            }
            Set<Block> disabled = DisabledContent.blocks();
            int cx = chunk.getPos().x;
            int cz = chunk.getPos().z;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkAccess target = dx == 0 && dz == 0 ? chunk : level.getChunk(cx + dx, cz + dz, ChunkStatus.EMPTY, false);
                    if (target != null) {
                        sweep(snap, sources, disabled, target, dx, dz);
                    }
                }
            }
        } catch (RuntimeException e) {
            if (WARNED.add("sweep:" + e)) {
                Strata.LOGGER.error("Block replacements: a chunk could not be swept: {}", e.toString(), e);
            }
        }
    }

    /** Sweeps one chunk: all of it (dx, dz 0), or the band of it nearest the chunk at (-dx, -dz). */
    private static void sweep(Snapshot snap, Set<Block> sources, Set<Block> disabled, ChunkAccess chunk, int dx, int dz) {
        int x0 = dx < 0 ? 16 - MARGIN : 0;
        int x1 = dx > 0 ? MARGIN : 16;
        int z0 = dz < 0 ? 16 - MARGIN : 0;
        int z1 = dz > 0 ? MARGIN : 16;
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunkSection[] sections = chunk.getSections();
        @SuppressWarnings("unchecked")
        Map<Block, Target>[] cells = new Map[64];
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            // THE PALETTE CHECK: a section holding none of the replaced blocks costs one read.
            if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> sources.contains(s.getBlock()))) {
                continue;
            }
            int baseY = chunk.getMinBuildHeight() + si * 16;
            java.util.Arrays.fill(cells, null);
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = z0; lz < z1; lz++) {
                    for (int lx = x0; lx < x1; lx++) {
                        BlockState old = section.getBlockState(lx, ly, lz);
                        Block block = old.getBlock();
                        if (!sources.contains(block)) {
                            continue;
                        }
                        // A DISABLED BLOCK COMES OUT, in every biome, and wins over a replacement rule that
                        // names it.
                        Target t;
                        if (disabled.contains(block)) {
                            t = AIR;
                        } else {
                            // The biome of this block's 4x4x4 cell, resolved once per cell per section.
                            int cell = ((ly >> 2) << 4) | ((lz >> 2) << 2) | (lx >> 2);
                            Map<Block, Target> map = cells[cell];
                            if (map == null) {
                                Holder<Biome> biome = chunk.getNoiseBiome((baseX + lx) >> 2, (baseY + ly) >> 2, (baseZ + lz) >> 2);
                                map = biome.unwrapKey().map(k -> snap.forBiome(k.location().toString())).orElse(Map.of());
                                cells[cell] = map;
                            }
                            t = map.get(block);
                        }
                        if (t != null) {
                            chunk.setBlockState(pos.set(baseX + lx, baseY + ly, baseZ + lz), become(old, t.to()), false);
                        }
                    }
                }
            }
        }
    }

    /** Brings land already in the world in line with the replacement rules and the disabled blocks. */
    public static void stripExisting(ServerLevel level, LevelChunk chunk) {
        Snapshot snap = snapshot;
        Set<Block> sources = snap.sources();
        if (sources.isEmpty()) {
            return;
        }
        Set<Block> disabled = DisabledContent.blocks();
        try {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            LevelChunkSection[] sections = chunk.getSections();
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            @SuppressWarnings("unchecked")
            Map<Block, Target>[] cells = new Map[64];
            for (int si = 0; si < sections.length; si++) {
                LevelChunkSection section = sections[si];
                if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> sources.contains(s.getBlock()))) {
                    continue;
                }
                int baseY = chunk.getMinBuildHeight() + si * 16;
                java.util.Arrays.fill(cells, null);
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int lx = 0; lx < 16; lx++) {
                            BlockState old = section.getBlockState(lx, ly, lz);
                            Block block = old.getBlock();
                            if (!sources.contains(block)) {
                                continue;
                            }
                            // A DISABLED BLOCK COMES OUT in every biome, and wins over a rule that names it.
                            Target t;
                            if (disabled.contains(block)) {
                                t = AIR;
                            } else {
                                int cell = ((ly >> 2) << 4) | ((lz >> 2) << 2) | (lx >> 2);
                                Map<Block, Target> map = cells[cell];
                                if (map == null) {
                                    Holder<Biome> biome = chunk.getNoiseBiome((baseX + lx) >> 2, (baseY + ly) >> 2, (baseZ + lz) >> 2);
                                    map = biome.unwrapKey().map(k -> snap.forBiome(k.location().toString())).orElse(Map.of());
                                    cells[cell] = map;
                                }
                                t = map.get(block);
                            }
                            if (t != null) {
                                level.setBlock(pos.set(baseX + lx, baseY + ly, baseZ + lz), become(old, t.to()), Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            if (WARNED.add("existing:" + e)) {
                Strata.LOGGER.error("Block replacements: an existing chunk could not be swept: {}", e.toString(), e);
            }
        }
    }

    /**
     * The replacement rules as a hash (order-independent): what Regeneration stamps a sweep with, so a change
     * to any rule's scope, id or blocks sweeps existing land once.
     */
    public static int rulesHash() {
        return Set.copyOf(snapshot.rules).hashCode();
    }
}
