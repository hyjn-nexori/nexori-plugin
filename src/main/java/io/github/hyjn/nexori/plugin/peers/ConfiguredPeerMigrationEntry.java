package io.github.hyjn.nexori.plugin.peers;

import javax.annotation.Nonnull;

public record ConfiguredPeerMigrationEntry(
    String oldConnectionAddress,
    String newConnectionAddress
) {

    @Nonnull
    public ConfiguredPeerMigrationEntry normalized() {
        return new ConfiguredPeerMigrationEntry(
            normalize(oldConnectionAddress),
            normalize(newConnectionAddress)
        );
    }

    public boolean hasAnyChange() {
        return !oldConnectionAddress.equals(newConnectionAddress);
    }

    @Nonnull
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
