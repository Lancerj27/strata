package com.strata.gametest;

import com.strata.domain.CategoryOps;
import com.strata.domain.MobOps;
import com.strata.model.SpawnCategory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** A MOB'S CATEGORY, IN A REAL GAME. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class CategoryGameTests {

    private CategoryGameTests() {
    }

    private static final List<MobOps.BatchMob> ZOMBIE =
            List.of(new MobOps.BatchMob("global", "", "minecraft:zombie", "", SpawnCategory.MONSTER));

    @GameTest(template = "arena", timeoutTicks = 60, batch = "category")
    public static void zombieSetToCreatureThenBackToMonster(GameTestHelper helper) {
        var set = CategoryOps.batch(ZOMBIE, "Creature");
        if (!set.ok()) {
            helper.fail("Could not set the zombie's category: " + set.first());
            return;
        }
        helper.runAfterDelay(5, () -> {
            MobCategory now = EntityType.ZOMBIE.getCategory();
            SpawnCategory listed = com.strata.game.EntityCategory.of("minecraft:zombie");
            String stored = CategoryOps.categoryOf("minecraft:zombie");
            var back = CategoryOps.batch(ZOMBIE, "Monster");
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(now == MobCategory.CREATURE, "The zombie's type is " + now + ", not creature");
                helper.assertTrue(listed == SpawnCategory.CREATURE, "The mob list files the zombie as " + listed);
                helper.assertTrue("creature".equals(stored), "Stored category: " + stored);
                helper.assertTrue(back.ok() && EntityType.ZOMBIE.getCategory() == MobCategory.MONSTER,
                        "Monster did not put the zombie back: " + EntityType.ZOMBIE.getCategory());
                helper.assertTrue(!com.strata.data.GameLayer.editedCategories(com.strata.data.CsvTable.read(
                                com.strata.data.Paths.table(com.strata.data.Schema.MobData.FILE)))
                                .containsKey("minecraft:zombie"),
                        "The game's category back still leaves an edit row");
                helper.succeed();
            });
        });
    }

    /** THE BAT SET TO CREATURE STAYS IN THE LIST. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "category_bat")
    public static void batSetToCreatureStaysInTheList(GameTestHelper helper) {
        List<MobOps.BatchMob> bat = List.of(new MobOps.BatchMob("global", "", "minecraft:bat", "", SpawnCategory.AMBIENT));
        var set = CategoryOps.batch(bat, "Creature");
        helper.runAfterDelay(5, () -> {
            var rows = com.strata.game.MobCatalog.entries("", "", helper.getLevel().registryAccess());
            var found = rows.stream().filter(r -> r.entity().equals("minecraft:bat") && r.variantName().isEmpty()).toList();
            String label = CategoryOps.moved().get("minecraft:bat");
            CategoryOps.batch(bat, "Ambient");
            helper.assertTrue("Creature (the game's: Ambient)".equals(label), "The bat's row says " + label);
            helper.assertTrue(!CategoryOps.moved().containsKey("minecraft:bat"), "Set back, the bat still says it moved");
            helper.assertTrue(set.ok(), "Could not set the bat's category: " + set.first());
            helper.assertTrue(!found.isEmpty(), "The bat is not in the Global list after it was set to Creature ("
                    + rows.size() + " rows)");
            helper.assertTrue(found.get(0).category() == SpawnCategory.CREATURE,
                    "The bat is listed under " + found.get(0).category() + ", not Creature");
            helper.succeed();
        });
    }

    /** THE BAT SET TO CREATURE FROM THE MOB EDITOR STAYS IN THE LIST. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "category_bat_editor")
    public static void batSetToCreatureInTheEditorStaysInTheList(GameTestHelper helper) {
        var at = com.strata.domain.VariantOps.EditorOpen.of(null, "minecraft:bat", "global", "", true);
        var form = new com.strata.domain.VariantOps.VariantForm("", "", "minecraft:bat", "1.0", "", false, "",
                "global", "", "10", "1", "4", true, "", "", "", "Creature");
        var saved = com.strata.domain.VariantOps.save(at, "global", "", form);
        helper.runAfterDelay(5, () -> {
            var rows = com.strata.game.MobCatalog.entries("", "", helper.getLevel().registryAccess());
            var bats = rows.stream().filter(r -> r.entity().equals("minecraft:bat")).toList();
            String seen = bats.stream().map(r -> "[" + r.variantName() + "|" + r.category() + "|" + r.source()
                    + "]").toList().toString();
            CategoryOps.batch(List.of(new MobOps.BatchMob("global", "", "minecraft:bat", "", SpawnCategory.CREATURE)),
                    "Ambient");
            helper.assertTrue(saved.report().ok(), "The editor could not save the bat: " + saved.report().first());
            helper.assertTrue(bats.stream().anyMatch(r -> r.variantName().isEmpty()),
                    "The bat is not in the Global list after the editor set it to Creature: " + seen);
            helper.assertTrue(bats.stream().allMatch(r -> r.category() == SpawnCategory.CREATURE),
                    "The bat's rows are not all under Creature: " + seen);
            helper.succeed();
        });
    }
}
