package cx.lpm.mobile.feature.memory

import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.memory.data.MemoryStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** The session memory store as the app contributes it. */
val MemoryStores: Set<SessionStore.Factory> = setOf(SessionStore.Factory(::MemoryStore))

/** A session in Demo Mode with the sample Mac's first answers in. */
fun TestScope.demoSession(): TestSession = TestSession(this, MemoryStores).apply {
    enterDemo()
    settle()
}

/** A session on a Mac the test answers for itself. */
fun TestScope.liveSession(): TestSession = TestSession(this, MemoryStores)

/** Let the demo Mac answer everything sent so far. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = 1_000) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

val TestSession.memory: MemoryStore get() = store()

/** A `memory` reply listing [names] (title = name) in the folder `~/.lpm/memory/[owner]`. */
fun memoryReply(project: String, owner: String = project, vararg names: String): String {
    val sessions = names.joinToString(",") {
        """{"name":"$it","title":"$it","path":"","updatedAt":1,"size":1,"content":""}"""
    }
    return """{"t":"memory","project":"$project","ok":true,"exists":true,""" +
        """"dir":"~/.lpm/memory/$owner","sessions":[$sessions]}"""
}
