package com.strata.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One villager or wandering trader trade, in the game's words: what the trader wants (cost A and an optional
 * cost B), what it gives (the result), how many times before it restocks (max uses), the XP the trader earns,
 * and the price multiplier (how much demand and reputation move the price).
 *
 * @param kind "plain" for a trade of fixed items; else the game's kind of a trade with a random part (an
 *     enchanted book, a treasure map, dyed armor...), which the editor can remove or replace but whose random
 *     part it cannot set
 * @param note what a special kind does, in words ("" for plain)
 */
public record Trade(String costA, int costACount, String costB, int costBCount, String result, int resultCount,
                    int maxUses, int xp, float priceMultiplier, String kind, String note) {

    public static final String PLAIN = "plain";

    public boolean plain() {
        return PLAIN.equals(kind);
    }

    /** A plain trade of fixed items. */
    public static Trade plain(String costA, int costACount, String costB, int costBCount, String result,
                              int resultCount, int maxUses, int xp, float priceMultiplier) {
        return new Trade(costA, costACount, costB == null ? "" : costB, costB == null || costB.isEmpty() ? 0 : costBCount,
                result, resultCount, maxUses, xp, priceMultiplier, PLAIN, "");
    }

    /** The game layer's value: the fields joined by "|". */
    public String encode() {
        return String.join("|", kind, costA, String.valueOf(costACount), costB, String.valueOf(costBCount), result,
                String.valueOf(resultCount), String.valueOf(maxUses), String.valueOf(xp),
                String.valueOf(priceMultiplier), note.replace('|', '/'));
    }

    /** The trade a game-layer value holds, or null. */
    public static Trade decode(String value) {
        String[] p = value == null ? new String[0] : value.split("\\|", -1);
        if (p.length < 11) {
            return null;
        }
        try {
            return new Trade(p[1], Integer.parseInt(p[2]), p[3], Integer.parseInt(p[4]), p[5], Integer.parseInt(p[6]),
                    Integer.parseInt(p[7]), Integer.parseInt(p[8]), Float.parseFloat(p[9]), p[0], p[10]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- the layers: global, then the tier, then the biome (as loot and equipment) ----

    /**
     * One edit row, as the edit table holds it.
     *
     * @param key "g3" edits or removes the game's fourth trade of the list; "a1" is a trade the user added at
     *     this scope
     * @param remove true for a removed trade
     * @param trade the trade's values (null for a remove)
     */
    public record Edit(String key, boolean remove, Trade trade) {
    }

    /**
     * One trade as a list serves it.
     *
     * @param key "g3" (the game's fourth) or "a1@tier:t1" (added at that scope)
     * @param trade what it offers
     * @param edited a game trade whose values a layer changed
     * @param layer the scope that last set it: "" the game's, else "global", "tier", "biome"
     */
    public record Entry(String key, Trade trade, boolean edited, String layer) {
    }

    /**
     * A list as a scope serves it (pure): the game's trades, then each layer's edits in order (global, tier,
     * biome).
     *
     * @param layers each scope's name ("global", "tier", "biome") to its edits, in that order
     */
    public static List<Entry> effective(List<Trade> game, Map<String, List<Edit>> layers) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < game.size(); i++) {
            out.add(new Entry("g" + i, game.get(i), false, ""));
        }
        layers.forEach((layer, edits) -> {
            for (Edit e : edits) {
                int at = -1;
                for (int i = 0; i < out.size(); i++) {
                    if (out.get(i).key().equals(e.key())) {
                        at = i;
                        break;
                    }
                }
                if (at >= 0) {
                    // An edit of a trade the list holds: the game's ("g3") or one a wider scope added
                    // ("a1@global").
                    if (e.remove()) {
                        out.remove(at);
                    } else if (e.trade() != null) {
                        out.set(at, new Entry(e.key(), e.trade(), true, layer));
                    }
                } else if (e.key().startsWith("a") && !e.key().contains("@") && !e.remove() && e.trade() != null) {
                    out.add(new Entry(e.key() + "@" + layer, e.trade(), false, layer));
                }
            }
        });
        return out;
    }
}
