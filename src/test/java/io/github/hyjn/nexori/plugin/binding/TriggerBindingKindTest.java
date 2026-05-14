package io.github.hyjn.nexori.plugin.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TriggerBindingKindTest {

    @Test
    void parsePortalCollisionEnter() {
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, TriggerBindingKind.parse("PORTAL_COLLISION_ENTER"));
    }

    @Test
    void parsePortalAlias() {
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, TriggerBindingKind.parse("PORTAL"));
    }

    @Test
    void parseCommand() {
        assertEquals(TriggerBindingKind.COMMAND, TriggerBindingKind.parse("COMMAND"));
    }

    @Test
    void parseNormalizesLowercase() {
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, TriggerBindingKind.parse("portal_collision_enter"));
    }

    @Test
    void parseNormalizesHyphens() {
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, TriggerBindingKind.parse("portal-collision-enter"));
    }

    @Test
    void parseNormalizesSpaces() {
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, TriggerBindingKind.parse("portal collision enter"));
    }

    @Test
    void parseUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> TriggerBindingKind.parse("BLOCK_BREAK"));
    }
}
