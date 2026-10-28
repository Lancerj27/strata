package com.strata.gametest;

import com.strata.game.RegistryQuery;
import com.strata.model.LootCondition;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** MOB LOOT IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class LootGameTests {

    private LootGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "loot")
    public static void deathCausesComeFromTheGame(GameTestHelper helper) {
        List<String> ids = RegistryQuery.damageTypesFor(helper.getLevel().registryAccess()).stream()
                .map(RegistryQuery.Option::id)
                .filter(id -> !id.startsWith("#"))
                .toList();
        helper.assertTrue(ids.contains("minecraft:drown"), "the drowned cause is missing");
        helper.assertTrue(ids.contains("minecraft:lava"), "the lava cause is missing");
        helper.assertTrue(ids.contains("minecraft:on_fire"), "the sunlight (on_fire) cause is missing");
        helper.assertTrue(ids.size() >= 30, "only " + ids.size() + " damage types; the game's list is larger");
        helper.assertTrue(!ids.contains("#minecraft:is_fire"), "a death cause is one damage type, never a tag");
        helper.assertTrue(LootCondition.checkDeathCause("minecraft:drown", "minecraft:drown"),
                "the drowned match");
        helper.assertTrue(!LootCondition.checkDeathCause("minecraft:drown", "minecraft:lava"),
                "a cause must not match another death");
        helper.succeed();
    }
}