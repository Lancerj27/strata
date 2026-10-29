package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.ui.Palette;
import com.strata.ui.screen.HelpLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Help: what applies when, how profiles work, and how a config moves from a solo world to a server. A page
 * opens as its summary and a list of closed blocks; a click on a block's title opens it.
 */
public class HelpScreen extends StrataScreen {

    /** The section shown, or null for the Help list. */
    private final String section;
    private int scroll;
    /** The blocks closed to their title rows; every block, when the page opens. */
    private final Set<Integer> folded = new HashSet<>();
    private boolean foldedOnce;

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
        return new HelpScreen(HelpLayout.sectionFor(screen));
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
            if (!foldedOnce) {
                closeAll();
                foldedOnce = true;
            }
            com.strata.ui.Layout layout = HelpLayout.build(frame(), this.font::width, title(), section, scroll, folded);
            if (layout.get("fold") != null) {
                placeAll(layout, new String[]{"fold"}, Button.builder(Component.literal(layout.textOf("fold")), b -> {
                    if (folded.isEmpty()) {
                        closeAll();
                    } else {
                        folded.clear();
                    }
                    scroll = 0;
                    rebuildWidgets();
                }).build());
            }
            return;
        }
        com.strata.ui.Layout layout = HelpLayout.index(frame(), this.font::width, title());
        useLayout(layout);
        List<String> sections = HelpLayout.sections();
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

    private void closeAll() {
        folded.clear();
        for (int b = 0; b < HelpLayout.blocks(section); b++) {
            folded.add(b);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (section == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int max = HelpLayout.maxScroll(frame(), this.font::width, section, folded);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    /** A click on a block's title opens or closes the block. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (section == null || button != 0) {
            return false;
        }
        List<HelpLayout.Line> all = HelpLayout.lines(frame(), this.font::width, section, folded);
        com.strata.ui.Layout layout = HelpLayout.build(frame(), this.font::width, title(), section, scroll, folded);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (!e.id().startsWith("line")) {
                continue;
            }
            HelpLayout.Line line = all.get(Integer.parseInt(e.id().substring(4)));
            com.strata.ui.Box box = e.box();
            if (line.subhead() && mouseX >= box.x() && mouseX < box.x() + box.w()
                    && mouseY >= box.y() - 2 && mouseY < box.y() + HelpLayout.LINE_STEP - 2) {
                if (!folded.remove(line.block())) {
                    folded.add(line.block());
                }
                scroll = Math.min(scroll, HelpLayout.maxScroll(frame(), this.font::width, section, folded));
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (section == null) {
            return;
        }
        // The text and its wrapping are the UI layer's, as the fit gate checked them: the heading and the
        // block titles in orange, the body in white.
        List<HelpLayout.Line> all = HelpLayout.lines(frame(), this.font::width, section, folded);
        com.strata.ui.Layout layout = HelpLayout.build(frame(), this.font::width, title(), section, scroll, folded);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.id().startsWith("line")) {
                HelpLayout.Line line = all.get(Integer.parseInt(e.id().substring(4)));
                text(graphics, e, line.heading() || line.subhead() ? Palette.GROUP : Palette.BODY);
            } else if (e.id().equals("more")) {
                text(graphics, e, Palette.MUTED);
            }
        }
    }
}
