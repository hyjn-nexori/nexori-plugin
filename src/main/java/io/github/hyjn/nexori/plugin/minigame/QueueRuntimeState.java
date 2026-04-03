package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public record QueueRuntimeState(
    String queueId,
    QueuePhase phase,
    List<QueueMemberState> waitingMembers,
    List<QueueMemberState> readyMembers,
    long countdownEndsAtEpochMs,
    long readyAtEpochMs,
    long lastStateChangeEpochMs,
    long lastLaunchAttemptAtEpochMs,
    String lastLaunchError
) {

    @Nonnull
    public QueueRuntimeState normalized() {
        String normalizedQueueId = QueueDefinition.normalizeId(queueId);
        QueuePhase normalizedPhase = phase == null ? QueuePhase.WAITING : phase;
        long now = System.currentTimeMillis();
        return new QueueRuntimeState(
            normalizedQueueId,
            normalizedPhase,
            normalizeMembers(waitingMembers),
            normalizeMembers(readyMembers),
            Math.max(0L, countdownEndsAtEpochMs),
            Math.max(0L, readyAtEpochMs),
            lastStateChangeEpochMs <= 0 ? now : lastStateChangeEpochMs,
            Math.max(0L, lastLaunchAttemptAtEpochMs),
            normalizeOptionalText(lastLaunchError)
        );
    }

    @Nonnull
    public static QueueRuntimeState empty(@Nonnull String queueId, long nowEpochMs) {
        return new QueueRuntimeState(
            QueueDefinition.normalizeId(queueId),
            QueuePhase.WAITING,
            List.of(),
            List.of(),
            0L,
            0L,
            nowEpochMs,
            0L,
            ""
        );
    }

    public int queuedPlayerCount() {
        return waitingMembers.size() + readyMembers.size();
    }

    public boolean hasReadyBatch() {
        return !readyMembers.isEmpty();
    }

    @Nonnull
    private static List<QueueMemberState> normalizeMembers(List<QueueMemberState> rawMembers) {
        if (rawMembers == null || rawMembers.isEmpty()) {
            return List.of();
        }
        List<QueueMemberState> normalized = new ArrayList<>();
        for (QueueMemberState member : rawMembers) {
            if (member != null) {
                normalized.add(member.normalized());
            }
        }
        return List.copyOf(normalized);
    }

    @Nonnull
    private static String normalizeOptionalText(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
