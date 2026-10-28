package com.strata.client.nav;

/** A screen whose title is a breadcrumb of where it was opened from. */
public interface Crumbed {

    /** The screen's path title. */
    String crumbPath();

    /** Told the path of the screen that opened it, once, before it is shown. */
    void openedFrom(String parentPath);
}
