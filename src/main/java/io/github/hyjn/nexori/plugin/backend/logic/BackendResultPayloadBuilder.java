package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.BackendResultStore;
import io.github.hyjn.nexori.plugin.backend.payload.BackendResultPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendResultPlayerPayload;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds backend result-reporting payloads from persisted result records.
 */
public final class BackendResultPayloadBuilder {

    private final Gson gson = new GsonBuilder().create();

    @Nonnull
    public BackendResultPayloadBuildResult build(
        int schemaVersion,
        long sentAtEpochMs,
        @Nonnull String reportingServerId,
        @Nonnull BackendResultStore.BackendResultRecord result
    ) {
        BackendResultPayload payload = buildPayload(schemaVersion, sentAtEpochMs, reportingServerId, result);
        return new BackendResultPayloadBuildResult(payload, gson.toJson(payload));
    }

    @Nonnull
    public BackendResultPayload buildPayload(
        int schemaVersion,
        long sentAtEpochMs,
        @Nonnull String reportingServerId,
        @Nonnull BackendResultStore.BackendResultRecord result
    ) {
        List<BackendResultPlayerPayload> players = new ArrayList<>();
        for (BackendResultStore.BackendResultPlayerRecord player : result.players()) {
            players.add(new BackendResultPlayerPayload(player.playerUuid(), player.outcome(), player.reason()));
        }
        return new BackendResultPayload(
            schemaVersion,
            result.resultId(),
            sentAtEpochMs,
            reportingServerId,
            result.localMatchId(),
            result.externalMatchId(),
            result.assignmentId(),
            result.assignmentIdsByPlayerUuid(),
            result.queueId(),
            result.arenaId(),
            result.rulesEngineId(),
            List.copyOf(players),
            result.reason(),
            result.metadata(),
            result.customData() == null ? new JsonObject() : result.customData().deepCopy(),
            result.endedAtEpochMs()
        );
    }
}
