package com.strata.client.screen;

import com.strata.client.widget.EditField;
import com.strata.domain.FormulaOps;
import com.strata.model.ScalingFormulas.Kind;
import com.strata.ui.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scaling > Formulas: every scaling formula, edited as text, each with its live preview. */
public class FormulasScreen extends StrataScreen {

    private static final List<Kind> KINDS = Arrays.asList(Kind.values());

    private final Map<Kind, EditField> fields = new LinkedHashMap<>();
    private final Map<Kind, String> saved = new LinkedHashMap<>();
    private Button saveButton;
    private Button defaultsButton;

    public FormulasScreen() {
        super("Strata — Formulas");
        for (Kind k : KINDS) {
            saved.put(k, FormulaOps.expression(k));
        }
    }

    @Override
    protected boolean scrolls() {
        return true;
    }

    @Override
    protected void init() {
        Map<Kind, String> typed = new LinkedHashMap<>();
        fields.forEach((k, f) -> typed.put(k, f.getValue()));
        fields.clear();
        super.init();
        com.strata.ui.Layout layout = layout();
        for (Kind k : KINDS) {
            EditField f = field(0, 0, 0, 0, k.label, typed.getOrDefault(k, saved.get(k)),
                    (EditField.Check) s -> FormulaOps.problem(k, s), this::refreshButtons);
            place(f.getWidget(), layout.get(k.id));
            fields.put(k, f);
        }
        defaultsButton = Button.builder(Component.literal("Defaults"), b -> defaults()).build();
        saveButton = Button.builder(Component.literal("Save"), b -> save()).build();
        placeAll(layout, new String[]{"undo", "save"}, defaultsButton, saveButton);
        refreshButtons();
    }

    private com.strata.ui.Layout layout() {
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<String> previews = new ArrayList<>();
        for (Kind k : KINDS) {
            ids.add(k.id);
            labels.add(k.label);
            EditField f = fields.get(k);
            String text = f == null ? saved.get(k) : f.getValue();
            String problem = FormulaOps.problem(k, text);
            previews.add(k.label + ": " + (problem != null ? problem : FormulaOps.preview(k, text)));
        }
        com.strata.ui.Layout built = com.strata.ui.screen.FormScreenLayouts.formulas(frame(), this.font::width,
                title(), ids, labels, previews, "Scaling");
        useLayout(built);
        return built;
    }

    @Override
    protected boolean hasUnsavedEdits() {
        return fields.entrySet().stream().anyMatch(e -> !e.getValue().getValue().strip().equals(saved.get(e.getKey())));
    }

    private void refreshButtons() {
        if (saveButton == null) return;
        saveButton.active = hasUnsavedEdits() && fields.values().stream().allMatch(EditField::isValid);
    }

    /** Puts every default back in the fields. */
    private void defaults() {
        fields.forEach((k, f) -> f.setValue(k.defaultExpression));
        refreshButtons();
    }

    private void save() {
        if (!saveButton.active) return;
        for (Map.Entry<Kind, EditField> e : fields.entrySet()) {
            String text = e.getValue().getValue().strip();
            if (text.equals(saved.get(e.getKey()))) continue;
            if (!report(FormulaOps.save(e.getKey(), text))) {
                return;
            }
            saved.put(e.getKey(), text);
        }
        savedHere();
        refreshButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        com.strata.ui.Layout layout = layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        titles(graphics, layout);
        for (int i = 0; i < KINDS.size(); i++) {
            Kind k = KINDS.get(i);
            EditField f = fields.get(k);
            boolean bad = f != null && !f.isValid();
            text(graphics, layout.get("note" + i), bad ? Palette.ERROR : Palette.TEXT);
            tooltip(graphics, mouseX, mouseY, layout.get(k.id),
                    wrapTip(k.help + " Names: " + String.join(", ", k.variableList())
                            + ". Functions: min max clamp floor ceil round abs sqrt log exp if(c, a, b)."
                            + " " + FormulaOps.sampleText(k)));
        }
        text(graphics, layout.get("footer1"), Palette.MUTED);
    }
}
