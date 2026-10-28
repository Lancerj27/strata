package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;
import com.strata.model.AbilityField;
import com.strata.model.Variant;
import com.strata.runtime.MobAbilities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.strata.domain.ScopeLayers.ScopeLayer;

import static com.strata.domain.ScopeLayers.*;

/**
 * Everything the editor reads and does for a mob's abilities (the Abilities half of EquipmentOps, named as
 * the editor names it).
 */
public final class AbilitiesOps {

    private AbilitiesOps() {
    }

    /**
     * One ability row of an instance, as the editor lists it.
     *
     * @param inherited true for the base mob's row a variant inherits, shown while the variant has no ability
     *     row of its own
     */
    public record AbilityRow(String ability, String parameter, String amount,
                             String intervalTicks, String radius, boolean enabled, boolean inherited,
                             String effectClass, String trigger, String chance,
                             String damage, String duration, String canKill, String damageType,
                             String particle, String hudIcon, String cure, String entity) {

        // Canonical constructor with all fields (required for Java records)
        public AbilityRow {
        }

        // Legacy constructor for legacy rows
        public AbilityRow(String ability, String parameter, String amount,
                          String intervalTicks, String radius, boolean enabled, boolean inherited) {
            this(ability, parameter, amount, intervalTicks, radius, enabled, inherited,
                    "", "", "", "", "", "", "", "", "", "", "");
        }

        // Legacy constructor without inherited
        public AbilityRow(String ability, String parameter, String amount,
                          String intervalTicks, String radius, boolean enabled) {
            this(ability, parameter, amount, intervalTicks, radius, enabled, false,
                    "", "", "", "", "", "", "", "", "", "", "");
        }

        /** True for a row of one of the six effect classes rather than a legacy ability. */
        public boolean isClass() {
            return effectClass != null && !effectClass.isBlank();
        }

        /** The same row with another enabled and inherited flag. */
        public AbilityRow with(boolean enabled, boolean inherited) {
            return new AbilityRow(ability, parameter, amount, intervalTicks, radius, enabled, inherited,
                    effectClass, trigger, chance, damage, duration, canKill, damageType,
                    particle, hudIcon, cure, entity);
        }
    }

    /**
     * WHERE AN ABILITIES LIST IS KEPT: a mob variant's instance (abilities.csv, with inheritance by scope),
     * or an item variant's effects (item_variant_effects.csv).
     */
    public interface Store {
        List<AbilityRow> shown();

        /** Where an inherited set comes from, or "" for none. */
        String from();

        /** The other scopes that hold rows of this owner, as words: "tier Harsh (2)". None by default. */
        default List<String> elsewhere() {
            return List.of();
        }

        WriteReport add(AbilityRow row, List<AbilityRow> shown);

        /** True when the list already holds a row with this row's key, so an add replaces it. */
        boolean has(AbilityRow row);

        WriteReport toggle(AbilityRow row, List<AbilityRow> shown);

        WriteReport remove(AbilityRow row, List<AbilityRow> shown);

        /** Writes an edited row in place of the old one: in place when its key is the same, else remove then add. */
        default WriteReport replace(AbilityRow old, AbilityRow edited, List<AbilityRow> shown) {
            if (sameRow(old, edited)) {
                return add(edited, shown);
            }
            return Commit.once(() -> {
                WriteReport removed = remove(old, shown);
                return removed.ok() ? add(edited, shown.stream().filter(r -> !sameRow(r, old)).toList()) : removed;
            });
        }

        WriteReport removeAll(List<AbilityRow> rows, List<AbilityRow> shown);

        /** Copies a group's ability rows into this list. */
        /** The triggers a row in this list can name, as ids. */
        default List<String> triggers() {
            return TriggerOps.creatureTriggers();
        }

        default WriteReport applyGroup(String groupName) {
            return WriteReport.refused("A group cannot be added to this list");
        }

        /**
         * Whether {@link #applyGroup} can do anything here, which is also whether this screen shows a group
         * picker at all.
         */
        default boolean canTakeGroups() {
            return false;
        }

        /** What {@link #applyGroup} would add to this list, asked BEFORE the designer commits. */
        default GroupOps.Added groupAdds(String groupName) {
            return new GroupOps.Added(0, 0);
        }
    }

    /**
     * AN ITEM OR BLOCK VARIANT'S EFFECTS (item_variant_effects.csv, block_variant_effects.csv): one variant
     * column and abilities.csv's columns under the same names, no scope and no inheritance.
     *
     * @param file the table
     * @param variantColumn its owner column
     */
    public static Store flatStore(String file, String variantColumn, String variant) {
        return flatStore(file, variantColumn, variant, TriggerOps.creatureTriggers());
    }

    /** @param triggers the triggers its rows can name (a projectile's: On impact only) */
    public static Store flatStore(String file, String variantColumn, String variant, List<String> triggers) {
        return new Store() {
            public List<String> triggers() {
                return triggers;
            }

            private java.nio.file.Path path() {
                return Paths.table(file);
            }

            private Map<String, String> key(AbilityRow row) {
                Map<String, String> keys = new LinkedHashMap<>();
                keys.put(variantColumn, variant);
                keys.put(Schema.Abilities.ABILITY, strip(row.ability()));
                keys.put(Schema.Abilities.PARAMETER, strip(row.parameter()));
                if (row.isClass()) {
                    keys.put(Schema.Abilities.EFFECT_CLASS, strip(row.effectClass()));
                    keys.put(Schema.Abilities.TRIGGER, strip(row.trigger()));
                    keys.put(Schema.Abilities.ENTITY, strip(row.entity()));
                }
                return keys;
            }

            public List<AbilityRow> shown() {
                List<AbilityRow> out = new ArrayList<>();
                for (Map<String, String> csv : CsvTable.read(path())) {
                    if (variant.equals(csv.getOrDefault(variantColumn, "").strip())) {
                        out.add(rowFrom(csv, false));
                    }
                }
                return out;
            }

            public String from() {
                return "";
            }

            public WriteReport add(AbilityRow row, List<AbilityRow> shown) {
                String problem = row.isClass() ? classProblem(row)
                        : abilityProblem(row.ability(), row.parameter(), row.amount(), row.intervalTicks(), row.radius());
                if (problem != null) {
                    return WriteReport.refused(problem);
                }
                AbilityRow adding = row.with(true, false);
                return Commit.reloadIfOk(new WriteReport().record("the effect",
                        TableWriter.upsert(file, key(adding), rowValues(adding))));
            }

            public boolean has(AbilityRow row) {
                return shown().stream().anyMatch(r -> sameRow(r, row));
            }

            public WriteReport toggle(AbilityRow row, List<AbilityRow> shown) {
                return Commit.reloadIfOk(new WriteReport().record("the effect", TableWriter.upsert(
                        file, key(row), Map.of(Schema.Abilities.ENABLED, String.valueOf(!row.enabled())))));
            }

            public WriteReport remove(AbilityRow row, List<AbilityRow> shown) {
                return removeAll(List.of(row), shown);
            }

            public WriteReport removeAll(List<AbilityRow> picked, List<AbilityRow> shown) {
                WriteReport report = new WriteReport();
                for (AbilityRow row : picked) {
                    report.record("the effect", TableWriter.delete(file, key(row)));
                }
                return Commit.reloadIfOk(report);
            }
        };
    }

    /** A mob variant instance's abilities, as the calls below keep them. */
    public static Store mobStore(String variantName, String baseEntity, String scope, String scopeId) {
        return new Store() {
            public List<AbilityRow> shown() {
                return abilitiesShown(variantName, baseEntity, scope, scopeId);
            }

            public String from() {
                return abilitiesFrom(variantName, baseEntity, scope, scopeId);
            }

            public List<String> elsewhere() {
                return abilitiesElsewhere(variantName, scope, scopeId);
            }

            public WriteReport add(AbilityRow row, List<AbilityRow> shown) {
                return addAbility(variantName, scope, scopeId, row, shown);
            }

            public boolean has(AbilityRow row) {
                return abilitiesFor(variantName, scope, scopeId).stream().anyMatch(r -> sameRow(r, row));
            }

            public WriteReport toggle(AbilityRow row, List<AbilityRow> shown) {
                return toggleAbility(variantName, scope, scopeId, row, shown);
            }

            public WriteReport remove(AbilityRow row, List<AbilityRow> shown) {
                return removeAbility(variantName, scope, scopeId, row, shown);
            }

            public WriteReport removeAll(List<AbilityRow> rows, List<AbilityRow> shown) {
                return removeAbilitiesAll(variantName, scope, scopeId, rows, shown);
            }

            public WriteReport applyGroup(String groupName) {
                return GroupOps.applyAbilities(groupName, variantName, scope, scopeId);
            }

            public boolean canTakeGroups() {
                return true;
            }

            public GroupOps.Added groupAdds(String groupName) {
                return GroupOps.countsAbilities(groupName, variantName, scope, scopeId);
            }
        };
    }

    /**
     * The abilities the editor shows for an instance, as the runtime takes them (ScopeIndex#resolveOrBase):
     * its own rows in the nearest scope that has any, else its base mob's, the same way.
     */
    public static List<AbilityRow> abilitiesShown(String variantName, String baseEntity,
                                                   String scope, String scopeId) {
        return abilitySet(variantName, baseEntity, scope, scopeId).rows();
    }

    /**
     * Where the shown abilities come from: "" for this scope's own, else {@link #FROM_TIER}, {@link
     * #FROM_GLOBAL}, or "base mob".
     */
    public static String abilitiesFrom(String variantName, String baseEntity, String scope, String scopeId) {
        return abilitySet(variantName, baseEntity, scope, scopeId).from();
    }

    private record AbilitySet(List<AbilityRow> rows, String from) {
    }

    private static AbilitySet abilitySet(String variantName, String baseEntity, String scope, String scopeId) {
        List<ScopeLayer> layers = scopeLayers(scope, scopeId);
        List<List<AbilityRow>> own = layers.stream()
                .map(l -> abilitiesFor(variantName, l.scope(), l.scopeId())).toList();
        int at = firstWithRows(own);
        if (at == 0) {
            return new AbilitySet(own.get(0), "");
        }
        if (at > 0) {
            return new AbilitySet(asInherited(own.get(at)), layers.get(at).from());
        }
        String base = Variant.defaultNameFor(baseEntity);
        if (base.isEmpty() || base.equals(variantName)) {
            return new AbilitySet(List.of(), "");
        }
        List<List<AbilityRow>> inherited = layers.stream()
                .map(l -> abilitiesFor(base, l.scope(), l.scopeId())).toList();
        int b = firstWithRows(inherited);
        return b < 0 ? new AbilitySet(List.of(), "") : new AbilitySet(asInherited(inherited.get(b)), "base mob");
    }

    private static List<AbilityRow> asInherited(List<AbilityRow> rows) {
        return rows.stream().map(r -> r.with(r.enabled(), true)).toList();
    }

    /**
     * The inherited rows to write as the instance's own before a change, so the change joins them instead of
     * replacing them.
     */
    public static List<AbilityRow> inheritedToKeep(List<AbilityRow> shown, AbilityRow changing) {
        List<AbilityRow> keep = new ArrayList<>();
        for (AbilityRow row : shown) {
            if (row.inherited() && (changing == null || !sameRow(row, changing))) {
                keep.add(row);
            }
        }
        return keep;
    }

    /** Writes the inherited rows as this instance's own, as the list showed them. */
    private static void keepInherited(WriteReport report, String variantName, String scope, String scopeId,
                                       List<AbilityRow> keep) {
        for (AbilityRow row : keep) {
            if (!report.ok()) {
                return;
            }
            report.record("the inherited ability", TableWriter.upsert(Schema.Abilities.FILE,
                    abilityKey(variantName, scope, scopeId, row), rowValues(row)));
        }
    }

    /** Every non-key cell of one row, as written. */
    public static Map<String, String> rowValues(AbilityRow row) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(Schema.Abilities.AMOUNT, strip(row.amount()));
        values.put(Schema.Abilities.INTERVAL_TICKS, strip(row.intervalTicks()));
        values.put(Schema.Abilities.RADIUS, strip(row.radius()));
        values.put(Schema.Abilities.ENABLED, String.valueOf(row.enabled()));
        values.put(Schema.Abilities.CHANCE, strip(row.chance()));
        values.put(Schema.Abilities.DAMAGE, strip(row.damage()));
        values.put(Schema.Abilities.DURATION, strip(row.duration()));
        values.put(Schema.Abilities.CAN_KILL, strip(row.canKill()));
        values.put(Schema.Abilities.DAMAGE_TYPE, strip(row.damageType()));
        values.put(Schema.Abilities.PARTICLE, strip(row.particle()));
        values.put(Schema.Abilities.HUD_ICON, strip(row.hudIcon()));
        values.put(Schema.Abilities.CURE, strip(row.cure()));
        if (!row.isClass()) {
            // A class row carries these three in its key instead.
            values.put(Schema.Abilities.EFFECT_CLASS, "");
            values.put(Schema.Abilities.TRIGGER, "");
            values.put(Schema.Abilities.ENTITY, "");
        }
        return values;
    }

    /** One abilities.csv row as the editor's row. */
    public static AbilityRow rowFrom(Map<String, String> csv, boolean inherited) {
        return new AbilityRow(
                csv.getOrDefault(Schema.Abilities.ABILITY, "").strip(),
                csv.getOrDefault(Schema.Abilities.PARAMETER, "").strip(),
                csv.getOrDefault(Schema.Abilities.AMOUNT, "").strip(),
                csv.getOrDefault(Schema.Abilities.INTERVAL_TICKS, "").strip(),
                csv.getOrDefault(Schema.Abilities.RADIUS, "").strip(),
                Parse.flag(csv.get(Schema.Abilities.ENABLED), true),
                inherited,
                csv.getOrDefault(Schema.Abilities.EFFECT_CLASS, "").strip(),
                csv.getOrDefault(Schema.Abilities.TRIGGER, "").strip(),
                csv.getOrDefault(Schema.Abilities.CHANCE, "").strip(),
                csv.getOrDefault(Schema.Abilities.DAMAGE, "").strip(),
                csv.getOrDefault(Schema.Abilities.DURATION, "").strip(),
                csv.getOrDefault(Schema.Abilities.CAN_KILL, "").strip(),
                csv.getOrDefault(Schema.Abilities.DAMAGE_TYPE, "").strip(),
                csv.getOrDefault(Schema.Abilities.PARTICLE, "").strip(),
                csv.getOrDefault(Schema.Abilities.HUD_ICON, "").strip(),
                csv.getOrDefault(Schema.Abilities.CURE, "").strip(),
                csv.getOrDefault(Schema.Abilities.ENTITY, "").strip());
    }

    /** The add row's fields were AbilitiesOps.AbilityField. */

    /** What an ability's parameter names (a picker, never typed). */
    public enum ParameterKind {
        /** A mob effect (regeneration, speed). */
        EFFECT,
        /** A damage type, or a damage type tag as "#" and its id. */
        DAMAGE,
        /** An attribute id (for attribute_change). */
        ATTRIBUTE,
        /** An entity type (for spawn). */
        ENTITY,
        /** The ability takes no parameter. */
        NONE
    }

    /** The parameter an ability takes, by its class id. */
    public static ParameterKind parameterKind(String effectClass) {
        if (effectClass == null || effectClass.isBlank()) {
            return ParameterKind.NONE;
        }
        String e = effectClass.strip();
        var a = com.strata.runtime.MobAbilities.Ability.parse(e);
        if (a != null) {
            return a.needsEffect ? ParameterKind.EFFECT
                    : a == com.strata.runtime.MobAbilities.Ability.DAMAGE_IMMUNITY ? ParameterKind.DAMAGE
                    : ParameterKind.NONE;
        }
        return switch (e) {
            case "apply_effect" -> ParameterKind.EFFECT;
            // Their damage type is the Damage type cell (Schema.Abilities.DAMAGE_TYPE), which the runtime
            // reads; picked into the parameter it was read by nothing.
            case "damage_over_time", "explode", "explode_and_die" -> ParameterKind.NONE;
            case "attribute_change" -> ParameterKind.ATTRIBUTE;
            case "aura" -> ParameterKind.EFFECT;
            // spawn's creature is the Entity field (AbilityField.ENTITY), not the parameter.
            case "spawn" -> ParameterKind.NONE;
            case "life_steal" -> ParameterKind.NONE;
            default -> ParameterKind.NONE;
        };
    }

    /** The tooltip for one field of the add row, for the ability or the class chosen (extended ). */
    public static String abilityTip(String ability, AbilityField field) {
        // If it's a legacy ability name, try to map it to a class
        String classId = com.strata.model.EffectClass.of(ability) != null ? ability : "";
        if (classId.isEmpty()) {
            // Legacy ability - try to get tip from legacy guide
            return com.strata.model.AbilityGuide.fieldText(ability, field);
        }
        String text = com.strata.model.AbilityGuide.fieldText(classId, field);
        // The chooser already prints the name it offers, so its own tip gives the id and what it does rather
        // than repeating the field's label.
        return field == AbilityField.EFFECT_CLASS ? text : field.label() + ": " + text;
    }

    /** True when the chosen row reads a field of the add row. */
    public static boolean usesField(String ability, AbilityField field) {
        // If it's a legacy ability, use legacy check
        if (com.strata.model.EffectClass.of(ability) == null) {
            return !com.strata.model.AbilityGuide.NOT_USED.equals(
                    com.strata.model.AbilityGuide.fieldText(ability, field));
        }
        // New class - use the class-based check
        return !com.strata.model.AbilityGuide.NOT_USED.equals(
                com.strata.model.AbilityGuide.fieldText(ability, field));
    }

    /** One ability row in a sentence, for the list. */
    public static String abilitySentence(AbilityRow row) {
        if (row.isClass()) {
            // A class row has no legacy name for the guide to phrase: say what it is set to.
            StringBuilder sb = new StringBuilder(com.strata.util.Labels.pretty(row.trigger()));
            appendPart(sb, row.entity());
            appendPart(sb, row.parameter());
            appendPart(sb, row.amount().isBlank() ? "" : "amount " + row.amount());
            appendPart(sb, row.intervalTicks().isBlank() ? "" : "every " + row.intervalTicks() + " ticks");
            appendPart(sb, row.duration().isBlank() ? "" : "for " + row.duration() + " ticks");
            appendPart(sb, row.radius().isBlank() ? "" : "radius " + row.radius());
            appendPart(sb, row.chance().isBlank() ? "" : row.chance() + "% chance");
            return sb.toString();
        }
        return com.strata.model.AbilityGuide.sentence(row.ability(), row.parameter(), row.amount(),
                row.intervalTicks(), row.radius());
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            sb.append(sb.isEmpty() ? "" : ", ").append(part.strip());
        }
    }

    /** What a row is called in the list: its class for a class row, its ability for a legacy one. */
    public static String rowName(AbilityRow row) {
        return row.isClass() ? row.effectClass() : row.ability();
    }

    /** The choices for a new ability row: the effect classes. */
    public static List<String> abilityChoices() {
        return new ArrayList<>(com.strata.model.EffectClass.ids());
    }

    /** The abilities of one variant instance. */
    public static List<AbilityRow> abilitiesFor(String variantName, String scope, String scopeId) {
        String variant = variantName == null ? "" : variantName;
        String wantScope = VariantOps.normalScope(scope);
        String wantScopeId = VariantOps.normalScopeId(scope, scopeId);
        List<AbilityRow> rows = new ArrayList<>();
        for (Map<String, String> csv : CsvTable.read(Paths.table(Schema.Abilities.FILE))) {
            if (!variant.equals(csv.getOrDefault(Schema.Abilities.VARIANT_NAME, "").strip())) continue;
            if (!wantScope.equals(VariantOps.normalScope(csv.getOrDefault(Schema.Abilities.SCOPE, "")))
                    || !wantScopeId.equals(csv.getOrDefault(Schema.Abilities.SCOPE_ID, "").strip())) continue;
            rows.add(rowFrom(csv, false));
        }
        return rows;
    }

    /**
     * The other scopes where this variant has ability rows, as words with a count ("tier Harsh (2)", "global
     * (1)"). A row there acts on mobs that spawn there, and this scope's list does not show it.
     */
    public static List<String> abilitiesElsewhere(String variantName, String scope, String scopeId) {
        String here = VariantOps.normalScope(scope) + "|" + VariantOps.normalScopeId(scope, scopeId);
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Map<String, String> csv : CsvTable.read(Paths.table(Schema.Abilities.FILE))) {
            if (!(variantName == null ? "" : variantName).equals(csv.getOrDefault(Schema.Abilities.VARIANT_NAME, "").strip())) {
                continue;
            }
            String s = VariantOps.normalScope(csv.getOrDefault(Schema.Abilities.SCOPE, ""));
            String id = csv.getOrDefault(Schema.Abilities.SCOPE_ID, "").strip();
            if (!(s + "|" + id).equals(here)) {
                counts.merge(s.isEmpty() ? "global" : s + " " + id, 1, Integer::sum);
            }
        }
        List<String> out = new ArrayList<>();
        counts.forEach((where, n) -> out.add(where + " (" + n + ")"));
        return out;
    }

    /** Adds an ability, enabled, then reloads. */
    public static WriteReport addAbility(String variantName, String scope, String scopeId,
                                          String ability, String parameter, String amount,
                                          String intervalTicks, String radius,
                                          String effectClass, String trigger, String chance,
                                          String damage, String duration, String canKill, String damageType,
                                          String particle, String hudIcon, String cure,
                                          List<AbilityRow> shown) {
        return addAbility(variantName, scope, scopeId, new AbilityRow(strip(ability), strip(parameter),
                strip(amount), strip(intervalTicks), strip(radius), true, false,
                strip(effectClass), strip(trigger), strip(chance), strip(damage), strip(duration),
                strip(canKill), strip(damageType), strip(particle), strip(hudIcon), strip(cure), ""), shown);
    }

    /** Adds one row, enabled, then reloads. */
    public static WriteReport addAbility(String variantName, String scope, String scopeId,
                                          AbilityRow row, List<AbilityRow> shown) {
        String problem = row.isClass() ? classProblem(row)
                : abilityProblem(row.ability(), row.parameter(), row.amount(), row.intervalTicks(), row.radius());
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        AbilityRow adding = row.with(true, false);
        // The inherited set becomes this variant's own first, so the new ability joins it instead of
        // replacing it.
        WriteReport report = new WriteReport();
        keepInherited(report, variantName, scope, scopeId, inheritedToKeep(shown, adding));
        if (!report.ok()) {
            return report;
        }
        return Commit.reloadIfOk(report.record("the ability", TableWriter.upsert(
                Schema.Abilities.FILE, abilityKey(variantName, scope, scopeId, adding), rowValues(adding))));
    }

    /** Why a class row cannot be added, or null. */
    public static String classProblem(AbilityRow row) {
        String effectClass = strip(row.effectClass());
        if (com.strata.model.EffectClass.of(effectClass) == null) {
            return "unknown effect class '" + effectClass + "'";
        }
        if (strip(row.trigger()).isEmpty()) {
            return "needs a trigger";
        }
        String chance = strip(row.chance());
        if (!chance.isEmpty()) {
            double c = Parse.number(chance, -1);
            if (c < 0 || c > 100) {
                return "chance must be 0 to 100";
            }
        }
        if (parameterKind(effectClass) != ParameterKind.NONE && strip(row.parameter()).isEmpty()) {
            return "needs an applied effect";
        }
        // Spawn must name its creature; an aura's blank entity means every creature but players.
        if ("spawn".equals(effectClass) && strip(row.entity()).isEmpty()) {
            return "needs an entity";
        }
        // EffectTable#entry's own refusals, so the editor never saves a row the loader skips.
        if ("damage_over_time".equals(effectClass)
                && (Parse.number(row.intervalTicks(), 0) <= 0 || Parse.number(row.duration(), 0) <= 0)) {
            return "needs ticks and a duration above 0";
        }
        if ("attribute_change".equals(effectClass) && Parse.number(row.duration(), 0) <= 0) {
            return "needs a duration above 0";
        }
        return null;
    }

    /** Why this ability row cannot be added, in the designer's words, or null when it can. */
    public static String abilityProblem(String ability, String parameter, String amount,
                                         String intervalTicks, String radius) {
        String problem = MobAbilities.rowProblem(ability, parameter, amount, intervalTicks, radius);
        return problem == null ? null : ability + " " + problem;
    }

    /** True when the instance already has this ability with this parameter. */
    public static boolean hasAbility(String variantName, String scope, String scopeId,
                                      String ability, String parameter) {
        for (AbilityRow row : abilitiesFor(variantName, scope, scopeId)) {
            if (sameAbility(row, ability, parameter)) {
                return true;
            }
        }
        return false;
    }

    /** True when two rows have the same key, so writing one replaces the other. */
    public static boolean sameRow(AbilityRow a, AbilityRow b) {
        return strip(a.ability()).equals(strip(b.ability()))
                && strip(a.parameter()).equals(strip(b.parameter()))
                && strip(a.effectClass()).equals(strip(b.effectClass()))
                && (!a.isClass() || (strip(a.trigger()).equals(strip(b.trigger()))
                        && strip(a.entity()).equals(strip(b.entity()))));
    }

    /** True when a listed row has the key an add of (ability, parameter) writes. */
    public static boolean sameAbility(AbilityRow row, String ability, String parameter) {
        return row.ability().equals(strip(ability)) && row.parameter().equals(strip(parameter));
    }

    /** Flips an ability's enabled flag, then reloads. */
    public static WriteReport toggleAbility(String variantName, String scope, String scopeId,
                                             AbilityRow row, List<AbilityRow> shown) {
        Map<String, String> keys = abilityKey(variantName, scope, scopeId, row);
        if (!row.inherited()) {
            return Commit.reloadIfOk(new WriteReport().record("the ability", TableWriter.upsert(
                    Schema.Abilities.FILE, keys,
                    Map.of(Schema.Abilities.ENABLED, String.valueOf(!row.enabled())))));
        }
        WriteReport report = new WriteReport();
        keepInherited(report, variantName, scope, scopeId, inheritedToKeep(shown, row));
        keepInherited(report, variantName, scope, scopeId, List.of(row.with(!row.enabled(), true)));
        return Commit.reloadIfOk(report);
    }

    /** Removes an own ability, then reloads. */
    public static WriteReport removeAbility(String variantName, String scope, String scopeId,
                                             AbilityRow row, List<AbilityRow> shown) {
        if (removeTurnsOff(row)) {
            return row.enabled() ? toggleAbility(variantName, scope, scopeId, row, shown)
                    : WriteReport.refused("It is already off");
        }
        return Commit.reloadIfOk(new WriteReport().record("the ability", TableWriter.delete(
                Schema.Abilities.FILE, abilityKey(variantName, scope, scopeId, row))));
    }

    /**
     * What removing several abilities at once writes.
     *
     * @param keep inherited rows written as own first, unchanged: once one inherited row changes, the set
     *     becomes this scope's own
     * @param turnOff inherited rows written as own, switched off
     * @param delete own rows deleted
     */
    public record AbilityRemovalPlan(List<AbilityRow> keep, List<AbilityRow> turnOff, List<AbilityRow> delete) {
    }

    /**
     * The plan for removing these abilities together.
     *
     * @param shown every row the list shows
     */
    public static AbilityRemovalPlan abilityRemovalPlan(List<AbilityRow> removing, List<AbilityRow> shown) {
        List<AbilityRow> turnOff = new ArrayList<>();
        List<AbilityRow> delete = new ArrayList<>();
        for (AbilityRow row : removing) {
            if (!removeTurnsOff(row)) {
                delete.add(row);
            } else if (row.enabled()) {
                turnOff.add(row);
            }
        }
        List<AbilityRow> keep = new ArrayList<>();
        if (!turnOff.isEmpty()) {
            for (AbilityRow row : shown) {
                if (row.inherited() && removing.stream().noneMatch(r -> sameRow(r, row))) {
                    keep.add(row);
                }
            }
        }
        return new AbilityRemovalPlan(List.copyOf(keep), List.copyOf(turnOff), List.copyOf(delete));
    }

    /** Removes several abilities together, as {@link #abilityRemovalPlan} says, then one reload. */
    public static WriteReport removeAbilitiesAll(String variantName, String scope, String scopeId,
                                                  List<AbilityRow> removing, List<AbilityRow> shown) {
        AbilityRemovalPlan plan = abilityRemovalPlan(removing, shown);
        if (plan.turnOff().isEmpty() && plan.delete().isEmpty()) {
            return WriteReport.refused("They are off already");
        }
        WriteReport report = new WriteReport();
        keepInherited(report, variantName, scope, scopeId, plan.keep());
        keepInherited(report, variantName, scope, scopeId, plan.turnOff().stream().map(r -> r.with(false, true)).toList());
        for (AbilityRow row : plan.delete()) {
            if (!report.ok()) return report;
            report.record("the ability", TableWriter.delete(Schema.Abilities.FILE,
                    abilityKey(variantName, scope, scopeId, row)));
        }
        return Commit.reloadIfOk(report);
    }

    /** True when Remove on this row switches it off: an inherited row. */
    public static boolean removeTurnsOff(AbilityRow row) {
        return row.inherited();
    }

    /** The key of one ability row. */
    public static Map<String, String> abilityKey(String variantName, String scope, String scopeId,
                                                  String ability, String parameter) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.Abilities.VARIANT_NAME, variantName);
        keys.put(Schema.Abilities.SCOPE, VariantOps.normalScope(scope));
        keys.put(Schema.Abilities.SCOPE_ID, VariantOps.normalScopeId(scope, scopeId));
        keys.put(Schema.Abilities.ABILITY, strip(ability));
        keys.put(Schema.Abilities.PARAMETER, strip(parameter));
        return keys;
    }

    /**
     * The key of one row as {@link #sameRow} sees it: a class row adds its class, trigger and entity to the
     * legacy key, so two class rows that share a (blank) parameter are two rows in the file.
     */
    public static Map<String, String> abilityKey(String variantName, String scope, String scopeId, AbilityRow row) {
        Map<String, String> keys = abilityKey(variantName, scope, scopeId, row.ability(), row.parameter());
        if (row.isClass()) {
            keys.put(Schema.Abilities.EFFECT_CLASS, strip(row.effectClass()));
            keys.put(Schema.Abilities.TRIGGER, strip(row.trigger()));
            keys.put(Schema.Abilities.ENTITY, strip(row.entity()));
        }
        return keys;
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}