package cx.lpm.mobile.core.data.config

import cx.lpm.mobile.core.data.testing.SessionHarness
import cx.lpm.mobile.core.model.int
import cx.lpm.mobile.core.model.obj
import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.core.model.strings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folder browser, the SSH host list and the project-creating writes. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectConfigCreationTest {
    @Test
    fun `the folder browser lists one level, and a silent Mac times out`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.loadDirs("~")
        assertTrue(store.state.value.dirListingLoading)
        val sent = socket.sentOf("listDirs").single()
        assertEquals(setOf("t", "path"), sent.keys)
        assertEquals("~", sent.string("path"))
        socket.receive(
            """{"t":"listDirs","ok":true,"path":"/Users/me","parent":"/Users",""" +
                """"dirs":["Desktop","Projects"]}"""
        )
        testScheduler.runCurrent()
        val listing = store.state.value.dirListing
        assertFalse(store.state.value.dirListingLoading)
        assertEquals("/Users/me", listing?.path)
        assertEquals("/Users", listing?.parent)
        assertEquals(listOf("Desktop", "Projects"), listing?.dirs)

        store.loadDirs("/private")
        socket.receive("""{"t":"listDirs","ok":false,"error":"Permission denied"}""")
        testScheduler.runCurrent()
        assertEquals("Permission denied", store.state.value.dirListingError)
        assertEquals("/Users/me", store.state.value.dirListing?.path)

        store.loadDirs("/Volumes")
        assertNull(store.state.value.dirListingError)
        testScheduler.advanceTimeBy(19_999)
        testScheduler.runCurrent()
        assertTrue(store.state.value.dirListingLoading)
        testScheduler.advanceTimeBy(2)
        testScheduler.runCurrent()
        assertFalse(store.state.value.dirListingLoading)
        assertEquals("The Mac didn't respond. Try again.", store.state.value.dirListingError)
    }

    @Test
    fun `SSH hosts load once, or time out as loaded with an error`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.loadSshHosts()
        assertEquals(setOf("t"), socket.sentOf("listSshHosts").single().keys)
        assertFalse(store.state.value.sshHostsLoaded)
        socket.receive(
            """{"t":"listSshHosts","ok":true,"hosts":[{"name":"staging",""" +
                """"hostName":"staging.internal","user":"deploy","port":2222,""" +
                """"identityFile":"~/.ssh/id_ed25519"}]}"""
        )
        testScheduler.runCurrent()
        val state = store.state.value
        assertTrue(state.sshHostsLoaded)
        assertEquals("staging", state.sshHosts.single().name)
        assertEquals(2222, state.sshHosts.single().port)
        assertNull(state.sshHostsError)

        val silent = SessionHarness(this)
        silent.connectReady()
        val other = silent.session.store<ProjectConfigStore>()
        other.loadSshHosts()
        testScheduler.advanceTimeBy(20_001)
        testScheduler.runCurrent()
        assertTrue(other.state.value.sshHostsLoaded)
        assertEquals("The Mac didn't respond. Try again.", other.state.value.sshHostsError)
    }

    @Test
    fun `creating a project sends the desktop's frames and settles the shared write`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()

        store.createProject("playground", "/Users/me/playground")
        val local = socket.sentOf("createProject").single()
        assertEquals(setOf("t", "name", "root"), local.keys)
        assertEquals("/Users/me/playground", local.string("root"))
        assertTrue(store.state.value.mutationInFlight)
        val projects = socket.sentOf("projects").size
        val sidebars = socket.sentOf("sidebar").size
        socket.receive("""{"t":"createProject","ok":true,"name":"playground","existing":false}""")
        testScheduler.runCurrent()
        assertFalse(store.state.value.mutationInFlight)
        assertEquals(1, store.state.value.mutationDoneToken)
        assertEquals(projects + 1, socket.sentOf("projects").size)
        assertEquals(sidebars + 1, socket.sentOf("sidebar").size)

        store.createSshProject(
            "deploy-staging",
            buildJsonObject {
                put("host", "staging")
                put("user", "deploy")
                put("port", 22)
                put("key", "~/.ssh/id_ed25519")
                put("dir", "")
            }
        )
        val ssh = socket.sentOf("createSshProject").single()
        assertEquals(setOf("t", "name", "ssh"), ssh.keys)
        val connection = ssh.obj("ssh")!!
        assertEquals(setOf("host", "user", "port", "key", "dir"), connection.keys)
        assertEquals(22, connection.int("port"))
        socket.receive(
            """{"t":"createSshProject","ok":false,"name":"deploy-staging",""" +
                """"error":"A project named “deploy-staging” already exists."}"""
        )
        testScheduler.runCurrent()
        assertEquals(
            "A project named “deploy-staging” already exists.",
            store.state.value.mutationError
        )
        assertEquals(1, store.state.value.mutationDoneToken)
    }

    @Test
    fun `a clone waits two minutes for the Mac, other writes twenty seconds`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.cloneProject("web-shop", "https://github.com/acme/web-shop.git", "", "/Users/me")
        val clone = socket.sentOf("cloneProject").single()
        assertEquals(setOf("t", "name", "url", "branch", "destParent"), clone.keys)
        assertEquals("", clone.string("branch"))
        testScheduler.advanceTimeBy(60_000)
        testScheduler.runCurrent()
        assertTrue(store.state.value.mutationInFlight)
        testScheduler.advanceTimeBy(60_001)
        testScheduler.runCurrent()
        assertFalse(store.state.value.mutationInFlight)
        assertEquals("The Mac didn't respond. Try again.", store.state.value.mutationError)

        store.createProject("late", "/Users/me/late")
        assertNull(store.state.value.mutationError)
        testScheduler.advanceTimeBy(20_001)
        testScheduler.runCurrent()
        assertEquals("The Mac didn't respond. Try again.", store.state.value.mutationError)
        assertEquals(0, store.state.value.mutationDoneToken)
    }

    @Test
    fun `a reset keeps the done token rising`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.deleteProfile("storefront", "frontend")
        assertEquals(
            setOf("t", "project", "name"),
            socket.sentOf("deleteProfile").single().keys
        )
        socket.receive(
            """{"t":"deleteProfile","ok":true,"project":"storefront","name":"frontend"}"""
        )
        testScheduler.runCurrent()
        store.loadDirs("~")
        store.reset()
        assertEquals(ProjectConfigState(mutationDoneToken = 1), store.state.value)
    }

    @Test
    fun `a profile write sends its ordered services and drops an empty previous name`() = runTest {
        val h = SessionHarness(this)
        val socket = h.connectReady()
        val store = h.session.store<ProjectConfigStore>()
        store.saveProfile("storefront", "frontend", listOf("web", "api"), previousName = null)
        val created = socket.sentOf("saveProfile").single()
        assertEquals(setOf("t", "project", "name", "services"), created.keys)
        assertEquals(listOf("web", "api"), created.strings("services"))
        socket.receive(
            """{"t":"saveProfile","ok":true,"project":"storefront","name":"frontend"}"""
        )
        testScheduler.runCurrent()
        assertEquals(1, store.state.value.mutationDoneToken)

        store.saveProfile("storefront", "web-only", listOf("web"), previousName = "frontend")
        assertEquals("frontend", socket.sentOf("saveProfile").last().string("previousName"))
    }
}
