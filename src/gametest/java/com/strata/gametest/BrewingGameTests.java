package com.strata.gametest;

import com.strata.data.JsonOverrides;
import com.strata.model.JsonPaths;
import com.strata.runtime.BrewingData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * BREWING THROUGH THE OVERRIDE SHEET: the stand's mixes are read as the game builds them, and an edited entry
 * - one mix removed, one added - is what the stand makes.
 */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class BrewingGameTests {

    private BrewingGameTests() {
    }

    private static ItemStack potion(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> p) {
        ItemStack s = new ItemStack(Items.POTION);
        s.set(DataComponents.POTION_CONTENTS, new PotionContents(p));
        return s;
    }

    private static PotionBrewing build() {
        PotionBrewing.Builder builder = new PotionBrewing.Builder(FeatureFlags.DEFAULT_FLAGS);
        PotionBrewing.addVanillaMixes(builder);
        BrewingData.onRegister(new RegisterBrewingRecipesEvent(builder, null));
        return builder.build();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "brewing")
    public static void brewingIsReadAndEdited(GameTestHelper helper) {
        PotionBrewing plain = build();
        var game = JsonOverrides.game(JsonOverrides.BREWING).get(BrewingData.ENTRY);
        helper.assertTrue(game != null && game.getAsJsonArray("potion_mixes").size() > 30, "The mixes were not read");
        helper.assertTrue(plain.hasMix(potion(Potions.AWKWARD), new ItemStack(Items.SUGAR)), "Awkward + sugar is not a mix");

        int sugar = -1;
        var mixes = game.getAsJsonArray("potion_mixes");
        for (int i = 0; i < mixes.size(); i++) {
            var m = mixes.get(i).getAsJsonObject();
            if (m.get("input").getAsString().equals("minecraft:awkward") && m.get("ingredient").getAsString().equals("minecraft:sugar")) {
                sugar = i;
            }
        }
        helper.assertTrue(sugar >= 0, "Awkward + sugar was not read");
        var edited = JsonPaths.duplicate(game, "potion_mixes[" + sugar + "]", "");
        edited = JsonPaths.set(edited, "potion_mixes[" + (mixes.size()) + "].ingredient", "minecraft:carrot");
        edited = JsonPaths.remove(edited, "potion_mixes[" + sugar + "]");
        JsonOverrides.store(JsonOverrides.BREWING, BrewingData.ENTRY, new JsonOverrides.Override(java.util.Map.of(), false, edited));
        try {
            PotionBrewing now = build();
            helper.assertTrue(!now.hasMix(potion(Potions.AWKWARD), new ItemStack(Items.SUGAR)), "Sugar still makes swiftness");
            helper.assertTrue(now.hasMix(potion(Potions.AWKWARD), new ItemStack(Items.CARROT)), "The carrot mix was not added");
            helper.assertTrue(now.hasMix(potion(Potions.AWKWARD), new ItemStack(Items.MAGMA_CREAM)), "Another mix was lost");
        } finally {
            JsonOverrides.store(JsonOverrides.BREWING, BrewingData.ENTRY, JsonOverrides.Override.NONE);
        }
        helper.succeed();
    }

    /** Game rules per profile: read with their defaults, and the changed ones set on the world. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "game_rules")
    public static void gameRulesAreSetFromTheProfile(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var rules = server.getGameRules();
        boolean daylight = rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        int ticks = rules.getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
        com.strata.runtime.GameRulesData.read();
        var game = JsonOverrides.game(JsonOverrides.GAME_RULES).get(com.strata.runtime.GameRulesData.ENTRY);
        helper.assertTrue(game != null && game.get("randomTickSpeed").getAsInt() == 3, "The rules were not read");
        JsonOverrides.store(JsonOverrides.GAME_RULES, com.strata.runtime.GameRulesData.ENTRY, new JsonOverrides.Override(
                java.util.Map.of("doDaylightCycle", String.valueOf(!daylight), "randomTickSpeed", "7"), false));
        try {
            com.strata.runtime.GameRulesData.apply(server);
            helper.assertTrue(rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT) == !daylight,
                    "doDaylightCycle was not set");
            helper.assertTrue(rules.getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING) == 7, "randomTickSpeed was not set");
        } finally {
            JsonOverrides.store(JsonOverrides.GAME_RULES, com.strata.runtime.GameRulesData.ENTRY, JsonOverrides.Override.NONE);
            rules.getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(daylight, server);
            rules.getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(ticks, server);
        }
        helper.succeed();
    }
}
