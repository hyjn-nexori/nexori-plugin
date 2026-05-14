package io.github.hyjn.nexori.plugin.accessgate.logic;

/**
 * Pure access-gate bypass category resolved by the runtime service before admission policy evaluation.
 */
public enum AccessGateBypassType {
    NONE,
    UUID,
    TRUSTED_REFERRAL
}
