package cx.lpm.mobile.feature.notes

import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.notes.data.NotesStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** The notes store as the app contributes it. */
val NotesStores: Set<SessionStore.Factory> = setOf(SessionStore.Factory(::NotesStore))

/** A session in Demo Mode with the sample Mac's first answers in. */
fun TestScope.demoSession(): TestSession = TestSession(this, NotesStores).apply {
    enterDemo()
    settle()
}

/** A session on a Mac the test answers for itself. */
fun TestScope.liveSession(): TestSession = TestSession(this, NotesStores)

/** Let the demo Mac answer everything sent so far. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = 1_500) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

val TestSession.notes: NotesStore get() = store()

/** A note as the wire describes it; [extra] adds fields (`"editedAt":1`). */
fun noteJson(
    id: String,
    chatId: String = "c1",
    ts: Long = 1,
    text: String = id,
    extra: String = ""
): String =
    """{"id":"$id","chatId":"$chatId","ts":$ts,"text":"$text"${if (extra.isEmpty()) "" else ",$extra"}}"""

/** A `notesMessages` reply carrying [notes] for the page asked [beforeId]. */
fun pageReply(project: String, chatId: String, beforeId: String, notes: List<String>): String =
    """{"t":"notesMessages","project":"$project","chatId":"$chatId","beforeId":"$beforeId",""" +
        """"ok":true,"messages":[${notes.joinToString(",")}]}"""
