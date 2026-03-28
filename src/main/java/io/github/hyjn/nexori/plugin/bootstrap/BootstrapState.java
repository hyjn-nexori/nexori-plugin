package io.github.hyjn.nexori.plugin.bootstrap;

public record BootstrapState(
    boolean bootstrapOpen,
    String sessionId,
    long sessionExpiresAtEpochMillis,
    long bundleVersion,
    String bundleHash
) {

    public static BootstrapState initial() {
        return new BootstrapState(false, "", 0L, 0L, "");
    }

    public boolean hasActiveSession() {
        return bootstrapOpen
            && !sessionId.isBlank()
            && sessionExpiresAtEpochMillis > System.currentTimeMillis();
    }
}
