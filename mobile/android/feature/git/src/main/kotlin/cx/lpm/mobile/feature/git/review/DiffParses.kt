package cx.lpm.mobile.feature.git.review

import cx.lpm.mobile.core.model.GitDiffResult
import cx.lpm.mobile.feature.git.diff.ParsedDiff
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Each file's parsed and highlighted diff, built on [dispatcher] once per distinct
 * diff text: a refresh that brings back the same diff keeps its parse, a changed one
 * drops it (the file shows "Loading diff…") until the new parse lands.
 */
internal class DiffParses(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher
) {
    private val mutable = MutableStateFlow<Map<String, ParsedDiff>>(emptyMap())

    /** path → parse. */
    val parsed: StateFlow<Map<String, ParsedDiff>> = mutable.asStateFlow()

    private val sources = mutableMapOf<String, String>()
    private val jobs = mutableMapOf<String, Job>()

    /** The project's current diffs, by path. */
    fun update(diffs: Map<String, GitDiffResult>) {
        for (path in sources.keys - diffs.keys) forget(path)
        for ((path, result) in diffs) {
            if (result.binary || result.diff.isBlank()) {
                forget(path)
                continue
            }
            if (sources[path] == result.diff) continue
            forget(path)
            val diff = result.diff
            sources[path] = diff
            jobs[path] = scope.launch {
                val parse = withContext(dispatcher) { ParsedDiff.parse(diff, extension(path)) }
                if (sources[path] == diff) mutable.update { it + (path to parse) }
            }
        }
    }

    private fun forget(path: String) {
        jobs.remove(path)?.cancel()
        sources.remove(path)
        if (path in mutable.value) mutable.update { it - path }
    }
}

/** `NSString.pathExtension`: what follows the last dot of the last path component. */
internal fun extension(path: String): String {
    val name = path.trimEnd('/').substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    return if (dot <= 0) "" else name.substring(dot + 1)
}

/** `NSString.lastPathComponent`. */
internal fun lastPathComponent(path: String): String {
    val trimmed = path.trimEnd('/')
    return if (trimmed.isEmpty()) path else trimmed.substringAfterLast('/')
}
