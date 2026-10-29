package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** HELP, LAID OUT: HelpScreen. A page is its intro, then folding blocks, one per sub-heading. */
public final class HelpLayout {

    private HelpLayout() {
    }

    /**
     * One wrapped line.
     *
     * @param heading part of the page's heading (the "# " line)
     * @param subhead a block's title row, clicked to open or close the block
     * @param block the block the line belongs to, -1 for the intro
     */
    public record Line(String text, boolean heading, boolean subhead, int block) {
    }

    /** The step from one line to the next: the font's height and 2. */
    public static final int LINE_STEP = 11;

    /** The fold button's words. */
    public static final String OPEN_ALL = "Open all";
    public static final String CLOSE_ALL = "Close all";
    /** A block's marker before its title: closed, open. */
    public static final String CLOSED = "[+] ";
    public static final String OPEN = "[-] ";

    // ---- ONE PAGE PER SECTION.

    /** The sections' headings, in the order of the text. */
    public static List<String> sections() {
        List<String> out = new ArrayList<>();
        for (com.strata.data.HelpText.Section s : com.strata.data.HelpText.sections()) {
            out.add(s.heading());
        }
        return out;
    }

    /** One section's text: its heading line and the lines under it, to the next heading. */
    public static List<String> sectionText(String section) {
        com.strata.data.HelpText.Section s = com.strata.data.HelpText.section(section);
        return s == null ? List.of() : s.text();
    }

    /**
     * The screens whose "?" opens the Help list on purpose: a picker used by many screens, and Help itself.
     */
    public static final List<String> TO_MASTER = List.of("RegistryPickerScreen", "HelpScreen");

    /** The section a screen's "?" opens, or null for the Help list. */
    public static String sectionFor(String screen) {
        return com.strata.data.HelpText.sectionFor(screen);
    }

    /**
     * Whether a source line is a sub-heading: a short line after a gap that is not a bullet, a step or a
     * sentence. The help files write their blocks this way ("The mob list", "Step by step: a hard region").
     */
    static boolean isSubhead(List<String> text, int i) {
        String line = text.get(i);
        if (line.isEmpty() || line.startsWith("# ") || line.startsWith("- ") || line.startsWith("  ")
                || line.matches("^\\d+\\..*") || line.length() > 60 || line.endsWith(".") || line.endsWith(":")) {
            return false;
        }
        if (i + 1 >= text.size() || text.get(i + 1).isEmpty()) {
            return false;
        }
        // The first body line after the heading is the summary, not a block.
        for (int j = i - 1; j >= 0; j--) {
            if (text.get(j).isEmpty()) {
                return true;
            }
            if (text.get(j).startsWith("# ")) {
                return false;
            }
            return false;
        }
        return false;
    }

    /** How many blocks a section has. */
    public static int blocks(String section) {
        List<String> text = sectionText(section);
        int n = 0;
        for (int i = 0; i < text.size(); i++) {
            if (isSubhead(text, i)) {
                n++;
            }
        }
        return n;
    }

    /** One section's text, wrapped to the content width, every block open. */
    public static List<Line> lines(Frame frame, TextFit.Measure measure, String section) {
        return lines(frame, measure, section, Set.of());
    }

    /**
     * One section's text, wrapped to the content width: the intro, then each block's title row, and the
     * lines of the blocks not in {@code folded}.
     */
    public static List<Line> lines(Frame frame, TextFit.Measure measure, String section, Set<Integer> folded) {
        List<Line> out = new ArrayList<>();
        List<String> text = sectionText(section);
        int block = -1;
        boolean hidden = false;
        for (int i = 0; i < text.size(); i++) {
            String raw = text.get(i);
            boolean heading = raw.startsWith("# ");
            if (isSubhead(text, i)) {
                block++;
                hidden = folded.contains(block);
                String title = (hidden ? CLOSED : OPEN) + raw;
                for (String part : TextFit.wrap(title, frame.contentWidth(), Integer.MAX_VALUE, measure)) {
                    out.add(new Line(part, false, true, block));
                }
                continue;
            }
            if (hidden) {
                // A closed block's lines are not shown; the gap after it is kept once.
                if (raw.isEmpty() && (out.isEmpty() || !out.getLast().text().isEmpty())) {
                    out.add(new Line("", false, false, block));
                }
                continue;
            }
            String body = heading ? raw.substring(2) : raw;
            if (body.isEmpty()) {
                out.add(new Line("", false, false, block));
                continue;
            }
            for (String part : TextFit.wrap(body, frame.contentWidth(), Integer.MAX_VALUE, measure)) {
                out.add(new Line(part, heading, false, block));
            }
        }
        return out;
    }

    /** Where the first line sits: under the fold button's row. */
    public static int linesTop() {
        return Grid.actionRowY() + Grid.CTRL_H + 6;
    }

    /** How many lines fit above the footer. */
    public static int visibleLines(Frame frame) {
        return Math.max(1, (frame.footerY() - linesTop()) / LINE_STEP);
    }

    /** How far a section can scroll, in lines. */
    public static int maxScroll(Frame frame, TextFit.Measure measure, String section) {
        return maxScroll(frame, measure, section, Set.of());
    }

    public static int maxScroll(Frame frame, TextFit.Measure measure, String section, Set<Integer> folded) {
        return Math.max(0, lines(frame, measure, section, folded).size() - visibleLines(frame));
    }

    /** A section's lines shown from the scroll (a line number), every block open. */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, String section, int scroll) {
        return build(frame, measure, title, section, scroll, Set.of());
    }

    /**
     * A section's lines shown from the scroll (a line number), the blocks in {@code folded} closed to their
     * title rows, the fold button ("fold") at the right of the first row, and "Scroll for more" while there
     * is more. A line's element is "line" plus its index in {@link #lines}.
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, String section, int scroll,
                               Set<Integer> folded) {
        Layout layout = Layout.framed(frame, measure, title);
        List<Line> all = lines(frame, measure, section, folded);
        if (blocks(section) > 0) {
            int w = Grid.buttonW(CLOSE_ALL);
            layout.add(Layout.control("fold", Layout.Kind.BUTTON,
                    new Box(frame.contentRight() - w, Grid.actionRowY(), w, Grid.CTRL_H),
                    folded.isEmpty() ? CLOSE_ALL : OPEN_ALL));
        }
        int shown = visibleLines(frame);
        int y = linesTop();
        for (int i = scroll; i < all.size() && i < scroll + shown; i++) {
            layout.add(layout.text("line" + i, frame.contentLeft(), y, all.get(i).text(), frame.contentWidth()));
            y += LINE_STEP;
        }
        if (all.size() > shown) {
            layout.add(layout.text("more", frame.contentLeft(), frame.footerY(), "Scroll for more",
                    frame.contentWidth()));
        }
        return layout;
    }

    /** The Help list's last button: reads the help files again. */
    public static final String RELOAD = "Reload help";

    /** A button of the Help list: wide enough for the longest heading. */
    public static final int INDEX_W = 280;

    /**
     * THE HELP LIST (Options > Help, the master): one button per section, "section" plus its number from 0,
     * in as many columns as fit, as the Client menu.
     */
    public static Layout index(Frame frame, TextFit.Measure measure, String title) {
        Layout layout = Layout.framed(frame, measure, title);
        // Each section, then Reload help: reads the files in config/strata/help/ again.
        List<String> sections = new ArrayList<>(sections());
        sections.add(RELOAD);
        int w = Math.min(INDEX_W, frame.contentWidth());
        int gap = MenuLayouts.MENU_GAP;
        int cols = Math.max(1, Math.min(sections.size(), (frame.contentWidth() + gap) / (w + gap)));
        int gridW = cols * w + (cols - 1) * gap;
        int left = frame.contentLeft() + (frame.contentWidth() - gridW) / 2;
        int y = Grid.actionRowY() + 4;
        for (int i = 0; i < sections.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            layout.add(Layout.control(i == sections.size() - 1 ? "reload" : "section" + i, Layout.Kind.BUTTON,
                    new com.strata.ui.Box(left + col * (w + gap), y + MenuLayouts.MENU_STEP * row, w,
                            MenuLayouts.MENU_H),
                    TextFit.clip(sections.get(i), w - Layout.LABEL_MARGIN, measure)));
        }
        return layout.scrolls(true);
    }
}
