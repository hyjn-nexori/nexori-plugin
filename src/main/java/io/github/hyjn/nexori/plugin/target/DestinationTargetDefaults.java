package io.github.hyjn.nexori.plugin.target;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.target.logic.DestinationTargetDefaultPlanner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class DestinationTargetDefaults {

    private final Path pluginDataDirectory;
    private final DestinationTargetService destinationTargetService;

    public DestinationTargetDefaults(
        @Nonnull Path pluginDataDirectory,
        @Nonnull DestinationTargetService destinationTargetService
    ) {
        this.pluginDataDirectory = pluginDataDirectory;
        this.destinationTargetService = destinationTargetService;
    }

    public void ensureDefaults(@Nonnull HytaleLogger logger) throws IOException {
        Path serverDirectory = findServerDirectory();
        if (serverDirectory == null) {
            logger.atWarning().log("Could not locate the Hytale Server directory from " + pluginDataDirectory + ".");
            return;
        }

        Path worldsDirectory = serverDirectory.resolve("universe").resolve("worlds");
        if (!Files.isDirectory(worldsDirectory)) {
            return;
        }

        try (Stream<Path> worlds = Files.list(worldsDirectory)) {
            worlds
                .filter(Files::isDirectory)
                .forEach(worldDirectory -> ensureNaturalSpawnTarget(logger, worldDirectory));
        }
    }

    private void ensureNaturalSpawnTarget(@Nonnull HytaleLogger logger, @Nonnull Path worldDirectory) {
        String worldName = worldDirectory.getFileName().toString();
        String targetId = DestinationTargetDefaultPlanner.targetId(worldName);
        try {
            DestinationTargetDefinition existing = destinationTargetService.find(targetId).orElse(null);
            DestinationTargetDefinition normalizedTarget = DestinationTargetDefaultPlanner.buildDefinition(worldName, existing);
            destinationTargetService.upsert(normalizedTarget, false);
            if (existing == null) {
                logger.atInfo().log("Registered Nexori natural spawn destination target " + targetId + ".");
            } else {
                logger.atInfo().log("Normalized Nexori natural spawn destination target " + targetId + ".");
            }
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to register default destination target " + targetId + ".");
        }
    }

    private Path findServerDirectory() {
        Path current = pluginDataDirectory.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("universe").resolve("worlds"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

}
