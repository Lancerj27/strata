package com.strata.ui;

/** THE GRID: the sizes every Strata screen is laid out on, and the pure arithmetic over them. */
public final class Grid {

    private Grid() {
    }

    public static final int HEADER_Y = 8;
    public static final int CONTENT_Y = 30;
    public static final int ROW_H = 36;
    public static final int LABEL_OFFSET = -14;
    public static final int FIELD_W = 200;

    /** The widest a form field grows on a wide column. */
    public static final int FIELD_W_MAX = 360;

    /**
     * A form field's width for a content column: 40% of the column, never under {@link #FIELD_W} (a small
     * window is unchanged) nor over {@link #FIELD_W_MAX}.
     */
    public static int fieldW(int contentWidth) {
        return Math.max(FIELD_W, Math.min(FIELD_W_MAX, contentWidth * 2 / 5));
    }

    /**
     * A control's width on a wide column: {@code base} grown to {@code share} percent of the column, never
     * under {@code base} nor over {@code max}.
     */
    public static int wide(int base, int contentWidth, int percent, int max) {
        return Math.max(base, Math.min(max, contentWidth * percent / 100));
    }

    /** The standard control height - every field, button, toggle and dropdown on a normal form row. */
    public static final int CTRL_H = ROW_H / 2;

    /** A BUTTON'S WIDTH FROM ITS WORDS. */
    /**
     * Strata's Auto GUI scale leaves at least this much room: a 1920x1080 screen at scale 2, full screen or
     * in a window.
     */
    public static final int AUTO_MIN_W = 800;
    public static final int AUTO_MIN_H = 400;

    /** STRATA'S AUTO GUI SCALE. */
    public static int autoGuiScale(int windowW, int windowH) {
        int k = 1;
        while (windowW / (k + 1) >= AUTO_MIN_W && windowH / (k + 1) >= AUTO_MIN_H) {
            k++;
        }
        return k;
    }

    public static int buttonW(String label) {
        int need = label.length() * 5 + 16;
        return Math.max(BTN_W, (need + 9) / 10 * 10);
    }

    /** The action row: Undo / Save / Delete, in that order, on every edit screen. */
    public static final int BTN_W = 70;
    /** Save is wider than Undo because it is the one you aim for. */
    public static final int BTN_W_WIDE = 80;
    /** Save's x, from the row's left. */
    public static final int BTN_2 = 74;
    /** Delete's x, from the row's left. */
    public static final int BTN_3 = 158;

    /** The shortest field row: an 18-high control and the 14 above it for its label. */
    public static final int MIN_ROW_H = CTRL_H - LABEL_OFFSET;

    /** Gap between the bottom of the last field row and the secondary navigation row. */
    public static final int NAV_GAP = 24;
    /** Reserve at the bottom of list screens for the per-list action bar. */
    public static final int BAR_TOP = 62;

    /** The gap between two controls in a row, and between two rows of a flow. */
    public static final int GAP = 4;

    /** The widest the content column is allowed to get. */
    public static final int MAX_CONTENT_W = 900;
    /** The room left and right of the content column, together. */
    public static final int GUTTER = 20;

    /** Design width of one scope tab, and the gap between two. */
    public static final int TAB_W = 92;
    public static final int TAB_GAP = 3;

    /** The UI scaling factor for a given GUI scale: 1.0 at every scale. */
    public static float scaleFor(double guiScale) {
        return 1.0f;
    }

    /** Canonical TOP action-row Y - the Undo/Save band. */
    public static int actionRowY() {
        return CONTENT_Y + 4;
    }

    /**
     * Canonical secondary-nav-row Y for an edit screen with {@code fieldRows} field rows: one NAV_GAP below
     * the bottom of the last field row.
     */
    public static int navRowY(int fieldRows, int rowH) {
        return actionRowY() + fieldRows * rowH + CTRL_H + NAV_GAP;
    }

    /** Bottom action bar for list screens - the list must end above this. */
    public static int bottomBarY(int height) {
        return height - BAR_TOP;
    }

    /** Adaptive row height for a screen height. */
    public static int rowH(int height) {
        return Math.max(MIN_ROW_H, Math.min(ROW_H, ROW_H * Math.max(1, height) / 360));
    }

    /** Content-column width for a viewport: the viewport minus its gutter, up to {@code maxContent}. */
    public static int contentWidthFor(int viewportWidth, int gutter, int maxContent) {
        return Math.min(viewportWidth - gutter, maxContent);
    }

    /** How many scope tabs fit across one row of a given content width. */
    public static int tabsPerRow(int contentWidth) {
        return Math.max(1, (contentWidth + TAB_GAP) / (TAB_W + TAB_GAP));
    }

    /** Rows of tabs needed for {@code count} tabs at that width. */
    public static int tabRowCount(int count, int contentWidth) {
        if (count <= 0) {
            return 0;
        }
        int perRow = tabsPerRow(contentWidth);
        return (count + perRow - 1) / perRow;
    }
}
