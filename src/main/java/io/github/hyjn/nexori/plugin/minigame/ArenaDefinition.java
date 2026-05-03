package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.regex.Pattern;

public record ArenaDefinition(
    String arenaId,
    String displayName,
    String destinationConnectionAddress,
    String destinationTargetId,
    String instanceTemplateId,
    String matchResolutionTriggerId,
    String rulesEngineId,
    int maxSupportedPlayers,
    boolean enabled
) {

    public static final String NO_INSTANCE_TEMPLATE_ID = "none";
    public static final String NO_MATCH_RESOLUTION_TRIGGER_ID = "none";
    public static final int MAX_RULES_ENGINE_ID_LENGTH = 64;
    private static final Pattern RULES_ENGINE_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]*");

    public ArenaDefinition(
        String arenaId,
        String displayName,
        String destinationConnectionAddress,
        String destinationTargetId,
        String instanceTemplateId,
        String matchResolutionTriggerId,
        int maxSupportedPlayers,
        boolean enabled
    ) {
        this(
            arenaId,
            displayName,
            destinationConnectionAddress,
            destinationTargetId,
            instanceTemplateId,
            matchResolutionTriggerId,
            "",
            maxSupportedPlayers,
            enabled
        );
    }

    @Nonnull
    public ArenaDefinition normalized() {
        String normalizedArenaId = normalizeRequired(arenaId, "Arena id cannot be blank.");
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedArenaId);
        String normalizedDestinationAddress = normalizeOptional(destinationConnectionAddress, "");
        String normalizedTargetId = normalizeOptional(destinationTargetId, "");
        return new ArenaDefinition(
            normalizedArenaId,
            normalizedDisplayName,
            normalizedDestinationAddress,
            normalizedTargetId,
            normalizeInstanceTemplateId(instanceTemplateId),
            normalizeTriggerId(matchResolutionTriggerId),
            normalizeRulesEngineId(rulesEngineId),
            maxSupportedPlayers,
            enabled
        );
    }

    public boolean usesInstanceTemplate() {
        return !NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId);
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        return normalizeRequired(rawId, "Arena id cannot be blank.");
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static String normalizeDisplayName(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static String normalizeTriggerId(String rawValue) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            return NO_MATCH_RESOLUTION_TRIGGER_ID;
        }
        return normalized;
    }

    @Nonnull
    public static String normalizeRulesEngineId(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > MAX_RULES_ENGINE_ID_LENGTH) {
            throw new IllegalArgumentException("Rules engine id cannot exceed " + MAX_RULES_ENGINE_ID_LENGTH + " characters.");
        }
        if (!RULES_ENGINE_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Rules engine id can only contain letters, numbers, underscore, dot, or dash.");
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeInstanceTemplateId(String rawValue) {
        if (rawValue == null) {
            return NO_INSTANCE_TEMPLATE_ID;
        }
        String normalized = rawValue.trim();
        if (normalized.isBlank() || NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(normalized)) {
            return NO_INSTANCE_TEMPLATE_ID;
        }
        return normalized;
    }
}
