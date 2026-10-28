package com.strata.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.strata.Strata;
import com.strata.data.JsonOverrides;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/** BREWING. */
public final class BrewingData {

    private BrewingData() {
    }

    /** The one entry of the Brewing kind. */
    public static final String ENTRY = "minecraft:brewing";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        try {
            List<Object> potionMixes = list(builder, "potionMixes");
            List<Object> containerMixes = list(builder, "containerMixes");
            JsonObject game = new JsonObject();
            game.add("potion_mixes", mixes(potionMixes));
            game.add("container_mixes", mixes(containerMixes));
            JsonOverrides.storeGame(JsonOverrides.BREWING, Map.of(ENTRY, game));

            JsonOverrides.Override o = JsonOverrides.overrides(JsonOverrides.BREWING)
                    .getOrDefault(ENTRY, JsonOverrides.Override.NONE);
            if (o.isEmpty()) {
                return;
            }
            JsonObject now = JsonOverrides.applied(JsonOverrides.BREWING, game, o);
            potionMixes.clear();
            containerMixes.clear();
            int set = 0;
            for (JsonElement e : now.getAsJsonArray("potion_mixes")) {
                JsonObject m = e.getAsJsonObject();
                Holder<Potion> from = potion(m.get("input").getAsString());
                Holder<Potion> to = potion(m.get("output").getAsString());
                Item with = item(m.get("ingredient").getAsString());
                if (from != null && to != null && with != null) {
                    builder.addMix(from, with, to);
                    set++;
                } else {
                    Strata.LOGGER.warn("Brewing: {} is not a mix this game can make - skipped", m);
                }
            }
            for (JsonElement e : now.getAsJsonArray("container_mixes")) {
                JsonObject m = e.getAsJsonObject();
                Item from = item(m.get("input").getAsString());
                Item to = item(m.get("output").getAsString());
                Item with = item(m.get("ingredient").getAsString());
                try {
                    if (from != null && to != null && with != null) {
                        builder.addContainerRecipe(from, with, to);
                        set++;
                    }
                } catch (RuntimeException ex) {
                    Strata.LOGGER.warn("Brewing: {} is not a container mix - skipped ({})", m, ex.toString());
                }
            }
            Strata.LOGGER.info("Brewing: the stand has {} mix(es) as edited", set);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Brewing: left as the game and its mods made it - {}", e.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object builder, String name) throws ReflectiveOperationException {
        Field f = PotionBrewing.Builder.class.getDeclaredField(name);
        f.setAccessible(true);
        return (List<Object>) f.get(builder);
    }

    /** Each mix as from, ingredient, to: ids, the ingredient's items joined with "|". */
    private static JsonArray mixes(List<Object> mixes) throws ReflectiveOperationException {
        JsonArray out = new JsonArray();
        for (Object mix : mixes) {
            JsonObject m = new JsonObject();
            m.addProperty("input", idOf(call(mix, "from")));
            Ingredient ingredient = (Ingredient) call(mix, "ingredient");
            List<String> items = new ArrayList<>();
            for (ItemStack s : ingredient.getItems()) {
                BuiltInRegistries.ITEM.getResourceKey(s.getItem()).ifPresent(k -> items.add(k.location().toString()));
            }
            m.addProperty("ingredient", String.join("|", items));
            m.addProperty("output", idOf(call(mix, "to")));
            out.add(m);
        }
        return out;
    }

    private static Object call(Object record, String accessor) throws ReflectiveOperationException {
        Method m = record.getClass().getDeclaredMethod(accessor);
        m.setAccessible(true);
        return m.invoke(record);
    }

    private static String idOf(Object holder) {
        return holder instanceof Holder<?> h ? h.unwrapKey().map(k -> k.location().toString()).orElse("") : "";
    }

    private static Holder<Potion> potion(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BuiltInRegistries.POTION.getHolder(rl).map(h -> (Holder<Potion>) h).orElse(null);
    }

    /** An item, the first of an ingredient's "|" list. */
    private static Item item(String ids) {
        String first = ids.split("\\|")[0].strip();
        ResourceLocation rl = ResourceLocation.tryParse(first);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.get(rl) : null;
    }
}
