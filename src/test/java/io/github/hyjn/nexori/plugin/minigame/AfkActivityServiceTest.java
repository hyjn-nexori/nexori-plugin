package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

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
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), 30_000L);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 30_999L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(1_000L, service.lastActivityEpochMs(PLAYER_UUID));
    }

    @Test
    void playerBecomesAfkAfterTimeoutWithoutActivity() {
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), 30_000L);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 31_000L);

        assertTrue(service.isAfk(PLAYER_UUID));
    }

    @Test
    void inventoryActivityClearsAfkState() {
        AfkActivityService service = serviceForActivePlayers(Set.of(PLAYER_UUID), 30_000L);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 1_000L);
        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", false, 31_000L);
        service.markInventoryActivity(PLAYER_UUID, "PlayerOne", 31_500L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(31_500L, service.lastActivityEpochMs(PLAYER_UUID));
    }

    @Test
    void inactiveMatchPlayersAreNotTracked() {
        AfkActivityService service = serviceForActivePlayers(Set.of(), 30_000L);

        service.handlePlayerInputTick(PLAYER_UUID, "PlayerOne", true, 1_000L);

        assertFalse(service.isAfk(PLAYER_UUID));
        assertEquals(0L, service.lastActivityEpochMs(PLAYER_UUID));
    }

    private static AfkActivityService serviceForActivePlayers(Set<UUID> activePlayerUuids, long inactivityTimeoutMs) {
        return new AfkActivityService(
            null,
            playerUuid -> activePlayerUuids.contains(playerUuid) ? Optional.of("match-1") : Optional.empty(),
            inactivityTimeoutMs
        );
    }
}
