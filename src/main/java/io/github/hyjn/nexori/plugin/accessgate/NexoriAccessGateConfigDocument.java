package io.github.hyjn.nexori.plugin.accessgate;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record NexoriAccessGateConfigDocument(
    int schemaVersion,
    boolean enabled,
    int maxPlayers,
    int reservedPrioritySlots,
    String fullMessage,
    boolean bypassReferralConnections,
    List<NexoriAccessGateBypassPlayer> bypassPlayerUuids
) {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int DEFAULT_MAX_PLAYERS = 80;
    public static final int DEFAULT_RESERVED_PRIORITY_SLOTS = 0;
    public static final String DEFAULT_FULL_MESSAGE = "This Nexori server is currently full. Please try again in a moment.";

    @Nonnull
    public static NexoriAccessGateConfigDocument defaults() {
        return new NexoriAccessGateConfigDocument(
            CURRENT_SCHEMA_VERSION,
            false,
            DEFAULT_MAX_PLAYERS,
            DEFAULT_RESERVED_PRIORITY_SLOTS,
            DEFAULT_FULL_MESSAGE,
            true,
            List.of()
        );
    }

    @Nonnull
    public NexoriAccessGateConfigDocument normalized() {
        int normalizedMaxPlayers = Math.max(1, maxPlayers);
        int normalizedReserved = Math.max(0, Math.min(reservedPrioritySlots, normalizedMaxPlayers));
        String normalizedMessage = normalizeRequired(fullMessage, DEFAULT_FULL_MESSAGE);
        return new NexoriAccessGateConfigDocument(
            CURRENT_SCHEMA_VERSION,
            enabled,
            normalizedMaxPlayers,
            normalizedReserved,
            normalizedMessage,
            bypassReferralConnections,
            normalizeBypassPlayers(bypassPlayerUuids)
        );
    }

    public boolean containsBypassUuid(@Nonnull UUID playerUuid) {
        String token = playerUuid.toString().toLowerCase(Locale.ROOT);
        for (NexoriAccessGateBypassPlayer entry : bypassPlayerUuids()) {
            if (entry != null && token.equals(entry.uuid())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static List<NexoriAccessGateBypassPlayer> normalizeBypassPlayers(List<NexoriAccessGateBypassPlayer> rawPlayers) {
        if (rawPlayers == null || rawPlayers.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seenUuids = new LinkedHashSet<>();
        List<NexoriAccessGateBypassPlayer> normalized = new ArrayList<>();
        for (NexoriAccessGateBypassPlayer rawPlayer : rawPlayers) {
            if (rawPlayer == null) {
                continue;
            }
            NexoriAccessGateBypassPlayer entry = rawPlayer.normalized();
            if (entry.uuid().isBlank() || seenUuids.contains(entry.uuid())) {
                continue;
            }
            seenUuids.add(entry.uuid());
            normalized.add(entry);
        }
        return List.copyOf(normalized);
    }
}
