package io.github.hyjn.nexori.plugin.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerPolicyCacheStoreTest {

    private static ServerPolicySummary policy(String address) {
        return new ServerPolicySummary(address, "srv-1", 1000L, true, 3);
    }

    @Test
    void constructorCreatesMissingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        new ServerPolicyCacheStore(file).loadOrCreate();
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateCreatesEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        List<ServerPolicySummary> policies = new ServerPolicyCacheStore(file).loadOrCreate();
        assertTrue(policies.isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsNormalizedPolicies(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);

        store.save(List.of(policy("host.example.com:5520")));
        List<ServerPolicySummary> loaded = store.loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals("host.example.com:5520", loaded.get(0).connectionAddress());
        assertEquals("srv-1", loaded.get(0).remoteServerId());
        assertTrue(loaded.get(0).recoveryEnabled());
    }

    @Test
    void blankFileIsRewrittenAsEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);

        List<ServerPolicySummary> policies = store.loadOrCreate();

        assertTrue(policies.isEmpty());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("policies"), "Blank file should be rewritten with valid JSON");
    }

    @Test
    void nullPoliciesDocumentIsRewrittenAsEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"policies\":null}", StandardCharsets.UTF_8);
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);

        List<ServerPolicySummary> policies = store.loadOrCreate();

        assertTrue(policies.isEmpty());
    }

    @Test
    void skipsNullRecordsInJsonArrayAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        Files.writeString(file,
            "{\"schemaVersion\":1,\"policies\":[null,{\"connectionAddress\":\"peer.a.com:5520\",\"remoteServerId\":\"s1\",\"confirmedAtEpochMillis\":1000,\"recoveryEnabled\":true,\"maxBackupsPerPlayer\":3}]}",
            StandardCharsets.UTF_8);
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);

        List<ServerPolicySummary> policies = store.loadOrCreate();

        assertEquals(1, policies.size(), "Null records in the array should be skipped");
        assertEquals("peer.a.com:5520", policies.get(0).connectionAddress());
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "Corrupt JSON should throw RuntimeException (JsonSyntaxException)");
    }

    @Test
    void saveCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/dir/policy-cache.json");
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);

        store.save(List.of());

        assertTrue(Files.exists(file));
    }

    @Test
    void savePreservesInsertionOrderAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy-cache.json");
        ServerPolicyCacheStore store = new ServerPolicyCacheStore(file);
        List<ServerPolicySummary> policies = List.of(
            policy("z.peer.com:5520"),
            policy("a.peer.com:5520")
        );

        store.save(policies);
        List<ServerPolicySummary> loaded = store.loadOrCreate();

        assertEquals(2, loaded.size());
        assertEquals("z.peer.com:5520", loaded.get(0).connectionAddress(),
            "Insertion order passed to save() should be preserved on load");
        assertEquals("a.peer.com:5520", loaded.get(1).connectionAddress());
    }
}
