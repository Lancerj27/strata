package com.strata.runtime;

import com.strata.domain.EffectOps.EffectRow;
import com.strata.model.EffectClass;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * FIRES CLASS ROWS FROM THEIR TRIGGERS, wherever the rows come from: a mob variant's abilities, an item
 * variant's effects on whoever wears it, a block variant's on whoever stands on it.
 */
public final class TriggeredEffects {

    private TriggeredEffects() {
    }

    /** The trigger that repeats every interval while the source lasts. */
    public static final String PERMANENT = "permanent";

    /** A Permanent row's interval when its Ticks is blank: once a second. */
    public static final int DEFAULT_INTERVAL = 20;

    /**
     * Applies each row hanging off this trigger, after its trigger's own condition and its chance.
     *
     * @param subject the entity the condition is checked on: the one in the sun, the one whose interval is
     *     counted
     * @param holder whose row it is, as EffectRuntime#apply takes it
     * @param target who it acts on
     */
    public static void fire(List<EffectTable.EffectEntry> entries, String trigger,
                            LivingEntity subject, LivingEntity holder, LivingEntity target) {
        if (entries.isEmpty() || !(subject.level() instanceof ServerLevel level)) {
            return;
        }
        for (EffectTable.EffectEntry entry : entries) {
            EffectRow row = entry.row();
            if (!trigger.equals(row.trigger())) {
                continue;
            }
            if ("in_sunlight".equals(trigger) && !SunBurn.inSunlight(subject, level)) {
                continue;
            }
            if (PERMANENT.equals(trigger) && subject.tickCount % interval(row) != 0) {
                continue;
            }
            if (!rollChance(holder, row.chance())) {
                continue;
            }
            EffectRuntime.apply(entry, holder, target);
        }
    }

    /** How often a Permanent row lands, in ticks. */
    public static int interval(EffectRow row) {
        return row.interval() > 0 ? (int) row.interval() : DEFAULT_INTERVAL;
    }

    /** The row as it runs from something worn or stood on. */
    public static EffectTable.EffectEntry whileHeld(EffectTable.EffectEntry entry) {
        EffectRow r = entry.row();
        boolean repeating = PERMANENT.equals(r.trigger()) || "in_sunlight".equals(r.trigger());
        if (r.effectClass() != EffectClass.APPLY_EFFECT || r.duration() > 0 || !repeating) {
            return entry;
        }
        double lasts = ("in_sunlight".equals(r.trigger()) ? 1 : interval(r)) + 20;
        EffectRow held = new EffectRow(r.effectClass(), r.trigger(), r.chance(), r.parameter(), r.amount(),
                r.interval(), r.radius(), r.damage(), lasts, r.canKill(), r.damageType(), r.particle(),
                r.hudIcon(), r.cure(), r.entity());
        return new EffectTable.EffectEntry(held, entry.effect(), entry.attribute(), entry.cure(),
                entry.creature(), entry.damageType());
    }

    /** A percent roll; 100 or more always lands. */
    static boolean rollChance(LivingEntity entity, double chance) {
        return chance >= 100.0 || entity.getRandom().nextDouble() * 100.0 < chance;
    }

    /** The same roll where there may be no one to roll for: a projectile with no shooter left. */
    static boolean rollChance(LivingEntity entity, double chance, ServerLevel level) {
        return entity != null ? rollChance(entity, chance) : chance >= 100.0 || level.getRandom().nextDouble() * 100.0 < chance;
    }
}
