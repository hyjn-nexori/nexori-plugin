package io.github.hyjn.nexori.plugin.binding.logic;

import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TriggerBindingValidationPolicyTest {

    private TriggerBindingDefinition definition(
        TriggerBindingAction action,
        String queueId,
        String address,
        String targetId,
        String profileId
    ) {
        return new TriggerBindingDefinition(
            "binding-1", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-1",
            action, queueId, address, targetId, profileId, "{}", true
        );
    }

    // ── JOIN_QUEUE ────────────────────────────────────────────────────────────

    @Test
    void joinQueueWithNonBlankQueueIdSucceeds() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.JOIN_QUEUE, "my-queue", "", "", ""));

        assertEquals(TriggerBindingAction.JOIN_QUEUE, result.action());
        assertEquals("my-queue", result.queueId());
    }

    @Test
    void joinQueueWithBlankQueueIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            TriggerBindingValidationPolicy.normalizeAndValidate(
                definition(TriggerBindingAction.JOIN_QUEUE, "", "", "", "")));
    }

    @Test
    void joinQueueWithWhitespaceOnlyQueueIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            TriggerBindingValidationPolicy.normalizeAndValidate(
                definition(TriggerBindingAction.JOIN_QUEUE, "   ", "", "", "")));
    }

    // ── LEAVE_QUEUE ───────────────────────────────────────────────────────────

    @Test
    void leaveQueueWithBlankQueueIdSucceedsAccordingToCurrentBehavior() {
        // LEAVE_QUEUE does not require a queueId — only JOIN_QUEUE does
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.LEAVE_QUEUE, "", "", "", ""));

        assertEquals(TriggerBindingAction.LEAVE_QUEUE, result.action());
    }

    // ── LOCAL_TARGET ──────────────────────────────────────────────────────────

    @Test
    void localTargetWithTargetIdSucceeds() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.LOCAL_TARGET, "", "", "lobby", ""));

        assertEquals(TriggerBindingAction.LOCAL_TARGET, result.action());
        assertEquals("lobby", result.destinationTargetId());
    }

    @Test
    void localTargetWithBlankTargetIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            TriggerBindingValidationPolicy.normalizeAndValidate(
                definition(TriggerBindingAction.LOCAL_TARGET, "", "", "", "")));
    }

    @Test
    void localTargetClearsQueueIdAndAddress() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.LOCAL_TARGET, "some-queue", "server:5520", "lobby", "keep_inventory"));

        assertEquals("", result.queueId());
        assertEquals("", result.destinationConnectionAddress());
    }

    @Test
    void localTargetClearsProfileAndSetsEmptyContextJson() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.LOCAL_TARGET, "", "", "lobby", "keep_inventory"));

        assertEquals("", result.travelProfileId());
        assertEquals("{}", result.contextJson());
    }

    @Test
    void localTargetNormalizesTargetIdToLowercase() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.LOCAL_TARGET, "", "", "  LOBBY  ", ""));

        assertEquals("lobby", result.destinationTargetId());
    }

    // ── TRAVEL ────────────────────────────────────────────────────────────────

    @Test
    void travelWithValidPeerAndTargetSucceeds() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.TRAVEL, "", "server:5520", "lobby", "keep_inventory"));

        assertEquals(TriggerBindingAction.TRAVEL, result.action());
        assertEquals("lobby", result.destinationTargetId());
    }

    @Test
    void travelWithBlankTargetIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            TriggerBindingValidationPolicy.normalizeAndValidate(
                definition(TriggerBindingAction.TRAVEL, "", "server:5520", "", "keep_inventory")));
    }

    @Test
    void travelWithBlankAddressThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            TriggerBindingValidationPolicy.normalizeAndValidate(
                definition(TriggerBindingAction.TRAVEL, "", "", "lobby", "keep_inventory")));
    }

    @Test
    void travelNormalizesAddressWithDefaultPortAccordingToCurrentBehavior() {
        // ConfiguredPeer.parse("server") appends default port 5520
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.TRAVEL, "", "server", "lobby", "keep_inventory"));

        assertEquals("server:5520", result.destinationConnectionAddress());
    }

    @Test
    void travelWithBlankProfileDefaultsToKeepInventory() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.TRAVEL, "", "server:5520", "lobby", ""));

        assertEquals("keep_inventory", result.travelProfileId());
    }

    @Test
    void travelClearsQueueId() {
        TriggerBindingDefinition result = TriggerBindingValidationPolicy.normalizeAndValidate(
            definition(TriggerBindingAction.TRAVEL, "some-queue", "server:5520", "lobby", "keep_inventory"));

        assertEquals("", result.queueId());
    }

    @Test
    void travelWithUnknownProfileThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            TriggerBindingValidationPolicy.normalizeAndValidate(
                definition(TriggerBindingAction.TRAVEL, "", "server:5520", "lobby", "invalid_profile")));
    }
}
