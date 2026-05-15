package io.github.hyjn.nexori.plugin.diagnostics.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsProtocolTest {

    // ── constant stability ────────────────────────────────────────────────────

    @Test
    void collectPayloadTypesHaveStableValues() {
        assertEquals("diagnostics.collect.manifest.request", DiagnosticsProtocol.COLLECT_MANIFEST_REQUEST);
        assertEquals("diagnostics.collect.manifest.response", DiagnosticsProtocol.COLLECT_MANIFEST_RESPONSE);
        assertEquals("diagnostics.collect.chunk.request", DiagnosticsProtocol.COLLECT_CHUNK_REQUEST);
        assertEquals("diagnostics.collect.chunk.response", DiagnosticsProtocol.COLLECT_CHUNK_RESPONSE);
        assertEquals("diagnostics.collect.error", DiagnosticsProtocol.COLLECT_ERROR);
    }

    @Test
    void snapshotPayloadTypesHaveStableValues() {
        assertEquals("diagnostics.snapshot.request", DiagnosticsProtocol.SNAPSHOT_REQUEST);
        assertEquals("diagnostics.snapshot.response", DiagnosticsProtocol.SNAPSHOT_RESPONSE);
    }

    // ── isCollectPayloadType ──────────────────────────────────────────────────

    @Test
    void isCollectPayloadTypeTrueForAllCollectConstants() {
        assertTrue(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.COLLECT_MANIFEST_REQUEST));
        assertTrue(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.COLLECT_MANIFEST_RESPONSE));
        assertTrue(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.COLLECT_CHUNK_REQUEST));
        assertTrue(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.COLLECT_CHUNK_RESPONSE));
        assertTrue(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.COLLECT_ERROR));
    }

    @Test
    void isCollectPayloadTypeFalseForSnapshotAndOtherValues() {
        assertFalse(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.SNAPSHOT_REQUEST));
        assertFalse(DiagnosticsProtocol.isCollectPayloadType(DiagnosticsProtocol.SNAPSHOT_RESPONSE));
        assertFalse(DiagnosticsProtocol.isCollectPayloadType("other.payload.type"));
    }

    // ── isSnapshotPayloadType ─────────────────────────────────────────────────

    @Test
    void isSnapshotPayloadTypeTrueForSnapshotConstants() {
        assertTrue(DiagnosticsProtocol.isSnapshotPayloadType(DiagnosticsProtocol.SNAPSHOT_REQUEST));
        assertTrue(DiagnosticsProtocol.isSnapshotPayloadType(DiagnosticsProtocol.SNAPSHOT_RESPONSE));
    }

    @Test
    void isSnapshotPayloadTypeFalseForCollectAndOtherValues() {
        assertFalse(DiagnosticsProtocol.isSnapshotPayloadType(DiagnosticsProtocol.COLLECT_MANIFEST_REQUEST));
        assertFalse(DiagnosticsProtocol.isSnapshotPayloadType("other.payload.type"));
    }

    // ── isOperationalOnlyPayloadType ──────────────────────────────────────────

    @Test
    void isOperationalOnlyPayloadTypeTrueForCollectAndSnapshot() {
        assertTrue(DiagnosticsProtocol.isOperationalOnlyPayloadType(DiagnosticsProtocol.COLLECT_CHUNK_REQUEST));
        assertTrue(DiagnosticsProtocol.isOperationalOnlyPayloadType(DiagnosticsProtocol.SNAPSHOT_REQUEST));
    }

    @Test
    void isOperationalOnlyPayloadTypeFalseForOtherValues() {
        assertFalse(DiagnosticsProtocol.isOperationalOnlyPayloadType("server-policy.fetch.request"));
        assertFalse(DiagnosticsProtocol.isOperationalOnlyPayloadType(""));
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void prefixMatchingIsCaseSensitiveAccordingToCurrentBehavior() {
        assertFalse(DiagnosticsProtocol.isCollectPayloadType("DIAGNOSTICS.COLLECT.error"),
            "startsWith is case-sensitive; uppercase prefix does not match");
        assertFalse(DiagnosticsProtocol.isSnapshotPayloadType("DIAGNOSTICS.SNAPSHOT.request"));
    }

    @Test
    void blankPayloadTypeBehaviorMatchesCurrentBehavior() {
        assertFalse(DiagnosticsProtocol.isCollectPayloadType(""),
            "Blank string does not start with the collect prefix");
        assertFalse(DiagnosticsProtocol.isSnapshotPayloadType("   "),
            "Whitespace string does not start with the snapshot prefix");
    }

    @Test
    void collectPrefixCustomTypeIsRecognized() {
        assertTrue(DiagnosticsProtocol.isCollectPayloadType("diagnostics.collect.custom.type"),
            "Any type starting with 'diagnostics.collect.' is recognized as a collect payload type");
    }

    @Test
    void nullPayloadTypeThrowsAccordingToCurrentBehavior() {
        // payloadType.startsWith(...) — null receiver throws NullPointerException
        assertThrows(NullPointerException.class,
            () -> DiagnosticsProtocol.isCollectPayloadType(null),
            "null payloadType throws NullPointerException because startsWith is called on it");
        assertThrows(NullPointerException.class,
            () -> DiagnosticsProtocol.isSnapshotPayloadType(null));
        assertThrows(NullPointerException.class,
            () -> DiagnosticsProtocol.isOperationalOnlyPayloadType(null));
    }
}
