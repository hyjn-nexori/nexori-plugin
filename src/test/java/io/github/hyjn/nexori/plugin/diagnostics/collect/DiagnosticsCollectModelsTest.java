package io.github.hyjn.nexori.plugin.diagnostics.collect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsCollectModelsTest {

    // ── DiagnosticsCollectActiveLock ──────────────────────────────────────────

    @Test
    void activeLockPreservesSessionStatusAndTimestamp() {
        DiagnosticsCollectActiveLock lock = new DiagnosticsCollectActiveLock(
            "session-abc", DiagnosticsCollectStatus.RUNNING, 9_000_000L);
        assertEquals("session-abc", lock.sessionId());
        assertEquals(DiagnosticsCollectStatus.RUNNING, lock.status());
        assertEquals(9_000_000L, lock.updatedAtEpochMs());
    }

    // ── DiagnosticsCollectConsolidatedIndex ───────────────────────────────────

    @Test
    void consolidatedIndexSchemaVersionIsOne() {
        assertEquals(1, DiagnosticsCollectConsolidatedIndex.SCHEMA_VERSION);
    }

    @Test
    void consolidatedIndexPreservesAllFields() {
        List<DiagnosticsCollectConsolidatedIndex.SourceSummary> sources = List.of(
            new DiagnosticsCollectConsolidatedIndex.SourceSummary(
                DiagnosticsCollectSourceKind.LOCAL, "srv-1", "host.a.com:5520", 1024L, 50L)
        );
        DiagnosticsCollectConsolidatedIndex index = new DiagnosticsCollectConsolidatedIndex(
            1, "ses-1", 1000L, 2000L, 3000L, 100L, 90L, 10L, 4096L, sources);
        assertEquals("ses-1", index.sessionId());
        assertEquals(1000L, index.generatedAtEpochMs());
        assertEquals(100L, index.inputEvents());
        assertEquals(90L, index.writtenEvents());
        assertEquals(10L, index.duplicateEvents());
        assertEquals(4096L, index.outputBytes());
        assertEquals(1, index.sources().size());
        assertEquals("srv-1", index.sources().get(0).sourceServerId());
        assertEquals(1024L, index.sources().get(0).importedBytes());
    }

    // ── DiagnosticsCollectConsolidationResult ─────────────────────────────────

    @Test
    void consolidationResultPreservesCountsAndNdjson() {
        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidationResult(
            100L, 90L, 10L, 4096L, "{\"eventId\":\"e1\"}\n");
        assertEquals(100L, result.inputEvents());
        assertEquals(90L, result.writtenEvents());
        assertEquals(10L, result.duplicateEvents());
        assertEquals(4096L, result.outputBytes());
        assertEquals("{\"eventId\":\"e1\"}\n", result.consolidatedNdjson());
    }

    // ── DiagnosticsCollectFileProgress ────────────────────────────────────────

    @Test
    void fileProgressWithNextLineMarksCompletedWhenAtWindowEnd() {
        DiagnosticsCollectFileProgress fp = new DiagnosticsCollectFileProgress(
            "f-1", "seg.jsonl", true, null, null, null, null, null,
            0L, 0L, 0L, 100L, 80L, 8192L, 50L, 3, false, 0, null);
        DiagnosticsCollectFileProgress updated = fp.withNextLineInclusive(100L, 4);
        assertTrue(updated.completed(),
            "completed should be true when nextLineInclusive >= windowEndLineExclusive");
        assertEquals(100L, updated.nextLineInclusive());
        assertEquals(4, updated.lastChunkIndex());
    }

    @Test
    void fileProgressWithNextLineKeepsIncompleteBeforeWindowEnd() {
        DiagnosticsCollectFileProgress fp = new DiagnosticsCollectFileProgress(
            "f-1", "seg.jsonl", true, null, null, null, null, null,
            0L, 0L, 0L, 100L, 80L, 8192L, 50L, 3, false, 0, null);
        DiagnosticsCollectFileProgress updated = fp.withNextLineInclusive(75L, 5);
        assertFalse(updated.completed(),
            "completed should be false when nextLineInclusive < windowEndLineExclusive");
    }

    @Test
    void fileProgressWithNextLinePreservesRetryAndErrorFields() {
        DiagnosticsCollectFileProgress fp = new DiagnosticsCollectFileProgress(
            "f-1", "seg.jsonl", false, null, null, null, null, null,
            0L, 0L, 0L, 100L, 0L, 0L, 0L, 0, false, 2, "prev-error");
        DiagnosticsCollectFileProgress updated = fp.withNextLineInclusive(10L, 1);
        assertEquals(2, updated.retryCount());
        assertEquals("prev-error", updated.lastError());
    }

    @Test
    void fileProgressWithErrorPreservesWindowAndProgressFields() {
        DiagnosticsCollectFileProgress fp = new DiagnosticsCollectFileProgress(
            "f-1", "seg.jsonl", false, null, null, null, null, null,
            100L, 200L, 10L, 90L, 5L, 512L, 50L, 2, false, 0, null);
        DiagnosticsCollectFileProgress updated = fp.withError("network-timeout", 1);
        assertEquals("network-timeout", updated.lastError());
        assertEquals(1, updated.retryCount());
        assertEquals(10L, updated.windowStartLineInclusive());
        assertEquals(90L, updated.windowEndLineExclusive());
        assertEquals(50L, updated.nextLineInclusive());
        assertFalse(updated.completed(), "completed field is unchanged by withError");
    }

    // ── DiagnosticsCollectManifest ────────────────────────────────────────────

    @Test
    void manifestPreservesEntriesNextIndexAndCompleteFlag() {
        DiagnosticsCollectManifestEntry entry = new DiagnosticsCollectManifestEntry(
            "f-1", "seg.jsonl", true, null, 100L, 8192L, 0L, 0L, 0L, 100L, 100L, 8192L,
            null, null, null, null);
        DiagnosticsCollectManifest manifest = new DiagnosticsCollectManifest(
            DiagnosticsCollectSourceKind.LOCAL, "srv-1", "host.a.com:5520",
            1000L, 2000L, List.of(entry), 1, true, 500L);
        assertEquals(DiagnosticsCollectSourceKind.LOCAL, manifest.sourceKind());
        assertEquals(1, manifest.entries().size());
        assertEquals(1, manifest.nextEntryIndex());
        assertTrue(manifest.complete());
        assertEquals(500L, manifest.plannedAtEpochMs());
    }

    // ── DiagnosticsCollectManifestEntry ──────────────────────────────────────

    @Test
    void manifestEntryPreservesSnapshotAndWindowFields() {
        DiagnosticsCollectManifestEntry entry = new DiagnosticsCollectManifestEntry(
            "f-2", "seg-2.jsonl", false, "sha256abc", 200L, 16384L,
            1000L, 2000L, 10L, 80L, 70L, 7000L,
            150L, 14000L, 1800L, "prefix-sha");
        assertEquals("f-2", entry.fileId());
        assertEquals("seg-2.jsonl", entry.fileName());
        assertFalse(entry.closed());
        assertEquals("sha256abc", entry.fileSha256());
        assertEquals(150L, entry.snapshotLineCount());
        assertEquals("prefix-sha", entry.snapshotPrefixSha256());
    }

    // ── DiagnosticsCollectSession ─────────────────────────────────────────────

    @Test
    void sessionSchemaVersionIsOne() {
        assertEquals(1, DiagnosticsCollectSession.SCHEMA_VERSION);
    }

    @Test
    void sessionPreservesProgressCountsAndCurrentRequest() {
        DiagnosticsCollectOriginSnapshot snapshot =
            new DiagnosticsCollectOriginSnapshot("world", 1f, 2f, 3f, 0f, 0f, 0f);
        DiagnosticsCollectSession session = new DiagnosticsCollectSession(
            1, "ses-1", DiagnosticsCollectStatus.RUNNING, 1000L, 2000L,
            "7d", 0L, 86_400_000L, "player-uuid", snapshot,
            List.of(), "srv-1", "req-1", 10_000L, 5_000L, 1000L, 500L, null);
        assertEquals("ses-1", session.sessionId());
        assertEquals(DiagnosticsCollectStatus.RUNNING, session.status());
        assertEquals("req-1", session.currentRequestId());
        assertEquals(10_000L, session.estimatedTotalBytes());
        assertEquals(1000L, session.estimatedTotalEvents());
    }

    // ── DiagnosticsCollectSourceProgress ─────────────────────────────────────

    @Test
    void sourceProgressPreservesFilesAndCounts() {
        DiagnosticsCollectSourceProgress sp = new DiagnosticsCollectSourceProgress(
            DiagnosticsCollectSourceKind.REMOTE, "srv-r", "host.r.com:5520",
            DiagnosticsCollectStatus.RUNNING, 10_000L, 4_000L, 500L, 200L,
            "f-1", null, List.of());
        assertEquals(DiagnosticsCollectSourceKind.REMOTE, sp.sourceKind());
        assertEquals("srv-r", sp.sourceServerId());
        assertEquals(10_000L, sp.estimatedBytes());
        assertEquals(200L, sp.downloadedEvents());
        assertEquals("f-1", sp.currentFileId());
        assertNull(sp.lastError());
    }

    // ── DiagnosticsCollectWindow ──────────────────────────────────────────────

    @Test
    void collectWindowPreservesStartEndAndLabel() {
        DiagnosticsCollectWindow window = new DiagnosticsCollectWindow(1000L, 2000L, "Last 7 Days");
        assertEquals(1000L, window.startEpochMs());
        assertEquals(2000L, window.endEpochMs());
        assertEquals("Last 7 Days", window.label());
    }

    // ── DiagnosticsCollectSourceKind ──────────────────────────────────────────

    @Test
    void sourceKindEnumValuesAreStable() {
        assertEquals(2, DiagnosticsCollectSourceKind.values().length);
        assertEquals(DiagnosticsCollectSourceKind.LOCAL, DiagnosticsCollectSourceKind.valueOf("LOCAL"));
        assertEquals(DiagnosticsCollectSourceKind.REMOTE, DiagnosticsCollectSourceKind.valueOf("REMOTE"));
    }

    // ── DiagnosticsCollectStatus ──────────────────────────────────────────────

    @Test
    void statusEnumValuesAreStable() {
        assertEquals(9, DiagnosticsCollectStatus.values().length);
        assertEquals(DiagnosticsCollectStatus.PENDING, DiagnosticsCollectStatus.valueOf("PENDING"));
        assertEquals(DiagnosticsCollectStatus.COMPLETED, DiagnosticsCollectStatus.valueOf("COMPLETED"));
        assertEquals(DiagnosticsCollectStatus.FAILED, DiagnosticsCollectStatus.valueOf("FAILED"));
        assertEquals(DiagnosticsCollectStatus.CANCELLED, DiagnosticsCollectStatus.valueOf("CANCELLED"));
        assertEquals(DiagnosticsCollectStatus.CONSOLIDATING, DiagnosticsCollectStatus.valueOf("CONSOLIDATING"));
        assertEquals(DiagnosticsCollectStatus.READY_TO_CONSOLIDATE,
            DiagnosticsCollectStatus.valueOf("READY_TO_CONSOLIDATE"));
    }
}
