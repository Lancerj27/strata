package com.strata.ui.screen;

import com.strata.ui.Box;
import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE PLAYER SPAWN SCREEN, LAID OUT. */
public final class PlayerSpawnLayout {

    private PlayerSpawnLayout() {
    }

    /** What a target does. */
    public static final String HELP = "The world spawn moves to the nearest place of the biome, or of any of the"
            + " tier's biomes, within the search radius, and you with it. New players and a death with no bed come back"
            + " there. It moves again whenever a world starts. Off keeps the spawn where it is and stops the moves.";

    public static final int BUTTON_W = 160;

    public static Layout build(Frame frame, TextFit.Measure m, String title, String now) {
        Layout layout = Layout.framed(frame, m, title);
        int x = frame.contentLeft();
        int y = Grid.CONTENT_Y + 10;
        layout.add(layout.text("now", x, y, now, frame.contentWidth()));
        int by = y + 16;
        int w = Math.min(Grid.wide(BUTTON_W, frame.contentWidth(), 30, 260), frame.contentWidth());
        layout.add(Layout.control("biome", Layout.Kind.BUTTON, new Box(x, by, w, Grid.CTRL_H), "Pick a biome...")
                .asMain());
        layout.add(Layout.control("tier", Layout.Kind.BUTTON, new Box(x, by + Grid.CTRL_H + 6, w, Grid.CTRL_H),
                "Pick a tier...").asMain());
        layout.add(Layout.control("off", Layout.Kind.BUTTON, new Box(x, by + 2 * (Grid.CTRL_H + 6), w, Grid.CTRL_H),
                "Off (the game's spawn)").asMain());
        // Undo: the session's newest change goes back.
        layout.add(Layout.control("undo", Layout.Kind.BUTTON,
                new Box(x, by + 3 * (Grid.CTRL_H + 6), Math.min(Grid.BTN_W, w), Grid.CTRL_H), "Undo"));
        // THE SEARCH RADIUS: its title above the field, Set beside it.
        int ry = by + 4 * (Grid.CTRL_H + 6) + 4;
        layout.add(layout.text("label.radius", x, ry, "Search radius (blocks, 256 to 6400)", frame.contentWidth()));
        int fy = ry - Grid.LABEL_OFFSET;
        layout.add(Layout.control("radius", Layout.Kind.FIELD, new Box(x, fy, 80, Grid.CTRL_H), ""));
        layout.add(Layout.control("setRadius", Layout.Kind.BUTTON, new Box(x + 80 + Grid.GAP, fy, Grid.buttonW("Set"),
                Grid.CTRL_H), "Set"));
        int hy = fy + Grid.CTRL_H + 8;
        List<String> lines = TextFit.wrap(HELP, frame.contentWidth(),
                Math.max(1, (frame.footerY() - 4 - hy) / (Layout.LINE_H + 2)), m);
        for (int i = 0; i < lines.size(); i++) {
            layout.add(layout.text("help" + i, x, hy + i * (Layout.LINE_H + 2), lines.get(i), frame.contentWidth()));
        }
        return layout;
    }
}
