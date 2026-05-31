package io.github.hyjn.nexori.plugin.minigame.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class MinigameTransferPhaseTest {

    @Test
    void allPhasesAreNonNull() {
        for (MinigameTransferPhase phase : MinigameTransferPhase.values()) {
            assertNotNull(phase);
        }
    }

    @Test
    void failureReasonConstantsAreNonNull() {
        assertNotNull(MinigameTransferFailureReason.INVALID_LAUNCH_CONTEXT);
        assertNotNull(MinigameTransferFailureReason.MISSING_MATCH_FOR_BACKFILL);
        assertNotNull(MinigameTransferFailureReason.BACKFILL_RESERVATION_REJECTED);
        assertNotNull(MinigameTransferFailureReason.INSTANCE_WORLD_MISSING);
        assertNotNull(MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED);
        assertNotNull(MinigameTransferFailureReason.INSTANCE_READY_TIMEOUT);
        assertNotNull(MinigameTransferFailureReason.WRONG_WORLD_TIMEOUT);
        assertNotNull(MinigameTransferFailureReason.TELEPORT_COMPONENT_STUCK_TIMEOUT);
        assertNotNull(MinigameTransferFailureReason.POSITION_VALIDATION_TIMEOUT);
        assertNotNull(MinigameTransferFailureReason.PLAYER_DISCONNECTED_DURING_PLACEMENT);
    }
}
