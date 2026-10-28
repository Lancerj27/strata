package com.strata;

import com.strata.ui.Box;
import com.strata.ui.Fit;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.GameFont;
import com.strata.ui.Grid;
import com.strata.ui.Layout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** THE FIT GATE. */
public final class FitCheckSelfTest {

    private FitCheckSelfTest() {
    }

    /** The options of the test form. */
    private record Form(int rows, boolean pinnedActions, boolean saveActive) {
    }

    /**
     * A form as Strata builds one: the header; Undo, Save (a main action); field rows, each with its label
     * above; a footer line.
     */
    private static Layout form(Frame frame, GameFont font, Form form) {
        Layout layout = Layout.framed(frame, font, "Strata — Tier: Deep caves");
        int cl = frame.contentLeft();
        int top = Grid.actionRowY();
        Layout.Element undo = Layout.control("undo", Layout.Kind.BUTTON, new Box(cl, top, Grid.BTN_W, Grid.CTRL_H), "Undo");
        Layout.Element save = Layout.control("save", Layout.Kind.BUTTON,
                new Box(cl + Grid.BTN_2, top, Grid.BTN_W_WIDE, Grid.CTRL_H), "Save").asMain()
                .withActive(form.saveActive());
        for (Layout.Element e : new Layout.Element[]{undo, save}) {
            layout.add(form.pinnedActions() ? e.asPinned() : e);
        }
        int fieldsTop = top + Grid.CTRL_H + Grid.GAP;
        if (form.pinnedActions()) {
            layout.viewTop(fieldsTop);
        }
        int y = fieldsTop - Grid.LABEL_OFFSET + 2;
        int fieldW = Math.min(Grid.FIELD_W, frame.contentWidth());
        for (int i = 0; i < form.rows(); i++) {
            layout.add(layout.text("label" + i, cl, y + Grid.LABEL_OFFSET, "Field number " + i, frame.contentWidth()));
            layout.add(Layout.control("field" + i, Layout.Kind.FIELD, new Box(cl, y, fieldW, Grid.CTRL_H), ""));
            y += frame.rowH();
        }
        layout.add(layout.text("footer", cl, frame.footerY(), "The footer line of the form, which stays put.",
                frame.contentWidth()).asPinned());
        return layout.scrolls(true).footerReserve(36);
    }

    /** The findings for a layout at every size, each named with its size. */
    private static List<String> atEverySize(Function<Frame, Layout> make) {
        List<String> out = new ArrayList<>();
        for (int[] size : Fit.SIZES) {
            for (String problem : Fit.check(make.apply(Fit.frameFor(size)))) {
                out.add(Fit.nameOf(size) + ": " + problem);
            }
        }
        return out;
    }

    /** True when the fault is found at the smallest size, and says so. */
    private static boolean catches(List<String> lines, String caseId, Layout layout, String finding) {
        List<String> found = Fit.check(layout);
        boolean ok = found.stream().anyMatch(f -> f.contains(finding));
        if (!ok) {
            lines.add("    " + caseId + " expected \"" + finding + "\", found " + found);
        }
        return ok;
    }

    private static boolean none(List<String> lines, String caseId, List<String> found) {
        for (String f : found.subList(0, Math.min(10, found.size()))) {
            lines.add("    " + caseId + " " + f);
        }
        return found.isEmpty();
    }

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];
        checks(lines, tally);
        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

        /** Shared by more than one part of checks. */
        private static GameFont font;

        /** Shared by more than one part of checks. */
        private static Frame small;

        /** Shared by more than one part of checks. */
        private static String longTitle;

        /** Shared by more than one part of checks. */
        private static String longFooter;

        /** Shared by more than one part of checks. */
        private static String longEmpty;

    private static void checks(List<String> lines, int[] tally) {
        checksPart01(lines, tally);
        checksPart02(lines, tally);
        checksPart03(lines, tally);
    }

    private static void checksPart01(List<String> lines, int[] tally) {
        font = LayoutCheckSelfTest.gameFont(lines, "fit");
        small = Fit.frameFor(Fit.SIZES[0]);

        SelfTestHarness.check(lines, tally, "fit.1 a short form fits at every size", () ->
                font != null && none(lines, "fit.1",
                        atEverySize(f -> form(f, font, new Form(3, false, true)))));

        SelfTestHarness.check(lines, tally,
                "fit.2 a tall form scrolls under its pinned actions and fits, at the top and at the end", () -> {
            if (font == null) {
                return false;
            }
            boolean scrolls = form(small, font, new Form(12, true, true)).maxScroll() > 0;
            if (!scrolls) {
                lines.add("    fit.2 the tall form does not scroll at " + Fit.nameOf(Fit.SIZES[0]));
            }
            return none(lines, "fit.2", atEverySize(f -> form(f, font, new Form(12, true, true)))) && scrolls;
        });

        SelfTestHarness.check(lines, tally, "fit.3 the header fits with the longest title", () -> {
            if (font == null) {
                return false;
            }
            String title = "Strata — Goals: " + "a variant with a very long name ".repeat(10);
            return none(lines, "fit.3", atEverySize(f -> Layout.framed(f, font, title)));
        });

        SelfTestHarness.check(lines, tally, "fit.4 a control off the screen is found", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = form(small, font, new Form(3, false, true));
            layout.add(Layout.control("off", Layout.Kind.BUTTON,
                    new Box(small.width() - 20, 100, Grid.BTN_W, Grid.CTRL_H), "Off"));
            return catches(lines, "fit.4", layout, "off screen: button 'off'");
        });

        SelfTestHarness.check(lines, tally, "fit.5 two controls that overlap are found", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = form(small, font, new Form(3, false, true));
            Box save = layout.get("save").box();
            layout.add(Layout.control("twin", Layout.Kind.BUTTON, new Box(save.x() + 10, save.y(), 40, save.h()), "Go"));
            return catches(lines, "fit.5", layout, "controls overlap");
        });

        SelfTestHarness.check(lines, tally, "fit.6 a label wider than its button is found", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = form(small, font, new Form(3, false, true));
            layout.add(Layout.control("narrow", Layout.Kind.BUTTON,
                    new Box(small.contentLeft() + 320, Grid.actionRowY(), 40, Grid.CTRL_H), "Conditions"));
            return catches(lines, "fit.6", layout, "label wider than its button: button 'narrow'");
        });

        SelfTestHarness.check(lines, tally, "fit.7 text across a control is found", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = form(small, font, new Form(3, false, true));
            Box save = layout.get("save").box();
            layout.add(layout.text("note", save.x() - 20, save.y() + 4, "A note that runs into Save", 400));
            return catches(lines, "fit.7", layout, "text crosses a control: text 'note'");
        });

        SelfTestHarness.check(lines, tally, "fit.8 text over text is found", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = form(small, font, new Form(3, false, true));
            Box title = layout.get("title").box();
            layout.add(layout.text("stray", title.x() + 4, title.y() + 2, "Stray", 100).asPinned());
            return catches(lines, "fit.8", layout, "text over text");
        });

        SelfTestHarness.check(lines, tally, "fit.9 a main action the scroll hides at the top is found", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = form(small, font, new Form(12, true, true));
            layout.add(Layout.control("goalSet", Layout.Kind.BUTTON,
                    new Box(small.contentLeft(), 400, Grid.FIELD_W, Grid.CTRL_H), "Goal set").asMain());
            return catches(lines, "fit.9", layout, "main action hidden at the top: button 'goalSet'");
        });

        SelfTestHarness.check(lines, tally, "fit.10 a main action greyed out is found", () -> {
            if (font == null) {
                return false;
            }
            return catches(lines, "fit.10", form(small, font, new Form(3, false, false)),
                    "main action greyed out: button 'save'");
        });

        SelfTestHarness.check(lines, tally, "fit.11 a flow row of tools fits at every size", () -> {
            if (font == null) {
                return false;
            }
            String[] tools = {"Spawn rules", "Loot", "Equipment", "Mob data", "Abilities",
                    "Goal set: zombie's own goals (default)", "Edit AI"};
            int[] widths = {100, Grid.BTN_W, 90, 90, Grid.BTN_W_WIDE, Grid.FIELD_W, 64};
            return none(lines, "fit.11", atEverySize(f -> {
                Layout layout = Layout.framed(f, font, "Strata — Mob: Zombie");
                Flow.Placed placed = Flow.row(f.contentLeft(), f.contentRight(), Grid.actionRowY(), Grid.CTRL_H,
                        Grid.GAP, widths);
                for (int i = 0; i < tools.length; i++) {
                    layout.add(Layout.control("tool" + i, Layout.Kind.BUTTON, placed.boxes().get(i), tools[i]).asMain());
                }
                return layout;
            }));
        });

        SelfTestHarness.check(lines, tally, "fit.31 text over a list is found, and a note in it is not", () -> {
            if (font == null) {
                return false;
            }
            Layout layout = Layout.framed(small, font, "Strata — List");
            Box list = new Box(small.contentLeft(), 40, 200, 100);
            layout.add(Layout.control("list", Layout.Kind.LIST, list, ""));
            layout.add(layout.note("empty", list.x() + 4, list.y() + 6, "Nothing here yet", 190));
            boolean noteOk = Fit.check(layout).isEmpty();
            layout.add(layout.text("stray", list.x() + 4, list.y() + 40, "A footer that slid up", 190));
            boolean strayFound = catches(lines, "fit.31", layout, "text crosses a control: text 'stray'");
            if (!noteOk) {
                lines.add("    fit.31 the note was reported: " + Fit.check(layout));
            }
            return noteOk && strayFound;
        });

        SelfTestHarness.check(lines, tally,
                "fit.43 a note out of its list, and a control taller than the view, are found", () -> {
            if (font == null) {
                return false;
            }
            Layout spill = Layout.framed(small, font, "Strata — Box");
            spill.add(Layout.control("box", Layout.Kind.LIST, new Box(small.contentLeft(), 40, 200, 20), ""));
            spill.add(spill.note("in", small.contentLeft() + 3, 43, "Inside", 190));
            spill.add(spill.note("out", small.contentLeft() + 3, 70, "Spilled out", 190));
            boolean spilled = catches(lines, "fit.43", spill, "note outside its list: note 'out'");
            Layout tall = form(small, font, new Form(3, true, true));
            tall.add(Layout.control("tall", Layout.Kind.LIST,
                    new Box(small.contentLeft(), 300, 200, small.height()), ""));
            boolean taller = catches(lines, "fit.43", tall, "taller than the view");
            return spilled && taller;
        });


        SelfTestHarness.check(lines, tally,
                "fit.12 the mob editor fits at every size: plain mob, variant with trades, new row", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (boolean[] state : new boolean[][]{{false, false, false}, {false, true, true}, {true, true, false}}) {
                for (String f : atEverySize(frame -> com.strata.ui.screen.MobEditLayout.build(frame, font,
                        mobEditor(state[0], state[1], state[2])))) {
                    found.add((state[0] ? "new " : "") + (state[1] ? "variant" : "plain mob")
                            + (state[2] ? " trades" : "") + " " + f);
                }
            }
            return none(lines, "fit.12", found);
        });

        SelfTestHarness.check(lines, tally, "fit.13 the equipment list fits at every size, with Remove, Deny, or both for many rows", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (String remove : new String[]{com.strata.ui.screen.EquipmentLayout.REMOVE,
                    com.strata.ui.screen.EquipmentLayout.DENY,
                    com.strata.ui.screen.EquipmentLayout.removeLabel(40, 59)}) {
                found.addAll(atEverySize(frame -> com.strata.ui.screen.EquipmentLayout.build(frame, font,
                        "Strata — Equipment: " + "an_extremely_long_variant_name_".repeat(3), remove,
                        longText("Deny blocks a piece with no row here (base mob equipment).", 20),
                        longText("3 own + 4 inherited entr(ies) — one slot is one draw;", 20),
                        "No equipment data for this variant's base entity",
                        "", false)));
            }
            return none(lines, "fit.13", found);
        });

        SelfTestHarness.check(lines, tally, "fit.14 one equipment row fits at every size, with every slot", () ->
                font != null && none(lines, "fit.14", atEverySize(frame ->
                        com.strata.ui.screen.EquipmentItemLayout.build(frame, font,
                                "Strata — Equipment: " + "An Extremely Long Modded Item Name ".repeat(2),
                                com.strata.domain.EquipmentOps.SLOT_CHOICES,
                                "Some Mod: An Extremely Long Modded Item Name Of Great Length",
                                longText("Equipment Biome: somemod:long_biome — one row is one draw", 10),
                                longText("That piece is base mob equipment — saving writes it as own", 10),
                                com.strata.domain.EquipmentOps.SCALED_CHOICES))));

        SelfTestHarness.check(lines, tally,
                "fit.15 the abilities list fits at every size, with every ability, Remove or Turn off", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (String remove : new String[]{com.strata.ui.screen.AbilitiesLayout.REMOVE,
                    com.strata.ui.screen.AbilitiesLayout.TURN_OFF,
                    com.strata.ui.screen.ListLayout.manyLabel(com.strata.ui.screen.AbilitiesLayout.REMOVE,
                            com.strata.ui.screen.AbilitiesLayout.TURN_OFF, 40, 59)}) {
                found.addAll(atEverySize(frame -> com.strata.ui.screen.AbilitiesLayout.build(frame, font,
                        "Strata — Abilities: " + "an_extremely_long_variant_name_".repeat(3),
                        com.strata.domain.AbilitiesOps.abilityChoices(), remove, "Some Mod: An Extremely Long Effect Name Of A Large Mod",
                        "on take damage", "An Extremely Long Entity Name",
                        longText("To add: the effect needs a potion id, like minecraft:speed,", 10),
                        longText("An Extremely Long Group Name ", 2),
                        true)));
            }
            for (String ability : com.strata.domain.AbilitiesOps.abilityChoices()) {
                java.util.function.Predicate<String> shows = id -> id.equals("parameter")
                        ? com.strata.domain.AbilitiesOps.parameterKind(ability)
                                != com.strata.domain.AbilitiesOps.ParameterKind.NONE
                        : java.util.Arrays.stream(com.strata.model.AbilityField.values())
                                .anyMatch(f -> f.key().equals(id.equals("ticks") ? "interval_ticks" : id)
                                        && com.strata.domain.AbilitiesOps.usesField(ability, f));
                found.addAll(atEverySize(frame -> com.strata.ui.screen.AbilitiesLayout.build(frame, font,
                        "Strata — Abilities: " + "an_extremely_long_variant_name_".repeat(3),
                        com.strata.domain.AbilitiesOps.abilityChoices(), com.strata.ui.screen.AbilitiesLayout.REMOVE,
                        "Some Mod: An Extremely Long Effect Name Of A Large Mod",
                        "on take damage", "An Extremely Long Entity Name",
                        longText("To add: the effect needs a potion id, like minecraft:speed,", 10),
                        longText("An Extremely Long Group Name ", 2), true, shows,
                        new com.strata.ui.screen.AbilitiesLayout.Extras("Some Mod: An Extremely Long Damage Type",
                                "Some Mod: An Extremely Long Item", "false"))));
            }
            return none(lines, "fit.15", found);
        });

        SelfTestHarness.check(lines, tally, "fit.16 the mob data sheet fits at every size, on every tab", () -> {
            if (font == null) {
                return false;
            }
            List<String> tabs = List.of("Attributes", "Type", "Fields", "Tags");
            List<com.strata.ui.screen.FormLayout.Cell> switches = new ArrayList<>();
            for (String name : List.of("Category", "Mob griefing", "Spawn as")) {
                switches.add(new com.strata.ui.screen.FormLayout.Cell("sw_" + name.toLowerCase(java.util.Locale.ROOT),
                        Layout.Kind.BUTTON, 150, name,
                        List.of(com.strata.ui.screen.ListLayout.Action.choice("sw_" + name, name,
                                List.of("An Extremely Long Category Word For A Test"), 150, font).label())));
            }
            List<String> found = new ArrayList<>();
            for (int tab = 0; tab < tabs.size(); tab++) {
                int open = tab;
                found.addAll(atEverySize(frame -> com.strata.ui.screen.MobDataLayout.build(frame, font,
                        "Strata — Mob data: " + "an_extremely_long_variant_name_".repeat(3), tabs, open,
                        longText("Game value: 0.23000000417232513. Inherited: 0.3. This variant:", 30),
                        longText("1234 value(s) in Attributes. The game's values are read", 10),
                        "No values read for this mob: the extraction at game start failed (see the log).",
                        open == 0 ? switches : List.of(), open != 0)));
            }
            return none(lines, "fit.16", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.17 the spawn conditions fit at every size: every row that fits, every value, the arrows", () -> {
            if (font == null) {
                return false;
            }
            List<com.strata.ui.screen.SpawnRulesLayout.Value> kinds = new ArrayList<>();
            for (String type : com.strata.domain.SpawnRulesOps.TYPE_CHOICES) {
                List<String> choices = com.strata.domain.SpawnRulesOps.valueChoices(type);
                kinds.add(choices != null
                        ? new com.strata.ui.screen.SpawnRulesLayout.Value(Layout.Kind.BUTTON, choices)
                        : new com.strata.ui.screen.SpawnRulesLayout.Value(Layout.Kind.FIELD, List.of()));
            }
            kinds.add(new com.strata.ui.screen.SpawnRulesLayout.Value(Layout.Kind.BUTTON,
                    List.of("Some Mod: The Extremely Long Named Ancient Structure")));
            return none(lines, "fit.17", atEverySize(frame -> {
                int n = com.strata.ui.screen.SpawnRulesLayout.visibleRows(frame);
                List<com.strata.ui.screen.SpawnRulesLayout.Value> shown = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    shown.add(kinds.get(i % kinds.size()));
                }
                return com.strata.ui.screen.SpawnRulesLayout.build(frame, font,
                        new com.strata.ui.screen.SpawnRulesLayout.Input(
                                "Strata — Conditions: " + "an_extremely_long_variant_name_".repeat(3),
                                com.strata.domain.SpawnRulesOps.TYPE_CHOICES, shown, true,
                                longText("Conditions for Some Mod: A Long Mob — Biome: somemod:long", 10),
                                null));
            }));
        });

        SelfTestHarness.check(lines, tally, "fit.49 the vegetation list fits at every size", () ->
                font != null && none(lines, "fit.49", atEverySize(frame ->
                        com.strata.ui.screen.ListScreenLayouts.vegetationList(frame, font,
                                "Strata — Vegetation: Biome: " + "somemod:an_extremely_long_biome_name ".repeat(2), true,
                                "1234 plant(s)", "No plants: the game data could not be read at game start"))));

        SelfTestHarness.check(lines, tally, "fit.18 the Equipment list with its longest note and every field tip fits",
                () -> {
            if (font == null) {
                return false;
            }
            String longestTip = java.util.Arrays.stream(com.strata.domain.EquipmentOps.EquipmentField.values())
                    .map(com.strata.domain.EquipmentOps::equipmentTip)
                    .max(java.util.Comparator.comparingInt(String::length)).orElse("");
            return none(lines, "fit.18", atEverySize(frame -> com.strata.ui.screen.EquipmentLayout.build(frame,
                    font, "Strata — Equipment: " + "an_extremely_long_variant_name_".repeat(3),
                    com.strata.ui.screen.EquipmentLayout.REMOVE, longestTip,
                    longText("3 own + 4 inherited entr(ies) — one slot is one draw;", 20), null,
                    "", false)));
        });

        longTitle = "Strata — Mobs: " + "Biome: somemod:an_extremely_long_biome_name ".repeat(2);
        longFooter = longText("1234 loot entries — Add: dropped as well · Replace: clears", 20);
        longEmpty = longText("No loot data for Some Mod: A Long Mob — Minecraft's data has none", 10);

        SelfTestHarness.check(lines, tally, "fit.19 the tier list fits at every size", () ->
                font != null && none(lines, "fit.19", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.tierList(f, font, longTitle, true))));

        SelfTestHarness.check(lines, tally, "fit.20 the ore list fits at every size", () ->
                font != null && none(lines, "fit.20", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.oreList(f, font, longTitle, true, longFooter, longEmpty))));

        SelfTestHarness.check(lines, tally, "fit.21 the biome list fits at every size, with each filter and assign", () ->
                font != null && none(lines, "fit.21", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.biomeList(f, font, longTitle,
                                List.of("All tiers", "Unassigned"),
                                "Add shown to an_extremely_long_tier_name_of_a_large_pack (1234)", true,
                                longFooter, longEmpty)))
                        && none(lines, "fit.21", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.biomeList(f, font, longTitle,
                                List.of("All tiers", "Unassigned"), null, false,
                                longFooter, longEmpty))));

        SelfTestHarness.check(lines, tally, "fit.22 the loot list fits at every size, with Remove or Deny entry", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (String remove : new String[]{"Remove", "Deny entry",
                    com.strata.ui.screen.ListLayout.manyLabel("Remove", "Deny entry", 40, 59)}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.lootList(f, font, longTitle,
                        remove, longFooter, longEmpty, "", false)));
            }
            return none(lines, "fit.22", found);
        });

        SelfTestHarness.check(lines, tally, "fit.51 the loot list with a group row fits at every size", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (String remove : new String[]{"Remove", "Deny entry",
                    com.strata.ui.screen.ListLayout.manyLabel("Remove", "Deny entry", 40, 59)}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.lootList(f, font, longTitle,
                        remove, longFooter, longEmpty,
                        longText("An Extremely Long Group Name ", 2), true)));
            }
            found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.lootList(f, font, longTitle,
                    "Remove", longFooter, longEmpty,
                    com.strata.ui.screen.GroupLayout.NO_GROUPS, false)));
            return none(lines, "fit.51", found);
        });

        SelfTestHarness.check(lines, tally, "fit.52 the equipment list with a group row fits at every size", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (String remove : new String[]{com.strata.ui.screen.EquipmentLayout.REMOVE,
                    com.strata.ui.screen.EquipmentLayout.DENY,
                    com.strata.ui.screen.EquipmentLayout.removeLabel(40, 59)}) {
                found.addAll(atEverySize(frame -> com.strata.ui.screen.EquipmentLayout.build(frame, font,
                        "Strata — Equipment: " + "an_extremely_long_variant_name_".repeat(3), remove,
                        longText("Deny blocks a piece with no row here (base mob equipment).", 20),
                        longText("3 own + 4 inherited entr(ies) — one slot is one draw;", 20),
                        "No equipment data for this variant's base entity",
                        longText("An Extremely Long Group Name ", 2), true)));
            }
            found.addAll(atEverySize(frame -> com.strata.ui.screen.EquipmentLayout.build(frame, font,
                    "Strata — Equipment: " + "an_extremely_long_variant_name_".repeat(3),
                    com.strata.ui.screen.EquipmentLayout.REMOVE, "Deny a piece with no row here.",
                    "3 own + 4 inherited entr(ies).", null,
                    com.strata.ui.screen.GroupLayout.NO_GROUPS, false)));
            return none(lines, "fit.52", found);
        });

        SelfTestHarness.check(lines, tally, "fit.23 the behaviors list fits at every size, from Client and for a mob",
                () -> font != null && none(lines, "fit.23", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.goalSetsList(f, font, longTitle,
                                true, longFooter)))
                        && none(lines, "fit.23", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.goalSetsList(f, font, longTitle,
                                true, true, true, longFooter))));

        SelfTestHarness.check(lines, tally, "fit.24 the AI list fits at every size, for a mob and an archetype", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (boolean goalSet : new boolean[]{false, true}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.goalsList(f, font, longTitle,
                        goalSet, longFooter, com.strata.model.Attacks.NONE.line(List.of(
                                new com.strata.model.GoalRow(true, 1, "HurtByTargetGoal", false, java.util.Map.of(),
                                        com.strata.model.GoalCondition.ALWAYS, true)), !goalSet, null).text())));
            }
            return none(lines, "fit.24", found);
        });

        SelfTestHarness.check(lines, tally, "fit.25 both scaling screens fit at every size", () ->
                font != null && none(lines, "fit.25", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.scaling(f, font, longTitle, false, "Player scaling: Off", null,
                                "Select a curve to edit, switch on or off, or remove it",
                                "scaled = base × (1 + per_level × level^exponent)   (level = player XP level)",
                                longFooter))) && none(lines, "fit.25", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.scaling(f, font, longTitle, true, "Mob scaling: Off", "Chunk scaling: Off",
                                "Select a curve to edit, switch on or off, or remove it",
                                "scaled = base × (1 + per_level × level^exponent × coefficient)", longFooter))));

        SelfTestHarness.check(lines, tally, "fit.26 the profiles fit at every size", () ->
                font != null && none(lines, "fit.26", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.profile(f, font, longTitle,
                                longText("Saved: a_profile_with_a_long_name", 10),
                                List.of(longText("Connected to a server with a long name (play.example.org). Its active profile: a_profile_with_a_long_name", 5),
                                        longText("Operator: yes. Activate also loads the profile onto the server. Unsaved edits are not sent", 5)),
                                longFooter))));

        SelfTestHarness.check(lines, tally, "fit.27 the picker fits at every size, with a long group", () ->
                font != null && none(lines, "fit.27", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.picker(f, font, longTitle,
                                List.of("All", "variant", "minecraft", "an_extremely_long_mod_id_here"))))
                && none(lines, "fit.27", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.picker(f, font, longTitle,
                                List.of("All", "variant", "minecraft", "an_extremely_long_mod_id_here"), true))));

        SelfTestHarness.check(lines, tally, "fit.57 the sounds and textures lists fit at every size", () ->
                font != null && none(lines, "fit.57", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.assetMaster(f, font, longTitle, "Sound", longFooter)))
                        && none(lines, "fit.57", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.assetMaster(f, font, longTitle, "Model", longFooter))));

        SelfTestHarness.check(lines, tally, "fit.44 the Settings sheet fits at every size, a switch and a number", () ->
                font != null && none(lines, "fit.44", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.settings(f, font, longTitle, List.of(),
                                longText("Players grow with their XP level, by the player curves in Scaling.", 4),
                                true, false, longFooter)))
                        && none(lines, "fit.44", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.settings(f, font, longTitle, List.of(), "", false, true,
                                longFooter))));

        SelfTestHarness.check(lines, tally,
                "fit.45 a dropdown's options fit at every size, under a top button and over a bottom one", () -> {
            if (font == null) {
                return false;
            }
            List<String> twelve = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                twelve.add(i == 5 ? "Distance from world spawn, a long option word here" : "Option " + i);
            }
            List<String> sixty = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                sixty.add("an_extremely_long_mod_id_number_" + i);
            }
            List<String> found = new ArrayList<>();
            for (List<String> words : List.of(twelve, sixty)) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ChoiceListLayout.build(f, font,
                        new com.strata.ui.Box(f.contentLeft(), 30, 120, 20), words, 0)));
                found.addAll(atEverySize(f -> com.strata.ui.screen.ChoiceListLayout.build(f, font,
                        new com.strata.ui.Box(f.contentRight() - 90, f.height() - 30, 90, 20), words, 99)));
            }
            return none(lines, "fit.45", found);
        });
        SelfTestHarness.check(lines, tally, "fit.46 the debug HUD fits at every size, with long lines", () ->
                font != null && none(lines, "fit.46", atEverySize(f ->
                        com.strata.ui.screen.DebugHudLayout.build(f, font,
                                com.strata.ui.screen.DebugHudLayout.lines(
                                        "An Extremely Long Modded Biome Name (some_mod:an_extremely_long_biome_id)",
                                        "a_tier_with_a_very_long_name", 125, 1.75, 1234, false)))));
    }

    private static void checksPart02(List<String> lines, int[] tally) {
        SelfTestHarness.check(lines, tally, "fit.47 the Player spawn screen fits at every size, with a long target", () ->
                font != null && none(lines, "fit.47", atEverySize(f ->
                        com.strata.ui.screen.PlayerSpawnLayout.build(f, font, "Strata — Player spawn",
                                "Now: the biome An Extremely Long Modded Biome Name (some_mod:an_extremely_long_biome)"))));
        SelfTestHarness.check(lines, tally, "fit.47b the custom asset screens fit at every size, as a texture and as a sound", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (boolean texture : new boolean[]{true, false}) {
                    found.addAll(atEverySize(f -> com.strata.ui.screen.CustomAssetLayout.build(f, font,
                            "Strata / Options / Re-import: an_extremely_long_custom_name_of_some_sort", texture,
                            com.strata.domain.CustomAssetsOps.KINDS, com.strata.domain.CustomAssetsOps.CATEGORIES,
                            List.of("Ogg Vorbis, stereo, 44100 Hz, 12.3 s. Stereo plays at one volume everywhere; use mono"
                                            + " for sounds that fade with distance",
                                    "That is Ogg Opus audio, not Ogg Vorbis. Minecraft plays only Ogg Vorbis: convert it to a .ogg file"),
                            com.strata.domain.CustomAssetsOps.where())));
            }
            for (boolean picked : new boolean[]{true, false}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.customAssets(f, font,
                        "Strata / Options / Custom textures & sounds", picked, longFooter,
                        "No custom textures or sounds yet. + Add custom... imports a PNG or an Ogg Vorbis file.")));
            }
            return none(lines, "fit.47b", found);
        });
        SelfTestHarness.check(lines, tally, "fit.48 the Batch screen fits at every size, every section, add and remove", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            List<String> groups = List.of("All mobs (123)", "Underground water creature (12)", "Water ambient (4)");
            for (var section : com.strata.ui.screen.FormScreenLayouts.BatchSection.values()) {
                for (boolean adds : new boolean[]{true, false}) {
                    for (List<String> values : java.util.Arrays.asList(List.of("not_clear", "thunder"), null)) {
                        found.addAll(atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.batch(f, font,
                                "Strata — Batch: Biome: somemod:the_extremely_long_named_biome_of_a_large_mod",
                                groups, com.strata.domain.BatchOps.FIELDS, List.of("Default", "On", "Off"),
                                section, adds, "An Extremely Long Modded Item Name of Some Sort",
                                List.of("mainhand", "offhand", "head", "chest", "legs", "feet"),
                                List.of("moon_phase", "structure", "distance"), values,
                                List.of("damage_immunity", "potion_on_hit", "regeneration_aura"),
                                "Pick a damage type...",
                                List.of("123 mob(s) of this list: Underground water creature in Biome: some_mod:long.",
                                        longText("Needs: The value must be a number or a range like 0-7", 6)),
                                true)));
                    }
                }
            }
            return none(lines, "fit.48", found);
        });
        SelfTestHarness.check(lines, tally, "fit.28 the Options menu fits at every size, with its top row", () ->
                font != null && none(lines, "fit.28", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.settings(f, font, "Strata — Options",
                                com.strata.ui.screen.ListScreenLayouts.menuTools(),
                                longText("Tiers and biomes change which mobs spawn.", 4), true, false, longFooter))));

        SelfTestHarness.check(lines, tally,
                "fit.29 help fits at every size: the Help list, and each section at the top and at the end", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            found.addAll(atEverySize(f -> com.strata.ui.screen.HelpLayout.index(f, font, "Strata — Help")));
            for (String section : com.strata.ui.screen.HelpLayout.sections()) {
                String title = "Strata — Help: " + section;
                found.addAll(atEverySize(f -> com.strata.ui.screen.HelpLayout.build(f, font, title, section, 0)));
                found.addAll(atEverySize(f -> com.strata.ui.screen.HelpLayout.build(f, font, title, section,
                        com.strata.ui.screen.HelpLayout.maxScroll(f, font, section))));
            }
            return none(lines, "fit.29", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.30 the mob list fits at every size: Global and scoped, long stat headings", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (boolean scoped : new boolean[]{false, true}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.MobListLayout.build(f, font, longTitle,
                        List.of("Global", "Tiers", "Biomes"), scoped ? 1 : 0, scoped,
                        List.of("Max health", "Attack knockback resistance", "Movement speed", "Armor toughness", "Follow range", "Luck"),
                        scoped ? "Remove from an_extremely_long_tier_name_of_a_large_pack (99)" : null, scoped,
                        !scoped, true, true, scoped, longFooter, longEmpty)));
            }
            return none(lines, "fit.30", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.30b the mob list's bar holds Mob caps and Special mobs; Mob caps and Goal sets are not placed on a scoped view", () -> {
            if (font == null) {
                return false;
            }
            com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
            var global = com.strata.ui.screen.MobListLayout.build(f, font, "t", List.of("Global", "Tiers", "Biomes"),
                    0, false, List.of(), null, false, true, true, false, false, "", null);
            var tier = com.strata.ui.screen.MobListLayout.build(f, font, "t", List.of("Global", "Tiers", "Biomes"),
                    1, true, List.of(), "Remove from t1 (0)", false, false, true, true, true, "", null);
            boolean ok = global.get("mobCaps") != null && global.get("mobCaps").active()
                    && "Special spawns".equals(global.get("special").text()) && global.get("special").active()
                    && tier.get("mobCaps") == null && tier.get("goalSets") == null
                    && tier.get("special").active() && tier.get("remove") != null
                    && global.get("goalSets") != null
                    && global.get("mobCaps").box().y() < global.get("search").box().y()
                    && global.get("goalSets").box().y() < global.get("search").box().y()
                    && tier.get("special").box().y() < tier.get("search").box().y()
                    && global.get("columns") == null
                    && global.get("data") != null && global.get("data").box().y() < global.get("search").box().y();
            if (!ok) {
                lines.add("    fit.30b the bar lacks a button, or Mob caps or Goal sets is placed on a scoped view");
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "fit.30d the mob list's stat columns are spread over the free width, as many as fit", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            List<String> three = List.of("Max health", "Attack damage", "Movement speed");
            for (int[] size : Fit.SIZES) {
                Frame f = Fit.frameFor(size);
                int room = com.strata.ui.screen.MobListLayout.columnRoom(f);
                int w = com.strata.ui.screen.MobListLayout.columnWidth(f, font, three);
                if (w > 0 && (w * three.size() > room || w > com.strata.ui.screen.MobListLayout.COLUMN_MAX_W
                        || w < Math.min(room / three.size(), com.strata.ui.screen.MobListLayout.COLUMN_MAX_W))) {
                    found.add(Fit.nameOf(size) + ": column " + w + " of room " + room);
                }
            }
            Frame wide = new Frame(960, 540);
            if (com.strata.ui.screen.MobListLayout.columnWidth(wide, font, three) < 150) {
                found.add("960 wide: the columns are not spread out");
            }
            return none(lines, "fit.30d", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.30c the mob list's Default mobs is on every tier and biome view and Replace on a one-scope view only, both grey without a world", () -> {
                    if (font == null) {
                        return false;
                    }
                    com.strata.ui.Frame f = new com.strata.ui.Frame(427, 240);
                    var tabs = List.of("Global", "Tiers", "Biomes");
                    var global = com.strata.ui.screen.MobListLayout.build(f, font, "t", tabs,
                            0, false, List.of(), null, false, true, true, false, false, "", null);
                    var tier = com.strata.ui.screen.MobListLayout.build(f, font, "t", tabs,
                            1, true, List.of(), "Remove from t1 (0)", false, false, true, true, true, "", null);
                    var spent = com.strata.ui.screen.MobListLayout.build(f, font, "t", tabs,
                            1, true, List.of(), "Remove from t1 (0)", false, false, true, false, true, "", null);
                    var grouped = com.strata.ui.screen.MobListLayout.build(f, font, "t", tabs,
                            1, true, List.of(), null, false, false, false, false, false, "", null);
                    boolean ok = global.get("fill") == null
                            && grouped.get("fill") != null && grouped.get("replace") == null
                            && none(lines, "fit.30c", Fit.check(grouped))
                            && grouped.get("special") == null
                            && tier.get("fill") != null && tier.get("fill").active()
                            && "Add default mobs".equals(tier.get("fill").text())
                            && tier.get("replace") != null && global.get("replace") == null
                            && !spent.get("fill").active() && !spent.get("replace").active()
                            && none(lines, "fit.30c", Fit.check(tier));
                    if (!ok) {
                        lines.add("    fit.30c Default mobs or Replace is missing, or live without one scope");
                    }
                    return ok;
                });

        SelfTestHarness.check(lines, tally,
                "fit.52 the override sheet's list with its editor panel fits at every size", () -> {
                    if (font == null) {
                        return false;
                    }
                    List<String> found = new ArrayList<>(atEverySize(frame -> com.strata.ui.screen.OverrideLayouts.listWithEditor(
                            frame, font, "Strata — Recipes",
                            longText("1300 shown, 40 changed. Grey: switched off.", 3), true, "Spacing", true,
                            List.of("Custom structures"),
                            List.of("Cook time (ticks)  (cookingtime)", "The game's: 200   Yours: 100"),
                            "Fields: " + "some_mod:an_extremely_long_recipe_id_".repeat(2))));
                    found.addAll(atEverySize(frame -> com.strata.ui.screen.OverrideLayouts.rules(frame, font,
                            "Strata — Game rules",
                            List.of("Select a rule, type its value, Set value. Yellow rules are changed.", ""),
                            longText("51 rule(s). Set on the world at its next start or /reload.", 2))));
                    return none(lines, "fit.52", found);
                });

        SelfTestHarness.check(lines, tally,
                "fit.51 the replacements list fits at every size", () -> {
                    if (font == null) {
                        return false;
                    }
                    List<String> found = new ArrayList<>(atEverySize(frame -> com.strata.ui.screen.ReplacementsLayout.build(frame, font,
                            "Strata — Replacements: " + "An Extremely Long Biome Name ".repeat(2),
                            "Some Mod: An Extremely Long Mob Name", "Some Mod: Another Extremely Long Mob",
                            com.strata.domain.ReplacementOps.SOURCES,
                            longText("To add: No mob some_mod:an_extremely_long_mob_id in this game", 3), true)));
                    return none(lines, "fit.51", found);
                });

        List<String> modes = java.util.Arrays.stream(com.strata.model.OverrideMode.values())
                .map(com.strata.model.OverrideMode::lower).toList();

        SelfTestHarness.check(lines, tally, "fit.32 the tier form fits at every size, new and existing, with none, few, or 400 biomes", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (boolean exists : new boolean[]{false, true}) {
                for (List<String> biomes : List.of(List.<String>of(), List.of("Plains", "Forest"),
                        java.util.stream.IntStream.range(0, 400)
                                .mapToObj(i -> "Some Mod: An Extremely Long Biome Name " + i).toList())) {
                    found.addAll(atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.tierEdit(f, font, longTitle,
                            exists, modes, com.strata.domain.TierOps.OTHER_SPAWN_CHOICES, biomes, longFooter,
                            longText("12 biome(s) use this tier", 10))));
                }
            }
            return none(lines, "fit.32", found);
        });

        SelfTestHarness.check(lines, tally, "fit.33 the biome form fits at every size, with a long tier and a note", () ->
                font != null && none(lines, "fit.33", atEverySize(f ->
                        com.strata.ui.screen.FormScreenLayouts.biomeEdit(f, font, longTitle,
                                "Tier: " + "a_tier_with_an_extremely_long_name_".repeat(2),
                                "Level override (blank = inherit: 12 from " + "a_long_tier_name_".repeat(3) + ")",
                                List.of("inherit", "on", "off"),
                                longText("3 override(s) diverge from tier a_long_tier_name", 10)))));

        SelfTestHarness.check(lines, tally, "fit.34 the loot entry form fits at every size, every mode and condition", () ->
                font != null && none(lines, "fit.34", atEverySize(f ->
                        com.strata.ui.screen.FormScreenLayouts.lootEntry(f, font, longTitle,
                                "Some Mod: An Extremely Long Modded Item Name Of Great Length",
                                com.strata.domain.LootOps.MODE_CHOICES, com.strata.domain.LootOps.CONDITION_CHOICES,
                                longFooter, longText("Chance must be 0 to 100 when the mode is Add", 10)))));

        SelfTestHarness.check(lines, tally, "fit.35 the scaling curve form fits at every size, mob and player, with both previews", () ->
                font != null && none(lines, "fit.35", atEverySize(f ->
                        com.strata.ui.screen.FormScreenLayouts.scalingEdit(f, font, longTitle,
                                "Some Mod: An Extremely Long Attribute Name",
                                "Preview on: Some Mod: An Extremely Long Creature Name",
                                longText("Mob Lv 1: +0%  ·  Lv 5: +10%  ·  Lv 10: +20%  ·  Lv 20: +40%", 20),
                                longText("Zombie, Max Health 20: Lv 1: 22  ·  Lv 5: 30  ·  Lv 10: 40", 20),
                                "Mob scaling")))
                        && none(lines, "fit.35", atEverySize(f ->
                        com.strata.ui.screen.FormScreenLayouts.scalingEdit(f, font, longTitle,
                                "Some Mod: An Extremely Long Attribute Name", null,
                                longText("XP Lv 1: +0%  ·  Lv 5: +10%  ·  Lv 10: +20%  ·  Lv 20: +40%", 20),
                                null, "Player scaling"))));

        SelfTestHarness.check(lines, tally, "fit.36 the value sheets fit at every size, the loot table with its search", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (int valueW : new int[]{180, 100}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.valueSheet(f, font, longTitle,
                        valueW, longText("Game value: 0.2300000041723251. Applies from the next world", 30),
                        longFooter, longEmpty)));
            }
            found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.valueSheet(f, font, longTitle,
                    180, longText("Game value: 0.23. Applies from the next world load.", 30), longFooter,
                    longEmpty, true)));
            return none(lines, "fit.36", found);
        });

        SelfTestHarness.check(lines, tally, "fit.37 the Client menu fits at every size, at the top and at the end", () ->
                font != null && none(lines, "fit.37", atEverySize(f ->
                        com.strata.ui.screen.MenuLayouts.client(f, font, "Strata",
                                longText("Offline: 64 biomes from the game layer (read at game", 10),
                                longText("Unsaved: edits reset at the next launch. Save them", 10)))));

        SelfTestHarness.check(lines, tally, "fit.37c the item list fits at every size", () -> {
            if (font == null) {
                return false;
            }
            return none(lines, "fit.37c", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.itemList(f, font,
                    longTitle, true, "Remove (12)", true, longFooter, longEmpty)))
                    && none(lines, "fit.37c", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.itemList(f, font,
                    longTitle, true, "Remove (12)", true, longFooter, longEmpty,
                    List.of("Max stack size", "Durability", "Attack damage (mainhand)", "Default mining speed", "Nutrition", "Saturation"))));
        });

        SelfTestHarness.check(lines, tally, "fit.37d the item variant screens fit at every size", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.variantSheet(f, font, longTitle,
                    longText("Attack damage (mainhand): While worn or held in the mainhand slot, it adds", 4),
                    longFooter, longEmpty)));
            for (boolean exists : new boolean[]{false, true}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.itemVariantEdit(f, font, longTitle,
                        exists, "Some Mod: An Extremely Long Item Name", com.strata.domain.ItemVariantOps.RARITY_CHOICES,
                        com.strata.model.ItemVariant.GLINTS, com.strata.domain.ItemVariantOps.YES_NO, longFooter)));
            }
            return none(lines, "fit.37d", found);
        });

        SelfTestHarness.check(lines, tally, "fit.37e the Special mobs list fits at every size", () ->
                font != null && none(lines, "fit.37e", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.specialMobsList(f, font, longTitle,
                                "Special mob spawns: Off", true, longFooter))));

        SelfTestHarness.check(lines, tally, "fit.37f the block list fits at every size", () ->
                font != null && none(lines, "fit.37f", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.blockList(f,
                        font, longTitle, true, "Remove (12)", true, longFooter, longEmpty)))
                        && none(lines, "fit.37f", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.blockList(f,
                        font, longTitle, true, "Remove (12)", true, longFooter, longEmpty,
                        List.of("Needs the correct tool", "Blast resistance", "Lava sets it on fire", "Hardness", "Tool level", "Light level"))))
                        && none(lines, "fit.37f", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.blockSheet(f,
                        font, longTitle, longText("Tool: the tool that mines it fast", 6), true, true, longEmpty))));

        SelfTestHarness.check(lines, tally, "fit.37g the block variant screens fit at every size", () ->
                font != null && none(lines, "fit.37g", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.blockVariantEdit(f,
                        font, longTitle, true, "Reinforced Deepslate Brick Stairs", longFooter)))
                        && none(lines, "fit.37g", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.blockVariantEdit(f,
                        font, longTitle, false, "Pick...", longFooter))));

        SelfTestHarness.check(lines, tally, "fit.37p the projectile list and editor fit at every size", () ->
                font != null && none(lines, "fit.37p", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.projectileList(f,
                        font, longTitle, true)))
                        && none(lines, "fit.37p", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.projectileEdit(f,
                        font, longTitle, true, "Some Mod: An Extremely Long Projectile Name", longFooter)))
                        && none(lines, "fit.37p", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.projectileEdit(f,
                        font, longTitle, false, "Pick...", longFooter))));
        SelfTestHarness.check(lines, tally, "fit.37w the World settings editor fits at every size, with a world and without", () ->
                font != null && none(lines, "fit.37w", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.worldSettings(f,
                        font, longTitle, true, longFooter)))
                        && none(lines, "fit.37w", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.worldSettings(f,
                        font, longTitle, true, com.strata.domain.WorldSettingsOps.NO_WORLD))));


        SelfTestHarness.check(lines, tally, "fit.37s the custom structure list and editor fit at every size", () ->
                font != null && none(lines, "fit.37s", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.customStructureList(f,
                        font, longTitle, true)))
                        && none(lines, "fit.37s", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.customStructureEdit(f,
                        font, longTitle, true, longFooter, longFooter)))
                        && none(lines, "fit.37s", atEverySize(f -> com.strata.ui.screen.FormScreenLayouts.customStructureEdit(f,
                        font, longTitle, false, "The file: 12 x 9 x 14 blocks, 840 placed.", longFooter))));

        SelfTestHarness.check(lines, tally, "fit.37h the item sheet with its crafting grid fits at every size", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (boolean isNew : new boolean[]{false, true}) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.itemSheet(f, font, longTitle,
                        longText("Attack damage: what it does", 6), longEmpty, "minecraft:a_long_recipe_name, 12 of 99",
                        isNew, true, true, true, true, "Restore recipe", true, true)));
            }
            return none(lines, "fit.37h", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.38 every menu is ONE column of narrow buttons, top to bottom, centred, never stretched", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (int[] size : Fit.SIZES) {
                Frame f = Fit.frameFor(size);
                java.util.Map<String, Layout> menus = new java.util.LinkedHashMap<>();
                menus.put("client", com.strata.ui.screen.MenuLayouts.client(f, font, "t", "s", "p"));
                for (var menu : menus.entrySet()) {
                    Layout layout = menu.getValue();
                    int centre = f.contentLeft() + f.contentWidth() / 2;
                    int previousBottom = Integer.MIN_VALUE;
                    for (int i = 0; layout.get("entry" + i) != null; i++) {
                        Box b = layout.get("entry" + i).box();
                        String where = Fit.nameOf(size) + " " + menu.getKey() + " entry" + i + ": ";
                        if (b.w() > com.strata.ui.screen.MenuLayouts.MENU_W) {
                            found.add(where + "wider than a menu button (" + b.w() + ")");
                        }
                        if (Math.abs(b.x() + b.w() / 2 - centre) > 1) {
                            found.add(where + "not centred");
                        }
                        if (b.y() < previousBottom) {
                            found.add(where + "not below the one above it");
                        }
                        previousBottom = b.bottom();
                    }
                }
            }
            return none(lines, "fit.38", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.38b NOTHING LOST: every screen the old World,"
                        + " Players, World generation and Scaling menus and the Items, Blocks, Options and Scaling buttons opened is a Section entry", () -> {
            var s = com.strata.ui.screen.Sections.ENTRIES;
            java.util.Map<String, List<String>> was = java.util.Map.of(
                    "World", List.of("Tiers", "Biomes", "Mobs", "Structures", "Dimensions", "Terrain", "Features"),
                    "Items", List.of("Items", "Loot tables", "Chest loot"),
                    "Blocks", List.of("Blocks", "Ores", "Vegetation", "Replacements"),
                    "Options", List.of("Settings", "World settings", "Game rules", "Damage types", "Player spawn",
                            "Player base stats", "World types", "Advancements", "Chat types", "Test mode",
                            "Custom textures & sounds"),
                    "Scaling", List.of("Mob scaling", "Player scaling"));
            List<String> lost = new ArrayList<>();
            was.forEach((sec, list) -> list.forEach(e -> {
                if (s.get(sec) == null || !s.get(sec).contains(e)) {
                    lost.add(sec + " > " + e);
                }
            }));
            boolean ok = lost.isEmpty() && com.strata.ui.screen.ListScreenLayouts.menuTools().isEmpty()
                    && s.values().stream().allMatch(l -> new java.util.HashSet<>(l).size() == l.size());
            if (!ok) {
                lines.add("    fit.38b lost: " + lost);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "fit.38c Blocks has Ores, Vegetation and Replacements on top and no + Add variant or Columns... button;"
                        + " Items no Columns...; Biomes has one Data... menu", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (int[] size : Fit.SIZES) {
                Frame f = Fit.frameFor(size);
                Layout blocks = com.strata.ui.screen.ListScreenLayouts.blockList(f, font, "t", true, "Remove", true, "f", null);
                Layout items = com.strata.ui.screen.ListScreenLayouts.itemList(f, font, "t", true, "Remove", true, "f", null);
                Layout biomes = com.strata.ui.screen.ListScreenLayouts.biomeList(f, font, "t", List.of("All tiers"), null,
                        false, "f", null);
                Layout fromTier = com.strata.ui.screen.ListScreenLayouts.biomeList(f, font, "t", List.of("All tiers"),
                        "Add shown to Deep", true, "f", null);
                String at = Fit.nameOf(size) + ": ";
                for (String id : com.strata.ui.screen.ListScreenLayouts.BLOCK_TOOLS) {
                    if (blocks.get(id) == null || blocks.get(id).box().y() >= blocks.get("list").box().y()) {
                        found.add(at + "Blocks lacks " + id + " above its list");
                    }
                }
                if (blocks.get("add") != null || blocks.get("columns") != null || items.get("columns") != null) {
                    found.add(at + "an Add variant or Columns... button is placed");
                }
                if (biomes.get("data") == null || biomes.get("tags") != null || fromTier.get("data") != null) {
                    found.add(at + "Biomes' Data... menu is wrong");
                }
                Layout.Element last = blocks.get("data");
                if (last != null && last.box().y() == blocks.get("search").box().y()
                        && last.box().right() != f.contentRight()) {
                    found.add(at + "Data... is not at the right edge");
                }
            }
            return none(lines, "fit.38c", found);
        });

        SelfTestHarness.check(lines, tally,
                "fit.38d Ores, Vegetation and Replacements with their Blocks tabs fit at every size", () -> {
            if (font == null) {
                return false;
            }
            return none(lines, "fit.38d", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.oreList(f, font,
                    longTitle, true, longFooter, longEmpty)))
                    && none(lines, "fit.38d", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.vegetationList(f,
                    font, longTitle, true, longFooter, longEmpty)))
                    && none(lines, "fit.38d", atEverySize(f -> com.strata.ui.screen.ListScreenLayouts.blockSwapList(f,
                    font, longTitle, "Deepslate Redstone Ore", "Disabled (air)", true, longFooter, longEmpty)))
                    && none(lines, "fit.38d", atEverySize(f -> com.strata.ui.screen.OverrideLayouts.listWithEditor(f, font,
                    longTitle, longFooter, true, null, false, List.of("Chest loot"),
                    List.of("a", "b"), longTitle)))
                    && none(lines, "fit.38d", atEverySize(f -> com.strata.ui.screen.OverrideLayouts.rules(f, font,
                    longTitle, List.of("a", "b"), longFooter)));
        });

        SelfTestHarness.check(lines, tally, "fit.39 test mode fits at every size, with a long preview", () -> {
            if (font == null) {
                return false;
            }
            List<String> preview = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                preview.add("Some Mod: A Long Attribute Name " + i + ": +1234.5%");
            }
            return none(lines, "fit.39", atEverySize(f -> com.strata.ui.screen.MenuLayouts.testMode(f, font,
                    "Strata — Test mode", "Some Mod: An Extremely Long Entity Name That Mods Give",
                    "At level 50:", preview, longText("Sent /strata test spawn — the result", 10),
                    "Note: /strata test spawn uses the same pipeline as natural spawns")));
        });

    }

    private static void checksPart03(List<String> lines, int[] tally) {
        SelfTestHarness.check(lines, tally, "fit.40 every goal's form fits at every size", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            List<com.strata.model.GoalType.Type> types = new ArrayList<>(com.strata.model.GoalType.all());
            types.add(null);
            for (com.strata.model.GoalType.Type type : types) {
                for (String f : atEverySize(frame -> com.strata.ui.screen.MenuLayouts.goal(frame, font,
                        "Strata — Goal: " + (type == null ? "the mob's own" : type.goal()),
                        com.strata.ui.screen.MenuLayouts.goalRows(type, key -> "any AbstractIllagerLongName", true),
                        "The mob's own goal: its priority, condition, and switch can change."))) {
                    found.add((type == null ? "own" : type.goal()) + " " + f);
                }
            }
            return none(lines, "fit.40", found);
        });


        SelfTestHarness.check(lines, tally,
                "fit.41 a list row fits in itself at every size: columns, a chance cell, every depth", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            String label = "▼ Some Mod: An Extremely Long Entity Name [t1:an_extremely_long_variant_name]";
            String sub = longText("game  spawns naturally, weight up to 100  [edited]", 10);
            List<String> headings = List.of("Max health", "Attack knockback resistance", "Movement speed");
            for (int[] size : Fit.SIZES) {
                Frame f = Fit.frameFor(size);
                Box list = new Box(f.contentLeft(), 40, f.contentWidth(), 150);
                int left = com.strata.ui.screen.ListLayout.rowLeft(list);
                int width = com.strata.ui.screen.ListLayout.rowWidth(list);
                int columnW = com.strata.ui.screen.MobListLayout.columnWidth(f, font, headings);
                for (int height : new int[]{20, 22, 24}) {
                    for (int indent = 0; indent <= 3; indent++) {
                        List<com.strata.ui.screen.ListRowLayout.Row> rows = List.of(
                                new com.strata.ui.screen.ListRowLayout.Row(indent, label, sub,
                                        List.of("1234.56", "0.0000001", "123456789.5"), columnW, null),
                                new com.strata.ui.screen.ListRowLayout.Row(indent, label, sub, List.of(), 0, "100%"),
                                new com.strata.ui.screen.ListRowLayout.Row(indent, label, sub, List.of(), 0, null));
                        for (com.strata.ui.screen.ListRowLayout.Row row : rows) {
                            Layout layout = new Layout(f, font);
                            com.strata.ui.screen.ListRowLayout.add(layout, 50, left, width, height, row);
                            if (row.chance() != null) {
                                layout.add(layout.fitted("heading0", left + width - com.strata.ui.screen.ListRowLayout.CHANCE_W / 2
                                        - com.strata.ui.screen.ListRowLayout.CHANCE_GAP, 38, "Chance %",
                                        com.strata.ui.screen.ListRowLayout.CHANCE_W));
                            }
                            Box rowBox = new Box(left, 50, width, height);
                            String where = Fit.nameOf(size) + " h" + height + " indent " + indent
                                    + (row.chance() != null ? " chance" : row.columnW() > 0 ? " columns" : "") + ": ";
                            for (String problem : Fit.check(layout)) {
                                found.add(where + problem);
                            }
                            for (Layout.Element e : layout.elements()) {
                                if (!e.box().inside(rowBox) && !e.id().equals("heading0")) {
                                    found.add(where + e.id() + " outside its row " + e.box());
                                }
                            }
                        }
                    }
                }
            }
            return none(lines, "fit.41", found);
        });

        SelfTestHarness.check(lines, tally, "fit.42 the confirm dialog fits at every size, a long message too", () -> {
            if (font == null) {
                return false;
            }
            List<String> messages = List.of("Delete profile 'x'? This cannot be undone.",
                    "Rename 'a' to 'b'?\n12 row(s) will be updated across 5 tables:\n  3 biome link(s), 4 mob spawn(s),"
                            + "\n  2 ore(s), 1 condition(s),\n  2 loot entries.",
                    longText("A message far too long for any screen:", 400));
            List<String> found = new ArrayList<>();
            for (String message : messages) {
                found.addAll(atEverySize(f -> com.strata.ui.screen.ConfirmLayout.build(f, font, message)));
            }
            return none(lines, "fit.42", found);
        });

        SelfTestHarness.check(lines, tally, "fit.53 a text field with no title is found, one titled above, beside, or by its column is not", () -> {
            if (font == null) {
                return false;
            }
            Layout bare = form(small, font, new Form(3, false, true));
            Box last = bare.get("field2").box();
            bare.add(Layout.control("bare", Layout.Kind.FIELD,
                    new Box(last.x() + last.w() + 20, last.y(), 60, Grid.CTRL_H), ""));
            boolean caught = catches(lines, "fit.53", bare, "field without a title: field 'bare'");
            Layout titled = form(small, font, new Form(3, false, true));
            Box at = titled.get("field2").box();
            int y = at.y();
            titled.add(titled.text("label.besideField", at.x() + at.w() + 10, y + 5, "Beside", 40));
            titled.add(Layout.control("besideField", Layout.Kind.FIELD,
                    new Box(at.x() + at.w() + 54, y, 40, Grid.CTRL_H), ""));
            boolean ok = none(lines, "fit.53", Fit.check(titled));
            return caught && ok;
        });

        SelfTestHarness.check(lines, tally, "fit.54 every text field of the tier form, a search, and a goal's value has its title, at every size", () -> {
            if (font == null) {
                return false;
            }
            List<String> found = new ArrayList<>();
            for (int[] size : Fit.SIZES) {
                Frame f = Fit.frameFor(size);
                String where = Fit.nameOf(size) + ": ";
                Layout tier = com.strata.ui.screen.FormScreenLayouts.tierEdit(f, font, "t", true,
                        List.of("Add", "Replace"), List.of("Default", "On", "Off"), List.of(), null, null);
                for (String id : new String[]{"name", "level", "coefficient"}) {
                    if (tier.get(id).kind() != Layout.Kind.FIELD
                            || tier.elements().stream().noneMatch(t -> t.kind() == Layout.Kind.TEXT && Fit.titles(t, tier.get(id)))) {
                        found.add(where + "tier " + id + " has no title");
                    }
                }
                Layout tiers = com.strata.ui.screen.ListScreenLayouts.tierList(f, font, "t", true);
                if (!"Search".equals(tiers.textOf("label.search"))) {
                    found.add(where + "the tier list's search has no title");
                }
            }
            return none(lines, "fit.54", found);
        });

        SelfTestHarness.check(lines, tally, "fit.55 the formulas form fits at every size, with long formulas and errors", () -> {
            List<String> ids = java.util.Arrays.stream(com.strata.model.ScalingFormulas.Kind.values()).map(k -> k.id).toList();
            List<String> labels = java.util.Arrays.stream(com.strata.model.ScalingFormulas.Kind.values()).map(k -> k.label).toList();
            List<String> previews = ids.stream().map(i -> longText("Lv 1: 0.15  ·  Lv 5: 0.6  ·  Lv 100: 12.3", 20)).toList();
            return font != null && none(lines, "fit.55", atEverySize(f ->
                    com.strata.ui.screen.FormScreenLayouts.formulas(f, font, longTitle, ids, labels, previews, "Scaling")));
        });

        SelfTestHarness.check(lines, tally, "fit.56 a mob's Biomes list fits at every size, full and empty", () ->
                font != null && none(lines, "fit.56", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.mobBiomes(f, font, longTitle, null)))
                        && none(lines, "fit.56", atEverySize(f ->
                        com.strata.ui.screen.ListScreenLayouts.mobBiomes(f, font, longTitle, longEmpty))));

    }

    /** A long run of words, for text that must wrap or be cut. */
    private static String longText(String start, int words) {
        StringBuilder s = new StringBuilder(start);
        for (int i = 0; i < words; i++) {
            s.append(" word").append(i);
        }
        return s.toString();
    }

    /** The mob editor with the longest text each part can show; the last flag turns on Trades. */
    private static com.strata.ui.screen.MobEditLayout.Input mobEditor(boolean isNew, boolean variant,
                                                                          boolean ownEntity) {
        return new com.strata.ui.screen.MobEditLayout.Input(
                "Strata — Variant: " + "an_extremely_long_variant_name_".repeat(3),
                isNew, !isNew, !isNew, !isNew, true,
                "Scope: Biome", "Target: somemod:the_extremely_long_named_biome_of_a_large_mod",
                "Biome: somemod:the_extremely_long_named_biome_of_a_large_mod",
                "Some Mod: An Extremely Long Entity Name That Mods Give Their Mobs",
                "Behavior: a_very_long_goal_set_name_from_a_mod_family",
                goalLines(),
                "Variant name (a name turns this mob into a variant)",
                variant, false,
                longText("Loot: rotten flesh, iron ingot, carrot, potato,", 40),
                longText("Equipment: iron sword, iron shovel, leather helmet,", 40), ownEntity,
                com.strata.ui.Layout.choiceText("Scaling", "Default"));
    }

    /** A long AI as the mob editor's box shows it: more lines than the box holds. */
    private static List<String> goalLines() {
        List<String> out = new ArrayList<>(List.of("Target goals (who it attacks):"));
        for (int i = 0; i < 30; i++) {
            out.add("  " + i + "  nearest attackable target: any abstract illager with a long name, only at night");
        }
        out.add("Goals (what it does):");
        return out;
    }
}
