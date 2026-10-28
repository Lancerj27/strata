package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** THE LIST SCREENS, LAID OUT: each a {@link ListLayout} with its own controls. */
public final class ListScreenLayouts {

    private ListScreenLayouts() {
    }

    /**
     * The data menu's button on the Items, Mobs, Biomes and Blocks lists: a dropdown of the registries, tags
     * and data maps about that list's subject.
     */
    public static final String DATA_MENU = "Data...";

    /** The search field, as a list's top row starts. */
    private static List<ListLayout.Action> search(Frame frame) {
        List<ListLayout.Action> top = new ArrayList<>();
        top.add(ListLayout.Action.field("search", Grid.wide(150, frame.contentWidth(), 25, 260)));
        return top;
    }

    /** The Blocks lists' tabs, in order. */
    public static final List<String> BLOCK_TABS = List.of("Blocks", "Ores", "Vegetation", "Block replacements");

    /** The tab row first in a Blocks list's top row, ids "tab0".."tab3"; the open one greyed, as on Mobs. */
    static List<ListLayout.Action> withTabs(List<ListLayout.Action> top, int active) {
        List<ListLayout.Action> out = new ArrayList<>();
        if (active >= 0) {
            for (int i = 0; i < BLOCK_TABS.size(); i++) {
                out.add(ListLayout.Action.of("tab" + i, BLOCK_TABS.get(i),
                        Math.max(Grid.TAB_W, Grid.buttonW(BLOCK_TABS.get(i)))).withActive(i != active));
            }
        }
        out.addAll(top);
        return out;
    }

    /** The block replacements list's hint over its bar. */
    public static final String SWAP_HINT = "Where the world would generate the From block, it puts the To block, or"
            + " nothing (air) when To is Disabled. A biome's rule wins over its tier's, a tier's over Global."
            + " New chunks only.";

    /**
     * BlockSwapScreen: the Blocks tabs over the rules of one place; under the list the add row, From and To
     * (each opens the block picker) and + Add; the hint; Remove.
     *
     * @param picked a rule is selected
     */
    public static Layout blockSwapList(Frame frame, TextFit.Measure m, String title,
                                       String from, String to, boolean picked, String footer, String empty) {
        List<ListLayout.Action> top = withTabs(new ArrayList<>(), 3);
        List<FormLayout.Cell> add = List.of(
                FormLayout.Cell.button("from", 180, "From", from),
                FormLayout.Cell.button("to", 180, "To", to),
                FormLayout.Cell.button("add", Grid.buttonW("+ Add"), null, "+ Add"));
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, add,
                List.of(ListLayout.Action.of("remove", "Remove").withActive(picked)),
                wrappedHelp(SWAP_HINT, frame, m), footer, empty));
    }

    /**
     * TierListScreen: the search, the tiers, + Add tier, Edit and Duplicate.
     *
     * @param picked a tier is selected
     */
    public static Layout tierList(Frame frame, TextFit.Measure m, String title, boolean picked) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, search(frame), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add tier").asMain(),
                        ListLayout.Action.of("edit", "Edit").withActive(picked),
                        ListLayout.Action.of("duplicate", "Duplicate").withActive(picked)), List.of(),
                TIERS_HINT, TIERS_EMPTY));
    }

    /**
     * ProjectileListScreen: the search, the designer's projectiles, + Add, Edit, Abilities, On, Off and
     * Remove on the picked ones.
     *
     * @param picked the list has a picked row
     */
    public static Layout projectileList(Frame frame, TextFit.Measure m, String title, boolean picked) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, search(frame), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add projectile").asMain(),
                        ListLayout.Action.of("edit", "Edit").withActive(picked),
                        ListLayout.Action.of("abilities", "Abilities").withActive(picked),
                        ListLayout.Action.of("on", "On").withActive(picked),
                        ListLayout.Action.of("off", "Off").withActive(picked),
                        ListLayout.Action.of("remove", "Remove").withActive(picked)), List.of(),
                PROJECTILES_HINT, PROJECTILES_EMPTY));
    }

    /** The projectile list's footer. */
    public static final String PROJECTILES_HINT =
            "A goal's Shoots a projectile fires one by name. Select one, then Edit (or double-click).";

    /** What the projectile list says with none. */
    public static final String PROJECTILES_EMPTY = "No projectiles yet. + Add projectile makes one.";

    /**
     * CustomStructuresScreen: the search, the designer's structures, + Add, Edit, On, Off and Remove on the
     * picked ones.
     *
     * @param picked the list has a picked row
     */
    public static Layout customStructureList(Frame frame, TextFit.Measure m, String title, boolean picked) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, search(frame), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add structure").asMain(),
                        ListLayout.Action.of("edit", "Edit").withActive(picked),
                        ListLayout.Action.of("on", "On").withActive(picked),
                        ListLayout.Action.of("off", "Off").withActive(picked),
                        ListLayout.Action.of("remove", "Remove").withActive(picked)), List.of(),
                STRUCTURES_HINT, STRUCTURES_EMPTY));
    }

    /**
     * MobBiomesScreen: the search, every biome where the mob spawns naturally, and On / Off on the picked
     * ones.
     *
     * @param empty the line for an empty list, or null
     */
    public static Layout mobBiomes(Frame frame, TextFit.Measure m, String title, String empty) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, search(frame), List.of(),
                List.of(ListLayout.Action.of("toggle", "On / Off").asMain()), List.of(),
                MOB_BIOMES_HINT, empty));
    }

    /** The mob biomes list's footer. */
    public static final String MOB_BIOMES_HINT =
            "Off stops it spawning in that biome. The biome's own mob list shows the same switch.";

    /** The custom structure list's footer. */
    public static final String STRUCTURES_HINT =
            "A structure block's saved .nbt, built where and as often as you set. Select one, then Edit (or double-click).";

    /** What the custom structure list says with none. */
    public static final String STRUCTURES_EMPTY = "No structures yet. + Add structure brings one in.";

    /** The tier list's footer. */
    public static final String TIERS_HINT = "Select a tier, then Edit (or double-click) opens it; Duplicate copies it.";

    /** What the tier list says with no tier: an empty box said nothing. */
    public static final String TIERS_EMPTY = "No tiers yet. + Add tier makes one.";

    /** The ore list's hint over its bar. */
    public static final String ORE_HINT =
            "Pick ores (Ctrl or Shift for several), then On, Off or Restore. Type a chance in its box.";

    /**
     * OreListScreen: the search, the ores, the hint, Enable, Disable, Default (a click used to change a rule
     * and save).
     *
     * @param picked the list has a picked row
     */
    public static Layout oreList(Frame frame, TextFit.Measure m, String title, boolean picked, String footer,
                                 String empty) {
        // The Blocks tabs first, as on Mobs.
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, withTabs(search(frame), 1), List.of(),
                List.of(ListLayout.Action.of("enable", "On").withActive(picked),
                        ListLayout.Action.of("disable", "Off").withActive(picked),
                        ListLayout.Action.of("default", "Restore").withActive(picked)),
                wrappedHelp(ORE_HINT, frame, m), footer, empty).withChanceHeading());
    }

    /** The flora list's hint over its bar. */
    public static final String VEGETATION_HINT = "Pick plants (Ctrl or Shift for several), then On (adds it where it"
            + " is missing), Off (removes it), or Restore. From the next world load.";

    /**
     * VegetationScreen: the search, the plant features by dimension, the hint, Enable, Disable, Default.
     *
     * @param picked the list has a picked row
     */
    public static Layout vegetationList(Frame frame, TextFit.Measure m, String title, boolean picked, String footer,
                                   String empty) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, withTabs(search(frame), 2), List.of(),
                List.of(ListLayout.Action.of("enable", "On").withActive(picked),
                        ListLayout.Action.of("disable", "Off").withActive(picked),
                        ListLayout.Action.of("default", "Restore").withActive(picked)),
                wrappedHelp(VEGETATION_HINT, frame, m), footer, empty));
    }

    /** The Rules list's hint over its bar. */
    public static final String RULES_HINT = "This tier's or biome's spawn rules. Select one, then On, Off, or Default"
            + " (a biome takes its tier's; with neither, the rule is on). The spawn rules act in a Replace tier's biomes.";

    /**
     * RulesScreen: the rules, the hint, On, Off, Default.
     *
     * @param picked a rule is selected
     * @param canDefault the selected rule can have no value of its own (not the Special spawns switch)
     */
    public static Layout rulesList(Frame frame, TextFit.Measure m, String title, boolean picked, boolean canDefault,
                                   String footer) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, List.of(), List.of(),
                List.of(ListLayout.Action.of("on", "On").withActive(picked),
                        ListLayout.Action.of("off", "Off").withActive(picked),
                        ListLayout.Action.of("default", "Default").withActive(picked && canDefault)),
                wrappedHelp(RULES_HINT, frame, m), footer, null));
    }

    /** World > Loot tables' hint over its bar. */
    public static final String LOOT_HINT = "Every loot table the game and its mods have: chests, mobs, blocks, fishing,"
            + " archaeology and the rest. Edit (or double-click) opens its entries by pool, as a mob's Loot does.";

    /** World > Loot tables: the chest list's shape. */
    public static Layout lootList(Frame frame, TextFit.Measure m, String title, boolean picked, String footer,
                                  String empty) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, search(frame), List.of(),
                List.of(ListLayout.Action.of("edit", "Edit").asMain().withActive(picked)),
                wrappedHelp(LOOT_HINT, frame, m), footer, empty));
    }

    /** The item list's hint over its bar. */
    public static final String ITEM_HINT = "Every item by kind; your variants of it under each (yellow). Edit (or"
            + " double-click) opens an item's stats and its crafting recipes, or a variant. + Add variant under an"
            + " item makes one of it.";

    /**
     * ItemsScreen, Client > Items: the search, Chest loot, and Blocks on top; the items by kind with their
     * variants and a "+ Add variant" row under each; the hint; Edit, Remove (a variant).
     *
     * @param picked the selected row can be edited
     * @param removeLabel Remove's text, with the count when several are picked
     */
    public static Layout itemList(Frame frame, TextFit.Measure m, String title, boolean picked, String removeLabel,
                                  boolean removeActive, String footer, String empty) {
        return itemList(frame, m, title, picked, removeLabel, removeActive, footer, empty, List.of());
    }

    /**
     * @param headings the at-a-glance columns' headings (domain.GlanceColumns), already cut to the room
     *     (GlanceLayout#visible); none draws no columns
     */
    public static Layout itemList(Frame frame, TextFit.Measure m, String title, boolean picked, String removeLabel,
                                  boolean removeActive, String footer, String empty, List<String> headings) {
        List<ListLayout.Action> top = search(frame);
        // Chest loot, Brewing, and the item and enchantment lists are one Data...
        top.add(ListLayout.Action.of("data", DATA_MENU));
        // The global Ores, Vegetation and Replacements are in the Blocks Section dropdown.
        return ListLayout.rightAlign(ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, List.of(),
                List.of(ListLayout.Action.of("edit", "Edit").asMain().withActive(picked),
                        // Disable an item, or turn it back on.
                        ListLayout.Action.of("toggle", "On / Off").withActive(picked),
                        ListLayout.Action.of("remove", removeLabel, Grid.BTN_W_WIDE).withActive(removeActive)),
                wrappedHelp(ITEM_HINT, frame, m), footer, empty, headings,
                GlanceLayout.columnWidth(GlanceLayout.listRoom(frame), m, headings))), frame, List.of("data"));
    }

    /** The share of the item sheet's width its value list keeps; the recipe panel has the rest. */
    static final int ITEM_LIST_SHARE = 50;
    /** The crafting grid's slot size: the game's 18, larger where there is room. */
    static final int SLOT_MIN = 18;
    static final int SLOT_MAX = 40;
    /** The room for the arrow between the grid and the result. */
    static final int ARROW_W = 24;

    /**
     * ItemStatScreen: the item's values on the left; on the right the recipes that make it, one at a time
     * (prev, its name or a new one's name field, next), on the game's 3x3 grid ("s0" to "s8"), the arrow, the
     * result slot ("result"), the type and the count.
     *
     * @param recipeTitle the shown recipe's name and place ("minecraft:stick, 1 of 2"), or the panel's note
     * @param isNew a new recipe: a name field in place of the title
     * @param hasRecipe a recipe is shown: the grid and its controls are usable
     * @param removeLabel Remove recipe or Restore recipe
     */
    public static Layout itemSheet(Frame frame, TextFit.Measure m, String title, String help, String empty,
                                   String recipeTitle, boolean isNew, boolean hasRecipe, boolean prevActive,
                                   boolean nextActive, boolean toggleActive, String removeLabel, boolean removeActive,
                                   boolean saveActive) {
        return itemSheet(frame, m, title, help, empty, recipeTitle, isNew, hasRecipe, prevActive, nextActive,
                toggleActive, removeLabel, removeActive, saveActive, "");
    }

    /**
     * @param detail what the grid cannot show of a mod's machine recipe: counts, chances, other outputs,
     *     fluids, chemicals, time; wrapped beside the grid as detail0, detail1...
     */
    public static Layout itemSheet(Frame frame, TextFit.Measure m, String title, String help, String empty,
                                   String recipeTitle, boolean isNew, boolean hasRecipe, boolean prevActive,
                                   boolean nextActive, boolean toggleActive, String removeLabel, boolean removeActive,
                                   boolean saveActive, String detail) {
        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, m));
        while (hints.size() < HELP_LINES) {
            hints.add("");
        }
        Layout layout = ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, List.of(), List.of(),
                List.of(ListLayout.Action.field("value", 120),
                        ListLayout.Action.of("set", "Set value"),
                        ListLayout.Action.of("restore", "Restore"),
                        // An attribute the item does not have: armour on a sword.
                        ListLayout.Action.of("addAttribute", "+ Add attribute"),
                        // Any other component the game or a mod registers.
                        ListLayout.Action.of("addComponent", "+ Add component"),
                        ListLayout.Action.of("addRecipe", "+ Add recipe"),
                        ListLayout.Action.of("toggle", "On / Off").withActive(toggleActive),
                        ListLayout.Action.of("removeRecipe", removeLabel, 100).withActive(removeActive),
                        ListLayout.Action.of("save", "Save recipe").withActive(saveActive)),
                hints, null, empty));
        Layout.Element list = layout.get("list");
        Box all = list.box();
        int gap = Grid.GAP * 2;
        int listW = (all.w() - gap) * ITEM_LIST_SHARE / 100;
        layout.replace(new Layout.Element("list", list.kind(), new Box(all.x(), all.y(), listW, all.h()),
                list.text(), list.pinned(), list.active(), list.main(), list.hidden()));
        // The empty-list note stays in the narrowed list.
        Layout.Element note = layout.get("empty");
        if (note != null) {
            layout.replace(layout.note("empty", all.x() + 4, note.box().y(), empty, listW - 8));
        }
        int x = all.x() + listW + gap;
        int w = all.right() - x;
        int h = Grid.CTRL_H;
        int y = all.y();
        // The top row: prev, the title or the name field, next.
        layout.add(Layout.control("prev", Layout.Kind.BUTTON, new Box(x, y, 18, h), "<").withActive(prevActive));
        layout.add(Layout.control("next", Layout.Kind.BUTTON, new Box(all.right() - 18, y, 18, h), ">")
                .withActive(nextActive));
        int midX = x + 18 + Grid.GAP;
        int midW = w - 2 * (18 + Grid.GAP);
        if (isNew) {
            // THE NAME'S TITLE BESIDE IT.
            int titleRoom = m.width("Name") + ListLayout.TITLE_GAP;
            layout.add(layout.text("label.name", midX, y + (h - Layout.LINE_H) / 2, "Name", titleRoom));
            layout.add(Layout.control("name", Layout.Kind.FIELD, new Box(midX + titleRoom, y, midW - titleRoom, h), ""));
        } else {
            layout.add(layout.text("recipeTitle", midX, y + (h - Layout.LINE_H) / 2, recipeTitle, midW));
        }
        // The grid, as large as the room allows: below it the type and the count.
        int gridTop = y + h + Grid.GAP;
        int below = h + Grid.GAP;
        int byHeight = (all.bottom() - gridTop - below) / 3;
        int byWidth = (w - ARROW_W - Grid.GAP) / 4;
        int slot = Math.max(SLOT_MIN, Math.min(SLOT_MAX, Math.min(byHeight, byWidth)));
        // The grid, the arrow, and the result sit at the panel's right edge.
        int resultSize = slot + 4;
        int gx = all.right() - (3 * slot + ARROW_W + resultSize);
        for (int i = 0; i < 9; i++) {
            layout.add(Layout.control("s" + i, Layout.Kind.BUTTON,
                    new Box(gx + (i % 3) * slot, gridTop + (i / 3) * slot, slot, slot), "").withActive(hasRecipe));
        }
        layout.add(Layout.control("result", Layout.Kind.BUTTON, new Box(gx + 3 * slot + ARROW_W,
                gridTop + slot - 2, resultSize, resultSize), "").withActive(hasRecipe));
        int rowY = gridTop + 3 * slot + Grid.GAP;
        // The machine's details in the room left of the grid, as many lines as fit.
        int detailW = gx - Grid.GAP * 2 - x;
        if (detail != null && !detail.isBlank() && detailW > 40) {
            int lines = Math.max(1, (rowY - gridTop) / Layout.LINE_H);
            List<String> wrapped = TextFit.wrap(detail, detailW, lines, m);
            for (int i = 0; i < wrapped.size(); i++) {
                layout.add(layout.text("detail" + i, x, gridTop + i * Layout.LINE_H, wrapped.get(i), detailW));
            }
        }
        String widest = Layout.choiceText("", "Shapeless");
        int typeW = m.width(widest) + Layout.LABEL_MARGIN * 2;
        int countX = all.right() - 30;
        int labelX = countX - Grid.GAP - m.width("Makes");
        int typeX = labelX - Grid.GAP * 2 - typeW;
        // The type's title beside it, as "Makes" is the count's.
        int typeLabelX = typeX - Grid.GAP - m.width("Type");
        layout.add(layout.text("typeLabel", typeLabelX, rowY + (h - Layout.LINE_H) / 2, "Type", 40));
        layout.add(Layout.control("type", Layout.Kind.DROPDOWN, new Box(typeX, rowY, typeW, h), widest)
                .withActive(hasRecipe));
        layout.add(layout.text("countLabel", labelX, rowY + (h - Layout.LINE_H) / 2, "Makes", 40));
        layout.add(Layout.control("count", Layout.Kind.FIELD, new Box(countX, rowY, 30, h), "")
                .withActive(hasRecipe));
        return layout;
    }

    /** The block list's hint over its bar. */
    public static final String BLOCK_HINT = "Every block by kind, your block variants under their base block (yellow)."
            + " Edit (or double-click) opens a block's stats or a variant. + Add variant under a block makes one of it.";

    /**
     * BlockStatsScreen, Client > Blocks: the search, the blocks by kind with their variants under them, the
     * hint, + Add variant, Edit, Remove (a variant).
     *
     * @param picked the list has a selected block or variant
     * @param addActive the selection is a block or a variant (+ Add variant copies its block)
     * @param removeLabel Remove's text, with the count when several variants are picked
     * @param removeActive a variant is picked
     */
    public static Layout blockList(Frame frame, TextFit.Measure m, String title, boolean picked,
                                   String removeLabel, boolean removeActive, String footer, String empty) {
        return blockList(frame, m, title, picked, removeLabel, removeActive, footer, empty, List.of());
    }

    /** The Blocks list's button at the top row's right end. */
    public static final List<String> BLOCK_TOOLS = List.of("data");

    /** @param headings the at-a-glance columns' headings, as itemList's */
    public static Layout blockList(Frame frame, TextFit.Measure m, String title, boolean picked,
                                   String removeLabel, boolean removeActive, String footer, String empty,
                                   List<String> headings) {
        // The Blocks tabs in place of a Section dropdown: Blocks, Ores, Vegetation, Block replacements.
        List<ListLayout.Action> top = withTabs(search(frame), 0);
        // No Columns...
        top.add(ListLayout.Action.of("data", DATA_MENU));
        return ListLayout.rightAlign(ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, List.of(),
                List.of(ListLayout.Action.of("edit", "Edit").asMain().withActive(picked),
                        // Disable a block, or turn it back on.
                        ListLayout.Action.of("toggle", "On / Off").withActive(picked),
                        ListLayout.Action.of("remove", removeLabel, Grid.BTN_W_WIDE).withActive(removeActive)),
                wrappedHelp(BLOCK_HINT, frame, m), footer, empty, headings,
                GlanceLayout.columnWidth(GlanceLayout.listRoom(frame), m, headings))), frame, BLOCK_TOOLS);
    }

    /**
     * ItemVariantStatsScreen: the Item stats value sheet for a variant, with + Attribute and + Enchantment.
     */
    public static Layout variantSheet(Frame frame, TextFit.Measure m, String title, String help, String footer,
                                      String empty) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, List.of(), List.of(),
                List.of(ListLayout.Action.field("value", 130),
                        ListLayout.Action.of("set", "Set value"),
                        ListLayout.Action.of("restore", "Restore"),
                        ListLayout.Action.of("addAttribute", "+ Add attribute"),
                        ListLayout.Action.of("addEnchantment", "+ Add enchantment"),
                        ListLayout.Action.of("addComponent", "+ Add component")),
                wrappedHelp(help, frame, m), footer, empty));
    }

    /** Help over a bar, wrapped to HELP_LINES, padded so the boxes do not move. */
    private static List<String> wrappedHelp(String help, Frame frame, TextFit.Measure m) {
        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, m));
        while (hints.size() < HELP_LINES) {
            hints.add("");
        }
        return hints;
    }

    /**
     * BiomeListScreen: the search and the tier filter, the biomes, the count; under them, "Add shown to
     * <tier>" when the list was opened from a tier.
     *
     * @param assignLabel the assign button's text, or null for none
     * @param assignActive the list shows a biome to assign
     */
    public static Layout biomeList(Frame frame, TextFit.Measure m, String title, List<String> filters,
                                   String assignLabel, boolean assignActive, String footer, String empty) {
        List<ListLayout.Action> top = search(frame);
        top.add(ListLayout.Action.choice("filter", "Show", filters, 130, m));
        // Biome placement, Biome modifiers and Biome tags are one Data...
        if (assignLabel == null) {
            top.add(ListLayout.Action.of("data", DATA_MENU));
        }
        List<ListLayout.Action> actions = new ArrayList<>();
        if (assignLabel != null) {
            int w = Math.min(frame.contentWidth(), Math.max(160, m.width(assignLabel) + 16));
            actions.add(ListLayout.Action.of("assign", TextFit.clip(assignLabel, w - Layout.LABEL_MARGIN, m), w)
                    .withActive(assignActive));
        }
        return ListLayout.rightAlign(ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, List.of(),
                actions, List.of(), footer, empty)), frame, BIOME_TOOLS);
    }

    /** The Biomes list's buttons at the top row's right end. */
    public static final List<String> BIOME_TOOLS = List.of("data");

    /**
     * ChestLootScreen (as LootScreen): the loot entries by pool; + Add loot entry, Edit, Remove, Restore,
     * Restore all, Loot table.
     *
     * @param removeLabel "Remove", or "Remove 3" for several picked
     * @param picked a row is selected
     */
    public static Layout chestLoot(Frame frame, TextFit.Measure m, String title, String removeLabel, boolean picked,
                                   String footer, String empty) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, List.of(), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add loot entry").asMain(),
                        ListLayout.Action.of("edit", "Edit").withActive(picked),
                        ListLayout.Action.of("remove", removeLabel, 110).withActive(picked),
                        ListLayout.Action.of("restore", "Restore").withActive(picked),
                        ListLayout.Action.of("restoreAll", "Restore all"),
                        ListLayout.Action.of("table", "Loot table")),
                List.of(), footer, empty));
    }

    /**
     * TradesScreen (as ChestLootScreen): a trading mob's trades by list (profession and level); + Add trade,
     * Edit, Remove, Restore, Restore all.
     *
     * @param removeLabel "Remove", or "Remove 3" for several picked
     * @param picked a trade is selected
     */
    public static Layout tradeList(Frame frame, TextFit.Measure m, String title, String removeLabel, boolean picked,
                                   String footer, String empty) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, List.of(), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add trade").asMain(),
                        ListLayout.Action.of("edit", "Edit").withActive(picked),
                        ListLayout.Action.of("remove", removeLabel, 110).withActive(picked),
                        ListLayout.Action.of("restore", "Restore").withActive(picked),
                        ListLayout.Action.of("restoreAll", "Restore all")),
                List.of(), footer, empty));
    }

    /**
     * LootScreen: the drops; + Add drop, Edit, Remove or Deny drop, Restore all, Game table, and above the
     * list the group's controls ({@link GroupLayout}).
     *
     * @param groupText the picker's text, {@link GroupLayout#text} of it
     * @param canGroup a group is picked, so Apply and Edit are live
     */
    public static Layout lootList(Frame frame, TextFit.Measure m, String title, String removeLabel, String footer,
                                  String empty, String groupText, boolean canGroup) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title,
                GroupLayout.top(m, groupText, canGroup), List.of(),
                List.of(ListLayout.Action.of("add", "+ Add loot entry").asMain(),
                        ListLayout.Action.of("edit", "Edit"),
                        ListLayout.Action.of("remove", removeLabel, 150),
                        ListLayout.Action.of("restore", "Restore all"),
                        ListLayout.Action.of("table", "Loot table")),
                List.of(), footer, empty)
                .withUnderTop(GroupLayout.underTop()));
    }

    /** The Behaviors menu's column headings. */
    public static final List<String> GOAL_SET_HEADINGS = List.of("Mobs", "Goals", "Fights");
    /** The room the archetype's name and its users keep at a row's left. */
    public static final int GOAL_SET_NAME_ROOM = 200;
    /** The narrowest a column may be before the columns are hidden. */
    public static final int GOAL_SET_COLUMN_MIN = 30;

    /**
     * The Behaviors menu's column width: the widest heading and 10, within the room the rows leave; 0 when
     * that is too narrow and the columns hide.
     */
    public static int goalSetColumnWidth(Frame frame, TextFit.Measure m) {
        int widest = GOAL_SET_HEADINGS.stream().mapToInt(m::width).max().orElse(0);
        int room = (frame.contentWidth() - ListLayout.ROW_INSET - GOAL_SET_NAME_ROOM) / GOAL_SET_HEADINGS.size();
        int w = Math.min(widest + 10, room);
        return w >= GOAL_SET_COLUMN_MIN ? w : 0;
    }

    /**
     * GoalSetsScreen: the archetypes, grouped, each with its columns (mobs that use it, goals, fights) under
     * their headings; + Add archetype, Edit, Delete, Restore.
     *
     * @param canRestore the selected archetype is a family's
     */
    public static Layout goalSetsList(Frame frame, TextFit.Measure m, String title, boolean canRestore,
                                      String footer) {
        return goalSetsList(frame, m, title, canRestore, false, false, footer);
    }

    /**
     * The Behaviors screen, opened from Client or from a mob's Behavior button: ONE SCREEN.
     *
     * @param forMob opened from a mob's Behavior button
     * @param canUse the selected row is one the mob can use
     */
    public static Layout goalSetsList(Frame frame, TextFit.Measure m, String title, boolean canRestore,
                                      boolean forMob, boolean canUse, String footer) {
        // The search on the left, the buttons on its right.
        List<ListLayout.Action> top = new ArrayList<>(search(frame));
        ListLayout.Action add = ListLayout.Action.of("add", "+ Add goal set");
        List<ListLayout.Action> bar = new ArrayList<>();
        if (forMob) {
            top.add(ListLayout.Action.of("use", "Use for this mob").asMain().withActive(canUse));
            top.add(ListLayout.Action.of("editGoals", "Edit this mob's goals"));
            bar.add(add);
        } else {
            // + Add goal set in place of Edit a mob's goals on top.
            top.add(add.asMain());
        }
        bar.add(ListLayout.Action.of("edit", "Edit"));
        bar.add(ListLayout.Action.of("delete", "Remove"));
        bar.add(ListLayout.Action.of("restore", "Restore").withActive(canRestore));
        Layout layout = ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, List.of(), bar,
                List.of(), footer, null, GOAL_SET_HEADINGS, goalSetColumnWidth(frame, m)));
        // The buttons after the search sit at the right edge when they share its row.
        List<String> right = forMob ? List.of("use", "editGoals") : List.of("add");
        int searchY = layout.boxOf("search").y();
        if (right.stream().allMatch(id -> layout.boxOf(id).y() == searchY)) {
            int shift = frame.contentRight() - layout.get(right.getLast()).box().right();
            for (String id : right) {
                Layout.Element e = layout.get(id);
                Box b = e.box();
                layout.replace(new Layout.Element(id, e.kind(), new Box(b.x() + shift, b.y(), b.w(), b.h()), e.text(),
                        e.pinned(), e.active(), e.main(), e.hidden()));
            }
        }
        return layout;
    }
    /**
     * GoalsScreen: Save and the list's sources on top (a behavior's name field, or a mob's Start from
     * behavior; Start from game went in: Copy from mob does the same; Clear own list in ); the goals; + Add
     * goal, Edit, Remove, On / Off.
     */
    public static Layout goalsList(Frame frame, TextFit.Measure m, String title, boolean goalSet,
                                String footer, String fightLine) {
        List<ListLayout.Action> top = new ArrayList<>();
        // Undo before Save, as on a form: the unsaved goal changes, one at a time.
        top.add(ListLayout.Action.of("undo", "Undo"));
        top.add(ListLayout.Action.of("save", "Save").asMain());
        if (goalSet) {
            top.add(ListLayout.Action.field("name", 160));
        } else {
            // Name the list: it becomes a behavior the mob uses by name.
            top.add(ListLayout.Action.field("name", 120));
            top.add(ListLayout.Action.of("saveAs", "Save as goal set"));
        }
        top.add(ListLayout.Action.of("copy", "Copy from mob"));
        return ListLayout.build(frame, m, new ListLayout.Input(title, top, List.of(),
                List.of(ListLayout.Action.of("add", "+ Add goal").asMain(),
                        ListLayout.Action.of("edit", "Edit"),
                        ListLayout.Action.of("remove", "Remove"),
                        ListLayout.Action.of("toggle", "On / Off")),
                // What the list fights with, or what it lacks (Attacks#line), over the bar; its room is kept
                // when there is nothing to say.
                List.of(fightLine == null ? "" : fightLine), footer, null));
    }

    // The Scaling bar, kept as constants so the headless layout gate can check the row fits a narrow content
    // width (layout.35).
    public static final int SCALING_ADD_W = 120;
    public static final int SCALING_EDIT_W = 60;
    public static final int SCALING_TOGGLE_W = 110;
    public static final int SCALING_REMOVE_W = 80;

    /** The Scaling bar's width on one line. */
    public static int scalingBarWidth() {
        return SCALING_ADD_W + SCALING_EDIT_W + SCALING_TOGGLE_W + SCALING_REMOVE_W + Grid.GAP * 3;
    }

    /**
     * ScalingScreen: the two tabs and the shown tab's system switch ("Player scaling: Off"), then
     * Player Base Stats (live on the Player scaling tab); the curves; the select hint and the formula above
     * the bar; + Add attribute, Edit, On / Off, Remove.
     *
     * @param selectHint the hint while nothing is selected, or "" (its room is kept)
     * @param switchText the system switch's text
     */
    public static Layout scaling(Frame frame, TextFit.Measure m, String title, boolean mobsShown, String switchText,
                                 String chunkText, String selectHint, String formula, String footer) {
        List<ListLayout.Action> top = new ArrayList<>(List.of(ListLayout.Action.of("switch", switchText, 150)));
        if (chunkText != null) {
            top.add(ListLayout.Action.of("chunk", chunkText, 150));
        }
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title,
                top,
                List.of(),
                List.of(ListLayout.Action.of("add", "+ Add attribute", SCALING_ADD_W).asMain(),
                        ListLayout.Action.of("edit", "Edit", SCALING_EDIT_W),
                        ListLayout.Action.of("toggle", "On / Off", SCALING_TOGGLE_W),
                        ListLayout.Action.of("remove", "Remove", SCALING_REMOVE_W)),
                List.of(selectHint, formula), footer, null));
    }

    /**
     * ProfileScreen: the profiles; the name to save as, and the last result beside it; Save, Activate (which
     * loads the profile here and, for an operator on a server, onto the server), Delete, and, as one file,
     * Export file and Import, then Regenerate on next start and Reload server config.
     *
     * @param serverLines the status lines ("hint" plus the number), at most HELP_LINES
     */
    public static Layout profile(Frame frame, TextFit.Measure m, String title, String status,
                                 List<String> serverLines, String footer) {
        return ListLayout.build(frame, m, new ListLayout.Input(title, List.of(),
                List.of(FormLayout.Cell.field("name", Grid.fieldW(frame.contentWidth()), "Save the current config as"),
                        FormLayout.Cell.text("status", Math.max(40, frame.contentWidth() - Grid.fieldW(frame.contentWidth()) - 4), status)),
                List.of(ListLayout.Action.of("save", "Save").asMain(),
                        ListLayout.Action.of("activate", "Activate"),
                        ListLayout.Action.of("delete", "Remove"),
                        ListLayout.Action.of("export", "Export file"),
                        ListLayout.Action.of("import", "Import..."),
                        // The old Server screen's two actions: Activate itself loads the profile onto the
                        // server.
                        ListLayout.Action.of("regenerate", "Regenerate on next start"),
                        ListLayout.Action.of("reload", "Reload server config"),
                        ListLayout.Action.of("server", SERVER_BUTTON, m.width(SERVER_BUTTON) + 16)),
                // The server status lines (singleplayer or remote, operator) over the bar.
                new ArrayList<>(serverLines), footer, null));
    }

    /** The top row of Options: empty. */
    public static List<String> menuTools() {
        return List.of();
    }

    /**
     * StrataOptionsScreen: Strata's settings, from strata-common.toml; the help and when a change applies,
     * above the bar; the value, Set value, Toggle (a switch), Restore.
     *
     * @param toggleActive the selected setting is a switch
     * @param setActive the selected setting takes a typed value
     */
    /** The Profiles screen's one-click server button. */
    public static final String SERVER_BUTTON = "Load active profile to server";

    public static Layout settings(Frame frame, TextFit.Measure m, String title, List<String> tools, String help,
                                  boolean toggleActive, boolean setActive, String footer) {
        List<ListLayout.Action> top = new ArrayList<>();
        for (int i = 0; i < tools.size(); i++) {
            top.add(ListLayout.Action.of("tool" + i, tools.get(i), Math.max(Grid.BTN_W, m.width(tools.get(i)) + 16)));
        }
        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, m));
        while (hints.size() < HELP_LINES) {
            hints.add("");
        }
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, top, List.of(),
                List.of(ListLayout.Action.field("value", 140),
                        ListLayout.Action.of("set", "Set value").withActive(setActive),
                        ListLayout.Action.of("toggle", "On / Off").withActive(toggleActive),
                        ListLayout.Action.of("restore", "Restore")),
                hints, footer, null));
    }

    /** The custom assets library's hint. */
    public static final String CUSTOM_ASSETS_HINT = "Textures are PNG; sounds are Ogg Vorbis (mono for sounds that"
            + " fade with distance). Pick one from a mob's Visuals or Sounds list. Re-import swaps a file in place.";

    /**
     * CustomAssetsScreen: the custom textures and sounds of this machine; Add, Re-import, Remove.
     *
     * @param picked an asset is selected
     */
    public static Layout customAssets(Frame frame, TextFit.Measure m, String title, boolean picked, String footer,
                                      String empty) {
        return ListLayout.build(frame, m, new ListLayout.Input(title,
                List.of(ListLayout.Action.of("add", "+ Add custom...").asMain()), List.of(),
                List.of(ListLayout.Action.of("reimport", "Re-import").withActive(picked),
                        ListLayout.Action.of("remove", "Remove").withActive(picked)),
                wrappedHelp(CUSTOM_ASSETS_HINT, frame, m), footer, empty));
    }

    /**
     * AssetMasterScreen: every sound or texture the game loaded; search; Play (sounds), Replace..., Restore.
     */
    public static Layout assetMaster(Frame frame, TextFit.Measure m, String title, String kind, String footer) {
        boolean sound = com.strata.domain.CustomAssetsOps.SOUND.equals(kind);
        List<ListLayout.Action> bottom = new java.util.ArrayList<>();
        if (sound) {
            bottom.add(ListLayout.Action.of("play", "Play"));
        }
        bottom.add(ListLayout.Action.of("replace", "Replace...").asMain());
        bottom.add(ListLayout.Action.of("restore", "Restore"));
        return ListLayout.build(frame, m, new ListLayout.Input(title,
                List.of(ListLayout.Action.field("search", 160)), List.of(), bottom,
                wrappedHelp(sound ? "Every sound the game knows. A click plays it. Replace... puts a custom Ogg"
                        + " Vorbis sound in its place; Restore puts the game's back."
                        : com.strata.domain.CustomAssetsOps.MODEL.equals(kind)
                        ? "Every block and item model the game loaded. Replace... puts a custom JSON model in its"
                        + " place; Restore puts the game's back. A mob's model is game code, not a file."
                        : "Every texture the game loaded. Replace... puts a custom PNG in its place; Restore puts"
                        + " the game's back. Keep the size of the original, or a model looks wrong.", frame, m),
                footer, null));
    }

    /** The Special mobs list's hint. */
    public static final String SPECIAL_HINT = "The game's special spawners, and the mobs set to Special. Edit (or"
            + " double-click) a spawner to change its values. Special mob spawns Off stops them all here.";

    /**
     * SpecialMobsScreen: the Special mob spawns switch on top; the spawners and the Special mobs, grouped as
     * the mob list; Edit, Restore all.
     *
     * @param picked a spawner is selected
     */
    public static Layout specialMobsList(Frame frame, TextFit.Measure m, String title, String switchText,
                                         boolean picked, String footer) {
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title,
                List.of(ListLayout.Action.of("switch", switchText, 170)), List.of(),
                List.of(ListLayout.Action.of("edit", "Edit").asMain().withActive(picked),
                        ListLayout.Action.of("restoreAll", "Restore all")),
                wrappedHelp(SPECIAL_HINT, frame, m), footer, null));
    }

    /** The help above a value sheet's bar takes at most this many lines. */
    public static final int HELP_LINES = 2;

    /**
     * A value sheet: LootTableScreen, MobCapScreen, BiomeDataScreen.
     *
     * @param valueW the value field's width
     */
    public static Layout valueSheet(Frame frame, TextFit.Measure m, String title, int valueW, String help,
                                    String footer, String empty) {
        return valueSheet(frame, m, title, valueW, help, footer, empty, false);
    }

    /**
     * BlockStatScreen: a value sheet with Drops on top, the block's loot table in the loot editor.
     *
     * @param dropsShown the Loot button is placed (a plain block's sheet, not a variant's)
     */
    public static Layout blockSheet(Frame frame, TextFit.Measure m, String title, String help, boolean dropsShown,
                                    boolean setActive, String empty) {
        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, m));
        while (hints.size() < HELP_LINES) {
            hints.add("");
        }
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title,
                dropsShown ? List.of(ListLayout.Action.of("drops", "Loot")) : List.of(), List.of(),
                List.of(ListLayout.Action.field("value", 140),
                        ListLayout.Action.of("set", "Set value").withActive(setActive),
                        ListLayout.Action.of("restore", "Restore")),
                hints, null, empty));
    }

    /** A value sheet with the search on top (a loot table holds over 100 values). */
    public static Layout valueSheet(Frame frame, TextFit.Measure m, String title, int valueW, String help,
                                    String footer, String empty, boolean withSearch) {
        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, m));
        while (hints.size() < HELP_LINES) {
            hints.add("");
        }
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, withSearch ? search(frame) : List.of(), List.of(),
                List.of(ListLayout.Action.field("value", valueW),
                        ListLayout.Action.of("set", "Set value"),
                        ListLayout.Action.of("restore", "Restore")),
                hints, footer, empty));
    }

    /**
     * SpecialSpawnsScreen: a value sheet of the special spawners' values; the value (a dropdown, a picker, or
     * a field by the row's type), Set value, Restore, Restore all.
     */
    public static Layout specialSpawns(Frame frame, TextFit.Measure m, String title, String help,
                                       String footer, String empty) {
        List<String> hints = new ArrayList<>(TextFit.wrap(help, frame.contentWidth(), HELP_LINES, m));
        while (hints.size() < HELP_LINES) {
            hints.add("");
        }
        return ListLayout.buildWithUndo(frame, m, new ListLayout.Input(title, List.of(), List.of(),
                List.of(ListLayout.Action.field("value", 140),
                        ListLayout.Action.of("set", "Set value"),
                        ListLayout.Action.of("restore", "Restore"),
                        ListLayout.Action.of("restoreAll", "Restore all")),
                hints, footer, empty));
    }

    /** RegistryPickerScreen: the search, filling its row, and the group filter; the options. */
    public static Layout picker(Frame frame, TextFit.Measure m, String title, List<String> groups) {
        return picker(frame, m, title, groups, false);
    }

    /** @param sound true for the sound picker: a click plays, Pick takes the selected sound */
    public static Layout picker(Frame frame, TextFit.Measure m, String title, List<String> groups, boolean sound) {
        List<ListLayout.Action> top = new java.util.ArrayList<>();
        top.add(ListLayout.Action.field("search", 100).filling());
        top.add(ListLayout.Action.dataChoice("group", "Show", groups, 150, frame.contentWidth() / 2, m));
        return ListLayout.build(frame, m, new ListLayout.Input(title, top, List.of(),
                sound ? List.of(ListLayout.Action.of("play", "Play"), ListLayout.Action.of("pick", "Pick"))
                        : List.of(),
                List.of(), null, null));
    }
}
