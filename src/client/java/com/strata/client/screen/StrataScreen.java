package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.Strata;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Arrays;
import java.util.List;

/** Base for every Strata config screen. */
public abstract class StrataScreen extends Screen implements com.strata.client.nav.Crumbed {

    // THE GRID lives in the UI layer (com.strata.ui.Grid), where the gates check it without a client.
    public static final int HEADER_Y = com.strata.ui.Grid.HEADER_Y;
    public static final int CONTENT_Y = com.strata.ui.Grid.CONTENT_Y;
    public static final int ROW_H = com.strata.ui.Grid.ROW_H;
    public static final int LABEL_OFFSET = com.strata.ui.Grid.LABEL_OFFSET;
    public static final int FIELD_W = com.strata.ui.Grid.FIELD_W;
    public static final int CTRL_H = com.strata.ui.Grid.CTRL_H;
    public static final int BTN_W = com.strata.ui.Grid.BTN_W;
    public static final int BTN_W_WIDE = com.strata.ui.Grid.BTN_W_WIDE;
    public static final int BTN_2 = com.strata.ui.Grid.BTN_2;
    public static final int BTN_3 = com.strata.ui.Grid.BTN_3;
    public static final int MIN_ROW_H = com.strata.ui.Grid.MIN_ROW_H;
    public static final int MAX_CONTENT_W = com.strata.ui.Grid.MAX_CONTENT_W;
    public static final int TAB_W = com.strata.ui.Grid.TAB_W;
    public static final int TAB_GAP = com.strata.ui.Grid.TAB_GAP;

    /** The UI scaling factor: 1.0 at every GUI scale ({@link com.strata.ui.Grid#scaleFor}). */
    protected float uiK() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return 1.0f;
        return com.strata.ui.Grid.scaleFor(this.minecraft.getWindow().getGuiScale());
    }

    /** Scale a design-space pixel value into real GUI pixels. */
    protected int scaled(int v) { return Math.round(v * uiK()); }

    /**
     * The shared frame at this screen's size, from the UI layer ({@link com.strata.ui.Frame}): the content
     * column, the header, the view, the footer lines.
     */
    protected com.strata.ui.Frame frame() {
        return new com.strata.ui.Frame(this.width, this.height);
    }

    /** The field row height at this screen's height ({@link com.strata.ui.Grid#rowH}). */
    protected int rowH() {
        return frame().rowH();
    }

    /** Canonical bottom status-line Y. */
    protected int footerY() {
        return frame().footerY();
    }

    /**
     * SECOND bottom line Y, stacked above {@link #footerY} for the screens that carry two lines of context
     * (e.g. the vanilla-data readout).
     */
    protected int footerY2() {
        return frame().footerY2();
    }

    /** The canonical TOP action-row Y (the Undo/Save band), less the scroll. */
    protected int scaledActionY() {
        return com.strata.ui.Grid.actionRowY() - scrollOffset();
    }

    /** The canonical secondary-nav-row Y below the last field row, less the scroll. */
    protected int scaledNavY(int fieldRows) {
        return com.strata.ui.Grid.navRowY(fieldRows, rowH()) - scrollOffset();
    }

    // ---------------------------------------------------------- flow rows A ROW OF CONTROLS THAT FITS ANY
    // WIDTH.

    /** The text, cut with "..." to the room, measured with the game's font. */
    protected String clip(String text, int room) {
        return com.strata.ui.TextFit.clip(text, room, this.font::width);
    }

    /** The room for the title and the failure banner, right of Back. */
    private int headerRoom() {
        return frame().titleRoom();
    }

    /** The section and entry this screen was opened as (SectionNav), or null: no Section dropdown. */
    private String section;
    private String sectionEntry;

    /** Marks this screen as a section's entry: its header shows the Section dropdown in place of the title. */
    final void inSection(String section, String entry) {
        this.section = section;
        this.sectionEntry = entry;
    }

    /** Blocks has none: the Blocks list opens Ores, Vegetation and Replacements by its own buttons. */
    private boolean sectionDropdown() {
        return section != null && !com.strata.ui.screen.Sections.BLOCKS.equals(section);
    }

    /** Swaps in another view of this screen's entry (a tab), keeping its Section dropdown. */
    protected void swapKeepingSection(StrataScreen next) {
        if (section != null) {
            next.inSection(section, sectionEntry);
        }
        ScreenNavigator.get().swapTo(next);
    }

    // ---------------------------------------------------------- form scroll A FORM TALLER THAN THE SCREEN
    // SCROLLS.

    private int scroll;
    private int maxScroll;
    private Button backButton;
    /** The "?" at the top right, or null on a screen with no help to open. */
    private Button helpButton;
    /** Whether a "?" was pressed this game run: until then it glows, with "Need help?" under it. */
    private static boolean helpSeen;
    private Button optionsButton;
    private Button scalingButton;
    private Button profilesButton;
    private final java.util.Set<net.minecraft.client.gui.components.AbstractWidget> scrollHidden =
            new java.util.HashSet<>();
    /** Controls and lines that stay put while the form scrolls: a form's main actions. */
    private final java.util.Set<net.minecraft.client.gui.components.AbstractWidget> pinned = new java.util.HashSet<>();

    /** Pins a control above the scrolling part. */
    protected <T extends net.minecraft.client.gui.components.AbstractWidget> T pin(T widget) {
        pinned.add(widget);
        return widget;
    }

    /** THE WORLD'S REGISTRIES AS THE SERVER HAS THEM, or null with no world */
    protected net.minecraft.core.RegistryAccess worldRegistries() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        var server = this.minecraft.getSingleplayerServer();
        return server != null ? server.registryAccess() : this.minecraft.level.registryAccess();
    }

    /** True for a form that scrolls when it is taller than the screen. */
    protected boolean scrolls() {
        return false;
    }

    /** The room kept under the form for its footer lines. */
    protected int footerReserve() {
        return 8;
    }

    /** Room under the last control for text the form draws there (a note, a summary). */
    protected int scrollPadding() {
        return 0;
    }

    protected int scrollOffset() {
        return scrolls() ? scroll : 0;
    }

    /** The screen's layout from the UI layer, when it has one. */
    private com.strata.ui.Layout uiLayout;

    /**
     * Declares the screen's layout: its view (under the pinned part, above the footer lines) is then the
     * layout's, as the fit gate checked it.
     */
    protected void useLayout(com.strata.ui.Layout layout) {
        this.uiLayout = layout;
    }

    public int viewTop() {
        return uiLayout != null ? uiLayout.viewTop() : frame().viewTop();
    }

    public int viewBottom() {
        return uiLayout != null ? uiLayout.viewBottom() : this.height - footerReserve();
    }

    private static com.strata.ui.Box boxOf(net.minecraft.client.gui.components.AbstractWidget w) {
        return new com.strata.ui.Box(w.getX(), w.getY(), w.getWidth(), w.getHeight());
    }

    /** True for a control that moves with the scroll: not the header's buttons, not a pinned one. */
    private boolean scrolled(net.minecraft.client.gui.components.Renderable r) {
        return r instanceof net.minecraft.client.gui.components.AbstractWidget w && w != backButton
                && w != helpButton && w != optionsButton && w != scalingButton && w != profilesButton && !pinned.contains(w);
    }

    /** Hides the controls outside the view, and works out how far the form can scroll. */
    private void updateScroll() {
        int bottom = 0;
        for (var child : children()) {
            // A control the screen hid (not the scroll) takes no room, as in com.strata.ui.Layout#maxScroll.
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w != backButton
                    && w != helpButton && w != optionsButton && w != scalingButton && w != profilesButton && !pinned.contains(w) && (w.visible || scrollHidden.contains(w))) {
                bottom = Math.max(bottom, w.getBottom() + scroll);
            }
        }
        maxScroll = com.strata.ui.Scroll.max(bottom, scrollPadding(), viewBottom());
        if (scroll > maxScroll) {
            scroll = maxScroll;
            rebuildWidgets();
            return;
        }
        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w != backButton
                    && w != helpButton && w != optionsButton && w != scalingButton && w != profilesButton && !pinned.contains(w)) {
                boolean out = com.strata.ui.Scroll.outside(boxOf(w), viewTop(), viewBottom());
                if (out && w.visible) {
                    w.visible = false;
                    scrollHidden.add(w);
                } else if (!out && scrollHidden.remove(w)) {
                    w.visible = true;
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (choices != null) {
            // The wheel scrolls a long option list, and never the form under it.
            choicesFirst = com.strata.ui.screen.ChoiceListLayout.clampFirst(frame(), choices.options().size(),
                    choicesFirst - (int) Math.signum(scrollY));
            return true;
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (!scrolls() || maxScroll <= 0) {
            return false;
        }
        int next = com.strata.ui.Scroll.afterWheel(scroll, maxScroll, scrollY);
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    // ---------------------------------------------------------- layout elements A SCREEN LAID OUT BY THE UI
    // LAYER.

    /** Puts the widget on the element's box: moved by the scroll unless pinned, and pinned if it is. */
    protected <T extends net.minecraft.client.gui.components.AbstractWidget> T place(T widget,
                                                                                   com.strata.ui.Layout.Element e) {
        // A layout id the screen asked for but the layout does not declare is a bug, and it used to NPE here
        // (Layout#get returns null) - TierList asked for "duplicate", whose slot tierList never declared, so
        // opening the tier list crashed the client.
        if (e == null) {
            Strata.LOGGER.warn("{}: a layout element was not provided for a placed widget", getClass().getSimpleName());
            return widget;
        }
        if (widget instanceof Button button) {
            buttonsById.put(e.id(), button);
        }
        int oldWidth = widget.getWidth();
        widget.setX(e.box().x());
        widget.setY(e.box().y() - (e.pinned() ? 0 : scrollOffset()));
        widget.setWidth(e.box().w());
        widget.setHeight(e.box().h());
        // A TEXT FIELD SHOWS ITS TEXT.
        if (widget instanceof net.minecraft.client.gui.components.EditBox box && oldWidth != e.box().w()) {
            int cursor = box.isFocused() ? box.getCursorPosition() : 0;
            box.setCursorPosition(cursor);
            box.setHighlightPos(cursor);
        }
        widget.visible = !e.hidden();
        if (e.pinned()) {
            pin(widget);
        }
        return widget;
    }

    /** Puts a list on the element's box. */
    protected void placeList(com.strata.client.widget.BrowseList list, com.strata.ui.Layout.Element e) {
        placeList(list, e, foldKey());
    }

    /**
     * As above, with the fold key named: a screen with TWO lists (a list and its editor)
     * keeps each list's folds apart.
     */
    protected void placeList(com.strata.client.widget.BrowseList list, com.strata.ui.Layout.Element e,
                             String foldKey) {
        if (e == null) {
            Strata.LOGGER.warn("{}: no list box in the layout for this list", getClass().getSimpleName());
            return;
        }
        list.setRectangle(e.box().w(), e.box().h(), e.box().x(), e.box().y() - (e.pinned() ? 0 : scrollOffset()));
        // Every list keeps its folds for the session.
        list.rememberFolds(foldKey);
        // A double-click on a row presses Edit (ui.Keys#DOUBLE_CLICK).
        list.setOnActivate(entry -> pressButton(com.strata.ui.Keys.DOUBLE_CLICK));
    }

    /**
     * The key this screen's list keeps its folds under: the screen, so a list reopens as it was left, for any
     * mob or scope.
     */
    protected String foldKey() {
        return getClass().getSimpleName();
    }

    /** Adds each button, in order (the keyboard focus order), on the layout element with its id. */
    protected void placeAll(com.strata.ui.Layout layout, String[] ids, Button... buttons) {
        for (int i = 0; i < ids.length; i++) {
            com.strata.ui.Layout.Element e = layout.get(ids[i]);
            if (e == null) {
                // One missing slot must not blank the whole row; place already warns, so skip quietly and
                // keep the row's other buttons live.
                Strata.LOGGER.warn("{}: layout is missing a button slot \"{}\"", getClass().getSimpleName(), ids[i]);
                continue;
            }
            addRenderableWidget(place(buttons[i], e));
            buttons[i].active = buttons[i].active && e.active();
        }
    }

    /**
     * The layout's "empty" note, drawn only while the list shows no row: a list with group or Recent rows
     * would have it written over them.
     */
    protected void emptyNote(GuiGraphics graphics, com.strata.ui.Layout layout,
                             com.strata.client.widget.BrowseList list) {
        if (layout.get("empty") != null && (list == null || list.children().isEmpty())) {
            text(graphics, layout.get("empty"), Palette.MUTED);
        }
    }

    /** An override list's entry in a screen's data menu: its words, and the screen it opens. */
    protected static java.util.Map.Entry<String, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>>
            dataEntry(String label, com.strata.data.JsonOverrides.Kind kind) {
        return java.util.Map.entry(label, () -> new OverridesScreen(kind));
    }

    /** A data menu entry that opens a view of several kinds. */
    protected static java.util.Map.Entry<String, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>>
            dataEntry(com.strata.domain.OverrideViews.View view) {
        return java.util.Map.entry(view.title(), () -> new OverridesScreen(view));
    }

    /** A DATA MENU: one dropdown button at the layout's id, whose options open their screens at once. */
    @SafeVarargs
    protected final void placeDataMenu(com.strata.ui.Layout layout, String id, String label,
            java.util.Map.Entry<String, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>>... entries) {
        java.util.Map<String, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>> opens =
                new java.util.LinkedHashMap<>();
        for (var entry : entries) {
            opens.put(entry.getKey(), entry.getValue());
        }
        com.strata.client.widget.DropdownSelect menu = new com.strata.client.widget.DropdownSelect(0, 0, 0, 0, label,
                List.copyOf(opens.keySet()), opens.keySet().iterator().next());
        menu.setWords(s -> s);
        menu.setMenu(pick -> com.strata.client.nav.ScreenNavigator.get().navigateTo(opens.get(pick).get()));
        addRenderableWidget(place(menu.getWidget(), layout.get(id)));
    }

    /** Shows or hides a widget for the screen's own reason (not the scroll). */
    protected void setShown(net.minecraft.client.gui.components.AbstractWidget widget, boolean shown) {
        scrollHidden.remove(widget);
        widget.visible = shown;
    }

    /** Draws a text element, already cut to its room by the layout. */
    protected void text(GuiGraphics graphics, com.strata.ui.Layout.Element e, int color) {
        // A missing slot name draws nothing rather than killing the frame - the same guard the widget
        // placement uses, applied to text, because a layout that has no element "footer" / "label.x" once
        // again should not be a client crash.
        if (e == null || e.hidden()) {
            return;
        }
        int y = e.box().y() - (e.pinned() ? 0 : scrollOffset());
        boolean clip = !e.pinned() && scrolls() && maxScroll > 0;
        if (clip) {
            graphics.enableScissor(0, viewTop(), this.width, viewBottom());
        }
        graphics.drawString(this.font, e.text(), e.box().x(), y, color);
        if (clip) {
            graphics.disableScissor();
        }
    }

    /**
     * EVERY FIELD TITLE OF THE LAYOUT, in the label colour: the row labels, the per-cell titles, and the
     * column titles, whatever their number.
     */
    protected void titles(GuiGraphics graphics, com.strata.ui.Layout layout) {
        if (layout == null) {
            return;
        }
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.kind() == com.strata.ui.Layout.Kind.TEXT && (e.id().startsWith("rowLabel")
                    || e.id().startsWith("label.") || e.id().startsWith("column."))) {
                text(graphics, e, Palette.LABEL);
            }
        }
    }

    /** A tooltip over the element, where it is drawn now. */
    protected void tooltip(GuiGraphics graphics, int mouseX, int mouseY, com.strata.ui.Layout.Element e,
                           String... lines) {
        if (e == null || e.hidden()) {
            return;
        }
        com.strata.ui.Box b = e.box();
        tooltip(graphics, mouseX, mouseY, b.x(), b.y() - (e.pinned() ? 0 : scrollOffset()), b.w(), b.h(), lines);
    }

    /**
     * After the frame: the header band (title, Back) over content scrolled up under it, the footer band (its
     * lines) over content below the view, and a scrollbar.
     */
    public void paintBands(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (scrolls() && maxScroll > 0) {
            paintScrollBands(graphics, mouseX, mouseY, partialTick);
        }
        paintChoices(graphics, mouseX, mouseY);
    }

    // ---------------------------------------------------------- dropdown options A DROPDOWN SHOWS ITS
    // OPTIONS.

    /** The dropdown whose options are open, or null. */
    private com.strata.client.widget.DropdownSelect choices;
    /** The first option shown, for a long list. */
    private int choicesFirst;

    /** A dropdown's list is open: a click is its, before any the screen takes itself. */
    protected boolean choicesOpen() {
        return choices != null;
    }

    private void openChoices(com.strata.client.widget.DropdownSelect d) {
        choices = d;
        choicesFirst = Math.max(0, d.selectedIndex() - 2);
    }

    private com.strata.ui.Layout choicesLayout() {
        Button b = choices.getWidget();
        List<String> words = choices.options().stream().map(choices::word).toList();
        return com.strata.ui.screen.ChoiceListLayout.build(frame(), this.font::width,
                new com.strata.ui.Box(b.getX(), b.getY(), b.getWidth(), b.getHeight()), words, choicesFirst);
    }

    private void paintChoices(GuiGraphics graphics, int mouseX, int mouseY) {
        if (choices == null) {
            return;
        }
        com.strata.ui.Layout layout = choicesLayout();
        com.strata.ui.Box panel = layout.boxOf("panel");
        int hover = com.strata.ui.screen.ChoiceListLayout.optionAt(layout, mouseX, mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(panel.x() - 1, panel.y() - 1, panel.right() + 1, panel.bottom() + 1, Palette.BORDER_MENU);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), Palette.PANEL);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (!e.id().startsWith("option")) {
                continue;
            }
            int index = Integer.parseInt(e.id().substring("option".length()));
            com.strata.ui.Box row = com.strata.ui.screen.ChoiceListLayout.rowBox(layout, index);
            if (index == hover) {
                graphics.fill(row.x(), row.y(), row.right(), row.bottom(), Palette.ROW_HOVER);
            }
            boolean selected = index == choices.selectedIndex();
            graphics.drawString(this.font, e.text(), e.box().x(), e.box().y(), selected ? Palette.EDITED : Palette.TEXT);
        }
        int all = choices.options().size();
        int shown = com.strata.ui.screen.ChoiceListLayout.rows(frame(), all);
        if (shown < all) {
            // A long list: a bar says where the rows are in it.
            int x = panel.right() - 2;
            int h = Math.max(4, panel.h() * shown / all);
            int y = panel.y() + (panel.h() - h) * choicesFirst / Math.max(1, all - shown);
            graphics.fill(x, y, x + 1, y + h, Palette.SCROLL_THUMB);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (choices != null) {
            int picked = com.strata.ui.screen.ChoiceListLayout.optionAt(choicesLayout(), (int) mouseX, (int) mouseY);
            com.strata.client.widget.DropdownSelect d = choices;
            choices = null;
            if (picked >= 0 && button == 0) {
                d.pick(picked);
            }
            return true;
        }
        if (button == 1) {
            // A right-click on a dropdown steps back one option.
            for (var child : children()) {
                if (child instanceof Button b && b.visible && b.active && b.isMouseOver(mouseX, mouseY)) {
                    com.strata.client.widget.DropdownSelect d = com.strata.client.widget.DropdownSelect.of(b);
                    if (d != null) {
                        d.stepBack();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The scrollbar. */
    private void paintScrollBands(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Scroll.Thumb thumb = com.strata.ui.Scroll.thumb(viewTop(), viewBottom(), scroll, maxScroll);
        int x = frame().contentRight() + 3;
        graphics.fill(x, viewTop(), x + 2, viewBottom(), Palette.SCROLL_TRACK);
        graphics.fill(x, thumb.y(), x + 2, thumb.y() + thumb.h(), Palette.SCROLL_THUMB);
    }

    /** The content column's width ({@link com.strata.ui.Frame#contentWidth}). */
    protected int contentWidth() {
        return frame().contentWidth();
    }

    protected int contentLeft() {
        return frame().contentLeft();
    }

    private String title;
    /** Set once it has taken its opener's path (Crumbed). */
    private boolean crumbed;
    /** A navigation a dialog asked for, run on the next frame of this screen. */
    private Runnable queuedNav;
    private String writeFailure;

    /**
     * The mob this screen edits, for the Recent group, or null when it edits something else (a tier, the ore
     * table, scaling).
     */
    private String subjectEntity;
    private String subjectVariant = "";

    /**
     * Declares the mob whose data this screen's writes belong to.
     *
     * @param entity the base entity id
     * @param variantName the variant's name, or "" for the plain creature
     */
    protected void editingMob(String entity, String variantName) {
        this.subjectEntity = entity;
        this.subjectVariant = variantName == null ? "" : variantName;
    }

    /**
     * Declares that this screen is on a GROUP rather than on a mob.
     *
     * @param name the group's display name, for whatever the screen labels
     */
    protected void editingGroup(String name) {
        this.subjectEntity = null;
        this.subjectVariant = name == null ? "" : name;
    }

    /** The tier or biome this screen's writes belong to, for its Recent group. */
    private String subjectTier;
    private String subjectBiome;

    /** Declares the tier this screen's writes belong to. */
    protected void editingTier(String name) {
        this.subjectTier = name;
    }

    /** The server's answer to a profile sent to it. */
    public void onUploadResult(com.strata.network.StrataPayloads.UploadResult result) {
        if (!result.ok()) {
            refuse(result.message());
            return;
        }
        var mc = this.minecraft;
        if (mc == null || mc.player == null || !mc.player.hasPermissions(com.strata.network.StrataNetwork.STOP_LEVEL)) {
            report(new com.strata.domain.WriteReport().needsReload(result.message() + " Ask the server's host to restart it."));
            return;
        }
        mc.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                result.message() + "\nRestart the server now? Players are told first."
                        + "\nThe host must start it again (most hosts do this on their own).",
                "Restart", com.strata.client.ClientNetwork::requestRestart));
    }

    /** Each list on a screen that has a Recent group records its picked rows. */
    private static void touchRecentLists(net.minecraft.client.gui.screens.Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof com.strata.client.widget.BrowseList list && list.recentKind() != null) {
                for (String id : list.pickedIds()) {
                    com.strata.client.util.RecentEdits.touchRow(list.recentKind(), id);
                }
            }
        }
    }

    /** Declares the biome this screen's writes belong to. */
    protected void editingBiome(String biomeId) {
        this.subjectBiome = biomeId;
    }

    /**
     * A screen named the short way ("Strata — Mob: Zombie"); its title is the PATH from the root: "Strata /
     * World / Mobs / Zombie", the sub-root being the class's (TitlePath#rootOfClass).
     */
    protected StrataScreen(String title) {
        super(Component.literal(title));
        this.title = com.strata.ui.TitlePath.compose(com.strata.ui.TitlePath.rootOfClass(getClass().getSimpleName()),
                title);
    }

    /** A screen of a given sub-root (World, Items, Blocks...): the generic screens take theirs from the kind. */
    protected StrataScreen(String root, String title) {
        super(Component.literal(title));
        this.title = com.strata.ui.TitlePath.compose(root, title);
    }

    @Override
    public String crumbPath() {
        return title;
    }

    @Override
    public void openedFrom(String parentPath) {
        if (!crumbed) {
            crumbed = true;
            title = com.strata.ui.TitlePath.crumb(parentPath, title);
        }
    }

    /** The path title (the base class's is the short name). */
    @Override
    public Component getTitle() {
        return Component.literal(title);
    }

    /**
     * A write the screen DEPENDS ON, with its failure made visible.
     *
     * @param what what was being saved, in the designer's words ("the tier")
     * @param wrote the value TableWriter returned
     * @return wrote, so the call site can guard on it directly
     */
    protected boolean saved(String what, boolean wrote) {
        writeFailure = failureMessage(what, wrote);
        if (!wrote) {
            Strata.LOGGER.error("{}: writing {} failed; the screen was kept open",
                    title, what);
        }
        return wrote;
    }

    /** The banner text for a write outcome, or null when it succeeded. */
    public static String failureMessage(String what, boolean wrote) {
        return wrote ? null : "Could not save " + what + " - the file was not written.";
    }

    /** A write whose FALSE IS NORMAL, recorded rather than reported. */
    protected void cascaded(String what, boolean wrote) {
        if (!wrote) {
            Strata.LOGGER.debug("{}: {} matched no rows - normal for a cascade", title, what);
        }
    }

    /** Shows what a domain operation could not write. */
    protected boolean report(com.strata.domain.WriteReport result) {
        // The action is over.
        com.strata.data.Rollback.close(result == null || result.ok());
        // Its writes become one change the Undo button can put back.
        com.strata.data.EditHistory.close();
        // A refusal wrote nothing: show the op's own reason, as it is.
        if (result.refusal() != null) {
            refuse(result.refusal());
            return false;
        }
        for (String note : result.emptyCascades()) {
            cascaded(note, false);
        }
        if (result.ok()) {
            // THE WRITE LANDED, so this mob has been worked on.
            if (subjectEntity != null && !subjectEntity.isBlank()) {
                com.strata.client.util.RecentEdits.touch(subjectEntity, subjectVariant);
            }
            com.strata.client.util.RecentEdits.touchTier(subjectTier);
            com.strata.client.util.RecentEdits.touchBiome(subjectBiome);
            // The rows a write acted on join their list's Recent group: this screen's lists, and the lists of
            // the screens under it, so an item saved in its editor is recent on Items.
            touchRecentLists(this);
            for (net.minecraft.client.gui.screens.Screen under : com.strata.client.nav.ScreenNavigator.get().history()) {
                touchRecentLists(under);
            }
            // A banner from an earlier failure or refusal no longer applies.
            writeFailure = null;
            if (result.reloadNote() != null) {
                reloadNote = result.reloadNote();
                reloadNoteAt = net.minecraft.Util.getMillis();
            }
            return true;
        }
        return saved(result.first(), false);
    }

    /** NAMES THE ROWS A CHANGE JUST TOUCHED AT THE FRONT OF THE NOTE LINE. */
    protected void nameNoteChange(String names) {
        if (names == null || names.isBlank() || reloadNote == null) {
            return;
        }
        // The op's note opens with the same verb ("Disabled." / "On again."): put the names after that word
        // rather than saying the verb twice.
        if (reloadNote.startsWith("Disabled.")) {
            reloadNote = "Disabled " + names + "." + reloadNote.substring("Disabled.".length());
        } else if (reloadNote.startsWith("On again.")) {
            reloadNote = "On again " + names + "." + reloadNote.substring("On again.".length());
        } else {
            reloadNote = names + ". " + reloadNote;
        }
        reloadNoteAt = net.minecraft.Util.getMillis();
    }

    /** The Undo of a screen that writes at once, or null. */
    private Button historyUndo;
    private String historyUndoText = "";

    /** Places a list screen's Undo on the layout's "undo" box. */
    protected void placeUndo(com.strata.ui.Layout layout) {
        com.strata.ui.Layout.Element e = layout.get("undo");
        if (e == null) {
            historyUndo = null;
            return;
        }
        historyUndo = Button.builder(Component.literal(e.text()), b -> {
            if (report(com.strata.domain.HistoryOps.undo())) {
                savedHere();
                rebuildWidgets();
            }
        }).build();
        historyUndoText = "";
        placeAll(layout, new String[]{"undo"}, historyUndo);
        refreshUndo();
    }

    /** Keeps the Undo usable only with a change to undo, and its tooltip current. */
    private void refreshUndo() {
        if (historyUndo == null) {
            return;
        }
        historyUndo.active = com.strata.domain.HistoryOps.canUndo();
        String text = com.strata.domain.HistoryOps.undoText();
        if (!text.equals(historyUndoText)) {
            historyUndoText = text;
            historyUndo.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(text)));
        }
    }

    /** Shows why the back end refused an input, in the same banner a failed write uses. */
    protected void refuse(String message) {
        writeFailure = message;
    }

    /** Clears the banner, when an action that does not write went through. */
    protected void clearRefusal() {
        writeFailure = null;
    }

    // ---------------------------------------------------------- save stays SAVE KEEPS THE SCREEN OPEN.

    /** When the last save landed, for the header's note. */
    private static long savedAt;
    /** How long the "Saved" note shows, in milliseconds. */
    private static final long SAVED_NOTE_MS = 2500;
    /** How long "reload the world" stays up: longer than Saved, as it asks something of the player. */
    private static final long RELOAD_NOTE_MS = 8000;
    private String reloadNote;
    private long reloadNoteAt;

    /** Marks a save that landed: the header says so for a moment. */
    protected void savedHere() {
        writeFailure = null;
        savedAt = net.minecraft.Util.getMillis();
    }

    /** Goes back to the list after a small form's save. */
    protected void backSaved() {
        savedHere();
        ScreenNavigator.get().back();
    }

    /** Opens the saved record's editor in this screen's place (no new Back step), after a save. */
    protected void reopen(net.minecraft.client.gui.screens.Screen editor) {
        savedHere();
        deferTo(() -> ScreenNavigator.get().swapTo(editor));
    }

    /** The buttons placed on layout boxes, by the box's id: what the keys press. */
    private final java.util.Map<String, Button> buttonsById = new java.util.HashMap<>();

    /**
     * Lets a key press a button placed under another id: the Behaviors list's Delete answers Del, which
     * presses a list's "remove".
     */
    protected void keyPresses(String keyId, Button button) {
        buttonsById.put(keyId, button);
    }

    /**
     * A key the focused widget does not use presses the screen's button: Enter Save (Set value on a value
     * sheet), Ctrl+Z Undo, Del Remove.
     */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (choices != null) {
            // Esc closes the open options, not the screen.
            if (key == com.strata.ui.Keys.ESCAPE) {
                choices = null;
            }
            return true;
        }
        if (key == com.strata.ui.Keys.ESCAPE) {
            // Esc in a list's chance cell cancels the cell, not the screen: the game closes the
            // screen on Esc before the list sees it.
            for (var child : children()) {
                if (child instanceof com.strata.client.widget.BrowseList l && l.editing()) {
                    return l.keyPressed(key, scan, mods);
                }
            }
        }
        if (super.keyPressed(key, scan, mods)) {
            return true;
        }
        for (String id : com.strata.ui.Keys.buttonsFor(com.strata.ui.Keys.action(key, hasControlDown(),
                hasShiftDown()))) {
            if (buttonsById.containsKey(id)) {
                return pressButton(id);
            }
        }
        return false;
    }

    /** Presses the button placed under this id, when it is there, usable, and shown. */
    protected boolean pressButton(String id) {
        Button button = buttonsById.get(id);
        if (button != null && button.active && button.visible) {
            button.onPress();
            return true;
        }
        return false;
    }

    /** True while Strata's own GUI scale is on the window in place of the game's. */
    private static boolean scaleApplied;

    /** STRATA'S GUI SCALE. */
    static boolean applyGuiScale(net.minecraft.client.Minecraft mc) {
        var window = mc.getWindow();
        int game = window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
        int want = com.strata.domain.OptionsOps.guiScale();
        // Auto is read off the MONITOR, not the window: sized by the window, any window under 1600x800 fell
        // to scale 1, half the size of full screen.
        int screenW = window.getWidth();
        int screenH = window.getHeight();
        var monitor = window.findBestMonitor();
        if (monitor != null) {
            screenW = Math.max(screenW, monitor.getCurrentMode().getWidth());
            screenH = Math.max(screenH, monitor.getCurrentMode().getHeight());
        }
        int k = want < 0 ? game
                : window.calculateScale(want == 0 ? com.strata.ui.Grid.autoGuiScale(screenW, screenH)
                        : want, mc.isEnforceUnicode());
        scaleApplied = k != game;
        if ((int) window.getGuiScale() == k) {
            return false;
        }
        window.setGuiScale(k);
        return true;
    }

    /** The game's own GUI scale back once no Strata screen is open. */
    public static void restoreGuiScale(net.minecraft.client.Minecraft mc) {
        boolean strataOpen = mc.screen != null && mc.screen.getClass().getName().startsWith("com.strata.client");
        if (scaleApplied && !strataOpen) {
            scaleApplied = false;
            mc.resizeDisplay();
        }
    }

    @Override
    protected void init() {
        if (this.minecraft != null && applyGuiScale(this.minecraft)) {
            this.width = this.minecraft.getWindow().getGuiScaledWidth();
            this.height = this.minecraft.getWindow().getGuiScaledHeight();
        }
        buttonsById.clear();
        choices = null;
        com.strata.client.widget.DropdownSelect.setOpener(this::openChoices);
        selectionButtons.clear();
        scrollHidden.clear();
        pinned.clear();
        com.strata.ui.Box back = frame().back();
        backButton = addRenderableWidget(
                Button.builder(Component.literal("← Back"), b -> leave())
                        .pos(back.x(), back.y()).size(back.w(), back.h()).build());
        // EXIT ON EVERY SCREEN: the whole editor closes in one click.
        com.strata.ui.Box exit = frame().exit();
        addRenderableWidget(Button.builder(Component.literal("Exit"), b -> exitEditor())
                .pos(exit.x(), exit.y()).size(exit.w(), exit.h())
                .tooltip(Tooltip.create(Component.literal("Close Strata"))).build());
        // HELP ON EVERY SCREEN: it opens this screen's section of Help.
        helpButton = null;
        if (helpScreen() != null) {
            com.strata.ui.Box help = frame().help();
            helpButton = addRenderableWidget(Button.builder(Component.literal("?"),
                            b -> {
                                helpSeen = true;
                                ScreenNavigator.get().jumpTo(helpScreen());
                            })
                    .pos(help.x(), help.y()).size(help.w(), help.h())
                    .tooltip(Tooltip.create(Component.literal("Help for this screen"))).build());
        }
        // OPTIONS ON EVERY SCREEN, left of Exit.
        com.strata.ui.Box options = frame().options();
        optionsButton = addRenderableWidget(Button.builder(Component.literal("Options"),
                        b -> ScreenNavigator.get().jumpTo(SectionNav.open(com.strata.ui.screen.Sections.OPTIONS)))
                .pos(options.x(), options.y()).size(options.w(), options.h())
                .tooltip(Tooltip.create(Component.literal("Strata's options and tools"))).build());
        // Greyed on the Options section's own screens: its Section dropdown moves between them.
        optionsButton.active = !com.strata.ui.screen.Sections.OPTIONS.equals(section);
        // SCALING TOO, left of Options.
        com.strata.ui.Box scaling = frame().scaling();
        scalingButton = addRenderableWidget(Button.builder(Component.literal("Scaling"),
                        b -> ScreenNavigator.get().jumpTo(SectionNav.open(com.strata.ui.screen.Sections.SCALING)))
                .pos(scaling.x(), scaling.y()).size(scaling.w(), scaling.h())
                .tooltip(Tooltip.create(Component.literal("Mob scaling and player scaling"))).build());
        // Greyed on both scaling screens too: it opens their menu.
        scalingButton.active = !com.strata.ui.screen.Sections.SCALING.equals(section);
        // PROFILES, left of Scaling.
        com.strata.ui.Box profiles = frame().profiles();
        profilesButton = addRenderableWidget(Button.builder(Component.literal("Profiles"),
                        b -> ScreenNavigator.get().jumpTo(new ProfileScreen()))
                .pos(profiles.x(), profiles.y()).size(profiles.w(), profiles.h())
                .tooltip(Tooltip.create(Component.literal("Strata's profiles: save, load, and switch configs"))).build());
        profilesButton.active = !(this instanceof ProfileScreen);
        // GO TO, left of Profiles: the main lists from any screen, in the order of their menus.
        java.util.Map<String, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen>> lists =
                new java.util.LinkedHashMap<>();
        // Every screen of World, Items and Blocks, as "Section: screen": Options, Scaling and Profiles have their
        // own buttons beside it.
        com.strata.ui.screen.Sections.ENTRIES.forEach((sec, entries) -> {
            if (!com.strata.ui.screen.Sections.OPTIONS.equals(sec) && !com.strata.ui.screen.Sections.SCALING.equals(sec)) {
                entries.forEach(entry -> lists.put(sec + ": " + entry, () -> SectionNav.open(sec, entry)));
            }
        });
        com.strata.client.widget.DropdownSelect goTo = new com.strata.client.widget.DropdownSelect(0, 0, 0, 0, "Go to",
                List.copyOf(lists.keySet()), lists.keySet().iterator().next());
        goTo.setWords(s -> s);
        goTo.setMenu(pick -> ScreenNavigator.get().jumpTo(lists.get(pick).get()));
        com.strata.ui.Box goToBox = frame().goTo();
        goTo.getWidget().setRectangle(goToBox.w(), goToBox.h(), goToBox.x(), goToBox.y());
        addRenderableWidget(goTo.getWidget());
        // THE SECTION DROPDOWN: a section's screens in one control, where the title is.
        if (sectionDropdown()) {
            List<String> entries = com.strata.ui.screen.Sections.ENTRIES.get(section);
            com.strata.client.widget.DropdownSelect pick = new com.strata.client.widget.DropdownSelect(0, 0, 0, 0,
                    section, entries, sectionEntry);
            pick.setWords(s -> s);
            String here = section;
            pick.setOnChange(e -> ScreenNavigator.get().swapTo(SectionNav.open(here, e)));
            int w = Math.min(frame().titleRoom(), entries.stream()
                    .mapToInt(e -> this.font.width(com.strata.ui.Layout.choiceText(here, e))).max().orElse(0) + 24);
            pick.getWidget().setRectangle(w, CTRL_H, frame().titleX(), frame().goTo().y());
            addRenderableWidget(pick.getWidget());
        }
    }

    /** THE FIRST TIME: the "?" glows and says "Need help?" under it, until any "?" is pressed this game run. */
    private void drawHelpHint(GuiGraphics graphics) {
        if (helpSeen || helpButton == null || !helpButton.visible) {
            return;
        }
        // A slow pulse between the hint grey and the heading orange.
        float t = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 400.0));
        int color = net.minecraft.util.FastColor.ARGB32.lerp(t, net.minecraft.util.FastColor.ARGB32.opaque(Palette.HINT),
                net.minecraft.util.FastColor.ARGB32.opaque(Palette.GROUP));
        int x = helpButton.getX();
        int y = helpButton.getY();
        graphics.renderOutline(x - 1, y - 1, helpButton.getWidth() + 2, helpButton.getHeight() + 2, color);
        String words = "Need help?";
        graphics.drawString(this.font, words, x + helpButton.getWidth() - this.font.width(words),
                y + helpButton.getHeight() + 3, color, false);
    }

    /**
     * The page this screen's "?" opens: its section of Help, by the screen's class (HelpLayout#sectionFor),
     * else the Help list.
     */
    protected net.minecraft.client.gui.screens.Screen helpScreen() {
        return HelpScreen.forScreen(helpKey());
    }

    /**
     * The name a help file's "@screen" line uses for this screen: its class, or "Class:option" for a class that
     * serves several menu options, each with its own page (a missing option page falls back to the class's).
     */
    protected String helpKey() {
        return getClass().getSimpleName();
    }

    /** Buttons that act on the selected list row, each with ITS OWN condition. */
    private final java.util.Map<Button, Needs> selectionButtons = new java.util.LinkedHashMap<>();
    /** The tooltips made so far, by text: a frame reuses them. */
    private final java.util.Map<String, Tooltip> selectionTips = new java.util.HashMap<>();

    /** A button's condition, and why it is greyed when the condition fails. */
    private record Needs(java.util.function.BooleanSupplier present, java.util.function.Supplier<String> why) {
    }

    /** The reason a row button is greyed when nothing says otherwise. */
    private static final String SELECT_FIRST = "Select a row in the list first";

    /** Declares buttons that act on the selected list row. */
    protected void needsSelection(java.util.function.BooleanSupplier present, Button... buttons) {
        needsSelection(present, () -> SELECT_FIRST, buttons);
    }

    /**
     * As above, with the reason the tooltip gives when the button is greyed (the XP row greyed On / Off and
     * Remove with "Select a row in the list first", though a row was selected).
     */
    protected void needsSelection(java.util.function.BooleanSupplier present,
                                  java.util.function.Supplier<String> why, Button... buttons) {
        for (Button button : buttons) {
            selectionButtons.put(button, new Needs(present, why));
        }
    }

    private void refreshSelectionButtons() {
        selectionButtons.forEach((button, needs) -> {
            boolean usable = needs.present().getAsBoolean();
            button.active = usable;
            button.setTooltip(usable ? standardTip(button) : selectionTips.computeIfAbsent(needs.why().get(),
                    text -> Tooltip.create(Component.literal(text))));
        });
        // A live standard button with no tooltip of its own says what it does.
        for (var child : children()) {
            if (child instanceof Button button && button.active && button.getTooltip() == null
                    && !selectionButtons.containsKey(button)) {
                button.setTooltip(standardTip(button));
            }
        }
    }

    /** The standard tooltip for a button's words (ui.ButtonTips), or null. */
    private Tooltip standardTip(Button button) {
        String text = com.strata.ui.ButtonTips.tip(button.getMessage().getString());
        return text == null ? null : selectionTips.computeIfAbsent(text, t -> Tooltip.create(Component.literal(t)));
    }

    /** True when the form holds edits that are not saved. */
    protected boolean hasUnsavedEdits() {
        return false;
    }

    /** Back and ESC. */
    protected void leave() {
        if (!hasUnsavedEdits()) {
            ScreenNavigator.get().back();
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, leaveMessage(), "Close", this::deferBack));
    }

    /** Closes the editor, after the same question as Back when this screen has unsaved edits. */
    private void exitEditor() {
        if (!hasUnsavedEdits()) {
            ScreenNavigator.get().exit();
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, leaveMessage(), "Close", () -> deferTo(ScreenNavigator.get()::exit)));
    }

    /** The question {@link #leave} asks when there are unsaved edits. */
    protected String leaveMessage() {
        return "Leave without saving?\nYour unsaved changes will be lost.";
    }

    /**
     * Runs a navigation on this screen's next frame: for a picker's choice that opens another screen, because
     * the picker goes back to this screen after its choice runs.
     */
    protected void deferTo(Runnable nav) {
        this.queuedNav = nav;
    }

    /** Goes back on the next frame. */
    protected void deferBack() {
        this.queuedNav = () -> ScreenNavigator.get().back();
    }

    /** ESC takes the same path as the Back button. */
    @Override
    public void onClose() {
        leave();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseXIn, int mouseYIn, float partialTick) {
        // While a dropdown's options are open, nothing under them reacts to the mouse.
        int[] mouse = com.strata.ui.screen.ChoiceListLayout.mouseForScreen(choices != null, mouseXIn, mouseYIn);
        int mouseX = mouse[0];
        int mouseY = mouse[1];
        refreshSelectionButtons();
        refreshUndo();
        if (scrolls()) {
            updateScroll();
        }
        if (scrolls() && maxScroll > 0) {
            // CLIPPED, NOT COVERED: a control scrolled part-way under the header or footer is cut at the
            // view's edge.
            renderBackground(graphics, mouseX, mouseY, partialTick);
            for (net.minecraft.client.gui.components.Renderable r : renderables) {
                if (scrolled(r)) {
                    graphics.enableScissor(0, viewTop(), this.width, viewBottom());
                    r.render(graphics, mouseX, mouseY, partialTick);
                    graphics.disableScissor();
                } else {
                    r.render(graphics, mouseX, mouseY, partialTick);
                }
            }
        } else {
            super.render(graphics, mouseX, mouseY, partialTick);
            drawHelpHint(graphics);
        }
        if (queuedNav != null) {
            Runnable nav = queuedNav;
            queuedNav = null;
            nav.run();
            return;
        }
        // A LIST ROW'S TOOLTIP: drawn after the screen, over everything, while the mouse is over a row that
        // has one.
        if (choices == null) {
            for (var child : children()) {
                if (child instanceof com.strata.client.widget.BrowseList list) {
                    String tip = list.tooltipAt(mouseX, mouseY);
                    if (tip != null) {
                        setTooltipForNextRenderPass(this.font.split(Component.literal(tip), 240));
                        break;
                    }
                }
            }
        }
        if (!sectionDropdown()) {
            graphics.drawString(this.font, com.strata.ui.TitlePath.clipKeepEnd(title, headerRoom(), this.font::width), frame().titleX(), frame().titleY(), Palette.TEXT);
        }
        boolean reloadShown = writeFailure == null && reloadNote != null
                && net.minecraft.Util.getMillis() - reloadNoteAt < RELOAD_NOTE_MS;
        if (reloadShown) {
            // A change that applies from the next world load says so (design-spec IDENTITY).
            graphics.drawString(this.font, clip(reloadNote, headerRoom()), frame().titleX(), frame().failureY(),
                    Palette.EDITED);
        } else if (writeFailure == null && savedAt > 0 && net.minecraft.Util.getMillis() - savedAt < SAVED_NOTE_MS) {
            graphics.drawString(this.font, "Saved", frame().titleX(), frame().failureY(), Palette.DONE);
        }
        if (writeFailure != null) {
            // Drawn by the BASE class so every screen reports a failed write the same way, including screens
            // not written yet.
            graphics.drawString(this.font, clip(writeFailure, headerRoom()), frame().titleX(), frame().failureY(),
                    Palette.ERROR);
        }
    }

    public String title() {
        return title;
    }

    /** Build, validate, wire and register a text field in one call. */
    protected EditField field(int x, int y, int w, int h, String label, String value,
                              java.util.function.Predicate<String> validator,
                              Runnable onEdit) {
        EditField f = new EditField(this.font, x, y, w, h, label, value);
        if (validator != null) {
            f.setValidator(validator);
        }
        if (onEdit != null) {
            f.setResponder(s -> onEdit.run());
        }
        addRenderableWidget(f.getWidget());
        return f;
    }

    /**
     * The value control of a value sheet: a {@link com.strata.client.widget.ValueInput} on the layout's slot,
     * rebuilt for each selected row with {@code show}.
     */
    protected com.strata.client.widget.ValueInput valueInput(com.strata.ui.Layout layout, String slot,
                                                           Runnable applyPick) {
        return new com.strata.client.widget.ValueInput(this.font,
                w -> addRenderableWidget(place(w, layout.get(slot))), this::removeWidget,
                this::worldRegistries, applyPick);
    }

    /** Shows a tooltip if the mouse is inside the given rectangle. */
    /** A long tooltip text as lines that fit a tooltip's width. */
    protected String[] wrapTip(String text) {
        return this.font.getSplitter().splitLines(text == null ? "" : text, 240,
                        net.minecraft.network.chat.Style.EMPTY)
                .stream().map(net.minecraft.network.chat.FormattedText::getString).toArray(String[]::new);
    }

    protected void tooltip(GuiGraphics graphics, int mouseX, int mouseY,
                           int rx, int ry, int rw, int rh, String... lines) {
        if (choices == null && mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) {
            List<FormattedCharSequence> tip = Arrays.stream(lines)
                    .map(s -> Component.literal(s).getVisualOrderText())
                    .toList();
            graphics.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }
}
