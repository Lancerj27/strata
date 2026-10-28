package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** THE EDIT FORMS, LAID OUT: each a {@link FormLayout} with its own rows. */
public final class FormScreenLayouts {

    private FormScreenLayouts() {
    }

    /** A button's text, cut to its width. */
    private static String fit(String text, int width, TextFit.Measure m) {
        return TextFit.clip(text, width - Layout.LABEL_MARGIN, m);
    }

    /** Names laid out as lines of "a, b, c", each within room: at most maxLines, the last saying "... */
    public static List<String> biomeLines(List<String> names, int room, int maxLines, TextFit.Measure m) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (int i = 0; i < names.size(); i++) {
            String piece = names.get(i) + (i < names.size() - 1 ? "," : "");
            String next = line.isEmpty() ? piece : line + " " + piece;
            if (!line.isEmpty() && m.width(next) > room) {
                lines.add(line);
                if (lines.size() == maxLines - 1) {
                    lines.add("... and " + (names.size() - i) + " more");
                    return lines;
                }
                line = piece;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /** The most lines the tier's biome list takes before it says how many more. */
    public static final int TIER_BIOME_LINES = 40;

    /**
     * TierEditScreen: name, level, spawn mode, coefficient; for a tier that exists, its biomes under the
     * fields, then Mobs, Ores, and + Add biomes.
     *
     * @param biomes the tier's biomes, in words, in order
     */
    /**
     * @param otherSpawns the Block other spawns choices: the global option, on, or off
     */
    public static Layout tierEdit(Frame frame, TextFit.Measure m, String title, boolean exists, List<String> modes,
                                  List<String> otherSpawns, List<String> biomes, String footer1, String footer2) {
        List<String> notes = new ArrayList<>();
        if (exists) {
            notes.add(biomes.isEmpty() ? "Biomes in this tier: none yet (+ Add biomes adds some)"
                    : "Biomes in this tier (" + biomes.size() + "):");
            biomeLines(biomes, frame.contentWidth() - 8, TIER_BIOME_LINES, m).forEach(l -> notes.add("  " + l));
        }
        // Duplicate moved to the tier list, acting on the selected tier.
        List<ListLayout.Action> nav = exists
                ? List.of(
                        ListLayout.Action.of("mobs", "Mobs"),
                        // This tier's Ores, Vegetation and Block replacements.
                        ListLayout.Action.of("blocks", "Blocks"),
                        ListLayout.Action.of("biomes", "+ Add biomes"),
                        // The spawn rules and the Special mob spawns switch.
                        ListLayout.Action.of("rules", "Rules"))
                : List.of();
        return FormLayout.build(frame, m, new FormLayout.Input(title, exists, true, List.of(
                FormLayout.Row.labelled("Tier name", FormLayout.Cell.field("name", fw(frame), null)),
                FormLayout.Row.labelled("Level", FormLayout.Cell.field("level", fw(frame), null)),
                FormLayout.Row.labelled("Spawn mode (mobs)",
                        FormLayout.Cell.choice("mode", 140, null, "Spawn mode", modes)),
                // Replace blocks other spawns moved to Rules.
                FormLayout.Row.labelled("Coefficient", FormLayout.Cell.field("coefficient", fw(frame), null))),
                notes, nav, footer1, footer2));
    }

    /**
     * ItemVariantScreen: the name (new only), the base item (a picker while new), the display name, the lore,
     * rarity, glint, and unbreakable, the custom model data; then Stats, Effects, and Give me one for a saved
     * variant.
     *
     * @param baseText the base item's button text (its name, or "Pick...")
     */
    public static Layout itemVariantEdit(Frame frame, TextFit.Measure m, String title, boolean exists, String baseText,
                                         List<String> rarities, List<String> glints, List<String> yesNo, String footer) {
        List<ListLayout.Action> nav = exists
                ? List.of(ListLayout.Action.of("stats", "Stats"),
                        ListLayout.Action.of("effects", "Effects"),
                        ListLayout.Action.of("give", "Give me one"))
                : List.of();
        return FormLayout.build(frame, m, new FormLayout.Input(title, exists, true, List.of(
                FormLayout.Row.labelled("Name (a to z, 0 to 9, _; fixed once saved)",
                        FormLayout.Cell.field("name", fw(frame), null)),
                FormLayout.Row.labelled("Base item (fixed once saved)",
                        FormLayout.Cell.button("base", fw(frame), null, fit(baseText, fw(frame), m))),
                FormLayout.Row.labelled("Display name (blank = the base item's)",
                        FormLayout.Cell.field("displayName", fw(frame), null)),
                FormLayout.Row.labelled("Lore (\" | \" between lines)", FormLayout.Cell.field("lore", itemW(frame), null)),
                FormLayout.Row.of(FormLayout.Cell.choice("rarity", 140, null, "Rarity", rarities),
                        FormLayout.Cell.choice("glint", 120, null, "Glint", glints),
                        FormLayout.Cell.choice("unbreakable", 130, null, "Unbreakable", yesNo)),
                FormLayout.Row.labelled("Custom model data (blank = the base item's look)",
                        FormLayout.Cell.field("model", fw(frame), null))),
                List.of(), nav, footer, null));
    }

    /** The projectile form's Keep base choices: the base's own impact too, or only yours. */
    public static final List<String> KEEP_BASE = List.of("Yes", "No");

    /**
     * ProjectileEditScreen: the name (fixed once saved), the projectile it is fired as, how it flies and
     * hits; Abilities for a saved one.
     *
     * @param baseText the base projectile's button text (its name, or "Pick...")
     */
    public static Layout projectileEdit(Frame frame, TextFit.Measure m, String title, boolean exists, String baseText,
                                        String footer) {
        List<ListLayout.Action> nav = exists ? List.of(ListLayout.Action.of("effects", "Abilities")) : List.of();
        return FormLayout.build(frame, m, new FormLayout.Input(title, exists, true, List.of(
                FormLayout.Row.labelled("Name (a to z, 0 to 9, _; fixed once saved)",
                        FormLayout.Cell.field("name", fw(frame), null)),
                FormLayout.Row.labelled("Fired as (the game's or a mod's projectile)",
                        FormLayout.Cell.button("base", fw(frame), null, fit(baseText, fw(frame), m))),
                FormLayout.Row.labelled("Speed (blank = the goal's)", FormLayout.Cell.field("speed", fw(frame), null)),
                FormLayout.Row.labelled("Gravity (0 flies straight, 1 = its own fall)", FormLayout.Cell.field("gravity", fw(frame), null)),
                FormLayout.Row.labelled("Damage (blank = its own)", FormLayout.Cell.field("damage", fw(frame), null)),
                FormLayout.Row.labelled("Explosion power where it lands (0 = none)", FormLayout.Cell.field("explosion", fw(frame), null)),
                FormLayout.Row.labelled("Sets what it hits on fire (seconds)", FormLayout.Cell.field("fire", fw(frame), null)),
                FormLayout.Row.labelled("Looks like item (for a snowball, fireball or other drawn as an item; blank = its own)",
                        FormLayout.Cell.field("look", fw(frame), null)),
                FormLayout.Row.labelled("Arrow texture (for a shot drawn as an arrow; a png id, blank = its own)",
                        FormLayout.Cell.field("texture", fw(frame), null)),
                FormLayout.Row.of(FormLayout.Cell.choice("keepBase", fw(frame), null, "Keep its own impact", KEEP_BASE))),
                List.of(), nav, footer, null));
    }

    /**
     * WorldSettingsScreen: the world's border, difficulty, time, day length, weather and spawn angle, one
     * labelled full-width row each; hardcore and the datapacks are shown locked, their reason in the label.
     */
    public static Layout worldSettings(Frame frame, TextFit.Measure m, String title, boolean saveActive,
                                       String footer) {
        int w = fw(frame);
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, saveActive, List.of(
                FormLayout.Row.labelled("World border: centre X (blocks)", FormLayout.Cell.field("centerX", w, null)),
                FormLayout.Row.labelled("World border: centre Z (blocks)", FormLayout.Cell.field("centerZ", w, null)),
                FormLayout.Row.labelled("World border: size (blocks across)", FormLayout.Cell.field("size", w, null)),
                FormLayout.Row.labelled("World border: damage per block outside the safe zone",
                        FormLayout.Cell.field("damage", w, null)),
                FormLayout.Row.labelled("World border: safe zone (blocks beyond it before damage)",
                        FormLayout.Cell.field("safeZone", w, null)),
                FormLayout.Row.labelled("World border: warning distance (blocks)",
                        FormLayout.Cell.field("warnBlocks", w, null)),
                FormLayout.Row.labelled("World border: warning time (seconds)",
                        FormLayout.Cell.field("warnSeconds", w, null)),
                FormLayout.Row.of(FormLayout.Cell.choice("difficulty", w, null, "Difficulty", com.strata.domain.WorldSettingsOps.DIFFICULTIES)),
                FormLayout.Row.of(FormLayout.Cell.choice("locked", w, null, "Difficulty locked",
                        com.strata.domain.WorldSettingsOps.YES_NO)),
                FormLayout.Row.labelled("Hardcore (" + com.strata.domain.WorldSettingsOps.HARDCORE_REASON + ")",
                        FormLayout.Cell.field("hardcore", w, null)),
                FormLayout.Row.labelled("Time of day (ticks, 0 to 23999; 0 sunrise, 6000 noon, 18000 midnight)",
                        FormLayout.Cell.field("time", w, null)),
                FormLayout.Row.labelled("Day length (minutes; 20 is the game's own)",
                        FormLayout.Cell.field("dayMinutes", w, null)),
                FormLayout.Row.of(FormLayout.Cell.choice("weather", w, null, "Weather",
                        com.strata.domain.WorldSettingsOps.WEATHERS)),
                FormLayout.Row.labelled("Weather lasts (ticks; 20 a second)", FormLayout.Cell.field("weatherTicks", w, null)),
                FormLayout.Row.labelled("Spawn angle (degrees a player faces on spawning)",
                        FormLayout.Cell.field("spawnAngle", w, null)),
                FormLayout.Row.labelled("Enabled datapacks (" + com.strata.domain.WorldSettingsOps.DATAPACKS_REASON + ")",
                        FormLayout.Cell.field("datapacks", w, null))),
                List.of(), List.of(), footer, null));
    }

    /**
     * CustomStructureScreen: its name (fixed once saved), its file, and where and how often the world builds
     * it; a line on what the file holds.
     *
     * @param fileNote what the typed file holds, or why it cannot be used
     */
    public static Layout customStructureEdit(Frame frame, TextFit.Measure m, String title, boolean exists,
                                             String fileNote, String footer) {
        int w = fw(frame);
        int button = CustomAssetLayout.BROWSE_W;
        int short_ = Math.max(120, w - button - FormLayout.CELL_GAP);
        return FormLayout.build(frame, m, new FormLayout.Input(title, exists, true, List.of(
                FormLayout.Row.labelled("Name (a to z, 0 to 9, _; fixed once saved)", FormLayout.Cell.field("name", w, null)),
                FormLayout.Row.labelled(exists ? "Structure file (a .nbt a structure block saved; blank keeps its file)"
                        : "Structure file (the path of a .nbt a structure block saved)", FormLayout.Cell.field("file", short_, null),
                        FormLayout.Cell.button("browse", button, null, "Browse...")),
                FormLayout.Row.labelled("Biomes (a #tag, as #minecraft:is_overworld, or biome ids with commas)",
                        FormLayout.Cell.field("biomes", short_, null),
                        FormLayout.Cell.button("addBiome", button, null, "+ Add biome")),
                FormLayout.Row.labelled("Spacing (chunks between tries)", FormLayout.Cell.field("spacing", w, null)),
                FormLayout.Row.labelled("Separation (chunks it keeps clear, less than the spacing)",
                        FormLayout.Cell.field("separation", w, null)),
                FormLayout.Row.of(FormLayout.Cell.choice("step", w, null, "Step", com.strata.model.CustomStructure.STEPS)),
                FormLayout.Row.of(FormLayout.Cell.choice("terrain", w, null, "Land around it",
                        com.strata.model.CustomStructure.TERRAIN_ADAPTATIONS)),
                FormLayout.Row.labelled("Height (surface, or a Y level)", FormLayout.Cell.field("height", w, null))),
                List.of(fileNote), List.of(), footer, null));
    }

    /**
     * BlockVariantScreen: the name and the base block (fixed once saved), the display name; Stats and Give me
     * one for a saved one.
     */
    public static Layout blockVariantEdit(Frame frame, TextFit.Measure m, String title, boolean exists, String baseText,
                                          String footer) {
        List<ListLayout.Action> nav = exists
                ? List.of(ListLayout.Action.of("stats", "Stats"),
                        ListLayout.Action.of("effects", "Abilities"),
                        ListLayout.Action.of("give", "Give me one"))
                : List.of();
        return FormLayout.build(frame, m, new FormLayout.Input(title, exists, true, List.of(
                FormLayout.Row.labelled("Name (a to z, 0 to 9, _; fixed once saved)",
                        FormLayout.Cell.field("name", fw(frame), null)),
                FormLayout.Row.labelled("Base block (fixed once saved)",
                        FormLayout.Cell.button("base", fw(frame), null, fit(baseText, fw(frame), m))),
                FormLayout.Row.labelled("Display name (blank = the name in words)",
                        FormLayout.Cell.field("displayName", fw(frame), null))),
                List.of(), nav, footer, null));
    }

    /** The tier and biome editors' Special spawns switch: its widest text and its width. */
    public static final String SPECIAL_SWITCH_TEXT = "Special spawns here: Off";
    public static final int SPECIAL_SWITCH_W = 140;

    /** The width of the biome form's switches. */
    public static final int SWITCH_W = 190;

    /**
     * BiomeEditScreen: the tier, the level override, the three switches, the coefficient override; a note
     * when the biome's own rules diverge; Mobs, Blocks, Structures, Biome data, Climate &
     * looks, Rules.
     *
     * @param levelLabel the level row's label (it names the inherited level)
     * @param note the divergence note, or ""
     */
    public static Layout biomeEdit(Frame frame, TextFit.Measure m, String title, String tierText, String levelLabel,
                                   List<String> switches, String note) {
        int nav = 80;
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, List.of(
                FormLayout.Row.of(FormLayout.Cell.button("tier", fw(frame), null, fit(tierText, fw(frame), m))),
                FormLayout.Row.labelled(levelLabel, FormLayout.Cell.field("level", fw(frame), null)),
                FormLayout.Row.of(FormLayout.Cell.choice("scaleMobs", SWITCH_W, null, "Scale mobs", switches),
                        FormLayout.Cell.choice("restrictOres", SWITCH_W, null, "Restrict ores", switches)),
                // Block other spawns moved to Rules.
                FormLayout.Row.of(FormLayout.Cell.choice("substitute", SWITCH_W, null, "Substitute spawns", switches)),
                FormLayout.Row.labelled("Coefficient override (blank = inherit)",
                        FormLayout.Cell.field("coefficient", fw(frame), null))),
                List.of(note),
                // Blocks holds this biome's Ores, Vegetation and Block replacements.
                List.of(ListLayout.Action.of("mobs", "Mobs", nav),
                        ListLayout.Action.of("blocks", "Blocks", nav),
                        ListLayout.Action.of("structures", "Structures", nav),
                        ListLayout.Action.of("biomeData", "Biome data", nav),
                        // The biome JSON (climate, looks, features), out of the old Data menu (now the Biomes
                        // screen's Worldgen...
                        ListLayout.Action.of("climate", "Climate & looks", nav),
                        // The spawn rules and the Special mob spawns switch.
                        ListLayout.Action.of("rules", "Rules", nav)),
                null, null));
    }

    /** The Batch screen's third section, by what it changes. */
    public enum BatchSection { NONE, LOOT, SPAWN_RULE, ABILITY, EQUIPMENT, ATTRIBUTE, COEFFICIENT, START_LEVEL, BOSS }

    /**
     * The Batch screen: which mobs; what to change and the action; then the change's own controls.
     *
     * @param adds the action adds or sets, so the section takes numbers
     * @param valueWords the condition value's words, or null for a typed value
     * @param notes how many mobs it takes, and where
     */
    public static Layout batch(Frame frame, TextFit.Measure m, String title, List<String> groups,
                               List<String> fields, List<String> actions, BatchSection section, boolean adds,
                               String itemText, List<String> slots, List<String> ruleTypes,
                               List<String> valueWords, List<String> abilities, String parameterText,
                               List<String> notes, boolean applyActive) {
        return batch(frame, m, title, groups, fields, actions, section, adds, itemText, slots, ruleTypes,
                valueWords, abilities, parameterText, List.of(), "", notes, applyActive);
    }

    /**
     * As above, with the effect class row under the ability: the entity, the trigger, the duration and the
     * chance a class row carries.
     *
     * @param triggers the Trigger dropdown's ids
     * @param entityText the Entity picker's text
     */
    public static Layout batch(Frame frame, TextFit.Measure m, String title, List<String> groups,
                               List<String> fields, List<String> actions, BatchSection section, boolean adds,
                               String itemText, List<String> slots, List<String> ruleTypes,
                               List<String> valueWords, List<String> abilities, String parameterText,
                               List<String> triggers, String entityText,
                               List<String> notes, boolean applyActive) {
        return batch(frame, m, title, groups, fields, actions, section, adds, itemText, slots, ruleTypes,
                valueWords, null, abilities, parameterText, triggers, entityText, notes, applyActive);
    }

    /**
     * As above, with the condition value picked from a registry: {@code valuePickText} is the picker button's
     * text, or null for a typed value.
     */
    public static Layout batch(Frame frame, TextFit.Measure m, String title, List<String> groups,
                               List<String> fields, List<String> actions, BatchSection section, boolean adds,
                               String itemText, List<String> slots, List<String> ruleTypes,
                               List<String> valueWords, String valuePickText, List<String> abilities,
                               String parameterText, List<String> triggers, String entityText,
                               List<String> notes, boolean applyActive) {
        List<FormLayout.Row> rows = new ArrayList<>();
        rows.add(FormLayout.Row.labelled("Which mobs",
                FormLayout.Cell.choice("group", 230, null, "Mobs", groups)));
        rows.add(FormLayout.Row.labelled("What to change",
                FormLayout.Cell.choice("field", 190, null, "Change", fields),
                FormLayout.Cell.choice("action", 140, null, "Action", actions)));
        int itemW = itemW(frame);
        switch (section) {
            case LOOT -> rows.add(adds
                    // A TITLE OVER EACH FIELD: one row label over four controls named only the first.
                    ? FormLayout.Row.of(
                    FormLayout.Cell.button("item", itemW, "The loot entry: item", fit(itemText, itemW, m)),
                    FormLayout.Cell.field("min", 60, "Count min"), FormLayout.Cell.field("max", 60, "Count max"),
                    FormLayout.Cell.field("chance", 70, "Chance %"))
                    : FormLayout.Row.labelled("The loot entry to remove (one the game gives is denied)",
                    FormLayout.Cell.button("item", itemW, null, fit(itemText, itemW, m))));
            case SPAWN_RULE -> {
                FormLayout.Cell type = FormLayout.Cell.choice("condType", 210, null, "Rule", ruleTypes);
                rows.add(!adds ? FormLayout.Row.labelled("The rule to remove", type)
                        : valueWords != null
                        ? FormLayout.Row.labelled("The rule and its value", type,
                        FormLayout.Cell.choice("condValue", 150, null, "Value", valueWords))
                        : valuePickText != null
                        ? FormLayout.Row.of(type, FormLayout.Cell.button("condValue", 150, "Value", fit(valuePickText, 150, m)))
                        : FormLayout.Row.of(type, FormLayout.Cell.field("condValue", 150, "Value")));
            }
            case ABILITY -> {
                rows.add(adds
                        ? FormLayout.Row.of(
                        FormLayout.Cell.choice("ability", 200, null, "Ability", abilities),
                        FormLayout.Cell.button("parameter", 160, "Parameter", fit(parameterText, 160, m)),
                        FormLayout.Cell.field("amount", 60, "Amount"), FormLayout.Cell.field("ticks", 60, "Ticks"),
                        FormLayout.Cell.field("radius", 60, "Radius"))
                        : FormLayout.Row.labelled("The ability to remove (and its parameter, if any)",
                        FormLayout.Cell.choice("ability", 200, null, "Ability", abilities),
                        FormLayout.Cell.button("parameter", 160, null, fit(parameterText, 160, m))));
                if (adds && !triggers.isEmpty()) {
                    rows.add(FormLayout.Row.of(
                            FormLayout.Cell.button("entity", 160, "An effect's entity", fit(entityText, 160, m)),
                            FormLayout.Cell.choice("trigger", 140, null, "Trigger", triggers),
                            FormLayout.Cell.field("duration", 60, "Duration"),
                            FormLayout.Cell.field("chance", 60, "Chance %")));
                }
            }
            case EQUIPMENT -> rows.add(adds
                    ? FormLayout.Row.of(
                    FormLayout.Cell.choice("slot", 150, null, "Slot", slots),
                    FormLayout.Cell.button("item", itemW, "Item", fit(itemText, itemW, m)),
                    FormLayout.Cell.field("chance", 70, "Chance %"))
                    : FormLayout.Row.labelled("The piece to remove",
                    FormLayout.Cell.choice("slot", 150, null, "Slot", slots),
                    FormLayout.Cell.button("item", itemW, null, fit(itemText, itemW, m))));
            case ATTRIBUTE -> rows.add(adds
                    ? FormLayout.Row.of(
                    FormLayout.Cell.choice("ability", 240, null, "Attribute", abilities),
                    FormLayout.Cell.field("amount", 80, "Value"))
                    : FormLayout.Row.labelled("The attribute to remove",
                    FormLayout.Cell.choice("ability", 240, null, "Attribute", abilities)));
            case COEFFICIENT -> rows.add(FormLayout.Row.labelled("The coefficient (1.0 = normal)",
                    FormLayout.Cell.field("amount", 120, null)));
            case START_LEVEL -> rows.add(FormLayout.Row.labelled("The start level (1 or more)",
                    FormLayout.Cell.field("amount", 120, null)));
            case BOSS -> {
            }
            case NONE -> {
            }
        }
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, applyActive, rows, notes, List.of(),
                null, null));
    }

    /** The loot entry's Item button: this wide at most. */
    public static final int ITEM_W = Grid.FIELD_W + 100;

    /** A text field or dropdown's width on a wide column: {@link Grid#fieldW}, FIELD_W on a small window. */
    static int fw(Frame frame) {
        return Grid.fieldW(frame.contentWidth());
    }

    /** An item picker's width: 100 wider than a field, within the column. */
    static int itemW(Frame frame) {
        return Math.min(fw(frame) + 100, frame.contentWidth());
    }

    /** LootEntryScreen: the item; min, max, chance; the mode; the condition and its value. */
    public static Layout lootEntry(Frame frame, TextFit.Measure m, String title, String itemText, List<String> modes,
                                   List<String> conditions, String footer1, String footer2) {
        int itemW = itemW(frame);
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, List.of(
                FormLayout.Row.labelled("Item", FormLayout.Cell.button("item", itemW, null, fit(itemText, itemW, m))),
                // A TITLE OVER EACH FIELD.
                FormLayout.Row.of(
                        FormLayout.Cell.field("min", 80, "Count min"), FormLayout.Cell.field("max", 80, "Count max"),
                        FormLayout.Cell.field("chance", 80, "Chance %")),
                FormLayout.Row.labelled("Override mode", FormLayout.Cell.choice("mode", 140, null, "Mode", modes)),
                FormLayout.Row.of(
                        FormLayout.Cell.choice("condition", 180, null, "Condition", conditions),
                        FormLayout.Cell.field("value", 120, "Condition value"))),
                footer1, footer2));
    }

    /** ChestEntryScreen: the item; the weight; the count min and max. */
    public static Layout chestEntry(Frame frame, TextFit.Measure m, String title, String itemText, String footer1,
                                    String footer2) {
        int itemW = itemW(frame);
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, List.of(
                FormLayout.Row.labelled("Item", FormLayout.Cell.button("item", itemW, null, fit(itemText, itemW, m))),
                FormLayout.Row.labelled("Weight (its share of the pool's picks; 0 never picked)",
                        FormLayout.Cell.field("weight", 80, null)),
                FormLayout.Row.of(
                        FormLayout.Cell.field("min", 80, "Count min"),
                        FormLayout.Cell.field("max", 80, "Count max"))),
                footer1, footer2));
    }

    /**
     * TradeEntryScreen: what the trader wants (cost A, and an optional cost B), what it gives, and the
     * trade's max uses, XP, and price multiplier, in the game's words.
     */
    public static Layout tradeEntry(Frame frame, TextFit.Measure m, String title, String costAText, String costBText,
                                    String resultText, String footer1, String footer2) {
        int itemW = Math.min(itemW(frame), frame.contentWidth() - 90);
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, List.of(
                // A TITLE OVER EACH FIELD.
                FormLayout.Row.of(
                        FormLayout.Cell.button("costA", itemW, "Cost A: what it wants", fit(costAText, itemW, m)),
                        FormLayout.Cell.field("costACount", 80, "Count")),
                FormLayout.Row.of(
                        FormLayout.Cell.button("costB", itemW, "Cost B: a second item", fit(costBText, itemW, m)),
                        FormLayout.Cell.field("costBCount", 80, "Count")),
                FormLayout.Row.of(
                        FormLayout.Cell.button("result", itemW, "Result: what it gives", fit(resultText, itemW, m)),
                        FormLayout.Cell.field("resultCount", 80, "Count")),
                FormLayout.Row.of(
                        FormLayout.Cell.field("maxUses", 80, "Max uses"), FormLayout.Cell.field("xp", 80, "XP"),
                        FormLayout.Cell.field("priceMultiplier", 110, "Price multiplier"))),
                footer1, footer2));
    }

    /** ChestPoolScreen: the pool's rolls, min and max. */
    public static Layout chestPool(Frame frame, TextFit.Measure m, String title, String footer1, String footer2) {
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, List.of(
                FormLayout.Row.of(
                        FormLayout.Cell.field("min", 80, "Rolls min"),
                        FormLayout.Cell.field("max", 80, "Rolls max"))),
                footer1, footer2));
    }

    /** Each scaling preview takes at most this many lines. */
    public static final int PREVIEW_LINES = 2;

    /**
     * ScalingEditScreen: the attribute; per level and exponent; start level and the switch; for a mob curve,
     * the mob to preview on; the previews under them, wrapped (it was a column beside the fields, which a
     * narrow window has no room for).
     *
     * @param mobText the preview mob's button text, or null for a player curve, which has no mob row and no
     *     real-value lines
     */
    public static Layout scalingEdit(Frame frame, TextFit.Measure m, String title, String attributeText,
                                     String mobText, String preview, String mobPreview, String footer1) {
        int attrW = itemW(frame);
        List<String> notes = new ArrayList<>(wrapped(preview, frame, m));
        List<FormLayout.Row> rows = new ArrayList<>(List.of(
                FormLayout.Row.labelled("Attribute",
                        FormLayout.Cell.button("attribute", attrW, null, fit(attributeText, attrW, m))),
                FormLayout.Row.of(
                        FormLayout.Cell.field("perLevel", 120, "Per level"),
                        FormLayout.Cell.field("exponent", 160, "Exponent (< 1 = diminishing)")),
                FormLayout.Row.of(
                        FormLayout.Cell.field("start", 160, "Start level (curve begins here)"),
                        FormLayout.Cell.toggle("enabled", 120, null, "Enabled"))));
        if (mobText != null) {
            rows.add(FormLayout.Row.labelled("Preview on mob (real values, coefficient 1)",
                    FormLayout.Cell.button("mob", attrW, null, fit(mobText, attrW, m))));
            notes.addAll(wrapped(mobPreview, frame, m));
        }
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, rows,
                notes, List.of(), footer1, null));
    }

    /**
     * Scaling > Formulas: one wide field for each formula, then one preview line for each, then what the
     * preview assumes.
     *
     * @param ids the field ids, in order
     * @param labels each field's label
     * @param previews each formula's preview line, in the same order
     */
    public static Layout formulas(Frame frame, TextFit.Measure m, String title, List<String> ids,
                                  List<String> labels, List<String> previews, String footer1) {
        List<FormLayout.Row> rows = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            rows.add(FormLayout.Row.labelled(labels.get(i), FormLayout.Cell.field(ids.get(i), frame.contentWidth(), null)));
        }
        List<String> notes = new ArrayList<>();
        for (String p : previews) {
            notes.add(TextFit.clip(p == null ? "" : p, frame.contentWidth(), m));
        }
        return FormLayout.build(frame, m, new FormLayout.Input(title, false, true, rows,
                notes, List.of(), footer1, null));
    }

    /** A preview wrapped to {@link #PREVIEW_LINES}, padded with "" so the boxes do not move. */
    private static List<String> wrapped(String text, Frame frame, TextFit.Measure m) {
        List<String> lines = new ArrayList<>(TextFit.wrap(text == null ? "" : text, frame.contentWidth(),
                PREVIEW_LINES, m));
        while (lines.size() < PREVIEW_LINES) {
            lines.add("");
        }
        return lines;
    }
}
