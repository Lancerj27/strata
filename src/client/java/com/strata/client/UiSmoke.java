package com.strata.client;

import com.strata.Strata;
import com.strata.domain.GoalsOps;
import com.strata.domain.MobDataOps;
import com.strata.game.MobCatalog;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** THE CLIENT CHECK. */
public final class UiSmoke {

    private UiSmoke() {
    }

    private static boolean ran;
    private static int ticks;

    /** Listens for the title screen when the flag is on. */
    public static void install() {
        if (!Boolean.getBoolean("strata.uismoke")) {
            return;
        }
        // The first tick with loading done and a menu up: the first menu is made behind the loading overlay,
        // before this listener exists.
        Strata.LOGGER.info("client check: armed, waiting for the title screen");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!ran && ++ticks % 100 == 0) {
                Strata.LOGGER.info("client check: waiting; screen {}, overlay {}",
                        mc.screen == null ? "none" : mc.screen.getClass().getName(), mc.getOverlay());
            }
            // Loading done and a menu up: the title screen, or the first-run onboarding screen a fresh
            // profile shows first.
            if (!ran && mc.screen != null && mc.getOverlay() == null) {
                ran = true;
                try {
                    run(mc);
                } catch (Throwable t) {
                    report("the check itself FAILED: {}", t.toString());
                    Strata.LOGGER.error("client check failure", t);
                }
                write();
                mc.stop();
            }
        });
    }

    private static void run(Minecraft mc) {
        String mob = "minecraft:zombie";
        String owner = com.strata.model.Variant.defaultNameFor(mob);
        int failed = 0;
        // WHAT THE GAME GAVE AT START: the key tables, each a failure when empty, and what they hold, for the
        // reader.
        var global = MobCatalog.entries("", "", null);
        long fromGame = global.stream().filter(e -> e.source().layer() == com.strata.model.Source.Layer.GAME).count();
        report("Global mob list: {} row(s), {} from the game", global.size(), fromGame);
        failed += expect(fromGame > 0, "the Global mob list has no game mobs");
        var sheet = MobDataOps.sheet(new MobDataOps.Target(mob, "", "", ""));
        failed += expect(sheet.extracted() && !sheet.values().isEmpty(), "no mob data read for " + mob);
        for (String group : MobDataOps.GROUPS) {
            var sections = MobDataOps.sections(MobDataOps.filter(sheet.values(), group, ""));
            report("Mob data {} of {}: sections {}", group, mob, sections.keySet());
        }
        var tags = MobDataOps.filter(sheet.values(), com.strata.data.GameLayer.TAG, "");
        report("tags of {}: {} listed, {} it is in", mob, tags.size(),
                tags.stream().filter(v -> v.effective().equals("true")).count());
        var loot = com.strata.domain.LootTableOps.sheet(mob);
        report("loot table of {}: {} with {} value(s)", mob, loot.table(), loot.values().size());
        failed += expect(!loot.values().isEmpty(), "no loot table read for " + mob);
        var ores = com.strata.game.OreDetector.knownOreNames();
        report("ores read from the game: {}", ores.stream().sorted().toList());
        failed += expect(!ores.isEmpty(), "no ores read from the game");
        report("ores in plains: {}; in nether_wastes: {}",
                com.strata.data.GameSnapshot.get().getOrDefault("minecraft:plains",
                        new com.strata.data.GameSnapshot.Entry(List.of(), List.of())).ores(),
                com.strata.data.GameSnapshot.get().getOrDefault("minecraft:nether_wastes",
                        new com.strata.data.GameSnapshot.Entry(List.of(), List.of())).ores());
        report("ore blocks: {}", com.strata.game.OreDetector.oreBlocks());
        var biomeValues = com.strata.domain.BiomeDataOps.values("minecraft:soul_sand_valley");
        report("biome data of minecraft:soul_sand_valley: {}", biomeValues.stream()
                .map(v -> v.key() + "=" + v.effective()).toList());
        failed += expect(!biomeValues.isEmpty(), "no biome data read");
        var ai = GoalsOps.effective(owner, mob, "", "", "");
        report("goal set of {}: {} ({}) {}", mob, ai.source(),
                ai.rows() == null ? 0 : ai.rows().size(), ai.rows() == null ? "" : GoalsOps.summary(ai.rows()));
        failed += expect(ai.rows() != null && !ai.rows().isEmpty(), "no goals read for " + mob);
        // The mob editor's AI section, on real data.
        report("mob editor: {}", GoalsOps.goalSetLabel(ai, ""));
        GoalsOps.goalsText(ai.rows()).forEach(line -> report("  ai box: {}", line));
        var goalSets = GoalsOps.goalSetRows();
        report("goal sets: {} goal set(s), {} from families, {} used by a mob", goalSets.size(),
                goalSets.stream().filter(r -> r.kind() == GoalsOps.Kind.FAMILY).count(),
                goalSets.stream().filter(r -> !r.users().isEmpty()).count());
        // The Behaviors menu's groups, as it shows them.
        var groups = new java.util.LinkedHashMap<String, List<String>>();
        goalSets.forEach(r -> groups.computeIfAbsent(String.join(" > ", r.path()), k -> new ArrayList<>())
                .add(r.name()));
        groups.forEach((group, names) -> report("  group {}: {}", group.isEmpty() ? "(top)" : group, names));
        // The mob families, from the extracted goals and class chains.
        var families = com.strata.domain.GoalFamilies.fromGameLayer();
        report("mob families: {}", families.size());
        for (var f : families) {
            report("  family {} under {}: {} ({} goal(s))", f.name(), String.join(" > ", f.path()), f.mobs(), f.goals().size());
        }
        failed += expect(families.size() >= 10, "fewer than 10 mob families read from the game");
        // Seeded into the archetype library at game start.
        var library = GoalsOps.goalSetNames();
        long seeded = families.stream().filter(f -> library.contains(f.name())).count();
        long asGame = families.stream().filter(f -> GoalsOps.goalSet(f.name())
                .equals(com.strata.domain.GoalFamilies.rows(f))).count();
        report("family goal sets in the library: {} of {}, {} still as the game runs them", seeded,
                families.size(), asGame);
        failed += expect(seeded == families.size(), "a family goal set is missing from goal_sets.csv");
        failed += expect(families.stream().noneMatch(f -> com.strata.model.GoalSets.NONE.equals(f.name())),
                "a family is named 'none', the game goals' word");
        // The structures' own spawn lists.
        var structureSpawns = com.strata.data.GameSnapshot.structureSpawns();
        report("structure spawns: {} entr(ies); blaze in {}, guardian in {}", structureSpawns.size(),
                MobCatalog.structuresOf("minecraft:blaze"), MobCatalog.structuresOf("minecraft:guardian"));
        failed += expect(!structureSpawns.isEmpty(), "no structure spawns read from the game");
        // The scaling preview in real values, on the real zombie.
        var preview = com.strata.domain.ScalingOps.mobPreview(mob, new com.strata.domain.ScalingOps.CurveForm(
                "minecraft:generic.max_health", "0.1", "1", "1", true));
        report("scaling preview of {} max health at +10% a level: base {}, values {}", mob,
                preview == null ? null : preview.base(), preview == null ? null : preview.values());
        failed += expect(preview != null && preview.base() != null && preview.base() > 0,
                "no base max health read for " + mob);
        failed += compareFont(mc);
        report("client check: data extraction and the font, {} failure(s)", failed);
    }

    /** 0 when the extraction gave what it should; else a failure, said once. */
    private static int expect(boolean ok, String problem) {
        if (ok) {
            return 0;
        }
        report("FAILED: {}", problem);
        return 1;
    }
    /** THE GATES' FONT AGAINST THE LIVE FONT. */
    private static int compareFont(Minecraft mc) {
        com.strata.ui.GameFont font;
        try {
            font = com.strata.ui.GameFont.load(UiSmoke.class.getClassLoader());
        } catch (java.io.IOException | RuntimeException e) {
            report("font FAILED: the gates' font did not load: {}", e.toString());
            return 1;
        }
        List<String> differ = new ArrayList<>();
        int checked = 0;
        for (int cp : font.codePoints()) {
            String s = new String(Character.toChars(cp));
            checked++;
            if (font.width(s) != mc.font.width(s)) {
                differ.add(String.format("U+%04X gates %d game %d", cp, font.width(s), mc.font.width(s)));
            }
        }
        for (String s : List.of("Save", "← Back", "Goal set: zombie's own goals (default)", "Mob data · 1 × 2 — 3", "§cSave")) {
            checked++;
            if (font.width(s) != mc.font.width(s)) {
                differ.add("\"" + s + "\" gates " + font.width(s) + " game " + mc.font.width(s));
            }
        }
        report("font: {} checked, {} differ{}", checked, differ.size(),
                differ.isEmpty() ? "" : " FAILED: " + differ.subList(0, Math.min(20, differ.size())));
        return differ.isEmpty() ? 0 : 1;
    }

    private static final List<String> LINES = new java.util.ArrayList<>();

    /** One result line, to the log and to the results file. */
    private static void report(String pattern, Object... args) {
        String line = org.slf4j.helpers.MessageFormatter.arrayFormat(pattern, args).getMessage();
        LINES.add(line);
        Strata.LOGGER.info("client check: {}", line);
    }

    /** Writes run/strata-uismoke.txt, so the results do not depend on the log. */
    private static void write() {
        try {
            java.nio.file.Files.write(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("strata-uismoke.txt"), LINES);
        } catch (java.io.IOException e) {
            Strata.LOGGER.error("client check: could not write the results: {}", e.toString());
        }
    }
}
