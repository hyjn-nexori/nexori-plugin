package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerReturnTarget;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionService;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionState;
import io.github.hyjn.nexori.plugin.minigame.NexoriMatchIds;
import io.github.hyjn.nexori.plugin.minigame.PlayerUuidLists;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.SourceContextId;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds minigame launch contexts without performing travel or runtime side effects.
 */
public final class MinigameLaunchContextFactory {

    public static final String ASSIGNMENT_TYPE_INITIAL_MATCH = "INITIAL_MATCH";
    public static final String ASSIGNMENT_TYPE_BACKFILL = "BACKFILL";
    private static final Gson GSON = new Gson();

    @Nonnull
    public MinigameLaunchContextBuildResult buildBackfillLaunchContext(
        @Nonnull QueueDefinition queue,
        @Nonnull String arenaId,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String matchId,
        @Nonnull String externalMatchId,
        @Nonnull List<AssignmentPlayerTicket> assignmentPlayerTickets,
        @Nonnull String reportingServerId,
        @Nonnull String returnConnectionAddress
    ) {
        return buildBackfillLaunchContext(
            queue, null, arenaId, readyMembers, nowEpochMs, assignmentId,
            matchId, externalMatchId, assignmentPlayerTickets, reportingServerId, returnConnectionAddress
        );
    }

    /**
     * Builds a BACKFILL launch context enriched with the same arena metadata as an initial-match
     * context.  The arena definition is optional; if null, the context is built without instance
     * template and arena-level fields (same as the legacy overload).
     */
    @Nonnull
    public MinigameLaunchContextBuildResult buildBackfillLaunchContext(
        @Nonnull QueueDefinition queue,
        @Nullable ArenaDefinition arena,
        @Nonnull String arenaId,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String matchId,
        @Nonnull String externalMatchId,
        @Nonnull List<AssignmentPlayerTicket> assignmentPlayerTickets,
        @Nonnull String reportingServerId,
        @Nonnull String returnConnectionAddress
    ) {
        if (readyMembers.isEmpty()) {
            throw new IllegalStateException("Cannot build a BACKFILL launch context for an empty assignment.");
        }
        String originLobbyId = normalizeSourceContextId(readyMembers.get(0).sourceLobbyId());
        if (returnConnectionAddress.isBlank()) {
            throw new IllegalStateException("This server does not have a local connection address configured for minigame return.");
        }
        String originReturnTargetId = defaultReturnTargetId(originLobbyId);
        LinkedHashMap<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid = playerReturnTargets(
            readyMembers,
            returnConnectionAddress,
            queue.launchTravelProfileId()
        );
        LinkedHashMap<UUID, AssignmentPlayerTicket> assignmentTicketsByPlayerUuid = assignmentTicketsByPlayerUuid(assignmentPlayerTickets);

        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.launch");
        root.addProperty("assignmentType", ASSIGNMENT_TYPE_BACKFILL);
        root.addProperty("assignmentId", assignmentId);
        root.addProperty("matchId", matchId);
        if (externalMatchId != null && !externalMatchId.isBlank()) {
            root.addProperty("externalMatchId", externalMatchId);
        }
        root.addProperty("queueId", queue.queueId());
        root.addProperty("arenaId", arena != null ? arena.arenaId() : arenaId);
        if (reportingServerId != null && !reportingServerId.isBlank()) {
            root.addProperty("reportingServerId", reportingServerId.trim());
        }
        root.addProperty("originLobbyId", originLobbyId);
        root.addProperty("returnConnectionAddress", returnConnectionAddress);
        root.addProperty("returnFallbackTargetId", originReturnTargetId);
        root.addProperty("launchTravelProfileId", queue.launchTravelProfileId());

        // Include the same arena-level metadata as an initial-match context so the unified
        // MinigameTransferService can handle BACKFILL using the same code path.
        if (arena != null) {
            root.addProperty("instanceTemplateId", arena.instanceTemplateId());
            root.addProperty("rulesEngineId", arena.rulesEngineId());
            QueueBackfillMode backfillMode = queue.effectiveBackfillMode();
            int admissionCapacity = Math.max(queue.maxPlayers(), 0);
            int arenaCapacity = Math.max(arena.maxSupportedPlayers(), 0);
            if (arenaCapacity > 0) {
                admissionCapacity = Math.min(admissionCapacity, arenaCapacity);
            }
            root.addProperty(
                "matchSource",
                queue.effectiveMatchmakingMode() == QueueMatchmakingMode.BACKEND_DRIVEN
                    ? ArenaMatchSource.BACKEND_DRIVEN.id()
                    : ArenaMatchSource.LOCAL_FIFO.id()
            );
            root.addProperty("admissionCapacity", admissionCapacity);
            root.addProperty("backfillEnabled", queue.backfillEnabled());
            root.addProperty("backfillMode", backfillMode.id());
            root.addProperty("backfillWindowSeconds", Math.max(queue.backfillWindowSeconds(), 0));
            root.add("afkDetectionPolicy", afkDetectionPolicyJson(arena.afkDetectionPolicy()));
            if (arena.usesInstanceTemplate()) {
                root.addProperty("serverEntryMode", "default_world_natural_spawn");
            }
        }

        MatchSessionState matchSessionState = new MatchSessionState(
            matchId,
            queue.queueId(),
            arenaId,
            originLobbyId,
            returnConnectionAddress,
            originReturnTargetId,
            queue.launchTravelProfileId(),
            List.of(),
            List.of(),
            nowEpochMs,
            nowEpochMs,
            0L,
            nowEpochMs + MatchSessionService.PREPARED_SESSION_GRACE_MS,
            ""
        ).normalized();
        return new MinigameLaunchContextBuildResult(
            matchId,
            GSON.toJson(root),
            matchSessionState,
            Map.copyOf(playerReturnTargetsByUuid),
            ASSIGNMENT_TYPE_BACKFILL,
            Map.copyOf(assignmentTicketsByPlayerUuid),
            reportingServerId == null ? "" : reportingServerId.trim()
        );
    }

    @Nonnull
    public MinigameLaunchContextBuildResult buildInitialMatchLaunchContext(
        @Nonnull QueueDefinition queue,
        @Nonnull ArenaDefinition arena,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String assignmentType,
        @Nonnull String matchIdOverride,
        @Nonnull String generatedMatchId,
        @Nonnull String externalMatchId,
        @Nonnull List<UUID> expectedPlayerUuidsOverride,
        @Nonnull List<AssignmentPlayerTicket> assignmentPlayerTickets,
        @Nonnull String reportingServerId,
        @Nonnull String returnConnectionAddress,
        int admissionPolicySchemaVersion
    ) {
        if (readyMembers.isEmpty()) {
            throw new IllegalStateException("Cannot build a launch context for an empty ready batch.");
        }
        String originLobbyId = normalizeSourceContextId(readyMembers.get(0).sourceLobbyId());
        if (returnConnectionAddress.isBlank()) {
            throw new IllegalStateException("This server does not have a local connection address configured for minigame return.");
        }
        String originReturnTargetId = defaultReturnTargetId(originLobbyId);
        String matchId = matchIdOverride != null && !matchIdOverride.isBlank()
            ? NexoriMatchIds.normalizeBackendOwnedMatchId(matchIdOverride)
            : NexoriMatchIds.normalizeGeneratedMatchId(generatedMatchId);
        QueueBackfillMode backfillMode = queue.effectiveBackfillMode();
        int admissionCapacity = Math.max(queue.maxPlayers(), 0);
        int arenaCapacity = Math.max(arena.maxSupportedPlayers(), 0);
        if (arenaCapacity > 0) {
            admissionCapacity = Math.min(admissionCapacity, arenaCapacity);
        }
        List<UUID> expectedPlayerUuids;
        if (expectedPlayerUuidsOverride != null && !expectedPlayerUuidsOverride.isEmpty()) {
            expectedPlayerUuids = PlayerUuidLists.canonicalize(expectedPlayerUuidsOverride);
        } else if (ASSIGNMENT_TYPE_BACKFILL.equals(assignmentType)) {
            expectedPlayerUuids = List.of();
        } else {
            expectedPlayerUuids = PlayerUuidLists.canonicalize(readyMembers.stream().map(QueueMemberState::playerUuid).toList());
        }

        LinkedHashMap<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid = playerReturnTargets(
            readyMembers,
            returnConnectionAddress,
            queue.launchTravelProfileId()
        );
        LinkedHashMap<UUID, AssignmentPlayerTicket> assignmentTicketsByPlayerUuid = assignmentTicketsByPlayerUuid(assignmentPlayerTickets);

        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.launch");
        root.addProperty("assignmentType", assignmentType);
        root.addProperty("matchId", matchId);
        root.addProperty("queueId", queue.queueId());
        root.addProperty("arenaId", arena.arenaId());
        root.addProperty("originLobbyId", originLobbyId);
        root.addProperty("returnConnectionAddress", returnConnectionAddress);
        root.addProperty("returnFallbackTargetId", originReturnTargetId);
        root.addProperty("launchTravelProfileId", queue.launchTravelProfileId());
        root.addProperty("instanceTemplateId", arena.instanceTemplateId());
        root.addProperty("rulesEngineId", arena.rulesEngineId());
        root.addProperty("expectedPlayerCount", expectedPlayerUuids.size());
        root.addProperty("admissionPolicySchemaVersion", admissionPolicySchemaVersion);
        root.addProperty(
            "matchSource",
            queue.effectiveMatchmakingMode() == QueueMatchmakingMode.BACKEND_DRIVEN
                ? ArenaMatchSource.BACKEND_DRIVEN.id()
                : ArenaMatchSource.LOCAL_FIFO.id()
        );
        root.addProperty("admissionCapacity", admissionCapacity);
        root.addProperty("backfillEnabled", queue.backfillEnabled());
        root.addProperty("backfillMode", backfillMode.id());
        root.addProperty("backfillWindowSeconds", Math.max(queue.backfillWindowSeconds(), 0));
        root.add("afkDetectionPolicy", afkDetectionPolicyJson(arena.afkDetectionPolicy()));
        JsonArray expectedPlayerUuidsJson = new JsonArray();
        for (UUID expectedPlayerUuid : expectedPlayerUuids) {
            expectedPlayerUuidsJson.add(expectedPlayerUuid.toString());
        }
        root.add("expectedPlayerUuids", expectedPlayerUuidsJson);
        root.addProperty("launchedAtEpochMs", nowEpochMs);
        if (assignmentId != null && !assignmentId.isBlank()) {
            root.addProperty("assignmentId", assignmentId);
        }
        if (externalMatchId != null && !externalMatchId.isBlank()) {
            root.addProperty("externalMatchId", externalMatchId);
        }
        if (reportingServerId != null && !reportingServerId.isBlank()) {
            root.addProperty("reportingServerId", reportingServerId);
        }
        if (arena.usesInstanceTemplate()) {
            root.addProperty("serverEntryMode", "default_world_natural_spawn");
        }
        MatchSessionState matchSessionState = new MatchSessionState(
            matchId,
            queue.queueId(),
            arena.arenaId(),
            originLobbyId,
            returnConnectionAddress,
            originReturnTargetId,
            queue.launchTravelProfileId(),
            PlayerUuidLists.canonicalize(readyMembers.stream().map(QueueMemberState::playerUuid).toList()),
            List.of(),
            nowEpochMs,
            nowEpochMs,
            0L,
            nowEpochMs + MatchSessionService.PREPARED_SESSION_GRACE_MS,
            ""
        ).normalized();
        return new MinigameLaunchContextBuildResult(
            matchId,
            GSON.toJson(root),
            matchSessionState,
            Map.copyOf(playerReturnTargetsByUuid),
            assignmentType,
            Map.copyOf(assignmentTicketsByPlayerUuid),
            reportingServerId == null ? "" : reportingServerId.trim()
        );
    }

    @Nonnull
    private static JsonObject afkDetectionPolicyJson(AfkDetectionPolicy rawPolicy) {
        AfkDetectionPolicy policy = AfkDetectionPolicy.normalize(rawPolicy);
        JsonObject json = new JsonObject();
        json.addProperty("enabled", policy.enabled());
        json.addProperty("inactivityTimeoutSeconds", policy.inactivityTimeoutSeconds());
        return json;
    }

    @Nonnull
    public String contextJsonWithLaunchIndex(
        @Nonnull String baseContextJson,
        int launchIndex,
        @Nonnull QueueMemberState member,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        @Nonnull String assignmentType,
        @Nonnull Map<UUID, AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
        @Nonnull String reportingServerId
    ) {
        JsonObject root = GSON.fromJson(baseContextJson, JsonObject.class);
        if (root == null) {
            root = new JsonObject();
        }
        root.addProperty("launchIndex", Math.max(launchIndex, 0));
        root.addProperty("assignmentType", assignmentType);
        addPerPlayerFields(root, member, playerReturnTargetsByUuid, assignmentType, assignmentPlayerTicketsByUuid, reportingServerId);
        return GSON.toJson(root);
    }

    @Nonnull
    public String backfillContextJsonForPlayer(
        @Nonnull String baseContextJson,
        @Nonnull QueueMemberState member,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        @Nonnull Map<UUID, AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
        @Nonnull String reportingServerId
    ) {
        JsonObject root = GSON.fromJson(baseContextJson, JsonObject.class);
        if (root == null) {
            root = new JsonObject();
        }
        root.addProperty("assignmentType", ASSIGNMENT_TYPE_BACKFILL);
        addPerPlayerFields(root, member, playerReturnTargetsByUuid, ASSIGNMENT_TYPE_BACKFILL, assignmentPlayerTicketsByUuid, reportingServerId);
        return GSON.toJson(root);
    }

    private static void addPerPlayerFields(
        @Nonnull JsonObject root,
        @Nonnull QueueMemberState member,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        @Nonnull String assignmentType,
        @Nonnull Map<UUID, AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
        @Nonnull String reportingServerId
    ) {
        ArenaPlayerReturnTarget returnTarget = playerReturnTargetsByUuid.get(member.playerUuid());
        if (returnTarget == null) {
            throw new IllegalStateException("Missing per-player return target for launched player " + member.playerUuid() + ".");
        }
        root.addProperty("originLobbyId", returnTarget.originLobbyId());
        root.addProperty("returnConnectionAddress", returnTarget.returnConnectionAddress());
        root.addProperty("returnFallbackTargetId", returnTarget.returnFallbackTargetId());
        root.addProperty("launchTravelProfileId", returnTarget.launchTravelProfileId());
        if (ASSIGNMENT_TYPE_BACKFILL.equals(assignmentType)) {
            AssignmentPlayerTicket ticket = assignmentPlayerTicketsByUuid.get(member.playerUuid());
            if (ticket == null) {
                throw new IllegalStateException("Missing backfill ticket for launched player " + member.playerUuid() + ".");
            }
            root.addProperty("playerUuid", member.playerUuid().toString());
            root.addProperty("admissionReservationId", ticket.admissionReservationId());
            root.addProperty("admissionExpiresAtEpochMs", ticket.admissionExpiresAtEpochMs());
            if (!reportingServerId.isBlank()) {
                root.addProperty("reportingServerId", reportingServerId);
            }
        }
    }

    @Nonnull
    private static LinkedHashMap<UUID, ArenaPlayerReturnTarget> playerReturnTargets(
        @Nonnull List<QueueMemberState> readyMembers,
        @Nonnull String returnConnectionAddress,
        @Nonnull String launchTravelProfileId
    ) {
        LinkedHashMap<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid = new LinkedHashMap<>();
        for (QueueMemberState member : readyMembers) {
            String memberSourceContextId = normalizeSourceContextId(member.sourceLobbyId());
            playerReturnTargetsByUuid.put(
                member.playerUuid(),
                new ArenaPlayerReturnTarget(
                    memberSourceContextId,
                    returnConnectionAddress,
                    defaultReturnTargetId(memberSourceContextId),
                    launchTravelProfileId
                ).normalized()
            );
        }
        return playerReturnTargetsByUuid;
    }

    @Nonnull
    private static LinkedHashMap<UUID, AssignmentPlayerTicket> assignmentTicketsByPlayerUuid(List<AssignmentPlayerTicket> assignmentPlayerTickets) {
        LinkedHashMap<UUID, AssignmentPlayerTicket> assignmentTicketsByPlayerUuid = new LinkedHashMap<>();
        if (assignmentPlayerTickets != null) {
            for (AssignmentPlayerTicket ticket : assignmentPlayerTickets) {
                if (ticket != null && ticket.playerUuid() != null) {
                    assignmentTicketsByPlayerUuid.put(ticket.playerUuid(), ticket.normalized());
                }
            }
        }
        return assignmentTicketsByPlayerUuid;
    }

    @Nonnull
    private static String normalizeSourceContextId(@Nonnull String rawSourceContextId) {
        return SourceContextId.normalizeId(rawSourceContextId);
    }

    @Nonnull
    private static String defaultReturnTargetId(@Nonnull String sourceContextId) {
        return normalizeSourceContextId(sourceContextId) + ".natural_spawn";
    }

    public record AssignmentPlayerTicket(
        UUID playerUuid,
        String admissionReservationId,
        long admissionExpiresAtEpochMs
    ) {

        @Nonnull
        public AssignmentPlayerTicket normalized() {
            if (playerUuid == null) {
                throw new IllegalArgumentException("Assignment playerUuid cannot be null.");
            }
            return new AssignmentPlayerTicket(
                playerUuid,
                admissionReservationId == null ? "" : admissionReservationId.trim(),
                Math.max(0L, admissionExpiresAtEpochMs)
            );
        }
    }
}
