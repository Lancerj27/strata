package com.strata.gametest;

import com.google.gson.JsonParser;
import com.strata.data.CustomStructures;
import com.strata.data.StrataDatapack;
import com.strata.model.CustomStructure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A CUSTOM STRUCTURE FROM A STRUCTURE FILE: a structure built in the world and saved as a structure block
 * saves it is read and checked, brought in, written into the pack as the game's own data - each file read
 * back by the game's codecs - and placed again from the copy the pack holds.
 */
@GameTestHolder("strata")
@PrefixGameTestTemplate(false)
public final class CustomStructureGameTests {

    private CustomStructureGameTests() {
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "custom_structures")
    public static void aStructureFileIsCheckedWrittenAndPlaced(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int y = 0; y < 3; y++) {
            level.setBlock(base.above(y), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        }
        StructureTemplate built = new StructureTemplate();
        built.fillFromWorld(level, base, new Vec3i(1, 3, 1), false, Blocks.STRUCTURE_VOID);
        Path tmp = Files.createTempDirectory("strata-structures");
        try {
            Path file = tmp.resolve("tower.nbt");
            NbtIo.writeCompressed(built.save(new net.minecraft.nbt.CompoundTag()), file);

            var probe = CustomStructures.probe(file);
            helper.assertTrue(probe.problem() == null && probe.x() == 1 && probe.y() == 3 && probe.z() == 1
                    && probe.blocks() == 3, "The tower read as " + probe);
            Path text = tmp.resolve("tower.txt");
            Files.writeString(text, "not a structure");
            helper.assertTrue(CustomStructures.probe(text).problem() != null, "A text file was taken as a structure");

            Path root = tmp.resolve("config");
            helper.assertTrue(CustomStructures.importFile(root, "tower", file) == null, "The tower was not brought in");
            Map<String, String> row = new HashMap<>(CustomStructure.DEFAULTS);
            row.put(CustomStructure.NAME, "tower");
            String id = StrataDatapack.NAMESPACE + ":custom/tower";
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,
                    level.registryAccess());
            var pool = net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.DIRECT_CODEC
                    .parse(ops, CustomStructures.pool(id));
            helper.assertTrue(pool.error().isEmpty(), "The pool does not load: " + pool.error());
            var placement = net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC
                    .parse(ops, CustomStructures.set(row, id, "tower").get("placement"));
            helper.assertTrue(placement.error().isEmpty(), "The spacing does not load: " + placement.error());
            var structureJson = CustomStructures.structure(row, id);
            structureJson.addProperty("start_pool", "minecraft:village/plains/town_centers");
            var structure = net.minecraft.world.level.levelgen.structure.Structure.DIRECT_CODEC.parse(ops, structureJson);
            helper.assertTrue(structure.error().isEmpty(), "The structure does not load: " + structure.error());

            var stored = NbtIo.readCompressed(CustomStructures.fileOf(root, "tower"), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            StructureTemplate again = new StructureTemplate();
            again.load(level.holderLookup(Registries.BLOCK), stored);
            BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
            again.placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), 2);
            helper.assertTrue(level.getBlockState(at.above(2)).is(Blocks.GOLD_BLOCK), "The stored tower was not placed");
            helper.succeed();
        } finally {
            try (var walk = Files.walk(tmp)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
