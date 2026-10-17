package cx.lpm.mobile.core.model

import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What an action input asks for, as the desktop's action wizard names it. The YAML
 * `type` is `text` (the default, left out), `radio` or `password`.
 */
enum class ActionInputKind(val yaml: String) {
    Text("text"),
    Radio("radio"),
    Password("password");

    companion object {
        /** Older phones wrote a choice as `select`. */
        fun of(yaml: String?): ActionInputKind = when (yaml) {
            "radio", "select" -> Radio
            "password" -> Password
            else -> Text
        }
    }
}

data class ActionOptionDraft(
    val value: String = "",
    val label: String = "",
    val id: String = UUID.randomUUID().toString()
)

/** One input of an action being edited on the phone. */
data class ActionInputDraft(
    val key: String = "",
    val label: String = "",
    val kind: ActionInputKind = ActionInputKind.Text,
    val required: Boolean = false,
    val placeholder: String = "",
    val defaultValue: String = "",
    val persist: Boolean = false,
    val options: List<ActionOptionDraft> = emptyList(),
    val id: String = UUID.randomUUID().toString()
) {
    val trimmedKey: String get() = key.trimmingWhitespace()

    val optionValues: List<String>
        get() = options.map { it.value.trimmingWhitespace() }.filter { it.isNotEmpty() }
}

/** Why an action's inputs can't be saved yet, mirroring the desktop's checks. */
sealed interface ActionInputsProblem {
    /** A name uses something other than letters, numbers, dots, dashes and underscores. */
    data object BadName : ActionInputsProblem

    data class Duplicate(val key: String) : ActionInputsProblem

    data class NoChoices(val key: String) : ActionInputsProblem

    /** A choice input's starting choice is no longer among its choices. */
    data class StaleStart(val key: String) : ActionInputsProblem
}

/**
 * An action's `inputs` as its config file holds them: a mapping keyed by the input's
 * name, the shape the desktop reads and its wizard writes (`inputsToYamlMap`).
 */
object ActionInputsYaml {
    /**
     * Drafts from an action body's `inputs`, in the order the Mac asks them: by
     * `position`, then by name. Also reads the `[{key, …}]` list earlier phones
     * wrote, so an action saved that way still shows its inputs.
     */
    fun drafts(raw: JsonElement?): List<ActionInputDraft> {
        raw.asObjects()?.let { list -> return list.map { draft(it.string("key").orEmpty(), it) } }
        val map = raw.asObject() ?: return emptyList()
        val bodies = map.mapValues { it.value as? JsonObject ?: JsonObject(emptyMap()) }
        return ordered(bodies).map { (key, body) -> draft(key, body) }
    }

    /** Entries of an `inputs` mapping in the Mac's prompt order. */
    fun ordered(map: Map<String, JsonObject>): List<Pair<String, JsonObject>> {
        fun position(key: String) = map[key]?.get("position").asNumber()
        val keys = map.keys.sortedWith { a, b ->
            val pa = position(a)
            val pb = position(b)
            when {
                pa != null && pb != null -> if (pa == pb) a.compareTo(b) else pa.compareTo(pb)
                pa != null -> -1
                pb != null -> 1
                else -> a.compareTo(b)
            }
        }
        return keys.map { it to (map[it] ?: JsonObject(emptyMap())) }
    }

    /**
     * The `inputs` mapping for an action body, or null when no input has a name.
     * `position` is written only when the order differs from the alphabetical one the
     * Mac would otherwise apply.
     */
    fun map(drafts: List<ActionInputDraft>): JsonObject? {
        val usable = drafts.filter { it.trimmedKey.isNotEmpty() }
        if (usable.isEmpty()) return null
        val keys = usable.map { it.trimmedKey }
        val needsPositions = keys != keys.sorted()
        val entries = LinkedHashMap<String, JsonElement>()
        usable.forEachIndexed { index, input ->
            entries[input.trimmedKey] =
                body(input, position = (index + 1).takeIf { needsPositions })
        }
        return JsonObject(entries)
    }

    fun problem(drafts: List<ActionInputDraft>): ActionInputsProblem? {
        val seen = mutableSetOf<String>()
        for (input in drafts) {
            val key = input.trimmedKey
            if (key.isEmpty()) continue
            if (!key.all { it.isKeyCharacter() }) return ActionInputsProblem.BadName
            if (!seen.add(key)) return ActionInputsProblem.Duplicate(key)
            if (input.kind != ActionInputKind.Radio) continue
            val values = input.optionValues
            if (values.isEmpty()) return ActionInputsProblem.NoChoices(key)
            val fallback = input.defaultValue.trimmingWhitespace()
            if (fallback.isNotEmpty() && fallback !in values) {
                return ActionInputsProblem.StaleStart(key)
            }
        }
        return null
    }

    private fun body(input: ActionInputDraft, position: Int?) = buildJsonObject {
        val label = input.label.trimmingWhitespace()
        if (label.isNotEmpty() && label != input.trimmedKey) put("label", label)
        if (input.kind != ActionInputKind.Text) put("type", input.kind.yaml)
        if (input.required) put("required", true)
        val placeholder = input.placeholder.trimmingWhitespace()
        if (placeholder.isNotEmpty() && input.kind != ActionInputKind.Radio) {
            put("placeholder", placeholder)
        }
        val fallback = input.defaultValue.trimmingWhitespace()
        if (fallback.isNotEmpty()) put("default", fallback)
        if (input.persist && input.kind != ActionInputKind.Password) put("persist", true)
        if (input.kind == ActionInputKind.Radio) {
            val options = input.options.mapNotNull(::optionYaml)
            if (options.isNotEmpty()) put("options", JsonArray(options))
        }
        if (position != null) put("position", position)
    }

    private fun draft(key: String, o: JsonObject): ActionInputDraft {
        val label = o.string("label").orEmpty()
        return ActionInputDraft(
            key = key,
            label = if (label == key) "" else label,
            kind = ActionInputKind.of(o.string("type")),
            required = o.bool("required") == true,
            placeholder = o.string("placeholder").orEmpty(),
            defaultValue = o.string("default").orEmpty(),
            persist = o.bool("persist") == true,
            options = (o["options"] as? JsonArray).orEmpty().mapNotNull(::optionDraft)
        )
    }

    private fun optionDraft(raw: JsonElement): ActionOptionDraft? {
        raw.asString()?.let { return ActionOptionDraft(value = it) }
        val o = raw.asObject() ?: return null
        val value = o.string("value").orEmpty()
        val label = o.string("label").orEmpty()
        return ActionOptionDraft(value = value, label = if (label == value) "" else label)
    }

    /**
     * A plain value, or `{label, value}` when it's shown under another name — the
     * desktop reads a mapping only when it has both.
     */
    private fun optionYaml(option: ActionOptionDraft): JsonElement? {
        val value = option.value.trimmingWhitespace()
        if (value.isEmpty()) return null
        val label = option.label.trimmingWhitespace()
        if (label.isEmpty() || label == value) return JsonPrimitive(value)
        return buildJsonObject {
            put("label", label)
            put("value", value)
        }
    }

    // The `{{key}}` tokens the Mac substitutes allow only these characters.
    private fun Char.isKeyCharacter(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "_.-"
}
