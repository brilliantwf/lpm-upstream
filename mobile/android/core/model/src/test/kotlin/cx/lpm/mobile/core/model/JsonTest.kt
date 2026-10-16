package cx.lpm.mobile.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun stringReadsOnlyJsonStrings() {
        val o = obj("""{"s":"x","n":1,"b":true,"z":null}""")
        assertEquals("x", o.string("s"))
        assertNull(o.string("n"))
        assertNull(o.string("b"))
        assertNull(o.string("z"))
        assertNull(o.string("missing"))
    }

    @Test
    fun boolTakesBooleansAndExactlyZeroOrOne() {
        val o = obj("""{"t":true,"f":false,"one":1,"zero":0.0,"two":2,"s":"true","z":null}""")
        assertEquals(true, o.bool("t"))
        assertEquals(false, o.bool("f"))
        assertEquals(true, o.bool("one"))
        assertEquals(false, o.bool("zero"))
        assertNull(o.bool("two"))
        assertNull(o.bool("s"))
        assertNull(o.bool("z"))
    }

    @Test
    fun intIsExactLikeNSNumberBridging() {
        val o = obj("""{"i":42,"whole":3.0,"frac":3.5,"exp":1e3,"t":true,"s":"7","neg":-5}""")
        assertEquals(42, o.int("i"))
        assertEquals(3, o.int("whole"))
        assertNull(o.int("frac"))
        assertEquals(1000, o.int("exp"))
        assertEquals(1, o.int("t"))
        assertNull(o.int("s"))
        assertEquals(-5, o.int("neg"))
    }

    @Test
    fun longKeepsMillisecondTimestampsAndIntRejectsThem() {
        val o = obj("""{"ms":1734567890123}""")
        assertEquals(1_734_567_890_123L, o.long("ms"))
        assertNull(o.int("ms"))
    }

    @Test
    fun numberReadsAnyNumericValue() {
        val o = obj("""{"d":12.5,"i":3,"t":true,"s":"1"}""")
        assertEquals(12.5, o.number("d")!!, 0.0)
        assertEquals(3.0, o.number("i")!!, 0.0)
        assertEquals(1.0, o.number("t")!!, 0.0)
        assertNull(o.number("s"))
    }

    @Test
    fun numberLongTruncatesLikeInt64Value() {
        val o = obj("""{"d":3.7,"n":-3.7,"big":9007199254740993,"f":false}""")
        assertEquals(3L, o.numberLong("d"))
        assertEquals(-3L, o.numberLong("n"))
        assertEquals(9_007_199_254_740_993L, o.numberLong("big"))
        assertEquals(0L, o.numberLong("f"))
    }

    @Test
    fun collectionsCastAllOrNothing() {
        val o = obj(
            """{"s":["a","b"],"mixed":["a",1],"i":[1,2.0],"badInts":[1,1.5],
               "objs":[{},{"a":1}],"badObjs":[{},1],"map":{"k":"v"},"badMap":{"k":1}}"""
        )
        assertEquals(listOf("a", "b"), o.strings("s"))
        assertNull(o.strings("mixed"))
        assertEquals(listOf(1, 2), o.ints("i"))
        assertNull(o.ints("badInts"))
        assertEquals(2, o.objects("objs")!!.size)
        assertNull(o.objects("badObjs"))
        assertEquals(mapOf("k" to "v"), o.stringMap("map"))
        assertNull(o.stringMap("badMap"))
        assertEquals(emptyList<Int>(), o.list("badObjs") { 1 })
    }

    @Test
    fun labelFallsBackOnMissingOrEmptyLabel() {
        assertEquals("api", obj("""{"label":"","name":"api"}""").label(fallback = "name"))
        assertEquals("api", obj("""{"name":"api"}""").label(fallback = "name"))
        assertEquals("API", obj("""{"label":"API","name":"api"}""").label(fallback = "name"))
        assertEquals("", obj("""{}""").label(fallback = "name"))
    }

    @Test
    fun reqIdStringNormalizesStringsAndNumbers() {
        val o = obj("""{"s":"abc","i":42,"whole":42.0,"d":1.5,"t":true,"z":null}""")
        assertEquals("abc", o["s"].reqIdString())
        assertEquals("42", o["i"].reqIdString())
        assertEquals("42", o["whole"].reqIdString())
        assertEquals("1.5", o["d"].reqIdString())
        assertEquals("1", o["t"].reqIdString())
        assertEquals("", o["z"].reqIdString())
        assertEquals("", o["missing"].reqIdString())
    }

    @Test
    fun trimmingMatchesSwiftWhitespaceAndNewlines() {
        assertEquals("a b", " \t\n a b \r\n".trimmingWhitespace())
        assertEquals("x", "  x\u0085　".trimmingWhitespace())
        assertEquals("\u001Cx", "\u001Cx".trimmingWhitespace())
        assertNull("  ".trimmedOrNull())
        assertNull(obj("""{"n":1}""")["n"].trimmedOrNull())
        assertEquals("v", obj("""{"s":" v "}""")["s"].trimmedOrNull())
    }

    @Test
    fun nullAndMissingAreDistinctOnlyByKey() {
        val o = obj("""{"z":null}""")
        assertTrue("z" in o)
        assertFalse("missing" in o)
        assertNull(o.obj("z"))
    }
}
