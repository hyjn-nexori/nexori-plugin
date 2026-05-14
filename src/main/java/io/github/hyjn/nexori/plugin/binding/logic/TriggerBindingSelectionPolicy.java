package io.github.hyjn.nexori.plugin.binding.logic;

import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingKind;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TriggerBindingSelectionPolicy {

    @Nonnull
    public static List<TriggerBindingDefinition> listPortalCollisionBindings(
        @Nonnull Collection<TriggerBindingDefinition> bindings,
        @Nonnull String portalId
    ) {
        String normalizedSourceId = portalId.trim().toLowerCase(Locale.ROOT);
        return bindings.stream()
            .filter(binding -> binding.triggerKind() == TriggerBindingKind.PORTAL_COLLISION_ENTER)
            .filter(binding -> binding.sourceId().equals(normalizedSourceId))
            .sorted(Comparator.comparingInt(binding -> actionPriority(binding.action())))
            .toList();
    }

    public static int actionPriority(@Nonnull TriggerBindingAction action) {
        return switch (action) {
            case JOIN_QUEUE -> 0;
            case LEAVE_QUEUE -> 1;
            case LOCAL_TARGET -> 2;
            case TRAVEL -> 3;
        };
    }

    public static boolean isQueueAction(@Nonnull TriggerBindingAction action) {
        return action == TriggerBindingAction.JOIN_QUEUE || action == TriggerBindingAction.LEAVE_QUEUE;
    }
}
