package io.github.hyjn.nexori.plugin.diagnostics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsSequenceStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void firstNextReturnsOne() throws IOException {
        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);

        assertEquals(1L, store.next());
    }

    @Test
    void subsequentNextCallsAreMonotonic() throws IOException {
        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);
        long first = store.next();
        long second = store.next();
        long third = store.next();

        assertTrue(second > first);
        assertTrue(third > second);
    }

    @Test
    void eachNextIncrementsByOne() throws IOException {
        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);

        long v1 = store.next();
        long v2 = store.next();
        long v3 = store.next();

        assertEquals(v1 + 1, v2);
        assertEquals(v2 + 1, v3);
    }

    @Test
    void valueIsPersistedAcrossInstances() throws IOException {
        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);
        store.next(); // 1
        store.next(); // 2

        DiagnosticsSequenceStore reloaded = new DiagnosticsSequenceStore(tempDir);

        assertEquals(3L, reloaded.next());
    }

    @Test
    void missingFileCreatesSequenceFileWithZero() throws IOException {
        new DiagnosticsSequenceStore(tempDir);

        Path file = tempDir.resolve("sequence.txt");
        assertTrue(Files.exists(file));
        assertEquals("0", Files.readString(file, StandardCharsets.UTF_8).trim());
    }

    @Test
    void afterNextSequenceFileContainsCurrentValue() throws IOException {
        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);
        store.next();

        Path file = tempDir.resolve("sequence.txt");
        assertEquals("1", Files.readString(file, StandardCharsets.UTF_8).trim());
    }

    @Test
    void blankFileAccordingToCurrentBehaviorStartsFromZero() throws IOException {
        Path file = tempDir.resolve("sequence.txt");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);

        assertEquals(1L, store.next());
    }

    @Test
    void corruptFileAccordingToCurrentBehaviorStartsFromZero() throws IOException {
        Path file = tempDir.resolve("sequence.txt");
        Files.writeString(file, "not-a-number", StandardCharsets.UTF_8);

        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);

        assertEquals(1L, store.next());
    }

    @Test
    void largePersistedValueResumesCorrectly() throws IOException {
        Path file = tempDir.resolve("sequence.txt");
        Files.writeString(file, "9999", StandardCharsets.UTF_8);

        DiagnosticsSequenceStore store = new DiagnosticsSequenceStore(tempDir);

        assertEquals(10_000L, store.next());
    }

    @Test
    void constructorCreatesDirectoryIfMissing() throws IOException {
        Path nested = tempDir.resolve("state").resolve("diagnostics");

        new DiagnosticsSequenceStore(nested);

        assertTrue(Files.isDirectory(nested));
        assertTrue(Files.exists(nested.resolve("sequence.txt")));
    }
}
