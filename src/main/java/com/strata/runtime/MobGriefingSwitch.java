package com.strata.runtime;

import com.strata.data.CsvTable;
import com.strata.data.Schema;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** A MOB'S OWN MOB GRIEFING SWITCH. */
public final class MobGriefingSwitch {

    /** "on" or "off" by ScopeIndex key; Default rows are not held. */
    private static volatile Map<String, String> byInstance = Map.of();

    /** Reads the switches from variants.csv. */
    public static void load(Path variantsFile) {
        byInstance = index(CsvTable.read(variantsFile));
    }

    /** The switches by ScopeIndex key, "on" or "off" only. */
    public static Map<String, String> index(List<Map<String, String>> variantRows) {
        return MobSwitches.index(variantRows, Schema.Variants.MOB_GRIEFING);
    }

    /**
     * Whether a mob may grief: its own switch when set, anywhere up its chain, else the game's mobGriefing
     * rule.
     *
     * @param gameRule the game's mobGriefing rule
     * @param base the variant's base mob's default variant, or ""
     */
    public static boolean canGrief(Map<String, String> index, boolean gameRule, String variant, String base,
                                   String tier, String biome) {
        Boolean own = MobSwitches.own(index, variant, base, tier, biome);
        return own == null ? gameRule : own;
    }

    /** The game asks whether a mob may change blocks. */
    // LAST, so a data layer has the final word over other mods.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void onGrief(EntityMobGriefingEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        // A plain mob's settings live on its default variant (Variant#defaultNameFor).
        String variant = mob.getData(MobLevel.VARIANT_ID);
        if (variant == null || variant.isEmpty()) {
            variant = com.strata.model.Variant.defaultNameFor(BuiltInRegistries.ENTITY_TYPE.getResourceKey(mob.getType())
                    .map(k -> k.location().toString()).orElse(""));
        }
        String packed = mob.getData(MobLevel.VARIANT_SCOPE);
        event.setCanGrief(canGrief(byInstance, event.canGrief(), variant, VariantHandler.baseOf(variant),
                ScopeIndex.tierOf(packed), ScopeIndex.biomeOf(packed)));
    }
}
