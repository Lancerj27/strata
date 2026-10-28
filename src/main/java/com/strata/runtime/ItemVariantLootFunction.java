package com.strata.runtime;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.strata.Strata;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * THE LOOT FUNCTION strata:item_variant: {"function": "strata:item_variant", "variant": "frost_blade"} on a
 * loot entry of the variant's base item turns the rolled stack into the variant, as it is on the day of the
 * roll (ItemVariants#make).
 */
public final class ItemVariantLootFunction extends LootItemConditionalFunction {

    public static final MapCodec<ItemVariantLootFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(Codec.STRING.fieldOf("variant").forGetter(f -> f.variant))
            .apply(i, ItemVariantLootFunction::new));

    private static final DeferredRegister<LootItemFunctionType<?>> TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Strata.MOD_ID);
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<ItemVariantLootFunction>> TYPE =
            TYPES.register("item_variant", () -> new LootItemFunctionType<>(CODEC));

    /** The function's id, as a loot table names it. */
    public static final String ID = Strata.MOD_ID + ":item_variant";

    private final String variant;

    private ItemVariantLootFunction(List<LootItemCondition> conditions, String variant) {
        super(conditions);
        this.variant = variant;
    }

    /** Registers the type (Strata's constructor). */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @Override
    public LootItemFunctionType<ItemVariantLootFunction> getType() {
        return TYPE.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        ItemStack made = ItemVariants.make(variant, stack.getCount(), context.getLevel().registryAccess());
        return made.isEmpty() ? stack : made;
    }
}
