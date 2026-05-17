package io.github.hyjn.nexori.plugin.backend;

import javax.annotation.Nonnull;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link BackendHttpTransport} backed by the JDK {@link HttpClient}.
 *
 * <p>Each instance holds a dedicated {@link HttpClient} configured with the given
 * connect timeout. The {@link BackendSyncService} (and other backend services) rebuild
 * this adapter whenever the matchmaking config is updated so that a new {@link HttpClient}
 * is created with the refreshed timeout value.</p>
 */
public final class JdkBackendHttpTransport implements BackendHttpTransport {

    private final HttpClient httpClient;

    public JdkBackendHttpTransport(long connectTimeoutMs) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();
    }

    @Nonnull
    @Override
    public CompletableFuture<HttpResponse<String>> sendAsync(@Nonnull HttpRequest request, long timeoutMs) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
