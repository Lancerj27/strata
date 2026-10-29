package com.strata.data;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Where Strata keeps its files. */
public final class Paths {

    private Paths() {}

    /** config/ (the NeoForge common config dir, where strata-common.toml lives). */
    public static final Path CONFIGDIR = FMLPaths.CONFIGDIR.get();

    /** config/strata/ */
    public static final Path ROOT = CONFIGDIR.resolve("strata");


    /** config/strata/profiles/ — exported profile bundles. */
    public static final Path PROFILES = ROOT.resolve("profiles");

    /** config/strata/datapack/ — generated output, never hand-edited. */
    public static final Path DATAPACK = ROOT.resolve("datapack");


    public static Path table(String fileName) {
        return ROOT.resolve(fileName);
    }

    public static Path profile(String profileName) {
        return PROFILES.resolve(profileName);
    }
}
