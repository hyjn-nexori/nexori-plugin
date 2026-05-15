package io.github.hyjn.nexori.plugin.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveredDestinationTargetCacheDocumentTest {

    @Test
    void currentSchemaVersionIsOne() {
        assertEquals(1, DiscoveredDestinationTargetCacheDocument.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void emptyFactoryUsesCurrentSchemaVersionAndEmptyList() {
        DiscoveredDestinationTargetCacheDocument doc = DiscoveredDestinationTargetCacheDocument.empty();
        assertEquals(DiscoveredDestinationTargetCacheDocument.CURRENT_SCHEMA_VERSION, doc.schemaVersion());
        assertTrue(doc.discoveries().isEmpty());
    }

    @Test
    void constructorPreservesDiscoveries() {
        DiscoveredDestinationTargetSet set = new DiscoveredDestinationTargetSet(
            "srv.example.com:25565", "srv-1", 1000L, List.of());
        DiscoveredDestinationTargetCacheDocument doc = new DiscoveredDestinationTargetCacheDocument(1, List.of(set));
        assertEquals(1, doc.discoveries().size());
        assertEquals("srv.example.com:25565", doc.discoveries().get(0).connectionAddress());
    }

    @Test
    void nullDiscoveriesPreservedAccordingToCurrentBehavior() {
        DiscoveredDestinationTargetCacheDocument doc = new DiscoveredDestinationTargetCacheDocument(1, null);
        assertNull(doc.discoveries(),
            "No compact constructor normalizes discoveries; null is preserved as-is");
    }

    @Test
    void schemaVersionIsPreservedVerbatim() {
        DiscoveredDestinationTargetCacheDocument doc = new DiscoveredDestinationTargetCacheDocument(99, List.of());
        assertEquals(99, doc.schemaVersion(),
            "Record constructor does not validate schemaVersion; arbitrary value preserved");
    }
}
