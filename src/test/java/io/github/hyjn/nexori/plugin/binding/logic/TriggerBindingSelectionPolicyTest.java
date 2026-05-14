package io.github.hyjn.nexori.plugin.binding.logic;

import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TriggerBindingSelectionPolicyTest {

    private TriggerBindingDefinition portalBinding(String id, String sourceId, TriggerBindingAction action) {
        return new TriggerBindingDefinition(id, TriggerBindingKind.PORTAL_COLLISION_ENTER, sourceId,
            action, "q", "s:5520", "t", "keep_inventory", "{}", true);
    }

    // ── listPortalCollisionBindings ───────────────────────────────────────────

    @Test
    void listPortalCollisionBindingsFiltersToMatchingSourceOnly() {
        List<TriggerBindingDefinition> all = List.of(
            portalBinding("b1", "portal-1", TriggerBindingAction.JOIN_QUEUE),
            portalBinding("b2", "portal-2", TriggerBindingAction.JOIN_QUEUE)
        );

        List<TriggerBindingDefinition> result = TriggerBindingSelectionPolicy.listPortalCollisionBindings(all, "portal-1");

        assertEquals(1, result.size());
        assertEquals("b1", result.get(0).id());
    }

    @Test
    void listPortalCollisionBindingsReturnsAllBindingsForSource() {
        List<TriggerBindingDefinition> all = List.of(
            portalBinding("b1", "portal-1", TriggerBindingAction.JOIN_QUEUE),
            portalBinding("b2", "portal-1", TriggerBindingAction.LOCAL_TARGET)
        );

        List<TriggerBindingDefinition> result = TriggerBindingSelectionPolicy.listPortalCollisionBindings(all, "portal-1");

        assertEquals(2, result.size());
    }

    @Test
    void listPortalCollisionBindingsSortsByActionPriorityAscending() {
        List<TriggerBindingDefinition> all = List.of(
            portalBinding("b-travel", "portal-1", TriggerBindingAction.TRAVEL),
            portalBinding("b-queue", "portal-1", TriggerBindingAction.JOIN_QUEUE),
            portalBinding("b-local", "portal-1", TriggerBindingAction.LOCAL_TARGET)
        );

        List<TriggerBindingDefinition> result = TriggerBindingSelectionPolicy.listPortalCollisionBindings(all, "portal-1");

        assertEquals("b-queue", result.get(0).id());
        assertEquals("b-local", result.get(1).id());
        assertEquals("b-travel", result.get(2).id());
    }

    @Test
    void listPortalCollisionBindingsReturnsEmptyForNoMatch() {
        List<TriggerBindingDefinition> all = List.of(
            portalBinding("b1", "portal-2", TriggerBindingAction.JOIN_QUEUE)
        );

        List<TriggerBindingDefinition> result = TriggerBindingSelectionPolicy.listPortalCollisionBindings(all, "portal-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void listPortalCollisionBindingsNormalizesSourceIdAccordingToCurrentBehavior() {
        List<TriggerBindingDefinition> all = List.of(
            portalBinding("b1", "portal-1", TriggerBindingAction.JOIN_QUEUE)
        );

        List<TriggerBindingDefinition> result = TriggerBindingSelectionPolicy.listPortalCollisionBindings(all, "  PORTAL-1  ");

        assertEquals(1, result.size());
    }

    @Test
    void listPortalCollisionBindingsReturnsEmptyForEmptyCollection() {
        List<TriggerBindingDefinition> result = TriggerBindingSelectionPolicy.listPortalCollisionBindings(List.of(), "portal-1");

        assertTrue(result.isEmpty());
    }

    // ── actionPriority ────────────────────────────────────────────────────────

    @Test
    void actionPriorityJoinQueueIsLowest() {
        assertEquals(0, TriggerBindingSelectionPolicy.actionPriority(TriggerBindingAction.JOIN_QUEUE));
    }

    @Test
    void actionPriorityLeaveQueueIsSecond() {
        assertEquals(1, TriggerBindingSelectionPolicy.actionPriority(TriggerBindingAction.LEAVE_QUEUE));
    }

    @Test
    void actionPriorityLocalTargetIsThird() {
        assertEquals(2, TriggerBindingSelectionPolicy.actionPriority(TriggerBindingAction.LOCAL_TARGET));
    }

    @Test
    void actionPriorityTravelIsHighest() {
        assertEquals(3, TriggerBindingSelectionPolicy.actionPriority(TriggerBindingAction.TRAVEL));
    }

    // ── isQueueAction ─────────────────────────────────────────────────────────

    @Test
    void isQueueActionDetectsJoinQueue() {
        assertTrue(TriggerBindingSelectionPolicy.isQueueAction(TriggerBindingAction.JOIN_QUEUE));
    }

    @Test
    void isQueueActionDetectsLeaveQueue() {
        assertTrue(TriggerBindingSelectionPolicy.isQueueAction(TriggerBindingAction.LEAVE_QUEUE));
    }

    @Test
    void isQueueActionReturnsFalseForTravel() {
        assertFalse(TriggerBindingSelectionPolicy.isQueueAction(TriggerBindingAction.TRAVEL));
    }

    @Test
    void isQueueActionReturnsFalseForLocalTarget() {
        assertFalse(TriggerBindingSelectionPolicy.isQueueAction(TriggerBindingAction.LOCAL_TARGET));
    }
}
