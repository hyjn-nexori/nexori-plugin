package io.github.hyjn.nexori.plugin.diagnostics.collect;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsCollectWindowPresetTest {

    // Fixed reference: 2024-01-15 12:00:00 UTC
    private static final long NOW = Instant.parse("2024-01-15T12:00:00Z").toEpochMilli();
    private static final long TODAY_START = Instant.parse("2024-01-15T00:00:00Z").toEpochMilli();
    private static final long TODAY_END = Instant.parse("2024-01-16T00:00:00Z").toEpochMilli() - 1L;
    private static final long YESTERDAY_START = Instant.parse("2024-01-14T00:00:00Z").toEpochMilli();
    private static final long YESTERDAY_END = Instant.parse("2024-01-15T00:00:00Z").toEpochMilli() - 1L;

    // ── ids and labels ────────────────────────────────────────────────────────

    @Test
    void idsAreStable() {
        assertEquals("today", DiagnosticsCollectWindowPreset.TODAY.id());
        assertEquals("yesterday", DiagnosticsCollectWindowPreset.YESTERDAY.id());
        assertEquals("3d", DiagnosticsCollectWindowPreset.LAST_3_DAYS.id());
        assertEquals("7d", DiagnosticsCollectWindowPreset.LAST_7_DAYS.id());
        assertEquals("30d", DiagnosticsCollectWindowPreset.LAST_30_DAYS.id());
    }

    @Test
    void labelsAreStable() {
        assertEquals("Today", DiagnosticsCollectWindowPreset.TODAY.label());
        assertEquals("Yesterday", DiagnosticsCollectWindowPreset.YESTERDAY.label());
        assertEquals("Last 3 Days", DiagnosticsCollectWindowPreset.LAST_3_DAYS.label());
        assertEquals("Last 7 Days", DiagnosticsCollectWindowPreset.LAST_7_DAYS.label());
        assertEquals("Last 30 Days", DiagnosticsCollectWindowPreset.LAST_30_DAYS.label());
    }

    // ── fromId ────────────────────────────────────────────────────────────────

    @Test
    void fromIdFindsKnownIdExact() {
        assertEquals(DiagnosticsCollectWindowPreset.TODAY, DiagnosticsCollectWindowPreset.fromId("today"));
        assertEquals(DiagnosticsCollectWindowPreset.LAST_7_DAYS, DiagnosticsCollectWindowPreset.fromId("7d"));
        assertEquals(DiagnosticsCollectWindowPreset.LAST_30_DAYS, DiagnosticsCollectWindowPreset.fromId("30d"));
    }

    @Test
    void fromIdFindsKnownIdCaseInsensitive() {
        assertEquals(DiagnosticsCollectWindowPreset.TODAY, DiagnosticsCollectWindowPreset.fromId("TODAY"));
        assertEquals(DiagnosticsCollectWindowPreset.YESTERDAY, DiagnosticsCollectWindowPreset.fromId("Yesterday"));
        assertEquals(DiagnosticsCollectWindowPreset.LAST_3_DAYS, DiagnosticsCollectWindowPreset.fromId("3D"));
    }

    @Test
    void fromIdUnknownDefaultsToLast7Days() {
        assertEquals(DiagnosticsCollectWindowPreset.LAST_7_DAYS,
            DiagnosticsCollectWindowPreset.fromId("unknown-id"));
    }

    @Test
    void fromIdBlankDefaultsToLast7DaysAccordingToCurrentBehavior() {
        assertEquals(DiagnosticsCollectWindowPreset.LAST_7_DAYS,
            DiagnosticsCollectWindowPreset.fromId(""),
            "Blank id does not match any preset, defaults to LAST_7_DAYS");
    }

    // ── TODAY ─────────────────────────────────────────────────────────────────

    @Test
    void todayResolveUsesUtcDayBounds() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.TODAY.resolve(NOW);
        assertEquals(TODAY_START, window.startEpochMs());
        assertEquals(TODAY_END, window.endEpochMs());
        assertEquals("Today", window.label());
    }

    // ── YESTERDAY ─────────────────────────────────────────────────────────────

    @Test
    void yesterdayResolveUsesPreviousUtcDayBounds() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.YESTERDAY.resolve(NOW);
        assertEquals(YESTERDAY_START, window.startEpochMs());
        assertEquals(YESTERDAY_END, window.endEpochMs());
        assertEquals("Yesterday", window.label());
    }

    // ── LAST_3_DAYS ───────────────────────────────────────────────────────────

    @Test
    void last3DaysResolveIncludesTodayAndTwoPriorDays() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.LAST_3_DAYS.resolve(NOW);
        long expectedStart = Instant.parse("2024-01-13T00:00:00Z").toEpochMilli();
        assertEquals(expectedStart, window.startEpochMs());
        assertEquals(TODAY_END, window.endEpochMs());
    }

    // ── LAST_7_DAYS ───────────────────────────────────────────────────────────

    @Test
    void last7DaysResolveIncludesTodayAndSixPriorDays() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.LAST_7_DAYS.resolve(NOW);
        long expectedStart = Instant.parse("2024-01-09T00:00:00Z").toEpochMilli();
        assertEquals(expectedStart, window.startEpochMs());
        assertEquals(TODAY_END, window.endEpochMs());
    }

    // ── LAST_30_DAYS ──────────────────────────────────────────────────────────

    @Test
    void last30DaysResolveIncludesTodayAndTwentyNinePriorDays() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.LAST_30_DAYS.resolve(NOW);
        long expectedStart = Instant.parse("2023-12-17T00:00:00Z").toEpochMilli();
        assertEquals(expectedStart, window.startEpochMs());
        assertEquals(TODAY_END, window.endEpochMs());
    }

    // ── end boundary ─────────────────────────────────────────────────────────

    @Test
    void resolveEndIsStartOfNextUtcDayMinusOneMillisecond() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.TODAY.resolve(NOW);
        long nextDayStart = Instant.parse("2024-01-16T00:00:00Z").toEpochMilli();
        assertEquals(nextDayStart - 1L, window.endEpochMs(),
            "end is endDay.plusDays(1).atStartOfDay(UTC).toEpochMilli() - 1");
    }

    @Test
    void windowStartIsInclusiveAndEndIsInclusiveAccordingToCurrentBehavior() {
        DiagnosticsCollectWindow window = DiagnosticsCollectWindowPreset.TODAY.resolve(NOW);
        assertTrue(window.startEpochMs() <= NOW && NOW <= window.endEpochMs(),
            "Now should be within today's window [start, end] inclusive");
    }
}
