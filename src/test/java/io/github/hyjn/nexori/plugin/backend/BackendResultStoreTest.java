package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendResultStoreTest {

    private static BackendResultStore.BackendResultRecord makeResult(String resultId) {
        return makeResultWithIds(resultId, "local-" + resultId, "ext-" + resultId, "hash-1");
    }

    private static BackendResultStore.BackendResultRecord makeResultWithIds(
        String resultId, String localMatchId, String externalMatchId, String payloadHash
    ) {
        long now = 1_000L;
        return new BackendResultStore.BackendResultRecord(
            resultId, localMatchId, externalMatchId, "assign-1",
            Map.of(), "queue-1", "arena-1", "rules-1",
            List.of(), "ok", Map.of(), new JsonObject(),
            payloadHash, BackendResultStore.BackendResultStatus.PENDING.name(),
            0, now, now, 0L, 0L, 0L,
            "", "", 0, ""
        );
    }

    @Test
    void putPendingNewResultReturnsQueuedOutcome(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);

        BackendResultStore.StorePutResult result = store.putPending(makeResult("result-1"));

        assertNotNull(result);
        assertEquals(BackendResultStore.StorePutOutcome.QUEUED, result.outcome());
    }

    @Test
    void putPendingSameResultIdReturnsAlreadySubmitted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        BackendResultStore.BackendResultRecord rec = makeResult("result-2");

        store.putPending(rec);
        BackendResultStore.StorePutResult second = store.putPending(rec);

        assertEquals(BackendResultStore.StorePutOutcome.ALREADY_SUBMITTED, second.outcome());
    }

    @Test
    void findNextDuePendingOnEmptyStoreReturnsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);

        assertTrue(store.findNextDuePending(System.currentTimeMillis()).isEmpty());
    }

    @Test
    void findNextDuePendingAfterPutReturnsPendingRecord(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-3"));

        assertTrue(store.findNextDuePending(System.currentTimeMillis()).isPresent());
    }

    @Test
    void findByResultIdAfterPutReturnsRecord(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-4"));

        BackendResultStore.BackendResultRecord found = store.find("result-4").orElseThrow();
        assertEquals("result-4", found.resultId());
    }

    @Test
    void findUnknownResultIdReturnsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);

        assertTrue(store.find("nonexistent").isEmpty());
    }

    @Test
    void markAttemptIncrementsAttemptCount(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-5"));

        store.markAttempt("result-5", System.currentTimeMillis());
        BackendResultStore.BackendResultRecord found = store.find("result-5").orElseThrow();

        assertEquals(1, found.attemptCount());
    }

    @Test
    void markAcknowledgedSetsStatusToAcknowledged(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-6"));

        store.markAcknowledged("result-6", 200, "OK", System.currentTimeMillis());
        BackendResultStore.BackendResultRecord found = store.find("result-6").orElseThrow();

        assertEquals(BackendResultStore.BackendResultStatus.ACKNOWLEDGED.name(), found.status());
    }

    @Test
    void markNeedsAttentionSetsStatusToNeedsAttention(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-7"));

        store.markNeedsAttention("result-7", 500, "InternalServerError", "server crashed", System.currentTimeMillis());
        BackendResultStore.BackendResultRecord found = store.find("result-7").orElseThrow();

        assertEquals(BackendResultStore.BackendResultStatus.NEEDS_ATTENTION.name(), found.status());
    }

    @Test
    void markPermanentFailureSetsStatusToFailedPermanent(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-8"));

        store.markPermanentFailure("result-8", 400, "BadRequest", "invalid payload", System.currentTimeMillis());
        BackendResultStore.BackendResultRecord found = store.find("result-8").orElseThrow();

        assertEquals(BackendResultStore.BackendResultStatus.FAILED_PERMANENT.name(), found.status());
    }

    @Test
    void markRetryPreservesResultInStore(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        store.putPending(makeResult("result-9"));

        long retryAt = System.currentTimeMillis() + 5_000L;
        store.markRetry("result-9", 503, "ServiceUnavailable", "try again", System.currentTimeMillis(), retryAt);

        assertTrue(store.find("result-9").isPresent());
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void loadAfterRestartPreservesPendingRecord(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store1 = new BackendResultStore(file);
        store1.putPending(makeResult("result-10"));

        BackendResultStore store2 = new BackendResultStore(file);

        assertTrue(store2.find("result-10").isPresent(), "Pending record should survive store restart");
    }

    @Test
    void markAcknowledgedPersistsAcrossInstances(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store1 = new BackendResultStore(file);
        store1.putPending(makeResult("result-11"));
        store1.markAcknowledged("result-11", 200, "OK", System.currentTimeMillis());

        BackendResultStore store2 = new BackendResultStore(file);
        BackendResultStore.BackendResultRecord found = store2.find("result-11").orElseThrow();

        assertEquals(BackendResultStore.BackendResultStatus.ACKNOWLEDGED.name(), found.status());
    }

    @Test
    void putPendingDuplicateConflictForSameLocalOrExternalDifferentHash(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        BackendResultStore store = new BackendResultStore(file);
        BackendResultStore.BackendResultRecord first =
            makeResultWithIds("result-a", "local-shared", "ext-shared", "hash-aaa");
        BackendResultStore.BackendResultRecord second =
            makeResultWithIds("result-b", "local-shared", "ext-shared", "hash-bbb");

        store.putPending(first);
        BackendResultStore.StorePutResult result = store.putPending(second);

        assertEquals(BackendResultStore.StorePutOutcome.DUPLICATE_CONFLICT, result.outcome(),
            "Different payload hash for same match IDs should be DUPLICATE_CONFLICT: " + result.message());
    }

    @Test
    void blankFileIsRewrittenAsEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        BackendResultStore store = new BackendResultStore(file);

        assertTrue(store.findNextDuePending(System.currentTimeMillis()).isEmpty());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(written.isBlank(), "Blank file should be rewritten to a valid empty document");
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("results.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> new BackendResultStore(file),
            "Corrupt JSON should throw RuntimeException (JsonSyntaxException)");
    }
}
