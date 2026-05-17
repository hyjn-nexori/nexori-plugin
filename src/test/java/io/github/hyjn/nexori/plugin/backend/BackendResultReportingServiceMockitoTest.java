package io.github.hyjn.nexori.plugin.backend;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.testsupport.BackendTestFixtures;
import io.github.hyjn.nexori.plugin.backend.testsupport.FakeBackendHttpTransport;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mockito-based integration tests for {@link BackendResultReportingService}.
 *
 * <p>Uses {@link FakeBackendHttpTransport} for HTTP control and a real
 * {@link BackendResultStore} with {@link TempDir} for filesystem isolation.
 * A known-ID result record is seeded into the store so tests can construct
 * deterministic response payloads without reading HTTP headers.</p>
 *
 * <p>Two-tick pattern: T1 starts the request, T2 drains the result
 * (already-completed futures fire their {@code whenComplete} synchronously
 * inside T1's {@code maybeStartResultRequest}).</p>
 */
final class BackendResultReportingServiceMockitoTest {

    private static final long T1 = 2_000_000L;
    private static final long T2 = T1 + 1L;

    // requestTimeoutMs=3000 + STALE_SAFETY_WINDOW_MS=1000 → stale after 4001 ms
    private static final long T_STALE = T1 + 4_100L;

    private static final String RESULT_ID = "test-result-id-abc123";

    @TempDir
    Path tempDir;

    // HytaleLogger has a private constructor and Flogger's AbstractLogger cannot be
    // instrumented by ByteBuddy's InlineMockMaker; use the real logger instead.
    private HytaleLogger logger;

    private FakeBackendHttpTransport transport;
    private BackendResultStore resultStore;
    private ServerIdentity serverIdentity;
    private BackendResultReportingService service;

    @BeforeEach
    void setUp() throws IOException {
        logger = HytaleLogger.getLogger();
        transport = new FakeBackendHttpTransport();
        resultStore = new BackendResultStore(tempDir.resolve("results.json"));
        serverIdentity = BackendTestFixtures.testServerIdentity();

        service = new BackendResultReportingService(
            logger,
            BackendTestFixtures.enabledResultConfig(),
            resultStore,
            serverIdentity,
            transport
        );
    }

    // ── Test 1: pending result is picked up and sent ──────────────────────────

    @Test
    void pendingResultIsPickedUpAndSentToBackend() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));
        transport.enqueueResponse(200, BackendTestFixtures.acceptedResultResponse(RESULT_ID));

        service.handleTick(T1);
        service.handleTick(T2);

        assertEquals(1, transport.capturedRequestCount());
    }

    // ── Test 2: accepted ACK marks result as acknowledged ─────────────────────

    @Test
    void acceptedAckMarksResultAsAcknowledged() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));

        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.handleTick(T1); // starts request, captures request, pending stays

        // Now complete the future — whenComplete fires in THIS thread → adds to queuedResults
        pending.complete(new io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse(
            200, BackendTestFixtures.acceptedResultResponse(RESULT_ID)
        ));

        service.handleTick(T2); // drains queued result → ACKNOWLEDGE

        BackendResultStore.BackendResultRecord stored = resultStore.find(RESULT_ID).orElse(null);
        assertNotNull(stored, "Result must still be in store after acknowledgement");
        assertEquals(BackendResultStore.BackendResultStatus.ACKNOWLEDGED.name(), stored.status());
    }

    // ── Test 3: duplicate ACK also marks result as acknowledged ───────────────

    @Test
    void duplicateAckMarksResultAsAcknowledged() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));

        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.handleTick(T1);

        pending.complete(new io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse(
            200, BackendTestFixtures.duplicateResultResponse(RESULT_ID)
        ));

        service.handleTick(T2);

        BackendResultStore.BackendResultRecord stored = resultStore.find(RESULT_ID).orElse(null);
        assertNotNull(stored);
        assertEquals(BackendResultStore.BackendResultStatus.ACKNOWLEDGED.name(), stored.status());
    }

    // ── Test 4: 400 marks result as permanent failure ─────────────────────────

    @Test
    void rejected400MarksResultAsPermanentFailure() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));

        // Empty body → failure result → policy: 400 → PERMANENT_FAILURE
        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.handleTick(T1);
        pending.complete(new io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse(400, ""));
        service.handleTick(T2);

        BackendResultStore.BackendResultRecord stored = resultStore.find(RESULT_ID).orElse(null);
        assertNotNull(stored);
        assertEquals(BackendResultStore.BackendResultStatus.FAILED_PERMANENT.name(), stored.status());
    }

    // ── Test 5: retryable 429 keeps result pending ────────────────────────────

    @Test
    void retryable429KeepsResultAsPending() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));

        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.handleTick(T1);
        pending.complete(new io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse(429, ""));
        service.handleTick(T2);

        BackendResultStore.BackendResultRecord stored = resultStore.find(RESULT_ID).orElse(null);
        assertNotNull(stored);
        assertEquals(BackendResultStore.BackendResultStatus.PENDING.name(), stored.status());
        assertTrue(stored.nextAttemptAtEpochMs() > T2,
            "Retry should be scheduled in the future");
    }

    // ── Test 6: malformed 200 body keeps result pending ───────────────────────

    @Test
    void malformed200BodyKeepsResultAsPending() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));

        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.handleTick(T1);
        pending.complete(new io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse(
            200, "NOT_VALID_JSON{{{"
        ));
        service.handleTick(T2);

        BackendResultStore.BackendResultRecord stored = resultStore.find(RESULT_ID).orElse(null);
        assertNotNull(stored);
        assertEquals(BackendResultStore.BackendResultStatus.PENDING.name(), stored.status());
    }

    // ── Test 7: stale in-flight triggers retry ────────────────────────────────

    @Test
    void staleInFlightSchedulesRetry() throws IOException {
        resultStore.putPending(BackendTestFixtures.pendingResultRecord(RESULT_ID, T1));

        // Never-completing future — simulates a request stuck in-flight
        transport.enqueuePendingFuture(new CompletableFuture<>());

        service.handleTick(T1);   // starts request, pending stays
        service.handleTick(T_STALE); // clearStaleInFlight → markRetryQuietly

        BackendResultStore.BackendResultRecord stored = resultStore.find(RESULT_ID).orElse(null);
        assertNotNull(stored);
        assertEquals(BackendResultStore.BackendResultStatus.PENDING.name(), stored.status());
        assertTrue(stored.nextAttemptAtEpochMs() > T1,
            "After stale-in-flight, retry should be scheduled in the future");
    }
}
