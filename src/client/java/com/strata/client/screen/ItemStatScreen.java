package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ValueInput;
import com.strata.domain.ItemStatsOps;
import com.strata.domain.RecipeOps;
import com.strata.domain.WriteReport;
import com.strata.model.CraftingRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One item: its values on the left, each with what it does and the game's number (Set value writes the
 * user's, Restore takes it away; the user's are yellow); on the right the crafting recipes that make it, one
 * at a time, on the game's own 3x3 grid.
 */
public class ItemStatScreen extends StrataScreen {

    /** The game's crafting table screen: its slots, arrow, and result slot are drawn from it. */
    private static final ResourceLocation CRAFTING = ResourceLocation.withDefaultNamespace(
            "textures/gui/container/crafting_table.png");

    private final String item;
    private List<ItemStatsOps.Value> values = List.of();
    /** Components picked to add and not set yet: listed with no value until Set value. */
    private final java.util.Set<String> pending = new java.util.LinkedHashSet<>();
    private BrowseList list;
    private ValueInput valueInput;

    private List<RecipeOps.Recipe> recipes = List.of();
    /** The recipe shown, by its place in {@link #recipes}. */
    private int shown;
    /** A new recipe is being made, not one of {@link #recipes}. */
    private boolean making;
    /** The grid as edited: nine cells, the result, the count, the type. */
    private List<String> cells;
    private String result = "";
    private String countText = "1";
    private String type = CraftingRecipe.SHAPED;
    private String newName = "";
    /** The grid as it was opened, to tell an edit. */
    private String opened = "";

    private EditField nameField;
    private EditField countField;
    private DropdownSelect typeSelect;
    private com.strata.ui.Layout layout;
    private Button removeButton;

    public ItemStatScreen(String item, String name) {
        super("Strata — Item: " + name);
        this.item = item;
    }

    @Override
    protected void init() {
        super.init();
        values = withPending();
        recipes = RecipeOps.forItem(item);
        if (cells == null) {
            openRecipe(Math.min(shown, Math.max(0, recipes.size() - 1)));
        }
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ItemStatScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            var row = values.get(Integer.parseInt(entry.id));
            valueInput.show(com.strata.model.ItemStats.typeOf(row.key()), row.effective());
        });
        addRenderableWidget(list);

        valueInput = valueInput(layout, "value", null);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> {
                    var v = selected();
                    if (v != null) act(ItemStatsOps.save(v, valueInput.value(), values));
                })
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(ItemStatsOps.restore(v));
                })
                .build();
        // + Attribute: any attribute of the registry the item lacks - armour on a sword.
        Button addAttribute = Button.builder(Component.literal("+ Add attribute"), b ->
                ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which attribute?",
                        com.strata.game.RegistryQuery.attributes(), picked -> act(ItemStatsOps.addAttribute(item, picked, values)))))
                .build();
        // + Component: any component of the registry - lore, dye colour, a mod's.
        Button addComponent = Button.builder(Component.literal("+ Add component"), b ->
                ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which component?",
                        componentOptions(ItemStatsOps.addableComponents(values)), picked -> {
                            pending.add(picked);
                            rebuildWidgets();
                        })))
                .build();
        placeAll(layout, new String[]{"set", "restore", "addAttribute", "addComponent"}, set, restore, addAttribute,
                addComponent);
        needsSelection(() -> selected() != null, set, restore);

        // THE RECIPE PANEL.
        removeButton = Button.builder(Component.literal(layout.textOf("removeRecipe")), b -> removeShown()).build();
        placeAll(layout, new String[]{"prev", "next", "addRecipe", "toggle", "removeRecipe", "save"},
                Button.builder(Component.literal("<"), b -> step(-1)).build(),
                Button.builder(Component.literal(">"), b -> step(1)).build(),
                Button.builder(Component.literal("+ Add recipe"), b -> startNew()).build(),
                Button.builder(Component.literal("On / Off"), b -> toggleShown()).build(),
                removeButton,
                Button.builder(Component.literal("Save recipe"), b -> saveShown()).build());
        if (making) {
            nameField = field(0, 0, 0, 0, "Name", newName, null, () -> newName = nameField.getValue());
            place(nameField.getWidget(), layout.get("name"));
        }
        countField = field(0, 0, 0, 0, "Count", countText, null, () -> countText = countField.getValue());
        place(countField.getWidget(), layout.get("count"));
        countField.getWidget().setEditable(hasGrid());
        // A station recipe shows its station in the type box.
        String station = RecipeOps.STATIONS.containsKey(type) || !type.startsWith("minecraft:") && !making
                && RecipeOps.isStation(current()) ? RecipeOps.stationName(type) : null;
        List<String> typeOptions;
        if (station == null) {
            typeOptions = CraftingRecipe.TYPES;
        } else if (RecipeOps.COOKING.contains(type)) {
            typeOptions = RecipeOps.COOKING.stream().map(RecipeOps.STATIONS::get).toList();
        } else {
            typeOptions = List.of(station);
        }
        typeSelect = new DropdownSelect(0, 0, 0, 0, "", typeOptions,
                station != null ? station : CraftingRecipe.TYPES.contains(type) ? type : CraftingRecipe.SHAPED);
        typeSelect.setOnChange(s -> type = RecipeOps.STATIONS.entrySet().stream().filter(e -> e.getValue().equals(s))
                .map(java.util.Map.Entry::getKey).findFirst().orElse(s));
        addRenderableWidget(place(typeSelect.getWidget(), layout.get("type")));
        typeSelect.getWidget().active = hasGrid();
        rebuild();
        list.restore(keepList);
    }

    // ---- the recipe shown ----

    private RecipeOps.Recipe current() {
        return making || recipes.isEmpty() ? null : recipes.get(shown);
    }

    /** A grid to edit: a new recipe, or a shown one the grid can hold (not fireworks and the like). */
    private boolean hasGrid() {
        RecipeOps.Recipe r = current();
        return making || (r != null && RecipeOps.editProblem(r) == null);
    }

    private void openRecipe(int index) {
        making = false;
        shown = Math.max(0, index);
        RecipeOps.Recipe r = current();
        if (r == null) {
            cells = new ArrayList<>(List.of("", "", "", "", "", "", "", "", ""));
            result = item;
            countText = "1";
            type = CraftingRecipe.SHAPED;
        } else {
            cells = new ArrayList<>(r.cells());
            result = r.result();
            countText = r.count();
            type = r.type();
        }
        opened = snapshot();
    }

    private String snapshot() {
        return String.join(",", cells) + "|" + result + "|" + countText + "|" + type + "|" + newName;
    }

    private boolean edited() {
        return cells != null && !snapshot().equals(opened);
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return edited();
    }

    /** Leaving a changed grid for another recipe asks first. */
    private void leaveGrid(Runnable then) {
        if (!edited()) {
            then.run();
            rebuildWidgets();
            return;
        }
        minecraft.setScreen(new ConfirmDialog(this, "Leave this recipe without saving?\nYour changes to its grid go.",
                () -> {
                    then.run();
                    rebuildWidgets();
                }));
    }

    private void step(int by) {
        if (recipes.isEmpty()) return;
        leaveGrid(() -> openRecipe(Math.floorMod((making ? 0 : shown) + by, recipes.size())));
    }

    private void startNew() {
        leaveGrid(() -> {
            making = true;
            newName = "";
            cells = new ArrayList<>(List.of("", "", "", "", "", "", "", "", ""));
            result = item;
            countText = "1";
            type = CraftingRecipe.SHAPED;
            opened = snapshot();
        });
    }

    private RecipeOps.Recipe form() {
        RecipeOps.Recipe r = current();
        return new RecipeOps.Recipe(making ? newName : r == null ? "" : r.name(), type, result, countText,
                List.copyOf(cells), r != null && r.game() ? RecipeOps.Source.EDITED : RecipeOps.Source.OWN);
    }

    private void saveShown() {
        if (!hasGrid()) return;
        RecipeOps.Recipe r = current();
        if (!making && RecipeOps.isStation(r)) {
            // A station recipe saves as changes of its values (RecipeOps#saveStation).
            if (report(RecipeOps.saveStation(r, type, cells, result, countText))) {
                recipes = RecipeOps.forItem(item);
                openRecipe(Math.min(shown, Math.max(0, recipes.size() - 1)));
                rebuildWidgets();
            }
            return;
        }
        RecipeOps.Recipe form = form();
        String problem = RecipeOps.problem(form, making);
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (!report(RecipeOps.save(making ? "" : r.name(), form))) {
            return;
        }
        String name = making ? newName.strip() : r.name();
        recipes = RecipeOps.forItem(item);
        int at = 0;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).name().equals(name)) at = i;
        }
        openRecipe(at);
        savedHere();
        rebuildWidgets();
    }

    private void toggleShown() {
        RecipeOps.Recipe r = current();
        if (r == null || !r.game()) return;
        if (RecipeOps.isStation(r)) {
            recipeAct(com.strata.domain.OverrideOps.setOff(com.strata.data.JsonOverrides.RECIPE, r.name(),
                    r.source() != RecipeOps.Source.OFF));
            return;
        }
        recipeAct(RecipeOps.setOff(List.of(r), r.source() != RecipeOps.Source.OFF));
    }

    /** Your own recipe goes; a changed or turned-off game recipe gets the game's back. */
    private void removeShown() {
        RecipeOps.Recipe r = current();
        if (r == null || r.source() == RecipeOps.Source.GAME) return;
        String what = r.game() ? "Put the game's " + r.name() + " back?\nYour change to it goes."
                : "Remove the recipe " + r.name() + "?";
        minecraft.setScreen(new ConfirmDialog(this, what + "\n" + RecipeOps.APPLIES,
                () -> recipeAct(RecipeOps.isStation(r)
                        ? com.strata.domain.OverrideOps.reset(com.strata.data.JsonOverrides.RECIPE, r.name())
                        : RecipeOps.remove(List.of(r.name())))));
    }

    private void recipeAct(WriteReport result) {
        if (report(result)) {
            recipes = RecipeOps.forItem(item);
            openRecipe(Math.min(shown, Math.max(0, recipes.size() - 1)));
            rebuildWidgets();
        }
    }

    /** The items to pick from, Empty first for a cell. */
    private static List<com.strata.game.RegistryQuery.Option> choices(boolean withEmpty) {
        List<com.strata.game.RegistryQuery.Option> out = new ArrayList<>();
        if (withEmpty) {
            out.add(new com.strata.game.RegistryQuery.Option("", "Empty (clears the cell)",
                    RegistryPickerScreen.CLEAR_GROUP));
        }
        out.addAll(com.strata.game.RegistryQuery.items());
        return out;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!choicesOpen() && hasGrid() && layout != null) {
            for (int i = 0; i < CraftingRecipe.CELLS; i++) {
                if (hit(layout.get("s" + i), mouseX, mouseY)) {
                    int cell = i;
                    // A station recipe uses its own cells only (the middle one; a smithing table's middle
                    // row).
                    if (!making && RecipeOps.isStation(current()) && !RecipeOps.stationCells(current().type()).contains(cell)) {
                        return true;
                    }
                    if (button == 1) {
                        cells.set(cell, "");
                    } else {
                        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick the item for this cell",
                                choices(true), id -> cells.set(cell, id == null ? "" : id)));
                    }
                    return true;
                }
            }
            if (hit(layout.get("result"), mouseX, mouseY)) {
                ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Pick the result", choices(false),
                        id -> result = id == null ? result : id));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean hit(com.strata.ui.Layout.Element e, double x, double y) {
        if (e == null) return false;
        com.strata.ui.Box b = e.box();
        return x >= b.x() && x < b.right() && y >= b.y() && y < b.bottom();
    }

    // ---- the values ----

    private ItemStatsOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : values.get(Integer.parseInt(sel.id));
    }


    /** The item's values, and each component picked to add that has no value yet, empty, last. */
    private List<ItemStatsOps.Value> withPending() {
        List<ItemStatsOps.Value> out = new java.util.ArrayList<>(ItemStatsOps.values(item));
        for (String id : pending) {
            if (out.stream().noneMatch(v -> v.key().equals(com.strata.model.ItemStats.componentKey(id)))) {
                out.add(ItemStatsOps.pendingComponent(item, id));
            }
        }
        return out;
    }

    /** The component types as picker options: each in words, grouped by its mod. */
    static List<com.strata.game.RegistryQuery.Option> componentOptions(List<String> ids) {
        List<com.strata.game.RegistryQuery.Option> out = new java.util.ArrayList<>();
        for (String id : ids) {
            int colon = id.indexOf(':');
            out.add(new com.strata.game.RegistryQuery.Option(id, com.strata.model.ItemStats.componentWords(id),
                    colon < 0 ? "minecraft" : id.substring(0, colon)));
        }
        return out;
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = withPending();
            rebuild();
        }
    }

    private void rebuild() {
        // Each value's row says what it does.
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            ItemStatsOps.Value v = values.get(i);
            String sub = ItemStatsOps.what(v) + (v.own().isEmpty() ? "" : "  (yours; the game's: " + v.game() + ")");
            entries.add(new BrowseList.Entry(String.valueOf(i), ItemStatsOps.name(v) + ": " + v.effective(), sub,
                    v.own().isEmpty() ? Palette.TEXT : Palette.EDITED, null, null, 0));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        ItemStatsOps.Value v = list == null ? null : selected();
        String help = v == null ? "Select a value to change it. Yellow values are yours. " + ItemStatsOps.APPLIES
                + " Recipes: click a cell to pick its item, right-click to empty it. " + RecipeOps.APPLIES
                : ItemStatsOps.name(v) + ": " + ItemStatsOps.what(v) + " The game's: " + v.game() + ". "
                        + ItemStatsOps.APPLIES;
        String empty = values.isEmpty() ? "No values: this item's stats could not be read at game start (see the log)."
                : null;
        RecipeOps.Recipe r = current();
        String title;
        if (making) {
            title = "";
        } else if (r == null) {
            title = "No recipe makes this item: + Add recipe";
        } else {
            String state = switch (r.source()) {
                case OWN, EDITED -> " (yours)";
                case OFF -> " (off)";
                default -> "";
            };
            String problem = RecipeOps.editProblem(r);
            title = r.name() + state + ", " + (shown + 1) + " of " + recipes.size()
                    + (RecipeOps.isStation(r) ? ": " + RecipeOps.stationDetail(r) : problem == null ? "" : ": no grid");
        }
        boolean restore = r != null && r.game();
        com.strata.ui.Layout built = com.strata.ui.screen.ListScreenLayouts.itemSheet(frame(), this.font::width,
                title(), help, empty, title, making, hasGrid(), recipes.size() > 1 || (making && !recipes.isEmpty()),
                recipes.size() > 1 || (making && !recipes.isEmpty()), r != null && r.game(),
                restore ? "Restore recipe" : "Remove recipe", r != null && r.source() != RecipeOps.Source.GAME,
                hasGrid(), making ? "" : RecipeOps.machineDetail(r));
        useLayout(built);
        return built;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout = layout();
        removeButton.setMessage(Component.literal(layout.textOf("removeRecipe")));
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        emptyNote(graphics, layout, list);
        if (layout.get("recipeTitle") != null) {
            text(graphics, layout.get("recipeTitle"), current() != null && current().source() == RecipeOps.Source.OFF
                    ? Palette.LOCKED : Palette.TEXT);
        }
        for (int i = 0; layout.get("detail" + i) != null; i++) {
            text(graphics, layout.get("detail" + i), Palette.TEXT);
        }
        text(graphics, layout.get("countLabel"), Palette.LABEL);
        text(graphics, layout.get("typeLabel"), Palette.LABEL);
        renderGrid(graphics, mouseX, mouseY);
    }

    /** The crafting table's slots, arrow, and result slot, with each cell's item, as the game draws them. */
    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        if (cells == null) return;
        for (int i = 0; i < CraftingRecipe.CELLS; i++) {
            com.strata.ui.Box b = layout.boxOf("s" + i);
            // A slot of the table's grid (its first, at 29,16 with its frame).
            graphics.blit(CRAFTING, b.x(), b.y(), b.w(), b.h(), 29, 16, 18, 18, 256, 256);
            drawItem(graphics, icon(cells.get(i)), b);
        }
        com.strata.ui.Box s5 = layout.boxOf("s5");
        com.strata.ui.Box res = layout.boxOf("result");
        // The arrow, between the grid and the result.
        int arrowW = Math.min(22, res.x() - s5.right() - 2);
        graphics.blit(CRAFTING, s5.right() + (res.x() - s5.right() - arrowW) / 2, s5.y() + (s5.h() - 15) / 2,
                arrowW, 15, 90, 35, 22, 15, 256, 256);
        graphics.blit(CRAFTING, res.x(), res.y(), res.w(), res.h(), 119, 30, 26, 26, 256, 256);
        ItemStack out = Labels.itemIcon(result);
        drawItem(graphics, out, res);
        if (!out.isEmpty()) {
            int n;
            try {
                n = Integer.parseInt(countText.strip());
            } catch (NumberFormatException e) {
                n = 1;
            }
            if (n > 1) {
                graphics.renderItemDecorations(this.font, out.copyWithCount(n), res.x() + (res.w() - 16) / 2,
                        res.y() + (res.h() - 16) / 2);
            }
        }
        if (current() != null && CraftingRecipe.SPECIAL.equals(current().type())) {
            // A shown recipe with no grid (fireworks and the like) is greyed over; a station's is drawn.
            com.strata.ui.Box s0 = layout.boxOf("s0");
            graphics.fill(s0.x(), s0.y(), res.right(), layout.boxOf("s8").bottom(), Palette.DIM);
        }
        // The name of the item under the mouse.
        for (int i = 0; i < CraftingRecipe.CELLS; i++) {
            String c = cells.get(i);
            if (!c.isEmpty()) {
                tooltip(graphics, mouseX, mouseY, layout.get("s" + i), c.startsWith("#")
                        ? "Any item of " + c + "; pick an item to use that one alone" : Labels.itemName(c));
            }
        }
        if (!result.isEmpty()) {
            tooltip(graphics, mouseX, mouseY, layout.get("result"), Labels.itemName(result));
        }
    }

    /** The item each cell draws, worked out once per cell text. */
    private final java.util.Map<String, ItemStack> icons = new java.util.HashMap<>();

    /** A cell's item; a "#tag" draws the item the recipe read gave it when the tag is not bound here. */
    private ItemStack icon(String cell) {
        return icons.computeIfAbsent(cell, c -> {
            ItemStack stack = Labels.itemIcon(c);
            if (stack.isEmpty() && c.startsWith("#")) {
                stack = Labels.itemIcon(RecipeOps.iconOf(c));
            }
            // A barrier is the game's "empty tag" stand-in, never a recipe's item.
            return stack.is(net.minecraft.world.item.Items.BARRIER) ? ItemStack.EMPTY : stack;
        });
    }

    /** An item centred in its slot, as large as the slot holds. */
    private void drawItem(GuiGraphics graphics, ItemStack stack, com.strata.ui.Box b) {
        if (stack.isEmpty()) return;
        float scale = Math.max(1f, (Math.min(b.w(), b.h()) - 2) / 16f);
        graphics.pose().pushPose();
        graphics.pose().translate(b.x() + (b.w() - 16 * scale) / 2f, b.y() + (b.h() - 16 * scale) / 2f, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }
}

