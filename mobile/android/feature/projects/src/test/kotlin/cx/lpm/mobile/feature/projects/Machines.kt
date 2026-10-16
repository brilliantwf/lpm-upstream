package cx.lpm.mobile.feature.projects

import cx.lpm.mobile.core.testing.FakeSocket
import cx.lpm.mobile.core.testing.TestSession
import kotlinx.coroutines.test.TestScope

/**
 * A Linux box the live Mac carries the phone to (`relay`), too old to be added here,
 * with a project whose agent waits and a copy of it.
 */
const val BUILD_BOX =
    """{"slug":"box","name":"Build Box","platform":"linux","serverId":"srv-box",""" +
        """"state":"update","relay":true,"projects":[{"name":"api","statusEntries":[""" +
        """{"key":"claude_code_1","value":"Waiting","paneID":"t1"}]},""" +
        """{"name":"api-2","parentName":"api"}]}"""

/** A live session on Studio Mac (projects `web`) whose machines are [machines]. */
fun TestScope.liveWithMachines(
    vararg machines: String = arrayOf(BUILD_BOX)
): Pair<TestSession, FakeSocket> {
    val t = TestSession(this, ScreenStores)
    val socket = t.connectReady()
    socket.receive("""{"t":"projects","projects":[{"name":"web"}]}""")
    socket.receive("""{"t":"machines","machines":[${machines.joinToString(",")}]}""")
    t.settle()
    return t to socket
}
