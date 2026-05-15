package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchSessionServiceTest {

    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static MatchSessionService service(Path dir) throws IOException {
        return new MatchSessionService(new MatchSessionStore(dir.resolve("sessions.json")));
    }

    private static MatchSessionState session(String matchId) {
        return new MatchSessionState(
            matchId, "queue-1", "arena-1", "lobby-1",
            "srv.example.com:25565", "hub-1", "profile-1",
            List.of(), List.of(),
            1000L, 1000L, 0L, 0L, ""
        );
    }

    private static MatchSessionState sessionWithPlayers(String matchId, List<UUID> players, long expiresAt) {
        return new MatchSessionState(
            matchId, "queue-1", "arena-1", "lobby-1",
            "srv.example.com:25565", "hub-1", "profile-1",
            players, List.of(),
            1000L, 1000L, 0L, expiresAt, ""
        );
    }

    // ── construction ──────────────────────────────────────────────────────────

    @Test
    void constructorCreatesEmptyServiceWhenNoSavedSessions(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        assertTrue(svc.list().isEmpty());
    }

    @Test
    void constructorDiscardsExpiredSessionsOnLoad(@TempDir Path dir) throws IOException {
        long past = System.currentTimeMillis() - 10_000L;
        MatchSessionStore store = new MatchSessionStore(dir.resolve("sessions.json"));
        store.save(List.of(sessionWithPlayers("match-expired", List.of(), past)));

        MatchSessionService svc = new MatchSessionService(store);

        assertTrue(svc.list().isEmpty(), "Expired sessions are pruned on construction");
    }

    @Test
    void constructorRetainsNonExpiredSessions(@TempDir Path dir) throws IOException {
        long future = System.currentTimeMillis() + 60_000L;
        MatchSessionStore store = new MatchSessionStore(dir.resolve("sessions.json"));
        store.save(List.of(sessionWithPlayers("match-active", List.of(), future)));

        MatchSessionService svc = new MatchSessionService(store);

        assertEquals(1, svc.list().size());
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void listReturnsSortedByMatchId(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-z"));
        svc.upsert(session("match-a"));
        svc.upsert(session("match-m"));

        List<MatchSessionState> listed = svc.list();

        assertEquals("match-a", listed.get(0).matchId());
        assertEquals("match-m", listed.get(1).matchId());
        assertEquals("match-z", listed.get(2).matchId());
    }

    // ── find ──────────────────────────────────────────────────────────────────

    @Test
    void findReturnsSessionByExactMatchId(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        Optional<MatchSessionState> found = svc.find("match-1");

        assertTrue(found.isPresent());
        assertEquals("match-1", found.get().matchId());
    }

    @Test
    void findNormalizesInputKey(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        Optional<MatchSessionState> found = svc.find("  MATCH-1  ");

        assertTrue(found.isPresent());
    }

    @Test
    void findBlankReturnsEmpty(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        assertTrue(svc.find("  ").isEmpty());
    }

    @Test
    void findMissingReturnsEmpty(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        assertTrue(svc.find("nonexistent-match").isEmpty());
    }

    // ── upsert ────────────────────────────────────────────────────────────────

    @Test
    void upsertNormalizesAndStoresSession(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);

        MatchSessionState returned = svc.upsert(session("  MATCH-1  "));

        assertEquals("match-1", returned.matchId());
        assertEquals(1, svc.list().size());
    }

    @Test
    void upsertReplacesExistingSession(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        MatchSessionState updated = new MatchSessionState(
            "match-1", "queue-1", "arena-1", "lobby-1",
            "srv.example.com:25565", "hub-1", "profile-1",
            List.of(UUID_A), List.of(),
            1000L, 2000L, 0L, 0L, "some error"
        );
        svc.upsert(updated);

        assertEquals(1, svc.list().size());
        assertTrue(svc.find("match-1").get().expectedPlayerUuids().contains(UUID_A));
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Test
    void removeReturnsTrueAndDeletesSession(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        boolean removed = svc.remove("match-1");

        assertTrue(removed);
        assertTrue(svc.find("match-1").isEmpty());
    }

    @Test
    void removeMissingSessionReturnsFalse(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        assertFalse(svc.remove("nonexistent-match"));
    }

    // ── pruneExpired ──────────────────────────────────────────────────────────

    @Test
    void pruneExpiredRemovesOnlyExpiredSessions(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        long now = System.currentTimeMillis();
        svc.upsert(sessionWithPlayers("match-past", List.of(), now - 1000L));
        svc.upsert(sessionWithPlayers("match-future", List.of(), now + 60_000L));
        svc.upsert(session("match-no-expiry"));

        int pruned = svc.pruneExpired(now);

        assertEquals(1, pruned);
        assertTrue(svc.find("match-past").isEmpty());
        assertTrue(svc.find("match-future").isPresent());
        assertTrue(svc.find("match-no-expiry").isPresent());
    }

    @Test
    void pruneExpiredReturnsZeroWhenNothingExpired(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        int pruned = svc.pruneExpired(System.currentTimeMillis());

        assertEquals(0, pruned);
    }

    // ── registerReturn ────────────────────────────────────────────────────────

    @Test
    void registerReturnMissingSessionReturnsMissingOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.MISSING, result.outcome());
    }

    @Test
    void registerReturnQueueMismatchReturnsInvalidOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "wrong-queue", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.INVALID, result.outcome());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().isBlank());
    }

    @Test
    void registerReturnPlayerNotInRosterReturnsInvalidOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(session("match-1"));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.INVALID, result.outcome());
    }

    @Test
    void registerReturnValidPlayerReturnsReturnedOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(sessionWithPlayers("match-1", List.of(UUID_A, UUID_B), 0L));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.RETURNED, result.outcome());
    }

    @Test
    void registerReturnLastPlayerCompletesMatch(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(sessionWithPlayers("match-1", List.of(UUID_A), 0L));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.COMPLETED, result.outcome());
    }

    @Test
    void registerReturnAlreadyReturnedPlayerReturnsAlreadyReturnedOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(sessionWithPlayers("match-1", List.of(UUID_A), 0L));
        svc.registerReturn("match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.ALREADY_RETURNED, result.outcome());
    }

    @Test
    void registerReturnOriginMismatchReturnsInvalidOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(sessionWithPlayers("match-1", List.of(UUID_A), 0L));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "wrong-lobby", "arena-1", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.INVALID, result.outcome());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("Origin source context mismatch"),
            "Error message should describe the origin mismatch; was: " + result.errorMessage());
    }

    @Test
    void registerReturnArenaMismatchReturnsInvalidOutcome(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(sessionWithPlayers("match-1", List.of(UUID_A), 0L));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "wrong-arena", "MATCH_ENDED", UUID_A);

        assertEquals(MatchSessionService.ReturnOutcome.INVALID, result.outcome());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("Source arena mismatch"),
            "Error message should describe the arena mismatch; was: " + result.errorMessage());
    }

    @Test
    void registerReturnBlankReasonDefaultsToMatchEndedAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        MatchSessionService svc = service(dir);
        svc.upsert(sessionWithPlayers("match-1", List.of(UUID_A), 0L));

        MatchSessionService.ReturnResult result = svc.registerReturn(
            "match-1", "queue-1", "lobby-1", "arena-1", "   ", UUID_A);

        // normalizeOptional("   ", "MATCH_ENDED"): trim → "" → blank → returns default "MATCH_ENDED"
        assertEquals("MATCH_ENDED", result.returnReason(),
            "Blank rawReturnReason normalizes to empty string and falls back to the default 'MATCH_ENDED'");
    }

    @Test
    void registerReturnPersistsObservedPlayer(@TempDir Path dir) throws IOException {
        MatchSessionStore store = new MatchSessionStore(dir.resolve("sessions.json"));
        MatchSessionService svc1 = new MatchSessionService(store);
        svc1.upsert(sessionWithPlayers("match-1", List.of(UUID_A, UUID_B), 0L));
        svc1.registerReturn("match-1", "queue-1", "lobby-1", "arena-1", "MATCH_ENDED", UUID_A);

        // Load a fresh service from the same store to verify the return was persisted
        MatchSessionService svc2 = new MatchSessionService(store);
        MatchSessionState loaded = svc2.find("match-1").orElseThrow();

        assertTrue(loaded.returnedPlayerUuids().contains(UUID_A),
            "registerReturn persists the observed player into returnedPlayerUuids via the store");
        assertFalse(loaded.returnedPlayerUuids().contains(UUID_B),
            "Only the player who returned should appear in returnedPlayerUuids");
    }
}
