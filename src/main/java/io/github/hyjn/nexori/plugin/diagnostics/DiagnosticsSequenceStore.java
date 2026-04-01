package io.github.hyjn.nexori.plugin.diagnostics;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiagnosticsSequenceStore {

    private final Path file;
    private long currentValue;

    public DiagnosticsSequenceStore(@Nonnull Path diagnosticsStateDir) throws IOException {
        Files.createDirectories(diagnosticsStateDir);
        this.file = diagnosticsStateDir.resolve("sequence.txt");
        this.currentValue = load();
    }

    public synchronized long next() throws IOException {
        currentValue += 1L;
        persist();
        return currentValue;
    }

    private long load() throws IOException {
        if (!Files.exists(file)) {
            persistValue(0L);
            return 0L;
        }
        String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (raw.isBlank()) {
            persistValue(0L);
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            persistValue(0L);
            return 0L;
        }
    }

    private void persist() throws IOException {
        persistValue(currentValue);
    }

    private void persistValue(long value) throws IOException {
        Files.writeString(file, Long.toString(value), StandardCharsets.UTF_8);
    }
}
