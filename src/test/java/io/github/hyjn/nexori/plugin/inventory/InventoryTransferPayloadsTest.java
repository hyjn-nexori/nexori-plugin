package io.github.hyjn.nexori.plugin.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class InventoryTransferPayloadsTest {

    @Test
    void receiptQueryPayloadPreservesTransferId() {
        InventoryTransferReceiptQueryPayload payload = new InventoryTransferReceiptQueryPayload(" Transfer-1 ");

        assertEquals(" Transfer-1 ", payload.transferId());
    }

    @Test
    void receiptQueryPayloadHandlesBlankValuesAccordingToCurrentBehavior() {
        InventoryTransferReceiptQueryPayload payload = new InventoryTransferReceiptQueryPayload("   ");

        assertEquals("   ", payload.transferId());
    }

    @Test
    void receiptReplyPayloadPreservesTransferId() {
        InventoryTransferReceiptReplyPayload payload = new InventoryTransferReceiptReplyPayload(" Transfer-1 ", "APPLIED");

        assertEquals(" Transfer-1 ", payload.transferId());
    }

    @Test
    void receiptReplyPayloadPreservesResult() {
        InventoryTransferReceiptReplyPayload payload = new InventoryTransferReceiptReplyPayload("transfer-1", " applied ");

        assertEquals(" applied ", payload.result());
    }

    @Test
    void receiptReplyPayloadHandlesBlankValuesAccordingToCurrentBehavior() {
        InventoryTransferReceiptReplyPayload payload = new InventoryTransferReceiptReplyPayload("   ", "   ");

        assertEquals("   ", payload.transferId());
        assertEquals("   ", payload.result());
    }

    @Test
    void queryResultParsesKnownValuesAccordingToCurrentBehavior() {
        assertEquals(InventoryTransferQueryResult.APPLIED, InventoryTransferQueryResult.parse(" applied "));
        assertEquals(InventoryTransferQueryResult.NOT_FOUND, InventoryTransferQueryResult.parse("NOT_FOUND"));
    }

    @Test
    void queryResultParsesBlankAsNotFoundAccordingToCurrentBehavior() {
        assertEquals(InventoryTransferQueryResult.NOT_FOUND, InventoryTransferQueryResult.parse(null));
        assertEquals(InventoryTransferQueryResult.NOT_FOUND, InventoryTransferQueryResult.parse("   "));
    }

    @Test
    void queryResultRejectsUnknownValueAccordingToCurrentBehavior() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> InventoryTransferQueryResult.parse("missing")
        );

        assertEquals("Unknown Nexori inventory transfer query result 'missing'.", exception.getMessage());
    }
}
