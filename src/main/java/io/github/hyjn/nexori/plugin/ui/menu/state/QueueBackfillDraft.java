package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;

import javax.annotation.Nonnull;

/**
 * Temporary backfill policy draft for the queue editor in the Nexori V2 menu.
 */
public record QueueBackfillDraft(
    Boolean enabled,
    QueueBackfillMode mode,
    String windowSeconds
) {

    @Nonnull
    public QueueBackfillDraft normalized(@Nonnull QueueDefinition editing) {
        return new QueueBackfillDraft(
            enabled == null ? editing.backfillEnabled() : enabled,
            mode == null ? editing.effectiveBackfillMode() : mode,
            windowSeconds == null || windowSeconds.isBlank()
                ? Integer.toString(Math.max(0, editing.backfillWindowSeconds()))
                : windowSeconds.trim()
        );
    }

    @Nonnull
    public QueueBackfillDraft withEnabled(boolean nextEnabled) {
        return new QueueBackfillDraft(nextEnabled, mode, windowSeconds);
    }

    @Nonnull
    public QueueBackfillDraft withMode(@Nonnull QueueBackfillMode nextMode) {
        return new QueueBackfillDraft(enabled, nextMode, windowSeconds);
    }

    @Nonnull
    public QueueBackfillDraft withWindowSeconds(@Nonnull String nextWindowSeconds) {
        return new QueueBackfillDraft(enabled, mode, nextWindowSeconds);
    }
}
