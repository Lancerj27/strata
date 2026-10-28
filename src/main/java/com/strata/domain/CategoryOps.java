package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.SpawnCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A MOB'S CATEGORY. */
public final class CategoryOps {

    private CategoryOps() {
    }

    /** The word for the game's misc category: no mob cap, never spawned by the game itself. */
    public static final String MISC_WORD = "Misc";
    public static final String MISC_ID = "misc";

    /** The dropdown's Special: not a game category, the mob's Special flag. */
    public static final String SPECIAL_WORD = "Special";

    /** The dropdown's words: Special, then the mob list's order, then Misc. */
    public static final List<String> CHOICES;

    static {
        List<String> words = new ArrayList<>();
        words.add(SPECIAL_WORD);
        for (SpawnCategory c : SpawnCategory.values()) {
            words.add(c.word());
        }
        words.add(MISC_WORD);
        CHOICES = List.copyOf(words);
    }

    /** What the dropdown does, for its tooltip. */
    public static final String HELP = "The mob's category (the game's word): which mob cap it counts toward"
            + " and which spawn slot of a biome it fills. A variant follows its base mob's unless it sets"
            + " its own; every tier and biome share it. Misc: the game never spawns it by itself. The spawn"
            + " lists follow from the next world load. Special: the mob list files it under Special and it"
            + " gets Spawn as; its game category stays.";

    /** What a category change asks of the player. */
    public static final String RELOAD_NOTE = "Saved. Reload the world for the new category to apply";

    /** A category name (monster, creature,... */
    public static String word(String categoryId) {
        String id = categoryId == null ? "" : categoryId.strip().toLowerCase(Locale.ROOT);
        if (MISC_ID.equals(id)) {
            return MISC_WORD;
        }
        for (SpawnCategory c : SpawnCategory.values()) {
            if (c.id().equals(id)) {
                return c.word();
            }
        }
        return "";
    }

    /** A dropdown word as the game's category name, or null for an unknown word. */
    public static String id(String word) {
        String w = word == null ? "" : word.strip();
        if (MISC_WORD.equalsIgnoreCase(w)) {
            return MISC_ID;
        }
        for (SpawnCategory c : SpawnCategory.values()) {
            if (c.word().equalsIgnoreCase(w)) {
                return c.id();
            }
        }
        return null;
    }

    /** The game's category for a mob, from the game layer, or "" when it was not read. */
    public static String gameCategory(String entity) {
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS))) {
            if (r.entity().equals(entity) && GameLayer.TYPE.equals(r.group()) && "category".equals(r.key())) {
                return r.value().strip().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    /**
     * Every mob whose category the user changed, to its new category and the game's in words: "Creature (the
     * game's: Ambient)".
     */
    public static Map<String, String> moved() {
        Map<String, String> out = new java.util.HashMap<>();
        GameLayer.editedCategories(CsvTable.read(Paths.table(Schema.MobData.FILE))).forEach((entity, now) -> {
            String game = gameCategory(entity);
            if (!game.isEmpty() && !game.equals(now)) {
                out.put(entity, word(now) + " (the game's: " + word(game) + ")");
            }
        });
        return out;
    }

    /** Every mob whose category the user set, to that category ("creature"). */
    public static Map<String, String> editedCategories() {
        return GameLayer.editedCategories(CsvTable.read(Paths.table(Schema.MobData.FILE)));
    }

    /** The mob's category as Strata runs it: its edit, else the game's. */
    public static String categoryOf(String entity) {
        String edited = GameLayer.editedCategories(CsvTable.read(Paths.table(Schema.MobData.FILE))).get(entity);
        return edited != null ? edited : gameCategory(entity);
    }

    /** A variant's category: its own, else its base mob's. */
    public static String categoryOf(String entity, String variant) {
        if (variant == null || variant.isBlank()) {
            return categoryOf(entity);
        }
        String own = editedVariantCategories().get(entity + "|" + variant.strip());
        return own != null ? own : categoryOf(entity);
    }

    /** Every variant's own category, "entity|variant" to the category id. */
    public static Map<String, String> editedVariantCategories() {
        return GameLayer.editedVariantCategories(CsvTable.read(Paths.table(Schema.MobData.FILE)));
    }

    /** SETS A VARIANT'S OWN CATEGORY. */
    public static WriteReport setCategory(String entity, String variant, String word) {
        if (variant == null || variant.isBlank()) {
            return setCategory(entity, word);
        }
        if (SPECIAL_WORD.equalsIgnoreCase(word == null ? "" : word.strip())) {
            return WriteReport.refused("A variant's Special is its Spawn as, on the Spawning tab");
        }
        String problem = problem(entity, word);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String id = id(word);
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobData.ENTITY, entity);
        keys.put(Schema.MobData.VARIANT_NAME, variant.strip());
        keys.put(Schema.MobData.SCOPE, "");
        keys.put(Schema.MobData.SCOPE_ID, "");
        keys.put(Schema.MobData.GROUP, GameLayer.TYPE);
        keys.put(Schema.MobData.KEY, "category");
        WriteReport report = new WriteReport();
        if (id.equals(categoryOf(entity))) {
            report.cascaded("the variant's category", TableWriter.delete(Schema.MobData.FILE, keys));
        } else {
            report.record("the variant's category", TableWriter.upsert(Schema.MobData.FILE, keys,
                    Map.of(Schema.MobData.VALUE, id)));
        }
        if (report.ok()) {
            report.cascaded("the variant's spawn rows", TableWriter.updateColumnWhere(Schema.MobSpawns.FILE,
                    Map.of(Schema.MobSpawns.ENTITY, entity, Schema.MobSpawns.VARIANT_NAME, variant.strip()),
                    Schema.MobSpawns.CATEGORY, com.strata.game.EntityCategory.forVanillaCategory(id).id()));
        }
        return Commit.reloadIfOk(report).needsReload(RELOAD_NOTE);
    }

    /** The word the dropdown shows for a mob: Special when it is flagged, else its category's. */
    public static String shownWord(String entity) {
        return SpecialSpawnsOps.flagged(entity) ? SPECIAL_WORD : word(categoryOf(entity));
    }

    /** Why a mob's category cannot be set to this word, or null. */
    public static String problem(String entity, String word) {
        if (entity == null || entity.isBlank()) {
            return "Pick a mob first";
        }
        if (SPECIAL_WORD.equalsIgnoreCase(word == null ? "" : word.strip())) {
            return null;
        }
        return id(word) == null ? "Pick a category: " + String.join(", ", CHOICES) : null;
    }

    /**
     * Sets a mob's category, with no reload: the Mob data type row (deleted when it is the game's), then the
     * category column of the mob's spawn rows, so its Strata rows land in the new category's list.
     */
    public static WriteReport setCategory(String entity, String word) {
        String problem = problem(entity, word);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        // SPECIAL IS THE FLAG, not a category: the game category stays.
        if (SPECIAL_WORD.equalsIgnoreCase(word.strip())) {
            return SpecialSpawnsOps.flagged(entity) ? new WriteReport() : SpecialSpawnsOps.setFlagged(entity, true);
        }
        WriteReport report = new WriteReport();
        if (SpecialSpawnsOps.flagged(entity)) {
            report = SpecialSpawnsOps.setFlagged(entity, false);
            if (!report.ok()) {
                return report;
            }
        }
        String id = id(word);
        if (id.equals(categoryOf(entity))) {
            return report;
        }
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobData.ENTITY, entity);
        keys.put(Schema.MobData.VARIANT_NAME, "");
        keys.put(Schema.MobData.SCOPE, "");
        keys.put(Schema.MobData.SCOPE_ID, "");
        keys.put(Schema.MobData.GROUP, GameLayer.TYPE);
        keys.put(Schema.MobData.KEY, "category");
        if (id.equals(gameCategory(entity))) {
            report.record("the category", TableWriter.delete(Schema.MobData.FILE, keys));
        } else {
            report.record("the category", TableWriter.upsert(Schema.MobData.FILE, keys,
                    Map.of(Schema.MobData.VALUE, id)));
        }
        if (report.ok()) {
            // A misc mob's rows stay in the creature list, as EntityCategory files misc.
            String to = com.strata.game.EntityCategory.forVanillaCategory(id).id();
            Map<String, String> own = editedVariantCategories();
            java.util.Set<String> variants = new java.util.LinkedHashSet<>();
            for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobSpawns.FILE))) {
                if (entity.equals(row.getOrDefault(Schema.MobSpawns.ENTITY, "").strip())) {
                    variants.add(row.getOrDefault(Schema.MobSpawns.VARIANT_NAME, "").strip());
                }
            }
            for (String v : variants) {
                if (!v.isEmpty() && own.containsKey(entity + "|" + v)) {
                    continue;
                }
                report.cascaded("the mob's spawn rows", TableWriter.updateColumnWhere(Schema.MobSpawns.FILE,
                        Map.of(Schema.MobSpawns.ENTITY, entity, Schema.MobSpawns.VARIANT_NAME, v),
                        Schema.MobSpawns.CATEGORY, to));
            }
        }
        return report;
    }

    /** One category for every mob of a Batch group, then one reload. */
    public static WriteReport batch(List<MobOps.BatchMob> mobs, String word) {
        String problem = mobs.isEmpty() ? "There are no mobs of that group in this list" : problem("-", word);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        Set<String> entities = new LinkedHashSet<>();
        mobs.forEach(m -> entities.add(m.entity()));
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String entity : entities) {
                WriteReport one = setCategory(entity, word);
                if (!one.ok()) {
                    return one;
                }
                report.record("the category of " + entity, true);
            }
            return Commit.reloadIfOk(report);
        });
    }
}
