package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.ServiceInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundConfigTest {
    @Test
    fun readConfig() {
        assertEquals(
            Inbound.ReadConfig(
                "api",
                "repo",
                "services:\n  web: npm start\n",
                available = true,
                error = null
            ),
            Inbound.parse(
                """{ "t": "readConfig", "ok": true, "project": "api", "layer": "repo",
                     "content": "services:\n  web: npm start\n" }"""
            )
        )
        assertEquals(
            Inbound.ReadConfig("api", "repo", "", available = false, error = null),
            Inbound.parse(
                """{ "t": "readConfig", "ok": true, "project": "api", "layer": "repo", "available": false }"""
            )
        )
        assertEquals(
            Inbound.ReadConfig(
                "api",
                "global",
                "",
                available = false,
                error = "Couldn't read the config."
            ),
            Inbound.parse(
                """{ "t": "readConfig", "project": "api", "layer": "global", "content": "x", "available": true }"""
            )
        )
    }

    @Test
    fun saveConfigEchoesTheProjectWhenNotRenamed() {
        assertEquals(
            Inbound.SaveConfig("api", "project", "api-v2", null),
            Inbound.parse(
                """{ "t": "saveConfig", "ok": true, "project": "api", "layer": "project", "name": "api-v2" }"""
            )
        )
        assertEquals(
            Inbound.SaveConfig("api", "repo", "api", "Couldn't save the config."),
            Inbound.parse(
                """{ "t": "saveConfig", "ok": false, "project": "api", "layer": "repo" }"""
            )
        )
    }

    @Test
    fun serviceBody() {
        val f = decode<Inbound.ServiceBody>(
            """{ "t": "serviceBody", "ok": true, "project": "api", "key": "web",
                 "body": { "cmd": "npm start", "port": 3000, "env": { "NODE_ENV": "dev" } }, "source": "project" }"""
        )
        assertEquals(
            Json.parseToJsonElement(
                """{ "cmd": "npm start", "port": 3000, "env": { "NODE_ENV": "dev" } }"""
            ),
            f.body
        )
        assertEquals("project", f.source)
        assertEquals(
            Inbound.ServiceBody("api", "web", null, "", "Couldn't read the service."),
            Inbound.parse("""{ "t": "serviceBody", "project": "api", "key": "web", "body": {} }""")
        )
    }

    @Test
    fun actionBodyDefaultsItsSection() {
        val f = decode<Inbound.ActionBody>(
            """{ "t": "actionBody", "ok": true, "project": "api", "key": "deploy:prod",
                 "body": { "cmd": "make deploy", "confirm": true }, "section": "terminals", "source": "repo" }"""
        )
        assertEquals("terminals", f.section)
        assertEquals("repo", f.source)
        assertEquals(
            "actions",
            decode<Inbound.ActionBody>("""{ "t": "actionBody", "ok": true, "body": {} }""").section
        )
        assertEquals(
            Inbound.ActionBody("api", "x", null, "actions", "", "Couldn't read the action."),
            Inbound.parse("""{ "t": "actionBody", "project": "api", "key": "x" }""")
        )
    }

    @Test
    fun configWrites() {
        assertEquals(
            Inbound.SaveService("api", "web", null),
            Inbound.parse("""{ "t": "saveService", "ok": true, "project": "api", "key": "web" }""")
        )
        assertEquals(
            Inbound.SaveService("api", "web", "Couldn't save the service."),
            Inbound.parse("""{ "t": "saveService", "project": "api", "key": "web" }""")
        )
        assertEquals(
            Inbound.DeleteService("api", "web", "Couldn't delete the service."),
            Inbound.parse(
                """{ "t": "deleteService", "ok": false, "project": "api", "key": "web" }"""
            )
        )
        assertEquals(
            Inbound.SaveProfile("api", "full", null),
            Inbound.parse(
                """{ "t": "saveProfile", "ok": true, "project": "api", "name": "full" }"""
            )
        )
        assertEquals(
            Inbound.DeleteProfile("api", "full", "Couldn't delete the profile."),
            Inbound.parse("""{ "t": "deleteProfile", "project": "api", "name": "full" }""")
        )
        assertEquals(
            Inbound.SaveAction("api", "deploy", "Duplicate key"),
            Inbound.parse(
                """{ "t": "saveAction", "ok": false, "error": "Duplicate key", "project": "api", "key": "deploy" }"""
            )
        )
        assertEquals(
            Inbound.DeleteAction("api", "deploy", null),
            Inbound.parse(
                """{ "t": "deleteAction", "ok": true, "project": "api", "key": "deploy" }"""
            )
        )
        assertEquals(
            "Couldn't save the profile.",
            decode<Inbound.SaveProfile>("""{ "t": "saveProfile" }""").error
        )
        assertEquals(
            "Couldn't save the action.",
            decode<Inbound.SaveAction>("""{ "t": "saveAction" }""").error
        )
    }

    @Test
    fun services() {
        val f = decode<Inbound.Services>(
            """{ "t": "services", "project": "api", "ok": true, "running": true,
                 "services": [{ "name": "web", "paneIndex": 0, "running": true, "cmd": "npm start", "port": 3000 },
                              { "name": "worker", "cmd": "node worker.js" }] }"""
        )
        assertTrue(f.running)
        assertEquals(ServiceInfo("web", 0, true, "npm start", 3000), f.services[0])
        assertEquals(ServiceInfo("worker", null, false, "node worker.js", 0), f.services[1])
        assertEquals(
            Inbound.Services("api", true, emptyList(), "Couldn't read services."),
            Inbound.parse(
                """{ "t": "services", "project": "api", "running": true, "services": [{ "name": "web" }] }"""
            )
        )
    }

    @Test
    fun serviceLogs() {
        assertEquals(
            Inbound.ServiceLogs("api", 2, "listening on :3000\n", null),
            Inbound.parse(
                """{ "t": "serviceLogs", "project": "api", "paneIndex": 2, "ok": true, "text": "listening on :3000\n" }"""
            )
        )
        assertEquals(
            Inbound.ServiceLogs("api", 0, null, "Couldn't read the logs."),
            Inbound.parse("""{ "t": "serviceLogs", "project": "api", "text": "x" }""")
        )
    }
}
