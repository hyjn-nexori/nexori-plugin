package io.github.hyjn.nexori.plugin.peers;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapMigrationPlan;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfiguredPeerMigrationServiceTest {

    private static ConfiguredPeer peer(String host, int port) {
        return new ConfiguredPeer(host + ":" + port, host, port);
    }

    @Test
    void constructorLoadsAndNormalizesPendingEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);
        store.save(List.of(
            new ConfiguredPeerMigrationEntry("  old.host:5520  ", "  new.host:5520  ")
        ));

        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(store);
        BootstrapMigrationPlan plan = service.buildPlan();

        assertFalse(plan.isEmpty(), "Should load the saved migration entry");
        assertEquals("old.host:5520", plan.replacements().get(0).oldValue());
        assertEquals("new.host:5520", plan.replacements().get(0).newValue());
    }

    @Test
    void constructorDropsEntriesWithoutAnyChange(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);
        store.save(List.of(
            new ConfiguredPeerMigrationEntry("same.host:5520", "same.host:5520")
        ));

        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(store);

        assertTrue(service.buildPlan().isEmpty(), "Entry with no change should be dropped");
    }

    @Test
    void recordUpdateAddsNewMigration(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));

        service.recordUpdate(peer("old.host", 5520), peer("new.host", 5520));

        BootstrapMigrationPlan plan = service.buildPlan();
        assertEquals(1, plan.replacements().size());
        assertEquals("old.host:5520", plan.replacements().get(0).oldValue());
        assertEquals("new.host:5520", plan.replacements().get(0).newValue());
    }

    @Test
    void recordUpdateNoOpsWhenConnectionUnchanged(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));
        ConfiguredPeer sameHost = peer("host.example.com", 5520);

        service.recordUpdate(sameHost, sameHost);

        assertTrue(service.buildPlan().isEmpty());
    }

    @Test
    void recordUpdateMergesWhenExistingNewEndpointMatchesExistingPeer(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));
        ConfiguredPeer peerA = peer("peer-a.host", 5520);
        ConfiguredPeer peerB = peer("peer-b.host", 5520);
        ConfiguredPeer peerC = peer("peer-c.host", 5520);

        service.recordUpdate(peerA, peerB);
        service.recordUpdate(peerB, peerC);

        BootstrapMigrationPlan plan = service.buildPlan();
        assertEquals(1, plan.replacements().size(),
            "Chained migration should merge to single A->C entry");
        assertEquals("peer-a.host:5520", plan.replacements().get(0).oldValue());
        assertEquals("peer-c.host:5520", plan.replacements().get(0).newValue());
    }

    @Test
    void recordUpdateRemovesEntryWhenUpdateCancelsMigration(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));
        ConfiguredPeer peerA = peer("peer-a.host", 5520);
        ConfiguredPeer peerB = peer("peer-b.host", 5520);

        service.recordUpdate(peerA, peerB);
        service.recordUpdate(peerB, peerA);

        assertTrue(service.buildPlan().isEmpty(),
            "Migration A->B then B->A should cancel to empty plan");
    }

    @Test
    void buildPlanReturnsReplacementsSortedByOldValueLengthDescending(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));

        service.recordUpdate(peer("a.b.c", 5520), peer("x.y.z", 5520));
        service.recordUpdate(peer("much.longer.hostname.example.com", 5520), peer("new.host", 5520));

        List<BootstrapTextReplacement> replacements = service.buildPlan().replacements();
        assertEquals(2, replacements.size());
        assertTrue(
            replacements.get(0).oldValue().length() >= replacements.get(1).oldValue().length(),
            "Replacements should be sorted by oldValue length descending"
        );
    }

    @Test
    void clearRemovesPendingEntriesAndPersists(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));
        service.recordUpdate(peer("old.host", 5520), peer("new.host", 5520));

        service.clear();

        assertTrue(service.buildPlan().isEmpty());
        ConfiguredPeerMigrationService reloaded = new ConfiguredPeerMigrationService(
            new ConfiguredPeerMigrationStore(file));
        assertTrue(reloaded.buildPlan().isEmpty(), "Cleared entries should not reload from store");
    }

    @Test
    void migrationPlanRoundTripsThroughStore(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);
        ConfiguredPeerMigrationService service = new ConfiguredPeerMigrationService(store);
        service.recordUpdate(peer("old.host", 5520), peer("new.host", 5520));

        ConfiguredPeerMigrationService reloaded = new ConfiguredPeerMigrationService(store);
        BootstrapMigrationPlan plan = reloaded.buildPlan();

        assertEquals(1, plan.replacements().size());
        assertEquals("old.host:5520", plan.replacements().get(0).oldValue());
    }
}
