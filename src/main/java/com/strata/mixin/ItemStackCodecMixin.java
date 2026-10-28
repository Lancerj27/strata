package com.strata.mixin;

import com.strata.runtime.ItemStatsRuntime;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A stack goes over the network with the item stats Strata changed. */
@Mixin(targets = "net.minecraft.world.item.ItemStack$1")
public abstract class ItemStackCodecMixin {

    @Redirect(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/component/PatchedDataComponentMap;asPatch()Lnet/minecraft/core/component/DataComponentPatch;"),
            // Not required: if the game's codec class moves, stacks go out as the game sends them (the item
            // stats still apply on the server), and the game test itemStatsSentWithTheStack fails.
            require = 0)
    private DataComponentPatch strata$withItemStats(PatchedDataComponentMap components, RegistryFriendlyByteBuf buf,
                                                    ItemStack stack) {
        return ItemStatsRuntime.forSending(stack, components.asPatch());
    }
}
