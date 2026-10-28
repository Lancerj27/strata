package com.strata.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** THE GAME'S FONT, WITHOUT A CLIENT. */
public final class GameFont implements TextFit.Measure {

    /** The width counted for a character no provider has. */
    public static final int UNKNOWN_ADVANCE = 6;
    /** The formatting code mark (U+00A7): it and the code after it take no width. */
    private static final int FORMAT = 0xA7;

    private final Map<Integer, Integer> advances;
    private final List<String> skipped;

    private GameFont(Map<Integer, Integer> advances, List<String> skipped) {
        this.advances = advances;
        this.skipped = skipped;
    }

    /**
     * Loads the default font from the class loader's resources.
     *
     * @throws IOException when the font definition or an image it names is missing
     */
    public static GameFont load(ClassLoader loader) throws IOException {
        Map<Integer, Integer> advances = new HashMap<>();
        List<String> skipped = new ArrayList<>();
        readDefinition(loader, "minecraft:default", advances, skipped);
        return new GameFont(advances, skipped);
    }

    /** The width of a line of text in GUI pixels. */
    @Override
    public int width(String text) {
        if (text == null) {
            return 0;
        }
        int sum = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == FORMAT) {
                // A formatting code and the character after it are not drawn.
                if (i < text.length()) {
                    i += Character.charCount(text.codePointAt(i));
                }
                continue;
            }
            sum += advances.getOrDefault(cp, UNKNOWN_ADVANCE);
        }
        return sum;
    }

    /** True when a provider has the character. */
    public boolean knows(int codePoint) {
        return advances.containsKey(codePoint);
    }

    /** The characters of the text no provider has. */
    public Set<Integer> unknown(String text) {
        Set<Integer> out = new TreeSet<>();
        text.codePoints().filter(cp -> !knows(cp)).forEach(out::add);
        return out;
    }

    /** Every character the font has, in order. */
    public Set<Integer> codePoints() {
        return new TreeSet<>(advances.keySet());
    }

    /** Providers of a type this reader does not read (for example "unihex"). */
    public List<String> skipped() {
        return List.copyOf(skipped);
    }

    // ------------------------------------------------------------------ reading

    private static String path(String id, String folder, String suffix) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
        String rest = colon < 0 ? id : id.substring(colon + 1);
        return "assets/" + ns + "/" + folder + "/" + rest + suffix;
    }

    private static InputStream open(ClassLoader loader, String resource) throws IOException {
        InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("not on the classpath: " + resource);
        }
        return in;
    }

    private static void readDefinition(ClassLoader loader, String id, Map<Integer, Integer> advances,
                                       List<String> skipped) throws IOException {
        JsonObject root;
        try (InputStream in = open(loader, path(id, "font", ".json"))) {
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (JsonElement element : root.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!usedByDefault(provider)) {
                continue;
            }
            String type = provider.get("type").getAsString();
            switch (type) {
                case "reference" -> readDefinition(loader, provider.get("id").getAsString(), advances, skipped);
                case "space" -> {
                    for (Map.Entry<String, JsonElement> e : provider.getAsJsonObject("advances").entrySet()) {
                        advances.putIfAbsent(e.getKey().codePointAt(0), Math.round(e.getValue().getAsFloat()));
                    }
                }
                case "bitmap" -> readBitmap(loader, provider, advances);
                default -> skipped.add(type + " in " + id);
            }
        }
    }

    /** A provider with a filter is used only when each option it names has the default value (off). */
    private static boolean usedByDefault(JsonObject provider) {
        if (!provider.has("filter")) {
            return true;
        }
        for (Map.Entry<String, JsonElement> e : provider.getAsJsonObject("filter").entrySet()) {
            if (e.getValue().getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    private static void readBitmap(ClassLoader loader, JsonObject provider, Map<Integer, Integer> advances)
            throws IOException {
        BufferedImage image;
        String file = provider.get("file").getAsString();
        try (InputStream in = open(loader, path(file, "textures", ""))) {
            image = ImageIO.read(in);
        }
        if (image == null) {
            throw new IOException("not an image: " + file);
        }
        JsonArray rows = provider.getAsJsonArray("chars");
        int[][] cps = new int[rows.size()][];
        for (int r = 0; r < rows.size(); r++) {
            cps[r] = rows.get(r).getAsString().codePoints().toArray();
        }
        int cellW = image.getWidth() / cps[0].length;
        int cellH = image.getHeight() / cps.length;
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        float scale = (float) height / cellH;
        for (int r = 0; r < cps.length; r++) {
            for (int c = 0; c < cps[r].length; c++) {
                int cp = cps[r][c];
                if (cp == 0 || advances.containsKey(cp)) {
                    continue;
                }
                int actual = inkWidth(image, c * cellW, r * cellH, cellW, cellH);
                advances.put(cp, (int) (0.5f + actual * scale) + 1);
            }
        }
    }

    /** The cell's rightmost column with a pixel in it, plus 1; 0 for an empty cell. */
    private static int inkWidth(BufferedImage image, int x0, int y0, int w, int h) {
        boolean alpha = image.getColorModel().hasAlpha();
        for (int i = w - 1; i >= 0; i--) {
            for (int k = 0; k < h; k++) {
                int argb = image.getRGB(x0 + i, y0 + k);
                int value = alpha ? argb >>> 24 : argb & 0xFF;
                if (value != 0) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
