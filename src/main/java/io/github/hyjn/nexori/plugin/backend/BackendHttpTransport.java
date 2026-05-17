package io.github.hyjn.nexori.plugin.backend;

import javax.annotation.Nonnull;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over the JDK {@link java.net.http.HttpClient} for testability.
 *
 * <p>Production code uses {@link JdkBackendHttpTransport}. Tests inject
 * {@code FakeBackendHttpTransport} (in the test source tree) to control responses
 * without opening real sockets.</p>
 */
public interface BackendHttpTransport {

    /**
     * Sends an HTTP request asynchronously and returns a future that completes
     * with the string-body response. Implementations are responsible for applying
     * the timeout.
     *
     * @param request   the pre-built HTTP request
     * @param timeoutMs request timeout in milliseconds
     * @return a {@link CompletableFuture} that completes with the response or
     *         exceptionally if the request fails or times out
     */
    @Nonnull
    CompletableFuture<HttpResponse<String>> sendAsync(@Nonnull HttpRequest request, long timeoutMs);
}
