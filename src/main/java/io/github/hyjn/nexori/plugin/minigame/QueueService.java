package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QueueService {

    private final QueueStore store;
    private final ArenaService arenaService;
    private final HytaleLogger logger;
    private final Map<String, QueueDefinition> queuesById = new LinkedHashMap<>();

    public QueueService(@Nonnull QueueStore store, @Nonnull ArenaService arenaService, @Nonnull HytaleLogger logger) throws IOException {
        this.store = store;
        this.arenaService = arenaService;
        this.logger = logger;
        for (QueueDefinition queue : store.loadOrCreate()) {
            warnInvalidMatchmakingMode(queue);
            warnInvalidBackfillMode(queue);
            QueueDefinition normalized = queue.normalized();
            validate(normalized);
            queuesById.put(normalized.queueId(), normalized);
        }
    }

    @Nonnull
    public synchronized List<QueueDefinition> list() {
        return queuesById.values().stream()
            .sorted(Comparator.comparing(QueueDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Nonnull
    public synchronized Optional<QueueDefinition> find(@Nonnull String rawQueueId) {
        if (rawQueueId == null || rawQueueId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(queuesById.get(QueueDefinition.normalizeId(rawQueueId)));
    }

    @Nonnull
    public synchronized QueueDefinition upsert(@Nonnull QueueDefinition definition) throws IOException {
        warnInvalidMatchmakingMode(definition);
        warnInvalidBackfillMode(definition);
        QueueDefinition normalized = definition.normalized();
        validate(normalized);
        queuesById.put(normalized.queueId(), normalized);
        persist();
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawQueueId) throws IOException {
        String normalizedQueueId = QueueDefinition.normalizeId(rawQueueId);
        QueueDefinition removed = queuesById.remove(normalizedQueueId);
        persist();
        return removed != null;
    }

    private void validate(@Nonnull QueueDefinition definition) {
        if (definition.arenaIds().isEmpty()) {
            throw new IllegalArgumentException("Queue must reference at least one arena.");
        }
        if (definition.minPlayers() < 1) {
            throw new IllegalArgumentException("Queue min players must be at least 1.");
        }
        if (definition.maxPlayers() < definition.minPlayers()) {
            throw new IllegalArgumentException("Queue max players must be greater than or equal to min players.");
        }
        if (definition.countdownSeconds() < 0) {
            throw new IllegalArgumentException("Queue countdown seconds cannot be negative.");
        }
        if (definition.backfillWindowSeconds() < 0) {
            throw new IllegalArgumentException("Queue backfill window seconds cannot be negative.");
        }
        TravelProfileType.parse(definition.launchTravelProfileId());
        for (String arenaId : definition.arenaIds()) {
            arenaService.find(arenaId)
                .orElseThrow(() -> new IllegalArgumentException("Queue references missing arena '" + arenaId + "'."));
        }
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(queuesById.values()));
    }

    private void warnInvalidMatchmakingMode(@Nonnull QueueDefinition definition) {
        if (!definition.hasInvalidMatchmakingMode()) {
            return;
        }
        String queueId = definition.queueId() == null || definition.queueId().isBlank()
            ? "<unknown>"
            : definition.queueId().trim();
        logger.atWarning().log(
            "Queue '" + queueId + "' has invalid matchmakingMode '"
                + definition.matchmakingMode()
                + "'; falling back to LOCAL_FIFO."
        );
    }

    private void warnInvalidBackfillMode(@Nonnull QueueDefinition definition) {
        if (!definition.hasInvalidBackfillMode()) {
            return;
        }
        String queueId = definition.queueId() == null || definition.queueId().isBlank()
            ? "<unknown>"
            : definition.queueId().trim();
        logger.atWarning().log(
            "Queue '" + queueId + "' has invalid backfillMode '"
                + definition.backfillMode()
                + "'; falling back to NONE."
        );
    }
}
