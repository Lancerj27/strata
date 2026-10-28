package com.strata.gametest;

import com.strata.domain.ItemVariantOps;
import com.strata.runtime.ItemVariants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ITEM VARIANTS, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ItemVariantGameTests {

    private ItemVariantGameTests() {
    }

    private static final String NAME = "gt_frost_blade";

    /** A variant's stack: the base item with the variant's name, lore, stats, and enchantments; the rest the base item's. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "itemvariants")
    public static void variantStackHasItsValues(GameTestHelper helper) {
        ItemStack stack = ItemVariants.make(NAME, 1, helper.getLevel().registryAccess());
        helper.assertTrue(stack.is(Items.IRON_SWORD), "The variant is not an iron sword: " + stack);
        helper.assertTrue("Frost Blade".equals(stack.getHoverName().getString()), "Name " + stack.getHoverName().getString());
        var lore = stack.get(DataComponents.LORE);
        helper.assertTrue(lore != null && lore.lines().size() == 2, "Lore " + lore);
        helper.assertTrue(stack.get(DataComponents.RARITY) == Rarity.RARE, "Rarity " + stack.get(DataComponents.RARITY));
        double[] mods = {0, 0, 0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) mods[0] += modifier.amount();
            if (attribute.is(Attributes.MOVEMENT_SPEED)) mods[1] += modifier.amount();
            if (attribute.is(Attributes.ATTACK_SPEED)) mods[2] += modifier.amount();
        });
        helper.assertTrue(mods[0] == 7 && mods[1] == 0.05 && Math.abs(mods[2] + 2.4) < 1e-6,
                "Damage " + mods[0] + ", move speed " + mods[1] + ", attack speed " + mods[2]
                        + " (not 7, 0.05, and the iron sword's -2.4)");
        helper.assertTrue(stack.getMaxDamage() == 250, "Durability " + stack.getMaxDamage() + ", not the iron sword's 250");
        var sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("sharpness")));
        helper.assertTrue(stack.getEnchantmentLevel(sharpness) == 3, "Sharpness " + stack.getEnchantmentLevel(sharpness));
        helper.assertTrue(NAME.equals(ItemVariants.variantOf(stack)), "The stack does not say its variant");
        helper.assertTrue(ItemVariants.stack("variant:" + NAME, 1, helper.getLevel().registryAccess()).is(Items.IRON_SWORD)
                        && ItemVariants.stack("minecraft:stick", 2, null).getCount() == 2
                        && ItemVariants.make("no_such_variant", 1, null).isEmpty(),
                "An item column does not make the right stack");
        helper.succeed();
    }

    /** A mob's Equipment row that names a variant arms it with the variant. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "itemvariants_worn")
    public static void mobWearsAVariantFromItsEquipment(GameTestHelper helper) {
        var made = com.strata.domain.MobOps.ensureDefaultVariant("minecraft:villager");
        String owner = made.name();
        var rows = com.strata.domain.EquipmentOps.equipmentFor(owner, "minecraft:villager", "global", "", java.util.List.of());
        var hand = com.strata.domain.EquipmentOps.saveEquipment(owner, "global", "", null, "mainhand",
                "variant:" + NAME, "100", "", "0", "0", false, "", rows);
        if (!made.report().ok() || !hand.ok()) {
            com.strata.domain.EquipmentOps.restoreEquipment(owner, "global", "");
            helper.fail("Could not save the villager's equipment: " + hand.first());
            return;
        }
        net.minecraft.world.entity.npc.Villager villager = net.minecraft.world.entity.EntityType.VILLAGER.spawn(
                helper.getLevel(), helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)),
                net.minecraft.world.entity.MobSpawnType.NATURAL);
        helper.runAfterDelay(10, () -> {
            ItemStack held = villager == null ? ItemStack.EMPTY : villager.getItemBySlot(EquipmentSlot.MAINHAND);
            com.strata.domain.EquipmentOps.restoreEquipment(owner, "global", "");
            helper.assertTrue(NAME.equals(ItemVariants.variantOf(held)) && "Frost Blade".equals(held.getHoverName().getString()),
                    "The villager holds " + held + " (" + held.getHoverName().getString() + "), not the variant");
            helper.succeed();
        });
    }

    /** A loot entry with strata:item_variant rolls the variant (chest loot). */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "itemvariants_loot")
    public static void lootFunctionRollsTheVariant(GameTestHelper helper) {
        var json = com.google.gson.JsonParser.parseString("{\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":"
                + "\"minecraft:item\",\"name\":\"minecraft:iron_sword\",\"functions\":[{\"function\":"
                + "\"minecraft:set_count\",\"count\":1},{\"function\":\"strata:item_variant\",\"variant\":\"" + NAME + "\"}]}]}]}");
        var ops = helper.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var table = net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.parse(ops, json).getOrThrow();
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
        var items = table.getRandomItems(params);
        helper.assertTrue(items.size() == 1 && NAME.equals(ItemVariants.variantOf(items.get(0))),
                "The loot rolled " + items + ", not the variant");
        helper.succeed();
    }

    /** A hit with the variant in the main hand gives its effect; in the off hand it does nothing. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "itemvariants_effect")
    public static void onHitEffectOnlyFromItsOwnSlot(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var armed = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, 1, 2, 1);
        var offhand = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, 3, 2, 1);
        var pigA = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 1, 2, 4);
        var pigB = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.PIG, 3, 2, 4);
        armed.setItemSlot(EquipmentSlot.MAINHAND, ItemVariants.make(NAME, 1, registries));
        offhand.setItemSlot(EquipmentSlot.OFFHAND, ItemVariants.make(NAME, 1, registries));
        helper.runAfterDelay(5, () -> {
            pigA.hurt(helper.getLevel().damageSources().mobAttack(armed), 1);
            pigB.hurt(helper.getLevel().damageSources().mobAttack(offhand), 1);
            boolean slowA = pigA.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            boolean slowB = pigB.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(slowA, "A hit with the Frost Blade in the main hand gave no Slowness");
            helper.assertTrue(!slowB, "The Frost Blade in the off hand gave Slowness");
            helper.succeed();
        });
    }

    /** A chest loot entry of a variant writes its base item and strata:item_variant, and reads back as the variant. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "itemvariants_chest")
    public static void chestEntryOfAVariantRoundTrips(GameTestHelper helper) {
        var sheet = com.strata.domain.ChestsOps.sheet("minecraft:chests/simple_dungeon");
        var form = new com.strata.domain.ChestsOps.EntryForm("variant:" + NAME, "5", "1", "1");
        helper.assertTrue(com.strata.domain.ChestsOps.entryProblem(null, form) == null,
                "The form is refused: " + com.strata.domain.ChestsOps.entryProblem(null, form));
        helper.assertTrue(com.strata.domain.ChestsOps.entryProblem(null,
                        new com.strata.domain.ChestsOps.EntryForm("variant:no_such", "5", "1", "1")) != null,
                "An unknown variant is not refused");
        var writes = com.strata.domain.ChestsOps.entryWrites(sheet, null, 0, form);
        String prefix = writes.keySet().iterator().next();
        prefix = prefix.substring(0, prefix.lastIndexOf('.'));
        helper.assertTrue("\"minecraft:iron_sword\"".equals(writes.get(prefix + ".name")),
                "The entry's item is " + writes.get(prefix + ".name"));
        helper.assertTrue("\"strata:item_variant\"".equals(writes.get(prefix + ".functions.1.function"))
                        && ("\"" + NAME + "\"").equals(writes.get(prefix + ".functions.1.variant")),
                "The entry lacks the variant function: " + writes);
        helper.succeed();
    }

    /** An edit reaches a stack made before it; the player's own enchantment stays. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "itemvariants_edit")
    public static void editRefreshesAStackAndKeepsItsEnchantments(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack stack = ItemVariants.make(NAME, 1, registries);
        var unbreaking = registries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("unbreaking")));
        stack.enchant(unbreaking, 2);
        var values = ItemVariantOps.values(NAME);
        var nameValue = values.stream().filter(v -> v.key().equals("display_name")).findFirst().orElseThrow();
        var saved = ItemVariantOps.save(nameValue, "Frost Edge", values);
        helper.assertTrue(saved.ok(), "Could not save the name: " + saved.first());
        boolean changed = ItemVariants.refresh(stack);
        boolean again = ItemVariants.refresh(stack);
        String now = stack.getHoverName().getString();
        var back = ItemVariantOps.save(nameValue, "Frost Blade", ItemVariantOps.values(NAME));
        helper.assertTrue(changed && !again, "Refresh changed " + changed + ", then again " + again);
        helper.assertTrue("Frost Edge".equals(now), "The edit did not reach the stack: " + now);
        helper.assertTrue(stack.getEnchantmentLevel(unbreaking) == 2, "The player's Unbreaking went");
        helper.assertTrue(back.ok(), "Could not put the name back");
        helper.succeed();
    }
}
