package com.strata.client.screen;

import com.strata.client.widget.BrowseList;
import com.strata.domain.AreaRulesOps;
import com.strata.domain.SpecialSpawnsOps;
import com.strata.domain.WriteReport;
import com.strata.ui.Palette;
import com.strata.util.Labels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** A tier's or a biome's Rules: the five spawn rules and the Special mob spawns switch. */
public class RulesScreen extends StrataScreen {

    /** The Special mob spawns switch's row id. */
    private static final String SPECIAL = "special";

    private final String scope;
    private final String scopeId;
    private BrowseList list;
    private List<AreaRulesOps.Row> rows = List.of();

    public RulesScreen(String scope, String scopeId) {
        super("Strata — Rules: " + Labels.scope(scope, scopeId));
        this.scope = scope;
        this.scopeId = scopeId;
    }

    private String selectedId() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : sel.id;
    }

    private com.strata.ui.Layout layout() {
        String id = selectedId();
        return com.strata.ui.screen.ListScreenLayouts.rulesList(frame(), this.font::width, title(), id != null,
                id != null && !SPECIAL.equals(id), "Yellow: set here. Grey: from the tier, or the default.");
    }

    @Override
    protected void init() {
        super.init();
        rows = AreaRulesOps.rows(scope, scopeId);
        BrowseList.State keep = list == null ? null : list.state();
        list = null;
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("RulesScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        Button on = Button.builder(Component.literal("On"), b -> set(Boolean.TRUE)).build();
        Button off = Button.builder(Component.literal("Off"), b -> set(Boolean.FALSE)).build();
        Button def = Button.builder(Component.literal("Default"), b -> set(null)).build();
        placeAll(layout, new String[]{"on", "off", "default"}, on, off, def);
        needsSelection(() -> selectedId() != null, on, off);
        needsSelection(() -> selectedId() != null && !SPECIAL.equals(selectedId()), def);

        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AreaRulesOps.Row r = rows.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i), r.label() + ": " + (r.effective() ? "On" : "Off")
                    + (r.own() == null ? "  (default)" : ""), r.help(), r.own() == null ? Palette.INHERITED : Palette.EDITED));
        }
        String special = SpecialSpawnsOps.switchText(scope, scopeId);
        entries.add(new BrowseList.Entry(SPECIAL, special.replace("Special spawns here", "Special mob spawns"),
                "Whether the special spawners (phantoms, patrols, sieges, the wandering trader) run here.", Palette.TEXT));
        list.refresh(entries);
        list.restore(keep);
    }

    private void set(Boolean value) {
        String id = selectedId();
        if (id == null) {
            return;
        }
        if (SPECIAL.equals(id)) {
            boolean on = SpecialSpawnsOps.switchText(scope, scopeId).endsWith("On");
            if (value != null && value != on) {
                act(SpecialSpawnsOps.toggleSwitch(scope, scopeId));
            }
            return;
        }
        act(AreaRulesOps.set(scope, scopeId, rows.get(Integer.parseInt(id)).rule(), value));
    }

    private void act(WriteReport report) {
        if (report(report)) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), Palette.MUTED);
    }
}
