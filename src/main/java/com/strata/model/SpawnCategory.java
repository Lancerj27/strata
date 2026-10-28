package com.strata.model;

import java.util.Locale;

/** The spawn categories, mirrored into the model package. */
public enum SpawnCategory {

    MONSTER("monster"),
    CREATURE("creature"),
    AMBIENT("ambient"),
    AXOLOTLS("axolotls"),
    UNDERGROUND_WATER_CREATURE("underground_water_creature"),
    WATER_CREATURE("water_creature"),
    WATER_AMBIENT("water_ambient");

    private final String id;

    SpawnCategory(String id) {
        this.id = id;
    }

    /** The editor's word for the category: the mob list's group, and the Batch menu's. */
    public String word() {
        return switch (this) {
            case MONSTER -> "Monster";
            case CREATURE -> "Creature";
            case AMBIENT -> "Ambient";
            case AXOLOTLS -> "Axolotls";
            case WATER_CREATURE -> "Water creature";
            case WATER_AMBIENT -> "Water ambient";
            case UNDERGROUND_WATER_CREATURE -> "Underground water creature";
        };
    }

    /** The vanilla name, which is what the CSV column holds. */
    public String id() {
        return id;
    }

    /** Blank reads as MONSTER, the category a designer means when they do not say. */
    public static SpawnCategory parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return MONSTER;
        }
        String needle = raw.strip().toLowerCase(Locale.ROOT);
        if (needle.equals("misc")) {
            return CREATURE;
        }
        for (SpawnCategory category : values()) {
            if (category.id.equals(needle) || category.name().toLowerCase(Locale.ROOT).equals(needle)) {
                return category;
            }
        }
        return MONSTER;
    }
}
