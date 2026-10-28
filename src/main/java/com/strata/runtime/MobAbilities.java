package com.strata.runtime;

import com.strata.data.Parse;
import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.domain.EffectOps;
import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What a variant DOES, from config/strata/abilities.csv. */
public final class MobAbilities {

    private MobAbilities() {}

    /**
     * Each constant carries its own behavior via the four dispatch methods (onTick / onIncomingDamage /
     * onDamageDealt / onSpawn).
     *
     * @param needsEffect true when `parameter` must name a MobEffect
     * @param ticking true when it needs the per-tick handler
     */
    public enum Ability {
        /** Does not burn in daylight. */
        SUN_IMMUNE(false, false) {
            @Override
            void onTick(LivingEntity entity, AbilityEntry entry, ServerLevel level) {
            }
        },

        /** BURNS IN DAYLIGHT, as a zombie or a skeleton does. */
        SUN_WEAKNESS(false, true) {
            @Override
            void onTick(LivingEntity entity, AbilityEntry entry, ServerLevel level) {
                // The burn_in_daylight property, when set, runs its own tick (MobPropertyEffects).
                if (entity instanceof net.minecraft.world.entity.Mob mob
                        && MobProperties.forMob(mob).burnInDaylight == null) {
                    SunBurn.tick(mob, level);
                }
            }
        },

        // HEAL, EFFECT, AURA_PLAYERS AND AURA_MOBS RUN AS CLASS ROWS.

        /** Read as heal, Permanent every interval (EffectOps#legacyAsClass). */
        HEAL(false, false),

        /** Read as apply_effect: every interval, or at spawn for good (EffectOps#legacyAsClass). */
        EFFECT(true, false),

        /** Read as aura on players, Permanent every interval (EffectOps#legacyAsClass). */
        AURA_PLAYERS(true, false),

        /** Read as aura on the creature it names, or all but players (EffectOps#legacyAsClass). */
        AURA_MOBS(true, false),

        /**
         * Takes no damage from `parameter` - either a damage type id (minecraft:lava) or a damage type TAG
         * with a leading # (#minecraft:is_fire).
         */
        DAMAGE_IMMUNITY(false, false) {
            @Override
            boolean onIncomingDamage(LivingEntity entity, AbilityEntry entry,
                                     LivingIncomingDamageEvent event) {
                // Never the void or /kill (#minecraft:bypasses_invulnerability): a mob immune to them fell
                // forever.
                return !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                        && matches(entry, event.getSource());
            }
        };

        public final boolean needsEffect;
        public final boolean ticking;

        Ability(boolean needsEffect, boolean ticking) {
            this.needsEffect = needsEffect;
            this.ticking = ticking;
        }

        /** Per-tick behavior. */
        void onTick(LivingEntity entity, AbilityEntry entry, ServerLevel level) {}

        /** Incoming-damage behavior. */
        boolean onIncomingDamage(LivingEntity entity, AbilityEntry entry,
                                 LivingIncomingDamageEvent event) {
            return false;
        }

        /** Post-damage behavior applied to the victim. */
        void onDamageDealt(LivingEntity victim, AbilityEntry entry) {}

        /** One-shot behavior at spawn. */
        void onSpawn(LivingEntity entity, AbilityEntry entry) {}

        public static Ability parse(String raw) {
            for (Ability ability : values()) {
                if (ability.name().equalsIgnoreCase(raw.strip())) {
                    return ability;
                }
            }
            return null;
        }
    }

    /**
     * @param effect resolved MobEffect, or null for abilities taking none
     * @param damageType resolved damage type id for DAMAGE_IMMUNITY, or null
     * @param damageTag resolved damage type tag for DAMAGE_IMMUNITY, or null
     * @param targetEntity target entity type for aura (75.10), from cure column
     */
    public record AbilityEntry(
            Ability ability,
            Holder<MobEffect> effect,
            ResourceLocation damageType,
            TagKey<DamageType> damageTag,
            double amount,
            int intervalTicks,
            double radius,
            ResourceLocation targetEntity
    ) {}

    private static volatile Map<String, List<AbilityEntry>> byVariant = Map.of();
    private static volatile boolean anyTicking;
    private static volatile boolean anyOnHit;
    private static volatile boolean anyOnHurt;
    private static volatile boolean anyImmunity;

    public static void load(Path csvPath) {
        Map<String, List<AbilityEntry>> parsed = new HashMap<>();
        boolean ticking = false;
        boolean onHit = false;
        boolean onHurt = false;
        boolean immunity = false;
        int count = 0;

        for (Map<String, String> row : CsvTable.read(csvPath)) {
            try {
                // A row of one of the six classes belongs to EffectTable, not to us.
                if (EffectOps.isEffectRow(row) || EffectOps.legacyAsClass(row, false) != null) {
                    // A class row, or an old row EffectTable reads as one.
                    continue;
                }
                // Parse#flag, as the screens read it: a blank cell is ON.
                String variant = row.getOrDefault(Schema.Abilities.VARIANT_NAME, "").strip();
                if (!Parse.flag(row.get(Schema.Abilities.ENABLED), true)) {
                    // A switched-off row still makes the set this instance's own, so a variant can turn off
                    // an ability it would inherit from its base mob.
                    if (!variant.isEmpty()) {
                        parsed.computeIfAbsent(ScopeIndex.key(
                                row.getOrDefault(Schema.Abilities.SCOPE, ""),
                                row.getOrDefault(Schema.Abilities.SCOPE_ID, ""),
                                variant), k -> new ArrayList<>());
                    }
                    continue;
                }
                if (variant.isEmpty()) {
                    Strata.LOGGER.warn("abilities.csv: row {} has no variant name - row skipped",
                            row.get(Schema.Abilities.ABILITY));
                    continue;
                }
                AbilityEntry parsedEntry = entry("abilities.csv", variant, row.getOrDefault(Schema.Abilities.ABILITY, ""),
                        row.getOrDefault(Schema.Abilities.PARAMETER, ""), row.get(Schema.Abilities.AMOUNT),
                        row.get(Schema.Abilities.INTERVAL_TICKS), row.get(Schema.Abilities.RADIUS),
                        row.getOrDefault(Schema.Abilities.CURE, ""));
                if (parsedEntry == null) {
                    continue;
                }
                Ability ability = parsedEntry.ability();

                // INSTANCED: keyed on the scope too, so a tier's abilities for a variant are a separate entry
                // from the global ones rather than piling into the same list.
                String instance = ScopeIndex.key(
                        row.getOrDefault(Schema.Abilities.SCOPE, ""),
                        row.getOrDefault(Schema.Abilities.SCOPE_ID, ""),
                        variant);
                parsed.computeIfAbsent(instance, k -> new ArrayList<>()).add(parsedEntry);

                ticking |= ability.ticking;
                immunity |= ability == Ability.DAMAGE_IMMUNITY;
                count++;
            } catch (Exception e) {
                Strata.LOGGER.warn("abilities.csv: bad row {} - {}", row, e.toString());
            }
        }

        Map<String, List<AbilityEntry>> frozen = new HashMap<>();
        parsed.forEach((variant, list) -> frozen.put(variant, List.copyOf(list)));
        byVariant = Map.copyOf(frozen);
        anyTicking = ticking;
        anyOnHit = onHit;
        anyOnHurt = onHurt;
        anyImmunity = immunity;

        Strata.LOGGER.info("Loaded {} variant abilities across {} variants (tick handler {})",
                count, frozen.size(), ticking ? "active" : "idle");
    }

    /** One ability row as an entry, or null when load would skip it. */
    public static AbilityEntry entry(String file, String owner, String abilityId, String parameterRaw, String amountRaw,
                                     String intervalRaw, String radiusRaw, String cureRaw) {
        Ability ability = Ability.parse(abilityId == null ? "" : abilityId);
        if (ability == null) {
            Strata.LOGGER.warn("{}: unknown ability '{}' for {} - row skipped. Valid: {}", file, abilityId, owner,
                    validNames());
            return null;
        }
        String parameter = parameterRaw == null ? "" : parameterRaw.strip();
        // The registry-free checks, shared with the editor so it refuses a row before the save rather than
        // after the load.
        String problem = rowProblem(ability.name(), parameter, amountRaw, intervalRaw, radiusRaw);
        if (problem != null) {
            Strata.LOGGER.warn("{}: {} on {} - {} - row skipped", file, ability, owner, problem);
            return null;
        }
        double amount = Parse.number(amountRaw, 0.0);
        int interval = (int) Parse.number(intervalRaw, 0.0);
        double radius = Parse.number(radiusRaw, 8.0);

        Holder<MobEffect> effect = null;
        if (ability.needsEffect) {
            effect = BuiltInRegistries.MOB_EFFECT
                    .getHolder(ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.parse(parameter)))
                    .orElse(null);
            if (effect == null) {
                Strata.LOGGER.warn("{}: unknown effect '{}' for {} - row skipped", file, parameter, owner);
                return null;
            }
        }

        ResourceLocation damageType = null;
        TagKey<DamageType> damageTag = null;
        if (ability == Ability.DAMAGE_IMMUNITY) {
            if (parameter.startsWith("#")) {
                damageTag = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse(parameter.substring(1)));
            } else {
                damageType = ResourceLocation.parse(parameter);
            }
        }

        // 75.10: target entity for aura (from cure column, unused by aura)
        ResourceLocation targetEntity = null;
        if (ability == Ability.AURA_PLAYERS || ability == Ability.AURA_MOBS) {
            String cure = cureRaw == null ? "" : cureRaw.strip();
            if (!cure.isBlank()) {
                targetEntity = ResourceLocation.tryParse(cure);
                if (targetEntity == null) {
                    Strata.LOGGER.warn("{}: invalid target entity '{}' for aura - row skipped", file, cure);
                    return null;
                }
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(targetEntity)) {
                    Strata.LOGGER.warn("{}: target entity '{}' not found in registry - row skipped", file, cure);
                    return null;
                }
            }
        }

        return new AbilityEntry(ability, effect, damageType, damageTag, amount, interval, radius, targetEntity);
    }

    /** Why load would skip a row with these cells, in the designer's words, or null when the row passes. */
    public static String rowProblem(String abilityId, String parameter, String amount,
                                    String intervalTicks, String radius) {
        Ability ability = abilityId == null ? null : Ability.parse(abilityId);
        if (ability == null) {
            return "unknown ability '" + abilityId + "'";
        }
        String param = parameter == null ? "" : parameter.strip();
        if (ability.needsEffect && param.isEmpty()) {
            return "needs an effect id in the parameter";
        }
        if (ability == Ability.DAMAGE_IMMUNITY && param.isEmpty()) {
            return "needs a damage type or #tag in the parameter";
        }
        if (!numberOrBlank(amount)) {
            return "the amount is not a number";
        }
        if (!numberOrBlank(radius)) {
            return "the radius is not a number";
        }
        if (!numberOrBlank(intervalTicks)) {
            return "the interval is not a number";
        }
        if (needsInterval(ability) && Parse.number(intervalTicks, 0.0) <= 0) {
            return "needs an interval above 0 ticks";
        }
        return null;
    }

    private static boolean numberOrBlank(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        try {
            return Double.isFinite(Double.parseDouble(raw.strip()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** HEAL and the auras are always periodic. */
    private static boolean needsInterval(Ability ability) {
        return ability == Ability.HEAL
                || ability == Ability.AURA_PLAYERS
                || ability == Ability.AURA_MOBS;
    }

    /**
     * Builds an instance of this row's effect.
     *
     * @param duration -1 is vanilla's infinite.
     */
    public static MobEffectInstance instance(AbilityEntry entry, int duration) {
        return new MobEffectInstance(entry.effect(), duration, (int) entry.amount(), false, false, true);
    }

    /** True when this damage source is the one a DAMAGE_IMMUNITY row names. */
    public static boolean matches(AbilityEntry entry, DamageSource source) {
        if (entry.damageTag() != null) {
            return source.is(entry.damageTag());
        }
        if (entry.damageType() == null) {
            return false;
        }
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().equals(entry.damageType()))
                .orElse(false);
    }


    static boolean due(LivingEntity entity, AbilityEntry entry) {
        return entry.intervalTicks() > 0 && entity.tickCount % entry.intervalTicks() == 0;
    }

    static int duration(AbilityEntry entry) {
        return entry.intervalTicks() > 0 ? entry.intervalTicks() : 100;
    }

    /** Every ability name the ability column accepts. */
    public static List<String> abilityNames() {
        List<String> names = new ArrayList<>();
        for (Ability ability : Ability.values()) {
            names.add(ability.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private static String validNames() {
        return String.join(", ", abilityNames());
    }

    public static List<AbilityEntry> forVariant(String variant) {
        return forVariant(variant, "", "");
    }

    /**
     * The abilities for a variant as seen from one scope: biome, then tier, then global, first layer that
     * defines any.
     */
    public static List<AbilityEntry> forVariant(String variant, String tier, String biome) {
        List<AbilityEntry> hit = ScopeIndex.resolveOrBase(byVariant, variant,
                VariantHandler.baseOf(variant), tier, biome);
        return hit == null ? List.of() : hit;
    }

    /** The abilities for the scope THIS creature spawned in. */
    public static List<AbilityEntry> forEntity(LivingEntity entity) {
        String packed = entity.getData(MobLevel.VARIANT_SCOPE);
        return forVariant(variantOf(entity),
                ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
    }

    /** The variant a creature rolled at spawn, or "". */
    public static String variantOf(LivingEntity entity) {
        return entity.getData(MobLevel.VARIANT_ID);
    }

    // Each handler checks its own flag first, so a pack that only uses auras costs nothing on the damage
    // path, and vice versa.
    public static boolean anyTicking() {
        return anyTicking;
    }

    public static boolean anyOnHit() {
        return anyOnHit;
    }

    public static boolean anyOnHurt() {
        return anyOnHurt;
    }

    public static boolean anyImmunity() {
        return anyImmunity;
    }
}
