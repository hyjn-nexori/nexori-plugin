package io.github.hyjn.nexori.plugin.peers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfiguredPeerMigrationEntryTest {

    @Test
    void normalizedTrimsBothAddresses() {
        ConfiguredPeerMigrationEntry entry = new ConfiguredPeerMigrationEntry("  old.host:5520  ", "  new.host:5520  ");
        ConfiguredPeerMigrationEntry normalized = entry.normalized();

        assertEquals("old.host:5520", normalized.oldConnectionAddress());
        assertEquals("new.host:5520", normalized.newConnectionAddress());
    }

    @Test
    void normalizedNullOldAddressBecomesEmpty() {
        ConfiguredPeerMigrationEntry entry = new ConfiguredPeerMigrationEntry(null, "new.host:5520");
        assertEquals("", entry.normalized().oldConnectionAddress());
    }

    @Test
    void normalizedNullNewAddressBecomesEmpty() {
        ConfiguredPeerMigrationEntry entry = new ConfiguredPeerMigrationEntry("old.host:5520", null);
        assertEquals("", entry.normalized().newConnectionAddress());
    }

    @Test
    void hasAnyChangeTrueWhenAddressesDiffer() {
        ConfiguredPeerMigrationEntry entry = new ConfiguredPeerMigrationEntry("old.host:5520", "new.host:5520");
        assertTrue(entry.hasAnyChange());
    }

    @Test
    void hasAnyChangeFalseWhenAddressesAreEqual() {
        ConfiguredPeerMigrationEntry entry = new ConfiguredPeerMigrationEntry("host:5520", "host:5520");
        assertFalse(entry.hasAnyChange());
    }
}
