package com.strata.ui;

/** A FORM TALLER THAN THE SCREEN SCROLLS (in the UI layer since ). */
public final class Scroll {

    private Scroll() {
    }

    /** The space kept under the last control before the view's bottom. */
    public static final int END_GAP = 6;
    /** How far one wheel notch moves the form. */
    public static final int WHEEL_STEP = 20;
    /** The shortest scroll bar thumb. */
    public static final int MIN_THUMB = 10;

    /**
     * How far a form can scroll.
     *
     * @param contentBottom the bottom of the lowest scrolling control, unscrolled
     * @param padding room under it for text the form draws there
     */
    public static int max(int contentBottom, int padding, int viewBottom) {
        return Math.max(0, contentBottom + padding + END_GAP - viewBottom);
    }

    /** True when any part of the box lies outside the view. */
    public static boolean outside(Box box, int viewTop, int viewBottom) {
        return box.y() < viewTop || box.bottom() > viewBottom;
    }

    /** The scroll after a wheel turn: scrollY notches, positive is up. */
    public static int afterWheel(int scroll, int max, double scrollY) {
        return Math.max(0, Math.min(max, scroll - (int) Math.round(scrollY * WHEEL_STEP)));
    }

    /** The scroll bar's thumb: its top and its height. */
    public record Thumb(int y, int h) {
    }

    public static Thumb thumb(int viewTop, int viewBottom, int scroll, int max) {
        int track = viewBottom - viewTop;
        int h = Math.max(MIN_THUMB, track * track / (track + Math.max(0, max)));
        int y = max <= 0 ? viewTop : viewTop + (track - h) * scroll / max;
        return new Thumb(y, h);
    }
}
