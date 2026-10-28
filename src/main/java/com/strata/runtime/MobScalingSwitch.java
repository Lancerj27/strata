package com.strata.runtime;

import com.strata.data.CsvTable;
import com.strata.data.Schema;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** A MOB'S OWN SCALING SWITCH. */
public final class MobScalingSwitch {

    private MobScalingSwitch() {
    }

    public static final String ON = MobSwitches.ON;
    public static final String OFF = MobSwitches.OFF;

    /** "on" or "off" by ScopeIndex key; Default rows are not held. */
    private static volatile Map<String, String> byInstance = Map.of();

    /** Start levels by ScopeIndex key, as typed; blank rows are not held. */
    private static volatile Map<String, String> startLevels = Map.of();

    /** Passive switches by ScopeIndex key, "on" or "off" only. */
    private static volatile Map<String, String> passive = Map.of();

    /** Conversion switches by ScopeIndex key, "on" or "off" only. */
    private static volatile Map<String, String> converts = Map.of();

    /** Reads the switches from variants.csv. */
    public static void load(Path variantsFile) {
        List<Map<String, String>> rows = CsvTable.read(variantsFile);
        byInstance = index(rows);
        startLevels = startLevelIndex(rows);
        passive = MobSwitches.index(rows, Schema.Variants.PASSIVE);
        converts = MobSwitches.index(rows, Schema.Variants.CONVERTS);
    }

    /** Start levels by ScopeIndex key. */
    public static Map<String, String> startLevelIndex(List<Map<String, String>> variantRows) {
        Map<String, String> out = new java.util.HashMap<>();
        for (Map<String, String> row : variantRows) {
            String value = row.getOrDefault(Schema.Variants.START_LEVEL, "").strip();
            String name = row.getOrDefault(Schema.Variants.NAME, "").strip();
            if (!value.isEmpty() && !name.isEmpty()) {
                out.put(ScopeIndex.key(row.getOrDefault(Schema.Variants.SCOPE, ""),
                        row.getOrDefault(Schema.Variants.SCOPE_ID, ""), name), value);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * A mob's own level in the scope it spawned in: biome, then tier, then global, the variant's before its
     * base mob's, the way its Scaling switch is found.
     */
    public static int startLevel(Map<String, String> index, String variant, String base, String tier, String biome) {
        String own = ScopeIndex.resolveOrBase(index, variant, base, tier, biome);
        int level = com.strata.data.Parse.integer(own, 1);
        return Math.max(1, level);
    }

    /** True when the mob's own Passive switch is On, found the way its Scaling switch is. */
    public static boolean passive(String variant, String base, String tier, String biome) {
        return Boolean.TRUE.equals(MobSwitches.own(passive, variant, base, tier, biome));
    }

    /** False when the mob's own Converts switch is Off, found the way its Scaling switch is. */
    public static boolean converts(String variant, String base, String tier, String biome) {
        return !Boolean.FALSE.equals(MobSwitches.own(converts, variant, base, tier, biome));
    }

    /** A MOB SET TO NOT CONVERT does not, last after every other mod. */
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onConvert(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre event) {
        if (converts.isEmpty() || event.getEntity().level().isClientSide()) {
            return;
        }
        var entity = event.getEntity();
        String variant = MobAbilities.variantOf(entity);
        if (variant.isEmpty()) {
            variant = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType())
                    .map(k -> com.strata.model.Variant.defaultNameFor(k.location().toString())).orElse("");
        }
        String packed = entity.getData(MobLevel.VARIANT_SCOPE);
        if (!converts(variant, VariantHandler.baseOf(variant), ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed))) {
            event.setCanceled(true);
            event.setConversionTimer(1200);
        }
    }

    /** As above, over the loaded rows. */
    public static int startLevel(String variant, String base, String tier, String biome) {
        return startLevel(startLevels, variant, base, tier, biome);
    }

    /** The switches by ScopeIndex key, "on" or "off" only. */
    public static Map<String, String> index(List<Map<String, String>> variantRows) {
        return MobSwitches.index(variantRows, Schema.Variants.SCALING);
    }

    /** "on", "off", or "" (Default) from a stored cell. */
    public static String normal(String cell) {
        return MobSwitches.normal(cell);
    }

    /**
     * Whether a mob scales: its own switch when set, anywhere up its chain, else the Scaling screen's switch.
     *
     * @param global the Scaling screen's Mob scaling switch
     * @param base the variant's base mob's default variant, or ""
     */
    public static boolean scales(Map<String, String> index, boolean global, String variant, String base,
                                 String tier, String biome) {
        Boolean own = own(index, variant, base, tier, biome);
        return own == null ? global : own;
    }

    /** As above, over the loaded switches. */
    public static boolean scales(boolean global, String variant, String base, String tier, String biome) {
        return scales(byInstance, global, variant, base, tier, biome);
    }

    /** The mob's own switch: true On, false Off, null Default. */
    public static Boolean own(Map<String, String> index, String variant, String base, String tier, String biome) {
        return MobSwitches.own(index, variant, base, tier, biome);
    }

    /** As above, over the loaded switches. */
    public static Boolean own(String variant, String base, String tier, String biome) {
        return own(byInstance, variant, base, tier, biome);
    }
}
