package io.github.hyjn.nexori.plugin.portal;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.access.NexoriAdminAccess;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.discovery.DestinationTargetDiscoveryService;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetCacheService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;
import io.github.hyjn.nexori.plugin.ui.NexoriMenuHyUiPage;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;
import io.github.hyjn.nexori.plugin.ui.NexoriPortalPage;
import io.github.hyjn.nexori.plugin.ui.PortalSetupDraft;
import io.github.hyjn.nexori.plugin.ui.PortalSetupDraftService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
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
    private final ConfiguredPeerService configuredPeerService;
    private final DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService;
    private final DestinationTargetDiscoveryService destinationTargetDiscoveryService;
    private final PortalSetupDraftService portalSetupDraftService;
    private final SecureTravelService secureTravelService;
    private final String adminPermission;
    private final Map<UUID, Long> lastCollisionHandledAtByPlayer = new ConcurrentHashMap<>();

    public NexoriPortalInteractionService(
        @Nonnull NexoriPlugin plugin,
        @Nonnull HytaleLogger logger,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService,
        @Nonnull DestinationTargetDiscoveryService destinationTargetDiscoveryService,
        @Nonnull PortalSetupDraftService portalSetupDraftService,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull String adminPermission
    ) {
        this.plugin = plugin;
        this.logger = logger;
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        this.configuredPeerService = configuredPeerService;
        this.discoveredDestinationTargetCacheService = discoveredDestinationTargetCacheService;
        this.destinationTargetDiscoveryService = destinationTargetDiscoveryService;
        this.portalSetupDraftService = portalSetupDraftService;
        this.secureTravelService = secureTravelService;
        this.adminPermission = adminPermission;
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
        NexoriMenuHyUiPage.openPortalSetup(ref, store, playerRef, player, plugin, portal, statusText);
    }

    private NexoriPortalPage tryCreateAdminPortalPage(
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

    private NexoriPortalPage tryHandlePortalTraverse(
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

        if (!shouldHandleTrigger(playerRef.getUuid())) {
            return null;
        }

        BlockPosition targetBlock = context.getTargetBlock();
        Vector3i blockPosition = targetBlock == null
            ? new Vector3i(0, 0, 0)
            : new Vector3i(targetBlock.x, targetBlock.y, targetBlock.z);

        Optional<PortalInstanceDefinition> portal = resolvePortal(player.getWorld().getName(), blockPosition);
        triggerPortalTravel(player, playerRef, portal);
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
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull Optional<PortalInstanceDefinition> portal
    ) {
        if (portal.isEmpty() || !portal.get().enabled()) {
            return;
        }

        Optional<TriggerBindingDefinition> binding = triggerBindingService.findPortalCollisionBinding(portal.get().portalId());
        if (binding.isEmpty() || !binding.get().enabled()) {
            return;
        }

        try {
            secureTravelService.travel(
                playerRef,
                ConfiguredPeer.parse(binding.get().destinationConnectionAddress()),
                binding.get().destinationTargetId(),
                "",
                binding.get().travelProfileId(),
                binding.get().contextJson()
            );
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(Message.raw("This Nexori portal could not start its secure travel: " + exception.getMessage()));
            logger.atWarning().withCause(exception).log("Failed to trigger secure travel from portal " + portal.get().portalId());
        }
    }

    private NexoriPortalPage buildAdminPortalPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String statusText
    ) {
        PortalSetupDraft draft = portalSetupDraftService.find(playerRef.getUuid(), portal.portalId())
            .orElse(new PortalSetupDraft(portal.portalId(), 0, "", "", "", portal.displayName()));

        return NexoriPortalPage.create(
            playerRef,
            portalInstanceService,
            triggerBindingService,
            configuredPeerService,
            discoveredDestinationTargetCacheService,
            destinationTargetDiscoveryService,
            portalSetupDraftService,
            player.getWorld().getName(),
            portal.blockPosition(),
            portal,
            draft.stepIndex(),
            draft.selectedDestinationAddress(),
            draft.selectedTargetId(),
            draft.selectedTravelProfileId(),
            draft.portalDisplayName(),
            statusText
        );
    }
}
