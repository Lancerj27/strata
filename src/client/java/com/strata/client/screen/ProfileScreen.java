package com.strata.client.screen;

import com.strata.ui.Palette;
import com.strata.client.ClientNetwork;
import com.strata.client.widget.BrowseList;
import com.strata.client.widget.ConfirmDialog;
import com.strata.client.widget.EditField;
import com.strata.command.StrataCommands;
import com.strata.data.ProfileManager;
import com.strata.data.ProfileManager.Result;
import com.strata.model.Profile;
import com.strata.network.StrataNetwork;
import com.strata.network.StrataPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Profile management: list, save, activate, delete config profiles. */
public class ProfileScreen extends StrataScreen {

    private BrowseList list;
    private EditField nameField;
    private Button saveButton;
    private Tooltip typeNameTip;
    private List<Profile> profiles = List.of();
    private String status = "";
    private boolean statusOk = true;
    /** True when the working config differs from the active profile. */
    private boolean unsaved;
    /**
     * The active profile's name, read at each rebuild: the layout runs every frame, and it read
     * the file from disk each time.
     */
    private String activeProfile = "";

    public ProfileScreen() {
        super("Strata — Profiles");
    }

    @Override
    protected void init() {
        super.init();
        profiles = ProfileManager.listProfiles();
        // init runs again after each confirmation.
        BrowseList.State keepList = list == null ? null : list.state();
        // The old list goes now: its picks name rows of the old table (a crash).
        list = null;
        // The name starts as the active profile's, so Save keeps the edits over a launch with no typing.
        String keepName = nameField == null ? ProfileManager.activeProfile() : nameField.getValue();

        // The layout is the UI layer's, as the fit gate checked it.
        com.strata.ui.Layout layout = layout();
        list = new BrowseList(this.minecraft, 1, 1, 0, scaled(22)).setRecent("ProfileScreen");
        placeList(list, layout.get("list"));
        // A picked profile's name goes into the field, so Save can write it.
        list.setOnSelect(entry -> nameField.setValue(entry.label));
        addRenderableWidget(list);

        nameField = new EditField(this.font, 0, 0, 0, 0, "name", keepName);
        // THE FIELD USES THE SAME RULE AS THE MANAGER, so a name the save would refuse is refused here
        // instead, red and pre-save.
        nameField.setValidator((EditField.Check) ProfileManager::nameProblem);
        addRenderableWidget(place(nameField.getWidget(), layout.get("name")));

        saveButton = Button.builder(Component.literal("Save"), b -> saveProfile()).build();
        Button activateButton = Button.builder(Component.literal("Activate"), b -> activateSelected()).build();
        Button deleteButton = Button.builder(Component.literal("Remove"), b -> deleteSelected()).build();
        // ONE FILE: a picked profile is written as config/strata/exports/<name>.zip; Import
        // takes a.zip from config/strata/imports/ as a profile.
        Button exportButton = Button.builder(Component.literal("Export file"), b -> exportSelected()).build();
        Button importButton = Button.builder(Component.literal("Import..."), b -> pickImport()).build();
        // THE SERVER SCREEN IS GONE: Activate loads the profile here and, as an operator on a server, onto
        // the server too; the two actions that were on the Server screen are buttons here.
        Button regenerateButton = Button.builder(Component.literal("Regenerate on next start"),
                // A regenerate changes the world, so it asks first.
                b -> this.minecraft.setScreen(new ConfirmDialog(this,
                        "Regenerate at the server's next start?\nMobs and existing land update to this"
                                + " config as they load.",
                        "Regenerate", () -> send(StrataCommands.regenerateCommand())))).build();
        Button reloadButton = Button.builder(Component.literal("Reload server config"),
                b -> send(StrataCommands.reloadCommand())).build();
        Button serverButton = Button.builder(Component.literal(com.strata.ui.screen.ListScreenLayouts.SERVER_BUTTON),
                b -> loadToServer()).build();
        placeAll(layout, new String[]{"save", "activate", "delete", "export", "import", "regenerate", "reload", "server"},
                saveButton, activateButton, deleteButton, exportButton, importButton, regenerateButton,
                reloadButton, serverButton);
        boolean operator = isOperator(StrataNetwork.OPERATOR);
        regenerateButton.active = operator;
        reloadButton.active = operator;
        if (!operator) {
            Tooltip tip = Tooltip.create(Component.literal(inWorld()
                    ? "Only a server operator can do this"
                    : "Open a world or join a server first"));
            regenerateButton.setTooltip(tip);
            reloadButton.setTooltip(tip);
        }
        needsSelection(() -> list.getSelected() != null && !list.manyPicked(), activateButton, exportButton);
        needsSelection(() -> !list.pickedIds().isEmpty(), deleteButton);
        // Del presses Delete here, as on Behaviors: Del presses a list's "remove".
        keyPresses("remove", deleteButton);

        rebuild();
        list.restore(keepList);
    }

    /**
     * One click: the active profile goes to the server the player is on, and the server loads it (design
     * 9.1).
     */
    private void loadToServer() {
        var mc = this.minecraft;
        if (mc == null || mc.player == null || mc.hasSingleplayerServer()) {
            refuse("Join a server first. A singleplayer world already uses this machine's active profile.");
            return;
        }
        if (!mc.player.hasPermissions(com.strata.network.StrataNetwork.OPERATOR)) {
            refuse("Only a server operator can load a profile onto the server.");
            return;
        }
        String profile = com.strata.data.ProfileManager.activeProfile();
        mc.setScreen(new com.strata.client.widget.ConfirmDialog(this,
                "Load profile '" + profile + "' onto this server?\nThe server's config becomes this profile.",
                "Load", () -> {
                    String problem = com.strata.client.ClientNetwork.upload(profile);
                    if (problem != null) {
                        refuse(problem);
                    }
                }));
    }

    /** The layout for the status and the count as they stand. */
    private com.strata.ui.Layout layout() {
        // The Client screen's save moved here, so the warning did too.
        return com.strata.ui.screen.ListScreenLayouts.profile(frame(), this.font::width, title(), status,
                serverLines(),
                unsaved ? "Unsaved: edits reset at the next launch. Save to '"
                        + activeProfile + "' keeps them."
                        : com.strata.ui.Words.count(profiles.size(), "profile", "profiles"));
    }
    /** Saves the working config as the named profile. */
    private void saveProfile() {
        String name = nameField.getValue().strip();
        Runnable save = () -> {
            showResult(ProfileManager.exportProfile(name), "Saved: " + name);
            profiles = ProfileManager.listProfiles();
            rebuild();
        };
        if (ProfileManager.saveReplacesOther(name)) {
            minecraft.setScreen(new ConfirmDialog(this,
                    "Replace profile '" + name + "' with the current config?", save));
        } else {
            save.run();
        }
    }

    /**
     * ACTIVATE LOADS THE GAME INTO THE PROFILE: it replaces this machine's config, and, joined to a server as
     * an operator, loads the same profile onto the server too (the old Server screen's "Load profile onto
     * server").
     */
    private void activateSelected() {
        BrowseList.Entry sel = list.getSelected();
        if (sel == null) return;
        String name = sel.label;
        boolean toServer = onRemoteServer() && isOperator(StrataNetwork.OPERATOR);
        minecraft.setScreen(new ConfirmDialog(this,
                "Activate profile '" + name + "'?\nThis replaces all current config files,"
                        + "\nand every launch loads this profile from now on."
                        + (toServer ? "\nIt is also loaded onto the server you are on." : onRemoteServer()
                        ? "\nYou are not an operator here, so the server keeps its own config." : ""),
                () -> {
                    var replacedBefore = com.strata.domain.CustomAssetsOps.replacements();
                    Result result = ProfileManager.activateProfile(name);
                    showResult(result, "Activated: " + name);
                    // The new profile's sound and texture replacements are in the pack: the game reads it again.
                    if (result == Result.OK && !replacedBefore.equals(com.strata.domain.CustomAssetsOps.replacements())) {
                        this.minecraft.reloadResourcePacks();
                    }
                    profiles = ProfileManager.listProfiles();
                    rebuild();
                    if (result == Result.OK && toServer) {
                        sendProfile(name);
                    }
                }));
    }

    private boolean inWorld() {
        return this.minecraft != null && this.minecraft.level != null && this.minecraft.player != null;
    }

    private boolean onRemoteServer() {
        return inWorld() && !this.minecraft.hasSingleplayerServer();
    }

    private boolean isOperator(int level) {
        return inWorld() && this.minecraft.player.hasPermissions(level);
    }

    private void send(String command) {
        if (!inWorld()) {
            return;
        }
        this.minecraft.player.connection.sendCommand(command);
        statusOk = true;
        status = "Sent /" + command + " (the result is in chat)";
    }

    private void sendProfile(String profile) {
        String problem = ClientNetwork.upload(profile);
        statusOk = problem == null;
        status = problem != null ? problem : "Activated '" + profile + "'. Sending it to the server...";
    }

    /** The server's answer to a profile sent by Activate. */
    @Override
    public void onUploadResult(StrataPayloads.UploadResult result) {
        if (!result.ok()) {
            statusOk = false;
            status = result.message();
            return;
        }
        statusOk = true;
        if (!isOperator(StrataNetwork.STOP_LEVEL)) {
            status = result.message() + " Ask the server's host to restart it.";
            return;
        }
        status = result.message();
        this.minecraft.setScreen(new ConfirmDialog(this,
                result.message() + "\nRestart the server now? Players are told first."
                        + "\nThe host must start it again (most hosts do this on their own).",
                "Restart", ClientNetwork::requestRestart));
    }

    /** What this screen knows about the world, two lines at most (the old Server screen's status). */
    private List<String> serverLines() {
        if (!inWorld()) {
            return List.of("Not in a world. Open a world or join a server, and Activate loads a profile there too.");
        }
        String operator = "Operator: " + (isOperator(StrataNetwork.OPERATOR) ? "yes" : "no");
        if (this.minecraft.hasSingleplayerServer()) {
            return List.of("Singleplayer world: it uses this machine's config, so Activate applies here.", operator);
        }
        var server = this.minecraft.getCurrentServer();
        StrataPayloads.ServerSync sync = ClientNetwork.serverSync();
        String line = "Connected to " + (server == null ? "a server" : server.name + " (" + server.ip + ")") + "."
                + (sync == null ? "" : " Its active profile: " + sync.activeProfile() + "."
                + (sync.regeneratePending() ? " A regenerate waits for the next start." : ""));
        return List.of(line, operator + ". Activate also loads the profile onto the server."
                + (ProfileManager.hasUnsavedEdits() ? " Unsaved edits are not sent: Save first." : ""));
    }

    /** Deletes every picked profile after asking, naming them. */
    private void deleteSelected() {
        List<String> names = list.pickedIds().stream()
                .map(id -> profiles.get(Integer.parseInt(id)).name()).toList();
        if (names.isEmpty()) return;
        String listed = com.strata.domain.Picks.listed(names);
        minecraft.setScreen(new ConfirmDialog(this,
                "Remove profile" + (names.size() > 1 ? "s " : " ") + listed + "? This cannot be undone.",
                () -> {
                    showResult(ProfileManager.deleteProfiles(names), "Removed: " + listed);
                    profiles = ProfileManager.listProfiles();
                    rebuild();
                }));
    }

    private void exportSelected() {
        BrowseList.Entry sel = list.getSelected();
        if (sel == null) return;
        String name = sel.label;
        showResult(ProfileManager.exportFile(name), "Exported: config/strata/exports/" + name + ProfileManager.ZIP);
    }

    /** The picker's first entry: open the folder the files are taken from. */
    private static final String OPEN_FOLDER = "strata:open_imports";

    /** Picks a.zip from config/strata/imports/ to import. */
    private void pickImport() {
        List<com.strata.game.RegistryQuery.Option> options = new ArrayList<>();
        options.add(new com.strata.game.RegistryQuery.Option(OPEN_FOLDER,
                "Open the imports folder (drop a .zip in, then Import again)", "folder"));
        options.addAll(RegistryPickerScreen.plainOptions("file", ProfileManager.importable()));
        com.strata.client.nav.ScreenNavigator.get().navigateTo(new RegistryPickerScreen("Import a profile file",
                options, picked -> {
                    if (OPEN_FOLDER.equals(picked)) {
                        net.minecraft.Util.getPlatform().openFile(ProfileManager.importsFolder().toFile());
                    } else {
                        deferTo(() -> confirmImport(picked));
                    }
                }));
    }

    /** Checks the file, then asks, saying when it replaces a profile of that name. */
    private void confirmImport(String fileName) {
        String problem = ProfileManager.importProblem(fileName);
        if (problem != null) {
            statusOk = false;
            status = problem;
            return;
        }
        String name = ProfileManager.importName(fileName);
        boolean replaces = profiles.stream().anyMatch(p -> p.name().equals(name));
        minecraft.setScreen(new ConfirmDialog(this,
                (replaces ? "Replace the profile '" + name + "' with " : "Import ") + fileName
                        + (replaces ? "?" : " as the profile '" + name + "'?")
                        + "\nActivate it to use it.",
                () -> {
                    showResult(ProfileManager.importFile(fileName), "Imported: " + name);
                    profiles = ProfileManager.listProfiles();
                    rebuild();
                }));
    }

    /** Shows the operation's real outcome, not a presumed success. */
    private void showResult(Result result, String onSuccess) {
        statusOk = result == Result.OK;
        status = statusOk ? onSuccess : result.failureText();
    }

    /** The rows, with the active profile marked: every launch loads it. */
    private void rebuild() {
        unsaved = ProfileManager.hasUnsavedEdits();
        activeProfile = ProfileManager.activeProfile();
        String active = activeProfile;
        List<BrowseList.Entry> entries = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i++) {
            Profile p = profiles.get(i);
            boolean isActive = p.name().equals(active);
            String sub = isActive ? "active — every launch loads it" : p.description();
            if (ProfileManager.SHIPPED.equals(p.name())) {
                sub = (sub.isEmpty() ? "" : sub + " · ") + "the modpack's shipped config";
            }
            entries.add(new BrowseList.Entry(String.valueOf(i), p.name(), sub,
                    isActive ? Palette.DONE : Palette.TEXT));
        }
        list.refresh(entries);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Save needs a usable name.
        if (typeNameTip == null) {
            typeNameTip = Tooltip.create(Component.literal("Type a profile name first"));
        }
        saveButton.active = nameField.isValid();
        saveButton.setTooltip(saveButton.active ? null : typeNameTip);
        super.render(graphics, mouseX, mouseY, partialTick);
        com.strata.ui.Layout layout = layout();
        text(graphics, layout.get("label.name"), Palette.LABEL);
        text(graphics, layout.get("hint0"), Palette.HINT);
        text(graphics, layout.get("hint1"), Palette.HINT);
        text(graphics, layout.get("footer"), unsaved ? Palette.GROUP : Palette.MUTED);
        text(graphics, layout.get("status"), statusOk ? Palette.DONE : Palette.ERROR);
    }
}
