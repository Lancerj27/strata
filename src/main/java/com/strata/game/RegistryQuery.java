package com.strata.game;

import com.strata.Strata;
import com.strata.util.Labels;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Single mechanism for enumerating any registry into a list of selectable options. */
public final class RegistryQuery {

    private RegistryQuery() {}

    /** One selectable option from a registry enumeration. */
    public record Option(String id, String displayName, String namespace) {}

    // -- built-in caches (immutable after game load) --------------------------
    private static List<Option> entityCache;
    private static List<Option> itemCache;
    private static List<Option> attributeCache;

    // -- datapack caches (keyed on RegistryAccess identity) -------------------
    private static RegistryAccess dataCachedFor;
    private static List<Option> structureCache;
    private static List<ResourceLocation> biomeCache;

    // -- damage type ids (the loot death-cause values; cached like the above) --
    private static RegistryAccess damageTypeCacheFor;
    private static List<String> damageTypeIdCache;

    private static void ensureDataCache(RegistryAccess registries) {
        if (dataCachedFor != registries) {
            dataCachedFor = registries;
            structureCache = null;
            biomeCache = null;
        }
    }

    // -- generic builder ------------------------------------------------------

    /** Enumerates registry keys into Options using the supplied labeler for display names. */
    public static List<Option> options(Iterable<ResourceLocation> keys,
                                       Function<String, String> labeler) {
        List<Option> opts = new ArrayList<>();
        for (ResourceLocation id : keys) {
            opts.add(new Option(id.toString(), labeler.apply(id.toString()),
                    id.getNamespace()));
        }
        return List.copyOf(opts);
    }

    // -- convenience factories (cached) ---------------------------------------

    /** All entity types from the built-in registry. */
    public static List<Option> entities() {
        if (entityCache != null) return entityCache;
        entityCache = options(BuiltInRegistries.ENTITY_TYPE.keySet(), Labels::creature);
        return entityCache;
    }

    /**
     * The living mobs, for a mob's model (the Model tab): the mobs the game layer read a model for at game
     * start, which leaves out arrows, boats, and the rest that cannot be drawn as a mob.
     */
    public static List<Option> livingMobs() {
        java.util.Set<String> living = new java.util.HashSet<>();
        for (com.strata.data.GameLayer.Row r : com.strata.data.GameLayer.read(
                com.strata.data.GameLayer.dir().resolve(com.strata.data.GameLayer.MOBS))) {
            if (com.strata.data.GameLayer.MODEL.equals(r.group()) && r.key().equals("model")) {
                living.add(r.entity());
            }
        }
        if (living.isEmpty()) {
            return entities();
        }
        return entities().stream().filter(o -> living.contains(o.id())).toList();
    }

    /** The group of the custom textures and sounds in a picker. */
    public static final String CUSTOM_GROUP = "custom";

    /** The id of the picker entry that opens the import screen ("+ Add custom..."). */
    public static final String ADD_CUSTOM = "__add_custom__";

    /** The group of that entry, first in the picker. */
    public static final String ADD_GROUP = "add";

    /** Every sound the game has, then the custom ones, for a mob's sounds (the Model tab). */
    public static List<Option> sounds() {
        List<Option> out = new ArrayList<>(options(BuiltInRegistries.SOUND_EVENT.keySet(),
                id -> id.substring(id.indexOf(':') + 1)));
        out.addAll(customOptions(com.strata.domain.CustomAssetsOps.SOUND));
        return out;
    }

    /** The custom assets of a kind as options, "Custom · name", in the group {@link #CUSTOM_GROUP}. */
    private static List<Option> customOptions(String kind) {
        List<Option> out = new ArrayList<>();
        for (var a : com.strata.domain.CustomAssetsOps.ofKind(com.strata.data.CustomAssetsPack.activeRoot(), kind)) {
            out.add(new Option(a.resource(), a.label(), CUSTOM_GROUP));
        }
        return out;
    }

    /** The picker entry that opens the import screen, for a sound or texture picker. */
    public static Option addCustomOption() {
        return new Option(ADD_CUSTOM, "+ Add custom...", ADD_GROUP);
    }

    /**
     * Every mob's own texture, for a mob's texture (the Model tab), each named by the mob it belongs to; read
     * by the client at start (game/textures.csv).
     */
    public static List<Option> mobTextures() {
        java.util.Map<String, String> byTexture = new java.util.TreeMap<>();
        for (com.strata.data.GameLayer.Row r : com.strata.data.GameLayer.read(
                com.strata.data.GameLayer.dir().resolve(com.strata.data.GameLayer.TEXTURES))) {
            byTexture.putIfAbsent(r.value(), Labels.creature(r.entity()));
        }
        List<Option> out = new ArrayList<>();
        byTexture.forEach((texture, mob) -> {
            ResourceLocation id = ResourceLocation.tryParse(texture);
            out.add(new Option(texture, mob + "  ·  " + texture, id == null ? "" : id.getNamespace()));
        });
        out.addAll(customOptions(com.strata.domain.CustomAssetsOps.TEXTURE));
        return out;
    }

    /** All items from the built-in registry. */
    public static List<Option> items() {
        if (itemCache != null) return itemCache;
        itemCache = options(BuiltInRegistries.ITEM.keySet(), Labels::itemName);
        return itemCache;
    }

    /** Every block the game and its mods have, for a block variant's base block. */
    public static List<Option> blocks() {
        if (blockCache != null) return blockCache;
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!com.strata.Strata.MOD_ID.equals(id.getNamespace())) ids.add(id);
        }
        blockCache = options(ids, com.strata.util.Labels::blockName);
        return blockCache;
    }

    private static List<Option> blockCache;

    /**
     * The designer's item variants, then every item: the picker for a place that makes a stack (equipment,
     * loot, chest loot, trades).
     */
    public static List<Option> itemsAndVariants() {
        List<Option> out = new ArrayList<>();
        for (var v : com.strata.domain.ItemVariantOps.variants()) {
            out.add(new Option(com.strata.model.ItemVariant.ref(v.name()),
                    v.name() + " (" + Labels.itemName(v.baseItem()) + ")", "Item variants"));
        }
        out.addAll(items());
        return out;
    }

    /** Every enchantment of the open world, the game's and every mod's; none with no world. */
    public static List<Option> enchantments(RegistryAccess registries) {
        if (registries == null) return List.of();
        return registries.registry(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .map(r -> options(r.keySet(), id -> Labels.pretty(ResourceLocation.tryParse(id))))
                .orElse(List.of());
    }

    /** All attributes from the built-in registry. */
    public static List<Option> attributes() {
        if (attributeCache != null) return attributeCache;
        List<ResourceLocation> keys = new ArrayList<>();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(h -> keys.add(h.key().location()));
        attributeCache = options(keys, Labels::attribute);
        return attributeCache;
    }

    /** Every particle that needs no settings, the game's and mods'. */
    public static List<Option> particles() {
        List<ResourceLocation> keys = new ArrayList<>();
        BuiltInRegistries.PARTICLE_TYPE.entrySet().forEach(e -> {
            if (e.getValue() instanceof net.minecraft.core.particles.SimpleParticleType) {
                keys.add(e.getKey().location());
            }
        });
        return options(keys, id -> Labels.pretty(ResourceLocation.tryParse(id)));
    }

    /** Every projectile the game and its mods register (a mob fires another's attack). */
    public static List<Option> projectiles(net.minecraft.world.level.Level level) {
        List<ResourceLocation> keys = new ArrayList<>();
        for (var e : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            if (e.getValue().getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
                continue;
            }
            if (level != null) {
                try {
                    var made = e.getValue().create(level);
                    boolean shot = made instanceof net.minecraft.world.entity.projectile.Projectile;
                    if (made != null) {
                        made.discard();
                    }
                    if (!shot) {
                        continue;
                    }
                } catch (RuntimeException | LinkageError ex) {
                    continue;
                }
            }
            keys.add(e.getKey().location());
        }
        // The designer's own first, as strata:name, under their own namespace.
        List<Option> out = new ArrayList<>();
        for (com.strata.model.ProjectilePreset p : com.strata.domain.ProjectileOps.all()) {
            out.add(new Option(p.id(), Labels.pretty(ResourceLocation.tryParse(p.id())), com.strata.model.ProjectilePreset.NAMESPACE));
        }
        out.addAll(options(keys, id -> Labels.pretty(ResourceLocation.tryParse(id))));
        return out;
    }

    /** Every mob effect from the built-in registry (an ability's parameter). */
    public static List<Option> effects() {
        return options(BuiltInRegistries.MOB_EFFECT.keySet(), id -> Labels.pretty(ResourceLocation.tryParse(id)));
    }

    /**
     * Every damage type, and every damage type tag as "#" and its id (damage immunity's parameter).
     */
    public static List<Option> damageTypesFor(RegistryAccess worldRegistries) {
        RegistryAccess registries = worldRegistries != null
                ? worldRegistries : com.strata.data.Config.boundRegistries();
        if (registries == null) {
            return List.of();
        }
        List<Option> out = new ArrayList<>();
        try {
            var lookup = registries.lookupOrThrow(Registries.DAMAGE_TYPE);
            lookup.listElementIds().forEach(k -> out.add(new Option(k.location().toString(),
                    Labels.pretty(k.location()), k.location().getNamespace())));
            lookup.listTagIds().forEach(t -> out.add(new Option("#" + t.location(),
                    "#" + t.location().getPath(), "tag")));
        } catch (Exception e) {
            Strata.LOGGER.error("Could not list damage types: {}", e.toString());
        }
        return List.copyOf(out);
    }

    /** The damage type ids the game has (vanilla and modded), for the loot death-cause value. */
    public static List<String> damageTypeIds() {
        RegistryAccess registries = com.strata.data.Config.boundRegistries();
        if (registries != damageTypeCacheFor) {
            damageTypeCacheFor = registries;
            damageTypeIdCache = null;
        }
        if (damageTypeIdCache != null) {
            return damageTypeIdCache;
        }
        List<String> out = new ArrayList<>();
        if (registries != null) {
            try {
                registries.lookupOrThrow(Registries.DAMAGE_TYPE).listElementIds()
                        .forEach(k -> out.add(k.location().toString()));
            } catch (Exception e) {
                Strata.LOGGER.error("Could not list damage types: {}", e.toString());
            }
            out.sort(Comparator.naturalOrder());
        }
        damageTypeIdCache = List.copyOf(out);
        return damageTypeIdCache;
    }

    /**
     * The structures a picker can offer: from the open world's registries when there is a world, else from
     * the startup pack-built ones.
     */
    public static List<Option> structuresFor(RegistryAccess worldRegistries) {
        RegistryAccess registries = worldRegistries != null
                ? worldRegistries : com.strata.data.Config.boundRegistries();
        return registries == null ? List.of() : structures(registries);
    }

    /** All structures from the live datapack registry. */
    public static List<Option> structures(RegistryAccess registries) {
        ensureDataCache(registries);
        if (structureCache != null) return structureCache;
        List<ResourceLocation> keys = new ArrayList<>();
        try {
            registries.lookupOrThrow(Registries.STRUCTURE).listElementIds()
                    .forEach(k -> keys.add(k.location()));
        } catch (Exception e) {
            Strata.LOGGER.error("Could not list structures: {}", e.toString());
        }
        structureCache = options(keys, id -> Labels.pretty(ResourceLocation.tryParse(id)));
        return structureCache;
    }

    /** All known ore names (vanilla + modded registrations via OreDefinitionRegistry). */
    public static List<Option> ores() {
        List<Option> opts = new ArrayList<>();
        for (String name : OreDetector.knownOreNames()) {
            String ns = OreDetector.placedFeaturesFor(name)
                    .filter(features -> !features.isEmpty())
                    .map(features -> features.getFirst().getNamespace())
                    .orElse("minecraft");
            opts.add(new Option(name, Labels.pretty(name), ns));
        }
        opts.sort(Comparator.comparing(Option::displayName));
        return List.copyOf(opts);
    }

    /**
     * The picker's options for a kind of registry value (ValueType.Registry): the list a searchable picker
     * shows.
     */
    public static List<Option> forKind(com.strata.model.ValueType.Registry kind, RegistryAccess worldRegistries) {
        return switch (kind) {
            case ITEM -> items();
            case BLOCK -> blocks();
            case ENTITY -> entities();
            case LIVING_MOB -> livingMobs();
            case EFFECT -> effects();
            case ATTRIBUTE -> attributes();
            case PARTICLE -> particles();
            case SOUND -> sounds();
            case TEXTURE -> mobTextures();
            case ENCHANTMENT -> enchantments(worldRegistries != null ? worldRegistries
                    : com.strata.data.Config.boundRegistries());
            case DAMAGE_TYPE -> damageTypesFor(worldRegistries);
            case STRUCTURE -> structuresFor(worldRegistries);
            case BIOME -> {
                RegistryAccess regs = worldRegistries != null ? worldRegistries : com.strata.data.Config.boundRegistries();
                yield regs == null ? List.of() : options(biomes(regs), id -> Labels.pretty(ResourceLocation.tryParse(id)));
            }
        };
    }

    /** The question a picker of this kind asks in its header ("Pick an item"). */
    public static String question(com.strata.model.ValueType.Registry kind) {
        return switch (kind) {
            case ITEM -> "Pick an item";
            case BLOCK -> "Pick a block";
            case ENTITY -> "Pick an entity";
            case LIVING_MOB -> "Pick a mob";
            case EFFECT -> "Pick an effect";
            case ATTRIBUTE -> "Pick an attribute";
            case PARTICLE -> "Pick a particle";
            case SOUND -> "Pick a sound";
            case TEXTURE -> "Pick a texture";
            case ENCHANTMENT -> "Pick an enchantment";
            case DAMAGE_TYPE -> "Pick a damage type";
            case STRUCTURE -> "Pick a structure";
            case BIOME -> "Pick a biome";
        };
    }

    /** Every biome the loaded datapacks define, sorted. */
    public static List<ResourceLocation> biomes(RegistryAccess registries) {
        ensureDataCache(registries);
        if (biomeCache != null) return biomeCache;
        List<ResourceLocation> out = new ArrayList<>();
        try {
            registries.lookupOrThrow(Registries.BIOME).listElementIds()
                    .forEach(key -> out.add(key.location()));
            out.sort(java.util.Comparator.comparing(ResourceLocation::toString));
        } catch (Exception e) {
            Strata.LOGGER.error("Could not list biomes: {}", e.toString());
        }
        biomeCache = List.copyOf(out);
        return biomeCache;
    }
}

