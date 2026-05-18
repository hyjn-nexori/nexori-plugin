package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.minigame.logic.SpectatorHiddenViewerPlan;
import io.github.hyjn.nexori.plugin.minigame.logic.SpectatorHiddenViewerPlanner;
import io.github.hyjn.nexori.plugin.minigame.logic.SpectatorRuntimeExecutionDecision;
import io.github.hyjn.nexori.plugin.minigame.logic.SpectatorRuntimeExecutionPlanner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpectatorRuntimeService implements SpectatorRuntimeController {

    private final HytaleLogger logger;
    private final Map<UUID, SpectatorRuntimeState> statesByPlayerUuid = new HashMap<>();
    private final SpectatorHiddenViewerPlanner hiddenViewerPlanner = new SpectatorHiddenViewerPlanner();
    private final SpectatorRuntimeExecutionPlanner executionPlanner = new SpectatorRuntimeExecutionPlanner();

    public SpectatorRuntimeService(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public synchronized SpectatorRuntimeResult enterSpectator(
        @Nonnull PlayerRef playerRef,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom,
        @Nonnull SpectatorRuntimeReason reason,
        @Nullable String spectatorModelId
    ) {
        UUID playerUuid = playerRef.getUuid();
        SpectatorRuntimeState existing = statesByPlayerUuid.get(playerUuid);
        if (existing != null) {
            SpectatorRuntimeResult refreshResult = refreshHiddenViewers(playerUuid, viewerUuidsToHideFrom);
            SpectatorRuntimeResult reapplyResult = applyRuntimeEffects(playerRef, spectatorModelId);
            return combine(playerUuid, refreshResult, reapplyResult);
        }

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        SpectatorRuntimeState state = snapshot(playerRef, reason, viewerUuidsToHideFrom, skipped, errors);
        statesByPlayerUuid.put(playerUuid, state);

        tryApplyRuntimeMutation(playerRef, "switch to Adventure game mode", applied, errors, () -> switchGameMode(playerRef, GameMode.Adventure));
        tryApplyRuntimeMutation(playerRef, "enable flight", applied, errors, () -> enableFlight(playerRef));
        tryApplyRuntimeMutation(playerRef, "force flying state", applied, errors, () -> forceFlying(playerRef, true));
        tryApply("hide spectator from active viewers", applied, errors, () -> hideFromViewers(playerUuid, state.hiddenViewerUuids()));
        tryApplyRuntimeMutation(playerRef, "add Intangible component", applied, errors, () -> addComponent(playerRef, Intangible.getComponentType()));
        tryApplyRuntimeMutation(playerRef, "add Invulnerable component", applied, errors, () -> addComponent(playerRef, Invulnerable.getComponentType()));
        tryApplyRuntimeMutation(playerRef, "disable spectator collision and trigger checks", applied, errors, () -> disableSpectatorCollisionChecks(playerRef));
        applySpectatorModel(playerRef, spectatorModelId, applied, skipped, warnings);

        SpectatorRuntimeResult result = new SpectatorRuntimeResult(playerUuid, List.copyOf(applied), List.copyOf(skipped), List.copyOf(warnings), List.copyOf(errors));
        if (!result.succeeded()) {
            logger.atWarning().log("Nexori spectator runtime enter had errors for player " + playerUuid + ". " + result.summary());
        }
        return result;
    }

    @Override
    public synchronized SpectatorRuntimeResult exitSpectator(@Nonnull PlayerRef playerRef, @Nonnull SpectatorRuntimeReason reason) {
        UUID playerUuid = playerRef.getUuid();
        SpectatorRuntimeState state = statesByPlayerUuid.get(playerUuid);
        if (state == null) {
            return SpectatorRuntimeResult.success(playerUuid);
        }

        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        restore(playerRef, state, applied, skipped, errors);
        if (!errors.isEmpty()) {
            warnings.add("Restore had errors; runtime spectator tracking was cleared so packet and pickup guards do not trap the player.");
        }
        SpectatorRuntimeResult result = new SpectatorRuntimeResult(playerUuid, List.copyOf(applied), List.copyOf(skipped), List.copyOf(warnings), List.copyOf(errors));
        statesByPlayerUuid.remove(playerUuid);
        if (!result.succeeded()) {
            logger.atWarning().log("Nexori spectator runtime restore had errors for player " + playerUuid + ". " + result.summary());
        }
        return result;
    }

    @Override
    public synchronized SpectatorRuntimeResult restoreIfTracked(@Nonnull UUID playerUuid, @Nonnull SpectatorRuntimeReason reason) {
        SpectatorRuntimeState state = statesByPlayerUuid.get(playerUuid);
        if (state == null) {
            return SpectatorRuntimeResult.success(playerUuid);
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef != null) {
            return exitSpectator(playerRef, reason);
        }

        hideOrShowViewers(playerUuid, state.hiddenViewerUuids(), false);
        statesByPlayerUuid.remove(playerUuid);
        return new SpectatorRuntimeResult(
            playerUuid,
            List.of("restore player visibility for online viewers"),
            List.of("full runtime restore skipped because player is offline"),
            List.of(),
            List.of()
        );
    }

    @Override
    public synchronized boolean isRuntimeSpectator(@Nonnull UUID playerUuid) {
        return statesByPlayerUuid.containsKey(playerUuid);
    }

    @Override
    public synchronized SpectatorRuntimeResult refreshHiddenViewers(
        @Nonnull UUID spectatorUuid,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom
    ) {
        SpectatorRuntimeState state = statesByPlayerUuid.get(spectatorUuid);
        if (state == null) {
            return SpectatorRuntimeResult.success(spectatorUuid);
        }

        SpectatorHiddenViewerPlan plan = hiddenViewerPlanner.plan(spectatorUuid, state.hiddenViewerUuids(), viewerUuidsToHideFrom);

        hideFromViewers(spectatorUuid, plan.viewersToHide());
        hideOrShowViewers(spectatorUuid, plan.viewersToShow(), false);
        statesByPlayerUuid.put(spectatorUuid, state.withHiddenViewerUuids(plan.desiredHiddenViewers()));
        return new SpectatorRuntimeResult(
            spectatorUuid,
            List.of("refresh spectator hidden viewers"),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private SpectatorRuntimeState snapshot(
        PlayerRef playerRef,
        SpectatorRuntimeReason reason,
        Collection<UUID> viewerUuidsToHideFrom,
        List<String> skipped,
        List<String> errors
    ) {
        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref == null ? null : ref.getStore();
        Player player = null;
        MovementManager movementManager = null;
        CollisionResult collisionResult = null;
        Model previousModel = null;

        if (store == null || ref == null) {
            skipped.add("Could not read entity store/reference for full restore snapshot.");
        } else {
            try {
                player = store.getComponent(ref, Player.getComponentType());
                movementManager = store.getComponent(ref, MovementManager.getComponentType());
                CollisionResultComponent collisionComponent = store.getComponent(ref, CollisionResultComponent.getComponentType());
                if (collisionComponent != null) {
                    collisionResult = collisionComponent.getCollisionResult();
                }
                ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
                previousModel = modelComponent == null ? null : modelComponent.getModel();
            } catch (RuntimeException exception) {
                errors.add("Could not read one or more snapshot components: " + exception.getMessage());
            }
        }

        MovementSettings settings = movementManager == null ? null : movementManager.getSettings();
        MovementSettings defaultSettings = movementManager == null ? null : movementManager.getDefaultSettings();
        boolean previousIntangible = store != null && ref != null && store.getComponent(ref, Intangible.getComponentType()) != null;
        boolean previousInvulnerable = store != null && ref != null && store.getComponent(ref, Invulnerable.getComponentType()) != null;

        Set<UUID> hiddenViewerUuids = hiddenViewerPlanner.normalizeDesiredViewers(playerRef.getUuid(), viewerUuidsToHideFrom);

        return new SpectatorRuntimeState(
            playerRef.getUuid(),
            System.currentTimeMillis(),
            reason,
            player == null ? null : player.getGameMode(),
            settings != null && settings.canFly,
            defaultSettings != null && defaultSettings.canFly,
            previousIntangible,
            previousInvulnerable,
            collisionResult == null ? null : collisionResult.getCollisionByMaterial(),
            collisionResult == null ? null : collisionResult.isCheckingForCharacterCollisions(),
            collisionResult == null ? null : collisionResult.isCheckingTriggerBlocks(),
            collisionResult == null ? null : collisionResult.isCheckingDamageBlocks(),
            collisionResult == null ? null : collisionResult.isDamageBlocking(),
            player == null ? null : player.executeTriggers,
            player == null ? null : player.executeBlockDamage,
            previousModel,
            Set.copyOf(hiddenViewerUuids)
        );
    }

    private void hideFromViewers(UUID spectatorUuid, Set<UUID> viewerUuids) {
        hideOrShowViewers(spectatorUuid, viewerUuids, true);
    }

    private void hideOrShowViewers(UUID spectatorUuid, Set<UUID> viewerUuids, boolean hide) {
        for (UUID viewerUuid : viewerUuids) {
            PlayerRef viewer = Universe.get().getPlayer(viewerUuid);
            if (viewer == null || viewer.getHiddenPlayersManager() == null) {
                continue;
            }
            HiddenPlayersManager hiddenPlayersManager = viewer.getHiddenPlayersManager();
            if (hide) {
                hiddenPlayersManager.hidePlayer(spectatorUuid);
            } else {
                hiddenPlayersManager.showPlayer(spectatorUuid);
            }
        }
    }

    private void enableFlight(PlayerRef playerRef) {
        MovementManager movementManager = movementManager(playerRef);
        MovementSettings settings = movementManager.getSettings();
        MovementSettings defaultSettings = movementManager.getDefaultSettings();
        if (settings != null) {
            settings.canFly = true;
        }
        if (defaultSettings != null) {
            defaultSettings.canFly = true;
        }
        movementManager.update(playerRef.getPacketHandler());
    }

    private SpectatorRuntimeResult applyRuntimeEffects(PlayerRef playerRef, @Nullable String spectatorModelId) {
        UUID playerUuid = playerRef.getUuid();
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        tryApplyRuntimeMutation(playerRef, "switch to Adventure game mode", applied, errors, () -> switchGameMode(playerRef, GameMode.Adventure));
        tryApplyRuntimeMutation(playerRef, "enable flight", applied, errors, () -> enableFlight(playerRef));
        tryApplyRuntimeMutation(playerRef, "force flying state", applied, errors, () -> forceFlying(playerRef, true));
        tryApplyRuntimeMutation(playerRef, "add Intangible component", applied, errors, () -> addComponent(playerRef, Intangible.getComponentType()));
        tryApplyRuntimeMutation(playerRef, "add Invulnerable component", applied, errors, () -> addComponent(playerRef, Invulnerable.getComponentType()));
        tryApplyRuntimeMutation(playerRef, "disable spectator collision and trigger checks", applied, errors, () -> disableSpectatorCollisionChecks(playerRef));
        applySpectatorModel(playerRef, spectatorModelId, applied, skipped, warnings);
        return new SpectatorRuntimeResult(playerUuid, List.copyOf(applied), List.copyOf(skipped), List.copyOf(warnings), List.copyOf(errors));
    }

    private void applySpectatorModel(
        PlayerRef playerRef,
        @Nullable String spectatorModelId,
        List<String> applied,
        List<String> skipped,
        List<String> warnings
    ) {
        String modelId = normalizeModelId(spectatorModelId);
        if (modelId.isBlank()) {
            skipped.add("spectator model skipped because no model id was provided");
            return;
        }

        try {
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelId);
            if (modelAsset == null) {
                warnings.add("spectator model '" + modelId + "' was not found");
                return;
            }
            Model model = Model.createScaledModel(modelAsset, modelAsset.generateRandomScale());
            tryApplyRuntimeMutation(
                playerRef,
                "apply spectator model " + modelAsset.getId(),
                applied,
                warnings,
                () -> {
                    Ref<EntityStore> ref = requireRef(playerRef);
                    ref.getStore().putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
                }
            );
        } catch (RuntimeException exception) {
            warnings.add("spectator model '" + modelId + "' failed: " + exception.getMessage());
        }
    }

    private void forceFlying(PlayerRef playerRef, boolean flying) {
        playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(flying)));
    }

    private void restoreFlight(PlayerRef playerRef, SpectatorRuntimeState state) {
        MovementManager movementManager = movementManager(playerRef);
        MovementSettings settings = movementManager.getSettings();
        MovementSettings defaultSettings = movementManager.getDefaultSettings();
        if (settings != null) {
            settings.canFly = state.previousCanFly();
        }
        if (defaultSettings != null) {
            defaultSettings.canFly = state.previousDefaultCanFly();
        }
        movementManager.update(playerRef.getPacketHandler());
        if (!state.previousCanFly() && !state.previousDefaultCanFly()) {
            forceFlying(playerRef, false);
        }
    }

    private void disableSpectatorCollisionChecks(PlayerRef playerRef) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Store<EntityStore> store = ref.getStore();
        CollisionResultComponent collisionComponent = store.getComponent(ref, CollisionResultComponent.getComponentType());
        if (collisionComponent == null) {
            throw new IllegalStateException("CollisionResultComponent is not available on the player entity.");
        }
        CollisionResult collisionResult = collisionComponent.getCollisionResult();
        collisionResult.disableCharacterCollisions();
        collisionResult.disableTriggerBlocks();
        collisionResult.disableDamageBlocks();
        collisionResult.setDamageBlocking(false);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.configTriggerBlockProcessing(false, false, collisionComponent);
        }
    }

    private void restoreCollisionChecks(PlayerRef playerRef, SpectatorRuntimeState state) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Store<EntityStore> store = ref.getStore();
        CollisionResultComponent collisionComponent = store.getComponent(ref, CollisionResultComponent.getComponentType());
        if (collisionComponent == null || state.previousCollisionByMaterial() == null) {
            return;
        }

        CollisionResult collisionResult = collisionComponent.getCollisionResult();
        collisionResult.setCollisionByMaterial(state.previousCollisionByMaterial());
        if (Boolean.TRUE.equals(state.previousCharacterCollisions())) {
            collisionResult.enableCharacterCollsions();
        } else {
            collisionResult.disableCharacterCollisions();
        }
        if (Boolean.TRUE.equals(state.previousTriggerBlocks())) {
            collisionResult.enableTriggerBlocks();
        } else {
            collisionResult.disableTriggerBlocks();
        }
        if (Boolean.TRUE.equals(state.previousDamageBlocks())) {
            collisionResult.enableDamageBlocks();
        } else {
            collisionResult.disableDamageBlocks();
        }
        if (state.previousDamageBlocking() != null) {
            collisionResult.setDamageBlocking(state.previousDamageBlocking());
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && state.previousExecuteTriggers() != null && state.previousExecuteBlockDamage() != null) {
            player.configTriggerBlockProcessing(state.previousExecuteTriggers(), state.previousExecuteBlockDamage(), collisionComponent);
        }
    }

    private <T extends com.hypixel.hytale.component.Component<EntityStore>> void addComponent(
        PlayerRef playerRef,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> componentType
    ) {
        Ref<EntityStore> ref = requireRef(playerRef);
        ref.getStore().ensureComponent(ref, componentType);
    }

    private <T extends com.hypixel.hytale.component.Component<EntityStore>> void restoreComponent(
        PlayerRef playerRef,
        com.hypixel.hytale.component.ComponentType<EntityStore, T> componentType,
        boolean existedBefore
    ) {
        Ref<EntityStore> ref = requireRef(playerRef);
        if (existedBefore) {
            ref.getStore().ensureComponent(ref, componentType);
            return;
        }
        ref.getStore().tryRemoveComponent(ref, componentType);
    }

    private void restore(PlayerRef playerRef, SpectatorRuntimeState state, List<String> applied, List<String> skipped, List<String> errors) {
        tryApply("restore player visibility", applied, errors, () -> hideOrShowViewers(playerRef.getUuid(), state.hiddenViewerUuids(), false));
        tryApplyRuntimeMutation(playerRef, "restore flight", applied, errors, () -> restoreFlight(playerRef, state));
        if (state.previousGameMode() != null) {
            tryApplyRuntimeMutation(playerRef, "restore game mode", applied, errors, () -> Player.setGameMode(requireRef(playerRef), state.previousGameMode(), requireRef(playerRef).getStore()));
        } else {
            skipped.add("Game mode restore skipped because no previous game mode was captured.");
        }
        tryApplyRuntimeMutation(playerRef, "restore collision checks", applied, errors, () -> restoreCollisionChecks(playerRef, state));
        tryApplyRuntimeMutation(playerRef, "restore Intangible component", applied, errors, () -> restoreComponent(playerRef, Intangible.getComponentType(), state.previousIntangible()));
        tryApplyRuntimeMutation(playerRef, "restore Invulnerable component", applied, errors, () -> restoreComponent(playerRef, Invulnerable.getComponentType(), state.previousInvulnerable()));
        tryApplyRuntimeMutation(playerRef, "restore player model", applied, errors, () -> restorePlayerModel(playerRef, state));
    }

    private void restorePlayerModel(PlayerRef playerRef, SpectatorRuntimeState state) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Store<EntityStore> store = ref.getStore();
        if (state.previousModel() != null) {
            store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(state.previousModel()));
            return;
        }

        PlayerSkinComponent playerSkinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (playerSkinComponent == null) {
            return;
        }
        Model model = CosmeticsModule.get().createModel(playerSkinComponent.getPlayerSkin());
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
        playerSkinComponent.setNetworkOutdated();
    }

    private void switchGameMode(PlayerRef playerRef, GameMode gameMode) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Player.setGameMode(ref, gameMode, ref.getStore());
    }

    private MovementManager movementManager(PlayerRef playerRef) {
        Ref<EntityStore> ref = requireRef(playerRef);
        MovementManager movementManager = ref.getStore().getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            throw new IllegalStateException("MovementManager is not available on the player entity.");
        }
        return movementManager;
    }

    private Ref<EntityStore> requireRef(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            throw new IllegalStateException("PlayerRef has no entity reference.");
        }
        return ref;
    }

    private void tryApply(String operation, List<String> applied, List<String> errors, Runnable runnable) {
        try {
            runnable.run();
            applied.add(operation);
        } catch (RuntimeException exception) {
            errors.add(operation + " failed: " + exception.getMessage());
        }
    }

    private void tryApplyRuntimeMutation(PlayerRef playerRef, String operation, List<String> applied, List<String> errors, Runnable runnable) {
        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref == null ? null : ref.getStore();
        World world = store == null || store.getExternalData() == null ? null : store.getExternalData().getWorld();
        SpectatorRuntimeExecutionDecision decision = executionPlanner.plan(
            store != null,
            store != null && store.isInThread(),
            store != null && store.isProcessing(),
            world != null
        );
        if (decision == SpectatorRuntimeExecutionDecision.SCHEDULE_ON_WORLD) {
            world.execute(() -> {
                try {
                    runnable.run();
                } catch (RuntimeException exception) {
                    logger.atWarning().log("Scheduled Nexori spectator runtime operation '" + operation + "' failed: " + exception.getMessage());
                }
            });
            applied.add("schedule " + operation);
            return;
        }
        tryApply(operation, applied, errors, runnable);
    }

    @Nonnull
    private String normalizeModelId(@Nullable String modelId) {
        return modelId == null ? "" : modelId.trim();
    }

    private SpectatorRuntimeResult combine(UUID playerUuid, SpectatorRuntimeResult first, SpectatorRuntimeResult second) {
        List<String> applied = new ArrayList<>();
        applied.addAll(first.applied());
        applied.addAll(second.applied());
        List<String> skipped = new ArrayList<>();
        skipped.addAll(first.skipped());
        skipped.addAll(second.skipped());
        List<String> warnings = new ArrayList<>();
        warnings.addAll(first.warnings());
        warnings.addAll(second.warnings());
        List<String> errors = new ArrayList<>();
        errors.addAll(first.errors());
        errors.addAll(second.errors());
        return new SpectatorRuntimeResult(
            playerUuid,
            List.copyOf(applied),
            List.copyOf(skipped),
            List.copyOf(warnings),
            List.copyOf(errors)
        );
    }
}
