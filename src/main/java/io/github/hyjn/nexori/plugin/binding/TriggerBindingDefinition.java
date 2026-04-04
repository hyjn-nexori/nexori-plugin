package io.github.hyjn.nexori.plugin.binding;

import javax.annotation.Nonnull;
import java.util.Locale;

public record TriggerBindingDefinition(
    String id,
    TriggerBindingKind triggerKind,
    String sourceId,
    TriggerBindingAction action,
    String queueId,
    String destinationConnectionAddress,
    String destinationTargetId,
    String travelProfileId,
    String contextJson,
    boolean enabled
) {

    @Nonnull
    public TriggerBindingDefinition normalized() {
        TriggerBindingKind normalizedKind = triggerKind == null ? TriggerBindingKind.PORTAL_COLLISION_ENTER : triggerKind;
        TriggerBindingAction normalizedAction = action == null ? TriggerBindingAction.TRAVEL : action;
        String normalizedSourceId = normalizeRequired(sourceId, "Trigger binding source id cannot be blank.");
        String normalizedId = normalizeOptional(
            id,
            normalizedKind.name().toLowerCase(Locale.ROOT) + "." + normalizedSourceId
        );
        if (normalizedAction == TriggerBindingAction.JOIN_QUEUE || normalizedAction == TriggerBindingAction.LEAVE_QUEUE) {
            return new TriggerBindingDefinition(
                normalizedId,
                normalizedKind,
                normalizedSourceId,
                normalizedAction,
                normalizeRequired(queueId, "Queue trigger bindings require a queue id."),
                "",
                "",
                "",
                "{}",
                enabled
            );
        }

        if (normalizedAction == TriggerBindingAction.LOCAL_TARGET) {
            return new TriggerBindingDefinition(
                normalizedId,
                normalizedKind,
                normalizedSourceId,
                normalizedAction,
                "",
                "",
                normalizeRequired(destinationTargetId, "Local target trigger bindings require a destination target id."),
                "",
                "{}",
                enabled
            );
        }

        return new TriggerBindingDefinition(
            normalizedId,
            normalizedKind,
            normalizedSourceId,
            normalizedAction,
            "",
            normalizeOptional(destinationConnectionAddress, ""),
            normalizeOptional(destinationTargetId, ""),
            normalizeOptional(travelProfileId, ""),
            normalizeContextJson(contextJson),
            enabled
        );
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        return normalizeRequired(rawId, "Trigger binding id cannot be blank.");
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
    private static String normalizeContextJson(String rawValue) {
        if (rawValue == null) {
            return "{}";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "{}" : normalized;
    }
}
