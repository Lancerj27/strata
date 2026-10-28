package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.ChestsOps;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One loot entry of a chest's table (as LootEntryScreen and EquipmentItemScreen): the item, its weight, its
 * count.
 */
public class ChestEntryScreen extends StrataScreen {

    private final String table;
    private final ChestsOps.Entry entry;
    private final int pool;

    private final UndoManager<ChestsOps.EntryForm> undo = new UndoManager<>();
    private ChestsOps.EntryForm current;
    private ChestsOps.EntryForm baseline;
    private ChestsOps.EntryForm pending;
    private boolean syncing;

    private String selectedItem;
    private Button itemButton;
    private EditField weightField;
    private EditField minField;
    private EditField maxField;
    private Button saveButton;
    private Button undoButton;

    public ChestEntryScreen(String table, String tableName, ChestsOps.Entry entry, int pool) {
        super(entry == null ? "Strata — New loot entry: " + tableName
                : "Strata — Loot entry: " + Labels.itemName(entry.item()));
        this.table = table;
        this.entry = entry;
        this.pool = pool;
        this.current = ChestsOps.formFor(entry);
    }

    private com.strata.ui.Layout layout(String problem) {
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.chestEntry(frame(), this.font::width,
                title(), selectedItem == null || selectedItem.isEmpty() ? "Pick an item..." : Labels.itemName(selectedItem),
                "Pool " + (pool + 1) + ". From the next world load, to loot not yet generated.", problem);
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
        selectedItem = current.item();
        com.strata.ui.Layout layout = layout(null);
        itemButton = addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("item")),
                b -> openItemPicker()).build(), layout.get("item")));
        weightField = field(0, 0, 0, 0, "Weight", current.weight(), EditField.nonNegativeIntegerValidator(), this::edited);
        place(weightField.getWidget(), layout.get("weight"));
        minField = field(0, 0, 0, 0, "Min", current.countMin(), EditField.nonNegativeIntegerValidator(), this::edited);
        place(minField.getWidget(), layout.get("min"));
        maxField = field(0, 0, 0, 0, "Max", current.countMax(), EditField.nonNegativeIntegerValidator(), this::edited);
        place(maxField.getWidget(), layout.get("max"));

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        if (pending != null) {
            ChestsOps.EntryForm snap = pending;
            pending = null;
            apply(snap);
            current = snap;
        } else {
            current = read();
        }
        if (baseline == null) {
            baseline = current;
        }
        refreshButtons();
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(read());
    }

    private void openItemPicker() {
        pending = read();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick an item", RegistryQuery.itemsAndVariants(), id -> {
            undo.pushState(current);
            ChestsOps.EntryForm base = pending;
            pending = new ChestsOps.EntryForm(id, base.weight(), base.countMin(), base.countMax());
        }));
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = read();
        refreshButtons();
    }

    private ChestsOps.EntryForm read() {
        return new ChestsOps.EntryForm(selectedItem, weightField.getValue(), minField.getValue(), maxField.getValue());
    }

    private void apply(ChestsOps.EntryForm f) {
        syncing = true;
        selectedItem = f.item();
        itemButton.setMessage(Component.literal(layout(null).textOf("item")));
        weightField.setValue(f.weight());
        minField.setValue(f.countMin());
        maxField.setValue(f.countMax());
        syncing = false;
    }

    private void undo() {
        ChestsOps.EntryForm prev = undo.undo();
        if (prev != null) { apply(prev); current = prev; refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = ChestsOps.entryProblem(entry, read()) == null;
    }

    private void save() {
        if (!saveButton.active) return;
        if (!report(ChestsOps.saveEntry(table, entry, pool, read()))) {
            return;
        }
        // A SMALL FORM GOES BACK TO ITS LIST.
        backSaved();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout(ChestsOps.entryProblem(entry, read()));
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("footer1"), Palette.MUTED);
        if (layout.get("footer2") != null) {
            text(graphics, layout.get("footer2"), Palette.ERROR);
        }
        tooltip(graphics, mouseX, mouseY, layout.get("item"), "Click to pick the item this entry gives");
        tooltip(graphics, mouseX, mouseY, layout.get("weight"),
                "Its share of the pool's picks: weight 10 beside a total of 40 is picked a quarter of the time. 0: never.");
        tooltip(graphics, mouseX, mouseY, layout.get("min"), "The fewest items it gives when picked");
        tooltip(graphics, mouseX, mouseY, layout.get("max"), "The most items it gives when picked");
    }
}

