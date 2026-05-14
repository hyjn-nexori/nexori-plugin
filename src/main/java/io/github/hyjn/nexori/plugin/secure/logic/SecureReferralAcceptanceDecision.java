package io.github.hyjn.nexori.plugin.secure.logic;

import javax.annotation.Nonnull;

/**
 * Pure decision describing whether a resolved secure referral should be accepted.
 */
public record SecureReferralAcceptanceDecision(
    Outcome outcome,
    String denyMessage
) {

    public enum Outcome {
        ACCEPTED,
        NOT_DECODED,
        EXPIRED,
        ISSUER_NOT_TRUSTED,
        SIGNATURE_INVALID,
        PAYLOAD_TYPE_UNSUPPORTED
    }

    @Nonnull
    public static SecureReferralAcceptanceDecision accepted() {
        return new SecureReferralAcceptanceDecision(Outcome.ACCEPTED, "");
    }

    @Nonnull
    public static SecureReferralAcceptanceDecision rejected(@Nonnull Outcome outcome, @Nonnull String denyMessage) {
        return new SecureReferralAcceptanceDecision(outcome, denyMessage);
    }

    public boolean acceptedReferral() {
        return outcome == Outcome.ACCEPTED;
    }
}
