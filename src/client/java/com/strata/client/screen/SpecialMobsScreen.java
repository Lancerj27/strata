package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.domain.SpecialSpawnsOps;
import com.strata.domain.WriteReport;
import com.strata.model.SpecialSpawners;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Special mobs at a scope, laid out as the mob list: the Special mob spawns switch on top; a Spawners group,
 * one row per spawner with its breakdown (On or Off and its first values); a Special mobs group, the mobs set
 * to Spawn as Special.
 */
public class SpecialMobsScreen extends StrataScreen {

    private static final String SPAWNERS = "grp:spawners";
    private static final String MOBS = "grp:mobs";
    private static final String SPAWNER = "spawner:";

    private final String scope;
    private final String scopeId;
    private List<SpecialSpawnsOps.Value> values = List.of();
    private BrowseList list;
    private Button switchButton;

    /** @param scope "global", "tier", or "biome" */
    public SpecialMobsScreen(String scope, String scopeId) {
        super("Strata — Special spawns: " + Labels.scope(scope, scopeId));
        this.scope = scope;
        this.scopeId = scopeId == null ? "" : scopeId;
    }

    @Override
    protected void init() {
        super.init();
        values = SpecialSpawnsOps.values(scope, scopeId);
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old list.
        list = null;
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        switchButton = Button.builder(Component.literal(layout.textOf("switch")), b -> {
            SpecialSpawnsOps.Value main = SpecialSpawnsOps.mainSwitch(values);
            if (main != null) act(SpecialSpawnsOps.toggle(scope, scopeId, main));
        }).build();
        placeAll(layout, new String[]{"switch"}, switchButton);
        switchButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                SpecialSpawners.setting(SpecialSpawners.SPECIAL, "enabled").what())));

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("SpecialMobsScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        Button restoreAll = Button.builder(Component.literal("Restore all"), b -> minecraft.setScreen(
                new ConfirmDialog(this, "Restore every special spawn value of " + Labels.scope(scope, scopeId)
                        + "?\nThe wider scope's, or the game's, apply again.",
                        () -> act(SpecialSpawnsOps.restoreAll(scope, scopeId))))).build();
        placeAll(layout, new String[]{"edit", "restoreAll"}, edit, restoreAll);
        needsSelection(() -> selectedSpawner() != null || selectedMob() != null, edit);
        // A double-click presses Edit.
        list.setOnActivate(entry -> editSelected());
        rebuild();
        list.restore(keepList);
    }

    private com.strata.ui.Layout layout() {
        SpecialSpawnsOps.Value main = SpecialSpawnsOps.mainSwitch(values);
        return com.strata.ui.screen.ListScreenLayouts.specialMobsList(frame(), this.font::width, title(),
                "Special mob spawns: " + (main == null ? SpecialSpawners.ON : main.effective()),
                list != null && (selectedSpawner() != null || selectedMob() != null),
                "Yellow rows have this scope's own values. Changes apply at once.");
    }

    /** The selected spawner, or null (a group or a mob row). */
    private String selectedSpawner() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || !sel.id.startsWith(SPAWNER) ? null : sel.id.substring(SPAWNER.length());
    }

    /** The selected mob row's entity id, or null (a group or a spawner row). */
    private String selectedMob() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || !sel.id.startsWith("mob:") ? null : sel.id.substring("mob:".length());
    }

    /** A spawner opens its values; a mob opens that mob's editor, where Spawn as Special is set. */
    private void editSelected() {
        String spawner = selectedSpawner();
        if (spawner != null) {
            ScreenNavigator.get().navigateTo(new SpecialSpawnsScreen(scope, scopeId, spawner));
            return;
        }
        String mob = selectedMob();
        if (mob != null) {
            ScreenNavigator.get().navigateTo(new MobEditScreen(null, mob, "global", "", true));
        }
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = SpecialSpawnsOps.values(scope, scopeId);
            switchButton.setMessage(Component.literal(layout().textOf("switch")));
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        List<String> spawners = SpecialSpawnsOps.spawners();
        entries.add(new BrowseList.Entry(SPAWNERS, "Spawners", com.strata.ui.Words.count(spawners.size(), "spawner", "spawners"), Palette.GROUP,
                SPAWNERS, null, 0));
        for (String spawner : spawners) {
            boolean yours = values.stream().anyMatch(v -> v.setting().spawner().equals(spawner) && v.from().equals("yours"));
            entries.add(new BrowseList.Entry(SPAWNER + spawner, SpecialSpawners.SPAWNERS.get(spawner),
                    SpecialSpawnsOps.summary(spawner, values), yours ? Palette.EDITED : Palette.TEXT, null, SPAWNERS, 1));
        }
        List<String> mobs = SpecialSpawnsOps.specialMobs().stream().sorted().toList();
        entries.add(new BrowseList.Entry(MOBS, "Special mobs", com.strata.ui.Words.count(mobs.size(), "mob", "mobs"), Palette.GROUP, MOBS, null, 0));
        for (String mob : mobs) {
            entries.add(new BrowseList.Entry("mob:" + mob, Labels.creature(mob),
                    mob + "  Spawn as Special: set in its mob editor", Palette.INHERITED, null, MOBS, 1));
        }
        list.refresh(entries);
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

