package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.Variant;
import com.strata.runtime.MobEquipment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.strata.domain.ScopeLayers.ScopeLayer;

import static com.strata.domain.ScopeLayers.*;

/**
 * Everything the editor reads and does for a mob's equipment (the Equipment half of EquipmentOps, named as
 * the editor names it).
 */
public final class EquipmentOps {

    private EquipmentOps() {
    }
    /**
     * What the mob wears, in one line, from the rows the Equipment list shows: its own rows and what it
     * inherits (a wider scope's, its base mob's, the game's), each marked where it comes from.
     *
     * @param slots a slot in words
     * @param words an item id in words
     */
    public static String equipmentSummary(List<EquipmentRow> rows, java.util.function.Function<String, String> slots,
                                     java.util.function.Function<String, String> words) {
        List<String> parts = new ArrayList<>();
        for (EquipmentRow r : rows) {
            String item = words.apply(r.item());
            String from = r.inherited() ? " (" + r.sourceLabel() + ")" : "";
            parts.add(slots.apply(r.slot()) + " " + item + from);
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    // ---------------------------------------------------------------- equipment

    /** The slot choices, from the runtime. */
    public static final List<String> SLOT_CHOICES = MobEquipment.SLOT_NAMES;

    /** What the equipment form starts with. */
    public static final String NEW_ITEM = "minecraft:iron_sword";
    public static final String NEW_CHANCE = "100";

    /**
     * A piece of equipment the game gives the mob, passed in by the caller
     * (DefaultEquipmentOps#gameEquipment): one of the game's items, or an armor piece, each with every number
     * of its own.
     *
     * @param pct its chance on Normal
     * @param pctHard its chance on Hard
     * @param drop its drop chance, or "" for the default
     * @param enchant its enchant chance, or "" for the default
     * @param scaled its chances scale with local difficulty (the game's armor)
     */
    public record GameEquipment(String slot, String item, double pct, double pctHard, String note,
                           String drop, String enchant, boolean scaled) {

        /** Equipment with one chance on every difficulty. */
        public GameEquipment(String slot, String item, double pct, String note) {
            this(slot, item, pct, pct, note, "", "", false);
        }

        /** Equipment the game always gives. */
        public GameEquipment(String slot, String item) {
            this(slot, item, 100, "");
        }
    }


    /**
     * One equipment row of an instance.
     *
     * @param from "" for the instance's own row, {@link #FROM_GAME} for the game's equipment, {@link
     *     #FROM_BASE} for the base mob's row a variant inherits
     * @param chanceHard its weight on Hard, or "" for chancePct
     * @param drop its drop chance, or "" for the game's default
     * @param enchant its enchant chance, or "" for the game's default
     * @param scaled its chances are at full local difficulty and scale down with it, as the game's armor
     * @param playerLevel the player XP level it needs, as typed, or "" for no gate. The shared grammar is
     *     {@link com.strata.model.LevelRange}.
     */
    public record EquipmentRow(String slot, String item, double chancePct, String from, String note,
                           String chanceHard, String drop, String enchant, boolean scaled,
                           String playerLevel) {

        /** A row whose chances are as typed and which needs no player level. */
        public EquipmentRow(String slot, String item, double chancePct, String from, String note,
                        String chanceHard, String drop, String enchant, boolean scaled) {
            this(slot, item, chancePct, from, note, chanceHard, drop, enchant, scaled, "");
        }

        /** A row whose chances are as typed. */
        public EquipmentRow(String slot, String item, double chancePct, String from, String note,
                        String chanceHard, String drop, String enchant) {
            this(slot, item, chancePct, from, note, chanceHard, drop, enchant, false);
        }

        public EquipmentRow(String slot, String item, double chancePct, String from, String note) {
            this(slot, item, chancePct, from, note, "", "", "");
        }

        public EquipmentRow(String slot, String item, double chancePct, String from) {
            this(slot, item, chancePct, from, "");
        }

        /** An own row (false) or the game's equipment (true). */
        public EquipmentRow(String slot, String item, double chancePct, boolean inherited) {
            this(slot, item, chancePct, inherited ? FROM_GAME : "");
        }


        /** True for a row this scope does not own: the game's, the base mob's, or a wider scope's. */
        public boolean inherited() {
            return !from.isEmpty();
        }

        /** The list's tag for an inherited row, or "" for an own row. */
        public String sourceLabel() {
            return switch (from) {
                case FROM_GAME -> "vanilla";
                case FROM_BASE -> "base mob";
                case FROM_TIER -> "from the tier";
                case FROM_GLOBAL -> "from global";
                default -> "";
            };
        }
    }

    /**
     * The equipment of one variant instance, as the mob gets it at spawn (MobEquipment#equip).
     *
     * @param baseEntity the mob the variant is made from, or "" for none
     */
    public static List<EquipmentRow> equipmentFor(String variantName, String baseEntity, String scope,
                                              String scopeId, List<GameEquipment> gameEquipment) {
        return equipmentFor(variantName, baseEntity, scope, scopeId, gameEquipment,
                biomeTier(scope, scopeId), Paths.ROOT);
    }

    /**
     * The same, path-rooted with the tier passed in: GroupOps asks what this instance currently wears so a
     * group stamped onto it does not take any of it away (see {@link #gameEquipmentToKeep}).
     */
    static List<EquipmentRow> equipmentFor(String variantName, String baseEntity, String scope, String scopeId,
                                           List<GameEquipment> gameEquipment, String biomeTier, Path root) {
        String base = Variant.defaultNameFor(baseEntity);
        boolean inherits = !base.isEmpty() && !base.equals(variantName);
        List<ScopeLayer> layers = ScopeLayers.scopeLayers(scope, scopeId, biomeTier);
        List<List<Map<String, String>>> own = decidingLayers(variantName, layers, root);
        int at = firstWithRows(own);
        List<List<Map<String, String>>> baseLayers = inherits ? decidingLayers(base, layers, root) : List.of();
        int b = firstWithRows(baseLayers);
        return equipmentRows(at < 0 ? List.of() : own.get(at), at < 0 ? "" : layers.get(at).from(),
                b < 0 ? List.of() : baseLayers.get(b), List.of(), gameEquipment);
    }

    /**
     * Each layer's equipment and deny rows, as the runtime loads a loadout: a layer with only the preset
     * placeholder has none, so it is empty here.
     */
    private static List<List<Map<String, String>>> decidingLayers(String variantName, List<ScopeLayer> layers) {
        return decidingLayers(variantName, layers, Paths.ROOT);
    }

    /**
     * The same, path-rooted, so GroupOps can ask what a variant's slot will hold - before and after it adopts
     * - from a temp directory rather than from the designer's own config.
     */
    static List<List<Map<String, String>>> decidingLayers(String variantName, List<ScopeLayer> layers,
                                                          Path root) {
        List<List<Map<String, String>>> out = new ArrayList<>();
        for (ScopeLayer l : layers) {
            out.add(instanceEquipment(variantName, l.scope(), l.scopeId(), root).stream()
                    .filter(EquipmentOps::decides).toList());
        }
        return out;
    }

    /**
     * The rows {@link #adoptWider} writes into the instance's own scope: the nearest wider layer's, when its
     * own layer (the first) has none.
     */
    public static List<Map<String, String>> toAdopt(List<List<Map<String, String>>> decidingLayers) {
        int at = firstWithRows(decidingLayers);
        return at <= 0 ? List.of() : decidingLayers.get(at);
    }

    /** True for an equipment row or a deny row: one that decides its slot. */
    static boolean decides(Map<String, String> csv) {
        return "deny".equalsIgnoreCase(csv.getOrDefault(Schema.Equipment.MODE, "").strip())
                || !csv.getOrDefault(Schema.Equipment.ITEM, "").strip().isEmpty();
    }

    /** Makes a wider scope's equipment this scope's own before a change. */
    private static void adoptWider(WriteReport report, String variantName, String scope, String scopeId) {
        adoptWider(report, variantName, scope, scopeId, biomeTier(scope, scopeId), Paths.ROOT);
    }

    /**
     * A biome scope's tier, or "" when the scope is not a biome - which is when the game's config is NOT read
     * at all, and that is what lets a headless case reach this code.
     */
    static String biomeTier(String scope, String scopeId) {
        if (!"biome".equals(VariantOps.normalScope(scope)) || scopeId == null) {
            return "";
        }
        var biome = com.strata.data.Config.tables().biomes().get(scopeId.strip());
        return biome == null || biome.tier() == null ? "" : biome.tier();
    }

    /** The same, path-rooted, with the biome's tier passed in. */
    static void adoptWider(WriteReport report, String variantName, String scope, String scopeId,
                           String biomeTier, Path root) {
        for (Map<String, String> row : toAdopt(decidingLayers(variantName,
                ScopeLayers.scopeLayers(scope, scopeId, biomeTier), root))) {
            if (!report.ok()) {
                return;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (String column : List.of(Schema.Equipment.CHANCE_PCT, Schema.Equipment.MODE,
                    Schema.Equipment.CHANCE_HARD, Schema.Equipment.DROP_PCT,
                    Schema.Equipment.ENCHANT_PCT, Schema.Equipment.LOCAL_DIFFICULTY,
                    Schema.Equipment.PLAYER_LEVEL)) {
                values.put(column, row.getOrDefault(column, ""));
            }
            report.record("the inherited equipment", TableWriter.upsert(root.resolve(Schema.Equipment.FILE),
                    equipmentKeys(variantName, scope, scopeId,
                            row.getOrDefault(Schema.Equipment.SLOT, "").strip(),
                            row.getOrDefault(Schema.Equipment.ITEM, "").strip()), values));
        }
    }

    /**
     * The list {@link #equipmentFor} shows, from the table rows it read.
     *
     * @param own the instance's rows
     * @param baseInScope the base mob's rows in the same scope, or none
     * @param baseGlobal the base mob's global rows, or none
     */
    public static List<EquipmentRow> equipmentRows(List<Map<String, String>> own,
                                               List<Map<String, String>> baseInScope,
                                               List<Map<String, String>> baseGlobal,
                                               List<GameEquipment> gameEquipment) {
        return equipmentRows(own, "", baseInScope, baseGlobal, gameEquipment);
    }

    /**
     * As above, with the own rows taken from a wider scope when ownFrom is {@link #FROM_TIER} or {@link
     * #FROM_GLOBAL}: they are listed as inherited.
     */
    public static List<EquipmentRow> equipmentRows(List<Map<String, String>> own, String ownFrom,
                                               List<Map<String, String>> baseInScope,
                                               List<Map<String, String>> baseGlobal,
                                               List<GameEquipment> gameEquipment) {
        Set<String> decided = new LinkedHashSet<>();
        List<EquipmentRow> rows = rowsOf(own, decided, ownFrom == null ? "" : ownFrom);
        Set<String> baseDecided = new LinkedHashSet<>();
        // A scope with no equipment row (a preset placeholder is not one) has no loadout at runtime, so the
        // lookup falls to global.
        List<EquipmentRow> baseRows = rowsOf(baseInScope, baseDecided, FROM_BASE);
        if (baseDecided.isEmpty()) {
            baseRows = rowsOf(baseGlobal, baseDecided, FROM_BASE);
        }
        for (EquipmentRow row : baseRows) {
            if (!decided.contains(row.slot())) {
                rows.add(row);
            }
        }
        for (GameEquipment equipment : gameEquipment) {
            if (!decided.contains(equipment.slot()) && !baseDecided.contains(equipment.slot())) {
                // The game's own chances, not 100: Normal and Hard, drop and enchant, and the local
                // difficulty.
                rows.add(new EquipmentRow(equipment.slot(), equipment.item(), equipment.pct(), FROM_GAME, equipment.note(),
                        equipment.pctHard() == equipment.pct() ? "" : chanceText(equipment.pctHard()), equipment.drop(), equipment.enchant(),
                        equipment.scaled()));
            }
        }
        return rows;
    }

    /**
     * A row's own numbers beyond its chance, as the Equipment list shows them: ", Hard 5%, drops 2%, enchant
     * 10%".
     */
    public static String pieceText(EquipmentRow row) {
        StringBuilder s = new StringBuilder();
        if (!row.chanceHard().isBlank()) {
            s.append(", Hard ").append(row.chanceHard()).append('%');
        }
        if (!row.drop().isBlank()) {
            s.append(", drops ").append(row.drop()).append('%');
        }
        if (!row.enchant().isBlank()) {
            s.append(", enchant ").append(row.enchant()).append('%');
        }
        if (row.scaled()) {
            s.append(", by local difficulty");
        }
        return s.toString();
    }

    /** A chance as the list and the field show it: "25", "6.25". */
    public static String chanceText(double chance) {
        return chance == Math.rint(chance) ? String.valueOf((long) chance) : String.valueOf(chance);
    }

    /** The equipment rows of one instance's csv rows, and the slots they decide. */
    private static List<EquipmentRow> rowsOf(List<Map<String, String>> csvRows, Set<String> decided,
                                           String from) {
        List<EquipmentRow> rows = new ArrayList<>();
        for (Map<String, String> csv : csvRows) {
            String slot = csv.getOrDefault(Schema.Equipment.SLOT, "").strip();
            String item = csv.getOrDefault(Schema.Equipment.ITEM, "").strip();
            // A deny first: denying the game's random armor has no item.
            if ("deny".equalsIgnoreCase(csv.getOrDefault(Schema.Equipment.MODE, "").strip())) {
                decided.add(slot);
                continue;
            }
            if (item.isEmpty()) {
                continue;
            }
            decided.add(slot);
            rows.add(new EquipmentRow(slot, item,
                    Parse.number(csv.get(Schema.Equipment.CHANCE_PCT), 100), from, "",
                    strip(csv.get(Schema.Equipment.CHANCE_HARD)), strip(csv.get(Schema.Equipment.DROP_PCT)),
                    strip(csv.get(Schema.Equipment.ENCHANT_PCT)),
                    com.strata.runtime.MobEquipment.scaled(csv.get(Schema.Equipment.LOCAL_DIFFICULTY)),
                    strip(csv.get(Schema.Equipment.PLAYER_LEVEL))));
        }
        return rows;
    }

    /**
     * The share of a slot its rows leave free: 100 minus their weights, never below 0.
     *
     * @param slotTotal the weights of every own row sharing the slot
     * @param currentWeight a row's own weight to leave out of the total, or 0
     */
    public static double availableForSlot(double slotTotal, double currentWeight) {
        return Math.max(0, 100 - (slotTotal - currentWeight));
    }

    /** The share of spawns a candidate gets in its slot's single draw, in percent, to two places. */
    public static double oddsPct(double chancePct, double slotTotal) {
        return Math.round(chancePct * 10000.0 / Math.max(100, slotTotal)) / 100.0;
    }

    /** Whether an equip edit MOVED the row - the slot or the item changed. */
    public static boolean equipmentRowMoved(String originalSlot, String originalItem,
                                            String slot, String item) {
        return !strip(originalSlot).equals(strip(slot)) || !strip(originalItem).equals(strip(item));
    }

    /** Why an equipment chance cannot be saved, or null when it can. */
    public static String equipmentChanceProblem(double typed) {
        return !(typed > 0) || Double.isInfinite(typed) ? "The chance must be a number above 0" : null;
    }

    /**
     * The same check as the chance the add/edit row holds, read exactly the way {@link #saveEquipment} reads
     * it.
     */
    public static String equipmentChanceProblem(EquipmentRow editing, String slot, String item,
                                                String chanceTyped) {
        return equipmentChanceProblem(Parse.number(chanceTyped, currentWeightFor(editing, slot, item)));
    }

    /**
     * The weight an edit falls back to when its cell is blank: its own row's weight, and only while the slot
     * and item still name that row.
     */
    private static double currentWeightFor(EquipmentRow editing, String slot, String item) {
        return editing != null && editing.slot().equals(slot) && editing.item().equals(item)
                ? editing.chancePct() : 0;
    }

    /**
     * Why an optional chance cannot be saved, or null: blank (the default applies) or a number from 0 to 100.
     *
     * @param label the field's name, for the words
     */
    public static String optionalChanceProblem(String label, String typed) {
        String t = strip(typed);
        if (t.isEmpty()) {
            return null;
        }
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) && v >= 0 && v <= 100 ? null : label + ": blank, or a number from 0 to 100";
        } catch (NumberFormatException e) {
            return label + ": blank, or a number from 0 to 100";
        }
    }

    /** Why a row's Hard, Drop, or Enchant cannot be saved, or null. */
    public static String pieceProblem(String hard, String drop, String enchant) {
        String h = strip(hard);
        if (!h.isEmpty()) {
            try {
                double v = Double.parseDouble(h);
                if (!Double.isFinite(v) || v < 0) {
                    return "Hard %: blank, or a number of 0 or more";
                }
            } catch (NumberFormatException e) {
                return "Hard %: blank, or a number of 0 or more";
            }
        }
        String p = optionalChanceProblem("Drop %", drop);
        return p != null ? p : optionalChanceProblem("Enchant %", enchant);
    }

    /** The equipment add/edit row's fields, for {@link #equipmentTip}. */
    public enum EquipmentField { SLOT, ITEM, CHANCE, HARD, DROP, ENCHANT, SCALED, PLAYER_LEVEL }

    /**
     * The equipment editor's one row, as its edit screen opens it: what the fields read, which row the save
     * moves, and where that row came from.
     *
     * @param editing the row the save MOVES, or null when it writes a new one
     * @param from where the opened row came from ("vanilla", "base mob"), kept because {@code editing} is
     *     null for an inherited row and the screen still has to say what the save will do
     * @param scaled its chances scale with local difficulty
     * @param playerLevel the player XP level it needs, or "" for no gate
     */
    public record EquipmentForm(String slot, String item, String chance,
                            EquipmentRow editing, String from, String hard, String drop, String enchant,
                            boolean scaled, String playerLevel) {

        /** A form with no player-level gate. */
        public EquipmentForm(String slot, String item, String chance, EquipmentRow editing, String from,
                             String hard, String drop, String enchant, boolean scaled) {
            this(slot, item, chance, editing, from, hard, drop, enchant, scaled, "");
        }

        public EquipmentForm(String slot, String item, String chance, EquipmentRow editing, String from) {
            this(slot, item, chance, editing, from, "", "", "", false);
        }
    }

    /** The Local difficulty choice's two words. */
    public static final String SCALED_YES = "Yes";
    public static final String SCALED_NO = "No";
    public static final List<String> SCALED_CHOICES = List.of(SCALED_YES, SCALED_NO);

    /** The choice's word for a row's switch, and back. */
    public static String scaledWord(boolean scaled) {
        return scaled ? SCALED_YES : SCALED_NO;
    }

    public static boolean scaledOf(String word) {
        return SCALED_YES.equals(word);
    }

    /** The equipment form for a new candidate: the first slot, a sword, every chance. */
    public static final EquipmentForm NEW_GEAR_FORM =
            new EquipmentForm(SLOT_CHOICES.getFirst(), NEW_ITEM, NEW_CHANCE, null, "");


    /** The equipment form as it opens for {@code row}, or for a new candidate when {@code row} is null. */
    public static EquipmentForm equipmentForm(EquipmentRow row) {
        if (row == null) {
            return NEW_GEAR_FORM;
        }
        return new EquipmentForm(
                row.slot(),
                row.item(),
                chanceText(row.chancePct()),
                row.inherited() ? null : row,
                row.sourceLabel(),
                row.chanceHard(), row.drop(), row.enchant(),
                row.scaled(),
                row.playerLevel());
    }

    /** What one field of the equipment add/edit row means, in the designer's words. */
    public static String equipmentTip(EquipmentField field) {
        return switch (field) {
            case SLOT -> "Where the piece is worn. Click to change. A slot can hold several"
                    + " candidates, and one draw at spawn picks one, so two never end up in the"
                    + " same hand.";
            case ITEM -> "Click to select an item from the registry";
            case CHANCE -> "This row's share of the slot on Easy and Normal. One candidate in the slot:"
                    + " its chance to be worn, 0 to 100. Several: they share one draw, so these are relative"
                    + " odds. A slot totalling under 100 leaves the rest empty-handed; over 100"
                    + " still works, the odds go by the ratio.";
            case HARD -> "The same share on Hard. Blank: the same as Normal.";
            case DROP -> "The chance this piece drops when the mob dies, 0 to 100. The game drops a"
                    + " piece only when a player lands the kill. To drop however it dies, use a Loot & XP"
                    + " slot chance above 1. Blank: the game's default for this mob. Looting adds 1% a level.";
            case ENCHANT -> "The chance this piece is enchanted at spawn, 0 to 100, scaled by local"
                    + " difficulty. Blank: the game's default for this mob and slot.";
            case SCALED -> "Yes: Normal % and Hard % are at full local difficulty, and scale down in"
                    + " new or easy areas, as the game's own armor does (never on Easy). No: they are"
                    + " as typed.";
            case PLAYER_LEVEL -> "The player's experience level this piece needs: a number like 5, a"
                    + " range like 3-10, or a minimum like 5+. Blank: no gate, so the piece applies as"
                    + " before. While a nearby player's level fails the test, this row is left out of its"
                    + " slot's draw, so a different candidate or the game's own equipment can fill it.";
        };
    }

    /**
     * The inherited equipment a save writes as this instance's own rows first, so a new item joins it instead
     * of pushing it out.
     */
    public static List<EquipmentRow> gameEquipmentToKeep(EquipmentRow editing, String slot, String item,
                                                List<EquipmentRow> rows) {
        return gameEquipmentToKeep(editing, slot, item == null ? Set.of() : Set.of(item), rows);
    }

    /**
     * The same, for a caller that brings SEVERAL new pieces to one slot at once (applying a group of
     * equipment).
     *
     * @param skip the items the caller supplies itself, and so must not be kept as well
     */
    public static List<EquipmentRow> gameEquipmentToKeep(EquipmentRow editing, String slot, Set<String> skip,
                                                List<EquipmentRow> rows) {
        List<EquipmentRow> keep = new ArrayList<>();
        if (editing != null) {
            return keep;
        }
        for (EquipmentRow row : rows) {
            // Every inherited piece is kept, the game's armor too: it has its own numbers since each piece
            // rolls alone.
            if (row.inherited() && !row.item().isEmpty()
                    && row.slot().equals(slot) && !skip.contains(row.item())) {
                keep.add(row);
            }
        }
        return keep;
    }

    /** The cells an equipment row writes. */
    public static Map<String, String> equipmentValues(double chance, String hard, String drop, String enchant,
                                                 boolean scaled, String playerLevel) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Equipment.CHANCE_PCT, chanceText(chance));
        values.put(Schema.Equipment.MODE, "");
        values.put(Schema.Equipment.CHANCE_HARD, strip(hard));
        values.put(Schema.Equipment.DROP_PCT, strip(drop));
        values.put(Schema.Equipment.ENCHANT_PCT, strip(enchant));
        values.put(Schema.Equipment.LOCAL_DIFFICULTY, scaled ? "yes" : "");
        values.put(Schema.Equipment.PLAYER_LEVEL, strip(playerLevel));
        return values;
    }

    /** The cells of an inherited row written as this instance's own: all its numbers kept. */
    public static Map<String, String> equipmentValues(EquipmentRow row) {
        return equipmentValues(row.chancePct(), row.chanceHard(), row.drop(), row.enchant(), row.scaled(),
                row.playerLevel());
    }

    /**
     * Saves an equipment candidate, then reloads.
     *
     * @param editing the own row being edited, or null for a new candidate
     * @param rows the rows the editor is showing, with the game's equipment
     * @param hard the weight on Hard, "" for the Normal weight
     * @param drop the drop chance, "" for the game's default
     * @param enchant the enchant chance, "" for the game's default
     * @param scaled its chances scale with local difficulty
     * @param playerLevel the player XP level it needs, "" for no gate
     */
    public static WriteReport saveEquipment(String variantName, String scope, String scopeId,
                                            EquipmentRow editing, String slot, String item,
                                            String chanceTyped, String hard, String drop, String enchant,
                                            boolean scaled, String playerLevel, List<EquipmentRow> rows) {
        String s = strip(slot);
        String i = strip(item);
        if (i.isEmpty()) {
            return WriteReport.refused("Pick an item first");
        }
        double currentWeight = currentWeightFor(editing, s, i);
        double typed = Parse.number(chanceTyped, currentWeight);
        String problem = equipmentChanceProblem(typed);
        if (problem == null) {
            problem = pieceProblem(hard, drop, enchant);
        }
        if (problem == null) {
            // A blank gate is "no gate" (allowBlank true); a set one must read.
            problem = com.strata.model.LevelRange.problem(playerLevel, true);
        }
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        WriteReport report = new WriteReport();
        adoptWider(report, variantName, scope, scopeId);
        for (EquipmentRow game : gameEquipmentToKeep(editing, s, i, rows)) {
            report.record("the game's equipment", TableWriter.upsert(Schema.Equipment.FILE,
                    equipmentKeys(variantName, scope, scopeId, game.slot(), game.item()), equipmentValues(game)));
            if (!report.ok()) {
                return report;
            }
        }
        report.record("the equipment", TableWriter.upsert(
                Schema.Equipment.FILE, equipmentKeys(variantName, scope, scopeId, s, i),
                equipmentValues(typed, hard, drop, enchant, scaled, playerLevel)));
        if (report.ok() && editing != null && equipmentRowMoved(editing.slot(), editing.item(), s, i)) {
            report.record("the equipment", TableWriter.delete(Schema.Equipment.FILE,
                    equipmentKeys(variantName, scope, scopeId, editing.slot(), editing.item())));
        }
        return Commit.reloadIfOk(report);
    }

    /** True when {@link #removeEquipment} on this row writes a deny, false when it deletes the row. */
    public static boolean removeDenies(EquipmentRow row) {
        return row.inherited();
    }

    /**
     * Removes this instance's own equipment row, or DENIES an inherited piece (the game's or the base mob's),
     * then reloads.
     *
     * @param rows the rows the editor is showing, with the inherited ones
     */
    public static WriteReport removeEquipment(String variantName, String scope, String scopeId,
                                              EquipmentRow row, List<EquipmentRow> rows) {
        Map<String, String> keys = equipmentKeys(variantName, scope, scopeId, row.slot(), row.item());
        if (removeDenies(row)) {
            WriteReport report = new WriteReport();
            adoptWider(report, variantName, scope, scopeId);
            for (EquipmentRow kept : gameEquipmentToKeep(null, row.slot(), row.item(), rows)) {
                report.record("the inherited equipment", TableWriter.upsert(Schema.Equipment.FILE,
                        equipmentKeys(variantName, scope, scopeId, kept.slot(), kept.item()), equipmentValues(kept)));
                if (!report.ok()) {
                    return report;
                }
            }
            Map<String, String> values = new LinkedHashMap<>();
            values.put(Schema.Equipment.MODE, "deny");
            // Zero weight, so even a build that ignores the mode column cannot equip it: load skips rows at
            // chance 0.
            values.put(Schema.Equipment.CHANCE_PCT, "0");
            return Commit.reloadIfOk(report.record("the deny",
                    TableWriter.upsert(Schema.Equipment.FILE, keys, values)));
        }
        return Commit.reloadIfOk(new WriteReport().record("the equipment",
                TableWriter.delete(Schema.Equipment.FILE, keys)));
    }

    /**
     * What removing several rows at once writes.
     *
     * @param keep inherited pieces written as own rows first: the rest of each slot a deny decides, as one
     *     deny keeps them
     * @param deny inherited pieces denied
     * @param delete own rows deleted
     */
    public record RemovalPlan(List<EquipmentRow> keep, List<EquipmentRow> deny, List<EquipmentRow> delete) {
    }

    /**
     * The plan for removing these rows together.
     *
     * @param shown every row the editor shows, the inherited ones too
     */
    public static RemovalPlan removalPlan(List<EquipmentRow> removing, List<EquipmentRow> shown) {
        List<EquipmentRow> deny = new ArrayList<>();
        List<EquipmentRow> delete = new ArrayList<>();
        Set<String> deniedSlots = new LinkedHashSet<>();
        for (EquipmentRow row : removing) {
            if (removeDenies(row)) {
                deny.add(row);
                deniedSlots.add(row.slot());
            } else {
                delete.add(row);
            }
        }
        List<EquipmentRow> keep = new ArrayList<>();
        for (EquipmentRow row : shown) {
            if (row.inherited() && !row.item().isEmpty()
                    && deniedSlots.contains(row.slot()) && !removing.contains(row)) {
                keep.add(row);
            }
        }
        return new RemovalPlan(List.copyOf(keep), List.copyOf(deny), List.copyOf(delete));
    }

    /**
     * Removes several rows together, as {@link #removalPlan} says: a wider scope's set is made this scope's
     * own first, the kept pieces are written, the denies, then the deletes; one reload at the end.
     */
    public static WriteReport removeEquipmentAll(String variantName, String scope, String scopeId,
                                                 List<EquipmentRow> removing, List<EquipmentRow> shown) {
        if (removing.isEmpty()) {
            return WriteReport.refused("Pick a row first");
        }
        RemovalPlan plan = removalPlan(removing, shown);
        WriteReport report = new WriteReport();
        if (!plan.deny().isEmpty()) {
            adoptWider(report, variantName, scope, scopeId);
        }
        for (EquipmentRow kept : plan.keep()) {
            if (!report.ok()) return report;
            report.record("the inherited equipment", TableWriter.upsert(Schema.Equipment.FILE,
                    equipmentKeys(variantName, scope, scopeId, kept.slot(), kept.item()), equipmentValues(kept)));
        }
        for (EquipmentRow row : plan.deny()) {
            if (!report.ok()) return report;
            Map<String, String> values = new LinkedHashMap<>();
            values.put(Schema.Equipment.MODE, "deny");
            values.put(Schema.Equipment.CHANCE_PCT, "0");
            report.record("the deny", TableWriter.upsert(Schema.Equipment.FILE,
                    equipmentKeys(variantName, scope, scopeId, row.slot(), row.item()), values));
        }
        for (EquipmentRow row : plan.delete()) {
            if (!report.ok()) return report;
            report.record("the equipment", TableWriter.delete(Schema.Equipment.FILE,
                    equipmentKeys(variantName, scope, scopeId, row.slot(), row.item())));
        }
        return Commit.reloadIfOk(report);
    }

    /**
     * Clears every equipment row and deny row of this instance, so the base entity's own equipment applies
     * again, then reloads.
     */
    public static WriteReport restoreEquipment(String variantName, String scope, String scopeId) {
        WriteReport report = new WriteReport();
        for (Map<String, String> row : instanceEquipment(variantName, scope, scopeId)) {
            Map<String, String> keys = new LinkedHashMap<>(row);
            keys.replaceAll((k, v) -> v == null ? "" : v.strip());
            report.record("an equipment row", TableWriter.delete(Schema.Equipment.FILE, keys));
        }
        return Commit.reloadIfOk(report);
    }

    /** Every equipment row of one instance, equipment and denies alike. */
    private static List<Map<String, String>> instanceEquipment(String variantName, String scope,
                                                               String scopeId) {
        return instanceEquipment(variantName, scope, scopeId, Paths.ROOT);
    }

    /** The same, path-rooted, so a test reads its own tables. */
    static List<Map<String, String>> instanceEquipment(String variantName, String scope, String scopeId,
                                                       Path root) {
        String variant = variantName == null ? "" : variantName;
        String wantScope = VariantOps.normalScope(scope);
        String wantScopeId = VariantOps.normalScopeId(scope, scopeId);
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> csv : CsvTable.read(root.resolve(Schema.Equipment.FILE))) {
            if (!variant.equals(csv.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip())) continue;
            if (!wantScope.equals(VariantOps.normalScope(csv.getOrDefault(Schema.Equipment.SCOPE, "")))
                    || !wantScopeId.equals(csv.getOrDefault(Schema.Equipment.SCOPE_ID, "").strip())) continue;
            out.add(csv);
        }
        return out;
    }

    /** The key an instance's row is written under. */
    static Map<String, String> equipmentKeys(String variantName, String scope, String scopeId,
                                             String slot, String item) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Equipment.VARIANT_NAME, variantName);
        // The scope is part of the key: without it, editing a tier's equipment rewrites the global variant's
        // row instead of its own.
        keys.put(Schema.Equipment.SCOPE, VariantOps.normalScope(scope));
        keys.put(Schema.Equipment.SCOPE_ID, VariantOps.normalScopeId(scope, scopeId));
        keys.put(Schema.Equipment.SLOT, slot);
        keys.put(Schema.Equipment.ITEM, item);
        return keys;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}