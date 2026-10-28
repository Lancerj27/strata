package com.strata.client.widget;

import com.strata.ui.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Scrollable list with search filtering. */
public class BrowseList extends ObjectSelectionList<BrowseList.Entry> {

    /** Width of the right-hand cell (inline chance field) on a row: the UI layer's. */
    public static final int ACTION_W = com.strata.ui.screen.ListRowLayout.CHANCE_W;
    /** Margin from the row's right edge to that cell. */
    public static final int ACTION_GAP = com.strata.ui.screen.ListRowLayout.CHANCE_GAP;

    private final List<Entry> allEntries = new ArrayList<>();
    /** The groups the designer opened. */
    private final Set<String> toggledGroups = new HashSet<>();
    /** The key this list's folds are remembered under for the session, or null. */
    private String foldKey;
    /** The rows given are a search's matches, filtered by the screen: show them unfolded. */
    private boolean searching;

    /** Remembers this list's folds under a key for the session, and takes the folds last left under it. */
    public void rememberFolds(String key) {
        if (key == null || key.equals(foldKey)) {
            return;
        }
        foldKey = key;
        toggledGroups.clear();
        toggledGroups.addAll(com.strata.client.util.FoldMemory.toggled(key));
        applyFilter();
    }

    /**
     * For a screen that filters its rows itself (the mob, biome, and ore lists, mob data): true while its
     * search has text, so a folded group never hides a match.
     */
    public void setSearching(boolean on) {
        this.searching = on;
    }
    /** A list whose only top-level group is shown OPEN unless the designer folded it. */
    private boolean openOnlyGroup;

    public void setOpenOnlyGroup(boolean on) {
        this.openOnlyGroup = on;
    }

    /** The groups shown open: those the designer toggled, with an only top group flipped (open by default). */
    private Set<String> effectiveToggled() {
        if (!openOnlyGroup) {
            return toggledGroups;
        }
        String only = null;
        for (Entry e : allEntries) {
            if (e.groupId != null && e.parentGroup == null) {
                if (only != null) {
                    return toggledGroups;
                }
                only = e.groupId;
            }
        }
        if (only == null) {
            return toggledGroups;
        }
        Set<String> out = new HashSet<>(toggledGroups);
        if (!out.remove(only)) {
            out.add(only);
        }
        return out;
    }
    private Consumer<Entry> onSelect;
    /** The rows picked with Ctrl and Shift. */
    private Set<String> picked = new java.util.LinkedHashSet<>();
    /** The row a Shift-click extends from: the last plain or Ctrl click. */
    private String anchor;
    /** Called with a row that was double-clicked, or null. */
    private Consumer<Entry> onActivate;
    /** The row and the time of the last plain click, for a double-click. */
    private String lastClickId;
    private long lastClickAt;

    /** DRAG A ROW ONTO ANOTHER. */
    private java.util.function.BiConsumer<String, String> onReorder;
    /** The row a press started on, while a reorder is possible. */
    private Entry dragFrom;
    /** True once the mouse moved with the button held: a drag, not a click. */
    private boolean dragging;

    /** Lets rows be dragged onto each other; see {@link #onReorder}. */
    public void setOnReorder(java.util.function.BiConsumer<String, String> onReorder) {
        this.onReorder = onReorder;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (onReorder != null && dragFrom != null && button == 0) {
            dragging = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragFrom != null) {
            Entry from = dragFrom;
            boolean wasDrag = dragging;
            dragFrom = null;
            dragging = false;
            if (wasDrag) {
                // As in mouseClicked: getEntryAtPosition bounds only the row index, so a drop below the list
                // would name a row the drag never reached.
                Entry to = isMouseOver(mx, my) ? getEntryAtPosition(mx, my) : null;
                if (to != null && to != from && to.groupId == null) {
                    onReorder.accept(from.id, to.id);
                }
                return true;
            }
        }
        return super.mouseReleased(mx, my, button);
    }

    /** The tooltip of the row under the mouse, or null. */
    public String tooltipAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return null;
        }
        Entry e = getEntryAtPosition(mouseX, mouseY);
        return e == null ? null : e.tooltip;
    }

    /** True while this entry is the row being dragged. */
    boolean isDragged(Entry e) {
        return dragging && dragFrom == e;
    }

    /** True while a drag is on. */
    boolean reordering() {
        return dragging;
    }
    private String filter = "";

    /** The row whose inline chance cell is being typed into, or null. */
    private Entry editingEntry;
    /** The width of one right-side column, or 0 for a list without columns. */
    private int columnWidth;

    /** Sets the width of the rows' right-side columns. */
    public void setColumnWidth(int width) {
        this.columnWidth = Math.max(0, width);
    }

    /** The x of column i's left edge, for headings drawn above the list. */
    public int columnLeft(int i, int count) {
        return getRowLeft() + getRowWidth() - (count - i) * columnWidth;
    }
    /** Digits typed so far into that cell. */
    private String editBuffer = "";
    /** True until the first keystroke, so typing REPLACES the old value. */
    private boolean editFresh;

    /** Max digits in a chance cell — "100" is the widest legal value. */
    public static final int CHANCE_MAX_DIGITS = 3;

    /** Digit accumulation for an inline chance cell. */
    public static String typeChanceDigit(String buffer, boolean fresh, char typed) {
        if (typed < '0' || typed > '9') {
            return buffer;
        }
        String base = fresh ? "" : buffer;
        if (base.length() >= CHANCE_MAX_DIGITS) {
            return base;
        }
        return base + typed;
    }

    /** Backspace for the chance cell. */
    public static String backspaceChance(String buffer) {
        return buffer.isEmpty() ? buffer : buffer.substring(0, buffer.length() - 1);
    }

    /** What an edited cell commits. */
    public static int commitChance(String buffer) {
        if (buffer.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(buffer)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public BrowseList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void setOnSelect(Consumer<Entry> onSelect) {
        this.onSelect = onSelect;
    }

    /** Called with a row that was double-clicked (ui.Keys#isDoubleClick). */
    public void setOnActivate(Consumer<Entry> onActivate) {
        this.onActivate = onActivate;
    }

    /** A pick: the rows, and the row the next Shift-click extends from. */
    public record Pick(Set<String> ids, String anchor) {
    }

    /**
     * What a click picks, as a desktop list does.
     *
     * @param order the pickable rows as shown, top to bottom
     */
    public static Pick pick(List<String> order, Set<String> current, String anchor, String clicked,
                            boolean ctrl, boolean shift) {
        Set<String> out = new java.util.LinkedHashSet<>();
        if (shift && anchor != null && order.contains(anchor) && order.contains(clicked)) {
            if (ctrl) {
                out.addAll(current);
            }
            int a = order.indexOf(anchor);
            int b = order.indexOf(clicked);
            out.addAll(order.subList(Math.min(a, b), Math.max(a, b) + 1));
            return new Pick(out, anchor);
        }
        if (ctrl) {
            out.addAll(current);
            if (!out.remove(clicked)) {
                out.add(clicked);
            }
            return new Pick(out, clicked);
        }
        out.add(clicked);
        return new Pick(out, clicked);
    }

    /**
     * The ids of the rows an action applies to, top to bottom: the picked rows, else the selected row, else
     * none.
     */
    public List<String> pickedIds() {
        List<String> out = new ArrayList<>();
        // EVERY PICKED ROW, folded or not.
        for (Entry e : allEntries) {
            // A row filed twice (Recent and its category) shares one id: name it once.
            if (picked.contains(e.id) && e.groupId == null && !out.contains(e.id)) {
                out.add(e.id);
            }
        }
        if (out.isEmpty() && getSelected() != null && getSelected().groupId == null) {
            out.add(getSelected().id);
        }
        return out;
    }

    /** True while a row's chance cell is being typed in: Esc then cancels the cell. */
    public boolean editing() {
        return editingEntry != null;
    }

    /** True when more than one row is picked. */
    public boolean manyPicked() {
        return pickedIds().size() > 1;
    }

    /**
     * Consumes clicks on a row's inline chance cell before the row selection logic runs — typing a percentage
     * must not also select the row.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // A click BELOW the list (the button row) still maps to a row in getEntryAtPosition, which bounds
            // only the row index, never the widget rectangle, so a scrolled list returns a row the click
            // never touched.
            if (!isMouseOver(mx, my)) {
                commitEdit();
                return super.mouseClicked(mx, my, button);
            }
            Entry entry = getEntryAtPosition(mx, my);
            int rowRight = getRowLeft() + getRowWidth();
            boolean inCell = mx >= rowRight - ACTION_W - ACTION_GAP
                    && mx <= rowRight - ACTION_GAP;

            // An inline chance cell takes the click before row selection, so editing a percentage never also
            // cycles the row's state.
            if (entry != null && entry.onChanceCommit != null && inCell) {
                commitEdit();
                editingEntry = entry;
                editBuffer = entry.chanceValue == null ? "" : String.valueOf(entry.chanceValue);
                editFresh = true;
                setFocused(true);
                return true;
            }

            // A click anywhere else ends an edit in progress, keeping the typed value rather than discarding
            // it.
            commitEdit();

            // A press on a row may start a drag.
            dragFrom = onReorder != null && entry != null && entry.groupId == null ? entry : null;
            dragging = false;

            // CTRL OR SHIFT ON A GROUP PICKS THE GROUP: every row under it, nested groups too.
            boolean groupPick = entry != null && entry.groupId != null
                    && (net.minecraft.client.gui.screens.Screen.hasControlDown()
                    || net.minecraft.client.gui.screens.Screen.hasShiftDown());
            if (groupPick) {
                List<Node> nodes = new ArrayList<>();
                for (Entry e : allEntries) {
                    nodes.add(new Node(e.id, e.label, e.groupId, e.parentGroup));
                }
                Set<String> next = new java.util.LinkedHashSet<>(picked);
                next.addAll(rowsUnder(nodes, entry.groupId));
                picked = next;
                return true;
            }
            // CTRL AND SHIFT PICK ROWS.
            if (entry != null && entry.groupId == null) {
                boolean ctrl = net.minecraft.client.gui.screens.Screen.hasControlDown();
                boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                List<String> order = new ArrayList<>();
                for (Entry e : children()) {
                    if (e.groupId == null && !order.contains(e.id)) {
                        order.add(e.id);
                    }
                }
                Pick next = pick(order, picked, anchor, entry.id, ctrl, shift);
                picked = next.ids();
                anchor = next.anchor();
                if (ctrl || shift) {
                    super.setSelected(entry);
                    lastClickId = null;
                    return true;
                }
                // A PLAIN CLICK selects the row, and the second of two on one row opens it.
                boolean handled = super.mouseClicked(mx, my, button);
                long now = net.minecraft.Util.getMillis();
                if (onActivate != null && com.strata.ui.Keys.isDoubleClick(lastClickId, lastClickAt, entry.id, now)) {
                    lastClickId = null;
                    onActivate.accept(entry);
                } else {
                    lastClickId = entry.id;
                    lastClickAt = now;
                }
                return handled;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editingEntry != null) {
            switch (key) {
                case com.strata.ui.Keys.ENTER, com.strata.ui.Keys.KEYPAD_ENTER -> {
                    commitEdit();
                    return true;
                }
                case com.strata.ui.Keys.ESCAPE -> {  // abandon the edit, keep the old value
                    cancelEdit();
                    return true;
                }
                case com.strata.ui.Keys.BACKSPACE -> {
                    editBuffer = backspaceChance(editFresh ? "" : editBuffer);
                    editFresh = false;
                    return true;
                }
                default -> { }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char typed, int mods) {
        if (editingEntry != null) {
            String next = typeChanceDigit(editBuffer, editFresh, typed);
            if (!next.equals(editFresh ? "" : editBuffer)) {
                editBuffer = next;
                editFresh = false;
                return true;
            }
            // Swallow non-digits so they cannot fall through to list navigation while a cell has the
            // keyboard.
            return true;
        }
        return super.charTyped(typed, mods);
    }

    /** Applies the typed value to its row, if an edit is open. */
    private void commitEdit() {
        if (editingEntry == null) {
            return;
        }
        Entry target = editingEntry;
        String buffer = editBuffer;
        boolean untouched = editFresh;
        cancelEdit();
        if (!untouched && target.onChanceCommit != null) {
            target.onChanceCommit.accept(commitChance(buffer));
        }
    }

    private void cancelEdit() {
        editingEntry = null;
        editBuffer = "";
        editFresh = false;
    }

    /**
     * What a designer has done to a list: the filter, the groups they opened or folded (toggled from how each
     * starts), and the scroll position.
     */
    public record State(String filter, Set<String> toggled, double scroll) {
    }

    /** This list's state, to hand to the list that replaces it. */
    public State state() {
        return new State(filter, Set.copyOf(toggledGroups), getScrollAmount());
    }

    /** Puts a saved state back, after {@link #refresh}. */
    public void restore(State state) {
        if (state == null) {
            return;
        }
        filter = state.filter();
        toggledGroups.clear();
        toggledGroups.addAll(state.toggled());
        com.strata.client.util.FoldMemory.remember(foldKey, toggledGroups);
        applyFilter();
        setScrollAmount(state.scroll());
    }

    public void setFilter(String text) {
        this.filter = text.toLowerCase();
        applyFilter();
    }

    /** Every row the list holds, folded or not: what the UI tour (UiTour) writes down. */
    public List<Entry> allRows() {
        return List.copyOf(allEntries);
    }

    /** The mod a row comes from, for the Minecraft / mod headings; null while the list has none. */
    private java.util.function.Function<Entry, String> sourceOf;

    /**
     * GROUPS THE LIST BY MOD: Minecraft's rows first, then each mod's, each under its own heading over the
     * list's own groups.
     *
     * @param sourceOf a row's namespace ("minecraft", "create"), or null to follow the row before it
     */
    public BrowseList bySource(java.util.function.Function<Entry, String> sourceOf) {
        this.sourceOf = sourceOf;
        return this;
    }

    /** A row id's leading index ("12", "12@plains"), or -1 for any other id. */
    public static int leadingIndex(String id) {
        int end = 0;
        while (end < id.length() && Character.isDigit(id.charAt(end))) {
            end++;
        }
        return end == 0 || end > 9 ? -1 : Integer.parseInt(id.substring(0, end));
    }

    /** The namespace of the id a row's index points at in a list, or null for a row with no index. */
    public static <T> String sourceAt(Entry e, List<T> rows, java.util.function.Function<T, String> idOf) {
        int i = leadingIndex(e.id);
        return i < 0 || i >= rows.size() ? null : namespace(idOf.apply(rows.get(i)));
    }

    /** The heading for a namespace: Minecraft, or the mod's id as it wrote it. */
    public static String sourceName(String ns) {
        return "minecraft".equals(ns) ? "Minecraft" : ns;
    }

    /** The namespace of an id, "minecraft" for one without; null for blank. */
    public static String namespace(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String s = id.strip();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        int c = s.indexOf(':');
        return c > 0 ? s.substring(0, c) : "minecraft";
    }

    /** The rows regrouped under their mods' headings; the rows' own ids are kept. */
    static List<Entry> regroup(List<Entry> entries, java.util.function.Function<Entry, String> sourceOf) {
        Map<Entry, String> source = new java.util.IdentityHashMap<>();
        Map<String, Entry> groups = new HashMap<>();
        String last = "minecraft";
        for (Entry e : entries) {
            if (e.groupId != null) {
                groups.put(e.groupId, e);
                continue;
            }
            String s = sourceOf.apply(e);
            last = s == null ? last : s;
            source.put(e, last);
        }
        java.util.TreeSet<String> sources = new java.util.TreeSet<>((a, b) -> "minecraft".equals(a) ? ("minecraft".equals(b) ? 0 : -1)
                : "minecraft".equals(b) ? 1 : a.compareTo(b));
        sources.addAll(source.values());
        if (sources.size() < 2) {
            return entries;
        }
        // Each group's count of each mod's rows, through every group above the row.
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        source.forEach((row, s) -> {
            Set<String> seen = new HashSet<>();
            for (String g = row.parentGroup; g != null && seen.add(g); g = groups.containsKey(g) ? groups.get(g).parentGroup : null) {
                counts.computeIfAbsent(g, k -> new HashMap<>()).merge(s, 1, Integer::sum);
            }
        });
        List<Entry> out = new ArrayList<>();
        for (String s : sources) {
            String top = "src:" + s;
            long n = source.values().stream().filter(s::equals).count();
            out.add(new Entry(top, sourceName(s) + " (" + n + ")", "", Palette.GROUP, top, null, 0));
            for (Entry e : entries) {
                if (e.groupId != null) {
                    int here = counts.getOrDefault(e.groupId, Map.of()).getOrDefault(s, 0);
                    if (here == 0) {
                        continue;
                    }
                    String label = e.label.matches(".* \\(\\d+\\)") ? e.label.replaceAll(" \\(\\d+\\)$", " (" + here + ")") : e.label;
                    out.add(e.moved(top + "/" + e.groupId, e.parentGroup == null ? top : top + "/" + e.parentGroup,
                            e.indent + 1, label));
                } else if (s.equals(source.get(e))) {
                    out.add(e.moved(null, e.parentGroup == null ? top : top + "/" + e.parentGroup, e.indent + 1, e.label));
                }
            }
        }
        return out;
    }

    /** The kind of list for its Recent group (RecentEdits), or null for none. */
    private String recentKind;

    /** Gives the list a Recent group: the rows of this kind written to this session, newest first. */
    public BrowseList setRecent(String kind) {
        this.recentKind = kind;
        return this;
    }

    /** The kind of list for its Recent group, or null. */
    public String recentKind() {
        return recentKind;
    }

    /** The group id of the Recent group. */
    public static final String RECENT_GROUP = "__recent";

    /** The rows with a Recent group on top: a copy of each data row whose id is recent, newest first. */
    public static List<Entry> withRecent(List<Entry> entries, List<String> recentIds) {
        java.util.Map<String, Entry> byId = new java.util.LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.groupId == null && recentIds.contains(e.id)) {
                byId.putIfAbsent(e.id, e);
            }
        }
        if (byId.isEmpty()) {
            return entries;
        }
        List<Entry> out = new ArrayList<>();
        int n = 0;
        for (String id : recentIds) {
            if (byId.containsKey(id)) n++;
        }
        out.add(new Entry(RECENT_GROUP, "Recent (" + n + ")", "", Palette.GROUP, RECENT_GROUP, null, 0));
        for (String id : recentIds) {
            Entry e = byId.get(id);
            if (e != null) {
                out.add(e.moved(null, RECENT_GROUP, 1, e.label));
            }
        }
        out.addAll(entries);
        return out;
    }

    public void refresh(List<Entry> entries) {
        if (sourceOf != null) {
            entries = regroup(entries, sourceOf);
        }
        if (recentKind != null) {
            entries = withRecent(entries, com.strata.client.util.RecentEdits.recentRows(recentKind));
        }
        // A rebuild replaces the Entry objects, so any open cell edit belongs to a row that no longer exists
        // — drop it rather than commit it into whichever row happens to land at that position.
        cancelEdit();
        picked = new java.util.LinkedHashSet<>();
        anchor = null;
        allEntries.clear();
        clearEntries();
        for (Entry entry : entries) {
            entry.list = this;
            allEntries.add(entry);
        }
        applyFilter();
    }

    /** True when {@code parentGroup} or ANY group above it is collapsed. */
    public static boolean hiddenByCollapse(String parentGroup,
                                           Map<String, String> parentOfGroup,
                                           Set<String> collapsed) {
        String group = parentGroup;
        // The guard is a belt-and-braces stop for a malformed parent chain: a cycle here would hang the
        // render thread.
        int guard = 0;
        while (group != null && guard++ < 64) {
            if (collapsed.contains(group)) {
                return true;
            }
            group = parentOfGroup.get(group);
        }
        return false;
    }

    /** The ids of the rows under a group, nested groups too, in order. */
    public static List<String> rowsUnder(List<Node> nodes, String group) {
        Map<String, String> parentOfGroup = new HashMap<>();
        for (Node n : nodes) {
            if (n.groupId() != null) {
                parentOfGroup.put(n.groupId(), n.parentGroup());
            }
        }
        List<String> out = new ArrayList<>();
        for (Node n : nodes) {
            if (n.groupId() == null && hiddenByCollapse(n.parentGroup(), parentOfGroup, Set.of(group))) {
                out.add(n.id());
            }
        }
        return out;
    }

    /** One row as the folding sees it: a group header has a groupId. */
    public record Node(String id, String label, String groupId, String parentGroup) {
    }

    /**
     * The rows shown, and the group headers shown open.
     *
     * @param rows indexes into the nodes, in order
     * @param openShown the groups whose header shows open
     */
    public record Shown(List<Integer> rows, Set<String> openShown) {
    }

    /** What the list shows. */
    public static Shown shown(List<Node> nodes, String filter, Set<String> toggled) {
        return shown(nodes, filter, toggled, false);
    }

    /**
     * As above; searching true means the nodes are already a search's matches (the screen filtered them):
     * every row shows, every group open.
     */
    public static Shown shown(List<Node> nodes, String filter, Set<String> toggled, boolean searching) {
        String f0 = filter == null ? "" : filter;
        if (searching && f0.isEmpty()) {
            List<Integer> all = new ArrayList<>();
            Set<String> groups = new HashSet<>();
            for (int i = 0; i < nodes.size(); i++) {
                all.add(i);
                if (nodes.get(i).groupId() != null) {
                    groups.add(nodes.get(i).groupId());
                }
            }
            return new Shown(all, groups);
        }
        Map<String, String> parentOfGroup = new HashMap<>();
        for (Node n : nodes) {
            if (n.groupId() != null) {
                parentOfGroup.put(n.groupId(), n.parentGroup());
            }
        }
        Set<String> closed = new HashSet<>();
        for (String group : parentOfGroup.keySet()) {
            if (!toggled.contains(group)) {
                closed.add(group);
            }
        }
        String f = filter == null ? "" : filter.toLowerCase();
        List<Integer> rows = new ArrayList<>();
        Set<String> openShown = new HashSet<>();
        if (f.isEmpty()) {
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                if (!hiddenByCollapse(n.parentGroup(), parentOfGroup, closed)) {
                    rows.add(i);
                    if (n.groupId() != null && !closed.contains(n.groupId())) {
                        openShown.add(n.groupId());
                    }
                }
            }
            return new Shown(rows, openShown);
        }
        // The groups above a match show, open, as its headers.
        Set<String> above = new HashSet<>();
        for (Node n : nodes) {
            if (matches(n, f)) {
                String group = n.parentGroup();
                int guard = 0;
                while (group != null && guard++ < 64) {
                    above.add(group);
                    group = parentOfGroup.get(group);
                }
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            if (matches(n, f) || (n.groupId() != null && above.contains(n.groupId()))) {
                rows.add(i);
            }
        }
        openShown.addAll(above);
        return new Shown(rows, openShown);
    }

    private static boolean matches(Node n, String filter) {
        // A space and an underscore are the same (ui.Search).
        return com.strata.ui.Search.matches(filter, n.label(), n.id());
    }

    private void applyFilter() {
        clearEntries();
        List<Node> nodes = new ArrayList<>();
        for (Entry entry : allEntries) {
            nodes.add(new Node(entry.id, entry.label, entry.groupId, entry.parentGroup));
        }
        Shown shown = shown(nodes, filter, effectiveToggled(), searching);
        for (int i : shown.rows()) {
            Entry entry = allEntries.get(i);
            if (entry.groupId != null) {
                entry.displayLabel = (shown.openShown().contains(entry.groupId) ? "▼ " : "▶ ") + entry.label;
            }
            addEntry(entry);
        }
    }

    @Override
    public void setSelected(Entry entry) {
        super.setSelected(entry);
        if (entry != null && entry.groupId != null) {
            if (!toggledGroups.remove(entry.groupId)) {
                toggledGroups.add(entry.groupId);
            }
            com.strata.client.util.FoldMemory.remember(foldKey, toggledGroups);
            applyFilter();
            return;
        }
        // A plain selection (a click, the arrow keys) picks that row alone.
        if (entry != null) {
            picked = new java.util.LinkedHashSet<>(List.of(entry.id));
            anchor = entry.id;
        }
        if (onSelect != null && entry != null) {
            onSelect.accept(entry);
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - com.strata.ui.screen.ListLayout.ROW_INSET;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        public final String id;
        public final String label;
        public final String subtitle;
        public final int labelColor;
        public final String groupId;
        public final String parentGroup;
        public final int indent;
        /** Current value of an inline chance cell, or null for rows without one. */
        public final Integer chanceValue;
        /** Commit handler for the inline chance cell; null makes the cell read-only. */
        public final IntConsumer onChanceCommit;
        String displayLabel;
        /** Owning list, so a cell can ask whether it currently has the keyboard. */
        BrowseList list;
        /** Read-only values drawn at the row's right, one per list column. */
        private List<String> columns = List.of();
        /** What the row is, shown when the mouse is over it, or null. */
        private String tooltip;

        /** Gives the row a tooltip: the screen draws it while the mouse is over the row. */
        public Entry tooltip(String text) {
            this.tooltip = text == null || text.isBlank() ? null : text;
            return this;
        }

        /** The same row under another group, deeper, with another label: its columns and tooltip kept. */
        Entry moved(String newGroupId, String newParent, int newIndent, String newLabel) {
            Entry e = new Entry(id, newLabel, subtitle, labelColor, newGroupId, newParent, newIndent,
                    chanceValue, onChanceCommit);
            e.columns = columns;
            e.tooltip = tooltip;
            return e;
        }

        /** Gives the row its column values, drawn under the list's column headings. */
        public Entry columns(List<String> values) {
            this.columns = values == null ? List.of() : List.copyOf(values);
            return this;
        }

        public Entry(String id, String label, String subtitle) {
            this(id, label, subtitle, Palette.TEXT, null, null, 0, null, null);
        }

        public Entry(String id, String label, String subtitle, int labelColor) {
            this(id, label, subtitle, labelColor, null, null, 0, null, null);
        }

        public Entry(String id, String label, String subtitle, int labelColor,
                     String groupId, String parentGroup, int indent) {
            this(id, label, subtitle, labelColor, groupId, parentGroup, indent,
                    null, null);
        }

        /**
         * Row with an INLINE CHANCE CELL — a typable percentage in the slot a button would occupy ("replace
         * that with the field that says the chance percentage...
         */
        public Entry(String id, String label, String subtitle, int labelColor,
                     Integer chanceValue, IntConsumer onChanceCommit) {
            this(id, label, subtitle, labelColor, null, null, 0,
                    chanceValue, onChanceCommit);
        }

        public Entry(String id, String label, String subtitle, int labelColor,
                     String groupId, String parentGroup, int indent,
                     Integer chanceValue, IntConsumer onChanceCommit) {
            this.id = id;
            this.label = label;
            this.subtitle = subtitle;
            this.labelColor = labelColor;
            this.groupId = groupId;
            this.parentGroup = parentGroup;
            this.indent = indent;
            this.chanceValue = chanceValue;
            this.onChanceCommit = onChanceCommit;
            this.displayLabel = label;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            // THE ROW IS THE UI LAYER'S, as the fit gate checked it: the name and subtitle stop before the
            // columns and the chance cell; a column value too wide is drawn smaller.
            var font = Minecraft.getInstance().font;
            // EVERY PICKED ROW IS LIT, one or many.
            if (list != null && list.picked.contains(id) && groupId == null) {
                graphics.fill(left - 2, top - 2, left + width - 3, top + height + 2, Palette.ROW_PICKED);
            }
            // A DRAG: the dragged row is lit, and the row under the mouse shows a gold line where the dragged
            // one will go.
            if (list != null && list.isDragged(this)) {
                graphics.fill(left - 2, top - 2, left + width - 3, top + height + 2, Palette.ROW_DRAGGED);
            } else if (list != null && list.reordering() && hovered && groupId == null) {
                // Dragged down, it lands below this row; dragged up, above.
                boolean below = list.children().indexOf(list.dragFrom) < index;
                int lineY = below ? top + height + 1 : top - 2;
                graphics.fill(left - 2, lineY, left + width - 3, lineY + 2, Palette.DROP_LINE);
            }
            String text = displayLabel != null ? displayLabel : label;
            int columnW = list == null ? 0 : list.columnWidth;
            com.strata.ui.screen.ListRowLayout.Row row = new com.strata.ui.screen.ListRowLayout.Row(indent, text,
                    subtitle, columns, columnW, chanceValue == null ? null : chanceValue + "%");
            com.strata.ui.Layout layout = new com.strata.ui.Layout(
                    new com.strata.ui.Frame(left + width, top + height), font::width);
            com.strata.ui.screen.ListRowLayout.add(layout, top, left, width, height, row);
            // A DISABLED ROW READS AS ONE GREY ROW.
            boolean dimmed = labelColor == Palette.MUTED;
            for (com.strata.ui.Layout.Element e : layout.elements()) {
                if (e.id().equals("label")) {
                    graphics.drawString(font, e.text(), e.box().x(), e.box().y(), labelColor);
                } else if (e.id().equals("subtitle")) {
                    graphics.drawString(font, e.text(), e.box().x(), e.box().y(),
                            dimmed ? Palette.MUTED : Palette.LABEL);
                } else if (e.id().startsWith("column")) {
                    drawFitted(graphics, font, e, dimmed ? Palette.MUTED : Palette.BODY);
                }
            }
            if (chanceValue != null) {
                renderChanceCell(graphics, top, left, width, height, mouseX, mouseY);
            }
        }

        /** A fitted text element: centred on its box, drawn smaller when the text is wider. */
        private static void drawFitted(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                       com.strata.ui.Layout.Element e, int color) {
            int cx = e.box().x() + e.box().w() / 2;
            int w = font.width(e.text());
            if (w > e.box().w() && w > 0) {
                float k = (float) e.box().w() / w;
                graphics.pose().pushPose();
                graphics.pose().translate(cx, e.box().y(), 0);
                graphics.pose().scale(k, k, 1);
                graphics.drawCenteredString(font, e.text(), 0, 0, color);
                graphics.pose().popPose();
            } else {
                graphics.drawCenteredString(font, e.text(), cx, e.box().y(), color);
            }
        }

        /** The inline chance cell: a text field, not a button. */
        private void renderChanceCell(GuiGraphics graphics, int top, int left, int width,
                                      int height, int mouseX, int mouseY) {
            BrowseList owner = this.list;
            boolean editing = owner != null && owner.editingEntry == this;
            boolean editable = onChanceCommit != null;

            com.strata.ui.Box cell = com.strata.ui.screen.ListRowLayout.chanceBox(top, left, width, height);
            int bx = cell.x();
            int byTop = cell.y();
            int boxH = cell.h();
            boolean hovered = mouseX >= bx && mouseX <= bx + ACTION_W
                    && mouseY >= byTop && mouseY <= byTop + boxH;

            int border = editing ? Palette.BORDER_ACTIVE : editable && hovered ? Palette.BORDER_HOVER : Palette.BORDER;
            graphics.fill(bx, byTop, bx + ACTION_W, byTop + boxH, border);
            graphics.fill(bx + 1, byTop + 1, bx + ACTION_W - 1, byTop + boxH - 1,
                    editable ? Palette.PANEL : Palette.PANEL_LOCKED);

            String text = editing
                    ? owner.editBuffer + "_"
                    : (chanceValue + "%");
            int textColor = editable ? (editing ? Palette.TEXT : Palette.BODY) : Palette.LOCKED;
            graphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.literal(text), bx + ACTION_W / 2, byTop + 4, textColor);
        }

        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
