package io.github.hyjn.nexori.plugin.diagnostics.collect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DiagnosticsCollectConsolidator {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Nonnull
    public DiagnosticsCollectConsolidationResult consolidate(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectSessionStore store
    ) throws IOException {
        List<DiagnosticsEvent> events = new ArrayList<>();
        long inputEvents = 0L;
        Set<String> seenEventIds = new LinkedHashSet<>();
        Set<String> seenFallbackKeys = new LinkedHashSet<>();

        for (DiagnosticsCollectSourceProgress source : session.sources()) {
            for (DiagnosticsCollectFileProgress file : source.files()) {
                Path rawFile = store.rawFinalFile(session.sessionId(), source.sourceKind(), source.sourceServerId(), file.fileId());
                if (!Files.exists(rawFile)) {
                    continue;
                }
                for (String line : Files.readAllLines(rawFile, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    DiagnosticsEvent event = GSON.fromJson(line, DiagnosticsEvent.class);
                    if (event == null || event.eventId().isBlank()) {
                        continue;
                    }
                    inputEvents += 1L;
                    if (!seenEventIds.add(event.eventId())) {
                        continue;
                    }
                    String fallbackKey = event.sourceServerId() + "|" + event.sourceSequence();
                    if (!seenFallbackKeys.add(fallbackKey)) {
                        continue;
                    }
                    events.add(event);
                }
            }
        }

        events.sort(Comparator
            .comparingLong(DiagnosticsEvent::occurredAtEpochMs)
            .thenComparingLong(DiagnosticsEvent::sourceSequence)
            .thenComparing(DiagnosticsEvent::sourceServerId)
            .thenComparing(DiagnosticsEvent::eventId));

        StringBuilder ndjson = new StringBuilder();
        for (DiagnosticsEvent event : events) {
            ndjson.append(GSON.toJson(event)).append('\n');
        }

        long outputBytes = ndjson.toString().getBytes(StandardCharsets.UTF_8).length;
        long duplicateEvents = Math.max(0L, inputEvents - events.size());
        return new DiagnosticsCollectConsolidationResult(inputEvents, events.size(), duplicateEvents, outputBytes, ndjson.toString());
    }

    @Nonnull
    public DiagnosticsCollectConsolidatedIndex buildIndex(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectConsolidationResult result
    ) {
        List<DiagnosticsCollectConsolidatedIndex.SourceSummary> sources = session.sources().stream()
            .map(source -> new DiagnosticsCollectConsolidatedIndex.SourceSummary(
                source.sourceKind(),
                source.sourceServerId(),
                source.sourceConnectionAddress(),
                source.downloadedBytes(),
                source.downloadedEvents()
            ))
            .toList();
        return new DiagnosticsCollectConsolidatedIndex(
            DiagnosticsCollectConsolidatedIndex.SCHEMA_VERSION,
            session.sessionId(),
            System.currentTimeMillis(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            result.inputEvents(),
            result.writtenEvents(),
            result.duplicateEvents(),
            result.outputBytes(),
            sources
        );
    }
}
