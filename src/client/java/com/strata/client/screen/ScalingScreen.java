package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.domain.ScalingOps;
import com.strata.domain.OptionsOps;
import com.strata.domain.WriteReport;
import com.strata.runtime.AttributeCurve;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Scaling curve browser: the mob curves, or the player curves. */
public class ScalingScreen extends StrataScreen {

    /** The curve formula, stated per scope so the two tabs say only what they do. */
    private static String formula(boolean isMob) {
        return isMob
                ? "scaled = base × (1 + per_level × level^exponent × coefficient)"
                : "scaled = base × (1 + per_level × level^exponent)   (level = player XP level)";
    }

    private BrowseList list;
    private String status = "";
    private List<ScalingOps.CurveRow> allRows = List.of();
    /** The mob curves (true) or the player curves (false). */
    private final boolean showingMobs;

    public ScalingScreen(boolean mobs) {
        super(mobs ? "Strata — Mob scaling" : "Strata — Player scaling");
        this.showingMobs = mobs;
    }

    @Override
    protected String helpKey() {
        return showingMobs ? "ScalingScreen:mob" : "ScalingScreen:player";
    }

    @Override
    protected void init() {
        super.init();
        // init runs again on the return from the curve editor.
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        // THE SYSTEM SWITCH, on its tab: player scaling was off in the file and no screen said so.
        switchButton = Button.builder(Component.literal(layout.textOf("switch")),
                b -> act(OptionsOps.toggle(switchKey()))).build();
        placeAll(layout, new String[]{"switch"}, switchButton);
        if (showingMobs) {
            chunkButton = Button.builder(Component.literal(layout.textOf("chunk")),
                    b -> act(OptionsOps.toggle(OptionsOps.CHUNK_SCALING))).build();
            placeAll(layout, new String[]{"chunk"}, chunkButton);
        }

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ScalingScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);

        Button addButton = Button.builder(Component.literal("+ Add attribute"),
                b -> ScreenNavigator.get().navigateTo(new ScalingEditScreen(showingMobs, null))).build();
        Button editButton = Button.builder(Component.literal("Edit"), b -> {
            if (xpSelected()) {
                ScreenNavigator.get().navigateTo(ScalingEditScreen.forMobXp());
                return;
            }
            var row = selectedRow();
            if (row != null) ScreenNavigator.get().navigateTo(new ScalingEditScreen(showingMobs, row));
        }).build();
        // EVERY PICKED CURVE: On / Off and Remove act on all of them.
        Button toggleButton = Button.builder(Component.literal("On / Off"), b -> {
            List<ScalingOps.CurveRow> rows = pickedRows();
            if (!rows.isEmpty()) act(ScalingOps.toggle(showingMobs, rows));
        }).build();
        Button removeButton = Button.builder(Component.literal("Remove"),
                b -> confirmRemove()).build();
        placeAll(layout, new String[]{"add", "edit", "toggle", "remove"},
                addButton, editButton, toggleButton, removeButton);
        // The row actions grey out until something is highlighted.
        needsSelection(() -> (selectedRow() != null || xpSelected()) && !list.manyPicked(), editButton);
        // The XP row has no switch of its own and is not removable: say so.
        needsSelection(() -> !pickedRows().isEmpty(),
                () -> xpSelected() ? "The XP curve is always here: Mob scaling, above, turns it on and off"
                        : "Select a curve in the list first", toggleButton, removeButton);

        refresh();
        list.restore(keepList);
    }

    /** The layout for the tab and the selection as they stand. */
    private com.strata.ui.Layout layout() {
        boolean hint = list != null && selectedRow() == null && !allRows.isEmpty();
        boolean on = switchOn();
        String name = showingMobs ? "Mob scaling" : "Player scaling";
        return com.strata.ui.screen.ListScreenLayouts.scaling(frame(), this.font::width, title(), showingMobs,
                name + ": " + OptionsOps.onOff(on),
                showingMobs ? "Chunk scaling: " + OptionsOps.onOff(isOn(OptionsOps.CHUNK_SCALING)) : null,
                hint ? "Select a curve to edit, switch on or off, or remove it" : "",
                on ? formula(showingMobs) : name + " is Off: these curves do nothing until it is On.", status);
    }

    /** The shown tab's system switch. */
    private String switchKey() {
        return showingMobs ? OptionsOps.MOB_SCALING : OptionsOps.PLAYER_SCALING;
    }

    private boolean switchOn() {
        return isOn(switchKey());
    }

    private static boolean isOn(String key) {
        OptionsOps.Setting s = OptionsOps.find(key);
        return s != null && "On".equals(s.value());
    }

    private Button switchButton;
    private Button chunkButton;
    /** Player Base Stats: on the Player scaling screen only. */

    private void refresh() {
        allRows = ScalingOps.curves(showingMobs);
        rebuild();
        if (switchButton != null) {
            com.strata.ui.Layout layout = layout();
            switchButton.setMessage(Component.literal(layout.textOf("switch")));
            if (chunkButton != null) {
                chunkButton.setMessage(Component.literal(layout.textOf("chunk")));
            }
        }
    }

    /**
     * Removes the picked curves after asking, naming them (a curve's delete always asks;: every picked
     * curve).
     */
    private void confirmRemove() {
        List<ScalingOps.CurveRow> rows = pickedRows();
        if (rows.isEmpty()) return;
        String names = com.strata.domain.Picks.listed(rows.stream().map(r -> Labels.attribute(r.attribute())).toList());
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Remove the " + (showingMobs ? "mob" : "player") + " curve" + (rows.size() > 1 ? "s" : "")
                        + " for " + names + "?\nTheir values are deleted.",
                () -> act(ScalingOps.remove(showingMobs, rows))));
    }

    /** The picked curves, top to bottom (BrowseList#pickedIds). */
    private List<ScalingOps.CurveRow> pickedRows() {
        if (list == null) return List.of();
        List<ScalingOps.CurveRow> out = new ArrayList<>();
        for (String id : list.pickedIds()) {
            try {
                int idx = Integer.parseInt(id);
                if (idx >= 0 && idx < allRows.size()) out.add(allRows.get(idx));
            } catch (NumberFormatException e) {
                // The XP row: not a curve.
            }
        }
        return out;
    }

    /** Shows what a curve action reported, and redraws the rows after it. */
    private void act(WriteReport result) {
        if (report(result)) {
            refresh();
        }
    }

    /** The mob XP row's id: the XP curve, on the Mob tab. */
    private static final String XP_ROW = "xp";

    /** True when the mob XP row is selected. */
    private boolean xpSelected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel != null && XP_ROW.equals(sel.id);
    }

    /** The highlighted curve, or null when nothing is selected. */
    private ScalingOps.CurveRow selectedRow() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel == null) return null;
        int idx;
        try { idx = Integer.parseInt(sel.id); }
        catch (NumberFormatException e) { return null; }
        return idx >= 0 && idx < allRows.size() ? allRows.get(idx) : null;
    }

    private String subtitle(ScalingOps.CurveRow row) {
        if (!row.enabled()) return "disabled";
        // The production curve's own static, so the preview cannot drift from what the runtime applies
        // (start_level included).
        double bonus10 = AttributeCurve.bonus(row.perLevel(), row.exponent(), 10, row.startLevel());
        String start = row.startLevel() > 1 ? "  (curve begins at lv " + row.startLevel() + ")" : "";
        return ScalingOps.shapeOf(row.exponent()) + "  +" + Labels.number(bonus10 * 100) + "% at "
                + (showingMobs ? "mob level" : "XP level") + " 10" + start
                + "  (per=" + Labels.number(row.perLevel()) + " exp=" + Labels.number(row.exponent()) + ")";
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        if (showingMobs) {
            // THE MOB XP CURVE IS HERE: it sat in Settings, away from the other curves.
            OptionsOps.Setting per = OptionsOps.find(OptionsOps.MOB_XP_PER_LEVEL);
            OptionsOps.Setting exp = OptionsOps.find(OptionsOps.MOB_XP_EXPONENT);
            double p = Double.parseDouble(per.value());
            double e = Double.parseDouble(exp.value());
            double at10 = new com.strata.runtime.MobXpCurve(p, e).multiplierAt(10);
            entries.add(new BrowseList.Entry(XP_ROW, "XP a mob drops",
                    "x" + Labels.number(at10) + " the game's XP at mob level 10  (per=" + Labels.number(p)
                            + " exp=" + Labels.number(e) + ")",
                    per.isDefault() && exp.isDefault() ? Palette.TEXT : Palette.EDITED));
        }
        for (int i = 0; i < allRows.size(); i++) {
            ScalingOps.CurveRow row = allRows.get(i);
            // What the attribute does, on hover.
            entries.add(new BrowseList.Entry(String.valueOf(i),
                    Labels.attribute(row.attribute()), subtitle(row),
                    row.enabled() ? Palette.TEXT : Palette.MUTED)
                    .tooltip(com.strata.model.AttributeGuide.scaled(row.attribute(), !showingMobs)));
        }
        list.refresh(entries);
        status = (showingMobs ? "Mob" : "Player") + " scaling: " + com.strata.ui.Words.count(entries.size(), "attribute", "attributes");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.MUTED);
        text(graphics, layout.get("hint1"), Palette.LABEL);
        text(graphics, layout.get("footer"), Palette.MUTED);
    }
}
