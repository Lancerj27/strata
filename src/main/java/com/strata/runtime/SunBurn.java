package com.strata.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/** THE ONE SUNLIGHT SYSTEM. */
public final class SunBurn {

    private SunBurn() {}

    /** How long a sun ignition counts: the 8 seconds of fire it starts, and a little more. */
    static final long MARK_TICKS = 200;

    private static final Map<LivingEntity, Long> SUN_LIT = new WeakHashMap<>();

    /** TRUE burns, FALSE immune, null the game's own behavior. */
    public static Boolean setting(Mob mob) {
        Boolean property = MobProperties.forMob(mob).burnInDaylight;
        if (property != null) {
            return property;
        }
        var abilities = MobAbilities.forEntity(mob);
        if (abilities.stream().anyMatch(e -> e.ability() == MobAbilities.Ability.SUN_IMMUNE)) {
            return false;
        }
        if (abilities.stream().anyMatch(e -> e.ability() == MobAbilities.Ability.SUN_WEAKNESS)) {
            return true;
        }
        return null;
    }

    /**
     * Day, bright, open sky over its eyes, not in water, rain or powder snow: Mob#isSunBurnTick without its
     * random roll.
     */
    public static boolean inSunlight(LivingEntity entity, ServerLevel level) {
        if (!level.isDay()) {
            return false;
        }
        float light = entity.getLightLevelDependentMagicValue();
        BlockPos eyes = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        boolean wet = entity.isInWaterRainOrBubble() || entity.isInPowderSnow || entity.wasInPowderSnow;
        return light > 0.5F && !wet && level.canSeeSky(eyes);
    }

    /** The game's sun check with its random roll: brighter light burns more often. */
    static boolean sunBurnTick(Mob mob, ServerLevel level) {
        float light = mob.getLightLevelDependentMagicValue();
        return inSunlight(mob, level) && mob.getRandom().nextFloat() * 30.0F < (light - 0.4F) * 2.0F;
    }

    /**
     * Each tick of a mob whose setting is Burns: the game's burn, for a mob the game does not burn itself.
     */
    static void tick(Mob mob, ServerLevel level) {
        if (!mob.isAlive() || !Boolean.TRUE.equals(setting(mob)) || !sunBurnTick(mob, level)) {
            return;
        }
        ItemStack helmet = mob.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) {
            mob.igniteForSeconds(8.0F);
            mark(mob);
        } else if (helmet.isDamageableItem()) {
            var item = helmet.getItem();
            helmet.setDamageValue(helmet.getDamageValue() + mob.getRandom().nextInt(2));
            if (helmet.getDamageValue() >= helmet.getMaxDamage()) {
                mob.onEquippedItemBroken(item, EquipmentSlot.HEAD);
                mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
        }
    }

    /** Records that the sun lit this mob now. */
    public static void mark(LivingEntity entity) {
        synchronized (SUN_LIT) {
            SUN_LIT.put(entity, entity.level().getGameTime());
        }
    }

    /**
     * True when this death by on_fire is a death by sunlight: the sun lit the
     * mob within {@link #MARK_TICKS}.
     * shortcut: a mob the sun lit and that a player also set on fire in the same 10 seconds counts as sunlight; track the fire source if designers need the split.
     */
    public static boolean diedInSunlight(LivingEntity entity, String damageTypeId) {
        if (!"minecraft:on_fire".equals(damageTypeId)) {
            return false;
        }
        Long at;
        synchronized (SUN_LIT) {
            at = SUN_LIT.get(entity);
        }
        return at != null && entity.level().getGameTime() - at <= MARK_TICKS;
    }
}
