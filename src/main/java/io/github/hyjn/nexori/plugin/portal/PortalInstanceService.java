package io.github.hyjn.nexori.plugin.portal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists placed Nexori portals and keeps their auto-generated destination targets in sync.
 */
public final class PortalInstanceService {

    private static final Gson GSON = new Gson();

    private final PortalInstanceStore store;
    private final DestinationTargetService destinationTargetService;
    private final TriggerBindingService triggerBindingService;
    private final DiagnosticsService diagnosticsService;
    private final Map<String, PortalInstanceDefinition> portalsById = new LinkedHashMap<>();

    /**
     * Loads the saved portal registry for this server.
     */
    public PortalInstanceService(
        @Nonnull PortalInstanceStore store,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull TriggerBindingService triggerBindingService,
        @Nonnull DiagnosticsService diagnosticsService
    ) throws IOException {
        this.store = store;
        this.destinationTargetService = destinationTargetService;
        this.triggerBindingService = triggerBindingService;
        this.diagnosticsService = diagnosticsService;
        for (PortalInstanceDefinition portal : store.loadOrCreate()) {
            PortalInstanceDefinition normalized = portal.normalized();
            portalsById.put(normalized.portalId(), normalized);
        }
    }

    /**
     * Lists every placed portal registered on this server.
     */
    @Nonnull
    public synchronized List<PortalInstanceDefinition> list() {
        return portalsById.values().stream()
            .sorted(Comparator.comparing(PortalInstanceDefinition::worldName).thenComparingInt(PortalInstanceDefinition::blockX))
            .toList();
    }

    /**
     * Finds a portal by its stable Nexori portal id.
     */
    @Nonnull
    public synchronized Optional<PortalInstanceDefinition> findById(@Nonnull String portalId) {
        if (portalId == null || portalId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(portalsById.get(portalId.trim().toLowerCase()));
    }

    /**
     * Finds the portal that owns a specific auto-generated destination target.
     */
    @Nonnull
    public synchronized Optional<PortalInstanceDefinition> findByAutoDestinationTargetId(@Nonnull String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return Optional.empty();
        }
        String normalizedTargetId = targetId.trim().toLowerCase();
        return portalsById.values().stream()
            .filter(portal -> portal.autoDestinationTargetId().equals(normalizedTargetId))
            .findFirst();
    }

    /**
     * Finds a portal placed at the exact block location in the given world.
     */
    @Nonnull
    public synchronized Optional<PortalInstanceDefinition> findByLocation(@Nonnull String worldName, @Nonnull Vector3i blockPosition) {
        String locationKey = PortalInstanceDefinition.locationKey(worldName, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
        return portalsById.values().stream()
            .filter(portal -> portal.locationKey().equals(locationKey))
            .findFirst();
    }

    /**
     * Finds the nearest portal around a block location inside the provided search radius.
     */
    @Nonnull
    public synchronized Optional<PortalInstanceDefinition> findNearestByLocation(
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        int horizontalRadius,
        int verticalRadius
    ) {
        String normalizedWorldName = worldName.trim().toLowerCase();
        PortalInstanceDefinition bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (PortalInstanceDefinition portal : portalsById.values()) {
            if (!portal.worldName().equals(normalizedWorldName)) {
                continue;
            }

            int dx = Math.abs(portal.blockX() - blockPosition.getX());
            int dy = Math.abs(portal.blockY() - blockPosition.getY());
            int dz = Math.abs(portal.blockZ() - blockPosition.getZ());
            if (dx > horizontalRadius || dy > verticalRadius || dz > horizontalRadius) {
                continue;
            }

            int distance = dx + dy + dz;
            if (bestMatch == null || distance < bestDistance) {
                bestMatch = portal;
                bestDistance = distance;
            }
        }

        return Optional.ofNullable(bestMatch);
    }

    /**
     * Registers or refreshes a placed portal and the automatic arrival target linked to it.
     */
    @Nonnull
    public synchronized PortalInstanceDefinition registerPlacedPortal(
        @Nonnull String worldName,
        @Nonnull Vector3i blockPosition,
        @Nonnull Vector3f arrivalRotation
    ) throws IOException {
        long now = System.currentTimeMillis();
        String normalizedWorldName = worldName.trim().toLowerCase();
        String autoTargetId = buildAutoDestinationTargetId(normalizedWorldName, blockPosition);
        DestinationTargetDefinition autoTarget = new DestinationTargetDefinition(
            autoTargetId,
            "Portal Arrival (" + normalizedWorldName + ")",
            DestinationTargetKind.PORTAL,
            normalizedWorldName,
            "portal_entry",
            "",
            buildPortalTargetMetadataJson(blockPosition, arrivalRotation)
        );
        destinationTargetService.upsert(autoTarget);

        PortalInstanceDefinition existing = findByLocation(normalizedWorldName, blockPosition).orElse(null);
        PortalInstanceDefinition portal = existing == null
            ? new PortalInstanceDefinition(
                UUID.randomUUID().toString().toLowerCase(),
                "Portal @ " + normalizedWorldName + " (" + blockPosition.getX() + ", " + blockPosition.getY() + ", " + blockPosition.getZ() + ")",
                normalizedWorldName,
                blockPosition.getX(),
                blockPosition.getY(),
                blockPosition.getZ(),
                autoTargetId,
                true,
                now,
                now
            ).normalized()
            : new PortalInstanceDefinition(
                existing.portalId(),
                existing.displayName(),
                normalizedWorldName,
                blockPosition.getX(),
                blockPosition.getY(),
                blockPosition.getZ(),
                autoTargetId,
                existing.enabled(),
                existing.createdAtEpochMillis(),
                now
            ).normalized();
        portalsById.put(portal.portalId(), portal);
        persist();
        recordPortalChange(portal, DiagnosticsAction.CONFIG_PORTAL_SAVE, DiagnosticsReasonCode.PORTAL_SAVED, "UPSERTED", "Saved a portal instance on this server.");
        return portal;
    }

    /**
     * Removes a placed portal and any Nexori state generated for it.
     */
    public synchronized boolean removePlacedPortal(@Nonnull String worldName, @Nonnull Vector3i blockPosition) throws IOException {
        PortalInstanceDefinition portal = findByLocation(worldName, blockPosition).orElse(null);
        if (portal == null) {
            return false;
        }

        portalsById.remove(portal.portalId());
        if (!portal.autoDestinationTargetId().isBlank()) {
            destinationTargetService.remove(portal.autoDestinationTargetId());
        }
        triggerBindingService.removeBindingsForSource(portal.portalId());
        persist();
        recordPortalChange(portal, DiagnosticsAction.CONFIG_PORTAL_DELETE, DiagnosticsReasonCode.PORTAL_DELETED, "DELETED", "Deleted a portal instance from this server.");
        return true;
    }

    /**
     * Renames a placed portal and its paired destination target.
     */
    @Nonnull
    public synchronized PortalInstanceDefinition renamePortalAndTarget(
        @Nonnull String targetId,
        @Nonnull String displayName
    ) throws IOException {
        String normalizedTargetId = targetId.trim().toLowerCase();
        String normalizedDisplayName = displayName.trim();
        if (normalizedTargetId.isBlank()) {
            throw new IllegalArgumentException("Portal target id cannot be blank.");
        }
        if (normalizedDisplayName.isBlank()) {
            throw new IllegalArgumentException("Portal display name cannot be blank.");
        }

        PortalInstanceDefinition current = findByAutoDestinationTargetId(normalizedTargetId)
            .orElseThrow(() -> new IllegalArgumentException("That destination target is not linked to a Nexori portal."));
        DestinationTargetDefinition target = destinationTargetService.find(normalizedTargetId)
            .orElseThrow(() -> new IllegalArgumentException("That Nexori destination target does not exist."));

        DestinationTargetDefinition updatedTarget = new DestinationTargetDefinition(
            target.id(),
            normalizedDisplayName,
            target.kind(),
            target.worldName(),
            target.arrivalPointId(),
            target.arrivalMessage(),
            target.metadataJson()
        );
        destinationTargetService.upsert(updatedTarget);

        PortalInstanceDefinition updatedPortal = new PortalInstanceDefinition(
            current.portalId(),
            normalizedDisplayName,
            current.worldName(),
            current.blockX(),
            current.blockY(),
            current.blockZ(),
            current.autoDestinationTargetId(),
            current.enabled(),
            current.createdAtEpochMillis(),
            System.currentTimeMillis()
        ).normalized();
        portalsById.put(updatedPortal.portalId(), updatedPortal);
        persist();
        recordPortalChange(updatedPortal, DiagnosticsAction.CONFIG_PORTAL_SAVE, DiagnosticsReasonCode.PORTAL_SAVED, "UPSERTED", "Saved a portal instance on this server.");
        return updatedPortal;
    }

    /**
     * Enables or disables a placed portal.
     */
    @Nonnull
    public synchronized PortalInstanceDefinition setEnabled(@Nonnull String portalId, boolean enabled) throws IOException {
        PortalInstanceDefinition current = findById(portalId)
            .orElseThrow(() -> new IllegalArgumentException("That Nexori portal does not exist."));
        PortalInstanceDefinition updated = new PortalInstanceDefinition(
            current.portalId(),
            current.displayName(),
            current.worldName(),
            current.blockX(),
            current.blockY(),
            current.blockZ(),
            current.autoDestinationTargetId(),
            enabled,
            current.createdAtEpochMillis(),
            System.currentTimeMillis()
        ).normalized();
        portalsById.put(updated.portalId(), updated);
        persist();
        recordPortalChange(updated, DiagnosticsAction.CONFIG_PORTAL_SAVE, DiagnosticsReasonCode.PORTAL_SAVED, "UPSERTED", "Saved a portal instance on this server.");
        return updated;
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(portalsById.values()));
    }

    private void recordPortalChange(
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String action,
        @Nonnull String reasonCode,
        @Nonnull String changeType,
        @Nonnull String message
    ) {
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            action,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            reasonCode,
            message,
            operationId,
            event -> event
                .entityType("PORTAL")
                .entityId(portal.portalId())
                .changeType(changeType)
                .portalId(portal.portalId())
                .portalDisplayName(portal.displayName())
                .targetId(portal.autoDestinationTargetId())
                .worldName(portal.worldName())
                .addPreview("portalId", portal.portalId())
                .addPreview("world", portal.worldName())
        );
    }

    @Nonnull
    public static String buildAutoDestinationTargetId(@Nonnull String worldName, @Nonnull Vector3i blockPosition) {
        return (worldName + ".portal." + blockPosition.getX() + "_" + blockPosition.getY() + "_" + blockPosition.getZ())
            .toLowerCase();
    }

    @Nonnull
    private static String buildPortalTargetMetadataJson(@Nonnull Vector3i blockPosition, @Nonnull Vector3f rotation) {
        JsonObject root = new JsonObject();
        JsonObject position = new JsonObject();
        position.addProperty("x", blockPosition.getX() + 0.5);
        position.addProperty("y", blockPosition.getY() + 1.0);
        position.addProperty("z", blockPosition.getZ() + 0.5);
        root.add("position", position);

        JsonObject rotationObject = new JsonObject();
        rotationObject.addProperty("pitch", rotation.x);
        rotationObject.addProperty("yaw", rotation.y);
        rotationObject.addProperty("roll", rotation.z);
        root.add("rotation", rotationObject);
        return GSON.toJson(root);
    }
}
