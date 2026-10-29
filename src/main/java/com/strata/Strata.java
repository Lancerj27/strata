package com.strata;

import com.strata.command.StrataCommands;
import com.strata.data.Config;
import com.strata.data.Defaults;
import com.strata.data.ProfileManager;
import com.strata.data.StrataConfig;
import com.strata.data.StrataDatapack;
import com.strata.game.OreDefinitionRegistry;
import com.strata.game.WorldgenRegistryBuilder;
import com.strata.runtime.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Strata.MOD_ID)
public class Strata {

    public static final String MOD_ID = "strata";
    public static final Logger LOGGER = LoggerFactory.getLogger("Strata");

    public Strata(IEventBus modBus, ModContainer container) {
        LOGGER.info("Strata loading");

        loadProfileAndConfig(modBus, container);
        registerPacksAndNetwork(modBus);
        registerContent(modBus);
        registerGameHandlers();
    }

    /** The active profile, then the common config. */
    private static void loadProfileAndConfig(IEventBus modBus, ModContainer container) {
        // THE ACTIVE PROFILE FIRST: every launch loads it, before the common config is parsed and before the
        // own-entity scan below reads variants.csv, so this launch sees one config throughout.
        ProfileManager.LaunchLoad launch = ProfileManager.loadActiveAtLaunch();
        if (launch.result() == ProfileManager.Result.OK) {
            LOGGER.info("Loaded the active profile '{}'", launch.profile());
        } else if (launch.result() == ProfileManager.Result.NOT_FOUND) {
            LOGGER.info("No profile '{}' to load - using the config as it is", launch.profile());
        }

        container.registerConfig(ModConfig.Type.COMMON, StrataConfig.SPEC);

        // The worldgen ore modifier runs before NeoForge parses COMMON config.
        modBus.addListener((FMLCommonSetupEvent e) -> earlyConfigParse());
        modBus.addListener(StrataConfig::onConfigEvent);
    }

    /** The generated packs, disabled content, and the server link. */
    private static void registerPacksAndNetwork(IEventBus modBus) {
        // Dedicated server: FMLCommonSetupEvent fires during mod loading, BEFORE Main.main constructs the
        // pack repository and scans data packs via AddPackFindersEvent.
        if (FMLEnvironment.dist.isDedicatedServer()) {
            modBus.addListener((FMLCommonSetupEvent e) -> preGeneratePack());
        }
        modBus.addListener(StrataDatapack::onAddPackFinders);
        // The custom textures and sounds, a client resource pack that is always on.
        modBus.addListener(com.strata.data.CustomAssetsPack::onAddPackFinders);
        // Disabled blocks and items: the loot modifier, and out of the tabs, last.
        com.strata.runtime.DisabledContent.LOOT_MODIFIERS.register(modBus);
        modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.strata.runtime.DisabledContent::onBuildTabs);
        // The server-held config: messages, and the own-entity list sent before the registry sync.
        modBus.addListener(com.strata.network.StrataNetwork::register);
        modBus.addListener(com.strata.network.StrataNetwork::onConfigurationTasks);
        NeoForge.EVENT_BUS.addListener(com.strata.network.StrataNetwork::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(com.strata.network.StrataNetwork::onPlayerLoggedOut);
        // A mob drawn as another mob has that mob's hitbox, on both sides.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.strata.runtime.MobModels::onSize);
        // What players need Strata for on this server, said as it starts.
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.ServerOnly::onServerStarted);
        // gradlew runVanillaCheck: Default is the game's own (off unless -Dstrata.vanillacheck=true).
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.strata.runtime.VanillaCheck::onServerStarted);
    }

    /** Registries, biome modifiers, variants, and the per-mob event listeners, in their order. */
    private static void registerContent(IEventBus modBus) {
        MobLevel.ATTACHMENT_TYPES.register(modBus);
        // Loaded first, so strata:biome_values joins the serializer register before it is registered.
        com.strata.runtime.BiomeValuesModifier.init();
        // strata:spawns: a biome's spawn rows, as a code modifier.
        com.strata.runtime.SpawnsModifier.init();
        OreRestrictionModifier.register(modBus);
        modBus.addListener(OreDefinitionRegistry::register);

        VariantEntityRegistry.scanAndRegister();
        VariantEntityRegistry.register(modBus);
        modBus.addListener(VariantEntityRegistry::onRegisterAttributes);
        // Attack damage for every mob type without it, so melee goals work on any mob.
        modBus.addListener(com.strata.runtime.BrainPause::onAttributes);
        modBus.addListener(VariantEntityRegistry::onRegisterSpawnPlacements);
        // A mob type's spawn placement and heightmap from the mob data table, last of every mod.
        modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.strata.runtime.SpawnPlacementEdits::onRegister);
        // Every item's stats into the game layer, then the user's over them, after every other mod's changes.
        modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.strata.runtime.ItemStatsRuntime::onModifyDefaults);
        // Every block's stats into the game layer once every mod's blocks are in; the user's are served by
        // the block mixins.
        modBus.addListener(com.strata.runtime.BlockStatsRuntime::onLoadComplete);
        // The game's crafting recipes, read at game start like everything else.
        modBus.addListener(com.strata.runtime.GameRecipes::onLoadComplete);
        // Block variants are real blocks, strata:<name>, copies of their base blocks, registered after every
        // other mod's.
        com.strata.runtime.BlockVariants.scan();
        modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                com.strata.runtime.BlockVariants::onRegister);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.BlockVariants::onDrops);
        // The world's level: what players do climbs it, and it gates blocks, recipes, areas and spawn levels.
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onServerStarted);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onAdvancement);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onLevelChange);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onDeath);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onBreak);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onUseItem);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.WorldLevel::onUseItemOnBlock);
        // The designer's projectiles: where they land, and how they fall.
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.Projectiles::onImpact);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.Projectiles::onTick);
        // Each mob's property values, sent as a player starts to see it.
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobProperties::onStartTracking);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.Projectiles::onStartTracking);
        // What the property tables do to a mob: loot and XP, flags, combat.
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onJoin);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onExperience);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onEquipment);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onHit);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onIncoming);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onTick);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onDespawn);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onHurtParticles);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onDeathParticles);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.MobPropertyEffects::onLeave);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.Projectiles::onJoin);
        // The developer's census, off unless -Dstrata.census=true (gradlew runGameTestServer -Pcensus).
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.Census::onServerStarted);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.BlockVariants::onTagsUpdated);
        // Every crafting recipe into the game layer, for the recipe editor.
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.GameRecipes::onServerStarted);
        NeoForge.EVENT_BUS.addListener(com.strata.runtime.GameRecipes::onDatapackSync);
        // Item variants in loot tables.
        com.strata.runtime.ItemVariantLootFunction.register(modBus);
    }

    /** The game-bus handlers, in their order. */
    private static void registerGameHandlers() {
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.register(new MobScalingHandler());
        gameBus.register(new SpawnHandler());
        gameBus.register(new com.strata.runtime.Replacements.Events());
        gameBus.register(new com.strata.runtime.DisabledContent.Events());
        // Brewing: read and set last, in NeoForge's brewing event.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.strata.runtime.BrewingData::onRegister);
        // A mob set to not convert does not.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.strata.runtime.MobScalingSwitch::onConvert);
        gameBus.register(new ReplaceSpawnRules());
        // Weight, then spawn chance, then group size.
        gameBus.register(new com.strata.runtime.SpawnGroups());
        // Each mob's own mob griefing switch.
        gameBus.register(new com.strata.runtime.MobGriefingSwitch());
        // Villager and wandering trader trades, served by Strata.
        gameBus.register(new com.strata.runtime.TradesRuntime());
        gameBus.register(new com.strata.runtime.VillagerDataRuntime());
        gameBus.register(new com.strata.runtime.SpecialSpawnsRuntime());
        gameBus.register(new com.strata.runtime.ClassChainSpawnChecks());
        // Item variants' stacks made current after an edit.
        gameBus.register(new com.strata.runtime.ItemVariants.Events());
        // Item variants' effects on whoever wears or holds them.
        gameBus.register(new com.strata.runtime.ItemVariantEffects.Events());
        // Block variant abilities, on whoever stands on or in one.
        gameBus.register(new com.strata.runtime.BlockVariantEffects.Events());
        gameBus.register(new VariantHandler());
        gameBus.register(new LootHandler());
        gameBus.register(new PlayerScalingEvents());
        gameBus.register(new MobXpScalingEvents());
        gameBus.register(new StrataLoadEvents());
        gameBus.register(new Regeneration.Events());
        gameBus.register(new StrataCommands());
    }

    private static void earlyConfigParse() {
        try {
            ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON,
                    FMLPaths.CONFIGDIR.get());
        } catch (Exception ex) {
            LOGGER.error("Could not pre-load Strata config: {}", ex.toString(), ex);
        }
    }

    /** Dedicated-server FMLCommonSetupEvent pre-generation. */
    private static void preGeneratePack() {
        try {
            Defaults.writeIfMissing();
            WorldgenRegistryBuilder.createAndBind();
            Config.reload();
        } catch (Throwable ex) {
            LOGGER.error("Could not pre-generate datapack at common setup: {}", ex.toString(), ex);
        }
    }
}
