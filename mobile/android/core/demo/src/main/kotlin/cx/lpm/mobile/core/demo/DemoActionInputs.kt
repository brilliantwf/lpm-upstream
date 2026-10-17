package cx.lpm.mobile.core.demo

// An action's `inputs` as its config file holds them (iOS `ActionInputsYaml`): a
// mapping keyed by the input's name, the shape the desktop's action wizard writes.

private class InputDraft(
    val key: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val placeholder: String,
    val defaultValue: String,
    val persist: Boolean,
    val options: List<Pair<String, String>>
) {
    val trimmedKey: String get() = key.trimmingWhitespacesAndNewlines()
}

/** Entries of an `inputs` mapping in the Mac's prompt order: by `position`, then by name. */
internal fun orderedInputs(map: Map<String, Dict>): List<Pair<String, Dict>> {
    fun position(key: String) = map[key]?.get("position").asDouble()
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
    return keys.map { it to (map[it] ?: emptyMap()) }
}

/**
 * The `inputs` mapping for inputs read from a body — the mapping, or the `[{key, …}]`
 * list earlier phones wrote — or null when no input has a name. `position` is written
 * only when the order differs from the alphabetical one the Mac would otherwise apply.
 */
internal fun inputsMapping(raw: Any?): Dict? {
    val usable = inputDrafts(raw).filter { it.trimmedKey.isNotEmpty() }
    if (usable.isEmpty()) return null
    val keys = usable.map { it.trimmedKey }
    val needsPositions = keys != keys.sorted()
    val out = linkedMapOf<String, Any?>()
    usable.forEachIndexed { index, input ->
        val body = linkedMapOf<String, Any?>()
        val label = input.label.trimmingWhitespacesAndNewlines()
        if (label.isNotEmpty() && label != input.trimmedKey) body["label"] = label
        if (input.type != "text") body["type"] = input.type
        if (input.required) body["required"] = true
        val placeholder = input.placeholder.trimmingWhitespacesAndNewlines()
        if (placeholder.isNotEmpty() && input.type != "radio") body["placeholder"] = placeholder
        val fallback = input.defaultValue.trimmingWhitespacesAndNewlines()
        if (fallback.isNotEmpty()) body["default"] = fallback
        if (input.persist && input.type != "password") body["persist"] = true
        if (input.type == "radio") {
            val options = input.options.mapNotNull(::optionYaml)
            if (options.isNotEmpty()) body["options"] = options
        }
        if (needsPositions) body["position"] = (index + 1).toLong()
        out[input.trimmedKey] = body
    }
    return out
}

private fun inputDrafts(raw: Any?): List<InputDraft> {
    raw.asDictList()?.let { list -> return list.map { draft(it.string("key") ?: "", it) } }
    val map = raw.asDict() ?: return emptyList()
    return orderedInputs(map.mapValues { it.value.asDict() ?: emptyMap() })
        .map { (key, body) -> draft(key, body) }
}

private fun draft(key: String, o: Dict): InputDraft {
    val label = o.string("label") ?: ""
    val type = when (o.string("type")) {
        "radio", "select" -> "radio"
        "password" -> "password"
        else -> "text"
    }
    return InputDraft(
        key = key,
        label = if (label == key) "" else label,
        type = type,
        required = o.bool("required") == true,
        placeholder = o.string("placeholder") ?: "",
        defaultValue = o.string("default") ?: "",
        persist = o.bool("persist") == true,
        options = o.list("options").orEmpty().mapNotNull(::optionDraft)
    )
}

/** A choice as value and label; the label is empty when it's the value itself. */
private fun optionDraft(raw: Any?): Pair<String, String>? {
    raw.asString()?.let { return it to "" }
    val o = raw.asDict() ?: return null
    val value = o.string("value") ?: ""
    val label = o.string("label") ?: ""
    return value to if (label == value) "" else label
}

/** A plain value, or `{label, value}` when it's shown under another name. */
private fun optionYaml(option: Pair<String, String>): Any? {
    val value = option.first.trimmingWhitespacesAndNewlines()
    if (value.isEmpty()) return null
    val label = option.second.trimmingWhitespacesAndNewlines()
    if (label.isEmpty() || label == value) return value
    return mapOf("label" to label, "value" to value)
}
