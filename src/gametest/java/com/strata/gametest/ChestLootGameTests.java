package com.strata.gametest;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.strata.data.GameLayer;
import com.strata.data.LootJson;
import com.strata.domain.LootTableOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** CHEST LOOT, IN THE GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ChestLootGameTests {

    private ChestLootGameTests() {
    }

    private static final String TABLE = "minecraft:chests/simple_dungeon";

    @GameTest(template = "arena", timeoutTicks = 20)
    public static void dungeonChestWithAnAddedDiamondRollsIt(GameTestHelper helper) {
        List<GameLayer.Row> rows = GameLayer.read(GameLayer.dir().resolve(GameLayer.LOOT));
        boolean listed = rows.stream().anyMatch(r -> GameLayer.CHEST.equals(r.group()) && TABLE.equals(r.value()));
        var sheet = new LootTableOps.Sheet(TABLE, LootTableOps.values(rows, TABLE, Map.of()));
        if (!listed || sheet.values().isEmpty()) {
            helper.fail("The dungeon chest's table is not in the game layer (listed " + listed + ")");
            return;
        }
        Map<String, String> own = new LinkedHashMap<>();
        Set<String> old = new HashSet<>();
        for (LootTableOps.Value v : sheet.values()) {
            String entry = LootTableOps.entryOf(v.path());
            if (entry == null || LootTableOps.poolOf(entry) != 0) {
                continue;
            }
            if (v.path().equals(entry + ".weight")) {
                own.putAll(LootTableOps.removal(sheet, v).write());
            } else if (v.path().equals(entry + ".name")) {
                old.add(LootJson.shown(v.game()));
            }
        }
        own.putAll(LootTableOps.itemRows(0, LootTableOps.nextEntry(sheet.values(), 0), "minecraft:diamond"));
        List<Map<String, String>> edits = new ArrayList<>();
        own.forEach((path, value) -> edits.add(Map.of("table", TABLE, "path", path, "value", value)));
        JsonElement json = LootJson.editedTables(rows, edits).get(TABLE);
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var parsed = LootTable.DIRECT_CODEC.parse(ops, json);
        if (parsed.error().isPresent()) {
            helper.fail("The game refuses the edited table: " + parsed.error().get().message());
            return;
        }
        LootTable table = parsed.getOrThrow();
        int diamonds = 0;
        List<String> wrong = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LootParams params = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)))
                    .create(LootContextParamSets.CHEST);
            for (ItemStack stack : table.getRandomItems(params)) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.equals("minecraft:diamond")) {
                    diamonds++;
                } else if (old.contains(id)) {
                    wrong.add(id);
                }
            }
        }
        helper.assertTrue(diamonds > 0, "30 rolls gave no diamond");
        helper.assertTrue(wrong.isEmpty(), "Removed items still roll: " + wrong);
        helper.succeed();
    }

    /**
     * THE FORMS' WRITES, IN THE GAME: the first pool keeps only its name tag, whose count the game does not
     * set, now 3 (Strata's own set_count), and the pool picks exactly twice (its rolls).
     */
    @GameTest(template = "arena", timeoutTicks = 20)
    public static void dungeonChestFormSetsCountAndRolls(GameTestHelper helper) {
        List<GameLayer.Row> rows = GameLayer.read(GameLayer.dir().resolve(GameLayer.LOOT));
        var sheet = new LootTableOps.Sheet(TABLE, LootTableOps.values(rows, TABLE, Map.of()));
        var entries = com.strata.domain.ChestsOps.entries(sheet);
        var tag = entries.stream().filter(e -> e.pool() == 0 && e.item().equals("minecraft:name_tag")).findFirst();
        if (tag.isEmpty()) {
            helper.fail("The dungeon chest's first pool has no name tag: " + entries);
            return;
        }
        Map<String, String> own = new LinkedHashMap<>();
        for (var e : entries) {
            if (e.pool() == 0 && e != tag.get() && e.single()) {
                own.put(e.prefix() + ".weight", "0");
            }
        }
        own.putAll(com.strata.domain.ChestsOps.entryWrites(sheet, tag.get(), 0,
                new com.strata.domain.ChestsOps.EntryForm("minecraft:name_tag", "1", "3", "3")));
        own.putAll(com.strata.domain.ChestsOps.rollsWrites(sheet, com.strata.domain.ChestsOps.pools(sheet).get(0), 2, 2));
        List<Map<String, String>> edits = new ArrayList<>();
        own.forEach((path, value) -> edits.add(Map.of("table", TABLE, "path", path, "value", value)));
        var parsed = LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,
                helper.getLevel().registryAccess()), LootJson.editedTables(rows, edits).get(TABLE));
        if (parsed.error().isPresent()) {
            helper.fail("The game refuses the edited table: " + parsed.error().get().message());
            return;
        }
        LootTable table = parsed.getOrThrow();
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            LootParams params = new LootParams.Builder(helper.getLevel())
                    .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)))
                    .create(LootContextParamSets.CHEST);
            counts.add(table.getRandomItems(params).stream().filter(s -> s.is(net.minecraft.world.item.Items.NAME_TAG))
                    .mapToInt(ItemStack::getCount).sum());
        }
        helper.assertTrue(counts.stream().allMatch(c -> c == 6), "Name tags per chest, want 6 each: " + counts);
        helper.succeed();
    }
}
