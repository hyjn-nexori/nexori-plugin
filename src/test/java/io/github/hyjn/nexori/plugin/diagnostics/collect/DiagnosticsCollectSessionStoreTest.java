package io.github.hyjn.nexori.plugin.diagnostics.collect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsCollectSessionStoreTest {

    private static DiagnosticsCollectOriginSnapshot snapshot() {
        return new DiagnosticsCollectOriginSnapshot("world", 0f, 0f, 0f, 0f, 0f, 0f);
    }

    private static DiagnosticsCollectSession session(
        String sessionId, DiagnosticsCollectStatus status, long updatedAt
    ) {
        return new DiagnosticsCollectSession(
            DiagnosticsCollectSession.SCHEMA_VERSION, sessionId, status,
            1000L, updatedAt, "7d", 0L, 86_400_000L,
            "player-uuid", snapshot(), List.of(), null, null,
            0L, 0L, 0L, 0L, null
        );
    }

    private static DiagnosticsCollectManifest manifest(String sessionId) {
        return new DiagnosticsCollectManifest(
            DiagnosticsCollectSourceKind.LOCAL, "srv-local", "local",
            0L, 86_400_000L, List.of(), 0, false, 500L
        );
    }

    private static DiagnosticsCollectSourceProgress sourceProgress(String serverId) {
        return new DiagnosticsCollectSourceProgress(
            DiagnosticsCollectSourceKind.LOCAL, serverId, "local",
            DiagnosticsCollectStatus.RUNNING, 1000L, 500L, 50L, 25L,
            null, null, List.of()
        );
    }

    @Test
    void constructorCreatesCollectDirectories(@TempDir Path dir) throws IOException {
        new DiagnosticsCollectSessionStore(dir);
        assertTrue(Files.isDirectory(dir.resolve("state/diagnostics/collect/sessions")));
    }

    @Test
    void loadActiveLockMissingReturnsEmpty(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        assertTrue(store.loadActiveLock().isEmpty());
    }

    @Test
    void saveAndLoadActiveLockRoundTrips(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectActiveLock lock = new DiagnosticsCollectActiveLock(
            "ses-1", DiagnosticsCollectStatus.RUNNING, 9_000L);

        store.saveActiveLock(lock);
        Optional<DiagnosticsCollectActiveLock> loaded = store.loadActiveLock();

        assertTrue(loaded.isPresent());
        assertEquals("ses-1", loaded.get().sessionId());
        assertEquals(DiagnosticsCollectStatus.RUNNING, loaded.get().status());
        assertEquals(9_000L, loaded.get().updatedAtEpochMs());
    }

    @Test
    void clearActiveLockRemovesFile(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveActiveLock(new DiagnosticsCollectActiveLock("ses-1", DiagnosticsCollectStatus.PENDING, 0L));

        store.clearActiveLock();

        assertTrue(store.loadActiveLock().isEmpty());
    }

    @Test
    void saveAndLoadSessionRoundTrips(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        DiagnosticsCollectSession ses = session("ses-abc", DiagnosticsCollectStatus.RUNNING, 5000L);

        store.saveSession(ses);
        Optional<DiagnosticsCollectSession> loaded = store.loadSession("ses-abc");

        assertTrue(loaded.isPresent());
        assertEquals("ses-abc", loaded.get().sessionId());
        assertEquals(DiagnosticsCollectStatus.RUNNING, loaded.get().status());
        assertEquals(5000L, loaded.get().updatedAtEpochMs());
    }

    @Test
    void loadSessionMissingReturnsEmpty(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        assertTrue(store.loadSession("nonexistent-session").isEmpty());
    }

    @Test
    void saveSessionCreatesSessionDirectory(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("my-session", DiagnosticsCollectStatus.PENDING, 1000L));
        assertTrue(Files.isDirectory(dir.resolve("state/diagnostics/collect/sessions/my-session")));
    }

    @Test
    void loadLatestSessionReturnsMostRecentlyUpdated(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-old", DiagnosticsCollectStatus.COMPLETED, 1000L));
        store.saveSession(session("ses-new", DiagnosticsCollectStatus.RUNNING, 9000L));

        Optional<DiagnosticsCollectSession> latest = store.loadLatestSession();

        assertTrue(latest.isPresent());
        assertEquals("ses-new", latest.get().sessionId());
    }

    @Test
    void loadLatestSessionTieBreaksBySessionIdDescendingAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        long sameTime = 5000L;
        store.saveSession(session("ses-a", DiagnosticsCollectStatus.PENDING, sameTime));
        store.saveSession(session("ses-z", DiagnosticsCollectStatus.PENDING, sameTime));

        Optional<DiagnosticsCollectSession> latest = store.loadLatestSession();

        assertTrue(latest.isPresent());
        assertEquals("ses-z", latest.get().sessionId(),
            "When updatedAt is equal, thenComparing(sessionId) picks lexicographically greatest");
    }

    @Test
    void loadLatestCompletedSessionIgnoresNonCompleted(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-running", DiagnosticsCollectStatus.RUNNING, 9000L));
        store.saveSession(session("ses-done", DiagnosticsCollectStatus.COMPLETED, 1000L));

        Optional<DiagnosticsCollectSession> latest = store.loadLatestCompletedSession();

        assertTrue(latest.isPresent());
        assertEquals("ses-done", latest.get().sessionId());
        assertEquals(DiagnosticsCollectStatus.COMPLETED, latest.get().status());
    }

    @Test
    void loadLatestCompletedSessionReturnsEmptyWhenNoneCompleted(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-running", DiagnosticsCollectStatus.RUNNING, 9000L));

        assertTrue(store.loadLatestCompletedSession().isEmpty());
    }

    @Test
    void saveAndLoadManifestRoundTrips(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.RUNNING, 1000L));
        DiagnosticsCollectManifest m = manifest("ses-1");

        store.saveManifest("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local", m);
        Optional<DiagnosticsCollectManifest> loaded = store.loadManifest("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local");

        assertTrue(loaded.isPresent());
        assertEquals(DiagnosticsCollectSourceKind.LOCAL, loaded.get().sourceKind());
        assertFalse(loaded.get().complete());
    }

    @Test
    void loadManifestMissingReturnsEmpty(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.PENDING, 1000L));
        assertTrue(store.loadManifest("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local").isEmpty());
    }

    @Test
    void saveAndLoadSourceProgressRoundTrips(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.RUNNING, 1000L));
        DiagnosticsCollectSourceProgress sp = sourceProgress("srv-local");

        store.saveSourceProgress("ses-1", sp);
        Optional<DiagnosticsCollectSourceProgress> loaded = store.loadSourceProgress(
            "ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local");

        assertTrue(loaded.isPresent());
        assertEquals("srv-local", loaded.get().sourceServerId());
        assertEquals(DiagnosticsCollectStatus.RUNNING, loaded.get().status());
        assertEquals(1000L, loaded.get().estimatedBytes());
    }

    @Test
    void loadSourceProgressMissingReturnsEmpty(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.PENDING, 1000L));
        assertTrue(store.loadSourceProgress("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local").isEmpty());
    }

    @Test
    void rawPartFileUsesExpectedPath(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.RUNNING, 1000L));

        Path part = store.rawPartFile("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local", "file-abc");

        assertTrue(part.toString().endsWith("file-abc.part"),
            "rawPartFile should end with fileId + '.part'");
        assertTrue(part.toString().contains("raw"));
    }

    @Test
    void rawFinalFileUsesExpectedPath(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.RUNNING, 1000L));

        Path finalFile = store.rawFinalFile("ses-1", DiagnosticsCollectSourceKind.LOCAL, "srv-local", "file-abc");

        assertTrue(finalFile.toString().endsWith("file-abc.jsonl"),
            "rawFinalFile should end with fileId + '.jsonl'");
    }

    @Test
    void outputFileUsesExpectedPath(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.COMPLETED, 1000L));

        Path output = store.outputFile("ses-1", "index.json");

        assertTrue(output.toString().endsWith("index.json"));
        assertTrue(output.toString().contains("output"));
    }

    @Test
    void readJsonBlankFileReturnsEmptyAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.PENDING, 1000L));
        Path sessionFile = dir.resolve("state/diagnostics/collect/sessions/ses-1/session.json");
        Files.writeString(sessionFile, "   ", StandardCharsets.UTF_8);

        Optional<DiagnosticsCollectSession> loaded = store.loadSession("ses-1");

        assertTrue(loaded.isEmpty(), "Blank JSON file returns Optional.empty()");
    }

    @Test
    void readJsonCorruptFileThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        store.saveSession(session("ses-1", DiagnosticsCollectStatus.PENDING, 1000L));
        Path sessionFile = dir.resolve("state/diagnostics/collect/sessions/ses-1/session.json");
        Files.writeString(sessionFile, "{not-json", StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> store.loadSession("ses-1"),
            "Corrupt JSON propagates as JsonSyntaxException (RuntimeException) uncaught from GSON");
    }

    @Test
    void nonDirectoryEntryUnderSessionsIsIgnoredByLoadLatestSession(@TempDir Path dir) throws IOException {
        DiagnosticsCollectSessionStore store = new DiagnosticsCollectSessionStore(dir);
        Path sessionsDir = dir.resolve("state/diagnostics/collect/sessions");
        Files.writeString(sessionsDir.resolve("not-a-directory.txt"), "stray file", StandardCharsets.UTF_8);

        Optional<DiagnosticsCollectSession> latest = store.loadLatestSession();

        assertTrue(latest.isEmpty(), "Non-directory entries under sessions are skipped");
    }
}
