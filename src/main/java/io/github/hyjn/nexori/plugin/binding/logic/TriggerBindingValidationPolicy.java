package io.github.hyjn.nexori.plugin.binding.logic;

import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingKind;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;

public final class TriggerBindingValidationPolicy {

    @Nonnull
    public static TriggerBindingDefinition normalizeAndValidate(@Nonnull TriggerBindingDefinition definition) {
        TriggerBindingDefinition normalized = definition.normalized();
        if (normalized.action() == TriggerBindingAction.JOIN_QUEUE || normalized.action() == TriggerBindingAction.LEAVE_QUEUE) {
            if (normalized.action() == TriggerBindingAction.JOIN_QUEUE && normalized.queueId().isBlank()) {
                throw new IllegalArgumentException("Join-queue trigger bindings require a queue id.");
            }
            return normalized;
        }

        if (normalized.action() == TriggerBindingAction.LOCAL_TARGET) {
            if (normalized.destinationTargetId().isBlank()) {
                throw new IllegalArgumentException("Local target trigger bindings require a destination target id.");
            }
            return new TriggerBindingDefinition(
                normalized.id(),
                normalized.triggerKind(),
                normalized.sourceId(),
                TriggerBindingAction.LOCAL_TARGET,
                "",
                "",
                normalized.destinationTargetId(),
                "",
                "{}",
                normalized.enabled()
            );
        }

        ConfiguredPeer destination = ConfiguredPeer.parse(normalized.destinationConnectionAddress());
        if (normalized.destinationTargetId().isBlank()) {
            throw new IllegalArgumentException("Travel trigger bindings require a destination target id.");
        }
        TravelProfileType profile = TravelProfileType.parse(normalized.travelProfileId());
        return new TriggerBindingDefinition(
            normalized.id(),
            normalized.triggerKind(),
            normalized.sourceId(),
            TriggerBindingAction.TRAVEL,
            "",
            destination.connectionAddress(),
            normalized.destinationTargetId(),
            profile.id(),
            normalized.contextJson(),
            normalized.enabled()
        );
    }
}
