package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.camera.SetFlyCameraMode;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.HiddenPlayersManager;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.collision.CollisionConfig;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NexoriSpectatorRuntimeProbeService {

    private final Map<UUID, SpectatorRuntimeSnapshot> snapshots = new HashMap<>();

    public ProbeResult enterProbe(@Nonnull PlayerRef playerRef, @Nonnull SpectatorRuntimeMode mode) {
        UUID playerUuid = playerRef.getUuid();
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (snapshots.containsKey(playerUuid)) {
            ProbeResult restoreResult = exitProbe(playerRef);
            applied.addAll(prefix("previous restore: ", restoreResult.applied()));
            skipped.addAll(prefix("previous restore: ", restoreResult.skipped()));
            errors.addAll(prefix("previous restore: ", restoreResult.errors()));
            warnings.addAll(prefix("previous restore: ", restoreResult.warnings()));
            if (!restoreResult.succeeded()) {
                errors.add("New spectator probe mode was not applied because the previous restore did not finish cleanly.");
                return new ProbeResult(playerUuid, mode, false, applied, skipped, warnings, errors);
            }
        }

        SpectatorRuntimeSnapshot snapshot = snapshot(playerRef, mode, skipped, errors);
        snapshots.put(playerUuid, snapshot);

        tryApply("hide player from current viewers", applied, errors, () -> hideFromCurrentViewers(playerRef, snapshot));

        if (mode == SpectatorRuntimeMode.FLY_HIDDEN
            || mode == SpectatorRuntimeMode.FLY_HIDDEN_COLLISION_OFF
            || mode == SpectatorRuntimeMode.CREATIVE_HIDDEN) {
            tryApply("enable flight", applied, errors, () -> enableFlight(playerRef));
            tryApply("set client flying state", applied, errors, () -> playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(true))));
        }

        if (mode == SpectatorRuntimeMode.SAFE_FLY_HIDDEN
            || mode == SpectatorRuntimeMode.NO_ATTACK_TEST
            || mode == SpectatorRuntimeMode.NO_INTERACT_TEST
            || mode == SpectatorRuntimeMode.NO_PICKUP_TEST
            || mode == SpectatorRuntimeMode.SAFE_PLUS_TEST) {
            applySafeRuntime(playerRef, applied, errors);
        }

        if (mode == SpectatorRuntimeMode.FLY_HIDDEN_COLLISION_OFF) {
            tryApply("disable collision checks", applied, errors, () -> disableCollisionChecks(playerRef));
            tryApply("add Intangible component", applied, errors, () -> addComponent(playerRef, Intangible.getComponentType()));
            tryApply("add Invulnerable component", applied, errors, () -> addComponent(playerRef, Invulnerable.getComponentType()));
        }

        if (mode == SpectatorRuntimeMode.CREATIVE_HIDDEN) {
            tryApply("switch to Creative game mode", applied, errors, () -> switchGameMode(playerRef, GameMode.Creative));
            tryApply("add Intangible component", applied, errors, () -> addComponent(playerRef, Intangible.getComponentType()));
            tryApply("add Invulnerable component", applied, errors, () -> addComponent(playerRef, Invulnerable.getComponentType()));
            skipped.add("CREATIVE_HIDDEN is intentionally not connected to production spectator flow.");
        }

        if (mode == SpectatorRuntimeMode.FREECAM_HIDDEN) {
            tryApply("enter fly camera mode", applied, errors, () -> playerRef.getPacketHandler().writeNoCache(new SetFlyCameraMode(true)));
        }

        if (mode == SpectatorRuntimeMode.NO_ATTACK_TEST || mode == SpectatorRuntimeMode.SAFE_PLUS_TEST) {
            tryApply("clear active attack interactions", applied, errors, () -> clearInteractionState(
                playerRef,
                InteractionType.Primary,
                InteractionType.Secondary,
                InteractionType.Ability1,
                InteractionType.Ability2,
                InteractionType.Ability3,
                InteractionType.Held,
                InteractionType.HeldOffhand,
                InteractionType.ProjectileSpawn
            ));
            warnings.add("Outgoing attack block is only a probe: Hytale exposes clear/reset interaction state, but no safe per-player future attack deny switch was found.");
        }

        if (mode == SpectatorRuntimeMode.NO_INTERACT_TEST || mode == SpectatorRuntimeMode.SAFE_PLUS_TEST) {
            tryApply("clear active world interaction chains", applied, errors, () -> clearInteractionState(
                playerRef,
                InteractionType.Primary,
                InteractionType.Secondary,
                InteractionType.Use,
                InteractionType.Pick,
                InteractionType.Collision,
                InteractionType.CollisionEnter,
                InteractionType.CollisionLeave
            ));
            warnings.add("Interaction block is only a probe: InteractionManager.clear() cancels current chains, but no safe per-player future UseBlock/OpenContainer deny switch was found.");
        }

        if (mode == SpectatorRuntimeMode.NO_PICKUP_TEST || mode == SpectatorRuntimeMode.SAFE_PLUS_TEST) {
            skipped.add("player pickup disable skipped: PreventPickup applies to item entities, and PlayerSettings exposes pickup locations but no None/disabled player-side pickup option.");
        }

        return new ProbeResult(playerUuid, mode, false, applied, skipped, warnings, errors);
    }

    public ProbeResult exitProbe(@Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        SpectatorRuntimeSnapshot snapshot = snapshots.get(playerUuid);
        List<String> applied = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (snapshot == null) {
            skipped.add("No active spectator probe snapshot was tracked for this player.");
            return new ProbeResult(playerUuid, null, true, applied, skipped, warnings, errors);
        }

        restore(playerRef, snapshot, applied, skipped, errors);
        if (errors.isEmpty()) {
            snapshots.remove(playerUuid);
        } else {
            warnings.add("Restore had errors; snapshot remains tracked so /nexorispectatorprobe off can be retried.");
        }
        return new ProbeResult(playerUuid, snapshot.mode(), true, applied, skipped, warnings, errors);
    }

    public ProbeResult restoreIfTracked(@Nonnull UUID playerUuid) {
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef == null) {
            snapshots.remove(playerUuid);
            return new ProbeResult(playerUuid, null, true, List.of(), List.of("Player is offline; discarded spectator probe snapshot."), List.of(), List.of());
        }
        return exitProbe(playerRef);
    }

    public boolean isTracked(@Nonnull UUID playerUuid) {
        return snapshots.containsKey(playerUuid);
    }

    private SpectatorRuntimeSnapshot snapshot(
        PlayerRef playerRef,
        SpectatorRuntimeMode mode,
        List<String> skipped,
        List<String> errors
    ) {
        Ref<EntityStore> ref = playerRef.getReference();
        Store<EntityStore> store = ref == null ? null : ref.getStore();
        Player player = null;
        MovementManager movementManager = null;
        CollisionResult collisionResult = null;

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
            } catch (RuntimeException exception) {
                errors.add("Could not read one or more snapshot components: " + exception.getMessage());
            }
        }

        MovementSettings settings = movementManager == null ? null : movementManager.getSettings();
        MovementSettings defaultSettings = movementManager == null ? null : movementManager.getDefaultSettings();
        boolean previousIntangible = store != null && ref != null && store.getComponent(ref, Intangible.getComponentType()) != null;
        boolean previousInvulnerable = store != null && ref != null && store.getComponent(ref, Invulnerable.getComponentType()) != null;
        Set<UUID> viewersHiddenByProbe = captureViewersToHide(playerRef);

        return new SpectatorRuntimeSnapshot(
            playerRef.getUuid(),
            mode,
            System.currentTimeMillis(),
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
            viewersHiddenByProbe
        );
    }

    private Set<UUID> captureViewersToHide(PlayerRef playerRef) {
        Set<UUID> viewers = new HashSet<>();
        UUID targetUuid = playerRef.getUuid();
        for (PlayerRef viewer : Universe.get().getPlayers()) {
            if (viewer == null || targetUuid.equals(viewer.getUuid())) {
                continue;
            }
            HiddenPlayersManager hiddenPlayersManager = viewer.getHiddenPlayersManager();
            if (hiddenPlayersManager != null && !hiddenPlayersManager.isPlayerHidden(targetUuid)) {
                viewers.add(viewer.getUuid());
            }
        }
        return viewers;
    }

    private void hideFromCurrentViewers(PlayerRef playerRef, SpectatorRuntimeSnapshot snapshot) {
        UUID targetUuid = playerRef.getUuid();
        for (UUID viewerUuid : snapshot.viewersHiddenByProbe()) {
            PlayerRef viewer = Universe.get().getPlayer(viewerUuid);
            if (viewer != null && viewer.getHiddenPlayersManager() != null) {
                viewer.getHiddenPlayersManager().hidePlayer(targetUuid);
            }
        }
    }

    private void restoreHiddenViewers(PlayerRef playerRef, SpectatorRuntimeSnapshot snapshot) {
        UUID targetUuid = playerRef.getUuid();
        for (UUID viewerUuid : snapshot.viewersHiddenByProbe()) {
            PlayerRef viewer = Universe.get().getPlayer(viewerUuid);
            if (viewer != null && viewer.getHiddenPlayersManager() != null) {
                viewer.getHiddenPlayersManager().showPlayer(targetUuid);
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

    private void applySafeRuntime(PlayerRef playerRef, List<String> applied, List<String> errors) {
        tryApply("enable flight", applied, errors, () -> enableFlight(playerRef));
        tryApply("set client flying state", applied, errors, () -> playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(true))));
        tryApply("add Intangible component", applied, errors, () -> addComponent(playerRef, Intangible.getComponentType()));
        tryApply("add Invulnerable component", applied, errors, () -> addComponent(playerRef, Invulnerable.getComponentType()));
        tryApply("disable spectator character and trigger collision checks", applied, errors, () -> disableSpectatorCollisionChecks(playerRef));
    }

    private void restoreFlight(PlayerRef playerRef, SpectatorRuntimeSnapshot snapshot) {
        MovementManager movementManager = movementManager(playerRef);
        MovementSettings settings = movementManager.getSettings();
        MovementSettings defaultSettings = movementManager.getDefaultSettings();
        if (settings != null) {
            settings.canFly = snapshot.previousCanFly();
        }
        if (defaultSettings != null) {
            defaultSettings.canFly = snapshot.previousDefaultCanFly();
        }
        movementManager.update(playerRef.getPacketHandler());
        if (!snapshot.previousCanFly() && !snapshot.previousDefaultCanFly()) {
            playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
        }
    }

    private void disableCollisionChecks(PlayerRef playerRef) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Store<EntityStore> store = ref.getStore();
        CollisionResultComponent collisionComponent = store.getComponent(ref, CollisionResultComponent.getComponentType());
        if (collisionComponent == null) {
            throw new IllegalStateException("CollisionResultComponent is not available on the player entity.");
        }
        CollisionResult collisionResult = collisionComponent.getCollisionResult();
        collisionResult.setCollisionByMaterial(CollisionConfig.MATERIAL_SET_NONE);
        collisionResult.disableCharacterCollisions();
        collisionResult.disableTriggerBlocks();
        collisionResult.disableDamageBlocks();
        collisionResult.setDamageBlocking(false);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.configTriggerBlockProcessing(false, false, collisionComponent);
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

    private void restoreCollisionChecks(PlayerRef playerRef, SpectatorRuntimeSnapshot snapshot) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Store<EntityStore> store = ref.getStore();
        CollisionResultComponent collisionComponent = store.getComponent(ref, CollisionResultComponent.getComponentType());
        if (collisionComponent == null || snapshot.previousCollisionByMaterial() == null) {
            return;
        }

        CollisionResult collisionResult = collisionComponent.getCollisionResult();
        collisionResult.setCollisionByMaterial(snapshot.previousCollisionByMaterial());
        if (Boolean.TRUE.equals(snapshot.previousCharacterCollisions())) {
            collisionResult.enableCharacterCollsions();
        } else {
            collisionResult.disableCharacterCollisions();
        }
        if (Boolean.TRUE.equals(snapshot.previousTriggerBlocks())) {
            collisionResult.enableTriggerBlocks();
        } else {
            collisionResult.disableTriggerBlocks();
        }
        if (Boolean.TRUE.equals(snapshot.previousDamageBlocks())) {
            collisionResult.enableDamageBlocks();
        } else {
            collisionResult.disableDamageBlocks();
        }
        if (snapshot.previousDamageBlocking() != null) {
            collisionResult.setDamageBlocking(snapshot.previousDamageBlocking());
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && snapshot.previousExecuteTriggers() != null && snapshot.previousExecuteBlockDamage() != null) {
            player.configTriggerBlockProcessing(snapshot.previousExecuteTriggers(), snapshot.previousExecuteBlockDamage(), collisionComponent);
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

    private void switchGameMode(PlayerRef playerRef, GameMode gameMode) {
        Ref<EntityStore> ref = requireRef(playerRef);
        Player.setGameMode(ref, gameMode, ref.getStore());
    }

    private void clearInteractionState(PlayerRef playerRef, InteractionType... interactionTypes) {
        InteractionManager interactionManager = interactionManager(playerRef);
        interactionManager.clear();
        if (interactionManager.getInteractionSimulationHandler() == null) {
            return;
        }
        for (InteractionType interactionType : interactionTypes) {
            interactionManager.getInteractionSimulationHandler().setState(interactionType, false);
        }
    }

    private void restore(PlayerRef playerRef, SpectatorRuntimeSnapshot snapshot, List<String> applied, List<String> skipped, List<String> errors) {
        if (snapshot.mode() == SpectatorRuntimeMode.FREECAM_HIDDEN) {
            tryApply("exit fly camera mode", applied, errors, () -> playerRef.getPacketHandler().writeNoCache(new SetFlyCameraMode(false)));
        }

        tryApply("restore player visibility", applied, errors, () -> restoreHiddenViewers(playerRef, snapshot));
        tryApply("restore flight", applied, errors, () -> restoreFlight(playerRef, snapshot));
        if (snapshot.previousGameMode() != null) {
            tryApply("restore game mode", applied, errors, () -> switchGameMode(playerRef, snapshot.previousGameMode()));
        } else {
            skipped.add("Game mode restore skipped because no previous game mode was captured.");
        }
        tryApply("restore collision checks", applied, errors, () -> restoreCollisionChecks(playerRef, snapshot));
        tryApply("restore Intangible component", applied, errors, () -> restoreComponent(playerRef, Intangible.getComponentType(), snapshot.previousIntangible()));
        tryApply("restore Invulnerable component", applied, errors, () -> restoreComponent(playerRef, Invulnerable.getComponentType(), snapshot.previousInvulnerable()));
    }

    private MovementManager movementManager(PlayerRef playerRef) {
        Ref<EntityStore> ref = requireRef(playerRef);
        MovementManager movementManager = ref.getStore().getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            throw new IllegalStateException("MovementManager is not available on the player entity.");
        }
        return movementManager;
    }

    private InteractionManager interactionManager(PlayerRef playerRef) {
        InteractionModule interactionModule = InteractionModule.get();
        if (interactionModule == null) {
            throw new IllegalStateException("InteractionModule is not available.");
        }
        Ref<EntityStore> ref = requireRef(playerRef);
        InteractionManager interactionManager = ref.getStore().getComponent(ref, interactionModule.getInteractionManagerComponent());
        if (interactionManager == null) {
            throw new IllegalStateException("InteractionManager is not available on the player entity.");
        }
        return interactionManager;
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

    private List<String> prefix(String prefix, List<String> values) {
        List<String> prefixed = new ArrayList<>();
        for (String value : values) {
            prefixed.add(prefix + value);
        }
        return prefixed;
    }

    public record ProbeResult(
        @Nonnull UUID playerUuid,
        SpectatorRuntimeMode mode,
        boolean restore,
        @Nonnull List<String> applied,
        @Nonnull List<String> skipped,
        @Nonnull List<String> warnings,
        @Nonnull List<String> errors
    ) {
        public boolean succeeded() {
            return errors.isEmpty();
        }

        public String summary() {
            StringBuilder builder = new StringBuilder();
            builder.append(restore ? "Nexori spectator probe restore" : "Nexori spectator probe");
            if (mode != null) {
                builder.append(" ").append(mode);
            }
            builder.append(succeeded() ? " completed." : " completed with warnings.");
            appendList(builder, " Applied: ", applied);
            appendList(builder, " Skipped: ", skipped);
            appendList(builder, " Warnings: ", warnings);
            appendList(builder, " Errors: ", errors);
            return builder.toString();
        }

        private static void appendList(StringBuilder builder, String label, List<String> values) {
            if (values.isEmpty()) {
                return;
            }
            builder.append(label).append(String.join("; ", values)).append(".");
        }
    }
}
