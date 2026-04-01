package io.github.hyjn.nexori.plugin.diagnostics.collect;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;

public record DiagnosticsCollectOriginSnapshot(
    @Nonnull String worldName,
    float x,
    float y,
    float z,
    float pitch,
    float yaw,
    float roll
) {
    @Nonnull
    public static DiagnosticsCollectOriginSnapshot capture(@Nonnull String worldName, @Nonnull Transform transform) {
        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        return new DiagnosticsCollectOriginSnapshot(
            worldName,
            (float) position.x,
            (float) position.y,
            (float) position.z,
            rotation.getX(),
            rotation.getY(),
            rotation.getZ()
        );
    }

    @Nonnull
    public Transform toTransform() {
        return new Transform(new Vector3d(x, y, z), new Vector3f(pitch, yaw, roll));
    }
}
