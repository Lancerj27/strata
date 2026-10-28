package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.ChestsOps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A chest pool's rolls: how many times the pool picks one of its loot entries, from min to max. */
public class ChestPoolScreen extends StrataScreen {

    private record Rolls(String min, String max) {
    }

    private final String table;
    private final ChestsOps.Pool pool;
    private final UndoManager<Rolls> undo = new UndoManager<>();
    private Rolls current;
    private Rolls baseline;
    private boolean syncing;
    private EditField minField;
    private EditField maxField;
    private Button saveButton;
    private Button undoButton;

    public ChestPoolScreen(String table, String tableName, ChestsOps.Pool pool) {
        super("Strata — " + tableName + ": pool " + (pool.index() + 1));
        this.table = table;
        this.pool = pool;
        this.current = new Rolls(pool.rollsMin(), pool.rollsMax());
    }

    private com.strata.ui.Layout layout(String problem) {
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.chestPool(frame(), this.font::width,
                title(), "From the next world load, to loot not yet generated.", problem);
        useLayout(built);
        return built;
    }

    @Override
    protected boolean scrolls() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        com.strata.ui.Layout layout = layout(null);
        minField = field(0, 0, 0, 0, "Min", current.min(), EditField.nonNegativeIntegerValidator(), this::edited);
        place(minField.getWidget(), layout.get("min"));
        maxField = field(0, 0, 0, 0, "Max", current.max(), EditField.nonNegativeIntegerValidator(), this::edited);
        place(maxField.getWidget(), layout.get("max"));
        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        current = read();
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(read());
    }

    private Rolls read() {
        return new Rolls(minField.getValue(), maxField.getValue());
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = read();
        refreshButtons();
    }

    private void undo() {
        Rolls prev = undo.undo();
        if (prev != null) {
            syncing = true;
            minField.setValue(prev.min());
            maxField.setValue(prev.max());
            syncing = false;
            current = prev;
            refreshButtons();
        }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = ChestsOps.rollsProblem(pool, minField.getValue(), maxField.getValue()) == null;
    }

    private void save() {
        if (!saveButton.active) return;
        if (report(ChestsOps.saveRolls(table, pool, minField.getValue(), maxField.getValue()))) {
            backSaved();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout(ChestsOps.rollsProblem(pool, minField.getValue(), maxField.getValue()));
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("footer1"), Palette.MUTED);
        if (layout.get("footer2") != null) {
            text(graphics, layout.get("footer2"), Palette.ERROR);
        }
        tooltip(graphics, mouseX, mouseY, layout.get("min"), "The fewest times the pool picks an entry");
        tooltip(graphics, mouseX, mouseY, layout.get("max"), "The most times the pool picks an entry");
    }
}
