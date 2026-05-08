package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NexoriMatchIds {

    private static final int MAX_MATCH_ID_LENGTH = 96;
    private static final Pattern VALID_MATCH_ID = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,94}[a-z0-9])?$");

    private NexoriMatchIds() {
    }

    @Nonnull
    public static String normalizeGeneratedMatchId(@Nonnull String rawMatchId) {
        String normalized = normalizeOptional(rawMatchId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Generated Nexori match id cannot be blank.");
        }
        return normalized;
    }

    @Nonnull
    public static String normalizeBackendOwnedMatchId(String rawMatchId) {
        String normalized = normalizeOptional(rawMatchId);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > MAX_MATCH_ID_LENGTH) {
            throw new IllegalArgumentException("Assignment matchId exceeds the maximum supported length.");
        }
        if (!VALID_MATCH_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Assignment matchId must contain only lowercase letters, digits, and internal hyphens.");
        }
        return normalized;
    }

    @Nonnull
    public static String normalizeRequiredMatchId(@Nonnull String rawMatchId, @Nonnull String message) {
        String normalized = normalizeGeneratedMatchId(rawMatchId);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "" : normalized;
    }
}
