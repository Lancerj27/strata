package com.strata.gametest;

import com.strata.data.GameLayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** ITEM STATS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ItemStatsGameTests {

    private ItemStatsGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "items")
    public static void itemStatsSetAtGameStart(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
        double[] dealt = {0};
        sword.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) dealt[0] += modifier.amount();
        });
        double damage = dealt[0];
        helper.assertTrue(sword.getMaxDamage() == 100, "Golden sword durability " + sword.getMaxDamage() + ", not 100");
        helper.assertTrue(damage == 6, "Golden sword attack damage +" + damage + ", not +6");
        helper.assertTrue(sword.has(DataComponents.FIRE_RESISTANT), "The golden sword is not fire resistant");

        ItemStack cookie = new ItemStack(Items.COOKIE);
        var food = cookie.get(DataComponents.FOOD);
        helper.assertTrue(cookie.getMaxStackSize() == 16, "Cookie stack size " + cookie.getMaxStackSize() + ", not 16");
        helper.assertTrue(food != null && food.nutrition() == 6, "Cookie nutrition " + (food == null ? "none" : food.nutrition()));

        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        double[] armor = {0, 0};
        chest.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) armor[0] += modifier.amount();
            if (attribute.is(Attributes.ARMOR_TOUGHNESS)) armor[1] += modifier.amount();
        });
        helper.assertTrue(armor[0] == 10 && armor[1] == 2,
                "Diamond chestplate armor " + armor[0] + " and toughness " + armor[1] + ", not 10 and 2");

        float speed = new ItemStack(Items.GOLDEN_PICKAXE).getDestroySpeed(Blocks.STONE.defaultBlockState());
        helper.assertTrue(speed == 20, "Golden pickaxe speed on stone " + speed + ", not 20");

        helper.assertTrue(new ItemStack(Items.IRON_SWORD).getMaxDamage() == 250, "The iron sword changed");

        List<GameLayer.Row> sheet = GameLayer.read(GameLayer.dir().resolve(GameLayer.ITEMS));
        helper.assertTrue(has(sheet, "minecraft:golden_sword", GameLayer.COMPONENT, "max_damage", "32"),
                "game/items.csv lacks the golden sword's own durability 32");
        helper.assertTrue(has(sheet, "minecraft:cookie", GameLayer.COMPONENT, "food.nutrition", "2"),
                "game/items.csv lacks the cookie's own nutrition 2");
        helper.assertTrue(has(sheet, "minecraft:diamond_chestplate", GameLayer.COMPONENT,
                        "attribute|minecraft:generic.armor|chest", "8.0"),
                "game/items.csv lacks the diamond chestplate's own armor 8");
        helper.assertTrue(has(sheet, "minecraft:iron_sword", GameLayer.KIND, GameLayer.KIND, "Weapons")
                        && has(sheet, "minecraft:diamond_chestplate", GameLayer.KIND, GameLayer.KIND, "Armor")
                        && has(sheet, "minecraft:golden_pickaxe", GameLayer.KIND, GameLayer.KIND, "Tools")
                        && has(sheet, "minecraft:cookie", GameLayer.KIND, GameLayer.KIND, "Food")
                        && has(sheet, "minecraft:stone", GameLayer.KIND, GameLayer.KIND, "Blocks"),
                "An item's kind is wrong in game/items.csv");
        helper.succeed();
    }

    /**
     * A stack goes over the network with the item stats Strata changed, so a player's game without
     * Strata shows the server's values: the golden sword's patch carries its durability 100 though its stack
     * patch is empty.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "items")
    public static void itemStatsSentWithTheStack(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.GOLDEN_SWORD);
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                helper.getLevel().registryAccess());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, sword);
        buf.readVarInt();
        net.minecraft.network.codec.ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.ITEM).decode(buf);
        var patch = net.minecraft.core.component.DataComponentPatch.STREAM_CODEC.decode(buf);
        var damage = patch.get(DataComponents.MAX_DAMAGE);
        helper.assertTrue(damage != null && damage.isPresent() && damage.get() == 100,
                "The golden sword went out without its durability 100: " + damage);
        helper.assertTrue(sword.getComponentsPatch().isEmpty(), "The stack itself was changed");
        helper.succeed();
    }

    private static boolean has(List<GameLayer.Row> sheet, String entity, String group, String key, String value) {
        return sheet.stream().anyMatch(r -> r.entity().equals(entity) && r.group().equals(group)
                && r.key().equals(key) && r.value().equals(value));
    }
}
