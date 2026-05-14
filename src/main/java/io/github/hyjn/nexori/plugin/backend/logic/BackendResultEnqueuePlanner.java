package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.BackendMatchmakingConfig;
import io.github.hyjn.nexori.plugin.backend.BackendResultStore;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure planner for constructing backend result records before store enqueue.
 */
public final class BackendResultEnqueuePlanner {

    @Nonnull
    public BackendResultEnqueuePlan plan(
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull ArenaActiveMatch match,
        @Nonnull List<ArenaMatchService.SubmitMatchPlayerResult> players,
        @Nonnull Map<String, String> metadata,
        @Nonnull JsonObject customData,
        @Nonnull String reason,
        @Nonnull String payloadHash,
        @Nonnull String resultId,
        long endedAtEpochMs
    ) {
        BackendMatchmakingConfig normalized = config.normalized();
        if (!normalized.resultReportingEnabled()) {
            return BackendResultEnqueuePlan.disabled("Result reporting is disabled.");
        }
        if (normalized.baseUrl().isBlank() || normalized.serverToken().isBlank()) {
            return BackendResultEnqueuePlan.disabled("Result reporting requires baseUrl and serverToken.");
        }
        if (match.externalMatchId().isBlank()) {
            return BackendResultEnqueuePlan.externalMatchMissing("Match has no externalMatchId.");
        }

        return BackendResultEnqueuePlan.storePending(new BackendResultStore.BackendResultRecord(
            resultId,
            match.matchId(),
            match.externalMatchId(),
            match.assignmentId(),
            toStoreAssignmentIdsByPlayerUuid(match),
            match.queueId(),
            match.arenaId(),
            match.rulesEngineId(),
            toStorePlayers(players),
            reason,
            metadata,
            customData.deepCopy(),
            payloadHash,
            BackendResultStore.BackendResultStatus.PENDING.name(),
            0,
            endedAtEpochMs,
            endedAtEpochMs,
            endedAtEpochMs,
            0L,
            0L,
            "",
            "",
            0,
            ""
        ));
    }

    @Nonnull
    private List<BackendResultStore.BackendResultPlayerRecord> toStorePlayers(
        @Nonnull List<ArenaMatchService.SubmitMatchPlayerResult> players
    ) {
        List<BackendResultStore.BackendResultPlayerRecord> records = new ArrayList<>();
        for (ArenaMatchService.SubmitMatchPlayerResult player : players) {
            records.add(new BackendResultStore.BackendResultPlayerRecord(
                player.playerUuid().toString(),
                player.backendOutcome(),
                player.reason()
            ));
        }
        return List.copyOf(records);
    }

    @Nonnull
    private Map<String, String> toStoreAssignmentIdsByPlayerUuid(@Nonnull ArenaActiveMatch match) {
        if (match.assignmentIdsByPlayerUuid().isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        match.assignmentIdsByPlayerUuid().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    return;
                }
                normalized.put(entry.getKey().toString().toLowerCase(), entry.getValue().trim());
            });
        return Collections.unmodifiableMap(normalized);
    }
}
