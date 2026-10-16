package cx.lpm.mobile.feature.projects.services

import cx.lpm.mobile.core.model.int
import cx.lpm.mobile.core.model.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceDraftTest {
    private val others = listOf("api", "worker")

    @Test
    fun `a draft needs a free name, a command and a real port`() {
        assertEquals(
            ServiceDraftError.NoName,
            ServiceDraft(cmd = "npm start").validationError(others)
        )
        assertEquals(
            ServiceDraftError.NameTaken,
            ServiceDraft(name = " api ", cmd = "x").validationError(others)
        )
        assertEquals(
            ServiceDraftError.NoCommand,
            ServiceDraft(name = "web").validationError(others)
        )
        assertEquals(
            ServiceDraftError.BadPort,
            ServiceDraft(name = "web", cmd = "x", port = "70000").validationError(others)
        )
        assertNull(ServiceDraft(name = "web", cmd = "x", port = " 3000 ").validationError(others))
    }

    @Test
    fun `the payload holds only what was filled in`() {
        val draft = ServiceDraft(
            name = "web",
            cmd = " npm run dev ",
            cwd = "apps/web",
            port = "3000",
            portConflict = PortConflict.Free,
            env = listOf(EnvRow("NODE_ENV", "development"), EnvRow(" ", "dropped")),
            dependsOn = listOf("api", "gone")
        )
        val payload = draft.payload(others)
        assertEquals("npm run dev", payload.string("cmd"))
        assertEquals("apps/web", payload.string("cwd"))
        assertEquals(3000, payload.int("port"))
        assertEquals("free", payload.string("portConflict"))
        assertEquals(setOf("NODE_ENV"), (payload["env"] as JsonObject).keys)
        assertEquals(1, (payload["dependsOn"] as JsonArray).size)
        assertEquals(setOf("cmd"), ServiceDraft(name = "w", cmd = "go").payload(others).keys)
    }

    @Test
    fun `a draft seeds from the Mac's mapping, either spelling`() {
        val body = Json.parseToJsonElement(
            """{"cmd":"npm run dev","port":3000,"port_conflict":"fail",
               "env":{"B":"2","A":1},"depends_on":["api"]}"""
        ).jsonObject
        val draft = ServiceDraft.from(body, "web")
        assertEquals("web", draft.name)
        assertEquals("3000", draft.port)
        assertEquals(PortConflict.Fail, draft.portConflict)
        assertEquals(listOf("A" to "1", "B" to "2"), draft.env.map { it.key to it.value })
        assertEquals(listOf("api"), draft.dependsOn)
    }
}
