package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ValueInput;
import com.strata.domain.SpecialSpawnsOps;
import com.strata.domain.WriteReport;
import com.strata.model.SpecialSpawners;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** One special spawner's values (the second screen of SpecialMobsScreen, which lists the spawners). */
public class SpecialSpawnsScreen extends StrataScreen {

    private static final String GROUP = "grp:";

    private final String scope;
    private final String scopeId;
    private List<SpecialSpawnsOps.Value> values = List.of();
    private BrowseList list;
    private ValueInput valueInput;

    /** The one spawner this sheet shows (the list's second screen). */
    private final String spawner;

    /**
     * @param scope "global", "tier", or "biome"
     * @param spawner the spawner whose values it shows (SpecialSpawners#SPAWNERS)
     */
    public SpecialSpawnsScreen(String scope, String scopeId, String spawner) {
        super("Strata — Special spawns: " + Labels.scope(scope, scopeId) + " — "
                + SpecialSpawners.SPAWNERS.getOrDefault(spawner, spawner));
        this.scope = scope;
        this.scopeId = scopeId == null ? "" : scopeId;
        this.spawner = spawner;
    }

    /** This spawner's values at this scope. */
    private List<SpecialSpawnsOps.Value> load() {
        return SpecialSpawnsOps.values(scope, scopeId).stream()
                .filter(v -> v.setting().spawner().equals(spawner)).toList();
    }

    @Override
    protected void init() {
        super.init();
        values = load();
        BrowseList.State keepList = list == null ? null : list.state();
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("SpecialSpawnsScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            SpecialSpawnsOps.Value v = selected();
            if (v != null) {
                valueInput.show(SpecialSpawnsOps.typeOf(v.setting()), SpecialSpawnsOps.shown(v.setting(), v.effective()));
            }
        });
        // A double-click turns a switch, or opens the picker (it presses the row's action).
        list.setOnActivate(entry -> {
            SpecialSpawnsOps.Value v = selected();
            if (v == null) {
                return;
            }
            if (v.setting().kind() == SpecialSpawners.Kind.SWITCH) {
                act(SpecialSpawnsOps.toggle(scope, scopeId, v));
            }
        });
        addRenderableWidget(list);

        valueInput = valueInput(layout, "value", this::setValue);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> setValue()).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
            SpecialSpawnsOps.Value v = selected();
            if (v != null) act(SpecialSpawnsOps.restore(scope, scopeId, v.setting()));
        }).build();
        Button restoreAll = Button.builder(Component.literal("Restore all"), b -> minecraft.setScreen(
                new com.strata.client.widget.ConfirmDialog(this, "Restore every special spawn value of "
                        + Labels.scope(scope, scopeId) + "?\nThe wider scope's, or the game's, apply again.",
                        () -> act(SpecialSpawnsOps.restoreAll(scope, scopeId))))).build();
        placeAll(layout, new String[]{"set", "restore", "restoreAll"}, set, restore, restoreAll);
        needsSelection(() -> selected() != null, set, restore);
        rebuild();
        list.restore(keepList);
    }

    private SpecialSpawnsOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(GROUP) ? null : values.get(Integer.parseInt(sel.id));
    }

    /** Set value: what the control holds, to the selected row. */
    private void setValue() {
        SpecialSpawnsOps.Value v = selected();
        if (v != null) {
            act(SpecialSpawnsOps.save(scope, scopeId, v.setting(),
                    SpecialSpawnsOps.idOf(v.setting(), valueInput.value())));
        }
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = load();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        java.util.Set<String> made = new java.util.HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            SpecialSpawnsOps.Value v = values.get(i);
            String group = GROUP + v.setting().spawner();
            if (made.add(group)) {
                entries.add(new BrowseList.Entry(group, SpecialSpawners.SPAWNERS.get(v.setting().spawner()), "",
                        Palette.GROUP, group, null, 0));
            }
            String value = v.setting().kind() == SpecialSpawners.Kind.MOB ? Labels.creature(v.effective())
                    : SpecialSpawnsOps.shown(v.setting(), v.effective());
            String game = v.setting().kind() == SpecialSpawners.Kind.MOB ? Labels.creature(v.setting().game())
                    : SpecialSpawnsOps.shown(v.setting(), v.setting().game());
            String sub = v.setting().what() + switch (v.from()) {
                case "yours" -> "  (yours; the game's: " + game + ")";
                case "tier" -> "  (from the tier; the game's: " + game + ")";
                case "Global" -> "  (from Global; the game's: " + game + ")";
                default -> "  (the game's)";
            };
            entries.add(new BrowseList.Entry(String.valueOf(i), v.setting().name() + ": " + value, sub,
                    v.from().equals("yours") ? Palette.EDITED : Palette.TEXT, null, group, 1));
        }
        list.refresh(entries);
    }

    private com.strata.ui.Layout layout() {
        SpecialSpawnsOps.Value v = list == null ? null : selected();
        String help = v == null
                ? "The game's special spawners and villagers. Select a value to change it; a double-click turns a switch."
                + (scope.equals("global") ? "" : " The spawners' clocks are set on Global.")
                : v.setting().name() + ": " + v.setting().what();
        return com.strata.ui.screen.ListScreenLayouts.specialSpawns(frame(), this.font::width, title(), help,
                "Yellow values are this scope's. They apply at once.", null);
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

