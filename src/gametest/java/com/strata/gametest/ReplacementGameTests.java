package com.strata.gametest;

import com.strata.data.Config;
import com.strata.data.Defaults;
import com.strata.domain.ReplacementOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ENTITY REPLACEMENT IN A REAL WORLD. */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class ReplacementGameTests {

    private ReplacementGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "replacement")
    public static void spawnerZombieBecomesHusk(GameTestHelper helper) {
        Defaults.writeIfMissing();
        var saved = ReplacementOps.save("global", "", "minecraft:zombie", "minecraft:husk", "", "all");
        helper.assertTrue(saved.ok(), "The replacement was refused: " + saved.refusal());
        Config.reload();
        try {
            BlockPos spawner = helper.absolutePos(new BlockPos(1, 2, 1));
            BlockPos egg = helper.absolutePos(new BlockPos(4, 2, 4));
            EntityType.ZOMBIE.spawn(helper.getLevel(), spawner, MobSpawnType.SPAWNER);
            EntityType.ZOMBIE.spawn(helper.getLevel(), egg, MobSpawnType.SPAWN_EGG);
            AABB nearSpawner = new AABB(spawner).inflate(1);
            AABB nearEgg = new AABB(egg).inflate(1);
            helper.assertTrue(!helper.getLevel().getEntities(EntityType.HUSK, nearSpawner, e -> true).isEmpty(),
                    "The spawner's zombie did not become a husk");
            helper.assertTrue(helper.getLevel().getEntities(EntityType.ZOMBIE, nearSpawner, e -> true).isEmpty(),
                    "The spawner's zombie spawned as well as the husk");
            helper.assertTrue(!helper.getLevel().getEntities(EntityType.ZOMBIE, nearEgg, e -> true).isEmpty(),
                    "An egg's zombie was replaced: a player's own spawn is theirs");
        } finally {
            ReplacementOps.rows("global", "").forEach(ReplacementOps::remove);
            Config.reload();
        }
        helper.succeed();
    }
}
