package io.github.hyjn.nexori.plugin.portal.logic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalAutoTargetPlannerTest {

    // ── buildAutoDestinationTargetId ──────────────────────────────────────────

    @Test
    void buildAutoDestinationTargetIdFormat() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("overworld", 10, 64, -5);

        assertEquals("overworld.portal.10_64_-5", id);
    }

    @Test
    void buildAutoDestinationTargetIdLowercasesWorldName() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("OverWorld", 0, 0, 0);

        assertTrue(id.startsWith("overworld."));
    }

    @Test
    void buildAutoDestinationTargetIdNegativeCoordinates() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("nether", -100, -64, -200);

        assertEquals("nether.portal.-100_-64_-200", id);
    }

    @Test
    void buildAutoDestinationTargetIdZeroCoordinates() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("world", 0, 0, 0);

        assertEquals("world.portal.0_0_0", id);
    }

    @Test
    void buildAutoDestinationTargetIdAlreadyLowercaseWorldNameUnchanged() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("the_end", 5, 60, 5);

        assertEquals("the_end.portal.5_60_5", id);
    }

    // ── buildPortalTargetMetadataJson ─────────────────────────────────────────

    @Test
    void buildPortalTargetMetadataJsonContainsPositionKey() {
        String json = PortalAutoTargetPlanner.buildPortalTargetMetadataJson(10, 64, -5, 0f, 0f, 0f);

        assertTrue(json.contains("position"));
    }

    @Test
    void buildPortalTargetMetadataJsonContainsRotationKey() {
        String json = PortalAutoTargetPlanner.buildPortalTargetMetadataJson(0, 0, 0, 10f, 90f, 0f);

        assertTrue(json.contains("rotation"));
    }

    @Test
    void buildPortalTargetMetadataJsonPositionOffsetApplied() {
        String json = PortalAutoTargetPlanner.buildPortalTargetMetadataJson(0, 0, 0, 0f, 0f, 0f);

        assertTrue(json.contains("0.5"), "x and z should be offset by +0.5: " + json);
        assertTrue(json.contains("1.0"), "y should be offset by +1.0: " + json);
    }

    @Test
    void buildPortalTargetMetadataJsonContainsPitchYawRoll() {
        String json = PortalAutoTargetPlanner.buildPortalTargetMetadataJson(0, 0, 0, 5f, 10f, 0f);

        assertTrue(json.contains("pitch"));
        assertTrue(json.contains("yaw"));
        assertTrue(json.contains("roll"));
    }

    @Test
    void buildPortalTargetMetadataJsonWritesExactPositionAndRotationValues() {
        int x = 10, y = 64, z = -5;
        float pitch = 5f, yaw = 90f, roll = 0f;

        String json = PortalAutoTargetPlanner.buildPortalTargetMetadataJson(x, y, z, pitch, yaw, roll);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject position = root.getAsJsonObject("position");
        JsonObject rotation = root.getAsJsonObject("rotation");

        assertEquals(x + 0.5, position.get("x").getAsDouble(), 1e-9);
        assertEquals(y + 1.0, position.get("y").getAsDouble(), 1e-9);
        assertEquals(z + 0.5, position.get("z").getAsDouble(), 1e-9);
        assertEquals(pitch, rotation.get("pitch").getAsFloat(), 1e-6f);
        assertEquals(yaw,   rotation.get("yaw").getAsFloat(),   1e-6f);
        assertEquals(roll,  rotation.get("roll").getAsFloat(),  1e-6f);
    }
}
