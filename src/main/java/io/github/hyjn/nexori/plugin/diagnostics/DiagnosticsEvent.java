package io.github.hyjn.nexori.plugin.diagnostics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DiagnosticsEvent(
    int schemaVersion,
    @Nonnull String eventId,
    long occurredAtEpochMs,
    long sourceSequence,
    @Nonnull String sourceServerId,
    @Nullable String sourceConnectionAddress,
    @Nullable String pluginVersion,
    @Nonnull DiagnosticsCategory category,
    @Nonnull String action,
    @Nonnull DiagnosticsOutcome outcome,
    @Nonnull DiagnosticsReasonClass reasonClass,
    @Nonnull String reasonCode,
    @Nonnull String message,
    @Nonnull String operationId,
    @Nullable Long durationMs,
    @Nullable String payloadType,
    @Nullable String payloadHash,
    @Nullable Map<String, String> payloadPreview,
    @Nullable String playerUuid,
    @Nullable String playerNameClaimed,
    @Nullable String remoteServerId,
    @Nullable String remoteConnectionAddress,
    @Nullable String portalId,
    @Nullable String bindingId,
    @Nullable String targetId,
    @Nullable String arrivalPointId,
    @Nullable String targetKind,
    @Nullable String worldName,
    @Nullable String travelProfileId,
    @Nullable String transferId,
    @Nullable String requestId,
    @Nullable String sessionId,
    @Nullable Long bundleVersion,
    @Nullable String bundleHash,
    @Nullable String ruleGroupId,
    @Nullable String entityType,
    @Nullable String entityId,
    @Nullable String changeType,
    @Nullable List<String> tags,
    boolean truncated,
    @Nullable List<String> truncatedFields
) {

    public static final int SCHEMA_VERSION = 1;
    private static final DateTimeFormatter EVENT_ISO = DateTimeFormatter.ISO_INSTANT;

    @Nonnull
    public String occurredAtIso() {
        return EVENT_ISO.format(Instant.ofEpochMilli(occurredAtEpochMs));
    }

    @Nonnull
    public String correlationId() {
        return operationId;
    }

    @Nonnull
    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }

    @Nonnull
    public List<String> truncatedFieldsOrEmpty() {
        return truncatedFields == null ? List.of() : truncatedFields;
    }

    @Nonnull
    public Builder toBuilder() {
        return new Builder()
            .schemaVersion(schemaVersion)
            .eventId(eventId)
            .occurredAtEpochMs(occurredAtEpochMs)
            .sourceSequence(sourceSequence)
            .sourceServerId(sourceServerId)
            .sourceConnectionAddress(sourceConnectionAddress)
            .pluginVersion(pluginVersion)
            .category(category)
            .action(action)
            .outcome(outcome)
            .reasonClass(reasonClass)
            .reasonCode(reasonCode)
            .message(message)
            .operationId(operationId)
            .durationMs(durationMs)
            .payloadType(payloadType)
            .payloadHash(payloadHash)
            .payloadPreview(payloadPreview)
            .playerUuid(playerUuid)
            .playerNameClaimed(playerNameClaimed)
            .remoteServerId(remoteServerId)
            .remoteConnectionAddress(remoteConnectionAddress)
            .portalId(portalId)
            .bindingId(bindingId)
            .targetId(targetId)
            .arrivalPointId(arrivalPointId)
            .targetKind(targetKind)
            .worldName(worldName)
            .travelProfileId(travelProfileId)
            .transferId(transferId)
            .requestId(requestId)
            .sessionId(sessionId)
            .bundleVersion(bundleVersion)
            .bundleHash(bundleHash)
            .ruleGroupId(ruleGroupId)
            .entityType(entityType)
            .entityId(entityId)
            .changeType(changeType)
            .tags(tags)
            .truncated(truncated)
            .truncatedFields(truncatedFields);
    }

    private static String optionalString(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String optionalPluginVersion(String value) {
        String normalized = optionalString(value);
        return normalized != null && normalized.equalsIgnoreCase("unknown") ? null : normalized;
    }

    private static <K, V> Map<K, V> optionalMap(Map<K, V> value) {
        return value == null || value.isEmpty() ? null : Map.copyOf(value);
    }

    private static <T> List<T> optionalList(List<T> value) {
        return value == null || value.isEmpty() ? null : List.copyOf(value);
    }

    public static final class Builder {
        private int schemaVersion = SCHEMA_VERSION;
        private String eventId = "";
        private long occurredAtEpochMs;
        private long sourceSequence;
        private String sourceServerId = "";
        private String sourceConnectionAddress;
        private String pluginVersion;
        private DiagnosticsCategory category = DiagnosticsCategory.SECURITY;
        private String action = "";
        private DiagnosticsOutcome outcome = DiagnosticsOutcome.FAILED;
        private DiagnosticsReasonClass reasonClass = DiagnosticsReasonClass.UNKNOWN;
        private String reasonCode = "";
        private String message = "";
        private String operationId = "";
        private Long durationMs;
        private String payloadType;
        private String payloadHash;
        private Map<String, String> payloadPreview;
        private String playerUuid;
        private String playerNameClaimed;
        private String remoteServerId;
        private String remoteConnectionAddress;
        private String portalId;
        private String bindingId;
        private String targetId;
        private String arrivalPointId;
        private String targetKind;
        private String worldName;
        private String travelProfileId;
        private String transferId;
        private String requestId;
        private String sessionId;
        private Long bundleVersion;
        private String bundleHash;
        private String ruleGroupId;
        private String entityType;
        private String entityId;
        private String changeType;
        private List<String> tags;
        private boolean truncated;
        private List<String> truncatedFields;

        @Nonnull public Builder schemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        @Nonnull public Builder eventId(@Nonnull String eventId) { this.eventId = eventId; return this; }
        @Nonnull public Builder occurredAtEpochMs(long occurredAtEpochMs) { this.occurredAtEpochMs = occurredAtEpochMs; return this; }
        @Nonnull public Builder sourceSequence(long sourceSequence) { this.sourceSequence = sourceSequence; return this; }
        @Nonnull public Builder sourceServerId(@Nonnull String sourceServerId) { this.sourceServerId = sourceServerId; return this; }
        @Nonnull public Builder sourceConnectionAddress(@Nullable String sourceConnectionAddress) { this.sourceConnectionAddress = sourceConnectionAddress; return this; }
        @Nonnull public Builder pluginVersion(@Nullable String pluginVersion) { this.pluginVersion = pluginVersion; return this; }
        @Nonnull public Builder category(@Nonnull DiagnosticsCategory category) { this.category = category; return this; }
        @Nonnull public Builder action(@Nonnull String action) { this.action = action; return this; }
        @Nonnull public Builder outcome(@Nonnull DiagnosticsOutcome outcome) { this.outcome = outcome; return this; }
        @Nonnull public Builder reasonClass(@Nonnull DiagnosticsReasonClass reasonClass) { this.reasonClass = reasonClass; return this; }
        @Nonnull public Builder reasonCode(@Nonnull String reasonCode) { this.reasonCode = reasonCode; return this; }
        @Nonnull public Builder message(@Nonnull String message) { this.message = message; return this; }
        @Nonnull public Builder operationId(@Nonnull String operationId) { this.operationId = operationId; return this; }
        @Nonnull public Builder durationMs(@Nullable Long durationMs) { this.durationMs = durationMs; return this; }
        @Nonnull public Builder payloadType(@Nullable String payloadType) { this.payloadType = payloadType; return this; }
        @Nonnull public Builder payloadHash(@Nullable String payloadHash) { this.payloadHash = payloadHash; return this; }
        @Nonnull public Builder payloadPreview(@Nullable Map<String, String> payloadPreview) {
            this.payloadPreview = payloadPreview == null ? null : new LinkedHashMap<>(payloadPreview);
            return this;
        }
        @Nonnull public Builder addPreview(@Nonnull String key, @Nonnull String value) {
            if (this.payloadPreview == null) {
                this.payloadPreview = new LinkedHashMap<>();
            }
            this.payloadPreview.put(key, value);
            return this;
        }
        @Nonnull public Builder playerUuid(@Nullable String playerUuid) { this.playerUuid = playerUuid; return this; }
        @Nonnull public Builder playerNameClaimed(@Nullable String playerNameClaimed) { this.playerNameClaimed = playerNameClaimed; return this; }
        @Nonnull public Builder remoteServerId(@Nullable String remoteServerId) { this.remoteServerId = remoteServerId; return this; }
        @Nonnull public Builder remoteConnectionAddress(@Nullable String remoteConnectionAddress) { this.remoteConnectionAddress = remoteConnectionAddress; return this; }
        @Nonnull public Builder portalId(@Nullable String portalId) { this.portalId = portalId; return this; }
        @Nonnull public Builder bindingId(@Nullable String bindingId) { this.bindingId = bindingId; return this; }
        @Nonnull public Builder targetId(@Nullable String targetId) { this.targetId = targetId; return this; }
        @Nonnull public Builder arrivalPointId(@Nullable String arrivalPointId) { this.arrivalPointId = arrivalPointId; return this; }
        @Nonnull public Builder targetKind(@Nullable String targetKind) { this.targetKind = targetKind; return this; }
        @Nonnull public Builder worldName(@Nullable String worldName) { this.worldName = worldName; return this; }
        @Nonnull public Builder travelProfileId(@Nullable String travelProfileId) { this.travelProfileId = travelProfileId; return this; }
        @Nonnull public Builder transferId(@Nullable String transferId) { this.transferId = transferId; return this; }
        @Nonnull public Builder requestId(@Nullable String requestId) { this.requestId = requestId; return this; }
        @Nonnull public Builder sessionId(@Nullable String sessionId) { this.sessionId = sessionId; return this; }
        @Nonnull public Builder bundleVersion(@Nullable Long bundleVersion) { this.bundleVersion = bundleVersion; return this; }
        @Nonnull public Builder bundleHash(@Nullable String bundleHash) { this.bundleHash = bundleHash; return this; }
        @Nonnull public Builder ruleGroupId(@Nullable String ruleGroupId) { this.ruleGroupId = ruleGroupId; return this; }
        @Nonnull public Builder entityType(@Nullable String entityType) { this.entityType = entityType; return this; }
        @Nonnull public Builder entityId(@Nullable String entityId) { this.entityId = entityId; return this; }
        @Nonnull public Builder changeType(@Nullable String changeType) { this.changeType = changeType; return this; }
        @Nonnull public Builder tags(@Nullable List<String> tags) { this.tags = tags == null ? null : new ArrayList<>(tags); return this; }
        @Nonnull public Builder addTag(@Nonnull String tag) {
            if (this.tags == null) {
                this.tags = new ArrayList<>();
            }
            this.tags.add(tag);
            return this;
        }
        @Nonnull public Builder truncated(boolean truncated) { this.truncated = truncated; return this; }
        @Nonnull public Builder truncatedFields(@Nullable List<String> truncatedFields) {
            this.truncatedFields = truncatedFields == null ? null : new ArrayList<>(truncatedFields);
            return this;
        }

        @Nonnull
        public DiagnosticsEvent build() {
            return new DiagnosticsEvent(
                schemaVersion,
                eventId,
                occurredAtEpochMs,
                sourceSequence,
                sourceServerId,
                optionalString(sourceConnectionAddress),
                optionalPluginVersion(pluginVersion),
                category,
                action,
                outcome,
                reasonClass,
                reasonCode,
                message == null ? "" : message.trim(),
                operationId,
                durationMs,
                optionalString(payloadType),
                optionalString(payloadHash),
                optionalMap(payloadPreview),
                optionalString(playerUuid),
                optionalString(playerNameClaimed),
                optionalString(remoteServerId),
                optionalString(remoteConnectionAddress),
                optionalString(portalId),
                optionalString(bindingId),
                optionalString(targetId),
                optionalString(arrivalPointId),
                optionalString(targetKind),
                optionalString(worldName),
                optionalString(travelProfileId),
                optionalString(transferId),
                optionalString(requestId),
                optionalString(sessionId),
                bundleVersion,
                optionalString(bundleHash),
                optionalString(ruleGroupId),
                optionalString(entityType),
                optionalString(entityId),
                optionalString(changeType),
                optionalList(tags),
                truncated,
                optionalList(truncatedFields)
            );
        }
    }
}
