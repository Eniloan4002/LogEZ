package com.enil.logez.core.data.billing

import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ADR-0008: `BillingClient.Builder` requires a listener at construction time, before
 * `EntitlementRepositoryImpl` (which reacts to updates) exists as a Hilt singleton — this shim
 * breaks that construction-order cycle. It does no interpretation of its own, so it has no
 * `@Binds` interface: there is no logic here worth faking in a test.
 */
@Singleton
class PurchaseUpdatesListener @Inject constructor(
    private val externalScope: CoroutineScope,
) : PurchasesUpdatedListener {
    private val _updates = MutableSharedFlow<Pair<BillingResult, List<Purchase>?>>(extraBufferCapacity = 1)
    val updates: SharedFlow<Pair<BillingResult, List<Purchase>?>> = _updates.asSharedFlow()

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        externalScope.launch { _updates.emit(billingResult to purchases) }
    }
}
