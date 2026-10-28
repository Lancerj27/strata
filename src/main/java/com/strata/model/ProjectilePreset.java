package com.strata.model;



import java.util.Map;
import java.util.regex.Pattern;

/**
 * ONE OF THE DESIGNER'S PROJECTILES.
 *
 * @param name the name, a to z, 0 to 9 and _; the goal names it strata:name
 * @param base the projectile it is fired as, an entity id
 * @param speed how fast it leaves, or NaN for the goal's own speed
 * @param gravity a share of the base's fall, or NaN for the base's
 * @param damage damage on a hit, or NaN for the base's
 * @param explosion an explosion's power where it lands, 0 for none
 * @param fireSeconds seconds what it hits burns, 0 for none
 * @param keepBase whether the base still does its own thing on impact
 * @param look the item a base drawn as an item (a snowball, a fireball) is drawn as, or "" for its own
 * @param texture the texture a base drawn as an arrow is drawn with, or "" for its own
 * @param enabled a switched-off projectile fires as its base, with nothing of its own
 */
public record ProjectilePreset(String name, String base, double speed, double gravity, double damage,
                               double explosion, double fireSeconds, boolean keepBase, String look,
                               String texture, boolean enabled) {

    /** What a projectile is named under in a goal's Fires: strata:name. */
    public static final String NAMESPACE = "strata";

    // The columns of projectiles.csv (Schema#Projectiles names them for the tables).
    public static final String NAME_COLUMN = "name";
    public static final String BASE = "base";
    public static final String SPEED = "speed";
    public static final String GRAVITY = "gravity";
    public static final String DAMAGE = "damage";
    public static final String EXPLOSION = "explosion";
    public static final String FIRE_SECONDS = "fire_seconds";
    public static final String KEEP_BASE = "keep_base";
    public static final String LOOK = "look";
    public static final String TEXTURE = "texture";
    public static final String ENABLED = "enabled";

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** The id a goal fires it by. */
    public String id() {
        return NAMESPACE + ":" + name;
    }

    /** The name in an id the goal holds, or null when the id is not one of the designer's. */
    public static String nameOf(String id) {
        String s = id == null ? "" : id.strip();
        return s.startsWith(NAMESPACE + ":") ? s.substring(NAMESPACE.length() + 1) : null;
    }

    /** A row of projectiles.csv as a projectile. */
    public static ProjectilePreset of(Map<String, String> row) {
        return new ProjectilePreset(cell(row, NAME_COLUMN), cell(row, BASE),
                number(row, SPEED), number(row, GRAVITY),
                number(row, DAMAGE), orZero(number(row, EXPLOSION)),
                orZero(number(row, FIRE_SECONDS)),
                flag(row, KEEP_BASE, true), cell(row, LOOK), cell(row, TEXTURE), flag(row, ENABLED, true));
    }

    /** What is wrong with a row, or null. */
    public static String problem(Map<String, String> row) {
        String name = cell(row, NAME_COLUMN);
        if (!NAME.matcher(name).matches()) {
            return "Name: a to z, 0 to 9 and _ only";
        }
        String base = cell(row, BASE);
        if (!ID.matcher(base).matches()) {
            return "Fired as: pick a projectile";
        }
        if (nameOf(base) != null) {
            return "Fired as: a game or mod projectile, not another of yours";
        }
        String look = cell(row, LOOK);
        if (!look.isEmpty() && !ID.matcher(look).matches()) {
            return "Looks like item: an item id, as minecraft:fire_charge, or blank for its own";
        }
        String texture = cell(row, TEXTURE);
        if (!texture.isEmpty() && !textureOk(texture)) {
            return "Arrow texture: a texture id, as minecraft:textures/entity/projectiles/spectral_arrow.png, or blank for its own";
        }
        String[][] numbers = {
                {SPEED, "Speed", "0.01", "10"},
                {GRAVITY, "Gravity", "0", "10"},
                {DAMAGE, "Damage", "0", "1000"},
                {EXPLOSION, "Explosion", "0", "10"},
                {FIRE_SECONDS, "Fire", "0", "600"}};
        for (String[] n : numbers) {
            String text = cell(row, n[0]);
            if (text.isEmpty()) {
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return n[1] + ": a number, or blank for the base's own";
            }
            if (!(v >= Double.parseDouble(n[2]) && v <= Double.parseDouble(n[3]))) {
                return n[1] + ": " + n[2] + " to " + n[3];
            }
        }
        return null;
    }

    /** A texture id an arrow can be drawn with: a png under textures/. */
    public static boolean textureOk(String id) {
        String s = id == null ? "" : id.strip();
        int colon = s.indexOf(':');
        return ID.matcher(s).matches() && colon > 0 && s.startsWith("textures/", colon + 1) && s.endsWith(".png")
                && !s.contains("..");
    }

    private static String cell(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.strip();
    }

    private static double number(Map<String, String> row, String key) {
        String v = cell(row, key);
        if (v.isEmpty()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double orZero(double v) {
        return Double.isNaN(v) ? 0 : v;
    }

    private static boolean flag(Map<String, String> row, String key, boolean blank) {
        String v = cell(row, key);
        return v.isEmpty() ? blank : !v.equalsIgnoreCase("false");
    }
}
