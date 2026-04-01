package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public enum DiagnosticsCollectWindowPreset {
    TODAY("today", "Today"),
    YESTERDAY("yesterday", "Yesterday"),
    LAST_3_DAYS("3d", "Last 3 Days"),
    LAST_7_DAYS("7d", "Last 7 Days"),
    LAST_30_DAYS("30d", "Last 30 Days");

    private final String id;
    private final String label;

    DiagnosticsCollectWindowPreset(@Nonnull String id, @Nonnull String label) {
        this.id = id;
        this.label = label;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String label() {
        return label;
    }

    @Nonnull
    public DiagnosticsCollectWindow resolve(long nowEpochMs) {
        LocalDate todayUtc = Instant.ofEpochMilli(nowEpochMs).atZone(ZoneOffset.UTC).toLocalDate();
        return switch (this) {
            case TODAY -> rangeForDays(todayUtc, todayUtc, label);
            case YESTERDAY -> {
                LocalDate yesterday = todayUtc.minusDays(1);
                yield rangeForDays(yesterday, yesterday, label);
            }
            case LAST_3_DAYS -> rangeForDays(todayUtc.minusDays(2), todayUtc, label);
            case LAST_7_DAYS -> rangeForDays(todayUtc.minusDays(6), todayUtc, label);
            case LAST_30_DAYS -> rangeForDays(todayUtc.minusDays(29), todayUtc, label);
        };
    }

    @Nonnull
    public static DiagnosticsCollectWindowPreset fromId(@Nonnull String id) {
        for (DiagnosticsCollectWindowPreset preset : values()) {
            if (preset.id.equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return LAST_7_DAYS;
    }

    @Nonnull
    private static DiagnosticsCollectWindow rangeForDays(
        @Nonnull LocalDate startUtc,
        @Nonnull LocalDate endUtc,
        @Nonnull String label
    ) {
        long start = startUtc.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long end = endUtc.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1L;
        return new DiagnosticsCollectWindow(start, end, label);
    }
}
