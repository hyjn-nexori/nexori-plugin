package io.github.hyjn.nexori.plugin.diagnostics;

public final class DiagnosticsReasonCode {

    private DiagnosticsReasonCode() {
    }

    public static final String REFERRAL_DECODE_FAILED = "REFERRAL_DECODE_FAILED";
    public static final String REFERRAL_EXPIRED = "REFERRAL_EXPIRED";
    public static final String ISSUER_NOT_TRUSTED = "ISSUER_NOT_TRUSTED";
    public static final String SIGNATURE_INVALID = "SIGNATURE_INVALID";
    public static final String SIGNATURE_VERIFY_EXCEPTION = "SIGNATURE_VERIFY_EXCEPTION";
    public static final String PAYLOAD_TYPE_UNSUPPORTED = "PAYLOAD_TYPE_UNSUPPORTED";
    public static final String BOOTSTRAP_ORIGIN_NOT_VERIFIED = "BOOTSTRAP_ORIGIN_NOT_VERIFIED";

    public static final String BOOTSTRAP_RUN_STARTED = "BOOTSTRAP_RUN_STARTED";
    public static final String BOOTSTRAP_RUN_ALREADY_ACTIVE = "BOOTSTRAP_RUN_ALREADY_ACTIVE";
    public static final String BOOTSTRAP_PEERS_EMPTY = "BOOTSTRAP_PEERS_EMPTY";
    public static final String PROOF_REQUEST_ANSWERED = "PROOF_REQUEST_ANSWERED";
    public static final String PROOF_SIGNATURE_VERIFIED = "PROOF_SIGNATURE_VERIFIED";
    public static final String PROOF_SIGNATURE_INVALID = "PROOF_SIGNATURE_INVALID";
    public static final String PROOF_RESPONSE_PROCESSING_FAILED = "PROOF_RESPONSE_PROCESSING_FAILED";
    public static final String LOCAL_CONNECTION_ADDRESS_MISSING = "LOCAL_CONNECTION_ADDRESS_MISSING";
    public static final String BUNDLE_BUILT = "BUNDLE_BUILT";
    public static final String BUNDLE_INSTALL_REQUEST_DENIED = "BUNDLE_INSTALL_REQUEST_DENIED";
    public static final String BUNDLE_INSTALLED = "BUNDLE_INSTALLED";
    public static final String BUNDLE_ACK_HASH_MISMATCH = "BUNDLE_ACK_HASH_MISMATCH";
    public static final String BUNDLE_DISTRIBUTION_FAILED = "BUNDLE_DISTRIBUTION_FAILED";
    public static final String BOOTSTRAP_RUN_COMPLETED = "BOOTSTRAP_RUN_COMPLETED";
    public static final String BOOTSTRAP_RUN_RESET = "BOOTSTRAP_RUN_RESET";
    public static final String BOOTSTRAP_PEER_ERROR = "BOOTSTRAP_PEER_ERROR";

    public static final String PORTAL_TRIGGER_DISPATCHED = "PORTAL_TRIGGER_DISPATCHED";
    public static final String PORTAL_TRIGGER_FAILED = "PORTAL_TRIGGER_FAILED";
    public static final String TRAVEL_DISPATCHED = "TRAVEL_DISPATCHED";
    public static final String DESTINATION_NOT_TRUSTED = "DESTINATION_NOT_TRUSTED";
    public static final String DESTINATION_TARGET_MISSING = "DESTINATION_TARGET_MISSING";
    public static final String INVENTORY_PROFILE_APPLY_FAILED = "INVENTORY_PROFILE_APPLY_FAILED";
    public static final String TRAVEL_ACCEPTED = "TRAVEL_ACCEPTED";
    public static final String ARRIVAL_PREPARED = "ARRIVAL_PREPARED";
    public static final String ARRIVAL_TELEPORT_QUEUED = "ARRIVAL_TELEPORT_QUEUED";
    public static final String ARRIVAL_METADATA_PARSE_FAILED = "ARRIVAL_METADATA_PARSE_FAILED";

    public static final String DISCOVERY_REQUEST_SENT = "DISCOVERY_REQUEST_SENT";
    public static final String DISCOVERY_DESTINATION_NOT_TRUSTED = "DISCOVERY_DESTINATION_NOT_TRUSTED";
    public static final String DISCOVERY_REQUEST_ANSWERED = "DISCOVERY_REQUEST_ANSWERED";
    public static final String DISCOVERY_RESPONSE_STORED = "DISCOVERY_RESPONSE_STORED";
    public static final String DISCOVERY_RESPONSE_SAVE_FAILED = "DISCOVERY_RESPONSE_SAVE_FAILED";
    public static final String DISCOVERY_REQUEST_EXPIRED = "DISCOVERY_REQUEST_EXPIRED";

    public static final String ORIGIN_BACKUP_SAVED = "ORIGIN_BACKUP_SAVED";
    public static final String LOCAL_OVERWRITE_BACKUP_SAVED = "LOCAL_OVERWRITE_BACKUP_SAVED";
    public static final String ORIGIN_INVENTORY_CLEARED = "ORIGIN_INVENTORY_CLEARED";
    public static final String TRANSFER_RECEIPT_SAVED = "TRANSFER_RECEIPT_SAVED";
    public static final String RECOVERY_QUERY_STARTED = "RECOVERY_QUERY_STARTED";
    public static final String RECOVERY_QUERY_ANSWERED_APPLIED = "RECOVERY_QUERY_ANSWERED_APPLIED";
    public static final String RECOVERY_QUERY_ANSWERED_NOT_FOUND = "RECOVERY_QUERY_ANSWERED_NOT_FOUND";
    public static final String RECOVERY_DISABLED = "RECOVERY_DISABLED";
    public static final String BACKUP_NOT_FOUND = "BACKUP_NOT_FOUND";
    public static final String RECOVERY_FINALIZE_FAILED = "RECOVERY_FINALIZE_FAILED";
    public static final String ORIGIN_BACKUP_RESTORED = "ORIGIN_BACKUP_RESTORED";
    public static final String LOCAL_BACKUP_CLAIMED = "LOCAL_BACKUP_CLAIMED";

    public static final String RULES_REFRESH_SENT = "RULES_REFRESH_SENT";
    public static final String RULES_APPLY_SENT = "RULES_APPLY_SENT";
    public static final String RULES_DESTINATION_NOT_TRUSTED = "RULES_DESTINATION_NOT_TRUSTED";
    public static final String RULES_FETCH_ANSWERED = "RULES_FETCH_ANSWERED";
    public static final String RULES_REMOTE_APPLIED = "RULES_REMOTE_APPLIED";
    public static final String RULES_CACHE_SAVED = "RULES_CACHE_SAVED";
    public static final String RULES_CACHE_SAVE_FAILED = "RULES_CACHE_SAVE_FAILED";

    public static final String BOOTSTRAP_PEER_ADDED = "BOOTSTRAP_PEER_ADDED";
    public static final String BOOTSTRAP_PEER_REMOVED = "BOOTSTRAP_PEER_REMOVED";
    public static final String BOOTSTRAP_PEERS_CLEARED = "BOOTSTRAP_PEERS_CLEARED";
    public static final String TARGET_SAVED = "TARGET_SAVED";
    public static final String TARGET_DELETED = "TARGET_DELETED";
    public static final String BINDING_SAVED = "BINDING_SAVED";
    public static final String BINDING_DELETED = "BINDING_DELETED";
    public static final String PORTAL_SAVED = "PORTAL_SAVED";
    public static final String PORTAL_DELETED = "PORTAL_DELETED";
    public static final String RULE_GROUP_SAVED = "RULE_GROUP_SAVED";
    public static final String RULE_GROUP_DELETED = "RULE_GROUP_DELETED";
    public static final String RULE_GROUP_SERVER_ASSIGNED = "RULE_GROUP_SERVER_ASSIGNED";
}
