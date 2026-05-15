package io.github.hyjn.nexori.plugin.discovery;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveredDestinationTargetSetTest {

    private static DiscoveredDestinationTargetSummary summary(String id) {
        return new DiscoveredDestinationTargetSummary(id, "Display " + id, "NATURAL_SPAWN", "world", "", "");
    }

    // ── normalized: connectionAddress ─────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesConnectionAddress() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "  SRV.EXAMPLE.COM:25565  ", "srv-1", 1000L, List.of()).normalized();
        assertEquals("srv.example.com:25565", set.connectionAddress());
    }

    @Test
    void normalizedBlankConnectionAddressThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class,
            () -> new DiscoveredDestinationTargetSet("   ", "srv-1", 1000L, List.of()).normalized(),
            "ConfiguredPeer.parse() throws IAE for blank address");
    }

    @Test
    void normalizedNullConnectionAddressThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class,
            () -> new DiscoveredDestinationTargetSet(null, "srv-1", 1000L, List.of()).normalized(),
            "ConfiguredPeer.parse(null) normalizes to blank then throws IAE");
    }

    // ── normalized: remoteServerId ────────────────────────────────────────────

    @Test
    void normalizedTrimsRemoteServerId() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "  srv-alpha  ", 1000L, List.of()).normalized();
        assertEquals("srv-alpha", set.remoteServerId());
    }

    @Test
    void normalizedNullRemoteServerIdBecomesEmpty() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", null, 1000L, List.of()).normalized();
        assertEquals("", set.remoteServerId());
    }

    // ── normalized: targets ───────────────────────────────────────────────────

    @Test
    void normalizedNullTargetsBecomesEmptyList() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 1000L, null).normalized();
        assertTrue(set.targets().isEmpty());
    }

    @Test
    void normalizedSkipsNullTargetsAccordingToCurrentBehavior() {
        List<DiscoveredDestinationTargetSummary> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(summary("target-a"));
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 1000L, withNull).normalized();
        assertEquals(1, set.targets().size());
        assertEquals("target-a", set.targets().get(0).id());
    }

    @Test
    void normalizedSkipsTargetsWithBlankIdAccordingToCurrentBehavior() {
        List<DiscoveredDestinationTargetSummary> withBlank = List.of(
            new DiscoveredDestinationTargetSummary("   ", "Blank", "NATURAL_SPAWN", "world", "", ""),
            summary("valid-target")
        );
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 1000L, withBlank).normalized();
        assertEquals(1, set.targets().size());
        assertEquals("valid-target", set.targets().get(0).id());
    }

    @Test
    void normalizedSortsTargetsById() {
        List<DiscoveredDestinationTargetSummary> unsorted = List.of(
            summary("target-z"), summary("target-a"), summary("target-m"));
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 1000L, unsorted).normalized();
        assertEquals("target-a", set.targets().get(0).id());
        assertEquals("target-m", set.targets().get(1).id());
        assertEquals("target-z", set.targets().get(2).id());
    }

    // ── normalized: discoveredAtEpochMillis ───────────────────────────────────

    @Test
    void normalizedPreservesDiscoveredAtEpochMillis() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 9_000_000L, List.of()).normalized();
        assertEquals(9_000_000L, set.discoveredAtEpochMillis());
    }

    // ── targets list immutability ─────────────────────────────────────────────

    @Test
    void normalizedTargetsListIsUnmodifiableAccordingToCurrentBehavior() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 1000L, List.of(summary("t"))).normalized();
        assertThrows(UnsupportedOperationException.class, () -> set.targets().add(summary("new")),
            "stream().toList() returns an unmodifiable list");
    }
}
