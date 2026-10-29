package com.strata.command;

import com.strata.SelfTestHarness;
import com.strata.data.Config;
import com.strata.data.ConfigValidator;
import com.strata.data.Paths;
import com.strata.data.ProfileManager;
import com.strata.data.TableWriterSelfTest;
import com.strata.util.Labels;
import com.strata.game.RegistryQuery;
import com.strata.model.Profile;
import com.strata.model.Resolution;
import com.strata.model.ResolverSelfTest;
import com.strata.runtime.MobLevel;
import com.strata.runtime.MobScalingHandler;
import com.strata.runtime.OreData;
import com.strata.runtime.PowerLevel;
import com.strata.runtime.Regeneration;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class StrataCommands {

    private static final double MOB_SEARCH_RADIUS = 16.0;

    /** The literals of /strata test spawn, shared by the tree and its callers. */
    public static final String ROOT = "strata";
    public static final String TEST = "test";
    public static final String SPAWN = "spawn";

    /** The command line that spawns a test mob, without the leading slash. */
    public static String testSpawnCommand(String entityId, int level) {
        return ROOT + " " + TEST + " " + SPAWN + " " + entityId + " " + level;
    }

    /** The literal of /strata help, and the help section it prints. */
    public static final String HELP = "help";
    public static final String HELP_SECTION = "Commands";

    /** The literal of /strata reload, shared by the tree and the Profiles screen. */
    public static final String RELOAD = "reload";

    /** The command line that reloads the server's config, without the slash. */
    public static String reloadCommand() {
        return ROOT + " " + RELOAD;
    }

    /** The literal of /strata regenerate, shared by the tree and the Profiles screen. */
    public static final String REGENERATE = "regenerate";

    /** The command line that queues a regenerate, without the slash. */
    public static String regenerateCommand() {
        return ROOT + " " + REGENERATE;
    }

    /** The lowest level the test spawn command accepts. */
    public static final int MIN_TEST_LEVEL = 0;

    /** Why these Test Mode inputs cannot be sent, in the designer's words, or null when they can. */
    public static String testSpawnProblem(String entityId, String rawLevel) {
        if (entityId == null || entityId.isBlank()) {
            return "Pick a mob to spawn";
        }
        String level = rawLevel == null ? "" : rawLevel.strip();
        if (level.isEmpty()) {
            return "Type a level";
        }
        try {
            return Integer.parseInt(level) < MIN_TEST_LEVEL
                    ? "The level must be " + MIN_TEST_LEVEL + " or more" : null;
        } catch (NumberFormatException e) {
            return "The level must be a whole number";
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(tree());
    }

    /** The whole /strata command tree. */
    public LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal(ROOT)
                        .then(Commands.literal(HELP)
                                .executes(this::help))
                        .then(Commands.literal("level")
                                .then(Commands.literal("get")
                                        .executes(ctx -> getLevel(ctx, ctx.getSource().getPlayerOrException()))
                                        // Another player's level needs operator level 2.
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .requires(s -> s.hasPermission(2))
                                                .executes(ctx -> getLevel(ctx, EntityArgument.getPlayer(ctx, "player"))))))
                        .then(Commands.literal("stage")
                                .executes(this::getStage)
                                // Setting the world's stage needs operator level 2.
                                .then(Commands.argument("stage", IntegerArgumentType.integer(0))
                                        .requires(s -> s.hasPermission(2))
                                        .executes(ctx -> setStage(ctx, IntegerArgumentType.getInteger(ctx, "stage")))))
                        .then(Commands.literal("mob")
                                .then(Commands.literal("get")
                                        .executes(this::getNearestMob))
                                // What the nearest mob's AI is doing.
                                .then(Commands.literal("goals")
                                        .executes(this::nearestMobGoals)))
                        .then(Commands.literal(RELOAD)
                                .requires(s -> s.hasPermission(2))
                                .executes(this::reload))
                        .then(Commands.literal("assets")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.literal(RELOAD)
                                        .executes(this::assetsReload)))
                        .then(Commands.literal(REGENERATE)
                                .requires(s -> s.hasPermission(2))
                                .executes(this::regenerate))
                        .then(Commands.literal("selftest")
                                .requires(s -> s.hasPermission(2))
                                .executes(this::selfTest))
                        .then(Commands.literal("ui")
                                .requires(s -> s.hasPermission(2))
                                .executes(this::openUi))
                        .then(Commands.literal("list")
                                .then(Commands.argument("registry", StringArgumentType.word())
                                        .executes(ctx -> listRegistry(ctx,
                                                StringArgumentType.getString(ctx, "registry")))))
                        .then(Commands.literal("validate")
                                .requires(s -> s.hasPermission(2))
                                .executes(this::validate))
                        .then(Commands.literal("ore")
                                .then(Commands.literal("list")
                                        .executes(this::listOres)))
                        // Item variants.
                        .then(Commands.literal("item")
                                .then(Commands.literal("list")
                                        .executes(this::listItemVariants))
                                .then(Commands.literal("give")
                                        .requires(s -> s.hasPermission(2))
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("variant", StringArgumentType.word())
                                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider
                                                                .suggest(com.strata.runtime.ItemVariants.names(), b))
                                                        .executes(ctx -> giveItemVariant(ctx, 1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 6400))
                                                                .executes(ctx -> giveItemVariant(ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "count"))))))))
                        .then(Commands.literal("profile")
                                .then(Commands.literal("list")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(this::listProfiles))
                                .then(Commands.literal("export")
                                        .requires(s -> s.hasPermission(2))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> exportProfile(ctx,
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("activate")
                                        .requires(s -> s.hasPermission(2))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> activateProfile(ctx,
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(Commands.literal("delete")
                                        .requires(s -> s.hasPermission(2))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> deleteProfile(ctx,
                                                        StringArgumentType.getString(ctx, "name"))))))
                        // A TOP-LEVEL literal.
                        .then(Commands.literal(TEST)
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.literal(SPAWN)
                                        .then(Commands.argument("entity", ResourceLocationArgument.id())
                                                .then(Commands.argument("level", IntegerArgumentType.integer(MIN_TEST_LEVEL))
                                                        .executes(ctx -> testSpawn(ctx,
                                                                ResourceLocationArgument.getId(ctx, "entity").toString(),
                                                                IntegerArgumentType.getInteger(ctx, "level")))))));
    }

    /**
     * /strata help: prints the Commands help section to chat, the same text the Help screen shows, so a
     * pack author can fix a word in config/strata/help/57-commands.txt without a new jar.
     */
    private int help(CommandContext<CommandSourceStack> ctx) {
        com.strata.data.HelpText.Section section = com.strata.data.HelpText.section(HELP_SECTION);
        if (section == null) {
            section = com.strata.data.HelpText.UNAVAILABLE;
        }
        for (String line : section.text()) {
            if (!line.isBlank()) {
                ctx.getSource().sendSystemMessage(
                        Component.literal(line.startsWith("# ") ? line.substring(2) : line));
            }
        }
        return 1;
    }

    /**
     * /strata assets reload: imports what is in config/strata/custom/import/ (textures in a folder per
     * category, sounds in sounds/) through the same hard coded checks as any import, then offers the library
     * to every player again.
     */
    private int assetsReload(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        List<String> lines = com.strata.domain.AssetSync.importFolder(Paths.ROOT);
        com.strata.domain.AssetSync.Built built = com.strata.domain.AssetSync.buildManifest(Paths.ROOT);
        com.strata.network.AssetServer.noticeOps(server, built.notices());
        com.strata.network.AssetServer.libraryChanged(server);
        for (String line : lines) {
            ctx.getSource().sendSystemMessage(Component.literal("Strata assets: " + line));
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Strata assets: " + built.entries().size()
                + " custom texture(s) and sound(s) offered to players."), true);
        return 1;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        Config.reload();
        ServerLevel overworld = ctx.getSource().getServer().getLevel(Level.OVERWORLD);
        if (overworld != null) {
            OreData.buildIndex(overworld);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Strata reloaded: " + Config.tables().tiers().size() + " tier(s), "
                        + Config.tables().biomes().size() + " configured biome(s), "
                        + Config.tables().variants().size() + " variant(s)."), true);
        return 1;
    }

    private int selfTest(CommandContext<CommandSourceStack> ctx) {
        SelfTestHarness.Result resolver = ResolverSelfTest.run();
        for (String line : resolver.lines()) {
            ctx.getSource().sendSystemMessage(Component.literal(line));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                resolver.ok()
                        ? "Resolver: all " + resolver.passed() + " passed."
                        : "Resolver: " + resolver.failed() + " FAILED, " + resolver.passed() + " passed."), false);

        SelfTestHarness.Result writer = TableWriterSelfTest.run();
        for (String line : writer.lines()) {
            ctx.getSource().sendSystemMessage(Component.literal(line));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                writer.ok()
                        ? "TableWriter: all " + writer.passed() + " passed."
                        : "TableWriter: " + writer.failed() + " FAILED, " + writer.passed() + " passed."), false);

        return resolver.ok() && writer.ok() ? 1 : 0;
    }

    private int openUi(CommandContext<CommandSourceStack> ctx) {
        // Only the host's own player opens this game's editor: on an open-to-LAN world a guest's
        // command runs here too, and opened the editor on the host's screen.
        var player = ctx.getSource().getPlayer();
        if (player == null || !ctx.getSource().getServer().isSingleplayerOwner(player.getGameProfile())) {
            ctx.getSource().sendFailure(Component.literal(
                    "/strata ui opens the editor in your own game: use the editor key, or the Mods list."));
            return 0;
        }
        if (FMLEnvironment.dist.isClient() && StrataUi.isAvailable()) {
            StrataUi.openRoot();
            ctx.getSource().sendSuccess(() -> Component.literal("Opening Strata editor..."), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("The Strata editor is client-only."));
        return 0;
    }

    /** The command the variant editor's Give me one sends: one to the player at the editor. */
    public static String giveVariantCommand(String variant) {
        return ROOT + " item give @s " + variant;
    }

    /** /strata item list: every item variant and its base item. */
    private int listItemVariants(CommandContext<CommandSourceStack> ctx) {
        List<String> names = com.strata.runtime.ItemVariants.names();
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No item variants. Add one in Client > Items, under its base item."), false);
            return 1;
        }
        List<String> rows = names.stream().map(n -> n + " (" + com.strata.runtime.ItemVariants.get(n).baseId() + ")").toList();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Item variants (" + rows.size() + "): " + String.join(", ", rows)), false);
        return rows.size();
    }

    /** /strata item give: a variant's stacks into each target's inventory, the rest dropped at their feet. */
    private int giveItemVariant(CommandContext<CommandSourceStack> ctx, int count) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "variant");
        var def = com.strata.runtime.ItemVariants.get(name);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("No item variant " + name
                    + ". /strata item list shows them."));
            return 0;
        }
        var targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            int left = count;
            while (left > 0) {
                net.minecraft.world.item.ItemStack stack = com.strata.runtime.ItemVariants.make(name, 1,
                        player.registryAccess());
                int size = Math.min(left, stack.getMaxStackSize());
                stack.setCount(size);
                left -= size;
                if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                    player.drop(stack, false);
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + " " + name + " to "
                + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private int listOres(CommandContext<CommandSourceStack> ctx) {
        List<String> names = RegistryQuery.ores().stream()
                .map(RegistryQuery.Option::id).sorted().toList();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Known ores (" + names.size() + "): " + String.join(", ", names)), false);
        return 1;
    }

    private int listRegistry(CommandContext<CommandSourceStack> ctx, String registryName) {
        if (!ConfigValidator.knownRegistryKeys().contains(registryName)) {
            List<String> sorted = ConfigValidator.knownRegistryKeys().stream().sorted().toList();
            ctx.getSource().sendFailure(Component.literal(
                    "Unknown registry '" + registryName + "'. Valid: "
                            + String.join(", ", sorted)));
            return 0;
        }

        List<RegistryQuery.Option> options;
        switch (registryName) {
            case "entity" -> options = RegistryQuery.entities();
            case "item" -> options = RegistryQuery.items();
            case "attribute" -> options = RegistryQuery.attributes();
            case "ore" -> options = RegistryQuery.ores();
            case "biome" -> {
                RegistryAccess ra = ctx.getSource().getServer().registryAccess();
                List<ResourceLocation> biomeIds = RegistryQuery.biomes(ra);
                options = biomeIds.stream()
                        .map(id -> new RegistryQuery.Option(id.toString(),
                                Labels.pretty(id), id.getNamespace()))
                        .toList();
            }
            case "structure" -> {
                RegistryAccess ra = ctx.getSource().getServer().registryAccess();
                options = RegistryQuery.structures(ra);
            }
            case "goal_set" -> {
                // The behaviors table, the designer's own included.
                options = com.strata.runtime.GoalsRuntime.goalSets().keySet().stream().sorted()
                        .map(k -> new RegistryQuery.Option(k, k, "strata"))
                        .toList();
            }
            default -> {
                return 0;
            }
        }

        List<RegistryQuery.Option> sorted = options.stream()
                .sorted(Comparator.comparing(RegistryQuery.Option::displayName))
                .toList();

        ctx.getSource().sendSuccess(() -> Component.literal(
                registryName + " (" + sorted.size() + " entries):"), false);
        for (RegistryQuery.Option opt : sorted) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "  " + opt.id() + " — " + opt.displayName()));
        }
        return 1;
    }

    private int validate(CommandContext<CommandSourceStack> ctx) {
        Config.reload();
        List<ConfigValidator.Finding> findings = ConfigValidator.validate(
                Config.tables(), Paths.ROOT);
        if (findings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Strata validate: no issues found."), true);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Strata validate: " + findings.size() + " issue(s):"), true);
        for (ConfigValidator.Finding f : findings) {
            ctx.getSource().sendSystemMessage(Component.literal(
                    "  [" + f.category() + "] " + f.message()));
        }
        return 0;
    }

    private int listProfiles(CommandContext<CommandSourceStack> ctx) {
        List<Profile> profiles = ProfileManager.listProfiles();
        if (profiles.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No profiles. Use /strata profile export <name>."), true);
            return 1;
        }
        List<String> names = profiles.stream().map(Profile::name).toList();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Profiles (" + names.size() + "): " + String.join(", ", names)), true);
        return 1;
    }

    private int exportProfile(CommandContext<CommandSourceStack> ctx, String name) {
        return reportProfile(ctx, ProfileManager.exportProfile(name), "Exported", name);
    }

    /** Activates a profile on this server, and queues a regenerate. */
    private int activateProfile(CommandContext<CommandSourceStack> ctx, String name) {
        int ok = reportProfile(ctx, Regeneration.loadProfile(ctx.getSource().getServer(), name),
                "Activated", name);
        if (ok == 1) {
            ctx.getSource().sendSuccess(() -> Component.literal(Regeneration.RESTART_NOTICE), false);
        }
        return ok;
    }

    /**
     * /strata regenerate: queues the next start to bring mobs and existing chunks in line with the config.
     */
    private int regenerate(CommandContext<CommandSourceStack> ctx) {
        Regeneration.request(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Regenerate queued. At the next start, mobs and existing chunks update to the"
                        + " config as they load."), true);
        return 1;
    }

    private int deleteProfile(CommandContext<CommandSourceStack> ctx, String name) {
        return reportProfile(ctx, ProfileManager.deleteProfile(name), "Deleted", name);
    }

    /** Tells the player what a profile operation really did. */
    private int reportProfile(CommandContext<CommandSourceStack> ctx,
                              ProfileManager.Result result, String verb, String name) {
        if (result == ProfileManager.Result.OK) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    verb + " profile '" + name + "'."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(
                result.failureText() + ": '" + name + "'."));
        return 0;
    }

    /**
     * /strata test spawn <entity> <level> — spawns a mob of the given type at an explicit level through the
     * SAME pipeline natural spawns use ({@link MobScalingHandler#applyTo}), so the preview matches the real
     * scaling exactly.
     */
    private int testSpawn(CommandContext<CommandSourceStack> ctx, String entityId, int level)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel serverLevel = player.serverLevel();

        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("Not a valid entity id: " + entityId));
            return 0;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(id);
        // A variant made this session has no mob type of its own until the world loads again: it spawns as its
        // base mob with the variant applied, as the spawn ability makes it.
        com.strata.model.Variant made = type.isPresent() ? null : variantNamed(id);
        if (made != null) {
            return spawnMadeVariant(ctx, player, serverLevel, made, level);
        }
        if (type.isEmpty() || !(type.get().create(serverLevel) instanceof Mob mob)) {
            ctx.getSource().sendFailure(Component.literal("Unknown entity or not a mob: " + entityId));
            return 0;
        }
        mob.moveTo(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());

        Holder<Biome> biome = serverLevel.getBiome(player.blockPosition());
        Resolution resolution = Config.resolve(
                biome.unwrapKey().map(ResourceKey::location).orElse(null));

        if (level == 0) {
            MobScalingHandler.applyToAtVariantLevel(mob, resolution);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Spawned " + entityId + " at its variant start level through the natural scaling pipeline."), false);
        } else {
            MobScalingHandler.applyTo(mob, resolution, level);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Spawned " + entityId + " at level " + level
                            + " through the natural scaling pipeline."), false);
        }
        serverLevel.addFreshEntity(mob);
        return 1;
    }

    /** The variant an id names: "strata:name" or "name", by its name or its mob type name; null when none. */
    private static com.strata.model.Variant variantNamed(ResourceLocation id) {
        var variants = Config.tables().variants();
        if (variants.containsKey(id.getPath())) {
            return variants.get(id.getPath());
        }
        for (var v : variants.values()) {
            if (com.strata.runtime.VariantEntityRegistry.registryName(v.id()).equals(id.getPath())) {
                return v;
            }
        }
        return null;
    }

    /** Spawns a variant with no mob type of its own yet as its base mob, the variant applied. */
    private int spawnMadeVariant(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ServerLevel level,
                                 com.strata.model.Variant variant, int mobLevel) {
        String base = com.strata.runtime.VariantHandler.baseOf(variant.id());
        Optional<EntityType<?>> baseType = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(base));
        if (baseType.isEmpty() || !(baseType.get().create(level) instanceof Mob mob)) {
            ctx.getSource().sendFailure(Component.literal("Variant " + variant.id() + ": its base mob " + base
                    + " is not a mob"));
            return 0;
        }
        mob.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        String biome = level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
        Resolution resolution = Config.resolve(biome);
        com.strata.runtime.MobScalingHandler.applyVariant(mob, variant, resolution);
        if (mobLevel > 0) {
            com.strata.runtime.MobAttributeScaling.apply(mob, mobLevel, resolution);
        }
        level.addFreshEntity(mob);
        ctx.getSource().sendSuccess(() -> Component.literal("Spawned " + variant.id() + " as a " + base
                + " with the variant" + (mobLevel > 0 ? ", at level " + mobLevel : "")
                + ". It becomes its own mob at the next world load."), false);
        return 1;
    }

    /** The world's stage, by number and name. */
    private int getStage(CommandContext<CommandSourceStack> ctx) {
        String level = String.format(java.util.Locale.ROOT, "%.2f", com.strata.runtime.WorldLevel.level());
        ctx.getSource().sendSuccess(() -> Component.literal("World stage " + stageWords(com.strata.runtime.WorldLevel.stage())
                + ", world level " + level + ", highest player level " + com.strata.runtime.WorldLevel.highestPlayerLevel()), false);
        return 1;
    }

    private int setStage(CommandContext<CommandSourceStack> ctx, int stage) {
        int now = com.strata.runtime.WorldLevel.setStage(ctx.getSource().getServer(), stage);
        ctx.getSource().sendSuccess(() -> Component.literal("World stage set to " + stageWords(now)), true);
        return 1;
    }

    private static String stageWords(int stage) {
        var row = com.strata.runtime.WorldLevel.rules().stages().get(stage);
        return stage + (row == null || row.name().isEmpty() ? "" : " (" + row.name() + ")");
    }

    private int getLevel(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        int powerLevel = PowerLevel.of(player);
        String suffix = "";
        if (player.level() instanceof ServerLevel serverLevel) {
            Holder<Biome> biome = serverLevel.getBiome(player.blockPosition());
            String biomeName = biome.unwrapKey().map(k -> Labels.biome(k.location().toString())).orElse("unknown");
            Resolution resolution = Config.resolve(
                    biome.unwrapKey().map(ResourceKey::location).orElse(null));
            suffix = ", biome: " + biomeName + " (" + resolution.describeLevel() + ")";
        }
        final String s = suffix;
        ctx.getSource().sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + " — power level " + powerLevel
                        + " (XP level: " + player.experienceLevel + ")" + s), false);
        return 1;
    }

    /**
     * /strata mob ai: the nearest mob's AI, where it came from, what it holds and targets, and each goal (* =
     * running now).
     */
    private int nearestMobGoals(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(MOB_SEARCH_RADIUS);
        net.minecraft.world.entity.Mob nearest = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        if (nearest == null) {
            ctx.getSource().sendFailure(Component.literal("No mobs within " + (int) MOB_SEARCH_RADIUS + " blocks."));
            return 0;
        }
        for (String line : com.strata.runtime.GoalsRuntime.report(nearest)) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        if (player.isCreative() || player.isSpectator()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "You are in " + player.gameMode.getGameModeForPlayer().getName()
                            + ": the game lets no mob target you, even one you hit. Test in Survival."), false);
        }
        return 1;
    }

    private int getNearestMob(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(MOB_SEARCH_RADIUS);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && !(e instanceof ServerPlayer));
        LivingEntity nearest = nearby.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
        if (nearest == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No mobs within " + (int) MOB_SEARCH_RADIUS + " blocks."));
            return 0;
        }
        MobLevel mobLevel = nearest.getData(MobLevel.MOB_LEVEL);
        String levelText = mobLevel.isSet() ? String.valueOf(mobLevel.level()) : "unset";
        double attackDamage = nearest.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? nearest.getAttributeValue(Attributes.ATTACK_DAMAGE) : -1;
        double maxHealth = nearest.getMaxHealth();
        LivingEntity mob = nearest;
        ctx.getSource().sendSuccess(() -> Component.literal(
                mob.getType().getDescription().getString() + " — level " + levelText
                        + String.format(" (atk: %.1f, hp: %.1f)", attackDamage, maxHealth)), false);
        return 1;
    }
}

