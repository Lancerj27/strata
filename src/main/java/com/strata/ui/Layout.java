package com.strata.ui;

import java.util.ArrayList;
import java.util.List;

/** ONE SCREEN, LAID OUT: every element it shows, with its box. */
public final class Layout {

    /** The height of one line of text, as the checks count it (the font's 9, less its last blank row). */
    public static final int LINE_H = 8;
    /** The room a button keeps around its label. */
    public static final int LABEL_MARGIN = 6;

    public enum Kind {
        BUTTON, FIELD, TOGGLE, LIST, TEXT,
        /** A value dropdown: its button shows the option, so it needs a title. */
        DROPDOWN,
        /** Text placed inside a list on purpose (the empty-list message). */
        NOTE;

        /** A control: something a click acts on. */
        public boolean control() {
            return this != TEXT && this != NOTE;
        }

        /** A control that draws its text inside it, which must fit. */
        public boolean labelled() {
            return this == BUTTON || this == TOGGLE || this == DROPDOWN;
        }
    }

    /**
     * One element.
     *
     * @param pinned stays put when the form scrolls (the header, a form's actions, footer lines)
     * @param active usable (a greyed-out button is not)
     * @param main a main action: it must be shown and usable at the top of the form
     * @param hidden laid out but not shown now (a variant-only field on a plain mob)
     */
    public record Element(String id, Kind kind, Box box, String text, boolean pinned, boolean active,
                          boolean main, boolean hidden) {

        public Element asPinned() {
            return new Element(id, kind, box, text, true, active, main, hidden);
        }

        public Element asMain() {
            return new Element(id, kind, box, text, pinned, active, true, hidden);
        }

        public Element withActive(boolean usable) {
            return new Element(id, kind, box, text, pinned, usable, main, hidden);
        }

        public Element withHidden(boolean off) {
            return new Element(id, kind, box, text, pinned, active, main, off);
        }

        Element shifted(int dy) {
            return new Element(id, kind, new Box(box.x(), box.y() + dy, box.w(), box.h()), text, pinned, active,
                    main, hidden);
        }
    }
    private final Frame frame;
    private final TextFit.Measure measure;
    private final List<Element> elements = new ArrayList<>();
    private boolean scrolls;
    private int viewTop;
    private int footerReserve = 8;
    private int padding;

    public Layout(Frame frame, TextFit.Measure measure) {
        this.frame = frame;
        this.measure = measure;
        this.viewTop = frame.viewTop();
    }

    /**
     * A layout with the header every Strata screen has: the Back button, the title beside it, cut to the room
     * it has, and Help ("?") at the right.
     */
    public static Layout framed(Frame frame, TextFit.Measure measure, String title) {
        Layout layout = new Layout(frame, measure);
        layout.add(control("back", Kind.BUTTON, frame.back(), "← Back").asPinned());
        // HELP ON EVERY SCREEN: "?" at the top right, the title left of it.
        layout.add(control("help", Kind.BUTTON, frame.help(), "?").asPinned());
        // OPTIONS ON EVERY SCREEN, left of Exit.
        layout.add(control("optionsMenu", Kind.BUTTON, frame.options(), "Options").asPinned());
        // Scaling too, left of Options.
        layout.add(control("scalingMenu", Kind.BUTTON, frame.scaling(), "Scaling").asPinned());
        // Profiles, left of Scaling.
        layout.add(control("profilesMenu", Kind.BUTTON, frame.profiles(), "Profiles").asPinned());
        // Go to, left of Profiles: one menu of the main lists, on every screen.
        layout.add(control("goToMenu", Kind.BUTTON, frame.goTo(), "Go to").asPinned());
        // A path title keeps its END when cut: "...
        layout.add(layout.textKeepEnd("title", frame.titleX(), frame.titleY(), title, frame.titleRoom()).asPinned());
        return layout;
    }

    /** A toggle's text: its label and ON or OFF. */
    public static String toggleText(String label, boolean on) {
        // "On" and "Off", as every switch says them.
        String state = on ? "On" : "Off";
        // A toggle with no label shows its state alone: it read ": On".
        return label == null || label.isEmpty() ? state : label + ": " + state;
    }

    /** A dropdown's text: its name and the option, or the option alone. */
    public static String choiceText(String name, String option) {
        String shown = ChoiceWords.word(option);
        return name == null || name.isEmpty() ? shown : name + ": " + shown;
    }

    /** A control at a box. */
    public static Element control(String id, Kind kind, Box box, String label) {
        return new Element(id, kind, box, label == null ? "" : label, false, true, false, false);
    }

    /** A path, cut with "..." at its START to the room, so the screen it names stays visible. */
    public Element textKeepEnd(String id, int x, int y, String text, int room) {
        String shown = TitlePath.clipKeepEnd(text == null ? "" : text, room, measure);
        return new Element(id, Kind.TEXT, new Box(x, y, measure.width(shown), LINE_H), shown, false, true, false, false);
    }

    /** A line of text at x, y, cut with "..." to room. */
    public Element text(String id, int x, int y, String text, int room) {
        String shown = TextFit.clip(text == null ? "" : text, room, measure);
        return new Element(id, Kind.TEXT, new Box(x, y, measure.width(shown), LINE_H), shown, false, true, false, false);
    }

    /** A line of text inside a list on purpose (the empty-list message), cut with "..." to room. */
    public Element note(String id, int x, int y, String text, int room) {
        String shown = TextFit.clip(text == null ? "" : text, room, measure);
        return new Element(id, Kind.NOTE, new Box(x, y, measure.width(shown), LINE_H), shown, false, true, false,
                false);
    }

    /**
     * A line of text centred on centerX and drawn SMALLER when it is wider than room (a column heading), so
     * it takes at most room.
     */
    public Element fitted(String id, int centerX, int y, String text, int room) {
        int w = Math.min(measure.width(text), Math.max(0, room));
        return new Element(id, Kind.TEXT, new Box(centerX - w / 2, y, w, LINE_H), text, false, true, false, false);
    }

    /** A line of text centred on centerX, cut with "..." to room. */
    public Element centred(String id, int centerX, int y, String text, int room) {
        String shown = TextFit.clip(text == null ? "" : text, room, measure);
        int w = measure.width(shown);
        return new Element(id, Kind.TEXT, new Box(centerX - w / 2, y, w, LINE_H), shown, false, true, false, false);
    }

    public Element add(Element element) {
        elements.add(element);
        return element;
    }

    /** Puts an element in the place of the one with its id (a list narrowed for a side panel). */
    public Element replace(Element element) {
        elements.replaceAll(e -> e.id().equals(element.id()) ? element : e);
        return element;
    }

    /** Makes the form scroll when it is taller than the screen. */
    public Layout scrolls(boolean on) {
        this.scrolls = on;
        return this;
    }

    /** Moves the top of the view down, under a pinned part. */
    public Layout viewTop(int y) {
        this.viewTop = y;
        return this;
    }

    /** The room kept under the view for footer lines. */
    public Layout footerReserve(int room) {
        this.footerReserve = room;
        return this;
    }

    /** Room under the last control for text drawn there. */
    public Layout padding(int room) {
        this.padding = room;
        return this;
    }

    public Frame frame() {
        return frame;
    }

    public TextFit.Measure measure() {
        return measure;
    }

    public List<Element> elements() {
        return List.copyOf(elements);
    }

    /** The element with that id, or null. */
    public Element get(String id) {
        for (Element e : elements) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** The named slot's text, or "" when the layout has no such id. */
    public String textOf(String id) {
        Element e = get(id);
        return e == null ? "" : e.text();
    }

    /** This slot's box, or a zero box when the layout has no such id. */
    public Box boxOf(String id) {
        Element e = get(id);
        return e == null ? new Box(0, 0, 0, 0) : e.box();
    }

    /** Whether this slot is active, false when the layout has no such id. */
    public boolean activeOf(String id) {
        Element e = get(id);
        return e != null && e.active();
    }

    public int viewTop() {
        return viewTop;
    }

    public int viewBottom() {
        return frame.height() - footerReserve;
    }

    /** How far the form can scroll: 0 when it does not scroll. */
    public int maxScroll() {
        if (!scrolls) {
            return 0;
        }
        int bottom = 0;
        for (Element e : elements) {
            if (!e.pinned() && !e.hidden()) {
                bottom = Math.max(bottom, e.box().bottom());
            }
        }
        return Scroll.max(bottom, padding, viewBottom());
    }

    /** The elements on screen at a scroll, where they are drawn. */
    public List<Element> shownAt(int scroll) {
        int max = maxScroll();
        int s = scrolls ? Math.max(0, Math.min(scroll, max)) : 0;
        List<Element> out = new ArrayList<>();
        // A list's notes hide with it (the mob editor's AI box).
        List<Element> hiddenLists = new ArrayList<>();
        for (Element e : elements) {
            if (e.kind() == Kind.LIST && !e.pinned() && !e.hidden() && scrolls
                    && Scroll.outside(e.shifted(-s).box(), viewTop, viewBottom())) {
                hiddenLists.add(e);
            }
        }
        for (Element e : elements) {
            if (e.hidden()) {
                continue;
            }
            if (e.kind() == Kind.NOTE && hiddenLists.stream().anyMatch(l -> e.box().inside(l.box()))) {
                continue;
            }
            if (e.pinned()) {
                out.add(e);
                continue;
            }
            Element at = e.shifted(-s);
            if (scrolls && e.kind().control() && Scroll.outside(at.box(), viewTop, viewBottom())) {
                continue;
            }
            if (scrolls && !e.kind().control() && max > 0) {
                boolean underTop = s > 0 && at.box().y() < viewTop;
                boolean underBottom = at.box().bottom() > viewBottom();
                if (underTop || underBottom) {
                    continue;
                }
            }
            out.add(at);
        }
        return out;
    }
}
