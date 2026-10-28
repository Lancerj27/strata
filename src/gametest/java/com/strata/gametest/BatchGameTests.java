package com.strata.gametest;

import com.strata.domain.BatchOps;
import com.strata.domain.LootOps;
import com.strata.domain.MobOps;
import com.strata.domain.SpawnRulesOps;
import com.strata.domain.EquipmentOps;
import com.strata.model.SpawnCategory;
import com.strata.model.Variant;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** THE BATCH SCREEN'S WRITES, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class BatchGameTests {

    private BatchGameTests() {
    }

    private static final List<MobOps.BatchMob> MOBS = List.of(
            new MobOps.BatchMob("global", "", "minecraft:zombie", "", SpawnCategory.MONSTER),
            new MobOps.BatchMob("global", "", "minecraft:skeleton", "", SpawnCategory.MONSTER));

    private static BatchOps.Request request(BatchOps.Field field, String action, String item, String slot,
                                            String ability) {
        return new BatchOps.Request(field, action, item, "1", "2", "50", slot, "time", "night", ability, "", "1",
                "20", "4", com.strata.domain.TriggerOps.all().getFirst().id(), "", "");
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "batchops")
    public static void batchAddsAndRemovesEveryKind(GameTestHelper helper) {
        String ability = com.strata.domain.AbilitiesOps.abilityChoices().stream()
                .filter(a -> com.strata.domain.AbilitiesOps.parameterKind(a) == com.strata.domain.AbilitiesOps.ParameterKind.NONE)
                .filter(a -> com.strata.domain.AbilitiesOps.abilityProblem(a, "", "1", "20", "4") == null)
                .findFirst().orElse(null);
        if (ability == null) {
            helper.fail("No ability without a parameter to test with");
            return;
        }
        for (BatchOps.Field field : List.of(BatchOps.Field.LOOT, BatchOps.Field.SPAWN_RULES,
                BatchOps.Field.ABILITIES, BatchOps.Field.EQUIPMENT)) {
            String add = BatchOps.actionsFor(field).contains("Add") ? "Add" : "Set";
            var report = BatchOps.apply(MOBS, request(field, add, field == BatchOps.Field.EQUIPMENT
                    ? "minecraft:iron_sword" : "minecraft:bone", "mainhand", ability));
            if (!report.ok()) {
                helper.fail(field.word + " " + add + " failed: "
                        + (report.refusal() != null ? report.refusal() : report.first()));
                return;
            }
        }
        for (MobOps.BatchMob m : MOBS) {
            String owner = Variant.defaultNameFor(m.entity());
            helper.assertTrue(LootOps.rowsFor("global", "", m.entity(), "", List.of()).stream()
                    .anyMatch(r -> r.item().equals("minecraft:bone") && !r.inherited()), m.entity() + ": no bone drop");
            helper.assertTrue(SpawnRulesOps.spawnRulesFor("global", "", m.entity(), "").stream()
                    .anyMatch(c -> "time".equals(c[0]) && "night".equals(c[1])), m.entity() + ": no night condition");
            helper.assertTrue(com.strata.domain.AbilitiesOps.abilitiesShown(owner, m.entity(), "global", "").stream()
                    .anyMatch(r -> com.strata.domain.AbilitiesOps.rowName(r).equals(ability)), m.entity() + ": no " + ability);
            helper.assertTrue(EquipmentOps.equipmentFor(owner, m.entity(), "global", "", List.of()).stream()
                    .anyMatch(r -> r.item().equals("minecraft:iron_sword")), m.entity() + ": no iron sword");
        }
        for (BatchOps.Field field : List.of(BatchOps.Field.LOOT, BatchOps.Field.SPAWN_RULES,
                BatchOps.Field.ABILITIES, BatchOps.Field.EQUIPMENT)) {
            var report = BatchOps.apply(MOBS, request(field, "Remove", field == BatchOps.Field.EQUIPMENT
                    ? "minecraft:iron_sword" : "minecraft:bone", "mainhand", ability));
            if (!report.ok()) {
                helper.fail(field.word + " Remove failed: "
                        + (report.refusal() != null ? report.refusal() : report.first()));
                return;
            }
        }
        for (MobOps.BatchMob m : MOBS) {
            String owner = Variant.defaultNameFor(m.entity());
            helper.assertTrue(LootOps.rowsFor("global", "", m.entity(), "", List.of()).stream()
                    .noneMatch(r -> r.item().equals("minecraft:bone") && !r.inherited()),
                    m.entity() + ": the bone drop is still there");
            helper.assertTrue(com.strata.data.CsvTable.read(com.strata.data.Paths.table(
                            com.strata.data.Schema.Loot.FILE)).stream()
                    .noneMatch(row -> m.entity().equals(row.get(com.strata.data.Schema.Loot.ENTITY))
                            && "minecraft:bone".equals(row.get(com.strata.data.Schema.Loot.ITEM))),
                    m.entity() + ": Remove left a bone row (a deny) behind");
            helper.assertTrue(SpawnRulesOps.spawnRulesFor("global", "", m.entity(), "").stream()
                    .noneMatch(c -> "time".equals(c[0])), m.entity() + ": the time condition is still there");
            helper.assertTrue(com.strata.domain.AbilitiesOps.abilitiesShown(owner, m.entity(), "global", "").stream()
                    .noneMatch(r -> com.strata.domain.AbilitiesOps.rowName(r).equals(ability) && r.enabled()),
                    m.entity() + ": " + ability + " is still on");
            helper.assertTrue(EquipmentOps.equipmentFor(owner, m.entity(), "global", "", List.of()).stream()
                    .noneMatch(r -> r.item().equals("minecraft:iron_sword") && !r.inherited()),
                    m.entity() + ": the iron sword is still there");
        }
        helper.succeed();
    }
}
