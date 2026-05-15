package io.github.hyjn.nexori.plugin.diagnostics.collect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsCollectConsolidatorTest {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static DiagnosticsCollectOriginSnapshot snapshot() {
        return new DiagnosticsCollectOriginSnapshot("world", 0f, 0f, 0f, 0f, 0f, 0f);
    }

    private static DiagnosticsEvent event(String eventId, long occurredAt, long seq, String serverId) {
        return new DiagnosticsEvent.Builder()
            .eventId(eventId)
            .occurredAtEpochMs(occurredAt)
            .sourceSequence(seq)
            .sourceServerId(serverId)
            .category(DiagnosticsCategory.SECURITY)
            .action("test.action")
            .outcome(DiagnosticsOutcome.SUCCEEDED)
            .reasonClass(DiagnosticsReasonClass.NORMAL)
            .reasonCode("TEST")
            .message("test event")
            .operationId("op-1")
            .build();
    }

    private static DiagnosticsCollectFileProgress fileProgress(String fileId) {
        return new DiagnosticsCollectFileProgress(
            fileId, fileId + ".jsonl", true, null, null, null, null, null,
            0L, 0L, 0L, 1000L, 0L, 0L, 1000L, 0, true, 0, null);
    }

    private static DiagnosticsCollectSourceProgress source(
        String serverId, List<DiagnosticsCollectFileProgress> files
    ) {
        return new DiagnosticsCollectSourceProgress(
            DiagnosticsCollectSourceKind.LOCAL, serverId, "local",
            DiagnosticsCollectStatus.COMPLETED, 0L, 0L, 0L, (long) files.size(),
            null, null, files);
    }

    private static DiagnosticsCollectSession session(String id, List<DiagnosticsCollectSourceProgress> sources) {
        return new DiagnosticsCollectSession(
            DiagnosticsCollectSession.SCHEMA_VERSION, id, DiagnosticsCollectStatus.READY_TO_CONSOLIDATE,
            1000L, 2000L, "7d", 0L, 86_400_000L, "player-uuid", snapshot(),
            sources, null, null, 0L, 0L, 0L, 0L, null);
    }

    private void writeRawFile(DiagnosticsCollectSessionStore store, String sessionId,
                               String serverId, String fileId, List<DiagnosticsEvent> events) throws IOException {
        Path raw = store.rawFinalFile(sessionId, DiagnosticsCollectSourceKind.LOCAL, serverId, fileId);
        StringBuilder sb = new StringBuilder();
        for (DiagnosticsEvent e : events) {
            sb.append(GSON.toJson(e)).append('\n');
        }
        Files.writeString(raw, sb.toString(), StandardCharsets.UTF_8);
    }

    @Test
    void consolidateReadsRawFinalFilesFromAllSources(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsEvent e1 = event("e-1", 1000L, 1L, "srv-1");
        DiagnosticsEvent e2 = event("e-2", 2000L, 2L, "srv-1");

        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);
        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(e1, e2));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(2L, result.inputEvents());
        assertEquals(2L, result.writtenEvents());
        assertEquals(0L, result.duplicateEvents());
    }

    @Test
    void consolidateSkipsMissingRawFiles(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-missing")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);
        // Intentionally NOT writing raw file

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(0L, result.inputEvents());
        assertEquals(0L, result.writtenEvents());
    }

    @Test
    void consolidateSkipsBlankLines(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        Path raw = store.rawFinalFile("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-1", "f-1");
        String content = "\n   \n" + GSON.toJson(event("e-1", 1000L, 1L, "srv-1")) + "\n";
        Files.writeString(raw, content, StandardCharsets.UTF_8);

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(1L, result.inputEvents());
        assertEquals(1L, result.writtenEvents());
    }

    @Test
    void consolidateSkipsEventsWithBlankEventId(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        Path raw = store.rawFinalFile("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-1", "f-1");
        // GSON deserializes missing eventId as null; the record field is @Nonnull but GSON can set it to null.
        // The consolidator checks event.eventId().isBlank() — null would throw NPE.
        // We test with a record that has an empty eventId (as serialized by GSON when field is "").
        DiagnosticsEvent blankId = new DiagnosticsEvent.Builder()
            .eventId("").sourceServerId("srv-1").category(DiagnosticsCategory.SECURITY)
            .action("a").outcome(DiagnosticsOutcome.SUCCEEDED).reasonClass(DiagnosticsReasonClass.NORMAL)
            .reasonCode("x").message("m").operationId("op-1").build();
        DiagnosticsEvent valid = event("e-valid", 1000L, 1L, "srv-1");
        String content = GSON.toJson(blankId) + "\n" + GSON.toJson(valid) + "\n";
        Files.writeString(raw, content, StandardCharsets.UTF_8);

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(1L, result.writtenEvents(), "Event with blank eventId should be skipped");
    }

    @Test
    void consolidateDeduplicatesByEventId(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        DiagnosticsEvent e1 = event("e-dup", 1000L, 1L, "srv-1");
        DiagnosticsEvent e1again = event("e-dup", 2000L, 2L, "srv-1"); // same eventId
        DiagnosticsEvent e2 = event("e-unique", 3000L, 3L, "srv-1");
        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(e1, e1again, e2));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(3L, result.inputEvents());
        assertEquals(2L, result.writtenEvents());
        assertEquals(1L, result.duplicateEvents());
    }

    @Test
    void consolidateDeduplicatesBySourceServerAndSourceSequenceAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        // e1 and e2 have different eventIds but same server+sequence fallback key
        DiagnosticsEvent e1 = event("e-first", 1000L, 42L, "srv-1");
        DiagnosticsEvent e2 = event("e-second", 2000L, 42L, "srv-1"); // same server+seq
        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(e1, e2));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(2L, result.inputEvents());
        assertEquals(1L, result.writtenEvents(),
            "e2 is blocked by fallback dedup (same sourceServerId+sourceSequence as e1)");
        assertEquals(1L, result.duplicateEvents());
    }

    @Test
    void consolidateSortsByOccurredAtThenSourceSequence(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        // Write in wrong order — consolidator should sort
        DiagnosticsEvent late = event("e-late", 3000L, 3L, "srv-1");
        DiagnosticsEvent early = event("e-early", 1000L, 1L, "srv-1");
        DiagnosticsEvent middle = event("e-mid", 2000L, 2L, "srv-1");
        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(late, early, middle));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        String ndjson = result.consolidatedNdjson();
        int earlyPos = ndjson.indexOf("e-early");
        int midPos = ndjson.indexOf("e-mid");
        int latePos = ndjson.indexOf("e-late");
        assertTrue(earlyPos < midPos && midPos < latePos, "Events should be sorted by occurredAtEpochMs ascending");
    }

    @Test
    void consolidateCountsInputWrittenAndDuplicateEvents(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(
            event("e-1", 1000L, 1L, "srv-1"),
            event("e-2", 2000L, 2L, "srv-1"),
            event("e-1", 3000L, 3L, "srv-1") // dup
        ));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertEquals(3L, result.inputEvents());
        assertEquals(2L, result.writtenEvents());
        assertEquals(1L, result.duplicateEvents());
    }

    @Test
    void consolidateOutputBytesMatchesUtf8Length(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(event("e-1", 1000L, 1L, "srv-1")));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        long expectedBytes = result.consolidatedNdjson().getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expectedBytes, result.outputBytes());
    }

    @Test
    void consolidatedNdjsonEndsWithNewlineWhenEventsExist(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        writeRawFile(store, "ses-1", "srv-1", "f-1", List.of(event("e-1", 1000L, 1L, "srv-1")));

        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidator().consolidate(ses, store);

        assertTrue(result.consolidatedNdjson().endsWith("\n"),
            "Each event is followed by '\\n', so consolidatedNdjson ends with newline");
    }

    @Test
    void buildIndexUsesSessionWindowAndResultCounts(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectConsolidator consolidator = new DiagnosticsCollectConsolidator();
        DiagnosticsCollectSession ses = session("ses-build", List.of());
        store.saveSession(ses);
        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidationResult(
            10L, 9L, 1L, 1024L, "");

        long before = System.currentTimeMillis();
        DiagnosticsCollectConsolidatedIndex index = consolidator.buildIndex(ses, result);
        long after = System.currentTimeMillis();

        assertEquals(DiagnosticsCollectConsolidatedIndex.SCHEMA_VERSION, index.schemaVersion());
        assertEquals("ses-build", index.sessionId());
        assertEquals(0L, index.windowStartEpochMs());
        assertEquals(86_400_000L, index.windowEndEpochMs());
        assertEquals(10L, index.inputEvents());
        assertEquals(9L, index.writtenEvents());
        assertEquals(1L, index.duplicateEvents());
        assertEquals(1024L, index.outputBytes());
        assertTrue(index.generatedAtEpochMs() >= before && index.generatedAtEpochMs() <= after,
            "generatedAtEpochMs should be set by System.currentTimeMillis() during buildIndex");
    }

    @Test
    void buildIndexIncludesSourceSummaries(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectConsolidator consolidator = new DiagnosticsCollectConsolidator();
        DiagnosticsCollectSourceProgress sp = new DiagnosticsCollectSourceProgress(
            DiagnosticsCollectSourceKind.LOCAL, "srv-local", "local",
            DiagnosticsCollectStatus.COMPLETED, 4096L, 4096L, 100L, 100L, null, null, List.of());
        DiagnosticsCollectSession ses = session("ses-idx", List.of(sp));
        store.saveSession(ses);
        DiagnosticsCollectConsolidationResult result = new DiagnosticsCollectConsolidationResult(100L, 100L, 0L, 4096L, "");

        DiagnosticsCollectConsolidatedIndex index = consolidator.buildIndex(ses, result);

        assertEquals(1, index.sources().size());
        DiagnosticsCollectConsolidatedIndex.SourceSummary summary = index.sources().get(0);
        assertEquals("srv-local", summary.sourceServerId());
        assertEquals(DiagnosticsCollectSourceKind.LOCAL, summary.sourceKind());
        assertEquals(4096L, summary.importedBytes());
        assertEquals(100L, summary.importedEvents());
    }

    @Test
    void malformedRawJsonLineThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        Path raw = store.rawFinalFile("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-1", "f-1");
        Files.writeString(raw, "{not-json\n", StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class,
            () -> new DiagnosticsCollectConsolidator().consolidate(ses, store),
            "GSON.fromJson throws JsonSyntaxException (RuntimeException) for malformed JSON lines; consolidate does not catch it");
    }

    @Test
    void rawEventWithMissingEventIdThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSourceProgress sp = source("srv-1", List.of(fileProgress("f-1")));
        DiagnosticsCollectSession ses = session("ses-1", List.of(sp));
        store.saveSession(ses);

        // GSON deserializes the record with eventId=null (missing field).
        // consolidate() then calls event.eventId().isBlank() → NullPointerException.
        Path raw = store.rawFinalFile("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-1", "f-1");
        Files.writeString(raw, "{\"schemaVersion\":1,\"occurredAtEpochMs\":1000,\"sourceSequence\":1}\n",
            StandardCharsets.UTF_8);

        assertThrows(NullPointerException.class,
            () -> new DiagnosticsCollectConsolidator().consolidate(ses, store),
            "When eventId field is missing from JSON, GSON sets it to null; event.eventId().isBlank() then throws NullPointerException");
    }
}
