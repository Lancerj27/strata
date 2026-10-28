package com.strata.ui;

/** Text against the room it has. */
public final class TextFit {

    private TextFit() {
    }

    public static final String ELLIPSIS = "...";

    /** The width of a line of text, in GUI pixels. */
    @FunctionalInterface
    public interface Measure {
        int width(String text);
    }

    /** The text, or its longest start that fits with "..." after it. */
    public static String clip(String text, int room, Measure measure) {
        if (text == null || measure.width(text) <= room) {
            return text;
        }
        return prefix(text, Math.max(0, room - measure.width(ELLIPSIS)), measure).stripTrailing() + ELLIPSIS;
    }

    /** The text in lines no wider than width, broken between words (a word wider than the width is cut). */
    public static java.util.List<String> wrap(String text, int width, int maxLines, Measure measure) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String rest = text == null ? "" : text.strip();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (lines.size() == maxLines - 1 || measure.width(rest) <= width) {
                lines.add(clip(rest, width, measure));
                break;
            }
            String head = prefix(rest, width, measure);
            int space = head.lastIndexOf(' ');
            if (space > 0 && head.length() < rest.length() && rest.charAt(head.length()) != ' ') {
                head = head.substring(0, space);
            }
            if (head.isEmpty()) {
                head = rest.substring(0, rest.offsetByCodePoints(0, 1));
            }
            lines.add(head.stripTrailing());
            rest = rest.substring(head.length()).stripLeading();
        }
        return lines;
    }

    /** The longest start of the text no wider than limit. */
    public static String prefix(String text, int limit, Measure measure) {
        int end = 0;
        while (end < text.length()) {
            int next = text.offsetByCodePoints(end, 1);
            if (measure.width(text.substring(0, next)) > limit) {
                break;
            }
            end = next;
        }
        return text.substring(0, end);
    }
}
