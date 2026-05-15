package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapReferralPayloadTest {

    private static BootstrapChallenge makeChallenge() {
        return BootstrapChallenge.create(UUID.randomUUID(), "target", "session-1", Instant.now().plusSeconds(60));
    }

    @Test
    void requestBuildsProofRequestPayload() {
        BootstrapChallenge challenge = makeChallenge();
        BootstrapReferralPayload payload = BootstrapReferralPayload.request("player-1", challenge, 1, 4);

        assertEquals(BootstrapMessageType.REQUEST_PROOF.name(), payload.type());
        assertEquals("player-1", payload.startedByPlayerUuid());
        assertEquals(challenge, payload.challenge());
        assertEquals(1, payload.currentPeerIndex());
        assertEquals(4, payload.totalPeers());
    }

    @Test
    void requestHasBlankDefaultsForResponderFields() {
        BootstrapReferralPayload payload = BootstrapReferralPayload.request("player", makeChallenge(), 0, 1);

        assertEquals("", payload.responderServerId());
        assertEquals("", payload.responderFingerprint());
        assertEquals("", payload.responderPublicKeyBase64());
        assertEquals("", payload.signatureBase64());
        assertEquals("", payload.errorMessage());
    }

    @Test
    void responseCopiesRequestChallengeAndPeerIndexes() {
        BootstrapChallenge challenge = makeChallenge();
        BootstrapReferralPayload request = BootstrapReferralPayload.request("player", challenge, 2, 5);

        BootstrapReferralPayload response = BootstrapReferralPayload.response(
            request, "server-id", "fingerprint-abc", "pubkey-base64", "signature-xyz");

        assertEquals(BootstrapMessageType.RESPONSE_PROOF.name(), response.type());
        assertEquals(challenge, response.challenge());
        assertEquals(2, response.currentPeerIndex());
        assertEquals(5, response.totalPeers());
        assertEquals("server-id", response.responderServerId());
        assertEquals("fingerprint-abc", response.responderFingerprint());
        assertEquals("pubkey-base64", response.responderPublicKeyBase64());
        assertEquals("signature-xyz", response.signatureBase64());
    }

    @Test
    void installBundlePreservesTrustBundleAndMigrationPlan() {
        TrustBundle bundle = new TrustBundle(3L, "hash-abc", 1_000L, List.of());
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old", "new", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        BootstrapMigrationPlan plan = new BootstrapMigrationPlan(List.of(rep));

        BootstrapReferralPayload payload = BootstrapReferralPayload.installBundle(
            "player", makeChallenge(), bundle, plan, 0, 2);

        assertEquals(BootstrapMessageType.INSTALL_BUNDLE.name(), payload.type());
        assertEquals(bundle, payload.trustBundle());
        assertNotNull(payload.migrationPlan());
        assertEquals(1, payload.migrationPlan().replacements().size());
    }

    @Test
    void installAckCopiesChallengeAndIndexes() {
        BootstrapChallenge challenge = makeChallenge();
        BootstrapReferralPayload request = BootstrapReferralPayload.request("player", challenge, 1, 3);

        BootstrapReferralPayload ack = BootstrapReferralPayload.installAck(request, "responder-id", "hash-bundle");

        assertEquals(BootstrapMessageType.INSTALL_ACK.name(), ack.type());
        assertEquals(challenge, ack.challenge());
        assertEquals(1, ack.currentPeerIndex());
        assertEquals(3, ack.totalPeers());
        assertEquals("hash-bundle", ack.acknowledgedBundleHash());
        assertEquals("responder-id", ack.responderServerId());
    }

    @Test
    void errorCopiesChallengeAndIndexes() {
        BootstrapChallenge challenge = makeChallenge();
        BootstrapReferralPayload request = BootstrapReferralPayload.request("player", challenge, 0, 2);

        BootstrapReferralPayload error = BootstrapReferralPayload.error(request, "Something went wrong");

        assertEquals(BootstrapMessageType.ERROR.name(), error.type());
        assertEquals(challenge, error.challenge());
        assertEquals(0, error.currentPeerIndex());
        assertEquals(2, error.totalPeers());
        assertEquals("Something went wrong", error.errorMessage());
    }

    @Test
    void messageTypeParsesKnownType() {
        BootstrapReferralPayload payload = BootstrapReferralPayload.request("player", makeChallenge(), 0, 1);
        assertEquals(BootstrapMessageType.REQUEST_PROOF, payload.messageType());
    }

    @Test
    void messageTypeThrowsForUnknownTypeAccordingToCurrentBehavior() {
        BootstrapReferralPayload payload = new BootstrapReferralPayload(
            "UNKNOWN_TYPE", "player", makeChallenge(), null,
            "", "", "", "", "", "", BootstrapMigrationPlan.empty(), 0, 1
        );
        assertThrows(IllegalArgumentException.class, payload::messageType,
            "messageType() should throw for unknown type string");
    }

    @Test
    void emptyMigrationPlanUsedWhenNotProvided() {
        BootstrapReferralPayload payload = BootstrapReferralPayload.request("player", makeChallenge(), 0, 1);
        assertNotNull(payload.migrationPlan());
        assertTrue(payload.migrationPlan().isEmpty());
    }

    @Test
    void installBundleTrustBundleIsNullWhenPlanIsEmpty() {
        BootstrapReferralPayload response = BootstrapReferralPayload.response(
            BootstrapReferralPayload.request("p", makeChallenge(), 0, 1),
            "srv", "fp", "pk", "sig"
        );
        assertNull(response.trustBundle(), "response() should set trustBundle to null");
    }
}
