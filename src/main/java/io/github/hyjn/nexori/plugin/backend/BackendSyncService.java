package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentAckPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncRequestPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncResponsePayload;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.ArenaService;
import io.github.hyjn.nexori.plugin.minigame.NetworkLobbyService;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;
import io.github.hyjn.nexori.plugin.minigame.QueueService;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;

import javax.annotation.Nonnull;
import java.io.IOException;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class BackendSyncService {

    private static final int SCHEMA_VERSION = 1;
    private static final long AUTH_BACKOFF_MS = 30_000L;
    private static final long ERROR_BACKOFF_MS = 5_000L;
    private static final long STALE_SAFETY_WINDOW_MS = 1_000L;
    private static final int MAX_SYNC_LOG_ENTRIES = 80;

    private final HytaleLogger logger;
    private BackendMatchmakingConfig config;
    private final BackendAssignmentStore assignmentStore;
    private final ServerIdentity localIdentity;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final NetworkLobbyService networkLobbyService;
    private final QueueService queueService;
    private final QueueCoordinatorService queueCoordinatorService;
    private final ArenaService arenaService;
    private final ArenaMatchService arenaMatchService;
    private HttpClient httpClient;
    private final Gson gson = new GsonBuilder().create();
    private final Queue<BackendSyncHttpResult> queuedResults = new ConcurrentLinkedQueue<>();
    private final Set<String> staleSyncIds = new HashSet<>();
    private final Deque<BackendSyncLogEntry> syncLogEntries = new ArrayDeque<>();

    private BackendSyncHealthState healthState = BackendSyncHealthState.healthy(0L);
    private long lastSyncAttemptAtEpochMs;
    private long nextSyncAllowedAtEpochMs;
    private boolean requestInFlight;
    private long inFlightStartedAtEpochMs;
    private String inFlightSyncId = "";
    private long inFlightSequence;

    public BackendSyncService(
        @Nonnull HytaleLogger logger,
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull BackendAssignmentStore assignmentStore,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull NetworkLobbyService networkLobbyService,
        @Nonnull QueueService queueService,
        @Nonnull QueueCoordinatorService queueCoordinatorService,
        @Nonnull ArenaService arenaService,
        @Nonnull ArenaMatchService arenaMatchService
    ) {
        this.logger = logger;
        this.config = config.normalized();
        this.assignmentStore = assignmentStore;
        this.localIdentity = localIdentity;
        this.localConnectionAddressService = localConnectionAddressService;
        this.networkLobbyService = networkLobbyService;
        this.queueService = queueService;
        this.queueCoordinatorService = queueCoordinatorService;
        this.arenaService = arenaService;
        this.arenaMatchService = arenaMatchService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.config.requestTimeoutMs()))
            .build();
    }

    public synchronized void handleTick(long nowEpochMs) {
        drainQueuedResults(nowEpochMs);
        clearStaleInFlight(nowEpochMs);
        maybeStartSync(nowEpochMs);
    }

    public synchronized void updateConfig(@Nonnull BackendMatchmakingConfig updatedConfig) {
        this.config = updatedConfig.normalized();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.config.requestTimeoutMs()))
            .build();
        if (!this.config.enabled()) {
            this.healthState = BackendSyncHealthState.healthy(System.currentTimeMillis());
        }
        this.nextSyncAllowedAtEpochMs = Math.min(this.nextSyncAllowedAtEpochMs, System.currentTimeMillis());
    }

    @Nonnull
    public synchronized BackendMatchmakingConfig config() {
        return config;
    }

    @Nonnull
    public synchronized BackendSyncHealthState healthState() {
        return healthState;
    }

    @Nonnull
    public synchronized List<BackendSyncLogEntry> recentSyncLogEntries() {
        return List.copyOf(syncLogEntries);
    }

    private void maybeStartSync(long nowEpochMs) {
        if (!config.enabled()) {
            return;
        }
        if (!config.isUsable()) {
            healthState = BackendSyncHealthState.failed(
                "CONFIG_INVALID",
                0,
                "CONFIG",
                "Backend matchmaking requires baseUrl and serverToken when enabled.",
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            nextSyncAllowedAtEpochMs = healthState.nextAttemptAtEpochMs();
            return;
        }
        if (requestInFlight || nowEpochMs < nextSyncAllowedAtEpochMs) {
            return;
        }

        String syncId = UUID.randomUUID().toString().toLowerCase();
        long sequence;
        try {
            sequence = assignmentStore.nextSequence();
        } catch (IOException exception) {
            healthState = BackendSyncHealthState.failed(
                "STORE_FAILED",
                0,
                exception.getClass().getSimpleName(),
                "Failed to allocate backend sync sequence.",
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            nextSyncAllowedAtEpochMs = healthState.nextAttemptAtEpochMs();
            logger.atWarning().withCause(exception).log("Failed to allocate Nexori backend sync sequence.");
            return;
        }

        BackendSyncRequestPayload payload = buildRequestPayload(syncId, sequence, nowEpochMs);
        String body = gson.toJson(payload);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.syncUrl()))
                .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                .header("Authorization", "Bearer " + config.serverToken())
                .header("Content-Type", "application/json")
                .header("X-Nexori-Server-Id", localIdentity.serverId().toString())
                .header("X-Nexori-Sync-Id", syncId)
                .header("X-Nexori-Sequence", Long.toString(sequence))
                .header("X-Nexori-Sent-At-Epoch-Ms", Long.toString(nowEpochMs))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (IllegalArgumentException exception) {
            healthState = BackendSyncHealthState.failed(
                "CONFIG_INVALID",
                0,
                exception.getClass().getSimpleName(),
                "Invalid backend sync URL.",
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            nextSyncAllowedAtEpochMs = healthState.nextAttemptAtEpochMs();
            return;
        }

        requestInFlight = true;
        inFlightStartedAtEpochMs = nowEpochMs;
        inFlightSyncId = syncId;
        inFlightSequence = sequence;
        lastSyncAttemptAtEpochMs = nowEpochMs;
        nextSyncAllowedAtEpochMs = nowEpochMs + config.syncIntervalMs();

        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(config.requestTimeoutMs(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> {
                    BackendSyncHttpResult result;
                    try {
                        result = toHttpResult(syncId, sequence, response, throwable);
                    } catch (Throwable unexpected) {
                        result = BackendSyncHttpResult.failure(
                            syncId,
                            sequence,
                            0,
                            unexpected.getClass().getSimpleName(),
                            "Unexpected sync completion failure."
                        );
                    }
                    queuedResults.add(result);
                });
        } catch (Throwable throwable) {
            queuedResults.add(BackendSyncHttpResult.failure(
                syncId,
                sequence,
                0,
                throwable.getClass().getSimpleName(),
                "Backend sync request could not be started."
            ));
        }
    }

    private void drainQueuedResults(long nowEpochMs) {
        BackendSyncHttpResult result;
        while ((result = queuedResults.poll()) != null) {
            if (staleSyncIds.remove(result.syncId())) {
                continue;
            }
            if (requestInFlight && result.syncId().equals(inFlightSyncId)) {
                requestInFlight = false;
                inFlightSyncId = "";
                inFlightSequence = 0L;
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
        String staleSyncId = inFlightSyncId;
        long staleSequence = inFlightSequence;
        staleSyncIds.add(staleSyncId);
        requestInFlight = false;
        inFlightSyncId = "";
        inFlightSequence = 0L;
        inFlightStartedAtEpochMs = 0L;
        healthState = BackendSyncHealthState.failed(
            "IN_FLIGHT_STALE",
            0,
            "TIMEOUT",
            "Backend sync request exceeded timeout safety window.",
            nowEpochMs,
            nowEpochMs + ERROR_BACKOFF_MS
        );
        nextSyncAllowedAtEpochMs = Math.max(nextSyncAllowedAtEpochMs, healthState.nextAttemptAtEpochMs());
        recordSyncLogEntry(
            BackendSyncHttpResult.failure(
                staleSyncId,
                staleSequence,
                0,
                "IN_FLIGHT_STALE",
                "Backend sync request exceeded timeout safety window."
            ),
            nowEpochMs
        );
    }

    private void handleHttpResult(@Nonnull BackendSyncHttpResult result, long nowEpochMs) {
        recordSyncLogEntry(result, nowEpochMs);
        if (result.isAuthFailure()) {
            healthState = BackendSyncHealthState.failed("AUTH_FAILED", 401, "HTTP", "Backend sync auth failed.", nowEpochMs, nowEpochMs + AUTH_BACKOFF_MS);
            nextSyncAllowedAtEpochMs = Math.max(nextSyncAllowedAtEpochMs, healthState.nextAttemptAtEpochMs());
            logger.atWarning().log("Nexori backend sync auth failed with status=401 path=/nexori/sync.");
            return;
        }
        if (result.isForbidden()) {
            healthState = BackendSyncHealthState.failed("AUTH_FORBIDDEN", 403, "HTTP", "Backend sync forbidden.", nowEpochMs, nowEpochMs + AUTH_BACKOFF_MS);
            nextSyncAllowedAtEpochMs = Math.max(nextSyncAllowedAtEpochMs, healthState.nextAttemptAtEpochMs());
            logger.atWarning().log("Nexori backend sync forbidden with status=403 path=/nexori/sync.");
            return;
        }
        if (!result.hasResponse()) {
            healthState = BackendSyncHealthState.failed(
                "SYNC_FAILED",
                result.statusCode(),
                result.errorClass(),
                result.message(),
                nowEpochMs,
                nowEpochMs + ERROR_BACKOFF_MS
            );
            nextSyncAllowedAtEpochMs = Math.max(nextSyncAllowedAtEpochMs, healthState.nextAttemptAtEpochMs());
            logger.atWarning().log(
                "Nexori backend sync failed status=" + result.statusCode()
                    + " path=/nexori/sync errorClass=" + result.errorClass()
            );
            return;
        }

        BackendSyncResponsePayload response = result.response();
        try {
            assignmentStore.acknowledge(response.acknowledgedAssignmentAckIds());
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist acknowledged Nexori backend assignment ACKs.");
            return;
        }
        processAssignments(response.assignments(), nowEpochMs);
        healthState = BackendSyncHealthState.healthy(nowEpochMs);
    }

    private void recordSyncLogEntry(@Nonnull BackendSyncHttpResult result, long nowEpochMs) {
        BackendSyncResponsePayload response = result.response();
        int assignmentCount = response == null || response.assignments() == null ? 0 : response.assignments().size();
        int acknowledgedAckCount = response == null || response.acknowledgedAssignmentAckIds() == null
            ? 0
            : response.acknowledgedAssignmentAckIds().size();
        String outcome;
        if (result.hasResponse()) {
            outcome = "OK";
        } else if (result.isAuthFailure()) {
            outcome = "AUTH_FAILED";
        } else if (result.isForbidden()) {
            outcome = "AUTH_FORBIDDEN";
        } else if (result.statusCode() > 0) {
            outcome = "HTTP_ERROR";
        } else {
            outcome = normalize(result.errorClass()).isBlank() ? "FAILED" : normalize(result.errorClass());
        }

        syncLogEntries.addFirst(new BackendSyncLogEntry(
            nowEpochMs,
            "POST",
            "/nexori/sync",
            normalize(result.syncId()),
            result.sequence(),
            result.statusCode(),
            outcome,
            normalize(result.errorClass()),
            normalize(result.message()),
            assignmentCount,
            acknowledgedAckCount
        ));
        while (syncLogEntries.size() > MAX_SYNC_LOG_ENTRIES) {
            syncLogEntries.removeLast();
        }
    }

    private void processAssignments(List<BackendAssignmentPayload> assignments, long nowEpochMs) {
        if (assignments == null || assignments.isEmpty()) {
            return;
        }
        for (BackendAssignmentPayload assignment : assignments) {
            processAssignment(assignment, nowEpochMs);
        }
    }

    private void processAssignment(BackendAssignmentPayload assignment, long nowEpochMs) {
        if (assignment == null || assignment.assignmentId() == null || assignment.assignmentId().isBlank()) {
            return;
        }
        String assignmentHash = hashAssignment(assignment);
        Optional<BackendAssignmentStore.BackendAssignmentRecord> existing = assignmentStore.findAssignment(assignment.assignmentId());
        if (existing.isPresent()) {
            if (existing.get().assignmentHash().equals(assignmentHash)) {
                return;
            }
            try {
                assignmentStore.recordRejectedDuplicate(
                    assignment,
                    "assignmentId reused with different payload",
                    nowEpochMs
                );
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to persist duplicate Nexori backend assignment rejection.");
            }
            return;
        }

        String validationError = validateAssignmentOutsideQueueCoordinator(assignment);
        if (!validationError.isBlank()) {
            persistAssignmentAck(assignment, assignmentHash, "REJECTED", "", validationError, nowEpochMs);
            return;
        }

        List<UUID> playerUuids = parsePlayerUuids(assignment.playerUuids());
        QueueCoordinatorService.AssignmentLaunchResult result = queueCoordinatorService.launchBackendAssignment(
            assignment.assignmentId(),
            normalize(assignment.externalMatchId()),
            assignment.queueId(),
            assignment.arenaId(),
            playerUuids
        );
        persistAssignmentAck(
            assignment,
            assignmentHash,
            result.outcome().name(),
            result.localMatchId(),
            result.reason(),
            nowEpochMs
        );
    }

    private String validateAssignmentOutsideQueueCoordinator(BackendAssignmentPayload assignment) {
        if (!"CREATE_MATCH".equalsIgnoreCase(normalize(assignment.type()))) {
            return "Unsupported assignment type.";
        }
        List<UUID> playerUuids;
        try {
            playerUuids = parsePlayerUuids(assignment.playerUuids());
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
        for (UUID playerUuid : playerUuids) {
            if (arenaMatchService.findActiveMatchId(playerUuid).isPresent()) {
                return "Player " + playerUuid + " is already in an active match.";
            }
        }
        return "";
    }

    private void persistAssignmentAck(
        BackendAssignmentPayload assignment,
        String assignmentHash,
        String status,
        String localMatchId,
        String reason,
        long nowEpochMs
    ) {
        try {
            assignmentStore.recordProcessed(assignment, assignmentHash, status, localMatchId, reason, nowEpochMs);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist Nexori backend assignment ACK.");
        }
    }

    @Nonnull
    private BackendSyncRequestPayload buildRequestPayload(@Nonnull String syncId, long sequence, long nowEpochMs) {
        Map<String, QueueRuntimeState> runtimeByQueueId = new LinkedHashMap<>();
        for (QueueRuntimeState state : queueCoordinatorService.listQueueStates()) {
            runtimeByQueueId.put(state.queueId(), state);
        }

        List<BackendSyncRequestPayload.QueueSnapshot> queues = new ArrayList<>();
        for (QueueDefinition queue : queueService.list()) {
            QueueRuntimeState runtime = runtimeByQueueId.get(queue.queueId());
            queues.add(new BackendSyncRequestPayload.QueueSnapshot(
                queue.queueId(),
                queue.displayName(),
                queue.minPlayers(),
                queue.maxPlayers(),
                queue.countdownSeconds(),
                queue.launchTravelProfileId(),
                queue.effectiveMatchmakingMode().id(),
                queue.enabled(),
                queue.arenaIds(),
                runtime == null ? null : buildRuntimeSnapshot(runtime)
            ));
        }

        List<BackendSyncRequestPayload.ArenaSnapshot> arenas = new ArrayList<>();
        for (ArenaDefinition arena : arenaService.list()) {
            arenas.add(new BackendSyncRequestPayload.ArenaSnapshot(
                arena.arenaId(),
                arena.displayName(),
                arena.destinationConnectionAddress(),
                arena.destinationTargetId(),
                arena.instanceTemplateId(),
                arena.matchResolutionTriggerId(),
                arena.maxSupportedPlayers(),
                arena.enabled()
            ));
        }

        List<BackendSyncRequestPayload.ActiveMatchSnapshot> activeMatches = new ArrayList<>();
        for (ArenaActiveMatch match : arenaMatchService.listMatches()) {
            activeMatches.add(new BackendSyncRequestPayload.ActiveMatchSnapshot(
                match.matchId(),
                match.queueId(),
                match.arenaId(),
                match.expectedPlayerCount(),
                match.arrivedPlayerUuids().size(),
                match.activePlayerUuids().size(),
                match.createdAtEpochMs(),
                match.lastUpdatedAtEpochMs(),
                match.lastError()
            ));
        }

        return new BackendSyncRequestPayload(
            SCHEMA_VERSION,
            syncId,
            sequence,
            nowEpochMs,
            localIdentity.serverId().toString(),
            new BackendSyncRequestPayload.ServerSnapshot(
                localIdentity.fingerprint(),
                localConnectionAddressService.getConnectionAddressOrBlank(),
                networkLobbyService.isCurrentServerLobby() ? "LOBBY" : "MEMBER",
                config.region()
            ),
            List.copyOf(queues),
            List.copyOf(arenas),
            List.copyOf(activeMatches),
            assignmentStore.listPendingAcks()
        );
    }

    @Nonnull
    private BackendSyncRequestPayload.RuntimeSnapshot buildRuntimeSnapshot(@Nonnull QueueRuntimeState runtime) {
        return new BackendSyncRequestPayload.RuntimeSnapshot(
            runtime.phase().name(),
            runtime.countdownEndsAtEpochMs(),
            runtime.readyAtEpochMs(),
            runtime.lastStateChangeEpochMs(),
            runtime.lastLaunchAttemptAtEpochMs(),
            runtime.lastLaunchError(),
            buildMemberSnapshots(runtime.waitingMembers()),
            buildMemberSnapshots(runtime.readyMembers())
        );
    }

    @Nonnull
    private List<BackendSyncRequestPayload.QueueMemberSnapshot> buildMemberSnapshots(@Nonnull List<QueueMemberState> members) {
        List<BackendSyncRequestPayload.QueueMemberSnapshot> snapshots = new ArrayList<>();
        for (QueueMemberState member : members) {
            snapshots.add(new BackendSyncRequestPayload.QueueMemberSnapshot(
                member.playerUuid().toString(),
                member.playerNameSnapshot(),
                member.sourceLobbyId(),
                member.sourcePortalId(),
                member.joinedAtEpochMs()
            ));
        }
        return List.copyOf(snapshots);
    }

    @Nonnull
    private BackendSyncHttpResult toHttpResult(
        @Nonnull String syncId,
        long sequence,
        HttpResponse<String> response,
        Throwable throwable
    ) {
        if (throwable != null) {
            Throwable unwrapped = unwrap(throwable);
            return BackendSyncHttpResult.failure(syncId, sequence, 0, unwrapped.getClass().getSimpleName(), "Backend sync request failed.");
        }
        if (response == null) {
            return BackendSyncHttpResult.failure(syncId, sequence, 0, "NO_RESPONSE", "Backend sync returned no response.");
        }
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            return BackendSyncHttpResult.failure(syncId, sequence, statusCode, "HTTP", "Backend sync returned non-success status.");
        }
        try {
            BackendSyncResponsePayload parsed = gson.fromJson(response.body(), BackendSyncResponsePayload.class);
            if (parsed == null) {
                return BackendSyncHttpResult.failure(syncId, sequence, statusCode, "PARSE", "Backend sync response was empty.");
            }
            return BackendSyncHttpResult.success(syncId, sequence, statusCode, parsed);
        } catch (RuntimeException exception) {
            return BackendSyncHttpResult.failure(syncId, sequence, statusCode, exception.getClass().getSimpleName(), "Backend sync response could not be parsed.");
        }
    }

    @Nonnull
    private List<UUID> parsePlayerUuids(List<String> rawPlayerUuids) {
        if (rawPlayerUuids == null || rawPlayerUuids.isEmpty()) {
            throw new IllegalArgumentException("Assignment must include at least one player.");
        }
        Set<UUID> parsed = new HashSet<>();
        List<UUID> ordered = new ArrayList<>();
        for (String rawPlayerUuid : rawPlayerUuids) {
            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(normalize(rawPlayerUuid));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Assignment includes an invalid player UUID.");
            }
            if (!parsed.add(playerUuid)) {
                throw new IllegalArgumentException("Assignment includes duplicate players.");
            }
            ordered.add(playerUuid);
        }
        return List.copyOf(ordered);
    }

    @Nonnull
    private String hashAssignment(@Nonnull BackendAssignmentPayload assignment) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(gson.toJson(assignment).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("sha256:");
            for (byte value : hash) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256:" + Integer.toHexString(gson.toJson(assignment).hashCode());
        }
    }

    @Nonnull
    private static Throwable unwrap(@Nonnull Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }

    public record BackendSyncLogEntry(
        long completedAtEpochMs,
        String method,
        String path,
        String syncId,
        long sequence,
        int statusCode,
        String outcome,
        String errorClass,
        String message,
        int assignmentCount,
        int acknowledgedAckCount
    ) {
    }
}
