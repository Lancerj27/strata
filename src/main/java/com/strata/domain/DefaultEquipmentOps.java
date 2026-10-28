package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.DefaultEquipmentTable;
import com.strata.data.Parse;
import com.strata.model.DefaultEquipment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** What the Equipment screen reads of the game's default equipment. */
public final class DefaultEquipmentOps {

    private DefaultEquipmentOps() {
    }

    private static Path gameLayer() {
        return GameLayer.dir().resolve(GameLayer.EQUIPMENT);
    }

    /** The game's roll for an entity, or null when the game does not arm it. */
    public static DefaultEquipment gameRoll(String entity) {
        return Files.isRegularFile(gameLayer()) ? DefaultEquipmentTable.fromRows(CsvTable.read(gameLayer())).get(entity) : null;
    }

    /**
     * The game's equipment as the Equipment list shows it: one row per piece, each with every number of its
     * own, drop and enchant written out.
     *
     * @param game the game's roll for the entity, or null
     */
    public static List<EquipmentOps.GameEquipment> gameEquipment(DefaultEquipment game) {
        List<EquipmentOps.GameEquipment> out = new ArrayList<>();
        if (game == null) {
            return out;
        }
        for (DefaultEquipment.Piece p : game.pieces()) {
            out.add(new EquipmentOps.GameEquipment(p.slot(), p.item(), p.pct(), p.pctHard(), "",
                    DefaultEquipmentTable.number(p.drop()), DefaultEquipmentTable.number(p.enchant()), p.scaled()));
        }
        return out;
    }

    /** {@link #gameEquipment} for an entity, from the game layer on disk. */
    public static List<EquipmentOps.GameEquipment> gameEquipment(String baseEntity) {
        return gameEquipment(gameRoll(baseEntity));
    }

    /**
     * A Equipment row's real chances by difficulty, for its tooltip: its share of the slot, its enchant and
     * drop (its own, else the game's default for the mob), through EquipmentOdds.
     *
     * @param game the game's roll for the mob, or null
     * @param slotTotal the weights of the rows sharing its slot
     */
    public static List<String> odds(EquipmentOps.EquipmentRow row, DefaultEquipment game, double slotTotal) {
        DefaultEquipment defaults = game == null ? DefaultEquipment.none() : game;
        double normal = EquipmentOps.oddsPct(row.chancePct(), slotTotal);
        double hard = row.chanceHard().isBlank() ? normal
                : EquipmentOps.oddsPct(Parse.number(row.chanceHard(), row.chancePct()), slotTotal);
        double enchant = row.enchant().isBlank() ? defaults.enchantFor(row.slot()) : Parse.number(row.enchant(), 0);
        double drop = row.drop().isBlank() ? defaults.dropChance() : Parse.number(row.drop(), 0);
        return com.strata.model.EquipmentOdds.lines(row.scaled(), normal, hard, enchant, drop);
    }
}
