package com.strata.mixin.client;

import com.strata.runtime.VariantEntityRegistry;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A WORLD OPENS WITH EVERY VARIANT AS ITS OWN MOB. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {

    @Inject(method = "initServer", at = @At("HEAD"))
    private void strata$registerVariants(CallbackInfoReturnable<Boolean> cir) {
        VariantEntityRegistry.registerNow(VariantEntityRegistry.variantsInTable());
    }
}
