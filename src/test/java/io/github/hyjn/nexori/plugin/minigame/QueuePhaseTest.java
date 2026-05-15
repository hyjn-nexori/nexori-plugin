package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class QueuePhaseTest {

    @Test
    void enumHasThreeValues() {
        assertEquals(3, QueuePhase.values().length);
    }

    @Test
    void valuesAreStable() {
        assertEquals(QueuePhase.WAITING, QueuePhase.valueOf("WAITING"));
        assertEquals(QueuePhase.COUNTDOWN, QueuePhase.valueOf("COUNTDOWN"));
        assertEquals(QueuePhase.READY, QueuePhase.valueOf("READY"));
    }
}
