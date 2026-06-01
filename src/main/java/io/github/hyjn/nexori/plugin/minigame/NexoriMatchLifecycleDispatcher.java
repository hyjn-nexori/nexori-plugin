package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriListenerRegistration;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementLifecycleEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches Nexori public match lifecycle callbacks to registered rules-engine listeners.
 */
public final class NexoriMatchLifecycleDispatcher {

    private final HytaleLogger logger;
    private final Map<String, List<NexoriMatchLifecycleListener>> listenersByRulesEngineId = new LinkedHashMap<>();

    public NexoriMatchLifecycleDispatcher() {
        this(HytaleLogger.forEnclosingClass());
    }

    public NexoriMatchLifecycleDispatcher(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    @Nonnull
    public NexoriListenerRegistration register(
        @Nonnull String rulesEngineId,
        @Nonnull NexoriMatchLifecycleListener listener
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

    public void dispatchMatchCreated(@Nonnull NexoriMatchLifecycleEvent event) {
        dispatchMatchEvent(event, NexoriMatchLifecycleListener::onMatchCreated);
    }

    public void dispatchPlayerArrived(@Nonnull NexoriPlayerMatchLifecycleEvent event) {
        dispatchPlayerEvent(event, NexoriMatchLifecycleListener::onPlayerArrived);
    }

    public void dispatchPlayerPlacementConfirmed(@Nonnull NexoriPlayerPlacementLifecycleEvent event) {
        if (event == null) {
            return;
        }
        List<NexoriMatchLifecycleListener> listeners = snapshotListeners(event.player().match().rulesEngineId());
        for (NexoriMatchLifecycleListener listener : listeners) {
            safelyInvoke(() -> listener.onPlayerPlacementConfirmed(event));
        }
    }

    public void dispatchMatchPlacementCompleted(@Nonnull NexoriMatchLifecycleEvent event) {
        dispatchMatchEvent(event, NexoriMatchLifecycleListener::onMatchPlacementCompleted);
    }

    public void dispatchMatchStartAllowed(@Nonnull NexoriMatchLifecycleEvent event) {
        dispatchMatchEvent(event, NexoriMatchLifecycleListener::onMatchStartAllowed);
    }

    public void dispatchMatchCancellationRequested(@Nonnull NexoriMatchLifecycleEvent event) {
        dispatchMatchEvent(event, NexoriMatchLifecycleListener::onMatchCancellationRequested);
    }

    public void dispatchMatchCompleted(@Nonnull NexoriMatchLifecycleEvent event) {
        dispatchMatchEvent(event, NexoriMatchLifecycleListener::onMatchCompleted);
    }

    public void dispatchMatchRuntimeClosed(@Nonnull NexoriMatchLifecycleEvent event) {
        dispatchMatchEvent(event, NexoriMatchLifecycleListener::onMatchRuntimeClosed);
    }

    private void dispatchMatchEvent(
        NexoriMatchLifecycleEvent event,
        @Nonnull MatchEventConsumer consumer
    ) {
        if (event == null) {
            return;
        }
        List<NexoriMatchLifecycleListener> listeners = snapshotListeners(event.rulesEngineId());
        for (NexoriMatchLifecycleListener listener : listeners) {
            safelyInvoke(() -> consumer.accept(listener, event));
        }
    }

    private void dispatchPlayerEvent(
        NexoriPlayerMatchLifecycleEvent event,
        @Nonnull PlayerEventConsumer consumer
    ) {
        if (event == null) {
            return;
        }
        List<NexoriMatchLifecycleListener> listeners = snapshotListeners(event.match().rulesEngineId());
        for (NexoriMatchLifecycleListener listener : listeners) {
            safelyInvoke(() -> consumer.accept(listener, event));
        }
    }

    @Nonnull
    private synchronized List<NexoriMatchLifecycleListener> snapshotListeners(String rulesEngineId) {
        String normalizedRulesEngineId = normalizeOptional(rulesEngineId);
        if (normalizedRulesEngineId.isBlank()) {
            return List.of();
        }
        List<NexoriMatchLifecycleListener> listeners = listenersByRulesEngineId.get(normalizedRulesEngineId);
        if (listeners == null || listeners.isEmpty()) {
            return List.of();
        }
        return List.copyOf(listeners);
    }

    private void unregister(@Nonnull String rulesEngineId, @Nonnull NexoriMatchLifecycleListener listener) {
        synchronized (this) {
            List<NexoriMatchLifecycleListener> listeners = listenersByRulesEngineId.get(rulesEngineId);
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
                "Nexori match lifecycle listener failed; continuing dispatch to remaining listeners."
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

    @FunctionalInterface
    private interface MatchEventConsumer {
        void accept(NexoriMatchLifecycleListener listener, NexoriMatchLifecycleEvent event);
    }

    @FunctionalInterface
    private interface PlayerEventConsumer {
        void accept(NexoriMatchLifecycleListener listener, NexoriPlayerMatchLifecycleEvent event);
    }

    private final class Registration implements NexoriListenerRegistration {

        private final String rulesEngineId;
        private final NexoriMatchLifecycleListener listener;
        private boolean closed;

        private Registration(@Nonnull String rulesEngineId, @Nonnull NexoriMatchLifecycleListener listener) {
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
