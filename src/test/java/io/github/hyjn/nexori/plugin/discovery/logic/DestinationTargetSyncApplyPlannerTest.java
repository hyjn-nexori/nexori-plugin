package io.github.hyjn.nexori.plugin.discovery.logic;

import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DestinationTargetSyncApplyPlannerTest {

    private static DiscoveredDestinationTargetSet set(String connectionAddress, String remoteServerId) {
        return new DiscoveredDestinationTargetSet(connectionAddress, remoteServerId, 1000L, List.of());
    }

    // ── filterDiscoveries ─────────────────────────────────────────────────────

    @Test
    void nullDiscoveriesBecomeEmpty() {
        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            null, "", ""
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void filtersNullDiscoveries() {
        List<DiscoveredDestinationTargetSet> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(set("remote.srv:25565", "srv-1"));

        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            withNull, "", "other-server"
        );

        assertEquals(1, result.size());
        assertEquals("remote.srv:25565", result.get(0).connectionAddress());
    }

    @Test
    void filtersBlankConnectionDiscoveries() {
        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            List.of(
                set("   ", "srv-1"),
                set("remote.srv:25565", "srv-2")
            ),
            "", "other-server"
        );

        assertEquals(1, result.size());
        assertEquals("remote.srv:25565", result.get(0).connectionAddress());
    }

    @Test
    void filtersLocalConnectionDiscoveries() {
        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            List.of(
                set("local.srv:25565", "srv-local"),
                set("remote.srv:25565", "srv-remote")
            ),
            "local.srv:25565", "other-server"
        );

        assertEquals(1, result.size());
        assertEquals("remote.srv:25565", result.get(0).connectionAddress());
    }

    @Test
    void filtersLocalConnectionDiscoveriesCaseInsensitive() {
        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            List.of(set("LOCAL.SRV:25565", "srv-local")),
            "local.srv:25565", "other-server"
        );

        assertTrue(result.isEmpty(),
            "normalized connectionAddress matches equalsIgnoreCase comparison against localConnectionAddress");
    }

    @Test
    void filtersOwnServerIdDiscoveries() {
        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            List.of(
                set("remote.srv:25565", "local-id"),
                set("other.srv:25565", "other-id")
            ),
            "", "local-id"
        );

        assertEquals(1, result.size());
        assertEquals("other.srv:25565", result.get(0).connectionAddress());
    }

    @Test
    void normalizesAcceptedDiscoveries() {
        List<DiscoveredDestinationTargetSet> result = DestinationTargetSyncApplyPlanner.filterDiscoveries(
            List.of(set("  REMOTE.SRV:25565  ", "srv-1")),
            "", "other-server"
        );

        assertEquals(1, result.size());
        assertEquals("remote.srv:25565", result.get(0).connectionAddress());
    }

    // ── successMessage ────────────────────────────────────────────────────────

    @Test
    void successMessageUsesLocalConnectionWhenPresent() {
        String msg = DestinationTargetSyncApplyPlanner.successMessage("local.srv:25565", "issuer.srv:25565");
        assertEquals("Synchronized portal and target info on local.srv:25565.", msg);
    }

    @Test
    void successMessageFallsBackToIssuerConnectionWhenLocalBlank() {
        String msg = DestinationTargetSyncApplyPlanner.successMessage("", "issuer.srv:25565");
        assertEquals("Synchronized portal and target info on issuer.srv:25565.", msg);
    }

    // ── failureMessage ────────────────────────────────────────────────────────

    @Test
    void failureMessageIncludesIssuerAndException() {
        String msg = DestinationTargetSyncApplyPlanner.failureMessage("issuer.srv:25565", "connection refused");
        assertEquals("Could not synchronize portal info on issuer.srv:25565: connection refused", msg);
    }
}
