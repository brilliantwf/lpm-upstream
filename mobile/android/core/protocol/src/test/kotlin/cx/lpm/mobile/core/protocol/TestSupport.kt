package cx.lpm.mobile.core.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

inline fun <reified T : Inbound> decode(json: String): T {
    val frame = Inbound.parse(json)
    assertTrue("expected ${T::class.simpleName}, got $frame", frame is T)
    return frame as T
}

/** Compares two frames as JSON trees, so key order doesn't matter but omitted keys do. */
fun assertFrame(expected: String, actual: String) {
    assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(actual))
}
