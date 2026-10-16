package cx.lpm.mobile.feature.mac

import cx.lpm.mobile.core.data.machines.MachineImporter
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.testing.FakeSocket
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.feature.mac.data.MacUpdateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** A live session with the update store and the machine importer. */
fun TestScope.macSession(): TestSession = TestSession(
    this,
    setOf(SessionStore.Factory(::MacUpdateStore), SessionStore.Factory(::MachineImporter))
)

/** Connect to a saved Mac whose `ready` reports `version`. */
fun TestSession.readyOn(version: String = "0.6.10", selfUpdate: Boolean = true): FakeSocket {
    savedMac()
    session.bootstrap()
    scheduler.runCurrent()
    return answerReady(version, selfUpdate)
}

/** The link dropped: wait for the client to dial again and let the Mac answer `ready`. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.reconnectOn(version: String, selfUpdate: Boolean = true): FakeSocket {
    val before = transport.sockets.size
    var waited = 0
    while (transport.sockets.size == before && waited < MAX_WAIT_MS) {
        scheduler.advanceTimeBy(STEP_MS)
        scheduler.runCurrent()
        waited += STEP_MS.toInt()
    }
    return answerReady(version, selfUpdate)
}

@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = 0) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

private fun TestSession.answerReady(version: String, selfUpdate: Boolean): FakeSocket {
    val socket = transport.last
    socket.open()
    scheduler.runCurrent()
    socket.receive(
        """{"t":"ready","serverId":"srv-1","serverName":"Studio Mac",""" +
            """"version":"$version","selfUpdate":$selfUpdate}"""
    )
    scheduler.runCurrent()
    return socket
}

/** The Mac's `updateCheck` reply. */
fun FakeSocket.checkAnswer(
    latest: String? = "0.6.12",
    available: Boolean = true,
    phase: String = "idle",
    progress: Int = -1
) = receive(
    """{"t":"updateCheck","ok":true,"currentVersion":"0.6.10","latestVersion":"$latest",""" +
        """"updateAvail":$available,"phase":"$phase","progress":$progress}"""
)

fun FakeSocket.progress(phase: String, progress: Int = -1) =
    receive("""{"t":"updateProgress","phase":"$phase","progress":$progress}""")

private const val STEP_MS = 500L
private const val MAX_WAIT_MS = 60_000
