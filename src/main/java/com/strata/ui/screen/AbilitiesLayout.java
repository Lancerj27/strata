package com.strata.ui.screen;

import com.strata.ui.Frame;
import com.strata.ui.Grid;
import com.strata.ui.Layout;
import com.strata.ui.TextFit;

import java.util.List;

/** THE ABILITIES LIST, LAID OUT: AbilitiesScreen. */
public final class AbilitiesLayout {

    private AbilitiesLayout() {
    }

    public static final String REMOVE = "Remove";
    public static final String TURN_OFF = "Turn off";
    /** The Parameter picker's width. */
    public static final int PARAMETER_W = 90;
    /** The Entity picker's width. */
    public static final int ENTITY_W = 80;

    /** The Parameter picker's width on a wide column; PARAMETER_W on a small one. */
    public static int parameterW(Frame frame) {
        return Grid.wide(PARAMETER_W, frame.contentWidth(), 12, 130);
    }

    /** The Entity picker's width on a wide column; ENTITY_W on a small one. */
    public static int entityW(Frame frame) {
        return Grid.wide(ENTITY_W, frame.contentWidth(), 10, 110);
    }
    /** Every add-row cell, in the owner's order. */
    public static final List<String> FIELDS = List.of(
            "ability", "entity", "parameter", "trigger", "amount", "ticks", "duration", "radius", "chance",
            "damage", "damage_type", "can_kill", "cure", "look");

    /** The cells only some classes read, shown only for them. */
    public static final List<String> CLASS_ONLY = List.of("damage", "damage_type", "can_kill", "cure", "look");

    /** The Can kill choices, as stored. */
    public static final List<String> CAN_KILL_CHOICES = List.of("false", "true");

    /**
     * The texts of the class-only cells.
     *
     * @param damageType the Damage type picker's text
     * @param cure the Cure picker's text
     * @param canKill the Can kill choice, "true" or "false"
     * @param canKillLabel the cell's label: "Can kill", or "Hurts itself" on an explode row
     */
    public record Extras(String damageType, String cure, String canKill, String particle, String hudIcon,
                         String canKillLabel) {
        public static final Extras NONE = new Extras("", "", "false", "", "", "Can kill");

        public Extras(String damageType, String cure, String canKill) {
            this(damageType, cure, canKill, "", "", "Can kill");
        }
    }

    /**
     * @param abilities every ability the Ability dropdown offers
     * @param removeLabel REMOVE or TURN_OFF, with a count for several rows (ListLayout#manyLabel)
     * @param parameterText the Parameter picker's text: the chosen effect or damage type, or none
     * @param triggerText the Trigger picker's current label
     * @param entityText the Entity picker's current label
     * @param groupText the chosen group's NAME, or {@link GroupLayout#NO_GROUPS}. The "Group: " prefix and
     *     the cut to width are added by {@link GroupLayout#top}, exactly as {@code parameterText} arrives raw.
     *     The picker opens on the groups of this screen's kind, never on every kind's: a group of loot has no
     *     abilities to hand.
     * @param canGroup false when no group is picked, which greys Apply and Edit rather than leaving them to
     *     refuse after the click
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, List<String> abilities,
                               String removeLabel, String parameterText, String triggerText, String entityText,
                               String footer, String groupText, boolean canGroup) {
        return build(frame, measure, title, abilities, removeLabel, parameterText, triggerText, entityText,
                footer, groupText, canGroup, id -> !CLASS_ONLY.contains(id), Extras.NONE);
    }

    /**
     * As above, showing only the cells the chosen ability or class reads.
     *
     * @param shows true for a cell id the chosen row reads
     */
    public static Layout build(Frame frame, TextFit.Measure measure, String title, List<String> abilities,
                               String removeLabel, String parameterText, String triggerText, String entityText,
                               String footer, String groupText, boolean canGroup,
                               java.util.function.Predicate<String> shows, Extras extras) {
        int pw = parameterW(frame);
        int ew = entityW(frame);
        List<FormLayout.Cell> all = List.of(FormLayout.Cell.choice("ability", 110, "Ability / Effect", "Ability", abilities),
                        // The entity the class acts on (spawn's creature), picked from the game's entity
                        // registry so a mod's entities are offered too.
                        FormLayout.Cell.button("entity", ew, "Entity",
                                TextFit.clip(entityText, ew - Layout.LABEL_MARGIN, measure)),
                        // The effect or damage type the row applies; only some effects use it.
                        FormLayout.Cell.button("parameter", pw, "Applied effect",
                                TextFit.clip(parameterText, pw - Layout.LABEL_MARGIN, measure)),
                        // What fires the row: on spawn, on hit, on hurt, in sunlight, etc.
                        FormLayout.Cell.button("trigger", pw, "Trigger",
                                TextFit.clip(triggerText, pw - Layout.LABEL_MARGIN, measure)),
                        FormLayout.Cell.field("amount", 50, "Amount"),
                        FormLayout.Cell.field("ticks", 50, "Ticks"),
                        FormLayout.Cell.field("duration", 50, "Duration"),
                        FormLayout.Cell.field("radius", 50, "Radius"),
                        // The percent chance the row lands when its trigger fires; blank is 100.
                        FormLayout.Cell.field("chance", 50, "Chance %"),
                        FormLayout.Cell.field("damage", 50, "Damage"),
                        FormLayout.Cell.button("damage_type", pw, "Damage type",
                                TextFit.clip(extras.damageType(), pw - Layout.LABEL_MARGIN, measure)),
                        FormLayout.Cell.choice("can_kill", 70, extras.canKillLabel(), extras.canKillLabel(),
                                CAN_KILL_CHOICES),
                        FormLayout.Cell.button("cure", ew, "Cure item",
                                TextFit.clip(extras.cure(), ew - Layout.LABEL_MARGIN, measure)),
                        // The row's own particle and HUD icon, one cell so the widest class still fits a
                        // small screen: it asks which to set.
                        FormLayout.Cell.button("look", 60, "Look",
                                TextFit.clip(extras.particle().isEmpty() && extras.hudIcon().isEmpty() ? "Game's"
                                        : "Own", 60 - Layout.LABEL_MARGIN, measure)));
        List<FormLayout.Cell> cells = all.stream().filter(c -> c.id().equals("ability") || shows.test(c.id())).toList();
        return ListLayout.buildWithUndo(frame, measure, new ListLayout.Input(title,
                GroupLayout.top(measure, groupText, canGroup),
                cells,
                List.of(ListLayout.Action.of("add", "+ Add ability").asMain(),
                        ListLayout.Action.of("edit", "Edit", 34),
                        ListLayout.Action.of("toggle", "On / Off", 54),
                        ListLayout.Action.of("remove", removeLabel, 130)),
                List.of(), footer, null)
                // Making a group lives under the picker it fills, so the name being typed and the list it
                // will hold are on screen together.
                .withUnderTop(GroupLayout.underTop()));
    }
}
