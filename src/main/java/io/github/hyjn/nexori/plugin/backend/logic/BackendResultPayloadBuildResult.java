package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendResultPayload;

/**
 * Pure result of building a backend match result payload and its serialized body.
 */
public record BackendResultPayloadBuildResult(
    BackendResultPayload payload,
    String body
) {
}
