package io.github.hyjn.nexori.plugin.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DestinationTargetDiscoveryPayloadsTest {

    // ── DestinationTargetDiscoveryRequestPayload ───────────────────────────────

    @Test
    void requestPayloadPreservesRequestId() {
        DestinationTargetDiscoveryRequestPayload payload = new DestinationTargetDiscoveryRequestPayload("req-abc-123");
        assertEquals("req-abc-123", payload.requestId());
    }

    @Test
    void blankRequestIdPreservedAccordingToCurrentBehavior() {
        DestinationTargetDiscoveryRequestPayload payload = new DestinationTargetDiscoveryRequestPayload("   ");
        assertEquals("   ", payload.requestId(),
            "Record constructor does not validate or trim requestId; blank value is preserved as-is");
    }

    @Test
    void nullRequestIdPreservedAccordingToCurrentBehavior() {
        DestinationTargetDiscoveryRequestPayload payload = new DestinationTargetDiscoveryRequestPayload(null);
        assertNull(payload.requestId(),
            "Record constructor does not validate requestId; null is preserved as-is");
    }

    // ── DestinationTargetDiscoveryResponsePayload ──────────────────────────────

    @Test
    void responsePayloadPreservesRequestIdAndTargets() {
        DiscoveredDestinationTargetSummary summary = new DiscoveredDestinationTargetSummary(
            "target-1", "Target 1", "NATURAL_SPAWN", "world", "", "");
        DestinationTargetDiscoveryResponsePayload payload = new DestinationTargetDiscoveryResponsePayload(
            "req-123", List.of(summary));
        assertEquals("req-123", payload.requestId());
        assertEquals(1, payload.targets().size());
        assertEquals("target-1", payload.targets().get(0).id());
    }

    @Test
    void responsePayloadAllowsNullTargetsAccordingToCurrentBehavior() {
        DestinationTargetDiscoveryResponsePayload payload = new DestinationTargetDiscoveryResponsePayload("req-1", null);
        assertEquals("req-1", payload.requestId());
        assertNull(payload.targets(),
            "Record constructor does not normalize targets; null is preserved as-is");
    }

    @Test
    void responsePayloadEmptyTargetListIsPreserved() {
        DestinationTargetDiscoveryResponsePayload payload = new DestinationTargetDiscoveryResponsePayload("req-1", List.of());
        assertEquals(0, payload.targets().size());
    }
}
