package com.strata.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strata.Strata;
import com.strata.data.JsonOverrides;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

/** GAME RULES PER PROFILE. */
public final class GameRulesData {

    private GameRulesData() {
    }

    /** The one entry of the Game rules kind. */
    public static final String ENTRY = "minecraft:game_rules";

    /** Every rule by its name, the game's and the mods'. */
    private static Map<String, GameRules.Key<?>> keys() {
        Map<String, GameRules.Key<?>> out = new LinkedHashMap<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                out.put(key.getId(), key);
            }
        });
        return out;
    }

    /**
     * Every rule's category as the game names it ("gamerule.category.mobs"), by the rule's name: the Game
     * rules list's groups.
     */
    public static Map<String, String> categories() {
        Map<String, String> out = new LinkedHashMap<>();
        keys().forEach((name, key) -> out.put(name, key.getCategory().getDescriptionId()));
        return out;
    }

    /** Reads every rule's default into the game layer, typed: a flag stays a flag, a number a number. */
    public static void read() {
        GameRules defaults = new GameRules();
        JsonObject entry = new JsonObject();
        keys().forEach((name, key) -> {
            String v = defaults.getRule(key).serialize();
            if (v.equals("true") || v.equals("false")) {
                entry.addProperty(name, Boolean.parseBoolean(v));
            } else {
                try {
                    entry.addProperty(name, Integer.parseInt(v));
                } catch (NumberFormatException e) {
                    entry.addProperty(name, v);
                }
            }
        });
        JsonOverrides.storeGame(JsonOverrides.GAME_RULES, Map.of(ENTRY, entry));
    }

    /** Sets the profile's changed rules on the server's world. */
    public static void apply(MinecraftServer server) {
        if (server == null) {
            return;
        }
        JsonOverrides.Override o = JsonOverrides.overrides(JsonOverrides.GAME_RULES)
                .getOrDefault(ENTRY, JsonOverrides.Override.NONE);
        if (o.isEmpty()) {
            return;
        }
        Map<String, GameRules.Key<?>> keys = keys();
        JsonObject now = JsonOverrides.applied(JsonOverrides.GAME_RULES, new JsonObject(), o);
        for (String name : o.set().keySet()) {
            GameRules.Key<?> key = keys.get(name);
            JsonElement value = now.get(name);
            if (key == null) {
                Strata.LOGGER.warn("Game rules: no rule {} in this game - skipped", name);
                continue;
            }
            GameRules.Value<?> rule = server.getGameRules().getRule(key);
            String v = o.set().get(name);
            try {
                if (rule instanceof GameRules.BooleanValue b) {
                    b.set(Boolean.parseBoolean(v), server);
                } else if (rule instanceof GameRules.IntegerValue i) {
                    i.set(Integer.parseInt(v), server);
                }
            } catch (RuntimeException e) {
                Strata.LOGGER.warn("Game rules: {} = {} could not be set - {}", name, value, e.toString());
            }
        }
    }
}
