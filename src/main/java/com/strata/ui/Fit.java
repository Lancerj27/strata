package com.strata.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** THE FIT RULES. */
public final class Fit {

    private Fit() {
    }

    /** The window sizes and GUI scales every screen must fit: width, height, GUI scale. */
    public static final int[][] SIZES = {
            {854, 480, 2}, {1280, 720, 2}, {1280, 720, 3}, {1920, 1080, 2},
            {1920, 1080, 3}, {1920, 1080, 4}, {2560, 1440, 4}};

    /** The screen in GUI pixels for one of {@link #SIZES}. */
    public static Frame frameFor(int[] size) {
        return new Frame((int) Math.ceil(size[0] / (double) size[2]), (int) Math.ceil(size[1] / (double) size[2]));
    }

    /** A short name for one of {@link #SIZES}, for a finding. */
    public static String nameOf(int[] size) {
        return size[0] + "x" + size[1] + "@" + size[2];
    }

    /** What does not fit, at the top and at the end of the scroll. */
    public static List<String> check(Layout layout) {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(checkAt(layout, 0));
        int max = layout.maxScroll();
        if (max > 0) {
            for (String problem : checkAt(layout, max)) {
                out.add("(scrolled to the end) " + problem);
            }
        }
        return new ArrayList<>(out);
    }

    private static List<String> checkAt(Layout layout, int scroll) {
        List<String> out = new ArrayList<>();
        List<Layout.Element> shown = layout.shownAt(scroll);
        Box screen = layout.frame().screen();
        List<Layout.Element> controls = shown.stream().filter(e -> e.kind().control()).toList();
        List<Layout.Element> texts = shown.stream().filter(e -> !e.kind().control()).toList();

        for (Layout.Element e : shown) {
            if (!e.box().inside(screen)) {
                out.add("off screen: " + name(e) + " at " + box(e));
            }
        }
        // A control taller than the view a form scrolls in is never shown whole, so the scroll never shows it
        // (the AI box).
        if (scroll == 0 && layout.maxScroll() > 0) {
            int view = layout.viewBottom() - layout.viewTop();
            for (Layout.Element e : layout.elements()) {
                if (e.kind().control() && !e.pinned() && !e.hidden() && e.box().h() > view) {
                    out.add("taller than the view (" + e.box().h() + "px in " + view + "px): " + name(e));
                }
            }
        }
        for (int i = 0; i < controls.size(); i++) {
            Layout.Element a = controls.get(i);
            if (a.kind().labelled()
                    && layout.measure().width(a.text()) > a.box().w() - Layout.LABEL_MARGIN) {
                out.add("label wider than its button: " + name(a) + " (" + layout.measure().width(a.text())
                        + "px in " + a.box().w() + "px)");
            }
            for (int j = i + 1; j < controls.size(); j++) {
                Layout.Element b = controls.get(j);
                if (a.box().overlaps(b.box())) {
                    out.add("controls overlap: " + name(a) + " " + box(a) + " and " + name(b) + " " + box(b));
                }
            }
        }
        for (int i = 0; i < texts.size(); i++) {
            Layout.Element t = texts.get(i);
            // A note belongs inside a list (the mob editor's AI box): one that spills out of every list is
            // out of its box.
            if (t.kind() == Layout.Kind.NOTE
                    && controls.stream().noneMatch(c -> c.kind() == Layout.Kind.LIST && t.box().inside(c.box()))) {
                out.add("note outside its list: " + name(t));
            }
            for (Layout.Element c : controls) {
                // A list draws its own rows: only a note belongs over it.
                boolean foreign = c.kind() == Layout.Kind.LIST ? t.kind() != Layout.Kind.NOTE
                        : !t.box().inside(c.box());
                if (t.box().overlaps(c.box()) && foreign) {
                    out.add("text crosses a control: " + name(t) + " over " + name(c) + " " + box(c));
                }
            }
            for (int j = i + 1; j < texts.size(); j++) {
                if (t.box().overlaps(texts.get(j).box())) {
                    out.add("text over text: " + name(t) + " and " + name(texts.get(j)));
                }
            }
        }
        // EVERY TEXT FIELD HAS A DRAWN TITLE: a text on screen that starts above the field or beside it.
        if (scroll == 0) {
            // Where the form is laid out, before it scrolls: every field and every text it has.
            List<Layout.Element> allTexts = layout.elements().stream()
                    .filter(e -> !e.kind().control() && !e.hidden()).toList();
            for (Layout.Element f : layout.elements()) {
                if (f.kind() == Layout.Kind.FIELD && !f.hidden() && allTexts.stream().noneMatch(t -> titles(t, f))) {
                    out.add("field without a title: " + name(f) + " " + box(f));
                }
            }
            // A VALUE DROPDOWN HAS A TITLE TOO: its button text starts "<Name>: " or a text on screen titles
            // it.
            for (Layout.Element d : layout.elements()) {
                if (d.kind() == Layout.Kind.DROPDOWN && !d.hidden() && !d.text().matches("^[^:]+: .*")
                        && allTexts.stream().noneMatch(t -> titles(t, d))) {
                    out.add("dropdown without a title: " + name(d) + " " + box(d));
                }
            }
        }
        if (scroll == 0) {
            for (Layout.Element e : layout.elements()) {
                if (!e.main()) {
                    continue;
                }
                boolean isShown = shown.stream().anyMatch(s -> s.id().equals(e.id()));
                if (!isShown) {
                    out.add("main action hidden at the top: " + name(e));
                } else if (!e.active()) {
                    out.add("main action greyed out: " + name(e));
                }
            }
        }
        return out;
    }

    /**
     * Whether text t is the title of field f: it starts over the field (its top within a line and a half
     * above, its left edge within the field's width), or beside it (it ends at the field's left, on its line,
     * within a gap).
     */
    /** The widest gap between a title and the field it is beside: a form's label column (the goal form's) is one. */
    static final int BESIDE_MAX = 200;

    public static boolean titles(Layout.Element t, Layout.Element f) {
        Box tb = t.box();
        Box fb = f.box();
        if (t.text().isBlank()) {
            return false;
        }
        boolean above = tb.bottom() <= fb.y() + 1 && fb.y() - tb.y() <= 14
                && tb.x() >= fb.x() - 2 && tb.x() < fb.right();
        boolean beside = tb.right() <= fb.x() && fb.x() - tb.right() <= BESIDE_MAX
                && tb.y() < fb.bottom() && tb.bottom() > fb.y();
        // A COLUMN'S HEADING titles every field under it (the spawn rules' Value, a list's Chance).
        boolean heading = (t.id().startsWith("heading") || t.id().startsWith("column."))
                && tb.bottom() <= fb.y() && tb.x() + tb.w() / 2 >= fb.x() && tb.x() + tb.w() / 2 <= fb.right();
        return above || beside || heading;
    }

    private static String name(Layout.Element e) {
        String text = e.text().length() > 40 ? e.text().substring(0, 40) + "..." : e.text();
        return e.kind().name().toLowerCase() + " '" + e.id() + "'" + (text.isBlank() ? "" : " \"" + text + "\"");
    }

    private static String box(Layout.Element e) {
        Box b = e.box();
        return "[" + b.x() + "," + b.y() + " " + b.w() + "x" + b.h() + "]";
    }
}
