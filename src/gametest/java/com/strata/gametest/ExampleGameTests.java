package com.strata.gametest;

import com.strata.data.Config;
import com.strata.domain.BiomeOps;
import com.strata.domain.FormulaOps;
import com.strata.domain.LootOps;
import com.strata.domain.OptionsOps;
import com.strata.domain.TierOps;
import com.strata.model.ScalingFormulas;
import com.strata.runtime.AttributeCurve;
import com.strata.runtime.MobScalingHandler;
import com.strata.runtime.SunBurn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The owner's example uses of Strata, each run in a real world. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ExampleGameTests {

    private ExampleGameTests() {}

    private static List<String> dropsAround(GameTestHelper helper, AABB around) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).stream()
                .map(e -> BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString()).toList();
    }

    /** Example: a skeleton drops an iron sword you added to its loot. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "examples")
    public static void anAddedDropFalls(GameTestHelper helper) {
        var saved = LootOps.save("minecraft:skeleton", "", new LootOps.EntryTarget("global", "", 0, null),
                new LootOps.LootForm("minecraft:iron_sword", "1", "1", "100", "add", LootOps.NO_CONDITION, ""));
        helper.assertTrue(saved.ok(), "The drop was not saved");
        var skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
        AABB around = skeleton.getBoundingBox().inflate(4);
        skeleton.kill();
        helper.runAfterDelay(2, () -> {
            List<String> dropped = dropsAround(helper, around);
            LootOps.restoreDefaults("global", "", "minecraft:skeleton", "");
            helper.assertTrue(dropped.contains("minecraft:iron_sword"), "No iron sword dropped: " + dropped);
            helper.succeed();
        });
    }

    /** Example: a zombie that dies in sunlight drops a gold nugget; one that burns another way does not. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "examples")
    public static void aSunlightDeathPaysItsDrop(GameTestHelper helper) {
        var saved = LootOps.save("minecraft:zombie", "", new LootOps.EntryTarget("global", "", 0, null),
                new LootOps.LootForm("minecraft:gold_nugget", "1", "1", "100", "add", "death_cause", "sunlight"));
        helper.assertTrue(saved.ok(), "The drop was not saved");
        var sunlit = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        var torched = helper.spawn(EntityType.ZOMBIE, 6, 2, 6);
        AABB sunAround = sunlit.getBoundingBox().inflate(1.5);
        AABB torchAround = torched.getBoundingBox().inflate(1.5);
        SunBurn.mark(sunlit);
        sunlit.hurt(helper.getLevel().damageSources().onFire(), 1000F);
        torched.hurt(helper.getLevel().damageSources().onFire(), 1000F);
        helper.runAfterDelay(2, () -> {
            List<String> sun = dropsAround(helper, sunAround);
            List<String> torch = dropsAround(helper, torchAround);
            LootOps.restoreDefaults("global", "", "minecraft:zombie", "");
            helper.assertTrue(sun.contains("minecraft:gold_nugget"), "The sunlit zombie dropped no nugget: " + sun);
            helper.assertTrue(!torch.contains("minecraft:gold_nugget"), "A zombie burned another way paid the sunlight drop");
            helper.succeed();
        });
    }

    /** Example: a tier at level 5 holds the test's biome, and a mob spawned there is stronger. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "examples")
    public static void aTierLevelsItsMobs(GameTestHelper helper) {
        String biome = helper.getLevel().getBiome(helper.absolutePos(new BlockPos(2, 2, 2))).unwrapKey()
                .map(k -> k.location().toString()).orElse("");
        var before = BiomeOps.formFor(biome);
        try {
            helper.assertTrue(TierOps.save("", new TierOps.TierForm("EXAMPLE", "5", "add", "1.0", "default")).ok(),
                    "The tier was not saved");
            helper.assertTrue(BiomeOps.save(biome, new BiomeOps.BiomeForm("EXAMPLE", "", null, null, null, "", null)).ok(),
                    "The biome was not put in the tier");
            var resolution = Config.resolve(biome);
            helper.assertTrue(resolution.level() == 5, "The biome's level is " + resolution.level() + ", not 5");
            var zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
            float base = zombie.getMaxHealth();
            MobScalingHandler.applyTo(zombie, resolution, resolution.level());
            helper.assertTrue(zombie.getMaxHealth() > base,
                    "A level 5 zombie has " + zombie.getMaxHealth() + " max health, the game's " + base);
        } finally {
            BiomeOps.save(biome, before);
            TierOps.delete("EXAMPLE");
        }
        helper.succeed();
    }

    /** Example: a formula that gives a flat +2 health a level, whatever the mob's base. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "examples")
    public static void aFlatHealthFormula(GameTestHelper helper) {
        var kind = ScalingFormulas.Kind.MOB_ATTRIBUTE;
        String old = FormulaOps.expression(kind);
        try {
            helper.assertTrue(FormulaOps.save(kind, "2 * level / base").ok(), "The formula was not saved");
            var zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
            zombie.setNoAi(true);
            new AttributeCurve(Attributes.MAX_HEALTH, ResourceLocation.parse("minecraft:generic.max_health"), 0, 1, 1,
                    ResourceLocation.fromNamespaceAndPath("strata", "example_flat"), kind).apply(zombie, 5);
            helper.assertTrue(Math.abs(zombie.getMaxHealth() - 30F) < 0.01F,
                    "A zombie at level 5 has " + zombie.getMaxHealth() + " max health, not 20 + 2 x 5");
        } finally {
            FormulaOps.save(kind, old);
        }
        helper.succeed();
    }

    /** Example: Chunk scaling On raises the level a mob spawns at in a harder chunk; Off leaves it. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "examples")
    public static void chunkScalingRaisesTheLevel(GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        String biome = helper.getLevel().getBiome(zombie.blockPosition()).unwrapKey()
                .map(k -> k.location().toString()).orElse("");
        var resolution = Config.resolve(biome);
        String was = OptionsOps.find(OptionsOps.CHUNK_SCALING).value();
        try {
            OptionsOps.set(OptionsOps.CHUNK_SCALING, "Off");
            int off = MobScalingHandler.areaLevel(zombie, resolution, 20);
            OptionsOps.set(OptionsOps.CHUNK_SCALING, "On");
            OptionsOps.set(OptionsOps.CHUNK_SCALE, "2");
            int on = MobScalingHandler.areaLevel(zombie, resolution, 20);
            float local = helper.getLevel().getCurrentDifficultyAt(zombie.blockPosition()).getEffectiveDifficulty();
            helper.assertTrue(off == 20, "Off: level " + off + ", not 20");
            helper.assertTrue(local <= 0 || on > 20, "On: level " + on + " at local difficulty " + local);
        } finally {
            OptionsOps.set(OptionsOps.CHUNK_SCALE, "0.13");
            OptionsOps.set(OptionsOps.CHUNK_SCALING, was);
        }
        helper.succeed();
    }

    /**
     * Example: a variant made in this session spawns with the variant before it has its own mob type, and its
     * abilities list says which other scope holds its rows.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "examples")
    public static void aVariantMadeThisSessionSpawnsAndItsOtherAbilitiesShow(GameTestHelper helper) {
        var keys = java.util.Map.of(com.strata.data.Schema.Variants.NAME, "example_made");
        var ability = java.util.Map.of(com.strata.data.Schema.Abilities.VARIANT_NAME, "example_made",
                com.strata.data.Schema.Abilities.SCOPE, "tier", com.strata.data.Schema.Abilities.SCOPE_ID, "Harsh",
                com.strata.data.Schema.Abilities.EFFECT_CLASS, "heal");
        try {
            com.strata.data.TableWriter.upsert(com.strata.data.Schema.Variants.FILE, keys,
                    java.util.Map.of(com.strata.data.Schema.Variants.BASE_ENTITY, "minecraft:zombie"));
            com.strata.data.TableWriter.upsert(com.strata.data.Schema.Abilities.FILE, ability,
                    java.util.Map.of(com.strata.data.Schema.Abilities.TRIGGER, "permanent"));
            Config.reload();
            var variant = Config.tables().variants().get("example_made");
            helper.assertTrue(variant != null, "The variant was not read");
            var zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
            String biome = helper.getLevel().getBiome(zombie.blockPosition()).unwrapKey()
                    .map(k -> k.location().toString()).orElse("");
            MobScalingHandler.applyVariant(zombie, variant, Config.resolve(biome));
            helper.assertTrue("example_made".equals(zombie.getData(com.strata.runtime.MobLevel.VARIANT_ID)),
                    "The zombie is not the variant");
            List<String> elsewhere = com.strata.domain.AbilitiesOps.abilitiesElsewhere("example_made", "global", "");
            helper.assertTrue(elsewhere.contains("tier Harsh (1)"), "The global list does not name the tier's row: " + elsewhere);
        } finally {
            com.strata.data.TableWriter.delete(com.strata.data.Schema.Abilities.FILE, ability);
            com.strata.data.TableWriter.delete(com.strata.data.Schema.Variants.FILE, keys);
            Config.reload();
        }
        helper.succeed();
    }
}
