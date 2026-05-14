package io.github.hyjn.nexori.plugin.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsTaxonomyTest {

    // ── DiagnosticsCategory ──────────────────────────────────────────────────

    @Test
    void categoryEnumHasAllExpectedValues() {
        Set<String> names = Set.of(
            "SECURITY", "BOOTSTRAP", "TRAVEL", "DISCOVERY", "RECOVERY", "RULES", "CONFIG"
        );
        for (DiagnosticsCategory value : DiagnosticsCategory.values()) {
            assertTrue(names.contains(value.name()), "Unexpected category: " + value.name());
        }
        assertEquals(names.size(), DiagnosticsCategory.values().length);
    }

    @Test
    void categorySecurityExists() {
        assertNotNull(DiagnosticsCategory.valueOf("SECURITY"));
    }

    @Test
    void categoryTravelExists() {
        assertNotNull(DiagnosticsCategory.valueOf("TRAVEL"));
    }

    @Test
    void categoryRecoveryExists() {
        assertNotNull(DiagnosticsCategory.valueOf("RECOVERY"));
    }

    // ── DiagnosticsOutcome ───────────────────────────────────────────────────

    @Test
    void outcomeEnumHasAllExpectedValues() {
        Set<String> names = Set.of(
            "STARTED", "ACCEPTED", "SUCCEEDED", "FAILED", "DENIED", "TIMED_OUT", "EXPIRED", "SKIPPED"
        );
        for (DiagnosticsOutcome value : DiagnosticsOutcome.values()) {
            assertTrue(names.contains(value.name()), "Unexpected outcome: " + value.name());
        }
        assertEquals(names.size(), DiagnosticsOutcome.values().length);
    }

    @Test
    void outcomeFailedExists() {
        assertNotNull(DiagnosticsOutcome.valueOf("FAILED"));
    }

    @Test
    void outcomeDeniedExists() {
        assertNotNull(DiagnosticsOutcome.valueOf("DENIED"));
    }

    // ── DiagnosticsReasonClass ───────────────────────────────────────────────

    @Test
    void reasonClassEnumHasAllExpectedValues() {
        Set<String> names = Set.of(
            "NORMAL", "SECURITY", "MISCONFIG", "STALE", "IO", "VALIDATION", "UNKNOWN"
        );
        for (DiagnosticsReasonClass value : DiagnosticsReasonClass.values()) {
            assertTrue(names.contains(value.name()), "Unexpected reason class: " + value.name());
        }
        assertEquals(names.size(), DiagnosticsReasonClass.values().length);
    }

    @Test
    void reasonClassUnknownExists() {
        assertNotNull(DiagnosticsReasonClass.valueOf("UNKNOWN"));
    }

    // ── DiagnosticsAction constants ──────────────────────────────────────────

    @Test
    void actionSecurityReferralDecodeHasCorrectValue() {
        assertEquals("security.referral.decode", DiagnosticsAction.SECURITY_REFERRAL_DECODE);
    }

    @Test
    void actionTravelDispatchHasCorrectValue() {
        assertEquals("travel.dispatch", DiagnosticsAction.TRAVEL_DISPATCH);
    }

    @Test
    void actionTravelArrivalTeleportHasCorrectValue() {
        assertEquals("travel.arrival.teleport", DiagnosticsAction.TRAVEL_ARRIVAL_TELEPORT);
    }

    @Test
    void actionRecoveryQueryStartHasCorrectValue() {
        assertEquals("recovery.query.start", DiagnosticsAction.RECOVERY_QUERY_START);
    }

    @Test
    void actionRecoveryClaimLocalHasCorrectValue() {
        assertEquals("recovery.claim.local", DiagnosticsAction.RECOVERY_CLAIM_LOCAL);
    }

    @Test
    void actionRecoveryFinalizeHasCorrectValue() {
        assertEquals("recovery.finalize", DiagnosticsAction.RECOVERY_FINALIZE);
    }

    @Test
    void actionBootstrapRunStartHasCorrectValue() {
        assertEquals("bootstrap.run.start", DiagnosticsAction.BOOTSTRAP_RUN_START);
    }

    @Test
    void actionRulesApplySendHasCorrectValue() {
        assertEquals("rules.apply.send", DiagnosticsAction.RULES_APPLY_SEND);
    }

    @Test
    void actionConfigTargetSaveHasCorrectValue() {
        assertEquals("config.target.save", DiagnosticsAction.CONFIG_TARGET_SAVE);
    }

    @Test
    void actionSecurityActionsHaveSecurityPrefix() {
        assertTrue(DiagnosticsAction.SECURITY_REFERRAL_DECODE.startsWith("security."));
        assertTrue(DiagnosticsAction.SECURITY_REFERRAL_EXPIRY.startsWith("security."));
        assertTrue(DiagnosticsAction.SECURITY_REFERRAL_SIGNATURE_VERIFY.startsWith("security."));
    }

    @Test
    void actionTravelActionsHaveTravelPrefix() {
        assertTrue(DiagnosticsAction.TRAVEL_PORTAL_TRIGGER.startsWith("travel."));
        assertTrue(DiagnosticsAction.TRAVEL_DISPATCH.startsWith("travel."));
        assertTrue(DiagnosticsAction.TRAVEL_ACCEPT.startsWith("travel."));
        assertTrue(DiagnosticsAction.TRAVEL_ARRIVAL_PREPARE.startsWith("travel."));
        assertTrue(DiagnosticsAction.TRAVEL_ARRIVAL_TELEPORT.startsWith("travel."));
    }

    @Test
    void actionRecoveryActionsHaveRecoveryPrefix() {
        assertTrue(DiagnosticsAction.RECOVERY_BACKUP_ORIGIN_SAVE.startsWith("recovery."));
        assertTrue(DiagnosticsAction.RECOVERY_QUERY_START.startsWith("recovery."));
        assertTrue(DiagnosticsAction.RECOVERY_CLAIM_LOCAL.startsWith("recovery."));
        assertTrue(DiagnosticsAction.RECOVERY_FINALIZE.startsWith("recovery."));
    }

    // ── DiagnosticsReasonCode constants ──────────────────────────────────────

    @Test
    void reasonCodeReferralDecodeFailedHasCorrectValue() {
        assertEquals("REFERRAL_DECODE_FAILED", DiagnosticsReasonCode.REFERRAL_DECODE_FAILED);
    }

    @Test
    void reasonCodeSignatureInvalidHasCorrectValue() {
        assertEquals("SIGNATURE_INVALID", DiagnosticsReasonCode.SIGNATURE_INVALID);
    }

    @Test
    void reasonCodeTravelDispatchedHasCorrectValue() {
        assertEquals("TRAVEL_DISPATCHED", DiagnosticsReasonCode.TRAVEL_DISPATCHED);
    }

    @Test
    void reasonCodeRecoveryQueryStartedHasCorrectValue() {
        assertEquals("RECOVERY_QUERY_STARTED", DiagnosticsReasonCode.RECOVERY_QUERY_STARTED);
    }

    @Test
    void reasonCodeLocalBackupClaimedHasCorrectValue() {
        assertEquals("LOCAL_BACKUP_CLAIMED", DiagnosticsReasonCode.LOCAL_BACKUP_CLAIMED);
    }

    @Test
    void reasonCodeBootstrapRunCompletedHasCorrectValue() {
        assertEquals("BOOTSTRAP_RUN_COMPLETED", DiagnosticsReasonCode.BOOTSTRAP_RUN_COMPLETED);
    }
}
