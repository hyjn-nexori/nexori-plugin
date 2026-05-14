package io.github.hyjn.nexori.plugin.secure.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SecureReferralAcceptancePolicyTest {

    private final SecureReferralAcceptancePolicy policy = new SecureReferralAcceptancePolicy();

    @Test
    void acceptsWhenDecodedNotExpiredTrustedSignatureValidAndSupported() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, true, true, true);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.ACCEPTED, decision.outcome());
        assertTrue(decision.acceptedReferral());
    }

    @Test
    void rejectsExpiredReferral() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, true, true, true, true);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.EXPIRED, decision.outcome());
        assertEquals(SecureReferralAcceptancePolicy.EXPIRED_MESSAGE, decision.denyMessage());
    }

    @Test
    void rejectsUntrustedIssuer() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, false, true, true);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.ISSUER_NOT_TRUSTED, decision.outcome());
        assertEquals(SecureReferralAcceptancePolicy.ISSUER_NOT_TRUSTED_MESSAGE, decision.denyMessage());
    }

    @Test
    void rejectsInvalidSignature() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, true, false, true);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.SIGNATURE_INVALID, decision.outcome());
        assertEquals(SecureReferralAcceptancePolicy.SIGNATURE_INVALID_MESSAGE, decision.denyMessage());
    }

    @Test
    void rejectsUnsupportedPayloadType() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, true, true, false);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.PAYLOAD_TYPE_UNSUPPORTED, decision.outcome());
        assertEquals(SecureReferralAcceptancePolicy.PAYLOAD_TYPE_UNSUPPORTED_MESSAGE, decision.denyMessage());
    }

    @Test
    void rejectsNotDecodedReferral() {
        SecureReferralAcceptanceDecision decision = policy.decide(false, false, true, true, true);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.NOT_DECODED, decision.outcome());
        assertEquals("", decision.denyMessage());
        assertEquals(false, decision.acceptedReferral());
    }

    @Test
    void notDecodedTakesPriorityOverExpired() {
        SecureReferralAcceptanceDecision decision = policy.decide(false, true, false, false, false);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.NOT_DECODED, decision.outcome());
    }

    @Test
    void expiredTakesPriorityOverIssuer() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, true, false, false, false);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.EXPIRED, decision.outcome());
    }

    @Test
    void issuerTakesPriorityOverSignature() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, false, false, false);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.ISSUER_NOT_TRUSTED, decision.outcome());
    }

    @Test
    void signatureTakesPriorityOverUnsupportedPayloadType() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, true, false, false);

        assertEquals(SecureReferralAcceptanceDecision.Outcome.SIGNATURE_INVALID, decision.outcome());
    }

    @Test
    void acceptedDecisionHasNoDenyMessage() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, true, true, true);

        assertEquals("", decision.denyMessage());
    }

    @Test
    void rejectedDecisionIncludesExpectedDenyMessage() {
        SecureReferralAcceptanceDecision decision = policy.decide(true, false, true, true, false);

        assertEquals(
            "This Nexori referral type is not supported on the destination server.",
            decision.denyMessage()
        );
    }
}
