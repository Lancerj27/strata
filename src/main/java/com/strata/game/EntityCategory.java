package com.strata.game;

import com.strata.Strata;
import com.strata.model.SpawnCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** The spawn category of an entity. */
public final class EntityCategory {

    private EntityCategory() {}

    /** The spawn category of an entity id, or MONSTER when the registry does not know the id. */
    public static SpawnCategory of(String entityId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(entityId == null ? "" : entityId.strip());
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                return forVanillaCategory(
                        BuiltInRegistries.ENTITY_TYPE.get(id).getCategory().name());
            }
        } catch (RuntimeException e) {
            // This value is WRITTEN into a row, so a silent fall-through is how a creature ends up filed as a
            // monster with nothing recorded.
            Strata.LOGGER.warn("Could not read the spawn category for '{}' - using "
                    + "monster: {}", entityId, e.toString());
        }
        return SpawnCategory.MONSTER;
    }

    /** The mapping itself, from a MobCategory enum name. */
    public static SpawnCategory forVanillaCategory(String mobCategoryName) {
        if ("MISC".equalsIgnoreCase(mobCategoryName == null ? "" : mobCategoryName.strip())) {
            return SpawnCategory.CREATURE;
        }
        return SpawnCategory.parse(mobCategoryName);
    }
}
