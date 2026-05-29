package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.logic.AdmissionStateEvaluation;
import io.github.hyjn.nexori.plugin.backend.logic.AdmissionStateEvaluator;
import io.github.hyjn.nexori.plugin.backend.logic.AdmissionStatePayloadBuildResult;
import io.github.hyjn.nexori.plugin.backend.logic.AdmissionStatePayloadBuilder;
import io.github.hyjn.nexori.plugin.backend.logic.AdmissionStateResponseDecision;
import io.github.hyjn.nexori.plugin.backend.logic.AdmissionStateResponsePolicy;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Supplier;

public final class BackendMatchAdmissionStateReportingService {

    private static final int SCHEMA_VERSION = 1;
    private static final long AUTH_BACKOFF_MS = 30_000L;
    private static final int MAX_LOG_ENTRIES = 80;
    private static final int MAX_CLOSED_MATCH_IDS = 2048;
    private static final String CHANGE_REASON_MATCH_CREATED = "MATCH_CREATED";
    private static final String CHANGE_REASON_PLAYER_ARRIVED = "PLAYER_ARRIVED";
    private static final String CHANGE_REASON_PLACEMENT_COMPLETED = "PLACEMENT_COMPLETED";
    private static final String CHANGE_REASON_ADMISSION_CLOSED = "ADMISSION_CLOSED";
    private static final String CHANGE_REASON_MATCH_STARTED = AdmissionStateEvaluator.CHANGE_REASON_MATCH_STARTED;

    private final HytaleLogger logger;
    private BackendMatchmakingConfig config;
    private final ServerIdentity localIdentity;
    private final Supplier<String> connectionAddressSupplier;
    private final ArenaMatchService arenaMatchService;
    private BackendHttpTransport transport;
    private final boolean useDefaultTransport;
    private final AdmissionStateEvaluator admissionStateEvaluator = new AdmissionStateEvaluator();
    private final AdmissionStatePayloadBuilder admissionStatePayloadBuilder = new AdmissionStatePayloadBuilder();
    private final AdmissionStateResponsePolicy admissionStateResponsePolicy = new AdmissionStateResponsePolicy();
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
        @Nonnull Supplier<String> connectionAddressSupplier,
        @Nonnull ArenaMatchService arenaMatchService
    ) {
        this.logger = logger;
        this.localIdentity = localIdentity;
        this.connectionAddressSupplier = connectionAddressSupplier;
        this.arenaMatchService = arenaMatchService;
        this.config = config.normalized();
        logConfigNormalizationWarning(config, this.config);
        this.transport = new JdkBackendHttpTransport(this.config.requestTimeoutMs());
        this.useDefaultTransport = true;
    }

    BackendMatchAdmissionStateReportingService(
        @Nonnull HytaleLogger logger,
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull ArenaMatchService arenaMatchService,
        @Nonnull BackendHttpTransport transport
    ) {
        this.logger = logger;
        this.localIdentity = localIdentity;
        this.connectionAddressSupplier = () -> "";
        this.arenaMatchService = arenaMatchService;
        this.config = config.normalized();
        this.transport = transport;
        this.useDefaultTransport = false;
    }

    public synchronized void updateConfig(@Nonnull BackendMatchmakingConfig updatedConfig) {
        BackendMatchmakingConfig normalized = updatedConfig.normalized();
        logConfigNormalizationWarning(updatedConfig, normalized);
        this.config = normalized;
        if (useDefaultTransport) {
            this.transport = new JdkBackendHttpTransport(this.config.requestTimeoutMs());
        }
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

        AdmissionStatePayloadBuildResult snapshot = buildSnapshot(matchId, state, nowEpochMs);
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

            AdmissionStatePayloadBuildResult snapshot = buildSnapshot(matchId, state, nowEpochMs);
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
            AdmissionStateEvaluation view = evaluateAdmissionState(context, nowEpochMs, state);
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
            AdmissionStateEvaluation view = evaluateAdmissionState(context, nowEpochMs, state);
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
        @Nonnull AdmissionStatePayloadBuildResult snapshot,
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
            transport.sendAsync(request, config.requestTimeoutMs())
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
            AdmissionStatePayloadBuildResult inFlight = state.inFlightSnapshot;
            if (inFlight == null || !inFlight.payload().stateUpdateId().equals(result.snapshot().payload().stateUpdateId())) {
                continue;
            }
            state.inFlightSnapshot = null;

            int statusCode = result.statusCode();
            boolean snapshotClosed = result.snapshot().payload().admissionReportingClosed();
            boolean retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500 || statusCode == 0;
            boolean reportableMatchStillExists = true;
            if ((statusCode == 400 || statusCode == 422) && !snapshotClosed) {
                reportableMatchStillExists = findReportableMatchContext(result.matchId()) != null;
            } else if (retryable
                && !state.dirty
                && nowEpochMs > result.snapshot().payload().stateExpiresAtEpochMs()) {
                reportableMatchStillExists = findReportableMatchContext(result.matchId()) != null;
            }
            AdmissionStateResponseDecision decision = admissionStateResponsePolicy.decide(
                statusCode,
                result.body(),
                nowEpochMs,
                AUTH_BACKOFF_MS,
                config.matchStateRetryIntervalMs(),
                snapshotClosed,
                result.snapshot().payload().stateExpiresAtEpochMs(),
                state.dirty,
                reportableMatchStillExists
            );
            if (!decision.lastHealthStatus().isBlank()) {
                lastHealthStatus = decision.lastHealthStatus();
            }
            if (decision.nextGlobalAttemptAtEpochMs() > 0L || decision.outcome() == AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED
                || decision.outcome() == AdmissionStateResponseDecision.Outcome.NOT_SEMANTICALLY_ACKNOWLEDGED) {
                nextGlobalAttemptAtEpochMs = decision.nextGlobalAttemptAtEpochMs();
            }
            if (decision.shouldMarkDirtyFromSnapshot()) {
                markDirtyFromSnapshot(state, result.snapshot(), nowEpochMs);
            }
            if (decision.shouldScheduleImmediateFlush()) {
                state.scheduledFlushAtEpochMs = nowEpochMs;
            }

            if (decision.outcome() == AdmissionStateResponseDecision.Outcome.NOT_SEMANTICALLY_ACKNOWLEDGED) {
                logEntry(
                    "Admission state was not semantically acknowledged matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " status=" + decision.backendStatus()
                );
                continue;
            }

            if (decision.outcome() == AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED) {
                ackConsumedAdmissionReservationIds(state, result.snapshot().consumedAdmissionReservationIdsIncluded());
                if (decision.shouldRememberClosedMatch()) {
                    rememberClosedMatch(result.matchId());
                }
                if (decision.shouldRemovePublicationState()) {
                    publicationStatesByMatchId.remove(result.matchId());
                }
                logEntry(
                    "Admission state acknowledged matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " status=" + decision.backendStatus()
                );
                continue;
            }

            if (decision.outcome() == AdmissionStateResponseDecision.Outcome.PERMANENT_FAILURE) {
                logEntry(
                    "Admission state permanent failure matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                        + " error=" + result.errorClass()
                );
                if (decision.shouldRememberClosedMatch()) {
                    rememberClosedMatch(result.matchId());
                }
                if (decision.shouldRemovePublicationState()) {
                    publicationStatesByMatchId.remove(result.matchId());
                }
                continue;
            }

            if (decision.outcome() == AdmissionStateResponseDecision.Outcome.AUTH_FAILURE) {
                logEntry(
                    "Admission state auth failure matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                );
                continue;
            }

            if (decision.outcome() == AdmissionStateResponseDecision.Outcome.UNEXPECTED_FAILURE) {
                logEntry(
                    "Admission state unexpected failure matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                        + " statusCode=" + statusCode
                        + " error=" + result.errorClass()
                );
                continue;
            }

            if (decision.shouldClearPendingRetrySnapshot()) {
                state.pendingRetrySnapshot = null;
                state.retryNotBeforeEpochMs = decision.retryNotBeforeEpochMs();
                logEntry(
                    "Admission state dropped stale retry in favor of newer state matchId=" + result.matchId()
                        + " sequence=" + result.snapshot().payload().admissionStateSequence()
                );
                continue;
            }

            if (decision.shouldStorePendingRetrySnapshot()) {
                state.pendingRetrySnapshot = result.snapshot();
                state.retryNotBeforeEpochMs = decision.retryNotBeforeEpochMs();
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
                if (decision.shouldMarkMatchStartedDirty()) {
                    markDirty(state, CHANGE_REASON_MATCH_STARTED, nowEpochMs);
                } else if (decision.shouldRemovePublicationState()) {
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

    private void markDirtyFromSnapshot(@Nonnull MatchPublicationState state, @Nonnull AdmissionStatePayloadBuildResult snapshot, long nowEpochMs) {
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

    private AdmissionStatePayloadBuildResult buildSnapshot(@Nonnull String matchId, @Nonnull MatchPublicationState state, long nowEpochMs) {
        ReportableMatchContext context = findReportableMatchContext(matchId);
        if (context == null) {
            return null;
        }

        AdmissionStateEvaluation view = evaluateAdmissionState(context, nowEpochMs, state);
        long sequence = ++state.lastAllocatedSequence;
        long sentAtEpochMs = nowEpochMs;
        long expiresAtEpochMs = nowEpochMs + config.matchStateStaleAfterMs();
        String connectionAddress = connectionAddressSupplier.get();
        return admissionStatePayloadBuilder.build(
            SCHEMA_VERSION,
            UUID.randomUUID().toString().toLowerCase(),
            sequence,
            sentAtEpochMs,
            expiresAtEpochMs,
            localIdentity.serverId().toString(),
            connectionAddress != null ? connectionAddress : "",
            context.match(),
            view,
            state.primaryChangeReason,
            state.coalescedReasons,
            state.pendingConsumedAdmissionReservationIds
        );
    }

    private AdmissionStateEvaluation evaluateAdmissionState(
        @Nonnull ReportableMatchContext context,
        long nowEpochMs,
        @Nonnull MatchPublicationState state
    ) {
        ArenaActiveMatch match = context.match();
        boolean placementComplete = arenaMatchService.findMatchPlacementState(match.matchId())
            .map(ArenaMatchService.MatchPlacementState::placementComplete)
            .orElse(match.placementCompletedAtEpochMs() > 0L);
        AdmissionStateEvaluation evaluation = admissionStateEvaluator.evaluate(
            match,
            placementComplete,
            nowEpochMs,
            state.primaryChangeReason
        );
        if (evaluation.initialRosterExceedsAdmissionCapacity()) {
            logger.atWarning().log(
                "Backend-driven match " + match.matchId()
                    + " reserved initial roster size "
                    + evaluation.initialRosterSize()
                    + " above admission capacity "
                    + evaluation.admissionCapacity()
                    + "; clamping admitted slots."
            );
        }
        return evaluation;
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

    private void cleanupUnreportableState(@Nonnull String matchId, @Nonnull String message) {
        publicationStatesByMatchId.remove(matchId);
        logger.atWarning().log(message + " matchId=" + matchId);
    }

    @Nonnull
    private AdmissionHttpResult toHttpResult(
        @Nonnull String matchId,
        @Nonnull AdmissionStatePayloadBuildResult snapshot,
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

    private static final class MatchPublicationState {
        private boolean dirty;
        private long firstDirtyAtEpochMs;
        private long lastDirtyAtEpochMs;
        private long scheduledFlushAtEpochMs;
        private String primaryChangeReason = "";
        private final LinkedHashSet<String> coalescedReasons = new LinkedHashSet<>();
        private final LinkedHashSet<String> pendingConsumedAdmissionReservationIds = new LinkedHashSet<>();
        private AdmissionStatePayloadBuildResult inFlightSnapshot;
        private AdmissionStatePayloadBuildResult pendingRetrySnapshot;
        private long retryNotBeforeEpochMs;
        private long lastAllocatedSequence;
    }

    private record AdmissionHttpResult(
        String matchId,
        AdmissionStatePayloadBuildResult snapshot,
        int statusCode,
        String errorClass,
        String detail,
        String body
    ) {
        private static AdmissionHttpResult failure(
            @Nonnull String matchId,
            @Nonnull AdmissionStatePayloadBuildResult snapshot,
            int statusCode,
            @Nonnull String errorClass,
            @Nonnull String detail
        ) {
            return new AdmissionHttpResult(matchId, snapshot, statusCode, errorClass, detail, "");
        }
    }
}
