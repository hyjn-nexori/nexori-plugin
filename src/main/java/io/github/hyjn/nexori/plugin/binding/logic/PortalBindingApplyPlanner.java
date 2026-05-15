package io.github.hyjn.nexori.plugin.binding.logic;

import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;

public final class PortalBindingApplyPlanner {

    private PortalBindingApplyPlanner() {
    }

    public static boolean isLocalTarget(
        String destinationConnectionAddress,
        @Nonnull String localConnectionAddress
    ) {
        return destinationConnectionAddress == null
            || destinationConnectionAddress.isBlank()
            || (!localConnectionAddress.isBlank() && destinationConnectionAddress.equalsIgnoreCase(localConnectionAddress));
    }

    @Nonnull
    public static String effectiveTravelProfileId(String rawTravelProfileId) {
        return rawTravelProfileId == null || rawTravelProfileId.isBlank()
            ? TravelProfileType.KEEP_INVENTORY.id()
            : rawTravelProfileId;
    }

    @Nonnull
    public static String effectiveContextJson(String rawContextJson) {
        return rawContextJson == null || rawContextJson.isBlank() ? "{}" : rawContextJson;
    }

    @Nonnull
    public static String successMessage(
        @Nonnull String localConnectionAddress,
        @Nonnull String issuerConnectionAddress
    ) {
        String address = localConnectionAddress.isBlank() ? issuerConnectionAddress : localConnectionAddress;
        return "Applied portal binding on " + address + ".";
    }

    @Nonnull
    public static String failureMessage(
        @Nonnull String issuerConnectionAddress,
        @Nonnull String exceptionMessage
    ) {
        return "Could not apply portal binding on " + issuerConnectionAddress + ": " + exceptionMessage;
    }
}
