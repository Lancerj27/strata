package com.strata.runtime;

import com.strata.data.JsonOverrides;
import com.strata.model.ItemStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WHAT THE GAME LETS A PACK CHANGE THAT STRATA DOES NOT SHOW.
 */
public final class Coverage {

    private Coverage() {
    }

    /**
     * Data-driven registries with editors of their own rather than an override kind: Strata's own ore
     * definitions (the Ores screen).
     */
    private static final Set<String> OWN_EDITORS = Set.of("strata/ore_definition");

    /** One category of the audit: what the game holds, what Strata lets you edit, what it shows locked. */
    public record Count(String category, int total, int editable, int locked, String note) {
        public String line() {
            int hidden = total - editable - locked;
            return String.format(java.util.Locale.ROOT, "%-44s %7d total  %7d editable (%5.1f%%)  %6d shown locked  %6d not shown  %s",
                    category, total, editable, total == 0 ? 100.0 : 100.0 * editable / total, locked, hidden, note);
        }
    }

    /**
     * THE AUDIT: every category the census reads, counted as the game holds it against what Strata's editors
     * reach.
     */
    public static List<Count> audit(MinecraftServer server) {
        List<Count> out = new ArrayList<>();
        Set<String> folders = new HashSet<>();
        JsonOverrides.KINDS.forEach(k -> folders.add(k.folder()));
        // Data-driven registries and their entries.
        int regs = 0, regsOk = 0, entries = 0, entriesOk = 0;
        for (var d : DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().toList()) {
            String folder = Registries.elementsDirPath(d.key());
            boolean ok = folders.contains(folder) || OWN_EDITORS.contains(folder);
            int n = server.registryAccess().registry(d.key()).map(r -> r.size()).orElse(0);
            regs++;
            entries += n;
            if (ok) {
                regsOk++;
                entriesOk += n;
            }
        }
        out.add(new Count("Datapack registries", regs, regsOk, 0, ""));
        out.add(new Count("Datapack registry entries", entries, entriesOk, 0, "biomes, structures, dimensions..."));
        // Tags, by registry.
        int tagged = 0, taggedOk = 0, tags = 0, tagsOk = 0;
        for (var entry : server.registryAccess().registries().toList()) {
            long n = entry.value().getTagNames().count();
            if (n == 0) {
                continue;
            }
            tagged++;
            tags += (int) n;
            if (folders.contains(Registries.tagsDirPath(entry.key()))) {
                taggedOk++;
                tagsOk += (int) n;
            }
        }
        out.add(new Count("Registries with tags", tagged, taggedOk, 0, ""));
        out.add(new Count("Tags", tags, tagsOk, 0, ""));
        // Datapack content that is not a registry.
        int recipes = server.getRecipeManager().getRecipes().size();
        int advancements = server.getAdvancements().getAllAdvancements().size();
        int loot = server.reloadableRegistries().getKeys(net.minecraft.core.registries.Registries.LOOT_TABLE).size();
        out.add(new Count("Recipes, advancements, loot tables", recipes + advancements + loot,
                recipes + advancements + loot, 0, recipes + " / " + advancements + " / " + loot));
        // Game rules.
        int[] rules = {0};
        net.minecraft.world.level.GameRules.visitGameRuleTypes(new net.minecraft.world.level.GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends net.minecraft.world.level.GameRules.Value<T>> void visit(
                    net.minecraft.world.level.GameRules.Key<T> key, net.minecraft.world.level.GameRules.Type<T> type) {
                rules[0]++;
            }
        });
        out.add(new Count("Game rules", rules[0], rules[0], 0, ""));
        // Item components: kinds, and every value on every item.
        int kinds = 0, kindsOk = 0;
        Set<String> shown = new HashSet<>(ItemComponents.settable());
        shown.addAll(ItemStats.FLATTENED);
        for (var type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            if (type.codec() == null) {
                continue;
            }
            kinds++;
            if (shown.contains(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)))) {
                kindsOk++;
            }
        }
        out.add(new Count("Item component kinds", kinds, kindsOk, 0, "a component can be added to any item"));
        int onItems = 0;
        for (var item : BuiltInRegistries.ITEM) {
            onItems += item.components().size();
        }
        out.add(new Count("Item component values on items", onItems, onItems, 0, "every item's own"));
        // Blocks: every value of every block.
        int blocks = BuiltInRegistries.BLOCK.size();
        out.add(new Count("Block values", blocks * (com.strata.model.BlockStats.KEYS.size() + com.strata.model.BlockStats.SHOWN.size()),
                blocks * com.strata.model.BlockStats.KEYS.size(), blocks * com.strata.model.BlockStats.SHOWN.size(),
                "locked: block entity, item, states (fixed at registration), loot and tags (their own editors)"));
        // Mobs: the game layer's rows, as the mob data editor shows them.
        int mobRows = 0, mobOk = 0, mobLocked = 0;
        for (com.strata.data.GameLayer.Row r : com.strata.data.GameLayer.read(
                com.strata.data.GameLayer.dir().resolve(com.strata.data.GameLayer.MOBS))) {
            String g = r.group();
            if (!g.equals(com.strata.data.GameLayer.ATTRIBUTE) && !g.equals(com.strata.data.GameLayer.FIELD)
                    && !g.equals(com.strata.data.GameLayer.TYPE) && !g.equals(com.strata.data.GameLayer.TAG)) {
                continue;
            }
            mobRows++;
            if (com.strata.data.GameLayer.lockReason(g, r.key()) == null) {
                mobOk++;
            } else {
                mobLocked++;
            }
        }
        out.add(new Count("Mob values (attributes, fields, type, tags)", mobRows, mobOk, mobLocked,
                "locked: the mob's running state, and type values fixed at startup"));
        int props = com.strata.data.Schema.MOB_PROPERTIES.size();
        out.add(new Count("Mob properties (Strata's own, per mob)", props, props, 0, "sounds, visuals, combat, boss..."));
        return out;
    }

    /** What is editable and has no editor in Strata, one line each; empty when everything is covered. */
    public static List<String> missing(MinecraftServer server) {
        Set<String> folders = new HashSet<>();
        JsonOverrides.KINDS.forEach(k -> folders.add(k.folder()));
        List<String> out = new ArrayList<>();
        // Every data-driven registry: its elements' folder.
        DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().forEach(d -> {
            String folder = Registries.elementsDirPath(d.key());
            if (!folders.contains(folder) && !OWN_EDITORS.contains(folder)) {
                out.add("registry " + d.key().location() + " (data/<namespace>/" + folder + ") has no editor");
            }
        });
        // Every registry the game holds tags of: its tag folder.
        server.registryAccess().registries().forEach(entry -> {
            if (entry.value().getTagNames().findAny().isPresent()) {
                String folder = Registries.tagsDirPath(entry.key());
                if (!folders.contains(folder)) {
                    out.add("tags of " + entry.key().location() + " (data/<namespace>/" + folder + ") have no editor");
                }
            }
        });
        // The datapack content that is not a registry.
        for (String folder : List.of("recipe", "loot_table", "advancement")) {
            if (!folders.contains(folder)) {
                out.add("data/<namespace>/" + folder + " has no editor");
            }
        }
        // Every item component saved with an item: a value of the item sheet.
        Set<String> shown = new HashSet<>(ItemComponents.settable());
        shown.addAll(ItemStats.FLATTENED);
        for (var type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            String id = String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
            if (type.codec() != null && !shown.contains(id)) {
                out.add("item component " + id + " is not a value of the item sheet");
            }
        }
        return out;
    }
}
