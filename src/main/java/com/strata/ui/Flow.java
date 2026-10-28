package com.strata.ui;

import java.util.ArrayList;
import java.util.List;

/** A ROW OF CONTROLS THAT FITS ANY WIDTH (in the UI layer since ). */
public final class Flow {

    private Flow() {
    }

    /**
     * The placed controls, in the order given, and the rows they take.
     *
     * @param boxes one box per control
     * @param top the y of the first line
     * @param bottom the y under the last line
     */
    public record Placed(List<Box> boxes, int top, int bottom) {
    }

    /** The line each control falls on, from 0, for the widths between left and right. */
    public static int[] lines(int left, int right, int gap, int[] widths) {
        int[] line = new int[widths.length];
        int x = left;
        int current = 0;
        int onLine = 0;
        for (int i = 0; i < widths.length; i++) {
            // A line always takes its first control, however wide.
            if (onLine > 0 && x + widths[i] > right) {
                current++;
                x = left;
                onLine = 0;
            }
            line[i] = current;
            x += widths[i] + gap;
            onLine++;
        }
        return line;
    }

    /** How many lines the controls take. */
    public static int lineCount(int left, int right, int gap, int[] widths) {
        int[] line = lines(left, right, gap, widths);
        return line.length == 0 ? 0 : line[line.length - 1] + 1;
    }

    /** Places the controls in lines from y down. */
    public static Placed row(int left, int right, int y, int h, int gap, int[] widths) {
        int[] line = lines(left, right, gap, widths);
        List<Box> boxes = new ArrayList<>(widths.length);
        int x = left;
        for (int i = 0; i < widths.length; i++) {
            if (i > 0 && line[i] != line[i - 1]) {
                x = left;
            }
            boxes.add(new Box(x, y + line[i] * (h + gap), widths[i], h));
            x += widths[i] + gap;
        }
        int count = widths.length == 0 ? 0 : line[line.length - 1] + 1;
        return new Placed(boxes, y, y + count * (h + gap) - gap);
    }

    /** Places a bottom bar: its last line at bottomY, the lines before it above. */
    public static Placed bar(int left, int right, int bottomY, int h, int gap, int[] widths) {
        int count = lineCount(left, right, gap, widths);
        return row(left, right, bottomY - Math.max(0, count - 1) * (h + gap), h, gap, widths);
    }
}
