package cx.lpm.mobile.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceExitTest {
    private fun project(running: Boolean, services: String, actionErrors: String = "[]") =
        Project.parse(
            Json.parseToJsonElement(
                """{"name":"storefront","running":$running,"services":[$services],""" +
                    """"allServices":[{"name":"web"},{"name":"api"},{"name":"worker"}],""" +
                    """"actionErrors":$actionErrors}"""
            ).jsonObject
        )

    @Test
    fun `an ended service reads its exit and stays out of the running set`() {
        val p = project(
            running = true,
            services = """{"name":"web","port":3000},{"name":"api","exited":true,"exitCode":1},""" +
                """{"name":"worker","exited":true,"exitCode":null}"""
        )
        assertEquals(setOf("web"), p.liveServiceNames)
        assertEquals(setOf("api", "worker"), p.endedServices.keys)
        assertTrue(p.endedServices.getValue("api").crashed)
        assertFalse(p.endedServices.getValue("worker").crashed)
        assertNull(p.endedServices.getValue("worker").exitCode)
        assertEquals(listOf("api"), p.crashedServices.map { it.name })
        assertTrue(p.isUp)
    }

    @Test
    fun `a project whose every started service ended is not up`() {
        val p = project(running = true, services = """{"name":"web","exited":true,"exitCode":0}""")
        assertFalse(p.isUp)
        assertTrue(p.crashedServices.isEmpty())
        assertTrue(project(running = true, services = "").isUp)
        assertFalse(project(running = false, services = """{"name":"web"}""").isUp)
    }

    @Test
    fun `a stopped project reports nothing ended or crashed`() {
        val p = project(running = false, services = """{"name":"api","exited":true,"exitCode":2}""")
        assertTrue(p.liveServiceNames.isEmpty())
        assertTrue(p.endedServices.isEmpty())
        assertTrue(p.crashedServices.isEmpty())
    }

    @Test
    fun `actions the Mac left out come with their problems`() {
        val p = project(running = false, services = "", actionErrors = """["deploy: no cmd"]""")
        assertEquals(listOf("deploy: no cmd"), p.actionErrors)
        assertEquals(emptyList<String>(), project(running = false, services = "").actionErrors)
    }

    @Test
    fun `a services reply keeps an ended pane with its exit`() {
        val info = ServiceInfo.parse(
            Json.parseToJsonElement(
                """{"name":"api","paneIndex":1,"running":false,"exited":true,"exitCode":137}"""
            ).jsonObject
        )
        assertEquals(1, info.paneIndex)
        assertTrue(info.exited)
        assertEquals(137, info.exitCode)
    }
}
