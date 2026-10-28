package com.strata.data;

import com.strata.Strata;
import com.strata.game.GameFactsAdapter;
import com.strata.model.Resolution;
import com.strata.model.Resolver;
import com.strata.model.Variant;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.strata.runtime.EntityBaseAttributes;
import com.strata.runtime.MobAttributeScaling;
import com.strata.runtime.MobLevel;
import com.strata.runtime.VariantHandler;
import com.strata.runtime.MobXpCurve;
import com.strata.runtime.Scaling;
import com.strata.runtime.MobAbilities;
import com.strata.runtime.MobEquipment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The loaded configuration, and the resolver built from it. */
public final class Config {

    private Config() {}

    private static volatile Tables.Loaded loaded = Tables.Loaded.EMPTY;
    private static volatile Resolver resolver;
    private static volatile RegistryAccess boundTo;
    private static volatile MinecraftServer server;
    /** The resolutions by biome. */
    private static volatile Map<String, Resolution> cache = new ConcurrentHashMap<>();

    /** THE RELOAD ENTRY POINT: at launch, at a world start, at a profile load, and for /strata reload. */
    public static void reload() {
        reload(true);
    }

    /**
     * After an editor save (Commit): the tables are on the current schema already, and Commit keeps the
     * profile, so neither the migrations nor the profile sync run again.
     */
    public static void reloadAfterEdit() {
        reload(false);
    }

    private static void reload(boolean launch) {
        // BEFORE the tables are read, so the first load after an update is already on the current schema.
        if (launch) {
            EditHistory.whileRestoring(Migrations::run);
        }
        loaded = Tables.loadAll();
        resolver = null;
        cache = new ConcurrentHashMap<>();

        loadIndex(() -> Scaling.reload(), "mob/player scaling curves");
        loadIndex(() -> EntityBaseAttributes.load(Paths.table(Schema.MobBaseStats.FILE)),
                "entity base attributes");
        loadIndex(() -> MobAbilities.load(Paths.table(Schema.Abilities.FILE)),
                "variant abilities");
        // The six behaviour classes, read out of the SAME file and ignoring the nine's rows, as the nine now
        // ignore these.
        loadIndex(() -> com.strata.runtime.EffectTable.load(Paths.table(Schema.Abilities.FILE)),
                "variant effect rows");
        // The designer's item variants.
        loadIndex(com.strata.runtime.ItemVariants::load, "item variants");
        loadIndex(com.strata.runtime.ItemVariantEffects::load, "item variant effects");
        // The designer's projectiles and their abilities.
        loadIndex(com.strata.runtime.Projectiles::load, "projectiles");
        // Block variant abilities.
        loadIndex(com.strata.runtime.BlockVariantEffects::load, "block variant abilities");
        // The user's block stats, read by the block mixins.
        loadIndex(com.strata.runtime.BlockStatsRuntime::load, "block stats");
        // BEFORE the choices, which are checked against it.
        loadIndex(() -> com.strata.runtime.GoalsRuntime.useGoalSets(
                GoalSetTable.load(Paths.table(Schema.GoalSets.FILE))), "goal sets");
        loadIndex(() -> com.strata.runtime.GoalsRuntime.loadChoices(Paths.table(Schema.MobGoalSets.FILE)),
                "goal set choices");
        loadIndex(() -> com.strata.runtime.GoalsRuntime.load(Paths.table(Schema.MobGoals.FILE)), "mob goal lists");
        // The mob families from the game layer: a family's mobs run its archetype once it is edited.
        loadIndex(() -> com.strata.runtime.GoalsRuntime.useFamilies(com.strata.domain.GoalFamilies.fromGameLayer()),
                "mob families");
        loadIndex(() -> com.strata.runtime.DefaultEquipmentRuntime.load(GameLayer.dir().resolve(GameLayer.EQUIPMENT)),
                "default equipment rolls");
        // The edits over the extracted mob values: type values are set on the entity types here, fields on
        // each mob at its join.
        loadIndexOnServer(() -> com.strata.runtime.MobDataRuntime.load(Paths.table(Schema.MobData.FILE)),
                "mob data");
        // The user's biome values, put on the biomes as a world loads.
        loadIndex(() -> com.strata.runtime.BiomeValues.load(Paths.table(Schema.BiomeData.FILE)), "biome values");
        // The user's mob caps over the game's.
        loadIndexOnServer(() -> com.strata.runtime.MobCapRuntime.load(Paths.table(Schema.MobCaps.FILE)), "mob caps");
        // Goal targets: entity id to class, from the game layer.
        loadIndex(() -> com.strata.runtime.GoalTargets.load(GameLayer.read(GameLayer.dir().resolve(GameLayer.MOBS))),
                "goal targets");
        loadIndex(() -> MobEquipment.load(Paths.table(Schema.Equipment.FILE)),
                "variant equipment");
        // Whether a Replace biome blocks the spawns not from its list.
        loadIndex(() -> com.strata.runtime.OtherSpawns.load(Paths.table(Schema.Tiers.FILE),
                Paths.table(Schema.BiomeOverrides.FILE)), "other-spawn blocks");
        // A tier's and a biome's spawn rules, after the old columns they take over.
        loadIndex(() -> com.strata.runtime.AreaRules.load(Paths.table(Schema.AreaRules.FILE)), "area rules");
        // Disabled blocks and items.
        loadIndex(com.strata.runtime.DisabledContent::load, "disabled blocks and items");
        // Mob replacements.
        loadIndex(() -> com.strata.runtime.Replacements.load(Paths.table(Schema.Replacements.FILE)), "mob replacements");
        // Block replacements in new chunks.
        loadIndex(() -> com.strata.runtime.BlockSwapRuntime.load(Paths.table(Schema.BlockReplacements.FILE)),
                "block replacements");
        // Each mob's own scaling switch.
        loadIndex(() -> com.strata.runtime.MobScalingSwitch.load(Paths.table(Schema.Variants.FILE)),
                "mob scaling switches");
        // Each mob's own mob griefing switch.
        loadIndex(() -> com.strata.runtime.MobGriefingSwitch.load(Paths.table(Schema.Variants.FILE)),
                "mob griefing switches");
        // Villager and wandering trader trade edits.
        loadIndex(() -> com.strata.runtime.TradesRuntime.load(Paths.table(Schema.Trades.FILE)), "trade edits");
        loadIndex(() -> com.strata.runtime.SpecialSpawnsRuntime.load(Paths.table(Schema.SpecialSpawns.FILE)),
                "special spawner values");
        loadIndex(() -> VariantHandler.indexTextures(loaded.variants().values().stream().toList()),
                "variant textures");
        // The eight property tables, by layer, BEFORE the models read them.
        loadIndex(() -> com.strata.runtime.MobProperties.load(loaded.properties()), "mob properties");
        // Each mob's model, texture, and sounds, over the game's.
        loadIndex(() -> com.strata.runtime.MobModels.load(Paths.table(Schema.MobData.FILE),
                loaded.variants().values()), "mob models");

        Strata.LOGGER.info("Strata configuration reloaded");

        // Entity data is server-entity state.
        MinecraftServer srv = server;
        if (srv == null) {
            reapplyToLoadedEntities();
        } else if (srv.isSameThread()) {
            reapplyToLoadedEntities();
        } else {
            srv.execute(() -> reapplyToLoadedEntities());
        }

        // A change to the block rules (a disabled block, a replacement rule) with a world open re-arms the
        // existing-land sweep at once.
        MinecraftServer blockSrv = server;
        if (blockSrv == null || !blockSrv.isRunning() || blockSrv.isSameThread()) {
            com.strata.runtime.Regeneration.rearmBlockRules(blockSrv);
        } else {
            blockSrv.execute(() -> com.strata.runtime.Regeneration.rearmBlockRules(blockSrv));
        }

        // The generated datapack is derived output of this exact config, so it is rebuilt on every reload,
        // live or not.
        StrataDatapack.regenerate();
        // A reload with a world open sets the profile's game rules on it.
        com.strata.runtime.GameRulesData.apply(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer());
        // Strata's own upgrades at launch (new settings, migrated columns) are not designer edits.
        if (launch) {
            ProfileManager.syncAfterLaunch();
        }
    }

    /**
     * Runs one runtime index rebuild and logs a FAILURE rather than letting an exception abort the remaining
     * indexes.
     */
    private static void loadIndex(Runnable loader, String name) {
        try {
            loader.run();
        } catch (Exception e) {
            Strata.LOGGER.error("Could not rebuild the {} index: {}", name, e.toString(), e);
        }
    }

    /**
     * {@link #loadIndex} on the server thread when a running server is on another thread; at once otherwise.
     */
    private static void loadIndexOnServer(Runnable loader, String name) {
        MinecraftServer srv = server;
        // A stopped server runs no more tasks: after a world closes, at once.
        if (srv == null || !srv.isRunning() || srv.isSameThread()) {
            loadIndex(loader, name);
        } else {
            srv.execute(() -> loadIndex(loader, name));
        }
    }

    private static void reapplyToLoadedEntities() {
        MinecraftServer srv = server;
        if (srv == null) return;

        int count = 0;
        for (ServerLevel level : srv.getAllLevels()) {
            for (Entity raw : level.getAllEntities()) {
                if (!(raw instanceof LivingEntity living)) continue;
                if (living instanceof net.minecraft.world.entity.player.Player) continue;
                if (reapply(living, level)) {
                    count++;
                }
            }
        }
        if (count > 0) {
            Strata.LOGGER.info("Reapplied attributes to {} loaded entities", count);
        }
        // The players' base stats, at once after an edit.
        com.strata.runtime.PlayerBaseStats.applyOnline(srv);
        // Each mob's property values, to the players who see it, where they changed.
        com.strata.runtime.MobProperties.resend(srv);
    }

    /**
     * Re-applies the current config's stats to one mob: base attributes, variant multipliers, scaling,
     * variant absolutes.
     */
    public static boolean reapply(LivingEntity living, ServerLevel level) {
        try {
            EntityBaseAttributes.apply(living);

            // A plain mob that spawned before its default variant existed gets it now, so its abilities reach
            // it.
            if (living instanceof net.minecraft.world.entity.Mob mob) {
                MobEquipment.tagPlain(mob);
            }
            String variantId = living.getExistingData(MobLevel.VARIANT_ID).orElse("");
            Variant variant = variantId.isEmpty() ? null : loaded.variants().get(variantId);

            // A plain mob's extras variant has no stats and no name to re-apply.
            if (variant != null && !variant.isPlainDefault()) {
                VariantHandler.resetMultipliers(living, variant);
                VariantHandler.applyMultipliers(living, variant);
            }

            // The toggle gates SCALING on the reload path too (user decision).
            String scope = living.getExistingData(MobLevel.VARIANT_SCOPE).orElse("");
            String switchOwner = !variantId.isEmpty() ? variantId
                    : Variant.defaultNameFor(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getResourceKey(living.getType()).map(k -> k.location().toString()).orElse(""));
            Boolean own = com.strata.runtime.MobScalingSwitch.own(switchOwner, VariantHandler.baseOf(switchOwner),
                    com.strata.runtime.ScopeIndex.tierOf(scope), com.strata.runtime.ScopeIndex.biomeOf(scope));
            boolean scales = own != null ? own : StrataConfig.ENABLE_MOB_SCALING.get();
            MobLevel mobLevel = scales
                    ? living.getExistingData(MobLevel.MOB_LEVEL).orElse(null)
                    : null;
            if (mobLevel != null && mobLevel.isSet()) {
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome =
                        level.getBiome(living.blockPosition());
                Resolution resolution = resolve(
                        biome.unwrapKey().map(key -> key.location()).orElse(null));
                Double stored = living.getExistingData(MobLevel.COEFFICIENT).orElse(null);
                double coefficient = stored == null ? resolution.coefficient() : stored;
                MobAttributeScaling.apply(living, mobLevel.level(), resolution, coefficient, own);
            }

            if (variant != null) {
                VariantHandler.applyAbsolutes(living, variant);
            }
            VariantHandler.refreshHealth(living);
            // The edited fields, so a save reaches mobs already loaded.
            com.strata.runtime.MobDataRuntime.apply(living);
            // The behavior too, so an edited AI reaches mobs already loaded.
            if (living instanceof net.minecraft.world.entity.Mob mob) {
                com.strata.runtime.MobEquipment.reapplyGoalSet(mob);
                // Its property values (MobPropertyEffects), so a save reaches it too.
                com.strata.runtime.MobPropertyEffects.apply(mob);
            }
            return true;
        } catch (Exception e) {
            Strata.LOGGER.error("Could not reapply strata data to entity {} at {}: {}",
                    living.getId(), living.blockPosition(), e.toString(), e);
            return false;
        }
    }

    public static Tables.Loaded tables() {
        return loaded;
    }

    public static MobXpCurve xpCurve() {
        return new MobXpCurve(
                StrataConfig.MOB_XP_PER_LEVEL.get(),
                StrataConfig.MOB_XP_EXPONENT.get());
    }

    /** The registries built at game start (WorldgenRegistryBuilder), bound again when a world closes. */
    private static volatile RegistryAccess startup;

    public static void bind(RegistryAccess registries, MinecraftServer srv) {
        if (srv == null) {
            startup = registries;
        }
        server = srv;
        if (boundTo != registries) {
            boundTo = registries;
            resolver = null;
            cache = new ConcurrentHashMap<>();
        }
    }

    /** A WORLD CLOSED: the startup registries again, no server, and no resolution kept. */
    public static void serverStopped() {
        server = null;
        boundTo = startup;
        resolver = null;
        cache = new ConcurrentHashMap<>();
    }

    /** The registries built at game start, or null when that build failed. */
    public static RegistryAccess startupRegistries() {
        return startup;
    }

    /** The server bound now, or null (a world is not open). */
    public static MinecraftServer boundServer() {
        return server;
    }

    /**
     * The RegistryAccess currently bound — the startup pack-built worldgen registries or a live server's — or
     * null before anything is bound.
     */
    public static RegistryAccess boundRegistries() {
        return boundTo;
    }

    public static Resolution resolve(ResourceLocation biome) {
        return biome == null ? Resolution.untouched("", null) : resolve(biome.toString());
    }

    public static Resolution resolve(String biome) {
        if (biome == null || biome.isEmpty()) {
            return Resolution.untouched(biome == null ? "" : biome, null);
        }
        // The map first, then the resolver: a reload nulls the resolver before it replaces the map, so a map
        // read now is never older than its resolver.
        Map<String, Resolution> current = cache;
        Resolver local = resolverOrNull();
        if (local == null) {
            return Resolution.untouched(biome, null);
        }
        return current.computeIfAbsent(biome, local::resolve);
    }

    public static List<com.strata.model.Spawn> declaredSpawns(
            String biome, com.strata.model.SpawnCategory category) {
        Resolver local = resolverOrNull();
        return local == null ? List.of() : local.declaredSpawns(biome, category);
    }

    /** True when the biome's tier replaces the game's spawns. */
    public static boolean replacesGameSpawns(String biome) {
        Resolver local = resolverOrNull();
        return local != null && local.replacesGame(biome);
    }

    /** The resolver when one exists, else null. */
    public static Resolver resolver() {
        return resolverOrNull();
    }

    private static Resolver resolverOrNull() {
        RegistryAccess registries = boundTo;
        if (registries == null) return null;
        Resolver local = resolver;
        if (local == null) {
            synchronized (Config.class) {
                if (resolver == null) {
resolver = new Resolver(new GameFactsAdapter(registries),
                        loaded.tiers(), loaded.biomes(), loaded.variants(),
                        loaded.globalLoot(), loaded.tierLoot(), loaded.globalCoefficient(),
                        loaded.globalSpawns());
                }
                local = resolver;
            }
        }
        return local;
    }
}
