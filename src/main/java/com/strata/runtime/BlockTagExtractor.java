package com.strata.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.strata.Strata;
import com.strata.data.GameLayer;
import com.strata.model.BlockStats;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** EVERY BLOCK'S TOOL, TOOL LEVEL, AND TAGS, FROM THE TAG FILES AT GAME START. */
public final class BlockTagExtractor {

    private BlockTagExtractor() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every block's tool, tool level, and tags, from the game's and each mod's tag files, rewritten at every game start.
            Do not edit: your values go in block_stats.csv. group component: key tool, tool_level, or tags (comma separated).""";

    /** Reads the block tag files of the open packs and writes game/block_info.csv. */
    public static void extract(ResourceManager resources) {
        Map<String, List<String>> members = new HashMap<>();
        Map<ResourceLocation, List<Resource>> stacks = resources.listResourceStacks("tags/block",
                f -> f.getPath().endsWith(".json"));
        for (ResourceLocation file : stacks.keySet()) {
            String path = file.getPath();
            String tag = file.getNamespace() + ":"
                    + path.substring("tags/block/".length(), path.length() - ".json".length());
            List<String> values = new ArrayList<>();
            for (Resource resource : stacks.get(file)) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement json = JsonParser.parseReader(reader);
                    if (!json.isJsonObject()) continue;
                    var obj = json.getAsJsonObject();
                    if (obj.has("replace") && obj.get("replace").getAsBoolean()) values.clear();
                    if (!obj.has("values")) continue;
                    for (JsonElement v : obj.getAsJsonArray("values")) {
                        String entry = v.isJsonPrimitive() ? v.getAsString()
                                : v.isJsonObject() && v.getAsJsonObject().has("id")
                                ? v.getAsJsonObject().get("id").getAsString() : "";
                        if (!entry.isEmpty()) values.add(entry);
                    }
                } catch (Exception ex) {
                    Strata.LOGGER.debug("Block tags: could not read {}: {}", tag, ex.toString());
                }
            }
            members.put(tag, values);
        }
        // Each block's tags, the nested ones followed.
        Map<String, Set<String>> tagsOf = new TreeMap<>();
        for (String tag : members.keySet()) {
            for (String block : blocksOf(tag, members, new HashSet<>())) {
                tagsOf.computeIfAbsent(block, k -> new TreeSet<>()).add(tag);
            }
        }
        List<GameLayer.Row> rows = new ArrayList<>();
        for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            String id = BlockVariants.idOf(block);
            Set<String> tags = tagsOf.getOrDefault(id, Set.of());
            String tool = "none";
            for (int i = 0; i < BlockStats.TOOL_TAGS.size(); i++) {
                if (tags.contains(BlockStats.TOOL_TAGS.get(i))) {
                    tool = BlockStats.TOOLS.get(i);
                    break;
                }
            }
            String level = tags.contains("minecraft:needs_diamond_tool") ? "diamond"
                    : tags.contains("minecraft:needs_iron_tool") ? "iron"
                    : tags.contains("minecraft:needs_stone_tool") ? "stone" : "any";
            rows.add(new GameLayer.Row(id, GameLayer.COMPONENT, BlockStats.TOOL, tool));
            rows.add(new GameLayer.Row(id, GameLayer.COMPONENT, BlockStats.TOOL_LEVEL, level));
            rows.add(new GameLayer.Row(id, GameLayer.COMPONENT, BlockStats.TAGS,
                    tags.isEmpty() ? "none" : String.join(", ", tags)));
        }
        if (GameLayer.write(GameLayer.dir().resolve(GameLayer.BLOCK_INFO), COMMENT, rows)) {
            Strata.LOGGER.info("Game layer: read the tools and tags of {} block(s) from {} block tag(s)",
                    tagsOf.size(), members.size());
        }
    }

    /** The blocks a tag holds, its nested tags followed once each. */
    private static Set<String> blocksOf(String tag, Map<String, List<String>> members, Set<String> seen) {
        Set<String> out = new HashSet<>();
        if (!seen.add(tag)) return out;
        for (String v : members.getOrDefault(tag, List.of())) {
            if (v.startsWith("#")) {
                out.addAll(blocksOf(v.substring(1), members, seen));
            } else {
                out.add(v);
            }
        }
        return out;
    }
}
