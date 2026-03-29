package io.github.hyjn.nexori.plugin.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalInteractionService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.UUID;

public final class NexoriTargetWizardPage extends InteractiveCustomUIPage<NexoriTargetWizardPage.PageData> {

    private static final DestinationTargetKind[] WIZARD_KINDS = new DestinationTargetKind[] {
        DestinationTargetKind.COORDINATE
    };

    private static final Gson GSON = new Gson();

    private static final int STEP_KIND = 0;
    private static final int STEP_DETAILS = 1;
    private static final int STEP_REVIEW = 2;
    private static final int LAST_STEP = STEP_REVIEW;

    private static final int PREVIOUS_STEP_INDEX = 1;
    private static final int NEXT_STEP_INDEX = 2;
    private static final int KIND_PREVIOUS_INDEX = 10;
    private static final int KIND_NEXT_INDEX = 11;
    private static final int SAVE_TARGET_INDEX = 20;
    private static final int RESET_INDEX = 21;

    private final PlayerRef playerRef;
    private final DestinationTargetService destinationTargetService;
    private final PortalInstanceService portalInstanceService;
    private final NexoriPortalInteractionService portalInteractionService;
    private final TargetSetupDraftService targetSetupDraftService;
    private final int stepIndex;
    private final String targetId;
    private final String displayName;
    private final String arrivalPointId;
    private final String selectedKindId;
    private final String statusText;

    private NexoriTargetWizardPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull NexoriPortalInteractionService portalInteractionService,
        @Nonnull TargetSetupDraftService targetSetupDraftService,
        int stepIndex,
        @Nonnull String targetId,
        @Nonnull String displayName,
        @Nonnull String arrivalPointId,
        @Nonnull String selectedKindId,
        @Nonnull String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.destinationTargetService = destinationTargetService;
        this.portalInstanceService = portalInstanceService;
        this.portalInteractionService = portalInteractionService;
        this.targetSetupDraftService = targetSetupDraftService;
        this.stepIndex = clampStep(stepIndex);
        this.targetId = safe(targetId);
        this.displayName = safe(displayName);
        this.arrivalPointId = safe(arrivalPointId);
        this.selectedKindId = safe(selectedKindId);
        this.statusText = safe(statusText);
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
        int stepIndex,
        @Nonnull String targetId,
        @Nonnull String displayName,
        @Nonnull String arrivalPointId,
        @Nonnull String selectedKindId,
        @Nonnull String statusText
    ) {
        PageManager pages = player == null ? null : player.getPageManager();
        if (pages == null) {
            return;
        }

        pages.openCustomPage(
            ref,
            store,
            new NexoriTargetWizardPage(
                playerRef,
                destinationTargetService,
                portalInstanceService,
                portalInteractionService,
                targetSetupDraftService,
                stepIndex,
                targetId,
                displayName,
                arrivalPointId,
                selectedKindId,
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
        Player player = store.getComponent(ref, Player.getComponentType());
        World world = player == null ? null : player.getWorld();
        DestinationTargetKind selectedKind = resolveSelectedKind();

        commands.append("Pages/Nexori/NexoriTargetWizard.ui");
        commands.set("#StatusText.Text", statusText);
        commands.set("#StatusText.Visible", !statusText.isBlank());
        commands.set("#StepProgressText.Text", "Step " + (stepIndex + 1) + " of " + (LAST_STEP + 1));
        commands.set("#StepTitleText.Text", switch (stepIndex) {
            case STEP_KIND -> "Choose what kind of arrival target you want to create.";
            case STEP_DETAILS -> "Give this target a display name.";
            case STEP_REVIEW -> "Review the current world and live position/orientation before saving.";
            default -> "Nexori Target Wizard";
        });

        commands.set("#KindStep.Visible", stepIndex == STEP_KIND);
        commands.set("#DetailsStep.Visible", stepIndex == STEP_DETAILS);
        commands.set("#ReviewStep.Visible", stepIndex == STEP_REVIEW);

        commands.set("#PrevStepButton.Visible", stepIndex > STEP_KIND);
        commands.set("#NextStepButton.Visible", stepIndex < STEP_REVIEW);
        commands.set("#SaveTargetButton.Visible", stepIndex == STEP_REVIEW);

        commands.set("#SelectedKindText.Text", selectedKind.displayName());
        commands.set("#SelectedKindMetaText.Text", switch (selectedKind) {
            case NATURAL_SPAWN -> "Use the world's real natural spawn when the player arrives.";
            case COORDINATE -> "Capture your current live position and facing direction as the destination.";
            case PORTAL -> "Portal targets are created automatically when you place a Nexori portal.";
        });

        commands.set("#DisplayNameField #Input.Value", displayName);
        commands.set("#DetailsHintText.Text", "Nexori will generate the internal target id for you. Saving captures your current live position and facing direction.");

        commands.set("#ReviewWorldText.Text", world == null ? "<unknown>" : world.getName());
        commands.set("#ReviewDisplayNameText.Text", displayName.isBlank() ? "<unnamed coordinate target>" : displayName);
        commands.set("#ReviewKindText.Text", selectedKind.displayName());
        commands.set("#ReviewCaptureText.Text", describeCurrentCapture(store, ref, selectedKind));

        bindIndex(events, "#PrevStepButton", PREVIOUS_STEP_INDEX);
        bindIndex(events, "#NextStepButton", NEXT_STEP_INDEX);
        bindIndex(events, "#KindPrevButton", KIND_PREVIOUS_INDEX);
        bindIndex(events, "#KindNextButton", KIND_NEXT_INDEX);
        bindIndex(events, "#SaveTargetButton", SAVE_TARGET_INDEX);
        bindIndex(events, "#ResetButton", RESET_INDEX);

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveTargetButton",
            new EventData()
                .append("Index", Integer.toString(SAVE_TARGET_INDEX))
                .append("@DisplayName", "#DisplayNameField #Input.Value")
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#NextStepButton",
            new EventData()
                .append("Index", Integer.toString(NEXT_STEP_INDEX))
                .append("@DisplayName", "#DisplayNameField #Input.Value")
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PrevStepButton",
            new EventData()
                .append("Index", Integer.toString(PREVIOUS_STEP_INDEX))
                .append("@DisplayName", "#DisplayNameField #Input.Value")
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ResetButton",
            new EventData().append("Index", Integer.toString(RESET_INDEX))
        );
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

        String nextTargetId = safe(targetId).trim();
        String nextDisplayName = safe(data.displayName).trim();
        String nextArrivalPointId = safe(arrivalPointId).trim();
        DestinationTargetKind selectedKind = resolveSelectedKind();

        try {
            switch (index) {
                case PREVIOUS_STEP_INDEX -> reopen(ref, store, player, stepIndex - 1, nextTargetId, nextDisplayName, nextArrivalPointId, selectedKind.name(), "");
                case NEXT_STEP_INDEX -> reopen(ref, store, player, stepIndex + 1, nextTargetId, nextDisplayName, nextArrivalPointId, selectedKind.name(), "");
                case KIND_PREVIOUS_INDEX -> reopen(ref, store, player, stepIndex, nextTargetId, nextDisplayName, nextArrivalPointId, cycleKind(selectedKind, -1).name(), "");
                case KIND_NEXT_INDEX -> reopen(ref, store, player, stepIndex, nextTargetId, nextDisplayName, nextArrivalPointId, cycleKind(selectedKind, 1).name(), "");
                case SAVE_TARGET_INDEX -> {
                    World world = player.getWorld();
                    if (world == null) {
                        reopen(ref, store, player, stepIndex, nextTargetId, nextDisplayName, nextArrivalPointId, selectedKind.name(), "Could not resolve the current world for this target.");
                        return;
                    }
                    String effectiveTargetId = nextTargetId.isBlank()
                        ? generateTargetId(selectedKind)
                        : nextTargetId;
                    DestinationTargetDefinition target = destinationTargetService.upsert(new DestinationTargetDefinition(
                        effectiveTargetId,
                        nextDisplayName,
                        selectedKind,
                        world.getName(),
                        "",
                        "",
                        buildMetadataJson(selectedKind, store, ref)
                    ));
                    targetSetupDraftService.clear(playerRef.getUuid());
                    NexoriTargetManagerPage.open(
                        ref,
                        store,
                        playerRef,
                        player,
                        destinationTargetService,
                        portalInstanceService,
                        portalInteractionService,
                        targetSetupDraftService,
                        target.id(),
                        "Saved Nexori destination target " + target.id() + " in world " + target.worldName() + "."
                    );
                }
                case RESET_INDEX -> {
                    targetSetupDraftService.clear(playerRef.getUuid());
                    reopen(ref, store, player, STEP_KIND, "", "", "", DestinationTargetKind.COORDINATE.name(), "");
                }
                default -> {
                }
            }
        } catch (IOException exception) {
            reopen(ref, store, player, stepIndex, nextTargetId, nextDisplayName, nextArrivalPointId, selectedKind.name(), "Failed to save the Nexori destination target: " + exception.getMessage());
        } catch (IllegalArgumentException exception) {
            reopen(ref, store, player, stepIndex, nextTargetId, nextDisplayName, nextArrivalPointId, selectedKind.name(), exception.getMessage());
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
            .append(
                new KeyedCodec<>("Action", Codec.STRING),
                (pageData, actionRaw) -> pageData.actionRaw = actionRaw,
                pageData -> pageData.actionRaw
            )
            .add()
            .append(
                new KeyedCodec<>("@DisplayName", Codec.STRING),
                (pageData, displayName) -> pageData.displayName = displayName,
                pageData -> pageData.displayName
            )
            .add()
            .build();

        private String indexRaw = "";
        private String actionRaw = "";
        private String displayName = "";
    }

    private void reopen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        int stepIndex,
        @Nonnull String targetId,
        @Nonnull String displayName,
        @Nonnull String arrivalPointId,
        @Nonnull String selectedKindId,
        @Nonnull String statusText
    ) {
        saveDraft(stepIndex, targetId, displayName, arrivalPointId, selectedKindId);
        open(
            ref,
            store,
            playerRef,
            player,
            destinationTargetService,
            portalInstanceService,
            portalInteractionService,
            targetSetupDraftService,
            stepIndex,
            targetId,
            displayName,
            arrivalPointId,
            selectedKindId,
            statusText
        );
    }

    @Nonnull
    private DestinationTargetKind resolveSelectedKind() {
        try {
            DestinationTargetKind parsed = DestinationTargetKind.parse(selectedKindId.isBlank() ? DestinationTargetKind.NATURAL_SPAWN.name() : selectedKindId);
            return parsed == DestinationTargetKind.COORDINATE ? parsed : DestinationTargetKind.COORDINATE;
        } catch (IllegalArgumentException ignored) {
            return DestinationTargetKind.COORDINATE;
        }
    }

    @Nonnull
    private DestinationTargetKind cycleKind(@Nonnull DestinationTargetKind current, int delta) {
        int index = 0;
        for (int i = 0; i < WIZARD_KINDS.length; i++) {
            if (WIZARD_KINDS[i] == current) {
                index = i;
                break;
            }
        }
        return WIZARD_KINDS[Math.floorMod(index + delta, WIZARD_KINDS.length)];
    }

    @Nonnull
    private String describeCurrentCapture(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DestinationTargetKind selectedKind
    ) {
        if (selectedKind == DestinationTargetKind.NATURAL_SPAWN) {
            return "This target will use the current world's natural spawn provider instead of saved coordinates.";
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return "Could not read the live player position yet.";
        }

        Vector3d position = transform.getPosition();
        Vector3f rotation = resolveRotation(store, ref, transform.getRotation());
        return "Current capture preview: pos=("
            + trim(position.x) + ", " + trim(position.y) + ", " + trim(position.z)
            + ") rot=(" + trim(rotation.x) + ", " + trim(rotation.y) + ", " + trim(rotation.z) + ")";
    }

    @Nonnull
    private String buildMetadataJson(
        @Nonnull DestinationTargetKind kind,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        if (kind == DestinationTargetKind.NATURAL_SPAWN) {
            return "{}";
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            throw new IllegalStateException("Could not read the live player position to create this destination target.");
        }

        Vector3d position = transform.getPosition();
        Vector3f rotation = resolveRotation(store, ref, transform.getRotation());

        JsonObject root = new JsonObject();
        JsonObject positionObject = new JsonObject();
        positionObject.addProperty("x", position.x);
        positionObject.addProperty("y", position.y);
        positionObject.addProperty("z", position.z);
        root.add("position", positionObject);

        JsonObject rotationObject = new JsonObject();
        rotationObject.addProperty("pitch", rotation.x);
        rotationObject.addProperty("yaw", rotation.y);
        rotationObject.addProperty("roll", rotation.z);
        root.add("rotation", rotationObject);
        return GSON.toJson(root);
    }

    @Nonnull
    private Vector3f resolveRotation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Vector3f fallback
    ) {
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        return headRotation == null ? fallback : headRotation.getRotation();
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

    @Nonnull
    private String trim(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Nonnull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clampStep(int stepIndex) {
        return Math.max(STEP_KIND, Math.min(LAST_STEP, stepIndex));
    }

    private void saveDraft(
        int stepIndex,
        @Nonnull String targetId,
        @Nonnull String displayName,
        @Nonnull String arrivalPointId,
        @Nonnull String selectedKindId
    ) {
        targetSetupDraftService.save(
            playerRef.getUuid(),
            new TargetSetupDraft(stepIndex, targetId, displayName, arrivalPointId, selectedKindId)
        );
    }

    @Nonnull
    private String generateTargetId(@Nonnull DestinationTargetKind kind) {
        String prefix = switch (kind) {
            case COORDINATE -> "coordinate";
            case NATURAL_SPAWN -> "spawn";
            case PORTAL -> "portal";
        };
        String candidate;
        do {
            candidate = prefix + "." + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        } while (destinationTargetService.find(candidate).isPresent());
        return candidate;
    }
}
