package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.data.StrataConfig;
import com.strata.model.LootCondition;
import com.strata.model.LootEntry;
import com.strata.model.LootPool;
import com.strata.model.MobLoot;
import com.strata.model.OverrideMode;
import com.strata.model.Resolution;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.List;

/** Applies loot table overrides from the CSV config when a mob dies. */
public class LootHandler {

    /** What each dying mob wore and held, by item, from its death to its drops. */
    private static final java.util.Map<LivingEntity, java.util.List<Item>> WORN =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Keeps what the mob wears as it dies; the drops come right after. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        java.util.List<Item> worn = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                worn.add(stack.getItem());
            }
        }
        WORN.put(entity, worn);
    }

    /** The drops that are the mob's equipment: one drop per piece it wore, matched by item. */
    public static java.util.Set<Integer> equipmentDrops(java.util.List<Item> drops, java.util.List<Item> worn) {
        java.util.List<Item> left = new java.util.ArrayList<>(worn);
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (int i = 0; i < drops.size(); i++) {
            if (left.remove(drops.get(i))) {
                out.add(i);
            }
        }
        return out;
    }

    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        java.util.List<Item> worn = WORN.remove(entity);
        if (!StrataConfig.ENABLE_LOOT.get()) {
            return;
        }

        // A player's drops are never a mob's loot: a hand-made "*" row matched players too, and a REPLACE
        // cleared their death drops.
        if (entity.level().isClientSide() || !(entity.level() instanceof ServerLevel serverLevel)
                || entity instanceof net.minecraft.world.entity.player.Player) {
            return;
        }
        java.util.List<net.minecraft.world.entity.item.ItemEntity> dropList = new java.util.ArrayList<>(event.getDrops());
        java.util.Set<Integer> equipmentIndex = equipmentDrops(dropList.stream().map(d -> d.getItem().getItem()).toList(),
                worn == null ? java.util.List.of() : worn);
        java.util.Set<net.minecraft.world.entity.item.ItemEntity> equipment = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        equipmentIndex.forEach(i -> equipment.add(dropList.get(i)));

        Holder<Biome> biome = serverLevel.getBiome(entity.blockPosition());
        Resolution resolution = Config.resolve(
                biome.unwrapKey().map(key -> key.location()).orElse(null));

        List<MobLoot> overrides = resolution.loot();
        if (overrides.isEmpty()) {
            return;
        }

        // RS-7: getKey is null for an unregistered type, and this is the loot bus — a throw here cancels the
        // drop event for every other listener.
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null) {
            return;
        }
        // An own-entity variant's rows are keyed on its base mob, as the editor saves them: its own type
        // matched none of them.
        String entityStr = VariantEntityRegistry.baseEntityIdForType(entity.getType()).orElse(entityId.toString());
        // A variant's drops are its own, else its base mob's.
        String mobVariant = MobLoot.lootOwner(overrides, entityStr, MobAbilities.variantOf(entity));

        for (MobLoot override : overrides) {
            if (!override.entity().equals(entityStr) && !override.entity().equals("*")) {
                continue;
            }
            if (!override.appliesTo(entityStr, mobVariant)) {
                continue;
            }

            if (override.mode() == OverrideMode.REPLACE) {
                event.getDrops().removeIf(drop -> !equipment.contains(drop));
            }

            // DENY REMOVES, it does not add.
            if (override.mode() == OverrideMode.DENY) {
                for (LootPool pool : override.pools()) {
                    for (LootEntry entry : pool.entries()) {
                        ResourceLocation denied = ResourceLocation.tryParse(entry.item());
                        if (denied == null || !BuiltInRegistries.ITEM.containsKey(denied)) {
                            continue;
                        }
                        Item deniedItem = BuiltInRegistries.ITEM.get(denied);
                        event.getDrops().removeIf(drop ->
                                drop.getItem().getItem() == deniedItem && !equipment.contains(drop));
                    }
                }
                continue;
            }

            for (LootPool pool : override.pools()) {
                if (!checkPoolConditions(pool, event)) {
                    continue;
                }
                for (LootEntry entry : pool.entries()) {
                    if (!checkEntryConditions(entry, event)) {
                        continue;
                    }
                    double chance = entry.chancePct() / 100.0;
                    if (chance < 1.0 && entity.getRandom().nextDouble() >= chance) {
                        continue;
                    }
                    int count = entry.countMin();
                    if (entry.countMax() > entry.countMin()) {
                        long span = (long) entry.countMax() - entry.countMin() + 1L;
                        if (span > 0L && span <= Integer.MAX_VALUE) {
                            count += entity.getRandom().nextInt((int) span);
                        }
                    }
                    if (count <= 0) continue;

                    // An item variant's stack, or the plain item's.
                    ItemStack one = ItemVariants.stack(entry.item(), 1, serverLevel.registryAccess());
                    if (one.isEmpty()) continue;

                    // One item entity per stack, as the game drops (LootEntry#stacks).
                    for (int size : LootEntry.stacks(count, one.getMaxStackSize())) {
                        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                                serverLevel,
                                entity.getX(), entity.getY(), entity.getZ(),
                                one.copyWithCount(size)));
                    }
                }
            }
        }
    }

    private boolean checkPoolConditions(LootPool pool, LivingDropsEvent event) {
        for (LootCondition cond : pool.conditions()) {
            if (!checkCondition(cond, event)) return false;
        }
        return true;
    }

    private boolean checkEntryConditions(LootEntry entry, LivingDropsEvent event) {
        for (LootCondition cond : entry.conditions()) {
            if (!checkCondition(cond, event)) return false;
        }
        return true;
    }

    /** Dispatches condition evaluation via LootCondition.Type — exhaustive, no default pass-through. */
    private boolean checkCondition(LootCondition cond, LivingDropsEvent event) {
        return switch (cond.type()) {
            case RANDOM_CHANCE -> {
                try {
                    double chance = Double.parseDouble(cond.value());
                    if (!Double.isFinite(chance) || chance <= 0) yield false;
                    yield event.getEntity().getRandom().nextDouble() < chance;
                } catch (NumberFormatException e) { yield false; }
            }
            case LOOTING -> {
                if (!(event.getSource().getEntity() instanceof LivingEntity killer))
                    yield false;
                try {
                    var lookup = killer.level().registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                    var looting = lookup.get(
                            net.minecraft.world.item.enchantment.Enchantments.LOOTING);
                    if (looting.isEmpty()) {
                        Strata.LOGGER.warn("Looting enchantment missing from registry");
                        yield false;
                    }
                    int lootingLevel = net.minecraft.world.item.enchantment.EnchantmentHelper
                            .getEnchantmentLevel(looting.get(), killer);
                    yield LootCondition.checkLooting(cond.value(), lootingLevel);
                } catch (Exception e) {
                    Strata.LOGGER.error("Could not evaluate looting condition: {}", e.toString(), e);
                    yield false;
                }
            }
            case PLAYER_LEVEL -> {
                if (!(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player killer))
                    yield false;
                yield LootCondition.checkPlayerLevel(cond.value(), killer.experienceLevel);
            }
            case DEATH_CAUSE -> {
                String id = event.getSource().typeHolder().unwrapKey()
                        .map(key -> key.location().toString()).orElse("");
                yield LootCondition.checkDeathCause(cond.value(), new LootCondition.DeathFacts(id,
                        event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player,
                        SunBurn.diedInSunlight(event.getEntity(), id)));
            }
        };
    }
}
