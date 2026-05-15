package io.github.hyjn.nexori.plugin.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DestinationTargetSyncPayloadsTest {

    private static DiscoveredDestinationTargetSet discoverySet() {
        return new DiscoveredDestinationTargetSet("srv.example.com:25565", "srv-1", 1000L, List.of());
    }

    // ── DestinationTargetSyncApplyRequestPayload ───────────────────────────────

    @Test
    void applyRequestPreservesRequestIdAndDiscoveries() {
        DestinationTargetSyncApplyRequestPayload payload = new DestinationTargetSyncApplyRequestPayload(
            "req-42", List.of(discoverySet()));
        assertEquals("req-42", payload.requestId());
        assertEquals(1, payload.discoveries().size());
    }

    @Test
    void applyRequestAllowsNullDiscoveriesAccordingToCurrentBehavior() {
        DestinationTargetSyncApplyRequestPayload payload = new DestinationTargetSyncApplyRequestPayload("req-1", null);
        assertEquals("req-1", payload.requestId());
        assertNull(payload.discoveries(),
            "Record constructor does not validate discoveries; null is preserved as-is");
    }

    @Test
    void applyRequestNullRequestIdPreservedAccordingToCurrentBehavior() {
        DestinationTargetSyncApplyRequestPayload payload = new DestinationTargetSyncApplyRequestPayload(null, List.of());
        assertNull(payload.requestId());
    }

    // ── DestinationTargetSyncApplyResponsePayload ──────────────────────────────

    @Test
    void responsePayloadPreservesRequestIdSuccessAndMessage() {
        DestinationTargetSyncApplyResponsePayload payload = new DestinationTargetSyncApplyResponsePayload(
            "req-99", true, "Sync applied.");
        assertEquals("req-99", payload.requestId());
        assertTrue(payload.success());
        assertEquals("Sync applied.", payload.message());
    }

    @Test
    void responsePayloadPreservesFailure() {
        DestinationTargetSyncApplyResponsePayload payload = new DestinationTargetSyncApplyResponsePayload(
            "req-5", false, "Could not synchronize.");
        assertFalse(payload.success());
        assertEquals("Could not synchronize.", payload.message());
    }

    @Test
    void responsePayloadBlankMessagePreservedAccordingToCurrentBehavior() {
        DestinationTargetSyncApplyResponsePayload payload = new DestinationTargetSyncApplyResponsePayload("req-1", true, "   ");
        assertEquals("   ", payload.message(),
            "Record constructor does not trim message; blank value is preserved as-is");
    }

    @Test
    void responsePayloadNullMessagePreservedAccordingToCurrentBehavior() {
        DestinationTargetSyncApplyResponsePayload payload = new DestinationTargetSyncApplyResponsePayload("req-1", true, null);
        assertNull(payload.message());
    }
}
