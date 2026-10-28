package com.strata.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

import java.util.List;

/** A level that exists only to make each mob once at game start. */
public final class ExtractionLevel extends Level {

    private final Scoreboard scoreboard = new Scoreboard();
    private final TickRateManager tickRate = new TickRateManager();

    public ExtractionLevel(RegistryAccess registries) {
        super(new Data(), Level.OVERWORLD, registries,
                registries.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
                () -> InactiveProfiler.INSTANCE, false, false, 0L, 0);
    }

    /** The level's data: the start of a normal game on Normal difficulty. */
    private static final class Data implements WritableLevelData {
        private final GameRules rules = new GameRules();
        private BlockPos spawn = BlockPos.ZERO;
        private float angle;
        private boolean raining;

        @Override public void setSpawn(BlockPos pos, float angle) { this.spawn = pos; this.angle = angle; }
        @Override public BlockPos getSpawnPos() { return spawn; }
        @Override public float getSpawnAngle() { return angle; }
        @Override public long getGameTime() { return 0L; }
        @Override public long getDayTime() { return 0L; }
        @Override public boolean isThundering() { return false; }
        @Override public boolean isRaining() { return raining; }
        @Override public void setRaining(boolean raining) { this.raining = raining; }
        @Override public boolean isHardcore() { return false; }
        @Override public GameRules getGameRules() { return rules; }
        @Override public Difficulty getDifficulty() { return Difficulty.NORMAL; }
        @Override public boolean isDifficultyLocked() { return false; }
    }

    @Override public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { }
    @Override public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
                                          SoundSource source, float volume, float pitch, long seed) { }
    @Override public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
                                          SoundSource source, float volume, float pitch, long seed) { }
    @Override public String gatherChunkSourceStats() { return ""; }
    @Override public Entity getEntity(int id) { return null; }
    @Override public TickRateManager tickRateManager() { return tickRate; }
    @Override public MapItemSavedData getMapData(MapId mapId) { return null; }
    @Override public void setMapData(MapId mapId, MapItemSavedData mapData) { }
    @Override public MapId getFreeMapId() { return new MapId(0); }
    @Override public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) { }
    @Override public Scoreboard getScoreboard() { return scoreboard; }
    @Override public RecipeManager getRecipeManager() { return new RecipeManager(registryAccess()); }
    @Override public PotionBrewing potionBrewing() { return PotionBrewing.EMPTY; }
    @Override public void setDayTimeFraction(float fraction) { }
    @Override public float getDayTimeFraction() { return 0f; }
    @Override public float getDayTimePerTick() { return 1f; }
    @Override public void setDayTimePerTick(float perTick) { }
    @Override protected LevelEntityGetter<Entity> getEntities() { return null; }
    @Override public ChunkSource getChunkSource() { return null; }
    @Override public LevelTickAccess<Block> getBlockTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public void levelEvent(Player player, int type, BlockPos pos, int data) { }
    @Override public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) { }
    @Override public float getShade(Direction direction, boolean shade) { return 1f; }
    @Override public List<? extends Player> players() { return List.of(); }
    @Override public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
    }
    @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }
}
