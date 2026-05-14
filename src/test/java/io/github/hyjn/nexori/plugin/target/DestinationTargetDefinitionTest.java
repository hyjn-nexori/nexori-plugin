package io.github.hyjn.nexori.plugin.target;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DestinationTargetDefinitionTest {

    // ── normalizeId ──────────────────────────────────────────────────────────

    @Test
    void normalizeIdTrimsWhitespace() {
        assertEquals("my-target", DestinationTargetDefinition.normalizeId("  My-Target  "));
    }

    @Test
    void normalizeIdLowercases() {
        assertEquals("lobby", DestinationTargetDefinition.normalizeId("LOBBY"));
    }

    @Test
    void normalizeIdTrimsAndLowercases() {
        assertEquals("spawn.world", DestinationTargetDefinition.normalizeId("  Spawn.World  "));
    }

    @Test
    void normalizeIdBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> DestinationTargetDefinition.normalizeId("   "));
    }

    @Test
    void normalizeIdEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> DestinationTargetDefinition.normalizeId(""));
    }

    // ── normalized() ─────────────────────────────────────────────────────────

    @Test
    void normalizedIdIsLowercasedAndTrimmed() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "  My Target  ", "Display", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "{}"
        );
        assertEquals("my target", target.normalized().id());
    }

    @Test
    void normalizedKindNullDefaultsToNaturalSpawn() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "target-id", "Display", null, "world", "", "", "{}"
        );
        assertEquals(DestinationTargetKind.NATURAL_SPAWN, target.normalized().kind());
    }

    @Test
    void normalizedDisplayNameFallsBackToId() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", null, DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "{}"
        );
        assertEquals("my-target", target.normalized().displayName());
    }

    @Test
    void normalizedDisplayNameBlankFallsBackToId() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "   ", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "{}"
        );
        assertEquals("my-target", target.normalized().displayName());
    }

    @Test
    void normalizedDisplayNameIsPreservedWhenNonBlank() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "  My Display  ", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "{}"
        );
        assertEquals("My Display", target.normalized().displayName());
    }

    @Test
    void normalizedWorldNameFallsBackToDefault() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, null, "", "", "{}"
        );
        assertEquals("default", target.normalized().worldName());
    }

    @Test
    void normalizedWorldNameBlankFallsBackToDefault() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, "   ", "", "", "{}"
        );
        assertEquals("default", target.normalized().worldName());
    }

    @Test
    void normalizedWorldNameIsPreservedWhenNonBlank() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, "  Alpha World  ", "", "", "{}"
        );
        assertEquals("Alpha World", target.normalized().worldName());
    }

    @Test
    void normalizedArrivalPointIdNullBecomesEmpty() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, "world", null, "", "{}"
        );
        assertEquals("", target.normalized().arrivalPointId());
    }

    @Test
    void normalizedArrivalMessageNullBecomesEmpty() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, "world", "", null, "{}"
        );
        assertEquals("", target.normalized().arrivalMessage());
    }

    @Test
    void normalizedMetadataJsonNullBecomesDefaultBraces() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", null
        );
        assertEquals("{}", target.normalized().metadataJson());
    }

    @Test
    void normalizedMetadataJsonBlankBecomesDefaultBraces() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "my-target", "Display", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "   "
        );
        assertEquals("{}", target.normalized().metadataJson());
    }

    @Test
    void normalizedPreservesAllFieldsWhenValid() {
        DestinationTargetDefinition original = new DestinationTargetDefinition(
            "lobby", "Lobby", DestinationTargetKind.COORDINATE, "overworld", "spawn-point", "Welcome!", "{\"key\":\"value\"}"
        );
        DestinationTargetDefinition result = original.normalized();

        assertEquals("lobby", result.id());
        assertEquals("Lobby", result.displayName());
        assertEquals(DestinationTargetKind.COORDINATE, result.kind());
        assertEquals("overworld", result.worldName());
        assertEquals("spawn-point", result.arrivalPointId());
        assertEquals("Welcome!", result.arrivalMessage());
        assertEquals("{\"key\":\"value\"}", result.metadataJson());
    }
}
