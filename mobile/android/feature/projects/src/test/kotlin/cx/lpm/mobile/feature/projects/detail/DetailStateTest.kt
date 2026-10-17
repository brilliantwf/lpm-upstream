package cx.lpm.mobile.feature.projects.detail

import cx.lpm.mobile.core.data.link.QueuedAction
import cx.lpm.mobile.core.data.projects.ProjectsState
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.feature.projects.project
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailStateTest {
    private val storefront = project("storefront", running = true, services = listOf("web", "api"))
    private val tab = TerminalInfo("t1", "Terminal 1", "storefront", 80, 24, false, false, "", "")

    private fun detail(
        services: List<Service> = storefront.services,
        inputs: DetailInputs = DetailInputs(terminals = listOf(tab))
    ) = ProjectsState(projects = listOf(storefront.copy(services = services)))
        .detail("storefront", inputs)

    @Test
    fun `a service that ended keeps its row, says how, and isn't counted running`() {
        val state =
            detail(listOf(Service("web", 0), Service("api", 0, exited = true, exitCode = 1)))
        val api = state.services.single { it.service.name == "api" }
        assertEquals(false, api.running)
        assertEquals(1, api.ended?.exitCode)
        assertNull(state.services.single { it.service.name == "web" }.ended)
        assertEquals(1, state.runningServices)
        assertEquals(DetailSubtitle.Crashed(listOf(api.ended!!)), state.subtitle)
    }

    @Test
    fun `a project whose services all ended cleanly reads stopped`() {
        val state = detail(listOf(Service("web", 0, exited = true, exitCode = 0)))
        assertEquals(DetailSubtitle.Stopped, state.subtitle)
    }

    @Test
    fun `an empty tab list while the Mac is away is no answer yet`() {
        val away = DetailInputs(terminals = emptyList(), live = false, linkText = "Reconnecting…")
        assertNull(detail(inputs = away).tabs)
        assertEquals(DetailSubtitle.Link("Reconnecting…"), detail(inputs = away).subtitle)
        assertEquals(1, detail(inputs = away.copy(terminals = listOf(tab))).tabs?.size)
        assertEquals(
            emptyList<TabEntry>(),
            detail(inputs = DetailInputs(terminals = emptyList())).tabs
        )
    }

    @Test
    fun `actions waiting for the Mac and its service controls reach the screen`() {
        val queued = QueuedAction("storefront", "Start", Instant.EPOCH)
        val state = detail(inputs = DetailInputs(queued = listOf(queued), canStopServices = true))
        assertEquals(listOf(queued), state.queued)
        assertTrue(state.canStopServices)
    }

    @Test
    fun `a rename goes out only for a new, non-blank name`() {
        assertNull(tabRenameResult(tab, "  "))
        assertNull(tabRenameResult(tab, " Terminal 1 "))
        assertEquals("build", tabRenameResult(tab, " build "))
    }
}
