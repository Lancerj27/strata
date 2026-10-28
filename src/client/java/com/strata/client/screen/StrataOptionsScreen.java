package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ValueInput;
import com.strata.domain.OptionsOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Strata's settings in config/strata-common.toml. */
public class StrataOptionsScreen extends StrataScreen {

    private List<OptionsOps.Setting> settings = List.of();
    private BrowseList list;
    private ValueInput valueInput;

    public StrataOptionsScreen() {
        this("Strata — Options");
    }

    protected StrataOptionsScreen(String title) {
        super(title);
    }

    /** The settings this screen lists. */
    protected List<OptionsOps.Setting> list() {
        return OptionsOps.all();
    }

    /** The screens the top row opens, in the order of #toolLabels. */
    protected List<Supplier<net.minecraft.client.gui.screens.Screen>> tools() {
        // None: the Options Section dropdown opens the other screens.
        return List.of();
    }

    /** The top row's labels (ListScreenLayouts#menuTools). */
    protected List<String> toolLabels() {
        return com.strata.ui.screen.ListScreenLayouts.menuTools();
    }

    /** The line under the bar: where the settings are kept. */
    protected String where() {
        return "This machine's config/strata-common.toml. A server keeps its own: load a profile onto it.";
    }

    @Override
    protected void init() {
        super.init();
        settings = list();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        List<Supplier<net.minecraft.client.gui.screens.Screen>> opens = tools();
        List<String> labels = toolLabels();
        for (int i = 0; i < opens.size(); i++) {
            Supplier<net.minecraft.client.gui.screens.Screen> open = opens.get(i);
            placeAll(layout, new String[]{"tool" + i}, Button.builder(Component.literal(labels.get(i)),
                    b -> ScreenNavigator.get().navigateTo(open.get())).build());
        }
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("StrataOptionsScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> showValue(settings.get(Integer.parseInt(entry.id))));
        addRenderableWidget(list);

        valueInput = valueInput(layout, "value", null);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> {
            var s = selected();
            if (s != null) act(OptionsOps.set(s.key(), valueInput.value()));
        }).build();
        Button toggle = Button.builder(Component.literal("On / Off"), b -> {
            var s = selected();
            if (s != null) act(OptionsOps.toggle(s.key()));
        }).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
            var s = selected();
            if (s != null) act(OptionsOps.restore(s.key()));
        }).build();
        placeAll(layout, new String[]{"set", "toggle", "restore"}, set, toggle, restore);
        needsSelection(() -> selected() != null && selected().kind() != OptionsOps.Kind.SWITCH, set);
        needsSelection(() -> selected() != null && selected().kind() == OptionsOps.Kind.SWITCH, toggle);
        needsSelection(() -> selected() != null, restore);
        rebuild();
        list.restore(keepList);
    }

    /**
     * The value control for a setting: a choice (the editor's key, the GUI scale) is a dropdown, a number a
     * field.
     */
    private void showValue(OptionsOps.Setting s) {
        valueInput.show(OptionsOps.typeOf(s), s.value());
        valueInput.setActive(s.kind() != OptionsOps.Kind.SWITCH);
    }

    private OptionsOps.Setting selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : settings.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            settings = list();
            rebuild();
            var s = selected();
            if (s != null) {
                showValue(s);
            }
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < settings.size(); i++) {
            OptionsOps.Setting s = settings.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i), s.label() + ": " + s.value(),
                    s.isDefault() ? "default" : "yours (default " + s.defaultValue() + ")",
                    s.isDefault() ? Palette.TEXT : Palette.EDITED));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        OptionsOps.Setting s = list == null ? null : selected();
        String help = s == null ? "Select a setting. Yellow values are yours."
                : s.help() + " Applies: " + s.applies();
        return com.strata.ui.screen.ListScreenLayouts.settings(frame(), this.font::width, title(),
                toolLabels(), help,
                s != null && s.kind() == OptionsOps.Kind.SWITCH, s != null && s.kind() != OptionsOps.Kind.SWITCH,
                where());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        if (layout.get("footer") != null) {
            text(graphics, layout.get("footer"), Palette.MUTED);
        }
    }
}
