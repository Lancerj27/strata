package com.strata.runtime;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Schema;
import com.strata.model.Trade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** VILLAGER AND WANDERING TRADER TRADES, SERVED BY STRATA. */
public final class TradesRuntime {

    public static final String WANDERER = "minecraft:wandering_trader";

    /** The game's listings, "profession|level" to the list as the data load gave it. */
    private static final Map<String, List<VillagerTrades.ItemListing>> GAME = new ConcurrentHashMap<>();
    /** The edits, "profession|level" to "scope|scope id" to the rows, in table order. */
    private static volatile Map<String, Map<String, List<Trade.Edit>>> edits = Map.of();

    private static final String COMMENT = """
            The GAME LAYER: every villager profession's and the wandering trader's trades, rewritten at every game
            start and every server data load (with the trades mods add). Do not edit: your edits go in trades.csv.
            entity: the profession (or minecraft:wandering_trader). key: level|index. value: kind|cost A|count|
            cost B|count|result|count|max uses|xp|price multiplier|note.""";

    // ---- the edits ----

    /** Reads trades.csv. */
    public static void load(Path csv) {
        edits = index(CsvTable.read(csv));
    }

    /** The edits by list and scope, in table order. */
    public static Map<String, Map<String, List<Trade.Edit>>> index(List<Map<String, String>> rows) {
        Map<String, Map<String, List<Trade.Edit>>> out = new HashMap<>();
        for (Map<String, String> row : rows) {
            String list = cell(row, Schema.Trades.PROFESSION) + "|" + cell(row, Schema.Trades.LEVEL);
            String scope = cell(row, Schema.Trades.SCOPE) + "|" + cell(row, Schema.Trades.SCOPE_ID);
            out.computeIfAbsent(list, k -> new HashMap<>()).computeIfAbsent(scope, k -> new ArrayList<>())
                    .add(edit(row));
        }
        return out;
    }

    /** One row as an edit. */
    public static Trade.Edit edit(Map<String, String> row) {
        boolean remove = "remove".equals(cell(row, Schema.Trades.MODE));
        Trade trade = remove ? null : Trade.plain(cell(row, Schema.Trades.COST_A), whole(row, Schema.Trades.COST_A_COUNT, 1),
                cell(row, Schema.Trades.COST_B), whole(row, Schema.Trades.COST_B_COUNT, 0),
                cell(row, Schema.Trades.RESULT), whole(row, Schema.Trades.RESULT_COUNT, 1),
                whole(row, Schema.Trades.MAX_USES, 12), whole(row, Schema.Trades.XP, 1),
                (float) com.strata.data.Parse.number(row.get(Schema.Trades.PRICE_MULTIPLIER), 0.05));
        return new Trade.Edit(cell(row, Schema.Trades.ROW), remove, trade);
    }

    /** The layers that reach a place: global, its tier's, its biome's, each a "scope|id" key. */
    public static Map<String, String> layers(String tier, String biome) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("global", "|");
        if (tier != null && !tier.isEmpty()) {
            out.put("tier", "tier|" + tier);
        }
        if (biome != null && !biome.isEmpty()) {
            out.put("biome", "biome|" + biome);
        }
        return out;
    }

    /** A list as a place gets it, from the loaded edits. */
    public static List<Trade.Entry> effective(List<Trade> game, Map<String, List<Trade.Edit>> byScope,
                                              Map<String, String> layers) {
        Map<String, List<Trade.Edit>> ordered = new LinkedHashMap<>();
        layers.forEach((layer, key) -> ordered.put(layer, byScope == null ? List.of()
                : byScope.getOrDefault(key, List.of())));
        return Trade.effective(game, ordered);
    }

    // ---- seed and serve ----

    /** At game start: the game's lists as the game holds them, into the game layer. */
    public static void extractAtStart() {
        Map<String, List<VillagerTrades.ItemListing>> lists = new LinkedHashMap<>();
        for (var e : VillagerTrades.TRADES.entrySet()) {
            String prof = professionId(e.getKey());
            e.getValue().int2ObjectEntrySet().forEach(l -> lists.put(prof + "|" + l.getIntKey(), List.of(l.getValue())));
        }
        VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet()
                .forEach(l -> lists.put(WANDERER + "|" + l.getIntKey(), List.of(l.getValue())));
        // A second start in one session finds Strata's slots there: the data load already wrote the layer.
        if (lists.values().stream().flatMap(List::stream).anyMatch(l -> l instanceof Slot)) {
            return;
        }
        write(lists);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onVillagerTrades(VillagerTradesEvent event) {
        String prof = professionId(event.getType());
        for (int level = 1; level <= 5; level++) {
            List<VillagerTrades.ItemListing> list = event.getTrades().get(level);
            GAME.put(prof + "|" + level, List.copyOf(list));
            if (!com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.ENABLE_TRADES)) {
                continue;
            }
            int n = Math.max(2, list.size());
            list.clear();
            for (int i = 0; i < n; i++) {
                list.add(new Slot(prof + "|" + level, i));
            }
        }
        write(GAME);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWandererTrades(WandererTradesEvent event) {
        serve(event.getGenericTrades(), WANDERER + "|1", 5);
        serve(event.getRareTrades(), WANDERER + "|2", 1);
        write(GAME);
    }

    private static void serve(List<VillagerTrades.ItemListing> list, String key, int picks) {
        GAME.put(key, List.copyOf(list));
        if (!com.strata.data.StrataConfig.read(com.strata.data.StrataConfig.ENABLE_TRADES)) {
            return;
        }
        int n = Math.max(picks, list.size());
        list.clear();
        for (int i = 0; i < n; i++) {
            list.add(new Slot(key, i));
        }
    }

    private static void write(Map<String, List<VillagerTrades.ItemListing>> lists) {
        List<GameLayer.Row> rows = new ArrayList<>();
        lists.forEach((key, list) -> {
            String prof = key.substring(0, key.lastIndexOf('|'));
            String level = key.substring(key.lastIndexOf('|') + 1);
            for (int i = 0; i < list.size(); i++) {
                rows.add(new GameLayer.Row(prof, GameLayer.TRADE, level + "|" + i, TradeReader.read(list.get(i)).encode()));
            }
        });
        // The villager types and professions the game has, for the Trades screen's choices.
        BuiltInRegistries.VILLAGER_TYPE.keySet().forEach(k -> rows.add(
                new GameLayer.Row(k.toString(), GameLayer.VILLAGER_TYPE, "id", k.toString())));
        BuiltInRegistries.VILLAGER_PROFESSION.keySet().forEach(k -> rows.add(
                new GameLayer.Row(k.toString(), GameLayer.PROFESSION, "id", k.toString())));
        GameLayer.write(GameLayer.dir().resolve(GameLayer.TRADES), COMMENT, rows);
    }

    /** The listings a place gets for a list: the game's where untouched, else Strata's own. */
    static List<VillagerTrades.ItemListing> listingsFor(String list, String tier, String biome) {
        List<VillagerTrades.ItemListing> game = GAME.getOrDefault(list, List.of());
        List<Trade> gameTrades = game.stream().map(TradeReader::read).toList();
        List<VillagerTrades.ItemListing> out = new ArrayList<>();
        for (Trade.Entry e : effective(gameTrades, edits.get(list), layers(tier, biome))) {
            if (e.key().startsWith("g") && !e.edited()) {
                out.add(game.get(Integer.parseInt(e.key().substring(1))));
            } else {
                VillagerTrades.ItemListing own = basic(e.trade());
                if (own != null) {
                    out.add(own);
                }
            }
        }
        return out;
    }

    /** A plain trade as NeoForge's BasicItemListing, or null when an item is unknown. */
    static VillagerTrades.ItemListing basic(Trade t) {
        ItemStack a = stack(t.costA(), t.costACount());
        ItemStack b = t.costB().isEmpty() || t.costBCount() <= 0 ? ItemStack.EMPTY : stack(t.costB(), t.costBCount());
        ItemStack r = stack(t.result(), t.resultCount());
        if (a == null || b == null || r == null) {
            return null;
        }
        return new net.neoforged.neoforge.common.BasicItemListing(a, b, r, t.maxUses(), t.xp(), t.priceMultiplier());
    }

    /** A trade's stack: an item variant's or the plain item's; null for none. */
    private static ItemStack stack(String id, int count) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ItemStack stack = ItemVariants.stack(id, count, server == null ? null : server.registryAccess());
        return stack.isEmpty() ? null : stack;
    }

    public static String professionId(VillagerProfession profession) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getResourceKey(profession)
                .map(k -> k.location().toString()).orElse("");
    }

    /** The trades already given in this pick: trader to (tick, list to the places taken). */
    private static final Map<Entity, Pick> PICKS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private record Pick(long tick, Map<String, Set<Integer>> taken) {
    }

    /** One place of a list the game picks from. */
    static final class Slot implements VillagerTrades.ItemListing {
        private final String list;
        /** Its place in the game's list: with nothing of the designer's, the slot is that listing. */
        private final int index;

        Slot(String list, int index) {
            this.list = list;
            this.index = index;
        }

        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            // No trade edits and nothing disabled: the game's own listing, so the game's pick stands.
            if (edits.isEmpty() && !DisabledContent.any()) {
                List<VillagerTrades.ItemListing> game = GAME.getOrDefault(list, List.of());
                return index < game.size() ? game.get(index).getOffer(trader, random) : null;
            }
            String biome = trader.level().getBiome(trader.blockPosition()).unwrapKey()
                    .map(k -> k.location().toString()).orElse("");
            String tier = biome.isEmpty() ? "" : com.strata.data.Config.resolve(biome).tier();
            List<VillagerTrades.ItemListing> listings = listingsFor(list, tier, biome);
            Pick pick = PICKS.compute(trader, (k, old) -> old != null && old.tick() == trader.level().getGameTime()
                    ? old : new Pick(trader.level().getGameTime(), new HashMap<>()));
            Set<Integer> taken = pick.taken().computeIfAbsent(list, k -> new java.util.HashSet<>());
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < listings.size(); i++) {
                if (!taken.contains(i)) {
                    free.add(i);
                }
            }
            while (!free.isEmpty()) {
                int i = free.remove(random.nextInt(free.size()));
                taken.add(i);
                MerchantOffer offer = listings.get(i).getOffer(trader, random);
                // A DISABLED ITEM IS NOT TRADED: an offer that gives one, or asks for one, is skipped and
                // another of the list is drawn - the game's, a mod's, or yours.
                if (offer != null && !DisabledContent.disabled(offer.getResult().getItem())
                        && !DisabledContent.disabled(offer.getCostA().getItem())
                        && (offer.getCostB().isEmpty() || !DisabledContent.disabled(offer.getCostB().getItem()))) {
                    return offer;
                }
            }
            return null;
        }
    }

    private static String cell(Map<String, String> row, String column) {
        return row.getOrDefault(column, "").strip();
    }

    private static int whole(Map<String, String> row, String column, int fallback) {
        try {
            return Integer.parseInt(cell(row, column));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
