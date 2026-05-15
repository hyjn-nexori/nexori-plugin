package io.github.hyjn.nexori.plugin.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PortalBindingPayloadsTest {

    // ── PortalBindingApplyRequestPayload ──────────────────────────────────────

    @Test
    void applyRequestPreservesAllFields() {
        PortalBindingApplyRequestPayload payload = new PortalBindingApplyRequestPayload(
            "req-1", "portal-src", "remote.srv:25565", "target-a", "keep_inventory", "{}"
        );
        assertEquals("req-1", payload.requestId());
        assertEquals("portal-src", payload.sourcePortalId());
        assertEquals("remote.srv:25565", payload.destinationConnectionAddress());
        assertEquals("target-a", payload.destinationTargetId());
        assertEquals("keep_inventory", payload.travelProfileId());
        assertEquals("{}", payload.contextJson());
    }

    @Test
    void applyRequestNullOptionalFieldsPreservedAccordingToCurrentBehavior() {
        PortalBindingApplyRequestPayload payload = new PortalBindingApplyRequestPayload(
            "req-1", null, null, null, null, null
        );
        assertNull(payload.sourcePortalId(),
            "Record constructor does not normalize optional fields; null is preserved as-is");
        assertNull(payload.destinationConnectionAddress());
        assertNull(payload.destinationTargetId());
        assertNull(payload.travelProfileId());
        assertNull(payload.contextJson());
    }

    @Test
    void applyRequestBlankTravelProfilePreservedAccordingToCurrentBehavior() {
        PortalBindingApplyRequestPayload payload = new PortalBindingApplyRequestPayload(
            "req-1", "portal-src", "remote.srv:25565", "target-a", "   ", "{}"
        );
        assertEquals("   ", payload.travelProfileId(),
            "Record constructor does not trim travelProfileId; blank is preserved as-is");
    }

    // ── PortalBindingApplyResponsePayload ─────────────────────────────────────

    @Test
    void applyResponsePreservesRequestIdSuccessAndMessage() {
        PortalBindingApplyResponsePayload payload = new PortalBindingApplyResponsePayload(
            "req-99", true, "Applied portal binding on remote.srv:25565."
        );
        assertEquals("req-99", payload.requestId());
        assertTrue(payload.success());
        assertEquals("Applied portal binding on remote.srv:25565.", payload.message());
    }

    @Test
    void applyResponsePreservesFailure() {
        PortalBindingApplyResponsePayload payload = new PortalBindingApplyResponsePayload(
            "req-5", false, "Could not apply portal binding on remote.srv:25565: some error"
        );
        assertFalse(payload.success());
    }

    @Test
    void applyResponseNullMessagePreservedAccordingToCurrentBehavior() {
        PortalBindingApplyResponsePayload payload = new PortalBindingApplyResponsePayload("req-1", true, null);
        assertNull(payload.message(),
            "Record constructor does not validate message; null is preserved as-is");
    }
}
