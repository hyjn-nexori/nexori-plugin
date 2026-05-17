package io.github.hyjn.nexori.plugin.backend.testsupport;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

/**
 * Minimal {@link HttpResponse} implementation for unit tests.
 *
 * <p>Only {@link #statusCode()} and {@link #body()} return meaningful values;
 * all other methods return safe sentinel values (null / empty).</p>
 */
public final class FakeHttpResponse implements HttpResponse<String> {

    private final int statusCode;
    private final String body;

    public FakeHttpResponse(int statusCode, @Nonnull String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public String body() {
        return body;
    }

    @Override
    public HttpRequest request() {
        return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return HttpHeaders.of(Map.of(), (k, v) -> true);
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return URI.create("http://fake.test");
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
    }
}
