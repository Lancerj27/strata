package com.strata.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.strata.data.GameLayer;
import com.strata.model.ItemStats;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** EVERY ITEM COMPONENT, BY THE GAME'S OWN CODEC. */
public final class ItemComponents {

    private ItemComponents() {
    }

    /** The JSON codecs read and write: with the running world's registries when there is one. */
    private static DynamicOps<JsonElement> ops() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? JsonOps.INSTANCE : RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
    }

    /** The component type an id names, or null. */
    public static DataComponentType<?> type(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id == null ? "" : id.strip());
        return rl == null || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(rl) ? null
                : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
    }

    /** A type's id, as its key writes it. */
    private static String idOf(DataComponentType<?> type) {
        return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
    }

    /**
     * The component types a value can be set for: every registered one that is saved with an item (a codec),
     * less the seven shown as plain values.
     */
    public static List<String> settable() {
        List<String> out = new ArrayList<>();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            String id = idOf(type);
            if (type.codec() != null && !ItemStats.FLATTENED.contains(id)) {
                out.add(id);
            }
        }
        out.sort(null);
        return out;
    }

    /** One item's component rows for the game layer: each component it has, as its JSON. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<GameLayer.Row> rows(String item, Item it) {
        List<GameLayer.Row> out = new ArrayList<>();
        for (TypedDataComponent<?> c : it.components()) {
            String id = idOf(c.type());
            Codec codec = c.type().codec();
            if (codec == null || ItemStats.FLATTENED.contains(id)) {
                continue;
            }
            try {
                var json = codec.encodeStart(ops(), c.value()).result();
                if (json.isPresent()) {
                    out.add(new GameLayer.Row(item, GameLayer.COMPONENT, ItemStats.componentKey(id), json.get().toString()));
                }
            } catch (RuntimeException e) {
                // A component naming a datapack registry's entry, before any world: not shown at game start.
            }
        }
        return out;
    }

    /**
     * Why a value cannot be set for a component, or null: the type the key names, then the JSON through the
     * type's own codec, whose words say what is wrong.
     */
    public static String problem(String key, String value) {
        String id = ItemStats.componentId(key);
        DataComponentType<?> type = type(id);
        if (type == null || type.codec() == null) {
            return "The game has no item component " + id;
        }
        String v = value == null ? "" : value.strip();
        if (ItemStats.REMOVE.equalsIgnoreCase(v)) {
            return null;
        }
        JsonElement json;
        try {
            json = JsonParser.parseString(v);
        } catch (RuntimeException e) {
            return "Not JSON: " + shortly(e.getMessage());
        }
        var read = type.codec().parse(ops(), json);
        if (read.error().isPresent()) {
            String why = read.error().get().message();
            return why.contains("registry") || why.contains("Registry")
                    ? "It names an entry of a datapack registry: open a world to set it (" + shortly(why) + ")"
                    : "The game cannot read it: " + shortly(why);
        }
        return null;
    }

    /** Sets (or takes off) each component value among an item's values. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void patch(DataComponentPatch.Builder b, String item, Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String id = ItemStats.componentId(e.getKey());
            if (id == null) {
                continue;
            }
            DataComponentType type = type(id);
            String problem = problem(e.getKey(), e.getValue());
            if (type == null || problem != null) {
                com.strata.Strata.LOGGER.warn("Item stats: {} component {}: {}; the row is skipped", item, id, problem);
                continue;
            }
            if (ItemStats.REMOVE.equalsIgnoreCase(e.getValue().strip())) {
                b.remove(type);
            } else {
                type.codec().parse(ops(), JsonParser.parseString(e.getValue().strip())).result()
                        .ifPresent(value -> b.set(type, value));
            }
        }
    }

    /** The component types an item's values set, for what a stack carries to a player without Strata. */
    public static Set<DataComponentType<?>> types(Map<String, String> values) {
        Set<DataComponentType<?>> out = new LinkedHashSet<>();
        for (String key : values.keySet()) {
            DataComponentType<?> type = type(ItemStats.componentId(key));
            if (type != null) {
                out.add(type);
            }
        }
        return out;
    }

    private static String shortly(String s) {
        String one = s == null ? "" : s.replace('\n', ' ').strip();
        return one.length() > 160 ? one.substring(0, 160) + "..." : one;
    }
}
