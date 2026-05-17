package io.github.hyjn.nexori.plugin.backend.testsupport;

import io.github.hyjn.nexori.plugin.backend.BackendHttpTransport;

import javax.annotation.Nonnull;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Test double for {@link io.github.hyjn.nexori.plugin.backend.BackendHttpTransport}.
 *
 * <p>Callers enqueue pre-built response futures before exercising the service under
 * test. Each {@link #sendAsync} call pops the next future from the queue and records
 * the captured request for later assertion.</p>
 *
 * <p>If no future is queued, {@link #sendAsync} returns an exceptionally-completed
 * future with {@link IllegalStateException} to make unexpected calls fail fast.</p>
 */
public final class FakeBackendHttpTransport implements BackendHttpTransport {

    private final Deque<CompletableFuture<HttpResponse<String>>> queuedFutures = new ArrayDeque<>();
    private final List<HttpRequest> capturedRequests = new ArrayList<>();

    /**
     * Enqueues an already-completed response future.
     * The {@code whenComplete} callback on the returned future will run synchronously
     * in the thread that attaches it (i.e., inside {@code handleTick}).
     */
    public void enqueueResponse(int statusCode, @Nonnull String body) {
        CompletableFuture<HttpResponse<String>> future = new CompletableFuture<>();
        future.complete(new FakeHttpResponse(statusCode, body));
        queuedFutures.add(future);
    }

    /**
     * Enqueues a manually-controlled future (e.g., one that will never complete,
     * simulating a pending in-flight request for stale-detection tests).
     */
    public void enqueuePendingFuture(@Nonnull CompletableFuture<HttpResponse<String>> future) {
        queuedFutures.add(future);
    }

    /** Returns a snapshot of all requests captured so far. */
    @Nonnull
    public List<HttpRequest> capturedRequests() {
        return List.copyOf(capturedRequests);
    }

    /** Returns the total number of requests captured since creation (or last clear). */
    public int capturedRequestCount() {
        return capturedRequests.size();
    }

    /** Clears the captured request list. */
    public void clearCapturedRequests() {
        capturedRequests.clear();
    }

    @Nonnull
    @Override
    public CompletableFuture<HttpResponse<String>> sendAsync(@Nonnull HttpRequest request, long timeoutMs) {
        capturedRequests.add(request);
        CompletableFuture<HttpResponse<String>> next = queuedFutures.poll();
        if (next == null) {
            CompletableFuture<HttpResponse<String>> unmatched = new CompletableFuture<>();
            unmatched.completeExceptionally(new IllegalStateException(
                "FakeBackendHttpTransport: no queued response for request to " + request.uri()
            ));
            return unmatched;
        }
        return next;
    }
}
