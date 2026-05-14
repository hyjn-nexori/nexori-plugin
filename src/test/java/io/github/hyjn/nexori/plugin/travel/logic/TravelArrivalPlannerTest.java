package io.github.hyjn.nexori.plugin.travel.logic;

import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.SecureTravelPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TravelArrivalPlannerTest {

    private static final String OPERATION_ID = "travel-op-1";
    private static final String SOURCE_SERVER_ID = "source-server-1";
    private static final String SOURCE_CONNECTION_ADDRESS = "127.0.0.1:5520";
    private static final String TARGET_ID = "hub.portal";
    private static final String ARRIVAL_POINT_ID = "entry";
    private static final String PROFILE_ID = "keep_inventory";
    private static final String PAYLOAD_MESSAGE = "Payload arrival.";
    private static final String CONTEXT_JSON = "{\"flowType\":\"minigame.launch\"}";
    private static final String METADATA_JSON = "{\"position\":{\"x\":1}}";

    private final TravelArrivalPlanner planner = new TravelArrivalPlanner();

    @Test
    void blankTargetAndDefaultWorldContextPlansDefaultWorldResolution() {
        TravelArrivalPlan plan = planner.route(payload("   "), true, false);

        assertEquals(TravelArrivalPlan.Route.RESOLVE_DEFAULT_WORLD_NATURAL_SPAWN, plan.route());
    }

    @Test
    void blankTargetAndMinigameLaunchContextPlansDefaultWorldResolution() {
        TravelArrivalPlan plan = planner.route(payload(""), false, true);

        assertEquals(TravelArrivalPlan.Route.RESOLVE_DEFAULT_WORLD_NATURAL_SPAWN, plan.route());
    }

    @Test
    void blankTargetWithoutSpecialContextPlansServerHop() {
        TravelArrivalPlan plan = planner.route(payload(null), false, false);

        assertEquals(TravelArrivalPlan.Route.SERVER_HOP_WITHOUT_TARGET, plan.route());
    }

    @Test
    void nonBlankTargetPlansConfiguredTargetResolution() {
        TravelArrivalPlan plan = planner.route(payload(TARGET_ID), true, true);

        assertEquals(TravelArrivalPlan.Route.RESOLVE_CONFIGURED_TARGET, plan.route());
    }

    @Test
    void effectiveTargetIdForErrorsUsesTrimmedPayloadTarget() {
        TravelArrivalPlan plan = planner.route(payload(" " + TARGET_ID + " "), false, false);

        assertEquals(TARGET_ID, plan.effectiveTargetIdForErrors());
    }

    @Test
    void buildsPendingArrivalFromResolvedTarget() {
        PendingArrival arrival = acceptedPlan(resolvedTarget("Resolved arrival.")).pendingArrival();

        assertEquals(OPERATION_ID, arrival.travelOperationId());
        assertEquals(SOURCE_SERVER_ID, arrival.sourceServerId());
        assertEquals(SOURCE_CONNECTION_ADDRESS, arrival.sourceConnectionAddress());
        assertEquals(TARGET_ID, arrival.destinationTargetId());
        assertEquals("Hub Portal", arrival.destinationTargetDisplayName());
        assertEquals(DestinationTargetKind.PORTAL.name(), arrival.destinationTargetKind());
        assertEquals("world-hub", arrival.worldName());
        assertEquals(ARRIVAL_POINT_ID, arrival.arrivalPointId());
    }

    @Test
    void resolvedTargetArrivalMessageOverridesPayloadMessage() {
        PendingArrival arrival = acceptedPlan(resolvedTarget("Resolved arrival.")).pendingArrival();

        assertEquals("Resolved arrival.", arrival.arrivalMessage());
    }

    @Test
    void blankResolvedTargetArrivalMessageUsesPayloadMessage() {
        PendingArrival arrival = acceptedPlan(resolvedTarget("   ")).pendingArrival();

        assertEquals(PAYLOAD_MESSAGE, arrival.arrivalMessage());
    }

    @Test
    void buildArrivalMessageUsesFallbackWhenArrivalMessageBlank() {
        String message = planner.buildArrivalMessage(new PendingArrival(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            SOURCE_CONNECTION_ADDRESS,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            CONTEXT_JSON,
            METADATA_JSON
        ));

        assertEquals("Secure Nexori travel accepted.", message);
    }

    @Test
    void buildArrivalMessageIncludesNonBlankTargetKindWorldArrivalPointAndProfile() {
        String message = planner.buildArrivalMessage(acceptedPlan(resolvedTarget("Welcome.")).pendingArrival());

        assertEquals(
            "Welcome. target=hub.portal kind=PORTAL world=world-hub arrivalPoint=entry travelProfile=keep_inventory",
            message
        );
    }

    @Test
    void buildArrivalMessageOmitsBlankOptionalFields() {
        String message = planner.buildArrivalMessage(new PendingArrival(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            SOURCE_CONNECTION_ADDRESS,
            "",
            "",
            "",
            "",
            "",
            "",
            "Welcome.",
            CONTEXT_JSON,
            METADATA_JSON
        ));

        assertEquals("Welcome.", message);
    }

    @Test
    void pendingArrivalPreservesContextJsonAndMetadataJson() {
        PendingArrival arrival = acceptedPlan(resolvedTarget("Welcome.")).pendingArrival();

        assertEquals(CONTEXT_JSON, arrival.contextJson());
        assertEquals(METADATA_JSON, arrival.metadataJson());
    }

    @Test
    void pendingArrivalUsesProfileTypeId() {
        PendingArrival arrival = acceptedPlan(resolvedTarget("Welcome.")).pendingArrival();

        assertEquals(TravelProfileType.KEEP_INVENTORY.id(), arrival.travelProfileId());
    }

    @Test
    void acceptResolvedTargetUsesResolvedTargetIdForEffectiveTargetId() {
        TravelArrivalPlan plan = acceptedPlan(resolvedTarget("Welcome."));

        assertEquals(TARGET_ID, plan.effectiveTargetIdForErrors());
    }

    @Test
    void acceptedPendingArrivalWithBlankResolvedAndPayloadMessagesUsesFallbackBaseWhenBuilt() {
        TravelArrivalPlan plan = planner.acceptResolvedTarget(
            OPERATION_ID,
            payload(TARGET_ID, "   "),
            TravelProfileType.KEEP_INVENTORY,
            resolvedTarget("   ")
        );

        assertEquals(
            "Secure Nexori travel accepted. target=hub.portal kind=PORTAL world=world-hub arrivalPoint=entry travelProfile=keep_inventory",
            planner.buildArrivalMessage(plan.pendingArrival())
        );
    }

    @Test
    void acceptResolvedTargetExposesPendingArrivalOptional() {
        TravelArrivalPlan plan = acceptedPlan(resolvedTarget("Welcome."));

        assertTrue(plan.pendingArrivalOptional().isPresent());
        assertEquals(TravelArrivalPlan.Route.ACCEPT_RESOLVED_TARGET, plan.route());
    }

    private TravelArrivalPlan acceptedPlan(ResolvedDestinationTarget resolvedTarget) {
        return planner.acceptResolvedTarget(
            OPERATION_ID,
            payload(TARGET_ID),
            TravelProfileType.KEEP_INVENTORY,
            resolvedTarget
        );
    }

    private static SecureTravelPayload payload(String destinationTargetId) {
        return payload(destinationTargetId, PAYLOAD_MESSAGE);
    }

    private static SecureTravelPayload payload(String destinationTargetId, String arrivalMessage) {
        return new SecureTravelPayload(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            SOURCE_CONNECTION_ADDRESS,
            destinationTargetId,
            ARRIVAL_POINT_ID,
            PROFILE_ID,
            arrivalMessage,
            CONTEXT_JSON,
            "transfer-1",
            null
        );
    }

    private static ResolvedDestinationTarget resolvedTarget(String arrivalMessage) {
        return new ResolvedDestinationTarget(
            new DestinationTargetDefinition(
                TARGET_ID,
                "Hub Portal",
                DestinationTargetKind.PORTAL,
                "world-unused",
                "unused",
                arrivalMessage,
                METADATA_JSON
            ),
            "world-hub",
            ARRIVAL_POINT_ID
        );
    }
}
