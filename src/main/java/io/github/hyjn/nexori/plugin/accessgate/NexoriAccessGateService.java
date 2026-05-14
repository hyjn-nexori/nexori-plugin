package io.github.hyjn.nexori.plugin.accessgate;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupDisconnectEvent;
import io.github.hyjn.nexori.plugin.accessgate.logic.AccessGateAdmissionDecider;
import io.github.hyjn.nexori.plugin.accessgate.logic.AccessGateAdmissionDecision;
import io.github.hyjn.nexori.plugin.accessgate.logic.AccessGateBypassPlayerListEditor;
import io.github.hyjn.nexori.plugin.accessgate.logic.AccessGateBypassType;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces Nexori server access caps with optional reserved priority slots.
 * Uses pending setup + connected tracking to avoid oversubscription from simultaneous connects.
 */
public final class NexoriAccessGateService {

    private static final long PENDING_SETUP_TTL_MILLIS = 15_000L;

    private final HytaleLogger logger;
    private final SecureReferralService secureReferralService;
    private final NexoriAccessGateStore store;
    private final AccessGateAdmissionDecider admissionDecider = new AccessGateAdmissionDecider();
    private final AccessGateBypassPlayerListEditor bypassPlayerListEditor = new AccessGateBypassPlayerListEditor();
    private final Map<UUID, Long> pendingSetupConnections = new ConcurrentHashMap<>();
    private final Set<UUID> connectedPlayers = ConcurrentHashMap.newKeySet();
    private volatile NexoriAccessGateConfigDocument config;

    public NexoriAccessGateService(
        @Nonnull HytaleLogger logger,
        @Nonnull SecureReferralService secureReferralService,
        @Nonnull NexoriAccessGateStore store
    ) throws IOException {
        this.logger = logger;
        this.secureReferralService = secureReferralService;
        this.store = store;
        this.config = store.loadOrCreate().normalized();
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument getConfig() {
        return config;
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument reloadConfig() throws IOException {
        this.config = store.loadOrCreate().normalized();
        return this.config;
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument saveConfig(@Nonnull NexoriAccessGateConfigDocument nextConfig) throws IOException {
        NexoriAccessGateConfigDocument normalized = nextConfig.normalized();
        store.save(normalized);
        this.config = normalized;
        return normalized;
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument setEnabled(boolean enabled) throws IOException {
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            enabled,
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
            config.redirectManualConnections(),
            config.manualRedirectAddress(),
            config.bypassPlayerUuids()
        ));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument setBypassReferralConnections(boolean enabled) throws IOException {
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            enabled,
            config.redirectManualConnections(),
            config.manualRedirectAddress(),
            config.bypassPlayerUuids()
        ));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument setRedirectManualConnections(boolean enabled) throws IOException {
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
            enabled,
            config.manualRedirectAddress(),
            config.bypassPlayerUuids()
        ));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument addBypassPlayerUuid(@Nonnull UUID playerUuid) throws IOException {
        return addBypassPlayerUuid(playerUuid, "");
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument addBypassPlayerUuid(@Nonnull UUID playerUuid, @Nonnull String username) throws IOException {
        List<NexoriAccessGateBypassPlayer> updatedPlayers = bypassPlayerListEditor.addBypassPlayerUuid(
            config.bypassPlayerUuids(),
            playerUuid,
            username
        );
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
            config.redirectManualConnections(),
            config.manualRedirectAddress(),
            List.copyOf(updatedPlayers)
        ));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument removeBypassPlayerUuid(@Nonnull UUID playerUuid) throws IOException {
        return removeBypassPlayerToken(playerUuid.toString().toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument removeBypassPlayerToken(@Nonnull String token) throws IOException {
        List<NexoriAccessGateBypassPlayer> updatedPlayers = bypassPlayerListEditor.removeBypassPlayerToken(
            config.bypassPlayerUuids(),
            token
        );
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
            config.redirectManualConnections(),
            config.manualRedirectAddress(),
            List.copyOf(updatedPlayers)
        ));
    }

    @Nonnull
    public synchronized List<UUID> connectedPlayerUuids() {
        return List.copyOf(new ArrayList<>(connectedPlayers));
    }

    public synchronized void handlePlayerSetupConnect(@Nonnull PlayerSetupConnectEvent event) {
        long now = System.currentTimeMillis();
        pruneExpiredPending(now);
        UUID playerUuid = event.getUuid();
        if (event.isCancelled()) {
            clearTracking(playerUuid);
            return;
        }

        NexoriAccessGateConfigDocument currentConfig = config;
        if (currentConfig.redirectManualConnections() && !event.isReferralConnection()) {
            if (currentConfig.manualRedirectAddress().isBlank()) {
                deny(event, playerUuid, "This server only accepts Nexori travel/referral connections.");
                logger.atWarning().log("NEXORI_ACCESS_GATE manual_redirect_enabled_but_missing_address player=" + event.getUsername());
                return;
            }
            try {
                ConfiguredPeer redirectPeer = ConfiguredPeer.parse(currentConfig.manualRedirectAddress());
                event.referToServer(redirectPeer.host(), redirectPeer.port(), new byte[0]);
                clearTracking(playerUuid);
                logger.atInfo().log("NEXORI_ACCESS_GATE redirected_manual_join player=" + event.getUsername()
                    + " redirect=" + redirectPeer.connectionAddress());
                return;
            } catch (Exception exception) {
                deny(event, playerUuid, "This server only accepts Nexori travel/referral connections.");
                logger.atWarning().withCause(exception).log(
                    "NEXORI_ACCESS_GATE invalid_manual_redirect_address=" + currentConfig.manualRedirectAddress()
                );
                return;
            }
        }

        int occupancy = pendingSetupConnections.size() + connectedPlayers.size();
        AccessGateBypassType bypassType = resolveBypass(event, currentConfig);
        AccessGateAdmissionDecision admissionDecision = admissionDecider.decide(currentConfig, occupancy, bypassType);
        int maxPlayers = admissionDecision.maxPlayers();
        int publicCap = admissionDecision.publicCap();

        if (admissionDecision.outcome() == AccessGateAdmissionDecision.Outcome.DENY_HARD_CAP) {
            deny(event, playerUuid, admissionDecision.message());
            logger.atInfo().log("NEXORI_ACCESS_GATE denied_hard_cap player=" + event.getUsername()
                + " occupancy=" + occupancy + " maxPlayers=" + maxPlayers);
            return;
        }

        if (admissionDecision.outcome() == AccessGateAdmissionDecision.Outcome.DENY_PUBLIC_CAP) {
            deny(event, playerUuid, admissionDecision.message());
            logger.atInfo().log("NEXORI_ACCESS_GATE denied_public_cap player=" + event.getUsername()
                + " occupancy=" + occupancy + " publicCap=" + publicCap + " maxPlayers=" + maxPlayers);
            return;
        }

        if (admissionDecision.shouldTrackPending()) {
            pendingSetupConnections.put(playerUuid, now);
        }
        if (!currentConfig.enabled()) {
            return;
        }
        if (bypassType == AccessGateBypassType.TRUSTED_REFERRAL) {
            logger.atInfo().log("NEXORI_ACCESS_GATE accepted_trusted_referral_bypass player=" + event.getUsername()
                + " occupancy=" + occupancy + " publicCap=" + publicCap + " maxPlayers=" + maxPlayers);
            return;
        }
        if (bypassType != AccessGateBypassType.NONE) {
            logger.atInfo().log("NEXORI_ACCESS_GATE accepted_bypass player=" + event.getUsername()
                + " bypassType=" + bypassType.name().toLowerCase(Locale.ROOT)
                + " occupancy=" + occupancy + " publicCap=" + publicCap + " maxPlayers=" + maxPlayers);
            return;
        }
        logger.atInfo().log("NEXORI_ACCESS_GATE accepted_normal player=" + event.getUsername()
            + " occupancy=" + occupancy + " publicCap=" + publicCap + " maxPlayers=" + maxPlayers);
    }

    public synchronized void handlePlayerConnect(@Nonnull PlayerConnectEvent event) {
        if (event.getPlayerRef() == null) {
            return;
        }
        UUID playerUuid = event.getPlayerRef().getUuid();
        pendingSetupConnections.remove(playerUuid);
        connectedPlayers.add(playerUuid);
    }

    public synchronized void handlePlayerSetupDisconnect(@Nonnull PlayerSetupDisconnectEvent event) {
        clearTracking(event.getUuid());
    }

    public synchronized void handlePlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        if (event.getPlayerRef() == null) {
            return;
        }
        clearTracking(event.getPlayerRef().getUuid());
    }

    @Nonnull
    private AccessGateBypassType resolveBypass(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull NexoriAccessGateConfigDocument currentConfig
    ) {
        if (currentConfig.bypassReferralConnections() && secureReferralService.isTrustedNexoriReferral(event)) {
            return AccessGateBypassType.TRUSTED_REFERRAL;
        }

        if (currentConfig.containsBypassUuid(event.getUuid())) {
            return AccessGateBypassType.UUID;
        }

        return AccessGateBypassType.NONE;
    }

    private void deny(@Nonnull PlayerSetupConnectEvent event, @Nonnull UUID playerUuid, @Nonnull String fullMessage) {
        clearTracking(playerUuid);
        event.setCancelled(true);
        event.setReason(Message.raw(fullMessage));
    }

    private void clearTracking(@Nonnull UUID playerUuid) {
        pendingSetupConnections.remove(playerUuid);
        connectedPlayers.remove(playerUuid);
    }

    private void pruneExpiredPending(long nowEpochMillis) {
        int removed = 0;
        for (Map.Entry<UUID, Long> entry : pendingSetupConnections.entrySet()) {
            Long acceptedAt = entry.getValue();
            if (acceptedAt == null || (nowEpochMillis - acceptedAt) > PENDING_SETUP_TTL_MILLIS) {
                if (pendingSetupConnections.remove(entry.getKey(), acceptedAt)) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            logger.atWarning().log(
                "NEXORI_ACCESS_GATE cleaned_expired_pending count=" + removed + " ttlMillis=" + PENDING_SETUP_TTL_MILLIS
            );
        }
    }

}
