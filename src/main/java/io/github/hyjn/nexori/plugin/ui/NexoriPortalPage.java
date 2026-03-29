package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;

import javax.annotation.Nonnull;
import java.util.Optional;

public final class NexoriPortalPage extends InteractiveCustomUIPage<NexoriPortalPage.PageData> {

    private static final int TOGGLE_ENABLED_INDEX = 10;

    private final PlayerRef playerRef;
    private final PortalInstanceService portalInstanceService;
    private final TriggerBindingService triggerBindingService;
    private final String worldName;
    private final Vector3i blockPosition;
    private final String portalId;

    private NexoriPortalPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        String portalId
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        this.worldName = worldName;
        this.blockPosition = blockPosition.clone();
        this.portalId = portalId == null ? "" : portalId.trim().toLowerCase();
    }

    @Nonnull
    public static NexoriPortalPage create(
        @Nonnull PlayerRef playerRef,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        PortalInstanceDefinition portal
    ) {
        return new NexoriPortalPage(
            playerRef,
            portalInstanceService,
            triggerBindingService,
            worldName,
            blockPosition,
            portal == null ? "" : portal.portalId()
        );
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        PortalInstanceDefinition portal
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
                worldName,
                blockPosition,
                portal
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
        commands.set("#PortalBindingText.Text", binding == null
            ? "Not bound yet"
            : binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId());
        commands.set("#PortalTravelProfileText.Text", binding == null || binding.travelProfileId().isBlank()
            ? "<none>"
            : binding.travelProfileId());
        commands.set("#PortalHelpText.Text", portal.isEmpty()
            ? "Place the Nexori portal block again if this one should be managed by Nexori."
            : "Use /nexoriportalbind " + portal.get().portalId() + " <host:port> <targetId> [travelProfile] to link this portal.");
        commands.set("#TogglePortalButton.Visible", portal.isPresent());
        commands.set("#TogglePortalButtonLabel.Text", portal.filter(PortalInstanceDefinition::enabled).isPresent()
            ? "Disable Portal"
            : "Enable Portal");

        bindIndex(events, "#TogglePortalButton", TOGGLE_ENABLED_INDEX);
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        Integer index = parseIndex(data.indexRaw);
        if (index == null || index != TOGGLE_ENABLED_INDEX) {
            return;
        }

        PortalInstanceDefinition portal = resolvePortal().orElse(null);
        if (portal == null) {
            return;
        }

        try {
            portalInstanceService.setEnabled(portal.portalId(), !portal.enabled());
        } catch (Exception ignored) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        open(ref, store, playerRef, player, portalInstanceService, triggerBindingService, worldName, blockPosition, null);
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
}
