package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriActiveMatchInfo;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivityListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerAfkChangedEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetAfkDetectionPolicyResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetAfkDetectionPolicyStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetMatchAfkDetectionPolicyRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            NexoriAfkActivitySource.IDLE_TIMEOUT
        ));

        assertEquals(1, received.size());
    }

    @Test
    void setMatchAfkDetectionPolicyMapsPublicPolicyAndResetsAffectedPlayers() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.setMatchAfkDetectionPolicy("match-1", new AfkDetectionPolicy(true, 10)))
            .thenReturn(new ArenaMatchService.SetAfkDetectionPolicyResult(
                ArenaMatchService.SetAfkDetectionPolicyOutcome.UPDATED,
                "match-1",
                null,
                new AfkDetectionPolicy(true, 10),
                "",
                List.of(new ArenaMatchService.AfkPolicyStateAction(
                    PLAYER_ONE,
                    new EffectiveAfkDetectionPolicy("match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 10)),
                    ArenaMatchService.AfkPolicyStateActionType.RESET_TIMER
                ))
            ));
        AfkActivityService afkActivityService = new AfkActivityService(
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                "match-1",
                "queue-1",
                "arena-1",
                "rules-1",
                new AfkDetectionPolicy(true, 10)
            ))
        );
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", true, 1_000L);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            afkActivityService,
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetAfkDetectionPolicyResult result = bridge.setMatchAfkDetectionPolicy(
            new NexoriSetMatchAfkDetectionPolicyRequest("match-1", new NexoriAfkDetectionPolicy(true, 10))
        );

        assertEquals(NexoriSetAfkDetectionPolicyStatus.UPDATED, result.status());
        assertEquals(new NexoriAfkDetectionPolicy(true, 10), result.policy());
        assertEquals(0L, afkActivityService.lastActivityEpochMs(PLAYER_ONE));
    }

    @Test
    void policyDisabledActionClearsAfkWithPolicyChangeSource() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.setMatchAfkDetectionPolicy("match-1", new AfkDetectionPolicy(false, 30)))
            .thenReturn(new ArenaMatchService.SetAfkDetectionPolicyResult(
                ArenaMatchService.SetAfkDetectionPolicyOutcome.UPDATED,
                "match-1",
                null,
                new AfkDetectionPolicy(false, 30),
                "",
                List.of(new ArenaMatchService.AfkPolicyStateAction(
                    PLAYER_ONE,
                    new EffectiveAfkDetectionPolicy("match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(false, 30)),
                    ArenaMatchService.AfkPolicyStateActionType.CLEAR_FOR_POLICY_CHANGE
                ))
            ));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService afkActivityService = new AfkActivityService(
            null,
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                "match-1",
                "queue-1",
                "arena-1",
                "rules-1",
                new AfkDetectionPolicy(true, 5)
            )),
            transitions::add
        );
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 1_000L);
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 6_000L);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            afkActivityService,
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        bridge.setMatchAfkDetectionPolicy(
            new NexoriSetMatchAfkDetectionPolicyRequest("match-1", new NexoriAfkDetectionPolicy(false, 30))
        );

        assertFalse(afkActivityService.isAfk(PLAYER_ONE));
        assertEquals(NexoriAfkActivitySource.POLICY_CHANGE, transitions.get(1).source());
    }

    @Test
    void invalidAfkPolicyRequestReturnsInvalidPolicy() {
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            mock(ArenaMatchService.class),
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetAfkDetectionPolicyResult result = bridge.setMatchAfkDetectionPolicy(null);

        assertEquals(NexoriSetAfkDetectionPolicyStatus.INVALID_POLICY, result.status());
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
