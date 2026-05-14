package io.github.hyjn.nexori.plugin.catalogsync.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncRequestPayload;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CatalogSyncRequestBuilder {

    private final Gson gson = new GsonBuilder().create();

    @Nonnull
    public CatalogEntitySyncRequestPayload build(@Nonnull CatalogSyncRequestBuildInput input) {
        if (input.entityType() == CatalogSyncEntityType.GAME) {
            ArenaDefinition arena = input.arena().normalized();
            return new CatalogEntitySyncRequestPayload(
                input.schemaVersion(),
                input.operationId(),
                input.sourceServerId(),
                input.targetServerId(),
                CatalogSyncEntityType.GAME,
                arena.arenaId(),
                hashArena(arena),
                input.sentAtEpochMs(),
                arena,
                null
            );
        }

        QueueDefinition queue = input.queue().normalized();
        return new CatalogEntitySyncRequestPayload(
            input.schemaVersion(),
            input.operationId(),
            input.sourceServerId(),
            input.targetServerId(),
            CatalogSyncEntityType.QUEUE,
            queue.queueId(),
            hashQueue(queue),
            input.sentAtEpochMs(),
            null,
            queue
        );
    }

    @Nonnull
    public String hashArena(@Nonnull ArenaDefinition arena) {
        return sha256Hex(gson.toJson(arena.normalized()));
    }

    @Nonnull
    public String hashQueue(@Nonnull QueueDefinition queue) {
        return sha256Hex(gson.toJson(queue.normalized()));
    }

    @Nonnull
    private static String sha256Hex(@Nonnull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
