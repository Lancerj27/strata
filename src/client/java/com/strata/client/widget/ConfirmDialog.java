package com.strata.client.widget;

import com.strata.ui.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Modal confirmation dialog. */
public class ConfirmDialog extends Screen {

    private final Screen parent;
    private final String message;
    private final Runnable onConfirm;
    /** The confirm button's word: the action it does. */
    private final String confirmWord;

    /** The confirm button takes its word from the message ("Delete...?" gives Delete). */
    public ConfirmDialog(Screen parent, String message, Runnable onConfirm) {
        this(parent, message, com.strata.ui.screen.ConfirmLayout.actionWord(message), onConfirm);
    }

    /** @param confirmWord the confirm button's word, for a message that does not start with its verb */
    public ConfirmDialog(Screen parent, String message, String confirmWord, Runnable onConfirm) {
        super(Component.literal("Confirm"));
        this.parent = parent;
        this.message = message;
        this.confirmWord = confirmWord;
        this.onConfirm = onConfirm;
    }

    /** The screen under the dialog: the editor that acts when it confirms. */
    public Screen parent() {
        return parent;
    }

    /** The layout: the message and the buttons, as the fit gate checked it. */
    private com.strata.ui.Layout layout() {
        return com.strata.ui.screen.ConfirmLayout.build(new com.strata.ui.Frame(this.width, this.height),
                this.font::width, message, confirmWord);
    }

    @Override
    protected void init() {
        com.strata.ui.Layout layout = layout();
        com.strata.ui.Box confirm = layout.boxOf("confirm");
        com.strata.ui.Box cancel = layout.boxOf("cancel");
        addRenderableWidget(Button.builder(Component.literal(confirmWord), b -> confirm())
                .pos(confirm.x(), confirm.y()).size(confirm.w(), confirm.h()).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            minecraft.setScreen(parent);
        }).pos(cancel.x(), cancel.y()).size(cancel.w(), cancel.h()).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // The message split at "\n", wrapped to the screen, and kept on it: a long one ran off the top of a
        // small window.
        for (com.strata.ui.Layout.Element e : layout().elements()) {
            if (e.id().startsWith("line")) {
                graphics.drawString(this.font, e.text(), e.box().x(), e.box().y(), Palette.TEXT);
            }
        }
    }
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void confirm() {
        onConfirm.run();
        minecraft.setScreen(parent);
    }

    /** Enter confirms, as the highlighted button would. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ("save".equals(com.strata.ui.Keys.action(key, hasControlDown(), hasShiftDown()))) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
