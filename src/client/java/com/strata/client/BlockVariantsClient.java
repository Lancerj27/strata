package com.strata.client;

import com.strata.Strata;
import com.strata.runtime.BlockVariants;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.Map;

/** A BLOCK VARIANT LOOKS LIKE ITS BASE BLOCK. */
@EventBusSubscriber(modid = Strata.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BlockVariantsClient {

    private BlockVariantsClient() {
    }

    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (BlockVariants.Def def : BlockVariants.all()) {
            for (BlockState state : def.block().getStateDefinition().getPossibleStates()) {
                BakedModel model = models.get(BlockModelShaper.stateToModelLocation(BlockVariants.twin(def.base(), state)));
                if (model != null) {
                    models.put(BlockModelShaper.stateToModelLocation(state), model);
                }
            }
            if (def.item() != null && def.base().asItem() != Items.AIR) {
                BakedModel item = models.get(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(def.base().asItem())));
                if (item != null) {
                    models.put(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(def.item())), item);
                }
            }
        }
    }

    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (BlockVariants.Def def : BlockVariants.all()) {
                ItemBlockRenderTypes.setRenderLayer(def.block(),
                        ItemBlockRenderTypes.getChunkRenderType(def.base().defaultBlockState()));
            }
        });
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        var colors = event.getBlockColors();
        for (BlockVariants.Def def : BlockVariants.all()) {
            Block base = def.base();
            event.register((state, level, pos, tint) -> colors.getColor(BlockVariants.twin(base, state), level, pos, tint),
                    def.block());
        }
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        var colors = event.getItemColors();
        for (BlockVariants.Def def : BlockVariants.all()) {
            if (def.item() == null || def.base().asItem() == Items.AIR) {
                continue;
            }
            ItemStack base = new ItemStack(def.base().asItem());
            event.register((stack, tint) -> colors.getColor(base, tint), def.item());
        }
    }
}
