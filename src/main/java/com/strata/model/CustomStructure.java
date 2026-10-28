package com.strata.model;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ONE OF THE DESIGNER'S STRUCTURES: a structure file saved by a structure block, and where and how often the world builds it.
 */
public final class CustomStructure {

    private CustomStructure() {
    }

    // The columns of custom_structures.csv (Schema#CustomStructures names them for the tables).
    public static final String NAME = "name";
    public static final String BIOMES = "biomes";
    public static final String SPACING = "spacing";
    public static final String SEPARATION = "separation";
    public static final String STEP = "step";
    public static final String TERRAIN = "terrain_adaptation";
    public static final String HEIGHT = "height";
    public static final String ENABLED = "enabled";

    /** The largest structure file read, in bytes. */
    public static final int MAX_FILE = 1 << 20;
    /** The largest side of a structure, in blocks: the structure block's own limit. */
    public static final int MAX_SIDE = 48;
    /** The most blocks a structure file may list. */
    public static final int MAX_BLOCKS = MAX_SIDE * MAX_SIDE * MAX_SIDE;

    /** The generation steps it can be built in (the game's GenerationStep.Decoration), early to late. */
    public static final List<String> STEPS = List.of("raw_generation", "lakes", "local_modifications",
            "underground_structures", "surface_structures", "strongholds", "underground_ores",
            "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification");
    /** How the land around it is shaped (the game's TerrainAdjustment). */
    public static final List<String> TERRAIN_ADAPTATIONS = List.of("none", "beard_thin", "beard_box", "bury",
            "encapsulate");

    /** The defaults of a new structure: every overworld biome, a village's spacing, on the surface. */
    public static final Map<String, String> DEFAULTS = Map.of(BIOMES, "#minecraft:is_overworld", SPACING, "34",
            SEPARATION, "8", STEP, "surface_structures", TERRAIN, "beard_thin", HEIGHT, "surface");

    private static final Pattern NAME_RULE = Pattern.compile("[a-z0-9_]{1,40}");
    private static final Pattern ID = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Why a name cannot be used, or null. */
    public static String nameProblem(String name) {
        return name != null && NAME_RULE.matcher(name.strip()).matches() ? null
                : "A name is 1 to 40 of a to z, 0 to 9, and _ (for example ruined_tower)";
    }

    /** Why a structure's settings cannot be saved, or null. */
    public static String problem(Map<String, String> row) {
        String name = nameProblem(cell(row, NAME));
        if (name != null) {
            return name;
        }
        String biomes = cell(row, BIOMES);
        if (biomes.isEmpty()) {
            return "Biomes: a biome tag (as #minecraft:is_overworld) or biome ids, separated by commas";
        }
        for (String b : biomes.split(",")) {
            if (!ID.matcher(b.strip()).matches()) {
                return "Biomes: \"" + b.strip() + "\" is not a biome id or a #tag";
            }
        }
        if (biomes.contains("#") && biomes.contains(",")) {
            return "Biomes: one #tag, or a list of biome ids, not both";
        }
        Integer spacing = whole(cell(row, SPACING));
        Integer separation = whole(cell(row, SEPARATION));
        if (spacing == null || spacing < 1 || spacing > 4096) {
            return "Spacing: a whole number of chunks, 1 to 4096";
        }
        if (separation == null || separation < 0 || separation >= spacing) {
            return "Separation: a whole number of chunks, 0 or more and less than the spacing";
        }
        if (!STEPS.contains(cell(row, STEP))) {
            return "Generation step: pick one";
        }
        if (!TERRAIN_ADAPTATIONS.contains(cell(row, TERRAIN))) {
            return "Land around it: pick one";
        }
        String height = cell(row, HEIGHT);
        Integer y = whole(height);
        if (!"surface".equals(height) && (y == null || y < -2032 || y > 2016)) {
            return "Height: surface, or a Y level from -2032 to 2016";
        }
        return null;
    }

    /**
     * Why a structure file's shape cannot be built, or null: its three sides and its block count, as read
     * from the file.
     */
    public static String shapeProblem(int x, int y, int z, int blocks) {
        if (x < 1 || y < 1 || z < 1 || x > MAX_SIDE || y > MAX_SIDE || z > MAX_SIDE) {
            return "The structure is " + x + " x " + y + " x " + z + ": each side 1 to " + MAX_SIDE
                    + " blocks (the structure block's own limit)";
        }
        return blocks < 0 || blocks > MAX_BLOCKS ? "The structure lists " + blocks + " blocks, over " + MAX_BLOCKS : null;
    }

    private static String cell(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.strip();
    }

    private static Integer whole(String v) {
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
