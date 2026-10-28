package com.strata.mixin.client;

import com.strata.client.ModelSwap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A MOB DRAWN AS ANOTHER SHEDS NONE OF ITS OWN PARTICLES. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelParticlesMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void strata$tickStart(Entity entity, CallbackInfo ci) {
        ModelSwap.ticking(entity);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void strata$tickEnd(Entity entity, CallbackInfo ci) {
        ModelSwap.ticked();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void strata$riderStart(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ModelSwap.ticking(passenger);
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void strata$riderEnd(Entity vehicle, Entity passenger, CallbackInfo ci) {
        ModelSwap.ticked();
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"),
            cancellable = true)
    private void strata$particle(ParticleOptions p, double x, double y, double z, double dx, double dy, double dz,
                                 CallbackInfo ci) {
        if (ModelSwap.intercept(p, r -> ((ClientLevel) (Object) this).addParticle(r, x, y, z, dx, dy, dz))) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"),
            cancellable = true)
    private void strata$particleForced(ParticleOptions p, boolean force, double x, double y, double z, double dx,
                                       double dy, double dz, CallbackInfo ci) {
        if (ModelSwap.intercept(p, r -> ((ClientLevel) (Object) this).addParticle(r, force, x, y, z, dx, dy, dz))) {
            ci.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V",
            at = @At("HEAD"), cancellable = true)
    private void strata$visibleParticle(ParticleOptions p, double x, double y, double z, double dx, double dy,
                                        double dz, CallbackInfo ci) {
        if (ModelSwap.intercept(p, r -> ((ClientLevel) (Object) this).addAlwaysVisibleParticle(r, x, y, z, dx, dy, dz))) {
            ci.cancel();
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
            at = @At("HEAD"), cancellable = true)
    private void strata$visibleParticleForced(ParticleOptions p, boolean force, double x, double y, double z,
                                              double dx, double dy, double dz, CallbackInfo ci) {
        if (ModelSwap.intercept(p, r -> ((ClientLevel) (Object) this).addAlwaysVisibleParticle(r, force, x, y, z, dx, dy, dz))) {
            ci.cancel();
        }
    }
}
