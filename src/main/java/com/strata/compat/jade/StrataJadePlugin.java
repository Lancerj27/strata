package com.strata.compat.jade;

import com.strata.Strata;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration entrypoint — shows a mob's Strata level on the Jade tooltip, so the information /strata
 * mob get reports is visible just by looking at the mob.
 */
@WailaPlugin
public class StrataJadePlugin implements IWailaPlugin {

    /** Identifies this provider to Jade. */
    public static final ResourceLocation MOB_LEVEL =
            ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, "mob_level");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // Server side: makes the level available to the client for the entity being looked at.
        registration.registerEntityDataProvider(
                StrataJadeMobLevelProvider.INSTANCE, LivingEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(
                StrataJadeMobLevelProvider.INSTANCE, LivingEntity.class);
    }
}
