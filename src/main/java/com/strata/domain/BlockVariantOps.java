package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.BlockStats;
import com.strata.model.BlockVariant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything Client > Blocks does with block variants: the designer's block variants (block_variants.csv) and
 * their own values (block_variant_values.csv) over their base blocks' (Block stats).
 */
public final class BlockVariantOps {

    private BlockVariantOps() {
    }

    /** When a change applies, said on the screens. */
    public static final String APPLIES = "A new variant is added at the next game start; its values apply at once."
            + " The server and each player's game need the same profile.";

    /** One variant in the list: its base block's kind, how many values it sets, whether this game has it. */
    public record Variant(String name, String baseBlock, String kind, int values, boolean registered) {
    }

    /** The editor's fields, as typed. */
    public record Form(String name, String baseBlock, String displayName) {
    }

    // ---- reads

    /** Every variant, by its base block's kind, then by name. */
    public static List<Variant> variants() {
        Map<String, String> kinds = new HashMap<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCKS))) {
            if (GameLayer.KIND.equals(r.group())) kinds.put(r.entity(), r.value());
        }
        return variants(rows(Schema.BlockVariants.FILE), rows(Schema.BlockVariantValues.FILE), kinds,
                new HashSet<>(registered()));
    }

    /** The variants of the two tables. */
    public static List<Variant> variants(List<Map<String, String>> variants, List<Map<String, String>> values,
                                         Map<String, String> kinds, Set<String> registered) {
        Map<String, Integer> valueCount = new HashMap<>();
        for (Map<String, String> row : values) {
            valueCount.merge(cell(row, Schema.BlockVariantValues.VARIANT), 1, Integer::sum);
        }
        List<Variant> out = new ArrayList<>();
        for (Map<String, String> row : variants) {
            String name = cell(row, Schema.BlockVariants.NAME);
            String base = cell(row, Schema.BlockVariants.BASE_BLOCK);
            if (!name.isEmpty()) {
                out.add(new Variant(name, base, kinds.getOrDefault(base, BlockStats.OTHER),
                        valueCount.getOrDefault(name, 0), registered.contains(name)));
            }
        }
        out.sort(Comparator.comparingInt((Variant v) -> kindOrder(v.kind())).thenComparing(Variant::name));
        return out;
    }

    /** One variant, or null. */
    public static Variant find(String name) {
        return variants().stream().filter(v -> v.name().equals(name)).findFirst().orElse(null);
    }

    /** A variant's own values, key to value, in file order. */
    public static Map<String, String> own(String name) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map<String, String> row : rows(Schema.BlockVariantValues.FILE)) {
            if (name.equals(cell(row, Schema.BlockVariantValues.VARIANT))) {
                out.put(cell(row, Schema.BlockVariantValues.KEY), cell(row, Schema.BlockVariantValues.VALUE));
            }
        }
        return out;
    }

    /**
     * A variant's stats sheet: each of its base block's values (the user's block stats over the game's) as
     * the "game" number, and the variant's own over it.
     */
    public static List<BlockStatsOps.Value> values(String name) {
        Variant v = find(name);
        return v == null ? List.of() : values(name, BlockStatsOps.values(v.baseBlock()), own(name));
    }

    /** The sheet of a variant from its base block's values and its own. */
    public static List<BlockStatsOps.Value> values(String name, List<BlockStatsOps.Value> base, Map<String, String> own) {
        List<BlockStatsOps.Value> out = new ArrayList<>();
        for (BlockStatsOps.Value b : base) {
            out.add(new BlockStatsOps.Value(name, b.key(), b.effective(), own.getOrDefault(b.key(), ""), b.note()));
        }
        return out;
    }

    // ---- writes

    /** Why a variant cannot be added, or null. */
    public static String addProblem(String name, String baseBlock, Collection<String> existing, Set<String> blocks,
                                    String refusal) {
        String problem = BlockVariant.nameProblem(name, existing);
        if (problem != null) return problem;
        String base = baseBlock == null ? "" : baseBlock.strip();
        if (base.isEmpty()) return "Pick the base block";
        if (!blocks.contains(base)) return "No block " + base + " in this game";
        return refusal == null ? null : "It cannot be a base block: " + refusal;
    }

    /** The form of a variant, or a new one's (blank, base block unpicked). */
    public static Form formFor(String name) {
        if (name == null || name.isEmpty()) {
            return new Form("", "", "");
        }
        Variant v = find(name);
        return new Form(name, v == null ? "" : v.baseBlock(), own(name).getOrDefault(BlockVariant.DISPLAY_NAME, ""));
    }

    /** Why the form cannot be saved, or null. */
    public static String formProblem(Form f, boolean isNew) {
        String display = BlockVariant.problem(BlockVariant.DISPLAY_NAME, f.displayName());
        if (display != null) return display;
        if (!isNew) return null;
        Set<String> blocks = new HashSet<>();
        GameLayer.read(GameLayer.dir().resolve(GameLayer.BLOCKS)).forEach(r -> blocks.add(r.entity()));
        return addProblem(f.name(), f.baseBlock(), variants().stream().map(Variant::name).toList(), blocks,
                refusal(f.baseBlock()));
    }

    /** Saves the form: a new variant is added first; a blank display name is removed. */
    public static WriteReport saveForm(String originalName, Form f) {
        boolean isNew = originalName == null || originalName.isEmpty();
        String problem = formProblem(f, isNew);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String name = isNew ? f.name().strip() : originalName;
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            if (isNew) {
                report.record("the variant", TableWriter.upsert(Schema.BlockVariants.FILE,
                        Map.of(Schema.BlockVariants.NAME, name), Map.of(Schema.BlockVariants.BASE_BLOCK, f.baseBlock().strip())));
            }
            String display = f.displayName() == null ? "" : f.displayName().strip();
            Map<String, String> keys = Map.of(Schema.BlockVariantValues.VARIANT, name,
                    Schema.BlockVariantValues.KEY, BlockVariant.DISPLAY_NAME);
            if (display.isEmpty()) {
                report.cascaded("the variant's display name", TableWriter.delete(Schema.BlockVariantValues.FILE, keys));
            } else {
                report.record("the variant's display name", TableWriter.upsert(Schema.BlockVariantValues.FILE, keys,
                        Map.of(Schema.BlockVariantValues.VALUE, display)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Removes variants with their values. */
    public static WriteReport remove(List<String> names) {
        return Commit.once(() -> {
            WriteReport report = new WriteReport();
            for (String name : names) {
                report.record("the variant " + name, TableWriter.delete(Schema.BlockVariants.FILE,
                        Map.of(Schema.BlockVariants.NAME, name)));
                report.cascaded("its values", TableWriter.delete(Schema.BlockVariantValues.FILE,
                        Map.of(Schema.BlockVariantValues.VARIANT, name)));
                report.cascaded("its abilities", TableWriter.delete(Schema.BlockVariantEffects.FILE,
                        Map.of(Schema.BlockVariantEffects.VARIANT, name)));
            }
            return Commit.reloadIfOk(report);
        });
    }

    /** Saves a variant's own stat (the Stats sheet), a word lower case. */
    public static WriteReport save(BlockStatsOps.Value v, String typed) {
        String problem = BlockVariant.problem(v.key(), typed);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String value = typed.strip();
        if (BlockStats.isSwitch(v.key())) {
            value = value.toLowerCase(java.util.Locale.ROOT);
        }
        return Commit.reloadIfOk(new WriteReport().record("the variant's value", TableWriter.upsert(
                Schema.BlockVariantValues.FILE, keys(v), Map.of(Schema.BlockVariantValues.VALUE, value))));
    }

    /** Takes a variant's own stat away: its base block's applies again. */
    public static WriteReport restore(BlockStatsOps.Value v) {
        return Commit.reloadIfOk(new WriteReport().cascaded("the variant's value",
                TableWriter.delete(Schema.BlockVariantValues.FILE, keys(v))));
    }

    /** The command that gives the player at the editor one variant (a real item: the game's own /give). */
    public static String giveCommand(String name) {
        return "give @s " + com.strata.Strata.MOD_ID + ":" + name;
    }

    private static Map<String, String> keys(BlockStatsOps.Value v) {
        return Map.of(Schema.BlockVariantValues.VARIANT, v.block(), Schema.BlockVariantValues.KEY, v.key());
    }

    // ---- abilities: the abilities list of one block variant

    /** A variant's effects, for AbilitiesScreen: its own rows only (a block variant inherits no abilities). */
    public static AbilitiesOps.Store effectsStore(String variant) {
        // The columns are abilities.csv's, so the mob editor's store serves.
        return AbilitiesOps.flatStore(Schema.BlockVariantEffects.FILE, Schema.BlockVariantEffects.VARIANT, variant);
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    // ---- the game (not pure)

    /** The variants this game registered at start. */
    private static List<String> registered() {
        try {
            return com.strata.runtime.BlockVariants.names();
        } catch (LinkageError e) {
            return List.of();
        }
    }

    /** Why this game cannot copy the block, or null (null too for a block it does not have: addProblem says so). */
    private static String refusal(String baseBlock) {
        return com.strata.runtime.BlockVariants.refusal(baseBlock);
    }

    // ---- helpers

    private static List<Map<String, String>> rows(String file) {
        return CsvTable.read(Paths.table(file));
    }

    private static String cell(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").strip();
    }

    private static int kindOrder(String kind) {
        int i = BlockStats.KINDS.indexOf(kind);
        return i < 0 ? BlockStats.KINDS.size() : i;
    }
}
