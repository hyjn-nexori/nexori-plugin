package io.github.hyjn.nexori.plugin.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

public class BootstrapStateStore {

    private final Path stateDir;
    private final Path statePath;
    private BootstrapState currentState;

    public BootstrapStateStore(Path stateDir) {
        this.stateDir = stateDir;
        this.statePath = stateDir.resolve("bootstrap-state.properties");
        this.currentState = BootstrapState.initial();
    }

    public BootstrapState loadOrCreate() throws IOException {
        Files.createDirectories(stateDir);
        if (!Files.exists(statePath)) {
            persist(BootstrapState.initial());
            return currentState;
        }

        Properties properties = readProperties(statePath);
        this.currentState = new BootstrapState(
            Boolean.parseBoolean(properties.getProperty("bootstrapOpen", "false")),
            properties.getProperty("sessionId", ""),
            Long.parseLong(properties.getProperty("sessionExpiresAtEpochMillis", "0")),
            Long.parseLong(properties.getProperty("bundleVersion", "0")),
            properties.getProperty("bundleHash", "")
        );
        return currentState;
    }

    public BootstrapState getCurrentState() {
        if (currentState.hasActiveSession()) {
            return currentState;
        }

        if (currentState.bootstrapOpen()) {
            currentState = new BootstrapState(false, "", 0L, currentState.bundleVersion(), currentState.bundleHash());
            persistUnchecked(currentState);
        }
        return currentState;
    }

    public BootstrapState openSession(Duration duration) {
        Instant expiresAt = Instant.now().plus(duration);
        BootstrapState state = new BootstrapState(
            true,
            UUID.randomUUID().toString(),
            expiresAt.toEpochMilli(),
            currentState.bundleVersion(),
            currentState.bundleHash()
        );
        persistUnchecked(state);
        return state;
    }

    public BootstrapState closeSession() {
        BootstrapState state = new BootstrapState(false, "", 0L, currentState.bundleVersion(), currentState.bundleHash());
        persistUnchecked(state);
        return state;
    }

    public BootstrapState markBundleInstalled(String bundleHash) {
        return markBundleInstalled(currentState.bundleVersion() + 1, bundleHash);
    }

    public BootstrapState markBundleInstalled(long bundleVersion, String bundleHash) {
        BootstrapState state = new BootstrapState(
            false,
            "",
            0L,
            bundleVersion,
            bundleHash
        );
        persistUnchecked(state);
        return state;
    }

    private void persistUnchecked(BootstrapState state) {
        try {
            persist(state);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist Nexori bootstrap state", exception);
        }
    }

    private void persist(BootstrapState state) throws IOException {
        Files.createDirectories(stateDir);
        Properties properties = new Properties();
        properties.setProperty("bootstrapOpen", Boolean.toString(state.bootstrapOpen()));
        properties.setProperty("sessionId", state.sessionId());
        properties.setProperty("sessionExpiresAtEpochMillis", Long.toString(state.sessionExpiresAtEpochMillis()));
        properties.setProperty("bundleVersion", Long.toString(state.bundleVersion()));
        properties.setProperty("bundleHash", state.bundleHash());

        try (OutputStream outputStream = Files.newOutputStream(statePath)) {
            properties.store(outputStream, "Nexori bootstrap state");
        }

        this.currentState = state;
    }

    private Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }
}
