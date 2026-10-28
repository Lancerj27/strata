package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A BLOCK VARIANT'S ABILITIES, ON WHOEVER STANDS ON OR IN IT. */
public final class BlockVariantEffects {

    private BlockVariantEffects() {
    }


    private static volatile Map<String, List<MobAbilities.AbilityEntry>> byVariant = Map.of();
    /** The class rows. */
    private static volatile Map<String, List<EffectTable.EffectEntry>> classByVariant = Map.of();
    private static volatile boolean any;

    /** Reads block_variant_effects.csv (Config#reload). */
    public static void load() {
        Map<String, List<MobAbilities.AbilityEntry>> parsed = new HashMap<>();
        Map<String, List<EffectTable.EffectEntry>> classes = new HashMap<>();
        int count = 0;
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.BlockVariantEffects.FILE))) {
            String variant = row.getOrDefault(Schema.BlockVariantEffects.VARIANT, "").strip();
            if (variant.isEmpty() || !Parse.flag(row.get(Schema.BlockVariantEffects.ENABLED), true)) {
                continue;
            }
            try {
                // A class row: the columns are abilities.csv's, so the mob parser reads it.
                Map<String, String> asClass = com.strata.domain.EffectOps.isEffectRow(row) ? row
                        : com.strata.domain.EffectOps.legacyAsClass(row, true);
                if (asClass != null) {
                    EffectTable.EffectEntry c = EffectTable.entry(Schema.BlockVariantEffects.FILE, variant, asClass);
                    if (c != null) {
                        classes.computeIfAbsent(variant, k -> new ArrayList<>()).add(TriggeredEffects.whileHeld(c));
                        count++;
                    }
                    continue;
                }
                MobAbilities.AbilityEntry e = MobAbilities.entry(Schema.BlockVariantEffects.FILE, variant,
                        row.get(Schema.BlockVariantEffects.ABILITY), row.get(Schema.BlockVariantEffects.PARAMETER),
                        row.get(Schema.BlockVariantEffects.AMOUNT), row.get(Schema.BlockVariantEffects.INTERVAL_TICKS),
                        row.get(Schema.BlockVariantEffects.RADIUS), "");
                if (e == null) {
                    continue;
                }
                parsed.computeIfAbsent(variant, k -> new ArrayList<>()).add(e);
                count++;
            } catch (RuntimeException ex) {
                Strata.LOGGER.warn("{}: bad row {} - {}", Schema.BlockVariantEffects.FILE, row, ex.toString());
            }
        }
        Map<String, List<MobAbilities.AbilityEntry>> frozen = new HashMap<>();
        parsed.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        byVariant = Map.copyOf(frozen);
        Map<String, List<EffectTable.EffectEntry>> frozenClasses = new HashMap<>();
        classes.forEach((k, v) -> frozenClasses.put(k, List.copyOf(v)));
        classByVariant = Map.copyOf(frozenClasses);
        any = count > 0;
        Strata.LOGGER.info("Loaded {} block variant abilit(ies) across {} variant(s)", count, frozen.size());
    }

    /** The abilities an entity has from the block variants it stands on or in. */
    public static List<MobAbilities.AbilityEntry> effectsOf(LivingEntity entity) {
        BlockPos on = entity.getOnPos();
        BlockPos in = entity.blockPosition();
        List<MobAbilities.AbilityEntry> out = new ArrayList<>();
        BlockVariants.Def under = BlockVariants.of(entity.level().getBlockState(on).getBlock());
        BlockVariants.Def feet = on.equals(in) ? null : BlockVariants.of(entity.level().getBlockState(in).getBlock());
        if (under != null) out.addAll(byVariant.getOrDefault(under.name(), List.of()));
        if (feet != null && feet != under) out.addAll(byVariant.getOrDefault(feet.name(), List.of()));
        return out;
    }

    /** The class rows an entity has from the block variants it stands on or in. */
    public static List<EffectTable.EffectEntry> classRowsOf(LivingEntity entity) {
        BlockPos on = entity.getOnPos();
        BlockPos in = entity.blockPosition();
        List<EffectTable.EffectEntry> out = new ArrayList<>();
        BlockVariants.Def under = BlockVariants.of(entity.level().getBlockState(on).getBlock());
        BlockVariants.Def feet = on.equals(in) ? null : BlockVariants.of(entity.level().getBlockState(in).getBlock());
        if (under != null) out.addAll(classByVariant.getOrDefault(under.name(), List.of()));
        if (feet != null && feet != under) out.addAll(classByVariant.getOrDefault(feet.name(), List.of()));
        return out;
    }

    /** The events: each tick, and the incoming damage (an immunity). */
    public static final class Events {

        @SubscribeEvent
        public void onTick(EntityTickEvent.Post event) {
            if (!any || !(event.getEntity() instanceof LivingEntity entity)
                    || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            for (MobAbilities.AbilityEntry entry : effectsOf(entity)) {
                entry.ability().onTick(entity, entry, level);
            }
            if (!classByVariant.isEmpty()) {
                List<EffectTable.EffectEntry> rows = classRowsOf(entity);
                TriggeredEffects.fire(rows, "in_sunlight", entity, entity, entity);
                TriggeredEffects.fire(rows, TriggeredEffects.PERMANENT, entity, entity, entity);
            }
        }

        @SubscribeEvent
        public void onIncomingDamage(LivingIncomingDamageEvent event) {
            LivingEntity entity = event.getEntity();
            if (!any || entity.level().isClientSide()) {
                return;
            }
            for (MobAbilities.AbilityEntry entry : effectsOf(entity)) {
                if (entry.ability().onIncomingDamage(entity, entry, event)) {
                    event.setCanceled(true);
                    return;
                }
            }
            if (!classByVariant.isEmpty() && event.getSource().getEntity() instanceof LivingEntity attacker) {
                List<EffectTable.EffectEntry> rows = classRowsOf(entity);
                TriggeredEffects.fire(rows, "on_take_damage", entity, entity, attacker);
                TriggeredEffects.fire(rows, "on_hurt", entity, entity, attacker);
            }
        }
    }
}
