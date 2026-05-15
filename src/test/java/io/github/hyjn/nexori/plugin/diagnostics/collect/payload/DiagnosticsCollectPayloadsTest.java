package io.github.hyjn.nexori.plugin.diagnostics.collect.payload;

import io.github.hyjn.nexori.plugin.diagnostics.collect.DiagnosticsCollectManifestEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsCollectPayloadsTest {

    // ── DiagnosticsCollectManifestRequestPayload ──────────────────────────────

    @Test
    void manifestRequestPreservesSessionRequestWindowAndStartIndex() {
        DiagnosticsCollectManifestRequestPayload payload = new DiagnosticsCollectManifestRequestPayload(
            "ses-1", "req-abc", 1_000L, 86_400_000L, 0);
        assertEquals("ses-1", payload.sessionId());
        assertEquals("req-abc", payload.requestId());
        assertEquals(1_000L, payload.windowStartEpochMs());
        assertEquals(86_400_000L, payload.windowEndEpochMs());
        assertEquals(0, payload.startEntryIndex());
    }

    @Test
    void manifestRequestPreservesNonZeroStartIndex() {
        DiagnosticsCollectManifestRequestPayload payload = new DiagnosticsCollectManifestRequestPayload(
            "ses-1", "req-1", 0L, 1000L, 5);
        assertEquals(5, payload.startEntryIndex());
    }

    // ── DiagnosticsCollectManifestResponsePayload ─────────────────────────────

    @Test
    void manifestResponsePreservesSourceServerConnectionIndexesAndEntries() {
        DiagnosticsCollectManifestEntry entry = new DiagnosticsCollectManifestEntry(
            "f-1", "seg.jsonl", true, null, 100L, 8192L,
            0L, 0L, 0L, 100L, 100L, 8192L, null, null, null, null);
        DiagnosticsCollectManifestResponsePayload payload = new DiagnosticsCollectManifestResponsePayload(
            "ses-1", "req-1", "srv-remote", "host.r.com:5520",
            0, 1, false, List.of(entry));
        assertEquals("ses-1", payload.sessionId());
        assertEquals("srv-remote", payload.sourceServerId());
        assertEquals("host.r.com:5520", payload.sourceConnectionAddress());
        assertEquals(0, payload.startEntryIndex());
        assertEquals(1, payload.nextEntryIndex());
        assertEquals(false, payload.hasMoreEntries());
        assertEquals(1, payload.entries().size());
        assertEquals("f-1", payload.entries().get(0).fileId());
    }

    @Test
    void emptyEntriesListIsPreservedAccordingToCurrentBehavior() {
        DiagnosticsCollectManifestResponsePayload payload = new DiagnosticsCollectManifestResponsePayload(
            "ses-1", "req-1", "srv-1", "host.a.com:5520", 0, 0, false, List.of());
        assertTrue(payload.entries().isEmpty());
    }

    // ── DiagnosticsCollectChunkRequestPayload ─────────────────────────────────

    @Test
    void chunkRequestPreservesSnapshotAndLineWindowFields() {
        DiagnosticsCollectChunkRequestPayload payload = new DiagnosticsCollectChunkRequestPayload(
            "ses-1", "req-1", "f-1", true, "sha256-expected",
            100L, 8192L, 1_000_000L, "prefix-sha",
            10L, 90L, 50L);
        assertEquals("ses-1", payload.sessionId());
        assertEquals("f-1", payload.fileId());
        assertTrue(payload.closed());
        assertEquals("sha256-expected", payload.expectedFileSha256());
        assertEquals(100L, payload.snapshotLineCount());
        assertEquals(8192L, payload.snapshotByteSize());
        assertEquals(1_000_000L, payload.snapshotEndedAtEpochMs());
        assertEquals("prefix-sha", payload.snapshotPrefixSha256());
        assertEquals(10L, payload.windowStartLineInclusive());
        assertEquals(90L, payload.windowEndLineExclusive());
        assertEquals(50L, payload.nextLineInclusive());
    }

    @Test
    void nullOptionalSnapshotFieldsArePreservedAccordingToCurrentBehavior() {
        DiagnosticsCollectChunkRequestPayload payload = new DiagnosticsCollectChunkRequestPayload(
            "ses-1", "req-1", "f-1", false, null, null, null, null, null, 0L, 100L, 0L);
        assertNull(payload.expectedFileSha256());
        assertNull(payload.snapshotLineCount());
        assertNull(payload.snapshotByteSize());
        assertNull(payload.snapshotEndedAtEpochMs());
        assertNull(payload.snapshotPrefixSha256());
    }

    // ── DiagnosticsCollectChunkResponsePayload ────────────────────────────────

    @Test
    void chunkResponsePreservesChunkMetadataAndNdjsonBlock() {
        DiagnosticsCollectChunkResponsePayload payload = new DiagnosticsCollectChunkResponsePayload(
            "ses-1", "req-1", "srv-r", "host.r.com:5520",
            "f-1", 3, 50L, 75L, 25L, 2048L, "sha256-chunk", "{\"eventId\":\"e1\"}\n", false);
        assertEquals("ses-1", payload.sessionId());
        assertEquals("srv-r", payload.sourceServerId());
        assertEquals("f-1", payload.fileId());
        assertEquals(3, payload.chunkIndex());
        assertEquals(50L, payload.startLineInclusive());
        assertEquals(75L, payload.endLineExclusive());
        assertEquals(25L, payload.chunkEventCount());
        assertEquals(2048L, payload.chunkRawBytes());
        assertEquals("sha256-chunk", payload.chunkSha256());
        assertEquals("{\"eventId\":\"e1\"}\n", payload.ndjsonBlock());
        assertEquals(false, payload.hasMoreInFile());
    }

    @Test
    void blankStringsArePreservedAccordingToCurrentBehavior() {
        DiagnosticsCollectChunkResponsePayload payload = new DiagnosticsCollectChunkResponsePayload(
            "", "", "", "", "", 0, 0L, 0L, 0L, 0L, "", "", false);
        assertTrue(payload.sessionId().isBlank(),
            "Blank strings are passed through without trimming or rejection");
        assertTrue(payload.ndjsonBlock().isEmpty());
    }

    // ── DiagnosticsCollectErrorPayload ────────────────────────────────────────

    @Test
    void errorPayloadPreservesCodeMessageAndFileId() {
        DiagnosticsCollectErrorPayload payload = new DiagnosticsCollectErrorPayload(
            "ses-1", "req-1", "f-3", "CHUNK_HASH_MISMATCH", "Expected sha256 did not match");
        assertEquals("ses-1", payload.sessionId());
        assertEquals("req-1", payload.requestId());
        assertEquals("f-3", payload.fileId());
        assertEquals("CHUNK_HASH_MISMATCH", payload.code());
        assertEquals("Expected sha256 did not match", payload.message());
    }

    @Test
    void errorPayloadNullFileIdIsPreservedAccordingToCurrentBehavior() {
        DiagnosticsCollectErrorPayload payload = new DiagnosticsCollectErrorPayload(
            "ses-1", "req-1", null, "SESSION_NOT_FOUND", "No active session");
        assertNull(payload.fileId(),
            "fileId is nullable and should be preserved as null when not applicable");
    }
}
