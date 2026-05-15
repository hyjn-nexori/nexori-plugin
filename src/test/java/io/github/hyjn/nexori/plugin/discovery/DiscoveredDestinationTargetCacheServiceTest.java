package io.github.hyjn.nexori.plugin.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveredDestinationTargetCacheServiceTest {

    @TempDir
    Path tempDir;

    private DiscoveredDestinationTargetCacheStore store() {
        return new DiscoveredDestinationTargetCacheStore(tempDir.resolve("cache.json"));
    }

    private static DiscoveredDestinationTargetSummary summary(String id) {
        return new DiscoveredDestinationTargetSummary(id, "Display " + id, "NATURAL_SPAWN", "world", "", "");
    }

    @Test
    void constructorLoadsAndNormalizesExistingDiscoveries() throws IOException {
        DiscoveredDestinationTargetCacheStore s = store();
        s.save(List.of(new DiscoveredDestinationTargetSet("SRV.EXAMPLE.COM:25565", "srv-1", 1000L, List.of())));

        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(s);

        assertEquals(1, service.list().size());
        assertEquals("srv.example.com:25565", service.list().get(0).connectionAddress());
    }

    @Test
    void listReturnsSortedByConnectionAddress() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());
        service.saveDiscovery("zebra-server:25565", "z", List.of());
        service.saveDiscovery("alpha-server:25565", "a", List.of());
        service.saveDiscovery("middle-server:25565", "m", List.of());

        List<DiscoveredDestinationTargetSet> result = service.list();

        assertEquals("alpha-server:25565", result.get(0).connectionAddress());
        assertEquals("middle-server:25565", result.get(1).connectionAddress());
        assertEquals("zebra-server:25565", result.get(2).connectionAddress());
    }

    @Test
    void findUsesNormalizedConnectionAddress() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());
        service.saveDiscovery("srv.example.com:25565", "srv-1", List.of());

        Optional<DiscoveredDestinationTargetSet> result = service.find("SRV.EXAMPLE.COM:25565");

        assertTrue(result.isPresent());
        assertEquals("srv.example.com:25565", result.get().connectionAddress());
    }

    @Test
    void findBlankConnectionAddressReturnsEmpty() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());

        assertFalse(service.find("   ").isPresent());
    }

    @Test
    void findUnknownAddressReturnsEmpty() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());

        assertFalse(service.find("unknown-host:25565").isPresent());
    }

    @Test
    void saveDiscoveryNormalizesAndPersists() throws IOException {
        DiscoveredDestinationTargetCacheStore s = store();
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(s);

        DiscoveredDestinationTargetSet saved = service.saveDiscovery("  SRV.EXAMPLE.COM:25565  ", "srv-1", List.of());

        assertEquals("srv.example.com:25565", saved.connectionAddress());
        assertTrue(service.find("srv.example.com:25565").isPresent());
    }

    @Test
    void saveDiscoveryTimestampIsPositive() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());

        DiscoveredDestinationTargetSet saved = service.saveDiscovery("srv.example.com:25565", "srv-1", List.of());

        assertTrue(saved.discoveredAtEpochMillis() > 0L);
    }

    @Test
    void saveDiscoveryReplacesExistingForSameAddress() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());
        service.saveDiscovery("srv.example.com:25565", "original", List.of());
        service.saveDiscovery("srv.example.com:25565", "updated", List.of(summary("target-1")));

        List<DiscoveredDestinationTargetSet> result = service.list();

        assertEquals(1, result.size());
        assertEquals("updated", result.get(0).remoteServerId());
        assertEquals(1, result.get(0).targets().size());
    }

    @Test
    void replaceAllNormalizesAndPersists() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());
        service.replaceAll(List.of(
            new DiscoveredDestinationTargetSet("SRV.EXAMPLE.COM:25565", "srv-1", 1000L, List.of())
        ));

        assertTrue(service.find("srv.example.com:25565").isPresent());
    }

    @Test
    void replaceAllSkipsNullDiscoveriesAccordingToCurrentBehavior() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());
        List<DiscoveredDestinationTargetSet> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new DiscoveredDestinationTargetSet("srv.example.com:25565", "srv-1", 1000L, List.of()));

        List<DiscoveredDestinationTargetSet> result = service.replaceAll(withNull);

        assertEquals(1, result.size());
        assertEquals("srv.example.com:25565", result.get(0).connectionAddress());
    }

    @Test
    void replaceAllSkipsBlankConnectionAddressAccordingToCurrentBehavior() throws IOException {
        DiscoveredDestinationTargetCacheService service = new DiscoveredDestinationTargetCacheService(store());

        List<DiscoveredDestinationTargetSet> result = service.replaceAll(List.of(
            new DiscoveredDestinationTargetSet("   ", "srv-1", 1000L, List.of()),
            new DiscoveredDestinationTargetSet("srv.example.com:25565", "srv-2", 1000L, List.of())
        ));

        assertEquals(1, result.size());
        assertEquals("srv.example.com:25565", result.get(0).connectionAddress());
    }

    @Test
    void loadAfterRestartPreservesSavedDiscoveries() throws IOException {
        DiscoveredDestinationTargetCacheStore s = store();
        DiscoveredDestinationTargetCacheService first = new DiscoveredDestinationTargetCacheService(s);
        first.saveDiscovery("srv.example.com:25565", "srv-1", List.of(summary("lobby")));

        DiscoveredDestinationTargetCacheService second = new DiscoveredDestinationTargetCacheService(s);

        Optional<DiscoveredDestinationTargetSet> found = second.find("srv.example.com:25565");
        assertTrue(found.isPresent());
        assertEquals("srv-1", found.get().remoteServerId());
        assertEquals(1, found.get().targets().size());
        assertEquals("lobby", found.get().targets().get(0).id());
    }
}
