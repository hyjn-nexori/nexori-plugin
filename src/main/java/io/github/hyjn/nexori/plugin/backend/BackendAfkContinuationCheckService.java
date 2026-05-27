package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkContinuationDecision;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkContinuationDecisionType;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.AfkActivityService;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sends an AFK continuation check to the backend when a player enters AFK in an active match,
 * and stores the resulting {@link NexoriAfkContinuationDecisionType#CONTINUE} /
 * {@link NexoriAfkContinuationDecisionType#CANCEL} decision for the match.
 *
 * <p>Checks are only sent when {@link BackendMatchmakingConfig#afkContinuationCheckEnabled()}
 * is {@code true} and {@code afk=true} on the transition. At most one check is in flight per
 * match at a time (subsequent AFK events from the same match are dropped until the response
 * arrives). A {@link NexoriAfkContinuationDecisionType#CANCEL} decision is sticky: once stored
 * for a match, no later {@code CONTINUE} response can overwrite it.</p>
 *
 * <p>HTTP responses are collected into a {@link ConcurrentLinkedQueue} from the completion
 * callback and drained on the next {@link #handleTick(long)} call on the game thread.</p>
 */
public final class BackendAfkContinuationCheckService {

    private final HytaleLogger logger;
    private BackendMatchmakingConfig config;
    private final ServerIdentity localIdentity;
    private BackendHttpTransport transport;
    private final boolean useDefaultTransport;

    private final Map<String, NexoriAfkContinuationDecision> decisions = new HashMap<>();
    private final Set<String> pendingMatchIds = new HashSet<>();
    private final ConcurrentLinkedQueue<AfkCheckCompletion> completions = new ConcurrentLinkedQueue<>();

    public BackendAfkContinuationCheckService(
        @Nonnull HytaleLogger logger,
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull ServerIdentity localIdentity
    ) {
        this.logger = logger;
        this.config = config.normalized();
        this.localIdentity = localIdentity;
        this.transport = new JdkBackendHttpTransport(this.config.requestTimeoutMs());
        this.useDefaultTransport = true;
    }

    public BackendAfkContinuationCheckService(
        HytaleLogger logger,
        @Nonnull BackendMatchmakingConfig config,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull BackendHttpTransport transport
    ) {
        this.logger = logger;
        this.config = config.normalized();
        this.localIdentity = localIdentity;
        this.transport = transport;
        this.useDefaultTransport = false;
    }

    /**
     * Enqueues a backend AFK continuation check for the transition if the feature is enabled,
     * the transition is an AFK-on event, and no check is already in flight for this match.
     *
     * <p>Safe to call from any thread.</p>
     */
    public synchronized void enqueue(@Nonnull AfkActivityService.AfkActivityTransition transition) {
        if (!config.afkContinuationCheckEnabled()) {
            return;
        }
        if (!transition.afk()) {
            return;
        }
        String matchId = transition.matchId();
        // CANCEL is sticky — no point sending another check
        NexoriAfkContinuationDecision existing = decisions.get(matchId);
        if (existing != null && existing.decision() == NexoriAfkContinuationDecisionType.CANCEL) {
            return;
        }
        // Anti-spam: one in-flight check per match at a time
        if (pendingMatchIds.contains(matchId)) {
            return;
        }
        pendingMatchIds.add(matchId);

        String body = buildRequestBody(transition);
        String checkId = UUID.randomUUID().toString().toLowerCase();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(config.afkContinuationCheckUrl()))
                .timeout(Duration.ofMillis(config.requestTimeoutMs()))
                .header("Authorization", "Bearer " + config.serverToken())
                .header("Content-Type", "application/json")
                .header("X-Nexori-Server-Id", localIdentity.serverId().toString())
                .header("X-Nexori-Afk-Check-Id", checkId)
                .header("X-Nexori-Sent-At-Epoch-Ms", Long.toString(System.currentTimeMillis()))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        } catch (IllegalArgumentException exception) {
            pendingMatchIds.remove(matchId);
            if (logger != null) {
                logger.atWarning().log(
                    "Nexori AFK continuation check skipped: invalid URL matchId=" + matchId
                );
            }
            return;
        }

        try {
            transport.sendAsync(request, config.requestTimeoutMs())
                .whenComplete((response, throwable) ->
                    completions.add(resolveCompletion(transition, response, throwable))
                );
        } catch (Throwable throwable) {
            pendingMatchIds.remove(matchId);
            if (logger != null) {
                logger.atWarning().log(
                    "Nexori AFK continuation check could not be started matchId=" + matchId
                        + " error=" + throwable.getClass().getSimpleName()
                );
            }
        }
    }

    /**
     * Drains completed HTTP responses and stores the resulting decisions.
     * Must be called from the game tick thread.
     */
    @Nonnull
    public synchronized List<NexoriAfkContinuationDecision> handleTick(long nowEpochMs) {
        List<NexoriAfkContinuationDecision> cancelDecisions = new ArrayList<>();
        AfkCheckCompletion completion;
        while ((completion = completions.poll()) != null) {
            pendingMatchIds.remove(completion.transition().matchId());
            NexoriAfkContinuationDecision decision = storeDecision(
                completion.transition(),
                completion.decisionType(),
                completion.reasonCode(),
                completion.message(),
                nowEpochMs
            );
            if (decision != null && decision.decision() == NexoriAfkContinuationDecisionType.CANCEL) {
                cancelDecisions.add(decision);
            }
        }
        return List.copyOf(cancelDecisions);
    }

    /**
     * Returns the current AFK continuation decision for the given match.
     *
     * <p>Safe to call from any thread.</p>
     */
    @Nonnull
    public synchronized NexoriAfkContinuationDecision getAfkContinuationDecision(@Nonnull String matchId) {
        String normalized = matchId == null ? "" : matchId.trim();
        if (normalized.isBlank()) {
            return NexoriAfkContinuationDecision.unavailable("");
        }
        NexoriAfkContinuationDecision decision = decisions.get(normalized);
        // CANCEL always takes precedence
        if (decision != null && decision.decision() == NexoriAfkContinuationDecisionType.CANCEL) {
            return decision;
        }
        // A check is in flight — report PENDING
        if (pendingMatchIds.contains(normalized)) {
            return new NexoriAfkContinuationDecision(
                normalized, NexoriAfkContinuationDecisionType.PENDING, null, "", "", 0L
            );
        }
        // Return stored CONTINUE (or null → UNAVAILABLE)
        return decision != null ? decision : NexoriAfkContinuationDecision.unavailable(normalized);
    }

    /**
     * Removes all decision and pending state for the given match (called when a match closes).
     */
    public synchronized void removeMatch(@Nonnull String matchId) {
        String normalized = matchId == null ? "" : matchId.trim();
        if (normalized.isBlank()) {
            return;
        }
        decisions.remove(normalized);
        pendingMatchIds.remove(normalized);
    }

    public synchronized void updateConfig(@Nonnull BackendMatchmakingConfig updatedConfig) {
        this.config = updatedConfig.normalized();
        if (useDefaultTransport) {
            this.transport = new JdkBackendHttpTransport(this.config.requestTimeoutMs());
        }
    }

    private NexoriAfkContinuationDecision storeDecision(
        @Nonnull AfkActivityService.AfkActivityTransition transition,
        @Nonnull NexoriAfkContinuationDecisionType type,
        @Nonnull String reasonCode,
        @Nonnull String message,
        long nowEpochMs
    ) {
        String matchId = transition.matchId();
        NexoriAfkContinuationDecision existing = decisions.get(matchId);
        // CANCEL is sticky: never overwrite CANCEL with anything else
        if (existing != null && existing.decision() == NexoriAfkContinuationDecisionType.CANCEL) {
            return null;
        }
        NexoriAfkContinuationDecision decision = new NexoriAfkContinuationDecision(
            matchId,
            type,
            transition.playerUuid(),
            reasonCode,
            message,
            nowEpochMs
        );
        decisions.put(matchId, decision);
        if (logger != null && (type == NexoriAfkContinuationDecisionType.CONTINUE || type == NexoriAfkContinuationDecisionType.CANCEL)) {
            logger.atInfo().log(
                "Nexori AFK continuation decision matchId=" + matchId
                    + " player=" + transition.playerName()
                    + " uuid=" + transition.playerUuid()
                    + " decision=" + type
                    + " reasonCode=" + reasonCode
            );
        }
        return decision;
    }

    @Nonnull
    private String buildRequestBody(@Nonnull AfkActivityService.AfkActivityTransition transition) {
        JsonObject body = new JsonObject();
        body.addProperty("server_id", localIdentity.serverId().toString());
        body.addProperty("match_id", transition.matchId());
        body.addProperty("queue_id", transition.queueId());
        body.addProperty("arena_id", transition.arenaId());
        body.addProperty("rules_engine_id", transition.rulesEngineId());
        body.addProperty("player_uuid", transition.playerUuid().toString());
        body.addProperty("player_name", transition.playerName());
        body.addProperty("afk", transition.afk());
        body.addProperty("changed_at_epoch_ms", transition.changedAtEpochMs());
        body.addProperty("idle_ms", transition.idleMs());
        body.addProperty("source", transition.source().name());
        return new com.google.gson.GsonBuilder().create().toJson(body);
    }

    @Nonnull
    private static AfkCheckCompletion resolveCompletion(
        @Nonnull AfkActivityService.AfkActivityTransition transition,
        HttpResponse<String> response,
        Throwable throwable
    ) {
        if (throwable != null) {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
            return AfkCheckCompletion.failure(transition, "AFK check failed: " + cause.getClass().getSimpleName());
        }
        if (response == null) {
            return AfkCheckCompletion.failure(transition, "AFK check returned no response.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return AfkCheckCompletion.failure(transition, "AFK check returned HTTP " + response.statusCode() + ".");
        }
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isBlank()) {
            return AfkCheckCompletion.failure(transition, "AFK check response body was empty.");
        }
        try {
            JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
            String decisionStr = parsed.has("decision") && !parsed.get("decision").isJsonNull()
                ? parsed.get("decision").getAsString() : "";
            String reasonCode = parsed.has("reason_code") && !parsed.get("reason_code").isJsonNull()
                ? parsed.get("reason_code").getAsString() : "";
            String message = parsed.has("message") && !parsed.get("message").isJsonNull()
                ? parsed.get("message").getAsString() : "";
            NexoriAfkContinuationDecisionType type = "CANCEL".equalsIgnoreCase(decisionStr)
                ? NexoriAfkContinuationDecisionType.CANCEL
                : NexoriAfkContinuationDecisionType.CONTINUE;
            return new AfkCheckCompletion(transition, type, reasonCode, message);
        } catch (Exception exception) {
            return AfkCheckCompletion.failure(transition, "AFK check response body could not be parsed.");
        }
    }

    private record AfkCheckCompletion(
        @Nonnull AfkActivityService.AfkActivityTransition transition,
        @Nonnull NexoriAfkContinuationDecisionType decisionType,
        @Nonnull String reasonCode,
        @Nonnull String message
    ) {
        static AfkCheckCompletion failure(
            @Nonnull AfkActivityService.AfkActivityTransition transition,
            @Nonnull String message
        ) {
            return new AfkCheckCompletion(transition, NexoriAfkContinuationDecisionType.UNAVAILABLE, "", message);
        }
    }
}
