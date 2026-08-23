package com.enil.logez.core.di

import javax.inject.Qualifier

/** Distinguishes the §9.5 active-session `DataStore<Preferences>` from the unqualified settings one (§5.2). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ActiveSessionDataStore
