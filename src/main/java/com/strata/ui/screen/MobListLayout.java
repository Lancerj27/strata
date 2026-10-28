package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** THE MOB LIST, LAID OUT: MobListScreen. */
public final class MobListLayout {

    private MobListLayout() {
    }

    /** The Edit picked button's width. */
    public static final int BATCH_W = Grid.buttonW("Edit picked");
    /** The Fill from biome default button's width. */
    static final int FILL_W = Grid.buttonW("Add default mobs");
    /** Replace mob, on a scoped view. */
    static final int REPLACE_W = Grid.buttonW("Replace mob");
    /**
     * The room at a row's left the columns keep clear of: the search, its clear button, and + Add mob on the
     * line of the headings, and a gap.
     */
    public static final int NAME_ROOM = 254;
    /** The search field's width, as the room NAME_ROOM keeps was cut for. */
    public static final int SEARCH_W = 130;

    /** The search field on a wide column; SEARCH_W on a small one. */
    static int searchW(Frame frame) {
        return Grid.wide(SEARCH_W, frame.contentWidth(), 20, 240);
    }

    /** The widest a spread-out stat column is drawn. */
    public static final int COLUMN_MAX_W = 300;

    /**
     * The width of one stat column: THE FREE WIDTH SHARED OUT EVENLY, up to COLUMN_MAX_W each; 0 when the
     * share is under SHOW_MIN_W and the columns are hidden.
     */
    public static int columnWidth(Frame frame, TextFit.Measure measure, List<String> headings) {
        if (headings.isEmpty()) {
            return 0;
        }
        int share = columnRoom(frame) / headings.size();
        int w = Math.min(share, COLUMN_MAX_W);
        return w >= GlanceLayout.SHOW_MIN_W ? w : 0;
    }

    /**
     * The room the columns share, from the rows' real right edge (ListLayout#columnLeft), not the content's:
     * the first heading lay 1 px over + Add mob at 427 wide.
     */
    public static int columnRoom(Frame frame) {
        return Math.max(0, GlanceLayout.room(frame, NAME_ROOM) - (searchW(frame) - SEARCH_W));
    }

    /**
     * @param tabs the tabs' labels
     * @param activeTab the tab shown, which is greyed out
     * @param scoped a tier or biome view: it has + Add mob
     * @param headings the stat columns' headings, one per column
     * @param removeLabel a tier or biome view's Remove button text, under the list, or null for none
     * @param mobCapsShown Mob caps and Goal sets are placed: the Global view only (both are global)
     * @param specialShown Special spawns is placed: the Global view, or one tier or biome
     * @param fillActive Game spawns and Replace mob act (a world is open)
     * @param oneScope the scoped view names one tier or biome: Game spawns and Replace mob are placed
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, List<String> tabs, int activeTab,
                               boolean scoped, List<String> headings, String removeLabel, boolean removeActive,
                               boolean mobCapsShown, boolean specialShown, boolean fillActive, boolean oneScope,
                               String footer, String empty) {
        Layout layout = Layout.framed(frame, measure, title);
        int x = frame.contentLeft();
        // THE TOP ROW: the scope tabs, then Mob caps, Special spawns and Goal sets, which left the bottom
        // bar.
        List<String> toolIds = new ArrayList<>();
        List<String> toolTexts = new ArrayList<>();
        if (mobCapsShown) {
            toolIds.add("mobCaps");
            toolTexts.add("Mob caps");
        }
        if (specialShown) {
            toolIds.add("special");
            toolTexts.add("Special spawns");
        }
        if (mobCapsShown) {
            toolIds.add("goalSets");
            toolTexts.add("Goal sets");
        }
        // Data...
        if (mobCapsShown) {
            toolIds.add("data");
            toolTexts.add(ListScreenLayouts.DATA_MENU);
        }
        int[] tabWidths = java.util.stream.IntStream.concat(tabs.stream().mapToInt(t -> Grid.TAB_W),
                toolTexts.stream().mapToInt(Grid::buttonW)).toArray();
        Flow.Placed tabRow = Flow.row(x, frame.contentRight(), Grid.CONTENT_Y, Grid.CTRL_H, Grid.TAB_GAP,
                tabWidths);
        for (int i = 0; i < tabs.size(); i++) {
            layout.add(Layout.control("tab" + i, Layout.Kind.BUTTON, tabRow.boxes().get(i), tabs.get(i))
                    .withActive(i != activeTab));
        }
        boolean oneLine = true;
        for (int i = 0; i < toolIds.size(); i++) {
            oneLine &= tabRow.boxes().get(tabs.size() + i).y() == tabRow.boxes().get(0).y();
        }
        int shift = 0;
        if (oneLine && !toolIds.isEmpty()) {
            shift = frame.contentRight() - tabRow.boxes().get(tabs.size() + toolIds.size() - 1).right();
        }
        for (int i = 0; i < toolIds.size(); i++) {
            Box b = tabRow.boxes().get(tabs.size() + i);
            layout.add(Layout.control(toolIds.get(i), Layout.Kind.BUTTON,
                    new Box(b.x() + shift, b.y(), b.w(), b.h()), toolTexts.get(i)));
        }
        int rowY = tabRow.bottom() + 2;
        // No Clear button.
        List<String> ids = new ArrayList<>(List.of("search"));
        List<Integer> widths = new ArrayList<>(List.of(searchW(frame)));
        if (scoped) {
            ids.add("addMob");
            widths.add(Grid.BTN_W);
        }
        // + Add mob sits after the search.
        int[] xs = {x, x + searchW(frame) + 4};
        for (int i = 0; i < ids.size(); i++) {
            Layout.Kind kind = ids.get(i).equals("search") ? Layout.Kind.FIELD : Layout.Kind.BUTTON;
            String label = ids.get(i).equals("addMob") ? "+ Add mob" : "";
            Box at = new Box(xs[i], rowY, widths.get(i), Grid.CTRL_H);
            if (kind == Layout.Kind.FIELD) {
                // THE SEARCH'S TITLE BESIDE IT.
                int room = measure.width("Search") + ListLayout.TITLE_GAP;
                layout.add(layout.text("label.search", at.x(), at.y() + (at.h() - Layout.LINE_H) / 2, "Search",
                        Integer.MAX_VALUE));
                at = new Box(at.x() + room, at.y(), at.w() - room, at.h());
            }
            Layout.Element e = Layout.control(ids.get(i), kind, at, label);
            layout.add(ids.get(i).equals("addMob") ? e.asMain() : e);
        }
        int listTop = rowY + 24;
        int listBottom = frame.footerY() - ListLayout.BOTTOM_MARGIN - ListLayout.LIST_GAP;
        // Undo, Batch..., then Remove, under the list, over the count.
        List<String> barIds = new ArrayList<>(List.of("undo", "batch", "biomes"));
        List<Integer> barWidths = new ArrayList<>(List.of(Grid.BTN_W, BATCH_W, Grid.BTN_W));
        if (scoped) {
            barIds.add("fill");
            barWidths.add(FILL_W);
        }
        if (scoped && oneScope) {
            barIds.add("replace");
            barWidths.add(REPLACE_W);
        }
        if (removeLabel != null) {
            barIds.add("remove");
            barWidths.add(Math.max(110, measure.width(removeLabel) + 16));
        }
        Flow.Placed bar = Flow.bar(x, frame.contentRight(), frame.footerY() - Grid.CTRL_H - 4,
                Grid.CTRL_H, Grid.GAP, barWidths.stream().mapToInt(Integer::intValue).toArray());
        for (int i = 0; i < barIds.size(); i++) {
            Box b = bar.boxes().get(i);
            switch (barIds.get(i)) {
                case "undo" -> layout.add(Layout.control("undo", Layout.Kind.BUTTON, b, "Undo"));
                case "batch" -> layout.add(Layout.control("batch", Layout.Kind.BUTTON, b, "Edit picked"));
                // Every biome where the selected mob spawns naturally, each On or Off.
                case "biomes" -> layout.add(Layout.control("biomes", Layout.Kind.BUTTON, b, "Biomes"));
                case "fill" -> layout.add(Layout.control("fill", Layout.Kind.BUTTON, b, "Add default mobs")
                        .withActive(fillActive));
                // One tier or biome only: a replacement names its scope.
                case "replace" -> layout.add(Layout.control("replace", Layout.Kind.BUTTON, b, "Replace mob")
                        .withActive(fillActive));
                default -> layout.add(Layout.control("remove", Layout.Kind.BUTTON, b,
                        TextFit.clip(removeLabel, b.w() - Layout.LABEL_MARGIN, measure)).withActive(removeActive));
            }
        }
        listBottom = bar.top() - ListLayout.LIST_GAP;
        Box list = new Box(x, listTop, frame.contentWidth(), Math.max(22, listBottom - listTop));
        layout.add(Layout.control("list", Layout.Kind.LIST, list, ""));

        // STAT COLUMNS: the headings over the columns, drawn smaller when a heading is wider than its column.
        int columnW = columnWidth(frame, measure, headings);
        if (columnW > 0) {
            for (int i = 0; i < headings.size(); i++) {
                int left = ListLayout.columnLeft(list, i, headings.size(), columnW);
                layout.add(layout.fitted("heading" + i, left + columnW / 2, rowY + Grid.CTRL_H / 2 - 4,
                        headings.get(i), columnW - 4));
            }
        }
        if (empty != null) {
            layout.add(layout.note("empty", x + 4, listTop + 8, empty, frame.contentWidth() - 8));
        }
        layout.add(layout.text("footer", x, frame.footerY(), footer, frame.contentWidth()));
        return layout;
    }
}
