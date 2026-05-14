package io.github.hyjn.nexori.plugin.travel.logic;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Pure parse result for optional Nexori travel context JSON.
 */
public record TravelContextData(
    JsonObject context,
    boolean parseFailed,
    Exception parseException
) {

    @Nonnull
    public Optional<JsonObject> contextOptional() {
        return Optional.ofNullable(context);
    }

    @Nonnull
    public Optional<Exception> parseExceptionOptional() {
        return Optional.ofNullable(parseException);
    }
}
