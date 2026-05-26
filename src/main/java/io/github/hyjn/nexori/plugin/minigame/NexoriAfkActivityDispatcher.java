package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivityListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriListenerRegistration;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerAfkChangedEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches Nexori public AFK activity callbacks to registered rules-engine listeners.
 */
public final class NexoriAfkActivityDispatcher {

    private final HytaleLogger logger;
    private final Map<String, List<NexoriAfkActivityListener>> listenersByRulesEngineId = new LinkedHashMap<>();

    public NexoriAfkActivityDispatcher() {
        this(HytaleLogger.forEnclosingClass());
    }

    public NexoriAfkActivityDispatcher(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    @Nonnull
    public NexoriListenerRegistration register(
        @Nonnull String rulesEngineId,
        @Nonnull NexoriAfkActivityListener listener
    ) {
        String normalizedRulesEngineId = normalizeRequired(rulesEngineId, "Rules engine id cannot be blank.");
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }
        synchronized (this) {
            listenersByRulesEngineId.computeIfAbsent(normalizedRulesEngineId, ignored -> new ArrayList<>()).add(listener);
        }
        return new Registration(normalizedRulesEngineId, listener);
    }

    public void dispatchPlayerAfkChanged(@Nonnull NexoriPlayerAfkChangedEvent event) {
        if (event == null) {
            return;
        }
        List<NexoriAfkActivityListener> listeners = snapshotListeners(event.rulesEngineId());
        for (NexoriAfkActivityListener listener : listeners) {
            safelyInvoke(() -> listener.onPlayerAfkChanged(event));
        }
    }

    @Nonnull
    private synchronized List<NexoriAfkActivityListener> snapshotListeners(String rulesEngineId) {
        String normalizedRulesEngineId = normalizeOptional(rulesEngineId);
        if (normalizedRulesEngineId.isBlank()) {
            return List.of();
        }
        List<NexoriAfkActivityListener> listeners = listenersByRulesEngineId.get(normalizedRulesEngineId);
        if (listeners == null || listeners.isEmpty()) {
            return List.of();
        }
        return List.copyOf(listeners);
    }

    private void unregister(@Nonnull String rulesEngineId, @Nonnull NexoriAfkActivityListener listener) {
        synchronized (this) {
            List<NexoriAfkActivityListener> listeners = listenersByRulesEngineId.get(rulesEngineId);
            if (listeners == null) {
                return;
            }
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                listenersByRulesEngineId.remove(rulesEngineId);
            }
        }
    }

    private void safelyInvoke(@Nonnull Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException | Error exception) {
            logger.atWarning().withCause(exception).log(
                "Nexori AFK activity listener failed; continuing dispatch to remaining listeners."
            );
        }
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    private final class Registration implements NexoriListenerRegistration {

        private final String rulesEngineId;
        private final NexoriAfkActivityListener listener;
        private boolean closed;

        private Registration(@Nonnull String rulesEngineId, @Nonnull NexoriAfkActivityListener listener) {
            this.rulesEngineId = rulesEngineId;
            this.listener = listener;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            unregister(rulesEngineId, listener);
        }
    }
}
