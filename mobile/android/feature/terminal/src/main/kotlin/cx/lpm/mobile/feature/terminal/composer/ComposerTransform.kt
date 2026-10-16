package cx.lpm.mobile.feature.terminal.composer

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The AI rewrite in flight, if any. */
internal class RewriteRun {
    var reqId: String? = null
    var variantCount = 1

    // The tab the rewrite started on, so its result lands there even if the person
    // switched or closed tabs meanwhile (dropped if the tab is gone).
    var tabId: String? = null

    // Latches once the variants picker has opened, so dismissing it doesn't bring
    // it back when the next variant streams in.
    var openedVariants = false
    var timeout: Job? = null
}

private const val REWRITE_TIMEOUT_MS = 180_000L
private const val MAX_VARIANTS = 5
private const val REWRITE_FAILED = "The rewrite couldn’t be completed. Try again."
private const val REWRITE_TIMED_OUT = "The rewrite timed out. Try again."

fun ComposerStore.startTransform(instruction: String, variants: Int) {
    val text = current.text
    if (text.isBlank()) return
    update {
        it.copy(
            transforming = true,
            transformError = null,
            variants = emptyList(),
            showVariants = false
        )
    }
    rewrite.variantCount = variants.coerceIn(1, MAX_VARIANTS)
    rewrite.tabId = current.activeTab.id
    rewrite.openedVariants = false
    rewrite.reqId = link.runTransform(termId, project, instruction, text, rewrite.variantCount)
    armTransformTimeout()
}

/** Stop waiting here; late frames for this reqId are ignored. */
fun ComposerStore.cancelTransform() {
    releaseTransform()
    update { it.copy(showVariants = false, variants = emptyList()) }
}

fun ComposerStore.receiveTransformVariant(reqId: String, idx: Int, text: String?, error: String?) {
    if (reqId != rewrite.reqId) return
    update { it.copy(variants = it.variants + TransformVariant(idx, text, error)) }
    armTransformTimeout()
    // A multi-variant run opens the picker on its first result so the rest stream
    // in live, the composer staying locked underneath.
    if (rewrite.variantCount > 1 && !rewrite.openedVariants) {
        rewrite.openedVariants = true
        update { it.copy(showVariants = true) }
    }
}

fun ComposerStore.finishTransform(reqId: String, ok: Boolean) {
    if (reqId != rewrite.reqId) return
    val tabId = rewrite.tabId
    releaseTransform()
    if (!ok) {
        update {
            it.copy(showVariants = false, transformError = REWRITE_FAILED, variants = emptyList())
        }
        return
    }
    if (rewrite.variantCount == 1) {
        val applied = current.variants.firstNotNullOfOrNull { it.text }
        if (applied != null) setText(applied, tabId)
        update {
            it.copy(
                showVariants = false,
                transformError = if (applied == null) REWRITE_FAILED else it.transformError,
                variants = emptyList()
            )
        }
    }
    // A multi-variant success leaves the picker open (transformDone lands just after
    // the last variant) until a variant is picked or the sheet dismissed.
}

/** Commit a picked variant to the tab the rewrite started on. */
fun ComposerStore.applyVariant(text: String) {
    val tabId = rewrite.tabId
    releaseTransform()
    update { it.copy(showVariants = false) }
    setText(text, tabId)
    update { it.copy(variants = emptyList()) }
}

/**
 * The variants sheet closed. Closed while still streaming cancels the run;
 * otherwise the unpicked variants are dropped.
 */
fun ComposerStore.variantsSheetDismissed() {
    if (current.transforming) releaseTransform()
    update { it.copy(showVariants = false, variants = emptyList()) }
}

fun ComposerStore.dismissTransformError() = update { it.copy(transformError = null) }

/**
 * Release the run's lock, reqId, timeout and route without touching the picker,
 * so a finished multi-variant batch leaves it open to choose from.
 */
private fun ComposerStore.releaseTransform() {
    val reqId = rewrite.reqId
    rewrite.reqId = null
    rewrite.timeout?.cancel()
    rewrite.timeout = null
    update { it.copy(transforming = false) }
    link.clearTransformRoute(reqId)
}

/** Unlock with an error if `transformDone` never arrives (dropped frame or link). */
private fun ComposerStore.armTransformTimeout() {
    rewrite.timeout?.cancel()
    val reqId = rewrite.reqId
    rewrite.timeout = scope.launch {
        delay(REWRITE_TIMEOUT_MS)
        if (rewrite.reqId != reqId || !current.transforming) return@launch
        rewrite.timeout = null
        releaseTransform()
        update {
            it.copy(
                showVariants = false,
                variants = emptyList(),
                transformError = REWRITE_TIMED_OUT
            )
        }
    }
}
