package com.strata.gametest;

import com.strata.model.ItemStats;
import com.strata.runtime.ItemComponents;
import com.strata.runtime.ItemStatsRuntime;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** EVERY ITEM COMPONENT, BY ITS CODEC. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ItemComponentGameTests {

    private ItemComponentGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "item_components")
    public static void anyComponentIsReadCheckedAndSet(GameTestHelper helper) {
        var rows = ItemComponents.rows("minecraft:stick", Items.STICK);
        helper.assertTrue(rows.stream().anyMatch(r -> r.key().equals(ItemStats.componentKey("minecraft:repair_cost"))
                && r.value().equals("0")), "The stick's repair cost was not read as 0: " + rows);
        helper.assertTrue(rows.stream().noneMatch(r -> r.key().equals(ItemStats.componentKey("minecraft:max_stack_size"))),
                "Stack size was read twice, as a component too");

        String repair = ItemStats.componentKey("minecraft:repair_cost");
        String lore = ItemStats.componentKey("minecraft:lore");
        helper.assertTrue(ItemComponents.problem(repair, "5") == null, "A repair cost of 5 was refused");
        helper.assertTrue(ItemComponents.problem(repair, "{\"cost\": 5}") != null, "An object was taken as a repair cost");
        helper.assertTrue(ItemComponents.problem(repair, "{not json") != null, "Broken JSON was taken");
        helper.assertTrue(ItemComponents.problem(repair, "remove") == null, "remove was refused");
        helper.assertTrue(ItemComponents.problem(ItemStats.componentKey("minecraft:no_such_thing"), "1") != null,
                "A component the game does not have was taken");
        helper.assertTrue(ItemComponents.problem(lore, "[\"\\\"Forged in the deep\\\"\"]") == null,
                "A line of lore was refused: " + ItemComponents.problem(lore, "[\"\\\"Forged in the deep\\\"\"]"));

        Map<String, String> values = new LinkedHashMap<>();
        values.put(repair, "7");
        values.put(lore, "[\"\\\"Forged in the deep\\\"\"]");
        values.put(ItemStats.componentKey("minecraft:dyed_color"), "{\"rgb\": 16711680}");
        DataComponentPatch.Builder b = DataComponentPatch.builder();
        ItemStatsRuntime.patch(b, Items.STICK, values);
        ItemStack stick = new ItemStack(Items.STICK);
        stick.applyComponents(b.build());
        helper.assertTrue(Integer.valueOf(7).equals(stick.get(DataComponents.REPAIR_COST)),
                "The repair cost is " + stick.get(DataComponents.REPAIR_COST) + ", not 7");
        var lines = stick.get(DataComponents.LORE);
        helper.assertTrue(lines != null && lines.lines().size() == 1
                        && lines.lines().get(0).getString().equals("Forged in the deep"), "The lore is " + lines);
        var dye = stick.get(DataComponents.DYED_COLOR);
        helper.assertTrue(dye != null && dye.rgb() == 0xFF0000, "The dye colour is " + dye);

        DataComponentPatch.Builder off = DataComponentPatch.builder();
        ItemStatsRuntime.patch(off, Items.STICK, Map.of(repair, "remove"));
        ItemStack plain = new ItemStack(Items.STICK);
        plain.applyComponents(off.build());
        helper.assertTrue(!plain.has(DataComponents.REPAIR_COST), "remove left the repair cost on");
        helper.succeed();
    }
}
