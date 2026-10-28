package com.strata.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** AN ITEM VARIANT, AS RULES. */
public final class ItemVariant {

    private ItemVariant() {
    }

    /** What an item column holds for a variant: "variant:frost_blade". */
    public static final String REF_PREFIX = "variant:";

    // The look keys (the game's components where it has one).
    public static final String DISPLAY_NAME = "display_name";
    public static final String LORE = "lore";
    public static final String GLINT = "glint";
    public static final String CUSTOM_MODEL_DATA = "custom_model_data";
    public static final String UNBREAKABLE = "unbreakable";
    /** An enchantment: "enchantment|minecraft:sharpness", value its level. */
    public static final String ENCHANT_PREFIX = "enchantment|";

    /** The look keys, in the editor's order. */
    public static final List<String> LOOK_KEYS = List.of(DISPLAY_NAME, LORE, ItemStats.RARITY, GLINT,
            UNBREAKABLE, CUSTOM_MODEL_DATA);

    /** Lore's line break, as typed in one field. */
    public static final String LORE_BREAK = " | ";

    /** The glint choices. */
    public static final List<String> GLINTS = List.of("default", "on", "off");

    /** The game's slot groups an attribute modifier may name (EquipmentSlotGroup). */
    public static final List<String> SLOTS = List.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest",
            "head", "armor", "body");

    /** The game's highest enchantment level (ItemEnchantments: 255). */
    public static final int MAX_ENCHANT = 255;

    /** A variant's reference, for an item column. */
    public static String ref(String name) {
        return REF_PREFIX + name;
    }

    /** The variant a reference names, or null for a plain item id. */
    public static String nameOf(String itemColumn) {
        String v = itemColumn == null ? "" : itemColumn.strip();
        return v.startsWith(REF_PREFIX) ? v.substring(REF_PREFIX.length()) : null;
    }

    /** Why a new variant's name cannot be used, or null. */
    public static String nameProblem(String name, java.util.Collection<String> existing) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty()) {
            return "Type a name";
        }
        if (!n.matches("[a-z0-9_]{1,40}")) {
            return "A name is 1 to 40 of a to z, 0 to 9, and _ (for example frost_blade)";
        }
        return existing.contains(n) ? "A variant named " + n + " exists" : null;
    }

    /** An enchantment key. */
    public static String enchantKey(String enchantment) {
        return ENCHANT_PREFIX + enchantment;
    }

    /** The enchantment of an enchantment key, or null. */
    public static String enchantmentOf(String key) {
        return key.startsWith(ENCHANT_PREFIX) ? key.substring(ENCHANT_PREFIX.length()) : null;
    }

    /** The lore's lines, from the one field. */
    public static List<String> loreLines(String lore) {
        List<String> out = new ArrayList<>();
        if (lore == null || lore.isBlank()) {
            return out;
        }
        for (String line : lore.split(java.util.regex.Pattern.quote(LORE_BREAK.strip()))) {
            if (!line.isBlank()) {
                out.add(line.strip());
            }
        }
        return out;
    }

    /** A look value's name. */
    public static String name(String key) {
        return switch (key) {
            case DISPLAY_NAME -> "Display name";
            case LORE -> "Lore";
            case GLINT -> "Glint";
            case CUSTOM_MODEL_DATA -> "Custom model data";
            case UNBREAKABLE -> "Unbreakable";
            default -> key;
        };
    }

    /** What a look value does. */
    public static String what(String key) {
        return switch (key) {
            case DISPLAY_NAME -> "The name it shows. An anvil can still rename it.";
            case LORE -> "Lines under its name. Type \" | \" between two lines.";
            case GLINT -> "The enchanted shine: default (shines when enchanted), on, or off.";
            case CUSTOM_MODEL_DATA -> "A number a resource pack uses for its own texture. Blank: the base item's look.";
            case UNBREAKABLE -> "true: it never loses durability.";
            default -> "";
        };
    }

    /**
     * What a variant's value is, for the sheet's control: an enchantment's level is a whole number from 1 to
     * 255, the glint one of its words, unbreakable true or false, a name or lore text, and any stat is its
     * {@link ItemStats#typeOf}.
     */
    public static ValueType typeOf(String key) {
        if (enchantmentOf(key) != null) {
            return ValueType.whole(1, MAX_ENCHANT);
        }
        return switch (key) {
            case DISPLAY_NAME, LORE -> ValueType.TEXT;
            case GLINT -> ValueType.choice(GLINTS);
            case UNBREAKABLE -> ValueType.BOOL;
            case CUSTOM_MODEL_DATA -> ValueType.WHOLE;
            default -> ItemStats.typeOf(key);
        };
    }

    /**
     * Why a typed value cannot be saved, or null.
     *
     * @param hasDurability the variant has durability (its max_damage or its base item's)
     */
    public static String problem(String key, String typed, boolean hasDurability) {
        String v = typed == null ? "" : typed.strip();
        String enchantment = enchantmentOf(key);
        if (enchantment != null) {
            Integer n = ItemStats.whole(v);
            return n == null || n < 1 || n > MAX_ENCHANT ? "The level must be a whole number from 1 to 255" : null;
        }
        switch (key) {
            case DISPLAY_NAME, LORE:
                return v.isEmpty() ? "Type a value, or press Restore for the base item's" : null;
            case GLINT:
                return GLINTS.contains(v.toLowerCase(Locale.ROOT)) ? null : "The value must be default, on, or off";
            case UNBREAKABLE:
                return "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v) ? null : "The value must be true or false";
            case CUSTOM_MODEL_DATA:
                return ItemStats.whole(v) == null ? "The value must be a whole number" : null;
            default:
                break;
        }
        String[] parts = ItemStats.attributeParts(key);
        if (parts != null && !SLOTS.contains(parts[1])) {
            return "The slot must be one of " + String.join(", ", SLOTS);
        }
        return ItemStats.problem(key, typed, hasDurability);
    }

    /**
     * The number a stack of the variant carries (an edit reaches the stacks already made): made
     * from its base item and its values, so a stack whose number differs is made current.
     */
    public static int hash(String baseItem, Map<String, String> values) {
        StringBuilder sb = new StringBuilder(baseItem);
        new TreeMap<>(values).forEach((k, v) -> sb.append('\n').append(k).append('=').append(v));
        return sb.toString().hashCode();
    }
}
