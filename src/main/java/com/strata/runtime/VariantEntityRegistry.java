package com.strata.runtime;

import com.strata.data.Schema;
import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.Paths;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registers real EntityTypes for promoted variants (is_own_entity = true). */
public final class VariantEntityRegistry {

    private VariantEntityRegistry() {}

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Strata.MOD_ID);

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Strata.MOD_ID);

    /**
     * Each variant's type: a DeferredHolder for one registered at startup, a plain supplier for one
     * registered at world open (#registerNow).
     */
    private static final Map<String, java.util.function.Supplier<EntityType<?>>> REGISTERED =
            new LinkedHashMap<>();

    private static final Map<String, String> BASE_TYPES = new LinkedHashMap<>();

    /** The full entity IDs (strata:<name>) of all promoted variants, for fast lookup. */
    private static final java.util.Set<String> PROMOTED_ENTITY_IDS = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private static Field factoryField;

    private static Field lootTableField;

    /**
     * Scans variants.csv at mod construction time and registers EntityTypes for any variant with
     * is_own_entity = true.
     */
    public static void scanAndRegister() {
        List<Map<String, String>> rows = CsvTable.read(Paths.table(Schema.Variants.FILE));
        int count = 0;
        for (Map<String, String> row : rows) {
            String id = row.getOrDefault(Schema.Variants.NAME, "").strip();
            String base = row.getOrDefault(Schema.Variants.BASE_ENTITY, "").strip();
            // ONCE PER NAME: a variant has one row per scope, and a second registration of one name stops the
            // game loading.
            if (id.isEmpty() || base.isEmpty() || REGISTERED.containsKey(id)) continue;

            if (registerVariantType(id, base)) {
                count++;
            }
        }
        // The types of servers this game has joined, so it matches them after the one restart a new server
        // asks for.
        for (var entry : com.strata.data.ServerEntityCache.read().entrySet()) {
            if (!REGISTERED.containsKey(entry.getKey()) && registerVariantType(entry.getKey(), entry.getValue())) {
                count++;
            }
        }
        if (count > 0) {
            Strata.LOGGER.info("Registered {} promoted variant EntityType(s)", count);
        }
    }

    /** Every own-entity variant this game registered, id to base mob. */
    public static Map<String, String> registeredBases() {
        return Map.copyOf(BASE_TYPES);
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerVariantType(String variantId, String baseEntityId) {
        ResourceLocation baseId = ResourceLocation.tryParse(baseEntityId);
        if (baseId == null) {
            Strata.LOGGER.warn("Variant '{}': invalid base entity id '{}'", variantId, baseEntityId);
            return false;
        }

        // RS-7: ENTITY_TYPE is a DEFAULTED registry — a missing key returns minecraft:pig, never null.
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(baseId)) {
            Strata.LOGGER.warn("Variant '{}': base entity '{}' not found in registry "
                    + "(modded bases may not be available at mod construction time)",
                    variantId, baseEntityId);
            return false;
        }
        EntityType baseType = BuiltInRegistries.ENTITY_TYPE.get(baseId);

        EntityType.EntityFactory factory = extractFactory(baseType);
        if (factory == null) {
            Strata.LOGGER.error("Variant '{}': could not extract factory from {}", variantId, baseEntityId);
            return false;
        }

        MobCategory category = baseType.getCategory();
        EntityDimensions dims = baseType.getDimensions();
        boolean immune = baseType.fireImmune();

        String registryName = registryName(variantId);
        // A NAME THE REGISTRY CANNOT TAKE, OR TAKES TWICE, IS SKIPPED: an invalid id or a
        // second registration throws at mod construction, and the game does not start.
        if (registryName.isEmpty() || BASE_TYPES.keySet().stream().anyMatch(v -> registryName(v).equals(registryName))) {
            Strata.LOGGER.error("Variant '{}': its name gives the entity id '{}', which is empty or taken by "
                    + "another variant - it gets no entity of its own. Rename it.", variantId, registryName);
            return false;
        }

        DeferredHolder holder = ENTITY_TYPES.register(registryName,
                () -> buildType(factory, category, dims, immune, baseType, registryName));

        REGISTERED.put(variantId, holder);
        BASE_TYPES.put(variantId, baseEntityId);
        PROMOTED_ENTITY_IDS.add(Strata.MOD_ID + ":" + registryName);

        registerSpawnEgg(variantId, registryName, baseType, holder);

        return true;
    }

    /** A variant's type, built like its base mob: its size, tracking, blocks, and loot. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityType buildType(EntityType.EntityFactory factory, MobCategory category, EntityDimensions dims,
                                        boolean immune, EntityType baseType, String registryName) {
        // THE BASE MOB'S TRACKING AND BLOCKS TOO: the builder's defaults (tracking 5, update 3)
        // made a variant vanish nearer than its base mob.
        EntityType.Builder builder = EntityType.Builder.of(factory, category)
                .sized(dims.width(), dims.height())
                .clientTrackingRange(baseType.clientTrackingRange())
                .updateInterval(baseType.updateInterval());
        if (immune) {
            builder = builder.fireImmune();
        }
        if (baseType.canSpawnFarFromPlayer()) {
            builder = builder.canSpawnFarFromPlayer();
        }
        net.minecraft.world.level.block.Block[] immuneTo = immuneBlocks(baseType);
        if (immuneTo.length > 0) {
            builder = builder.immuneTo(immuneTo);
        }
        EntityType type = builder.build(Strata.MOD_ID + ":" + registryName);
        inheritLootTable(type, baseType);
        return type;
    }

    // ------------------------------------------------ registered at world open

    /** Run after #registerNow adds types: the client builds their renderers. */
    public static volatile Runnable afterRegisterNow = () -> { };

    /**
     * NO RESTART FOR A NEW VARIANT.
     *
     * @param wanted variant id to base mob id; ones already registered are skipped
     * @return the variants now registered
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static synchronized List<String> registerNow(Map<String, String> wanted) {
        List<String> added = new java.util.ArrayList<>();
        Map<String, String> todo = new LinkedHashMap<>();
        wanted.forEach((id, base) -> {
            if (id != null && !id.isBlank() && base != null && !base.isBlank() && !inRegistry(id.strip())) {
                todo.put(id.strip(), base.strip());
            }
        });
        if (todo.isEmpty()) {
            return added;
        }
        net.minecraft.core.MappedRegistry registry = (net.minecraft.core.MappedRegistry) BuiltInRegistries.ENTITY_TYPE;
        Map intrusive;
        try {
            registry.unfreeze();
            intrusive = openIntrusiveHolders(registry);
        } catch (RuntimeException | ReflectiveOperationException e) {
            Strata.LOGGER.error("Variants: cannot open the mob type list to add {} variant(s): {}", todo.size(), e.toString());
            registry.freeze();
            return added;
        }
        try {
            for (Map.Entry<String, String> e : todo.entrySet()) {
                String variantId = e.getKey();
                ResourceLocation baseId = ResourceLocation.tryParse(e.getValue());
                if (baseId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(baseId)) {
                    Strata.LOGGER.warn("Variant '{}': base mob '{}' not found - not registered", variantId, e.getValue());
                    continue;
                }
                String registryName = registryName(variantId);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, registryName);
                if (registryName.isEmpty() || BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    Strata.LOGGER.error("Variant '{}': its id '{}' is empty or taken - rename it", variantId, registryName);
                    continue;
                }
                EntityType base = BuiltInRegistries.ENTITY_TYPE.get(baseId);
                EntityType.EntityFactory factory = extractFactory(base);
                if (factory == null) {
                    continue;
                }
                try {
                    EntityType type = buildType(factory, base.getCategory(), base.getDimensions(), base.fireImmune(),
                            base, registryName);
                    net.minecraft.core.Holder.Reference holder =
                            net.minecraft.core.Registry.registerForHolder(registry, id, type);
                    bindTags(holder, base);
                    copyAttributesAndPlacement(type, base);
                    REGISTERED.put(variantId, () -> type);
                    BASE_TYPES.put(variantId, e.getValue());
                    PROMOTED_ENTITY_IDS.add(Strata.MOD_ID + ":" + registryName);
                    added.add(variantId);
                } catch (RuntimeException ex) {
                    Strata.LOGGER.error("Variant '{}': could not be registered: {}", variantId, ex.toString());
                }
            }
        } finally {
            // A type built and not registered would stop the list locking again.
            intrusive.clear();
            registry.freeze();
        }
        if (!added.isEmpty()) {
            Strata.LOGGER.info("Variants: registered {} as their own mobs without a restart: {}", added.size(), added);
            try {
                afterRegisterNow.run();
            } catch (RuntimeException e) {
                Strata.LOGGER.warn("Variants: could not prepare the new mobs' drawing: {}", e.toString());
            }
        }
        return added;
    }

    /**
     * True when the variant's own type is in the game's mob type list under its own id. Strata's record is not
     * enough: NeoForge puts the list back to its startup state when a world closes, which drops a type added
     * while the game ran, and the list answers an unknown type with its default mob (a pig). A variant whose
     * type was dropped is registered again.
     */
    static boolean inRegistry(String variantId) {
        var known = REGISTERED.get(variantId);
        if (known == null) {
            return false;
        }
        ResourceLocation own = ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, registryName(variantId));
        try {
            return own.equals(BuiltInRegistries.ENTITY_TYPE.getKey(known.get()));
        } catch (RuntimeException e) {
            // A startup type not bound yet.
            return false;
        }
    }

    /** Every variant in variants.csv, id to base mob, as #scanAndRegister reads them. */
    public static Map<String, String> variantsInTable() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.Variants.FILE))) {
            String id = row.getOrDefault(Schema.Variants.NAME, "").strip();
            String base = row.getOrDefault(Schema.Variants.BASE_ENTITY, "").strip();
            if (!id.isEmpty() && !base.isEmpty()) {
                out.putIfAbsent(id, base);
            }
        }
        return out;
    }

    /** Lets the locked list make holders for new types again: the map a lock empties. */
    @SuppressWarnings("rawtypes")
    private static Map openIntrusiveHolders(net.minecraft.core.MappedRegistry registry) throws ReflectiveOperationException {
        Field f = net.minecraft.core.MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        f.setAccessible(true);
        Map map = (Map) f.get(registry);
        if (map == null) {
            map = new java.util.IdentityHashMap();
            f.set(registry, map);
        }
        return map;
    }

    /** The base mob's tags, so a variant is undead, an illager, a raider, as its base mob is. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void bindTags(net.minecraft.core.Holder.Reference holder, EntityType base) {
        java.util.Collection tags;
        try {
            tags = base.builtInRegistryHolder().tags().toList();
        } catch (RuntimeException e) {
            tags = List.of();
        }
        try {
            java.lang.reflect.Method bind = net.minecraft.core.Holder.Reference.class
                    .getDeclaredMethod("bindTags", java.util.Collection.class);
            bind.setAccessible(true);
            bind.invoke(holder, tags);
        } catch (ReflectiveOperationException e) {
            Strata.LOGGER.warn("Variant {}: could not give it its base mob's tags: {}", holder.key(), e.toString());
        }
    }

    /** The base mob's attributes and spawn rules, as the startup events give them. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyAttributesAndPlacement(EntityType type, EntityType base) {
        try {
            Field attrs = net.neoforged.neoforge.common.CommonHooks.class.getDeclaredField("FORGE_ATTRIBUTES");
            attrs.setAccessible(true);
            if (DefaultAttributes.hasSupplier(base)) {
                ((Map) attrs.get(null)).put(type, DefaultAttributes.getSupplier(base));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Variant {}: could not copy its base mob's attributes: {}", type, e.toString());
        }
        try {
            Field data = net.minecraft.world.entity.SpawnPlacements.class.getDeclaredField("DATA_BY_TYPE");
            data.setAccessible(true);
            Map byType = (Map) data.get(null);
            Object baseData = byType.get(base);
            if (baseData != null) {
                byType.put(type, baseData);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Variant {}: could not copy its base mob's spawn rules: {}", type, e.toString());
        }
    }

    /**
     * The entity id path an own-entity variant registers under: its name, lower case, spaces to "_", every
     * character a registry id cannot hold left out.
     */
    public static String registryName(String variantId) {
        String lower = (variantId == null ? "" : variantId.strip()).toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '_');
        StringBuilder out = new StringBuilder();
        for (char c : lower.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final int DEFAULT_EGG_BG = 0x6B3FA0;
    private static final int DEFAULT_EGG_FG = 0xD4AF37;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerSpawnEgg(String variantId, String registryName,
            EntityType baseType, DeferredHolder holder) {
        int bg = DEFAULT_EGG_BG;
        int fg = DEFAULT_EGG_FG;
        SpawnEggItem baseEgg = SpawnEggItem.byId(baseType);
        if (baseEgg != null) {
            bg = baseEgg.getColor(0);
            fg = baseEgg.getColor(1);
        }
        int finalBg = bg;
        int finalFg = fg;
        ITEMS.register(registryName + "_spawn_egg",
                () -> new SpawnEggItem((EntityType) holder.get(),
                        finalBg, finalFg, new Item.Properties()) {
                    @Override
                    public Component getName(ItemStack stack) {
                        // NAMED AT RUNTIME, because it cannot be named at build time.
                        return Component.literal(
                                VariantEntityRegistry.displayNameOf(variantId)
                                        + " Spawn Egg");
                    }
                });
    }

    /**
     * A promoted variant's display name, or its id when the tables are not loaded yet (item names are asked
     * for on the client, which may reach this before a config load).
     */
    static String displayNameOf(String variantId) {
        var variant = com.strata.data.Config.tables().variants().get(variantId);
        if (variant == null || variant.displayName() == null
                || variant.displayName().isBlank()) {
            return variantId;
        }
        return variant.displayName();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> EntityType.EntityFactory<T> extractFactory(EntityType<T> type) {
        try {
            if (factoryField == null) {
                factoryField = EntityType.class.getDeclaredField("factory");
                factoryField.setAccessible(true);
            }
            return (EntityType.EntityFactory<T>) factoryField.get(type);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Strata.LOGGER.error("Reflection failed on EntityType.factory: {}", e.toString());
            return null;
        }
    }

    /**
     * Copies the base mob's default loot table key onto a promoted variant type so variants drop exactly what
     * their base drops.
     */
    @SuppressWarnings("rawtypes")
    private static void inheritLootTable(EntityType variant, EntityType base) {
        try {
            ResourceKey<LootTable> baseKey = base.getDefaultLootTable();
            if (baseKey == null) return;
            if (lootTableField == null) {
                lootTableField = EntityType.class.getDeclaredField("lootTable");
                lootTableField.setAccessible(true);
            }
            lootTableField.set(variant, baseKey);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Strata.LOGGER.error("Variant loot-table inheritance failed for {}: {}",
                    variant.getDescriptionId(), e.toString());
        }
    }

    /** The blocks the base mob takes no harm from (a wither skeleton's wither rose), or none. */
    @SuppressWarnings("unchecked")
    private static net.minecraft.world.level.block.Block[] immuneBlocks(EntityType<?> base) {
        try {
            Field f = EntityType.class.getDeclaredField("immuneTo");
            f.setAccessible(true);
            return ((java.util.Set<net.minecraft.world.level.block.Block>) f.get(base))
                    .toArray(new net.minecraft.world.level.block.Block[0]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Strata.LOGGER.warn("Could not read the blocks {} is immune to: {}", base, e.toString());
            return new net.minecraft.world.level.block.Block[0];
        }
    }

    /** GIVES EACH OWN-ENTITY VARIANT ITS BASE MOB'S SPAWN RULES. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegisterSpawnPlacements(
            net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        for (var entry : REGISTERED.entrySet()) {
            ResourceLocation baseId = ResourceLocation.tryParse(BASE_TYPES.getOrDefault(entry.getKey(), ""));
            if (baseId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(baseId)) {
                continue;
            }
            EntityType base = BuiltInRegistries.ENTITY_TYPE.get(baseId);
            if (!net.minecraft.world.entity.SpawnPlacements.hasPlacement(base)) {
                continue;
            }
            event.register((EntityType) entry.getValue().get(),
                    net.minecraft.world.entity.SpawnPlacements.getPlacementType(base),
                    net.minecraft.world.entity.SpawnPlacements.getHeightmapType(base),
                    (type, level, spawnType, pos, random) -> net.minecraft.world.entity.SpawnPlacements
                            .checkSpawnRules(base, level, spawnType, pos, random),
                    net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    /** Copies the base mob's attributes to each registered promoted variant. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        for (var entry : REGISTERED.entrySet()) {
            String variantId = entry.getKey();
            String baseEntityId = BASE_TYPES.get(variantId);
            // This runs on the MOD bus during startup: anything thrown here kills mod loading for every mod,
            // so each step degrades instead.
            ResourceLocation baseId = baseEntityId == null
                    ? null : ResourceLocation.tryParse(baseEntityId);
            if (baseId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(baseId)) {
                Strata.LOGGER.warn("Variant '{}': cannot copy attributes, base entity "
                        + "'{}' is missing - the variant keeps vanilla defaults",
                        variantId, baseEntityId);
                continue;
            }
            EntityType baseType = BuiltInRegistries.ENTITY_TYPE.get(baseId);

            try {
                // getSupplier THROWS for a type with no registered attributes (a non-living base, or a mod
                // whose supplier is absent).
                AttributeSupplier baseAttrs = DefaultAttributes.getSupplier(
                        (EntityType) baseType);
                event.put((EntityType) entry.getValue().get(), baseAttrs);
            } catch (Exception e) {
                Strata.LOGGER.warn("Variant '{}': base entity '{}' has no default "
                        + "attributes - skipping ({})", variantId, baseEntityId, e.toString());
            }
        }
    }

    /** True if a variant with this id has a registered EntityType. */
    public static boolean isPromoted(String variantId) {
        return inRegistry(variantId);
    }

    /** The registered EntityType for a promoted variant, if any. */
    public static Optional<EntityType<?>> getRegistered(String variantId) {
        java.util.function.Supplier<EntityType<?>> h = REGISTERED.get(variantId);
        return h != null && inRegistry(variantId) ? Optional.of(h.get()) : Optional.empty();
    }

    /** All registered promoted variant types, keyed by variant id. */
    public static Map<String, EntityType<?>> allRegistered() {
        Map<String, EntityType<?>> out = new LinkedHashMap<>();
        for (var entry : REGISTERED.entrySet()) {
            out.put(entry.getKey(), entry.getValue().get());
        }
        return Collections.unmodifiableMap(out);
    }

    /** The base entity id for a promoted variant, or empty. */
    public static Optional<String> baseEntityId(String variantId) {
        return Optional.ofNullable(BASE_TYPES.get(variantId));
    }

    /** True if this EntityType is a registered promoted variant. */
    public static boolean isPromotedType(EntityType<?> type) {
        for (java.util.function.Supplier<EntityType<?>> h : REGISTERED.values()) {
            if (h.get() == type) return true;
        }
        return false;
    }

    /** True if this full entity ID (e.g. "strata:big_guy") is a promoted variant. */
    public static boolean isPromotedEntityId(String entityId) {
        return entityId != null && PROMOTED_ENTITY_IDS.contains(entityId);
    }

    /** Test-only: add a promoted entity ID for testing. */
    public static void testAddPromotedEntityId(String entityId) {
        PROMOTED_ENTITY_IDS.add(entityId);
    }

    /** Test-only: remove a promoted entity ID for testing. */
    public static void testRemovePromotedEntityId(String entityId) {
        PROMOTED_ENTITY_IDS.remove(entityId);
    }

    /** The base entity id for a promoted variant EntityType, or empty. */
    public static Optional<String> baseEntityIdForType(EntityType<?> type) {
        for (var entry : REGISTERED.entrySet()) {
            if (entry.getValue().get() == type) {
                return Optional.ofNullable(BASE_TYPES.get(entry.getKey()));
            }
        }
        return Optional.empty();
    }

    /** The variant id for a promoted variant EntityType, or empty. */
    public static Optional<String> variantIdForType(EntityType<?> type) {
        for (var entry : REGISTERED.entrySet()) {
            if (entry.getValue().get() == type) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}
