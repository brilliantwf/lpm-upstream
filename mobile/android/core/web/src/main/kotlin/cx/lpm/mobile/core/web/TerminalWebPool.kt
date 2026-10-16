package cx.lpm.mobile.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper

/**
 * Pre-warms the terminal WebView. The first WebView an app creates loads the
 * WebView provider and starts its renderer process, and the page then has to
 * parse xterm.js, so the first terminal would open slowly. [prewarm] builds one
 * terminal page in the background of the main thread's idle time, and the next
 * terminal screen takes it, already loaded; another is warmed for the one after.
 */
object TerminalWebPool {
    // Built on the application context; a screen's context goes in only on take().
    @SuppressLint("StaticFieldLeak")
    private var warm: TerminalPage? = null
    private var scheduled = false

    /** Idempotent; call from the main thread once the first screen is up. */
    fun prewarm(context: Context) {
        if (warm != null || scheduled) return
        scheduled = true
        val app = context.applicationContext
        Looper.getMainLooper().queue.addIdleHandler {
            scheduled = false
            if (warm == null) {
                val page = TerminalPage.create(app)
                page.onGone = { drop(page) }
                warm = page
            }
            false
        }
    }

    internal fun take(context: Context): TerminalPage {
        val page = warm?.also { warm = null } ?: TerminalPage.create(context)
        page.onGone = {}
        page.adopt(context)
        prewarm(context)
        return page
    }

    private fun drop(page: TerminalPage) {
        if (warm !== page) return
        warm = null
        page.destroy()
    }
}
