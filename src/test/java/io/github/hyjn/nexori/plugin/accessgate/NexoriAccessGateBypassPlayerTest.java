package io.github.hyjn.nexori.plugin.accessgate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class NexoriAccessGateBypassPlayerTest {

    @Test
    void normalizedTrimsAndLowercasesUuid() {
        NexoriAccessGateBypassPlayer normalized = new NexoriAccessGateBypassPlayer(" ABC-123 ", "").normalized();

        assertEquals("abc-123", normalized.uuid());
    }

    @Test
    void normalizedTrimsUsername() {
        NexoriAccessGateBypassPlayer normalized = new NexoriAccessGateBypassPlayer("uuid", " Janiel ").normalized();

        assertEquals("Janiel", normalized.username());
    }

    @Test
    void normalizedHandlesNullUuidAsBlank() {
        NexoriAccessGateBypassPlayer normalized = new NexoriAccessGateBypassPlayer(null, "Janiel").normalized();

        assertEquals("", normalized.uuid());
    }

    @Test
    void normalizedHandlesNullUsernameAsBlank() {
        NexoriAccessGateBypassPlayer normalized = new NexoriAccessGateBypassPlayer("uuid", null).normalized();

        assertEquals("", normalized.username());
    }
}
