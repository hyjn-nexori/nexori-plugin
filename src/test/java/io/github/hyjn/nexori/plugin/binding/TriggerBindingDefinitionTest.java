package io.github.hyjn.nexori.plugin.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TriggerBindingDefinitionTest {

    // ── normalizeId ───────────────────────────────────────────────────────────

    @Test
    void normalizeIdTrimsAndLowercases() {
        assertEquals("my-binding", TriggerBindingDefinition.normalizeId("  My-Binding  "));
    }

    @Test
    void normalizeIdBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> TriggerBindingDefinition.normalizeId("   "));
    }

    // ── buildDefaultId ────────────────────────────────────────────────────────

    @Test
    void buildDefaultIdCombinesKindSourceAction() {
        String id = TriggerBindingDefinition.buildDefaultId(
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            "portal-abc",
            TriggerBindingAction.TRAVEL
        );
        assertEquals("portal_collision_enter.portal-abc.travel", id);
    }

    @Test
    void buildDefaultIdLowercasesEverything() {
        String id = TriggerBindingDefinition.buildDefaultId(
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            "Portal-XYZ",
            TriggerBindingAction.JOIN_QUEUE
        );
        assertEquals("portal_collision_enter.portal-xyz.join_queue", id);
    }

    @Test
    void buildDefaultIdBlankSourceThrows() {
        assertThrows(IllegalArgumentException.class, () -> TriggerBindingDefinition.buildDefaultId(
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            "   ",
            TriggerBindingAction.TRAVEL
        ));
    }

    // ── normalized() – TRAVEL action ──────────────────────────────────────────

    @Test
    void normalizedTravelClearsQueueId() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "default_travel", "{}", true);
        assertEquals("", binding.normalized().queueId());
    }

    @Test
    void normalizedTravelPreservesDestinationAddress() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "default_travel", "{}", true);
        assertEquals("server:5520", binding.normalized().destinationConnectionAddress());
    }

    @Test
    void normalizedTravelLowercasesDestinationAddress() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "SERVER:5520", "lobby", "default_travel", "{}", true);
        assertEquals("server:5520", binding.normalized().destinationConnectionAddress());
    }

    @Test
    void normalizedTravelPreservesTargetId() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "LOBBY", "default_travel", "{}", true);
        assertEquals("lobby", binding.normalized().destinationTargetId());
    }

    @Test
    void normalizedTravelPreservesTravelProfileId() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "CLEAR_INVENTORY", "{}", true);
        assertEquals("clear_inventory", binding.normalized().travelProfileId());
    }

    @Test
    void normalizedTravelContextJsonNullBecomesDefaultBraces() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "profile", null, true);
        assertEquals("{}", binding.normalized().contextJson());
    }

    @Test
    void normalizedTravelContextJsonBlankBecomesDefaultBraces() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "profile", "   ", true);
        assertEquals("{}", binding.normalized().contextJson());
    }

    @Test
    void normalizedTravelContextJsonPreservedWhenNonBlank() {
        String ctx = "{\"key\":\"value\"}";
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "profile", ctx, true);
        assertEquals(ctx, binding.normalized().contextJson());
    }

    @Test
    void normalizedTravelSourceIdIsLowercasedAndTrimmed() {
        TriggerBindingDefinition binding = travelBinding("  Portal-ABC  ", "server:5520", "lobby", "profile", "{}", true);
        assertEquals("portal-abc", binding.normalized().sourceId());
    }

    @Test
    void normalizedTravelGeneratesDefaultIdWhenIdBlank() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.TRAVEL, "", "server:5520", "lobby", "default_travel", "{}", true
        );
        TriggerBindingDefinition normalized = binding.normalized();

        String expectedId = TriggerBindingDefinition.buildDefaultId(
            TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1", TriggerBindingAction.TRAVEL
        );
        assertEquals(expectedId, normalized.id());
    }

    @Test
    void normalizedTravelPreservesEnabledFalse() {
        TriggerBindingDefinition binding = travelBinding("portal-1", "server:5520", "lobby", "profile", "{}", false);
        assertEquals(false, binding.normalized().enabled());
    }

    // ── normalized() – JOIN_QUEUE action ─────────────────────────────────────

    @Test
    void normalizedJoinQueueClearsDestinationFields() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.JOIN_QUEUE, "queue-abc", "server:5520", "target", "profile", "{}", true
        );
        TriggerBindingDefinition normalized = binding.normalized();

        assertEquals("", normalized.destinationConnectionAddress());
        assertEquals("", normalized.destinationTargetId());
        assertEquals("", normalized.travelProfileId());
        assertEquals("{}", normalized.contextJson());
    }

    @Test
    void normalizedJoinQueueRequiresQueueId() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.JOIN_QUEUE, "   ", "", "", "", "{}", true
        );
        assertThrows(IllegalArgumentException.class, binding::normalized);
    }

    @Test
    void normalizedJoinQueueLowercasesQueueId() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.JOIN_QUEUE, "QUEUE-ABC", "", "", "", "{}", true
        );
        assertEquals("queue-abc", binding.normalized().queueId());
    }

    // ── normalized() – LOCAL_TARGET action ───────────────────────────────────

    @Test
    void normalizedLocalTargetClearsQueueAndAddressFields() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.LOCAL_TARGET, "queue", "server:5520", "target", "profile", "{}", true
        );
        TriggerBindingDefinition normalized = binding.normalized();

        assertEquals("", normalized.queueId());
        assertEquals("", normalized.destinationConnectionAddress());
        assertEquals("", normalized.travelProfileId());
        assertEquals("{}", normalized.contextJson());
    }

    @Test
    void normalizedLocalTargetRequiresDestinationTargetId() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.LOCAL_TARGET, "", "", "   ", "", "{}", true
        );
        assertThrows(IllegalArgumentException.class, binding::normalized);
    }

    @Test
    void normalizedLocalTargetLowercasesTargetId() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            TriggerBindingAction.LOCAL_TARGET, "", "", "LOBBY-TARGET", "", "{}", true
        );
        assertEquals("lobby-target", binding.normalized().destinationTargetId());
    }

    // ── normalized() – defaults ───────────────────────────────────────────────

    @Test
    void normalizedNullKindDefaultsToPortalCollisionEnter() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "binding-id", null, "portal-1",
            TriggerBindingAction.TRAVEL, "", "server:5520", "lobby", "profile", "{}", true
        );
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, binding.normalized().triggerKind());
    }

    @Test
    void normalizedNullActionDefaultsToTravel() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "binding-id", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            null, "", "server:5520", "lobby", "profile", "{}", true
        );
        assertEquals(TriggerBindingAction.TRAVEL, binding.normalized().action());
    }

    @Test
    void normalizedBlankSourceIdThrows() {
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "binding-id", TriggerBindingKind.PORTAL_COLLISION_ENTER, "   ",
            TriggerBindingAction.TRAVEL, "", "server:5520", "lobby", "profile", "{}", true
        );
        assertThrows(IllegalArgumentException.class, binding::normalized);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TriggerBindingDefinition travelBinding(
        String sourceId,
        String destinationConnectionAddress,
        String destinationTargetId,
        String travelProfileId,
        String contextJson,
        boolean enabled
    ) {
        return new TriggerBindingDefinition(
            "",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            sourceId,
            TriggerBindingAction.TRAVEL,
            "",
            destinationConnectionAddress,
            destinationTargetId,
            travelProfileId,
            contextJson,
            enabled
        );
    }
}
