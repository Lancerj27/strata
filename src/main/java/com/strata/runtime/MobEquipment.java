package com.strata.runtime;

import com.strata.data.Schema;
import com.strata.model.WeightedPick;
import com.strata.data.Parse;
import com.strata.data.CsvTable;
import com.strata.Strata;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What a variant CARRIES, from config/strata/equipment.csv. */
public final class MobEquipment {

    private MobEquipment() {}

    /** The slot count: SLOT_NAMES' size. */
    private static final int SLOT_COUNT = 6;

    /**
     * One thing a slot MIGHT hold.
     *
     * @param item the item, never null
     * @param weight this candidate's share of its slot on Easy and Normal, in percentage points as written in
     *     chance_pct
     * @param weightHard the same on Hard (chance_hard, blank = weight)
     * @param drop its chance to drop on death, or null for the default
     * @param enchant its chance to be enchanted, or null for the default
     * @param scaled true when its weights are at full local difficulty and scale down with it
     * @param playerLevel the player XP level this candidate needs, or null for no gate. The shared grammar is
     *     {@link com.strata.model.LevelRange}.
     */
    public record Choice(Item item, double weight, double weightHard, Double drop, Double enchant,
                         boolean scaled, String variant, com.strata.model.LevelRange playerLevel) {

        /** A candidate with no player-level gate. */
        public Choice(Item item, double weight, double weightHard, Double drop, Double enchant,
                      boolean scaled, String variant) {
            this(item, weight, weightHard, drop, enchant, scaled, variant, null);
        }

        /** A plain item's candidate (no item variant). */
        public Choice(Item item, double weight, double weightHard, Double drop, Double enchant, boolean scaled) {
            this(item, weight, weightHard, drop, enchant, scaled, null);
        }

        /** A candidate with one weight and the defaults. */
        public Choice(Item item, double weight) {
            this(item, weight, weight, null, null, false);
        }

        /** The stack to put on: the item variant's, else the plain item's. */
        public net.minecraft.world.item.ItemStack stack(net.minecraft.core.HolderLookup.Provider registries) {
            if (variant != null) {
                net.minecraft.world.item.ItemStack made = ItemVariants.make(variant, 1, registries);
                if (!made.isEmpty()) {
                    return made;
                }
            }
            return new net.minecraft.world.item.ItemStack(item);
        }
    }

    /**
     * A whole loadout.
     *
     * @param slots one candidate list per slot, indexed to SLOT_NAMES
     * @param decided the slot indexes this loadout decides: a candidate or a deny row. A slot outside it is
     *     left to the default equipment.
     */
    public record Loadout(
            List<List<Choice>> slots,
            Set<Integer> decided
    ) {
        /** True when this loadout decides the slot at this index. */
        public boolean decides(int slot) {
            return decided.contains(slot);
        }
    }

    /** The variant's loadout, with each slot it does not decide taken from its base mob's. */
    public static Loadout merged(Loadout own, Loadout base) {
        if (base == null) {
            return own;
        }
        if (own == null) {
            return base;
        }
        List<List<Choice>> slots = new ArrayList<>(SLOT_NAMES.size());
        Set<Integer> decided = new HashSet<>();
        for (int i = 0; i < SLOT_NAMES.size(); i++) {
            Loadout from = own.decides(i) ? own : base;
            slots.add(from.slots().get(i));
            if (from.decides(i)) {
                decided.add(i);
            }
        }
        return new Loadout(slots, Set.copyOf(decided));
    }

    private static volatile Map<String, Loadout> byVariant = Map.of();

    /** Loads the equipment from equipment.csv. */
    public static void load(Path csvPath) {
        Map<String, List<List<Choice>>> bySlot = new HashMap<>();
        Map<String, Set<Integer>> decidedBy = new HashMap<>();

        for (Map<String, String> row : CsvTable.read(csvPath)) {
            try {
                String variant = row.getOrDefault(Schema.Equipment.VARIANT_NAME, "").strip();
                if (variant.isEmpty()) continue;
                // INSTANCED: the key carries the scope, so a tier's loadout for a variant is a different
                // entry from the global one rather than the last row read winning.
                String instance = ScopeIndex.key(
                        row.getOrDefault(Schema.Equipment.SCOPE, ""),
                        row.getOrDefault(Schema.Equipment.SCOPE_ID, ""),
                        variant);

                String slotName = row.getOrDefault(Schema.Equipment.SLOT, "").strip().toLowerCase(java.util.Locale.ROOT);
                String itemStr = row.getOrDefault(Schema.Equipment.ITEM, "").strip();
                // A deny of the game's random armor has no item.
                if (itemStr.isEmpty() && "deny".equalsIgnoreCase(
                        row.getOrDefault(Schema.Equipment.MODE, "").strip())) {
                    int denied = slotIndex(slotName);
                    if (denied >= 0) {
                        bySlot.computeIfAbsent(instance, k -> emptySlots());
                        decidedBy.computeIfAbsent(instance, k -> new HashSet<>()).add(denied);
                    }
                    continue;
                }
                if (itemStr.isEmpty()) continue;

                int slotIdx = slotIndex(slotName);
                if (slotIdx < 0) {
                    Strata.LOGGER.warn("equipment.csv: {} has unknown slot '{}' - row skipped",
                            variant, slotName);
                    continue;
                }

                // An item variant is its base item here, and made whole at spawn.
                String variantName = com.strata.model.ItemVariant.nameOf(itemStr);
                ItemVariants.Def variantDef = ItemVariants.get(variantName);
                if (variantName != null && variantDef == null) {
                    Strata.LOGGER.warn("equipment.csv: {} has no item variant '{}' - row skipped", variant, variantName);
                    continue;
                }
                ResourceLocation itemId = variantDef != null ? ResourceLocation.tryParse(variantDef.baseId())
                        : ResourceLocation.tryParse(itemStr);
                if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                    Strata.LOGGER.warn("equipment.csv: {} has unknown item '{}' - row skipped",
                            variant, itemStr);
                    continue;
                }

                // A DENY IS NOT A CANDIDATE.
                if ("deny".equalsIgnoreCase(
                        row.getOrDefault(Schema.Equipment.MODE, "").strip())) {
                    bySlot.computeIfAbsent(instance, k -> emptySlots());
                    decidedBy.computeIfAbsent(instance, k -> new HashSet<>()).add(slotIdx);
                    continue;
                }

                double weight = Parse.number(row.get(Schema.Equipment.CHANCE_PCT), 100.0);
                double weightHard = Parse.number(row.get(Schema.Equipment.CHANCE_HARD), weight);
                if (weight <= 0.0 && weightHard <= 0.0) {
                    Strata.LOGGER.warn("equipment.csv: {} has {} at chance 0 - row skipped",
                            variant, slotName);
                    continue;
                }

                bySlot.computeIfAbsent(instance, k -> emptySlots())
                        .get(slotIdx)
                        .add(new Choice(BuiltInRegistries.ITEM.get(itemId), Math.max(0, weight),
                                Math.max(0, weightHard), optional(row.get(Schema.Equipment.DROP_PCT)),
                                optional(row.get(Schema.Equipment.ENCHANT_PCT)),
                                scaled(row.get(Schema.Equipment.LOCAL_DIFFICULTY)),
                                variantDef == null ? null : variantName,
                                levelGate(row.get(Schema.Equipment.PLAYER_LEVEL))));
                decidedBy.computeIfAbsent(instance, k -> new HashSet<>()).add(slotIdx);
            } catch (Exception e) {
                Strata.LOGGER.warn("equipment.csv: bad row {} - {}", row, e.toString());
            }
        }

        Map<String, Loadout> parsed = new HashMap<>();
        for (var entry : bySlot.entrySet()) {
            parsed.put(entry.getKey(), new Loadout(entry.getValue(),
                    Set.copyOf(decidedBy.getOrDefault(entry.getKey(), Set.of()))));
        }

        byVariant = Map.copyOf(parsed);
        Strata.LOGGER.info("Loaded equipment for {} variants", parsed.size());
    }

    /** A fresh candidate list per slot, indexed to SLOT_NAMES. */
    private static List<List<Choice>> emptySlots() {
        List<List<Choice>> slots = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots.add(new ArrayList<>());
        }
        return slots;
    }

    /** A 0 to 100 chance cell, or null when blank or not a number: the default then applies. */
    public static Double optional(String cell) {
        String t = cell == null ? "" : cell.strip();
        if (t.isEmpty()) {
            return null;
        }
        try {
            double v = Double.parseDouble(t);
            return Double.isFinite(v) ? Math.max(0, Math.min(100, v)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True for a local_difficulty cell of "yes". */
    public static boolean scaled(String cell) {
        return cell != null && "yes".equalsIgnoreCase(cell.strip());
    }

    /** A player level gate from a cell, or null for no gate. */
    public static com.strata.model.LevelRange levelGate(String cell) {
        String t = cell == null ? "" : cell.strip();
        if (t.isEmpty()) {
            return null;
        }
        com.strata.model.LevelRange range = com.strata.model.LevelRange.parse(t);
        if (range == null) {
            Strata.LOGGER.warn("equipment.csv: player_level '{}' is not a level test - the gate is off", t);
        }
        return range;
    }

    /**
     * The candidates a player at this experience level may see: a gated candidate is left out while the test
     * fails, so a different candidate or the game's own equipment can fill the slot.
     */
    public static List<Choice> applicable(List<Choice> candidates, int playerLevel) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }
        List<Choice> out = new ArrayList<>(candidates.size());
        for (Choice c : candidates) {
            if (c.playerLevel() == null || c.playerLevel().matches(playerLevel)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * The nearest player's experience level within 128 blocks - the radius a Time since rest and a Player
     * level spawn rule read - or 0 when no player is near, which no gate above 0 passes.
     */
    public static int nearestPlayerLevel(net.minecraft.server.level.ServerLevel level,
                                         net.minecraft.core.BlockPos pos) {
        var player = level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 128, false);
        return player == null ? 0 : player.experienceLevel;
    }

    /** The slot names the slot column accepts, indexed to SLOTS. */
    public static final List<String> SLOT_NAMES =
            List.of("mainhand", "offhand", "head", "chest", "legs", "feet");

    private static int slotIndex(String name) {
        return SLOT_NAMES.indexOf(name);
    }

    /** Equips a mob marked at spawn (MobLevel#EQUIPMENT_PENDING), once, when it joins its level. */
    public static void equipPending(Mob mob) {
        if (!mob.getData(MobLevel.EQUIPMENT_PENDING)) {
            return;
        }
        mob.removeData(MobLevel.EQUIPMENT_PENDING);
        net.minecraft.resources.ResourceLocation key =
                BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType()).map(k -> k.location()).orElse(null);
        String entityId = VariantEntityRegistry.baseEntityIdForType(mob.getType())
                .orElse(key == null ? "" : key.toString());
        String packed = mob.getData(MobLevel.VARIANT_SCOPE);
        String tier = ScopeIndex.tierOf(packed);
        String biome = ScopeIndex.biomeOf(packed);
        String variant = mob.getData(MobLevel.VARIANT_ID);
        // A plain mob's equipment lives on its default variant (Variant#defaultNameFor).
        String equipmentOwner = variant == null || variant.isEmpty()
                ? com.strata.model.Variant.defaultNameFor(entityId) : variant;
        String base = VariantHandler.baseOf(equipmentOwner);
        // ONE PASS: the game's pieces and the instance's Equipment rows arm the mob together
        // (DefaultEquipmentRuntime#arm).
        Loadout loadout = loadoutFor(equipmentOwner, base, tier, biome);
        // No equipment rows for this mob: the game's own roll at spawn stands.
        if (loadout == null) {
            return;
        }
        DefaultEquipmentRuntime.arm(mob, DefaultEquipmentRuntime.gameRoll(entityId), loadout);
        // AFTER the equipment: a per-slot drop chance or Always drops wins over a piece's own Drop %, and the
        // join applied the properties before the equipment went on.
        MobPropertyEffects.reapplyDropChances(mob);
        // After the equipment: a skeleton picks its bow or melee goal from what it holds, and
        // "attack-players" checks for that attack.
        applyGoalSet(mob, variant, VariantHandler.baseOf(variant), tier, biome);
    }

    /**
     * The Equipment rows an instance runs FOR THE SCOPE IT SPAWNED IN: resolved biome -&gt; tier -&gt;
     * global, so a tier that defines equipment overrides the global set and one that does not inherits it.
     */
    public static Loadout loadoutFor(String variant, String base, String tier, String biome) {
        return merged(ScopeIndex.resolve(byVariant, variant, tier, biome),
                base.isEmpty() ? null : ScopeIndex.resolve(byVariant, base, tier, biome));
    }

    /** What a mob's join does. */
    public enum JoinAction { NOTHING, EQUIPMENT_AND_GOALS, GOALS }

    /**
     * What a join does: a fresh spawn gets its equipment and behavior, and any other mob with a variant gets
     * its behavior again.
     */
    public static JoinAction joinAction(boolean equipmentPending, String variant) {
        if (equipmentPending) {
            return JoinAction.EQUIPMENT_AND_GOALS;
        }
        return variant == null || variant.isEmpty() ? JoinAction.NOTHING : JoinAction.GOALS;
    }

    /** The mob objects whose behavior is on, so one object never gets it twice. */
    private static final Set<Mob> GOALS_ON =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /**
     * TAGS A PLAIN MOB WITH ITS DEFAULT VARIANT, when it has no variant and that variant exists.
     *
     * @return the mob's variant after, or "" for none
     */
    public static String tagPlain(Mob mob) {
        String variant = mob.getData(MobLevel.VARIANT_ID);
        if (variant != null && !variant.isEmpty()) {
            return variant;
        }
        String own = com.strata.model.Variant.defaultNameFor(typeId(mob));
        if (own.isEmpty() || !com.strata.data.Config.tables().variants().containsKey(own)) {
            return "";
        }
        mob.setData(MobLevel.VARIANT_ID, own);
        MobProperties.forget(mob);
        String packed = mob.getData(MobLevel.VARIANT_SCOPE);
        // A mob from before its spawn stored a scope: where it stands now, so a tier's or biome's goals and
        // abilities reach it.
        if ((packed == null || packed.isEmpty()) && mob.level() instanceof net.minecraft.server.level.ServerLevel) {
            var here = com.strata.data.Config.resolve(mob.level().getBiome(mob.blockPosition()).unwrapKey()
                    .map(k -> k.location()).orElse(null));
            packed = ScopeIndex.packScope(here.tier(), here.biome());
            mob.setData(MobLevel.VARIANT_SCOPE, packed);
        }
        VariantHandler.applyAtSpawn(mob, own, ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
        return own;
    }

    /** Runs at every join of a mob on the server (MobScalingHandler#onJoin). */
    public static void onJoin(Mob mob) {
        String variant = tagPlain(mob);
        switch (joinAction(mob.getData(MobLevel.EQUIPMENT_PENDING), variant)) {
            case EQUIPMENT_AND_GOALS -> equipPending(mob);
            case GOALS -> {
                if (!GOALS_ON.contains(mob)) {
                    String packed = mob.getData(MobLevel.VARIANT_SCOPE);
                    applyGoalSet(mob, variant, VariantHandler.baseOf(variant),
                            ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
                }
            }
            case NOTHING -> {
                // A mob with no variant tag: the plain mob's own behavior, as the editor shows it, then its
                // family's once edited.
                if (!GOALS_ON.contains(mob)) {
                    applyPlainGoalSet(mob);
                }
            }
        }
    }

    /** A plain mob's behavior, from its default variant, in the scope it spawned in if known. */
    private static void applyPlainGoalSet(Mob mob) {
        String packed = mob.getData(MobLevel.VARIANT_SCOPE);
        applyGoalSet(mob, com.strata.model.Variant.defaultNameFor(typeId(mob)), "",
                ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
    }

    /** Applies a loaded mob's behavior again after a reload. */
    public static void reapplyGoalSet(Mob mob) {
        GOALS_ON.remove(mob);
        String variant = mob.getData(MobLevel.VARIANT_ID);
        if (variant == null || variant.isEmpty()) {
            applyPlainGoalSet(mob);
        } else {
            String packed = mob.getData(MobLevel.VARIANT_SCOPE);
            applyGoalSet(mob, variant, VariantHandler.baseOf(variant),
                    ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed));
        }
    }

    /**
     * Applies the AI the mob's instance runs, in the scope it spawned in (GoalsRuntime#listFor: the nearest
     * scope's own list or behavior, its base mob's, else its family's once edited).
     */
    private static void applyGoalSet(Mob mob, String variant, String base, String tier, String biome) {
        GOALS_ON.add(mob);
        List<com.strata.model.GoalRow> rows = GoalsRuntime.listFor(variant, base, tier, biome, typeId(mob));
        if (rows != null) {
            GoalsRuntime.apply(mob, rows);
        }
        // A PASSIVE MOB HUNTS NO PLAYER, whatever goal list it runs.
        if (MobScalingSwitch.passive(variant, base, tier, biome)) {
            GoalsRuntime.dropPlayerHunting(mob);
        }
        // A sun-immune mob does not fear the sun, and a mob that burns seeks shade, as a skeleton does.
        Boolean sun = SunBurn.setting(mob);
        if (Boolean.FALSE.equals(sun)) {
            GoalsRuntime.dropSunGoals(mob);
        } else if (Boolean.TRUE.equals(sun)) {
            GoalsRuntime.addSunGoals(mob);
        }
    }

    /**
     * The winner of one slot's weighted draw, or null for an empty slot.
     *
     * @param candidates the slot's rows, in table order
     * @param roll a uniform value in [0, 1)
     * @param hard true on Hard: each row's Hard weight
     * @param factor the area's local-difficulty factor, 0 to 1: a row marked for it has its weight times this
     */
    public static Choice pick(List<Choice> candidates, double roll, boolean hard, double factor) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < weights.length; i++) {
            Choice c = candidates.get(i);
            weights[i] = (hard ? c.weightHard() : c.weight()) * (c.scaled() ? factor : 1);
        }
        // Spread over at least 100, so weights that do not fill the slot leave a share where nothing wins —
        // one 30% row is a 30% chance, not a certainty.
        int winner = WeightedPick.indexWithRemainder(weights, roll, 100.0);
        return winner == WeightedPick.NONE ? null : candidates.get(winner);
    }

    /** The mob's type id, for its family. */
    private static String typeId(Mob mob) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType())
                .map(k -> k.location().toString()).orElse("");
    }
}
