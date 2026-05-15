package io.github.hyjn.nexori.plugin.discovery.logic;

import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;

import javax.annotation.Nonnull;
import java.util.List;

public final class DestinationTargetSyncApplyPlanner {

    private DestinationTargetSyncApplyPlanner() {
    }

    @Nonnull
    public static List<DiscoveredDestinationTargetSet> filterDiscoveries(
        List<DiscoveredDestinationTargetSet> rawDiscoveries,
        @Nonnull String localConnectionAddress,
        @Nonnull String localServerId
    ) {
        return (rawDiscoveries == null ? List.<DiscoveredDestinationTargetSet>of() : rawDiscoveries).stream()
            .filter(d -> d != null && d.connectionAddress() != null && !d.connectionAddress().isBlank())
            .map(DiscoveredDestinationTargetSet::normalized)
            .filter(d -> (localConnectionAddress.isBlank() || !localConnectionAddress.equalsIgnoreCase(d.connectionAddress()))
                && (d.remoteServerId() == null || !localServerId.equals(d.remoteServerId().trim())))
            .toList();
    }

    @Nonnull
    public static String successMessage(
        @Nonnull String localConnectionAddress,
        @Nonnull String issuerConnectionAddress
    ) {
        String address = localConnectionAddress.isBlank() ? issuerConnectionAddress : localConnectionAddress;
        return "Synchronized portal and target info on " + address + ".";
    }

    @Nonnull
    public static String failureMessage(
        @Nonnull String issuerConnectionAddress,
        @Nonnull String exceptionMessage
    ) {
        return "Could not synchronize portal info on " + issuerConnectionAddress + ": " + exceptionMessage;
    }
}
