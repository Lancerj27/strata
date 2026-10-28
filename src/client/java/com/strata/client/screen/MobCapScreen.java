package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ValueInput;
import com.strata.domain.MobCapOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The mob caps of each category, editable: how many monsters, creatures, and the rest a chunk area holds, the
 * despawn distance, and the friendly and persistent flags.
 */
public class MobCapScreen extends StrataScreen {

    private List<MobCapOps.Value> values = List.of();
    private BrowseList list;
    private ValueInput valueInput;

    public MobCapScreen() {
        super("Strata — Mobs: Mob caps");
    }

    @Override
    protected void init() {
        super.init();
        values = MobCapOps.editable(MobCapOps.values());
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("MobCapScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            if (!entry.id.startsWith(GROUP)) {
                var row = values.get(Integer.parseInt(entry.id));
                valueInput.show(MobCapOps.typeOf(row), row.effective());
            }
        });
        addRenderableWidget(list);

        valueInput = valueInput(layout, "value", null);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> {
                    var v = selected();
                    if (v != null) act(MobCapOps.save(v, valueInput.value()));
                })
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(MobCapOps.restore(v));
                })
                .build();
        placeAll(layout, new String[]{"set", "restore"}, set, restore);
        needsSelection(() -> selected() != null, set, restore);
        rebuild();
        list.restore(keepList);
    }

    /** A category group's id prefix. */
    private static final String GROUP = "cat:";

    private MobCapOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(GROUP) ? null : values.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = MobCapOps.editable(MobCapOps.values());
            rebuild();
        }
    }

    private void rebuild() {
        // A GROUP PER CATEGORY, NAMING ITS MOBS, and each value's row says what it does.
        List<BrowseList.Entry> entries = new ArrayList<>();
        java.util.Set<String> made = new java.util.HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            MobCapOps.Value v = values.get(i);
            // Locked values only when asked for (Options > Show locked values).
            if (MobCapOps.lockReason(v) != null && !com.strata.domain.OptionsOps.viewLocked()) {
                continue;
            }
            String group = GROUP + v.category();
            if (made.add(group)) {
                entries.add(new BrowseList.Entry(group, MobCapOps.category(v.category()), "", Palette.GROUP, group, null, 0));
            }
            String sub = MobCapOps.what(v) + (v.own().isEmpty() ? "" : "  (yours; the game's: " + v.game() + ")");
            entries.add(new BrowseList.Entry(String.valueOf(i), MobCapOps.name(v) + ": " + v.effective(), sub,
                    v.own().isEmpty() ? Palette.TEXT : Palette.EDITED, null, group, 1));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        MobCapOps.Value v = list == null ? null : selected();
        String lock = v == null ? null : MobCapOps.lockReason(v);
        String help = v == null ? "Each kind of mob has its own caps. Select a value to change it. Yellow values are yours."
                : lock != null ? "Locked: " + lock + "."
                : MobCapOps.name(v) + ": " + MobCapOps.what(v) + " The game's: " + v.game() + ". A change works at once.";
        String footer = null;
        String empty = values.isEmpty() ? "No caps read: the extraction at game start failed (see the log)." : null;
        return com.strata.ui.screen.ListScreenLayouts.valueSheet(frame(), this.font::width, title(), 100, help,
                footer, empty);
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
        emptyNote(graphics, layout, list);
    }
}
