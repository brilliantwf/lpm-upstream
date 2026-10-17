package cx.lpm.mobile.feature.actions.editor

import cx.lpm.mobile.core.model.ActionInputsYaml
import cx.lpm.mobile.core.model.trimmingWhitespace
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The action mapping the Mac writes, as a desktop edit would. An edit starts from the
 * action's body on disk so fields the form doesn't show (shortcut, port, position, …)
 * survive; a new action starts empty for the cleanest YAML. Each field is set, or
 * removed when cleared — the desktop's ActionPatch set/remove pair — so a cleared
 * value doesn't linger.
 */
fun ActionDraft.payload(editing: Boolean, seedBody: JsonObject?): JsonObject {
    val p = PayloadBuilder(if (editing) seedBody.orEmpty() else emptyMap())
    p["label"] = label.trimmingWhitespace().ifEmpty { null }?.let(::JsonPrimitive)
    p["emoji"] = emoji.trimmingWhitespace().ifEmpty { null }?.let(::JsonPrimitive)
    when (kind) {
        ActionKind.Command -> {
            p["cmd"] = JsonPrimitive(cmd.trimmingWhitespace())
            p["cwd"] = cwd.trimmingWhitespace().ifEmpty { null }?.let(::JsonPrimitive)
            p["type"] = runIn.typeValue?.let(::JsonPrimitive)
            p["reuse"] = if (runIn == ActionRunIn.Terminal && reuse) JsonPrimitive(true) else null
            p["confirm"] = if (confirm) JsonPrimitive(true) else null
        }

        ActionKind.Menu -> MenuOnlyRemovals.forEach { p[it] = null }
    }
    if (!editing || placement != seededPlacement) {
        p["display"] = displayValue(editing)?.let(::JsonPrimitive)
        p["layer"] = placement.layer?.let(::JsonPrimitive)
    }
    when (kind) {
        ActionKind.Command -> {
            p["inputs"] = inputsValue(seedBody)
            p["actions"] = null
            p["primary"] = null
        }

        ActionKind.Menu -> {
            val (children, keys) = childrenPayload()
            p["actions"] = children
            p["primary"] = when (val choice = primary) {
                PrimaryChoice.Menu -> null
                PrimaryChoice.LastUsed -> JsonPrimitive(ActionDraft.LAST_USED)
                is PrimaryChoice.Child -> keys[choice.id]?.let(::JsonPrimitive)
            }
        }
    }
    p["color"] = color.ifEmpty { null }?.let(::JsonPrimitive)
    return p.build()
}

/** Fields only a command button has, dropped when it becomes a menu. */
private val MenuOnlyRemovals =
    listOf("cmd", "cwd", "type", "reuse", "confirm", "port", "portConflict", "inputs")

private fun ActionDraft.displayValue(editing: Boolean): String? = when (val p = placement) {
    ActionPlacement.Footer -> "footer"
    is ActionPlacement.InZone -> p.zone
    ActionPlacement.Header -> if (editing) "header" else null
}

/**
 * The inputs mapping, or an empty one once every input saved before has been
 * removed: the Mac keeps an `inputs` the payload leaves out.
 */
private fun ActionDraft.inputsValue(seedBody: JsonObject?): JsonObject? =
    ActionInputsYaml.map(inputs) ?: seedBody?.get("inputs")?.let { JsonObject(emptyMap()) }

/**
 * The menu's items keyed for YAML, and the key each draft item got. An item from
 * disk keeps its key; a new one takes a slug of its label.
 */
private fun ActionDraft.childrenPayload(): Pair<JsonObject, Map<String, String>> {
    val map = LinkedHashMap<String, JsonElement>()
    val keys = mutableMapOf<String, String>()
    children.forEachIndexed { index, child ->
        val cmd = child.cmd.trimmingWhitespace()
        if (cmd.isEmpty()) return@forEachIndexed
        val base = child.key.ifEmpty { slugify(child.label) }.ifEmpty { "option-${index + 1}" }
        val key = uniqueKey(base, map.keys)
        keys[child.id] = key
        map[key] = buildJsonObject {
            put("label", child.label.trimmingWhitespace().ifEmpty { key })
            put("cmd", cmd)
            child.runIn.typeValue?.let { put("type", it) }
        }
    }
    return JsonObject(map) to keys
}

internal fun slugify(raw: String): String = raw.lowercase()
    .map { if (it.isLetter() || it.category in NumberCategories) it else '-' }
    .joinToString("")
    .split('-')
    .filter { it.isNotEmpty() }
    .joinToString("-")

private val NumberCategories = setOf(
    CharCategory.DECIMAL_DIGIT_NUMBER,
    CharCategory.LETTER_NUMBER,
    CharCategory.OTHER_NUMBER
)

private fun uniqueKey(base: String, taken: Set<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("$base-$n" in taken) n++
    return "$base-$n"
}

/** A mapping edited in place: a set keeps the key's place, a null removes it. */
private class PayloadBuilder(seed: Map<String, JsonElement>) {
    private val map = LinkedHashMap(seed)

    operator fun set(key: String, value: JsonElement?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    fun build() = JsonObject(map)
}
