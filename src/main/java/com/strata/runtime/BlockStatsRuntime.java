package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.model.BlockStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** EVERY BLOCK'S STATS: THE GAME'S READ AT START, THE USER'S SERVED LIVE. */
public final class BlockStatsRuntime {

    private BlockStatsRuntime() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every block's stats, rewritten at every game start. Do not edit: your values go in block_stats.csv.
            group component: key a block stats key, value the game's. group note: LIT when only its lit states give light. group kind: the list's group.""";

    /** One block's own values; NaN (or -1 for a whole number, null for a switch) is the game's. */
    public static final class Own {
        public float hardness = Float.NaN;
        public float blastResistance = Float.NaN;
        public float friction = Float.NaN;
        public float speedFactor = Float.NaN;
        public float jumpFactor = Float.NaN;
        public int light = -1;
        /** Only the states the game lights take the light (a lamp, a furnace). */
        public boolean lightWhenLit;
        public Boolean requiresTool;
        public int flammability = -1;
        public int fireSpread = -1;
        public Boolean replaceable;
        public net.minecraft.world.level.material.PushReaction pushReaction;
        public net.minecraft.world.level.block.state.properties.NoteBlockInstrument instrument;
        public Boolean ignitedByLava;
        // PLAN C STEP 5: the rest of what the game builds a block from.
        public SoundType sound;
        public net.minecraft.world.level.material.MapColor mapColor;
        public Boolean randomTicks;
        public Boolean solid;
        public Boolean collision;
        public Boolean occludes;
        public Boolean redstoneConductor;
        public Boolean terrainParticles;
        public Boolean emissive;
        public Boolean mobsSpawnOn;
        public Boolean suffocating;
        public Boolean viewBlocking;
        /** none, xz or xyz; null is the game's. */
        public String offset;
    }

    /** The game's public constant of a class by its lower-case name (SoundType "wood"), or null. */
    private static <T> T constant(Class<T> type, String name) {
        for (java.lang.reflect.Field f : type.getFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())
                        && f.getName().equalsIgnoreCase(name)) {
                    return type.cast(f.get(null));
                }
            } catch (IllegalAccessException e) {
                // A field we may not read is not the one.
            }
        }
        return null;
    }

    /** The lower-case names of a class's public constants of its own type, in the order the class declares them. */
    private static List<String> constantNames(Class<?> type) {
        List<String> out = new ArrayList<>();
        for (java.lang.reflect.Field f : type.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                out.add(f.getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    /**
     * Where an offset block sits by its position (the game's own sums, BlockBehaviour.Properties#offsetType),
     * or null for the game's offset.
     */
    public static net.minecraft.world.phys.Vec3 offsetOf(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                         net.minecraft.core.BlockPos pos, String offset) {
        if (offset == null) {
            return null;
        }
        var function = Offsets.ALL.get(offset);
        return function == null ? net.minecraft.world.phys.Vec3.ZERO : function.evaluate(state, level, pos);
    }

    /**
     * The game's own offset functions, xz and xyz, as BlockBehaviour.Properties#offsetType makes them (read
     * off a Properties built for each), so an offset block sits where the game would put it.
     */
    private static final class Offsets {
        /** Made on first use, once the game's block classes are up. */
        static final Map<String, net.minecraft.world.level.block.state.BlockBehaviour.OffsetFunction> ALL = offsets();
    }

    private static Map<String, net.minecraft.world.level.block.state.BlockBehaviour.OffsetFunction> offsets() {
        Map<String, net.minecraft.world.level.block.state.BlockBehaviour.OffsetFunction> out = new java.util.HashMap<>();
        try {
            java.lang.reflect.Field f = net.minecraft.world.level.block.state.BlockBehaviour.Properties.class
                    .getDeclaredField("offsetFunction");
            f.setAccessible(true);
            for (var type : List.of(net.minecraft.world.level.block.state.BlockBehaviour.OffsetType.XZ,
                    net.minecraft.world.level.block.state.BlockBehaviour.OffsetType.XYZ)) {
                Object made = f.get(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().offsetType(type));
                out.put(type.name().toLowerCase(java.util.Locale.ROOT),
                        (net.minecraft.world.level.block.state.BlockBehaviour.OffsetFunction) made);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Block stats: the game's offset functions could not be read ({}); offsets stay the game's",
                    e.toString());
        }
        return Map.copyOf(out);
    }

    /** The user's values by block id, key to value, from block_stats.csv. */
    private static volatile Map<String, Map<String, String>> edits = Map.of();
    /** The block variants' own values by variant name, from block_variant_values.csv. */
    private static volatile Map<String, Map<String, String>> variantEdits = Map.of();
    /** The same, resolved to the blocks. */
    private static volatile Map<Block, Own> own = Map.of();
    /** Set while this thread reads the game's values: the mixins answer with the game's. */
    private static final ThreadLocal<Boolean> READING = ThreadLocal.withInitial(() -> false);

    /** The block's own values, or null (the game's). */
    public static Own of(Block block) {
        Map<Block, Own> map = own;
        if (map.isEmpty()) {
            return null;
        }
        Own o = map.get(block);
        return o == null || READING.get() ? null : o;
    }

    /**
     * The user edited a value a player's game also works out on its own, so a game without Strata sees the
     * game's: hardness and the correct-tool rule (mining is predicted there), friction and the speed and jump
     * factors (movement is).
     */
    public static boolean predictedByPlayers() {
        java.util.Set<String> predicted = java.util.Set.of(BlockStats.HARDNESS, BlockStats.REQUIRES_TOOL,
                BlockStats.FRICTION, BlockStats.SPEED_FACTOR, BlockStats.JUMP_FACTOR,
                // : what a player's own game works out too - bumping into it, its sounds and look.
                BlockStats.COLLISION, BlockStats.SOUND, BlockStats.OCCLUDES, BlockStats.TERRAIN_PARTICLES,
                BlockStats.EMISSIVE, BlockStats.VIEW_BLOCKING, BlockStats.OFFSET);
        return edits.values().stream().anyMatch(m -> m.keySet().stream().anyMatch(predicted::contains));
    }

    /** Reads block_stats.csv (Config#reload). */
    public static void load() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockStats.FILE))) {
            String block = row.getOrDefault(Schema.BlockStats.BLOCK, "").strip();
            String key = row.getOrDefault(Schema.BlockStats.KEY, "").strip();
            String value = row.getOrDefault(Schema.BlockStats.VALUE, "").strip();
            if (!block.isEmpty() && !key.isEmpty() && !value.isEmpty()) {
                out.computeIfAbsent(block, k -> new LinkedHashMap<>()).put(key, value);
            }
        }
        edits = Map.copyOf(out);
        Map<String, Map<String, String>> variants = new LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockVariantValues.FILE))) {
            String variant = row.getOrDefault(Schema.BlockVariantValues.VARIANT, "").strip();
            String key = row.getOrDefault(Schema.BlockVariantValues.KEY, "").strip();
            String value = row.getOrDefault(Schema.BlockVariantValues.VALUE, "").strip();
            if (!variant.isEmpty() && BlockStats.KEYS.contains(key) && !value.isEmpty()) {
                variants.computeIfAbsent(variant, k -> new LinkedHashMap<>()).put(key, value);
            }
        }
        variantEdits = Map.copyOf(variants);
        resolve(false);
    }

    /** Puts the edits on the blocks. */
    private static void resolve(boolean warn) {
        Map<Block, Own> out = new IdentityHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : edits.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                if (warn) Strata.LOGGER.warn("Block stats: no block {} in this game; its rows are skipped", e.getKey());
                continue;
            }
            Block block = BuiltInRegistries.BLOCK.get(id);
            out.put(block, own(block, e.getKey(), e.getValue(), warn));
        }
        // A block variant: its own values, else its base block's (the user's, else the game's, which its
        // copied properties hold).
        for (BlockVariants.Def def : BlockVariants.all()) {
            String baseId = BlockVariants.idOf(def.base());
            Map<String, String> merged = new LinkedHashMap<>(edits.getOrDefault(baseId, Map.of()));
            merged.putAll(variantEdits.getOrDefault(def.name(), Map.of()));
            Own o = own(def.block(), Strata.MOD_ID + ":" + def.name(), merged, warn);
            FireBlock fire = (FireBlock) Blocks.FIRE;
            boolean was = READING.get();
            READING.set(true);
            try {
                if (o.flammability < 0) o.flammability = fire.getBurnOdds(def.base().defaultBlockState());
                if (o.fireSpread < 0) o.fireSpread = fire.getIgniteOdds(def.base().defaultBlockState());
            } finally {
                READING.set(was);
            }
            out.put(def.block(), o);
        }
        own = out.isEmpty() ? Map.of() : out;
    }

    /** One block's values from its rows. */
    private static Own own(Block block, String id, Map<String, String> values, boolean warn) {
        Own o = new Own();
        values.forEach((key, value) -> {
            String problem = BlockStats.problem(key, value);
            if (problem != null) {
                if (warn) Strata.LOGGER.warn("Block stats: {} {} = {}: {}; the row is skipped", id, key, value, problem);
                return;
            }
            switch (key) {
                case BlockStats.HARDNESS -> o.hardness = Float.parseFloat(value);
                case BlockStats.BLAST_RESISTANCE -> o.blastResistance = Float.parseFloat(value);
                case BlockStats.FRICTION -> o.friction = Float.parseFloat(value);
                case BlockStats.SPEED_FACTOR -> o.speedFactor = Float.parseFloat(value);
                case BlockStats.JUMP_FACTOR -> o.jumpFactor = Float.parseFloat(value);
                case BlockStats.LIGHT_LEVEL -> o.light = Integer.parseInt(value);
                case BlockStats.REQUIRES_TOOL -> o.requiresTool = Boolean.parseBoolean(value);
                case BlockStats.FLAMMABILITY -> o.flammability = Integer.parseInt(value);
                case BlockStats.FIRE_SPREAD -> o.fireSpread = Integer.parseInt(value);
                case BlockStats.REPLACEABLE -> o.replaceable = Boolean.parseBoolean(value);
                case BlockStats.IGNITED_BY_LAVA -> o.ignitedByLava = Boolean.parseBoolean(value);
                case BlockStats.SOUND -> o.sound = constant(SoundType.class, value);
                case BlockStats.MAP_COLOR -> o.mapColor = constant(net.minecraft.world.level.material.MapColor.class, value);
                case BlockStats.RANDOM_TICKS -> o.randomTicks = Boolean.parseBoolean(value);
                case BlockStats.SOLID -> o.solid = Boolean.parseBoolean(value);
                case BlockStats.COLLISION -> o.collision = Boolean.parseBoolean(value);
                case BlockStats.OCCLUDES -> o.occludes = Boolean.parseBoolean(value);
                case BlockStats.REDSTONE_CONDUCTOR -> o.redstoneConductor = Boolean.parseBoolean(value);
                case BlockStats.TERRAIN_PARTICLES -> o.terrainParticles = Boolean.parseBoolean(value);
                case BlockStats.EMISSIVE -> o.emissive = Boolean.parseBoolean(value);
                case BlockStats.MOBS_SPAWN_ON -> o.mobsSpawnOn = Boolean.parseBoolean(value);
                case BlockStats.SUFFOCATING -> o.suffocating = Boolean.parseBoolean(value);
                case BlockStats.VIEW_BLOCKING -> o.viewBlocking = Boolean.parseBoolean(value);
                case BlockStats.OFFSET -> o.offset = value.toLowerCase(java.util.Locale.ROOT);
                case BlockStats.PUSH_REACTION -> o.pushReaction = net.minecraft.world.level.material.PushReaction
                        .valueOf(value.toUpperCase(java.util.Locale.ROOT));
                case BlockStats.INSTRUMENT -> {
                    for (var i : net.minecraft.world.level.block.state.properties.NoteBlockInstrument.values()) {
                        if (i.getSerializedName().equals(value)) o.instrument = i;
                    }
                }
                // Tool and Tool level are tags: the generated data pack writes them.
                default -> {
                }
            }
        });
        // THE GAME'S RULE ON OFFSETS: it keeps each block's own collision shape, which cannot follow an
        // offset, and refuses to start on a block with both.
        if (o.offset != null && !"none".equals(o.offset) && !gameLetsThrough(block)) {
            if (warn) Strata.LOGGER.warn("Block stats: {} offset {}: the game allows an offset only on a block it lets"
                    + " things through; the row is skipped", id, o.offset);
            o.offset = null;
        }
        if (o.light >= 0) {
            o.lightWhenLit = lightWhenLit(block);
        }
        return o;
    }

    /** Reads every block into the game layer, then resolves the edits again (mod bus). */
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            // The game's sound sets and map colours by name, for the sheet's dropdowns.
            BlockStats.soundNames = constantNames(SoundType.class);
            BlockStats.mapColorNames = constantNames(net.minecraft.world.level.material.MapColor.class);
            List<GameLayer.Row> rows = new ArrayList<>();
            READING.set(true);
            try {
                for (Block block : BuiltInRegistries.BLOCK) {
                    String id = BlockVariants.idOf(block);
                    // A block variant is not a game block: its values are on its own sheet.
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR
                            || BlockVariants.of(block) != null) {
                        continue;
                    }
                    try {
                        rows.addAll(rows(id, block));
                    } catch (RuntimeException | LinkageError e) {
                        Strata.LOGGER.debug("Block stats: could not read {}: {}", id, e.toString());
                    }
                }
            } finally {
                READING.set(false);
            }
            if (GameLayer.write(GameLayer.dir().resolve(GameLayer.BLOCKS), COMMENT, rows)) {
                Strata.LOGGER.info("Game layer: extracted the stats of {} block(s)", BuiltInRegistries.BLOCK.size());
            }
            resolve(true);
            if (!own.isEmpty()) {
                Strata.LOGGER.info("Block stats: your values are on {} block(s)", own.size());
            }
        });
    }

    /** One block's game layer rows. */
    public static List<GameLayer.Row> rows(String id, Block block) {
        List<GameLayer.Row> out = new ArrayList<>();
        BlockState state = block.defaultBlockState();
        out.add(value(id, BlockStats.HARDNESS, decimal(block.defaultDestroyTime())));
        out.add(value(id, BlockStats.BLAST_RESISTANCE, decimal(block.getExplosionResistance())));
        out.add(value(id, BlockStats.REQUIRES_TOOL, String.valueOf(state.requiresCorrectToolForDrops())));
        out.add(value(id, BlockStats.FRICTION, decimal(block.getFriction())));
        out.add(value(id, BlockStats.SPEED_FACTOR, decimal(block.getSpeedFactor())));
        out.add(value(id, BlockStats.JUMP_FACTOR, decimal(block.getJumpFactor())));
        boolean lit = lightWhenLit(block);
        out.add(value(id, BlockStats.LIGHT_LEVEL, String.valueOf(lit ? maxLight(block) : state.getLightEmission())));
        if (lit) {
            out.add(new GameLayer.Row(id, GameLayer.NOTE, BlockStats.LIGHT_LEVEL, "LIT"));
        }
        FireBlock fire = (FireBlock) Blocks.FIRE;
        out.add(value(id, BlockStats.FLAMMABILITY, String.valueOf(fire.getBurnOdds(state))));
        out.add(value(id, BlockStats.FIRE_SPREAD, String.valueOf(fire.getIgniteOdds(state))));
        SoundType sound = state.getSoundType();
        boolean wood = sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.BAMBOO_WOOD;
        boolean plant = block instanceof BushBlock || block instanceof GrowingPlantBlock || block instanceof VineBlock
                || block instanceof LeavesBlock;
        boolean ore = block instanceof DropExperienceBlock || block instanceof RedStoneOreBlock;
        out.add(value(id, BlockStats.REPLACEABLE, String.valueOf(state.canBeReplaced())));
        out.add(value(id, BlockStats.PUSH_REACTION, state.getPistonPushReaction().name().toLowerCase(java.util.Locale.ROOT)));
        out.add(value(id, BlockStats.INSTRUMENT, state.instrument().getSerializedName()));
        out.add(value(id, BlockStats.IGNITED_BY_LAVA, String.valueOf(state.ignitedByLava())));
        // The rest, each read on its own, so one a block cannot give leaves the rest (editable now).
        shown(out, id, BlockStats.SOUND, () -> constantName(SoundType.class, sound));
        shown(out, id, BlockStats.MAP_COLOR, () -> constantName(net.minecraft.world.level.material.MapColor.class,
                state.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)));
        shown(out, id, BlockStats.RANDOM_TICKS, () -> String.valueOf(state.isRandomlyTicking()));
        shown(out, id, BlockStats.SOLID, () -> String.valueOf(state.isSolid()));
        shown(out, id, BlockStats.COLLISION, () -> String.valueOf(!state.getCollisionShape(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()));
        shown(out, id, BlockStats.OCCLUDES, () -> String.valueOf(state.canOcclude()));
        shown(out, id, BlockStats.REDSTONE_CONDUCTOR, () -> String.valueOf(state.isRedstoneConductor(
                net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)));
        net.minecraft.world.level.BlockGetter none = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
        shown(out, id, BlockStats.TERRAIN_PARTICLES, () -> String.valueOf(state.shouldSpawnTerrainParticles()));
        shown(out, id, BlockStats.EMISSIVE, () -> String.valueOf(state.emissiveRendering(none, net.minecraft.core.BlockPos.ZERO)));
        shown(out, id, BlockStats.MOBS_SPAWN_ON, () -> String.valueOf(state.isValidSpawn(none, net.minecraft.core.BlockPos.ZERO,
                net.minecraft.world.entity.EntityType.ZOMBIE)));
        shown(out, id, BlockStats.SUFFOCATING, () -> String.valueOf(state.isSuffocating(none, net.minecraft.core.BlockPos.ZERO)));
        shown(out, id, BlockStats.VIEW_BLOCKING, () -> String.valueOf(state.isViewBlocking(none, net.minecraft.core.BlockPos.ZERO)));
        shown(out, id, BlockStats.OFFSET, () -> !state.hasOffsetFunction() ? "none"
                : java.util.stream.IntStream.range(0, 16).anyMatch(n -> state.getOffset(none,
                        new net.minecraft.core.BlockPos(n, 0, n * 3)).y != 0) ? "xyz" : "xz");
        shown(out, id, BlockStats.STATES, () -> block.getStateDefinition().getProperties().isEmpty() ? "none"
                : String.join("; ", block.getStateDefinition().getProperties().stream()
                        .map(p -> p.getName() + " " + p.getPossibleValues().stream().map(String::valueOf).toList()).toList()));
        shown(out, id, BlockStats.BLOCK_ENTITY, () -> String.valueOf(block instanceof net.minecraft.world.level.block.EntityBlock));
        shown(out, id, BlockStats.ITEM, () -> block.asItem() == net.minecraft.world.item.Items.AIR ? "none"
                : BuiltInRegistries.ITEM.getResourceKey(block.asItem()).map(k -> k.location().toString()).orElse("none"));
        shown(out, id, BlockStats.LOOT_TABLE, () -> block.getLootTable().location().toString());
        out.add(new GameLayer.Row(id, GameLayer.KIND, GameLayer.KIND, BlockStats.kind(block instanceof LiquidBlock,
                ore, plant, wood, state.requiresCorrectToolForDrops())));
        return out;
    }

    private static void shown(List<GameLayer.Row> out, String id, String key, java.util.function.Supplier<String> read) {
        try {
            out.add(value(id, key, read.get()));
        } catch (RuntimeException | LinkageError e) {
            Strata.LOGGER.debug("Block stats: could not read {} of {}: {}", key, id, e.toString());
        }
    }

    /** The name of the public constant of a class that holds this value (SoundType.WOOD is "wood"), or "other". */
    private static String constantName(Class<?> type, Object value) {
        for (java.lang.reflect.Field f : type.getFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.get(null) == value) {
                    return f.getName().toLowerCase(java.util.Locale.ROOT);
                }
            } catch (IllegalAccessException e) {
                // A field we may not read is not the one.
            }
        }
        return "other";
    }

    /** The game's own collision of every state of the block is empty: things pass through it. */
    public static boolean gameLetsThrough(Block block) {
        boolean was = READING.get();
        READING.set(true);
        try {
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                if (!s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        } finally {
            READING.set(was);
        }
    }

    /** Its default state gives no light and another state does (a lamp, a furnace): only lit states take the light. */
    private static boolean lightWhenLit(Block block) {
        boolean was = READING.get();
        READING.set(true);
        try {
            return block.defaultBlockState().getLightEmission() == 0 && maxLight(block) > 0;
        } finally {
            READING.set(was);
        }
    }

    private static int maxLight(Block block) {
        int max = 0;
        for (BlockState s : block.getStateDefinition().getPossibleStates()) {
            max = Math.max(max, s.getLightEmission());
        }
        return max;
    }

    private static GameLayer.Row value(String id, String key, String value) {
        return new GameLayer.Row(id, GameLayer.COMPONENT, key, value);
    }

    /** A float as it reads (0.6, not 0.6000000238418579). */
    private static String decimal(float f) {
        return Parse.number(Double.parseDouble(Float.toString(f)));
    }
}
