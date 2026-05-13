package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.payload.BackendMatchAdmissionStatePayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendMatchAdmissionStateResponsePayload;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class BackendMatchAdmissionStateReportingService {

    private static final int SCHEMA_VERSION = 1;
    private static final long AUTH_BACKOFF_MS = 30_000L;
    private static final int MAX_LOG_ENTRIES = 80;
    private static final int MAX_CLOSED_MATCH_IDS = 2048;
    private static final String CHANGE_REASON_MATCH_CREATED = "MATCH_CREATED";
    private static final String CHANGE_REASON_PLAYER_ARRIVED = "PLAYER_ARRIVED";
    private static final String CHANGE_REASON_PLACEMENT_COMPLETED = "PLACEMENT_COMPLETED";
    private static final String CHANGE_REASON_MATCH_STARTED = "MATCH_STARTED";
    private static final String CHANGE_REASON_ADMISSION_WINDOW_EXPIRED = "ADMISSION_WINDOW_EXPIRED";
    private static final String CHANGE_REASON_ADMISSION_CLOSED = "ADMISSION_CLOSED";
    private static final String CLOSE_REASON_WINDOW_EXPIRED = "BACKFILL_WINDOW_EXPIRED";
    private static final String CLOSE_REASON_NO_LONGER_ACCEPTING = "MATCH_NO_LONGER_ACCEPTING_PLAYERS";
    private static final String CLOSE_REASON_NOT_REPORTABLE = "MATCH_NOT_REPORTABLE";
    private static final String STATUS_PLACEMENT = "PLACEMENT";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final HytaleLogger logger;
    private BackendMatchmakingConfig config;
    private final ServerIdentity localIdentity;
    private final ArenaMatchService arenaMatchService;
    private HttpClient httpClient;
    private final Gson gson = new GsonBuilder().create();
    private final Queue<AdmissionHttpResult> queuedResults = new ConcurrentLinkedQueue<>();
    private final Map<String, MatchPublicationState> publicationStatesByMatchId = new LinkedHashMap<>();
    private final LinkedHashSet<String> closedAdmissionReportingMatchIds = new LinkedHashSet<>();
    private final Deque<String> recentLogEntries = new ArrayDeque<>();

    private long nextGlobalAttemptAtEpochMs;
    private String lastHealthStatus = "HEALTHY";

    public BackendMatchAdmissionStateReportingService(
        @Nonnull HytaleLogger logger,
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull ArenaMatchService arenaMatchService
    ) {
        this.logger = logger;
        this.localIdentity = localIdentity;
        this.arenaMatchService = arenaMatchService;
        this.config = config.normalized();
        logConfigNormalizationWarning(config, this.config);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.config.requestTimeoutMs()))
            .build();
    }

    public synchronized void updateConfig(@Nonnull BackendMatchmakingConfig updatedConfig) {
        BackendMatchmakingConfig normalized = updatedConfig.normalized();
        logConfigNormalizationWarning(updatedConfig, normalized);
        this.config = normalized;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.config.requestTimeoutMs()))
            .build();
        if (!this.config.matchStateReportingEnabled()) {
            this.nextGlobalAttemptAtEpochMs = 0L;
            this.lastHealthStatus = "HEALTHY";
        }
    }

    public synchronized boolean isMatchStateReportingEnabled() {
        return config.matchStateReportingEnabled() && config.isMatchStateReportingUsable();
    }

    public synchronized void markMatchDirty(@Nonnull String rawMatchId, @Nonnull String rawReason, long nowEpochMs) {
        if (!config.matchStateReportingEnabled()) {
            return;
        }
        String matchId = normalizeOptional(rawMatchId);
        if (matchId.isBlank()) {
            return;
        }
        if (closedAdmissionReportingMatchIds.contains(matchId)) {
            return;
        }
        ReportableMatchContext context = findReportableMatchContext(matchId);
        if (context == null) {
            return;
        }
        MatchPublicationState state = publicationStatesByMatchId.computeIfAbsent(matchId, ignored -> new MatchPublicationState());
        markDirty(state, normalizeOptional(rawReason), nowEpochMs);
    }

    public synchronized void markMatchDirtyImmediate(@Nonnull String rawMatchId, @Nonnull String rawReason, long nowEpochMs) {
        if (!config.matchStateReportingEnabled()) {
            return;
        }
        String matchId = normalizeOptional(rawMatchId);
        if (matchId.isBlank()) {
            return;
        }
        if (closedAdmissionReportingMatchIds.contains(matchId)) {
            return;
        }
        ReportableMatchContext context = findReportableMatchContext(matchId);
        if (context == null) {
            return;
        }
        MatchPublicationState state = publicationStatesByMatchId.computeIfAbsent(matchId, ignored -> new MatchPublicationState());
        markDirty(state, normalizeOptional(rawReason), nowEpochMs);
        if (state.dirty) {
            state.scheduledFlushAtEpochMs = nowEpochMs;
        }
    }

    public synchronized void flushMatchImmediately(@Nonnull String rawMatchId, @Nonnull String rawReason, long nowEpochMs) {
        if (!config.matchStateReportingEnabled()) {
            return;
        }
        String matchId = normalizeOptional(rawMatchId);
        if (matchId.isBlank()) {
            return;
        }
        if (closedAdmissionReportingMatchIds.contains(matchId)) {
            return;
        }
        ReportableMatchContext context = findReportableMatchContext(matchId);
        if (context == null) {
            return;
        }
        MatchPublicationState state = publicationStatesByMatchId.computeIfAbsent(matchId, ignored -> new MatchPublicationState());
        markDirty(state, normalizeOptional(rawReason), nowEpochMs);
        if (!state.dirty) {
            return;
        }

        BuiltAdmissionSnapshot snapshot = buildSnapshot(matchId, state, nowEpochMs);
        if (snapshot == null) {
            cleanupUnreportableState(matchId, "Admission reporting immediate flush dropped because the match is no longer reportable.");
            return;
        }
        state.dirty = false;
        state.firstDirtyAtEpochMs = 0L;
        state.lastDirtyAtEpochMs = 0L;
        state.scheduledFlushAtEpochMs = 0L;
        state.primaryChangeReason = "";
        state.coalescedReasons.clear();
        startHttpRequest(matchId, state, snapshot, nowEpochMs);
    }

    public synchronized void markAdmissionReservationConsumed(
        @Nonnull String rawMatchId,
        @Nonnull String rawReservationId,
        long nowEpochMs
    ) {
        String matchId = normalizeOptional(rawMatchId);
        String reservationId = normalizeOptional(rawReservationId);
        if (matchId.isBlank() || reservationId.isBlank()) {
            return;
        }
        MatchPublicationState state = publicationStatesByMatchId.computeIfAbsent(matchId, ignored -> new MatchPublicationState());
        if (state.pendingConsumedAdmissionReservationIds.add(reservationId)) {
            markDirty(state, CHANGE_REASON_PLAYER_ARRIVED, nowEpochMs);
        }
    }

    public synchronized void handleTick(long nowEpochMs) {
        drainQueuedResults(nowEpochMs);
        if (!config.matchStateReportingEnabled()) {
            publicationStatesByMatchId.clear();
            return;
        }
        evaluateOpenReportingWindows(nowEpochMs);
        processPublicationStates(nowEpochMs);
        cleanupIdleStates(nowEpochMs);
    }

    private void processPublicationStates(long nowEpochMs) {
        if (nowEpochMs < nextGlobalAttemptAtEpochMs) {
            return;
        }

        List<Map.Entry<String, MatchPublicationState>> states = new ArrayList<>(publicationStatesByMatchId.entrySet());
        states.sort(Map.Entry.comparingByKey());
        for (Map.Entry<String, MatchPublicationState> entry : states) {
            String matchId = entry.getKey();
            MatchPublicationState state = entry.getValue();
            if (state.inFlightSnapshot != null) {
                continue;
            }

            if (state.pendingRetrySnapshot != null) {
                if (state.dirty) {
                    state.pendingRetrySnapshot = null;
                } else if (nowEpochMs >= state.retryNotBeforeEpochMs) {
                    if (nowEpochMs > state.pendingRetrySnapshot.payload().stateExpiresAtEpochMs()) {
                        logEntry("Dropped expired admission snapshot retry for match " + matchId + ".");
                        state.pendingRetrySnapshot = null;
                        if (findReportableMatchContext(matchId) != null) {
                            markDirty(state, CHANGE_REASON_MATCH_STARTED, nowEpochMs);
                        }
                    } else {
                        startHttpRequest(matchId, state, state.pendingRetrySnapshot, nowEpochMs);
                        return;
                    }
                }
            }

            if (!state.dirty || nowEpochMs < state.scheduledFlushAtEpochMs) {
                continue;
            }

            BuiltAdmissionSnapshot snapshot = buildSnapshot(matchId, state, nowEpochMs);
            if (snapshot == null) {
                cleanupUnreportableState(matchId, "Admission reporting dropped because the match is no longer reportable.");
                continue;
            }
            state.dirty = false;
            state.firstDirtyAtEpochMs = 0L;
            state.lastDirtyAtEpochMs = 0L;
            state.scheduledFlushAtEpochMs = 0L;
            state.primaryChangeReason = "";
            state.coalescedReasons.clear();
            startHttpRequest(matchId, state, snapshot, nowEpochMs);
            return;
        }
    }

    private void evaluateOpenReportingWindows(long nowEpochMs) {
        List<String> matchIds = new ArrayList<>(publicationStatesByMatchId.keySet());
        for (String matchId : matchIds) {
            MatchPublicationState state = publicationStatesByMatchId.get(matchId);
            if (state == null || isClosurePending(state)) {
                continue;
            }
            ReportableMatchContext context = findReportableMatchContext(matchId);
            if (context == null) {
                if (state.inFlightSnapshot == null && state.pendingRetrySnapshot == null) {
                    cleanupUnreportableState(matchId, "Admission reporting cleared because the match stopped being reportable.");
                }
                continue;
            }
            AdmissionSnapshotView view = evaluateAdmissionState(context, nowEpochMs, state);
            if (view.admissionReportingClosed()) {
                String closeReason = normalizeOptional(view.primaryChangeReason());
                if (closeReason.isBlank()) {
                    closeReason = CHANGE_REASON_ADMISSION_CLOSED;
                }
                markDirty(state, closeReason, nowEpochMs);
            }
        }
    }

    private void cleanupIdleStates(long nowEpochMs) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, MatchPublicationState> entry : publicationStatesByMatchId.entrySet()) {
            MatchPublicationState state = entry.getValue();
            if (state.inFlightSnapshot != null || state.pendingRetrySnapshot != null || state.dirty) {
                continue;
            }
            ReportableMatchContext context = findReportableMatchContext(entry.getKey());
            if (context == null) {
                toRemove.add(entry.getKey());
                continue;
            }
            AdmissionSnapshotView view = evaluateAdmissionState(context, nowEpochMs, state);
            if (view.admissionReportingClosed() && closedAdmissionReportingMatchIds.contains(entry.getKey())) {
                toRemove.add(entry.getKey());
            }
        }
        for (String matchId : toRemove) {
            publicationStatesByMatchId.remove(matchId);
        }
    }

    private void startHttpRequest(
        @Nonnull String matchId,
        @Nonnull MatchPublicationState state,
        @Nonnull BuiltAdmissionSnapshot snapshot,
        long nowEpochMs
    ) {
        if (!config.isMatchStateReportingUsable()) {
            nextGlobalAttemptAtEpochMs = nowEpochMs + config.matchStateRetryIntervalMs();
            lastHealthStatus = "CONFIG_INVALID";
            markDirtyFromSnapshot(state, snapshot, nowEpochMs);
            logger.atWarning().log(
                "Backend match admission state reporting requires baseUrl and serverToken when enabled."
            );
            return;
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.matchStateUrl()))
                .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                .header("Authorization", "Bearer " + config.serverToken())
                .header("Content-Type", "application/json")
                .header("X-Nexori-Server-Id", localIdentity.serverId().toString())
                .header("X-Nexori-State-Update-Id", snapshot.payload().stateUpdateId())
                .header("X-Nexori-Sequence", Long.toString(snapshot.payload().admissionStateSequence()))
                .header("X-Nexori-Sent-At-Epoch-Ms", Long.toString(snapshot.payload().sentAtEpochMs()))
                .POST(HttpRequest.BodyPublishers.ofString(snapshot.body(), StandardCharsets.UTF_8))
                .build();
        } catch (IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log("Invalid backend match admission state URL.");
            state.pendingRetrySnapshot = null;
            state.inFlightSnapshot = null;
            nextGlobalAttemptAtEpochMs = nowEpochMs + config.matchStateRetryIntervalMs();
            markDirtyFromSnapshot(state, snapshot, nowEpochMs);
            return;
        }

        state.inFlightSnapshot = snapshot;
        state.pendingRetrySnapshot = null;

        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(config.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> queuedResults.add(toHttpResult(matchId, snapshot, response, throwable)));
        } catch (Throwable throwable) {
            queuedResults.add(AdmissionHttpResult.failure(matchId, snapshot, 0, throwable.getClass().getSimpleName(), "Admission state request could not be started."));
        }

        logEntry(
            "Sending admission state matchId=" + matchId
                + " sequence=" + snapshot.payload().admissionStateSequence()
                + " closed=" + snapshot.payload().admissionReportingClosed()
                + " open=" + snapshot.payload().admissionOpen()
                + " slots=" + snapshot.payload().availableAdmissionSlots()
        );
    }

    private void drainQueuedResults(long nowEpochMs) {
        AdmissionHttpResult result;
        while ((result = queuedResults.poll()) != null) {
            MatchPublicationState state = publicationStatesByMatchId.get(result.matchId());
            if (state == null) {
                continue;
            }
            BuiltAdmissionSnapshot inFlight = state.inFlightSnapshot;
            if (inFlight == null || !inFlight.payload().stateUpdateId().equals(result.snapshot().payload().stateUpdateId())) {
                continue;
            }
            state.inFlightSnapshot = null;

            int statusCode = result.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                lastHealthStatus = "HEALTHY";
                nextGlobalAttemptAtEpochMs = 0L;
                String backendStatus = parseBackendStatus(result.body());
                if (!isSemanticallyAcceptedBackendStatus(backendStatus)) {
                    markDirtyFromSnapshot(state, result.snapshot(), nowEpochMs);
                    state.scheduledFlushAtEpochMs = nowEpochMs;
                    logEntry(
                        "Admission state was not semantically acknowledged matchId=" + result.matchId()
                            + " sequence=" + result.snapshot().payload().admissionStateSequence()
                            + " status=" + backendStatus
                    );
                    continue;
                }
                ackConsumedAdmissionReservationIds(state, result.snapshot().consumedAdmissionReservationIdsIncluded());
                if (result.snapshot().payload().admissionReportingClosed()) {
                    rememberClosedMatch(result.matchId());
                    publicationStatesByMatchId.remove(result.matchId());
                } else if (state.dirty) {
                    state.scheduledFlushAtEpochMs = nowEpochMs;
                }
                logEntry(
                    "Admission state acknowledged matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " status=" + backendStatus
                );
                continue;
            }

            if (statusCode == 400 || statusCode == 422) {
                logEntry(
                    "Admission state permanent failure matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                        + " error=" + result.errorClass()
                );
                if (result.snapshot().payload().admissionReportingClosed()) {
                    rememberClosedMatch(result.matchId());
                    publicationStatesByMatchId.remove(result.matchId());
                } else if (findReportableMatchContext(result.matchId()) == null) {
                    publicationStatesByMatchId.remove(result.matchId());
                }
                continue;
            }

            if (statusCode == 401 || statusCode == 403) {
                lastHealthStatus = "AUTH_FAILED";
                nextGlobalAttemptAtEpochMs = nowEpochMs + AUTH_BACKOFF_MS;
                markDirtyFromSnapshot(state, result.snapshot(), nowEpochMs);
                logEntry(
                    "Admission state auth failure matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                );
                continue;
            }

            boolean retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500 || statusCode == 0;
            if (!retryable) {
                logEntry(
                    "Admission state unexpected failure matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                        + " error=" + result.errorClass()
                );
                if (state.dirty) {
                    state.scheduledFlushAtEpochMs = nowEpochMs;
                }
                continue;
            }

            nextGlobalAttemptAtEpochMs = nowEpochMs + config.matchStateRetryIntervalMs();
            if (state.dirty) {
                state.pendingRetrySnapshot = null;
                state.retryNotBeforeEpochMs = nextGlobalAttemptAtEpochMs;
                logEntry(
                    "Admission state dropped stale retry in favor of newer state matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                );
                continue;
            }

            if (nowEpochMs <= result.snapshot().payload().stateExpiresAtEpochMs()) {
                state.pendingRetrySnapshot = result.snapshot();
                state.retryNotBeforeEpochMs = nextGlobalAttemptAtEpochMs;
                logEntry(
                    "Admission state scheduled retry matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                );
            } else {
                logEntry(
                    "Admission state expired before retry matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                );
                if (findReportableMatchContext(result.matchId()) != null) {
                    markDirty(state, CHANGE_REASON_MATCH_STARTED, nowEpochMs);
                } else {
                    publicationStatesByMatchId.remove(result.matchId());
                }
            }
        }
    }

    private void rememberClosedMatch(@Nonnull String matchId) {
        String normalizedMatchId = normalizeOptional(matchId);
        if (normalizedMatchId.isBlank()) {
            return;
        }
        closedAdmissionReportingMatchIds.remove(normalizedMatchId);
        closedAdmissionReportingMatchIds.add(normalizedMatchId);
        while (closedAdmissionReportingMatchIds.size() > MAX_CLOSED_MATCH_IDS) {
            String oldest = closedAdmissionReportingMatchIds.iterator().next();
            closedAdmissionReportingMatchIds.remove(oldest);
        }
    }

    private void markDirty(@Nonnull MatchPublicationState state, @Nonnull String rawReason, long nowEpochMs) {
        String reason = normalizeOptional(rawReason);
        if (reason.isBlank()) {
            return;
        }
        state.dirty = true;
        if (state.firstDirtyAtEpochMs <= 0L) {
            state.firstDirtyAtEpochMs = nowEpochMs;
        }
        state.lastDirtyAtEpochMs = nowEpochMs;
        state.primaryChangeReason = reason;
        state.coalescedReasons.add(reason);
        long debounceTarget = nowEpochMs + config.matchStateDebounceMs();
        long maxWindowTarget = state.firstDirtyAtEpochMs + config.matchStateMaxCoalesceWindowMs();
        state.scheduledFlushAtEpochMs = Math.min(debounceTarget, maxWindowTarget);
    }

    private void ackConsumedAdmissionReservationIds(
        @Nonnull MatchPublicationState state,
        @Nonnull List<String> includedReservationIds
    ) {
        for (String reservationId : includedReservationIds) {
            state.pendingConsumedAdmissionReservationIds.remove(normalizeOptional(reservationId));
        }
    }

    private void markDirtyFromSnapshot(@Nonnull MatchPublicationState state, @Nonnull BuiltAdmissionSnapshot snapshot, long nowEpochMs) {
        List<String> reasons = snapshot.payload().coalescedChangeReasons().isEmpty()
            ? List.of(snapshot.payload().primaryChangeReason())
            : snapshot.payload().coalescedChangeReasons();
        for (String reason : reasons) {
            markDirty(state, reason, nowEpochMs);
        }
        if (state.scheduledFlushAtEpochMs <= 0L) {
            state.scheduledFlushAtEpochMs = nowEpochMs;
        }
    }

    private boolean isClosurePending(@Nonnull MatchPublicationState state) {
        return (state.inFlightSnapshot != null && state.inFlightSnapshot.payload().admissionReportingClosed())
            || (state.pendingRetrySnapshot != null && state.pendingRetrySnapshot.payload().admissionReportingClosed());
    }

    private BuiltAdmissionSnapshot buildSnapshot(@Nonnull String matchId, @Nonnull MatchPublicationState state, long nowEpochMs) {
        ReportableMatchContext context = findReportableMatchContext(matchId);
        if (context == null) {
            return null;
        }

        AdmissionSnapshotView view = evaluateAdmissionState(context, nowEpochMs, state);
        long sequence = ++state.lastAllocatedSequence;
        long sentAtEpochMs = nowEpochMs;
        long expiresAtEpochMs = nowEpochMs + config.matchStateStaleAfterMs();
        List<String> coalescedReasons = state.coalescedReasons.stream()
            .filter(reason -> reason != null && !reason.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        List<String> consumedAdmissionReservationIdsIncluded = state.pendingConsumedAdmissionReservationIds.stream()
            .filter(Objects::nonNull)
            .map(BackendMatchAdmissionStateReportingService::normalizeOptional)
            .filter(reservationId -> !reservationId.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        String primaryChangeReason = normalizeOptional(state.primaryChangeReason);
        if (primaryChangeReason.isBlank()) {
            primaryChangeReason = coalescedReasons.isEmpty() ? CHANGE_REASON_MATCH_STARTED : coalescedReasons.get(coalescedReasons.size() - 1);
        }
        BackendMatchAdmissionStatePayload payloadWithoutHash = new BackendMatchAdmissionStatePayload(
            SCHEMA_VERSION,
            UUID.randomUUID().toString().toLowerCase(),
            sequence,
            "",
            sentAtEpochMs,
            expiresAtEpochMs,
            localIdentity.serverId().toString(),
            context.match().matchId(),
            context.match().externalMatchId(),
            context.match().queueId(),
            context.match().arenaId(),
            context.match().backfillEnabled(),
            context.match().effectiveBackfillMode().id(),
            Math.max(context.match().backfillWindowSeconds(), 0),
            view.matchLifecycleStatus(),
            view.admissionOpen(),
            view.admissionOpenUntilEpochMs(),
            view.admissionCapacity(),
            view.admittedSlotCount(),
            view.availableAdmissionSlots(),
            view.initialRosterSize(),
            view.arrivedInitialPlayerCount(),
            view.unfilledInitialRosterCount(),
            consumedAdmissionReservationIdsIncluded,
            view.admissionReportingClosed(),
            view.admissionReportingCloseReason(),
            primaryChangeReason,
            coalescedReasons
        );
        String payloadHash = hashCanonicalPayload(payloadWithoutHash);
        BackendMatchAdmissionStatePayload payload = new BackendMatchAdmissionStatePayload(
            payloadWithoutHash.schemaVersion(),
            payloadWithoutHash.stateUpdateId(),
            payloadWithoutHash.admissionStateSequence(),
            payloadHash,
            payloadWithoutHash.sentAtEpochMs(),
            payloadWithoutHash.stateExpiresAtEpochMs(),
            payloadWithoutHash.reportingServerId(),
            payloadWithoutHash.matchId(),
            payloadWithoutHash.externalMatchId(),
            payloadWithoutHash.queueId(),
            payloadWithoutHash.arenaId(),
            payloadWithoutHash.backfillEnabled(),
            payloadWithoutHash.backfillMode(),
            payloadWithoutHash.backfillWindowSeconds(),
            payloadWithoutHash.matchLifecycleStatus(),
            payloadWithoutHash.admissionOpen(),
            payloadWithoutHash.admissionOpenUntilEpochMs(),
            payloadWithoutHash.admissionCapacity(),
            payloadWithoutHash.admittedSlotCount(),
            payloadWithoutHash.availableAdmissionSlots(),
            payloadWithoutHash.initialRosterSize(),
            payloadWithoutHash.arrivedInitialPlayerCount(),
            payloadWithoutHash.unfilledInitialRosterCount(),
            payloadWithoutHash.consumedAdmissionReservationIds(),
            payloadWithoutHash.admissionReportingClosed(),
            payloadWithoutHash.admissionReportingCloseReason(),
            payloadWithoutHash.primaryChangeReason(),
            payloadWithoutHash.coalescedChangeReasons()
        );
        return new BuiltAdmissionSnapshot(payload, gson.toJson(payload), List.copyOf(consumedAdmissionReservationIdsIncluded));
    }

    private AdmissionSnapshotView evaluateAdmissionState(
        @Nonnull ReportableMatchContext context,
        long nowEpochMs,
        @Nonnull MatchPublicationState state
    ) {
        ArenaActiveMatch match = context.match();
        int admissionCapacity = Math.max(match.admissionCapacity(), 0);
        int initialRosterSize = Math.max(match.expectedPlayerCount(), match.expectedPlayerUuids().size());
        int arrivedInitialPlayerCount = countArrivedInitialPlayers(match);
        int admittedSlotCount = Math.min(admissionCapacity, initialRosterSize + Math.max(match.consumedBackfillAdmissionCount(), 0));
        int unfilledInitialRosterCount = Math.max(0, initialRosterSize - arrivedInitialPlayerCount);
        if (initialRosterSize > admissionCapacity) {
            logger.atWarning().log(
                "Backend-driven match " + match.matchId()
                    + " reserved initial roster size "
                    + initialRosterSize
                    + " above admission capacity "
                    + admissionCapacity
                    + "; clamping admitted slots."
            );
        }
        boolean placementComplete = arenaMatchService.findMatchPlacementState(match.matchId())
            .map(ArenaMatchService.MatchPlacementState::placementComplete)
            .orElse(match.placementCompletedAtEpochMs() > 0L);
        String lifecycleStatus = lifecycleStatus(match, placementComplete);
        QueueBackfillMode mode = match.effectiveBackfillMode();
        long admissionOpenUntilEpochMs = 0L;
        boolean admissionOpen = false;
        boolean admissionReportingClosed = false;
        String closeReason = "";
        int availableAdmissionSlots = 0;

        if (match.explicitAdmissionClosed()) {
            return new AdmissionSnapshotView(
                lifecycleStatus,
                false,
                0L,
                admissionCapacity,
                admittedSlotCount,
                0,
                initialRosterSize,
                arrivedInitialPlayerCount,
                unfilledInitialRosterCount,
                true,
                normalizeOptional(match.explicitAdmissionCloseReason()),
                normalizeOptional(match.explicitAdmissionCloseReason())
            );
        }

        switch (mode) {
            case NONE -> {
                availableAdmissionSlots = 0;
                if (STATUS_ACTIVE.equals(lifecycleStatus)) {
                    admissionReportingClosed = true;
                    closeReason = CLOSE_REASON_NO_LONGER_ACCEPTING;
                }
            }
            case PLACEMENT_ONLY -> {
                admissionOpen = !placementComplete;
                availableAdmissionSlots = admissionOpen ? Math.max(0, admissionCapacity - admittedSlotCount) : 0;
                if (placementComplete) {
                    admissionReportingClosed = true;
                    closeReason = CLOSE_REASON_NO_LONGER_ACCEPTING;
                }
            }
            case ACTIVE_WINDOW -> {
                if (STATUS_PLACEMENT.equals(lifecycleStatus)) {
                    admissionOpen = true;
                    admissionOpenUntilEpochMs = 0L;
                    availableAdmissionSlots = Math.max(0, admissionCapacity - admittedSlotCount);
                } else {
                    long startedAtEpochMs = match.matchStartedAtEpochMs();
                    admissionOpenUntilEpochMs = startedAtEpochMs > 0L
                        ? startedAtEpochMs + Math.max(match.backfillWindowSeconds(), 0) * 1000L
                        : 0L;
                    admissionOpen = admissionOpenUntilEpochMs > 0L && nowEpochMs <= admissionOpenUntilEpochMs;
                    availableAdmissionSlots = admissionOpen ? Math.max(0, admissionCapacity - admittedSlotCount) : 0;
                    if (!admissionOpen && admissionOpenUntilEpochMs > 0L) {
                        admissionReportingClosed = true;
                        closeReason = CLOSE_REASON_WINDOW_EXPIRED;
                    } else if (admissionOpen && availableAdmissionSlots == 0) {
                        admissionOpen = false;
                        admissionReportingClosed = true;
                        closeReason = CLOSE_REASON_NO_LONGER_ACCEPTING;
                        availableAdmissionSlots = 0;
                    }
                }
            }
        }

        if (admissionReportingClosed && closeReason.isBlank()) {
            closeReason = CLOSE_REASON_NOT_REPORTABLE;
        }

        String primaryReason = normalizeOptional(state.primaryChangeReason);
        if (admissionReportingClosed) {
            if (CLOSE_REASON_WINDOW_EXPIRED.equals(closeReason)) {
                primaryReason = CHANGE_REASON_ADMISSION_WINDOW_EXPIRED;
            } else {
                primaryReason = CHANGE_REASON_ADMISSION_CLOSED;
            }
        }

        return new AdmissionSnapshotView(
            lifecycleStatus,
            admissionOpen,
            admissionOpenUntilEpochMs,
            admissionCapacity,
            admittedSlotCount,
            availableAdmissionSlots,
            initialRosterSize,
            arrivedInitialPlayerCount,
            unfilledInitialRosterCount,
            admissionReportingClosed,
            closeReason,
            primaryReason
        );
    }

    private String lifecycleStatus(@Nonnull ArenaActiveMatch match, boolean placementComplete) {
        return placementComplete ? STATUS_ACTIVE : STATUS_PLACEMENT;
    }

    private ReportableMatchContext findReportableMatchContext(@Nonnull String matchId) {
        ArenaActiveMatch match = arenaMatchService.find(matchId).orElse(null);
        if (match == null) {
            return null;
        }
        if (!isReportableMatchSnapshot(match)) {
            return null;
        }
        return new ReportableMatchContext(match);
    }

    private boolean isReportableMatchSnapshot(ArenaActiveMatch match) {
        if (match.effectiveMatchSource() != ArenaMatchSource.BACKEND_DRIVEN) {
            return false;
        }
        if (match.externalMatchId().isBlank()
            || match.queueId().isBlank()
            || match.arenaId().isBlank()
            || match.expectedPlayerUuids().isEmpty()) {
            return false;
        }
        if (match.admissionPolicySchemaVersion() <= 0
            || match.admissionCapacity() <= 0
            || match.matchSource().isBlank()
            || match.backfillMode().isBlank()) {
            logger.atWarning().log(
                "Skipping admission reporting because launch context is missing admission policy snapshot for match "
                    + match.matchId()
            );
            return false;
        }
        return true;
    }

    private static int countArrivedInitialPlayers(@Nonnull ArenaActiveMatch match) {
        if (match.expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        int arrivedInitialPlayers = 0;
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            if (expected.contains(playerUuid)) {
                arrivedInitialPlayers++;
            }
        }
        return arrivedInitialPlayers;
    }

    private void cleanupUnreportableState(@Nonnull String matchId, @Nonnull String message) {
        publicationStatesByMatchId.remove(matchId);
        logger.atWarning().log(message + " matchId=" + matchId);
    }

    @Nonnull
    private String parseBackendStatus(@Nonnull String responseBody) {
        if (responseBody.isBlank()) {
            return "OK";
        }
        try {
            BackendMatchAdmissionStateResponsePayload response = gson.fromJson(
                JsonParser.parseString(responseBody),
                BackendMatchAdmissionStateResponsePayload.class
            );
            if (response != null && response.status() != null && !response.status().isBlank()) {
                return response.status().trim();
            }
        } catch (RuntimeException ignored) {
        }
        return "OK";
    }

    private boolean isSemanticallyAcceptedBackendStatus(@Nonnull String rawStatus) {
        String status = normalizeOptional(rawStatus).toUpperCase();
        return status.isBlank()
            || "OK".equals(status)
            || "ACCEPTED".equals(status)
            || "DUPLICATE".equals(status)
            || "DUPLICATE_ACCEPTED".equals(status);
    }

    @Nonnull
    private AdmissionHttpResult toHttpResult(
        @Nonnull String matchId,
        @Nonnull BuiltAdmissionSnapshot snapshot,
        HttpResponse<String> response,
        Throwable throwable
    ) {
        if (throwable != null) {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            return AdmissionHttpResult.failure(
                matchId,
                snapshot,
                0,
                cause.getClass().getSimpleName(),
                normalizeOptional(cause.getMessage())
            );
        }
        if (response == null) {
            return AdmissionHttpResult.failure(matchId, snapshot, 0, "NO_RESPONSE", "Backend admission state response was null.");
        }
        return new AdmissionHttpResult(
            matchId,
            snapshot,
            response.statusCode(),
            "",
            "",
            Objects.toString(response.body(), "")
        );
    }

    @Nonnull
    private String hashCanonicalPayload(@Nonnull BackendMatchAdmissionStatePayload payload) {
        JsonObject canonical = new JsonObject();
        canonical.addProperty("schemaVersion", payload.schemaVersion());
        canonical.addProperty("stateUpdateId", payload.stateUpdateId());
        canonical.addProperty("admissionStateSequence", payload.admissionStateSequence());
        canonical.addProperty("sentAtEpochMs", payload.sentAtEpochMs());
        canonical.addProperty("stateExpiresAtEpochMs", payload.stateExpiresAtEpochMs());
        canonical.addProperty("reportingServerId", payload.reportingServerId());
        canonical.addProperty("matchId", payload.matchId());
        canonical.addProperty("externalMatchId", payload.externalMatchId());
        canonical.addProperty("queueId", payload.queueId());
        canonical.addProperty("arenaId", payload.arenaId());
        canonical.addProperty("backfillEnabled", payload.backfillEnabled());
        canonical.addProperty("backfillMode", payload.backfillMode());
        canonical.addProperty("backfillWindowSeconds", payload.backfillWindowSeconds());
        canonical.addProperty("matchLifecycleStatus", payload.matchLifecycleStatus());
        canonical.addProperty("admissionOpen", payload.admissionOpen());
        canonical.addProperty("admissionOpenUntilEpochMs", payload.admissionOpenUntilEpochMs());
        canonical.addProperty("admissionCapacity", payload.admissionCapacity());
        canonical.addProperty("admittedSlotCount", payload.admittedSlotCount());
        canonical.addProperty("availableAdmissionSlots", payload.availableAdmissionSlots());
        canonical.addProperty("initialRosterSize", payload.initialRosterSize());
        canonical.addProperty("arrivedInitialPlayerCount", payload.arrivedInitialPlayerCount());
        canonical.addProperty("unfilledInitialRosterCount", payload.unfilledInitialRosterCount());
        JsonArray consumedReservationIds = new JsonArray();
        payload.consumedAdmissionReservationIds().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(consumedReservationIds::add);
        canonical.add("consumedAdmissionReservationIds", consumedReservationIds);
        canonical.addProperty("admissionReportingClosed", payload.admissionReportingClosed());
        canonical.addProperty("admissionReportingCloseReason", payload.admissionReportingCloseReason());
        canonical.addProperty("primaryChangeReason", payload.primaryChangeReason());
        JsonArray reasons = new JsonArray();
        payload.coalescedChangeReasons().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(reasons::add);
        canonical.add("coalescedChangeReasons", reasons);
        return sha256Hex(gson.toJson(canonical));
    }

    @Nonnull
    private static String sha256Hex(@Nonnull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void logConfigNormalizationWarning(
        @Nonnull BackendMatchmakingConfig raw,
        @Nonnull BackendMatchmakingConfig normalized
    ) {
        if (raw.matchStateDebounceMs() == normalized.matchStateDebounceMs()
            && raw.matchStateMaxCoalesceWindowMs() == normalized.matchStateMaxCoalesceWindowMs()
            && raw.matchStateRetryIntervalMs() == normalized.matchStateRetryIntervalMs()
            && raw.matchStateStaleAfterMs() == normalized.matchStateStaleAfterMs()) {
            return;
        }
        logger.atWarning().log(
            "Normalized backend match admission state config: debounceMs="
                + normalized.matchStateDebounceMs()
                + " maxCoalesceWindowMs="
                + normalized.matchStateMaxCoalesceWindowMs()
                + " retryIntervalMs="
                + normalized.matchStateRetryIntervalMs()
                + " staleAfterMs="
                + normalized.matchStateStaleAfterMs()
                + "."
        );
    }

    private void logEntry(@Nonnull String message) {
        recentLogEntries.addLast(message);
        while (recentLogEntries.size() > MAX_LOG_ENTRIES) {
            recentLogEntries.removeFirst();
        }
        logger.atInfo().log(message);
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    private record ReportableMatchContext(
        ArenaActiveMatch match
    ) {
    }

    private record BuiltAdmissionSnapshot(
        BackendMatchAdmissionStatePayload payload,
        String body,
        List<String> consumedAdmissionReservationIdsIncluded
    ) {
    }

    private record AdmissionSnapshotView(
        String matchLifecycleStatus,
        boolean admissionOpen,
        long admissionOpenUntilEpochMs,
        int admissionCapacity,
        int admittedSlotCount,
        int availableAdmissionSlots,
        int initialRosterSize,
        int arrivedInitialPlayerCount,
        int unfilledInitialRosterCount,
        boolean admissionReportingClosed,
        String admissionReportingCloseReason,
        String primaryChangeReason
    ) {
    }

    private static final class MatchPublicationState {
        private boolean dirty;
        private long firstDirtyAtEpochMs;
        private long lastDirtyAtEpochMs;
        private long scheduledFlushAtEpochMs;
        private String primaryChangeReason = "";
        private final LinkedHashSet<String> coalescedReasons = new LinkedHashSet<>();
        private final LinkedHashSet<String> pendingConsumedAdmissionReservationIds = new LinkedHashSet<>();
        private BuiltAdmissionSnapshot inFlightSnapshot;
        private BuiltAdmissionSnapshot pendingRetrySnapshot;
        private long retryNotBeforeEpochMs;
        private long lastAllocatedSequence;
    }

    private record AdmissionHttpResult(
        String matchId,
        BuiltAdmissionSnapshot snapshot,
        int statusCode,
        String errorClass,
        String detail,
        String body
    ) {
        private static AdmissionHttpResult failure(
            @Nonnull String matchId,
            @Nonnull BuiltAdmissionSnapshot snapshot,
            int statusCode,
            @Nonnull String errorClass,
            @Nonnull String detail
        ) {
            return new AdmissionHttpResult(matchId, snapshot, statusCode, errorClass, detail, "");
        }
    }
}
