package io.github.hyjn.nexori.plugin.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerPolicyCacheServiceTest {

    private static ServerPolicyCacheService service(Path dir) throws IOException {
        return new ServerPolicyCacheService(new ServerPolicyCacheStore(dir.resolve("policy-cache.json")));
    }

    private static ServerPolicyCacheStore store(Path dir) {
        return new ServerPolicyCacheStore(dir.resolve("policy-cache.json"));
    }

    @Test
    void constructorLoadsAndNormalizesExistingPolicies(@TempDir Path dir) throws IOException {
        ServerPolicyCacheStore st = store(dir);
        st.save(List.of(new ServerPolicySummary("HOST.EXAMPLE.COM:5520", "srv-1", 1000L, true, 3)));

        ServerPolicyCacheService svc = new ServerPolicyCacheService(st);

        Optional<ServerPolicySummary> found = svc.find("host.example.com:5520");
        assertTrue(found.isPresent(), "Service should load and normalize policies on construction");
        assertEquals("host.example.com:5520", found.get().connectionAddress());
    }

    @Test
    void listReturnsPoliciesSortedByConnectionAddress(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);
        svc.saveConfirmedPolicy("z.peer.com:5520", "srv-z", true, 3);
        svc.saveConfirmedPolicy("a.peer.com:5520", "srv-a", false, 1);

        List<ServerPolicySummary> list = svc.list();

        assertEquals(2, list.size());
        assertEquals("a.peer.com:5520", list.get(0).connectionAddress(),
            "list() should be sorted by connectionAddress ascending");
        assertEquals("z.peer.com:5520", list.get(1).connectionAddress());
    }

    @Test
    void findUsesNormalizedConnectionAddress(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);
        svc.saveConfirmedPolicy("peer.example.com:5520", "srv-1", true, 3);

        Optional<ServerPolicySummary> found = svc.find("PEER.EXAMPLE.COM:5520");

        assertTrue(found.isPresent());
        assertEquals("peer.example.com:5520", found.get().connectionAddress());
    }

    @Test
    void findReturnsEmptyForBlankAddressAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);

        Optional<ServerPolicySummary> found = svc.find("   ");

        assertTrue(found.isEmpty(),
            "find() catches IllegalArgumentException from ConfiguredPeer.parse and returns empty");
    }

    @Test
    void findReturnsEmptyWhenNoPolicyForAddress(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);

        Optional<ServerPolicySummary> found = svc.find("missing.peer.com:5520");

        assertFalse(found.isPresent());
    }

    @Test
    void saveConfirmedPolicyNormalizesAndPersists(@TempDir Path dir) throws IOException {
        ServerPolicyCacheStore st = store(dir);
        ServerPolicyCacheService svc = new ServerPolicyCacheService(st);

        svc.saveConfirmedPolicy("PEER.EXAMPLE.COM:5520", "srv-1", true, 3);

        Optional<ServerPolicySummary> found = svc.find("peer.example.com:5520");
        assertTrue(found.isPresent());
        assertEquals("peer.example.com:5520", found.get().connectionAddress());
        assertEquals("srv-1", found.get().remoteServerId());
        assertTrue(found.get().recoveryEnabled());
        assertEquals(3, found.get().maxBackupsPerPlayer());
    }

    @Test
    void saveConfirmedPolicyReplacesExistingForSameAddress(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);
        svc.saveConfirmedPolicy("peer.example.com:5520", "srv-old", true, 5);

        svc.saveConfirmedPolicy("peer.example.com:5520", "srv-new", false, 2);

        List<ServerPolicySummary> list = svc.list();
        assertEquals(1, list.size(), "Same address should replace the existing entry");
        assertEquals("srv-new", list.get(0).remoteServerId());
        assertFalse(list.get(0).recoveryEnabled());
        assertEquals(2, list.get(0).maxBackupsPerPlayer());
    }

    @Test
    void saveConfirmedPolicyClampsMaxBackupsToAtLeastOne(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);

        ServerPolicySummary saved = svc.saveConfirmedPolicy("peer.example.com:5520", "srv-1", false, 0);

        assertEquals(1, saved.maxBackupsPerPlayer(),
            "normalized() clamps maxBackupsPerPlayer to at least 1");
    }

    @Test
    void loadAfterRestartPreservesSavedPolicy(@TempDir Path dir) throws IOException {
        ServerPolicyCacheStore st = store(dir);
        new ServerPolicyCacheService(st).saveConfirmedPolicy("peer.example.com:5520", "srv-1", true, 5);

        ServerPolicyCacheService reloaded = new ServerPolicyCacheService(st);

        Optional<ServerPolicySummary> found = reloaded.find("peer.example.com:5520");
        assertTrue(found.isPresent());
        assertEquals("srv-1", found.get().remoteServerId());
        assertTrue(found.get().recoveryEnabled());
        assertEquals(5, found.get().maxBackupsPerPlayer());
    }

    @Test
    void saveConfirmedPolicySetsNonZeroConfirmedAtTimestamp(@TempDir Path dir) throws IOException {
        ServerPolicyCacheService svc = service(dir);

        ServerPolicySummary saved = svc.saveConfirmedPolicy("peer.example.com:5520", "srv-1", true, 3);

        assertTrue(saved.confirmedAtEpochMillis() > 0,
            "saveConfirmedPolicy uses Instant.now() for the timestamp");
    }
}
