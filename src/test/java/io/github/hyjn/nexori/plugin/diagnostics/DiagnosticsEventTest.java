package io.github.hyjn.nexori.plugin.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class DiagnosticsEventTest {

    @Test
    void schemaVersionIsOne() {
        assertEquals(1, DiagnosticsEvent.SCHEMA_VERSION);
    }

    @Test
    void builderDefaultSchemaVersionMatchesConstant() {
        DiagnosticsEvent event = minimalBuilder().build();

        assertEquals(DiagnosticsEvent.SCHEMA_VERSION, event.schemaVersion());
    }

    @Test
    void preservesCategoryActionOutcome() {
        DiagnosticsEvent event = minimalBuilder()
            .category(DiagnosticsCategory.TRAVEL)
            .action(DiagnosticsAction.TRAVEL_DISPATCH)
            .outcome(DiagnosticsOutcome.SUCCEEDED)
            .build();

        assertEquals(DiagnosticsCategory.TRAVEL, event.category());
        assertEquals(DiagnosticsAction.TRAVEL_DISPATCH, event.action());
        assertEquals(DiagnosticsOutcome.SUCCEEDED, event.outcome());
    }

    @Test
    void preservesReasonClassAndReasonCode() {
        DiagnosticsEvent event = minimalBuilder()
            .reasonClass(DiagnosticsReasonClass.SECURITY)
            .reasonCode(DiagnosticsReasonCode.SIGNATURE_INVALID)
            .build();

        assertEquals(DiagnosticsReasonClass.SECURITY, event.reasonClass());
        assertEquals(DiagnosticsReasonCode.SIGNATURE_INVALID, event.reasonCode());
    }

    @Test
    void blankOptionalStringsBecomesNull() {
        DiagnosticsEvent event = minimalBuilder()
            .sourceConnectionAddress("   ")
            .playerUuid("   ")
            .remoteServerId("   ")
            .build();

        assertNull(event.sourceConnectionAddress());
        assertNull(event.playerUuid());
        assertNull(event.remoteServerId());
    }

    @Test
    void emptyOptionalStringBecomesNull() {
        DiagnosticsEvent event = minimalBuilder()
            .sourceConnectionAddress("")
            .payloadType("")
            .build();

        assertNull(event.sourceConnectionAddress());
        assertNull(event.payloadType());
    }

    @Test
    void pluginVersionUnknownBecomesNull() {
        DiagnosticsEvent event = minimalBuilder()
            .pluginVersion("unknown")
            .build();

        assertNull(event.pluginVersion());
    }

    @Test
    void pluginVersionUnknownCaseInsensitiveBecomesNull() {
        DiagnosticsEvent event = minimalBuilder()
            .pluginVersion("UNKNOWN")
            .build();

        assertNull(event.pluginVersion());
    }

    @Test
    void pluginVersionNonUnknownIsPreserved() {
        DiagnosticsEvent event = minimalBuilder()
            .pluginVersion("1.2.3")
            .build();

        assertEquals("1.2.3", event.pluginVersion());
    }

    @Test
    void emptyMapPayloadPreviewBecomesNull() {
        DiagnosticsEvent event = minimalBuilder()
            .payloadPreview(Map.of())
            .build();

        assertNull(event.payloadPreview());
    }

    @Test
    void nonEmptyMapPayloadPreviewIsPreserved() {
        DiagnosticsEvent event = minimalBuilder()
            .addPreview("key", "value")
            .build();

        assertNotNull(event.payloadPreview());
        assertEquals("value", event.payloadPreview().get("key"));
    }

    @Test
    void emptyTagsListBecomesNull() {
        DiagnosticsEvent event = minimalBuilder()
            .tags(List.of())
            .build();

        assertNull(event.tags());
    }

    @Test
    void nonEmptyTagsListIsPreserved() {
        DiagnosticsEvent event = minimalBuilder()
            .addTag("tag-one")
            .build();

        assertNotNull(event.tags());
        assertEquals(List.of("tag-one"), event.tags());
    }

    @Test
    void tagsOrEmptyReturnsEmptyListWhenTagsNull() {
        DiagnosticsEvent event = minimalBuilder().build();

        assertNull(event.tags());
        assertEquals(List.of(), event.tagsOrEmpty());
    }

    @Test
    void truncatedFieldsOrEmptyReturnsEmptyListWhenNull() {
        DiagnosticsEvent event = minimalBuilder().build();

        assertNull(event.truncatedFields());
        assertEquals(List.of(), event.truncatedFieldsOrEmpty());
    }

    @Test
    void messageNullBecomesEmpty() {
        DiagnosticsEvent event = minimalBuilder()
            .message(null)
            .build();

        assertEquals("", event.message());
    }

    @Test
    void messageIsTrimmed() {
        DiagnosticsEvent event = minimalBuilder()
            .message("  hello world  ")
            .build();

        assertEquals("hello world", event.message());
    }

    @Test
    void occurredAtIsoFormatsEpochMs() {
        long epochMs = 1_000_000_000_000L;
        DiagnosticsEvent event = minimalBuilder()
            .occurredAtEpochMs(epochMs)
            .build();

        assertEquals("2001-09-09T01:46:40Z", event.occurredAtIso());
    }

    @Test
    void correlationIdIsOperationId() {
        DiagnosticsEvent event = minimalBuilder()
            .operationId("op-123")
            .build();

        assertEquals("op-123", event.correlationId());
        assertEquals("op-123", event.operationId());
    }

    @Test
    void toBuilderRoundTrips() {
        DiagnosticsEvent original = minimalBuilder()
            .category(DiagnosticsCategory.RECOVERY)
            .action(DiagnosticsAction.RECOVERY_FINALIZE)
            .outcome(DiagnosticsOutcome.SUCCEEDED)
            .playerUuid("player-uuid")
            .addTag("test-tag")
            .build();

        DiagnosticsEvent copy = original.toBuilder().build();

        assertEquals(original, copy);
    }

    @Test
    void preservesTimestampAsProvided() {
        long ts = 99_999_999L;
        DiagnosticsEvent event = minimalBuilder()
            .occurredAtEpochMs(ts)
            .build();

        assertEquals(ts, event.occurredAtEpochMs());
    }

    @Test
    void preservesSourceSequenceAsProvided() {
        DiagnosticsEvent event = minimalBuilder()
            .sourceSequence(42L)
            .build();

        assertEquals(42L, event.sourceSequence());
    }

    @Test
    void preservesSourceServerIdAsProvided() {
        DiagnosticsEvent event = minimalBuilder()
            .sourceServerId("srv-abc")
            .build();

        assertEquals("srv-abc", event.sourceServerId());
    }

    @Test
    void preservesOperationId() {
        DiagnosticsEvent event = minimalBuilder()
            .operationId("travel:abc-123")
            .build();

        assertEquals("travel:abc-123", event.operationId());
    }

    @Test
    void truncatedFalseByDefault() {
        DiagnosticsEvent event = minimalBuilder().build();

        assertEquals(false, event.truncated());
    }

    @Test
    void durationMsNullByDefault() {
        DiagnosticsEvent event = minimalBuilder().build();

        assertNull(event.durationMs());
    }

    @Test
    void durationMsIsPreservedWhenSet() {
        DiagnosticsEvent event = minimalBuilder()
            .durationMs(250L)
            .build();

        assertEquals(250L, event.durationMs());
    }

    private DiagnosticsEvent.Builder minimalBuilder() {
        return new DiagnosticsEvent.Builder()
            .eventId("evt-1")
            .occurredAtEpochMs(1_000L)
            .sourceSequence(1L)
            .sourceServerId("server-1")
            .category(DiagnosticsCategory.SECURITY)
            .action(DiagnosticsAction.SECURITY_REFERRAL_DECODE)
            .outcome(DiagnosticsOutcome.FAILED)
            .reasonClass(DiagnosticsReasonClass.UNKNOWN)
            .reasonCode(DiagnosticsReasonCode.REFERRAL_DECODE_FAILED)
            .message("test message")
            .operationId("op-1");
    }
}
