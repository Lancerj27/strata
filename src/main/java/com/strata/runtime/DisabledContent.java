package com.strata.runtime;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.strata.Strata;
import com.strata.domain.DisabledOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** WHAT A DISABLED BLOCK OR ITEM IS IN THE GAME. */
public final class DisabledContent {

    private DisabledContent() {
    }

    private static volatile Set<String> items = Set.of();
    private static volatile Set<String> blocks = Set.of();

    /** The disabled block ids, resolved to the game's blocks. */
    private static volatile Set<Block> resolved;

    /** Reads disabled.csv (Config#reload). */
    public static void load() {
        items = Set.copyOf(DisabledOps.ids(DisabledOps.ITEM));
        blocks = Set.copyOf(DisabledOps.ids(DisabledOps.BLOCK));
        resolved = null;
    }

    /** The disabled blocks, resolved against the game's block registry (cached). */
    public static Set<Block> blocks() {
        Set<Block> set = resolved;
        if (set == null) {
            Set<Block> out = new HashSet<>();
            for (String id : blocks) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && BuiltInRegistries.BLOCK.containsKey(rl)) {
                    out.add(BuiltInRegistries.BLOCK.get(rl));
                }
            }
            resolved = set = Set.copyOf(out);
        }
        return set;
    }

    /** True when at least one block is disabled: what the worldgen sweep asks before any work. */
    /** True when any block or item is disabled. */
    public static boolean any() {
        return !items.isEmpty() || !blocks.isEmpty();
    }

    public static boolean blocksActive() {
        return !blocks().isEmpty();
    }

    /** True for a disabled item, or the item of a disabled block. */
    public static boolean disabled(Item item) {
        if (items.isEmpty() && blocks.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getResourceKey(item).map(k -> k.location().toString()).orElse("");
        return items.contains(id) || blocks.contains(id);
    }

    public static boolean disabled(Block block) {
        return !blocks.isEmpty() && blocks.contains(
                BuiltInRegistries.BLOCK.getResourceKey(block).map(k -> k.location().toString()).orElse(""));
    }

    /** Out of every creative tab and the search (mod bus). */
    public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        for (ItemStack stack : java.util.List.copyOf(event.getParentEntries())) {
            if (disabled(stack.getItem())) {
                event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
        for (ItemStack stack : java.util.List.copyOf(event.getSearchEntries())) {
            if (disabled(stack.getItem())) {
                event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
            }
        }
    }

    /** A disabled block is not placed. */
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPlace(BlockEvent.EntityPlaceEvent event) {
            if (disabled(event.getPlacedBlock().getBlock())) {
                event.setCanceled(true);
            }
        }
    }

    /** Every loot table: a disabled item is taken out of what it drops. */
    public static final class StripDisabled extends LootModifier {
        public static final MapCodec<StripDisabled> CODEC = RecordCodecBuilder.mapCodec(
                inst -> codecStart(inst).apply(inst, StripDisabled::new));

        public StripDisabled(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(stack -> disabled(stack.getItem()));
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Strata.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<StripDisabled>> STRIP =
            LOOT_MODIFIERS.register("strip_disabled", () -> StripDisabled.CODEC);
}
