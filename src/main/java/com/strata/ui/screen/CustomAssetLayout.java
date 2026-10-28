package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Flow;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.Comparator;
import java.util.List;

/**
 * THE IMPORT FORM, LAID OUT: Kind and, for a texture, Category; the File field with its Browse button; the
 * Name, and for a sound its Subtitle; the preview and problem lines; Add.
 */
public final class CustomAssetLayout {

    private CustomAssetLayout() {
    }

    /** The widest the File field is. */
    public static final int FILE_W = 340;
    public static final int BROWSE_W = 76;

    /**
     * @param texture the Texture form (Category) when true, the Sound form (Subtitle) when false
     * @param notes the preview line, then the problem line ("" keeps a line); each cut to the width
     */
    public static Layout build(Frame frame, TextFit.Measure m, String title, boolean texture, List<String> kinds,
                               List<String> categories, List<String> notes, String footer) {
        return build(frame, m, title, texture, !texture, kinds, categories, notes, footer, "Add");
    }

    /**
     * As above, with the main button's words: "Add", or "Upload to server" for an operator on a server.
     *
     * @param subtitle true for a sound: the Subtitle field
     */
    public static Layout build(Frame frame, TextFit.Measure m, String title, boolean texture, boolean subtitle,
                               List<String> kinds, List<String> categories, List<String> notes, String footer,
                               String addLabel) {
        Layout layout = Layout.framed(frame, m, title);
        int x = frame.contentLeft();
        int viewTop = Grid.CONTENT_Y + 14;
        layout.viewTop(viewTop);
        int rh = frame.rowH();
        int y = viewTop;
        int gap = FormLayout.CELL_GAP;

        List<FormLayout.Row> rows = new java.util.ArrayList<>();
        List<FormLayout.Cell> first = new java.util.ArrayList<>();
        first.add(FormLayout.Cell.choice("kind", 110, "Kind", "Kind", kinds));
        if (texture) {
            first.add(FormLayout.Cell.choice("category", 110, "Category", "Category", categories));
        }
        rows.add(new FormLayout.Row(null, first));
        int fileW = Math.max(120, Math.min(FILE_W, frame.contentWidth() - BROWSE_W - gap));
        rows.add(FormLayout.Row.of(FormLayout.Cell.field("file", fileW, "File"),
                FormLayout.Cell.button("browse", BROWSE_W, null, "Browse...")));
        List<FormLayout.Cell> third = new java.util.ArrayList<>();
        third.add(FormLayout.Cell.field("name", 200, "Name"));
        if (subtitle) {
            third.add(FormLayout.Cell.field("subtitle", 260, "Subtitle (optional)"));
        }
        rows.add(new FormLayout.Row(null, third));

        for (FormLayout.Row row : rows) {
            int[] widths = row.cells().stream().mapToInt(c -> Math.min(c.width(), frame.contentWidth())).toArray();
            int[] line = Flow.lines(x, frame.contentRight(), gap, widths);
            int cx = x;
            for (int i = 0; i < widths.length; i++) {
                if (i > 0 && line[i] != line[i - 1]) {
                    cx = x;
                }
                FormLayout.Cell cell = row.cells().get(i);
                int cy = y + line[i] * rh;
                String text = cell.texts().stream().max(Comparator.comparingInt(m::width)).orElse("");
                layout.add(Layout.control(cell.id(), cell.kind(), new Box(cx, cy, widths[i], Grid.CTRL_H), text));
                if (cell.label() != null) {
                    layout.add(layout.text("label." + cell.id(), cx, cy + Grid.LABEL_OFFSET, cell.label(),
                            Integer.MAX_VALUE));
                }
                cx += widths[i] + gap;
            }
            y += rh * (widths.length == 0 ? 1 : line[line.length - 1] + 1);
        }

        int below = y - rh + Grid.CTRL_H + Grid.GAP + 2;
        for (int i = 0; i < notes.size(); i++) {
            layout.add(layout.text("note" + i, x, below, notes.get(i), frame.contentWidth()));
            below += FormLayout.NOTE_STEP;
        }
        layout.add(Layout.control("add", Layout.Kind.BUTTON,
                new Box(x, below + Grid.GAP + 2, Grid.buttonW(addLabel), Grid.CTRL_H), addLabel).asMain());

        int reserve = 8;
        if (footer != null) {
            layout.add(layout.text("footer", x, frame.footerY(), footer, frame.contentWidth()).asPinned());
            reserve = Math.max(reserve, frame.height() - frame.footerY() + 2);
        }
        return layout.scrolls(true).footerReserve(reserve);
    }
}
