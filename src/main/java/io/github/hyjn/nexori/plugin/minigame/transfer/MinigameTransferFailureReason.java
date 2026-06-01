package io.github.hyjn.nexori.plugin.minigame.transfer;

/**
 * String constants for minigame transfer failure reasons logged and stored in sessions.
 */
public final class MinigameTransferFailureReason {

    public static final String UNSAFE_READY_TIMEOUT = "UNSAFE_READY_TIMEOUT";
    public static final String NULL_WORLD_READY_TIMEOUT = "NULL_WORLD_READY_TIMEOUT";
    public static final String INVALID_LAUNCH_CONTEXT = "INVALID_LAUNCH_CONTEXT";
    public static final String MISSING_MATCH_FOR_BACKFILL = "MISSING_MATCH_FOR_BACKFILL";
    public static final String BACKFILL_RESERVATION_REJECTED = "BACKFILL_RESERVATION_REJECTED";
    public static final String INSTANCE_TEMPLATE_MISSING = "INSTANCE_TEMPLATE_MISSING";
    public static final String INSTANCE_WORLD_MATERIALIZATION_FAILED = "INSTANCE_WORLD_MATERIALIZATION_FAILED";
    public static final String INSTANCE_WORLD_MISSING = "INSTANCE_WORLD_MISSING";
    public static final String TELEPORT_ISSUE_FAILED = "TELEPORT_ISSUE_FAILED";
    public static final String INSTANCE_READY_TIMEOUT = "INSTANCE_READY_TIMEOUT";
    public static final String WRONG_WORLD_TIMEOUT = "WRONG_WORLD_TIMEOUT";
    public static final String TELEPORT_COMPONENT_STUCK_TIMEOUT = "TELEPORT_COMPONENT_STUCK_TIMEOUT";
    public static final String POSITION_VALIDATION_TIMEOUT = "POSITION_VALIDATION_TIMEOUT";
    public static final String PLAYER_DISCONNECTED_DURING_PLACEMENT = "PLAYER_DISCONNECTED_DURING_PLACEMENT";
    public static final String LAUNCH_CONTEXT_INCONSISTENCY = "LAUNCH_CONTEXT_INCONSISTENCY";
    public static final String PLAYER_NOT_IN_EXPECTED_ROSTER = "PLAYER_NOT_IN_EXPECTED_ROSTER";
    public static final String LATE_INITIAL_ARRIVAL = "LATE_INITIAL_ARRIVAL";
    public static final String INITIAL_PLACEMENT_SHORTFALL = "INITIAL_PLACEMENT_SHORTFALL";
    public static final String INITIAL_PLACEMENT_WINDOW_MISSED = "INITIAL_PLACEMENT_WINDOW_MISSED";

    private MinigameTransferFailureReason() {
    }
}
