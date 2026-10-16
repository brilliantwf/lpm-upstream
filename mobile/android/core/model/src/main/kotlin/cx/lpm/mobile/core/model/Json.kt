package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Lenient reads mirroring the iOS app's `o["key"] as? T` over JSONSerialization
// output: a missing key or a value of the wrong type reads as null, never as an
// error. JSON numbers and booleans are both NSNumber there, so `as? Int` takes
// `true` as 1 and `3.0` as 3 (but not `3.5`), and `as? Bool` takes exactly 0 and 1.
// Arrays and maps cast all-or-nothing: one element of the wrong type drops the lot.

private fun JsonElement?.numeric(): JsonPrimitive? =
    (this as? JsonPrimitive)?.takeUnless { it.isString || it is JsonNull }

/** `as? String` */
fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** `as? Bool` */
fun JsonElement?.asBool(): Boolean? {
    val p = numeric() ?: return null
    p.booleanOrNull?.let { return it }
    val d = p.content.toDoubleOrNull() ?: return null
    return when (d) {
        0.0 -> false
        1.0 -> true
        else -> null
    }
}

/** `as? Int` with Swift's 64-bit Int: exact integers only. */
fun JsonElement?.asLong(): Long? {
    val p = numeric() ?: return null
    p.booleanOrNull?.let { return if (it) 1 else 0 }
    p.content.toLongOrNull()?.let { return it }
    val d = p.content.toDoubleOrNull() ?: return null
    return d.toLong().takeIf { it.toDouble() == d }
}

/** `as? Int` for a value that always fits 32 bits; a larger one reads as null. */
fun JsonElement?.asInt(): Int? = asLong()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

/** `(x as? NSNumber)?.doubleValue`: any number or boolean. */
fun JsonElement?.asNumber(): Double? {
    val p = numeric() ?: return null
    p.booleanOrNull?.let { return if (it) 1.0 else 0.0 }
    return p.content.toDoubleOrNull()
}

/** `(x as? NSNumber)?.int64Value`: any number or boolean, truncated toward zero. */
fun JsonElement?.asNumberLong(): Long? {
    val p = numeric() ?: return null
    p.booleanOrNull?.let { return if (it) 1 else 0 }
    return p.content.toLongOrNull() ?: p.content.toDoubleOrNull()?.toLong()
}

/** `as? [String: Any]` */
fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

/** `as? [[String: Any]]` */
fun JsonElement?.asObjects(): List<JsonObject>? =
    (this as? JsonArray)?.map { it as? JsonObject ?: return null }

/** `as? [String]` */
fun JsonElement?.asStrings(): List<String>? = (this as? JsonArray)?.map {
    it.asString()
        ?: return null
}

/** `as? [Int]` */
fun JsonElement?.asInts(): List<Int>? = (this as? JsonArray)?.map { it.asInt() ?: return null }

/** `as? [String: String]` */
fun JsonElement?.asStringMap(): Map<String, String>? =
    (this as? JsonObject)?.mapValues { it.value.asString() ?: return null }

fun JsonObject.string(key: String): String? = this[key].asString()

fun JsonObject.bool(key: String): Boolean? = this[key].asBool()

fun JsonObject.int(key: String): Int? = this[key].asInt()

fun JsonObject.long(key: String): Long? = this[key].asLong()

fun JsonObject.number(key: String): Double? = this[key].asNumber()

fun JsonObject.numberLong(key: String): Long? = this[key].asNumberLong()

fun JsonObject.obj(key: String): JsonObject? = this[key].asObject()

fun JsonObject.objects(key: String): List<JsonObject>? = this[key].asObjects()

fun JsonObject.strings(key: String): List<String>? = this[key].asStrings()

fun JsonObject.ints(key: String): List<Int>? = this[key].asInts()

fun JsonObject.stringMap(key: String): Map<String, String>? = this[key].asStringMap()

/** `(o[key] as? [[String: Any]] ?? []).map(T.init)` */
inline fun <T> JsonObject.list(key: String, parse: (JsonObject) -> T): List<T> =
    objects(key).orEmpty().map(parse)

/**
 * The desktop shows `label || name`; an empty label string (common in YAML) must
 * fall back to another key too, not just a missing one.
 */
fun JsonObject.label(fallback: String): String =
    string("label").orEmpty().ifEmpty { string(fallback).orEmpty() }

/**
 * A reqId echoes back verbatim as a string or a number; normalize either to the
 * string that was sent so replies match requests (NSNumber's `stringValue`).
 */
fun JsonElement?.reqIdString(): String {
    asString()?.let { return it }
    val p = numeric() ?: return ""
    p.booleanOrNull?.let { return if (it) "1" else "0" }
    p.content.toLongOrNull()?.let { return it.toString() }
    val d = p.content.toDoubleOrNull() ?: return ""
    val whole = d.toLong()
    return if (whole.toDouble() == d) whole.toString() else d.toString()
}

/** Swift's `trimmingCharacters(in: .whitespacesAndNewlines)`. */
fun String.trimmingWhitespace(): String = trim { it.isSwiftWhitespace() }

private fun Char.isSwiftWhitespace(): Boolean = this in '\u0009'..'\u000D' ||
    this == '\u0085' ||
    when (Character.getType(this).toByte()) {
        Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
        else -> false
    }

/** The trimmed string, or null when nothing is left. */
fun JsonElement?.trimmedOrNull(): String? = asString()?.trimmingWhitespace()?.ifEmpty { null }

fun String?.trimmedOrNull(): String? = this?.trimmingWhitespace()?.ifEmpty { null }
