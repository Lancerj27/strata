package com.strata.data;

import com.strata.Strata;
import com.strata.domain.CustomAssetsOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Registers the custom textures and sounds as a CLIENT RESOURCE PACK, as {@link StrataDatapack} registers the
 * generated data pack: config/strata/assets_pack/, namespace {@value CustomAssetsOps#NAMESPACE}.
 */
public final class CustomAssetsPack {

    private CustomAssetsPack() {
    }

    /** WHICH LIBRARY THE PACK SERVES. */
    private static volatile Path serverRoot;

    public static Path activeRoot() {
        Path server = serverRoot;
        return server != null ? server : Paths.ROOT;
    }

    /** Serve a server's cache, or (null) this machine's library. */
    public static boolean use(Path root) {
        Path before = activeRoot();
        serverRoot = root;
        return !before.equals(activeRoot());
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path dir = CustomAssetsOps.packDir(Paths.ROOT);
        try {
            CustomAssetsOps.ensurePack(Paths.ROOT);
        } catch (IOException e) {
            Strata.LOGGER.error("Could not make the custom assets pack in {}: {}", dir, e.toString());
            return;
        }
        PackLocationInfo location = new PackLocationInfo(
                CustomAssetsOps.NAMESPACE,
                Component.literal("Strata - your custom textures and sounds"),
                PackSource.BUILT_IN,
                Optional.empty());
        Pack pack = Pack.readMetaAndCreate(
                location,
                BuiltInPackSource.fromName(info -> new PathPackResources(info, CustomAssetsOps.packDir(activeRoot()))),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (pack == null) {
            Strata.LOGGER.error("Could not create the custom assets pack from {}", dir);
            return;
        }
        event.addRepositorySource(packConsumer -> packConsumer.accept(pack));
    }
}
