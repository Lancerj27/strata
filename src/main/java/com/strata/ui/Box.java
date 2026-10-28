package com.strata.ui;

/** A rectangle on screen, in GUI pixels: the place of one element of a layout. */
public record Box(int x, int y, int w, int h) {

    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    /** True when the two boxes share any area. */
    public boolean overlaps(Box other) {
        return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
    }

    /** True when this box lies wholly inside the other. */
    public boolean inside(Box other) {
        return x >= other.x && y >= other.y && right() <= other.right() && bottom() <= other.bottom();
    }
}
