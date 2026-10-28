package com.strata.model;

import java.util.List;
import java.util.Locale;

/** THE ITEM STATS, AS RULES. */
public final class ItemStats {

    private ItemStats() {
    }

    public static final String MAX_STACK_SIZE = "max_stack_size";
    public static final String MAX_DAMAGE = "max_damage";
    public static final String RARITY = "rarity";
    public static final String FIRE_RESISTANT = "fire_resistant";
    public static final String NUTRITION = "food.nutrition";
    public static final String SATURATION = "food.saturation";
    public static final String CAN_ALWAYS_EAT = "food.can_always_eat";
    public static final String EAT_SECONDS = "food.eat_seconds";
    public static final String MINING_SPEED = "tool.default_mining_speed";
    public static final String DAMAGE_PER_BLOCK = "tool.damage_per_block";
    /** A tool rule's speed: "tool.rule.N.speed". */
    public static final String RULE_PREFIX = "tool.rule.";
    public static final String RULE_SUFFIX = ".speed";
    /** An attribute modifier: "attribute|attribute id|slot group", with "#n" on a second one of the same. */
    public static final String ATTRIBUTE_PREFIX = "attribute|";

    /** The game's rarities, in its order. */
    public static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic");

    /**
     * EVERY OTHER COMPONENT: component|<the type's id>, its value the JSON the component's codec reads
     * (runtime.ItemComponents), or remove to take it off.
     */
    public static final String COMPONENT_PREFIX = "component|";
    /** The value that takes a component off the item. */
    public static final String REMOVE = "remove";
    /** The components shown as plain values of their own, so never as a component| value. */
    public static final java.util.Set<String> FLATTENED = java.util.Set.of("minecraft:max_stack_size",
            "minecraft:max_damage", "minecraft:rarity", "minecraft:fire_resistant", "minecraft:food",
            "minecraft:tool", "minecraft:attribute_modifiers");

    /** The key of a component's value. */
    public static String componentKey(String typeId) {
        return COMPONENT_PREFIX + typeId;
    }

    /** The component type id in a key, or null when the key is not a component's. */
    public static String componentId(String key) {
        return key != null && key.startsWith(COMPONENT_PREFIX) ? key.substring(COMPONENT_PREFIX.length()) : null;
    }

    /** A component's id in words: minecraft:repair_cost reads Repair cost, a mod's keeps its mod. */
    public static String componentWords(String typeId) {
        int colon = typeId.indexOf(':');
        String path = colon < 0 ? typeId : typeId.substring(colon + 1);
        String words = path.replace('_', ' ').replace('/', ' ');
        words = words.isEmpty() ? typeId : Character.toUpperCase(words.charAt(0)) + words.substring(1);
        return colon < 0 || typeId.startsWith("minecraft:") ? words : words + " (" + typeId.substring(0, colon) + ")";
    }

    /** The most the game lets a stack hold (DataComponents MAX_STACK_SIZE: 1 to 99). */
    public static final int MAX_STACK = 99;

    /** The kinds the list groups by, in order. */
    public static final String WEAPONS = "Weapons";
    public static final String ARMOR = "Armor";
    public static final String TOOLS = "Tools";
    public static final String FOOD = "Food";
    public static final String BLOCKS = "Blocks";
    public static final String OTHER = "Other";
    public static final List<String> KINDS = List.of(WEAPONS, ARMOR, TOOLS, FOOD, BLOCKS, OTHER);

    /** An item's kind from what it is: never from a list of item names. */
    public static String kind(boolean armor, boolean weapon, boolean tool, boolean food, boolean block) {
        if (armor) return ARMOR;
        if (weapon) return WEAPONS;
        if (tool) return TOOLS;
        if (food) return FOOD;
        if (block) return BLOCKS;
        return OTHER;
    }

    /** An attribute modifier's key. */
    public static String attributeKey(String attribute, String slot) {
        return ATTRIBUTE_PREFIX + attribute + "|" + slot;
    }

    /** A tool rule's key. */
    public static String ruleKey(int index) {
        return RULE_PREFIX + index + RULE_SUFFIX;
    }

    /** The rule's number in a tool rule key, or -1. */
    public static int ruleIndex(String key) {
        if (!key.startsWith(RULE_PREFIX) || !key.endsWith(RULE_SUFFIX)) return -1;
        try {
            return Integer.parseInt(key.substring(RULE_PREFIX.length(), key.length() - RULE_SUFFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The attribute id and the slot group of an attribute key, or null. */
    public static String[] attributeParts(String key) {
        if (!key.startsWith(ATTRIBUTE_PREFIX)) return null;
        String rest = key.substring(ATTRIBUTE_PREFIX.length());
        int hash = rest.indexOf('#');
        if (hash >= 0) rest = rest.substring(0, hash);
        int bar = rest.lastIndexOf('|');
        return bar < 0 ? null : new String[]{rest.substring(0, bar), rest.substring(bar + 1)};
    }

    /** A value's name in the list. */
    public static String name(String key, String attributeWords) {
        String[] parts = attributeParts(key);
        if (parts != null) {
            return attributeWords + " (" + parts[1] + ")";
        }
        int rule = ruleIndex(key);
        if (rule >= 0) {
            return "Mining speed, rule " + (rule + 1);
        }
        if (componentId(key) != null) {
            return componentWords(componentId(key));
        }
        return switch (key) {
            case MAX_STACK_SIZE -> "Max stack size";
            case MAX_DAMAGE -> "Durability";
            case RARITY -> "Rarity";
            case FIRE_RESISTANT -> "Fire resistant";
            case NUTRITION -> "Nutrition";
            case SATURATION -> "Saturation";
            case CAN_ALWAYS_EAT -> "Can always eat";
            case EAT_SECONDS -> "Eat seconds";
            case MINING_SPEED -> "Default mining speed";
            case DAMAGE_PER_BLOCK -> "Damage per block";
            default -> key;
        };
    }

    /**
     * What a value does.
     *
     * @param note the game layer's note on the value: a rule's blocks, a modifier's operation
     */
    public static String what(String key, String note) {
        String[] parts = attributeParts(key);
        if (parts != null) {
            String how = switch (note) {
                case "add_multiplied_base" -> "adds this share of the base value";
                case "add_multiplied_total" -> "multiplies the total by 1 plus this";
                default -> "adds this to the value";
            };
            String own = parts[0].endsWith("attack_damage") ? " The player's own attack damage (1) comes on top."
                    : parts[0].endsWith("attack_speed") ? " The player's own attack speed (4) comes on top." : "";
            return "While worn or held in the " + parts[1] + " slot, it " + how + "." + own;
        }
        if (componentId(key) != null) {
            return "The game's " + componentId(key) + " component, as the JSON a datapack writes it."
                    + " Set value checks it with the game's own reader. remove takes it off the item.";
        }
        if (ruleIndex(key) >= 0) {
            return "Mining speed on " + (note.isEmpty() ? "the blocks this rule names" : note) + ".";
        }
        return switch (key) {
            case MAX_STACK_SIZE -> "How many fit in one slot, 1 to 99. An item with durability must be 1.";
            case MAX_DAMAGE -> "How many uses it takes before it breaks.";
            case RARITY -> "The color of its name: common (white), uncommon (yellow), rare (aqua), epic (magenta).";
            case FIRE_RESISTANT -> "true: as an item on the ground it floats in lava and does not burn, as netherite does.";
            case NUTRITION -> "Hunger points it fills: 2 is one drumstick of the bar.";
            case SATURATION -> "Saturation it gives: hidden food that runs out before the hunger bar does.";
            case CAN_ALWAYS_EAT -> "true: it can be eaten with a full hunger bar.";
            case EAT_SECONDS -> "Seconds it takes to eat. Most foods take 1.6.";
            case MINING_SPEED -> "Mining speed on blocks no rule names. 1 is the speed of a hand.";
            case DAMAGE_PER_BLOCK -> "Durability it loses for each block it mines.";
            default -> "";
        };
    }

    /**
     * Why a typed value cannot be saved, or null.
     *
     * @param hasDurability the item has durability (its max_damage, the user's or the game's)
     */
    public static String problem(String key, String typed, boolean hasDurability) {
        String v = typed == null ? "" : typed.strip();
        if (v.isEmpty()) {
            return "Type a value, or press Restore for the game's";
        }
        if (attributeParts(key) != null) {
            return number(v) == null ? "The value must be a number" : null;
        }
        if (componentId(key) != null) {
            // Its JSON is checked against the component's codec (ItemStatsOps#problem,
            // runtime.ItemComponents).
            return null;
        }
        if (ruleIndex(key) >= 0 || MINING_SPEED.equals(key) || SATURATION.equals(key)) {
            Double d = number(v);
            return d == null || d < 0 ? "The value must be a number, 0 or more" : null;
        }
        return switch (key) {
            case MAX_STACK_SIZE -> {
                Integer n = whole(v);
                if (n == null || n < 1 || n > MAX_STACK) yield "The value must be a whole number from 1 to 99";
                yield hasDurability && n > 1 ? "An item with durability stacks to 1 only (the game's rule)" : null;
            }
            case MAX_DAMAGE -> {
                Integer n = whole(v);
                yield n == null || n < 1 ? "The value must be a whole number, 1 or more" : null;
            }
            case NUTRITION, DAMAGE_PER_BLOCK -> {
                Integer n = whole(v);
                yield n == null || n < 0 ? "The value must be a whole number, 0 or more" : null;
            }
            case EAT_SECONDS -> {
                Double d = number(v);
                yield d == null || d <= 0 ? "The value must be a number over 0" : null;
            }
            case RARITY -> RARITIES.contains(v.toLowerCase(Locale.ROOT)) ? null
                    : "The value must be common, uncommon, rare, or epic";
            case FIRE_RESISTANT, CAN_ALWAYS_EAT -> "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v) ? null
                    : "The value must be true or false";
            default -> "Strata does not edit this value";
        };
    }

    /** The words a value takes (rarity, the two switches), or empty for a number. */
    public static List<String> choices(String key) {
        return switch (key) {
            case RARITY -> RARITIES;
            case FIRE_RESISTANT, CAN_ALWAYS_EAT -> ValueType.BOOLEANS;
            default -> List.of();
        };
    }

    /**
     * What a value is, for the sheet's control: rarity is one of its four words, the two switches are true or
     * false, the stack size is a whole number from 1 to 99, and the rest are numbers in the range {@link
     * #problem} allows.
     */
    public static ValueType typeOf(String key) {
        if (attributeParts(key) != null) {
            return ValueType.NUMBER;
        }
        if (ruleIndex(key) >= 0) {
            return ValueType.number(0, Double.MAX_VALUE);
        }
        return switch (key) {
            case RARITY -> ValueType.choice(RARITIES);
            case FIRE_RESISTANT, CAN_ALWAYS_EAT -> ValueType.BOOL;
            case MAX_STACK_SIZE -> ValueType.whole(1, MAX_STACK);
            case MAX_DAMAGE -> ValueType.whole(1, Integer.MAX_VALUE);
            case NUTRITION, DAMAGE_PER_BLOCK -> ValueType.whole(0, Integer.MAX_VALUE);
            case SATURATION, MINING_SPEED -> ValueType.number(0, Double.MAX_VALUE);
            case EAT_SECONDS -> ValueType.number(0, Double.MAX_VALUE);
            default -> ValueType.TEXT;
        };
    }

    /** A whole number, or null. */
    public static Integer whole(String v) {
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A finite number, or null. */
    public static Double number(String v) {
        try {
            double d = Double.parseDouble(v.strip());
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
