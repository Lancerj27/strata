package com.strata.runtime;

import com.strata.data.Schema;
import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import com.strata.model.ScalingFormulas;

import java.util.List;
import java.util.Map;

/** One row of a scaling table: how an attribute grows with level. */
public record AttributeCurve(
        Holder<Attribute> attribute,
        ResourceLocation attributeId,
        double perLevel,
        double exponent,
        int startLevel,
        ResourceLocation modifierId,
        ScalingFormulas.Kind kind
) {

    /**
     * The start level of a curve that names none: level 1 is the game's values, and the curve gives its first
     * bonus at level 2.
     */
    public static final int DEFAULT_START_LEVEL = 2;

    /**
     * The multiplicative bonus at a level: {@code max(0, level - start_level + 1)} then {@code per_level
     * x effective ^ exponent}.
     */
    public static double bonus(double perLevel, double exponent, int level, int startLevel) {
        return bonus(ScalingFormulas.Kind.MOB_ATTRIBUTE, 1.0, perLevel, exponent, level, startLevel, 1.0);
    }

    /** The bonus a formula gives for these numbers. */
    public static double bonus(ScalingFormulas.Kind kind, double base, double perLevel, double exponent,
                               int level, int startLevel, double coefficient) {
        return ScalingFormulas.eval(kind, ScalingFormulas.vars("base", base, "level", level,
                "start_level", startLevel, "per_level", perLevel, "exponent", exponent, "coefficient", coefficient));
    }

    /** The bonus this curve's own numbers produce at a level, for a base of 1 and a coefficient of 1. */
    public double bonusAt(int level) {
        return bonus(kind, 1.0, perLevel, exponent, level, startLevel, 1.0);
    }

    private double bonusAt(AttributeInstance instance, int level, double coefficient) {
        return bonus(kind, instance.getBaseValue(), perLevel, exponent, level, startLevel, coefficient);
    }

    /** Removes any modifier this curve previously applied, then applies a fresh one. */
    public void apply(LivingEntity entity, int level) {
        apply(entity, level, 1.0);
    }

    public void apply(LivingEntity entity, int level, double coefficient) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        float share = healthShare(entity);
        instance.removeModifier(modifierId);

        double bonus = bonusAt(instance, level, coefficient);
        if (!Double.isFinite(bonus)) {
            Strata.LOGGER.error("{}: non-finite scaling bonus {} for {} at level {} - "
                    + "aborting. Check per_level/exponent in the scaling table.",
                    modifierId, bonus, attributeId, level);
            return;
        }
        if (bonus <= 0.0) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(
                modifierId, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

        if (attribute.equals(Attributes.MAX_HEALTH)) {
            entity.setHealth(share * entity.getMaxHealth());
        }
    }

    /** Applies the curve from a BASE level to a TARGET level. */
    public void applyFromBase(LivingEntity entity, int targetLevel, int baseLevel, double coefficient) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        float share = healthShare(entity);
        instance.removeModifier(modifierId);

        double bonus = bonusAt(instance, targetLevel, coefficient) - bonusAt(instance, baseLevel, coefficient);
        if (!Double.isFinite(bonus)) {
            Strata.LOGGER.error("{}: non-finite scaling bonus {} for {} at level {} (base {}) - "
                    + "aborting. Check per_level/exponent in the scaling table.",
                    modifierId, bonus, attributeId, targetLevel, baseLevel);
            return;
        }
        if (bonus <= 0.0) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(
                modifierId, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

        if (attribute.equals(Attributes.MAX_HEALTH)) {
            entity.setHealth(share * entity.getMaxHealth());
        }
    }

    /** The share of its hearts the entity has: 1 for a new mob, the player's share for a player. */
    static float healthShare(LivingEntity entity) {
        float max = entity.getMaxHealth();
        return max <= 0 ? 1F : Math.min(1F, entity.getHealth() / max);
    }

    /** Loads a scaling table. */
    public static List<AttributeCurve> load(Path csvPath) {
        List<AttributeCurve> out = new ArrayList<>();
        List<ResourceLocation> seen = new ArrayList<>();

        for (Map<String, String> row : CsvTable.read(csvPath)) {
            if (!Parse.flag(row.get(Schema.Scaling.ENABLED), true)) {
                continue;
            }
            try {
                ResourceLocation attributeId = ResourceLocation.parse(
                        row.getOrDefault(Schema.Scaling.ATTRIBUTE, "").strip());
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                        .getHolder(ResourceKey.create(Registries.ATTRIBUTE, attributeId))
                        .orElse(null);
                if (attribute == null) {
                    Strata.LOGGER.warn("{}: unknown attribute {} - row skipped",
                            csvPath.getFileName(), attributeId);
                    continue;
                }

                String rawId = row.getOrDefault(Schema.Scaling.MODIFIER_ID, "").strip();
                ResourceLocation modifierId = rawId.isEmpty()
                        ? ResourceLocation.fromNamespaceAndPath("strata", defaultId(csvPath, attributeId))
                        : ResourceLocation.parse(rawId);

                if (seen.contains(modifierId)) {
                    Strata.LOGGER.warn("{}: modifier_id {} is used twice - the second row is "
                            + "dropped. Duplicates make one row remove another's modifier.",
                            csvPath.getFileName(), modifierId);
                    continue;
                }
                seen.add(modifierId);

                out.add(new AttributeCurve(attribute, attributeId,
                        Parse.number(row.get(Schema.Scaling.PER_LEVEL), 0.0),
                        Parse.number(row.get(Schema.Scaling.EXPONENT), 0.6),
                        Parse.integer(row.get(Schema.Scaling.START_LEVEL), DEFAULT_START_LEVEL),
                        modifierId,
                        csvPath.getFileName().toString().equals(Schema.Scaling.PLAYER_FILE)
                                ? ScalingFormulas.Kind.PLAYER_ATTRIBUTE : ScalingFormulas.Kind.MOB_ATTRIBUTE));
            } catch (Exception e) {
                Strata.LOGGER.warn("{}: bad row {} - {}", csvPath.getFileName(), row, e.toString());
            }
        }
        Strata.LOGGER.info("Loaded {} curve(s) from {}", out.size(), csvPath.getFileName());
        return List.copyOf(out);
    }

    /** "mob_max_health" - derived so a blank modifier_id is still unique per table. */
    private static String defaultId(Path csvPath, ResourceLocation attributeId) {
        String table = csvPath.getFileName().toString().replace(".csv", "").replace("scaling_", "");
        return table + "_" + attributeId.getPath().replace('.', '_');
    }
}