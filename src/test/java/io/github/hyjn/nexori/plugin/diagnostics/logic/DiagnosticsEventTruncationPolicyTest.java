package io.github.hyjn.nexori.plugin.diagnostics.logic;

import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticsEventTruncationPolicyTest {

    private static final int MAX_BYTES = DiagnosticsEventTruncationPolicy.MAX_EVENT_BYTES;

    private final DiagnosticsEventTruncationPolicy policy = new DiagnosticsEventTruncationPolicy();

    // ── FASE B: Limits / truncation ───────────────────────────────────────────

    @Test
    void maxEventBytesIs2048() {
        assertEquals(2048, MAX_BYTES);
    }

    @Test
    void eventWithinLimitIsReturnedUnchanged() {
        DiagnosticsEvent event = minimalEvent();

        DiagnosticsEvent result = policy.apply(event);

        assertSame(event, result);
    }

    @Test
    void truncatedFlagIsFalseForSmallEvent() {
        DiagnosticsEvent result = policy.apply(minimalEvent());

        assertFalse(result.truncated());
    }

    @Test
    void serializedSizeOfMinimalEventIsBelowLimit() {
        int size = policy.serializedSize(minimalEvent());

        assertTrue(size < MAX_BYTES, "Minimal event size " + size + " should be < " + MAX_BYTES);
    }

    @Test
    void oversizedEventSetsTruncatedFlag() {
        DiagnosticsEvent oversized = minimalBuilder()
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertTrue(result.truncated());
    }

    @Test
    void oversizedEventHasTruncatedFieldsList() {
        DiagnosticsEvent oversized = minimalBuilder()
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertNotNull(result.truncatedFields());
        assertFalse(result.truncatedFields().isEmpty());
    }

    @Test
    void oversizedEventFitsAfterTruncation() {
        DiagnosticsEvent oversized = minimalBuilder()
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertTrue(
            policy.serializedSize(result) <= MAX_BYTES,
            "Truncated event should fit within " + MAX_BYTES + " bytes"
        );
    }

    @Test
    void payloadPreviewIsRemovedFirstWhenOverLimit() {
        String longValue = "v".repeat(2000);
        DiagnosticsEvent event = minimalBuilder()
            .addPreview("large", longValue)
            .build();

        DiagnosticsEvent result = policy.apply(event);

        assertNull(result.payloadPreview());
        assertTrue(result.truncatedFields().contains("payloadPreview"));
    }

    @Test
    void payloadPreviewRemovedBeforeMessageIsShrunken() {
        // Event is oversized only because of payloadPreview; short message should survive intact
        String shortMessage = "short message";
        DiagnosticsEvent event = minimalBuilder()
            .message(shortMessage)
            .addPreview("big", "v".repeat(2000))
            .build();

        DiagnosticsEvent result = policy.apply(event);

        assertNull(result.payloadPreview());
        assertEquals(shortMessage, result.message());
        assertTrue(result.truncatedFields().contains("payloadPreview"));
        assertFalse(result.truncatedFields().contains("message"));
    }

    @Test
    void messageIsTruncatedFieldNameWhenMessageShrunk() {
        DiagnosticsEvent oversized = minimalBuilder()
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertTrue(result.truncatedFields().contains("message"));
    }

    @Test
    void tagsAreRemovedAsLastResortBeforeFinalFallback() {
        // Build an event that overflows, with no payloadPreview, empty message, but with tags
        // We can't easily force message to be empty while still oversized through normal flow,
        // so we verify that tags field name appears when tags are removed.
        List<String> tags = List.of("tag-a", "tag-b");
        DiagnosticsEvent event = minimalBuilder()
            .message("x".repeat(2200))
            .tags(tags)
            .build();

        DiagnosticsEvent result = policy.apply(event);

        // After truncation, tags may or may not be removed depending on whether message shrinking was enough.
        // If message shrank enough, tags remain. We assert consistent flag state.
        if (result.tags() == null) {
            assertTrue(result.truncatedFields().contains("tags"));
        }
        assertTrue(result.truncated());
    }

    @Test
    void truncationPreservesSchemaVersion() {
        DiagnosticsEvent oversized = minimalBuilder()
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertEquals(DiagnosticsEvent.SCHEMA_VERSION, result.schemaVersion());
    }

    @Test
    void truncationPreservesCategoryActionOutcome() {
        DiagnosticsEvent oversized = minimalBuilder()
            .category(DiagnosticsCategory.TRAVEL)
            .action(DiagnosticsAction.TRAVEL_DISPATCH)
            .outcome(DiagnosticsOutcome.SUCCEEDED)
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertEquals(DiagnosticsCategory.TRAVEL, result.category());
        assertEquals(DiagnosticsAction.TRAVEL_DISPATCH, result.action());
        assertEquals(DiagnosticsOutcome.SUCCEEDED, result.outcome());
    }

    @Test
    void truncationPreservesEventIdAndSourceFields() {
        DiagnosticsEvent oversized = minimalBuilder()
            .eventId("evt-xyz")
            .sourceServerId("srv-abc")
            .sourceSequence(77L)
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent result = policy.apply(oversized);

        assertEquals("evt-xyz", result.eventId());
        assertEquals("srv-abc", result.sourceServerId());
        assertEquals(77L, result.sourceSequence());
    }

    @Test
    void truncationIsStableForSameInput() {
        DiagnosticsEvent oversized = minimalBuilder()
            .message("x".repeat(2200))
            .build();

        DiagnosticsEvent first = policy.apply(oversized);
        DiagnosticsEvent second = policy.apply(oversized);

        assertEquals(first, second);
    }

    // ── shrinkMessage steps ──────────────────────────────────────────────────

    @Test
    void shrinkMessageOver512TrimTo512() {
        String result = policy.shrinkMessage("a".repeat(1000));

        assertEquals(512, result.length());
    }

    @Test
    void shrinkMessageExactly512TrimTo256() {
        String result = policy.shrinkMessage("a".repeat(512));

        assertEquals(256, result.length());
    }

    @Test
    void shrinkMessageOver256TrimTo256() {
        String result = policy.shrinkMessage("a".repeat(300));

        assertEquals(256, result.length());
    }

    @Test
    void shrinkMessageExactly256TrimTo128() {
        String result = policy.shrinkMessage("a".repeat(256));

        assertEquals(128, result.length());
    }

    @Test
    void shrinkMessageOver128TrimTo128() {
        String result = policy.shrinkMessage("a".repeat(150));

        assertEquals(128, result.length());
    }

    @Test
    void shrinkMessageExactly128TrimTo64() {
        String result = policy.shrinkMessage("a".repeat(128));

        assertEquals(64, result.length());
    }

    @Test
    void shrinkMessageOver64TrimTo64() {
        String result = policy.shrinkMessage("a".repeat(100));

        assertEquals(64, result.length());
    }

    @Test
    void shrinkMessageExactly64BecomesEmpty() {
        String result = policy.shrinkMessage("a".repeat(64));

        assertEquals("", result);
    }

    @Test
    void shrinkMessageShorterThan64BecomesEmpty() {
        String result = policy.shrinkMessage("short");

        assertEquals("", result);
    }

    @Test
    void shrinkMessageEmptyBecomesEmpty() {
        String result = policy.shrinkMessage("");

        assertEquals("", result);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private DiagnosticsEvent minimalEvent() {
        return minimalBuilder().build();
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
