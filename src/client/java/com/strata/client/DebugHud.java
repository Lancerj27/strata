package com.strata.client;

import com.strata.ui.Palette;
import com.strata.util.Labels;
import com.strata.data.Config;
import com.strata.data.StrataConfig;
import com.strata.model.Resolution;
import com.strata.ui.screen.DebugHudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** THE DEBUG HUD. */
public final class DebugHud {

    private DebugHud() {
    }

    /** The biome read last, and when: the lines are made again on a new biome or once a second. */
    private static String lastBiome = "";
    private static long lastTime = Long.MIN_VALUE;
    private static List<String> lines = List.of();

    /** Draws the HUD after the game's own, each frame. */
    public static void install() {
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> draw(e.getGuiGraphics()));
    }

    private static void draw(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!StrataConfig.ENABLE_DEBUG_HUD.get() || mc.player == null || mc.level == null
                || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        refresh(mc);
        com.strata.ui.Layout layout = DebugHudLayout.build(
                new com.strata.ui.Frame(graphics.guiWidth(), graphics.guiHeight()), mc.font::width, lines);
        com.strata.ui.Box panel = DebugHudLayout.panel(layout);
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), com.strata.ui.Palette.HUD_PANEL);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            graphics.drawString(mc.font, e.text(), e.box().x(), e.box().y(),
                    e.id().equals("line0") ? Palette.GROUP : Palette.TEXT);
        }
    }

    private static void refresh(Minecraft mc) {
        String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                .map(k -> k.location().toString()).orElse("");
        long now = mc.level.getGameTime();
        if (biome.equals(lastBiome) && now - lastTime < 20 && now >= lastTime) {
            return;
        }
        lastBiome = biome;
        lastTime = now;
        Resolution r = Config.resolve(biome);
        lines = DebugHudLayout.lines(biome.isEmpty() ? "unknown" : Labels.biome(biome) + " (" + biome + ")",
                r.tier(), r.level(), r.coefficient(), mc.player.experienceLevel,
                StrataConfig.ENABLE_PLAYER_SCALING.get());
    }
}

