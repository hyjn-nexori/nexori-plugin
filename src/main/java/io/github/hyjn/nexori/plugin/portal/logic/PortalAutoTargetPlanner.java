package io.github.hyjn.nexori.plugin.portal.logic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

public final class PortalAutoTargetPlanner {

    private static final Gson GSON = new Gson();

    @Nonnull
    public static String buildAutoDestinationTargetId(@Nonnull String worldName, int x, int y, int z) {
        return (worldName + ".portal." + x + "_" + y + "_" + z).toLowerCase();
    }

    @Nonnull
    public static String buildPortalTargetMetadataJson(int x, int y, int z, float pitch, float yaw, float roll) {
        JsonObject root = new JsonObject();
        JsonObject position = new JsonObject();
        position.addProperty("x", x + 0.5);
        position.addProperty("y", y + 1.0);
        position.addProperty("z", z + 0.5);
        root.add("position", position);

        JsonObject rotationObject = new JsonObject();
        rotationObject.addProperty("pitch", pitch);
        rotationObject.addProperty("yaw", yaw);
        rotationObject.addProperty("roll", roll);
        root.add("rotation", rotationObject);
        return GSON.toJson(root);
    }
}
