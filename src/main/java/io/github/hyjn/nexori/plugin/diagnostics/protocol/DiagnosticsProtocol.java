package io.github.hyjn.nexori.plugin.diagnostics.protocol;

import javax.annotation.Nonnull;

public final class DiagnosticsProtocol {

    public static final String COLLECT_MANIFEST_REQUEST = "diagnostics.collect.manifest.request";
    public static final String COLLECT_MANIFEST_RESPONSE = "diagnostics.collect.manifest.response";
    public static final String COLLECT_CHUNK_REQUEST = "diagnostics.collect.chunk.request";
    public static final String COLLECT_CHUNK_RESPONSE = "diagnostics.collect.chunk.response";
    public static final String COLLECT_ERROR = "diagnostics.collect.error";
    public static final String SNAPSHOT_REQUEST = "diagnostics.snapshot.request";
    public static final String SNAPSHOT_RESPONSE = "diagnostics.snapshot.response";

    private static final String COLLECT_PREFIX = "diagnostics.collect.";
    private static final String SNAPSHOT_PREFIX = "diagnostics.snapshot.";

    private DiagnosticsProtocol() {
    }

    public static boolean isCollectPayloadType(@Nonnull String payloadType) {
        return payloadType.startsWith(COLLECT_PREFIX);
    }

    public static boolean isSnapshotPayloadType(@Nonnull String payloadType) {
        return payloadType.startsWith(SNAPSHOT_PREFIX);
    }

    public static boolean isOperationalOnlyPayloadType(@Nonnull String payloadType) {
        return isCollectPayloadType(payloadType) || isSnapshotPayloadType(payloadType);
    }
}
