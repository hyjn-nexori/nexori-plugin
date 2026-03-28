package io.github.hyjn.nexori.plugin.secure;

import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;

import javax.annotation.Nonnull;

public interface SecureReferralHandler {

    @Nonnull
    String payloadType();

    void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral);
}
