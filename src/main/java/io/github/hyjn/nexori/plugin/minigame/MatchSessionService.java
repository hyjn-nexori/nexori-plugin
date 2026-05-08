package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MatchSessionService {

    public static final long PREPARED_SESSION_GRACE_MS = 5L * 60L * 1000L;
    public static final long HANDOFF_RECORD_RETENTION_MS = 6L * 60L * 60L * 1000L;

    private final MatchSessionStore store;
    private final Map<String, MatchSessionState> sessionsById = new LinkedHashMap<>();

    public MatchSessionService(@Nonnull MatchSessionStore store) throws IOException {
        this.store = store;
        long now = System.currentTimeMillis();
        for (MatchSessionState session : store.loadOrCreate()) {
            MatchSessionState normalized = session.normalized();
            if (!normalized.isExpired(now)) {
                sessionsById.put(normalized.matchId(), normalized);
            }
        }
        persist();
    }

    @Nonnull
    public synchronized List<MatchSessionState> list() {
        return sessionsById.values().stream()
            .sorted(Comparator.comparing(MatchSessionState::matchId))
            .toList();
    }

    @Nonnull
    public synchronized Optional<MatchSessionState> find(@Nonnull String rawMatchId) {
        if (rawMatchId == null || rawMatchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionsById.get(rawMatchId.trim().toLowerCase()));
    }

    @Nonnull
    public synchronized MatchSessionState upsert(@Nonnull MatchSessionState state) throws IOException {
        MatchSessionState normalized = state.normalized();
        sessionsById.put(normalized.matchId(), normalized);
        persist();
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawMatchId) throws IOException {
        String matchId = normalizeMatchId(rawMatchId);
        MatchSessionState removed = sessionsById.remove(matchId);
        persist();
        return removed != null;
    }

    @Nonnull
    public synchronized ReturnResult registerReturn(
        @Nonnull String rawMatchId,
        @Nonnull String rawQueueId,
        @Nonnull String rawOriginContextId,
        @Nonnull String rawSourceArenaId,
        @Nonnull String rawReturnReason,
        @Nonnull UUID playerUuid
    ) throws IOException {
        String matchId = normalizeMatchId(rawMatchId);
        String queueId = QueueDefinition.normalizeId(rawQueueId);
        String originContextId = SourceContextId.normalizeId(rawOriginContextId);
        String sourceArenaId = ArenaDefinition.normalizeId(rawSourceArenaId);
        String returnReason = normalizeOptional(rawReturnReason, "MATCH_ENDED");

        MatchSessionState session = sessionsById.get(matchId);
        if (session == null) {
            return ReturnResult.missing(matchId);
        }
        if (!session.queueId().equals(queueId)) {
            return ReturnResult.invalid(session, "Queue mismatch on return: expected '" + session.queueId() + "' but got '" + queueId + "'.");
        }
        if (!session.originLobbyId().equals(originContextId)) {
            return ReturnResult.invalid(session, "Origin source context mismatch on return: expected '" + session.originLobbyId() + "' but got '" + originContextId + "'.");
        }
        if (!session.arenaId().equals(sourceArenaId)) {
            return ReturnResult.invalid(session, "Source arena mismatch on return: expected '" + session.arenaId() + "' but got '" + sourceArenaId + "'.");
        }
        if (!session.expectsPlayer(playerUuid)) {
            return ReturnResult.invalid(session, "Player is not part of expected match roster.");
        }
        if (session.hasObservedPlayer(playerUuid)) {
            return ReturnResult.alreadyReturned(session, returnReason);
        }

        MatchSessionState updated = session.withObservedPlayer(playerUuid, System.currentTimeMillis());
        sessionsById.put(updated.matchId(), updated);
        persist();
        if (updated.allLaunchedPlayersObserved()) {
            return ReturnResult.completed(updated, returnReason);
        }
        return ReturnResult.returned(updated, returnReason);
    }

    public synchronized int pruneExpired(long nowEpochMs) throws IOException {
        List<String> expiredMatchIds = sessionsById.values().stream()
            .filter(session -> session.isExpired(nowEpochMs))
            .map(MatchSessionState::matchId)
            .toList();
        if (expiredMatchIds.isEmpty()) {
            return 0;
        }
        for (String matchId : expiredMatchIds) {
            sessionsById.remove(matchId);
        }
        persist();
        return expiredMatchIds.size();
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(sessionsById.values()));
    }

    @Nonnull
    private static String normalizeMatchId(@Nonnull String rawMatchId) {
        String normalized = NexoriMatchIds.normalizeRequiredMatchId(rawMatchId, "Match session id cannot be blank.");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Match session id cannot be blank.");
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim().toLowerCase();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    public enum ReturnOutcome {
        RETURNED,
        COMPLETED,
        ALREADY_RETURNED,
        MISSING,
        INVALID
    }

    public record ReturnResult(
        ReturnOutcome outcome,
        String matchId,
        String returnReason,
        String errorMessage,
        MatchSessionState session
    ) {

        @Nonnull
        public static ReturnResult returned(@Nonnull MatchSessionState session, @Nonnull String returnReason) {
            return new ReturnResult(ReturnOutcome.RETURNED, session.matchId(), returnReason, "", session);
        }

        @Nonnull
        public static ReturnResult completed(@Nonnull MatchSessionState session, @Nonnull String returnReason) {
            return new ReturnResult(ReturnOutcome.COMPLETED, session.matchId(), returnReason, "", session);
        }

        @Nonnull
        public static ReturnResult alreadyReturned(@Nonnull MatchSessionState session, @Nonnull String returnReason) {
            return new ReturnResult(ReturnOutcome.ALREADY_RETURNED, session.matchId(), returnReason, "", session);
        }

        @Nonnull
        public static ReturnResult missing(@Nonnull String matchId) {
            return new ReturnResult(ReturnOutcome.MISSING, normalizeMatchId(matchId), "", "", null);
        }

        @Nonnull
        public static ReturnResult invalid(@Nonnull MatchSessionState session, @Nonnull String errorMessage) {
            return new ReturnResult(ReturnOutcome.INVALID, session.matchId(), "", errorMessage, session);
        }
    }

}
