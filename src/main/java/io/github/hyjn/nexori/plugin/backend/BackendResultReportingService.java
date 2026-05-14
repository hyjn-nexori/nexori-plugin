package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.logic.BackendResultPayloadBuildResult;
import io.github.hyjn.nexori.plugin.backend.logic.BackendResultPayloadBuilder;
import io.github.hyjn.nexori.plugin.backend.logic.BackendResultEnqueuePlan;
import io.github.hyjn.nexori.plugin.backend.logic.BackendResultEnqueuePlanner;
import io.github.hyjn.nexori.plugin.backend.logic.BackendResultResponseDecision;
import io.github.hyjn.nexori.plugin.backend.logic.BackendResultResponsePolicy;
import io.github.hyjn.nexori.plugin.backend.payload.BackendResultResponsePayload;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class BackendResultReportingService {

    private static final int SCHEMA_VERSION = 1;
    private static final long AUTH_BACKOFF_MS = 30_000L;
    private static final long ERROR_BACKOFF_MS = 5_000L;
    private static final long STALE_SAFETY_WINDOW_MS = 1_000L;
    private static final int MAX_RESULT_LOG_ENTRIES = 80;

    private final HytaleLogger logger;
    private BackendMatchmakingConfig config;
    private final BackendResultStore resultStore;
    private final ServerIdentity localIdentity;
    private HttpClient httpClient;
    private final BackendResultEnqueuePlanner resultEnqueuePlanner = new BackendResultEnqueuePlanner();
    private final BackendResultPayloadBuilder resultPayloadBuilder = new BackendResultPayloadBuilder();
    private final BackendResultResponsePolicy resultResponsePolicy = new BackendResultResponsePolicy();
    private final Gson gson = new GsonBuilder().create();
    private final Queue<BackendResultHttpResult> queuedResults = new ConcurrentLinkedQueue<>();
    private final Set<String> staleRequestIds = new HashSet<>();
    private final Deque<BackendResultLogEntry> resultLogEntries = new ArrayDeque<>();

    private BackendResultReportingHealthState healthState = BackendResultReportingHealthState.healthy(0L);
    private boolean requestInFlight;
    private long inFlightStartedAtEpochMs;
    private String inFlightRequestId = "";
    private String inFlightResultId = "";

    public BackendResultReportingService(
        @Nonnull HytaleLogger logger,
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull BackendResultStore resultStore,
        @Nonnull ServerIdentity localIdentity
    ) {
        this.logger = logger;
        this.config = config.normalized();
        this.resultStore = resultStore;
        this.localIdentity = localIdentity;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.config.requestTimeoutMs()))
            .build();
    }

    public synchronized void handleTick(long nowEpochMs) {
        drainQueuedResults(nowEpochMs);
        clearStaleInFlight(nowEpochMs);
        maybeStartResultRequest(nowEpochMs);
    }

    public synchronized void updateConfig(@Nonnull BackendMatchmakingConfig updatedConfig) {
        this.config = updatedConfig.normalized();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.config.requestTimeoutMs()))
            .build();
        if (!this.config.resultReportingEnabled()) {
            this.healthState = BackendResultReportingHealthState.healthy(System.currentTimeMillis());
        }
    }

    @Nonnull
    public synchronized BackendResultReportingHealthState healthState() {
        return healthState;
    }

    @Nonnull
    public synchronized List<BackendResultLogEntry> recentResultLogEntries() {
        return List.copyOf(resultLogEntries);
    }

    @Nonnull
    public synchronized EnqueueResult enqueueResult(
        @Nonnull ArenaActiveMatch match,
        @Nonnull List<ArenaMatchService.SubmitMatchPlayerResult> players,
        @Nonnull Map<String, String> metadata,
        @Nonnull JsonObject customData,
        @Nonnull String reason,
        @Nonnull String payloadHash,
        long endedAtEpochMs
    ) {
        String resultId = UUID.randomUUID().toString().toLowerCase();
        BackendResultEnqueuePlan plan = resultEnqueuePlanner.plan(
            config,
            match,
            players,
            metadata,
            customData,
            reason,
            payloadHash,
            resultId,
            endedAtEpochMs
        );
        if (plan.outcome() == BackendResultEnqueuePlan.Outcome.DISABLED) {
            return new EnqueueResult(EnqueueOutcome.DISABLED, "", plan.message());
        }
        if (plan.outcome() == BackendResultEnqueuePlan.Outcome.EXTERNAL_MATCH_MISSING) {
            return new EnqueueResult(EnqueueOutcome.EXTERNAL_MATCH_MISSING, "", plan.message());
        }
        try {
            BackendResultStore.StorePutResult stored = resultStore.putPending(plan.record());
            return switch (stored.outcome()) {
                case QUEUED -> new EnqueueResult(EnqueueOutcome.QUEUED, stored.resultId(), stored.message());
                case ALREADY_SUBMITTED -> new EnqueueResult(EnqueueOutcome.ALREADY_SUBMITTED, stored.resultId(), stored.message());
                case DUPLICATE_CONFLICT -> new EnqueueResult(EnqueueOutcome.DUPLICATE_CONFLICT, stored.resultId(), stored.message());
            };
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist Nexori backend result.");
            return new EnqueueResult(EnqueueOutcome.STORE_FAILED, "", "Failed to persist backend result.");
        }
    }

    private void maybeStartResultRequest(long nowEpochMs) {
        if (!config.resultReportingEnabled() || requestInFlight) {
            return;
        }
        if (!config.isResultReportingUsable()) {
            healthState = BackendResultReportingHealthState.failed(
                "CONFIG_INVALID",
                0,
                "CONFIG",
                "Backend result reporting requires baseUrl and serverToken when enabled.",
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            return;
        }

        BackendResultStore.BackendResultRecord result = resultStore.findNextDuePending(nowEpochMs).orElse(null);
        if (result == null) {
            return;
        }

        String requestId = UUID.randomUUID().toString().toLowerCase();
        BackendResultPayloadBuildResult payloadResult = resultPayloadBuilder.build(
            SCHEMA_VERSION,
            nowEpochMs,
            localIdentity.serverId().toString(),
            result
        );
        String body = payloadResult.body();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.resultsUrl()))
                .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                .header("Authorization", "Bearer " + config.serverToken())
                .header("Content-Type", "application/json")
                .header("X-Nexori-Server-Id", localIdentity.serverId().toString())
                .header("X-Nexori-Result-Id", result.resultId())
                .header("X-Nexori-Sent-At-Epoch-Ms", Long.toString(nowEpochMs))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (IllegalArgumentException exception) {
            markRetryQuietly(result.resultId(), 0, exception.getClass().getSimpleName(), "Invalid backend results URL.", nowEpochMs, nowEpochMs + ERROR_BACKOFF_MS);
            healthState = BackendResultReportingHealthState.failed(
                "CONFIG_INVALID",
                0,
                exception.getClass().getSimpleName(),
                "Invalid backend results URL.",
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            return;
        }

        try {
            resultStore.markAttempt(result.resultId(), nowEpochMs);
        } catch (IOException exception) {
            healthState = BackendResultReportingHealthState.failed(
                "STORE_FAILED",
                0,
                exception.getClass().getSimpleName(),
                "Failed to update backend result attempt state.",
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            logger.atWarning().withCause(exception).log("Failed to update Nexori backend result attempt state.");
            return;
        }

        requestInFlight = true;
        inFlightStartedAtEpochMs = nowEpochMs;
        inFlightRequestId = requestId;
        inFlightResultId = result.resultId();

        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(config.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> {
                    BackendResultHttpResult httpResult;
                    try {
                        httpResult = toHttpResult(requestId, result.resultId(), response, throwable);
                    } catch (Throwable unexpected) {
                        httpResult = BackendResultHttpResult.failure(
                            requestId,
                            result.resultId(),
                            0,
                            unexpected.getClass().getSimpleName(),
                            "Unexpected result report completion failure."
                        );
                    }
                    queuedResults.add(httpResult);
                });
        } catch (Throwable throwable) {
            queuedResults.add(BackendResultHttpResult.failure(
                requestId,
                result.resultId(),
                0,
                throwable.getClass().getSimpleName(),
                "Backend result request could not be started."
            ));
        }
    }

    private void drainQueuedResults(long nowEpochMs) {
        BackendResultHttpResult result;
        while ((result = queuedResults.poll()) != null) {
            if (staleRequestIds.remove(result.requestId())) {
                continue;
            }
            if (requestInFlight && result.requestId().equals(inFlightRequestId)) {
                requestInFlight = false;
                inFlightRequestId = "";
                inFlightResultId = "";
                inFlightStartedAtEpochMs = 0L;
            }
            handleHttpResult(result, nowEpochMs);
        }
    }

    private void clearStaleInFlight(long nowEpochMs) {
        if (!requestInFlight) {
            return;
        }
        long staleAfterMs = config.requestTimeoutMs() + STALE_SAFETY_WINDOW_MS;
        if (nowEpochMs - inFlightStartedAtEpochMs <= staleAfterMs) {
            return;
        }
        String staleRequestId = inFlightRequestId;
        String staleResultId = inFlightResultId;
        staleRequestIds.add(staleRequestId);
        requestInFlight = false;
        inFlightRequestId = "";
        inFlightResultId = "";
        inFlightStartedAtEpochMs = 0L;
        long nextAttemptAt = nowEpochMs + config.resultRetryIntervalMs();
        markRetryQuietly(
            staleResultId,
            0,
            "IN_FLIGHT_STALE",
            "Backend result request exceeded timeout safety window.",
            nowEpochMs,
            nextAttemptAt
        );
        recordResultLogEntry(
            BackendResultHttpResult.failure(
                staleRequestId,
                staleResultId,
                0,
                "IN_FLIGHT_STALE",
                "Backend result request exceeded timeout safety window."
            ),
            nowEpochMs
        );
        healthState = BackendResultReportingHealthState.failed(
            "IN_FLIGHT_STALE",
            0,
            "TIMEOUT",
            "Backend result request exceeded timeout safety window.",
            nowEpochMs,
            nextAttemptAt
        );
    }

    private void handleHttpResult(@Nonnull BackendResultHttpResult result, long nowEpochMs) {
        recordResultLogEntry(result, nowEpochMs);
        BackendResultResponsePayload response = result.response();
        BackendResultResponseDecision decision = resultResponsePolicy.decide(
            result.resultId(),
            result.statusCode(),
            response == null ? "" : response.receivedResultId(),
            response == null ? "" : response.status(),
            result.errorClass(),
            result.message(),
            nowEpochMs,
            config.resultRetryIntervalMs(),
            AUTH_BACKOFF_MS,
            ERROR_BACKOFF_MS
        );
        if (decision.action() == BackendResultResponseDecision.Action.ACKNOWLEDGE) {
            try {
                resultStore.markAcknowledged(result.resultId(), decision.statusCode(), decision.backendStatus(), nowEpochMs);
                healthState = BackendResultReportingHealthState.healthy(nowEpochMs);
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to mark Nexori backend result acknowledged.");
            }
            return;
        }

        if (decision.action() == BackendResultResponseDecision.Action.PERMANENT_FAILURE) {
            markPermanentQuietly(result.resultId(), decision.statusCode(), decision.errorClass(), decision.errorMessage(), nowEpochMs);
            healthState = BackendResultReportingHealthState.failed(
                decision.healthStatus(),
                decision.statusCode(),
                decision.errorClass(),
                decision.errorMessage(),
                nowEpochMs,
                0L
            );
            return;
        }

        markRetryQuietly(
            result.resultId(),
            decision.statusCode(),
            decision.errorClass(),
            decision.errorMessage(),
            nowEpochMs,
            decision.nextAttemptAtEpochMs()
        );
        healthState = BackendResultReportingHealthState.failed(
            decision.healthStatus(),
            decision.statusCode(),
            decision.errorClass(),
            decision.errorMessage(),
            nowEpochMs,
            decision.nextAttemptAtEpochMs()
        );
        if (decision.authWarning()) {
            logger.atWarning().log(
                "Nexori backend result reporting auth failed status=" + decision.statusCode() + " path=/nexori/results."
            );
        }
    }

    private void recordResultLogEntry(@Nonnull BackendResultHttpResult result, long nowEpochMs) {
        BackendResultResponsePayload response = result.response();
        String backendStatus = response == null || response.status() == null ? "" : response.status().trim();
        int playerCount = resultStore.find(result.resultId())
            .map(record -> record.players().size())
            .orElse(0);
        String outcome;
        if (isAcceptedOrDuplicate(result)) {
            outcome = backendStatus.isBlank() ? "OK" : backendStatus;
        } else if (result.isAuthFailure()) {
            outcome = "AUTH_FAILED";
        } else if (result.isForbidden()) {
            outcome = "AUTH_FORBIDDEN";
        } else if (result.statusCode() > 0) {
            outcome = "HTTP_ERROR";
        } else {
            outcome = result.errorClass() == null || result.errorClass().isBlank() ? "FAILED" : result.errorClass().trim();
        }

        resultLogEntries.addFirst(new BackendResultLogEntry(
            nowEpochMs,
            "POST",
            "/nexori/results",
            result.resultId(),
            result.statusCode(),
            outcome,
            result.errorClass(),
            result.message(),
            backendStatus,
            playerCount
        ));
        while (resultLogEntries.size() > MAX_RESULT_LOG_ENTRIES) {
            resultLogEntries.removeLast();
        }
    }

    private boolean isAcceptedOrDuplicate(@Nonnull BackendResultHttpResult result) {
        if (!result.hasResponse()) {
            return false;
        }
        BackendResultResponsePayload response = result.response();
        if (response.receivedResultId() == null || !response.receivedResultId().equals(result.resultId())) {
            return false;
        }
        String status = response.status() == null ? "" : response.status().trim();
        return "ACCEPTED".equalsIgnoreCase(status) || "DUPLICATE".equalsIgnoreCase(status);
    }

    @Nonnull
    private BackendResultHttpResult toHttpResult(
        @Nonnull String requestId,
        @Nonnull String resultId,
        HttpResponse<String> response,
        Throwable throwable
    ) {
        if (throwable != null) {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            return BackendResultHttpResult.failure(
                requestId,
                resultId,
                0,
                cause.getClass().getSimpleName(),
                "Backend result request failed."
            );
        }
        if (response == null) {
            return BackendResultHttpResult.failure(requestId, resultId, 0, "NO_RESPONSE", "Backend result request returned no response.");
        }
        String body = response.body() == null ? "" : response.body();
        if (body.isBlank()) {
            return BackendResultHttpResult.failure(
                requestId,
                resultId,
                response.statusCode(),
                "EMPTY_BODY",
                "Backend result response body was empty."
            );
        }
        try {
            BackendResultResponsePayload payload = gson.fromJson(body, BackendResultResponsePayload.class);
            if (payload == null) {
                return BackendResultHttpResult.failure(
                    requestId,
                    resultId,
                    response.statusCode(),
                    "PARSE_ERROR",
                    "Backend result response body could not be parsed."
                );
            }
            return BackendResultHttpResult.success(requestId, resultId, response.statusCode(), payload);
        } catch (JsonSyntaxException exception) {
            return BackendResultHttpResult.failure(
                requestId,
                resultId,
                response.statusCode(),
                exception.getClass().getSimpleName(),
                "Backend result response body could not be parsed."
            );
        }
    }

    private void markRetryQuietly(
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message,
        long nowEpochMs,
        long nextAttemptAtEpochMs
    ) {
        try {
            resultStore.markRetry(resultId, statusCode, errorClass, message, nowEpochMs, nextAttemptAtEpochMs);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist Nexori backend result retry state.");
        }
    }

    private void markPermanentQuietly(
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message,
        long nowEpochMs
    ) {
        try {
            resultStore.markPermanentFailure(resultId, statusCode, errorClass, message, nowEpochMs);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist Nexori backend result permanent failure.");
        }
    }

    public enum EnqueueOutcome {
        QUEUED,
        DISABLED,
        EXTERNAL_MATCH_MISSING,
        STORE_FAILED,
        ALREADY_SUBMITTED,
        DUPLICATE_CONFLICT
    }

    public record EnqueueResult(
        EnqueueOutcome outcome,
        String resultId,
        String message
    ) {
    }

    public record BackendResultLogEntry(
        long completedAtEpochMs,
        String method,
        String path,
        String resultId,
        int statusCode,
        String outcome,
        String errorClass,
        String message,
        String backendStatus,
        int playerCount
    ) {
    }
}
