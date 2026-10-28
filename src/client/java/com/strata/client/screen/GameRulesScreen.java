package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ValueInput;
import com.strata.data.JsonOverrides;
import com.strata.domain.OverrideOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * THE GAME RULES, AS ONE LIST: Options > Game rules opens the rules straight away, grouped by the game's own
 * categories (Player, Mobs, Spawning, Drops, World updates, Chat, Miscellaneous), each by its name in the
 * game's words.
 */
public class GameRulesScreen extends StrataScreen {

    /** A category header row's id prefix; a rule row's id is its index in fields. */
    private static final String GROUP = "grp:";

    private final JsonOverrides.Kind kind = JsonOverrides.GAME_RULES;
    private final String id = com.strata.runtime.GameRulesData.ENTRY;
    private List<OverrideOps.Field> fields = List.of();
    private BrowseList list;
    private ValueInput valueInput;

    public GameRulesScreen() {
        super(com.strata.ui.TitlePath.rootOfKind("game_rule"), "Strata — Game rules");
    }

    private OverrideOps.Field selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(GROUP) ? null : fields.get(Integer.parseInt(sel.id));
    }

    private com.strata.ui.Layout layout() {
        OverrideOps.Field f = selected();
        List<String> hints = f == null
                ? List.of("Select a rule, type its value, Set value. Yellow rules are changed.", "")
                : List.of((f.name().isEmpty() ? f.path() : f.name() + "  (" + f.path() + ")"),
                "The game's: " + f.game() + (f.edited() ? "   Yours: " + f.current() : ""));
        return com.strata.ui.screen.OverrideLayouts.rules(frame(), this.font::width, title(), hints,
                com.strata.ui.Words.count(fields.size(), "rule", "rules")
                        + ". Set on the world at its next start or /reload.");
    }

    @Override
    protected void init() {
        super.init();
        fields = OverrideOps.fields(kind, id);
        BrowseList.State keep = list == null ? null : list.state();
        list = null;
        String keepValue = valueInput == null ? "" : valueInput.value();
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("GameRulesScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            if (entry.id.startsWith(GROUP)) {
                return;
            }
            OverrideOps.Field row = fields.get(Integer.parseInt(entry.id));
            valueInput.show(com.strata.model.JsonPaths.typeOf(row.path(), row.current()), row.current());
        });
        addRenderableWidget(list);

        valueInput = valueInput(layout, "value", this::setValue);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> setValue()).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
            OverrideOps.Field f = selected();
            if (f != null) {
                act(OverrideOps.restore(kind, id, f.path()));
            }
        }).build();
        Button reset = Button.builder(Component.literal("Restore all"), b -> minecraft.setScreen(
                new com.strata.client.widget.ConfirmDialog(this, "Reset the game rules to the game's?\n"
                        + "Every change of them goes.", () -> act(OverrideOps.reset(kind, id))))).build();
        placeAll(layout, new String[]{"set", "restore", "reset"}, set, restore, reset);
        needsSelection(() -> selected() != null, set, restore);
        needsSelection(() -> !fields.isEmpty(), reset);

        rebuild();
        list.restore(keep);
    }

    /** Set value: what the control holds, to the selected rule. */
    private void setValue() {
        OverrideOps.Field f = selected();
        if (f != null) {
            act(OverrideOps.set(kind, id, f.path(), valueInput.value()));
        }
    }

    private void act(WriteReport result) {
        if (report(result)) {
            rebuildWidgets();
        }
    }

    /** A rule's name in the game's words ("Spread fire"), or its id when the game has none. */
    private static String ruleName(String rule) {
        String key = "gamerule." + rule;
        return net.minecraft.client.resources.language.I18n.exists(key)
                ? net.minecraft.client.resources.language.I18n.get(key) : rule;
    }

    /** The rules under their categories, each sorted by name; the categories by name. */
    private void rebuild() {
        java.util.Map<String, String> category = com.strata.runtime.GameRulesData.categories();
        java.util.Map<String, List<Integer>> byCategory = new java.util.TreeMap<>();
        for (int i = 0; i < fields.size(); i++) {
            String key = category.getOrDefault(fields.get(i).path(), "gamerule.category.misc");
            String heading = net.minecraft.client.resources.language.I18n.exists(key)
                    ? net.minecraft.client.resources.language.I18n.get(key) : "Other";
            byCategory.computeIfAbsent(heading, k -> new ArrayList<>()).add(i);
        }
        List<BrowseList.Entry> entries = new ArrayList<>();
        byCategory.forEach((heading, rows) -> {
            String group = GROUP + heading;
            entries.add(new BrowseList.Entry(group, heading + " (" + rows.size() + ")", "", Palette.GROUP, group, null, 0));
            rows.stream().sorted(java.util.Comparator.comparing(i -> ruleName(fields.get(i).path()).toLowerCase()))
                    .forEach(i -> {
                        OverrideOps.Field f = fields.get(i);
                        entries.add(new BrowseList.Entry(String.valueOf(i), ruleName(f.path()) + ": " + f.current(),
                                f.path() + (f.edited() ? "  (game " + f.game() + ")" : ""),
                                f.edited() ? Palette.EDITED : Palette.TEXT, null, group, 1));
                    });
        });
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.id().startsWith("hint")) {
                text(graphics, e, Palette.HINT);
            }
        }
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
    }
}
