package cx.lpm.mobile.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put

/** Config editing (services, profiles, actions, raw YAML) and service logs. */
sealed interface WireConfig {
    /** Read a config layer's raw YAML text for the editor. `layer` ∈ project|repo|global. */
    fun readConfig(project: String, layer: String): String = frame("readConfig") {
        put("project", project)
        put("layer", layer)
    }

    /** Write a config layer's raw YAML text (comment-preserving round-trip). */
    fun saveConfig(project: String, layer: String, content: String): String = frame("saveConfig") {
        put("project", project)
        put("layer", layer)
        put("content", content)
    }

    /** Read one service's normalized mapping to seed the service form. */
    fun serviceBody(project: String, key: String): String = frame("serviceBody") {
        put("project", project)
        put("key", key)
    }

    /**
     * Read one action's normalized mapping (searching actions + terminals across
     * layers) to seed the action form. `key` may be a composite `parent:child`.
     */
    fun actionBody(project: String, key: String): String = frame("actionBody") {
        put("project", project)
        put("key", key)
    }

    /**
     * Create or update a service. `previousKey` (when different) renames the entry
     * and rewrites every reference to it. `payload` is the managed-fields map.
     */
    fun saveService(
        project: String,
        key: String,
        payload: JsonObject,
        previousKey: String?
    ): String = frame("saveService") {
        put("project", project)
        put("key", key)
        put("payload", payload)
        putNonEmpty("previousKey", previousKey)
    }

    fun deleteService(project: String, key: String): String = frame("deleteService") {
        put("project", project)
        put("key", key)
    }

    /** Create or update a profile (its ordered service list). `previousName` (when different) renames it. */
    fun saveProfile(
        project: String,
        name: String,
        services: List<String>,
        previousName: String?
    ): String = frame("saveProfile") {
        put("project", project)
        put("name", name)
        putStrings("services", services)
        putNonEmpty("previousName", previousName)
    }

    fun deleteProfile(project: String, name: String): String = frame("deleteProfile") {
        put("project", project)
        put("name", name)
    }

    /**
     * Create or update an action. `payload` is the full action mapping the form
     * built; `previousKey` (when different) renames it, preserving `section`
     * (actions|terminals).
     */
    fun saveAction(
        project: String,
        key: String,
        payload: JsonObject,
        previousKey: String?,
        section: String?
    ): String = frame("saveAction") {
        put("project", project)
        put("key", key)
        put("payload", payload)
        putNonEmpty("previousKey", previousKey)
        putNonEmpty("section", section)
    }

    fun deleteAction(project: String, key: String): String = frame("deleteAction") {
        put("project", project)
        put("key", key)
    }

    fun services(project: String): String = frame("services") { put("project", project) }

    fun serviceLogs(project: String, paneIndex: Int, lines: Int): String = frame("serviceLogs") {
        put("project", project)
        put("paneIndex", paneIndex)
        put("lines", lines)
    }

    fun serviceLogsWatch(project: String, paneIndex: Int, watch: Boolean): String =
        frame(if (watch) "serviceLogsWatch" else "serviceLogsUnwatch") {
            put("project", project)
            put("paneIndex", paneIndex)
        }
}
