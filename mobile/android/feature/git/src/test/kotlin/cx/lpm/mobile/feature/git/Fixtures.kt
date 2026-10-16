package cx.lpm.mobile.feature.git

import cx.lpm.mobile.core.data.git.GitAutoStore
import cx.lpm.mobile.core.data.git.GitStore
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.testing.TestSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** A session in Demo Mode, where `storefront` has four uncommitted changes. */
fun TestScope.demoSession(): TestSession = TestSession(
    this,
    setOf(SessionStore.Factory(::GitStore), SessionStore.Factory(::GitAutoStore))
).apply {
    enterDemo()
    settle()
}

/** Let the demo Mac answer everything sent so far. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = 1_000) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}
