package io.github.hyjn.nexori.plugin.accessgate;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupDisconnectEvent;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            config.bypassPlayerUuids()
        ));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument addBypassPlayerUuid(@Nonnull UUID playerUuid) throws IOException {
        return addBypassPlayerUuid(playerUuid, "");
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument addBypassPlayerUuid(@Nonnull UUID playerUuid, @Nonnull String username) throws IOException {
        String token = playerUuid.toString().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> bypassPlayers = new LinkedHashMap<>();
        for (NexoriAccessGateBypassPlayer entry : config.bypassPlayerUuids()) {
            if (entry == null || entry.uuid() == null || entry.uuid().isBlank()) {
                continue;
            }
            bypassPlayers.put(entry.uuid(), entry.username() == null ? "" : entry.username());
        }
        String normalizedUsername = username.trim();
        if (!bypassPlayers.containsKey(token) || !normalizedUsername.isBlank()) {
            bypassPlayers.put(token, normalizedUsername);
        }

        List<NexoriAccessGateBypassPlayer> updatedPlayers = new ArrayList<>();
        for (Map.Entry<String, String> entry : bypassPlayers.entrySet()) {
            updatedPlayers.add(new NexoriAccessGateBypassPlayer(entry.getKey(), entry.getValue()));
        }
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
            List.copyOf(updatedPlayers)
        ));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument removeBypassPlayerUuid(@Nonnull UUID playerUuid) throws IOException {
        return removeBypassPlayerToken(playerUuid.toString().toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument removeBypassPlayerToken(@Nonnull String token) throws IOException {
        String normalizedToken = token.trim().toLowerCase(Locale.ROOT);
        List<NexoriAccessGateBypassPlayer> updatedPlayers = new ArrayList<>();
        for (NexoriAccessGateBypassPlayer entry : config.bypassPlayerUuids()) {
            if (entry == null || entry.uuid() == null) {
                continue;
            }
            if (entry.uuid().trim().toLowerCase(Locale.ROOT).equals(normalizedToken)) {
                continue;
            }
            updatedPlayers.add(entry);
        }
        return saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
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
        int occupancy = pendingSetupConnections.size() + connectedPlayers.size();
        int maxPlayers = Math.max(1, currentConfig.maxPlayers());
        int publicCap = Math.max(0, maxPlayers - currentConfig.reservedPrioritySlots());
        BypassType bypassType = resolveBypass(event, currentConfig);
        boolean bypass = bypassType != BypassType.NONE;

        if (currentConfig.enabled() && occupancy >= maxPlayers) {
            deny(event, playerUuid, currentConfig.fullMessage());
            logger.atInfo().log("NEXORI_ACCESS_GATE denied_hard_cap player=" + event.getUsername()
                + " occupancy=" + occupancy + " maxPlayers=" + maxPlayers);
            return;
        }

        if (currentConfig.enabled() && !bypass && occupancy >= publicCap) {
            deny(event, playerUuid, currentConfig.fullMessage());
            logger.atInfo().log("NEXORI_ACCESS_GATE denied_public_cap player=" + event.getUsername()
                + " occupancy=" + occupancy + " publicCap=" + publicCap + " maxPlayers=" + maxPlayers);
            return;
        }

        pendingSetupConnections.put(playerUuid, now);
        if (!currentConfig.enabled()) {
            return;
        }
        if (bypassType == BypassType.TRUSTED_REFERRAL) {
            logger.atInfo().log("NEXORI_ACCESS_GATE accepted_trusted_referral_bypass player=" + event.getUsername()
                + " occupancy=" + occupancy + " publicCap=" + publicCap + " maxPlayers=" + maxPlayers);
            return;
        }
        if (bypass) {
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
    private BypassType resolveBypass(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull NexoriAccessGateConfigDocument currentConfig
    ) {
        if (currentConfig.bypassReferralConnections() && secureReferralService.isTrustedNexoriReferral(event)) {
            return BypassType.TRUSTED_REFERRAL;
        }

        if (currentConfig.containsBypassUuid(event.getUuid())) {
            return BypassType.UUID;
        }

        return BypassType.NONE;
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

    private enum BypassType {
        NONE,
        UUID,
        TRUSTED_REFERRAL
    }
}
