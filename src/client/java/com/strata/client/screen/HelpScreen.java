package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Help: what applies when, how profiles work, and how a config moves from a solo world to a server. */
public class HelpScreen extends StrataScreen {

    /** The section shown, or null for the Help list. */
    private final String section;
    private int scroll;

    /** The Help list: Options > Help. */
    public HelpScreen() {
        this(null);
    }

    private HelpScreen(String section) {
        super(section == null ? "Strata — Help" : "Strata — Help: " + section);
        this.section = section;
    }

    /** The page for a screen's "?": its section (HelpLayout#sectionFor), else the Help list. */
    public static HelpScreen forScreen(String screen) {
        return new HelpScreen(com.strata.ui.screen.HelpLayout.sectionFor(screen));
    }

    /** The Help list's "?" has nowhere further to go; a section's opens the list. */
    @Override
    protected net.minecraft.client.gui.screens.Screen helpScreen() {
        return section == null ? null : new HelpScreen();
    }

    /** The Help list scrolls on a small window. */
    @Override
    protected boolean scrolls() {
        return section == null;
    }

    @Override
    protected void init() {
        super.init();
        if (section != null) {
            return;
        }
        com.strata.ui.Layout layout = com.strata.ui.screen.HelpLayout.index(frame(), this.font::width, title());
        useLayout(layout);
        List<String> sections = com.strata.ui.screen.HelpLayout.sections();
        for (int i = 0; i < sections.size(); i++) {
            String open = sections.get(i);
            placeAll(layout, new String[]{"section" + i}, Button.builder(Component.literal(
                            layout.textOf("section" + i)),
                    b -> ScreenNavigator.get().navigateTo(new HelpScreen(open))).build());
        }
        placeAll(layout, new String[]{"reload"}, Button.builder(Component.literal(
                        layout.textOf("reload")), b -> {
                    com.strata.data.HelpText.reload();
                    rebuildWidgets();
                }).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (section == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int max = com.strata.ui.screen.HelpLayout.maxScroll(frame(), this.font::width, section);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (section == null) {
            return;
        }
        // The text and its wrapping are the UI layer's, as the fit gate checked them: a heading's lines in
        // orange.
        List<com.strata.ui.screen.HelpLayout.Line> all = com.strata.ui.screen.HelpLayout.lines(frame(),
                this.font::width, section);
        com.strata.ui.Layout layout = com.strata.ui.screen.HelpLayout.build(frame(), this.font::width, title(),
                section, scroll);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.id().startsWith("line")) {
                boolean heading = all.get(Integer.parseInt(e.id().substring(4))).heading();
                text(graphics, e, heading ? Palette.GROUP : Palette.BODY);
            } else if (e.id().equals("more")) {
                text(graphics, e, Palette.MUTED);
            }
        }
    }
}
