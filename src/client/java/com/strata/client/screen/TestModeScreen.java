package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.Strata;
import com.strata.client.nav.ScreenNavigator;
import com.strata.util.Labels;
import com.strata.client.widget.EditField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Test mode: spawn a temporary mob at a given level to preview scaling. */
public class TestModeScreen extends StrataScreen {

    private String selectedEntity = "minecraft:zombie";
    private String levelValue = "5";
    private EditField levelField;
    private String feedback = "";

    public TestModeScreen() {
        super("Strata — Test mode");
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    @Override
    protected void init() {
        super.init();
        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        addRenderableWidget(place(Button.builder(Component.literal(layout.textOf("entity")),
                b -> openEntityPicker()).build(), layout.get("entity")));
        levelField = new EditField(this.font, 0, 0, 0, 0, "Level", levelValue);
        levelField.setValidator(EditField.integerValidator());
        levelField.setResponder(s -> levelValue = s);
        addRenderableWidget(place(levelField.getWidget(), layout.get("level")));
        // Not an edit form (no Undo/Save), so the Spawn action stays on the canonical row under the fields.
        placeAll(layout, new String[]{"spawn"}, Button.builder(Component.literal("Spawn test mob"),
                b -> spawnTest()).build());
    }

    /**
     * The layout for the preview and the lines as they stand: the preview holds as many
     * lines as the view does.
     */
    private com.strata.ui.Layout layout() {
        String head = null;
        java.util.List<String> preview = new java.util.ArrayList<>();
        try {
            int level = Integer.parseInt(levelValue.strip());
            var curves = com.strata.runtime.Scaling.mobs();
            if (!curves.isEmpty()) {
                head = "At level " + level + ":";
                for (var curve : curves) {
                    preview.add(Labels.attribute(curve.attributeId().toString())
                            + ": +" + Labels.number(curve.bonusAt(level) * 100) + "%");
                }
            }
        } catch (NumberFormatException e) {
            // Rendered while the operator is still typing, so DEBUG rather than warn.
            Strata.LOGGER.debug("Test preview skipped for a partial number: {}", e.toString());
        }
        com.strata.ui.Layout layout = com.strata.ui.screen.MenuLayouts.testMode(frame(), this.font::width, title(),
                Labels.creature(selectedEntity), head, preview, feedback,
                "Note: /strata test spawn uses the same pipeline as natural spawns");
        useLayout(layout);
        return layout;
    }
    private void openEntityPicker() {
        ScreenNavigator.get().navigateTo(new RegistryPickerScreen(
                "Pick a mob",
                RegistryPickerScreen.entitiesWithRecent(),
                id -> selectedEntity = id));
    }

    /** Sends the test spawn. */
    private void spawnTest() {
        feedback = "";
        String problem = com.strata.command.StrataCommands.testSpawnProblem(
                selectedEntity, levelField.getValue());
        if (problem != null) {
            refuse(problem);
            return;
        }
        if (this.minecraft == null || this.minecraft.player == null) {
            refuse("No world loaded — open a world first");
            return;
        }
        String command = com.strata.command.StrataCommands.testSpawnCommand(
                selectedEntity.strip(), Integer.parseInt(levelField.getValue().strip()));
        this.minecraft.player.connection.sendCommand(command);
        clearRefusal();
        feedback = "Sent /" + command + " — the result is in chat, close the editor to read it";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (com.strata.ui.Layout.Element e : layout().elements()) {
            if (e.kind() != com.strata.ui.Layout.Kind.TEXT || e.id().equals("title")) {
                continue;
            }
            int color = e.id().equals("feedback") ? Palette.DONE : e.id().equals("note") ? Palette.MUTED
                    : e.id().startsWith("preview") && !e.id().equals("previewHead") ? Palette.HINT : Palette.LABEL;
            text(graphics, e, color);
        }
    }
}
