package com.strata.runtime;

import com.strata.data.CsvTable;
import com.strata.data.Schema;
import com.strata.Strata;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-creature BASE attribute values, from config/strata/mob_base_stats.csv. */
public final class EntityBaseAttributes {

    private EntityBaseAttributes() {}

    /** entity id -> (attribute -> base value). */
    private static volatile Map<ResourceLocation, Map<Holder<Attribute>, Double>> byEntity = Map.of();

    public static void load(Path csvPath) {
        Map<ResourceLocation, Map<Holder<Attribute>, Double>> parsed = new HashMap<>();
        int rows = 0;
        int skipped = 0;

        for (Map<String, String> row : CsvTable.read(csvPath)) {
            // D-8: THE SHIPPED TABLE HAS NO `enabled` COLUMN.
            if (!com.strata.data.Parse.flag(row.get("enabled"), true)) {
                // Reached only for a file a designer has widened by hand.
                continue;
            }
            try {
                ResourceLocation entityId = ResourceLocation.parse(row.get(Schema.MobBaseStats.ENTITY).strip());
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                    Strata.LOGGER.warn("mob_base_stats.csv: unknown entity {} - row skipped", entityId);
                    skipped++;
                    continue;
                }

                ResourceLocation attributeId = ResourceLocation.parse(row.get(Schema.MobBaseStats.ATTRIBUTE).strip());
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.ATTRIBUTE, attributeId)).orElse(null);
                if (attribute == null) {
                    Strata.LOGGER.warn("mob_base_stats.csv: unknown attribute {} - row skipped", attributeId);
                    skipped++;
                    continue;
                }

                double value = Double.parseDouble(row.get(Schema.MobBaseStats.VALUE).strip());
                if (!Double.isFinite(value)) {
                    Strata.LOGGER.warn("mob_base_stats.csv: non-finite value {} for "
                            + "{} - row skipped", row.get(Schema.MobBaseStats.VALUE), entityId);
                    skipped++;
                    continue;
                }
                parsed.computeIfAbsent(entityId, k -> new HashMap<>()).put(attribute, value);
                rows++;
            } catch (Exception e) {
                Strata.LOGGER.warn("mob_base_stats.csv: bad row {} - {}", row, e.toString());
                skipped++;
            }
        }

        byEntity = Map.copyOf(parsed);
        Strata.LOGGER.info("Loaded {} entity base attribute overrides across {} entities ({} skipped)",
                rows, parsed.size(), skipped);
    }

    /** The configured override for one attribute of one creature, or null if there is none. */
    public static Double overrideFor(ResourceLocation entityId, ResourceLocation attributeId) {
        Map<Holder<Attribute>, Double> overrides = byEntity.get(entityId);
        if (overrides == null) {
            return null;
        }
        for (Map.Entry<Holder<Attribute>, Double> entry : overrides.entrySet()) {
            if (entry.getKey().unwrapKey()
                    .map(key -> key.location().equals(attributeId)).orElse(false)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** One entity's overrides, attribute to base value; empty when it has none (the player). */
    public static Map<Holder<Attribute>, Double> overridesOf(ResourceLocation entityId) {
        return byEntity.getOrDefault(entityId, Map.of());
    }

    /**
     * Whose base stat edits a mob takes: its base mob's, for a variant registered as its own entity type,
     * else its own type's.
     *
     * @param base the base entity of a variant's own type, or empty
     */
    public static ResourceLocation statOwner(ResourceLocation typeKey, java.util.Optional<String> base) {
        return base.map(ResourceLocation::tryParse).orElse(typeKey);
    }

    /** Applies this creature's base overrides. */
    public static void apply(LivingEntity entity) {
        if (byEntity.isEmpty()) {
            return;
        }
        ResourceLocation id = statOwner(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                VariantEntityRegistry.baseEntityIdForType(entity.getType()));
        // RS-7: an unregistered type has no key, and get(null) on this map throws - on the spawn path, a
        // crash.
        Map<Holder<Attribute>, Double> overrides = id == null ? null : byEntity.get(id);
        if (overrides == null) {
            return;
        }

        boolean healthChanged = false;
        for (Map.Entry<Holder<Attribute>, Double> entry : overrides.entrySet()) {
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (instance == null) {
                // The row names an attribute this creature does not have.
                continue;
            }
            instance.setBaseValue(entry.getValue());
            if (entry.getKey().equals(Attributes.MAX_HEALTH)) {
                healthChanged = true;
            }
        }

        if (healthChanged) {
            // See the class doc: current health does not follow the base value on its own, in either
            // direction.
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /** One attribute as the editor lists it: its effective value, and whether an override sets it. */
    public record AttributeRow(String attribute, double value, boolean overridden) {
    }

    /**
     * The attribute list the editor shows for an entity id, or empty when the id names nothing the registry
     * knows (the query moved here from AttributeEditScreen).
     */
    public static List<AttributeRow> rowsFor(String entityId) {
        List<AttributeRow> out = new ArrayList<>();
        try {
            ResourceLocation entityRl = ResourceLocation.tryParse(entityId == null ? "" : entityId);
            if (entityRl == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(entityRl)) {
                return out;
            }
            for (var entry : effectiveFor(BuiltInRegistries.ENTITY_TYPE.get(entityRl))) {
                ResourceLocation attributeId = entry.getKey();
                if (attributeId == null) {
                    continue;
                }
                out.add(new AttributeRow(attributeId.toString(), entry.getValue(),
                        overrideFor(entityRl, attributeId) != null));
            }
        } catch (RuntimeException e) {
            Strata.LOGGER.error("Could not read attributes for {} - showing none "
                    + "rather than a partial list: {}", entityId, e.toString());
            return List.of();
        }
        return out;
    }

    /**
     * This creature's EFFECTIVE base stats: its enabled overrides where it has them, and the game's own
     * defaults everywhere else.
     */
    public static List<Map.Entry<ResourceLocation, Double>> effectiveFor(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        Map<Holder<Attribute>, Double> overrides = id == null ? Map.of() : byEntity.getOrDefault(id, Map.of());

        // Flattened to ids ONCE rather than compared holder-by-holder for every attribute.
        Map<ResourceLocation, Double> overrideIds = new HashMap<>();
        overrides.forEach((holder, value) ->
                holder.unwrapKey().ifPresent(key -> overrideIds.put(key.location(), value)));

        List<Map.Entry<ResourceLocation, Double>> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Double> base : defaultsFor(type)) {
            Double override = overrideIds.get(base.getKey());
            out.add(Map.entry(base.getKey(), override == null ? base.getValue() : override));
        }
        return out;
    }

    /** Every attribute a given entity type actually has, with vanilla's own default value for each. */
    public static List<Map.Entry<ResourceLocation, Double>> defaultsFor(EntityType<?> type) {
        List<Map.Entry<ResourceLocation, Double>> out = new ArrayList<>();
        try {
            @SuppressWarnings("unchecked")
            EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
            if (!net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(living)) {
                return out;
            }
            var supplier = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(living);
            BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
                if (supplier.hasAttribute(holder)) {
                    out.add(Map.entry(holder.key().location(), supplier.getValue(holder)));
                }
            });
        } catch (Exception e) {
            // A non-living entity type, or a mod whose supplier throws.
            return List.of();
        }
        return out;
    }
}
