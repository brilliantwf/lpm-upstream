package cx.lpm.mobile.core.designsystem.code

/**
 * A language's lexical rules for [SyntaxHighlighter], picked by file extension
 * ([forExtension]). [isPlain] leaves the text uncolored; [mapKeys] colors a leading
 * `key:` (YAML).
 */
class LanguageSpec internal constructor(
    val keywords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    block: Pair<String, String>? = null,
    val strings: Set<String> = emptySet(),
    val isPlain: Boolean = false,
    val mapKeys: Boolean = false
) {
    val blockOpen: String? = block?.first
    val blockClose: String? = block?.second

    companion object {
        fun forExtension(ext: String): LanguageSpec = when (ext.lowercase()) {
            "swift" -> swift
            "rs" -> rust
            "js", "jsx", "mjs", "cjs", "ts", "tsx" -> javascript
            "json" -> json
            "yaml", "yml" -> yaml
            "css" -> css
            "scss", "sass", "less" -> scss
            "html", "htm", "xml", "vue", "svelte" -> html
            "py" -> python
            "go" -> go
            "rb" -> ruby
            "sh", "bash", "zsh", "fish" -> shell
            else -> plain
        }

        private val plain = LanguageSpec(isPlain = true)

        private val swift = LanguageSpec(
            keywords = words(
                "let var func class struct enum protocol extension if else guard switch case " +
                    "default for while repeat do try catch throw throws rethrows return break " +
                    "continue in is as nil true false self Self super init deinit subscript " +
                    "typealias associatedtype import public private internal fileprivate open " +
                    "static final lazy weak unowned mutating nonmutating override convenience " +
                    "required some any where defer async await actor inout"
            ),
            lineComments = listOf("//"),
            block = "/*" to "*/",
            strings = setOf("\"")
        )

        private val rust = LanguageSpec(
            keywords = words(
                "let mut fn struct enum trait impl mod pub use crate self super if else match " +
                    "loop while for in break continue return as const static ref move dyn where " +
                    "type unsafe async await true false Some None Ok Err Box Vec String str bool " +
                    "char usize isize i8 i16 i32 i64 u8 u16 u32 u64 f32 f64"
            ),
            lineComments = listOf("//"),
            block = "/*" to "*/",
            strings = setOf("\"")
        )

        private val javascript = LanguageSpec(
            keywords = words(
                "var let const function return if else for while do switch case default break " +
                    "continue new delete typeof instanceof in of class extends super this null " +
                    "undefined true false void yield async await try catch finally throw import " +
                    "export from as interface type enum implements private public protected " +
                    "readonly static namespace declare keyof infer"
            ),
            lineComments = listOf("//"),
            block = "/*" to "*/",
            strings = setOf("\"", "'", "`")
        )

        private val json = LanguageSpec(keywords = words("true false null"), strings = setOf("\""))

        private val yaml = LanguageSpec(
            keywords = words("true false null yes no on off"),
            lineComments = listOf("#"),
            strings = setOf("\"", "'"),
            mapKeys = true
        )

        private val css = LanguageSpec(block = "/*" to "*/", strings = setOf("\"", "'"))

        private val scss = LanguageSpec(
            lineComments = listOf("//"),
            block = "/*" to "*/",
            strings = setOf("\"", "'")
        )

        private val html = LanguageSpec(block = "<!--" to "-->", strings = setOf("\"", "'"))

        private val python = LanguageSpec(
            keywords = words(
                "def class return if elif else for while break continue pass import from as " +
                    "with try except finally raise lambda yield global nonlocal in is not and or " +
                    "None True False self async await del assert"
            ),
            lineComments = listOf("#"),
            strings = setOf("\"", "'")
        )

        private val go = LanguageSpec(
            keywords = words(
                "func package import var const type struct interface map chan go defer if else " +
                    "for range switch case default break continue return select fallthrough nil " +
                    "true false iota string int int64 bool byte rune error"
            ),
            lineComments = listOf("//"),
            block = "/*" to "*/",
            strings = setOf("\"", "`")
        )

        private val ruby = LanguageSpec(
            keywords = words(
                "def end class module if elsif else unless case when while until for in do " +
                    "begin rescue ensure raise return yield next break then self nil true false " +
                    "and or not require require_relative attr_accessor attr_reader attr_writer " +
                    "lambda proc"
            ),
            lineComments = listOf("#"),
            strings = setOf("\"", "'")
        )

        private val shell = LanguageSpec(
            keywords = words(
                "if then else elif fi for while until do done case esac function in return " +
                    "select break continue export local readonly source alias echo"
            ),
            lineComments = listOf("#"),
            strings = setOf("\"", "'")
        )

        private fun words(list: String): Set<String> = list.split(' ').toSet()
    }
}
