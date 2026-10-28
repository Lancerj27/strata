package com.strata.compat.jade;

import com.strata.runtime.MobLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Both halves of the Jade mob-level display in one class, because they are two ends of the same wire and
 * splitting them makes the NBT key easy to get out of sync.
 */
public class StrataJadeMobLevelProvider
        implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {

    public static final StrataJadeMobLevelProvider INSTANCE = new StrataJadeMobLevelProvider();

    /** NBT key inside Jade's payload. */
    private static final String LEVEL_KEY = "strata:mob_level";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof LivingEntity living)) {
            return;
        }

        MobLevel mobLevel = living.getData(MobLevel.MOB_LEVEL);
        if (!mobLevel.isSet()) {
            // No level assigned: spawned before Strata was installed, or not a natural spawn (a /summon, for
            // instance, does not fire FinalizeSpawnEvent).
            return;
        }

        data.putInt(LEVEL_KEY, mobLevel.level());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(LEVEL_KEY)) {
            return;  // Unlevelled mob, or the server hasn't answered yet.
        }

        tooltip.add(Component.translatable("strata.jade.mob_level", data.getInt(LEVEL_KEY)));
    }

    @Override
    public ResourceLocation getUid() {
        return StrataJadePlugin.MOB_LEVEL;
    }
}
