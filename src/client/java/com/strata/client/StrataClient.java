package com.strata.client;

import com.strata.Strata;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.screen.ClientScreen;
import com.strata.command.StrataUi;
import com.strata.data.Config;
import com.strata.data.StrataConfig;
import com.strata.game.WorldgenRegistryBuilder;
import com.strata.runtime.VariantEntityRegistry;
import com.strata.runtime.VariantHandler;
import com.strata.runtime.VariantTextureLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

// A block variant's looks (its base block's models, layer, and tint) are set by its own subscriber,
// BlockVariantsClient.
@EventBusSubscriber(modid = Strata.MOD_ID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class StrataClient {

    private StrataClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // The shipped help, as files to copy from (config/strata/help/defaults/).
        com.strata.data.HelpText.writeDefaults();
        StrataUi.setOpenRoot(() -> Minecraft.getInstance().execute(() ->
                ScreenNavigator.get().openRoot(new ClientScreen())));

        ModList.get().getModContainerById(Strata.MOD_ID).ifPresent(container -> {
            IConfigScreenFactory factory = (mc, modListScreen) ->
                    ScreenNavigator.get().startAt(modListScreen, new ClientScreen());
            container.registerExtensionPoint(IConfigScreenFactory.class, factory);
        });

        WorldgenRegistryBuilder.preloadEditorData();

        // The player's side of the server link.
        ClientNetwork.install();

        // THE EDITOR'S UNDO: a write is recorded only while a Strata screen is open, on the client thread.
        com.strata.data.EditHistory.setActor(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isSameThread()) {
                return null;
            }
            net.minecraft.client.gui.screens.Screen s = mc.screen;
            if (s instanceof com.strata.client.widget.ConfirmDialog dialog) {
                s = dialog.parent();
            }
            return s instanceof com.strata.client.screen.StrataScreen strata
                    ? strata.getTitle().getString().replace("Strata / ", "") : null;
        });

        reflectionCheck();

        // Each mob's edited model, drawn in place of its own; and each mob's own texture, read for the game
        // layer once the mobs are made.
        NeoForge.EVENT_BUS.addListener(ModelSwap::onRenderPre);
        com.strata.runtime.MobExtractor.afterMade = ModelSwap::readTextures;
        com.strata.runtime.MobModels.onChange = ModelSwap::resizeLoaded;
        com.strata.runtime.VariantEntityRegistry.afterRegisterNow = StrataClient::renderNewVariants;

        // THE STRATA BUTTON on the title screen and the pause menu: the editor, out of the box.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e) -> {
            if (e.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen
                    || e.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen pause && pause.showsPauseMenu()) {
                net.minecraft.client.gui.screens.Screen screen = e.getScreen();
                e.addListener(net.minecraft.client.gui.components.Button.builder(
                        net.minecraft.network.chat.Component.literal("Strata"),
                        b -> Minecraft.getInstance().setScreen(ScreenNavigator.get().startAt(screen, new ClientScreen())))
                        .bounds(4, 4, 60, 20).build());
            }
        });

        // A scrolling form's header and footer bands, painted after the frame over content scrolled under
        // them.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) -> {
            if (e.getScreen() instanceof com.strata.client.screen.StrataScreen strata) {
                strata.paintBands(e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick());
            }
        });

        // The game's crafting recipes as this game was sent them, for the recipe editor: on another machine's
        // server, the server's own read is not here.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RecipesUpdatedEvent e) -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (!mc.hasSingleplayerServer() && mc.getConnection() != null) {
                com.strata.runtime.GameRecipes.fromPlayersGame(e.getRecipeManager(), mc.getConnection().registryAccess());
            }
        });

        // The debug HUD, when Options (top right) has it On.
        DebugHud.install();

        // The developer's client check (data extraction and the font), off unless -Dstrata.uismoke=true.
        UiSmoke.install();
        UiTour.install();
        // Strata's GUI scale is on the window only while a Strata screen is open.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) ->
                com.strata.client.screen.StrataScreen.restoreGuiScale(net.minecraft.client.Minecraft.getInstance()));

        NeoForge.EVENT_BUS.addListener((InputEvent.Key key) -> {
            if (key.getAction() != GLFW.GLFW_PRESS) return;
            StrataConfig.EditorKey editorKey = StrataConfig.isLoaded()
                    ? StrataConfig.EDITOR_KEY.get()
                    : StrataConfig.EditorKey.NONE;
            if (editorKey == StrataConfig.EditorKey.NONE
                    || key.getKey() != editorKey.glfwCode()) return;
            Minecraft mc = Minecraft.getInstance();
            Screen screen = mc.screen;
            if (screen instanceof TitleScreen || (screen == null && com.strata.command.StrataUi.keyAllowed(
                    mc.player == null, mc.hasSingleplayerServer(), mc.player != null && mc.player.hasPermissions(1)))) {
                StrataUi.openRoot();
            }
        });
    }

    /**
     * Registers renderers for promoted variant EntityTypes by copying the base mob's renderer provider from
     * the vanilla registry.
     */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Map<EntityType<?>, EntityRendererProvider<?>> providers = getProviderMap();
        if (providers == null) return;

        for (var entry : VariantEntityRegistry.allRegistered().entrySet()) {
            String baseId = VariantEntityRegistry.baseEntityId(entry.getKey()).orElse("");
            ResourceLocation baseLoc = ResourceLocation.tryParse(baseId);
            if (baseLoc == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(baseLoc)) continue;
            EntityType<?> baseType = BuiltInRegistries.ENTITY_TYPE.get(baseLoc);
            EntityRendererProvider<?> provider = providers.get(baseType);
            if (provider != null) {
                event.registerEntityRenderer((EntityType) entry.getValue(),
                        (EntityRendererProvider) provider);
            } else {
                Strata.LOGGER.warn("No renderer provider found for base entity '{}'"
                        + " (variant '{}')", baseId, entry.getKey());
            }
        }
    }

    /**
     * Variants registered while the game runs (VariantEntityRegistry#registerNow) are drawn as their base
     * mob: its renderer is given to each, and the renderers are built again.
     */
    private static void renderNewVariants() {
        Map<EntityType<?>, EntityRendererProvider<?>> providers = getProviderMap();
        if (providers == null) {
            return;
        }
        for (var entry : VariantEntityRegistry.allRegistered().entrySet()) {
            if (providers.containsKey(entry.getValue())) {
                continue;
            }
            ResourceLocation baseLoc = ResourceLocation.tryParse(
                    VariantEntityRegistry.baseEntityId(entry.getKey()).orElse(""));
            if (baseLoc != null && BuiltInRegistries.ENTITY_TYPE.containsKey(baseLoc)) {
                EntityRendererProvider<?> base = providers.get(BuiltInRegistries.ENTITY_TYPE.get(baseLoc));
                if (base != null) {
                    providers.put(entry.getValue(), base);
                }
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        Runnable rebuild = () -> minecraft.getEntityRenderDispatcher()
                .onResourceManagerReload(minecraft.getResourceManager());
        if (minecraft.isSameThread()) {
            rebuild.run();
        } else {
            try {
                minecraft.submit(rebuild).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Strata.LOGGER.warn("Variants: the new mobs' renderers were not built in time: {}", e.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<EntityType<?>, EntityRendererProvider<?>> getProviderMap() {
        try {
            Field field = EntityRenderers.class.getDeclaredField("PROVIDERS");
            field.setAccessible(true);
            return (Map<EntityType<?>, EntityRendererProvider<?>>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Strata.LOGGER.error("Cannot access EntityRenderers.PROVIDERS: {}", e.toString());
            return null;
        }
    }

    /** One-time bootstrap guard. */
    private static void reflectionCheck() {
        int promoted = VariantEntityRegistry.allRegistered().size();
        if (promoted == 0) return;
        if (getProviderMap() == null) {
            Strata.LOGGER.error("[REFLECTION-CHECK] FAILED: {} promoted variant "
                    + "EntityType(s) are registered but EntityRenderers.PROVIDERS is "
                    + "not accessible — promoted mobs will be invisible. The NeoForge "
                    + "runtime no longer matches the pinned Mojmap field names "
                    + "(version bump?). Pinned runtime: NeoForge 1.21.1 Mojmap.", promoted);
        }
    }

    /**
     * Variant textures: attach the {@link VariantTextureLayer} to every living-entity
     * renderer.
     */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        ModelSwap.onAddLayers(event);
        VariantHandler.indexTextures(List.copyOf(Config.tables().variants().values()));
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer<?, ?> living) {
                attachVariantLayer(living);
            }
        }
    }

    /**
     * Entity renderers are keyed by EntityType with disjoint type parameters, so the registry hands us a
     * wildcard.
     */
    private static void attachVariantLayer(LivingEntityRenderer<?, ?> living) {
        @SuppressWarnings("unchecked")
        LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>> typed =
                (LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>>) living;
        typed.addLayer(new VariantTextureLayer<>(typed));
        // A mob's glowing texture over it, at full brightness.
        typed.addLayer(new com.strata.runtime.GlowTextureLayer<>(typed));
    }
}
