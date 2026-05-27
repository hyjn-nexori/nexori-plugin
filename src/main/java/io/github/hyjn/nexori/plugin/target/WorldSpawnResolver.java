package io.github.hyjn.nexori.plugin.target;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class WorldSpawnResolver {

    private static final Gson GSON = new Gson();

    private WorldSpawnResolver() {
    }

    @Nonnull
    public static Optional<Transform> resolveConfiguredSpawn(@Nonnull World world) {
        return resolveConfiguredSpawn(world.getSavePath().resolve("config.json"));
    }

    @Nonnull
    public static Optional<Transform> resolveConfiguredSpawn(@Nonnull Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("SpawnProvider")) {
                return Optional.empty();
            }

            JsonObject spawnProvider = root.getAsJsonObject("SpawnProvider");
            if (spawnProvider == null || !spawnProvider.has("SpawnPoint")) {
                return Optional.empty();
            }

            JsonObject spawnPoint = spawnProvider.getAsJsonObject("SpawnPoint");
            if (spawnPoint == null) {
                return Optional.empty();
            }

            return Optional.of(new Transform(
                new Vector3d(
                    getDouble(spawnPoint, "X"),
                    getDouble(spawnPoint, "Y"),
                    getDouble(spawnPoint, "Z")
                ),
                new Rotation3f(
                    (float) getDouble(spawnPoint, "Pitch"),
                    (float) getDouble(spawnPoint, "Yaw"),
                    (float) getDouble(spawnPoint, "Roll")
                )
            ));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static double getDouble(@Nonnull JsonObject object, @Nonnull String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0;
    }
}
