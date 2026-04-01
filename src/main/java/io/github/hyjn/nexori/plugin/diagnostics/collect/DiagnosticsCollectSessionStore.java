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

    public synchronized void saveManifest(@Nonnull String sessionId, @Nonnull String remoteServerId, @Nonnull DiagnosticsCollectManifest manifest) {
        writeJson(manifestFile(sessionId, remoteServerId), manifest);
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectManifest> loadManifest(@Nonnull String sessionId, @Nonnull String remoteServerId) {
        return readJson(manifestFile(sessionId, remoteServerId), DiagnosticsCollectManifest.class);
    }

    public synchronized void saveServerProgress(@Nonnull String sessionId, @Nonnull DiagnosticsCollectServerProgress progress) {
        writeJson(progressFile(sessionId, progress.remoteServerId()), progress);
    }

    @Nonnull
    public synchronized Optional<DiagnosticsCollectServerProgress> loadServerProgress(@Nonnull String sessionId, @Nonnull String remoteServerId) {
        return readJson(progressFile(sessionId, remoteServerId), DiagnosticsCollectServerProgress.class);
    }

    @Nonnull
    public Path rawPartFile(@Nonnull String sessionId, @Nonnull String remoteServerId, @Nonnull String fileId) {
        Path file = rawDir(sessionId, remoteServerId).resolve(fileId + ".part");
        ensureParent(file);
        return file;
    }

    @Nonnull
    public Path rawFinalFile(@Nonnull String sessionId, @Nonnull String remoteServerId, @Nonnull String fileId) {
        Path file = rawDir(sessionId, remoteServerId).resolve(fileId + ".jsonl");
        ensureParent(file);
        return file;
    }

    public synchronized void finalizeRawFile(@Nonnull String sessionId, @Nonnull String remoteServerId, @Nonnull String fileId) {
        Path part = rawPartFile(sessionId, remoteServerId, fileId);
        Path target = rawFinalFile(sessionId, remoteServerId, fileId);
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

    private Path activeLockFile() {
        return collectDir.resolve("active.lock.json");
    }

    private Path sessionFile(@Nonnull String sessionId) {
        return sessionDir(sessionId).resolve("session.json");
    }

    private Path manifestFile(@Nonnull String sessionId, @Nonnull String remoteServerId) {
        Path file = sessionDir(sessionId).resolve("manifests").resolve(remoteServerId + ".json");
        ensureParent(file);
        return file;
    }

    private Path progressFile(@Nonnull String sessionId, @Nonnull String remoteServerId) {
        Path file = sessionDir(sessionId).resolve("progress").resolve(remoteServerId + ".json");
        ensureParent(file);
        return file;
    }

    private Path rawDir(@Nonnull String sessionId, @Nonnull String remoteServerId) {
        Path dir = sessionDir(sessionId).resolve("raw").resolve(remoteServerId);
        ensureDir(dir);
        return dir;
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
