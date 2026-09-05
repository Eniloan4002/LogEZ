package com.enil.logez.core.di

import javax.inject.Qualifier

/** Distinguishes the §9.5 active-session `DataStore<Preferences>` from the unqualified settings one (§5.2). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveSessionDataStore

/** ADR-0008: the offline-grace entitlement cache's own `DataStore<Preferences>` — a separate store
 * from settings, deliberately: entitlement state is Billing-verified and Owner-facing, not a user
 * preference. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EntitlementDataStore
