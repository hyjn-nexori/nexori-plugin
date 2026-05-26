package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArenaMatchServiceAfkPolicyOverrideTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void matchRuntimeOverrideWinsOverActiveMatchDefaultPolicy() {
        ArenaMatchService service = serviceWithMatch(new AfkDetectionPolicy(false, 30));

        ArenaMatchService.SetAfkDetectionPolicyResult result = service.setMatchAfkDetectionPolicy(
            "match-1",
            new AfkDetectionPolicy(true, 10)
        );

        EffectiveAfkDetectionPolicy effective = service.findEffectiveAfkDetectionPolicy(PLAYER_ONE).orElseThrow();
        assertEquals(ArenaMatchService.SetAfkDetectionPolicyOutcome.UPDATED, result.outcome());
        assertTrue(effective.policy().enabled());
        assertEquals(10, effective.policy().inactivityTimeoutSeconds());
        assertEquals(
            List.of(ArenaMatchService.AfkPolicyStateActionType.RESET_TIMER, ArenaMatchService.AfkPolicyStateActionType.RESET_TIMER),
            result.stateActions().stream().map(ArenaMatchService.AfkPolicyStateAction::type).toList()
        );
    }

    @Test
    void playerOverrideWinsOverMatchRuntimeOverride() {
        ArenaMatchService service = serviceWithMatch(new AfkDetectionPolicy(false, 30));

        service.setMatchAfkDetectionPolicy("match-1", new AfkDetectionPolicy(true, 30));
        ArenaMatchService.SetAfkDetectionPolicyResult result = service.setPlayerAfkDetectionPolicy(
            "match-1",
            PLAYER_ONE,
            new AfkDetectionPolicy(false, 30)
        );

        EffectiveAfkDetectionPolicy playerOnePolicy = service.findEffectiveAfkDetectionPolicy(PLAYER_ONE).orElseThrow();
        EffectiveAfkDetectionPolicy playerTwoPolicy = service.findEffectiveAfkDetectionPolicy(PLAYER_TWO).orElseThrow();
        assertFalse(playerOnePolicy.policy().enabled());
        assertTrue(playerTwoPolicy.policy().enabled());
        assertEquals(List.of(ArenaMatchService.AfkPolicyStateActionType.CLEAR_FOR_POLICY_CHANGE), result.stateActions().stream()
            .map(ArenaMatchService.AfkPolicyStateAction::type)
            .toList());
    }

    @Test
    void clearingPlayerOverrideFallsBackToMatchRuntimeOverride() {
        ArenaMatchService service = serviceWithMatch(new AfkDetectionPolicy(false, 30));
        service.setMatchAfkDetectionPolicy("match-1", new AfkDetectionPolicy(true, 15));
        service.setPlayerAfkDetectionPolicy("match-1", PLAYER_ONE, new AfkDetectionPolicy(false, 30));

        ArenaMatchService.SetAfkDetectionPolicyResult result = service.clearPlayerAfkDetectionPolicy("match-1", PLAYER_ONE);

        EffectiveAfkDetectionPolicy effective = service.findEffectiveAfkDetectionPolicy(PLAYER_ONE).orElseThrow();
        assertEquals(ArenaMatchService.SetAfkDetectionPolicyOutcome.CLEARED, result.outcome());
        assertTrue(effective.policy().enabled());
        assertEquals(15, effective.policy().inactivityTimeoutSeconds());
        assertEquals(List.of(ArenaMatchService.AfkPolicyStateActionType.RESET_TIMER), result.stateActions().stream()
            .map(ArenaMatchService.AfkPolicyStateAction::type)
            .toList());
    }

    @Test
    void clearingMatchOverrideFallsBackToActiveMatchDefaultPolicy() {
        ArenaMatchService service = serviceWithMatch(new AfkDetectionPolicy(true, 20));
        service.setMatchAfkDetectionPolicy("match-1", new AfkDetectionPolicy(false, 30));

        ArenaMatchService.SetAfkDetectionPolicyResult result = service.clearMatchAfkDetectionPolicy("match-1");

        EffectiveAfkDetectionPolicy effective = service.findEffectiveAfkDetectionPolicy(PLAYER_ONE).orElseThrow();
        assertEquals(ArenaMatchService.SetAfkDetectionPolicyOutcome.CLEARED, result.outcome());
        assertTrue(effective.policy().enabled());
        assertEquals(20, effective.policy().inactivityTimeoutSeconds());
    }

    @SuppressWarnings("unchecked")
    private static ArenaMatchService serviceWithMatch(AfkDetectionPolicy defaultPolicy) {
        ArenaMatchService service = new ArenaMatchService(null, null, null, null, null);
        ArenaActiveMatch match = activeMatch(defaultPolicy);
        try {
            Field matchesField = ArenaMatchService.class.getDeclaredField("matchesById");
            matchesField.setAccessible(true);
            ((Map<String, ArenaActiveMatch>) matchesField.get(service)).put(match.matchId(), match);
            Field matchByPlayerField = ArenaMatchService.class.getDeclaredField("matchIdByPlayerUuid");
            matchByPlayerField.setAccessible(true);
            Map<UUID, String> matchIdByPlayerUuid = (Map<UUID, String>) matchByPlayerField.get(service);
            matchIdByPlayerUuid.put(PLAYER_ONE, match.matchId());
            matchIdByPlayerUuid.put(PLAYER_TWO, match.matchId());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return service;
    }

    private static ArenaActiveMatch activeMatch(AfkDetectionPolicy defaultPolicy) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby.example:19132",
            "lobby-1.natural_spawn",
            "keep_inventory",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "",
            LastPlayerAliveArenaMatchResolutionTrigger.ID,
            "rules-default",
            "assignment-1",
            "INITIAL_MATCH",
            "external-match-1",
            ArenaMatchSource.BACKEND_DRIVEN.id(),
            1,
            4,
            true,
            QueueBackfillMode.ACTIVE_WINDOW.id(),
            30,
            defaultPolicy,
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            0L,
            0L,
            0L,
            0L,
            "",
            1_000L,
            1_000L,
            ""
        ).normalized();
    }
}
