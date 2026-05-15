package io.github.hyjn.nexori.plugin.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ServerPolicyPayloadsTest {

    // ── ServerPolicyFetchRequestPayload ───────────────────────────────────────

    @Test
    void fetchRequestPreservesRequestId() {
        ServerPolicyFetchRequestPayload payload = new ServerPolicyFetchRequestPayload("req-abc-123");
        assertEquals("req-abc-123", payload.requestId());
    }

    @Test
    void fetchRequestNullRequestIdBehaviorMatchesCurrentBehavior() {
        ServerPolicyFetchRequestPayload payload = new ServerPolicyFetchRequestPayload(null);
        assertNull(payload.requestId(), "Null requestId is preserved as-is by the record constructor");
    }

    // ── ServerPolicyApplyRequestPayload ──────────────────────────────────────

    @Test
    void applyRequestPreservesAllFields() {
        ServerPolicyApplyRequestPayload payload = new ServerPolicyApplyRequestPayload("req-apply-1", true, 5);
        assertEquals("req-apply-1", payload.requestId());
        assertEquals(true, payload.recoveryEnabled());
        assertEquals(5, payload.maxBackupsPerPlayer());
    }

    @Test
    void applyRequestDoesNotClampMaxBackupsAccordingToCurrentBehavior() {
        ServerPolicyApplyRequestPayload payload = new ServerPolicyApplyRequestPayload("req-1", false, 0);
        assertEquals(0, payload.maxBackupsPerPlayer(),
            "The payload record itself does not clamp — clamping happens in ServerPolicySyncService.apply()");
    }

    @Test
    void applyRequestPreservesRecoveryEnabledFalse() {
        ServerPolicyApplyRequestPayload payload = new ServerPolicyApplyRequestPayload("req-1", false, 3);
        assertEquals(false, payload.recoveryEnabled());
    }

    @Test
    void applyRequestNullRequestIdBehaviorMatchesCurrentBehavior() {
        ServerPolicyApplyRequestPayload payload = new ServerPolicyApplyRequestPayload(null, true, 3);
        assertNull(payload.requestId());
    }

    // ── ServerPolicySyncResponsePayload ──────────────────────────────────────

    @Test
    void syncResponsePreservesAllFields() {
        ServerPolicySyncResponsePayload payload = new ServerPolicySyncResponsePayload("req-resp-1", true, 7);
        assertEquals("req-resp-1", payload.requestId());
        assertEquals(true, payload.recoveryEnabled());
        assertEquals(7, payload.maxBackupsPerPlayer());
    }

    @Test
    void syncResponseDoesNotClampMaxBackupsAccordingToCurrentBehavior() {
        ServerPolicySyncResponsePayload payload = new ServerPolicySyncResponsePayload("req-1", false, -1);
        assertEquals(-1, payload.maxBackupsPerPlayer(),
            "The response payload record itself does not clamp maxBackupsPerPlayer");
    }

    @Test
    void syncResponseNullRequestIdBehaviorMatchesCurrentBehavior() {
        ServerPolicySyncResponsePayload payload = new ServerPolicySyncResponsePayload(null, false, 3);
        assertNull(payload.requestId());
    }

    @Test
    void syncResponsePreservesRecoveryEnabledFalse() {
        ServerPolicySyncResponsePayload payload = new ServerPolicySyncResponsePayload("req-1", false, 3);
        assertEquals(false, payload.recoveryEnabled());
    }
}
