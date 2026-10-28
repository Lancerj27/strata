package com.strata.runtime;

import com.strata.model.SpecialSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** A VILLAGER'S TYPE AND PROFESSION BY SCOPE. */
public final class VillagerDataRuntime {

    /** The mark of a villager Strata has set, in its persistent data. */
    public static final String MARK = "strata_villager_data";

    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Villager villager)
                || villager.getPersistentData().getBoolean(MARK)
                || !SpecialSpawnsRuntime.hasRows()) {
            return;
        }
        villager.getPersistentData().putBoolean(MARK, true);
        BlockPos pos = villager.blockPosition();
        VillagerData data = villager.getVillagerData();
        ResourceLocation type = ResourceLocation.tryParse(
                SpecialSpawnsRuntime.value(SpecialSpawners.VILLAGERS, "villager_type", level, pos));
        if (type != null && BuiltInRegistries.VILLAGER_TYPE.containsKey(type)) {
            data = data.setType(BuiltInRegistries.VILLAGER_TYPE.get(type));
        }
        ResourceLocation profession = ResourceLocation.tryParse(
                SpecialSpawnsRuntime.value(SpecialSpawners.VILLAGERS, "profession", level, pos));
        boolean setProfession = profession != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(profession)
                && !villager.isBaby() && villager.getVillagerXp() == 0 && data.getLevel() <= 1;
        if (setProfession) {
            data = data.setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(profession));
        }
        villager.setVillagerData(data);
        if (setProfession) {
            villager.setVillagerXp(1);
        }
    }
}
