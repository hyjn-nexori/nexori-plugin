package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.discovery.DestinationTargetDiscoveryService;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetCacheService;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public final class NexoriPortalPage extends InteractiveCustomUIPage<NexoriPortalPage.PageData> {

    private static final int STEP_SELECT_SERVER = 0;
    private static final int STEP_SELECT_TARGET = 1;
    private static final int STEP_SELECT_PROFILE = 2;
    private static final int STEP_REVIEW = 3;
    private static final int LAST_STEP = STEP_REVIEW;

    private static final int PREVIOUS_STEP_INDEX = 1;
    private static final int NEXT_STEP_INDEX = 2;
    private static final int DESTINATION_PREVIOUS_INDEX = 10;
    private static final int DESTINATION_NEXT_INDEX = 11;
    private static final int DISCOVER_INDEX = 12;
    private static final int TARGET_PREVIOUS_INDEX = 20;
    private static final int TARGET_NEXT_INDEX = 21;
    private static final int PROFILE_PREVIOUS_INDEX = 30;
    private static final int PROFILE_NEXT_INDEX = 31;
    private static final int SAVE_BINDING_INDEX = 40;
    private static final int CLEAR_BINDING_INDEX = 41;
    private static final int TOGGLE_ENABLED_INDEX = 42;

    private final PlayerRef playerRef;
    private final PortalInstanceService portalInstanceService;
    private final TriggerBindingService triggerBindingService;
    private final ConfiguredPeerService configuredPeerService;
    private final DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService;
    private final DestinationTargetDiscoveryService destinationTargetDiscoveryService;
    private final String worldName;
    private final Vector3i blockPosition;
    private final String portalId;
    private final int stepIndex;
    private final String selectedDestinationAddress;
    private final String selectedTargetId;
    private final String selectedTravelProfileId;
    private final String statusText;

    private NexoriPortalPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService,
        @Nonnull DestinationTargetDiscoveryService destinationTargetDiscoveryService,
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        String portalId,
        int stepIndex,
        @Nonnull String selectedDestinationAddress,
        @Nonnull String selectedTargetId,
        @Nonnull String selectedTravelProfileId,
        @Nonnull String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        this.configuredPeerService = configuredPeerService;
        this.discoveredDestinationTargetCacheService = discoveredDestinationTargetCacheService;
        this.destinationTargetDiscoveryService = destinationTargetDiscoveryService;
        this.worldName = worldName;
        this.blockPosition = blockPosition.clone();
        this.portalId = safe(portalId).trim().toLowerCase();
        this.stepIndex = clampStep(stepIndex);
        this.selectedDestinationAddress = safe(selectedDestinationAddress).trim().toLowerCase();
        this.selectedTargetId = safe(selectedTargetId).trim().toLowerCase();
        this.selectedTravelProfileId = safe(selectedTravelProfileId).trim().toLowerCase();
        this.statusText = safe(statusText);
    }

    @Nonnull
    public static NexoriPortalPage create(
        @Nonnull PlayerRef playerRef,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService,
        @Nonnull DestinationTargetDiscoveryService destinationTargetDiscoveryService,
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        PortalInstanceDefinition portal,
        int stepIndex,
        @Nonnull String selectedDestinationAddress,
        @Nonnull String selectedTargetId,
        @Nonnull String selectedTravelProfileId,
        @Nonnull String statusText
    ) {
        return new NexoriPortalPage(
            playerRef,
            portalInstanceService,
            triggerBindingService,
            configuredPeerService,
            discoveredDestinationTargetCacheService,
            destinationTargetDiscoveryService,
            worldName,
            blockPosition,
            portal == null ? "" : portal.portalId(),
            stepIndex,
            selectedDestinationAddress,
            selectedTargetId,
            selectedTravelProfileId,
            statusText
        );
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService,
        @Nonnull DestinationTargetDiscoveryService destinationTargetDiscoveryService,
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        PortalInstanceDefinition portal,
        int stepIndex,
        @Nonnull String selectedDestinationAddress,
        @Nonnull String selectedTargetId,
        @Nonnull String selectedTravelProfileId,
        @Nonnull String statusText
    ) {
        PageManager pages = player == null ? null : player.getPageManager();
        if (pages == null) {
            return;
        }

        pages.openCustomPage(
            ref,
            store,
            create(
                playerRef,
                portalInstanceService,
                triggerBindingService,
                configuredPeerService,
                discoveredDestinationTargetCacheService,
                destinationTargetDiscoveryService,
                worldName,
                blockPosition,
                portal,
                stepIndex,
                selectedDestinationAddress,
                selectedTargetId,
                selectedTravelProfileId,
                statusText
            )
        );
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        Optional<PortalInstanceDefinition> portal = resolvePortal();
        TriggerBindingDefinition binding = portal
            .flatMap(found -> triggerBindingService.findPortalCollisionBinding(found.portalId()))
            .orElse(null);

        List<ConfiguredPeer> configuredPeers = configuredPeerService.list();
        String effectiveDestinationAddress = resolveSelectedDestinationAddress(configuredPeers, binding);
        DiscoveredDestinationTargetSet discovery = discoveredDestinationTargetCacheService.find(effectiveDestinationAddress)
            .orElse(null);
        String effectiveTargetId = resolveSelectedTargetId(discovery, effectiveDestinationAddress, binding);
        TravelProfileType effectiveTravelProfile = resolveSelectedTravelProfile(binding);

        commands.append("Pages/Nexori/NexoriPortal.ui");
        commands.set("#PortalLocationText.Text", worldName + " @ (" + blockPosition.getX() + ", " + blockPosition.getY() + ", " + blockPosition.getZ() + ")");
        commands.set("#PortalSummaryText.Text", portal
            .map(PortalInstanceDefinition::displayName)
            .orElse("No registered Nexori portal record was found at this block."));
        commands.set("#PortalIdText.Text", portal
            .map(PortalInstanceDefinition::portalId)
            .orElse("<missing>"));
        commands.set("#PortalTargetText.Text", portal
            .map(PortalInstanceDefinition::autoDestinationTargetId)
            .orElse("<missing>"));
        commands.set("#PortalEnabledText.Text", portal
            .map(found -> found.enabled() ? "Enabled" : "Disabled")
            .orElse("Missing"));
        commands.set("#StatusText.Text", statusText);
        commands.set("#StatusText.Visible", !statusText.isBlank());
        commands.set("#StepProgressText.Text", "Step " + (stepIndex + 1) + " of " + (LAST_STEP + 1));
        commands.set("#StepTitleText.Text", switch (stepIndex) {
            case STEP_SELECT_SERVER -> "Select the trusted destination server for this portal.";
            case STEP_SELECT_TARGET -> "Discover and choose the remote destination target.";
            case STEP_SELECT_PROFILE -> "Choose how this portal should carry the player's inventory.";
            case STEP_REVIEW -> "Review the current setup and save the portal binding.";
            default -> "Nexori Portal Setup";
        });

        commands.set("#ServerStep.Visible", stepIndex == STEP_SELECT_SERVER);
        commands.set("#TargetStep.Visible", stepIndex == STEP_SELECT_TARGET);
        commands.set("#ProfileStep.Visible", stepIndex == STEP_SELECT_PROFILE);
        commands.set("#ReviewStep.Visible", stepIndex == STEP_REVIEW);

        commands.set("#PrevStepButton.Visible", stepIndex > STEP_SELECT_SERVER);
        commands.set("#NextStepButton.Visible", stepIndex < STEP_REVIEW);

        commands.set("#SelectedDestinationText.Text", effectiveDestinationAddress.isBlank()
            ? "No configured trusted server available yet."
            : effectiveDestinationAddress);
        commands.set("#SelectedDestinationMetaText.Text", configuredPeers.isEmpty()
            ? "Add and bootstrap a peer first before binding this portal."
            : "Configured trusted servers on this origin: " + configuredPeers.size());
        commands.set("#DiscoverButton.Visible", portal.isPresent() && !effectiveDestinationAddress.isBlank());

        commands.set("#SelectedTargetText.Text", effectiveTargetId.isBlank()
            ? "No discovered target selected yet."
            : effectiveTargetId);
        commands.set("#SelectedTargetMetaText.Text", describeTarget(discovery, effectiveTargetId));
        commands.set("#DiscoveryStateText.Text", discovery == null
            ? "No discovery cache found yet for the selected server. Run discovery from this page first."
            : "Cached " + discovery.targets().size() + " remote target(s) from " + discovery.connectionAddress() + ".");

        commands.set("#SelectedProfileText.Text", effectiveTravelProfile.displayName());
        commands.set("#SelectedProfileMetaText.Text", effectiveTravelProfile.description());

        commands.set("#ReviewDestinationText.Text", effectiveDestinationAddress.isBlank() ? "<not selected>" : effectiveDestinationAddress);
        commands.set("#ReviewTargetText.Text", effectiveTargetId.isBlank() ? "<not selected>" : effectiveTargetId);
        commands.set("#ReviewProfileText.Text", effectiveTravelProfile.displayName() + " (" + effectiveTravelProfile.id() + ")");
        commands.set("#ReviewBindingStateText.Text", binding == null
            ? "This portal does not have a saved collision binding yet."
            : "Current binding: " + binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId());

        commands.set("#BindPortalButton.Visible", portal.isPresent() && !effectiveDestinationAddress.isBlank() && !effectiveTargetId.isBlank());
        commands.set("#ClearBindingButton.Visible", binding != null);
        commands.set("#TogglePortalButton.Visible", portal.isPresent());
        commands.set("#TogglePortalButtonLabel.Text", portal.filter(PortalInstanceDefinition::enabled).isPresent()
            ? "Disable Portal"
            : "Enable Portal");

        bindIndex(events, "#PrevStepButton", PREVIOUS_STEP_INDEX);
        bindIndex(events, "#NextStepButton", NEXT_STEP_INDEX);
        bindIndex(events, "#DestinationPrevButton", DESTINATION_PREVIOUS_INDEX);
        bindIndex(events, "#DestinationNextButton", DESTINATION_NEXT_INDEX);
        bindIndex(events, "#DiscoverButton", DISCOVER_INDEX);
        bindIndex(events, "#TargetPrevButton", TARGET_PREVIOUS_INDEX);
        bindIndex(events, "#TargetNextButton", TARGET_NEXT_INDEX);
        bindIndex(events, "#ProfilePrevButton", PROFILE_PREVIOUS_INDEX);
        bindIndex(events, "#ProfileNextButton", PROFILE_NEXT_INDEX);
        bindIndex(events, "#BindPortalButton", SAVE_BINDING_INDEX);
        bindIndex(events, "#ClearBindingButton", CLEAR_BINDING_INDEX);
        bindIndex(events, "#TogglePortalButton", TOGGLE_ENABLED_INDEX);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        Integer index = parseIndex(data.indexRaw);
        if (index == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        Optional<PortalInstanceDefinition> portal = resolvePortal();
        TriggerBindingDefinition binding = portal
            .flatMap(found -> triggerBindingService.findPortalCollisionBinding(found.portalId()))
            .orElse(null);
        List<ConfiguredPeer> configuredPeers = configuredPeerService.list();
        String effectiveDestinationAddress = resolveSelectedDestinationAddress(configuredPeers, binding);
        DiscoveredDestinationTargetSet discovery = discoveredDestinationTargetCacheService.find(effectiveDestinationAddress)
            .orElse(null);
        String effectiveTargetId = resolveSelectedTargetId(discovery, effectiveDestinationAddress, binding);
        TravelProfileType effectiveTravelProfile = resolveSelectedTravelProfile(binding);

        try {
            switch (index) {
                case PREVIOUS_STEP_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex - 1,
                    effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), ""
                );
                case NEXT_STEP_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex + 1,
                    effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), ""
                );
                case DESTINATION_PREVIOUS_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex,
                    cycleDestination(configuredPeers, effectiveDestinationAddress, -1), "",
                    effectiveTravelProfile.id(), ""
                );
                case DESTINATION_NEXT_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex,
                    cycleDestination(configuredPeers, effectiveDestinationAddress, 1), "",
                    effectiveTravelProfile.id(), ""
                );
                case DISCOVER_INDEX -> {
                    if (effectiveDestinationAddress.isBlank()) {
                        reopen(ref, store, player, portal.orElse(null), stepIndex, effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), "Select a destination server first.");
                        return;
                    }
                    Transform transform = captureCurrentTransform(store, ref);
                    destinationTargetDiscoveryService.discover(
                        playerRef,
                        ConfiguredPeer.parse(effectiveDestinationAddress),
                        player.getWorld().getName(),
                        transform
                    );
                    player.sendMessage(Message.raw("Nexori is discovering destination targets from " + effectiveDestinationAddress + "..."));
                }
                case TARGET_PREVIOUS_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex,
                    effectiveDestinationAddress, cycleTarget(discovery, effectiveTargetId, -1),
                    effectiveTravelProfile.id(), ""
                );
                case TARGET_NEXT_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex,
                    effectiveDestinationAddress, cycleTarget(discovery, effectiveTargetId, 1),
                    effectiveTravelProfile.id(), ""
                );
                case PROFILE_PREVIOUS_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex,
                    effectiveDestinationAddress, effectiveTargetId,
                    cycleProfile(effectiveTravelProfile, -1).id(), ""
                );
                case PROFILE_NEXT_INDEX -> reopen(
                    ref, store, player, portal.orElse(null), stepIndex,
                    effectiveDestinationAddress, effectiveTargetId,
                    cycleProfile(effectiveTravelProfile, 1).id(), ""
                );
                case SAVE_BINDING_INDEX -> {
                    PortalInstanceDefinition resolvedPortal = portal.orElse(null);
                    if (resolvedPortal == null) {
                        return;
                    }
                    if (effectiveDestinationAddress.isBlank()) {
                        reopen(ref, store, player, resolvedPortal, stepIndex, effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), "Select a destination server first.");
                        return;
                    }
                    if (effectiveTargetId.isBlank()) {
                        reopen(ref, store, player, resolvedPortal, stepIndex, effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), "Select a discovered destination target first.");
                        return;
                    }

                    TriggerBindingDefinition saved = triggerBindingService.bindPortalCollision(
                        resolvedPortal.portalId(),
                        effectiveDestinationAddress,
                        effectiveTargetId,
                        effectiveTravelProfile.id(),
                        "{}"
                    );
                    reopen(
                        ref, store, player, resolvedPortal, STEP_REVIEW,
                        saved.destinationConnectionAddress(), saved.destinationTargetId(), saved.travelProfileId(),
                        "Saved portal binding to " + saved.destinationConnectionAddress() + " -> " + saved.destinationTargetId() + "."
                    );
                }
                case CLEAR_BINDING_INDEX -> {
                    PortalInstanceDefinition resolvedPortal = portal.orElse(null);
                    if (resolvedPortal == null) {
                        return;
                    }
                    boolean removed = triggerBindingService.removePortalCollisionBinding(resolvedPortal.portalId());
                    reopen(
                        ref, store, player, resolvedPortal, STEP_REVIEW,
                        effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(),
                        removed ? "Removed the portal binding." : "This portal did not have a saved binding."
                    );
                }
                case TOGGLE_ENABLED_INDEX -> {
                    PortalInstanceDefinition resolvedPortal = portal.orElse(null);
                    if (resolvedPortal == null) {
                        return;
                    }
                    portalInstanceService.setEnabled(resolvedPortal.portalId(), !resolvedPortal.enabled());
                    reopen(
                        ref, store, player, null, STEP_REVIEW,
                        effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(),
                        resolvedPortal.enabled() ? "Portal disabled." : "Portal enabled."
                    );
                }
                default -> {
                }
            }
        } catch (IOException exception) {
            reopen(ref, store, player, portal.orElse(null), stepIndex, effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), "The Nexori portal setup could not be saved: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            reopen(ref, store, player, portal.orElse(null), stepIndex, effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), exception.getMessage());
        } catch (Exception exception) {
            reopen(ref, store, player, portal.orElse(null), stepIndex, effectiveDestinationAddress, effectiveTargetId, effectiveTravelProfile.id(), "The Nexori portal action failed: " + exception.getMessage());
        }
    }

    public static final class PageData {

        private static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(
                new KeyedCodec<>("Index", Codec.STRING),
                (pageData, indexRaw) -> pageData.indexRaw = indexRaw,
                pageData -> pageData.indexRaw
            )
            .add()
            .build();

        private String indexRaw = "";
    }

    private void reopen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        PortalInstanceDefinition portal,
        int stepIndex,
        @Nonnull String selectedDestinationAddress,
        @Nonnull String selectedTargetId,
        @Nonnull String selectedTravelProfileId,
        @Nonnull String statusText
    ) {
        open(
            ref,
            store,
            playerRef,
            player,
            portalInstanceService,
            triggerBindingService,
            configuredPeerService,
            discoveredDestinationTargetCacheService,
            destinationTargetDiscoveryService,
            worldName,
            blockPosition,
            portal,
            stepIndex,
            selectedDestinationAddress,
            selectedTargetId,
            selectedTravelProfileId,
            statusText
        );
    }

    @Nonnull
    private String resolveSelectedDestinationAddress(
        @Nonnull List<ConfiguredPeer> configuredPeers,
        TriggerBindingDefinition binding
    ) {
        if (!selectedDestinationAddress.isBlank()) {
            return selectedDestinationAddress;
        }
        if (binding != null && !binding.destinationConnectionAddress().isBlank()) {
            return binding.destinationConnectionAddress();
        }
        return configuredPeers.isEmpty() ? "" : configuredPeers.getFirst().connectionAddress();
    }

    @Nonnull
    private String resolveSelectedTargetId(
        DiscoveredDestinationTargetSet discovery,
        @Nonnull String effectiveDestinationAddress,
        TriggerBindingDefinition binding
    ) {
        if (!selectedTargetId.isBlank()) {
            return selectedTargetId;
        }
        if (binding != null
            && binding.destinationConnectionAddress().equals(effectiveDestinationAddress)
            && !binding.destinationTargetId().isBlank()) {
            return binding.destinationTargetId();
        }
        if (discovery == null || discovery.targets().isEmpty()) {
            return "";
        }
        return discovery.targets().getFirst().id();
    }

    @Nonnull
    private TravelProfileType resolveSelectedTravelProfile(TriggerBindingDefinition binding) {
        if (!selectedTravelProfileId.isBlank()) {
            return TravelProfileType.parse(selectedTravelProfileId);
        }
        if (binding != null && !binding.travelProfileId().isBlank()) {
            return TravelProfileType.parse(binding.travelProfileId());
        }
        return TravelProfileType.KEEP_INVENTORY;
    }

    @Nonnull
    private String describeTarget(DiscoveredDestinationTargetSet discovery, @Nonnull String targetId) {
        if (discovery == null || discovery.targets().isEmpty()) {
            return "Discover the destination server to browse its available arrival targets.";
        }

        for (DiscoveredDestinationTargetSummary target : discovery.targets()) {
            if (target.id().equals(targetId)) {
                return target.displayName() + " / " + target.kind() + " / " + target.worldName();
            }
        }

        return targetId.isBlank()
            ? "No discovered target selected yet."
            : targetId + " (not present in the current discovery cache)";
    }

    @Nonnull
    private String cycleDestination(@Nonnull List<ConfiguredPeer> configuredPeers, @Nonnull String current, int delta) {
        if (configuredPeers.isEmpty()) {
            return "";
        }

        int index = 0;
        for (int i = 0; i < configuredPeers.size(); i++) {
            if (configuredPeers.get(i).connectionAddress().equals(current)) {
                index = i;
                break;
            }
        }

        int nextIndex = Math.floorMod(index + delta, configuredPeers.size());
        return configuredPeers.get(nextIndex).connectionAddress();
    }

    @Nonnull
    private String cycleTarget(DiscoveredDestinationTargetSet discovery, @Nonnull String current, int delta) {
        if (discovery == null || discovery.targets().isEmpty()) {
            return "";
        }

        List<DiscoveredDestinationTargetSummary> targets = discovery.targets();
        int index = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).id().equals(current)) {
                index = i;
                break;
            }
        }

        int nextIndex = Math.floorMod(index + delta, targets.size());
        return targets.get(nextIndex).id();
    }

    @Nonnull
    private TravelProfileType cycleProfile(@Nonnull TravelProfileType current, int delta) {
        TravelProfileType[] values = TravelProfileType.values();
        int nextIndex = Math.floorMod(current.ordinal() + delta, values.length);
        return values[nextIndex];
    }

    @Nonnull
    private Transform captureCurrentTransform(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            throw new IllegalStateException("Could not read the live player position for Nexori discovery.");
        }

        Vector3f rotation = transformComponent.getRotation();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            rotation = headRotation.getRotation();
        }
        return new Transform(transformComponent.getPosition(), rotation);
    }

    private void bindIndex(@Nonnull UIEventBuilder events, @Nonnull String selector, int index) {
        EventData data = EventData.of("Index", Integer.toString(index));
        String[] selectorCandidates = new String[] {selector, selector + " #Button"};

        for (String candidate : selectorCandidates) {
            try {
                events.addEventBinding(CustomUIEventBindingType.Activating, candidate, data, false);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private Integer parseIndex(String rawIndex) {
        String normalized = safe(rawIndex).trim();
        if (normalized.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nonnull
    private Optional<PortalInstanceDefinition> resolvePortal() {
        if (!portalId.isBlank()) {
            Optional<PortalInstanceDefinition> byId = portalInstanceService.findById(portalId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        return portalInstanceService.findByLocation(worldName, blockPosition);
    }

    @Nonnull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clampStep(int stepIndex) {
        return Math.max(STEP_SELECT_SERVER, Math.min(LAST_STEP, stepIndex));
    }
}
