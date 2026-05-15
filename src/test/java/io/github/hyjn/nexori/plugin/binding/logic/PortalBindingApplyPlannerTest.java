package io.github.hyjn.nexori.plugin.binding.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalBindingApplyPlannerTest {

    // ── isLocalTarget ─────────────────────────────────────────────────────────

    @Test
    void blankDestinationConnectionPlansLocalTarget() {
        assertTrue(PortalBindingApplyPlanner.isLocalTarget("   ", "local.srv:25565"));
    }

    @Test
    void nullDestinationConnectionPlansLocalTarget() {
        assertTrue(PortalBindingApplyPlanner.isLocalTarget(null, "local.srv:25565"));
    }

    @Test
    void destinationEqualsLocalConnectionPlansLocalTargetCaseInsensitive() {
        assertTrue(PortalBindingApplyPlanner.isLocalTarget("LOCAL.SRV:25565", "local.srv:25565"));
    }

    @Test
    void remoteDestinationPlansTravel() {
        assertFalse(PortalBindingApplyPlanner.isLocalTarget("remote.srv:25565", "local.srv:25565"));
    }

    @Test
    void remoteDestinationWithBlankLocalAddressPlansTravel() {
        assertFalse(PortalBindingApplyPlanner.isLocalTarget("remote.srv:25565", ""),
            "When localConnectionAddress is blank, equalsIgnoreCase guard prevents local match");
    }

    // ── effectiveTravelProfileId ──────────────────────────────────────────────

    @Test
    void travelDefaultsBlankTravelProfileToKeepInventory() {
        assertEquals("keep_inventory", PortalBindingApplyPlanner.effectiveTravelProfileId("   "));
    }

    @Test
    void travelDefaultsNullTravelProfileToKeepInventory() {
        assertEquals("keep_inventory", PortalBindingApplyPlanner.effectiveTravelProfileId(null));
    }

    @Test
    void travelPreservesExplicitTravelProfileId() {
        assertEquals("clear_inventory", PortalBindingApplyPlanner.effectiveTravelProfileId("clear_inventory"));
    }

    // ── effectiveContextJson ──────────────────────────────────────────────────

    @Test
    void travelDefaultsBlankContextJsonToEmptyObject() {
        assertEquals("{}", PortalBindingApplyPlanner.effectiveContextJson("   "));
    }

    @Test
    void travelDefaultsNullContextJsonToEmptyObject() {
        assertEquals("{}", PortalBindingApplyPlanner.effectiveContextJson(null));
    }

    @Test
    void travelPreservesExplicitContextJson() {
        assertEquals("{\"key\":\"val\"}", PortalBindingApplyPlanner.effectiveContextJson("{\"key\":\"val\"}"));
    }

    // ── successMessage ────────────────────────────────────────────────────────

    @Test
    void successMessageUsesLocalConnectionWhenPresent() {
        String msg = PortalBindingApplyPlanner.successMessage("local.srv:25565", "issuer.srv:25565");
        assertEquals("Applied portal binding on local.srv:25565.", msg);
    }

    @Test
    void successMessageFallsBackToIssuerConnectionWhenLocalBlank() {
        String msg = PortalBindingApplyPlanner.successMessage("", "issuer.srv:25565");
        assertEquals("Applied portal binding on issuer.srv:25565.", msg);
    }

    // ── failureMessage ────────────────────────────────────────────────────────

    @Test
    void failureMessageIncludesIssuerAndException() {
        String msg = PortalBindingApplyPlanner.failureMessage("issuer.srv:25565", "target not found");
        assertEquals("Could not apply portal binding on issuer.srv:25565: target not found", msg);
    }
}
