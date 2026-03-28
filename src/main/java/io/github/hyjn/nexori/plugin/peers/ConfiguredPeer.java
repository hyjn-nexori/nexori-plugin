package io.github.hyjn.nexori.plugin.peers;

import javax.annotation.Nonnull;
import java.util.Locale;

public record ConfiguredPeer(
    String host,
    int port
) {

    @Nonnull
    public static ConfiguredPeer parse(@Nonnull String rawConnectionAddress) {
        String trimmed = rawConnectionAddress == null ? "" : rawConnectionAddress.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Enter a server as host:port.");
        }

        int separator = trimmed.lastIndexOf(':');
        if (separator <= 0 || separator == trimmed.length() - 1) {
            throw new IllegalArgumentException("Use host:port, for example 127.0.0.1:7002.");
        }

        String host = trimmed.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String rawPort = trimmed.substring(separator + 1).trim();
        if (host.isBlank()) {
            throw new IllegalArgumentException("The host cannot be blank.");
        }

        int port;
        try {
            port = Integer.parseInt(rawPort);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("The port must be a number between 1 and 65535.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The port must be between 1 and 65535.");
        }

        return new ConfiguredPeer(host, port).normalized();
    }

    @Nonnull
    public ConfiguredPeer normalized() {
        return new ConfiguredPeer(
            host == null ? "" : host.trim().toLowerCase(Locale.ROOT),
            port
        );
    }

    @Nonnull
    public String connectionAddress() {
        return normalized().host + ":" + port;
    }
}
