package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.nav.ScreenNavigator;
import com.strata.client.util.FileDialog;
import com.strata.client.widget.DropdownSelect;
import com.strata.client.widget.EditField;
import com.strata.domain.CustomAssetsOps;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/** Imports a custom texture or sound. */
public class CustomAssetScreen extends StrataScreen {

    private String kind;
    private String category = CustomAssetsOps.CATEGORIES.get(0);
    private String file = "";
    private String name = "";
    private String subtitle = "";
    /** The name made from the file's, so a name the person typed is not overwritten by the next file. */
    private String suggested = "";
    /** Re-import: the name and kind are the asset's own, and its file is replaced. */
    private final boolean replace;
    /** Given the new asset's id (a texture's path, a sound's event id) when Add lands; may be null. */
    private final Consumer<String> onAdded;

    private EditField fileField;
    private EditField nameField;
    private EditField subtitleField;
    private DropdownSelect kindDropdown;
    private DropdownSelect categoryDropdown;
    private Button addButton;
    private CustomAssetsOps.Probe probe = new CustomAssetsOps.Probe("", "");

    /** A new import of a kind, from the library or a picker. */
    public CustomAssetScreen(String kind, Consumer<String> onAdded) {
        super("Strata — Add custom texture or sound");
        this.kind = CustomAssetsOps.KINDS.contains(kind) ? kind : CustomAssetsOps.TEXTURE;
        this.replace = false;
        this.onAdded = onAdded;
    }

    /** A new file for an asset already in the library. */
    public CustomAssetScreen(CustomAssetsOps.Asset existing) {
        super("Strata — Re-import: " + existing.id());
        this.kind = existing.kind();
        this.name = existing.id();
        this.category = existing.isTexture() ? existing.category() : this.category;
        this.subtitle = existing.subtitle();
        this.replace = true;
        this.onAdded = null;
    }

    private boolean texture() {
        return CustomAssetsOps.TEXTURE.equals(kind);
    }

    /**
     * An operator on a server UPLOADS the file to the server, instead of adding it to this machine's library.
     */
    private boolean upload() {
        return !replace && com.strata.client.ClientAssets.canUpload();
    }

    /** A form taller than the screen scrolls. */
    @Override
    protected boolean scrolls() {
        return true;
    }

    private com.strata.ui.Layout layout() {
        String problemLine = probe.problem() == null ? "" : probe.problem();
        String nameProblem = name.isEmpty() ? null : CustomAssetsOps.nameProblem(name);
        if (problemLine.isEmpty() && nameProblem != null) {
            problemLine = nameProblem;
        }
        com.strata.ui.Layout built = com.strata.ui.screen.CustomAssetLayout.build(frame(), this.font::width, title(),
                texture(), CustomAssetsOps.SOUND.equals(kind), CustomAssetsOps.KINDS, CustomAssetsOps.CATEGORIES,
                List.of(probe.summary() == null ? "" : probe.summary(), problemLine), CustomAssetsOps.where(),
                upload() ? "Upload to server" : "Add");
        useLayout(built);
        return built;
    }

    /** The file is a good one, and the name is. */
    private boolean ready() {
        return probe.ok() && !file.isBlank() && CustomAssetsOps.nameProblem(name) == null;
    }

    private void look() {
        probe = file.isBlank()
                ? new CustomAssetsOps.Probe("", "")
                : CustomAssetsOps.probe(kind, CustomAssetsOps.pathOf(file));
    }

    /** Add is usable while the file and the name are good. */
    private void refreshAdd() {
        if (addButton != null) {
            addButton.active = ready();
        }
    }

    @Override
    protected void init() {
        super.init();
        look();
        com.strata.ui.Layout layout = layout();

        kindDropdown = new DropdownSelect(0, 0, 0, 0, "", CustomAssetsOps.KINDS, kind);
        kindDropdown.setWords(s -> s);
        kindDropdown.setOnChange(s -> {
            if (!s.equals(kind)) {
                kind = s;
                rebuildWidgets();
            }
        });
        Button kindButton = addRenderableWidget(place(kindDropdown.getWidget(), layout.get("kind")));
        kindDropdown.fitTo(this.font::width, layout.boxOf("kind").w() - com.strata.ui.screen.ListLayout.CHOICE_MARGIN);
        // A re-import keeps its kind.
        kindButton.active = !replace;

        if (texture()) {
            categoryDropdown = new DropdownSelect(0, 0, 0, 0, "", CustomAssetsOps.CATEGORIES, category);
            categoryDropdown.setWords(s -> s);
            categoryDropdown.setOnChange(s -> category = s);
            addRenderableWidget(place(categoryDropdown.getWidget(), layout.get("category")));
            categoryDropdown.fitTo(this.font::width,
                    layout.boxOf("category").w() - com.strata.ui.screen.ListLayout.CHOICE_MARGIN);
        } else {
            categoryDropdown = null;
        }

        fileField = field(0, 0, 0, 0, "File", file, null, null);
        fileField.getWidget().setMaxLength(1024);
        place(fileField.getWidget(), layout.get("file"));
        fileField.setResponder(s -> {
            file = s;
            suggestName();
            look();
            refreshAdd();
        });
        Button browse = Button.builder(Component.literal(layout.textOf("browse")), b -> browse()).build();
        placeAll(layout, new String[]{"browse"}, browse);

        nameField = field(0, 0, 0, 0, "Name", name,
                (EditField.Check) s -> s.isEmpty() ? null : CustomAssetsOps.nameProblem(s), null);
        place(nameField.getWidget(), layout.get("name"));
        nameField.setResponder(s -> {
            name = s;
            refreshAdd();
        });
        // A re-import keeps the asset's name: the file under it is what changes.
        nameField.getWidget().setEditable(!replace);

        if (CustomAssetsOps.SOUND.equals(kind)) {
            subtitleField = field(0, 0, 0, 0, "Subtitle", subtitle, null, null);
            subtitleField.getWidget().setMaxLength(CustomAssetsOps.MAX_SUBTITLE);
            place(subtitleField.getWidget(), layout.get("subtitle"));
            subtitleField.setResponder(s -> subtitle = s);
        } else {
            subtitleField = null;
        }

        addButton = Button.builder(Component.literal(layout.textOf("add")), b -> add()).build();
        placeAll(layout, new String[]{"add"}, addButton);
        addButton.active = ready();
    }

    /** A name from the file's, while the name is empty or is the last one made this way. */
    private void suggestName() {
        if (replace) {
            return;
        }
        java.nio.file.Path path = CustomAssetsOps.pathOf(file);
        String made = path == null || path.getFileName() == null ? "" : CustomAssetsOps.suggestName(
                path.getFileName().toString());
        if (name.isEmpty() || name.equals(suggested)) {
            suggested = made;
            name = made;
            if (nameField != null) {
                nameField.setValue(made);
            }
        }
    }

    private void browse() {
        boolean png = texture();
        boolean json = CustomAssetsOps.MODEL.equals(kind);
        FileDialog.pick(png ? "Pick a PNG texture" : json ? "Pick a JSON block or item model" : "Pick an Ogg Vorbis sound",
                png ? "*.png" : json ? "*.json" : "*.ogg",
                png ? "PNG images" : json ? "JSON models" : "Ogg Vorbis sounds", picked -> {
                    if (fileField != null) {
                        fileField.setValue(picked);
                    }
                });
    }

    private void add() {
        if (!addButton.active) {
            return;
        }
        if (upload()) {
            uploadToServer();
            return;
        }
        var request = new CustomAssetsOps.Request(kind, CustomAssetsOps.pathOf(file), name, category, subtitle,
                replace);
        if (!report(CustomAssetsOps.add(request))) {
            return;
        }
        // The game reads its resources again, so the new texture is loadable and the sound is known.
        this.minecraft.reloadResourcePacks();
        var added = CustomAssetsOps.find(kind, name);
        if (onAdded != null && added != null) {
            onAdded.accept(added.resource());
        }
        deferBack();
    }

    /** Reads the picked file (never more than its kind's limit) and sends it; the answer comes as chat lines. */
    private void uploadToServer() {
        java.nio.file.Path path = CustomAssetsOps.pathOf(file);
        try {
            if (path == null || java.nio.file.Files.size(path) > com.strata.domain.AssetRules.maxFile(kind)) {
                report(com.strata.domain.WriteReport.refused("That file is missing or over the limit"));
                return;
            }
            String problem = com.strata.client.ClientAssets.upload(kind, texture() ? category : "", name, subtitle,
                    java.nio.file.Files.readAllBytes(path), false);
            if (problem != null) {
                report(com.strata.domain.WriteReport.refused(problem));
                return;
            }
        } catch (java.io.IOException e) {
            report(com.strata.domain.WriteReport.refused("Could not read that file: " + e.getMessage()));
            return;
        }
        deferBack();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        titles(graphics, layout);
        text(graphics, layout.get("note0"), Palette.HINT);
        text(graphics, layout.get("note1"), Palette.ERROR);
        text(graphics, layout.get("footer"), Palette.MUTED);
    }

    /** Opens this screen over the current one. */
    public static void open(String kind, Consumer<String> onAdded) {
        ScreenNavigator.get().navigateTo(new CustomAssetScreen(kind, onAdded));
    }
}
