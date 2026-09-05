package com.enil.logez.core.domain.model

/**
 * ADR-0008: a point-in-time read of [EntitlementStatus], distinguishing a fresh Billing Library
 * answer from one replayed out of the local offline-grace cache (see
 * `EntitlementRepositoryImpl.resolveOfflineSnapshot`). [isFromCache] is metadata for a future gate
 * screen (e.g. an "offline" indicator) — it is never itself a gating input.
 */
data class EntitlementSnapshot(
    val status: EntitlementStatus,
    val lastVerifiedAtMillis: Long?,
    val isFromCache: Boolean,
)
