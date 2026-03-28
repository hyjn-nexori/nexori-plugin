package io.github.hyjn.nexori.plugin.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class BootstrapPayloadCodec {

    private static final Gson GSON = new GsonBuilder().create();
    private static final byte[] MAGIC = new byte[] {'N', 'X', 'B', '1'};

    @Nonnull
    public byte[] encode(@Nonnull BootstrapReferralPayload payload) throws IOException {
        byte[] json = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        byte[] compressed = gzip(json);
        byte[] out = new byte[MAGIC.length + compressed.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(compressed, 0, out, MAGIC.length, compressed.length);
        if (out.length > 4096) {
            throw new IllegalArgumentException(out.length + " bytes exceeds the 4096 byte Hytale referral limit.");
        }
        return out;
    }

    @Nonnull
    public Optional<BootstrapReferralPayload> tryDecode(byte[] payloadBytes) throws IOException {
        if (payloadBytes == null || payloadBytes.length <= MAGIC.length) {
            return Optional.empty();
        }

        for (int i = 0; i < MAGIC.length; i++) {
            if (payloadBytes[i] != MAGIC[i]) {
                return Optional.empty();
            }
        }

        byte[] compressed = Arrays.copyOfRange(payloadBytes, MAGIC.length, payloadBytes.length);
        byte[] json = gunzip(compressed);
        BootstrapReferralPayload payload = GSON.fromJson(new String(json, StandardCharsets.UTF_8), BootstrapReferralPayload.class);
        return Optional.ofNullable(payload);
    }

    private static byte[] gzip(@Nonnull byte[] raw) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(raw);
        }
        return output.toByteArray();
    }

    private static byte[] gunzip(@Nonnull byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
