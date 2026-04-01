package io.github.hyjn.nexori.plugin.secure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class SecureReferralPayloadCodec {

    private static final Gson GSON = new GsonBuilder().create();
    private static final byte[] MAGIC = new byte[] {'N', 'X', 'S', '1'};

    @Nonnull
    public byte[] encode(@Nonnull SecureReferralEnvelope envelope) throws IOException {
        byte[] json = GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
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
    public Optional<SecureReferralEnvelope> tryDecode(byte[] payloadBytes) throws IOException {
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
        SecureReferralEnvelope envelope = GSON.fromJson(new String(json, StandardCharsets.UTF_8), SecureReferralEnvelope.class);
        return Optional.ofNullable(envelope);
    }

    @Nonnull
    public Optional<String> tryPeekPayloadType(byte[] payloadBytes) {
        try {
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
            JsonObject object = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            if (object.has("payloadType") && !object.get("payloadType").isJsonNull()) {
                return Optional.ofNullable(object.get("payloadType").getAsString());
            }
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
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
