package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Schema;
import com.strata.domain.EffectOps;
import com.strata.domain.EffectOps.EffectRow;
import com.strata.model.EffectClass;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;

/** THE SIX BEHAVIOUR CLASSES, READ FROM abilities.csv. */
public final class EffectTable {

    private EffectTable() {
    }

    /**
     * One class row after every id in it has been resolved.
     *
     * @param row the parsed row, whose effectClass is never null
     * @param effect the MobEffect apply_effect gives
     * @param attribute the Attribute attribute_change moves
     * @param cure the Item that ends an attribute_change, or null
     * @param creature the EntityType spawn creates
     * @param damageType the key of the damage type the row deals
     */
    public record EffectEntry(EffectRow row, Holder<MobEffect> effect, Holder<Attribute> attribute,
                              Item cure, Holder<EntityType<?>> creature, ResourceKey<DamageType> damageType) {
    }

    /** Keyed on (scope, scope_id, variant), exactly as MobAbilities keys its own. */
    private static volatile Map<String, List<EffectEntry>> byVariant = Map.of();

    /** Every trigger some loaded row names. */
    private static volatile java.util.Set<String> triggers = java.util.Set.of();

    /** True when a loaded, enabled class row hangs off this trigger. */
    public static boolean hasTrigger(String trigger) {
        return triggers.contains(trigger);
    }

    /** Reads every class row of one abilities file, or reloads them. */
    public static void load(Path csvPath) {
        Map<String, List<EffectEntry>> parsed = new HashMap<>();
        int count = 0;

        for (Map<String, String> raw : CsvTable.read(csvPath)) {
            // An old heal, effect, aura or on hit/hurt row is read as the class row it always was.
            Map<String, String> folded = EffectOps.legacyAsClass(raw, false);
            Map<String, String> row = folded != null ? folded : raw;
            try {
                if (!EffectOps.isEffectRow(row)) {
                    // Sun immune, sun weakness, damage immunity: MobAbilities' own.
                    continue;
                }
                // Parse#flag, as the screens read it: a blank cell is ON.
                String variant = row.getOrDefault(Schema.Abilities.VARIANT_NAME, "").strip();
                if (!Parse.flag(row.get(Schema.Abilities.ENABLED), true)) {
                    if (!variant.isEmpty()) {
                        parsed.computeIfAbsent(ScopeIndex.key(
                                row.getOrDefault(Schema.Abilities.SCOPE, ""),
                                row.getOrDefault(Schema.Abilities.SCOPE_ID, ""),
                                variant), k -> new ArrayList<>());
                    }
                    continue;
                }
                if (variant.isEmpty()) {
                    Strata.LOGGER.warn("abilities.csv: effect row with no variant name ({}) - row skipped",
                            row.get(Schema.Abilities.EFFECT_CLASS));
                    continue;
                }
                EffectEntry parsedEntry = entry("abilities.csv", variant, row);
                if (parsedEntry == null) {
                    continue;
                }
                String instance = ScopeIndex.key(
                        row.getOrDefault(Schema.Abilities.SCOPE, ""),
                        row.getOrDefault(Schema.Abilities.SCOPE_ID, ""),
                        variant);
                parsed.computeIfAbsent(instance, k -> new ArrayList<>()).add(parsedEntry);
                count++;
            } catch (Exception e) {
                Strata.LOGGER.warn("abilities.csv: bad effect row {} - {}", row, e.toString());
            }
        }

        Map<String, List<EffectEntry>> frozen = new HashMap<>();
        parsed.forEach((variant, list) -> frozen.put(variant, List.copyOf(list)));
        byVariant = Map.copyOf(frozen);
        java.util.Set<String> named = new java.util.HashSet<>();
        frozen.values().forEach(list -> list.forEach(e -> named.add(e.row().trigger())));
        triggers = java.util.Set.copyOf(named);

        Strata.LOGGER.info("Loaded {} variant effect rows across {} variants",
                count, frozen.size());
    }

    /** One class row as an entry, or null when load would skip it. */
    public static EffectEntry entry(String file, String owner, Map<String, String> row) {
        EffectRow parsed = EffectOps.parse(row);
        if (parsed == null) {
            Strata.LOGGER.warn("{}: {} names no effect class Strata knows - row skipped", file, owner);
            return null;
        }
        if (parsed.effectClass() == EffectClass.DAMAGE_OVER_TIME
                && (parsed.interval() <= 0 || parsed.duration() <= 0)) {
            Strata.LOGGER.warn("{}: {}'s damage_over_time row needs an interval and a duration above 0"
                    + " - row skipped", file, owner);
            return null;
        }
        if (parsed.effectClass() == EffectClass.ATTRIBUTE_CHANGE && parsed.duration() <= 0) {
            Strata.LOGGER.warn("{}: {}'s attribute_change row needs a duration above 0 - row skipped",
                    file, owner);
            return null;
        }

        Holder<MobEffect> effect = null;
        Holder<Attribute> attribute = null;
        Item cure = null;
        Holder<EntityType<?>> creature = null;
        ResourceKey<DamageType> damageType = null;
        String parameter = parsed.parameter();

        switch (parsed.effectClass()) {
            case APPLY_EFFECT -> {
                effect = builtIn(BuiltInRegistries.MOB_EFFECT,
                        ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.parse(parameter)),
                        file, owner, "effect id");
                if (effect == null) {
                    return null;
                }
            }
            case ATTRIBUTE_CHANGE -> {
                attribute = builtIn(BuiltInRegistries.ATTRIBUTE,
                        ResourceKey.create(Registries.ATTRIBUTE, ResourceLocation.parse(parameter)),
                        file, owner, "attribute id");
                if (attribute == null) {
                    return null;
                }
                if (!parsed.cure().isBlank()) {
                    Holder<Item> resolved = builtIn(BuiltInRegistries.ITEM,
                            ResourceKey.create(Registries.ITEM, ResourceLocation.parse(parsed.cure())),
                            file, owner, "cure item id");
                    if (resolved == null) {
                        return null;
                    }
                    cure = resolved.value();
                }
            }
            case SPAWN -> {
                // The entity column names the creature.
                String id = parsed.entity().isBlank() ? parameter : parsed.entity();
                // A variant spawns as its own mob type when it has one, else as its base mob.
                if (id.startsWith(VARIANT_PREFIX)) {
                    id = variantTypeId(id.substring(VARIANT_PREFIX.length()));
                    if (id == null) {
                        Strata.LOGGER.warn("{}: {} spawns the variant {}, which does not exist - row skipped",
                                file, owner, parsed.entity());
                        return null;
                    }
                }
                creature = builtIn(BuiltInRegistries.ENTITY_TYPE,
                        ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(id)),
                        file, owner, "creature id");
                if (creature == null) {
                    return null;
                }
            }
            case DAMAGE_OVER_TIME, EXPLODE, EXPLODE_AND_DIE -> {
                if (!parsed.damageType().isBlank()) {
                    damageType = ResourceKey.create(Registries.DAMAGE_TYPE,
                            ResourceLocation.parse(parsed.damageType()));
                }
            }
            case AURA -> {
                effect = builtIn(BuiltInRegistries.MOB_EFFECT,
                        ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.parse(parameter)),
                        file, owner, "effect id");
                if (effect == null) {
                    return null;
                }
                // Blank is every creature but players; a #tag is matched live; one id is resolved here, so a
                // typo costs the row at load.
                String who = parsed.entity().strip();
                if (!who.isEmpty() && !who.startsWith("#")) {
                    creature = builtIn(BuiltInRegistries.ENTITY_TYPE,
                            ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(who)),
                            file, owner, "creature id");
                    if (creature == null) {
                        return null;
                    }
                }
            }
            case HEAL, KILL -> {
                // Nothing to resolve: heal reads amount, kill reads nothing.
            }
            case LIFE_STEAL -> {
                // Nothing to resolve: the whole class reads is amount.
            }
        }
        return new EffectEntry(parsed, effect, attribute, cure, creature, damageType);
    }

    /**
     * The effect rows for a variant as seen from one scope: biome, then tier, then global, first layer that
     * defines any, and the base mob's when the variant defines none in any layer.
     */
    public static List<EffectEntry> forVariant(String variant, String tier, String biome) {
        List<EffectEntry> hit = ScopeIndex.resolveOrBase(byVariant, variant,
                VariantHandler.baseOf(variant), tier, biome);
        return hit == null ? List.of() : hit;
    }

    /** A built-in registry id resolved, or null after naming it. */
    private static <T> Holder<T> builtIn(Registry<T> registry, ResourceKey<T> key,
                                         String file, String owner, String what) {
        Holder<T> hit = registry.getHolder(key).orElse(null);
        if (hit == null) {
            Strata.LOGGER.warn("{}: {}'s {} names nothing in the game - row skipped", file, owner, what);
        }
        return hit;
    }

    /** A spawn row's creature that is a variant: "variant:" and its name. */
    public static final String VARIANT_PREFIX = "variant:";

    /** The mob type a variant spawns as: its own when it has one, else its base mob's; null when unknown. */
    static String variantTypeId(String name) {
        var own = VariantEntityRegistry.getRegistered(name).flatMap(t -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(t));
        if (own.isPresent()) {
            return own.get().location().toString();
        }
        String base = VariantHandler.baseOf(name);
        return base.isEmpty() ? null : base;
    }
}
