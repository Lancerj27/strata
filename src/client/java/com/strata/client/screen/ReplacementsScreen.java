package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.domain.ReplacementOps;
import com.strata.domain.WriteReport;
import com.strata.util.Labels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** ONE TIER'S OR BIOME'S MOB REPLACEMENTS. */
public class ReplacementsScreen extends StrataScreen {

    private final String scope;
    private final String scopeId;
    private List<ReplacementOps.Row> rows = List.of();
    private BrowseList list;
    private String from = "";
    private String to = "";
    private String sources = ReplacementOps.SOURCES.getFirst();
    private EditField shareField;
    /** The typed share, kept apart from its field so a layout can read it before init. */
    private String share = "";

    /** @param from the mob to replace, picked already when opened from its row, or "" */
    public ReplacementsScreen(String scope, String scopeId, String from) {
        super("Strata — Replacements: " + Labels.scope(scope, scopeId));
        this.scope = scope;
        this.scopeId = scopeId;
        this.from = from == null ? "" : from;
    }

    private com.strata.ui.Layout layout() {
        String problem = ReplacementOps.problem(from, to, share);
        return com.strata.ui.screen.ReplacementsLayout.build(frame(), this.font::width, title(),
                from.isEmpty() ? "Pick a mob..." : Labels.creature(from),
                to.isEmpty() ? "Pick a mob..." : Labels.creature(to),
                ReplacementOps.SOURCES,
                problem != null ? "To add: " + problem : com.strata.ui.Words.count(rows.size(), "replacement", "replacements") + ". Natural spawns change at"
                        + " the next world load; every-spawn rules at once.",
                list != null && list.getSelected() != null);
    }

    @Override
    protected void init() {
        super.init();
        rows = ReplacementOps.rows(scope, scopeId);
        BrowseList.State keep = list == null ? null : list.state();
        list = null;
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ReplacementsScreen");
        // Grouped by mod, Minecraft's first.
        list.bySource(e -> BrowseList.sourceAt(e, rows, ReplacementOps.Row::from));
        placeList(list, layout.get("list"));
        addRenderableWidget(list);

        addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("from")),
                b -> pick("Replace which mob?", id -> from = id)).build(), layout.get("from")));
        addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("to")),
                b -> pick("Spawn which mob instead?", id -> to = id)).build(), layout.get("to")));
        shareField = new EditField(this.font, 0, 0, 0, 0, "Share %", share);
        shareField.setValidator(EditField.numericValidator());
        shareField.setResponder(s -> share = s);
        addRenderableWidget(place(shareField.getWidget(), layout.get("share")));
        DropdownSelect sourceSelect = new DropdownSelect(0, 0, 0, 0, "", ReplacementOps.SOURCES, sources);
        sourceSelect.setOnChange(s -> sources = s);
        Button sourceButton = addRenderableWidget(place(sourceSelect.getWidget(), layout.get("sources")));
        sourceButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "natural: the biome's own spawns. all: also a structure's, a spawner's, a patrol's."
                        + " A player's own (an egg, a command) never.")));

        Button add = Button.builder(Component.literal("+ Add replacement"),
                b -> act(ReplacementOps.save(scope, scopeId, from, to, share, sources))).build();
        Button remove = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        placeAll(layout, new String[]{"add", "remove"}, add, remove);
        needsSelection(() -> !list.pickedIds().isEmpty(), remove);

        rebuild();
        list.restore(keep);
    }

    /** Removes the picked replacements: one at once, several after a question. */
    private void removePicked() {
        List<ReplacementOps.Row> picked = list.pickedIds().stream().map(id -> rows.get(Integer.parseInt(id))).toList();
        if (picked.isEmpty()) {
            return;
        }
        // ONE ROW GOES AT ONCE; SEVERAL ASK.
        if (picked.size() == 1) {
            act(ReplacementOps.remove(picked.getFirst()));
            return;
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, "Remove " + picked.size()
                + " replacements?\n" + com.strata.domain.Picks.listed(picked.stream().map(ReplacementOps.Row::sentence).toList()),
                () -> {
                    WriteReport report = new WriteReport();
                    for (ReplacementOps.Row r : picked) {
                        report = report.ok() ? ReplacementOps.remove(r) : report;
                    }
                    act(report);
                }));
    }

    /** Picks a mob from the game's entity registry: the game's, every mod's, Strata's own. */
    private void pick(String title, java.util.function.Consumer<String> onPick) {
        var options = com.strata.game.RegistryQuery.entities();
        if (options.isEmpty()) {
            refuse("mob: the game data could not be read at game start (see the log)");
            return;
        }
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(title, options, onPick));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            from = "";
            to = "";
            rebuildWidgets();
        }
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ReplacementOps.Row r = rows.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i),
                    Labels.creature(r.from()) + "  ->  " + Labels.creature(r.to()), r.sentence(), Palette.TEXT));
        }
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        for (String id : com.strata.ui.screen.ReplacementsLayout.FIELDS) {
            if (layout.get("label." + id) != null) {
                text(graphics, layout.get("label." + id), Palette.LABEL);
            }
        }
        text(graphics, layout.get("footer"),
                ReplacementOps.problem(from, to, share) != null
                        ? Palette.ERROR : Palette.MUTED);
    }
}
