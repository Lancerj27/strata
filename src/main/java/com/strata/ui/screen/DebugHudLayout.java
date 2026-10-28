package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** THE DEBUG HUD, LAID OUT. */
public final class DebugHudLayout {

    private DebugHudLayout() {
    }

    /** The corner's margin, the room round the lines, and one line's step. */
    public static final int MARGIN = 4;
    public static final int PAD = 3;
    public static final int LINE_STEP = 10;

    /**
     * The lines, in words.
     *
     * @param biome the biome, as shown ("Dark Forest (minecraft:dark_forest)")
     * @param tier its tier, or null for none
     * @param level its area level, 0 for the game's own
     * @param coefficient its mob coefficient
     * @param xpLevel the player's XP level
     * @param playerScaling whether player scaling is On
     */
    public static List<String> lines(String biome, String tier, int level, double coefficient, int xpLevel,
                                     boolean playerScaling) {
        List<String> out = new ArrayList<>();
        out.add("Strata debug");
        out.add("Biome: " + biome);
        out.add("Tier: " + (tier == null || tier.isBlank() ? "none" : tier));
        out.add(level > 0 ? "Area level: " + level + ", coefficient " + number(coefficient)
                : "Area level: 0 (mobs are the game's own)");
        out.add("Your XP level: " + xpLevel + (playerScaling ? "" : " (player scaling is Off)"));
        return out;
    }

    private static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    public static Layout build(Frame frame, TextFit.Measure m, List<String> lines) {
        Layout layout = new Layout(frame, m);
        int room = Math.max(20, frame.width() - 2 * MARGIN - 2 * PAD);
        for (int i = 0; i < lines.size(); i++) {
            layout.add(layout.text("line" + i, MARGIN + PAD, MARGIN + PAD + i * LINE_STEP, lines.get(i), room));
        }
        return layout;
    }

    /** The dark panel behind the lines: round the widest line. */
    public static Box panel(Layout layout) {
        int w = 0;
        int n = 0;
        for (Layout.Element e : layout.elements()) {
            w = Math.max(w, e.box().w());
            n++;
        }
        return new Box(MARGIN, MARGIN, w + 2 * PAD, Math.max(0, n * LINE_STEP - 2) + 2 * PAD);
    }
}
