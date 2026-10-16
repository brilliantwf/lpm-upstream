package cx.lpm.mobile.feature.actions

import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.model.asStringMap
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What the phone remembers about a project's controls, kept as the desktop keeps its own. */
data class ActionMemoryState(
    /** `<project>/<zone>` → the layer the zone shows. */
    val openLayers: Map<String, String> = emptyMap(),
    /** `<project>/<group>` → the child a `primary: last-used` group ran last. */
    val lastUsed: Map<String, String> = emptyMap()
) {
    fun openLayer(project: String, zone: String): String? = openLayers["$project/$zone"]

    fun lastUsed(project: String, group: String): String? = lastUsed["$project/$group"]
}

/** iOS's `ZoneLayerMemory` and `ActionPrimaryMemory`, under the same keys. */
class ActionMemory @Inject constructor(private val prefs: AppPreferences) {
    val state: Flow<ActionMemoryState> = prefs.data
        .map {
            ActionMemoryState(
                read(PreferenceKeys.ZONE_OPEN_LAYERS),
                read(PreferenceKeys.ACTION_PRIMARY_LAST_USED)
            )
        }
        .distinctUntilChanged()

    fun showLayer(project: String, zone: String, layer: String) =
        write(PreferenceKeys.ZONE_OPEN_LAYERS, "$project/$zone", layer)

    fun rememberChild(project: String, group: String, child: String) =
        write(PreferenceKeys.ACTION_PRIMARY_LAST_USED, "$project/$group", child)

    private fun read(key: String): Map<String, String> {
        val raw = prefs.string(key) ?: return emptyMap()
        return runCatching { Json.parseToJsonElement(raw) }.getOrNull().asStringMap().orEmpty()
    }

    private fun write(key: String, entry: String, value: String) {
        val all = read(key) + (entry to value)
        prefs.setString(key, JsonObject(all.mapValues { JsonPrimitive(it.value) }).toString())
    }
}
