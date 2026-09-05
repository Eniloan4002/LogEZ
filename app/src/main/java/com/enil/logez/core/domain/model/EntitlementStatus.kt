package com.enil.logez.core.domain.model

/**
 * ADR-0008: whether the app is unlocked. Play's free-trial phase produces an ordinary
 * [ENTITLED]-mapped purchase for its duration — Billing Library has no separate "in trial" state,
 * and exact trial-days-remaining isn't available without a server-side Play Developer API call
 * (out of scope; this app stays local aside from Billing's own on-device IPC to the Play Store
 * app) — so this model only distinguishes what can actually be known from an on-device query.
 */
enum class EntitlementStatus {
    /** No verified or cached result yet (first-ever check still in flight). */
    UNKNOWN,

    /** An active purchase exists — trial or paid, gating behavior is identical either way. */
    ENTITLED,

    /** Purchase is pending (e.g. a GCash-style payment method mid-confirmation) — Google's policy
     * requires NOT granting access for a pending purchase. */
    PENDING,

    /** No active purchase — never subscribed, trial lapsed, or the subscription ended. */
    NOT_ENTITLED,
}
