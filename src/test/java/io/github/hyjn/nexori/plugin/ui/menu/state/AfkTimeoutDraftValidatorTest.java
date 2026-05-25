package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AfkTimeoutDraftValidatorTest {

    @Test
    void parseTimeoutSecondsRejectsNonNumericValue() {
        assertThrows(IllegalArgumentException.class, () -> AfkTimeoutDraftValidator.parseTimeoutSeconds("abc"));
    }

    @Test
    void parseTimeoutSecondsRejectsBelowMinimum() {
        assertThrows(
            IllegalArgumentException.class,
            () -> AfkTimeoutDraftValidator.parseTimeoutSeconds(Integer.toString(AfkDetectionPolicy.MIN_INACTIVITY_TIMEOUT_SECONDS - 1))
        );
    }

    @Test
    void parseTimeoutSecondsAcceptsValidValue() {
        assertEquals(30, AfkTimeoutDraftValidator.parseTimeoutSeconds(" 30 "));
    }
}
