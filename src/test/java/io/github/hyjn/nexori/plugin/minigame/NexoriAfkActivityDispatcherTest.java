package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivityListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriListenerRegistration;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerAfkChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NexoriAfkActivityDispatcherTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void dispatchesRegisteredListener() {
        NexoriAfkActivityDispatcher dispatcher = new NexoriAfkActivityDispatcher();
        List<NexoriPlayerAfkChangedEvent> received = new ArrayList<>();

        dispatcher.register("capture_the_zone", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                received.add(event);
            }
        });
        dispatcher.dispatchPlayerAfkChanged(event("capture_the_zone", true, NexoriAfkActivitySource.IDLE_TIMEOUT));

        assertEquals(1, received.size());
        assertEquals(PLAYER_ONE, received.get(0).playerUuid());
    }

    @Test
    void filtersByRulesEngineId() {
        NexoriAfkActivityDispatcher dispatcher = new NexoriAfkActivityDispatcher();
        AtomicInteger matchingCalls = new AtomicInteger();
        AtomicInteger otherCalls = new AtomicInteger();

        dispatcher.register("capture_the_zone", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                matchingCalls.incrementAndGet();
            }
        });
        dispatcher.register("other_rules", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                otherCalls.incrementAndGet();
            }
        });
        dispatcher.dispatchPlayerAfkChanged(event("capture_the_zone", true, NexoriAfkActivitySource.IDLE_TIMEOUT));

        assertEquals(1, matchingCalls.get());
        assertEquals(0, otherCalls.get());
    }

    @Test
    void listenerThrowingExceptionDoesNotBlockRemainingListeners() {
        NexoriAfkActivityDispatcher dispatcher = new NexoriAfkActivityDispatcher();
        AtomicInteger calls = new AtomicInteger();

        dispatcher.register("capture_the_zone", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                calls.incrementAndGet();
            }
        });
        dispatcher.dispatchPlayerAfkChanged(event("capture_the_zone", true, NexoriAfkActivitySource.IDLE_TIMEOUT));

        assertEquals(1, calls.get());
    }

    @Test
    void registrationCloseUnregistersListener() {
        NexoriAfkActivityDispatcher dispatcher = new NexoriAfkActivityDispatcher();
        AtomicInteger calls = new AtomicInteger();
        NexoriListenerRegistration registration = dispatcher.register("capture_the_zone", new NexoriAfkActivityListener() {
            @Override
            public void onPlayerAfkChanged(NexoriPlayerAfkChangedEvent event) {
                calls.incrementAndGet();
            }
        });

        registration.close();
        registration.close();
        dispatcher.dispatchPlayerAfkChanged(event("capture_the_zone", true, NexoriAfkActivitySource.IDLE_TIMEOUT));

        assertEquals(0, calls.get());
    }

    @Test
    void registerRejectsInvalidInput() {
        NexoriAfkActivityDispatcher dispatcher = new NexoriAfkActivityDispatcher();

        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(" ", new NexoriAfkActivityListener() {
        }));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.register("capture_the_zone", null));
    }

    private static NexoriPlayerAfkChangedEvent event(
        String rulesEngineId,
        boolean afk,
        NexoriAfkActivitySource source
    ) {
        return new NexoriPlayerAfkChangedEvent(
            "match-1",
            "queue-1",
            "arena-1",
            rulesEngineId,
            PLAYER_ONE,
            "PlayerOne",
            afk,
            2_000L,
            1_000L,
            source
        );
    }
}
