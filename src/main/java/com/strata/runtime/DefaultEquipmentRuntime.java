package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.CsvTable;
import com.strata.data.DefaultEquipmentTable;
import com.strata.model.DefaultEquipment;
import com.strata.model.WeightedPick;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Strata's default equipment: the game's roll, run by Strata so every value is editable. */
public final class DefaultEquipmentRuntime {

    private DefaultEquipmentRuntime() {
    }

    /** The game's rolls, by entity id. */
    private static volatile Map<String, DefaultEquipment> gameRolls = Map.of();

    /** Reads the game's rolls. */
    public static void load(Path gameLayer) {
        Map<String, DefaultEquipment> game = java.nio.file.Files.isRegularFile(gameLayer)
                ? DefaultEquipmentTable.fromRows(CsvTable.read(gameLayer)) : Map.of();
        gameRolls = Map.copyOf(game);
        Strata.LOGGER.info("Loaded default equipment rolls for {} mob(s)", game.size());
    }

    /** The game's roll for an entity id, or null when the game does not arm it. */
    public static DefaultEquipment gameRoll(String entityId) {
        return gameRolls.get(entityId);
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * Arms a mob with the GAME's roll alone: for reading a freshly made mob as the game makes it
     * (GameData#defaultGoals).
     */
    public static boolean rollGame(Mob mob, String entityId) {
        return arm(mob, gameRolls.get(entityId), null);
    }

    /**
     * Arms a mob in one pass.
     *
     * @param game the game's roll for the mob's entity, or null
     * @param loadout the slots the Equipment rows decide, or null
     */
    public static boolean arm(Mob mob, DefaultEquipment game, MobEquipment.Loadout loadout) {
        if ((game == null && loadout == null) || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        DefaultEquipment defaults = game == null ? DefaultEquipment.none() : game;
        List<DefaultEquipment.Piece> pieces = game == null ? List.of() : game.pieces();
        RandomSource random = mob.getRandom();
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(mob.blockPosition());
        double factor = difficulty.getSpecialMultiplier();
        boolean hard = level.getDifficulty() == Difficulty.HARD;
        Map<EquipmentSlot, Double> enchants = new EnumMap<>(EquipmentSlot.class);
        Map<EquipmentSlot, Double> drops = new EnumMap<>(EquipmentSlot.class);
        // A gated row is offered only while a nearby player's level passes it: one read for the whole arming
        // pass.
        int playerLevel = MobEquipment.nearestPlayerLevel(level, mob.blockPosition());

        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            String name = MobEquipment.SLOT_NAMES.get(i);
            // A slot whose every candidate is gated out falls back to the game's own roll, as an undecided
            // slot does.
            List<MobEquipment.Choice> gated = loadout != null && loadout.decides(i)
                    ? MobEquipment.applicable(loadout.slots().get(i), playerLevel) : null;
            boolean useRows = gated != null
                    && (!gated.isEmpty() || loadout.slots().get(i).isEmpty());
            if (useRows) {
                MobEquipment.Choice c = MobEquipment.pick(gated, random.nextDouble(), hard, factor);
                mob.setItemSlot(slot, c == null ? ItemStack.EMPTY : c.stack(level.registryAccess()));
                if (c != null) {
                    enchants.put(slot, c.enchant() != null ? c.enchant() : defaults.enchantFor(name));
                    drops.put(slot, c.drop() != null ? c.drop() : defaults.dropChance());
                }
            } else if (game != null) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                List<DefaultEquipment.Piece> candidates = pieces.stream().filter(p -> p.slot().equals(name)).toList();
                double[] weights = candidates.stream()
                        .mapToDouble(p -> (hard ? p.pctHard() : p.pct()) * (p.scaled() ? factor : 1)).toArray();
                int winner = WeightedPick.indexWithRemainder(weights, random.nextDouble(), 100.0);
                if (winner != WeightedPick.NONE && set(mob, slot, candidates.get(winner).item())) {
                    enchants.put(slot, candidates.get(winner).enchant());
                    drops.put(slot, candidates.get(winner).drop());
                }
            }
        }
        // Each piece's enchant roll, times the area factor as the game does, and its drop chance.
        for (EquipmentSlot slot : enchants.keySet()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            double enchant = enchants.get(slot);
            if (enchant > 0 && random.nextDouble() * 100 < enchant * factor) {
                EnchantmentHelper.enchantItemFromProvider(stack, level.registryAccess(),
                        VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
                mob.setItemSlot(slot, stack);
            }
            mob.setDropChance(slot, (float) (drops.get(slot) / 100.0));
        }
        return true;
    }

    /** Puts the item (or item variant) in the slot; false, and a debug line, for an unknown one. */
    private static boolean set(Mob mob, EquipmentSlot slot, String itemId) {
        ItemStack stack = ItemVariants.stack(itemId, 1, mob.level().registryAccess());
        if (stack.isEmpty()) {
            Strata.LOGGER.debug("Default equipment: unknown item '{}' - skipped", itemId);
            return false;
        }
        mob.setItemSlot(slot, stack);
        return true;
    }
}