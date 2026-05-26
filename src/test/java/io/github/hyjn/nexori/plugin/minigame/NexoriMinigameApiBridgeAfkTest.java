package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriActiveMatchInfo;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivityListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerAfkChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class NexoriMinigameApiBridgeAfkTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void activeMatchInfoIncludesOnlyAfkPlayersFromActivityService() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                "match-1",
                "queue-1",
                "arena-1",
                "rules-1",
                new AfkDetectionPolicy(true, 5)
            ))
        );
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 1_000L);
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 6_000L);
        afkActivityService.handlePlayerInputTick(PLAYER_TWO, "PlayerTwo", true, 6_000L);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            afkActivityService,
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();

        assertEquals(List.of(PLAYER_ONE), info.afkPlayerUuids());
    }

    @Test
    void registerAfkActivityListenerRoutesToDispatcher() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        NexoriAfkActivityDispatcher dispatcher = new NexoriAfkActivityDispatcher();
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            dispatcher
        );
        List<NexoriPlayerAfkChangedEvent> received = new ArrayList<>();

        bridge.registerAfkActivityListener("rules-1", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                received.add(event);
            }
        });
        dispatcher.dispatchPlayerAfkChanged(new NexoriPlayerAfkChangedEvent(
            "match-1",
            "queue-1",
            "arena-1",
            "rules-1",
            PLAYER_ONE,
            "PlayerOne",
            true,
            10_000L,
            5_000L,
            io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource.IDLE_TIMEOUT
        ));

        assertEquals(1, received.size());
    }

    private static ArenaMatchService.ActiveMatchInfo activeMatchInfo() {
        return new ArenaMatchService.ActiveMatchInfo(
            "match-1",
            "queue-1",
            "arena-1",
            "assignment-1",
            "external-1",
            "rules-1",
            "none",
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(),
            List.of(),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(),
            2,
            0L,
            0L
        );
    }
}
