package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ValueInput;
import com.strata.domain.ItemVariantOps;
import com.strata.domain.WriteReport;
import com.strata.model.ItemVariant;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * An item variant's stats and enchantments: the Item stats sheet, with the base item's number under the variant's (yellow).
 */
public class ItemVariantStatsScreen extends StrataScreen {

    private final String variant;
    private List<ItemVariantOps.Value> values = List.of();
    private BrowseList list;
    private ValueInput valueInput;

    public ItemVariantStatsScreen(String variant) {
        super("Strata — Item stats: " + variant);
        this.variant = variant;
    }

    /** The sheet's values: stats and enchantments (the look values are on the variant editor). */
    /** Components picked to add and not set yet: listed with no value until Set value. */
    private final java.util.Set<String> pending = new java.util.LinkedHashSet<>();

    private List<ItemVariantOps.Value> load() {
        List<ItemVariantOps.Value> out = new java.util.ArrayList<>(ItemVariantOps.values(variant).stream()
                .filter(v -> !ItemVariant.LOOK_KEYS.contains(v.key())).toList());
        for (String id : pending) {
            if (out.stream().noneMatch(v -> v.key().equals(com.strata.model.ItemStats.componentKey(id)))) {
                out.add(ItemVariantOps.pendingComponent(variant, id));
            }
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();
        values = load();
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ItemVariantStatsScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            var row = values.get(Integer.parseInt(entry.id));
            valueInput.show(com.strata.model.ItemVariant.typeOf(row.key()), row.effective());
        });
        addRenderableWidget(list);

        valueInput = valueInput(layout, "value", null);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> {
                    var v = selected();
                    if (v != null) act(ItemVariantOps.save(v, valueInput.value(), values));
                })
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(ItemVariantOps.restore(v));
                })
                .build();
        Button addAttribute = Button.builder(Component.literal("+ Add attribute"), b -> pickAttribute()).build();
        Button addEnchantment = Button.builder(Component.literal("+ Add enchantment"), b -> pickEnchantment()).build();
        // + Component: any component, listed with no value until Set value gives its JSON.
        Button addComponent = Button.builder(Component.literal("+ Add component"), b -> {
            List<String> has = values.stream().map(ItemVariantOps.Value::key).toList();
            List<String> ids = com.strata.runtime.ItemComponents.settable().stream()
                    .filter(id -> !has.contains(com.strata.model.ItemStats.componentKey(id))).toList();
            ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which component?",
                    ItemStatScreen.componentOptions(ids), picked -> {
                        pending.add(picked);
                        rebuildWidgets();
                    }));
        }).build();
        placeAll(layout, new String[]{"set", "restore", "addAttribute", "addEnchantment", "addComponent"},
                set, restore, addAttribute, addEnchantment, addComponent);
        needsSelection(() -> selected() != null, set, restore);
        rebuild();
        list.restore(keepList);
    }

    private void pickAttribute() {
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which attribute?",
                com.strata.game.RegistryQuery.attributes(), id -> {
                    String taken = ItemVariantOps.attributeTaken(id, values);
                    if (taken != null) {
                        refuse(taken);
                        return;
                    }
                    report(ItemVariantOps.addAttribute(variant, id, values));
                }));
    }

    private void pickEnchantment() {
        var options = com.strata.game.RegistryQuery.enchantments(this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.registryAccess() : null);
        if (options.isEmpty()) {
            refuse("No enchantments: open a world first (the game keeps them per world)");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which enchantment?", options,
                id -> report(ItemVariantOps.setEnchantment(variant, id, "1"))));
    }

    private ItemVariantOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null ? null : values.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            values = load();
            rebuild();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            ItemVariantOps.Value v = values.get(i);
            String sub = ItemVariantOps.what(v) + (v.own().isEmpty() ? ""
                    : v.base().isEmpty() ? "  (the variant's own)" : "  (the variant's; the base item's: " + v.base() + ")");
            entries.add(new BrowseList.Entry(String.valueOf(i), ItemVariantOps.name(v) + ": " + v.effective(), sub,
                    v.own().isEmpty() ? Palette.TEXT : Palette.EDITED, null, null, 0));
        }
        list.refresh(entries);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        ItemVariantOps.Value v = list == null ? null : selected();
        String help = v == null ? "The base item's values, with the variant's in yellow. Select one to change it."
                + " An edit reaches stacks already made; their enchantments stay the player's."
                : ItemVariantOps.name(v) + ": " + ItemVariantOps.what(v)
                        + (v.base().isEmpty() ? "" : " The base item's: " + v.base() + ".");
        String empty = values.isEmpty() ? "No values: the base item's stats could not be read at game start (see the log)."
                : null;
        return com.strata.ui.screen.ListScreenLayouts.variantSheet(frame(), this.font::width, title(), help, null, empty);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        emptyNote(graphics, layout, list);
    }
}
