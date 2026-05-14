package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerReturnTarget;
import io.github.hyjn.nexori.plugin.minigame.NexoriMatchIds;
import io.github.hyjn.nexori.plugin.minigame.PlayerUuidLists;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.SourceContextId;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure parser for Nexori minigame launch travel contexts.
 */
public final class LaunchContextParser {

    private static final String ASSIGNMENT_TYPE_INITIAL_MATCH = "INITIAL_MATCH";

    @Nonnull
    public LaunchContextData parse(@Nonnull JsonObject root) {
        return new LaunchContextData(
            NexoriMatchIds.normalizeRequiredMatchId(readRequired(root, "matchId"), "Minigame context field 'matchId' cannot be blank."),
            QueueDefinition.normalizeId(readRequired(root, "queueId")),
            ArenaDefinition.normalizeId(readRequired(root, "arenaId")),
            SourceContextId.normalizeId(readRequired(root, "originLobbyId")),
            readRequired(root, "returnConnectionAddress"),
            readRequired(root, "returnFallbackTargetId"),
            readRequired(root, "launchTravelProfileId").toLowerCase(),
            root.has("instanceTemplateId")
                ? normalizeOptional(root.get("instanceTemplateId").getAsString(), ArenaDefinition.NO_INSTANCE_TEMPLATE_ID)
                : ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            root.has("matchResolutionTriggerId")
                ? normalizeOptional(root.get("matchResolutionTriggerId").getAsString(), ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID).toLowerCase()
                : ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID,
            root.has("rulesEngineId")
                ? ArenaDefinition.normalizeRulesEngineId(root.get("rulesEngineId").getAsString())
                : "",
            root.has("assignmentId")
                ? normalizeOptional(root.get("assignmentId").getAsString(), "")
                : "",
            root.has("assignmentType")
                ? normalizeOptional(root.get("assignmentType").getAsString(), ASSIGNMENT_TYPE_INITIAL_MATCH)
                : ASSIGNMENT_TYPE_INITIAL_MATCH,
            root.has("externalMatchId")
                ? normalizeOptional(root.get("externalMatchId").getAsString(), "")
                : "",
            root.has("matchSource")
                ? normalizeOptional(root.get("matchSource").getAsString(), ArenaMatchSource.defaultSource().id())
                : ArenaMatchSource.defaultSource().id(),
            root.has("admissionPolicySchemaVersion") ? Math.max(root.get("admissionPolicySchemaVersion").getAsInt(), 0) : 0,
            root.has("admissionCapacity") ? Math.max(root.get("admissionCapacity").getAsInt(), 0) : 0,
            root.has("backfillEnabled") && root.get("backfillEnabled").getAsBoolean(),
            root.has("backfillMode")
                ? normalizeOptional(root.get("backfillMode").getAsString(), QueueBackfillMode.defaultMode().id())
                : QueueBackfillMode.defaultMode().id(),
            root.has("backfillWindowSeconds") ? Math.max(root.get("backfillWindowSeconds").getAsInt(), 0) : 0,
            readExpectedPlayerUuids(root),
            root.has("expectedPlayerCount") ? Math.max(root.get("expectedPlayerCount").getAsInt(), 0) : 0,
            readOptionalUuid(root, "playerUuid"),
            root.has("admissionReservationId")
                ? normalizeOptional(root.get("admissionReservationId").getAsString(), "")
                : "",
            root.has("admissionExpiresAtEpochMs") ? Math.max(root.get("admissionExpiresAtEpochMs").getAsLong(), 0L) : 0L,
            root.has("reportingServerId")
                ? normalizeOptional(root.get("reportingServerId").getAsString(), "")
                : "",
            new ArenaPlayerReturnTarget(
                SourceContextId.normalizeId(readRequired(root, "originLobbyId")),
                readRequired(root, "returnConnectionAddress"),
                readRequired(root, "returnFallbackTargetId"),
                readRequired(root, "launchTravelProfileId").toLowerCase()
            ).normalized()
        );
    }

    @Nonnull
    private static String readRequired(@Nonnull JsonObject root, @Nonnull String key) {
        if (!root.has(key)) {
            throw new IllegalArgumentException("Missing required minigame context field '" + key + "'.");
        }
        return normalizeRequired(root.get(key).getAsString(), "Minigame context field '" + key + "' cannot be blank.");
    }

    @Nonnull
    private static List<UUID> readExpectedPlayerUuids(@Nonnull JsonObject root) {
        if (!root.has("expectedPlayerUuids")) {
            return List.of();
        }
        if (!root.get("expectedPlayerUuids").isJsonArray()) {
            throw new IllegalArgumentException("Minigame context field 'expectedPlayerUuids' must be an array.");
        }
        JsonArray array = root.getAsJsonArray("expectedPlayerUuids");
        List<UUID> playerUuids = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            String rawUuid = normalizeOptional(element.getAsString(), "");
            if (rawUuid.isBlank()) {
                continue;
            }
            try {
                playerUuids.add(UUID.fromString(rawUuid));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Minigame context field 'expectedPlayerUuids' contains invalid UUID '" + rawUuid + "'.", exception);
            }
        }
        return PlayerUuidLists.canonicalize(playerUuids);
    }

    private static UUID readOptionalUuid(@Nonnull JsonObject root, @Nonnull String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        try {
            return UUID.fromString(root.get(key).getAsString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Minigame context field '" + key + "' contains invalid UUID.", exception);
        }
    }

    @Nonnull
    private static String normalizeRequired(@Nonnull String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }
}
