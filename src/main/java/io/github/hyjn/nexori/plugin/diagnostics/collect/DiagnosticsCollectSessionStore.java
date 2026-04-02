package io.github.hyjn.nexori.plugin.diagnostics.collect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class DiagnosticsCollectSessionStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path collectDir;

    public DiagnosticsCollectSessionStore(@Nonnull Path pluginDataDirectory) throws IOException {
        this.collectDir = pluginDataDirectory.resolve("state").resolve("diagnostics").resolve("collect");
        Files.createDirectories(collectDir.resolve("sessions"));
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectActiveLock> loadActiveLock() {
        return readJson(activeLockFile(), DiagnosticsCollectActiveLock.class);
    }

    public synchronized void saveActiveLock(@Nonnull DiagnosticsCollectActiveLock lock) {
        writeJson(activeLockFile(), lock);
    }

    public synchronized void clearActiveLock() {
        try {
            Files.deleteIfExists(activeLockFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clear Nexori diagnostics collect active lock", exception);
        }
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectSession> loadSession(@Nonnull String sessionId) {
        return readJson(sessionFile(sessionId), DiagnosticsCollectSession.class);
    }

    public synchronized void saveSession(@Nonnull DiagnosticsCollectSession session) {
        writeJson(sessionFile(session.sessionId()), session);
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectSession> loadLatestSession() {
        List<DiagnosticsCollectSession> sessions = new ArrayList<>();
        Path sessionsDir = collectDir.resolve("sessions");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir)) {
            for (Path sessionDir : stream) {
                if (!Files.isDirectory(sessionDir)) {
                    continue;
                }
                readJson(sessionDir.resolve("session.json"), DiagnosticsCollectSession.class).ifPresent(sessions::add);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list Nexori diagnostics collect sessions", exception);
        }
        return sessions.stream()
            .max(Comparator.comparingLong(DiagnosticsCollectSession::updatedAtEpochMs)
                .thenComparing(DiagnosticsCollectSession::sessionId));
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectSession> loadLatestCompletedSession() {
        List<DiagnosticsCollectSession> sessions = new ArrayList<>();
        Path sessionsDir = collectDir.resolve("sessions");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir)) {
            for (Path sessionDir : stream) {
                if (!Files.isDirectory(sessionDir)) {
                    continue;
                }
                readJson(sessionDir.resolve("session.json"), DiagnosticsCollectSession.class)
                    .filter(session -> session.status() == DiagnosticsCollectStatus.COMPLETED)
                    .ifPresent(sessions::add);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list completed Nexori diagnostics collect sessions", exception);
        }
        return sessions.stream()
            .max(Comparator.comparingLong(DiagnosticsCollectSession::updatedAtEpochMs)
                .thenComparing(DiagnosticsCollectSession::sessionId));
    }

    public synchronized void saveManifest(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId, @Nonnull DiagnosticsCollectManifest manifest) {
        writeJson(manifestFile(sessionId, sourceKind, sourceServerId), manifest);
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectManifest> loadManifest(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId) {
        return readJson(manifestFile(sessionId, sourceKind, sourceServerId), DiagnosticsCollectManifest.class);
    }

    public synchronized void saveSourceProgress(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceProgress progress) {
        writeJson(progressFile(sessionId, progress.sourceKind(), progress.sourceServerId()), progress);
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectSourceProgress> loadSourceProgress(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId) {
        return readJson(progressFile(sessionId, sourceKind, sourceServerId), DiagnosticsCollectSourceProgress.class);
    }

    @Nonnull
    public Path rawPartFile(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId, @Nonnull String fileId) {
        Path file = rawDir(sessionId, sourceKind, sourceServerId).resolve(fileId + ".part");
        ensureParent(file);
        return file;
    }

    @Nonnull
    public Path rawFinalFile(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId, @Nonnull String fileId) {
        Path file = rawDir(sessionId, sourceKind, sourceServerId).resolve(fileId + ".jsonl");
        ensureParent(file);
        return file;
    }

    public synchronized void finalizeRawFile(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId, @Nonnull String fileId) {
        Path part = rawPartFile(sessionId, sourceKind, sourceServerId, fileId);
        Path target = rawFinalFile(sessionId, sourceKind, sourceServerId, fileId);
        if (!Files.exists(part)) {
            return;
        }
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            try {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException nested) {
                throw new IllegalStateException("Failed to finalize Nexori diagnostics raw collect file", nested);
            }
        }
    }

    @Nonnull
    public Path sessionDir(@Nonnull String sessionId) {
        Path dir = collectDir.resolve("sessions").resolve(sessionId);
        ensureDir(dir);
        return dir;
    }

    @Nonnull
    public Path outputFile(@Nonnull String sessionId, @Nonnull String fileName) {
        Path file = sessionDir(sessionId).resolve("output").resolve(fileName);
        ensureParent(file);
        return file;
    }

    public synchronized void writeOutputJson(@Nonnull String sessionId, @Nonnull String fileName, @Nonnull Object value) {
        writeJson(outputFile(sessionId, fileName), value);
    }

    public synchronized void writeOutputText(@Nonnull String sessionId, @Nonnull String fileName, @Nonnull String raw) {
        Path file = outputFile(sessionId, fileName);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, raw, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist Nexori diagnostics collect output to " + file, exception);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    private Path activeLockFile() {
        return collectDir.resolve("active.lock.json");
    }

    private Path sessionFile(@Nonnull String sessionId) {
        return sessionDir(sessionId).resolve("session.json");
    }

    private Path manifestFile(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId) {
        Path file = sessionDir(sessionId).resolve("manifests").resolve(sourceKey(sourceKind, sourceServerId) + ".json");
        ensureParent(file);
        return file;
    }

    private Path progressFile(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId) {
        Path file = sessionDir(sessionId).resolve("progress").resolve(sourceKey(sourceKind, sourceServerId) + ".json");
        ensureParent(file);
        return file;
    }

    private Path rawDir(@Nonnull String sessionId, @Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId) {
        Path dir = sessionDir(sessionId).resolve("raw").resolve(sourceKey(sourceKind, sourceServerId));
        ensureDir(dir);
        return dir;
    }

    @Nonnull
    private String sourceKey(@Nonnull DiagnosticsCollectSourceKind sourceKind, @Nonnull String sourceServerId) {
        return sourceKind == DiagnosticsCollectSourceKind.LOCAL ? "local" : sourceServerId;
    }

    private <T> Optional<T> readJson(@Nonnull Path file, @Nonnull Class<T> type) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(GSON.fromJson(json, type));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Nexori diagnostics collect json from " + file, exception);
        }
    }

    private void writeJson(@Nonnull Path file, @Nonnull Object value) {
        ensureParent(file);
        String json = GSON.toJson(value);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist Nexori diagnostics collect json to " + file, exception);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    private void ensureParent(@Nonnull Path file) {
        Path parent = file.getParent();
        if (parent != null) {
            ensureDir(parent);
        }
    }

    private void ensureDir(@Nonnull Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create Nexori diagnostics collect directory " + dir, exception);
        }
    }
}
