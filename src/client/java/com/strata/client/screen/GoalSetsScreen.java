package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.domain.GoalsOps;
import com.strata.domain.VariantOps;
import com.strata.domain.WriteReport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The Behaviors menu: the archetype library. */
public class GoalSetsScreen extends StrataScreen {

    /**
     * A mob the screen picks a behavior for.
     *
     * @param entity the mob's entity, for the behaviors it can use
     * @param plain a plain mob (its default is its family's), not a variant
     * @param current its behavior now, as a choice: one of VariantOps#defaultChoices, or a behavior's name
     * @param onUse told the choice picked, before the screen goes back
     * @param onEditAi run after the screen goes back, to open the mob's AI
     */
    public record MobPick(String entity, boolean plain, String current,
                          java.util.function.Consumer<String> onUse, Runnable onEditAi) {
    }

    /** The mob this screen picks for, or null for Client > Behaviors. */
    private final MobPick pick;
    private BrowseList list;
    private EditField search;
    private Button restoreButton;
    private List<GoalsOps.GoalSetRow> rows = List.of();
    /** The screen's layout, built in init. */
    private com.strata.ui.Layout layout;

    public GoalSetsScreen() {
        this(null);
    }

    private GoalSetsScreen(MobPick pick) {
        super(pick == null ? "Strata — Goal sets" : "Strata — Goal set: " + Labels.creature(pick.entity()));
        this.pick = pick;
    }

    /** The Behaviors screen for a mob's Behavior button. */
    public static GoalSetsScreen forMob(MobPick pick) {
        return new GoalSetsScreen(pick);
    }

    /** The layout for the selection as it stands. */
    private com.strata.ui.Layout layout() {
        GoalsOps.GoalSetRow selected = list == null ? null : selectedRow();
        long families = rows.stream().filter(r -> r.kind() == GoalsOps.Kind.FAMILY).count();
        String footer = pick == null ? com.strata.ui.Words.count(rows.size(), "goal set", "goal sets") + ", " + families + " from the game's mob families"
                : "Now: " + pick.current() + ". Select a goal set, then Use for this mob.";
        return com.strata.ui.screen.ListScreenLayouts.goalSetsList(frame(), this.font::width, title(),
                selected != null && selected.kind() == GoalsOps.Kind.FAMILY, pick != null,
                list != null && pickedChoice() != null, footer);
    }

    @Override
    protected void init() {
        super.init();
        rows = GoalsOps.goalSetRows();
        if (pick != null) {
            // Only the behaviors this mob can use (VariantOps#goalSetChoices).
            List<String> usable = VariantOps.goalSetChoices(pick.entity());
            rows = rows.stream().filter(r -> usable.contains(r.name())).toList();
        }
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;

        // The layout is the UI layer's, as the fit gate checked it.
        layout = layout();
        // Undo, and Ctrl+Z: the session's newest change goes back.
        placeUndo(layout);

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22));
        placeList(list, layout.get("list"));
        list.setColumnWidth(com.strata.ui.screen.ListScreenLayouts.goalSetColumnWidth(frame(), this.font::width));
        addRenderableWidget(list);
        // The search, by name.
        String keepSearch = search == null ? "" : search.getValue();
        search = new EditField(this.font, 0, 0, 0, 0, "filter", keepSearch);
        search.setValidator(s -> true);
        search.setResponder(s -> list.setFilter(s));
        addRenderableWidget(place(search.getWidget(), layout.get("search")));

        Button addButton = Button.builder(Component.literal("+ Add goal set"),
                b -> ScreenNavigator.get().navigateTo(GoalsScreen.forGoalSet(null))).build();
        Button edit = Button.builder(Component.literal("Edit"), b -> {
                    var row = selectedRow();
                    if (row != null) ScreenNavigator.get().navigateTo(GoalsScreen.forGoalSet(row.name()));
                })
                .build();
        Button delete = Button.builder(Component.literal("Remove"), b -> confirmDelete()).build();
        restoreButton = Button.builder(Component.literal("Restore"), b -> confirmRestore()).build();

        // THE BAR WRAPS to the width, and the list ends above it.
        placeAll(layout, new String[]{"add", "edit", "delete", "restore"},
                addButton, edit, delete, restoreButton);
        // Del presses Delete here: Del presses a list's "remove".
        keyPresses("remove", delete);
        needsSelection(() -> selectedRow() != null && !list.manyPicked(), edit);
        needsSelection(() -> !pickedRows().isEmpty(), delete);
        if (pick != null) {
            Button use = Button.builder(Component.literal("Use for this mob"), b -> {
                String choice = pickedChoice();
                if (choice != null) {
                    pick.onUse().accept(choice);
                    ScreenNavigator.get().back();
                }
            }).build();
            placeAll(layout, new String[]{"use"}, use);
            needsSelection(() -> pickedChoice() != null, use);
        }
        // EDIT THIS MOB'S GOALS, for a mob.
        if (pick != null) {
            placeAll(layout, new String[]{"editGoals"}, Button.builder(Component.literal("Edit this mob's goals"), b -> {
                ScreenNavigator.get().back();
                pick.onEditAi().run();
            }).build());
        }

        rebuild();
        list.restore(keepList);
    }

    private GoalsOps.GoalSetRow selectedRow() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel == null || sel.id.startsWith(GROUP) || sel.id.startsWith(CHOICE)) {
            return null;
        }
        return rows.get(Integer.parseInt(sel.id));
    }

    /** The choice the selected row gives a mob: a behavior's name, or one of the mob's own choices. */
    private String pickedChoice() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel != null && sel.id.startsWith(CHOICE)) {
            return sel.id.substring(CHOICE.length());
        }
        GoalsOps.GoalSetRow row = selectedRow();
        return row == null ? null : row.name();
    }

    /** A mob's own choice's id prefix: its default, or the game's AI. */
    private static final String CHOICE = "choice:";

    /** Shows what an action reported, then rebuilds. */
    private boolean act(WriteReport result) {
        if (report(result)) {
            clearWidgets();
            init();
            return true;
        }
        return false;
    }

    /** The behaviors picked with Ctrl and Shift, or the selected one alone. */
    private List<GoalsOps.GoalSetRow> pickedRows() {
        if (list == null) {
            return List.of();
        }
        List<GoalsOps.GoalSetRow> out = new ArrayList<>();
        for (String id : list.pickedIds()) {
            if (!id.startsWith(GROUP) && !id.startsWith(CHOICE)) {
                out.add(rows.get(Integer.parseInt(id)));
            }
        }
        return out;
    }

    /**
     * Deletes the picked behaviors after a confirmation that names them (a behavior delete always asks,
     * drift.31).
     */
    private void confirmDelete() {
        List<GoalsOps.GoalSetRow> picked = pickedRows();
        if (picked.isEmpty()) return;
        List<String> names = picked.stream().map(GoalsOps.GoalSetRow::name).toList();
        boolean family = picked.stream().anyMatch(r -> r.kind() == GoalsOps.Kind.FAMILY);
        String what = names.size() == 1 ? "the goal set '" + names.getFirst() + "'"
                : names.size() + " goal sets: " + com.strata.domain.Picks.listed(names);
        minecraft.setScreen(new ConfirmDialog(this,
                "Remove " + what + "?\nMobs that use " + (names.size() == 1 ? "it" : "them")
                        + " go back to the game's goals."
                        + (family ? "\nA family's goal set comes back, as the game runs it, when the game next starts."
                        : ""),
                () -> act(GoalsOps.deleteGoalSets(names))));
    }



    private void confirmRestore() {
        var row = selectedRow();
        if (row == null || row.kind() == GoalsOps.Kind.OWN) return;
        minecraft.setScreen(new ConfirmDialog(this,
                "Restore '" + row.name() + "' to "
                        + "the goals the game gives its mobs"
                        + "?\nYour changes to it are lost.",
                () -> {
                    if (act(GoalsOps.restoreGoalSet(row.name()))) {
                        com.strata.client.util.RecentEdits.touchGoalSet(row.name());
                    }
                }));
    }

    /** A mob's name, or a variant's, with its scope: "Zombie", "Zombie in t1", "big_zombie". */
    private static String userLabel(String user) {
        int in = user.indexOf(" in ");
        String who = in < 0 ? user : user.substring(0, in);
        return (who.contains(":") ? Labels.creature(who) : who) + (in < 0 ? "" : user.substring(in));
    }

    /** A group header's id prefix. */
    private static final String GROUP = "grp:";

    private void rebuild() {
        // BY FAMILY: a group per level of each row's path, nested; each archetype under its deepest group.
        List<BrowseList.Entry> entries = new ArrayList<>();
        java.util.Set<String> made = new java.util.HashSet<>();
        if (pick != null) {
            // THE MOB'S OWN CHOICES first: its default (its family's, or its base mob's for a variant) and
            // the game's own AI (VariantOps#defaultChoices).
            String group = GROUP + "This mob";
            entries.add(new BrowseList.Entry(group, "This mob", "", Palette.GROUP, group, null, 0));
            for (String choice : VariantOps.defaultChoices(pick.plain())) {
                entries.add(new BrowseList.Entry(CHOICE + choice, choice,
                        VariantOps.GAME_DEFAULT.equals(choice) ? "the game's own goals for this mob"
                                : "what it inherits", Palette.TEXT, null, group, 1));
            }
        }
        // RECENT FIRST: the behaviors saved this session, newest first, and ALSO in its own group, as on the
        // mob list.
        List<String> recent = com.strata.client.util.RecentEdits.recentGoalSets();
        List<Integer> order = new ArrayList<>();
        for (String name : recent) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).name().equals(name)) {
                    order.add(i);
                }
            }
        }
        int recentCount = order.size();
        for (int i = 0; i < rows.size(); i++) {
            order.add(i);
        }
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            GoalsOps.GoalSetRow row = rows.get(i);
            List<String> path = k < recentCount ? List.of("Recent") : row.path();
            String parent = null;
            for (int d = 0; d < path.size(); d++) {
                String id = GROUP + String.join("/", path.subList(0, d + 1));
                if (made.add(id)) {
                    entries.add(new BrowseList.Entry(id, path.get(d), "", Palette.GROUP, id, parent, d));
                }
                parent = id;
            }
            String users = row.users().isEmpty() ? "used by no mob"
                    : "used by " + String.join(", ", row.users().stream().map(GoalSetsScreen::userLabel).toList());
            String sub = users + (row.changed() ? "  (edited)" : "");
            int color = row.kind() == GoalsOps.Kind.OWN ? Palette.INHERITED : row.changed() ? Palette.EDITED : Palette.TEXT;
            entries.add(new BrowseList.Entry(String.valueOf(i), row.name(), sub, color, null, parent,
                    path.size()).columns(List.of(String.valueOf(row.users().size()),
                    String.valueOf(row.goals()), row.fights())));
        }
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Restore is for a built-in or a family's archetype.
        var selected = selectedRow();
        restoreButton.active = selected != null && selected.kind() != GoalsOps.Kind.OWN;
        restoreButton.setTooltip(restoreButton.active ? null
                : Tooltip.create(Component.literal("Select a family's goal set first")));
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        for (int i = 0; i < com.strata.ui.screen.ListScreenLayouts.GOAL_SET_HEADINGS.size(); i++) {
            var heading = layout.get("heading" + i);
            if (heading != null) {
                drawFittedHeading(graphics, heading);
            }
        }
        text(graphics, layout.get("footer"), Palette.MUTED);
    }

    /** A column heading, centred on its box and drawn smaller when wider, as the mob list's. */
    private void drawFittedHeading(GuiGraphics graphics, com.strata.ui.Layout.Element e) {
        int cx = e.box().x() + e.box().w() / 2;
        int w = this.font.width(e.text());
        if (w > e.box().w() && w > 0) {
            float k = (float) e.box().w() / w;
            graphics.pose().pushPose();
            graphics.pose().translate(cx, e.box().y(), 0);
            graphics.pose().scale(k, k, 1);
            graphics.drawCenteredString(this.font, e.text(), 0, 0, Palette.GROUP);
            graphics.pose().popPose();
        } else {
            graphics.drawCenteredString(this.font, e.text(), cx, e.box().y(), Palette.GROUP);
        }
    }
}

