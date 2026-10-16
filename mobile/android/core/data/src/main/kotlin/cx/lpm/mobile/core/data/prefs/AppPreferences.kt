package cx.lpm.mobile.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.di.PreferencesDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UserDefaults over Preferences DataStore: [load] once before the first frame, then
 * read synchronously from memory; writes land in memory at once and reach disk
 * asynchronously, in the order they were made. Keys are the iOS names
 * ([PreferenceKeys]); like UserDefaults a read of the wrong type is null.
 */
@Singleton
class AppPreferences @Inject constructor(
    @param:PreferencesDataStore private val dataStore: DataStore<Preferences>,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val snapshot = MutableStateFlow(emptyPreferences())

    /** Every change, for screens that follow a setting live. */
    val data: StateFlow<Preferences> = snapshot.asStateFlow()

    var isLoaded = false
        private set

    suspend fun load() {
        if (isLoaded) return
        snapshot.value = dataStore.data.first()
        isLoaded = true
    }

    fun string(key: String): String? = raw(key) as? String

    fun bool(key: String): Boolean? = raw(key) as? Boolean

    fun int(key: String): Int? = raw(key) as? Int

    fun double(key: String): Double? = raw(key) as? Double

    fun contains(key: String): Boolean = raw(key) != null

    /** A null value removes the key, like `UserDefaults.removeObject`. */
    fun setString(key: String, value: String?) = write(stringPreferencesKey(key), value)

    fun setBool(key: String, value: Boolean?) = write(booleanPreferencesKey(key), value)

    fun setInt(key: String, value: Int?) = write(intPreferencesKey(key), value)

    fun setDouble(key: String, value: Double?) = write(doublePreferencesKey(key), value)

    fun remove(key: String) = write(stringPreferencesKey(key), null)

    // Preferences keys compare by name only, so any typed key finds the stored value.
    private fun raw(key: String): Any? = snapshot.value.asMap()[stringPreferencesKey(key)]

    private fun <T> write(key: Preferences.Key<T>, value: T?) {
        val edit: (MutablePreferences) -> Unit = { prefs ->
            prefs.asMap().keys.filter { it.name == key.name }.forEach { prefs.remove(it) }
            if (value != null) prefs[key] = value
        }
        snapshot.update { it.toMutablePreferences().apply(edit).toPreferences() }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { dataStore.edit(edit) }
    }
}
