package io.github.hyjn.nexori.plugin.accessgate.logic;

import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateBypassPlayer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Applies pure add/remove edits to the Access Gate bypass player list.
 */
public final class AccessGateBypassPlayerListEditor {

    @Nonnull
    public List<NexoriAccessGateBypassPlayer> addBypassPlayerUuid(
        List<NexoriAccessGateBypassPlayer> currentPlayers,
        @Nonnull UUID playerUuid,
        @Nonnull String username
    ) {
        String token = playerUuid.toString().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> bypassPlayers = normalizedPlayersByUuid(currentPlayers);
        String normalizedUsername = username.trim();
        if (!bypassPlayers.containsKey(token) || !normalizedUsername.isBlank()) {
            bypassPlayers.put(token, normalizedUsername);
        }
        return toList(bypassPlayers);
    }

    @Nonnull
    public List<NexoriAccessGateBypassPlayer> removeBypassPlayerUuid(
        List<NexoriAccessGateBypassPlayer> currentPlayers,
        @Nonnull UUID playerUuid
    ) {
        return removeBypassPlayerToken(currentPlayers, playerUuid.toString());
    }

    @Nonnull
    public List<NexoriAccessGateBypassPlayer> removeBypassPlayerToken(
        List<NexoriAccessGateBypassPlayer> currentPlayers,
        @Nonnull String token
    ) {
        String normalizedToken = token.trim().toLowerCase(Locale.ROOT);
        LinkedHashMap<String, String> bypassPlayers = normalizedPlayersByUuid(currentPlayers);
        bypassPlayers.remove(normalizedToken);
        return toList(bypassPlayers);
    }

    @Nonnull
    private static LinkedHashMap<String, String> normalizedPlayersByUuid(List<NexoriAccessGateBypassPlayer> currentPlayers) {
        LinkedHashMap<String, String> bypassPlayers = new LinkedHashMap<>();
        if (currentPlayers == null || currentPlayers.isEmpty()) {
            return bypassPlayers;
        }
        for (NexoriAccessGateBypassPlayer rawPlayer : currentPlayers) {
            if (rawPlayer == null) {
                continue;
            }
            NexoriAccessGateBypassPlayer entry = rawPlayer.normalized();
            if (entry.uuid().isBlank() || bypassPlayers.containsKey(entry.uuid())) {
                continue;
            }
            bypassPlayers.put(entry.uuid(), entry.username());
        }
        return bypassPlayers;
    }

    @Nonnull
    private static List<NexoriAccessGateBypassPlayer> toList(@Nonnull LinkedHashMap<String, String> bypassPlayers) {
        List<NexoriAccessGateBypassPlayer> updatedPlayers = new ArrayList<>();
        for (Map.Entry<String, String> entry : bypassPlayers.entrySet()) {
            updatedPlayers.add(new NexoriAccessGateBypassPlayer(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(updatedPlayers);
    }
}
