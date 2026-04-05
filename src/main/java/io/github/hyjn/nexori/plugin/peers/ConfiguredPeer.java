package io.github.hyjn.nexori.plugin.peers;

import javax.annotation.Nonnull;
import java.util.Locale;

public record ConfiguredPeer(
    String displayName,
    String host,
    int port
) {

    public static final int DEFAULT_PORT = 5520;

    @Nonnull
    public static ConfiguredPeer create(@Nonnull String rawDisplayName, @Nonnull String rawConnectionAddress) {
        ConfiguredPeer parsed = parse(rawConnectionAddress);
        String normalizedDisplayName = rawDisplayName == null ? "" : rawDisplayName.trim();
        if (normalizedDisplayName.isBlank()) {
            throw new IllegalArgumentException("Enter a display name for this server.");
        }
        return new ConfiguredPeer(normalizedDisplayName, parsed.host(), parsed.port()).normalized();
    }

    @Nonnull
    public static ConfiguredPeer parse(@Nonnull String rawConnectionAddress) {
        String trimmed = rawConnectionAddress == null ? "" : rawConnectionAddress.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Enter a server as host[:port].");
        }

        if (trimmed.endsWith(":")) {
            throw new IllegalArgumentException("Use host[:port], for example 127.0.0.1 or 127.0.0.1:7002.");
        }

        int separator = trimmed.lastIndexOf(':');
        String host;
        int port;
        if (separator <= 0) {
            host = trimmed.trim().toLowerCase(Locale.ROOT);
            port = DEFAULT_PORT;
        } else {
            host = trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String rawPort = trimmed.substring(separator + 1).trim();
            try {
                port = Integer.parseInt(rawPort);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("The port must be a number between 1 and 65535.");
            }
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("The host cannot be blank.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The port must be between 1 and 65535.");
        }

        return new ConfiguredPeer("", host, port).normalized();
    }

    @Nonnull
    public ConfiguredPeer normalized() {
        String normalizedHost = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        int normalizedPort = port <= 0 ? DEFAULT_PORT : port;
        String normalizedDisplayName = displayName == null ? "" : displayName.trim();
        return new ConfiguredPeer(
            normalizedDisplayName.isBlank() ? normalizedHost + ":" + normalizedPort : normalizedDisplayName,
            normalizedHost,
            normalizedPort
        );
    }

    @Nonnull
    public String connectionAddress() {
        return normalized().host + ":" + port;
    }
}
