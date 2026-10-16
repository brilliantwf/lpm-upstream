package cx.lpm.mobile.core.demo

import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Swift's `[String: Any]`: a frame as maps, lists, strings, numbers and booleans, so
 * the handlers read like their Swift originals. A JSON `null` read from a frame stays
 * [JsonNull] (Swift's `NSNull`): present, but no cast matches it.
 */
internal typealias Dict = Map<String, Any?>

/** `JSONSerialization.jsonObject`: the frame's top-level object, or null. */
internal fun parseDict(text: String): Dict? {
    val element = runCatching { Json.parseToJsonElement(text) }.getOrNull()
    return (element as? JsonObject)?.let { fromJson(it).asDict() }
}

/** `JSONSerialization.data(withJSONObject:)` as compact JSON text. */
internal fun encodeJson(obj: Dict): String = toJson(obj).toString()

private fun fromJson(element: JsonElement): Any = when (element) {
    is JsonNull -> JsonNull

    is JsonPrimitive -> when {
        element.isString -> element.content
        element.content == "true" -> true
        element.content == "false" -> false
        else -> element.content.toLongOrNull() ?: element.content.toDouble()
    }

    is JsonArray -> element.map(::fromJson)

    is JsonObject -> element.mapValues { fromJson(it.value) }
}

private fun toJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull

    is JsonElement -> value

    is String -> JsonPrimitive(value)

    is Boolean -> JsonPrimitive(value)

    // JSONSerialization writes a whole Double without a fraction ("62", not "62.0").
    is Double -> if (value % 1.0 == 0.0 && abs(value) < 1e15) {
        JsonPrimitive(value.toLong())
    } else {
        JsonPrimitive(value)
    }

    is Number -> JsonPrimitive(value)

    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJson(it.value) })

    is Iterable<*> -> JsonArray(value.map(::toJson))

    else -> JsonPrimitive(value.toString())
}

// Swift `as?` casts on a JSONSerialization value. Numbers bridge like NSNumber: a whole
// number is an Int, 0 and 1 are also Bools, and a Bool is also 0 or 1.

/** `as? Int`: integral numbers and booleans. */
internal fun Any?.asLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Double -> if (this % 1.0 == 0.0 && abs(this) < 9.2e18) toLong() else null
    is Boolean -> if (this) 1L else 0L
    else -> null
}

internal fun Any?.asInt(): Int? = asLong()?.toInt()

/** `as? Bool`: booleans, and the numbers 0 and 1. */
internal fun Any?.asBool(): Boolean? = when (this) {
    is Boolean -> this

    is Number -> when (toDouble()) {
        0.0 -> false
        1.0 -> true
        else -> null
    }

    else -> null
}

/** `as? Double`. */
internal fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is Boolean -> if (this) 1.0 else 0.0
    else -> null
}

/** `(x as? NSNumber)?.intValue`: any number, truncated toward zero. */
internal fun Any?.numberValue(): Long? = when (this) {
    is Number -> toLong()
    is Boolean -> if (this) 1L else 0L
    else -> null
}

internal fun Any?.asString(): String? = this as? String

@Suppress("UNCHECKED_CAST")
internal fun Any?.asDict(): Dict? = this as? Map<String, Any?>

internal fun Any?.asList(): List<Any?>? = this as? List<*>

internal fun Any?.asStringList(): List<String>? =
    asList()?.takeIf { list -> list.all { it is String } }?.map { it as String }

internal fun Any?.asDictList(): List<Dict>? =
    asList()?.takeIf { list -> list.all { it is Map<*, *> } }?.map { it.asDict()!! }

internal fun Any?.asStringMap(): Map<String, String>? =
    asDict()?.takeIf { map -> map.values.all { it is String } }?.mapValues { it.value as String }

internal fun Dict.string(key: String): String? = this[key].asString()

internal fun Dict.int(key: String): Int? = this[key].asInt()

internal fun Dict.long(key: String): Long? = this[key].asLong()

internal fun Dict.bool(key: String): Boolean? = this[key].asBool()

internal fun Dict.dict(key: String): Dict? = this[key].asDict()

internal fun Dict.list(key: String): List<Any?>? = this[key].asList()

internal fun Dict.strings(key: String): List<String>? = this[key].asStringList()

internal fun Dict.dicts(key: String): List<Dict>? = this[key].asDictList()

/** `String(describing:)` for a JSONSerialization value. */
internal fun describing(value: Any?): String = when (value) {
    null, is JsonNull -> "<null>"

    is Boolean -> if (value) "1" else "0"

    is Double -> if (value % 1.0 == 0.0 &&
        abs(value) < 1e15
    ) {
        value.toLong().toString()
    } else {
        value.toString()
    }

    else -> value.toString()
}
