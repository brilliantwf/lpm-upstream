package cx.lpm.mobile.feature.config

import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.testing.TestSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** A session in Demo Mode, with the config store, and the sample Mac's first answers in. */
fun TestScope.demoSession(): TestSession =
    TestSession(this, setOf(SessionStore.Factory(::ProjectConfigStore))).apply {
        enterDemo()
        settle()
    }

/** Let the demo Mac answer everything sent so far. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = SETTLE_MS) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

fun TestSession.demoProject(name: String): Project? =
    store<ProjectsStore>().state.value.project(name)

private const val SETTLE_MS = 1_000L
