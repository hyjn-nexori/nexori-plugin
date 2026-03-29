package io.github.hyjn.nexori.plugin.portal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
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

public final class PortalInstanceService {

    private static final Gson GSON = new Gson();

    private final PortalInstanceStore store;
    private final DestinationTargetService destinationTargetService;
    private final TriggerBindingService triggerBindingService;
    private final Map<String, PortalInstanceDefinition> portalsById = new LinkedHashMap<>();

    public PortalInstanceService(
        @Nonnull PortalInstanceStore store,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull TriggerBindingService triggerBindingService
    ) throws IOException {
        this.store = store;
        this.destinationTargetService = destinationTargetService;
        this.triggerBindingService = triggerBindingService;
        for (PortalInstanceDefinition portal : store.loadOrCreate()) {
            PortalInstanceDefinition normalized = portal.normalized();
            portalsById.put(normalized.portalId(), normalized);
        }
    }

    @Nonnull
    public synchronized List<PortalInstanceDefinition> list() {
        return portalsById.values().stream()
            .sorted(Comparator.comparing(PortalInstanceDefinition::worldName).thenComparingInt(PortalInstanceDefinition::blockX))
            .toList();
    }

    @Nonnull
    public synchronized Optional<PortalInstanceDefinition> findById(@Nonnull String portalId) {
        if (portalId == null || portalId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(portalsById.get(portalId.trim().toLowerCase()));
    }

    @Nonnull
    public synchronized Optional<PortalInstanceDefinition> findByLocation(@Nonnull String worldName, @Nonnull Vector3i blockPosition) {
        String locationKey = PortalInstanceDefinition.locationKey(worldName, blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
        return portalsById.values().stream()
            .filter(portal -> portal.locationKey().equals(locationKey))
            .findFirst();
    }

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
        return portal;
    }

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
        return true;
    }

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
        return updated;
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(portalsById.values()));
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
