package io.github.hyjn.nexori.plugin.accessgate;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record NexoriAccessGateConfigDocument(
    int schemaVersion,
    boolean enabled,
    int maxPlayers,
    int reservedPrioritySlots,
    String fullMessage,
    boolean bypassReferralConnections,
    List<String> bypassPlayerUuids,
    List<String> bypassGroupNames,
    List<String> bypassPermissions
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
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
            List.of(),
            List.of(),
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
            normalizeEntries(bypassPlayerUuids, false),
            normalizeEntries(bypassGroupNames, true),
            normalizeEntries(bypassPermissions, true)
        );
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
    private static List<String> normalizeEntries(List<String> rawValues, boolean lowercase) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            if (rawValue == null) {
                continue;
            }
            String value = rawValue.trim();
            if (value.isBlank()) {
                continue;
            }
            if (lowercase) {
                value = value.toLowerCase(Locale.ROOT);
            }
            normalized.add(value);
        }
        return List.copyOf(new ArrayList<>(normalized));
    }
}