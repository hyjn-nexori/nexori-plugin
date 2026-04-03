package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ArenaMatchService {

    private static final Gson GSON = new Gson();

    private final HytaleLogger logger;
    private final SecureTravelService secureTravelService;
    private final Map<String, ArenaActiveMatch> matchesById = new LinkedHashMap<>();
    private final Map<UUID, String> matchIdByPlayerUuid = new LinkedHashMap<>();

    public ArenaMatchService(
        @Nonnull HytaleLogger logger,
        @Nonnull SecureTravelService secureTravelService
    ) {
        this.logger = logger;
        this.secureTravelService = secureTravelService;
    }

    public synchronized void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingArrival arrival = secureTravelService.consumeRecentArrival(playerRef.getUuid()).orElse(null);
        if (arrival == null) {
            return;
        }

        JsonObject context = parseContext(arrival.contextJson());
        if (context == null || !context.has("flowType")) {
            return;
        }

        String flowType = context.get("flowType").getAsString();
        if ("minigame.launch".equalsIgnoreCase(flowType)) {
            handleLaunchArrival(playerRef, context);
            return;
        }
        if ("minigame.return".equalsIgnoreCase(flowType)) {
            handleReturnArrival(playerRef, context);
        }
    }

    @Nonnull
    public synchronized List<ArenaActiveMatch> listMatches() {
        return matchesById.values().stream()
            .sorted(Comparator.comparing(ArenaActiveMatch::matchId))
            .toList();
    }

    @Nonnull
    public synchronized Optional<ArenaActiveMatch> find(@Nonnull String rawMatchId) {
        if (rawMatchId == null || rawMatchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchesById.get(rawMatchId.trim().toLowerCase()));
    }

    @Nonnull
    public synchronized EndMatchResult endMatch(@Nonnull String rawMatchId, @Nonnull String rawReason) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return EndMatchResult.matchMissing(matchId);
        }

        ConfiguredPeer destination;
        try {
            destination = ConfiguredPeer.parse(match.returnConnectionAddress());
        } catch (IllegalArgumentException exception) {
            ArenaActiveMatch updated = new ArenaActiveMatch(
                match.matchId(),
                match.queueId(),
                match.arenaId(),
                match.originLobbyId(),
                match.returnConnectionAddress(),
                match.returnFallbackTargetId(),
                match.launchTravelProfileId(),
                match.playerUuids(),
                match.createdAtEpochMs(),
                System.currentTimeMillis(),
                exception.getMessage()
            ).normalized();
            matchesById.put(updated.matchId(), updated);
            return EndMatchResult.failed(updated, exception.getMessage());
        }

        long now = System.currentTimeMillis();
        String returnReason = normalizeOptional(rawReason, "MATCH_ENDED");
        List<UUID> returnedPlayers = new ArrayList<>();
        String failureMessage = "";
        for (UUID playerUuid : match.playerUuids()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                returnedPlayers.add(playerUuid);
                continue;
            }
            try {
                secureTravelService.travel(
                    playerRef,
                    destination,
                    match.returnFallbackTargetId(),
                    "",
                    match.launchTravelProfileId(),
                    buildReturnContextJson(match, returnReason, now)
                );
                returnedPlayers.add(playerUuid);
            } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                failureMessage = exception.getMessage();
                logger.atWarning().withCause(exception).log(
                    "Failed to return Nexori match " + match.matchId() + " to lobby " + match.originLobbyId() + "."
                );
                break;
            }
        }

        if (failureMessage.isBlank()) {
            removeMatchPlayers(match.matchId(), returnedPlayers);
            matchesById.remove(match.matchId());
            return EndMatchResult.completed(match.matchId(), returnedPlayers.size());
        }

        removeMatchPlayers(match.matchId(), returnedPlayers);
        List<UUID> remainingPlayers = new ArrayList<>(match.playerUuids());
        remainingPlayers.removeAll(returnedPlayers);
        ArenaActiveMatch updated = new ArenaActiveMatch(
            match.matchId(),
            match.queueId(),
            match.arenaId(),
            match.originLobbyId(),
            match.returnConnectionAddress(),
            match.returnFallbackTargetId(),
            match.launchTravelProfileId(),
            List.copyOf(remainingPlayers),
            match.createdAtEpochMs(),
            now,
            failureMessage
        ).normalized();
        matchesById.put(updated.matchId(), updated);
        return EndMatchResult.failed(updated, failureMessage);
    }

    private void handleLaunchArrival(@Nonnull PlayerRef playerRef, @Nonnull JsonObject context) {
        LaunchContext launch = LaunchContext.from(context);
        long now = System.currentTimeMillis();
        ArenaActiveMatch existing = matchesById.get(launch.matchId());
        ArenaActiveMatch updated = existing == null
            ? new ArenaActiveMatch(
                launch.matchId(),
                launch.queueId(),
                launch.arenaId(),
                launch.originLobbyId(),
                launch.returnConnectionAddress(),
                launch.returnFallbackTargetId(),
                launch.launchTravelProfileId(),
                List.of(playerRef.getUuid()),
                now,
                now,
                ""
            ).normalized()
            : existing.withPlayer(playerRef.getUuid(), now);

        String previousMatchId = matchIdByPlayerUuid.put(playerRef.getUuid(), updated.matchId());
        if (previousMatchId != null && !previousMatchId.equals(updated.matchId())) {
            ArenaActiveMatch previous = matchesById.get(previousMatchId);
            if (previous != null) {
                List<UUID> remaining = new ArrayList<>(previous.playerUuids());
                remaining.remove(playerRef.getUuid());
                if (remaining.isEmpty()) {
                    matchesById.remove(previousMatchId);
                } else {
                    matchesById.put(previousMatchId, new ArenaActiveMatch(
                        previous.matchId(),
                        previous.queueId(),
                        previous.arenaId(),
                        previous.originLobbyId(),
                        previous.returnConnectionAddress(),
                        previous.returnFallbackTargetId(),
                        previous.launchTravelProfileId(),
                        List.copyOf(remaining),
                        previous.createdAtEpochMs(),
                        now,
                        previous.lastError()
                    ).normalized());
                }
            }
        }
        matchesById.put(updated.matchId(), updated);
        playerRef.sendMessage(Message.raw(
            "Joined Nexori match " + updated.matchId() + " on arena " + updated.arenaId() + "."
        ));
    }

    private void handleReturnArrival(@Nonnull PlayerRef playerRef, @Nonnull JsonObject context) {
        String queueId = readRequired(context, "queueId");
        String sourceArenaId = readRequired(context, "sourceArenaId");
        String reason = readRequired(context, "returnReason");
        playerRef.sendMessage(Message.raw(
            "Returned from Nexori arena " + sourceArenaId + " queue=" + queueId + " reason=" + reason + "."
        ));
    }

    private void removeMatchPlayers(@Nonnull String matchId, @Nonnull List<UUID> playerUuids) {
        for (UUID playerUuid : playerUuids) {
            String currentMatchId = matchIdByPlayerUuid.get(playerUuid);
            if (matchId.equals(currentMatchId)) {
                matchIdByPlayerUuid.remove(playerUuid);
            }
        }
    }

    @Nonnull
    private String buildReturnContextJson(@Nonnull ArenaActiveMatch match, @Nonnull String returnReason, long nowEpochMs) {
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.return");
        root.addProperty("matchId", match.matchId());
        root.addProperty("queueId", match.queueId());
        root.addProperty("originLobbyId", match.originLobbyId());
        root.addProperty("sourceArenaId", match.arenaId());
        root.addProperty("returnReason", returnReason);
        root.addProperty("returnedAtEpochMs", nowEpochMs);
        return GSON.toJson(root);
    }

    private JsonObject parseContext(String rawContextJson) {
        if (rawContextJson == null || rawContextJson.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(rawContextJson, JsonObject.class);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to parse Nexori minigame travel context JSON.");
            return null;
        }
    }

    @Nonnull
    private static String readRequired(@Nonnull JsonObject root, @Nonnull String key) {
        if (!root.has(key)) {
            throw new IllegalArgumentException("Missing required minigame context field '" + key + "'.");
        }
        return normalizeRequired(root.get(key).getAsString(), "Minigame context field '" + key + "' cannot be blank.");
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

    private record LaunchContext(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId
    ) {

        @Nonnull
        private static LaunchContext from(@Nonnull JsonObject root) {
            return new LaunchContext(
                readRequired(root, "matchId").toLowerCase(),
                QueueDefinition.normalizeId(readRequired(root, "queueId")),
                ArenaDefinition.normalizeId(readRequired(root, "arenaId")),
                LobbyDefinition.normalizeId(readRequired(root, "originLobbyId")),
                readRequired(root, "returnConnectionAddress"),
                readRequired(root, "returnFallbackTargetId"),
                readRequired(root, "launchTravelProfileId").toLowerCase()
            );
        }
    }

    public enum EndMatchOutcome {
        COMPLETED,
        MATCH_MISSING,
        FAILED
    }

    public record EndMatchResult(
        EndMatchOutcome outcome,
        String matchId,
        int returnedPlayerCount,
        String errorMessage,
        ArenaActiveMatch activeMatch
    ) {

        @Nonnull
        public static EndMatchResult completed(@Nonnull String matchId, int returnedPlayerCount) {
            return new EndMatchResult(EndMatchOutcome.COMPLETED, matchId, returnedPlayerCount, "", null);
        }

        @Nonnull
        public static EndMatchResult matchMissing(@Nonnull String matchId) {
            return new EndMatchResult(EndMatchOutcome.MATCH_MISSING, normalizeRequired(matchId, "Match id cannot be blank."), 0, "", null);
        }

        @Nonnull
        public static EndMatchResult failed(@Nonnull ArenaActiveMatch activeMatch, @Nonnull String errorMessage) {
            return new EndMatchResult(EndMatchOutcome.FAILED, activeMatch.matchId(), 0, normalizeOptional(errorMessage, "Unknown match end failure."), activeMatch);
        }
    }
}
