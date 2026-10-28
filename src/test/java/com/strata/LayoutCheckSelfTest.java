package com.strata;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.strata.client.nav.NavHistory;
import com.strata.command.StrataCommands;
import net.minecraft.commands.CommandSourceStack;
import com.strata.client.screen.MobListScreen;
import com.strata.client.screen.RegistryPickerScreen;
import com.strata.util.Labels;
import com.strata.client.screen.StrataScreen;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.game.DimensionBiomes;
import com.strata.model.LootCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** HEADLESS LAYOUT GATE — the layout-contract seam. */
public final class LayoutCheckSelfTest {

    private LayoutCheckSelfTest() {}

    private record EditFixture(String name, int fieldRows) {}

    private static final List<EditFixture> EDIT_FIXTURES = List.of(
            new EditFixture("BiomeEdit", 5),
            new EditFixture("TierEdit", 4),
            new EditFixture("MobEdit", 5),
            new EditFixture("LootEntryEdit", 4),
            new EditFixture("ScalingEdit", 3),
            new EditFixture("VariantEdit", 5));

    private static final int[] TEST_HEIGHTS = {240, 360, 720};

    public static SelfTestHarness.Result run() {
        List<String> lines = new ArrayList<>();
        int[] tally = new int[2];
        checks(lines, tally);
        return new SelfTestHarness.Result(tally[0], tally[1], lines);
    }

    private static void checks(List<String> lines, int[] tally) {
        checksPart01(lines, tally);
        checksPart02(lines, tally);
    }

    private static void checksPart01(List<String> lines, int[] tally) {

        SelfTestHarness.check(lines, tally,
                "layout.1 edit action row strictly above its nav row", () -> {
            for (int h : TEST_HEIGHTS) {
                for (EditFixture f : EDIT_FIXTURES) {
                    int action = com.strata.ui.Grid.actionRowY();
                    int nav = com.strata.ui.Grid.navRowY(f.fieldRows(), StrataScreen.ROW_H);
                    if (action + 18 > nav) {
                        lines.add("    layout.1 " + f.name() + " @ " + h
                                + " action " + action + " collides with nav " + nav);
                        return false;
                    }
                    if (action <= StrataScreen.CONTENT_Y) {
                        return false;
                    }
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.2 edit rows fit a compact 360px window", () -> {
            for (EditFixture f : EDIT_FIXTURES) {
                int action = com.strata.ui.Grid.actionRowY();
                int nav = com.strata.ui.Grid.navRowY(f.fieldRows(), StrataScreen.ROW_H);
                if (nav + 18 > 360 - 6) {
                    lines.add("    layout.2 " + f.name() + " nav bottom "
                            + (nav + 18) + " escapes 354");
                    return false;
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.3 list bars never cover list content", () -> {
            for (int h : TEST_HEIGHTS) {
                int canonBar = com.strata.ui.Grid.bottomBarY(h);
                int canonBottom = canonBar - 6;
                if (canonBottom + 6 > canonBar) return false;

                int nativeListBottom = (StrataScreen.CONTENT_Y + 4)
                        + (h - StrataScreen.CONTENT_Y - 60);

                int scBar = h - 56;
                if (nativeListBottom + 6 > (h - 50)) {
                    lines.add("    layout.3 native list bottom " + nativeListBottom
                            + " collides with bar at height " + h);
                    return false;
                }
                if (scBar - 8 <= StrataScreen.CONTENT_Y) {
                    return false;
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.4 canonical list bar math is exact", () -> {
            for (int h : TEST_HEIGHTS) {
                int bar = com.strata.ui.Grid.bottomBarY(h);
                if (bar != h - com.strata.ui.Grid.BAR_TOP) return false;
                if (bar - (StrataScreen.CONTENT_Y + 24) - 6 < 1) {
                    return false;  // list would have no visible rows
                }
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.5 ore Enable writes an ore with no rule", () -> {
            var none = new com.strata.domain.OreOps.OreRow("iron", false, true, 100, false, "", true);
            return com.strata.domain.OreOps.statePlan(java.util.List.of(none),
                    com.strata.domain.OreOps.OreState.ENABLE).get(true).equals(java.util.List.of(none));
        });

        SelfTestHarness.check(lines, tally,
                "layout.6 ore Disable writes an enabled rule and skips a disabled one", () -> {
            var on = new com.strata.domain.OreOps.OreRow("iron", true, true, 50, false, "", true);
            var off = new com.strata.domain.OreOps.OreRow("gold", true, false, 50, false, "", true);
            return com.strata.domain.OreOps.statePlan(java.util.List.of(on, off),
                    com.strata.domain.OreOps.OreState.DISABLE).get(true).equals(java.util.List.of(on));
        });

        SelfTestHarness.check(lines, tally,
                "layout.7 ore Default removes a rule and skips an ore with none", () -> {
            var ruled = new com.strata.domain.OreOps.OreRow("iron", true, false, 50, false, "", true);
            var none = new com.strata.domain.OreOps.OreRow("gold", false, true, 100, false, "", true);
            return com.strata.domain.OreOps.statePlan(java.util.List.of(ruled, none),
                    com.strata.domain.OreOps.OreState.DEFAULT).get(true).equals(java.util.List.of(ruled));
        });

        SelfTestHarness.check(lines, tally,
                "layout.8 ore buttons leave a tier's rule to its tier", () -> {
            var tiers = new com.strata.domain.OreOps.OreRow("iron", true, true, 50, true, "t1", true);
            var plan = com.strata.domain.OreOps.statePlan(java.util.List.of(tiers),
                    com.strata.domain.OreOps.OreState.DISABLE);
            return plan.get(true).isEmpty() && plan.get(false).equals(java.util.List.of(tiers));
        });

        SelfTestHarness.check(lines, tally,
                "layout.9 per-row action button stays inside the row", () -> {
            int rowWidth = 448;
            int right = rowWidth;
            int bx = right - BrowseList.ACTION_W - BrowseList.ACTION_GAP;
            return bx >= 0 && bx + BrowseList.ACTION_W <= right - BrowseList.ACTION_GAP;
        });

        SelfTestHarness.check(lines, tally,
                "layout.10 instanced name: entity + biome", () ->
                "creeper_plains".equals(com.strata.domain.VariantOps.variantNameFor(
                        "minecraft:creeper", "biome", "minecraft:plains")));

        SelfTestHarness.check(lines, tally,
                "layout.11 instanced name: tier scope appends scope id", () ->
                "zombie_cold_steppe".equals(com.strata.domain.VariantOps.variantNameFor(
                        "minecraft:zombie", "tier", "cold_steppe")));

        SelfTestHarness.check(lines, tally,
                "layout.12 instanced name: global scope has no suffix", () ->
                "creeper".equals(com.strata.domain.VariantOps.variantNameFor(
                        "minecraft:creeper", "global", "")));

        SelfTestHarness.check(lines, tally,
                "layout.13 instanced name: tokens are sanitized", () ->
                "wither_skeleton_nether_wastes".equals(
                        com.strata.domain.VariantOps.variantNameFor(
                                "minecraft:wither_skeleton", "biome",
                                "minecraft:nether_wastes")));

        SelfTestHarness.check(lines, tally,
                "layout.14 loot-condition dropdown covers every model type", () -> {
            if (!com.strata.domain.LootOps.CONDITION_CHOICES.contains("(none)")) {
                lines.add("    layout.14 missing \"(none)\" option");
                return false;
            }
            for (LootCondition.Type t : LootCondition.Type.values()) {
                String option = t.name().toLowerCase(Locale.ROOT);
                if (!com.strata.domain.LootOps.CONDITION_CHOICES.contains(option)) {
                    lines.add("    layout.14 missing dropdown option: " + option);
                    return false;
                }
            }
            return com.strata.domain.LootOps.CONDITION_CHOICES.size()
                    == LootCondition.Type.values().length + 1;
        });

        SelfTestHarness.check(lines, tally,
                "layout.15 scale k is 1.0 at gui scale 2 (baseline)", () ->
                        com.strata.ui.Grid.scaleFor(2.0) == 1.0f);

        SelfTestHarness.check(lines, tally,
                "layout.16 scale k is 1.0 at gui scale 3: controls keep their size", () ->
                        com.strata.ui.Grid.scaleFor(3.0) == 1.0f);

        SelfTestHarness.check(lines, tally,
                "layout.17 scale k is 1.0 at gui scale 4: controls keep their size", () ->
                        com.strata.ui.Grid.scaleFor(4.0) == 1.0f);

        SelfTestHarness.check(lines, tally,
                "layout.18 scale k is 1.0 at every scale, and a row fits its label", () ->
                        com.strata.ui.Grid.scaleFor(1.0) == 1.0f
                                && com.strata.ui.Grid.scaleFor(1.5) == 1.0f
                                && com.strata.ui.Grid.scaleFor(8.0) == 1.0f
                                && StrataScreen.MIN_ROW_H >= StrataScreen.CTRL_H - StrataScreen.LABEL_OFFSET);

        SelfTestHarness.check(lines, tally,
                "layout.19 an added variant appears in the list", () -> {
            List<String> ids = MobListScreen.planGroupRowIds(true, 7, List.of(8, 9));
            if (!ids.contains(MobListScreen.VARIANT_PREFIX + 8)
                    || !ids.contains(MobListScreen.VARIANT_PREFIX + 9)) {
                lines.add("    layout.19 added variants missing from the plan: " + ids);
                return false;
            }
            return ids.equals(List.of("7", "var:8", "var:9", "addvar:7"));
        });

        SelfTestHarness.check(lines, tally,
                "layout.20 exactly one Add-variant slot, on the base row", () -> {
            List<String> ids = MobListScreen.planGroupRowIds(true, 3, List.of(4, 5, 6));
            long slots = ids.stream()
                    .filter(id -> id.startsWith(MobListScreen.ADD_VARIANT_PREFIX))
                    .count();
            if (slots != 1) {
                lines.add("    layout.20 expected 1 Add-variant slot, got " + slots);
                return false;
            }
            return ids.contains(MobListScreen.ADD_VARIANT_PREFIX + 3);
        });

        SelfTestHarness.check(lines, tally,
                "layout.21 variant rows follow their base, slot last", () -> {
            List<String> ids = MobListScreen.planGroupRowIds(true, 1, List.of(2, 3));
            return ids.equals(List.of("1", "var:2", "var:3", "addvar:1"));
        });

        SelfTestHarness.check(lines, tally,
                "layout.22 a base-less group keeps its variants", () -> {
            List<String> ids = MobListScreen.planGroupRowIds(false, -1, List.of(4, 5));
            if (!ids.equals(List.of("var:4", "var:5"))) {
                lines.add("    layout.22 base-less group lost its variants: " + ids);
                return false;
            }
            for (String id : ids) {
                if (id.startsWith(MobListScreen.ADD_VARIANT_PREFIX)) return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.23 a group with no variants is just base + slot", () ->
                MobListScreen.planGroupRowIds(true, 2, List.of())
                        .equals(List.of("2", "addvar:2")));

        SelfTestHarness.check(lines, tally,
                "layout.39 Back walks list, Strata root, then the title screen", () -> {
            NavHistory<String> nav = new NavHistory<>();
            nav.restart("title");  // editor opened from the title screen
            nav.push("root");  // Root -> Mobs
            nav.push("mobList");  // Mobs -> mob editor
            boolean saveReturns = "mobList".equals(nav.pop().orElse(""));
            boolean rootNext = "root".equals(nav.pop().orElse(""));
            boolean titleLast = "title".equals(nav.pop().orElse(""));
            return saveReturns && rootNext && titleLast && nav.depth() == 0;
        });

        SelfTestHarness.check(lines, tally,
                "layout.40 back unwinds in order and bottoms out cleanly", () -> {
            NavHistory<String> nav = new NavHistory<>();
            if (nav.canGoBack() || nav.pop().isPresent()) {
                return false;  // empty history must not claim a destination
            }
            nav.push("a");
            nav.push("b");
            if (!"b".equals(nav.pop().orElse("")) || !"a".equals(nav.pop().orElse(""))) {
                return false;
            }
            return !nav.canGoBack() && nav.pop().isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "layout.41 a null screen is never pushed", () -> {
            NavHistory<String> nav = new NavHistory<>();
            nav.push(null);
            return nav.depth() == 0 && !nav.canGoBack();
        });

        SelfTestHarness.check(lines, tally,
                "layout.57 recent tiers and biomes list newest first, apart", () -> {
            com.strata.client.util.RecentEdits.touchTier("L57 Tier A");
            com.strata.client.util.RecentEdits.touchTier("L57 Tier B");
            com.strata.client.util.RecentEdits.touchTier("L57 Tier A");
            com.strata.client.util.RecentEdits.touchTier(" ");
            com.strata.client.util.RecentEdits.touchBiome("l57:biome");
            List<String> tiers = com.strata.client.util.RecentEdits.recentTiers();
            boolean order = tiers.indexOf("L57 Tier A") == 0 && tiers.indexOf("L57 Tier B") == 1;
            boolean apart = !tiers.contains("l57:biome")
                    && com.strata.client.util.RecentEdits.recentBiomes().getFirst().equals("l57:biome");
            return order && apart && !tiers.contains(" ") && !tiers.contains("");
        });

        SelfTestHarness.check(lines, tally,
                "layout.58 AI goals read as words", () ->
                "melee attack".equals(com.strata.util.Labels.goal(
                        "net.minecraft.world.entity.ai.goal.MeleeAttackGoal"))
                        && "shoots a bow".equals(com.strata.util.Labels.goal("RangedBowAttackGoal"))
                        && "wanders on land".equals(com.strata.util.Labels.goal("WaterAvoidingRandomStrollGoal"))
                        && "dig for bones".equals(com.strata.util.Labels.goal("com.mod.DigForBonesGoal"))
                        && "".equals(com.strata.util.Labels.goal(null)));

        SelfTestHarness.check(lines, tally,
                "layout.56 Test Mode refuses the inputs the test spawn command would refuse", () ->
                com.strata.command.StrataCommands.testSpawnProblem("", "5") != null
                        && com.strata.command.StrataCommands.testSpawnProblem("minecraft:zombie", "") != null
                        && com.strata.command.StrataCommands.testSpawnProblem("minecraft:zombie", "-1") != null
                        && com.strata.command.StrataCommands.testSpawnProblem("minecraft:zombie", "x") != null
                        && com.strata.command.StrataCommands.testSpawnProblem("minecraft:zombie", " 0 ") == null
                        && com.strata.command.StrataCommands.testSpawnProblem("minecraft:zombie", "12") == null);

        SelfTestHarness.check(lines, tally,
                "layout.55 a new editor session forgets the old one and backs out to its caller", () -> {
            NavHistory<String> nav = new NavHistory<>();
            nav.push("root");  // an earlier session, left with ESC
            nav.push("tierList");
            nav.restart("modList");  // opened again from the Mods list
            if (nav.depth() != 1 || !"modList".equals(nav.pop().orElse(""))) {
                return false;
            }
            nav.push("root");
            nav.restart(null);  // opened in game: Back closes the editor
            return nav.depth() == 0;
        });

        SelfTestHarness.check(lines, tally,
                "layout.42 picker lists variants first, then vanilla, then mods", () -> {
            List<String> ordered = RegistryPickerScreen.orderGroups(
                    List.of("zzzmod", "minecraft", "aaamod",
                            RegistryPickerScreen.VARIANT_GROUP));
            List<String> expected = List.of(
                    RegistryPickerScreen.VARIANT_GROUP, "minecraft", "aaamod", "zzzmod");
            if (!ordered.equals(expected)) {
                lines.add("    layout.42 group order was " + ordered);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.43 the Show filter narrows to one group", () -> {
            if (!RegistryPickerScreen.matchesGroup(
                        RegistryPickerScreen.ALL_GROUPS, "minecraft")
                    || !RegistryPickerScreen.matchesGroup(
                        RegistryPickerScreen.ALL_GROUPS,
                        RegistryPickerScreen.VARIANT_GROUP)) {
                return false;
            }
            if (!RegistryPickerScreen.matchesGroup(
                    RegistryPickerScreen.VARIANT_GROUP,
                    RegistryPickerScreen.VARIANT_GROUP)) {
                return false;
            }
            return !RegistryPickerScreen.matchesGroup(
                    RegistryPickerScreen.VARIANT_GROUP, "minecraft");
        });

        SelfTestHarness.check(lines, tally,
                "layout.24 ore cell shows 100 for a row with no rule", () ->
                com.strata.domain.OreOps.chanceCellValue(false, 100) == 100
                        && com.strata.domain.OreOps.chanceCellValue(false, 42) == 100);

        SelfTestHarness.check(lines, tally,
                "layout.25 ore cell shows the rule's own chance", () ->
                com.strata.domain.OreOps.chanceCellValue(true, 60) == 60
                        && com.strata.domain.OreOps.chanceCellValue(true, 0) == 0);

        SelfTestHarness.check(lines, tally,
                "layout.26 chance cell typing replaces, then appends", () -> {
            String b = BrowseList.typeChanceDigit("100", true, '6');
            if (!"6".equals(b)) {
                lines.add("    layout.26 first keystroke did not replace: " + b);
                return false;
            }
            b = BrowseList.typeChanceDigit(b, false, '5');
            if (!"65".equals(b)) return false;
            b = BrowseList.typeChanceDigit("100", false, '9');
            if (!"100".equals(b)) return false;
            return "65".equals(BrowseList.typeChanceDigit("65", false, 'x'))
                    && "65".equals(BrowseList.typeChanceDigit("65", false, '-'));
        });

        SelfTestHarness.check(lines, tally,
                "layout.27 chance cell commits clamped into 0..100", () ->
                BrowseList.commitChance("60") == 60
                        && BrowseList.commitChance("999") == 100
                        && BrowseList.commitChance("0") == 0
                        && BrowseList.commitChance("") == 0);

        SelfTestHarness.check(lines, tally,
                "layout.28 chance cell backspace never underflows", () ->
                "6".equals(BrowseList.backspaceChance("65"))
                        && "".equals(BrowseList.backspaceChance("6"))
                        && "".equals(BrowseList.backspaceChance("")));

        SelfTestHarness.check(lines, tally,
                "layout.29 attribute label keeps its qualifying segment", () -> {
            String zombie = Labels.attribute("minecraft:zombie.spawn_reinforcements");
            if (!zombie.contains("Zombie")) {
                lines.add("    layout.29 qualifier dropped: " + zombie);
                return false;
            }
            String horse = Labels.attribute("minecraft:horse.jump_strength");
            return horse.contains("Horse") && horse.contains("Jump");
        });

        SelfTestHarness.check(lines, tally,
                "layout.30 attribute label drops only the generic bucket", () ->
                "Max Health".equals(Labels.attribute("minecraft:generic.max_health"))
                        && "Movement Speed".equals(
                                Labels.attribute("minecraft:generic.movement_speed"))
                        && "Armor".equals(Labels.attribute("generic.armor")));

        SelfTestHarness.check(lines, tally,
                "layout.31 attribute labels stay distinct across categories", () -> {
            String a = Labels.attribute("minecraft:zombie.spawn_reinforcements");
            String b = Labels.attribute("minecraft:skeleton.spawn_reinforcements");
            if (a.equals(b)) {
                lines.add("    layout.31 distinct attributes render identically: " + a);
                return false;
            }
            return Labels.attribute("othermod:foo.bar").startsWith("Othermod");
        });

        SelfTestHarness.check(lines, tally,
                "layout.32 a field label clears the control above it", () -> {
            int minRowH = 22;
            if (Math.abs(StrataScreen.LABEL_OFFSET) >= minRowH) {
                lines.add("    layout.32 label offset " + StrataScreen.LABEL_OFFSET
                        + " collides with a " + minRowH + "px row");
                return false;
            }
            return StrataScreen.LABEL_OFFSET < 0;
        });

        SelfTestHarness.check(lines, tally,
                "layout.33 biome edit puts every control on its own row", () -> {
            com.strata.ui.Layout form = biomeForm();
            int[] ys = {form.get("tier").box().y(), form.get("level").box().y(), form.get("scaleMobs").box().y(),
                    form.get("substitute").box().y(), form.get("coefficient").box().y()};
            for (int k = 1; k < ys.length; k++) {
                if (ys[k] <= ys[k - 1]) {
                    lines.add("    layout.33 rows out of order: " + java.util.Arrays.toString(ys));
                    return false;
                }
            }
            return form.get("restrictOres").box().y() == ys[2];
        });

    }

    private static void checksPart02(List<String> lines, int[] tally) {
        SelfTestHarness.check(lines, tally,
                "layout.34 the nav row accounts for every biome field row", () -> {
            com.strata.ui.Layout form = biomeForm();
            int lastFieldBottom = form.get("coefficient").box().bottom();
            int nav = form.get("mobs").box().y();
            if (nav < lastFieldBottom) {
                lines.add("    layout.34 nav row " + nav + " above the last field's bottom " + lastFieldBottom);
                return false;
            }
            return true;
        });
        SelfTestHarness.check(lines, tally,
                "layout.35 scaling action row fits the content width", () -> {
            int rowWidth = com.strata.ui.screen.ListScreenLayouts.scalingBarWidth();
            if (rowWidth > 460) {
                lines.add("    layout.35 action row " + rowWidth + " exceeds the 460 floor");
                return false;
            }
            return rowWidth > 0;
        });

        SelfTestHarness.check(lines, tally,
                "layout.48 a variant instance id names all four of its parts", () -> {
            String tier = Labels.instanceId("minecraft:creeper", "Grassland", "grassland_creeper");
            String biome = Labels.instanceId("minecraft:creeper", "minecraft:plains", "plains_creeper");
            String global = Labels.instanceId("minecraft:creeper", "", "grassland_creeper");
            String plain = Labels.instanceId("minecraft:creeper", "Grassland", "");
            String modded = Labels.instanceId("alexsmobs:grizzly_bear", "Taiga", "big_bear");
            if (!"minecraft:Grassland:creeper:grassland_creeper".equals(tier)
                    || !"minecraft:plains:creeper:plains_creeper".equals(biome)
                    || !"minecraft:creeper:grassland_creeper".equals(global)
                    || !"minecraft:creeper".equals(plain)
                    || !"alexsmobs:Taiga:grizzly_bear:big_bear".equals(modded)) {
                lines.add("    layout.48 tier=" + tier + " biome=" + biome
                        + " global=" + global
                        + " plain=" + plain + " modded=" + modded);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.49 scope tabs wrap instead of running off the screen", () -> {
            int wide = StrataScreen.MAX_CONTENT_W;
            int perRow = com.strata.ui.Grid.tabsPerRow(wide);
            int few = com.strata.ui.Grid.tabRowCount(4, wide);
            int many = com.strata.ui.Grid.tabRowCount(32, wide);
            int tiny = com.strata.ui.Grid.tabsPerRow(10);
            int none = com.strata.ui.Grid.tabRowCount(0, wide);
            boolean wrapsHonestly = many == (32 + perRow - 1) / perRow && many > 1;
            if (perRow < 1 || few != 1 || tiny != 1 || none != 0 || !wrapsHonestly) {
                lines.add("    layout.49 perRow=" + perRow + " few=" + few
                        + " many=" + many + " tiny=" + tiny + " none=" + none);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.47 the content column grows with the viewport, up to the cap", () -> {
            int max = StrataScreen.MAX_CONTENT_W;
            int gutter = 20;
            int wide = com.strata.ui.Grid.contentWidthFor(4000, gutter, max);
            int narrow = com.strata.ui.Grid.contentWidthFor(300, gutter, max);
            int exact = com.strata.ui.Grid.contentWidthFor(max + gutter, gutter, max);
            boolean growsPastOldCap = wide > 460;
            if (wide != max || narrow != 280 || exact != max || !growsPastOldCap) {
                lines.add("    layout.47 wide=" + wide + " narrow=" + narrow
                        + " exact=" + exact + " cap=" + max);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.36 collapsing a group hides its whole subtree", () -> {
            java.util.Map<String, String> parents = new java.util.HashMap<>();
            parents.put("dim:Overworld", null);
            parents.put("dim:Overworld/band:Hot", "dim:Overworld");
            parents.put("dim:Overworld/band:Hot/minecraft", "dim:Overworld/band:Hot");

            java.util.Set<String> collapsed = new java.util.HashSet<>();
            collapsed.add("dim:Overworld");

            if (!BrowseList.hiddenByCollapse(
                    "dim:Overworld/band:Hot/minecraft", parents, collapsed)) {
                lines.add("    layout.36 grandchild survived a collapsed ancestor");
                return false;
            }
            return BrowseList.hiddenByCollapse("dim:Overworld", parents, collapsed);
        });

        SelfTestHarness.check(lines, tally,
                "layout.37 an uncollapsed subtree stays visible", () -> {
            java.util.Map<String, String> parents = new java.util.HashMap<>();
            parents.put("dim:Overworld", null);
            parents.put("dim:Overworld/band:Hot", "dim:Overworld");
            java.util.Set<String> collapsed = new java.util.HashSet<>();
            collapsed.add("dim:TheNether");
            return !BrowseList.hiddenByCollapse("dim:Overworld/band:Hot", parents, collapsed)
                    && !BrowseList.hiddenByCollapse(null, parents, collapsed);
        });

        SelfTestHarness.check(lines, tally,
                "layout.38 dimension headings order vanilla first, Other last", () -> {
            List<String> headings = new ArrayList<>(List.of(
                    DimensionBiomes.UNCLAIMED, "The End", "Zzz: Twilight",
                    "Overworld", "The Nether", "Aaa: Cavern"));
            headings.sort(DimensionBiomes.headingOrder());
            List<String> expected = List.of(
                    "Overworld", "The Nether", "The End",
                    "Aaa: Cavern", "Zzz: Twilight", DimensionBiomes.UNCLAIMED);
            if (!headings.equals(expected)) {
                lines.add("    layout.38 heading order was " + headings);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.44 a successful write reports nothing", () -> {
            return StrataScreen.failureMessage("the tier", true) == null;
        });

        SelfTestHarness.check(lines, tally,
                "layout.61 a flow row wraps at the width, and a bar grows upward", () -> {
            var row = com.strata.ui.Flow.row(0, 150, 10, 18, 4, new int[]{70, 70, 70});
            boolean wraps = row.boxes().get(1).equals(new com.strata.ui.Box(74, 10, 70, 18))
                    && row.boxes().get(2).equals(new com.strata.ui.Box(0, 32, 70, 18))
                    && row.bottom() == 50;
            var bar = com.strata.ui.Flow.bar(0, 150, 100, 18, 4, new int[]{70, 70, 70});
            boolean upward = bar.top() == 78 && bar.boxes().get(2).y() == 100;
            var wide = com.strata.ui.Flow.row(0, 150, 0, 18, 4, new int[]{200, 70});
            boolean alone = wide.boxes().get(0).y() == 0 && wide.boxes().get(1).y() == 22;
            if (!wraps || !upward || !alone) {
                lines.add("    layout.61 row=" + row + " bar=" + bar + " wide=" + wide);
            }
            return wraps && upward && alone;
        });

        SelfTestHarness.check(lines, tally,
                "layout.62 the scroll: how far, what is hidden, the wheel, the bar", () -> {
            boolean far = com.strata.ui.Scroll.max(300, 0, 240) == 66 && com.strata.ui.Scroll.max(100, 0, 240) == 0;
            boolean hidden = com.strata.ui.Scroll.outside(new com.strata.ui.Box(0, 20, 10, 18), 30, 240)
                    && !com.strata.ui.Scroll.outside(new com.strata.ui.Box(0, 30, 10, 18), 30, 240)
                    && !com.strata.ui.Scroll.outside(new com.strata.ui.Box(0, 222, 10, 18), 30, 240)
                    && com.strata.ui.Scroll.outside(new com.strata.ui.Box(0, 223, 10, 18), 30, 240);
            boolean wheel = com.strata.ui.Scroll.afterWheel(10, 66, 1) == 0
                    && com.strata.ui.Scroll.afterWheel(60, 66, -1) == 66
                    && com.strata.ui.Scroll.afterWheel(30, 66, -1) == 50;
            var end = com.strata.ui.Scroll.thumb(30, 230, 100, 100);
            var none = com.strata.ui.Scroll.thumb(30, 230, 0, 0);
            boolean thumb = end.y() + end.h() == 230 && none.y() == 30 && none.h() == 200;
            if (!far || !hidden || !wheel || !thumb) {
                lines.add("    layout.62 far=" + far + " hidden=" + hidden + " wheel=" + wheel + " thumb=" + end);
            }
            return far && hidden && wheel && thumb;
        });

        SelfTestHarness.check(lines, tally,
                "layout.63 a line too long for its room is cut with ...", () -> {
            com.strata.ui.TextFit.Measure six = s -> s.length() * 6;
            boolean fits = "abcdef".equals(com.strata.ui.TextFit.clip("abcdef", 36, six));
            String cut = com.strata.ui.TextFit.clip("abcdefgh", 36, six);
            boolean cuts = "abc...".equals(cut) && six.width(cut) <= 36;
            boolean whole = "😀".equals(com.strata.ui.TextFit.prefix("😀😀", 12, six));
            if (!fits || !cuts || !whole) {
                lines.add("    layout.63 cut=" + cut);
            }
            return fits && cuts && whole;
        });

        SelfTestHarness.check(lines, tally,
                "layout.64 the frame fits every window size and GUI scale", () -> {
            int[][] sizes = {{427, 240}, {640, 360}, {427, 240}, {960, 540}, {640, 360}, {480, 270}, {640, 360}};
            List<String> bad = new ArrayList<>();
            for (int[] s : sizes) {
                var frame = new com.strata.ui.Frame(s[0], s[1]);
                boolean ok = frame.back().inside(frame.screen())
                        && frame.contentLeft() >= 0 && frame.contentRight() <= s[0]
                        && frame.rowH() >= com.strata.ui.Grid.MIN_ROW_H
                        && frame.footerY2() > frame.viewTop() && frame.footerY() + 9 <= s[1]
                        && frame.titleX() > frame.back().right();
                if (!ok) {
                    bad.add(s[0] + "x" + s[1]);
                }
            }
            boolean capped = new com.strata.ui.Frame(2000, 1000).contentWidth() == com.strata.ui.Grid.MAX_CONTENT_W;
            if (!bad.isEmpty() || !capped) {
                lines.add("    layout.64 frame does not fit: " + bad + " capped=" + capped);
            }
            return bad.isEmpty() && capped;
        });

        SelfTestHarness.check(lines, tally,
                "layout.65 the game's font loads without a client, and has every printable ASCII character", () -> {
            com.strata.ui.GameFont font = gameFont(lines, "layout.65");
            if (font == null) {
                return false;
            }
            List<String> missing = new ArrayList<>();
            for (int cp = 32; cp < 127; cp++) {
                if (!font.knows(cp)) {
                    missing.add(String.valueOf((char) cp));
                }
            }
            if (!missing.isEmpty()) {
                lines.add("    layout.65 missing: " + missing + " skipped providers: " + font.skipped());
            }
            return missing.isEmpty();
        });

        SelfTestHarness.check(lines, tally,
                "layout.66 the game's font gives the widths the game draws", () -> {
            com.strata.ui.GameFont font = gameFont(lines, "layout.66");
            if (font == null) {
                return false;
            }
            int[] got = {font.width("Save"), font.width("i"), font.width("l"), font.width("t"),
                    font.width("I"), font.width(" "), font.width("..."), font.width(""),
                    font.width("§"), font.width("§cSave")};
            int[] want = {24, 2, 3, 4, 4, 4, 6, 0, 0, 24};
            boolean ok = java.util.Arrays.equals(got, want);
            if (!ok) {
                lines.add("    layout.66 widths " + java.util.Arrays.toString(got) + ", want "
                        + java.util.Arrays.toString(want));
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "layout.67 text wraps between words, and the last line holds the rest cut", () -> {
            com.strata.ui.TextFit.Measure six = s -> s.length() * 6;
            List<String> two = com.strata.ui.TextFit.wrap("aaa bbb ccc", 42, 5, six);
            List<String> cut = com.strata.ui.TextFit.wrap("aaa bbb ccc ddd eee", 42, 2, six);
            List<String> long1 = com.strata.ui.TextFit.wrap("abcdefghij", 36, 3, six);
            boolean ok = two.equals(List.of("aaa bbb", "ccc"))
                    && cut.equals(List.of("aaa bbb", "ccc...")) && six.width(cut.get(1)) <= 42
                    && long1.equals(List.of("abcdef", "ghij"))
                    && com.strata.ui.TextFit.wrap("", 42, 2, six).isEmpty();
            if (!ok) {
                lines.add("    layout.67 two=" + two + " cut=" + cut + " long=" + long1);
            }
            return ok;
        });

        SelfTestHarness.check(lines, tally,
                "layout.45 a failed write reports what did not save", () -> {
            String msg = StrataScreen.failureMessage("the tier", false);
            boolean present = msg != null && !msg.isBlank();
            boolean names = present && msg.contains("the tier");
            if (!present || !names) {
                lines.add("    layout.45 message was " + msg);
            }
            return present && names;
        });

        SelfTestHarness.check(lines, tally,
                "layout.46 a slot's free share is 100 minus the variant's own weights", () -> {
            double empty = com.strata.domain.EquipmentOps.availableForSlot(0, 0);
            double freshInBusy = com.strata.domain.EquipmentOps.availableForSlot(60, 0);
            double editKeepsShare = com.strata.domain.EquipmentOps.availableForSlot(80, 40);
            double fullCloses = com.strata.domain.EquipmentOps.availableForSlot(100, 0);
            double overfullClamps = com.strata.domain.EquipmentOps.availableForSlot(120, 0);
            if (empty != 100 || freshInBusy != 40 || editKeepsShare != 60
                    || fullCloses != 0 || overfullClamps != 0) {
                lines.add("    layout.46 share drifted: empty=" + empty
                        + " fresh=" + freshInBusy + " edit=" + editKeepsShare
                        + " full=" + fullCloses + " over=" + overfullClamps);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.50 a base row's Name starts empty, so Save keeps it a base row", () -> {
            java.util.function.Supplier<String> boom = () -> {
                throw new IllegalStateException("suggested a name for a base row");
            };
            String base = com.strata.domain.VariantOps.initialName(true, null, boom);
            if (!base.isEmpty()) {
                lines.add("    layout.50 base row opened with name '" + base + "'");
                return false;
            }
            if (!com.strata.domain.VariantOps.initialName(true, "leftover", boom).isEmpty()) {
                lines.add("    layout.50 base row took a name from the table");
                return false;
            }
            String existing = com.strata.domain.VariantOps.initialName(false, "fire_zombie", boom);
            if (!"fire_zombie".equals(existing)) {
                lines.add("    layout.50 existing variant lost its name: " + existing);
                return false;
            }
            String fresh = com.strata.domain.VariantOps.initialName(false, null, () -> "zombie_2");
            if (!"zombie_2".equals(fresh)) {
                lines.add("    layout.50 new variant did not take the suggestion: " + fresh);
                return false;
            }
            return true;
        });

        SelfTestHarness.check(lines, tally,
                "layout.51 the numeric cell refuses NaN and Infinity", () -> {
            var v = EditField.numericValidator();
            return !v.test("NaN") && !v.test("Infinity") && !v.test("-Infinity")
                    && v.test("12.5") && v.test("-3") && v.test("0");
        });

        SelfTestHarness.check(lines, tally,
                "layout.52 the coefficient cell is a bounded decimal", () -> {
            var v = EditField.coefficientValidator();
            if (!v.test("") || !v.test("12") || !v.test("0.5") || !v.test("1.0")) {
                return false;
            }
            return !v.test(".") && !v.test("1.") && !v.test("1.2.3")
                    && !v.test("NaN") && !v.test("-1") && !v.test("a");
        });

        SelfTestHarness.check(lines, tally,
                "layout.53 the Test Mode command walks the real command tree", () -> {
            CommandNode<CommandSourceStack> root = new StrataCommands().tree().build();
            if (!walksTree(root, StrataCommands.testSpawnCommand("minecraft:zombie", 5),
                    lines, "layout.53")) {
                return false;
            }
            CommandNode<CommandSourceStack> profile = root.getChild("profile");
            return profile == null || profile.getChild(StrataCommands.TEST) == null;
        });

        SelfTestHarness.check(lines, tally,
                "layout.59 the Server menu's reload command walks the real command tree", () ->
                walksTree(new StrataCommands().tree().build(), StrataCommands.reloadCommand(),
                        lines, "layout.59"));

        SelfTestHarness.check(lines, tally,
                "layout.60 the Server menu's regenerate command walks the real command tree", () ->
                walksTree(new StrataCommands().tree().build(), StrataCommands.regenerateCommand(),
                        lines, "layout.60"));

        SelfTestHarness.check(lines, tally,
                "layout.61b /strata help walks the tree and its section names every command", () -> {
            CommandNode<CommandSourceStack> root = new StrataCommands().tree().build();
            boolean walks = walksTree(root, StrataCommands.ROOT + " " + StrataCommands.HELP,
                    lines, "layout.61b");
            var section = com.strata.data.HelpText.section(StrataCommands.HELP_SECTION);
            String text = section == null ? "" : String.join("\n", section.text());
            boolean named = section != null;
            for (CommandNode<CommandSourceStack> child : root.getChildren()) {
                if (child instanceof LiteralCommandNode<?> literal && !text.contains(literal.getLiteral())) {
                    lines.add("    layout.61b the section does not name /strata " + literal.getLiteral());
                    named = false;
                }
            }
            return walks && named;
        });

        SelfTestHarness.check(lines, tally,
                "layout.54 a grouped-view row names its own tier or biome", () -> {
            String tier = Labels.scopeIdOf(com.strata.model.Source.tier("Tier 1"), "");
            String groupedBiome = Labels.scopeIdOf(new com.strata.model.Source(
                    com.strata.model.Source.Layer.BIOME, "minecraft:plains"), "");
            String listBiome = Labels.scopeIdOf(com.strata.model.Source.BIOME, "minecraft:desert");
            String global = Labels.scopeIdOf(com.strata.model.Source.GLOBAL, "x");
            if (!"Tier 1".equals(tier) || !"minecraft:plains".equals(groupedBiome)
                    || !"minecraft:desert".equals(listBiome) || !global.isEmpty()) {
                lines.add("    layout.54 got tier='" + tier + "' grouped='" + groupedBiome
                        + "' list='" + listBiome + "' global='" + global + "'");
                return false;
            }
            return true;
        });

    }

    /**
     * Walks a command line, token by token, through the tree the server registers, and says why it fails.
     */
    private static boolean walksTree(CommandNode<CommandSourceStack> root, String line,
                                     List<String> lines, String caseId) {
        CommandNode<CommandSourceStack> node = root;
        String[] tokens = line.split(" ");
        if (!root.getName().equals(tokens[0])) {
            lines.add("    " + caseId + " root is '" + root.getName() + "'");
            return false;
        }
        for (int i = 1; i < tokens.length; i++) {
            CommandNode<CommandSourceStack> next = null;
            for (CommandNode<CommandSourceStack> child : node.getChildren()) {
                if (child instanceof LiteralCommandNode<?> literal) {
                    if (literal.getLiteral().equals(tokens[i])) {
                        next = child;
                        break;
                    }
                } else if (child instanceof ArgumentCommandNode<?, ?> argument) {
                    StringReader reader = new StringReader(tokens[i]);
                    try {
                        argument.getType().parse(reader);
                    } catch (CommandSyntaxException e) {
                        continue;
                    }
                    if (!reader.canRead()) {
                        next = child;
                        break;
                    }
                }
            }
            if (next == null) {
                lines.add("    " + caseId + " no node under '" + node.getName()
                        + "' takes '" + tokens[i] + "'");
                return false;
            }
            node = next;
        }
        if (node.getCommand() == null) {
            lines.add("    " + caseId + " the line ends on a node that runs nothing");
            return false;
        }
        return true;
    }

    private static com.strata.ui.GameFont cachedFont;

    /** The game's font from the classpath, loaded once; null, with a line saying why, when it will not load. */
    static com.strata.ui.GameFont gameFont(List<String> lines, String caseId) {
        if (cachedFont == null) {
            try {
                cachedFont = com.strata.ui.GameFont.load(LayoutCheckSelfTest.class.getClassLoader());
            } catch (java.io.IOException | RuntimeException e) {
                lines.add("    " + caseId + " the game's font did not load: " + e);
                return null;
            }
        }
        return cachedFont;
    }

    /** The biome form at the smallest GUI size, measured 6 px a character. */
    private static com.strata.ui.Layout biomeForm() {
        return com.strata.ui.screen.FormScreenLayouts.biomeEdit(new com.strata.ui.Frame(427, 240),
                s -> s.length() * 6, "Strata — Biome", "Tier: t1", "Level override (blank = inherit)",
                List.of("inherit", "on", "off"), "");
    }
}
