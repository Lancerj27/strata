package com.strata.gametest;

import com.strata.runtime.Coverage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EVERYTHING EDITABLE HAS AN EDITOR: asked of the running game, so a game or mod update that adds a
 * data-driven registry, a registry's tags or an item component Strata does not show fails here, naming what
 * it is.
 */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class CoverageGameTests {

    private CoverageGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "coverage")
    public static void everythingEditableHasAnEditor(GameTestHelper helper) {
        var missing = Coverage.missing(helper.getLevel().getServer());
        helper.assertTrue(missing.isEmpty(), "Editable with no editor in Strata: " + String.join("; ", missing));
        helper.succeed();
    }

    /**
     * THE AUDIT'S LAST TWO GAPS: a mob type's spawn placement and heightmap, set as the game starts from the
     * fixture's mob_data.csv (the strider, a lava mob, made a ground one), with the game layer still showing
     * the game's own; and an entity's settings - invulnerable, shown on fire - set on a mob from the mob data
     * table.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "coverage_settings")
    public static void spawnPlacementAndEntitySettingsApply(GameTestHelper helper) {
        var strider = net.minecraft.world.entity.EntityType.STRIDER;
        helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.getPlacementType(strider)
                        == net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                "The strider's spawn placement is not on the ground");
        helper.assertTrue(net.minecraft.world.entity.SpawnPlacements.getHeightmapType(strider)
                        == net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                "The strider's spawn heightmap is not the world surface");
        var sheet = com.strata.data.GameLayer.read(com.strata.data.GameLayer.dir().resolve(com.strata.data.GameLayer.MOBS));
        helper.assertTrue(sheet.stream().anyMatch(r -> r.entity().equals("minecraft:strider") && r.key().equals("spawn_placement")
                && r.value().equals("in_lava")), "The game layer shows Strata's placement, not the game's in_lava");

        java.util.Map<String, String> invulnerable = java.util.Map.of(com.strata.data.Schema.MobData.ENTITY, "minecraft:sniffer",
                com.strata.data.Schema.MobData.VARIANT_NAME, "", com.strata.data.Schema.MobData.SCOPE, "",
                com.strata.data.Schema.MobData.SCOPE_ID, "", com.strata.data.Schema.MobData.GROUP, "field",
                com.strata.data.Schema.MobData.KEY, "Entity.invulnerable");
        java.util.Map<String, String> fire = new java.util.HashMap<>(invulnerable);
        fire.put(com.strata.data.Schema.MobData.KEY, "Entity.hasVisualFire");
        try {
            com.strata.data.TableWriter.upsert(com.strata.data.Schema.MobData.FILE, invulnerable,
                    java.util.Map.of(com.strata.data.Schema.MobData.VALUE, "true"));
            com.strata.data.TableWriter.upsert(com.strata.data.Schema.MobData.FILE, fire,
                    java.util.Map.of(com.strata.data.Schema.MobData.VALUE, "true"));
            com.strata.data.Config.reload();
            var sniffer = helper.spawn(net.minecraft.world.entity.EntityType.SNIFFER, 2, 2, 2);
            sniffer.setNoAi(true);
            float before = sniffer.getHealth();
            sniffer.hurt(helper.getLevel().damageSources().generic(), 10.0F);
            helper.assertTrue(sniffer.isInvulnerable() && sniffer.getHealth() == before, "The sniffer is not invulnerable");
            helper.assertTrue(visualFire(sniffer), "The sniffer is not set to show as on fire");
            helper.assertTrue(com.strata.data.GameLayer.lockReason("field", "Entity.invulnerable") == null
                    && com.strata.data.GameLayer.lockReason("field", "Entity.tickCount") != null
                    && com.strata.data.GameLayer.lockReason("type", "spawn_placement") == null,
                    "The settings are still locked, or the running state is not");
        } finally {
            com.strata.data.TableWriter.delete(com.strata.data.Schema.MobData.FILE, invulnerable);
            com.strata.data.TableWriter.delete(com.strata.data.Schema.MobData.FILE, fire);
            com.strata.data.Config.reload();
        }
        helper.succeed();
    }

    /** Entity#hasVisualFire, which the game turns into the burning look on the next tick. */
    private static boolean visualFire(net.minecraft.world.entity.Entity e) {
        try {
            var f = net.minecraft.world.entity.Entity.class.getDeclaredField("hasVisualFire");
            f.setAccessible(true);
            return f.getBoolean(e);
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}
