package cx.lpm.mobile.core.data.di

import javax.inject.Qualifier

/**
 * The app-wide scope for long-lived work: a SupervisorJob on
 * Dispatchers.Main.immediate, so stores mutate state on the main thread.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** The DataStore holding UserDefaults-equivalent preferences. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PreferencesDataStore

/** The DataStore holding Keystore-encrypted credentials. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CredentialsDataStore
