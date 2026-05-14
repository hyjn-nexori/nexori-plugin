package io.github.hyjn.nexori.plugin.accessgate;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NexoriAccessGateConfigDocumentTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void defaultsUsesCurrentSchemaAndSafeValues() {
        NexoriAccessGateConfigDocument defaults = NexoriAccessGateConfigDocument.defaults();

        assertEquals(NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION, defaults.schemaVersion());
        assertEquals(NexoriAccessGateConfigDocument.DEFAULT_MAX_PLAYERS, defaults.maxPlayers());
        assertEquals(NexoriAccessGateConfigDocument.DEFAULT_RESERVED_PRIORITY_SLOTS, defaults.reservedPrioritySlots());
        assertEquals(NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE, defaults.fullMessage());
        assertTrue(defaults.bypassReferralConnections());
        assertEquals(List.of(), defaults.bypassPlayerUuids());
    }

    @Test
    void normalizedForcesCurrentSchemaVersion() {
        assertEquals(NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION, config().normalized().schemaVersion());
    }

    @Test
    void normalizedClampsMaxPlayersToAtLeastOne() {
        assertEquals(1, config(-5, 0).normalized().maxPlayers());
    }

    @Test
    void normalizedClampsReservedPrioritySlotsBetweenZeroAndMaxPlayers() {
        assertEquals(0, config(10, -5).normalized().reservedPrioritySlots());
        assertEquals(10, config(10, 99).normalized().reservedPrioritySlots());
    }

    @Test
    void normalizedUsesDefaultFullMessageWhenBlank() {
        NexoriAccessGateConfigDocument normalized = config("   ").normalized();

        assertEquals(NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE, normalized.fullMessage());
    }

    @Test
    void normalizedTrimsFullMessage() {
        NexoriAccessGateConfigDocument normalized = config(" Server full ").normalized();

        assertEquals("Server full", normalized.fullMessage());
    }

    @Test
    void normalizedTrimsAndLowercasesManualRedirectAddress() {
        NexoriAccessGateConfigDocument normalized = configWithRedirect(" Example.COM:25565 ").normalized();

        assertEquals("example.com:25565", normalized.manualRedirectAddress());
    }

    @Test
    void normalizedHandlesNullBypassPlayersAsEmptyList() {
        assertEquals(List.of(), configWithBypassPlayers(null).normalized().bypassPlayerUuids());
    }

    @Test
    void normalizedRemovesNullBypassPlayers() {
        NexoriAccessGateConfigDocument normalized = configWithBypassPlayers(Arrays.asList(
            new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString(), "One"),
            null
        )).normalized();

        assertEquals(List.of(new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString(), "One")), normalized.bypassPlayerUuids());
    }

    @Test
    void normalizedRemovesBlankBypassPlayers() {
        NexoriAccessGateConfigDocument normalized = configWithBypassPlayers(List.of(
            new NexoriAccessGateBypassPlayer("   ", "Blank"),
            new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString(), "One")
        )).normalized();

        assertEquals(List.of(new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString(), "One")), normalized.bypassPlayerUuids());
    }

    @Test
    void normalizedDeduplicatesBypassPlayersPreservingFirstOccurrence() {
        NexoriAccessGateConfigDocument normalized = configWithBypassPlayers(List.of(
            new NexoriAccessGateBypassPlayer(" " + PLAYER_ONE + " ", "First"),
            new NexoriAccessGateBypassPlayer(PLAYER_TWO.toString(), "Second"),
            new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString().toUpperCase(), "Duplicate")
        )).normalized();

        assertEquals(List.of(
            new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString(), "First"),
            new NexoriAccessGateBypassPlayer(PLAYER_TWO.toString(), "Second")
        ), normalized.bypassPlayerUuids());
    }

    @Test
    void containsBypassUuidMatchesNormalizedLowercaseUuid() {
        NexoriAccessGateConfigDocument normalized = configWithBypassPlayers(List.of(
            new NexoriAccessGateBypassPlayer(PLAYER_ONE.toString().toUpperCase(), "One")
        )).normalized();

        assertTrue(normalized.containsBypassUuid(PLAYER_ONE));
    }

    private static NexoriAccessGateConfigDocument config() {
        return config(80, 0);
    }

    private static NexoriAccessGateConfigDocument config(int maxPlayers, int reservedPrioritySlots) {
        return new NexoriAccessGateConfigDocument(
            -1,
            true,
            maxPlayers,
            reservedPrioritySlots,
            NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE,
            true,
            false,
            "",
            List.of()
        );
    }

    private static NexoriAccessGateConfigDocument config(String fullMessage) {
        return new NexoriAccessGateConfigDocument(
            -1,
            true,
            80,
            0,
            fullMessage,
            true,
            false,
            "",
            List.of()
        );
    }

    private static NexoriAccessGateConfigDocument configWithRedirect(String manualRedirectAddress) {
        return new NexoriAccessGateConfigDocument(
            -1,
            true,
            80,
            0,
            NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE,
            true,
            true,
            manualRedirectAddress,
            List.of()
        );
    }

    private static NexoriAccessGateConfigDocument configWithBypassPlayers(List<NexoriAccessGateBypassPlayer> bypassPlayers) {
        return new NexoriAccessGateConfigDocument(
            -1,
            true,
            80,
            0,
            NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE,
            true,
            false,
            "",
            bypassPlayers
        );
    }
}
