package cx.lpm.mobile.feature.git.diff

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import cx.lpm.mobile.core.designsystem.code.LanguageSpec
import cx.lpm.mobile.core.designsystem.code.SyntaxColors
import cx.lpm.mobile.core.designsystem.code.SyntaxHighlighter
import cx.lpm.mobile.core.designsystem.code.SyntaxToken
import cx.lpm.mobile.core.designsystem.theme.DarkLpmColors
import cx.lpm.mobile.core.designsystem.theme.LightLpmColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every diff in `ios-parity/diffs.json`, parsed and highlighted by the port, against
 * what the iOS `ParsedDiff` and `DiffHighlighter` produced for it
 * (`ios-parity/generate.sh`): the same lines, numbers, blocks and text, every run in
 * the same light and dark colors, and the same rules for every language.
 */
class DiffParityTest {
    private val expected = load("expected.json").jsonObject
    private val light = SyntaxColors(LightLpmColors)
    private val dark = SyntaxColors(DarkLpmColors)

    @Test
    fun `every diff parses, pads and colors as on iOS`() {
        val cases = load("diffs.json").jsonArray.map { it.jsonObject }
        val byName = expected.getValue("cases").jsonArray.associateBy { it.name }
        for (case in cases) {
            val name = case.name
            val ext = case["ext"]?.jsonPrimitive?.content.orEmpty()
            val actual = case["diff"]?.let { parsedJson(it.jsonPrimitive.content, ext) }
                ?: highlightJson(
                    case.getValue("lines").jsonArray.map {
                        it.jsonPrimitive.content
                    },
                    ext
                )
            val ios = byName.getValue(name).jsonObject - "name"
            assertEquals(name, ios, actual)
        }
    }

    @Test
    fun `every language has the iOS rules`() {
        for (ios in expected.getValue("specs").jsonArray.map { it.jsonObject }) {
            val ext = ios.getValue("ext").jsonPrimitive.content
            val spec = LanguageSpec.forExtension(ext)
            val actual = buildJsonObject {
                put("blockClose", spec.blockClose)
                put("blockOpen", spec.blockOpen)
                put("ext", ext)
                put("isPlain", spec.isPlain)
                put("keywords", strings(spec.keywords.sorted()))
                put("lineComments", strings(spec.lineComments))
                put("mapKeys", spec.mapKeys)
                put("strings", strings(spec.strings.sorted()))
            }
            assertEquals(ext, ios, actual)
        }
    }

    private fun parsedJson(diff: String, ext: String): JsonObject {
        val parsed = ParsedDiff.parse(diff, ext)
        return buildJsonObject {
            put("added", parsed.addedCount)
            put("blocks", JsonArray(parsed.blocks.map(::blockJson)))
            put(
                "lines",
                JsonArray(
                    parsed.lines.map { line ->
                        buildJsonObject {
                            put("hunkLabel", line.hunkLabel)
                            put("id", line.id)
                            put("indentHint", line.indentHint)
                            put("kind", line.kind.iosName)
                            put("new", line.newNumber)
                            put("old", line.oldNumber)
                            put("text", line.text)
                        }
                    }
                )
            )
            put("prompt", promptDiff(diff))
            put("removed", parsed.removedCount)
        }
    }

    private fun blockJson(block: DiffBlock) = buildJsonObject {
        put("code", block.code)
        put("codeRuns", runs(codeColors(block)))
        put("gutter", block.gutter)
        put("gutterRuns", runs(gutterColors(block)))
        put("hunkContext", block.hunkContext)
        put("hunkLabel", block.hunkLabel)
        put("id", block.id)
        put("indentHint", block.indentHint)
        put("kind", if (block.kind == DiffBlockKind.Hunk) "hunk" else "code")
        put("lineCount", block.lineCount)
        put("rows", strings(block.rows.map { it.iosName }))
    }

    private fun highlightJson(lines: List<String>, ext: String): JsonObject {
        val highlighter = SyntaxHighlighter(ext)
        return buildJsonObject {
            put(
                "highlight",
                JsonArray(
                    lines.map { line ->
                        val runs = highlighter.highlight(line)
                        val colors = runs.flatMap { run ->
                            List(run.text.length) {
                                run.token.desc to
                                    ""
                            }
                        }
                        buildJsonObject {
                            put("runs", runs(colors))
                            put("text", runs.joinToString("") { it.text })
                        }
                    }
                )
            )
        }
    }

    /** Each UTF-16 unit's (foreground, background), as the attributed string paints it. */
    private fun codeColors(block: DiffBlock): List<Pair<String, String>> {
        val fg = Array(block.code.length) { SyntaxToken.Plain.desc }
        val bg = Array(block.code.length) { "" }
        block.tokens.forEach { span ->
            for (i in span.start until span.end) fg[i] = span.token.desc
        }
        block.emphasis.forEach { span ->
            val tint = if (span.added) DiffPalette.addWord else DiffPalette.delWord
            for (i in span.start until span.end) bg[i] = tint.desc
        }
        return fg.zip(bg)
    }

    private fun gutterColors(block: DiffBlock): List<Pair<String, String>> {
        val lines = if (block.gutter.isEmpty()) emptyList() else block.gutter.split("\n")
        return lines.flatMapIndexed { index, line ->
            val tint = when (block.rows[index]) {
                DiffLineKind.Added -> DiffPalette.addGutter
                DiffLineKind.Removed -> DiffPalette.delGutter
                else -> DiffPalette.contextGutter
            }
            val cells = List(line.length) { "secondaryLabel" to tint.desc }
            if (index < lines.lastIndex) cells + ("" to "") else cells
        }
    }

    /** Equal neighbors merged into `[location, length, fg, bg]`, as NSAttributedString enumerates. */
    private fun runs(colors: List<Pair<String, String>>): JsonArray = buildJsonArray {
        var start = 0
        for (i in 1..colors.size) {
            if (i < colors.size && colors[i] == colors[start]) continue
            if (i > start) {
                add(
                    JsonArray(
                        listOf(
                            JsonPrimitive(start),
                            JsonPrimitive(i - start),
                            JsonPrimitive(colors[start].first),
                            JsonPrimitive(colors[start].second)
                        )
                    )
                )
            }
            start = i
        }
    }

    private val SyntaxToken.desc: String
        get() = when (this) {
            SyntaxToken.Plain -> "label"
            SyntaxToken.Comment -> "secondaryLabel"
            else -> pair(light.color(this), dark.color(this))
        }

    private val Dynamic.desc: String get() = "#%06X/#%06X".format(light, dark)

    private fun pair(light: Color, dark: Color) =
        "#%06X/#%06X".format(light.toArgb() and RGB, dark.toArgb() and RGB)

    private val DiffLineKind.iosName: String get() = name.lowercase()

    private val JsonElement.name: String get() = jsonObject.getValue("name").jsonPrimitive.content

    private fun strings(list: List<String>) = JsonArray(list.map(::JsonPrimitive))

    private fun load(file: String): JsonElement {
        val text = checkNotNull(javaClass.getResource("/ios-parity/$file")).readText()
        return Json.parseToJsonElement(text)
    }

    private operator fun JsonObject.minus(key: String) = JsonObject(this.filterKeys { it != key })
}

private const val RGB = 0xFFFFFF
