package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;

public record EffectiveAfkDetectionPolicy(
    String matchId,
    String queueId,
    String arenaId,
    String rulesEngineId,
    AfkDetectionPolicy policy
) {

    public EffectiveAfkDetectionPolicy(
        String matchId,
        AfkDetectionPolicy policy
    ) {
        this(matchId, "", "", "", policy);
    }

    @Nonnull
    public EffectiveAfkDetectionPolicy normalized() {
        String normalizedMatchId = matchId == null ? "" : matchId.trim();
        return new EffectiveAfkDetectionPolicy(
            normalizedMatchId,
            normalize(queueId),
            normalize(arenaId),
            normalize(rulesEngineId),
            AfkDetectionPolicy.normalize(policy)
        );
    }

    @Nonnull
    private static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
