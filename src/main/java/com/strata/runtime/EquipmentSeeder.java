package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.GameLayer;
import com.strata.data.DefaultEquipmentTable;
import com.strata.model.EquipmentFamilies;
import com.strata.model.DefaultEquipment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Extracts every mob's default equipment into the game layer. */
public final class EquipmentSeeder {

    private EquipmentSeeder() {
    }

    private static final String COMMENT = """
            The GAME LAYER: every mob's default equipment as the game runs it, rewritten at every game start. Do not edit:
            edit each piece as a Equipment row (equipment.csv). rule: family, armor, armor_stop, material, item, enchant, drop.""";

    /** A mob's game-layer equipment lines, the family row first, or none when the game does not arm it. */
    static List<String> lines(String id, String family) {
        DefaultEquipment roll = EquipmentFamilies.of(family);
        if (roll == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add(com.strata.data.Parse.line(new String[]{id, DefaultEquipmentTable.FAMILY, "", family, "0", ""}));
        for (Map<String, String> row : DefaultEquipmentTable.toRows(id, roll)) {
            out.add(com.strata.data.Parse.line(row.values().toArray(String[]::new)));
        }
        return out;
    }

    /** Writes game/equipment.csv whole. */
    static void write(List<String> lines, int mobs) {
        if (GameLayer.writeLines(GameLayer.dir().resolve(GameLayer.EQUIPMENT), COMMENT, DefaultEquipmentTable.HEADER, lines)) {
            Strata.LOGGER.info("Game layer: extracted the default equipment rolls of {} mob(s)", mobs);
        }
    }

    /**
     * Puts the items the game ALWAYS gives the mob in its hands and armor slots (a skeleton's bow), so its
     * goals read as the game makes them: a skeleton picks its bow or melee goal from what it holds.
     */
    static void equipSure(Mob mob, String family) {
        DefaultEquipment roll = EquipmentFamilies.of(family);
        if (roll == null) {
            return;
        }
        for (DefaultEquipment.Item item : roll.items()) {
            if (item.pct() < 100) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(item.item());
            EquipmentSlot slot = EquipmentSlot.byName(item.slot());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                mob.setItemSlot(slot, new ItemStack(BuiltInRegistries.ITEM.get(id)));
            }
        }
    }
    /** Most specific class first: a drowned is a zombie too, and has its own roll. */
    static String familyOf(Entity e) {
        if (e instanceof Drowned) return EquipmentFamilies.DROWNED;
        if (e instanceof ZombifiedPiglin) return EquipmentFamilies.ZOMBIFIED_PIGLIN;
        if (e instanceof Zombie) return EquipmentFamilies.ZOMBIE;
        if (e instanceof WitherSkeleton) return EquipmentFamilies.WITHER_SKELETON;
        if (e instanceof AbstractSkeleton) return EquipmentFamilies.SKELETON;
        if (e instanceof PiglinBrute) return EquipmentFamilies.PIGLIN_BRUTE;
        if (e instanceof Piglin) return EquipmentFamilies.PIGLIN;
        if (e instanceof Pillager) return EquipmentFamilies.PILLAGER;
        if (e instanceof Vindicator) return EquipmentFamilies.VINDICATOR;
        if (e instanceof Vex) return EquipmentFamilies.VEX;
        if (e instanceof Fox) return EquipmentFamilies.FOX;
        return "";
    }
}
