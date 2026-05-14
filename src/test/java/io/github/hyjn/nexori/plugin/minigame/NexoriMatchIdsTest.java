package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NexoriMatchIdsTest {

    @Test
    void normalizeGeneratedMatchIdTrimsAndLowercases() {
        assertEquals("match-one", NexoriMatchIds.normalizeGeneratedMatchId(" Match-One "));
    }

    @Test
    void normalizeGeneratedMatchIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> NexoriMatchIds.normalizeGeneratedMatchId(" "));
    }

    @Test
    void normalizeBackendOwnedMatchIdAllowsBlankAsOptional() {
        assertEquals("", NexoriMatchIds.normalizeBackendOwnedMatchId(" "));
        assertEquals("", NexoriMatchIds.normalizeBackendOwnedMatchId(null));
    }

    @Test
    void normalizeBackendOwnedMatchIdTrimsLowercasesAndAllowsInternalHyphens() {
        assertEquals("backend-match-1", NexoriMatchIds.normalizeBackendOwnedMatchId(" Backend-Match-1 "));
    }

    @Test
    void normalizeBackendOwnedMatchIdRejectsInvalidCharactersAndEdgeHyphens() {
        assertThrows(IllegalArgumentException.class, () -> NexoriMatchIds.normalizeBackendOwnedMatchId("match_one"));
        assertThrows(IllegalArgumentException.class, () -> NexoriMatchIds.normalizeBackendOwnedMatchId("-match"));
        assertThrows(IllegalArgumentException.class, () -> NexoriMatchIds.normalizeBackendOwnedMatchId("match-"));
    }

    @Test
    void normalizeBackendOwnedMatchIdRejectsTooLongValues() {
        String tooLong = "a" + "b".repeat(95) + "c";

        assertThrows(IllegalArgumentException.class, () -> NexoriMatchIds.normalizeBackendOwnedMatchId(tooLong));
    }

    @Test
    void normalizeRequiredMatchIdUsesGeneratedMatchIdMessageForBlankValues() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> NexoriMatchIds.normalizeRequiredMatchId(" ", "custom message")
        );

        assertEquals("Generated Nexori match id cannot be blank.", exception.getMessage());
    }
}
