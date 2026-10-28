package com.strata.network;

import com.strata.Strata;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The messages Strata sends between a player's game and a server (SOLO TO SERVER). */
public final class StrataPayloads {

    private StrataPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Strata.MOD_ID, path));
    }

    /** A map of names to short text, as the codec writes it. */
    private static final StreamCodec<ByteBuf, Map<String, String>> TEXT_MAP =
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    /** The largest file an upload carries, in characters. */
    public static final int MAX_FILE_CHARS = com.strata.data.ProfileManager.MAX_FILE_CHARS;

    /**
     * HARD CAPS ON DECODE: an upload is read whole before the operator check, so the file
     * count and every name are bounded here, not only by ProfileManager#uploadProblem afterwards.
     */
    public static final int MAX_FILES = 64;
    public static final int MAX_NAME_CHARS = 64;

    /** A map of file names to file text. */
    private static final StreamCodec<ByteBuf, Map<String, String>> FILE_MAP =
            ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.stringUtf8(MAX_NAME_CHARS),
                    ByteBufCodecs.stringUtf8(MAX_FILE_CHARS), MAX_FILES);

    // ---- configuration phase: before registry sync ----

    /** One own-entity variant: its id and the base mob its type copies. */
    public record OwnEntity(String id, String base) {
        static final StreamCodec<ByteBuf, OwnEntity> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(128), OwnEntity::id,
                ByteBufCodecs.stringUtf8(256), OwnEntity::base,
                OwnEntity::new);
    }

    /** The server's own-entity variants, sent the moment configuration starts, BEFORE the registry sync. */
    public record OwnEntities(List<OwnEntity> entities) implements CustomPacketPayload {
        public static final Type<OwnEntities> TYPE = payloadType("own_entities");
        public static final StreamCodec<FriendlyByteBuf, OwnEntities> CODEC = StreamCodec.composite(
                OwnEntity.CODEC.apply(ByteBufCodecs.list(com.strata.data.ServerEntityCache.MAX)), OwnEntities::entities,
                OwnEntities::new);

        @Override
        public Type<OwnEntities> type() {
            return TYPE;
        }
    }

    // ---- play phase ----

    /**
     * What a player's game shows that only the server knows: the server's active profile, whether a
     * regenerate waits for the next start, the variant textures (display name to texture path), and the mobs'
     * models and textures (com.strata.runtime.MobModels#clientIndex).
     */
    public record ServerSync(String activeProfile, boolean regeneratePending,
                             Map<String, String> textures, Map<String, String> models)
            implements CustomPacketPayload {
        public static final Type<ServerSync> TYPE = payloadType("server_sync");
        public static final StreamCodec<FriendlyByteBuf, ServerSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ServerSync::activeProfile,
                ByteBufCodecs.BOOL, ServerSync::regeneratePending,
                TEXT_MAP, ServerSync::textures,
                TEXT_MAP, ServerSync::models,
                ServerSync::new);

        @Override
        public Type<ServerSync> type() {
            return TYPE;
        }
    }

    /** A profile sent from an admin's machine to the server: its name, and each file's name and text. */
    public record ProfileUpload(String name, Map<String, String> files) implements CustomPacketPayload {
        public static final Type<ProfileUpload> TYPE = payloadType("profile_upload");
        public static final StreamCodec<FriendlyByteBuf, ProfileUpload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_NAME_CHARS), ProfileUpload::name,
                FILE_MAP, ProfileUpload::files,
                ProfileUpload::new);

        @Override
        public Type<ProfileUpload> type() {
            return TYPE;
        }
    }

    /** The server's answer to an upload: did it land, and why not. */
    public record UploadResult(boolean ok, String message) implements CustomPacketPayload {
        public static final Type<UploadResult> TYPE = payloadType("upload_result");
        public static final StreamCodec<FriendlyByteBuf, UploadResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, UploadResult::ok,
                ByteBufCodecs.STRING_UTF8, UploadResult::message,
                UploadResult::new);

        @Override
        public Type<UploadResult> type() {
            return TYPE;
        }
    }

    /** An operator's "Restart now". */
    public record RestartRequest() implements CustomPacketPayload {
        public static final Type<RestartRequest> TYPE = payloadType("restart_request");
        public static final StreamCodec<FriendlyByteBuf, RestartRequest> CODEC =
                StreamCodec.unit(new RestartRequest());

        @Override
        public Type<RestartRequest> type() {
            return TYPE;
        }
    }

    /** "What AI does this mob have by default?". */
    public record GoalsRequest(String entity) implements CustomPacketPayload {
        public static final Type<GoalsRequest> TYPE = payloadType("goals_request");
        public static final StreamCodec<FriendlyByteBuf, GoalsRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, GoalsRequest::entity,
                GoalsRequest::new);

        @Override
        public Type<GoalsRequest> type() {
            return TYPE;
        }
    }

    /** The answer: the mob's default goals, as simple class names. */
    /** An effect shown on the player's HUD with another effect's icon. */
    public record HudIcon(String effect, String icon, int ticks) implements CustomPacketPayload {
        public static final Type<HudIcon> TYPE = payloadType("hud_icon");
        public static final StreamCodec<FriendlyByteBuf, HudIcon> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, HudIcon::effect,
                ByteBufCodecs.STRING_UTF8, HudIcon::icon,
                ByteBufCodecs.VAR_INT, HudIcon::ticks,
                HudIcon::new);

        @Override
        public Type<HudIcon> type() {
            return TYPE;
        }
    }

    /**
     * A mob's stacked property values, as group.column to value: the server works them out from the tier and
     * biome the mob spawned in, which a client cannot know.
     */
    public record MobProps(int entity, Map<String, String> values) implements CustomPacketPayload {
        public static final Type<MobProps> TYPE = payloadType("mob_props");
        public static final StreamCodec<FriendlyByteBuf, MobProps> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MobProps::entity,
                ByteBufCodecs.map(java.util.LinkedHashMap::new, ByteBufCodecs.stringUtf8(128),
                        ByteBufCodecs.stringUtf8(1024), 256), MobProps::values,
                MobProps::new);

        @Override
        public Type<MobProps> type() {
            return TYPE;
        }
    }

    /** An arrow of the designer's and the texture it is drawn with (Projectiles#onStartTracking). */
    public record ProjectileLook(int entity, String texture) implements CustomPacketPayload {
        public static final Type<ProjectileLook> TYPE = payloadType("projectile_look");
        public static final StreamCodec<FriendlyByteBuf, ProjectileLook> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ProjectileLook::entity,
                ByteBufCodecs.stringUtf8(256), ProjectileLook::texture,
                ProjectileLook::new);

        @Override
        public Type<ProjectileLook> type() {
            return TYPE;
        }
    }

    public record GoalsReply(String entity, List<String> goals) implements CustomPacketPayload {
        public static final Type<GoalsReply> TYPE = payloadType("goals_reply");
        public static final StreamCodec<FriendlyByteBuf, GoalsReply> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, GoalsReply::entity,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GoalsReply::goals,
                GoalsReply::new);

        @Override
        public Type<GoalsReply> type() {
            return TYPE;
        }
    }

    // ---- custom textures and sounds: the server serves its library ---- THE PROTOCOL, in both phases
    // (CONFIGURATION before play, and PLAY for a live change).

    private static final StreamCodec<ByteBuf, String> SHORT = ByteBufCodecs.stringUtf8(64);

    /** One entry of a manifest, as it travels. */
    public static final StreamCodec<ByteBuf, com.strata.domain.AssetSync.Entry> ASSET_ENTRY = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), com.strata.domain.AssetSync.Entry::kind,
            ByteBufCodecs.stringUtf8(16), com.strata.domain.AssetSync.Entry::category,
            SHORT, com.strata.domain.AssetSync.Entry::name,
            SHORT, com.strata.domain.AssetSync.Entry::sha256,
            ByteBufCodecs.VAR_INT, com.strata.domain.AssetSync.Entry::size,
            ByteBufCodecs.stringUtf8(256), com.strata.domain.AssetSync.Entry::subtitle,
            com.strata.domain.AssetSync.Entry::new);

    /** @param replacements the game sounds and textures the library's files replace, one CSV line each */
    public record AssetManifest(List<com.strata.domain.AssetSync.Entry> entries, List<String> replacements)
            implements CustomPacketPayload {
        public static final Type<AssetManifest> TYPE = payloadType("asset_manifest");
        public static final StreamCodec<FriendlyByteBuf, AssetManifest> CODEC = StreamCodec.composite(
                ASSET_ENTRY.apply(ByteBufCodecs.list()), AssetManifest::entries,
                ByteBufCodecs.stringUtf8(1024).apply(ByteBufCodecs.list(com.strata.domain.CustomAssetsOps.MAX_REPLACEMENTS)),
                AssetManifest::replacements,
                AssetManifest::new);

        @Override
        public Type<AssetManifest> type() {
            return TYPE;
        }
    }

    /** The client's answer to a manifest: whether it takes the server's files, and the hashes it lacks. */
    public record AssetNeed(boolean accept, List<String> hashes) implements CustomPacketPayload {
        public static final Type<AssetNeed> TYPE = payloadType("asset_need");
        public static final StreamCodec<FriendlyByteBuf, AssetNeed> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AssetNeed::accept,
                SHORT.apply(ByteBufCodecs.list()), AssetNeed::hashes,
                AssetNeed::new);

        @Override
        public Type<AssetNeed> type() {
            return TYPE;
        }
    }

    public record AssetChunk(String sha256, int index, byte[] data) implements CustomPacketPayload {
        public static final Type<AssetChunk> TYPE = payloadType("asset_chunk");
        public static final StreamCodec<FriendlyByteBuf, AssetChunk> CODEC = StreamCodec.composite(
                SHORT, AssetChunk::sha256,
                ByteBufCodecs.VAR_INT, AssetChunk::index,
                ByteBufCodecs.byteArray(com.strata.domain.AssetRules.CHUNK_BYTES), AssetChunk::data,
                AssetChunk::new);

        @Override
        public Type<AssetChunk> type() {
            return TYPE;
        }
    }

    /** state: 0 this chunk is in (send the next), 1 the file is checked and stored, 2 the file is dropped. */
    public record AssetAck(String sha256, int index, int state) implements CustomPacketPayload {
        public static final int CHUNK = 0;
        public static final int STORED = 1;
        public static final int DROPPED = 2;
        public static final Type<AssetAck> TYPE = payloadType("asset_ack");
        public static final StreamCodec<FriendlyByteBuf, AssetAck> CODEC = StreamCodec.composite(
                SHORT, AssetAck::sha256,
                ByteBufCodecs.VAR_INT, AssetAck::index,
                ByteBufCodecs.VAR_INT, AssetAck::state,
                AssetAck::new);

        @Override
        public Type<AssetAck> type() {
            return TYPE;
        }
    }

    public record AssetUploadBegin(String kind, String category, String name, String subtitle, int size,
                                   String sha256, boolean replace) implements CustomPacketPayload {
        public static final Type<AssetUploadBegin> TYPE = payloadType("asset_upload_begin");
        public static final StreamCodec<FriendlyByteBuf, AssetUploadBegin> CODEC = StreamCodec.of(
                (buf, v) -> {
                    ByteBufCodecs.stringUtf8(16).encode(buf, v.kind());
                    ByteBufCodecs.stringUtf8(16).encode(buf, v.category());
                    SHORT.encode(buf, v.name());
                    ByteBufCodecs.stringUtf8(256).encode(buf, v.subtitle());
                    ByteBufCodecs.VAR_INT.encode(buf, v.size());
                    SHORT.encode(buf, v.sha256());
                    ByteBufCodecs.BOOL.encode(buf, v.replace());
                },
                buf -> new AssetUploadBegin(ByteBufCodecs.stringUtf8(16).decode(buf),
                        ByteBufCodecs.stringUtf8(16).decode(buf), SHORT.decode(buf),
                        ByteBufCodecs.stringUtf8(256).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        SHORT.decode(buf), ByteBufCodecs.BOOL.decode(buf)));

        @Override
        public Type<AssetUploadBegin> type() {
            return TYPE;
        }
    }

    public record AssetUploadChunk(int index, byte[] data) implements CustomPacketPayload {
        public static final Type<AssetUploadChunk> TYPE = payloadType("asset_upload_chunk");
        public static final StreamCodec<FriendlyByteBuf, AssetUploadChunk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AssetUploadChunk::index,
                ByteBufCodecs.byteArray(com.strata.domain.AssetRules.UPLOAD_CHUNK_BYTES), AssetUploadChunk::data,
                AssetUploadChunk::new);

        @Override
        public Type<AssetUploadChunk> type() {
            return TYPE;
        }
    }

    /** The server's answer to an upload's start or chunk: send chunk {@code next}, or (ok false) it is refused. */
    public record AssetUploadAck(int next, boolean ok, String message) implements CustomPacketPayload {
        public static final Type<AssetUploadAck> TYPE = payloadType("asset_upload_ack");
        public static final StreamCodec<FriendlyByteBuf, AssetUploadAck> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AssetUploadAck::next,
                ByteBufCodecs.BOOL, AssetUploadAck::ok,
                ByteBufCodecs.stringUtf8(256), AssetUploadAck::message,
                AssetUploadAck::new);

        @Override
        public Type<AssetUploadAck> type() {
            return TYPE;
        }
    }
}
