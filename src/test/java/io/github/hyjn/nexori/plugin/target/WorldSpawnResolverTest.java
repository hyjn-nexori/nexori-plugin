package io.github.hyjn.nexori.plugin.target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldSpawnResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void missingConfigFileReturnsEmpty() {
        Path configFile = tempDir.resolve("config.json");
        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);
        assertFalse(result.isPresent());
    }

    @Test
    void blankConfigFileReturnsEmptyAccordingToCurrentBehavior() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "   ", StandardCharsets.UTF_8);

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertFalse(result.isPresent(),
            "GSON.fromJson(blank, JsonObject.class) returns null; root == null check returns empty");
    }

    @Test
    void malformedJsonReturnsEmpty() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{ not valid json", StandardCharsets.UTF_8);

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertFalse(result.isPresent(),
            "JsonSyntaxException is caught by the outer catch(Exception) and returns empty");
    }

    @Test
    void missingSpawnProviderReturnsEmpty() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
            "{\"SomeOtherKey\":{}}",
            StandardCharsets.UTF_8
        );

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertFalse(result.isPresent());
    }

    @Test
    void missingSpawnPointReturnsEmpty() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
            "{\"SpawnProvider\":{\"SomeOtherKey\":{}}}",
            StandardCharsets.UTF_8
        );

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertFalse(result.isPresent());
    }

    @Test
    void spawnPointWithAllFieldsReturnsTransform() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
            "{\"SpawnProvider\":{\"SpawnPoint\":{\"X\":10.0,\"Y\":64.0,\"Z\":-5.0,\"Pitch\":0.0,\"Yaw\":90.0,\"Roll\":0.0}}}",
            StandardCharsets.UTF_8
        );

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertTrue(result.isPresent(),
            "Valid SpawnPoint with all fields should resolve to a non-empty Transform optional");
    }

    @Test
    void spawnPointMissingFieldsDefaultsToZero() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
            "{\"SpawnProvider\":{\"SpawnPoint\":{}}}",
            StandardCharsets.UTF_8
        );

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertTrue(result.isPresent(),
            "SpawnPoint with no fields is valid; getDouble() defaults missing keys to 0.0");
    }

    @Test
    void invalidNumberReturnsEmptyAccordingToCurrentBehavior() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
            "{\"SpawnProvider\":{\"SpawnPoint\":{\"X\":\"not-a-number\",\"Y\":64,\"Z\":0}}}",
            StandardCharsets.UTF_8
        );

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertFalse(result.isPresent(),
            "getAsDouble() on a non-numeric string throws; outer catch(Exception) returns empty");
    }

    @Test
    void extraFieldsIgnoredAccordingToCurrentBehavior() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile,
            "{\"UnknownKey\":true,\"SpawnProvider\":{\"SpawnPoint\":{\"X\":1.0,\"Y\":2.0,\"Z\":3.0},\"ExtraField\":99}}",
            StandardCharsets.UTF_8
        );

        Optional<?> result = WorldSpawnResolver.resolveConfiguredSpawn(configFile);

        assertTrue(result.isPresent(),
            "Extra JSON fields outside SpawnPoint are ignored by GSON.fromJson and getAsJsonObject");
    }
}
