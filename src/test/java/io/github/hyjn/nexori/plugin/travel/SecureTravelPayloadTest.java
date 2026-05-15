package io.github.hyjn.nexori.plugin.travel;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SecureTravelPayloadTest {

    @Test
    void preservesAllFields() {
        SecureTravelPayload payload = new SecureTravelPayload(
            "op-1", "srv-origin", "origin.srv:25565",
            "target-lobby", "natural_spawn", "keep_inventory",
            "Welcome!", "{}", "inv-transfer-1", null
        );
        assertEquals("op-1", payload.travelOperationId());
        assertEquals("srv-origin", payload.sourceServerId());
        assertEquals("origin.srv:25565", payload.sourceConnectionAddress());
        assertEquals("target-lobby", payload.destinationTargetId());
        assertEquals("natural_spawn", payload.arrivalPointId());
        assertEquals("keep_inventory", payload.travelProfileId());
        assertEquals("Welcome!", payload.arrivalMessage());
        assertEquals("{}", payload.contextJson());
        assertEquals("inv-transfer-1", payload.inventoryTransferId());
    }

    @Test
    void allowsNullInventoryStateAccordingToCurrentBehavior() {
        SecureTravelPayload payload = new SecureTravelPayload(
            "op-1", "srv-1", "srv.host:25565",
            "t", "", "keep_inventory", "", "{}", null, null
        );
        assertNull(payload.inventoryState(),
            "Record constructor does not validate inventoryState; null is preserved as-is");
    }

    @Test
    void preservesInventoryState() {
        ContainerTransferState empty = new ContainerTransferState("storage", 0, Map.of());
        InventoryTransferState state = new InventoryTransferState(
            1, empty, empty, empty, empty, empty, empty, 0, 0, 0
        );
        SecureTravelPayload payload = new SecureTravelPayload(
            "op-1", "srv-1", "srv.host:25565",
            "t", "", "keep_inventory", "", "{}", "inv-1", state
        );
        assertEquals(state, payload.inventoryState());
    }

    @Test
    void contextJsonPreservedExactly() {
        String contextJson = "{\"launchMode\":\"minigame\",\"matchId\":\"match-1\"}";
        SecureTravelPayload payload = new SecureTravelPayload(
            "op-1", "srv-1", "srv.host:25565",
            "t", "", "keep_inventory", "", contextJson, null, null
        );
        assertEquals(contextJson, payload.contextJson());
    }

    @Test
    void inventoryTransferIdPreservedExactly() {
        SecureTravelPayload payload = new SecureTravelPayload(
            "op-1", "srv-1", "srv.host:25565",
            "t", "", "keep_inventory", "", "{}", "transfer-abc-123", null
        );
        assertEquals("transfer-abc-123", payload.inventoryTransferId());
    }
}
