package io.github.hyjn.nexori.plugin.accessgate.logic;

import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateConfigDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AccessGateAdmissionDeciderTest {

    private final AccessGateAdmissionDecider decider = new AccessGateAdmissionDecider();

    @Test
    void disabledConfigAllowsEvenWhenFull() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(false, 2, 0),
            99,
            AccessGateBypassType.NONE
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.ALLOW, decision.outcome());
        assertTrue(decision.shouldTrackPending());
    }

    @Test
    void hardCapDeniesNormalPlayer() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 2, 0),
            2,
            AccessGateBypassType.NONE
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_HARD_CAP, decision.outcome());
    }

    @Test
    void hardCapDeniesBypassPlayer() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 2, 1),
            2,
            AccessGateBypassType.UUID
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_HARD_CAP, decision.outcome());
    }

    @Test
    void publicCapDeniesNormalPlayer() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 2),
            2,
            AccessGateBypassType.NONE
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_PUBLIC_CAP, decision.outcome());
    }

    @Test
    void publicCapAllowsBypassPlayer() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 2),
            2,
            AccessGateBypassType.UUID
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.ALLOW, decision.outcome());
        assertEquals(AccessGateBypassType.UUID, decision.bypassType());
    }

    @Test
    void publicCapAllowsTrustedReferralBypass() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 2),
            2,
            AccessGateBypassType.TRUSTED_REFERRAL
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.ALLOW, decision.outcome());
        assertEquals(AccessGateBypassType.TRUSTED_REFERRAL, decision.bypassType());
    }

    @Test
    void hardCapTakesPriorityOverPublicCap() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 2),
            4,
            AccessGateBypassType.NONE
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_HARD_CAP, decision.outcome());
    }

    @Test
    void maxPlayersClampsToAtLeastOne() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 0, 0),
            0,
            AccessGateBypassType.NONE
        );

        assertEquals(1, decision.maxPlayers());
        assertEquals(AccessGateAdmissionDecision.Outcome.ALLOW, decision.outcome());
    }

    @Test
    void publicCapClampsToZero() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 99),
            0,
            AccessGateBypassType.NONE
        );

        assertEquals(0, decision.publicCap());
        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_PUBLIC_CAP, decision.outcome());
    }

    @Test
    void occupancyBelowPublicCapAllowsNormalPlayer() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 1),
            2,
            AccessGateBypassType.NONE
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.ALLOW, decision.outcome());
    }

    @Test
    void occupancyExactlyPublicCapDeniesNormalPlayer() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 1),
            3,
            AccessGateBypassType.NONE
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_PUBLIC_CAP, decision.outcome());
    }

    @Test
    void occupancyExactlyMaxPlayersDeniesEveryone() {
        AccessGateAdmissionDecision decision = decider.decide(
            config(true, 4, 1),
            4,
            AccessGateBypassType.TRUSTED_REFERRAL
        );

        assertEquals(AccessGateAdmissionDecision.Outcome.DENY_HARD_CAP, decision.outcome());
    }

    private static NexoriAccessGateConfigDocument config(boolean enabled, int maxPlayers, int reservedPrioritySlots) {
        return new NexoriAccessGateConfigDocument(
            NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION,
            enabled,
            maxPlayers,
            reservedPrioritySlots,
            NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE,
            true,
            false,
            "",
            List.of()
        );
    }
}
