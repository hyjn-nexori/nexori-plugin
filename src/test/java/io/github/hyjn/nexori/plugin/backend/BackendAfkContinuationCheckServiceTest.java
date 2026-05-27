package io.github.hyjn.nexori.plugin.backend;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkContinuationDecision;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkContinuationDecisionType;
import io.github.hyjn.nexori.plugin.backend.testsupport.BackendTestFixtures;
import io.github.hyjn.nexori.plugin.backend.testsupport.FakeBackendHttpTransport;
import io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.AfkActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link BackendAfkContinuationCheckService}.
 *
 * <p>Two-tick pattern: {@code enqueue()} fires the async request; {@code handleTick()} drains
 * the response. For already-completed futures (via {@code enqueueResponse}), the
 * {@code whenComplete} callback fires synchronously inside {@code enqueue()}, so the result
 * is already queued and a single {@code handleTick()} call drains it.</p>
 */
final class BackendAfkContinuationCheckServiceTest {

    private static final long T1 = 2_000_000L;
    private static final long T2 = T1 + 1L;

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String MATCH_ID = "match-test-1";

    private HytaleLogger logger;
    private FakeBackendHttpTransport transport;
    private ServerIdentity serverIdentity;
    private BackendAfkContinuationCheckService service;

    @BeforeEach
    void setUp() {
        logger = HytaleLogger.getLogger();
        transport = new FakeBackendHttpTransport();
        serverIdentity = BackendTestFixtures.testServerIdentity();
        service = new BackendAfkContinuationCheckService(
            logger,
            BackendTestFixtures.enabledAfkCheckConfig(),
            serverIdentity,
            transport
        );
    }

    // ── 1: feature disabled → no HTTP request ────────────────────────────────

    @Test
    void checkNotSentWhenFeatureIsDisabled() {
        BackendAfkContinuationCheckService disabled = new BackendAfkContinuationCheckService(
            logger,
            BackendMatchmakingConfig.defaults(),
            serverIdentity,
            transport
        );

        disabled.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        disabled.handleTick(T1);

        assertEquals(0, transport.capturedRequestCount());
    }

    // ── 2: afk=false → no HTTP request ───────────────────────────────────────

    @Test
    void checkNotSentWhenAfkIsFalse() {
        service.enqueue(afkTransition(MATCH_ID, PLAYER, false));
        service.handleTick(T1);

        assertEquals(0, transport.capturedRequestCount());
    }

    // ── 3: afk=true → HTTP request is sent ───────────────────────────────────

    @Test
    void checkSentOnAfkTrueTransition() {
        transport.enqueueResponse(200, continueResponse());

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T1);

        assertEquals(1, transport.capturedRequestCount());
    }

    // ── 4: PENDING while request is in flight ─────────────────────────────────

    @Test
    void decisionIsPendingWhileInFlight() {
        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        // Response has not arrived yet

        NexoriAfkContinuationDecision decision = service.getAfkContinuationDecision(MATCH_ID);

        assertEquals(NexoriAfkContinuationDecisionType.PENDING, decision.decision());
        assertEquals(MATCH_ID, decision.matchId());
        assertNull(decision.triggeringPlayerUuid());

        // Complete the future to avoid dangling threads
        pending.completeExceptionally(new RuntimeException("cancelled"));
    }

    // ── 5: backend responds CONTINUE ─────────────────────────────────────────

    @Test
    void decisionIsSetToContinueFromResponse() {
        transport.enqueueResponse(200, continueResponse());

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T1);

        NexoriAfkContinuationDecision decision = service.getAfkContinuationDecision(MATCH_ID);
        assertEquals(NexoriAfkContinuationDecisionType.CONTINUE, decision.decision());
        assertEquals(MATCH_ID, decision.matchId());
        assertEquals(PLAYER, decision.triggeringPlayerUuid());
    }

    // ── 6: backend responds CANCEL ───────────────────────────────────────────

    @Test
    void decisionIsSetToCancelFromResponse() {
        transport.enqueueResponse(200, cancelResponse("AFK_LIMIT", "Too many AFK players."));

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        List<NexoriAfkContinuationDecision> cancelDecisions = service.handleTick(T1);

        NexoriAfkContinuationDecision decision = service.getAfkContinuationDecision(MATCH_ID);
        assertEquals(NexoriAfkContinuationDecisionType.CANCEL, decision.decision());
        assertEquals("AFK_LIMIT", decision.reasonCode());
        assertEquals("Too many AFK players.", decision.message());
        assertEquals(PLAYER, decision.triggeringPlayerUuid());
        assertEquals(1, cancelDecisions.size());
        assertEquals(decision, cancelDecisions.get(0));
    }

    // ── 7: CANCEL is sticky — not overwritten by subsequent CONTINUE ──────────

    @Test
    void cancelIsNotOverwrittenBySubsequentContinue() {
        UUID playerTwo = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        transport.enqueueResponse(200, cancelResponse("", ""));

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T1);

        assertEquals(NexoriAfkContinuationDecisionType.CANCEL, service.getAfkContinuationDecision(MATCH_ID).decision());

        // A second player goes AFK → backend says CONTINUE this time
        transport.enqueueResponse(200, continueResponse());
        service.enqueue(afkTransition(MATCH_ID, playerTwo, true));
        service.handleTick(T2);

        // CANCEL must still be in place
        NexoriAfkContinuationDecision decision = service.getAfkContinuationDecision(MATCH_ID);
        assertEquals(NexoriAfkContinuationDecisionType.CANCEL, decision.decision());
    }

    // ── 8: HTTP error (5xx) → UNAVAILABLE ────────────────────────────────────

    @Test
    void decisionIsUnavailableOnHttpServerError() {
        transport.enqueueResponse(500, "Internal Server Error");

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T1);

        assertEquals(NexoriAfkContinuationDecisionType.UNAVAILABLE, service.getAfkContinuationDecision(MATCH_ID).decision());
    }

    // ── 9: timeout / CompletableFuture failure → UNAVAILABLE ─────────────────

    @Test
    void decisionIsUnavailableOnTimeout() {
        CompletableFuture<java.net.http.HttpResponse<String>> failing = new CompletableFuture<>();
        transport.enqueuePendingFuture(failing);

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));

        // Simulate timeout: complete exceptionally — whenComplete fires, adds UNAVAILABLE to queue
        failing.completeExceptionally(new java.util.concurrent.TimeoutException("timed out"));

        service.handleTick(T1);

        assertEquals(NexoriAfkContinuationDecisionType.UNAVAILABLE, service.getAfkContinuationDecision(MATCH_ID).decision());
    }

    // ── 10: duplicate enqueue while pending → only one HTTP request ───────────

    @Test
    void duplicateEnqueueWhilePendingIsDropped() {
        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        // Second enqueue for same match — should be ignored
        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));

        assertEquals(1, transport.capturedRequestCount());

        pending.completeExceptionally(new RuntimeException("cancelled"));
    }

    // ── 11: removeMatch clears state ─────────────────────────────────────────

    @Test
    void decisionClearedWhenMatchIsRemoved() {
        transport.enqueueResponse(200, continueResponse());

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T1);

        assertEquals(NexoriAfkContinuationDecisionType.CONTINUE, service.getAfkContinuationDecision(MATCH_ID).decision());

        service.removeMatch(MATCH_ID);

        assertEquals(NexoriAfkContinuationDecisionType.UNAVAILABLE, service.getAfkContinuationDecision(MATCH_ID).decision());
    }

    // ── 12: no prior check → UNAVAILABLE ─────────────────────────────────────

    @Test
    void decisionIsUnavailableWhenNoCheckWasEverSent() {
        assertEquals(NexoriAfkContinuationDecisionType.UNAVAILABLE, service.getAfkContinuationDecision(MATCH_ID).decision());
    }

    // ── 13: CANCEL skips future enqueue attempts ──────────────────────────────

    @Test
    void noRequestSentWhenCancelAlreadyStoredForMatch() {
        transport.enqueueResponse(200, cancelResponse("", ""));

        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T1);

        assertEquals(NexoriAfkContinuationDecisionType.CANCEL, service.getAfkContinuationDecision(MATCH_ID).decision());

        // Clear captured count, then try to enqueue again
        transport.clearCapturedRequests();
        service.enqueue(afkTransition(MATCH_ID, PLAYER, true));
        service.handleTick(T2);

        assertEquals(0, transport.capturedRequestCount());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AfkActivityService.AfkActivityTransition afkTransition(
        String matchId, UUID playerUuid, boolean afk
    ) {
        return new AfkActivityService.AfkActivityTransition(
            matchId,
            "queue-1",
            "arena-1",
            "rules-1",
            playerUuid,
            "TestPlayer",
            afk,
            T1,
            5_000L,
            NexoriAfkActivitySource.IDLE_TIMEOUT
        );
    }

    private static String continueResponse() {
        return "{\"decision\":\"CONTINUE\"}";
    }

    private static String cancelResponse(String reasonCode, String message) {
        return "{\"decision\":\"CANCEL\",\"reason_code\":\"" + reasonCode + "\",\"message\":\"" + message + "\"}";
    }
}
