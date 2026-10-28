package com.strata.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** THE SECURITY RULES OF CUSTOM TEXTURES AND SOUNDS. */
public final class AssetRules {

    private AssetRules() {
    }

    // ---- limits (all hard coded) ----

    /** Every transfer chunk but a file's last is exactly this long (server to client). */
    public static final int CHUNK_BYTES = 32 * 1024;
    /** A client's upload chunk. */
    public static final int UPLOAD_CHUNK_BYTES = 16 * 1024;
    public static final int MAX_TEXTURE_FILE = 256 * 1024;
    public static final int MAX_SOUND_FILE = 1 << 20;
    public static final int MAX_MODEL_FILE = 64 * 1024;
    /** The most cubes one model may have. */
    public static final int MAX_MODEL_ELEMENTS = 1024;
    public static final int MIN_DIMENSION = 16;
    public static final int MAX_DIMENSION = 512;
    public static final int DIMENSION_STEP = 16;
    /** Width x height x 4: the decompression-bomb guard, checked from the header BEFORE inflating. */
    public static final long MAX_PIXEL_BYTES = 1L << 20;
    public static final List<Integer> SOUND_RATES = List.of(22050, 44100, 48000);
    public static final double MAX_SECONDS = 20.0;
    public static final int MAX_NAME = 32;
    public static final int MAX_SUBTITLE = 48;
    public static final List<String> CATEGORIES = List.of("entity", "item", "block", "misc");

    /** A server sends at most this many bytes a second to one player. */
    public static final long SEND_BYTES_PER_SECOND = 4L << 20;
    /** A file's transfer is dropped when no chunk or acknowledgement came for this long. */
    public static final long FILE_TIMEOUT_MS = 60_000;
    /** A player may take this long to answer "accept server files?". */
    public static final long CONSENT_TIMEOUT_MS = 300_000;
    /** An operator's uploads: one at most every 2 seconds, and 4 MiB a minute. */
    public static final long UPLOAD_GAP_MS = 2_000;
    public static final long UPLOAD_BYTES_PER_MINUTE = 4L << 20;
    /** Chunks a client may get that it did not ask for, before it counts as abuse. */
    public static final int MAX_STRAY_CHUNKS = 8;

    private static final Pattern NAME = Pattern.compile("^[a-z0-9_]{1,32}$");

    /** The biggest file of a kind. */
    public static int maxFile(String kind) {
        return CustomAssetsOps.TEXTURE.equals(kind) ? MAX_TEXTURE_FILE
                : CustomAssetsOps.MODEL.equals(kind) ? MAX_MODEL_FILE : MAX_SOUND_FILE;
    }

    /** Why a name cannot be used, or null: lowercase letters, digits and _, 1 to 32. */
    public static String nameProblem(String name) {
        if (name == null || name.isEmpty()) {
            return "Type a name";
        }
        if (!NAME.matcher(name).matches()) {
            return "A name is 1 to " + MAX_NAME + " lowercase letters, digits or _ (no spaces, capitals, / . or -)";
        }
        return null;
    }

    public static String categoryProblem(String kind, String category) {
        if (CustomAssetsOps.TEXTURE.equals(kind)) {
            return CATEGORIES.contains(category) ? null
                    : "Pick a category for the texture: " + String.join(", ", CATEGORIES);
        }
        return category == null || category.isEmpty() ? null
                : "A " + String.valueOf(kind).toLowerCase(java.util.Locale.ROOT) + " has no category";
    }

    /**
     * A subtitle as plain printable text: the section sign and the character after it (a formatting code),
     * control, format and unassigned characters removed, spaces collapsed.
     */
    public static String cleanSubtitle(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            int type = Character.getType(c);
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE
                    || type == Character.PRIVATE_USE || type == Character.UNASSIGNED
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString().replaceAll("\\s+", " ").strip();
    }

    public static String subtitleProblem(String cleaned) {
        return cleaned.length() > MAX_SUBTITLE ? "The subtitle is at most " + MAX_SUBTITLE + " characters" : null;
    }

    // ---- results ----

    /**
     * A checked file: the problem (nothing is stored), or the bytes to store (a texture's are re-encoded by
     * Strata; a sound's are the file's own) and a summary line.
     */
    public record Checked(String problem, byte[] clean, String summary) {

        public boolean ok() {
            return problem == null;
        }

        static Checked bad(String problem) {
            return new Checked(problem, null, "");
        }
    }

    /** Checks the bytes of a file of a kind. */
    public static Checked check(String kind, byte[] data, boolean strict) {
        return CustomAssetsOps.TEXTURE.equals(kind) ? texture(data, strict)
                : CustomAssetsOps.MODEL.equals(kind) ? model(data) : sound(data);
    }

    // ---- block or item model ----

    /**
     * A block or item model: UTF-8 JSON, one object, no "loader" (a NeoForge model loader reads other files),
     * at most {@link #MAX_MODEL_ELEMENTS} elements. The stored bytes are the JSON as Strata writes it again.
     */
    static Checked model(byte[] data) {
        if (data == null || data.length == 0) {
            return Checked.bad("The file is empty.");
        }
        if (data.length > MAX_MODEL_FILE) {
            return Checked.bad("A model is at most " + (MAX_MODEL_FILE >> 10) + " KB.");
        }
        com.google.gson.JsonElement json;
        try {
            String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(data)).toString();
            json = com.google.gson.JsonParser.parseString(text);
        } catch (java.nio.charset.CharacterCodingException | RuntimeException e) {
            return Checked.bad("That is not a JSON model: " + e.getMessage());
        }
        if (!json.isJsonObject()) {
            return Checked.bad("A model is one JSON object.");
        }
        com.google.gson.JsonObject model = json.getAsJsonObject();
        if (model.has("loader")) {
            return Checked.bad("A model with a \"loader\" is not taken: it reads files Strata does not check.");
        }
        int elements = model.has("elements") && model.get("elements").isJsonArray()
                ? model.getAsJsonArray("elements").size() : 0;
        if (elements > MAX_MODEL_ELEMENTS) {
            return Checked.bad("A model has at most " + MAX_MODEL_ELEMENTS + " elements. That one has " + elements + ".");
        }
        byte[] clean = new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(model)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String parent = model.has("parent") && model.get("parent").isJsonPrimitive()
                ? ", parent " + model.get("parent").getAsString() : "";
        return new Checked(null, clean, "JSON model, " + elements + " element(s)" + parent);
    }

    // ---- PNG ----

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /**
     * A PNG: only the chunks IHDR, PLTE, tRNS, IDAT and IEND; 8 bits, colour type 2, 3 or 6, no interlace; 16
     * to 512 pixels each way in steps of 16; every CRC right; the pixel data inflated once, under a hard cap
     * taken from the header; and written again as a clean PNG.
     */
    public static Checked texture(byte[] d, boolean strict) {
        if (d == null || d.length < 8 || !prefix(d, 0, PNG_SIGNATURE)) {
            if (d != null && d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) {
                return Checked.bad("That is a JPEG image. Textures must be PNG: save it again as a .png file.");
            }
            return Checked.bad("That is not a PNG image. Textures must be PNG files.");
        }
        if (d.length > MAX_TEXTURE_FILE) {
            return Checked.bad("That PNG is " + (d.length >> 10) + " KB: the most is " + (MAX_TEXTURE_FILE >> 10) + " KB.");
        }
        int pos = 8;
        int width = 0;
        int height = 0;
        int colour = 0;
        boolean seenHeader = false;
        boolean seenPalette = false;
        boolean seenTrans = false;
        boolean seenData = false;
        boolean dataEnded = false;
        boolean seenEnd = false;
        byte[] palette = null;
        byte[] trans = null;
        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        while (pos < d.length) {
            if (seenEnd) {
                return Checked.bad("That PNG has data after its end.");
            }
            if (pos + 12 > d.length) {
                return Checked.bad("That PNG file is damaged: it ends inside a chunk.");
            }
            long len = beInt(d, pos) & 0xFFFFFFFFL;
            if (len > d.length - pos - 12L) {
                return Checked.bad("That PNG file is damaged: a chunk is longer than the file.");
            }
            int n = (int) len;
            String type = new String(d, pos + 4, 4, StandardCharsets.ISO_8859_1);
            for (int i = 0; i < 4; i++) {
                char c = type.charAt(i);
                if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')) {
                    return Checked.bad("That PNG file is damaged: a chunk has a bad name.");
                }
            }
            crc.reset();
            crc.update(d, pos + 4, 4 + n);
            if ((int) crc.getValue() != beInt(d, pos + 8 + n)) {
                return Checked.bad("That PNG file is damaged: the check value of its " + type + " chunk is wrong.");
            }
            int at = pos + 8;
            if (!seenHeader && !type.equals("IHDR")) {
                return Checked.bad("That PNG file is damaged: its header is missing.");
            }
            if (seenData && !type.equals("IDAT")) {
                dataEnded = true;
            }
            switch (type) {
                case "IHDR" -> {
                    if (seenHeader || n != 13) {
                        return Checked.bad("That PNG file is damaged: its header is wrong.");
                    }
                    seenHeader = true;
                    long w = beInt(d, at) & 0xFFFFFFFFL;
                    long h = beInt(d, at + 4) & 0xFFFFFFFFL;
                    int depth = d[at + 8] & 0xFF;
                    colour = d[at + 9] & 0xFF;
                    if (w < MIN_DIMENSION || h < MIN_DIMENSION || w > MAX_DIMENSION || h > MAX_DIMENSION
                            || w % DIMENSION_STEP != 0 || h % DIMENSION_STEP != 0) {
                        return Checked.bad("A texture is " + MIN_DIMENSION + " to " + MAX_DIMENSION
                                + " pixels each way, in multiples of " + DIMENSION_STEP + ". That one is " + w + " x " + h + ".");
                    }
                    if (w * h * 4 > MAX_PIXEL_BYTES) {
                        return Checked.bad("That PNG holds too many pixels.");
                    }
                    if (depth != 8) {
                        return Checked.bad("A texture has 8 bits per channel. That one has " + depth + ".");
                    }
                    if (colour != 2 && colour != 3 && colour != 6) {
                        return Checked.bad("A texture is RGB, RGBA or palette colour (no greyscale).");
                    }
                    if ((d[at + 10] & 0xFF) != 0 || (d[at + 11] & 0xFF) != 0 || (d[at + 12] & 0xFF) != 0) {
                        return Checked.bad("A texture is not interlaced, and uses PNG's standard compression.");
                    }
                    width = (int) w;
                    height = (int) h;
                }
                case "PLTE" -> {
                    if (seenPalette || seenData || colour != 3 || n < 3 || n > 768 || n % 3 != 0) {
                        return Checked.bad("That PNG file is damaged: its palette is wrong.");
                    }
                    seenPalette = true;
                    palette = Arrays.copyOfRange(d, at, at + n);
                }
                case "tRNS" -> {
                    if (seenTrans || seenData || colour == 6
                            || (colour == 2 && n != 6) || (colour == 3 && (palette == null || n > palette.length / 3))) {
                        return Checked.bad("That PNG file is damaged: its transparency is wrong.");
                    }
                    seenTrans = true;
                    trans = Arrays.copyOfRange(d, at, at + n);
                }
                case "IDAT" -> {
                    if (dataEnded) {
                        return Checked.bad("That PNG file is damaged: its pixel data is split.");
                    }
                    seenData = true;
                    idat.write(d, at, n);
                }
                case "IEND" -> {
                    if (n != 0 || !seenData) {
                        return Checked.bad("That PNG file is damaged: its end is wrong.");
                    }
                    seenEnd = true;
                }
                default -> {
                    boolean ancillary = (type.charAt(0) & 0x20) != 0;
                    if (strict) {
                        return Checked.bad("That PNG holds a " + type + " chunk, which is not allowed.");
                    }
                    if (!ancillary) {
                        return Checked.bad("That PNG holds a " + type + " chunk Strata cannot use.");
                    }
                }
            }
            pos += 12 + n;
        }
        if (!seenEnd || !seenHeader) {
            return Checked.bad("That PNG file is damaged: it has no end.");
        }
        if (colour == 3 && palette == null) {
            return Checked.bad("That PNG file is damaged: it has no palette.");
        }
        int bytesPerPixel = colour == 3 ? 1 : colour == 2 ? 3 : 4;
        int row = 1 + width * bytesPerPixel;
        int expected = row * height;
        byte[] raw = new byte[expected + 1];
        int got = 0;
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(idat.toByteArray());
            while (!inflater.finished()) {
                int read = inflater.inflate(raw, got, raw.length - got);
                got += read;
                if (got > expected) {
                    return Checked.bad("That PNG holds more pixel data than its header says.");
                }
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
            }
            if (!inflater.finished() || got != expected || inflater.getRemaining() != 0) {
                return Checked.bad("That PNG file is damaged: its pixel data is the wrong size.");
            }
        } catch (DataFormatException e) {
            return Checked.bad("That PNG file is damaged: its pixel data cannot be read.");
        } finally {
            inflater.end();
        }
        for (int y = 0; y < height; y++) {
            int filter = raw[y * row] & 0xFF;
            if (filter > 4) {
                return Checked.bad("That PNG file is damaged: a row has an unknown filter.");
            }
            if (colour == 3) {
                for (int x = 1; x < row; x++) {
                    if ((raw[y * row + x] & 0xFF) >= palette.length / 3 && filter == 0) {
                        return Checked.bad("That PNG file is damaged: a pixel is outside the palette.");
                    }
                }
            }
        }
        // Written again: Strata's own bytes are what is kept.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = 8;
        header[9] = (byte) colour;
        chunk(out, "IHDR", header);
        if (palette != null) {
            chunk(out, "PLTE", palette);
        }
        if (trans != null) {
            chunk(out, "tRNS", trans);
        }
        Deflater deflater = new Deflater(9);
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try {
            deflater.setInput(raw, 0, expected);
            deflater.finish();
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                packed.write(buf, 0, deflater.deflate(buf));
            }
        } finally {
            deflater.end();
        }
        chunk(out, "IDAT", packed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return new Checked(null, out.toByteArray(), "PNG, " + width + " x " + height + " pixels");
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] head = new byte[4];
        putInt(head, 0, data.length);
        out.writeBytes(head);
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        out.writeBytes(name);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        putInt(head, 0, (int) crc.getValue());
        out.writeBytes(head);
    }

    // ---- Ogg Vorbis ----

    private static final int[] OGG_TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int r = i << 24;
            for (int k = 0; k < 8; k++) {
                r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
            }
            OGG_TABLE[i] = r;
        }
    }

    /** Ogg's page checksum (CRC-32, polynomial 0x04C11DB7, not reflected) of a page whose checksum field is zero. */
    public static int oggCrc(byte[] data, int from, int length) {
        int crc = 0;
        for (int i = from; i < from + length; i++) {
            crc = (crc << 8) ^ OGG_TABLE[((crc >>> 24) & 0xFF) ^ (data[i] & 0xFF)];
        }
        return crc;
    }

    /**
     * An Ogg Vorbis file: one stream, every page's checksum right, mono or stereo at 22050, 44100 or 48000
     * Hz, at most {@value #MAX_SECONDS} seconds (from the last page's granule position), at most 1 MiB.
     */
    public static Checked sound(byte[] d) {
        String convert = " Minecraft plays only Ogg Vorbis: convert it to a .ogg file (for example with Audacity,"
                + " or: ffmpeg -i in.wav -ac 1 out.ogg).";
        if (d == null || d.length < 4) {
            return Checked.bad("That file is empty or too small to be a sound.");
        }
        if (!prefix(d, 0, new byte[]{'O', 'g', 'g', 'S'})) {
            if (prefix(d, 0, new byte[]{'R', 'I', 'F', 'F'})) {
                return Checked.bad("That is a WAV file." + convert);
            }
            if (prefix(d, 0, new byte[]{'I', 'D', '3'}) || ((d[0] & 0xFF) == 0xFF && (d[1] & 0xE0) == 0xE0)) {
                return Checked.bad("That is an MP3 file." + convert);
            }
            if (prefix(d, 0, new byte[]{'f', 'L', 'a', 'C'})) {
                return Checked.bad("That is a FLAC file." + convert);
            }
            return Checked.bad("That is not an Ogg file." + convert);
        }
        if (d.length > MAX_SOUND_FILE) {
            return Checked.bad("That sound is " + (d.length >> 10) + " KB: the most is " + (MAX_SOUND_FILE >> 10) + " KB.");
        }
        int pos = 0;
        int pages = 0;
        int serial = 0;
        int sequence = 0;
        int channels = 0;
        int rate = 0;
        long granule = -1;
        boolean last = false;
        while (pos < d.length) {
            if (last) {
                return Checked.bad("That Ogg file has data after its end.");
            }
            if (pos + 27 > d.length || !prefix(d, pos, new byte[]{'O', 'g', 'g', 'S'}) || d[pos + 4] != 0) {
                return Checked.bad("That Ogg file is damaged: a page is wrong.");
            }
            int type = d[pos + 5] & 0xFF;
            int segments = d[pos + 26] & 0xFF;
            if (pos + 27 + segments > d.length) {
                return Checked.bad("That Ogg file is damaged: it ends inside a page.");
            }
            int body = 0;
            for (int i = 0; i < segments; i++) {
                body += d[pos + 27 + i] & 0xFF;
            }
            int length = 27 + segments + body;
            if (pos + length > d.length) {
                return Checked.bad("That Ogg file is damaged: it ends inside a page.");
            }
            byte[] page = Arrays.copyOfRange(d, pos, pos + length);
            int stored = leInt(page, 22);
            page[22] = page[23] = page[24] = page[25] = 0;
            if (oggCrc(page, 0, length) != stored) {
                return Checked.bad("That Ogg file is damaged: the check value of a page is wrong.");
            }
            int pageSerial = leInt(d, pos + 14);
            int pageSequence = leInt(d, pos + 18);
            if (pages == 0) {
                if ((type & 0x02) == 0 || pageSequence != 0 || segments != 1 || body != 30) {
                    return Checked.bad("That Ogg file does not start with a Vorbis header." + convert);
                }
                int packet = pos + 27 + segments;
                if (prefix(d, packet, new byte[]{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'})) {
                    return Checked.bad("That is Ogg Opus audio, not Ogg Vorbis." + convert);
                }
                if (d[packet] != 1 || !prefix(d, packet + 1, new byte[]{'v', 'o', 'r', 'b', 'i', 's'})
                        || leInt(d, packet + 7) != 0 || (d[packet + 29] & 1) == 0) {
                    return Checked.bad("That Ogg file does not hold Vorbis audio." + convert);
                }
                channels = d[packet + 11] & 0xFF;
                rate = leInt(d, packet + 12);
                if (channels != 1 && channels != 2) {
                    return Checked.bad("A sound is mono or stereo. That one has " + channels + " channels.");
                }
                if (!SOUND_RATES.contains(rate)) {
                    return Checked.bad("A sound plays at 22050, 44100 or 48000 Hz. That one is " + rate + " Hz.");
                }
                serial = pageSerial;
            } else if ((type & 0x02) != 0 || pageSerial != serial || pageSequence != sequence) {
                return Checked.bad("That Ogg file is damaged: its pages are out of order, or hold more than one stream.");
            }
            sequence = pageSequence + 1;
            pages++;
            if ((type & 0x04) != 0) {
                last = true;
                granule = leLong(d, pos + 6);
            }
            pos += length;
        }
        if (!last || pages < 2) {
            return Checked.bad("That Ogg file is damaged: it has no end.");
        }
        if (granule < 0) {
            return Checked.bad("That Ogg file does not say how long it is.");
        }
        double seconds = (double) granule / rate;
        if (seconds > MAX_SECONDS) {
            return Checked.bad("That sound is " + String.format(java.util.Locale.ROOT, "%.1f", seconds)
                    + " seconds: the most is " + (int) MAX_SECONDS + ".");
        }
        String summary = "Ogg Vorbis, " + (channels == 1 ? "mono" : "stereo") + ", " + rate + " Hz, "
                + String.format(java.util.Locale.ROOT, "%.1f", seconds) + " s"
                + (channels > 1 ? ". Stereo plays at one volume everywhere; use mono for sounds that fade with distance" : "");
        return new Checked(null, d, summary);
    }

    // ---- bytes ----

    private static boolean prefix(byte[] data, int at, byte[] prefix) {
        if (at < 0 || data.length < at + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[at + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    static int beInt(byte[] d, int at) {
        return ((d[at] & 0xFF) << 24) | ((d[at + 1] & 0xFF) << 16) | ((d[at + 2] & 0xFF) << 8) | (d[at + 3] & 0xFF);
    }

    static int leInt(byte[] d, int at) {
        return ((d[at + 3] & 0xFF) << 24) | ((d[at + 2] & 0xFF) << 16) | ((d[at + 1] & 0xFF) << 8) | (d[at] & 0xFF);
    }

    private static long leLong(byte[] d, int at) {
        long v = 0;
        for (int b = 7; b >= 0; b--) {
            v = (v << 8) | (d[at + b] & 0xFFL);
        }
        return v;
    }

    private static void putInt(byte[] d, int at, int v) {
        d[at] = (byte) (v >>> 24);
        d[at + 1] = (byte) (v >>> 16);
        d[at + 2] = (byte) (v >>> 8);
        d[at + 3] = (byte) v;
    }
}
