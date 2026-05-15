package io.github.hyjn.nexori.plugin.policy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerRuleGroupDefinitionTest {

    private static ServerRuleGroupDefinition group(
        String groupId, String displayName, boolean recovery, int maxBackups, List<String> keys
    ) {
        return new ServerRuleGroupDefinition(groupId, displayName, recovery, maxBackups, keys);
    }

    // ── normalized ───────────────────────────────────────────────────────────

    @Test
    void normalizedDefaultsBlankGroupIdAccordingToCurrentBehavior() {
        ServerRuleGroupDefinition def = group("   ", "Name", false, 3, List.of()).normalized();
        assertTrue(def.groupId().startsWith("group."),
            "Blank groupId should be replaced with UUID-based id starting with 'group.'");
        assertEquals(38, def.groupId().length(),
            "Generated groupId should be 'group.' (6) + 32 hex chars (UUID without dashes)");
    }

    @Test
    void normalizedTrimsAndLowercasesGroupId() {
        ServerRuleGroupDefinition def = group("  MY-GROUP  ", "Name", false, 3, List.of()).normalized();
        assertEquals("my-group", def.groupId());
    }

    @Test
    void normalizedDefaultsDisplayNameWhenBlank() {
        ServerRuleGroupDefinition def = group("group-1", "   ", false, 3, List.of()).normalized();
        assertEquals("Rule Group", def.displayName());
    }

    @Test
    void normalizedTrimsDisplayNameButDoesNotLowercaseAccordingToCurrentBehavior() {
        ServerRuleGroupDefinition def = group("group-1", "  My Rule Group  ", false, 3, List.of()).normalized();
        assertEquals("My Rule Group", def.displayName(),
            "displayName is trimmed but NOT lowercased");
    }

    @Test
    void normalizedClampsMaxBackupsPerPlayerToAtLeastOne() {
        assertEquals(1, group("g", "N", false, 0, List.of()).normalized().maxBackupsPerPlayer());
        assertEquals(1, group("g", "N", false, -5, List.of()).normalized().maxBackupsPerPlayer());
        assertEquals(5, group("g", "N", false, 5, List.of()).normalized().maxBackupsPerPlayer());
    }

    @Test
    void normalizedDefaultsNullAssignedServerKeysToEmpty() {
        ServerRuleGroupDefinition def = group("group-1", "Name", false, 3, null).normalized();
        assertNotNull(def.assignedServerKeys());
        assertTrue(def.assignedServerKeys().isEmpty());
    }

    @Test
    void normalizedTrimsLowercasesAndDeduplicatesAssignedServerKeys() {
        List<String> keys = List.of("HOST.A.COM:5520", "host.a.com:5520", "host.b.com:5520");
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 3, keys).normalized();
        assertEquals(2, def.assignedServerKeys().size());
        assertEquals("host.a.com:5520", def.assignedServerKeys().get(0));
        assertEquals("host.b.com:5520", def.assignedServerKeys().get(1));
    }

    @Test
    void normalizedPreservesInsertionOrderForDeduplicatedKeys() {
        List<String> keys = List.of("host.b.com:5520", "host.a.com:5520");
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 3, keys).normalized();
        assertEquals("host.b.com:5520", def.assignedServerKeys().get(0));
        assertEquals("host.a.com:5520", def.assignedServerKeys().get(1));
    }

    // ── normalizeServerKey ───────────────────────────────────────────────────

    @Test
    void normalizeServerKeyReturnsEmptyForNullAndBlank() {
        assertEquals("", ServerRuleGroupDefinition.normalizeServerKey(null));
        assertEquals("", ServerRuleGroupDefinition.normalizeServerKey(""));
        assertEquals("", ServerRuleGroupDefinition.normalizeServerKey("   "));
    }

    @Test
    void normalizeServerKeyReturnsLocalServerKeyForLocalToken() {
        assertEquals(ServerRuleGroupDefinition.LOCAL_SERVER_KEY,
            ServerRuleGroupDefinition.normalizeServerKey("__local__"));
    }

    @Test
    void normalizeServerKeyLocalTokenIsCaseInsensitive() {
        assertEquals(ServerRuleGroupDefinition.LOCAL_SERVER_KEY,
            ServerRuleGroupDefinition.normalizeServerKey("__LOCAL__"));
        assertEquals(ServerRuleGroupDefinition.LOCAL_SERVER_KEY,
            ServerRuleGroupDefinition.normalizeServerKey("__Local__"));
    }

    @Test
    void normalizeServerKeyTrimsAndLowercasesRegularKey() {
        assertEquals("host.example.com:5520",
            ServerRuleGroupDefinition.normalizeServerKey("  HOST.EXAMPLE.COM:5520  "));
    }

    @Test
    void normalizeServerKeyFallsBackToLowercaseForInvalidAddressAccordingToCurrentBehavior() {
        // "host:abc" → parse fails (non-numeric port) → returns trimmed lowercase
        assertEquals("host:abc",
            ServerRuleGroupDefinition.normalizeServerKey("HOST:abc"));
    }

    // ── containsServer ───────────────────────────────────────────────────────

    @Test
    void containsServerUsesNormalizedKey() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1,
            List.of("host.example.com:5520")).normalized();
        assertTrue(def.containsServer("HOST.EXAMPLE.COM:5520"));
        assertFalse(def.containsServer("other.host:5520"));
    }

    @Test
    void containsServerReturnsFalseForBlankKey() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1,
            List.of("host.example.com:5520")).normalized();
        assertFalse(def.containsServer("   "));
    }

    // ── assignServer ─────────────────────────────────────────────────────────

    @Test
    void assignServerAddsNormalizedKey() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1, List.of()).normalized();
        ServerRuleGroupDefinition updated = def.assignServer("HOST.EXAMPLE.COM:5520");
        assertTrue(updated.containsServer("host.example.com:5520"));
        assertEquals(1, updated.assignedServerKeys().size());
    }

    @Test
    void assignServerDoesNotDuplicateExistingKey() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1,
            List.of("host.example.com:5520")).normalized();
        ServerRuleGroupDefinition updated = def.assignServer("HOST.EXAMPLE.COM:5520");
        assertEquals(1, updated.assignedServerKeys().size());
    }

    @Test
    void assignServerBlankKeyReturnsNormalizedWithoutAddingAccordingToCurrentBehavior() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1, List.of()).normalized();
        ServerRuleGroupDefinition updated = def.assignServer("   ");
        assertTrue(updated.assignedServerKeys().isEmpty(),
            "Blank key normalizes to '' which is blank, so returns normalized() without adding");
    }

    // ── unassignServer ───────────────────────────────────────────────────────

    @Test
    void unassignServerRemovesNormalizedKey() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1,
            List.of("host.a.com:5520", "host.b.com:5520")).normalized();
        ServerRuleGroupDefinition updated = def.unassignServer("HOST.A.COM:5520");
        assertFalse(updated.containsServer("host.a.com:5520"));
        assertTrue(updated.containsServer("host.b.com:5520"));
        assertEquals(1, updated.assignedServerKeys().size());
    }

    @Test
    void unassignServerMissingKeyIsNoOpAccordingToCurrentBehavior() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1,
            List.of("host.a.com:5520")).normalized();
        ServerRuleGroupDefinition updated = def.unassignServer("host.x.com:5520");
        assertEquals(1, updated.assignedServerKeys().size(),
            "Unassigning a key that is not present should leave the list unchanged");
    }

    // ── with* builders ────────────────────────────────────────────────────────

    @Test
    void withDisplayNameNormalizes() {
        ServerRuleGroupDefinition def = group("g-1", "Old Name", false, 1, List.of()).normalized();
        ServerRuleGroupDefinition updated = def.withDisplayName("  New Name  ");
        assertEquals("New Name", updated.displayName());
        assertEquals("g-1", updated.groupId());
    }

    @Test
    void withRecoveryEnabledPreservesOtherFields() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 5,
            List.of("host.x.com:5520")).normalized();
        ServerRuleGroupDefinition updated = def.withRecoveryEnabled(true);
        assertTrue(updated.recoveryEnabled());
        assertEquals("g-1", updated.groupId());
        assertEquals("Name", updated.displayName());
        assertEquals(5, updated.maxBackupsPerPlayer());
        assertTrue(updated.containsServer("host.x.com:5520"));
    }

    @Test
    void withMaxBackupsPerPlayerNormalizesToAtLeastOne() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 5, List.of()).normalized();
        assertEquals(1, def.withMaxBackupsPerPlayer(0).maxBackupsPerPlayer());
        assertEquals(10, def.withMaxBackupsPerPlayer(10).maxBackupsPerPlayer());
    }

    @Test
    void withAssignedServerKeysNormalizesAndDedupes() {
        ServerRuleGroupDefinition def = group("g-1", "Name", false, 1, List.of()).normalized();
        List<String> newKeys = List.of("HOST.A.COM:5520", "host.a.com:5520", "HOST.B.COM:5520");
        ServerRuleGroupDefinition updated = def.withAssignedServerKeys(newKeys);
        assertEquals(2, updated.assignedServerKeys().size());
        assertTrue(updated.containsServer("host.a.com:5520"));
        assertTrue(updated.containsServer("host.b.com:5520"));
    }
}
