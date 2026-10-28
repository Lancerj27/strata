package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** AN ITEM VARIANT'S EFFECTS, ON WHOEVER WEARS OR HOLDS IT. */
public final class ItemVariantEffects {

    private ItemVariantEffects() {
    }


    private static volatile Map<String, List<MobAbilities.AbilityEntry>> byVariant = Map.of();
    /** The class rows. */
    private static volatile Map<String, List<EffectTable.EffectEntry>> classByVariant = Map.of();
    private static volatile boolean any;
    /** Each entity's effects from what it wears now. */
    private static final Map<LivingEntity, List<MobAbilities.AbilityEntry>> worn = new WeakHashMap<>();
    /** Each entity's class rows from what it wears now. */
    private static final Map<LivingEntity, List<EffectTable.EffectEntry>> wornClass = new WeakHashMap<>();

    /** Reads item_variant_effects.csv (Config#reload). */
    public static void load() {
        Map<String, List<MobAbilities.AbilityEntry>> parsed = new HashMap<>();
        Map<String, List<EffectTable.EffectEntry>> classes = new HashMap<>();
        int count = 0;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.ItemVariantEffects.FILE))) {
            String variant = row.getOrDefault(Schema.ItemVariantEffects.VARIANT, "").strip();
            if (variant.isEmpty() || !Parse.flag(row.get(Schema.ItemVariantEffects.ENABLED), true)) {
                continue;
            }
            try {
                // A class row: the columns are abilities.csv's, so the mob parser reads it.
                Map<String, String> asClass = com.strata.domain.EffectOps.isEffectRow(row) ? row
                        : com.strata.domain.EffectOps.legacyAsClass(row, true);
                if (asClass != null) {
                    EffectTable.EffectEntry c = EffectTable.entry(Schema.ItemVariantEffects.FILE, variant, asClass);
                    if (c != null) {
                        classes.computeIfAbsent(variant, k -> new ArrayList<>()).add(TriggeredEffects.whileHeld(c));
                        count++;
                    }
                    continue;
                }
                MobAbilities.AbilityEntry e = MobAbilities.entry(Schema.ItemVariantEffects.FILE, variant,
                        row.get(Schema.ItemVariantEffects.ABILITY), row.get(Schema.ItemVariantEffects.PARAMETER),
                        row.get(Schema.ItemVariantEffects.AMOUNT), row.get(Schema.ItemVariantEffects.INTERVAL_TICKS),
                        row.get(Schema.ItemVariantEffects.RADIUS), "");
                if (e == null) {
                    continue;
                }
                parsed.computeIfAbsent(variant, k -> new ArrayList<>()).add(e);
                count++;
            } catch (RuntimeException ex) {
                Strata.LOGGER.warn("{}: bad row {} - {}", Schema.ItemVariantEffects.FILE, row, ex.toString());
            }
        }
        Map<String, List<MobAbilities.AbilityEntry>> frozen = new HashMap<>();
        parsed.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        byVariant = Map.copyOf(frozen);
        Map<String, List<EffectTable.EffectEntry>> frozenClasses = new HashMap<>();
        classes.forEach((k, v) -> frozenClasses.put(k, List.copyOf(v)));
        classByVariant = Map.copyOf(frozenClasses);
        any = count > 0;
        synchronized (worn) {
            worn.clear();
            wornClass.clear();
        }
        Strata.LOGGER.info("Loaded {} item variant effect(s) across {} variant(s)", count, frozen.size());
    }

    /** The effects an entity has from what it wears and holds, each in its own slot. */
    public static List<MobAbilities.AbilityEntry> effectsOf(LivingEntity entity) {
        List<MobAbilities.AbilityEntry> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            String variant = stack.isEmpty() ? null : ItemVariants.variantOf(stack);
            List<MobAbilities.AbilityEntry> effects = variant == null ? null : byVariant.get(variant);
            if (effects != null && inOwnSlot(entity, stack, slot)) {
                out.addAll(effects);
            }
        }
        return out;
    }

    /** The class rows an entity has from what it wears and holds, each in its own slot. */
    public static List<EffectTable.EffectEntry> classRowsOf(LivingEntity entity) {
        List<EffectTable.EffectEntry> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            String variant = stack.isEmpty() ? null : ItemVariants.variantOf(stack);
            List<EffectTable.EffectEntry> rows = variant == null ? null : classByVariant.get(variant);
            if (rows != null && inOwnSlot(entity, stack, slot)) {
                out.addAll(rows);
            }
        }
        return out;
    }

    /** The stack is in the slot it is made for. */
    static boolean inOwnSlot(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        if (stack.getItem() instanceof ShieldItem) {
            return slot == EquipmentSlot.OFFHAND;
        }
        return entity.getEquipmentSlotForItem(stack) == slot;
    }

    /** The kept effects, worked out once when there are none kept (a new entity, or after a reload). */
    private static List<MobAbilities.AbilityEntry> kept(LivingEntity entity) {
        synchronized (worn) {
            return worn.computeIfAbsent(entity, e -> List.copyOf(effectsOf(e)));
        }
    }

    /** The kept class rows, worked out once when none are kept. */
    private static List<EffectTable.EffectEntry> keptClass(LivingEntity entity) {
        synchronized (worn) {
            return wornClass.computeIfAbsent(entity, e -> List.copyOf(classRowsOf(e)));
        }
    }

    /** The events: what an entity wears, each tick, and the two damage moments. */
    public static final class Events {

        @SubscribeEvent
        public void onEquipmentChange(LivingEquipmentChangeEvent event) {
            LivingEntity entity = event.getEntity();
            if (!any || entity.level().isClientSide()) {
                return;
            }
            List<MobAbilities.AbilityEntry> effects = List.copyOf(effectsOf(entity));
            List<EffectTable.EffectEntry> classRows = List.copyOf(classRowsOf(entity));
            synchronized (worn) {
                worn.put(entity, effects);
                wornClass.put(entity, classRows);
            }
        }

        @SubscribeEvent
        public void onTick(EntityTickEvent.Post event) {
            if (!any || !(event.getEntity() instanceof LivingEntity entity)
                    || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            for (MobAbilities.AbilityEntry entry : kept(entity)) {
                entry.ability().onTick(entity, entry, level);
            }
            List<EffectTable.EffectEntry> rows = keptClass(entity);
            TriggeredEffects.fire(rows, "in_sunlight", entity, entity, entity);
            TriggeredEffects.fire(rows, TriggeredEffects.PERMANENT, entity, entity, entity);
        }

        @SubscribeEvent
        public void onIncomingDamage(LivingIncomingDamageEvent event) {
            LivingEntity entity = event.getEntity();
            if (!any || entity.level().isClientSide()) {
                return;
            }
            for (MobAbilities.AbilityEntry entry : kept(entity)) {
                if (entry.ability().onIncomingDamage(entity, entry, event)) {
                    event.setCanceled(true);
                    return;
                }
            }
            // The wearer is hurt: holder the wearer, target whoever hurt it.
            if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                List<EffectTable.EffectEntry> rows = keptClass(entity);
                TriggeredEffects.fire(rows, "on_take_damage", entity, entity, attacker);
                TriggeredEffects.fire(rows, "on_hurt", entity, entity, attacker);
            }
        }

        @SubscribeEvent
        public void onDamageDealt(LivingDamageEvent.Post event) {
            LivingEntity victim = event.getEntity();
            if (!any || victim.level().isClientSide()
                    || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
                return;
            }
            for (MobAbilities.AbilityEntry entry : kept(attacker)) {
                entry.ability().onDamageDealt(victim, entry);
            }
            // The wearer hits: holder the wearer, target the one it hit.
            TriggeredEffects.fire(keptClass(attacker), "on_hit", attacker, attacker, victim);
        }

        @SubscribeEvent
        public void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
            LivingEntity entity = event.getEntity();
            if (!any || entity.level().isClientSide()) {
                return;
            }
            TriggeredEffects.fire(keptClass(entity), "on_death", entity, entity, entity);
        }
    }
}
