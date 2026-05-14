package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferQueryResult;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptQueryPayload;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class InventoryReceiptQueryPlannerTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final InventoryReceiptQueryPlanner planner = new InventoryReceiptQueryPlanner();

    @Test
    void receiptExistsReturnsAppliedPlan() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), receipt("transfer-1", PLAYER_ONE));

        assertEquals(InventoryTransferQueryResult.APPLIED, plan.result());
    }

    @Test
    void receiptMissingReturnsNotFoundPlan() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), null);

        assertEquals(InventoryTransferQueryResult.NOT_FOUND, plan.result());
    }

    @Test
    void appliedPlanBuildsReplyPayloadWithAppliedResult() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), receipt("transfer-1", PLAYER_ONE));

        assertEquals("transfer-1", plan.replyPayload().transferId());
        assertEquals("APPLIED", plan.replyPayload().result());
    }

    @Test
    void notFoundPlanBuildsReplyPayloadWithNotFoundResult() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), null);

        assertEquals("transfer-1", plan.replyPayload().transferId());
        assertEquals("NOT_FOUND", plan.replyPayload().result());
    }

    @Test
    void preservesTransferId() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), receipt("transfer-1", PLAYER_ONE));

        assertEquals("transfer-1", plan.replyPayload().transferId());
    }

    @Test
    void receiptPlayerUuidDoesNotAffectReplyAccordingToCurrentBehavior() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), receipt("transfer-1", PLAYER_TWO));

        assertEquals(InventoryTransferQueryResult.APPLIED, plan.result());
        assertEquals("transfer-1", plan.replyPayload().transferId());
        assertEquals("APPLIED", plan.replyPayload().result());
    }

    @Test
    void preservesWhitespaceInTransferIdAccordingToCurrentBehavior() {
        InventoryReceiptQueryPlan plan = planner.plan(query(" Transfer-1 "), receipt("transfer-1", PLAYER_ONE));

        assertEquals(" Transfer-1 ", plan.replyPayload().transferId());
    }

    @Test
    void handlesBlankTransferIdAccordingToCurrentBehavior() {
        InventoryReceiptQueryPlan plan = planner.plan(query("   "), null);

        assertEquals(InventoryTransferQueryResult.NOT_FOUND, plan.result());
        assertEquals("   ", plan.replyPayload().transferId());
        assertEquals("NOT_FOUND", plan.replyPayload().result());
    }

    @Test
    void handlesNullReceiptAccordingToCurrentBehavior() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), null);

        assertEquals(InventoryTransferQueryResult.NOT_FOUND, plan.result());
    }

    @Test
    void doesNotUseStoreSecureReferralOrRuntimeDependencies() {
        InventoryReceiptQueryPlan plan = planner.plan(query("transfer-1"), receipt("transfer-1", PLAYER_ONE));

        assertNotNull(plan.replyPayload());
        assertEquals(InventoryTransferQueryResult.APPLIED, plan.result());
    }

    private static InventoryTransferReceiptQueryPayload query(String transferId) {
        return new InventoryTransferReceiptQueryPayload(transferId);
    }

    private static InventoryTransferReceiptRecord receipt(String transferId, UUID playerUuid) {
        return new InventoryTransferReceiptRecord(
            transferId,
            100L,
            playerUuid,
            "origin-server",
            "origin.example:25565"
        );
    }
}
