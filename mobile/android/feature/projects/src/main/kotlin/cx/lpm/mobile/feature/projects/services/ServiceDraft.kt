package cx.lpm.mobile.feature.projects.services

import cx.lpm.mobile.core.model.int
import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.core.model.trimmingWhitespace
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One environment variable row; [id] keeps a row's identity while either field changes. */
data class EnvRow(
    val key: String = "",
    val value: String = "",
    val id: String = UUID.randomUUID().toString()
)

/** What the port conflict picker offers: ask (empty), free the port, or don't start. */
enum class PortConflict(val wire: String) { Ask(""), Free("free"), Fail("fail") }

/** The service form's draft, the phone's take on the desktop's ServiceForm. */
data class ServiceDraft(
    val name: String = "",
    val cmd: String = "",
    val cwd: String = "",
    val port: String = "",
    val portConflict: PortConflict = PortConflict.Ask,
    val env: List<EnvRow> = emptyList(),
    val dependsOn: List<String> = emptyList()
) {
    private val portValid: Boolean
        get() {
            val trimmed = port.trimmingWhitespace()
            if (trimmed.isEmpty()) return true
            return trimmed.toIntOrNull()?.let { it in 1..MAX_PORT } == true
        }

    /** Why it can't be saved yet, named as the form does; null once it can. */
    fun validationError(otherServices: List<String>): ServiceDraftError? {
        val name = name.trimmingWhitespace()
        return when {
            name.isEmpty() -> ServiceDraftError.NoName
            name in otherServices -> ServiceDraftError.NameTaken
            cmd.trimmingWhitespace().isEmpty() -> ServiceDraftError.NoCommand
            !portValid -> ServiceDraftError.BadPort
            else -> null
        }
    }

    fun toggleDependsOn(service: String): ServiceDraft =
        copy(dependsOn = if (service in dependsOn) dependsOn - service else dependsOn + service)

    /** The managed fields the Mac writes into the service's layer file. */
    fun payload(otherServices: List<String>): JsonObject = buildJsonObject {
        put("cmd", cmd.trimmingWhitespace())
        cwd.trimmingWhitespace().takeIf { it.isNotEmpty() }?.let { put("cwd", it) }
        port.trimmingWhitespace().toIntOrNull()?.takeIf { it > 0 }?.let { put("port", it) }
        if (portConflict != PortConflict.Ask) put("portConflict", portConflict.wire)
        val vars = env.filter { it.key.trimmingWhitespace().isNotEmpty() }
        if (vars.isNotEmpty()) {
            putJsonObject("env") { vars.forEach { put(it.key.trimmingWhitespace(), it.value) } }
        }
        val deps = dependsOn.filter { it in otherServices }
        if (deps.isNotEmpty()) putJsonArray("dependsOn") { deps.forEach { add(JsonPrimitive(it)) } }
    }

    companion object {
        private const val MAX_PORT = 65_535

        /** A draft seeded from the Mac's mapping for the service called [key]. */
        fun from(body: JsonObject, key: String): ServiceDraft {
            val conflict = body.string("portConflict") ?: body.string("port_conflict")
            val env = body["env"] as? JsonObject
            val deps = (body["dependsOn"] ?: body["depends_on"]) as? JsonArray
            return ServiceDraft(
                name = key,
                cmd = body.string("cmd").orEmpty(),
                cwd = body.string("cwd").orEmpty(),
                port = body.int("port")?.takeIf { it > 0 }?.toString().orEmpty(),
                portConflict =
                    PortConflict.entries.firstOrNull {
                        it.wire == conflict && it != PortConflict.Ask
                    }
                        ?: PortConflict.Ask,
                env = env?.keys?.sorted()?.map { k ->
                    EnvRow(
                        k,
                        env[k]?.let {
                            (it as? JsonPrimitive)?.contentOrNull ?: it.toString()
                        }.orEmpty()
                    )
                }.orEmpty(),
                dependsOn = deps?.map { it.jsonPrimitive.content }.orEmpty()
            )
        }
    }
}

enum class ServiceDraftError { NoName, NameTaken, NoCommand, BadPort }
