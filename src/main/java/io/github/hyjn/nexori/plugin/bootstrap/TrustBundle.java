package io.github.hyjn.nexori.plugin.bootstrap;

import java.util.List;

public record TrustBundle(
    long bundleVersion,
    String bundleHash,
    long updatedAtEpochMillis,
    List<BundleMember> members
) {

    public static TrustBundle initial() {
        return new TrustBundle(0L, "", 0L, List.of());
    }
}
