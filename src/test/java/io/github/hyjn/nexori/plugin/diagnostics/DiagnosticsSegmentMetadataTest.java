package io.github.hyjn.nexori.plugin.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DiagnosticsSegmentMetadataTest {

    @Test
    void schemaVersionConstantIsOne() {
        assertEquals(1, DiagnosticsSegmentMetadata.SCHEMA_VERSION);
    }

    @Test
    void constructorPreservesAllFields() {
        Map<String, Long> categoryCounts = Map.of("TRAVEL", 5L, "SECURITY", 2L);
        Map<String, Long> outcomeCounts = Map.of("SUCCEEDED", 4L, "FAILED", 3L);

        DiagnosticsSegmentMetadata metadata = new DiagnosticsSegmentMetadata(
            1,
            "20240101-0001",
            "events-20240101-0001.jsonl",
            1_000_000L,
            2_000_000L,
            42L,
            8192L,
            "sha256:abc123",
            1L,
            42L,
            categoryCounts,
            outcomeCounts
        );

        assertEquals(1, metadata.schemaVersion());
        assertEquals("20240101-0001", metadata.fileId());
        assertEquals("events-20240101-0001.jsonl", metadata.fileName());
        assertEquals(1_000_000L, metadata.startedAtEpochMs());
        assertEquals(2_000_000L, metadata.endedAtEpochMs());
        assertEquals(42L, metadata.lineCount());
        assertEquals(8192L, metadata.byteSize());
        assertEquals("sha256:abc123", metadata.sha256());
        assertEquals(1L, metadata.firstSequence());
        assertEquals(42L, metadata.lastSequence());
        assertEquals(5L, metadata.categoryCounts().get("TRAVEL"));
        assertEquals(2L, metadata.categoryCounts().get("SECURITY"));
        assertEquals(4L, metadata.outcomeCounts().get("SUCCEEDED"));
        assertEquals(3L, metadata.outcomeCounts().get("FAILED"));
    }

    @Test
    void zeroLineCountIsPreserved() {
        DiagnosticsSegmentMetadata metadata = minimalMetadata(0L, 0L);

        assertEquals(0L, metadata.lineCount());
        assertEquals(0L, metadata.byteSize());
    }

    @Test
    void nullCategoryCountsAllowedByConstructorAccordingToCurrentBehavior() {
        DiagnosticsSegmentMetadata metadata = new DiagnosticsSegmentMetadata(
            1, "id", "file.jsonl", 0L, 0L, 0L, 0L, "sha256:", 0L, 0L, null, null
        );

        assertNull(metadata.categoryCounts());
        assertNull(metadata.outcomeCounts());
    }

    @Test
    void fileIdAndFileNameAreIndependent() {
        DiagnosticsSegmentMetadata metadata = minimalMetadata(10L, 512L);

        assertEquals("20240115-0001", metadata.fileId());
        assertEquals("events-20240115-0001.jsonl", metadata.fileName());
    }

    @Test
    void firstAndLastSequenceArePreserved() {
        DiagnosticsSegmentMetadata metadata = new DiagnosticsSegmentMetadata(
            1, "id", "f.jsonl", 0L, 0L, 5L, 100L, "sha256:", 7L, 99L, Map.of(), Map.of()
        );

        assertEquals(7L, metadata.firstSequence());
        assertEquals(99L, metadata.lastSequence());
    }

    private DiagnosticsSegmentMetadata minimalMetadata(long lineCount, long byteSize) {
        return new DiagnosticsSegmentMetadata(
            1,
            "20240115-0001",
            "events-20240115-0001.jsonl",
            1_000L,
            2_000L,
            lineCount,
            byteSize,
            "sha256:aabbcc",
            1L,
            lineCount,
            Map.of(),
            Map.of()
        );
    }
}
