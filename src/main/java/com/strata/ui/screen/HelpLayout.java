package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;

/** HELP, LAID OUT: HelpScreen. */
public final class HelpLayout {

    private HelpLayout() {
    }
    /** One wrapped line, and whether it is part of a heading. */
    public record Line(String text, boolean heading) {
    }

    /** The step from one line to the next: the font's height and 2. */
    public static final int LINE_STEP = 11;

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

    /** One section's text, wrapped to the content width. */
    public static List<Line> lines(Frame frame, TextFit.Measure measure, String section) {
        List<Line> out = new ArrayList<>();
        for (String text : sectionText(section)) {
            boolean heading = text.startsWith("# ");
            String body = heading ? text.substring(2) : text;
            if (body.isEmpty()) {
                out.add(new Line("", false));
                continue;
            }
            for (String part : TextFit.wrap(body, frame.contentWidth(), Integer.MAX_VALUE, measure)) {
                out.add(new Line(part, heading));
            }
        }
        return out;
    }

    /** How many lines fit above the footer. */
    public static int visibleLines(Frame frame) {
        return Math.max(1, (frame.footerY() - (Grid.CONTENT_Y + 4)) / LINE_STEP);
    }

    /** How far a section can scroll, in lines. */
    public static int maxScroll(Frame frame, TextFit.Measure measure, String section) {
        return Math.max(0, lines(frame, measure, section).size() - visibleLines(frame));
    }

    /** A section's lines shown from the scroll (a line number), and "Scroll for more" while there is more. */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, String section, int scroll) {
        Layout layout = Layout.framed(frame, measure, title);
        List<Line> all = lines(frame, measure, section);
        int shown = visibleLines(frame);
        int y = Grid.CONTENT_Y + 4;
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