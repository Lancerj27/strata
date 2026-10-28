package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.MobGoals;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.domain.GoalsOps;
import com.strata.model.GoalRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** An AI list: a mob's own, or an archetype. */
public class GoalsScreen extends StrataScreen {

    /** Whose list this is. */
    private final boolean editsGoalSet;
    private final String goalSetName;
    private final String owner;
    private final String baseEntity;
    private final String scope;
    private final String scopeId;
    /** The mob's behavior as stored, kept up to date by this screen's saves. */
    private String mobGoalSet;
    /**
     * Told the mob's behavior choice after a save here: the name when the list is saved as a named behavior,
     * "" when it is saved as the mob's own list.
     */
    private final java.util.function.Consumer<String> onNamed;

    private List<GoalRow> rows = null;
    private List<GoalRow> baseline = null;
    private String source = "";
    /** The list before each unsaved change. */
    private final com.strata.client.widget.UndoManager<List<GoalRow>> undo =
            new com.strata.client.widget.UndoManager<>();
    private Button undoButton;
    /** Where Save puts a mob's list, in words (GoalsOps#saveTarget), read at each load. */
    private String saveLine = "";
    /** True while the game's own goals, with their real values, are on their way. */
    private boolean waitingForGame;
    private String name = "";
    private BrowseList list;
    /**
     * HOW THE LIST FIGHTS: the attack reader and the mob's attack damage, read once from the game layer, and
     * the line for the rows it was last worked out for.
     */
    private com.strata.model.Attacks attacks;
    private Double attackDamage;
    private List<GoalRow> fightRows;
    private com.strata.model.Attacks.Line fightLine = new com.strata.model.Attacks.Line("", false);

    private GoalsScreen(boolean editsGoalSet, String goalSetName, String owner, String baseEntity,
                         String scope, String scopeId, String mobGoalSet, java.util.function.Consumer<String> onNamed) {
        super(editsGoalSet ? (goalSetName == null ? "Strata — New goal set" : "Strata — Goal set: " + goalSetName)
                : "Strata — Goals: " + (owner.equals(com.strata.model.Variant.defaultNameFor(baseEntity))
                        ? com.strata.util.Labels.creature(baseEntity) : owner));
        this.editsGoalSet = editsGoalSet;
        this.goalSetName = goalSetName == null ? "" : goalSetName;
        this.owner = owner;
        this.baseEntity = baseEntity;
        this.scope = scope;
        this.scopeId = scopeId;
        this.mobGoalSet = mobGoalSet;
        this.onNamed = onNamed == null ? n -> { } : onNamed;
        if (!editsGoalSet) {
            editingMob(baseEntity, owner);
        }
    }

    /** The editor for an archetype, or a new one when name is null. */
    public static GoalsScreen forGoalSet(String name) {
        return new GoalsScreen(true, name, "", "", "", "", "", null);
    }

    /**
     * The editor for a mob instance's AI.
     *
     * @param owner the variant, or the plain mob's default variant
     * @param preset the behavior as stored
     * @param onNamed told the name when the list is saved as a named behavior
     */
    public static GoalsScreen forMob(String owner, String baseEntity, String scope, String scopeId,
                                      String mobGoalSet, java.util.function.Consumer<String> onNamed) {
        return new GoalsScreen(false, null, owner, baseEntity, scope, scopeId, mobGoalSet == null ? "" : mobGoalSet,
                onNamed);
    }

    @Override
    protected void init() {
        super.init();
        if (rows == null) {
            load();
        }
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        // THE TOP ROW AND THE BOTTOM BAR WRAP to the width, and the list runs between them: the layout is the
        // UI layer's.
        com.strata.ui.Layout layout = layout();

        Button save = Button.builder(Component.literal("Save"), b -> save()).build();
        // UNDO: the unsaved goal changes, one at a time; Ctrl+Z presses it.
        undoButton = Button.builder(Component.literal("Undo"), b -> {
            List<GoalRow> previous = undo.undo();
            if (previous != null) {
                rows = new ArrayList<>(previous);
                rebuild();
            }
        }).build();
        placeAll(layout, new String[]{"undo", "save"}, undoButton, save);
        if (editsGoalSet) {
            EditField nameField = field(0, 0, 0, 0, "Name", name,
                    (EditField.Check) s -> GoalsOps.nameProblem(s, goalSetName, GoalsOps.goalSetNames()), null);
            nameField.setResponder(s -> name = s.strip());
            place(nameField.getWidget(), layout.get("name"));
        } else {
            // No Start from goal set.
            EditField nameField = field(0, 0, 0, 0, "Goal set name", name,
                    (EditField.Check) s -> GoalsOps.nameProblem(s, "", GoalsOps.goalSetNames()), null);
            nameField.setResponder(s -> name = s.strip());
            place(nameField.getWidget(), layout.get("name"));
            placeAll(layout, new String[]{"saveAs"}, Button.builder(Component.literal("Save as goal set"),
                    b -> confirmName()).build());
        }
        placeAll(layout, new String[]{"copy"}, Button.builder(Component.literal("Copy from mob"),
                b -> pickMob()).build());

        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("GoalsScreen");
        placeList(list, layout.get("list"));
        addRenderableWidget(list);
        // DRAG A GOAL TO REORDER: GoalsOps#moved, within WHO or within HOW.
        list.setOnReorder((from, to) -> {
            List<GoalRow> next = GoalsOps.moved(rows, Integer.parseInt(from), Integer.parseInt(to));
            if (next == null) {
                refuse("A goal moves within its own list: target goals or goals");
                return;
            }
            changing();
            rows = new ArrayList<>(next);
            clearRefusal();
            rebuild();
        });

        Button add = Button.builder(Component.literal("+ Add goal"), b -> addGoal()).build();
        Button edit = Button.builder(Component.literal("Edit"), b -> editSelected()).build();
        // EVERY PICKED GOAL: Remove and On / Off act on all of them, Del too.
        Button remove = Button.builder(Component.literal("Remove"), b -> removePicked()).build();
        Button toggle = Button.builder(Component.literal("On / Off"), b -> {
                    List<Integer> picked = pickedIndexes();
                    if (!picked.isEmpty()) {
                        changing();
                        rows = new ArrayList<>(GoalsOps.switchedRows(rows, picked));
                        rebuild();
                    }
                })
                .build();
        // The old Hostile button is gone: its layout slot was never declared, so it never showed.
        placeAll(layout, new String[]{"add", "edit", "remove", "toggle"}, add, edit, remove, toggle);
        needsSelection(() -> selectedIndex() >= 0 && !list.manyPicked(), edit);
        needsSelection(() -> !pickedIndexes().isEmpty(), remove, toggle);

        rebuild();
        list.restore(keepList);
    }

    /** The layout for the list as it stands. */
    private com.strata.ui.Layout layout() {
        // The order is the goals' priority: the top runs first.
        String line = editsGoalSet ? "Drag a goal up or down: the top runs first. Edit a goal to change how it works."
                : "Drag a goal up or down: the top runs first. Now: " + source + ". " + saveLine;
        return com.strata.ui.screen.ListScreenLayouts.goalsList(frame(), this.font::width, title(), editsGoalSet,
                line, fightLine().text());
    }

    /** What the list fights with (GoalsOps#attacks, Attacks#line), worked out again when the rows change. */
    private com.strata.model.Attacks.Line fightLine() {
        if (rows == null) {
            return fightLine;
        }
        if (attacks == null) {
            attacks = GoalsOps.attacks();
            attackDamage = editsGoalSet ? null : GoalsOps.attackDamage(baseEntity);
        }
        if (!rows.equals(fightRows)) {
            fightRows = List.copyOf(rows);
            fightLine = attacks.line(rows, !editsGoalSet, attackDamage);
        }
        return fightLine;
    }
    /** Keeps the list as it is, for Undo, before a change. */
    private void changing() {
        undo.pushState(List.copyOf(rows));
    }

    /** The first open: the archetype's rows, or what the mob runs now. */
    private void load() {
        undo.clear();
        if (editsGoalSet) {
            name = goalSetName;
            rows = GoalsOps.sorted(GoalsOps.goalSet(goalSetName));
            source = goalSetName.isEmpty() ? "a new goal set" : "goal set '" + goalSetName + "'";
            baseline = List.copyOf(rows);
            return;
        }
        GoalsOps.Effective effective = GoalsOps.effective(owner, baseEntity, scope, scopeId, mobGoalSet);
        source = effective.source();
        GoalsOps.SaveTarget target = GoalsOps.saveTarget(effective, GoalsOps.goalSetNames(),
                GoalsOps.mobName(owner, baseEntity));
        saveLine = target.isNew() ? "Save makes it the goal set '" + target.goalSet() + "'."
                : "Save changes the goal set '" + target.goalSet() + "' for every mob that uses it.";
        if (effective.rows() != null) {
            rows = GoalsOps.sorted(effective.rows());
        } else {
            // The game's own goals, with their real values, once they arrive.
            rows = new ArrayList<>();
            waitingForGame = true;
        }
        baseline = List.copyOf(rows);
    }

    private void readGameGoals() {
        MobGoals.Answer answer = MobGoals.lookup(baseEntity);
        if (answer.goals() != null) {
            waitingForGame = false;
            rows = GoalsOps.fromGame(answer.goals());
            baseline = List.copyOf(rows);
            source = "the game's own goals, with their real values";
            if (list != null) {
                rebuild();
            }
        } else if (!answer.pending()) {
            waitingForGame = false;
            source = answer.note();
        }
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return baseline != null && rows != null && !baseline.equals(rows);
    }

    /** The picked goals' indexes into the rows (BrowseList#pickedIds): the group headers are not goals. */
    private List<Integer> pickedIndexes() {
        if (list == null) {
            return List.of();
        }
        return list.pickedIds().stream().filter(id -> !id.startsWith("grp:")).map(Integer::parseInt).toList();
    }

    /** Removes the picked goals: one at once, several after a question that names them. */
    private void removePicked() {
        List<Integer> picked = pickedIndexes();
        if (picked.isEmpty()) {
            return;
        }
        Runnable remove = () -> {
            changing();
            rows = new ArrayList<>(GoalsOps.withoutRows(rows, picked));
            rebuild();
        };
        if (picked.size() == 1) {
            remove.run();
            return;
        }
        String names = com.strata.domain.Picks.listed(picked.stream().map(i -> rows.get(i).name()).toList());
        minecraft.setScreen(new ConfirmDialog(this, "Remove " + picked.size() + " goals?\n" + names, remove));
    }

    private int selectedIndex() {
        BrowseList.Entry sel = list == null ? null : list.getSelected();
        if (sel == null || sel.id.startsWith("grp:")) {
            return -1;
        }
        return Integer.parseInt(sel.id);
    }

    private void rebuild() {
        rows = GoalsOps.sorted(rows);
        // A GOAL THIS MOB CANNOT RUN SAYS SO (a bat given a phantom's AI): applying the list skips it, which
        // before showed only in the log.
        boolean forMob = baseEntity != null && !baseEntity.isBlank();
        java.util.Set<String> met = forMob ? GoalsOps.needsMet(baseEntity) : null;
        java.util.Set<String> own = forMob ? GoalsOps.ownGoalClasses(baseEntity) : java.util.Set.of();
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (boolean who : new boolean[]{true, false}) {
            String group = who ? "grp:target" : "grp:goal";
            entries.add(new BrowseList.Entry(group, who ? "Target goals: pick a target" : "Goals: what they do", "",
                    Palette.GROUP, group, null, 0));
            for (int i = 0; i < rows.size(); i++) {
                GoalRow row = rows.get(i);
                if (row.target() != who) {
                    continue;
                }
                String when = row.condition().type() == com.strata.model.GoalCondition.Type.ALWAYS ? ""
                        // Words as the dropdown shows them.
                        : "  only if: " + com.strata.ui.ChoiceWords.word(row.condition().type().word())
                                + (row.condition().type().takesValue() ? " " + row.condition().value() : "");
                String whyNot = forMob && row.enabled() ? GoalsOps.whyNot(row, met, own) : null;
                entries.add(new BrowseList.Entry(String.valueOf(i),
                        row.priority() + "  " + row.name() + (row.enabled() ? "" : "  [off]")
                                + (whyNot == null ? "" : "  [won't run]"),
                        whyNot == null ? GoalsOps.settingsLine(row) + when : "Won't run on this mob: " + whyNot,
                        !row.enabled() ? Palette.MUTED : whyNot != null ? Palette.ERROR : row.game() ? Palette.INHERITED : Palette.TEXT,
                        null, group, 1));
            }
        }
        list.refresh(entries);
    }

    private void addGoal() {
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Add which goal?", GoalsOps.goalOptions(),
                goal -> {
                    changing();
                    rows.add(GoalsOps.newRow(goal, rows));
                }));
    }

    private void editSelected() {
        int i = selectedIndex();
        if (i < 0) {
            return;
        }
        ScreenNavigator.get().navigateTo(new GoalScreen(rows.get(i), edited -> {
            changing();
            rows.set(i, edited);
        }));
    }

    /**
     * Starts the list from any mob's own AI, as the game layer holds it: a mod's mob included, with its real
     * values.
     */
    private void pickMob() {
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Copy the goals of which mob?",
                RegistryPickerScreen.plainOptions("mob", GoalsOps.mobsWithGoals()),
                picked -> {
                    changing();
                    rows = GoalsOps.copyOf(picked);
                }));
    }

    /** Saves the list as a named behavior the mob then uses, after saying so. */
    private void confirmName() {
        String problem = GoalsOps.nameProblem(name, "", GoalsOps.goalSetNames());
        if (problem == null && rows.isEmpty()) {
            problem = "A goal set needs at least one goal";
        }
        if (problem != null) {
            refuse(problem);
            return;
        }
        String n = name;
        minecraft.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Save this list as the goal set '" + n + "'?\nThis mob then uses it by name, and any mob can"
                        + " pick it.\nIt is edited in Mobs > Goal sets.",
                () -> {
                    if (report(GoalsOps.nameOwnList(owner, scope, scopeId, n, rows))) {
                        mobGoalSet = n;
                        onNamed.accept(n);
                        com.strata.client.util.RecentEdits.touchGoalSet(n);
                        // BACK TO THE MOB.
                        rows = null;
                        baseline = null;
                        savedHere();
                        deferBack();
                    }
                }));
    }

    private void save() {
        if (!editsGoalSet) {
            // SAVE EDITS THE BEHAVIOR THE MOB RUNS: never an own list.
            GoalsOps.MobGoalsSave saved = GoalsOps.saveMobGoals(owner, baseEntity, scope, scopeId, mobGoalSet, rows);
            if (report(saved.report())) {
                String goalSet = saved.target().goalSet();
                // The mob runs the saved set by name (a new one, or one picked in its editor and stored with
                // this save): its form takes it too, so the mob's Save does not write the old one back.
                if (saved.target().isNew() || goalSet.equals(mobGoalSet)) {
                    mobGoalSet = goalSet;
                    onNamed.accept(goalSet);
                }
                com.strata.client.util.RecentEdits.touchGoalSet(goalSet);
                // Save stays: the list is read again.
                rows = null;
                savedHere();
                rebuildWidgets();
            }
            return;
        }
        if (report(GoalsOps.saveGoalSet(goalSetName, name, rows))) {
            baseline = List.copyOf(rows);
            // A saved behavior goes to the top of the Recent group.
            com.strata.client.util.RecentEdits.touchGoalSet(name);
            // SAVE STAYS: a new or renamed behavior opens again as its own editor.
            if (!name.equals(goalSetName)) {
                reopen(GoalsScreen.forGoalSet(name));
                return;
            }
            savedHere();
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (waitingForGame) {
            readGameGoals();
        }
        if (undoButton != null) {
            undoButton.active = undo.canUndo();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("footer"), Palette.MUTED);
        // What the list fights with; the warning color when it cannot fight.
        text(graphics, layout.get("hint0"), fightLine().warning() ? Palette.ERROR : Palette.LABEL);
    }
}
