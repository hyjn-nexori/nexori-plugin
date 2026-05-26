package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AfkActivityServiceTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void playerInputActivityUpdatesLastActivityAndPreventsAfk() {
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 30);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 30_999L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(1_000L, service.lastActivityEpochMs(PLAYER_UUID));
    }

    @Test
    void playerBecomesAfkAfterTimeoutWithoutActivity() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 30, transitions);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 31_000L);

        assertTrue(service.isAfk(PLAYER_UUID));
        assertEquals(1, transitions.size());
        assertTrue(transitions.get(0).afk());
        assertEquals(NexoriAfkActivitySource.IDLE_TIMEOUT, transitions.get(0).source());
        assertEquals(30_000L, transitions.get(0).idleMs());
    }

    @Test
    void playerInputActivityClearsAfkStateAndEmitsTransition() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 30, transitions);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 31_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 31_500L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(31_500L, service.lastActivityEpochMs(PLAYER_UUID));
        assertEquals(2, transitions.size());
        assertFalse(transitions.get(1).afk());
        assertEquals(NexoriAfkActivitySource.PLAYER_INPUT, transitions.get(1).source());
        assertEquals(30_500L, transitions.get(1).idleMs());
    }

    @Test
    void inventoryActivityClearsAfkStateAndEmitsTransition() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 30, transitions);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 31_000L);
        service.markInventoryActivity(PLAYER_UUID, "PlayerOne", 31_500L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(31_500L, service.lastActivityEpochMs(PLAYER_UUID));
        assertEquals(2, transitions.size());
        assertFalse(transitions.get(1).afk());
        assertEquals(NexoriAfkActivitySource.INVENTORY_PACKET, transitions.get(1).source());
        assertEquals(30_500L, transitions.get(1).idleMs());
    }

    @Test
    void inactiveMatchPlayersAreNotTracked() {
        AfkActivityService service = serviceForActivePlayers(Set.of(), true, 30);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 1_000L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(0L, service.lastActivityEpochMs(PLAYER_UUID));
    }

    @Test
    void disabledPolicyRemovesStaleStateAndDoesNotEvaluateTimeout() {
        boolean[] enabled = {true};
        AfkActivityService service = new AfkActivityService(
            null,
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy("match-1", new AfkDetectionPolicy(enabled[0], 5)))
        );

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 1_000L);
        assertEquals(1_000L, service.lastActivityEpochMs(PLAYER_UUID));

        enabled[0] = false;
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 10_000L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(0L, service.lastActivityEpochMs(PLAYER_UUID));
    }

    @Test
    void policyChangeClearsAfkStateAndEmitsTransition() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 5, transitions);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 6_000L);
        service.clearPlayerForPolicyChange(
            PLAYER_UUID,
            new EffectiveAfkDetectionPolicy(
                "match-1",
                "queue-1",
                "arena-1",
                "rules-1",
                new AfkDetectionPolicy(false, 5)
            ),
            7_000L
        );

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(), service.afkPlayerUuids("match-1"));
        assertEquals(2, transitions.size());
        assertFalse(transitions.get(1).afk());
        assertEquals(NexoriAfkActivitySource.POLICY_CHANGE, transitions.get(1).source());
        assertEquals(6_000L, transitions.get(1).idleMs());
    }

    @Test
    void configuredTimeoutControlsAfkThreshold() {
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 10);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 10_999L);
        assertFalse(service.isAfk(PLAYER_UUID));

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 11_000L);
        assertTrue(service.isAfk(PLAYER_UUID));
    }

    @Test
    void afkPlayerUuidsContainsOnlyAfkPlayersForMatch() {
        UUID otherPlayerUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AfkActivityService service = new AfkActivityService(
            null,
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                playerUuid.equals(PLAYER_UUID) ? "match-1" : "match-2",
                new AfkDetectionPolicy(true, 5)
            ))
        );

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 6_000L);
        service.handlePlayerInputTick(otherPlayerUuid, "PlayerTwo", false, 1_000L);

        assertEquals(List.of(PLAYER_UUID), service.afkPlayerUuids("match-1"));
        assertEquals(List.of(), service.afkPlayerUuids("match-2"));

        service.removeMatch("match-1");

        assertEquals(List.of(), service.afkPlayerUuids("match-1"));
    }

    @Test
    void setExternalAfkTrueWhenActiveReturnsUpdatedAndAddsToAfkList() {
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty());
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );

        boolean changed = service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        assertTrue(changed);
        assertTrue(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(PLAYER_UUID), service.afkPlayerUuids("match-1"));
    }

    @Test
    void setExternalAfkTrueEmitsExternalApiTransition() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );

        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        assertEquals(1, transitions.size());
        assertTrue(transitions.get(0).afk());
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(0).source());
        assertEquals("match-1", transitions.get(0).matchId());
    }

    @Test
    void setExternalAfkFalseWhenAfkReturnsUpdatedAndRemovesFromAfkList() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        boolean changed = service.setExternalAfk(PLAYER_UUID, context, false, 6_000L);

        assertTrue(changed);
        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(), service.afkPlayerUuids("match-1"));
    }

    @Test
    void setExternalAfkFalseEmitsExternalApiTransition() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        service.setExternalAfk(PLAYER_UUID, context, false, 6_000L);

        assertEquals(2, transitions.size());
        assertFalse(transitions.get(1).afk());
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(1).source());
    }

    @Test
    void setExternalAfkSameStateReturnsUnchangedAndEmitsNoTransition() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        boolean changed = service.setExternalAfk(PLAYER_UUID, context, true, 6_000L);

        assertFalse(changed);
        assertEquals(1, transitions.size());
    }

    @Test
    void setExternalAfkWorksWhenAutomaticDetectionIsDisabled() {
        boolean[] enabled = {false};
        AfkActivityService service = new AfkActivityService(
            null,
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy("match-1", new AfkDetectionPolicy(enabled[0], 30)))
        );
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );

        boolean changed = service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        assertTrue(changed);
        assertTrue(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(PLAYER_UUID), service.afkPlayerUuids("match-1"));
    }

    @Test
    void setExternalAfkAfkStatePersistsAcrossTicksWhenDetectorIsDisabled() {
        AfkActivityService service = new AfkActivityService(
            null,
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy("match-1", new AfkDetectionPolicy(false, 30)))
        );
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        // Simulate detector ticks with disabled policy — AFK state must survive
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 6_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 7_000L);

        assertTrue(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(PLAYER_UUID), service.afkPlayerUuids("match-1"));
    }

    @Test
    void setExternalAfkFalseResetsTimerToPreventImmediateReTrigger() {
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 5);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 6_000L);
        assertTrue(service.isAfk(PLAYER_UUID));
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 5)
        );

        service.setExternalAfk(PLAYER_UUID, context, false, 7_000L);

        // Timer was reset to 7_000, so the player is no longer AFK and the timeout won't re-fire immediately
        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(7_000L, service.lastActivityEpochMs(PLAYER_UUID));

        // One more tick just below the timeout — still not AFK
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 11_999L);
        assertFalse(service.isAfk(PLAYER_UUID));
    }

    @Test
    void setExternalAfkFalseClearsAutomaticAfk() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 5, transitions);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 6_000L);
        assertTrue(service.isAfk(PLAYER_UUID));
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 5)
        );

        service.setExternalAfk(PLAYER_UUID, context, false, 7_000L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(), service.afkPlayerUuids("match-1"));
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(1).source());
        assertFalse(transitions.get(1).afk());
    }

    @Test
    void automaticInputClearsExternallySetAfkWhenDetectorEnabled() {
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), true, 30, transitions);
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 1_000L);
        assertTrue(service.isAfk(PLAYER_UUID));

        // Player input clears AFK in single-state model when detector is enabled
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 2_000L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(2, transitions.size());
        assertFalse(transitions.get(1).afk());
        assertEquals(NexoriAfkActivitySource.PLAYER_INPUT, transitions.get(1).source());
    }

    @Test
    void removePlayerClearsExternallySetAfk() {
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty());
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        service.removePlayer(PLAYER_UUID);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(), service.afkPlayerUuids("match-1"));
    }

    @Test
    void removeMatchClearsExternallySetAfk() {
        AfkActivityService service = new AfkActivityService(null, playerUuid -> Optional.empty());
        EffectiveAfkDetectionPolicy context = new EffectiveAfkDetectionPolicy(
            "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)
        );
        service.setExternalAfk(PLAYER_UUID, context, true, 5_000L);

        service.removeMatch("match-1");

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(List.of(), service.afkPlayerUuids("match-1"));
    }

    private static AfkActivityService serviceForActivePlayers(Set<UUID> activePlayerUuids, boolean enabled, int inactivityTimeoutSeconds) {
        return serviceForActivePlayers(activePlayerUuids, enabled, inactivityTimeoutSeconds, new ArrayList<>());
    }

    private static AfkActivityService serviceForActivePlayers(
        Set<UUID> activePlayerUuids,
        boolean enabled,
        int inactivityTimeoutSeconds,
        List<AfkActivityService.AfkActivityTransition> transitions
    ) {
        return new AfkActivityService(
            null,
            playerUuid -> activePlayerUuids.contains(playerUuid)
                ? Optional.of(new EffectiveAfkDetectionPolicy("match-1", new AfkDetectionPolicy(enabled, inactivityTimeoutSeconds)))
                : Optional.empty(),
            transitions::add
        );
    }
}
