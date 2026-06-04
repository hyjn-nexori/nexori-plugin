package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriActiveMatchInfo;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivityListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerAfkChangedEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetAfkDetectionPolicyResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetAfkDetectionPolicyStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetMatchAfkDetectionPolicyRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerAfkRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerAfkResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerAfkStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitFinalMatchResultRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class NexoriMinigameApiBridgeAfkTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    @SuppressWarnings("removal")
    void legacyMatchResolutionTriggerReturnsEmptyValueOnlyForExistingMatch() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        when(arenaMatchService.findActiveMatchInfo("missing")).thenReturn(Optional.empty());
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        assertEquals(Optional.of("none"), bridge.findMatchResolutionTriggerId("match-1"));
        assertEquals(Optional.empty(), bridge.findMatchResolutionTriggerId("missing"));
    }

    @Test
    @SuppressWarnings("removal")
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
        assertEquals("none", info.matchResolutionTriggerId());
    }

    @Test
    void submitFinalMatchResultAttachesLocalAfkReportToCustomData() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.submitFinalMatchResult(eq("match-1"), eq("finished"), any(JsonObject.class)))
            .thenReturn(ArenaMatchService.SubmitMatchResult.matchMissing("match-1"));
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
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            afkActivityService,
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );
        JsonObject customData = new JsonObject();
        customData.addProperty("round", 3);

        bridge.submitFinalMatchResult(new NexoriSubmitFinalMatchResultRequest("match-1", "finished", customData));

        ArgumentCaptor<JsonObject> customDataCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(arenaMatchService).submitFinalMatchResult(eq("match-1"), eq("finished"), customDataCaptor.capture());
        JsonObject enrichedCustomData = customDataCaptor.getValue();
        JsonObject report = enrichedCustomData.getAsJsonObject("nexoriAfk");
        JsonArray player = report.getAsJsonArray("players").get(0).getAsJsonArray();
        assertEquals(3, enrichedCustomData.get("round").getAsInt());
        assertEquals("match-1", report.get("matchId").getAsString());
        assertEquals(PLAYER_ONE.toString(), player.get(0).getAsString());
        assertTrue(player.get(2).getAsBoolean());
        assertEquals(NexoriAfkActivitySource.IDLE_TIMEOUT.name(), player.get(7).getAsJsonArray().get(0).getAsString());
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
    void setPlayerAfkTrueAddsPlayerToActiveMatchAfkList() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(playerUuid -> Optional.empty());
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.UPDATED, result.status());
        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(PLAYER_ONE), info.afkPlayerUuids());
    }

    @Test
    void setPlayerAfkTrueEmitsTransitionWithExternalApiSource() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService afkActivityService = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );

        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "test"));

        assertEquals(1, transitions.size());
        assertTrue(transitions.get(0).afk());
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(0).source());
    }

    @Test
    void setPlayerAfkFalseRemovesPlayerFromActiveMatchAfkList() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(playerUuid -> Optional.empty());
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );
        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "mark-afk"));

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, false, "clear-afk"));

        assertEquals(NexoriSetPlayerAfkStatus.UPDATED, result.status());
        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(), info.afkPlayerUuids());
    }

    @Test
    void setPlayerAfkFalseEmitsTransitionWithExternalApiSource() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService afkActivityService = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );
        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "mark-afk"));

        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, false, "clear-afk"));

        assertEquals(2, transitions.size());
        assertFalse(transitions.get(1).afk());
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(1).source());
    }

    @Test
    void setPlayerAfkRepeatStateReturnsUnchanged() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService afkActivityService = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );
        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "mark-afk"));

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "repeat"));

        assertEquals(NexoriSetPlayerAfkStatus.UNCHANGED, result.status());
        assertEquals(1, transitions.size());
    }

    @Test
    void setPlayerAfkWorksWhenAutomaticDetectionIsDisabled() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy("match-1", new AfkDetectionPolicy(false, 30)))
        );
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.UPDATED, result.status());
        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(PLAYER_ONE), info.afkPlayerUuids());
    }

    @Test
    void setPlayerAfkFalseClearsAutomaticAfk() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService afkActivityService = new AfkActivityService(
            null,
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 5)
            )),
            transitions::add
        );
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 1_000L);
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 6_000L);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );

        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, false, "force-clear"));

        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(), info.afkPlayerUuids());
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(1).source());
    }

    @Test
    void setPlayerAfkWithNullRequestReturnsInvalidRequest() {
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            mock(ArenaMatchService.class),
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(null);

        assertEquals(NexoriSetPlayerAfkStatus.INVALID_REQUEST, result.status());
    }

    @Test
    void setPlayerAfkWithBlankMatchIdReturnsInvalidRequest() {
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            mock(ArenaMatchService.class),
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("  ", PLAYER_ONE, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.INVALID_REQUEST, result.status());
    }

    @Test
    void setPlayerAfkWithNullPlayerUuidReturnsInvalidRequest() {
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            mock(ArenaMatchService.class),
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", null, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.INVALID_REQUEST, result.status());
    }

    @Test
    void setPlayerAfkWithMissingMatchReturnsMatchMissing() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("missing-match", PLAYER_ONE))
            .thenReturn(new ArenaMatchService.SetPlayerAfkResult(
                ArenaMatchService.SetPlayerAfkOutcome.MATCH_MISSING,
                "missing-match", "", "", "", null, "", "Match is not active."
            ));
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("missing-match", PLAYER_ONE, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.MATCH_MISSING, result.status());
    }

    @Test
    void setPlayerAfkWithPlayerNotInMatchReturnsPlayerMissing() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_TWO))
            .thenReturn(new ArenaMatchService.SetPlayerAfkResult(
                ArenaMatchService.SetPlayerAfkOutcome.PLAYER_MISSING,
                "match-1", "queue-1", "arena-1", "rules-1", PLAYER_TWO, "", "Player is not part of the active match."
            ));
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_TWO, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.PLAYER_MISSING, result.status());
    }

    @Test
    void setPlayerAfkWithCompletedMatchReturnsMatchAlreadyCompleted() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE))
            .thenReturn(new ArenaMatchService.SetPlayerAfkResult(
                ArenaMatchService.SetPlayerAfkOutcome.MATCH_ALREADY_COMPLETED,
                "match-1", "queue-1", "arena-1", "rules-1", PLAYER_ONE, "", "Match result was already submitted."
            ));
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService,
            new AfkActivityService(playerUuid -> Optional.empty()),
            null,
            new NexoriMatchLifecycleDispatcher(),
            new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "test"));

        assertEquals(NexoriSetPlayerAfkStatus.MATCH_ALREADY_COMPLETED, result.status());
    }

    @Test
    void setPlayerAfkEmitsPlayerNameWhenNoActivityHistory() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE))
            .thenReturn(validatedResult(PLAYER_ONE, "PlayerOne"));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        // No prior handlePlayerInputTick — AfkActivityService has never seen this player
        AfkActivityService afkActivityService = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );

        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "test"));

        assertEquals(1, transitions.size());
        assertEquals("PlayerOne", transitions.get(0).playerName());
        assertEquals(NexoriAfkActivitySource.EXTERNAL_API, transitions.get(0).source());
    }

    @Test
    void setPlayerAfkFalseWhenAlreadyActiveReturnsUnchanged() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        List<AfkActivityService.AfkActivityTransition> transitions = new ArrayList<>();
        AfkActivityService afkActivityService = new AfkActivityService(null, playerUuid -> Optional.empty(), transitions::add);
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );

        NexoriSetPlayerAfkResult result = bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, false, "already-active"));

        assertEquals(NexoriSetPlayerAfkStatus.UNCHANGED, result.status());
        assertEquals(0, transitions.size());
    }

    @Test
    void setPlayerAfkAfkStatePersistsAcrossTicksWhenDetectorIsDisabled() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy("match-1", new AfkDetectionPolicy(false, 30)))
        );
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );
        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "mark-afk"));

        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 60_000L);

        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(PLAYER_ONE), info.afkPlayerUuids());
    }

    @Test
    void automaticInputClearsExternallySetAfkWhenDetectorEnabled() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 5)
            ))
        );
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );
        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, true, "mark-afk"));

        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", true, 10_000L);

        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(), info.afkPlayerUuids());
    }

    @Test
    void setPlayerAfkFalseResetsTimerToPreventImmediateReTrigger() {
        ArenaMatchService arenaMatchService = mock(ArenaMatchService.class);
        when(arenaMatchService.validateForExternalAfk("match-1", PLAYER_ONE)).thenReturn(validatedResult(PLAYER_ONE));
        when(arenaMatchService.findActiveMatchInfo("match-1")).thenReturn(Optional.of(activeMatchInfo()));
        AfkActivityService afkActivityService = new AfkActivityService(
            playerUuid -> Optional.of(new EffectiveAfkDetectionPolicy(
                "match-1", "queue-1", "arena-1", "rules-1", new AfkDetectionPolicy(true, 5)
            ))
        );
        NexoriMinigameApiBridge bridge = new NexoriMinigameApiBridge(
            arenaMatchService, afkActivityService, null, new NexoriMatchLifecycleDispatcher(), new NexoriAfkActivityDispatcher()
        );
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 1_000L);
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, 6_001L);
        assertTrue(afkActivityService.isAfk(PLAYER_ONE));
        bridge.setPlayerAfk(new NexoriSetPlayerAfkRequest("match-1", PLAYER_ONE, false, "force-clear"));

        // Tick 1000ms after the external clear — well under the 5000ms timeout — should not re-trigger AFK
        long resetTime = afkActivityService.lastActivityEpochMs(PLAYER_ONE);
        afkActivityService.handlePlayerInputTick(PLAYER_ONE, "PlayerOne", false, resetTime + 1_000L);

        NexoriActiveMatchInfo info = bridge.findActiveMatchInfo("match-1").orElseThrow();
        assertEquals(List.of(), info.afkPlayerUuids());
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

    private static ArenaMatchService.SetPlayerAfkResult validatedResult(UUID playerUuid) {
        return validatedResult(playerUuid, "PlayerOne");
    }

    private static ArenaMatchService.SetPlayerAfkResult validatedResult(UUID playerUuid, String username) {
        return new ArenaMatchService.SetPlayerAfkResult(
            ArenaMatchService.SetPlayerAfkOutcome.VALIDATED,
            "match-1",
            "queue-1",
            "arena-1",
            "rules-1",
            playerUuid,
            username,
            ""
        );
    }
}
