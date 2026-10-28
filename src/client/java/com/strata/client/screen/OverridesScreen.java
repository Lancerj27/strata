package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ValueInput;
import com.strata.data.JsonOverrides;
import com.strata.domain.OverrideOps;
import com.strata.domain.OverrideViews;
import com.strata.domain.WriteReport;
import com.strata.model.JsonPaths;
import com.strata.model.ValueType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ONE KIND OF DATAPACK JSON, EVERY ENTRY: recipes first, of every type - crafting, smelting,
 * blasting, smoking, campfire, stonecutting, smithing, and each mod's own.
 */
public class OverridesScreen extends StrataScreen {

    /** A group header row's id prefix; an entry row's id is its index in rows. */
    private static final String GROUP = "ovg:";

    private final OverrideViews.View view;
    private List<OverrideViews.Row> rows = List.of();
    private BrowseList list;
    private EditField search;
    private String searchText = "";
    private int changed;

    /** The editor: the entry whose fields show beside the list, and its fields. */
    private JsonOverrides.Kind editKind;
    private String editId;
    private List<OverrideOps.Field> fields = List.of();
    private BrowseList fieldList;
    private ValueInput valueInput;

    public OverridesScreen(JsonOverrides.Kind kind) {
        this(OverrideViews.of(kind));
    }

    public OverridesScreen(OverrideViews.View view) {
        this(view, null, "Strata — " + view.title());
    }

    /** The biome the list is scoped to (only the structures it takes), or null for every entry. */
    private final String biome;

    private OverridesScreen(OverrideViews.View view, String biome, String title) {
        super(com.strata.ui.TitlePath.rootOfKind(view.kind().id()), title);
        this.view = view;
        this.biome = biome;
    }

    /** The structures one biome takes, from its editor: the Structures view, scoped. */
    public static OverridesScreen structuresIn(String biomeId) {
        return new OverridesScreen(OverrideViews.STRUCTURES, biomeId,
                "Strata — Structures: " + OverrideViews.biomeGroup(biomeId));
    }

    /**
     * One kind's list, opened with ONE entry's editor loaded (a biome's own JSON entry, opened from its
     * editor): the list stands, and the entry's fields show beside it.
     */
    public static OverridesScreen entry(JsonOverrides.Kind kind, String id) {
        OverridesScreen screen = new OverridesScreen(kind);
        screen.editKind = kind;
        screen.editId = id;
        return screen;
    }

    /** One list per view: a group named the same in two views does not open in both. */
    @Override
    protected String foldKey() {
        return getClass().getSimpleName() + "|" + view.id();
    }

    private boolean grouped() {
        return view.grouping() != OverrideViews.Grouping.NAMESPACE;
    }

    /** The selected entry row, or null (nothing, or a group header). */
    private OverrideViews.Row selectedRow() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(GROUP) ? null : rows.get(Integer.parseInt(sel.id));
    }

    /** What the side button (Spacing, Settings) opens for the selected row, as entry ids; empty for none. */
    private List<String> sideTargets() {
        OverrideViews.Row row = selectedRow();
        if (row == null || view.sideLabel() == null || row.kind() != view.kind()) {
            return List.of();
        }
        // The layout asks every frame, and the game's file is read from disk: ask once per row.
        String key = row.entry().id();
        if (!key.equals(sideKey)) {
            if (sideSource == null) {
                sideSource = JsonOverrides.game(view.grouping() == OverrideViews.Grouping.STRUCTURE_TYPE
                        ? JsonOverrides.STRUCTURE_SET : view.kind());
            }
            if (view.grouping() == OverrideViews.Grouping.STRUCTURE_TYPE) {
                sideCache = OverrideViews.setsOfStructure(key, sideSource);
            } else {
                String settings = OverrideViews.settingsOf(sideSource.get(key));
                sideCache = settings.isEmpty() ? List.of() : List.of(settings);
            }
            sideKey = key;
        }
        return sideCache;
    }

    private String sideKey = "";
    private List<String> sideCache = List.of();
    private java.util.Map<String, com.google.gson.JsonObject> sideSource;

    private com.strata.ui.Layout layout() {
        // The biome view's note says what its repeated rows are, in place of the rest, which is on its help
        // page.
        String footer = view.grouping() == OverrideViews.Grouping.BIOME
                ? rows.size() + " rows, " + changed + " changed. A feature in several biomes is listed under each;"
                + " changing it changes it everywhere."
                : rows.size() + " shown, " + changed + " changed. Grey: switched off."
                + " Changes apply on the next world load or /reload.";
        return com.strata.ui.screen.OverrideLayouts.listWithEditor(frame(), this.font::width, title(),
                footer, list != null && !list.pickedIds().isEmpty(),
                view.sideLabel(), !sideTargets().isEmpty(), tools(), editorHints(), editorTitle());
    }

    /** The editor's hint lines: what the selected field is, or how to start. */
    private List<String> editorHints() {
        OverrideOps.Field f = selectedField();
        if (f == null) {
            return List.of(editId == null ? "Select an entry at the left to edit its fields."
                    : "Select a field. Yellow fields are changed.", "");
        }
        return List.of(f.name().isEmpty() ? f.path() : f.name() + "  (" + f.path() + ")",
                "The game's: " + f.game() + (f.edited() ? "   Yours: " + f.current() : ""));
    }

    /** The editor panel's title: the entry it edits. */
    private String editorTitle() {
        if (editId == null) {
            return "Fields";
        }
        int colon = editId.indexOf(':');
        String ns = colon > 0 ? editId.substring(0, colon) : "";
        return "Fields: " + OverrideOps.nameOf(editId)
                + (ns.isEmpty() || ns.equals("minecraft") ? "" : " (" + ns + ")");
    }

    @Override
    protected String helpKey() {
        return "OverridesScreen:" + view.id();
    }

    /** The Structures screen's top row opens Strata's own structures first. */
    private List<String> tools() {
        return view == OverrideViews.STRUCTURES && biome == null ? List.of("Custom structures") : List.of();
    }

    @Override
    protected void init() {
        super.init();
        sideSource = null;
        sideKey = "";
        reload();
        BrowseList.State keep = list == null ? null : list.state();
        BrowseList.State keepFields = fieldList == null ? null : fieldList.state();
        String keepValue = valueInput == null ? "" : valueInput.value();
        list = null;
        fieldList = null;
        com.strata.ui.Layout layout = layout();
        placeUndo(layout);

        search = new EditField(this.font, 0, 0, 0, 0, "filter", searchText);
        search.setValidator(s -> true);
        search.setResponder(s -> {
            searchText = s;
            reload();
            rebuild();
        });
        addRenderableWidget(place(search.getWidget(), layout.get("search")));
        if (!tools().isEmpty()) {
            placeAll(layout, new String[]{"tool0"}, Button.builder(Component.literal(tools().getFirst()),
                    b -> ScreenNavigator.get().navigateTo(new CustomStructuresScreen())).build());
        }

        // The entries list.
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("OverridesScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            if (entry == null || entry.id.startsWith(GROUP)) {
                return;
            }
            OverrideViews.Row row = rows.get(Integer.parseInt(entry.id));
            loadEditor(row.kind(), row.entry().id());
        });
        addRenderableWidget(list);

        // The editor's fields list, beside the entries.
        fieldList = new BrowseList(this.minecraft, 1, 1, 0, scaled(22));
        placeList(fieldList, layout.get("fieldList"), foldKey() + "|fields");
        fieldList.setOnSelect(entry -> {
            OverrideOps.Field f = entry == null ? null : fieldFor(entry.id);
            if (f != null) {
                valueInput.show(valueType(f), f.current());
            }
        });
        addRenderableWidget(fieldList);

        valueInput = valueInput(layout, "value", this::setValue);
        valueInput.show(ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> setValue()).build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
            OverrideOps.Field f = selectedField();
            if (f != null) {
                act(OverrideOps.restore(editKind, editId, f.path()));
            }
        }).build();
        Button dup = Button.builder(Component.literal("Duplicate element"), b -> {
            OverrideOps.Field f = selectedField();
            if (f != null) {
                act(OverrideOps.duplicate(editKind, editId, f.path(), valueInput.value()));
            }
        }).build();
        Button drop = Button.builder(Component.literal("Remove element"), b -> {
            OverrideOps.Field f = selectedField();
            String element = f == null ? null : OverrideOps.element(f.path());
            if (element != null) {
                minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, "Remove " + element + "?",
                        () -> act(OverrideOps.removeElement(editKind, editId, f.path()))));
            }
        }).build();
        Button all = Button.builder(Component.literal("Restore all"), b -> {
            if (editId != null) {
                minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, "Reset " + editId
                        + " to the game's?\nEvery change of it goes.", () -> act(OverrideOps.reset(editKind, editId))));
            }
        }).build();
        placeAll(layout, new String[]{"pset", "prestore", "pdup", "pdrop", "presetall"}, set, restore, dup, drop, all);
        needsSelection(() -> selectedField() != null, set, restore);
        needsSelection(() -> selectedField() != null && OverrideOps.element(selectedField().path()) != null,
                dup, drop);
        needsSelection(() -> editId != null, all);

        Button toggle = Button.builder(Component.literal("On / Off"), b -> togglePicked()).build();
        Button reset = Button.builder(Component.literal("Restore"), b -> resetPicked()).build();
        placeAll(layout, new String[]{"toggle", "reset"}, toggle, reset);
        needsSelection(() -> !list.pickedIds().isEmpty(), toggle, reset);
        if (view.sideLabel() != null) {
            Button side = Button.builder(Component.literal(view.sideLabel()), b -> openSide()).build();
            placeAll(layout, new String[]{"side"}, side);
            needsSelection(() -> !sideTargets().isEmpty(), () -> selectedRow() == null
                    ? "Select a " + (view.grouping() == OverrideViews.Grouping.STRUCTURE_TYPE ? "structure" : "feature")
                    + " in the list first"
                    : view.grouping() == OverrideViews.Grouping.STRUCTURE_TYPE
                    ? "This structure is in no structure set, so it has no spacing to edit"
                    : "This feature has no settings entry of its own: it is not a feature this list places by name",
                    side);
        }

        rebuild();
        rebuildFields();
        list.restore(keep);
        fieldList.restore(keepFields);
    }

    /** Reads the rows for the filters as they stand. */
    private void reload() {
        rows = OverrideViews.rows(view, searchText);
        if (biome != null) {
            java.util.Set<String> taken = OverrideViews.structuresInBiome(biome, JsonOverrides.game(JsonOverrides.STRUCTURE),
                    JsonOverrides.game(JsonOverrides.BIOME_TAG));
            rows = rows.stream().filter(r -> r.kind() == JsonOverrides.STRUCTURE && taken.contains(r.entry().id())).toList();
        }
        changed = (int) rows.stream().filter(r -> r.entry().state() != OverrideOps.State.GAME).count();
    }

    private List<OverrideViews.Row> picked() {
        return list.pickedIds().stream().map(id -> rows.get(Integer.parseInt(id))).toList();
    }

    // ---------------------------------------------------------- the editor beside the list

    /** Loads one entry's fields into the editor panel; no second screen. */
    private void loadEditor(JsonOverrides.Kind kind, String id) {
        editKind = kind;
        editId = id;
        rebuildFields();
        // A new entry starts with no field picked: a stale pick would otherwise name this entry's field at
        // the old index.
        if (fieldList != null) {
            fieldList.setSelected(null);
        }
        if (valueInput != null) {
            valueInput.show(ValueType.TEXT, "");
        }
    }

    /** The selected field of the editor panel, or null (nothing, or no entry). */
    private OverrideOps.Field selectedField() {
        BrowseList.Entry sel = fieldList == null ? null : fieldList.getSelected();
        return sel == null ? null : fieldFor(sel.id);
    }

    /** The field a panel row's id names, or null (a guard for a row that is not one). */
    private OverrideOps.Field fieldFor(String id) {
        try {
            return fields.get(Integer.parseInt(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The value control's mode for a field: a data map's member is text, else the field's own kind. */
    private static ValueType valueType(OverrideOps.Field f) {
        String element = OverrideOps.element(f.path());
        return element != null && element.startsWith("values.") ? ValueType.TEXT
                : JsonPaths.typeOf(f.path(), f.current());
    }

    /** Set value: what the control holds, to the selected field (an item picked from the picker calls it too). */
    private void setValue() {
        OverrideOps.Field f = selectedField();
        if (f != null && editKind != null) {
            act(OverrideOps.set(editKind, editId, f.path(), valueInput.value()));
        }
    }

    /**
     * Spacing (a structure's structure set) or Settings (a feature's configured feature): its fields load
     * into the same editor, the one a picker chooses from when a structure is in several sets.
     */
    private void openSide() {
        List<String> targets = sideTargets();
        JsonOverrides.Kind kind = view.also().getFirst();
        if (targets.size() == 1) {
            loadEditor(kind, targets.getFirst());
        } else if (targets.size() > 1) {
            ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Spacing of which set?",
                    RegistryPickerScreen.plainOptions("set", targets),
                    pick -> deferTo(() -> loadEditor(kind, pick))));
        }
    }

    /** On / Off over every picked entry: all off when any is on, else all on. */
    private void togglePicked() {
        List<OverrideViews.Row> picked = picked();
        boolean off = picked.stream().anyMatch(r -> r.entry().state() != OverrideOps.State.OFF);
        WriteReport report = new WriteReport();
        for (OverrideViews.Row r : picked) {
            report = report.ok() ? OverrideOps.setOff(r.kind(), r.entry().id(), off) : report;
        }
        act(report);
    }

    /** Restore over every picked entry: one at once, several after a question. */
    private void resetPicked() {
        List<OverrideViews.Row> picked = picked();
        if (picked.size() == 1) {
            act(OverrideOps.reset(picked.getFirst().kind(), picked.getFirst().entry().id()));
            return;
        }
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this, "Restore " + picked.size()
                + " entries to the game's?\nEvery change of each goes, and a switched-off one is on again.",
                () -> {
                    WriteReport report = new WriteReport();
                    for (OverrideViews.Row r : picked) {
                        report = report.ok() ? OverrideOps.reset(r.kind(), r.entry().id()) : report;
                    }
                    act(report);
                }));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            rebuildWidgets();
        }
    }

    private static String stateWords(OverrideOps.State state) {
        return switch (state) {
            case GAME -> "";
            case EDITED -> "  [edited]";
            case OFF -> "  [off]";
        };
    }

    private BrowseList.Entry rowEntry(int i, String group, int indent) {
        OverrideOps.Entry e = rows.get(i).entry();
        return new BrowseList.Entry(String.valueOf(i),
                (e.summary().isEmpty() ? OverrideOps.nameOf(e.id()) : e.summary()) + stateWords(e.state()),
                (OverrideOps.typeLabel(e.type()).isEmpty() ? "" : OverrideOps.typeLabel(e.type()) + "  ") + e.id(),
                e.state() == OverrideOps.State.OFF ? Palette.MUTED
                        : e.state() == OverrideOps.State.EDITED ? Palette.EDITED : Palette.TEXT, null, group,
                indent);
    }

    private void rebuild() {
        List<BrowseList.Entry> entries = new ArrayList<>();
        if (grouped()) {
            // THE VIEW'S OWN GROUPS: one collapsible group per structure type, per biome, or per kind: the
            // list every other screen has, not a dropdown.
            for (OverrideViews.Group group : OverrideViews.groups(rows)) {
                String id = GROUP + group.name();
                entries.add(new BrowseList.Entry(id, group.name() + " (" + group.rows().size() + ")", "",
                        Palette.GROUP, id, null, 0));
                for (int i : group.rows()) {
                    entries.add(rowEntry(i, id, 1));
                }
            }
        } else {
            addNamespaceRows(entries);
        }
        list.setSearching(!searchText.isBlank());
        list.setOpenOnlyGroup(true);
        list.refresh(entries);
    }

    /** The editor's fields, with each field's current value beside the game's (the old entry screen's rows). */
    private void rebuildFields() {
        if (fieldList == null) {
            return;
        }
        if (editKind == null || editId == null) {
            fields = List.of();
            fieldList.refresh(List.of());
            return;
        }
        fields = OverrideOps.fields(editKind, editId);
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            OverrideOps.Field f = fields.get(i);
            entries.add(new BrowseList.Entry(String.valueOf(i),
                    // The name alone when Strata knows it; the path shows in the hint when the field is picked.
                    f.name().isEmpty() ? f.path() : f.name(),
                    f.current() + (f.edited() ? (f.game().isEmpty() ? "  (added)" : "  (game " + f.game() + ")") : ""),
                    f.edited() ? Palette.EDITED : Palette.TEXT));
        }
        fieldList.refresh(entries);
    }

    /** GROUPED, SORTED: by namespace and, in a big one, folder; OverrideOps owns the grouping. */
    private void addNamespaceRows(List<BrowseList.Entry> entries) {
        String lastGroup = null;
        int nsTotal = 0;
        List<OverrideOps.Section> sections = OverrideOps.sections(rows.stream().map(OverrideViews.Row::entry).toList());
        for (OverrideOps.Section section : sections) {
            // No namespace folder (group "") is flat, or the folders alone at the top.
            boolean noNs = section.group().isEmpty();
            boolean nested = !section.sub().isEmpty();
            String top = noNs ? (nested ? GROUP + section.sub() : null) : GROUP + section.group();
            String group = noNs ? top : nested ? top + "/" + section.sub() : top;
            int base = noNs ? (nested ? 1 : 0) : (nested ? 2 : 1);
            if (!noNs && !section.group().equals(lastGroup)) {
                lastGroup = section.group();
                nsTotal = 0;
                for (OverrideOps.Section s : sections) {
                    if (s.group().equals(lastGroup)) {
                        nsTotal += s.indexes().size();
                    }
                }
                entries.add(new BrowseList.Entry(top, section.group() + " (" + nsTotal + ")",
                        "", Palette.GROUP, top, null, 0));
            }
            if (noNs && nested) {
                entries.add(new BrowseList.Entry(top, section.sub() + " (" + section.indexes().size() + ")",
                        "", Palette.GROUP, top, null, 0));
            } else if (nested) {
                entries.add(new BrowseList.Entry(group, section.sub() + " (" + section.indexes().size() + ")",
                        "", Palette.GROUP, group, top, 1));
            }
            for (int i : section.indexes()) {
                entries.add(rowEntry(i, group, base));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        text(graphics, layout.get("panelTitle"), Palette.LABEL);
        for (com.strata.ui.Layout.Element e : layout.elements()) {
            if (e.id().startsWith("hint")) {
                text(graphics, e, Palette.HINT);
            }
        }
        text(graphics, layout.get("footer"), Palette.MUTED);
        emptyNote(graphics, layout, list);
        if (editId == null && layout.get("panelEmpty") != null) {
            text(graphics, layout.get("panelEmpty"), Palette.MUTED);
        }
    }
}
