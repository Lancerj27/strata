package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.EditField;
import com.strata.client.widget.UndoManager;
import com.strata.domain.TradesOps;
import com.strata.game.RegistryQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One trade (as ChestEntryScreen), in the game's words: cost A and its count, an optional cost B, the result
 * and its count, max uses, XP, and the price multiplier.
 */
public class TradeEntryScreen extends StrataScreen {

    private static final String[] ITEMS = {"costA", "costB", "result"};

    private final String scope;
    private final String scopeId;
    private final String list;
    private final TradesOps.TradeRow row;

    private final UndoManager<TradesOps.TradeForm> undo = new UndoManager<>();
    private TradesOps.TradeForm current;
    private TradesOps.TradeForm baseline;
    private TradesOps.TradeForm pending;
    private boolean syncing;

    private final String[] items = new String[3];
    private final Button[] itemButtons = new Button[3];
    private EditField costACount;
    private EditField costBCount;
    private EditField resultCount;
    private EditField maxUses;
    private EditField xp;
    private EditField priceMultiplier;
    private Button saveButton;
    private Button undoButton;

    public TradeEntryScreen(String scope, String scopeId, String list, TradesOps.TradeRow row) {
        super((row == null ? "Strata — New trade: " : "Strata — Trade: ") + TradesOps.listName(
                list.substring(0, list.lastIndexOf('|')), Integer.parseInt(list.substring(list.lastIndexOf('|') + 1))));
        this.scope = scope;
        this.scopeId = scopeId;
        this.list = list;
        this.row = row;
        this.current = TradesOps.formFor(row == null ? null : row.trade());
    }

    private static String itemText(String id, String none) {
        return id == null || id.isEmpty() ? none : Labels.itemName(id);
    }

    private com.strata.ui.Layout layout(String problem) {
        String note = row != null && !row.trade().plain()
                ? "The game's trade gives " + row.trade().note() + ". A save makes it a trade of fixed items."
                : "From the next time a trader reaches this level, " + Labels.scope(scope, scopeId) + ".";
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.tradeEntry(frame(), this.font::width,
                title(), itemText(items[0], "Pick an item..."), itemText(items[1], "None (pick an item...)"),
                itemText(items[2], "Pick an item..."), note, problem);
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
        items[0] = current.costA();
        items[1] = current.costB();
        items[2] = current.result();
        com.strata.ui.Layout layout = layout(null);
        for (int i = 0; i < 3; i++) {
            int at = i;
            itemButtons[i] = addRenderableWidget(place(Button.builder(Component.literal(layout.textOf(ITEMS[i])),
                    b -> openItemPicker(at)).build(), layout.get(ITEMS[i])));
        }
        costACount = placed(layout, "costACount", "Count", current.costACount(), EditField.nonNegativeIntegerValidator());
        costBCount = placed(layout, "costBCount", "Count", current.costBCount(), EditField.nonNegativeIntegerValidator());
        resultCount = placed(layout, "resultCount", "Count", current.resultCount(), EditField.nonNegativeIntegerValidator());
        maxUses = placed(layout, "maxUses", "Max uses", current.maxUses(), EditField.nonNegativeIntegerValidator());
        xp = placed(layout, "xp", "XP", current.xp(), EditField.nonNegativeIntegerValidator());
        priceMultiplier = placed(layout, "priceMultiplier", "Price multiplier", current.priceMultiplier(),
                EditField.numericValidator());

        undoButton = Button.builder(Component.literal("Undo"), b -> undo()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, saveButton);
        if (pending != null) {
            TradesOps.TradeForm snap = pending;
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

    private EditField placed(com.strata.ui.Layout layout, String id, String label, String value, EditField.Check check) {
        EditField f = field(0, 0, 0, 0, label, value, check, this::edited);
        place(f.getWidget(), layout.get(id));
        return f;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && !baseline.equals(read());
    }

    private void openItemPicker(int which) {
        pending = read();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick an item", RegistryQuery.itemsAndVariants(), id -> {
            undo.pushState(current);
            TradesOps.TradeForm b = pending;
            pending = switch (which) {
                case 0 -> new TradesOps.TradeForm(id, b.costACount(), b.costB(), b.costBCount(), b.result(),
                        b.resultCount(), b.maxUses(), b.xp(), b.priceMultiplier());
                // A picked second item starts at a count of 1.
                case 1 -> new TradesOps.TradeForm(b.costA(), b.costACount(), id,
                        "0".equals(b.costBCount().strip()) ? "1" : b.costBCount(), b.result(), b.resultCount(),
                        b.maxUses(), b.xp(), b.priceMultiplier());
                default -> new TradesOps.TradeForm(b.costA(), b.costACount(), b.costB(), b.costBCount(), id,
                        b.resultCount(), b.maxUses(), b.xp(), b.priceMultiplier());
            };
        }));
    }

    private void edited() {
        if (syncing) return;
        undo.pushState(current);
        current = read();
        refreshButtons();
    }

    private TradesOps.TradeForm read() {
        return new TradesOps.TradeForm(items[0], costACount.getValue(), items[1], costBCount.getValue(), items[2],
                resultCount.getValue(), maxUses.getValue(), xp.getValue(), priceMultiplier.getValue());
    }

    private void apply(TradesOps.TradeForm f) {
        syncing = true;
        items[0] = f.costA();
        items[1] = f.costB();
        items[2] = f.result();
        com.strata.ui.Layout layout = layout(null);
        for (int i = 0; i < 3; i++) {
            itemButtons[i].setMessage(Component.literal(layout.textOf(ITEMS[i])));
        }
        costACount.setValue(f.costACount());
        costBCount.setValue(f.costBCount());
        resultCount.setValue(f.resultCount());
        maxUses.setValue(f.maxUses());
        xp.setValue(f.xp());
        priceMultiplier.setValue(f.priceMultiplier());
        syncing = false;
    }

    private void undo() {
        TradesOps.TradeForm prev = undo.undo();
        if (prev != null) { apply(prev); current = prev; refreshButtons(); }
    }

    private void refreshButtons() {
        undoButton.active = undo.canUndo();
        saveButton.active = TradesOps.problem(read()) == null;
    }

    private void save() {
        if (!saveButton.active) return;
        if (!report(TradesOps.save(scope, scopeId, list, row, read()))) {
            return;
        }
        // A SMALL FORM GOES BACK TO ITS LIST.
        backSaved();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout(TradesOps.problem(read()));
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("footer1"), Palette.MUTED);
        if (layout.get("footer2") != null) {
            text(graphics, layout.get("footer2"), Palette.ERROR);
        }
        tooltip(graphics, mouseX, mouseY, layout.get("costA"), "Click to pick the first item the trader wants");
        tooltip(graphics, mouseX, mouseY, layout.get("costB"),
                "Click to pick a second item the trader wants. A count of 0: no second item.");
        tooltip(graphics, mouseX, mouseY, layout.get("result"), "Click to pick the item the trader gives");
        tooltip(graphics, mouseX, mouseY, layout.get("maxUses"),
                "How many times the trade works before the trader restocks it at its workstation");
        tooltip(graphics, mouseX, mouseY, layout.get("xp"), "The XP the trader earns toward its next level");
        tooltip(graphics, mouseX, mouseY, layout.get("priceMultiplier"),
                "How much demand and your reputation move the price of cost A. The game uses 0.05 or 0.2.");
    }
}

