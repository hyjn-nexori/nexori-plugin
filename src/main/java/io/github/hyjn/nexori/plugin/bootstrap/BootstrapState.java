package io.github.hyjn.nexori.plugin.bootstrap;

public record BootstrapState(
    boolean bootstrapOpen,
    String sessionId,
    long sessionExpiresAtEpochMillis,
    long bundleVersion,
    String bundleHash,
    String lastRunMessage,
    boolean lastRunFailed
) {

    public static BootstrapState initial() {
        return new BootstrapState(false, "", 0L, 0L, "", "", false);
    }

    public boolean hasActiveSession() {
        return bootstrapOpen
            && !sessionId.isBlank()
            && sessionExpiresAtEpochMillis > System.currentTimeMillis();
    }
}
