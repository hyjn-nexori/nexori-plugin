package io.github.hyjn.nexori.plugin.api.minigame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class NexoriAfkPublicApiCompatibilityTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void activeMatchInfoOldConstructorDefaultsAfkPlayersToEmptyList() {
        NexoriActiveMatchInfo info = new NexoriActiveMatchInfo(
            "match-1",
            "queue-1",
            "arena-1",
            "assignment-1",
            "external-1",
            "rules-1",
            "none",
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(),
            List.of(),
            List.of(PLAYER_ONE),
            List.of(),
            1,
            0L,
            0L
        );

        assertEquals(List.of(), info.afkPlayerUuids());
    }

    @Test
    void activeMatchInfoCanonicalConstructorCopiesAndDeduplicatesAfkPlayers() {
        NexoriActiveMatchInfo info = new NexoriActiveMatchInfo(
            "match-1",
            "queue-1",
            "arena-1",
            "assignment-1",
            "external-1",
            "rules-1",
            "none",
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(),
            List.of(),
            List.of(PLAYER_ONE, PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(),
            1,
            0L,
            0L
        );

        assertEquals(List.of(PLAYER_ONE), info.afkPlayerUuids());
    }

    @Test
    void minigameApiDefaultAfkListenerRegistrationDoesNotBreakImplementations() {
        NexoriMinigameApi api = new MinimalMinigameApi();

        NexoriListenerRegistration registration = api.registerAfkActivityListener("rules-1", new NexoriAfkActivityListener() {
        });
        registration.close();

        assertFalse(api.findActiveMatchInfo("match-1").isPresent());
    }

    private static final class MinimalMinigameApi implements NexoriMinigameApi {

        @Override
        public NexoriListenerRegistration registerMatchLifecycleListener(String rulesEngineId, NexoriMatchLifecycleListener listener) {
            return () -> {
            };
        }

        @Override
        public Optional<String> findActiveMatchId(UUID playerUuid) {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> findActivePlayerUuid(String matchId, String playerToken) {
            return Optional.empty();
        }

        @Override
        public Optional<NexoriActiveMatchInfo> findActiveMatchInfo(String matchId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> findRulesEngineId(String matchId) {
            return Optional.empty();
        }

        @Override
        public NexoriSetPlayerOutcomeResult setPlayerOutcome(
            String matchId,
            UUID playerUuid,
            NexoriMatchResultPlayerOutcome outcome,
            String reason
        ) {
            return new NexoriSetPlayerOutcomeResult(
                NexoriSetPlayerOutcomeStatus.MATCH_MISSING,
                matchId,
                playerUuid,
                null,
                ""
            );
        }

        @Override
        public NexoriSetPlayerSpectatorResult setPlayerSpectator(
            String matchId,
            UUID playerUuid,
            boolean spectator,
            String reason
        ) {
            return new NexoriSetPlayerSpectatorResult(
                NexoriSetPlayerSpectatorStatus.MATCH_MISSING,
                matchId,
                playerUuid,
                spectator,
                ""
            );
        }

        @Override
        public NexoriReturnPlayerResult returnPlayerToLobby(String matchId, UUID playerUuid, int delaySeconds, String reason) {
            return new NexoriReturnPlayerResult(
                NexoriReturnPlayerStatus.MATCH_MISSING,
                matchId,
                playerUuid,
                0L,
                ""
            );
        }

        @Override
        public Optional<NexoriMatchResultRequirements> findMatchResultRequirements(String matchId) {
            return Optional.empty();
        }

        @Override
        public NexoriSubmitFinalMatchResultResult submitFinalMatchResult(NexoriSubmitFinalMatchResultRequest request) {
            return new NexoriSubmitFinalMatchResultResult(
                NexoriMatchCompletionStatus.MATCH_MISSING,
                NexoriBackendReportStatus.NOT_ATTEMPTED,
                "",
                ""
            );
        }

        @Override
        public NexoriCloseMatchAdmissionResult closeMatchAdmission(NexoriCloseMatchAdmissionRequest request) {
            return new NexoriCloseMatchAdmissionResult(
                NexoriCloseMatchAdmissionStatus.MATCH_MISSING,
                "",
                false,
                ""
            );
        }

        @Override
        public Optional<NexoriMatchPlacementState> findMatchPlacementState(String matchId) {
            return Optional.empty();
        }

        @Override
        public Optional<String> findMatchResolutionTriggerId(String matchId) {
            return Optional.empty();
        }
    }
}
