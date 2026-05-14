package io.github.hyjn.nexori.plugin.secure.logic;

import javax.annotation.Nonnull;

/**
 * Evaluates the pure acceptance rules for a decoded secure referral.
 */
public final class SecureReferralAcceptancePolicy {

    public static final String EXPIRED_MESSAGE = "This Nexori referral expired before it could be used.";
    public static final String ISSUER_NOT_TRUSTED_MESSAGE = "This Nexori referral came from a server that is not in the current trust bundle.";
    public static final String SIGNATURE_INVALID_MESSAGE = "This Nexori referral signature was invalid.";
    public static final String PAYLOAD_TYPE_UNSUPPORTED_MESSAGE = "This Nexori referral type is not supported on the destination server.";

    @Nonnull
    public SecureReferralAcceptanceDecision decide(
        boolean decoded,
        boolean expired,
        boolean trustedIssuer,
        boolean signatureValid,
        boolean supportedPayloadType
    ) {
        if (!decoded) {
            return SecureReferralAcceptanceDecision.rejected(
                SecureReferralAcceptanceDecision.Outcome.NOT_DECODED,
                ""
            );
        }
        if (expired) {
            return SecureReferralAcceptanceDecision.rejected(
                SecureReferralAcceptanceDecision.Outcome.EXPIRED,
                EXPIRED_MESSAGE
            );
        }
        if (!trustedIssuer) {
            return SecureReferralAcceptanceDecision.rejected(
                SecureReferralAcceptanceDecision.Outcome.ISSUER_NOT_TRUSTED,
                ISSUER_NOT_TRUSTED_MESSAGE
            );
        }
        if (!signatureValid) {
            return SecureReferralAcceptanceDecision.rejected(
                SecureReferralAcceptanceDecision.Outcome.SIGNATURE_INVALID,
                SIGNATURE_INVALID_MESSAGE
            );
        }
        if (!supportedPayloadType) {
            return SecureReferralAcceptanceDecision.rejected(
                SecureReferralAcceptanceDecision.Outcome.PAYLOAD_TYPE_UNSUPPORTED,
                PAYLOAD_TYPE_UNSUPPORTED_MESSAGE
            );
        }
        return SecureReferralAcceptanceDecision.accepted();
    }
}
