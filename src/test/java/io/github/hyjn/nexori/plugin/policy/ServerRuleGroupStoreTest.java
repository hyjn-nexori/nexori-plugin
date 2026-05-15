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

final class ServerRuleGroupStoreTest {

    private static ServerRuleGroupDefinition group(String groupId, String displayName) {
        return new ServerRuleGroupDefinition(groupId, displayName, false, 3, List.of()).normalized();
    }

    @Test
    void constructorCreatesMissingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        new ServerRuleGroupStore(file).loadOrCreate();
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateCreatesEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        List<ServerRuleGroupDefinition> groups = new ServerRuleGroupStore(file).loadOrCreate();
        assertTrue(groups.isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsNormalizedGroups(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);
        ServerRuleGroupDefinition g = group("my-group", "My Group");

        store.save(List.of(g));
        List<ServerRuleGroupDefinition> loaded = store.loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals("my-group", loaded.get(0).groupId());
        assertEquals("My Group", loaded.get(0).displayName());
    }

    @Test
    void blankFileIsRewrittenAsEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);

        List<ServerRuleGroupDefinition> groups = store.loadOrCreate();

        assertTrue(groups.isEmpty());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("ruleGroups"), "Blank file should be rewritten with valid JSON");
    }

    @Test
    void nullGroupsDocumentIsRewrittenAsEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"ruleGroups\":null}", StandardCharsets.UTF_8);
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);

        List<ServerRuleGroupDefinition> groups = store.loadOrCreate();

        assertTrue(groups.isEmpty());
    }

    @Test
    void skipsNullRecordsInJsonArrayAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        Files.writeString(file,
            "{\"schemaVersion\":1,\"ruleGroups\":[null,{\"groupId\":\"g-1\",\"displayName\":\"Group One\",\"recoveryEnabled\":false,\"maxBackupsPerPlayer\":3,\"assignedServerKeys\":[]}]}",
            StandardCharsets.UTF_8);
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);

        List<ServerRuleGroupDefinition> groups = store.loadOrCreate();

        assertEquals(1, groups.size(), "Null records in the array should be skipped");
        assertEquals("g-1", groups.get(0).groupId());
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "Corrupt JSON should throw RuntimeException (JsonSyntaxException)");
    }

    @Test
    void schemaVersionIsNotUsedForMigrationAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        Files.writeString(file,
            "{\"schemaVersion\":0,\"ruleGroups\":[{\"groupId\":\"g-old\",\"displayName\":\"Old Group\",\"recoveryEnabled\":false,\"maxBackupsPerPlayer\":2,\"assignedServerKeys\":[]}]}",
            StandardCharsets.UTF_8);
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);

        List<ServerRuleGroupDefinition> groups = store.loadOrCreate();

        assertEquals(1, groups.size(), "Old schemaVersion is loaded without migration or rejection");
        assertEquals("g-old", groups.get(0).groupId());
    }

    @Test
    void saveCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/dir/rule-groups.json");
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);

        store.save(List.of());

        assertTrue(Files.exists(file));
    }

    @Test
    void savePreservesOrderingAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("rule-groups.json");
        ServerRuleGroupStore store = new ServerRuleGroupStore(file);
        List<ServerRuleGroupDefinition> groups = List.of(
            group("z-group", "Z Group"),
            group("a-group", "A Group")
        );

        store.save(groups);
        List<ServerRuleGroupDefinition> loaded = store.loadOrCreate();

        assertEquals(2, loaded.size());
        assertEquals("z-group", loaded.get(0).groupId(), "Order passed to save() should be preserved on load");
        assertEquals("a-group", loaded.get(1).groupId());
    }
}
