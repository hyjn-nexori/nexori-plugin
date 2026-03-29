package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import io.github.hyjn.nexori.plugin.portal.NexoriPortalInteractionService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

public final class NexoriTargetManagerPage extends InteractiveCustomUIPage<NexoriTargetManagerPage.PageData> {

    private static final int TARGET_INDEX_BASE = 1000;
    private static final int ACTION_CREATE_INDEX = 2000;
    private static final int ACTION_CONTINUE_DRAFT_INDEX = 2001;
    private static final int ACTION_EDIT_INDEX = 2002;
    private static final int ACTION_REMOVE_INDEX = 2003;
    private static final int ACTION_OPEN_PORTAL_SETUP_INDEX = 2004;

    private final PlayerRef playerRef;
    private final DestinationTargetService destinationTargetService;
    private final PortalInstanceService portalInstanceService;
    private final NexoriPortalInteractionService portalInteractionService;
    private final TargetSetupDraftService targetSetupDraftService;
    private final List<DestinationTargetDefinition> targets;
    private final String selectedTargetId;
    private final String statusText;

    private NexoriTargetManagerPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull NexoriPortalInteractionService portalInteractionService,
        @Nonnull TargetSetupDraftService targetSetupDraftService,
        @Nonnull List<DestinationTargetDefinition> targets,
        @Nonnull String selectedTargetId,
        @Nonnull String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.destinationTargetService = destinationTargetService;
        this.portalInstanceService = portalInstanceService;
        this.portalInteractionService = portalInteractionService;
        this.targetSetupDraftService = targetSetupDraftService;
        this.targets = targets;
        this.selectedTargetId = selectedTargetId;
        this.statusText = statusText;
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull NexoriPortalInteractionService portalInteractionService,
        @Nonnull TargetSetupDraftService targetSetupDraftService,
        String selectedTargetId,
        @Nonnull String statusText
    ) {
        PageManager pages = player == null ? null : player.getPageManager();
        if (pages == null) {
            return;
        }

        List<DestinationTargetDefinition> targets = destinationTargetService.list();
        String resolvedSelectedId = resolveSelectedTargetId(targets, selectedTargetId);
        pages.openCustomPage(
            ref,
            store,
            new NexoriTargetManagerPage(
                playerRef,
                destinationTargetService,
                portalInstanceService,
                portalInteractionService,
                targetSetupDraftService,
                targets,
                resolvedSelectedId,
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
        TargetSetupDraft draft = targetSetupDraftService.find(playerRef.getUuid()).orElse(null);
        DestinationTargetDefinition selectedTarget = selectedTarget();
        boolean selectedPortal = selectedTarget != null && selectedTarget.kind() == DestinationTargetKind.PORTAL;
        boolean selectedNaturalSpawn = selectedTarget != null && selectedTarget.kind() == DestinationTargetKind.NATURAL_SPAWN;

        commands.append("Pages/Nexori/NexoriTargetManager.ui");
        commands.set("#TargetCountText.Text", targets.size() + " destination target(s)");
        commands.set("#StatusText.Text", statusText);
        commands.set("#StatusText.Visible", !statusText.isBlank());
        commands.set("#DraftStatusText.Visible", draft != null);
        commands.set("#DraftStatusText.Text", draft == null
            ? ""
            : "Saved draft: "
                + (draft.targetId().isBlank() ? "<new target>" : draft.targetId())
                + " / step " + (draft.stepIndex() + 1));

        bindIndex(events, "#CreateTargetButton", ACTION_CREATE_INDEX);
        bindIndex(events, "#ContinueDraftButton", ACTION_CONTINUE_DRAFT_INDEX);
        bindIndex(events, "#EditTargetButton", ACTION_EDIT_INDEX);
        bindIndex(events, "#RemoveTargetButton", ACTION_REMOVE_INDEX);
        bindIndex(events, "#OpenPortalSetupButton", ACTION_OPEN_PORTAL_SETUP_INDEX);

        commands.set("#ContinueDraftButton.Visible", draft != null);
        commands.set("#EditTargetButton.Visible", selectedTarget != null && selectedTarget.kind() == DestinationTargetKind.COORDINATE);
        commands.set("#RemoveTargetButton.Visible", selectedTarget != null && selectedTarget.kind() == DestinationTargetKind.COORDINATE);
        commands.set("#SelectedPortalHint.Visible", selectedPortal || selectedNaturalSpawn);
        commands.set("#PortalSetupGroup.Visible", selectedPortal);

        for (int i = 0; i < targets.size(); i++) {
            DestinationTargetDefinition target = targets.get(i);
            new NexoriTargetEntryElement(
                target.displayName(),
                target.kind().displayName() + " / " + target.worldName(),
                target.id().equals(selectedTargetId)
            ).addButton(commands, events, "#TargetList[" + i + "]", playerRef);
            bindIndex(events, "#TargetList[" + i + "]", TARGET_INDEX_BASE + i);
        }

        commands.set("#SelectedTargetId.Text", selectedTarget == null ? "<none>" : selectedTarget.id());
        commands.set("#SelectedDisplayName.Text", selectedTarget == null ? "<none>" : selectedTarget.displayName());
        commands.set("#SelectedKind.Text", selectedTarget == null ? "<none>" : selectedTarget.kind().displayName());
        commands.set("#SelectedWorld.Text", selectedTarget == null ? "<none>" : selectedTarget.worldName());
        commands.set("#SelectedArrivalPoint.Text", selectedTarget == null ? "<none>" : selectedTarget.arrivalPointId());
        commands.set("#SelectedPortalHint.Text", selectedPortal
            ? "Portal targets are created automatically when you place a Nexori portal. Open the portal setup to rename it or change its travel binding."
            : selectedNaturalSpawn
                ? "Natural spawn targets are generated automatically, one per world, and cannot be edited or removed from this screen."
                : "");
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

        if (index >= TARGET_INDEX_BASE && index < TARGET_INDEX_BASE + targets.size()) {
            DestinationTargetDefinition selected = targets.get(index - TARGET_INDEX_BASE);
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, selected.id(), statusText);
            return;
        }

        switch (index) {
            case ACTION_CREATE_INDEX -> {
                targetSetupDraftService.clear(playerRef.getUuid());
                NexoriTargetWizardPage.open(
                    ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService,
                    0, "", "", "", DestinationTargetKind.COORDINATE.name(), ""
                );
            }
            case ACTION_CONTINUE_DRAFT_INDEX -> {
                TargetSetupDraft draft = targetSetupDraftService.find(playerRef.getUuid()).orElse(null);
                if (draft == null) {
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, selectedTargetId, "No saved target draft was found.");
                    return;
                }
                NexoriTargetWizardPage.open(
                    ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService,
                    draft.stepIndex(), draft.targetId(), draft.displayName(), draft.arrivalPointId(), draft.selectedKindId(), ""
                );
            }
            case ACTION_EDIT_INDEX -> {
                DestinationTargetDefinition target = selectedTarget();
                if (target == null) {
                    return;
                }
                if (target.kind() == DestinationTargetKind.PORTAL) {
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, target.id(), "Use Open Portal Setup to manage this portal.");
                    return;
                }
                if (target.kind() == DestinationTargetKind.NATURAL_SPAWN) {
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, target.id(), "Natural spawn targets are generated automatically and cannot be edited here.");
                    return;
                }
                targetSetupDraftService.save(
                    playerRef.getUuid(),
                    new TargetSetupDraft(1, target.id(), target.displayName(), target.arrivalPointId(), target.kind().name())
                );
                NexoriTargetWizardPage.open(
                    ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService,
                    1, target.id(), target.displayName(), target.arrivalPointId(), target.kind().name(), ""
                );
            }
            case ACTION_REMOVE_INDEX -> {
                DestinationTargetDefinition target = selectedTarget();
                if (target == null) {
                    return;
                }
                if (target.kind() != DestinationTargetKind.COORDINATE) {
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, target.id(), target.kind() == DestinationTargetKind.PORTAL
                        ? "Portal targets are removed automatically when their portal is removed."
                        : "Natural spawn targets are generated automatically and cannot be removed here.");
                    return;
                }
                try {
                    destinationTargetService.remove(target.id());
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, "", "Removed destination target " + target.id() + ".");
                } catch (IOException exception) {
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, target.id(), "Failed to remove the destination target: " + exception.getMessage());
                }
            }
            case ACTION_OPEN_PORTAL_SETUP_INDEX -> {
                DestinationTargetDefinition target = selectedTarget();
                if (target == null || target.kind() != DestinationTargetKind.PORTAL) {
                    return;
                }
                PortalInstanceDefinition portal = portalInstanceService.findByAutoDestinationTargetId(target.id()).orElse(null);
                if (portal == null) {
                    open(ref, store, playerRef, player, destinationTargetService, portalInstanceService, portalInteractionService, targetSetupDraftService, target.id(), "Could not resolve the owning portal for this target.");
                    return;
                }
                portalInteractionService.openAdminPortalPage(ref, store, playerRef, player, portal, "");
            }
            default -> {
            }
        }
    }

    private DestinationTargetDefinition selectedTarget() {
        for (DestinationTargetDefinition target : targets) {
            if (target.id().equals(selectedTargetId)) {
                return target;
            }
        }
        return null;
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
        if (rawIndex == null || rawIndex.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawIndex.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nonnull
    private static String resolveSelectedTargetId(
        @Nonnull List<DestinationTargetDefinition> targets,
        String selectedTargetId
    ) {
        String normalized = selectedTargetId == null ? "" : selectedTargetId.trim().toLowerCase();
        if (!normalized.isBlank()) {
            for (DestinationTargetDefinition target : targets) {
                if (target.id().equals(normalized)) {
                    return normalized;
                }
            }
        }
        return targets.isEmpty() ? "" : targets.getFirst().id();
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
