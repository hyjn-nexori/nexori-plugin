package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;

public record EffectiveAfkDetectionPolicy(
    String matchId,
    AfkDetectionPolicy policy
) {

    @Nonnull
    public EffectiveAfkDetectionPolicy normalized() {
        String normalizedMatchId = matchId == null ? "" : matchId.trim();
        return new EffectiveAfkDetectionPolicy(normalizedMatchId, AfkDetectionPolicy.normalize(policy));
    }
}
