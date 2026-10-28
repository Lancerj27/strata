package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.domain.PlayerSpawnOps;
import com.strata.domain.TierOps;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Where players spawn, a debug tool: Options > Player spawn. */
public class PlayerSpawnScreen extends StrataScreen {

    public PlayerSpawnScreen() {
        super("Strata — Player spawn");
    }

    /** The target now, in words. */
    private String now() {
        PlayerSpawnOps.Target t = PlayerSpawnOps.current();
        return switch (t.kind()) {
            case OFF -> "Now: Off, the game's own spawn.";
            case BIOME -> "Now: the biome " + Labels.biome(t.id()) + " (" + t.id() + ").";
            case TIER -> "Now: the tier " + t.id() + ", the nearest of its "
                    + com.strata.ui.Words.count(TierOps.biomesIn(t.id()).size(), "biome", "biomes") + ".";
        };
    }

    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.PlayerSpawnLayout.build(frame(), this.font::width, title(), now());
    }

    @Override
    protected void init() {
        super.init();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        placeAll(layout, new String[]{"biome", "tier", "off"},
                Button.builder(Component.literal(layout.textOf("biome")), b -> pickBiome()).build(),
                Button.builder(Component.literal(layout.textOf("tier")), b -> pickTier()).build(),
                Button.builder(Component.literal(layout.textOf("off")), b -> report(PlayerSpawnOps.off())).build());
        radius = field(0, 0, 0, 0, "Search radius", String.valueOf(com.strata.data.StrataConfig.PLAYER_SPAWN_RADIUS.get()),
                null, null);
        place(radius.getWidget(), layout.get("radius"));
        placeAll(layout, new String[]{"setRadius"}, Button.builder(Component.literal("Set"),
                b -> report(com.strata.domain.OptionsOps.set(com.strata.domain.OptionsOps.PLAYER_SPAWN_RADIUS,
                        radius.getValue()))).build());
    }

    /** How far the search goes, typed. */
    private com.strata.client.widget.EditField radius;

    private void pickBiome() {
        List<RegistryQuery.Option> options = new ArrayList<>();
        com.strata.game.BiomeCatalog.all(this.minecraft != null && this.minecraft.level != null
                ? worldRegistries() : null)
                .forEach(b -> options.add(new RegistryQuery.Option(b.id(), Labels.biome(b.id()), b.dimension())));
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick the spawn biome", options,
                id -> deferTo(() -> report(PlayerSpawnOps.setBiome(id)))));
    }

    private void pickTier() {
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick the spawn tier",
                RegistryPickerScreen.plainOptions("tier", TierOps.names()),
                name -> deferTo(() -> report(PlayerSpawnOps.setTier(name)))));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout());
        for (com.strata.ui.Layout.Element e : layout().elements()) {
            if (e.id().equals("now")) {
                text(graphics, e, Palette.TEXT);
            } else if (e.id().startsWith("help")) {
                text(graphics, e, Palette.LABEL);
            }
        }
    }
}

