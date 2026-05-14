package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendMatchAdmissionStatePayload;

import java.util.List;

/**
 * Pure result of building an admission state payload and its serialized body.
 */
public record AdmissionStatePayloadBuildResult(
    BackendMatchAdmissionStatePayload payload,
    String body,
    List<String> consumedAdmissionReservationIdsIncluded
) {
}
