package io.github.hyjn.nexori.plugin.diagnostics.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;

public final class DiagnosticsEventTruncationPolicy {

    public static final int MAX_EVENT_BYTES = 2048;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Nonnull
    public DiagnosticsEvent apply(@Nonnull DiagnosticsEvent event) {
        DiagnosticsEvent current = event;
        if (serializedSize(current) <= MAX_EVENT_BYTES) {
            return current;
        }

        LinkedHashSet<String> truncatedFields = new LinkedHashSet<>(current.truncatedFieldsOrEmpty());
        if (current.payloadPreview() != null && !current.payloadPreview().isEmpty()) {
            truncatedFields.add("payloadPreview");
            current = current.toBuilder()
                .payloadPreview(null)
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        while (serializedSize(current) > MAX_EVENT_BYTES && !current.message().isEmpty()) {
            truncatedFields.add("message");
            current = current.toBuilder()
                .message(shrinkMessage(current.message()))
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        if (serializedSize(current) > MAX_EVENT_BYTES && !current.tagsOrEmpty().isEmpty()) {
            truncatedFields.add("tags");
            current = current.toBuilder()
                .tags(null)
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        if (serializedSize(current) > MAX_EVENT_BYTES) {
            current = current.toBuilder()
                .message("Diagnostics event truncated.")
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        return current;
    }

    public int serializedSize(@Nonnull DiagnosticsEvent event) {
        return GSON.toJson(event).getBytes(StandardCharsets.UTF_8).length;
    }

    @Nonnull
    String shrinkMessage(@Nonnull String message) {
        if (message.length() > 512) {
            return message.substring(0, 512);
        }
        if (message.length() > 256) {
            return message.substring(0, 256);
        }
        if (message.length() > 128) {
            return message.substring(0, 128);
        }
        if (message.length() > 64) {
            return message.substring(0, 64);
        }
        return "";
    }
}
