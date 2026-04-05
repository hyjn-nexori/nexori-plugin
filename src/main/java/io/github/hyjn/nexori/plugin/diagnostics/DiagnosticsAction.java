package io.github.hyjn.nexori.plugin.diagnostics;

public final class DiagnosticsAction {

    private DiagnosticsAction() {
    }

    public static final String SECURITY_REFERRAL_DECODE = "security.referral.decode";
    public static final String SECURITY_REFERRAL_EXPIRY = "security.referral.expiry";
    public static final String SECURITY_REFERRAL_ISSUER_LOOKUP = "security.referral.issuer_lookup";
    public static final String SECURITY_REFERRAL_SIGNATURE_VERIFY = "security.referral.signature_verify";
    public static final String SECURITY_REFERRAL_PAYLOAD_TYPE_LOOKUP = "security.referral.payload_type_lookup";

    public static final String BOOTSTRAP_RUN_START = "bootstrap.run.start";
    public static final String BOOTSTRAP_RUN_RESET = "bootstrap.run.reset";
    public static final String BOOTSTRAP_PROOF_REQUEST_ANSWER = "bootstrap.proof.request.answer";
    public static final String BOOTSTRAP_PROOF_RESPONSE_VERIFY = "bootstrap.proof.response.verify";
    public static final String BOOTSTRAP_BUNDLE_BUILD = "bootstrap.bundle.build";
    public static final String BOOTSTRAP_BUNDLE_INSTALL_REQUEST = "bootstrap.bundle.install.request";
    public static final String BOOTSTRAP_BUNDLE_INSTALL_ACK = "bootstrap.bundle.install.ack";
    public static final String BOOTSTRAP_RUN_FINISH = "bootstrap.run.finish";
    public static final String BOOTSTRAP_RUN_FAIL = "bootstrap.run.fail";

    public static final String TRAVEL_PORTAL_TRIGGER = "travel.portal.trigger";
    public static final String TRAVEL_DISPATCH = "travel.dispatch";
    public static final String TRAVEL_ACCEPT = "travel.accept";
    public static final String TRAVEL_ARRIVAL_PREPARE = "travel.arrival.prepare";
    public static final String TRAVEL_ARRIVAL_TELEPORT = "travel.arrival.teleport";

    public static final String DISCOVERY_REQUEST_SEND = "discovery.request.send";
    public static final String DISCOVERY_REQUEST_ANSWER = "discovery.request.answer";
    public static final String DISCOVERY_RESPONSE_STORE = "discovery.response.store";

    public static final String RECOVERY_BACKUP_ORIGIN_SAVE = "recovery.backup.origin_save";
    public static final String RECOVERY_BACKUP_LOCAL_OVERWRITE_SAVE = "recovery.backup.local_overwrite_save";
    public static final String RECOVERY_ORIGIN_CLEAR = "recovery.origin.clear";
    public static final String RECOVERY_RECEIPT_SAVE = "recovery.receipt.save";
    public static final String RECOVERY_QUERY_START = "recovery.query.start";
    public static final String RECOVERY_QUERY_ANSWER = "recovery.query.answer";
    public static final String RECOVERY_FINALIZE = "recovery.finalize";
    public static final String RECOVERY_RESTORE_ORIGIN = "recovery.restore.origin";
    public static final String RECOVERY_CLAIM_LOCAL = "recovery.claim.local";

    public static final String RULES_REFRESH_SEND = "rules.refresh.send";
    public static final String RULES_FETCH_ANSWER = "rules.fetch.answer";
    public static final String RULES_APPLY_SEND = "rules.apply.send";
    public static final String RULES_APPLY_REMOTE = "rules.apply.remote";
    public static final String RULES_CACHE_SAVE = "rules.cache.save";

    public static final String CONFIG_BOOTSTRAP_PEER_ADD = "config.bootstrap_peer.add";
    public static final String CONFIG_BOOTSTRAP_PEER_UPDATE = "config.bootstrap_peer.update";
    public static final String CONFIG_BOOTSTRAP_PEER_REMOVE = "config.bootstrap_peer.remove";
    public static final String CONFIG_BOOTSTRAP_PEER_CLEAR = "config.bootstrap_peer.clear";
    public static final String CONFIG_TARGET_SAVE = "config.target.save";
    public static final String CONFIG_TARGET_DELETE = "config.target.delete";
    public static final String CONFIG_BINDING_SAVE = "config.binding.save";
    public static final String CONFIG_BINDING_DELETE = "config.binding.delete";
    public static final String CONFIG_PORTAL_SAVE = "config.portal.save";
    public static final String CONFIG_PORTAL_DELETE = "config.portal.delete";
    public static final String CONFIG_RULE_GROUP_SAVE = "config.rule_group.save";
    public static final String CONFIG_RULE_GROUP_DELETE = "config.rule_group.delete";
    public static final String CONFIG_RULE_GROUP_ASSIGN = "config.rule_group.assign";
}
