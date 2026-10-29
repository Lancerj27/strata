package com.strata.mixin;

import com.strata.runtime.WorldLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/** A recipe locked at the world's stage (runtime.WorldLevel) does not match: the grid shows nothing. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    public abstract <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T> type);

    /** The stonecutter asks for every matching recipe at once: the locked ones leave the list. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void strata$lockedRecipes(RecipeType type, RecipeInput input, Level level, CallbackInfoReturnable<List<RecipeHolder>> cir) {
        List<RecipeHolder> found = cir.getReturnValue();
        if (found != null && found.stream().anyMatch(h -> WorldLevel.recipeLocked(h.id()))) {
            cir.setReturnValue(found.stream().filter(h -> !WorldLevel.recipeLocked(h.id())).toList());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private void strata$lockedRecipe(RecipeType type, RecipeInput input, Level level, RecipeHolder lastRecipe,
            CallbackInfoReturnable<Optional<RecipeHolder>> cir) {
        Optional<RecipeHolder> found = cir.getReturnValue();
        if (found.isPresent() && WorldLevel.recipeLocked(found.get().id())) {
            // The next unlocked recipe the input fits, if any.
            List<RecipeHolder> all = getAllRecipesFor(type);
            cir.setReturnValue(all.stream()
                    .filter(h -> !WorldLevel.recipeLocked(h.id()) && ((Recipe) h.value()).matches(input, level))
                    .findFirst());
        }
    }
}
