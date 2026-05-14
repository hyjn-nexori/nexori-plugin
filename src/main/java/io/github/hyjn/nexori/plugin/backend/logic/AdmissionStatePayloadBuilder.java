package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.payload.BackendMatchAdmissionStatePayload;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Builds backend admission state payloads from already-evaluated, deterministic inputs.
 */
public final class AdmissionStatePayloadBuilder {

    public static final String CHANGE_REASON_MATCH_STARTED = AdmissionStateEvaluator.CHANGE_REASON_MATCH_STARTED;

    private final Gson gson = new GsonBuilder().create();

    @Nonnull
    public AdmissionStatePayloadBuildResult build(
        int schemaVersion,
        @Nonnull String stateUpdateId,
        long sequence,
        long sentAtEpochMs,
        long expiresAtEpochMs,
        @Nonnull String reportingServerId,
        @Nonnull ArenaActiveMatch match,
        @Nonnull AdmissionStateEvaluation evaluation,
        String primaryChangeReason,
        @Nonnull Collection<String> coalescedChangeReasons,
        @Nonnull Collection<String> pendingConsumedAdmissionReservationIds
    ) {
        List<String> normalizedCoalescedReasons = coalescedChangeReasons.stream()
            .filter(reason -> reason != null && !reason.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        List<String> consumedAdmissionReservationIdsIncluded = pendingConsumedAdmissionReservationIds.stream()
            .filter(Objects::nonNull)
            .map(AdmissionStatePayloadBuilder::normalizeOptional)
            .filter(reservationId -> !reservationId.isBlank())
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        String normalizedPrimaryChangeReason = normalizeOptional(primaryChangeReason);
        if (normalizedPrimaryChangeReason.isBlank()) {
            normalizedPrimaryChangeReason = normalizedCoalescedReasons.isEmpty()
                ? CHANGE_REASON_MATCH_STARTED
                : normalizedCoalescedReasons.get(normalizedCoalescedReasons.size() - 1);
        }

        BackendMatchAdmissionStatePayload payloadWithoutHash = new BackendMatchAdmissionStatePayload(
            schemaVersion,
            stateUpdateId,
            sequence,
            "",
            sentAtEpochMs,
            expiresAtEpochMs,
            reportingServerId,
            match.matchId(),
            match.externalMatchId(),
            match.queueId(),
            match.arenaId(),
            match.backfillEnabled(),
            match.effectiveBackfillMode().id(),
            Math.max(match.backfillWindowSeconds(), 0),
            evaluation.matchLifecycleStatus(),
            evaluation.admissionOpen(),
            evaluation.admissionOpenUntilEpochMs(),
            evaluation.admissionCapacity(),
            evaluation.admittedSlotCount(),
            evaluation.availableAdmissionSlots(),
            evaluation.initialRosterSize(),
            evaluation.arrivedInitialPlayerCount(),
            evaluation.unfilledInitialRosterCount(),
            consumedAdmissionReservationIdsIncluded,
            evaluation.admissionReportingClosed(),
            evaluation.admissionReportingCloseReason(),
            normalizedPrimaryChangeReason,
            normalizedCoalescedReasons
        );
        String payloadHash = hashCanonicalPayload(payloadWithoutHash);
        BackendMatchAdmissionStatePayload payload = new BackendMatchAdmissionStatePayload(
            payloadWithoutHash.schemaVersion(),
            payloadWithoutHash.stateUpdateId(),
            payloadWithoutHash.admissionStateSequence(),
            payloadHash,
            payloadWithoutHash.sentAtEpochMs(),
            payloadWithoutHash.stateExpiresAtEpochMs(),
            payloadWithoutHash.reportingServerId(),
            payloadWithoutHash.matchId(),
            payloadWithoutHash.externalMatchId(),
            payloadWithoutHash.queueId(),
            payloadWithoutHash.arenaId(),
            payloadWithoutHash.backfillEnabled(),
            payloadWithoutHash.backfillMode(),
            payloadWithoutHash.backfillWindowSeconds(),
            payloadWithoutHash.matchLifecycleStatus(),
            payloadWithoutHash.admissionOpen(),
            payloadWithoutHash.admissionOpenUntilEpochMs(),
            payloadWithoutHash.admissionCapacity(),
            payloadWithoutHash.admittedSlotCount(),
            payloadWithoutHash.availableAdmissionSlots(),
            payloadWithoutHash.initialRosterSize(),
            payloadWithoutHash.arrivedInitialPlayerCount(),
            payloadWithoutHash.unfilledInitialRosterCount(),
            payloadWithoutHash.consumedAdmissionReservationIds(),
            payloadWithoutHash.admissionReportingClosed(),
            payloadWithoutHash.admissionReportingCloseReason(),
            payloadWithoutHash.primaryChangeReason(),
            payloadWithoutHash.coalescedChangeReasons()
        );
        return new AdmissionStatePayloadBuildResult(
            payload,
            gson.toJson(payload),
            List.copyOf(consumedAdmissionReservationIdsIncluded)
        );
    }

    @Nonnull
    public String hashCanonicalPayload(@Nonnull BackendMatchAdmissionStatePayload payload) {
        JsonObject canonical = new JsonObject();
        canonical.addProperty("schemaVersion", payload.schemaVersion());
        canonical.addProperty("stateUpdateId", payload.stateUpdateId());
        canonical.addProperty("admissionStateSequence", payload.admissionStateSequence());
        canonical.addProperty("sentAtEpochMs", payload.sentAtEpochMs());
        canonical.addProperty("stateExpiresAtEpochMs", payload.stateExpiresAtEpochMs());
        canonical.addProperty("reportingServerId", payload.reportingServerId());
        canonical.addProperty("matchId", payload.matchId());
        canonical.addProperty("externalMatchId", payload.externalMatchId());
        canonical.addProperty("queueId", payload.queueId());
        canonical.addProperty("arenaId", payload.arenaId());
        canonical.addProperty("backfillEnabled", payload.backfillEnabled());
        canonical.addProperty("backfillMode", payload.backfillMode());
        canonical.addProperty("backfillWindowSeconds", payload.backfillWindowSeconds());
        canonical.addProperty("matchLifecycleStatus", payload.matchLifecycleStatus());
        canonical.addProperty("admissionOpen", payload.admissionOpen());
        canonical.addProperty("admissionOpenUntilEpochMs", payload.admissionOpenUntilEpochMs());
        canonical.addProperty("admissionCapacity", payload.admissionCapacity());
        canonical.addProperty("admittedSlotCount", payload.admittedSlotCount());
        canonical.addProperty("availableAdmissionSlots", payload.availableAdmissionSlots());
        canonical.addProperty("initialRosterSize", payload.initialRosterSize());
        canonical.addProperty("arrivedInitialPlayerCount", payload.arrivedInitialPlayerCount());
        canonical.addProperty("unfilledInitialRosterCount", payload.unfilledInitialRosterCount());
        JsonArray consumedReservationIds = new JsonArray();
        payload.consumedAdmissionReservationIds().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(consumedReservationIds::add);
        canonical.add("consumedAdmissionReservationIds", consumedReservationIds);
        canonical.addProperty("admissionReportingClosed", payload.admissionReportingClosed());
        canonical.addProperty("admissionReportingCloseReason", payload.admissionReportingCloseReason());
        canonical.addProperty("primaryChangeReason", payload.primaryChangeReason());
        JsonArray reasons = new JsonArray();
        payload.coalescedChangeReasons().stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(reasons::add);
        canonical.add("coalescedChangeReasons", reasons);
        return sha256Hex(gson.toJson(canonical));
    }

    @Nonnull
    private static String sha256Hex(@Nonnull String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
