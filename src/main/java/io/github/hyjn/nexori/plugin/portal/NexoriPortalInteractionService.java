package io.github.hyjn.nexori.plugin.portal;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3i;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.access.NexoriAdminAccess;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.ui.NexoriPortalQuickEditPage;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;
import io.github.hyjn.nexori.plugin.target.WorldSpawnResolver;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NexoriPortalInteractionService {

    private static final Duration MINIMUM_TIME_IN_WORLD_BEFORE_PORTAL_TRAVEL = Duration.ofMillis(3000L);

    private final NexoriPlugin plugin;
    private final HytaleLogger logger;
    private final PortalInstanceService portalInstanceService;
    private final TriggerBindingService triggerBindingService;
    private final QueueCoordinatorService queueCoordinatorService;
    private final SecureTravelService secureTravelService;
    private final String adminPermission;
    private final DiagnosticsService diagnosticsService;
    private final Map<UUID, Long> lastCollisionHandledAtByPlayer = new ConcurrentHashMap<>();

    public NexoriPortalInteractionService(
        @Nonnull NexoriPlugin plugin,
        @Nonnull HytaleLogger logger,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull QueueCoordinatorService queueCoordinatorService,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull String adminPermission,
        @Nonnull DiagnosticsService diagnosticsService
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        this.queueCoordinatorService = queueCoordinatorService;
        this.secureTravelService = secureTravelService;
        this.adminPermission = adminPermission;
        this.diagnosticsService = diagnosticsService;
    }

    public void registerPageSupplier() {
        OpenCustomUIInteraction.registerCustomPageSupplier(
            plugin,
            plugin.getClass(),
            NexoriPortalIds.ADMIN_PAGE_ID,
            this::tryCreateAdminPortalPage
        );
        OpenCustomUIInteraction.registerCustomPageSupplier(
            plugin,
            plugin.getClass(),
            NexoriPortalIds.TRAVERSE_PAGE_ID,
            this::tryHandlePortalTraverse
        );
    }

    public void openAdminPortalPage(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String statusText
    ) {
        NexoriPortalQuickEditPage.open(ref, store, playerRef, player, plugin, portal, statusText);
    }

    private InteractiveCustomUIPage<?> tryCreateAdminPortalPage(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return null;
        }

        if (!NexoriAdminAccess.canManage(playerRef, player, adminPermission)) {
            return null;
        }

        BlockPosition targetBlock = context.getTargetBlock();
        Vector3i blockPosition = targetBlock == null
            ? new Vector3i(0, 0, 0)
            : new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z);

        Optional<PortalInstanceDefinition> portal = resolvePortal(player.getWorld().getName(), blockPosition);
        if (portal.isEmpty()) {
            return null;
        }

        openAdminPortalPage(ref, ref.getStore(), playerRef, player, portal.get(), "");
        return null;
    }

    private InteractiveCustomUIPage<?> tryHandlePortalTraverse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull PlayerRef playerRef,
        @Nonnull InteractionContext context
    ) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return null;
        }

        if (player.getSinceLastSpawnNanos() < MINIMUM_TIME_IN_WORLD_BEFORE_PORTAL_TRAVEL.toNanos()) {
            return null;
        }

        BlockPosition targetBlock = context.getTargetBlock();
        Vector3i blockPosition = targetBlock == null
            ? new Vector3i(0, 0, 0)
            : new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z);

        Optional<PortalInstanceDefinition> portal = resolvePortal(player.getWorld().getName(), blockPosition);
        if (portal.isEmpty()) {
            return null;
        }

        if (shouldSuppressRecentArrivalTravel(playerRef.getUuid(), portal.get())) {
            return null;
        }

        if (!shouldHandleTrigger(playerRef.getUuid())) {
            return null;
        }

        triggerPortalTravel(ref, player, playerRef, portal);
        return null;
    }

    private boolean shouldHandleCollision(@Nonnull UUID playerUuid) {
        long now = System.currentTimeMillis();
        Long lastHandled = lastCollisionHandledAtByPlayer.get(playerUuid);
        if (lastHandled != null && now - lastHandled < 1500L) {
            return false;
        }
        lastCollisionHandledAtByPlayer.put(playerUuid, now);
        return true;
    }

    private boolean shouldHandleTrigger(@Nonnull UUID playerUuid) {
        return shouldHandleCollision(playerUuid);
    }

    private boolean shouldSuppressRecentArrivalTravel(@Nonnull UUID playerUuid, @Nonnull PortalInstanceDefinition portal) {
        String autoDestinationTargetId = portal.autoDestinationTargetId();
        if (autoDestinationTargetId == null || autoDestinationTargetId.isBlank()) {
            return false;
        }
        return secureTravelService.shouldSuppressPortalTravel(playerUuid, autoDestinationTargetId);
    }

    @Nonnull
    private Optional<PortalInstanceDefinition> resolvePortal(
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition
    ) {
        Optional<PortalInstanceDefinition> portal = portalInstanceService.findByLocation(worldName, blockPosition);
        if (portal.isPresent()) {
            return portal;
        }
        return portalInstanceService.findNearestByLocation(worldName, blockPosition, 1, 1);
    }

    private void triggerPortalTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull Optional<PortalInstanceDefinition> portal
    ) {
        if (portal.isEmpty() || !portal.get().enabled()) {
            return;
        }

        List<TriggerBindingDefinition> bindings = triggerBindingService.listPortalCollisionBindings(portal.get().portalId()).stream()
            .filter(TriggerBindingDefinition::enabled)
            .toList();
        if (bindings.isEmpty()) {
            return;
        }

        for (TriggerBindingDefinition binding : bindings) {
            switch (binding.action()) {
                case JOIN_QUEUE -> {
                    if (!joinQueue(player, playerRef, portal.get(), binding)) {
                        return;
                    }
                    continue;
                }
                case LEAVE_QUEUE -> {
                    if (!leaveQueue(player, playerRef, binding)) {
                        return;
                    }
                    continue;
                }
                case LOCAL_TARGET -> {
                    if (!triggerLocalTargetTravel(ref, player, playerRef, binding)) {
                        return;
                    }
                    continue;
                }
                case TRAVEL -> {
                    if (blockQueuedRemoteTravel(player, playerRef)) {
                        return;
                    }
                }
            }

            try {
                String operationId = diagnosticsService.newOperationId("travel");
                secureTravelService.travel(
                    playerRef,
                    ConfiguredPeer.parse(binding.destinationConnectionAddress()),
                    binding.destinationTargetId(),
                    "",
                    binding.travelProfileId(),
                    binding.contextJson(),
                    operationId
                );
                diagnosticsService.record(
                    DiagnosticsCategory.TRAVEL,
                    DiagnosticsAction.TRAVEL_PORTAL_TRIGGER,
                    DiagnosticsOutcome.SUCCEEDED,
                    DiagnosticsReasonClass.NORMAL,
                    DiagnosticsReasonCode.PORTAL_TRIGGER_DISPATCHED,
                    "Triggered secure portal travel from a Nexori portal.",
                    operationId,
                    event -> event
                        .playerUuid(playerRef.getUuid().toString())
                        .playerNameClaimed(playerRef.getUsername())
                        .portalId(portal.get().portalId())
                        .portalDisplayName(portal.get().displayName())
                        .bindingId(binding.id())
                        .targetId(binding.destinationTargetId())
                        .travelProfileId(binding.travelProfileId())
                        .remoteConnectionAddress(binding.destinationConnectionAddress())
                );
            } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                String operationId = diagnosticsService.newOperationId("travel");
                diagnosticsService.record(
                    DiagnosticsCategory.TRAVEL,
                    DiagnosticsAction.TRAVEL_PORTAL_TRIGGER,
                    DiagnosticsOutcome.FAILED,
                    DiagnosticsReasonClass.IO,
                    DiagnosticsReasonCode.PORTAL_TRIGGER_FAILED,
                    "This Nexori portal could not start its secure travel: " + exception.getMessage(),
                    operationId,
                    event -> event
                        .playerUuid(playerRef.getUuid().toString())
                        .playerNameClaimed(playerRef.getUsername())
                        .portalId(portal.get().portalId())
                        .portalDisplayName(portal.get().displayName())
                        .bindingId(binding.id())
                        .targetId(binding.destinationTargetId())
                        .travelProfileId(binding.travelProfileId())
                        .remoteConnectionAddress(binding.destinationConnectionAddress())
                );
                playerRef.sendMessage(Message.raw("This Nexori portal could not start its secure travel: " + exception.getMessage()));
                logger.atWarning().withCause(exception).log("Failed to trigger secure travel from portal " + portal.get().portalId());
            }
            return;
        }
    }

    private boolean blockQueuedRemoteTravel(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {
        String currentQueueId = queueCoordinatorService.findQueuedQueueId(playerRef.getUuid()).orElse("");
        if (currentQueueId.isBlank()) {
            return false;
        }
        playerRef.sendMessage(Message.raw(
            "You are currently in Nexori queue " + currentQueueId + ". Leave the queue before using a cross-server portal."
        ));
        return true;
    }

    private boolean leaveQueue(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull TriggerBindingDefinition binding
    ) {
        String currentQueueId = queueCoordinatorService.findQueuedQueueId(playerRef.getUuid()).orElse("");
        if (currentQueueId.isBlank()) {
            playerRef.sendMessage(Message.raw("You are not currently in a Nexori queue."));
            return false;
        }
        if (!binding.queueId().isBlank() && !currentQueueId.equals(binding.queueId())) {
            playerRef.sendMessage(Message.raw(
                "This portal leaves Nexori queue " + binding.queueId() + ", but you are currently in " + currentQueueId + "."
            ));
            return false;
        }

        QueueCoordinatorService.LeaveResult result = queueCoordinatorService.leaveCurrentQueue(playerRef.getUuid());
        if (result.outcome() == QueueCoordinatorService.LeaveOutcome.LEFT) {
            playerRef.sendMessage(Message.raw("Left Nexori queue " + result.queueId() + "."));
            return true;
        }
        playerRef.sendMessage(Message.raw("You are not currently in a Nexori queue."));
        return false;
    }

    private boolean triggerLocalTargetTravel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull TriggerBindingDefinition binding
    ) {
        ResolvedDestinationTarget resolvedTarget = plugin.getDestinationTargetService()
            .resolve(binding.destinationTargetId(), "")
            .orElse(null);
        if (resolvedTarget == null) {
            playerRef.sendMessage(Message.raw(
                "This Nexori portal points to local target '" + binding.destinationTargetId() + "', but that target does not exist."
            ));
            return false;
        }

        Transform transform = resolveLocalTargetTransform(resolvedTarget, playerRef.getUuid());
        if (transform == null) {
            playerRef.sendMessage(Message.raw(
                "This Nexori portal could not resolve its local target transform."
            ));
            return false;
        }

        World targetWorld = Universe.get().getWorld(resolvedTarget.effectiveWorldName());
        Teleport teleport = targetWorld == null
            ? Teleport.createForPlayer(transform.clone())
            : Teleport.createForPlayer(targetWorld, transform.clone());
        World currentWorld = player.getWorld();
        if (currentWorld == null) {
            playerRef.sendMessage(Message.raw("This Nexori portal could not find your current world for local teleport."));
            return false;
        }
        currentWorld.execute(() -> ref.getStore().addComponent(ref, Teleport.getComponentType(), teleport));
        playerRef.sendMessage(Message.raw("Teleported to Nexori target " + binding.destinationTargetId() + "."));
        return true;
    }

    private boolean joinQueue(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull TriggerBindingDefinition binding
    ) {
        QueueCoordinatorService.JoinResult result = queueCoordinatorService.joinQueue(
            playerRef.getUuid(),
            playerRef.getUsername(),
            binding.queueId(),
            portal.worldName(),
            portal.portalId()
        );
        switch (result.outcome()) {
            case JOINED -> {
                int waiting = result.state().waitingMembers().size();
                int ready = result.state().readyMembers().size();
                String suffix = result.state().phase() == io.github.hyjn.nexori.plugin.minigame.QueuePhase.COUNTDOWN
                    ? " countdown running."
                    : (result.state().phase() == io.github.hyjn.nexori.plugin.minigame.QueuePhase.READY
                        ? " ready batch pending."
                        : "");
                playerRef.sendMessage(Message.raw(
                    "Joined Nexori queue " + result.state().queueId()
                        + ". waiting=" + waiting
                        + " ready=" + ready
                        + "." + suffix
                ));
                return true;
            }
            case ALREADY_QUEUED -> {
                playerRef.sendMessage(Message.raw(
                    "You are already in Nexori queue " + result.existingQueueId() + "."
                ));
                return result.existingQueueId().equals(binding.queueId());
            }
            case QUEUE_MISSING -> {
                playerRef.sendMessage(Message.raw(
                    "This Nexori portal points to queue '" + binding.queueId() + "', but that queue does not exist."
                ));
                return false;
            }
            case QUEUE_DISABLED -> {
                playerRef.sendMessage(Message.raw(
                    "This Nexori queue is currently disabled."
                ));
                return false;
            }
        }
        return false;
    }

    private Transform resolveLocalTargetTransform(@Nonnull ResolvedDestinationTarget target, @Nonnull UUID playerUuid) {
        DestinationTargetKind targetKind = target.definition().kind();
        World world = Universe.get().getWorld(target.effectiveWorldName());
        if (targetKind == DestinationTargetKind.NATURAL_SPAWN && world != null) {
            try {
                Transform spawnTransform = world.getWorldConfig().getSpawnProvider().getSpawnPoint(world, playerUuid);
                if (spawnTransform != null) {
                    return spawnTransform.clone();
                }
            } catch (Exception exception) {
                logger.atWarning().withCause(exception).log(
                    "Falling back to configured spawn resolution for local Nexori target '" + target.definition().id() + "'."
                );
            }

            Transform configuredSpawn = WorldSpawnResolver.resolveConfiguredSpawn(world).orElse(null);
            if (configuredSpawn != null) {
                return configuredSpawn.clone();
            }

            return new Transform(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f);
        }

        try {
            var root = com.google.gson.JsonParser.parseString(target.definition().metadataJson()).getAsJsonObject();
            if (root == null || !root.has("position")) {
                return null;
            }
            var position = root.getAsJsonObject("position");
            var rotation = root.has("rotation") ? root.getAsJsonObject("rotation") : null;
            return new Transform(
                new org.joml.Vector3d(
                    position.has("x") ? position.get("x").getAsDouble() : 0.0,
                    position.has("y") ? position.get("y").getAsDouble() : 0.0,
                    position.has("z") ? position.get("z").getAsDouble() : 0.0
                ),
                new com.hypixel.hytale.math.vector.Rotation3f(
                    rotation != null && rotation.has("pitch") ? (float) rotation.get("pitch").getAsDouble() : 0.0f,
                    rotation != null && rotation.has("yaw") ? (float) rotation.get("yaw").getAsDouble() : 0.0f,
                    rotation != null && rotation.has("roll") ? (float) rotation.get("roll").getAsDouble() : 0.0f
                )
            );
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log(
                "Failed to parse metadata for local Nexori target '" + target.definition().id() + "'."
            );
            return null;
        }
    }

}
