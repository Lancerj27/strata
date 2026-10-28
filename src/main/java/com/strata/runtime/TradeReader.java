package com.strata.runtime;

import com.strata.model.Trade;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;

import java.lang.reflect.Field;
import java.util.Optional;

/** Reads a trade object as Strata's Trade. */
public final class TradeReader {

    private TradeReader() {
    }

    /** The trade a listing gives, as far as its fields say. */
    public static Trade read(VillagerTrades.ItemListing listing) {
        String kind = listing.getClass().getSimpleName();
        try {
            if (listing instanceof net.neoforged.neoforge.common.BasicItemListing) {
                ItemStack price = get(listing, "price", ItemStack.class);
                ItemStack price2 = get(listing, "price2", ItemStack.class);
                ItemStack sale = get(listing, "forSale", ItemStack.class);
                return Trade.plain(id(price.getItem()), price.getCount(), price2.isEmpty() ? "" : id(price2.getItem()),
                        price2.getCount(), id(sale.getItem()), sale.getCount(), num(listing, "maxTrades"),
                        num(listing, "xp"), flt(listing, "priceMult"));
            }
            switch (kind) {
                case "EmeraldForItems" -> {
                    ItemCost cost = get(listing, "itemStack", ItemCost.class);
                    return Trade.plain(id(cost.item().value()), cost.count(), "", 0, id(Items.EMERALD),
                            num(listing, "emeraldAmount"), num(listing, "maxUses"), num(listing, "villagerXp"),
                            flt(listing, "priceMultiplier"));
                }
                case "ItemsForEmeralds" -> {
                    ItemStack sale = get(listing, "itemStack", ItemStack.class);
                    Trade t = Trade.plain(id(Items.EMERALD), num(listing, "emeraldCost"), "", 0, id(sale.getItem()),
                            sale.getCount(), num(listing, "maxUses"), num(listing, "villagerXp"),
                            flt(listing, "priceMultiplier"));
                    return enchanted(listing) ? special(t, kind, "an enchanted " + words(sale.getItem())
                            + ", a random enchantment") : t;
                }
                case "ItemsAndEmeraldsToItems" -> {
                    ItemCost from = get(listing, "fromItem", ItemCost.class);
                    ItemStack to = get(listing, "toItem", ItemStack.class);
                    Trade t = Trade.plain(id(Items.EMERALD), num(listing, "emeraldCost"), id(from.item().value()),
                            from.count(), id(to.getItem()), to.getCount(), num(listing, "maxUses"),
                            num(listing, "villagerXp"), flt(listing, "priceMultiplier"));
                    return enchanted(listing) ? special(t, kind, "an enchanted " + words(to.getItem())
                            + ", a random enchantment") : t;
                }
                default -> {
                    return new Trade("", 0, "", 0, "", 0, numOr(listing, "maxUses", 0),
                            numOr(listing, "villagerXp", numOr(listing, "xp", 0)), 0f, kind, noteFor(kind));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new Trade("", 0, "", 0, "", 0, 0, 0, 0f, kind, "a trade Strata cannot read: " + kind);
        }
    }

    private static Trade special(Trade t, String kind, String note) {
        return new Trade(t.costA(), t.costACount(), t.costB(), t.costBCount(), t.result(), t.resultCount(),
                t.maxUses(), t.xp(), t.priceMultiplier(), kind, note);
    }

    /** What a random kind gives, in words. */
    public static String noteFor(String kind) {
        return switch (kind) {
            case "EnchantBookForEmeralds" -> "an enchanted book, a random enchantment";
            case "EnchantedItemForEmeralds" -> "an enchanted tool or armor, a random enchantment";
            case "TreasureMapForEmeralds" -> "a treasure map to a structure";
            case "DyedArmorForEmeralds" -> "dyed leather armor, a random color";
            case "TippedArrowForItemsAndEmeralds" -> "tipped arrows, a random potion";
            case "SuspiciousStewForEmerald" -> "suspicious stew with an effect";
            case "EmeraldsForVillagerTypeItem" -> "emeralds for an item that depends on the villager's biome type";
            default -> "a trade from a mod Strata cannot read: " + kind;
        };
    }

    private static boolean enchanted(Object listing) {
        try {
            Optional<?> provider = get(listing, "enchantmentProvider", Optional.class);
            return provider.isPresent();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getResourceKey(item).map(k -> k.location().toString()).orElse("");
    }

    private static String words(Item item) {
        String id = id(item);
        return id.substring(id.indexOf(':') + 1).replace('_', ' ');
    }

    private static int num(Object o, String name) throws ReflectiveOperationException {
        return ((Number) field(o.getClass(), name).get(o)).intValue();
    }

    private static int numOr(Object o, String name, int fallback) {
        try {
            return num(o, name);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static float flt(Object o, String name) throws ReflectiveOperationException {
        return ((Number) field(o.getClass(), name).get(o)).floatValue();
    }

    private static <T> T get(Object o, String name, Class<T> type) throws ReflectiveOperationException {
        return type.cast(field(o.getClass(), name).get(o));
    }

    private static Field field(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // the superclass may declare it
            }
        }
        throw new NoSuchFieldException(name);
    }
}
