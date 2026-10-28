package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.EditField;
import com.strata.client.widget.ValueInput;
import com.strata.data.GameLayer;
import com.strata.domain.MobDataOps;
import com.strata.domain.WriteReport;
import com.strata.model.PropertyGuide;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every value Strata extracted for one mob, editable. */
public class MobDataScreen extends StrataScreen {

    private static final Map<String, String> GROUP_LABELS = Map.ofEntries(
            Map.entry(GameLayer.ATTRIBUTE, "Attributes"),
            Map.entry(GameLayer.TYPE, "Type"),
            Map.entry(GameLayer.FIELD, "Fields"),
            Map.entry(GameLayer.TAG, "Tags"),
            Map.entry(GameLayer.MODEL, "Model"),
            Map.entry(GameLayer.SOUNDS, "Sounds"),
            Map.entry(GameLayer.VISUALS, "Visuals"),
            Map.entry(GameLayer.PHYSICS, "Physics"),
            Map.entry(GameLayer.FLAGS, "Flags"),
            Map.entry(GameLayer.COMBAT, "Combat"),
            Map.entry(GameLayer.PARTICLES, "Particles"),
            Map.entry(GameLayer.LOOT_XP, "Loot & XP"),
            Map.entry(MobDataOps.BODY, "Body"),
            Map.entry(GameLayer.BOSS, "Boss"),
            Map.entry(GameLayer.PATHFINDING, "Pathfinding"),
            Map.entry(MobDataOps.SPAWNING, "Spawning"));

    private final MobDataOps.Target target;
    /** The scope the mob editor was opened in: the Spawning tab's Scaling and Mob griefing are its. */
    private final String switchScope;
    private final String switchScopeId;
    private String group = GameLayer.ATTRIBUTE;
    private MobDataOps.Sheet sheet = new MobDataOps.Sheet(false, List.of());
    private List<MobDataOps.Value> shown = List.of();
    private BrowseList list;
    private EditField search;
    private ValueInput valueInput;

    /**
     * @param variant the variant instance to edit, or "" for the base mob
     */
    public MobDataScreen(String entity, String variant, String scope, String scopeId) {
        super("Strata — Mob data: " + (variant == null || variant.isEmpty() ? Labels.creature(entity)
                : Labels.instanceId("", scopeId, variant)));
        this.target = new MobDataOps.Target(entity, variant, scope, scopeId);
        this.switchScope = scope == null ? "" : scope;
        this.switchScopeId = scopeId == null ? "" : scopeId;
        editingMob(entity, target.variant());
    }

    /** The layout for the tab and the selection as they stand. */
    private com.strata.ui.Layout layout() {
        MobDataOps.Value v = list == null ? null : selected();
        String lock = v == null ? null : MobDataOps.lockReason(target, v);
        String help = v == null ? "Select a value. Yellow values are changed. Restore puts back the value beneath."
                : lock != null ? "Locked: " + lock + "."
                : group.equals(GameLayer.ATTRIBUTE) ? com.strata.model.AttributeGuide.of(v.key()).what()
                : !com.strata.model.FieldGuide.what(group, v.key()).isEmpty()
                        ? com.strata.model.FieldGuide.what(group, v.key()) + " The game's: " + v.game() + "."
                : "Game value: " + v.game() + (v.base().isEmpty() ? "" : ". Inherited: " + v.base())
                        + (v.own().isEmpty() ? "" : ". This variant: " + v.own()) + ".";
        List<String> tabs = tabs();
        // THE SWITCHES, each on its own row beneath the tabs and the search.
        List<com.strata.ui.screen.FormLayout.Cell> switchRow = new ArrayList<>();
        if (group.equals(GameLayer.ATTRIBUTE)) {
            for (MobDataOps.Value sw : switches) {
                String id = "sw_" + sw.key();
                String name = MobDataOps.switchName(sw.key());
                String shown = com.strata.ui.screen.ListLayout.Action.choice(id, name,
                        MobDataOps.spawningChoices(target, sw.key()), 150, this.font::width).label();
                switchRow.add(new com.strata.ui.screen.FormLayout.Cell(id, com.strata.ui.Layout.Kind.BUTTON,
                        150, name, List.of(shown)));
            }
        }
        return com.strata.ui.screen.MobDataLayout.build(frame(), this.font::width, title(),
                tabs.stream().map(this::tabLabel).toList(),
                tabs.indexOf(group), help,
                com.strata.ui.Words.count(shown.size(), "value", "values") + " in " + GROUP_LABELS.getOrDefault(group, group)
                        + ". The game's values are read when the game starts.",
                sheet.extracted() ? null
                        : "No values read for this mob: the extraction at game start failed (see the log).",
                switchRow, group.equals(GameLayer.SOUNDS));
    }

    /** The mob's switches, the Attributes tab's dropdowns. */
    private List<MobDataOps.Value> switches = List.of();

    /** THE TABS WITH SOMETHING IN THEM: a tab is drawn when it holds a value the list shows. */
    private List<String> tabs() {
        // ATTRIBUTES IS WHERE THE SCREEN OPENS, and Type, Fields, Tags, Visuals and Sounds are TABS beside
        // it: every view shows the whole row, the open one greyed.
        boolean viewLocked = com.strata.domain.OptionsOps.viewLocked();
        List<String> out = new ArrayList<>();
        out.add(GameLayer.ATTRIBUTE);
        for (String g : MobDataOps.SUB_VIEWS) {
            // THE BOSS TAB ONLY ON A BOSS: its values do nothing until the Body tab's Boss flag is on.
            if (g.equals(GameLayer.BOSS) && !MobDataOps.isBoss(sheet.values())) {
                continue;
            }
            // A property view always opens: it offers what can be set before it is (MobDataOps#VIEW_KEYS).
            boolean any = MobDataOps.VIEW_KEYS.containsKey(g)
                    || sheet.values().stream().anyMatch(v -> MobDataOps.inView(g, v)
                    && (viewLocked || MobDataOps.lockReason(target, v) == null));
            if (any) {
                out.add(g);
            }
        }
        return out;
    }

    /** A tab's words. */
    private String tabLabel(String g) {
        return GROUP_LABELS.getOrDefault(g, g);
    }

    @Override
    protected void init() {
        super.init();
        sheet = MobDataOps.sheet(target);
        switches = MobDataOps.switches(target, switchScope, switchScopeId);
        // A Boss tab that is gone (the flag was turned off) opens Attributes, not an empty tab.
        if (!group.equals(GameLayer.ATTRIBUTE) && !tabs().contains(group)) {
            group = GameLayer.ATTRIBUTE;
        }
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        String keepSearch = search == null ? "" : search.getValue();
        String keepValue = valueInput == null ? "" : valueInput.value();
        com.strata.ui.Layout layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);

        // The tabs and the search wrap to the width: the layout places them.
        List<String> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            String g = tabs.get(i);
            Button tab = Button.builder(Component.literal(tabLabel(g)), b -> {
                        group = g;
                        clearWidgets();
                        init();
                    })
                    .build();
            placeAll(layout, new String[]{"tab" + i}, tab);
        }
        // THE SWITCHES, as dropdowns at the top of Attributes: a pick saves at once.
        if (group.equals(GameLayer.ATTRIBUTE)) {
            for (MobDataOps.Value sw : switches) {
                com.strata.client.widget.DropdownSelect choice = new com.strata.client.widget.DropdownSelect(
                        0, 0, 0, 0, MobDataOps.switchName(sw.key()), MobDataOps.spawningChoices(target, sw.key()),
                        sw.effective());
                choice.setOnChange(word -> act(MobDataOps.saveSpawning(target, switchScope, switchScopeId,
                        sw.key(), word)));
                Button b = addRenderableWidget(place(choice.getWidget(), layout.get("sw_" + sw.key())));
                b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(MobDataOps.spawningHelp(sw.key()))));
            }
        }
        search = field(0, 0, 0, 0, "Search", keepSearch, null, this::rebuild);
        place(search.getWidget(), layout.get("search"));
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("MobDataScreen");
        placeList(list, layout.get("list"));
        list.setOnSelect(entry -> {
            if (!entry.id.startsWith(SECTION)) {
                var row = shown.get(Integer.parseInt(entry.id));
                valueInput.show(MobDataOps.typeOf(row), row.effective());
            }
        });
        addRenderableWidget(list);

        // THE VALUE CONTROL IS THE ROW'S TYPE: a switch is a true/false dropdown, the model, a texture, a
        // particle and a sound are picked from their registries, a number is a field.
        valueInput = valueInput(layout, "value", this::setValue);
        valueInput.show(com.strata.model.ValueType.TEXT, keepValue);
        Button set = Button.builder(Component.literal("Set value"), b -> setValue())
                .build();
        Button restore = Button.builder(Component.literal("Restore"), b -> {
                    var v = selected();
                    if (v != null) act(MobDataOps.restore(target, v));
                })
                .build();
        placeAll(layout, new String[]{"set", "restore"}, set, restore);
        // Restore on a value with no edit here removes nothing, a correct no-op.
        needsSelection(() -> selected() != null, set, restore);
        if (group.equals(GameLayer.SOUNDS)) {
            Button play = Button.builder(Component.literal("Play"), b -> play()).build();
            placeAll(layout, new String[]{"play"}, play);
            needsSelection(() -> selected() != null && selected().key().endsWith("_sound"), play);
        }

        rebuild();
        list.restore(keepList);
        // Every section starts folded, as in every list.
    }
    /** Set value: what the control holds, saved to the selected row (a registry pick calls it too). */
    private void setValue() {
        var v = selected();
        if (v != null) {
            act(MobDataOps.save(target, v, valueInput.value()));
        }
    }

    /** Plays the selected sound row here, at its row's volume and pitch when they are set. */
    private void play() {
        var v = selected();
        if (v == null || !v.key().endsWith("_sound")) {
            return;
        }
        String kind = v.key().substring(0, v.key().length() - "_sound".length());
        // shortcut: an unset row plays the game's usual name, entity.<mob>.<kind>; a mob that names its sound
        // otherwise (a slime's squish) is not found, and says so.
        net.minecraft.resources.ResourceLocation mob = net.minecraft.resources.ResourceLocation.tryParse(target.entity());
        String id = !v.effective().isBlank() ? v.effective()
                : mob == null ? "" : mob.getNamespace() + ":entity." + mob.getPath() + "." + kind;
        if (!com.strata.client.SoundPreview.play(id, number(kind + "_volume", 1F), number(kind + "_pitch", 1F))) {
            refuse("No sound " + id + " in the game");
        }
    }

    /** A number row of this sheet, or the fallback when it is not set. */
    private float number(String key, float fallback) {
        for (MobDataOps.Value v : sheet.values()) {
            if (v.key().equals(key)) {
                try {
                    return Float.parseFloat(v.effective().strip());
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    /** A section header's id prefix: it is not a value. */
    private static final String SECTION = "sec:";

    private MobDataOps.Value selected() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        return sel == null || sel.id.startsWith(SECTION) ? null : shown.get(Integer.parseInt(sel.id));
    }

    private void act(WriteReport result) {
        if (report(result)) {
            boolean wasBoss = MobDataOps.isBoss(sheet.values());
            sheet = MobDataOps.sheet(target);
            switches = MobDataOps.switches(target, switchScope, switchScopeId);
            if (wasBoss != MobDataOps.isBoss(sheet.values())) {
                // The Boss flag moved: the tab row gains or loses its Boss tab.
                clearWidgets();
                init();
                return;
            }
            rebuild();
        }
    }

    private void rebuild() {
        // THE VALUES IN SECTIONS: an attribute under its namespace and prefix, a field under the class that
        // declares it, a tag under its namespace.
        boolean viewLocked = com.strata.domain.OptionsOps.viewLocked();
        String text = search == null ? "" : search.getValue().strip().toLowerCase(java.util.Locale.ROOT);
        // WHAT CAN BE SET BEFORE IT IS (a sound or a particle the game has no value of its own for) sits in its
        // own section with the rest, not in a group of its own.
        List<MobDataOps.Value> unset = MobDataOps.unsetInView(group, sheet.values()).stream()
                .filter(v -> text.isEmpty() || v.key().toLowerCase(java.util.Locale.ROOT).contains(text)
                        || getDisplayName(v.group(), v).toLowerCase(java.util.Locale.ROOT).contains(text)).toList();
        List<MobDataOps.Value> listed = new ArrayList<>(MobDataOps.filterView(sheet.values(), group,
                search == null ? "" : search.getValue()).stream()
                .filter(v -> viewLocked || MobDataOps.lockReason(target, v) == null).toList());
        listed.addAll(unset);
        var sections = MobDataOps.sections(listed);
        List<MobDataOps.Value> ordered = new ArrayList<>();
        List<BrowseList.Entry> entries = new ArrayList<>();


        for (var section : sections.entrySet()) {
            String id = SECTION + group + ":" + section.getKey();
            entries.add(new BrowseList.Entry(id, section.getKey() + " (" + section.getValue().size() + ")", "",
                    Palette.GROUP, id, null, 0));
            for (MobDataOps.Value v : section.getValue()) {
                int index = ordered.size();
                ordered.add(v);
                String name = getDisplayName(v.group(), v);
                if (unset.contains(v)) {
                    String what = PropertyGuide.what(v.group(), v.key());
                    entries.add(new BrowseList.Entry(String.valueOf(index), name,
                            "the game's own" + (what.isEmpty() ? "" : "  " + what), Palette.TEXT, null, id, 1));
                    continue;
                }
                String what = getHelpText(v.group(), v);
                String sub = v.effective() + (v.source().equals("game") ? "" : "  (" + v.source() + ", game " + v.game() + ")")
                        + (what.isEmpty() || MobDataOps.lockReason(target, v) != null ? "" : "  " + what);
                boolean locked = MobDataOps.lockReason(target, v) != null;
                entries.add(new BrowseList.Entry(String.valueOf(index), name, locked ? sub + "  (locked)" : sub,
                        locked ? Palette.LOCKED : v.source().equals("game") ? Palette.TEXT : Palette.EDITED, null, id, 1));
            }
        }

        shown = ordered;
        // A search's matches show unfolded.
        list.setSearching(search != null && !search.getValue().isBlank());
        list.refresh(entries);
    }

    private String getDisplayName(String group, MobDataOps.Value v) {
        if (group.equals(GameLayer.ATTRIBUTE)) {
            return com.strata.model.AttributeGuide.of(v.key()).name() + "  \u00b7  " + v.key();
        }
        if (group.equals(GameLayer.FIELD)) {
            int dot = v.key().indexOf('.');
            return dot > 0 ? v.key().substring(dot + 1) : v.key();
        }
        if (PropertyGuide.knowsGroup(group)) {
            return PropertyGuide.name(group, v.key()) + "  \u00b7  " + v.key();
        }
        return v.key();
    }

    private String getHelpText(String group, MobDataOps.Value v) {
        if (group.equals(GameLayer.ATTRIBUTE)) {
            return "";
        }
        if (PropertyGuide.knowsGroup(group)) {
            return PropertyGuide.what(group, v.key());
        }
        return com.strata.model.FieldGuide.what(group, v.key());
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
