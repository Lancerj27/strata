package com.strata.game;

import com.strata.Strata;
import com.strata.data.Config;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.level.WorldDataConfiguration;

import java.util.List;

/**
 * Builds a worldgen RegistryAccess from the available packs at startup, without requiring a loaded world.
 */
public final class WorldgenRegistryBuilder {

    private WorldgenRegistryBuilder() {}

    /**
     * Builds a frozen RegistryAccess containing every WORLDGEN registry (biomes, structures, placed features,
     * configured features, noise settings, etc.) from the packs in the given repository.
     *
     * @return a frozen RegistryAccess with the worldgen layer populated, or null if the build fails (logged,
     *     never thrown)
     */
    public static RegistryAccess.Frozen build(PackRepository packRepository) {
        try {
            MinecraftServer.configurePackRepository(
                    packRepository, WorldDataConfiguration.DEFAULT, true, false);

            List<Pack> selected = List.copyOf(packRepository.getSelectedPacks());
            Strata.LOGGER.info("Building worldgen registries from selected packs: {}",
                    selected.stream().map(Pack::getId).toList());
            if (selected.isEmpty()) {
                Strata.LOGGER.warn("No data packs selected — worldgen registry build skipped");
                return null;
            }

            try (CloseableResourceManager resourceManager = new MultiPackResourceManager(
                    PackType.SERVER_DATA,
                    selected.stream().map(Pack::open).toList())) {

                LayeredRegistryAccess<RegistryLayer> layered =
                        RegistryLayer.createRegistryAccess();
                RegistryAccess.Frozen staticAccess =
                        layered.getAccessForLoading(RegistryLayer.WORLDGEN);

                // THE LIST THE SERVER LOADS: the game's worldgen registries AND NeoForge's and the mods' own
                // data registries (biome modifiers among them), so a mod's spawns and ores are in the game
                // layer at game start.
                RegistryAccess.Frozen worldgen = RegistryDataLoader.load(
                        resourceManager,
                        staticAccess,
                        net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistries());

                Strata.LOGGER.info("Worldgen registry layer built: {} registries",
                        worldgen.registries().count());

                RegistryAccess.Frozen built = layered.replaceFrom(RegistryLayer.WORLDGEN, worldgen)
                        .compositeAccess();
                // THE GAME LAYER IS FILLED HERE, at game start, while every pack is open.
                try (CloseableResourceManager gameOnly = new MultiPackResourceManager(
                        PackType.SERVER_DATA,
                        selected.stream().filter(p -> !com.strata.data.StrataDatapack.NAMESPACE.equals(p.getId()))
                                .map(Pack::open).toList())) {
                    com.strata.runtime.MobExtractor.extractAll(built, gameOnly);
                }
                return built;
            }
        } catch (Exception e) {
            Strata.LOGGER.warn("Could not build worldgen registries at startup: {}",
                    e.toString(), e);
            return null;
        }
    }

    /** Builds a PackRepository containing the vanilla server data packs. */
    public static PackRepository createServerPackRepository() {
        return ServerPacksSource.createVanillaTrustedRepository();
    }

    /**
     * Convenience for the two startup call sites (client setup and the dedicated-server common-setup
     * pre-generation): create the repository, build the worldgen registries, and bind them to {@link Config}.
     */
    public static boolean createAndBind() {
        try {
            PackRepository repo = createServerPackRepository();
            RegistryAccess.Frozen registries = build(repo);
            if (registries == null) {
                return false;
            }
            Config.bind(registries, null);
            return true;
        } catch (Throwable t) {
            Strata.LOGGER.warn("Could not bind startup worldgen registries, "
                    + "snapshot fallback stays in place: {}", t.toString(), t);
            return false;
        }
    }

    /**
     * The client-setup preload, so the editor works from the title screen: write any missing default tables,
     * bind the startup registries, seed a fresh install, and load the config.
     */
    public static void preloadEditorData() {
        try {
            com.strata.data.Defaults.writeIfMissing();
            if (createAndBind()) {
                Strata.LOGGER.info("Built worldgen registries at game start — "
                        + "biomes and ores available from the title screen");
            }
            Config.reload();
        } catch (Exception ex) {
            Strata.LOGGER.error("Could not pre-load tables for main menu: {}", ex.toString(), ex);
        }
    }
}
