package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerUuidLists {

    private PlayerUuidLists() {
    }

    @Nonnull
    public static List<UUID> canonicalize(Collection<UUID> rawPlayerUuids) {
        if (rawPlayerUuids == null || rawPlayerUuids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        for (UUID playerUuid : rawPlayerUuids) {
            if (playerUuid != null) {
                deduplicated.add(playerUuid);
            }
        }
        List<UUID> ordered = new ArrayList<>(deduplicated);
        ordered.sort(Comparator.comparing(UUID::toString));
        return List.copyOf(ordered);
    }

    public static boolean sameCanonicalPlayers(@Nonnull Collection<UUID> left, @Nonnull Collection<UUID> right) {
        return canonicalize(left).equals(canonicalize(right));
    }

    public static boolean isSubset(@Nonnull Collection<UUID> subset, @Nonnull Collection<UUID> superset) {
        Set<UUID> supersetValues = new LinkedHashSet<>(canonicalize(superset));
        for (UUID value : canonicalize(subset)) {
            if (!supersetValues.contains(value)) {
                return false;
            }
        }
        return true;
    }
}
