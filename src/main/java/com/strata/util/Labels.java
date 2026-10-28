package com.strata.util;

import com.strata.model.Source;
import com.strata.model.SpawnCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class Labels {

    private Labels() {}

    public static String pretty(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        String path = parsed == null ? id : parsed.getPath();
        StringBuilder sb = new StringBuilder();
        for (String word : path.replace('_', ' ').split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(sb.isEmpty() ? "" : " ")
                    .append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return sb.toString();
    }

    public static String pretty(ResourceLocation id) {
        return id == null ? "" : pretty(id.toString());
    }

    public static String creature(String entityId) {
        if (entityId == null) return "";
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) return entityId;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return pretty(id);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type.getDescription().getString();
    }

    /** Vanilla's catch-all bucket. */
    private static final String GENERIC_BUCKET = "generic.";

    /** An attribute's display name, with NOTHING MEANINGFUL DROPPED. */
    public static String attribute(String attributeId) {
        if (attributeId == null || attributeId.isBlank()) {
            return attributeId == null ? "" : attributeId;
        }
        String id = attributeId.strip();
        String namespace = "";
        String path = id;
        int colon = id.indexOf(':');
        if (colon >= 0) {
            namespace = id.substring(0, colon);
            path = id.substring(colon + 1);
        }
        if (path.startsWith(GENERIC_BUCKET)) {
            path = path.substring(GENERIC_BUCKET.length());
        }
        String pretty = titleCase(path.replace('.', ' ').replace('_', ' '));
        if (pretty.isEmpty()) {
            return id;
        }
        if (!namespace.isEmpty() && !"minecraft".equals(namespace)) {
            return titleCase(namespace.replace('_', ' ')) + ": " + pretty;
        }
        return pretty;
    }

    private static String titleCase(String words) {
        StringBuilder sb = new StringBuilder();
        for (String word : words.split(" ")) {
            if (word.isEmpty()) continue;
            sb.append(sb.isEmpty() ? "" : " ")
                    .append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return sb.toString();
    }

    /** A block's name in the game's words, or its id. */
    public static String blockName(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return blockId;
        return BuiltInRegistries.BLOCK.get(id).getName().getString();
    }

    /**
     * The stack a recipe cell draws: the item's, or for "#tag" the tag's first item, else empty (a blank or
     * unknown id, or a tag whose items are not known: no world loaded).
     */
    public static net.minecraft.world.item.ItemStack itemIcon(String id) {
        if (id != null && id.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
            if (tag == null) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            var bound = BuiltInRegistries.ITEM.getTag(
                            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))
                    .flatMap(set -> set.stream().findFirst())
                    .map(h -> new net.minecraft.world.item.ItemStack(h.value()));
            if (bound.isPresent()) {
                return bound.get();
            }
            // No world yet, so tags are not bound: an item named like the tag stands in (#minecraft:planks
            // shows oak planks, #minecraft:logs an oak log).
            String path = tag.getPath();
            String one = path.endsWith("es") && !path.endsWith("planks") ? path.substring(0, path.length() - 2)
                    : path.endsWith("s") ? path.substring(0, path.length() - 1) : path;
            for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                if (key.getNamespace().equals(tag.getNamespace())
                        && (key.getPath().equals(path) || key.getPath().endsWith("_" + path)
                        || key.getPath().endsWith("_" + one) || key.getPath().equals(one))) {
                    return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(key));
                }
            }
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        ResourceLocation rl = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    public static String itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return itemId;
        if (!BuiltInRegistries.ITEM.containsKey(id)) return itemId;
        var item = BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? itemId : item.getDescription().getString();
    }

    /**
     * A VARIANT INSTANCE'S ID, qualified by the scope it lives in: {@code minecraft:tier:Grassland_Creeper}.
     *
     * @param entityId the base entity, e.g. {@code minecraft:creeper}
     * @param scope {@code tier}, {@code biome}, or blank/global
     * @param variantName the variant's name, blank for a plain mob
     */
    /**
     * A mob tool's title: the creature's name for the mob itself, or for its default variant, which is the
     * mob as the game makes it; a variant's id for a variant.
     */
    public static String ownerTitle(String entityId, String scopeId, String variantName) {
        String entity = entityId == null ? "" : entityId;
        if (variantName == null || variantName.isBlank()
                || variantName.equals(com.strata.model.Variant.defaultNameFor(entity))) {
            return creature(entity);
        }
        return instanceId(entity, scopeId, variantName);
    }

    public static String instanceId(String entityId, String scopeId, String variantName) {
        String variant = variantName == null ? "" : variantName.strip();
        String id = entityId == null ? "" : entityId.strip();
        if (variant.isEmpty()) {
            // Not a variant at all - the entity id already identifies it.
            return id;
        }
        // 1.
        String namespace = "minecraft";
        int colon = id.indexOf(':');
        if (colon > 0) {
            namespace = id.substring(0, colon);
        }
        // 3.
        String baseMob = colon >= 0 ? id.substring(colon + 1) : id;

        // 2.
        String where = scopeId == null ? "" : scopeId.strip();
        int whereColon = where.indexOf(':');
        if (whereColon >= 0) {
            where = where.substring(whereColon + 1);
        }

        StringBuilder sb = new StringBuilder(namespace).append(':');
        if (!where.isEmpty()) {
            sb.append(where).append(':');
        }
        return sb.append(baseMob).append(':').append(variant).toString();
    }

    /** The tier or biome a resolved row belongs to, blank when it is global. */
    public static String scopeIdOf(Source source, String listScopeId) {
        if (source == null) {
            return "";
        }
        return switch (source.layer()) {
            case TIER -> source.detail() == null ? "" : source.detail();
            case BIOME -> source.detail() != null && !source.detail().isBlank()
                    ? source.detail()
                    : listScopeId == null ? "" : listScopeId;
            default -> "";
        };
    }

    /**
     * A biome's display name: the game's own name when a language entry exists, else the id made readable.
     */
    public static String biome(String biomeId) {
        if (biomeId == null || biomeId.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(biomeId.strip());
        if (id == null) {
            return biomeId;
        }
        String key = "biome." + id.getNamespace() + "." + id.getPath();
        String name = Language.getInstance().has(key)
                ? Language.getInstance().getOrDefault(key) : pretty(id);
        return "minecraft".equals(id.getNamespace()) ? name
                : titleCase(id.getNamespace().replace('_', ' ')) + ": " + name;
    }

    /** Where a row lives, in words: "Global", "Tier: Tier 1", or "Biome: Plains". */
    public static String scope(String scope, String scopeId) {
        String s = scope == null ? "" : scope.strip();
        String id = scopeId == null ? "" : scopeId.strip();
        if (s.isEmpty() || "global".equalsIgnoreCase(s)) {
            return "Global";
        }
        if ("biome".equalsIgnoreCase(s)) {
            return "Biome: " + (id.isEmpty() ? "all" : biome(id));
        }
        return "Tier: " + (id.isEmpty() ? "all" : id);
    }

    /** An AI goal's class name in words: "MeleeAttackGoal" reads "melee attack". */
    public static String goal(String goalClass) {
        // One source for goal names: GoalGuide.
        if (goalClass == null || goalClass.isBlank()) {
            return "";
        }
        return com.strata.model.GoalGuide.of(goalClass).name();
    }

    public static String category(SpawnCategory cat) {
        return cat.word();
    }

    public static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return (long) value + ".0";
        }
        return String.valueOf(Math.round(value * 100000.0) / 100000.0);
    }
}
