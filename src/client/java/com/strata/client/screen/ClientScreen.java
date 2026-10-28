package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.data.GameSnapshot;
import com.strata.data.ProfileManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** The Client menu: the editor and the local profiles. */
public class ClientScreen extends StrataScreen {

    /** The profile every launch loads, and whether the working config differs from it. */
    private String activeProfile = "";
    private boolean unsaved;

    public ClientScreen() {
        super("Strata");
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    /** The biome count: from the open world, else from the game layer. */
    private String status() {
        if (this.minecraft != null && this.minecraft.level != null) {
            int count = this.minecraft.level.registryAccess()
                    .registry(Registries.BIOME).map(reg -> reg.keySet().size()).orElse(0);
            return count + " biomes in registry";
        }
        int snap = GameSnapshot.get().size();
        return snap == 0
                ? "No biomes: the game data could not be read at game start (see the log)"
                : "Offline: " + snap + " biomes from the game layer (read at game start)";
    }

    /** The layout for the lines as they stand. */
    private com.strata.ui.Layout layout() {
        com.strata.ui.Layout layout = com.strata.ui.screen.MenuLayouts.client(frame(), this.font::width, title(),
                status(), unsaved ? "Unsaved: edits reset at the next launch. Save them in Profiles (top bar)."
                        : "Active profile: " + activeProfile + " (every launch loads it)");
        useLayout(layout);
        return layout;
    }

    @Override
    protected void init() {
        super.init();
        // Every launch loads the active profile.
        activeProfile = ProfileManager.activeProfile();
        unsaved = ProfileManager.hasUnsavedEdits();
        // The entries, in as many columns as fit: the layout is the UI layer's.
        com.strata.ui.Layout layout = layout();
        // Three sections.
        List<Supplier<net.minecraft.client.gui.screens.Screen>> opens = List.of(
                () -> SectionNav.open(com.strata.ui.screen.Sections.WORLD),
                () -> SectionNav.open(com.strata.ui.screen.Sections.ITEMS),
                () -> SectionNav.open(com.strata.ui.screen.Sections.BLOCKS));
        for (int i = 0; i < opens.size(); i++) {
            Supplier<net.minecraft.client.gui.screens.Screen> open = opens.get(i);
            placeAll(layout, new String[]{"entry" + i}, Button.builder(
                    Component.literal(com.strata.ui.screen.MenuLayouts.CLIENT_ENTRIES.get(i)),
                    b -> ScreenNavigator.get().navigateTo(open.get())).build());
        }
    }

    /**
     * Strata opens here, so closing the editor from here with edits the next launch would reset asks first.
     */
    @Override
    protected boolean hasUnsavedEdits() {
        return ProfileManager.hasUnsavedEdits();
    }

    @Override
    protected String leaveMessage() {
        return "Your edits are not saved to the profile '" + ProfileManager.activeProfile()
                + "'.\nThey reset at the next launch. Close anyway?\n"
                + "(Save in Profiles (top bar) keeps them.)";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        text(graphics, layout.get("profile"), unsaved ? Palette.GROUP : Palette.MUTED);
        text(graphics, layout.get("status"), Palette.LABEL);
    }
}