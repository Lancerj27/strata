package com.strata.client;

import com.strata.model.ProjectilePreset;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.WeakHashMap;

/** THE TEXTURES THE DESIGNER'S ARROWS ARE DRAWN WITH. */
public final class ProjectileLooks {

    private ProjectileLooks() {
    }

    /** By arrow; weak, so a gone arrow is forgotten. */
    private static final Map<Entity, ResourceLocation> TEXTURES = new WeakHashMap<>();

    /** The server's word on an arrow (ClientNetwork). */
    public static void received(Entity arrow, String texture) {
        if (arrow == null) {
            return;
        }
        ResourceLocation id = ProjectilePreset.textureOk(texture) ? ResourceLocation.tryParse(texture.strip()) : null;
        if (id == null) {
            TEXTURES.remove(arrow);
        } else {
            TEXTURES.put(arrow, id);
        }
    }

    /** The texture an arrow is drawn with instead of its own, or null. */
    public static ResourceLocation texture(Entity arrow) {
        return TEXTURES.get(arrow);
    }

    /** Leaving a world: forget every arrow. */
    public static void clear() {
        TEXTURES.clear();
    }
}
