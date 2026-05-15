package io.github.hyjn.nexori.plugin.peers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LocalConnectionAddressServiceTest {

    @Test
    void constructorCreatesMissingFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        new LocalConnectionAddressService(file);
        assertTrue(Files.exists(file));
    }

    @Test
    void missingFileStartsBlank(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        LocalConnectionAddressService service = new LocalConnectionAddressService(file);
        assertTrue(service.getConnectionAddressOrBlank().isBlank());
    }

    @Test
    void blankFileStartsBlank(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        LocalConnectionAddressService service = new LocalConnectionAddressService(file);
        assertTrue(service.getConnectionAddressOrBlank().isBlank());
    }

    @Test
    void invalidAddressFileStartsBlankAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        Files.writeString(file, ":::not-a-valid-address", StandardCharsets.UTF_8);

        LocalConnectionAddressService service = new LocalConnectionAddressService(file);
        assertTrue(service.getConnectionAddressOrBlank().isBlank(),
            "Invalid address in file should silently default to blank");
    }

    @Test
    void validAddressFileIsNormalized(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        Files.writeString(file, "  HOST.EXAMPLE.COM:5520  ", StandardCharsets.UTF_8);

        LocalConnectionAddressService service = new LocalConnectionAddressService(file);
        assertEquals("host.example.com:5520", service.getConnectionAddressOrBlank());
    }

    @Test
    void saveNormalizesAndPersistsAddress(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        LocalConnectionAddressService service = new LocalConnectionAddressService(file);

        service.save("  MY.HOST.COM:7001  ");

        LocalConnectionAddressService reloaded = new LocalConnectionAddressService(file);
        assertEquals("my.host.com:7001", reloaded.getConnectionAddressOrBlank());
    }

    @Test
    void getConfiguredPeerReturnsEmptyWhenBlank(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        LocalConnectionAddressService service = new LocalConnectionAddressService(file);

        Optional<ConfiguredPeer> peer = service.getConfiguredPeer();
        assertTrue(peer.isEmpty());
    }

    @Test
    void getConfiguredPeerReturnsParsedPeerWhenSaved(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        LocalConnectionAddressService service = new LocalConnectionAddressService(file);
        service.save("peer.example.com:7001");

        Optional<ConfiguredPeer> peer = service.getConfiguredPeer();

        assertTrue(peer.isPresent());
        assertEquals("peer.example.com", peer.get().host());
        assertEquals(7001, peer.get().port());
    }

    @Test
    void saveRejectsInvalidAddressAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("local-address.txt");
        LocalConnectionAddressService service = new LocalConnectionAddressService(file);

        assertThrows(IllegalArgumentException.class, () -> service.save("   "),
            "save() with blank address should throw IllegalArgumentException via ConfiguredPeer.parse()");
    }
}
