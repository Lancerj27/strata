package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.domain.GlanceColumns;
import com.strata.game.RegistryQuery;
import com.strata.ui.Layout;
import com.strata.ui.Palette;
import com.strata.ui.screen.GlanceLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * THE AT-A-GLANCE COLUMNS' FRONT END, shared by the mob, item, and block lists: reads the list's columns,
 * places the Columns...
 */
final class GlanceHeaders {

    /** The picker's option that takes the column away. */
    private static final String REMOVE_ID = "";
    /** The picker's option that opens the add-a-column picker. */
    private static final String ADD_ID = "strata:add_column";

    private final StrataScreen screen;
    private final Font font;
    private final String list;
    private final Runnable refresh;

    private List<String> chosen = List.of();
    private int shown;
    private int room;
    private int columnW;
    private int headingY = -1;

    /**
     * @param refresh builds the screen's widgets and rows again, after a column is removed
     */
    GlanceHeaders(StrataScreen screen, Font font, String list, Runnable refresh) {
        this.screen = screen;
        this.font = font;
        this.list = list;
        this.refresh = refresh;
    }

    /** Reads the list's columns for room pixels of columns: how many show. */
    void load(int room) {
        this.room = room;
        chosen = GlanceColumns.chosen(list);
        shown = GlanceLayout.visible(room, chosen.size());
    }

    /** The keys of the columns shown. */
    List<String> shownKeys() {
        return chosen.subList(0, shown);
    }

    /** The headings of the columns shown. */
    List<String> headings() {
        return GlanceColumns.headings(list, shownKeys());
    }

    /** Sets the list's column width and finds where the headings are, after the layout is built. */
    void layOut(BrowseList browse, Layout layout, int width) {
        columnW = width;
        browse.setColumnWidth(width);
        Layout.Element first = layout.get("heading0");
        headingY = width > 0 && first != null ? first.box().y() - 3 : -1;
    }

    private void addColumn() {
        if (chosen.size() >= GlanceLayout.maxAdd(room)) {
            screen.refuse("No room for another column in this window: remove one, or make the window wider");
            return;
        }
        List<RegistryQuery.Option> choices = GlanceColumns.choices(list, chosen);
        if (choices.isEmpty()) {
            screen.refuse("There is nothing more to show here");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add a column", choices,
                key -> screen.report(GlanceColumns.add(list, key))));
    }

    /** The column whose heading is under the mouse, or -1. */
    int headingAt(BrowseList browse, double mouseX, double mouseY) {
        // Stop at the list top: the heading sits ~13 above it, so a band sized by CTRL_H reached past the
        // list top and stole the first row's top sliver.
        if (headingY < 0 || mouseY < headingY || mouseY >= browse.getY()) {
            return -1;
        }
        for (int i = 0; i < shown; i++) {
            int left = browse.columnLeft(i, shown);
            if (mouseX >= left && mouseX < left + columnW) {
                return i;
            }
        }
        return -1;
    }

    /** A click on a heading opens the picker: another value for the column, or none. */
    boolean click(BrowseList browse, double mouseX, double mouseY) {
        int column = headingAt(browse, mouseX, mouseY);
        if (column < 0) {
            return false;
        }
        List<RegistryQuery.Option> choices = new ArrayList<>();
        // The last column stays, so a heading is always there to click and add another from.
        if (chosen.size() > 1) {
            choices.add(new RegistryQuery.Option(REMOVE_ID, "(remove this column)", "Column"));
        }
        if (chosen.size() < GlanceLayout.maxAdd(room)) {
            choices.add(new RegistryQuery.Option(ADD_ID, "(add another column)", "Column"));
        }
        choices.addAll(GlanceColumns.choices(list, chosen));
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Show what in this column?", choices,
                key -> {
                    if (ADD_ID.equals(key)) {
                        screen.deferTo(this::addColumn);
                    } else {
                        if (screen.report(key.isEmpty() ? GlanceColumns.remove(list, column)
                                : GlanceColumns.replace(list, column, key))) {
                            refresh.run();
                        }
                    }
                }));
        return true;
    }

    /** The headings, as text over the columns, and a tooltip on the one under the mouse. */
    void render(GuiGraphics graphics, BrowseList browse, Layout layout, int mouseX, int mouseY) {
        if (headingY < 0) {
            return;
        }
        // ONE SIZE FOR THE ROW: every heading takes the scale the widest one needs.
        int roomEach = columnW - 4;
        int widest = 0;
        for (int i = 0; i < shown; i++) {
            Layout.Element h = layout.get("heading" + i);
            widest = h == null ? widest : Math.max(widest, font.width(h.text()));
        }
        for (int i = 0; i < shown; i++) {
            Layout.Element h = layout.get("heading" + i);
            if (h != null) {
                int w = font.width(h.text());
                int fitRoom = widest > roomEach ? Math.max(1, (int) ((long) roomEach * w / widest)) : roomEach;
                drawFitted(graphics, h.text(), h.box().x() + h.box().w() / 2, h.box().y(), fitRoom, Palette.GROUP);
            }
        }
        int hovered = headingAt(browse, mouseX, mouseY);
        if (hovered >= 0) {
            String key = shownKeys().get(hovered);
            String text = GlanceColumns.heading(list, key) + ": " + GlanceColumns.what(list, key)
                    + " Click to change, add or remove.";
            graphics.renderTooltip(font, font.split(Component.literal(text), screen.scaled(240)), mouseX, mouseY);
        }
    }

    /** Centered text, drawn smaller when it is wider than the room it has, so it never overlaps. */
    private void drawFitted(GuiGraphics graphics, String text, int centerX, int y, int room, int color) {
        int w = font.width(text);
        if (w <= room || w == 0) {
            graphics.drawCenteredString(font, text, centerX, y, color);
            return;
        }
        float k = (float) room / w;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + font.lineHeight * (1 - k) / 2, 0);
        graphics.pose().scale(k, k, 1);
        graphics.drawCenteredString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }
}
