package com.strata.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** THE BATCH SCREEN'S BACK END. */
public final class BatchOps {

    private BatchOps() {
    }

    /** What a batch changes: the Batch screen's "Change" dropdown. */
    public enum Field {
        SPAWNING("Spawning"), SCALING("Scaling"), MOB_GRIEFING("Mob griefing"), CATEGORY("Category"), LOOT("Loot"),
        SPAWN_RULES("Spawn rules"), ABILITIES("Abilities"), EQUIPMENT("Equipment"), ATTRIBUTES("Attributes"),
        COEFFICIENT("Coefficient"), START_LEVEL("Start level"), PASSIVE("Passive"), CONVERTS("Converts"),
        BOSS("Boss");

        public final String word;

        Field(String word) {
            this.word = word;
        }

        /** The field for a dropdown word, else SPAWNING. */
        public static Field of(String word) {
            for (Field f : values()) {
                if (f.word.equals(word)) {
                    return f;
                }
            }
            return SPAWNING;
        }
    }

    /** The fields' words, in the dropdown's order. */
    public static final List<String> FIELDS = java.util.Arrays.stream(Field.values()).map(f -> f.word).toList();

    /** The actions a field offers, in the "Action" dropdown's order. */
    public static List<String> actionsFor(Field field) {
        return switch (field) {
            case SPAWNING -> List.of("On", "Off");
            case SCALING, MOB_GRIEFING, PASSIVE, CONVERTS -> VariantOps.SWITCH_CHOICES;
            // The mob list's groups: the action is the new category.
            case CATEGORY -> CategoryOps.CHOICES;
            case SPAWN_RULES -> List.of("Set", "Remove");
            case LOOT, ABILITIES, EQUIPMENT -> List.of("Add", "Remove");
            case ATTRIBUTES -> List.of("Set", "Remove");
            case COEFFICIENT, START_LEVEL -> List.of("Set");
            case BOSS -> List.of("On", "Off", "Default");
        };
    }

    /** True when the action adds or sets, and so takes numbers (the "Add" and "Set" sections). */
    public static boolean adds(String action) {
        return "Add".equals(action) || "Set".equals(action);
    }

    /** One batch change, as the screen gathered it. */
    public record Request(Field field, String action, String item, String countMin, String countMax,
                          String chance, String slot, String ruleType, String ruleValue,
                          String ability, String parameter, String amount, String ticks, String radius,
                          String trigger, String entity, String duration) {

        /** A request with no effect class fields: a legacy ability, loot, equipment, a rule. */
        public Request(Field field, String action, String item, String countMin, String countMax,
                       String chance, String slot, String ruleType, String ruleValue,
                       String ability, String parameter, String amount, String ticks, String radius) {
            this(field, action, item, countMin, countMax, chance, slot, ruleType, ruleValue,
                    ability, parameter, amount, ticks, radius, "", "", "");
        }

        /** The ability row this request adds. */
        public AbilitiesOps.AbilityRow abilityRow() {
            if (com.strata.model.EffectClass.of(ability) != null) {
                return new AbilitiesOps.AbilityRow("", parameter, amount, ticks, radius, true, false,
                        ability.strip(), trigger, chance, "", duration, "", "", "", "", "", entity);
            }
            return new AbilitiesOps.AbilityRow(ability, parameter, amount, ticks, radius, true);
        }
    }

    /** Why the request cannot run, in the designer's words, or null. */
    public static String problem(Request r) {
        if (!actionsFor(r.field()).contains(r.action())) {
            return "Pick an action";
        }
        return switch (r.field()) {
            case SPAWNING, SCALING, MOB_GRIEFING, PASSIVE, CONVERTS, CATEGORY, BOSS -> null;
            case LOOT -> blank(r.item()) ? "Pick an item"
                    : adds(r.action()) ? LootOps.entryProblem("add", r.countMin(), r.countMax(), r.chance()) : null;
            case SPAWN_RULES -> blank(r.ruleType()) ? "Pick a rule"
                    : adds(r.action()) ? SpawnRulesOps.valueProblem(r.ruleType(), r.ruleValue()) : null;
            case ABILITIES -> blank(r.ability()) ? "Pick an ability"
                    : !adds(r.action()) ? null
                    : r.abilityRow().isClass() ? AbilitiesOps.classProblem(r.abilityRow())
                    : AbilitiesOps.abilityProblem(r.ability(), r.parameter(), r.amount(), r.ticks(), r.radius());
            case EQUIPMENT -> blank(r.slot()) ? "Pick a slot" : blank(r.item()) ? "Pick an item"
                    : adds(r.action()) ? chanceProblem(r.chance()) : null;
            case ATTRIBUTES -> blank(r.ability()) ? "Pick an attribute"
                    : !adds(r.action()) ? null : MobStatsOps.valueProblem(r.amount());
            case COEFFICIENT -> numberProblem(r.amount(), "The coefficient must be a number");
            case START_LEVEL -> VariantOps.startLevelProblem(r.amount());
        };
    }

    /** Why a typed number cannot be used, or null when it can. */
    private static String numberProblem(String typed, String word) {
        try {
            return Double.isFinite(Double.parseDouble(typed.strip())) ? null : word;
        } catch (NumberFormatException | NullPointerException e) {
            return word;
        }
    }

    private static String chanceProblem(String chance) {
        try {
            return EquipmentOps.equipmentChanceProblem(Double.parseDouble(chance.strip()));
        } catch (NumberFormatException | NullPointerException e) {
            return "The chance must be a number";
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    /** The change in words, for the question: "add the loot entry minecraft:bone (1 to 2, 50%)". */
    public static String describe(Request r) {
        String a = r.action().toLowerCase(Locale.ROOT);
        return switch (r.field()) {
            case SPAWNING -> "turn spawning " + r.action();
            case SCALING -> "set scaling to " + r.action();
            case MOB_GRIEFING -> "set mob griefing to " + r.action();
            case PASSIVE -> "set passive to " + r.action();
            case CONVERTS -> "set converts to " + r.action();
            case CATEGORY -> "set the category to " + r.action();
            case LOOT -> adds(r.action())
                    ? "add the loot entry " + r.item() + " (" + r.countMin().strip() + " to " + r.countMax().strip() + ", "
                    + r.chance().strip() + "%)"
                    : "remove the loot entry " + r.item();
            case SPAWN_RULES -> adds(r.action())
                    ? "set the spawn rule " + r.ruleType() + " to " + r.ruleValue().strip()
                    : "remove the spawn rule " + r.ruleType();
            case ABILITIES -> a + " the ability " + r.ability() + (blank(r.parameter()) ? "" : " " + r.parameter());
            case EQUIPMENT -> adds(r.action())
                    ? "add " + r.item() + " to the " + r.slot() + " slot (" + r.chance().strip() + "%)"
                    : "remove " + r.item() + " from the " + r.slot() + " slot";
            case ATTRIBUTES -> adds(r.action())
                    ? "set the attribute " + r.ability() + " to " + r.amount().strip()
                    : "remove the attribute " + r.ability();
            case COEFFICIENT -> "set the coefficient to " + r.amount().strip();
            case START_LEVEL -> "set the start level to " + r.amount().strip();
            case BOSS -> "turn the boss flag " + a;
        };
    }

    /** The question before a batch: what, to how many, in which group and list. */
    public static String question(Request r, int count, String group, String where) {
        String what = describe(r);
        return Character.toUpperCase(what.charAt(0)) + what.substring(1) + " for " + count + " mob(s): "
                + group + " in " + where + "?\nEach gets it as its own editor would give it. Undo puts them all back.";
    }

    /** Runs the change for every mob, then reloads once. */
    public static WriteReport apply(List<MobOps.BatchMob> mobs, Request r) {
        if (mobs.isEmpty()) {
            return WriteReport.refused("There are no mobs of that group in this list");
        }
        String problem = problem(r);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        return switch (r.field()) {
            case SPAWNING -> MobOps.batch(mobs, "On".equals(r.action())
                    ? MobOps.BatchAction.SPAWN_ON : MobOps.BatchAction.SPAWN_OFF);
            case SCALING -> MobOps.batch(mobs, switch (r.action()) {
                case "On" -> MobOps.BatchAction.SCALING_ON;
                case "Off" -> MobOps.BatchAction.SCALING_OFF;
                default -> MobOps.BatchAction.SCALING_DEFAULT;
            });
            case MOB_GRIEFING -> MobOps.batch(mobs, switch (r.action()) {
                case "On" -> MobOps.BatchAction.MOB_GRIEFING_ON;
                case "Off" -> MobOps.BatchAction.MOB_GRIEFING_OFF;
                default -> MobOps.BatchAction.MOB_GRIEFING_DEFAULT;
            });
            case CATEGORY -> CategoryOps.batch(mobs, r.action());
            case PASSIVE -> MobOps.setVariantColumn(mobs, com.strata.data.Schema.Variants.PASSIVE,
                    VariantOps.switchStored(r.action()));
            case CONVERTS -> MobOps.setVariantColumn(mobs, com.strata.data.Schema.Variants.CONVERTS,
                    VariantOps.switchStored(r.action()));
            default -> Commit.once(() -> {
                WriteReport report = new WriteReport();
                for (MobOps.BatchMob m : mobs) {
                    WriteReport one = applyOne(m, r);
                    if (one.refusal() != null) {
                        return WriteReport.refused(m.entity() + (m.variantName().isEmpty() ? "" : " "
                                + m.variantName()) + ": " + one.refusal());
                    }
                    if (!one.ok()) {
                        return one;
                    }
                    report.record("the batch change for " + m.entity(), true);
                }
                return Commit.reloadIfOk(report);
            });
        };
    }

    /** One mob's change, as its own editor makes it. */
    private static WriteReport applyOne(MobOps.BatchMob m, Request r) {
        String scope = m.scope();
        String scopeId = VariantOps.normalScopeId(scope, m.scopeId());
        return switch (r.field()) {
            // REMOVE AS THE LOOT EDITOR DOES: the mob's rows of that item are removed (its own
            // deleted, an inherited one denied).
            case LOOT -> {
                if (!adds(r.action())) {
                    List<LootOps.LootRow> rows = LootOps.rowsFor(scope, scopeId, m.entity(), m.variantName(),
                            List.of());
                    List<LootOps.LootRow> matching = rows.stream()
                            .filter(row -> row.item().equals(r.item().strip())).toList();
                    if (!matching.isEmpty()) {
                        yield LootOps.removeAll(scope, scopeId, m.entity(), m.variantName(), matching, rows);
                    }
                }
                yield LootOps.save(m.entity(), m.variantName(),
                        new LootOps.EntryTarget(scope, scopeId, 0, null),
                        new LootOps.LootForm(r.item().strip(), adds(r.action()) ? r.countMin().strip() : "1",
                                adds(r.action()) ? r.countMax().strip() : "1",
                                adds(r.action()) ? r.chance().strip() : "100",
                                adds(r.action()) ? "add" : "deny", LootOps.NO_CONDITION, ""));
            }
            case SPAWN_RULES -> {
                List<String[]> own = new ArrayList<>(SpawnRulesOps.spawnRulesFor(scope, scopeId, m.entity(),
                        m.variantName()));
                if (own.isEmpty()) {
                    own.addAll(SpawnRulesOps.inheritedFor(scope, scopeId, m.entity(), m.variantName()));
                }
                String type = r.ruleType().strip().toLowerCase(Locale.ROOT);
                own.removeIf(c -> c[0] != null && c[0].strip().toLowerCase(Locale.ROOT).equals(type));
                if (adds(r.action())) {
                    own.add(new String[]{type, r.ruleValue().strip()});
                }
                yield SpawnRulesOps.replaceAll(scope, scopeId, m.entity(), m.variantName(), own);
            }
            case ABILITIES -> {
                String owner = owner(m);
                if (owner == null) {
                    yield WriteReport.refused("Its default variant could not be made");
                }
                var shown = com.strata.domain.AbilitiesOps.abilitiesShown(owner, m.entity(), scope, scopeId);
                if (adds(r.action())) {
                    yield com.strata.domain.AbilitiesOps.addAbility(owner, scope, scopeId, r.abilityRow(), shown);
                }
                // The editor's several-row remove, over one list read: the one-row remove ran in
                // a loop over a stale list, and its refusals were counted as done.
                var removing = shown.stream().filter(row -> row.enabled()
                        && AbilitiesOps.rowName(row).equals(r.ability().strip())
                        && (blank(r.parameter()) || row.parameter().equals(r.parameter().strip()))).toList();
                yield removing.isEmpty() ? new WriteReport()
                        : com.strata.domain.AbilitiesOps.removeAbilitiesAll(owner, scope, scopeId, removing, shown);
            }
            case EQUIPMENT -> {
                String owner = owner(m);
                if (owner == null) {
                    yield WriteReport.refused("Its default variant could not be made");
                }
                var rows = EquipmentOps.equipmentFor(owner, m.entity(), scope, scopeId,
                        DefaultEquipmentOps.gameEquipment(m.entity()));
                if (adds(r.action())) {
                    yield EquipmentOps.saveEquipment(owner, scope, scopeId, null, r.slot(), r.item(),
                            r.chance(), "", "", "", false, "", rows);
                }
                var removing = rows.stream().filter(row -> row.slot().equals(r.slot().strip())
                        && row.item().equals(r.item().strip())).toList();
                yield removing.isEmpty() ? new WriteReport()
                        : EquipmentOps.removeEquipmentAll(owner, scope, scopeId, removing, rows);
            }
            case ATTRIBUTES -> {
                MobDataOps.Target t = new MobDataOps.Target(m.entity(), m.variantName(), m.scope(), m.scopeId());
                MobDataOps.Value v = new MobDataOps.Value(com.strata.data.GameLayer.ATTRIBUTE, r.ability().strip(),
                        "", "", "");
                yield adds(r.action()) ? MobDataOps.save(t, v, r.amount().strip()) : MobDataOps.restore(t, v);
            }
            case COEFFICIENT -> MobOps.batchCoefficient(List.of(m), r.amount().strip());
            case START_LEVEL -> MobOps.setVariantColumn(List.of(m), com.strata.data.Schema.Variants.START_LEVEL,
                    r.amount().strip());
            case BOSS -> {
                MobDataOps.Target t = new MobDataOps.Target(m.entity(), m.variantName(), m.scope(), m.scopeId());
                MobDataOps.Value v = new MobDataOps.Value(com.strata.data.GameLayer.FLAGS,
                        com.strata.data.Schema.MobFlags.BOSS, "", "", "");
                yield switch (r.action()) {
                    case "On" -> MobDataOps.save(t, v, "true");
                    case "Off" -> MobDataOps.save(t, v, "false");
                    default -> MobDataOps.restore(t, v);
                };
            }
            default -> new WriteReport();
        };
    }

    /** Where a mob's abilities and equipment live: its variant, or a plain mob's default variant. */
    private static String owner(MobOps.BatchMob m) {
        if (!m.variantName().isEmpty()) {
            return m.variantName();
        }
        MobOps.DefaultVariant made = MobOps.ensureDefaultVariant(m.entity());
        return made.report().ok() ? made.name() : null;
    }
}
