package io.github.hyjn.nexori.plugin.hud;

import javax.annotation.Nonnull;

/**
 * Pure resolver for the return/cancellation HUD title and subtitle.
 *
 * <p>Kept dependency-free (no engine/UI types) so it can be unit tested directly. The title is
 * reason-aware for NO_CONTEST cancellations so a player returned because the initial placement
 * window expired or because not enough players joined does not see AFK-specific copy.</p>
 */
public final class ReturnHudCopy {

    // Return reason codes carried by ReturnHudState. They mirror the codes the match runtime stamps
    // on the player outcome / explicit admission close so the HUD can map them to friendly copy.
    public static final String REASON_INITIAL_PLACEMENT_WINDOW_MISSED = "INITIAL_PLACEMENT_WINDOW_MISSED";
    public static final String REASON_NOT_ENOUGH_PLAYERS = "INITIAL_WINDOW_EXPIRED_MIN_PLAYERS_NOT_MET";
    public static final String REASON_BACKEND_AFK_CANCEL = "BACKEND_AFK_CANCEL";

    private static final String RETURNING_TO_ORIGIN = "Returning to Origin Server";
    private static final String RETURNING_TO_LOBBY = "Returning to lobby...";

    private ReturnHudCopy() {
    }

    @Nonnull
    public static Copy resolve(String outcomeLabel, String reasonCode) {
        String normalizedOutcome = outcomeLabel == null ? "" : outcomeLabel.trim().toLowerCase();
        String normalizedReason = reasonCode == null ? "" : reasonCode.trim();
        if ("victory".equals(normalizedOutcome)) {
            return new Copy("VICTORY", RETURNING_TO_ORIGIN);
        }
        if ("eliminated".equals(normalizedOutcome)) {
            return new Copy("ELIMINATED", RETURNING_TO_ORIGIN);
        }
        if ("no contest".equals(normalizedOutcome)) {
            if (REASON_INITIAL_PLACEMENT_WINDOW_MISSED.equals(normalizedReason)) {
                return new Copy("MATCH START WINDOW EXPIRED", "You did not join in time. " + RETURNING_TO_LOBBY);
            }
            if (REASON_NOT_ENOUGH_PLAYERS.equals(normalizedReason)) {
                return new Copy("NOT ENOUGH PLAYERS", RETURNING_TO_LOBBY);
            }
            if (REASON_BACKEND_AFK_CANCEL.equals(normalizedReason)) {
                return new Copy("MATCH CANCELLED DUE TO AFK PLAYER", RETURNING_TO_LOBBY);
            }
            return new Copy("MATCH CANCELLED", RETURNING_TO_LOBBY);
        }
        return new Copy("MATCH COMPLETE", RETURNING_TO_ORIGIN);
    }

    public record Copy(String title, String subtitle) {
    }
}
