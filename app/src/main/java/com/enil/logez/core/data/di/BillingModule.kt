package com.enil.logez.core.data.di

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.enil.logez.core.data.billing.PurchaseUpdatesListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ADR-0008: `BillingClient` is builder-constructed (`.newBuilder(context).setListener(...)
 * .enablePendingPurchases(...).build()`), so it can't have an `@Inject constructor` — same
 * reasoning as `DataModule.provideDatabase()`. Kept as its own small module rather than folded
 * into `DataModule` because Billing is a distinct vendor-SDK concern, not local-persistence infra.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    fun provideBillingClient(
        @ApplicationContext context: Context,
        listener: PurchaseUpdatesListener,
    ): BillingClient =
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
}
