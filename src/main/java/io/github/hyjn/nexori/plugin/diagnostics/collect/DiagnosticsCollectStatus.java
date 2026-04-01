package io.github.hyjn.nexori.plugin.diagnostics.collect;

public enum DiagnosticsCollectStatus {
    PENDING,
    PLANNING,
    READY,
    RUNNING,
    READY_TO_CONSOLIDATE,
    CONSOLIDATING,
    COMPLETED,
    FAILED,
    CANCELLED
}
