package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryPurchasesAsync
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.billing.PurchaseUpdatesListener
import com.enil.logez.core.di.EntitlementDataStore
import com.enil.logez.core.domain.model.EntitlementSnapshot
import com.enil.logez.core.domain.model.EntitlementStatus
import com.enil.logez.core.domain.repository.EntitlementRepository
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ADR-0008: wraps Play [BillingClient] to answer "is this device entitled right now?" — the
 * connection is kept open for the process lifetime (Google's own guidance; [endConnection] is
 * never called). Every genuinely branching decision lives in the two pure, `internal` functions at
 * the bottom of this file, kept free of any Play SDK type so they run under plain JUnit with no
 * Robolectric — this class itself is just Play-SDK plumbing around them.
 */
class EntitlementRepositoryImpl @Inject constructor(
    private val billingClient: BillingClient,
    purchaseUpdatesListener: PurchaseUpdatesListener,
    @EntitlementDataStore private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
    private val externalScope: CoroutineScope,
) : EntitlementRepository {

    private object Keys {
        val STATUS = stringPreferencesKey("status")
        val LAST_VERIFIED_AT_MILLIS = longPreferencesKey("lastVerifiedAtMillis")
    }

    private val _entitlement = MutableStateFlow(EntitlementSnapshot(EntitlementStatus.UNKNOWN, null, isFromCache = false))
    override val entitlement: Flow<EntitlementSnapshot> = _entitlement.asStateFlow()

    init {
        // Load whatever was last verified before any refresh() is ever called, so a future
        // collector (M19b's gate) doesn't sit on UNKNOWN while waiting for a live round-trip.
        externalScope.launch {
            readCache()?.let { cached ->
                _entitlement.value = EntitlementSnapshot(cached.status, cached.lastVerifiedAtMillis, isFromCache = true)
            }
        }
        // A purchase completed/changed elsewhere (e.g. the paywall's launchBillingFlow, M19b) —
        // re-verify rather than trusting the pushed payload directly.
        externalScope.launch {
            purchaseUpdatesListener.updates.collect { (billingResult, _) ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) refresh()
            }
        }
    }

    override suspend fun refresh(): EntitlementSnapshot {
        if (!ensureConnected()) return fallBackToCache()

        val purchasesResult = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            )
        } ?: return fallBackToCache()

        if (purchasesResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return fallBackToCache()
        }

        val purchase = purchasesResult.purchasesList.find { SUBSCRIPTION_PRODUCT_ID in it.products }
        if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            acknowledge(purchase)
        }

        val status = mapToStatus(purchase?.let { PurchaseInfo(it.purchaseState, it.isAcknowledged) })
        val nowMillis = clock.now().toEpochMilliseconds()
        persistCache(status, nowMillis)
        return EntitlementSnapshot(status, nowMillis, isFromCache = false).also { _entitlement.value = it }
    }

    /** Non-fatal: a failed acknowledgement is retried on the next [refresh] — Google auto-refunds
     * only after 3 days unacknowledged, so one missed attempt is not urgent. */
    private suspend fun acknowledge(purchase: Purchase) {
        runCatching {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
        }
    }

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return withTimeoutOrNull(CONNECTION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) {
                            continuation.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        // enableAutoServiceReconnection() (BillingModule) handles retries.
                    }
                })
            }
        } ?: false
    }

    private suspend fun fallBackToCache(): EntitlementSnapshot {
        val nowMillis = clock.now().toEpochMilliseconds()
        val snapshot = resolveOfflineSnapshot(readCache(), nowMillis, LOCAL_OFFLINE_GRACE_PERIOD_MILLIS)
        _entitlement.value = snapshot
        return snapshot
    }

    private suspend fun persistCache(status: EntitlementStatus, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.STATUS] = status.name
            prefs[Keys.LAST_VERIFIED_AT_MILLIS] = atMillis
        }
    }

    private suspend fun readCache(): CachedEntitlement? {
        val prefs = dataStore.data.first()
        val status = prefs[Keys.STATUS]?.let { runCatching { EntitlementStatus.valueOf(it) }.getOrNull() } ?: return null
        val lastVerifiedAtMillis = prefs[Keys.LAST_VERIFIED_AT_MILLIS] ?: return null
        return CachedEntitlement(status, lastVerifiedAtMillis)
    }

    companion object {
        // TODO(Owner): reconcile with the real product ID once created in Play Console (ADR-0008).
        private const val SUBSCRIPTION_PRODUCT_ID = "logez_pro_monthly"
        private const val CONNECTION_TIMEOUT_MILLIS = 10_000L
        private const val QUERY_TIMEOUT_MILLIS = 10_000L

        // Deliberately NOT called "grace period" alone — Play Console has its own unrelated
        // server-side subscription grace period (account-hold/dunning for failed payments),
        // handled entirely by Play and never surfacing here. This one is purely local cache
        // staleness for when BillingClient genuinely can't be reached (ADR-0008, Owner-confirmed
        // 2026-09-06: 3 days).
        private const val LOCAL_OFFLINE_GRACE_PERIOD_MILLIS = 3 * 24 * 60 * 60 * 1000L
    }
}

internal data class CachedEntitlement(val status: EntitlementStatus, val lastVerifiedAtMillis: Long)

internal data class PurchaseInfo(val purchaseState: Int, val isAcknowledged: Boolean)

/**
 * The offline-grace decision (ADR-0008: "a briefly offline device isn't hard-locked out"). Pure
 * function — no cache -> [EntitlementStatus.UNKNOWN]; within the window -> replay the cached
 * status as-is (including NOT_ENTITLED — grace doesn't resurrect someone who was never entitled);
 * past the window -> fail closed to NOT_ENTITLED, since grace forgives connectivity, not lapsed
 * payment.
 */
internal fun resolveOfflineSnapshot(
    cached: CachedEntitlement?,
    nowMillis: Long,
    gracePeriodMillis: Long,
): EntitlementSnapshot = when {
    cached == null -> EntitlementSnapshot(EntitlementStatus.UNKNOWN, null, isFromCache = true)
    nowMillis - cached.lastVerifiedAtMillis <= gracePeriodMillis ->
        EntitlementSnapshot(cached.status, cached.lastVerifiedAtMillis, isFromCache = true)
    else -> EntitlementSnapshot(EntitlementStatus.NOT_ENTITLED, cached.lastVerifiedAtMillis, isFromCache = true)
}

/**
 * Takes a plain [PurchaseInfo], not the real Play [Purchase] — [Purchase] parses its fields via
 * `org.json` internally, which is stub-and-throws on a plain (non-Robolectric) JVM unit test. The
 * one BillingClient call site extracts the two fields this needs, so this decision itself stays
 * pure Kotlin.
 */
internal fun mapToStatus(purchase: PurchaseInfo?): EntitlementStatus = when {
    purchase == null -> EntitlementStatus.NOT_ENTITLED
    purchase.purchaseState == Purchase.PurchaseState.PENDING -> EntitlementStatus.PENDING
    purchase.purchaseState == Purchase.PurchaseState.PURCHASED -> EntitlementStatus.ENTITLED
    else -> EntitlementStatus.NOT_ENTITLED
}
