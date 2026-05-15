package io.github.hyjn.nexori.plugin.diagnostics.reporting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsTestChartStateTest {

    @Test
    void schemaVersionIsOne() {
        assertEquals(1, DiagnosticsTestChartState.SCHEMA_VERSION);
    }

    @Test
    void preservesAllFields() {
        DiagnosticsTestChartState state = new DiagnosticsTestChartState(
            1, 3, "test-chart-v3.png", 9_000_000L, 42);
        assertEquals(1, state.schemaVersion());
        assertEquals(3, state.version());
        assertEquals("test-chart-v3.png", state.currentFileName());
        assertEquals(9_000_000L, state.generatedAtEpochMs());
        assertEquals(42, state.visualValue());
    }

    @Test
    void schemaVersionFieldDefaultsToConstantWhenSet() {
        DiagnosticsTestChartState state = new DiagnosticsTestChartState(
            DiagnosticsTestChartState.SCHEMA_VERSION, 1, "test-chart-v1.png", 1000L, 0);
        assertEquals(DiagnosticsTestChartState.SCHEMA_VERSION, state.schemaVersion());
    }

    @Test
    void preservesZeroVisualValue() {
        DiagnosticsTestChartState state = new DiagnosticsTestChartState(1, 0, "file.png", 0L, 0);
        assertEquals(0, state.visualValue());
        assertEquals(0L, state.generatedAtEpochMs());
    }

    @Test
    void preservesNegativeVersionAccordingToCurrentBehavior() {
        DiagnosticsTestChartState state = new DiagnosticsTestChartState(1, -1, "file.png", 1000L, 5);
        assertEquals(-1, state.version(),
            "Record constructor does not validate version; negative values are preserved as-is");
    }

    @Test
    void currentFileNameIsPreservedVerbatim() {
        String fileName = "test-chart-v99.png";
        DiagnosticsTestChartState state = new DiagnosticsTestChartState(1, 99, fileName, 1000L, 100);
        assertTrue(state.currentFileName().contains("v99"));
        assertEquals(fileName, state.currentFileName());
    }
}
