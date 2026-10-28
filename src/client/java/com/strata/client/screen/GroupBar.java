package com.strata.client.screen;

import com.strata.client.nav.ScreenNavigator;
import com.strata.client.widget.EditField;
import com.strata.domain.GroupOps;
import com.strata.domain.WriteReport;
import com.strata.game.RegistryQuery;
import com.strata.ui.Layout;
import com.strata.ui.screen.GroupLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** THE GROUP ROW, WIRED ONCE. */
final class GroupBar {

    /** What the editor behind this row does. */
    interface Host {
        /** Whether this list can be handed groups at all. */
        boolean canTakeGroups();

        /**
         * What this list would gain from the group, in the words the write itself would use - the footer's
         * sentence, asked BEFORE the apply so the sentence and the write cannot disagree.
         */
        GroupOps.Added adds(String groupId);

        /** Stamps the group onto this list and redraws it. */
        boolean stamp(String groupId);

        /** Opens this group's own rows in this same editor. */
        void openGroup(GroupOps.Group group);

        /** Redraws the list after a group action that did not change its rows. */
        void redraw();
    }

    private final StrataScreen screen;
    private final Host host;
    /** Which kind of group this row hands out: abilities, loot or equipment. */
    private final String kind;
    /** What ONE row of this kind is called in the note: "abilities", "drops", "equipment". */
    private final String rows;

    /**
     * The group picked in the picker, as its ID: that is what a picker hands back, and it is the owner key
     * the group's rows are filed under - the one thing that cannot be wrong about which group this is.
     */
    private String group = "";
    /** What the last group action said, in the footer. */
    private String note = "";
    private EditField nameField;

    GroupBar(StrataScreen screen, Host host, String kind, String rows) {
        this.screen = screen;
        this.host = host;
        this.kind = kind;
        this.rows = rows;
    }

    /** Whether there is a group to pick, so "none yet" is only shown when it is true. */
    boolean anyMade() {
        return host.canTakeGroups() && !GroupOps.ofKind(kind).isEmpty();
    }

    /** Whether a group is picked, which greys Apply and Edit rather than letting them refuse. */
    boolean picked() {
        return !group.isBlank();
    }

    /**
     * The picker's text: the picked group's NAME, "none yet" when this kind has no group at all, and ""
     * before a pick when it does.
     */
    String text() {
        return GroupLayout.text(name(), anyMade());
    }

    /**
     * The footer's group half: what the last action said, or - before any action - what the apply would do.
     */
    String note() {
        if (!note.isEmpty()) {
            return note;
        }
        return picked() ? host.adds(group).said() : "";
    }

    /** The picked group's name for the picker button, or the id if it has gone. */
    String name() {
        GroupOps.Group found = GroupOps.find(group, com.strata.data.Paths.ROOT);
        return found == null ? group : found.name();
    }

    /**
     * Makes the group's widgets and puts them where the layout says, in ONE call, so no screen has to
     * remember the order.
     *
     * @param show how the screen puts a widget on itself. Asked of the screen rather than reached for here
     *     because Minecraft keeps that entry point protected: the caller is inside the subclass, this class is
     *     not, and a front end should not widen the game's own visibility to get around that.
     */
    void init(Font font, Layout layout, Consumer<AbstractWidget> show) {
        if (!host.canTakeGroups() || layout.get("group") == null) {
            return;
        }
        // Kept across init, so a dialog that closes does not clear the name the designer just typed.
        String keep = nameField == null ? "" : nameField.getValue();
        nameField = new EditField(font, 0, 0, 0, 0, "grp", keep);
        nameField.getWidget().setMaxLength(64);
        add(show, Button.builder(Component.literal(layout.textOf("group")), b -> pick()).build(),
                layout.get("group"));
        add(show, Button.builder(Component.literal(GroupLayout.APPLY_GROUP), b -> apply()).build(),
                layout.get("applyGroup"));
        add(show, Button.builder(Component.literal(GroupLayout.EDIT_GROUP), b -> open()).build(),
                layout.get("editGroup"));
        add(show, nameField.getWidget(), layout.get("groupName"));
        add(show, Button.builder(Component.literal(GroupLayout.MAKE_GROUP), b -> make()).build(),
                layout.get("groupMake"));
    }

    private <T extends AbstractWidget> void add(Consumer<AbstractWidget> show, T widget,
                                                Layout.Element where) {
        show.accept(screen.place(widget, where));
    }

    /** Picks one of this kind's groups. */
    private void pick() {
        List<GroupOps.Group> groups = host.canTakeGroups() ? GroupOps.ofKind(kind) : List.of();
        if (groups.isEmpty()) {
            screen.refuse("No groups yet. Name one below and press Make group.");
            return;
        }
        // The picker hands back the option's ID, which is the group's id: the display name is for the
        // designer and the id is for the write.
        List<RegistryQuery.Option> options = groups.stream()
                .map(g -> new RegistryQuery.Option(g.id(), g.name(), "")).toList();
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Which group?", options, picked -> {
            group = picked;
            note = "";
            // The picker button, the two actions and the footer all read this field, so the widgets are
            // rebuilt rather than patched one by one.
            host.redraw();
        }));
    }

    /** Copies the chosen group's rows into this list. */
    private void apply() {
        if (!picked()) {
            return;
        }
        GroupOps.Added adds = host.adds(group);
        if (!host.stamp(group)) {
            return;
        }
        note = adds.toAdd() == 0
                ? adds.said()
                : adds.said() + ". They are yours now: changing " + name()
                + " later will not change them.";
    }

    /** Opens the chosen group's own rows in this same editor. */
    private void open() {
        if (!picked()) {
            return;
        }
        GroupOps.Group found = GroupOps.find(group, com.strata.data.Paths.ROOT);
        if (found == null) {
            screen.refuse("There is no group called " + group);
            return;
        }
        host.openGroup(found);
    }

    /** Makes a group of this kind from the name field, and picks it. */
    private void make() {
        String name = nameField == null ? "" : nameField.getValue();
        WriteReport made = GroupOps.create(name, kind);
        if (made.ok()) {
            group = GroupOps.idFor(name);
            nameField.setValue("");
            note = "Made the group " + name() + ". Open it with Edit group and add " + rows + " to it.";
        }
        screen.report(made);
    }
}
