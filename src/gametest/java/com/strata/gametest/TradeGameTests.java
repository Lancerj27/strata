package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** A VILLAGER'S TRADES FOLLOW ITS BIOME, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class TradeGameTests {

    private TradeGameTests() {
    }

    private static final String FARMER = "minecraft:farmer";

    @GameTest(template = "arena", timeoutTicks = 40, batch = "trades")
    public static void villagerTradesFollowTheBiome(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        String biome = helper.getLevel().getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        List<Map<String, String>> written = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                written.add(write("", "", "g" + i, "remove", "", "", ""));
            }
            written.add(write("", "", "a1", "add", "minecraft:emerald", "minecraft:diamond", "1"));
            written.add(write("biome", biome, "a1", "add", "minecraft:emerald", "minecraft:netherite_ingot", "1"));
            Config.reload();
            TreeSet<String> here = offers(helper, helper.absolutePos(new BlockPos(1, 2, 1)));
            helper.assertTrue(here.equals(new TreeSet<>(List.of("minecraft:diamond", "minecraft:netherite_ingot"))),
                    "A Novice farmer in " + biome + " should offer the global diamond and the biome's netherite: " + here);
            TableWriter.delete(Schema.Trades.FILE, written.removeLast());
            Config.reload();
            TreeSet<String> global = offers(helper, helper.absolutePos(new BlockPos(3, 2, 3)));
            helper.assertTrue(global.equals(new TreeSet<>(List.of("minecraft:diamond"))),
                    "Without the biome's trade a Novice farmer should offer only the diamond: " + global);
        } finally {
            for (Map<String, String> keys : written) {
                TableWriter.delete(Schema.Trades.FILE, keys);
            }
            Config.reload();
        }
        helper.succeed();
    }

    /** A VILLAGER TAKES ITS BIOME'S TYPE AND PROFESSION. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "villagerdata")
    public static void villagerTakesTheBiomesTypeAndProfession(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        String biome = helper.getLevel().getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("");
        var typeSetting = com.strata.model.SpecialSpawners.setting("villagers", "villager_type");
        var jobSetting = com.strata.model.SpecialSpawners.setting("villagers", "profession");
        com.strata.domain.SpecialSpawnsOps.save("biome", biome, typeSetting, "minecraft:snow");
        com.strata.domain.SpecialSpawnsOps.save("biome", biome, jobSetting, "minecraft:librarian");
        try {
            Villager grown = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
            Villager baby = EntityType.VILLAGER.create(helper.getLevel());
            baby.setAge(-24000);
            baby.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            helper.getLevel().addFreshEntity(baby);
            String type = BuiltInRegistries.VILLAGER_TYPE.getKey(grown.getVillagerData().getType()).toString();
            String profession = com.strata.runtime.TradesRuntime.professionId(grown.getVillagerData().getProfession());
            helper.assertTrue(type.equals("minecraft:snow") && profession.equals("minecraft:librarian")
                            && grown.getVillagerXp() == 1,
                    "A villager in " + biome + " should be a snow librarian with 1 XP: " + type + " " + profession
                            + " " + grown.getVillagerXp());
            String babyType = BuiltInRegistries.VILLAGER_TYPE.getKey(baby.getVillagerData().getType()).toString();
            String babyJob = com.strata.runtime.TradesRuntime.professionId(baby.getVillagerData().getProfession());
            helper.assertTrue(babyType.equals("minecraft:snow") && babyJob.equals("minecraft:none"),
                    "A baby should take the type only: " + babyType + " " + babyJob);
            baby.discard();
            helper.runAfterDelay(20, () -> {
                String later = com.strata.runtime.TradesRuntime.professionId(grown.getVillagerData().getProfession());
                helper.assertTrue(later.equals("minecraft:librarian"),
                        "With no lectern the villager should stay a librarian: " + later);
                helper.succeed();
            });
        } finally {
            com.strata.domain.SpecialSpawnsOps.restore("biome", biome, typeSetting);
            com.strata.domain.SpecialSpawnsOps.restore("biome", biome, jobSetting);
        }
    }

    /** What a new Novice farmer offers, by result item. */
    private static TreeSet<String> offers(GameTestHelper helper, BlockPos pos) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(villager != null, "A villager should be made");
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.getLevel().addFreshEntity(villager);
        VillagerProfession farmer = BuiltInRegistries.VILLAGER_PROFESSION.get(
                net.minecraft.resources.ResourceLocation.parse(FARMER));
        villager.setVillagerData(villager.getVillagerData().setProfession(farmer).setLevel(1));
        TreeSet<String> out = new TreeSet<>();
        for (MerchantOffer offer : villager.getOffers()) {
            out.add(BuiltInRegistries.ITEM.getResourceKey(offer.getResult().getItem())
                    .map(k -> k.location().toString()).orElse(""));
        }
        villager.discard();
        return out;
    }

    private static Map<String, String> write(String scope, String scopeId, String row, String mode, String costA,
                                             String result, String count) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Trades.PROFESSION, FARMER);
        keys.put(Schema.Trades.LEVEL, "1");
        keys.put(Schema.Trades.SCOPE, scope);
        keys.put(Schema.Trades.SCOPE_ID, scopeId);
        keys.put(Schema.Trades.ROW, row);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Trades.MODE, mode);
        values.put(Schema.Trades.COST_A, costA);
        values.put(Schema.Trades.COST_A_COUNT, costA.isEmpty() ? "" : "1");
        values.put(Schema.Trades.RESULT, result);
        values.put(Schema.Trades.RESULT_COUNT, count);
        TableWriter.upsert(Schema.Trades.FILE, keys, values);
        return keys;
    }
}
