package cx.lpm.mobile.feature.config.add

import cx.lpm.mobile.core.model.SshHostInfo
import cx.lpm.mobile.core.model.int
import cx.lpm.mobile.core.model.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddProjectDraftTest {
    private val hosts = listOf(
        SshHostInfo("dev-server", "dev-server.local", "demo", 22, ""),
        SshHostInfo("staging", "staging.internal", "deploy", 2222, "~/.ssh/id_ed25519")
    )

    @Test
    fun `slugify matches the desktop's`() {
        assertEquals("my-cool-app", slugify("  My Cool App!  "))
        assertEquals("api_v2.1", slugify("api_v2.1"))
        assertEquals("caf", slugify("Café"))
        assertEquals("a-b", slugify("a -- b"))
        assertEquals("", slugify("—"))
    }

    @Test
    fun `names follow the folder, the repository or the connection`() {
        val local = AddProjectDraft(localRoot = "/Users/demo/Developer/playground/")
        assertEquals("playground", local.suggestedName(AddProjectMode.Local))
        val https = AddProjectDraft(cloneUrl = " https://github.com/acme/Web-Shop.git ")
        assertEquals("web-shop", https.suggestedName(AddProjectMode.Clone))
        val scp = AddProjectDraft(cloneUrl = "git@github.com:acme/api.git")
        assertEquals("api", scp.suggestedName(AddProjectMode.Clone))
        assertEquals(
            "box",
            AddProjectDraft(cloneUrl = "host:box").suggestedName(AddProjectMode.Clone)
        )
        val ssh = AddProjectDraft(host = "staging", user = "deploy")
        assertEquals("deploy-staging", ssh.suggestedName(AddProjectMode.Ssh))
        assertEquals("staging", ssh.copy(user = "").suggestedName(AddProjectMode.Ssh))
        assertEquals("", AddProjectDraft().suggestedName(AddProjectMode.Ssh))
    }

    @Test
    fun `a typed name stops following the form`() {
        val typed = AddProjectDraft(name = "mine", nameEdited = true, localRoot = "/x/y")
        assertEquals("mine", typed.rederived(AddProjectMode.Local).name)
        assertEquals("y", typed.copy(nameEdited = false).rederived(AddProjectMode.Local).name)
    }

    @Test
    fun `a saved host fills the connection, and typing another host detaches it`() {
        val picked = AddProjectDraft().pickingHost("staging", hosts)
        assertEquals("staging", picked.host)
        assertEquals("deploy", picked.user)
        assertEquals("2222", picked.port)
        assertEquals("~/.ssh/id_ed25519", picked.identityFile)
        assertEquals("deploy-staging", picked.name)

        val same = picked.editingHost("staging", hosts)
        assertEquals("staging", same.hostPick)
        val other = picked.editingHost("staging2", hosts)
        assertEquals(MANUAL_HOST_PICK, other.hostPick)
        assertEquals("deploy-staging2", other.name)

        val manual = picked.pickingHost(MANUAL_HOST_PICK, hosts)
        assertEquals("", manual.host)
        assertEquals("22", manual.port)
        assertEquals("", manual.identityFile)
        assertEquals("", manual.name)
    }

    @Test
    fun `a form is complete once its mode's fields and a name are in`() {
        assertFalse(AddProjectDraft().isComplete(AddProjectMode.Local))
        assertTrue(AddProjectDraft(localRoot = "/a", name = "a").isComplete(AddProjectMode.Local))
        assertFalse(AddProjectDraft(cloneUrl = "u", name = "u").isComplete(AddProjectMode.Clone))
        assertFalse(AddProjectDraft(localRoot = "/a", name = "!!").isComplete(AddProjectMode.Local))
        assertTrue(
            AddProjectDraft(host = "h", user = "u", name = "u-h").isComplete(AddProjectMode.Ssh)
        )
    }

    @Test
    fun `the SSH map falls back to port 22 and trims every field`() {
        val payload = AddProjectDraft(
            host = " staging ",
            user = "deploy",
            port = "abc",
            identityFile = " ~/.ssh/k ",
            remoteDir = "/srv/app "
        ).sshPayload()
        assertEquals(setOf("host", "user", "port", "key", "dir"), payload.keys)
        assertEquals("staging", payload.string("host"))
        assertEquals(22, payload.int("port"))
        assertEquals("~/.ssh/k", payload.string("key"))
        assertEquals("/srv/app", payload.string("dir"))
    }
}
