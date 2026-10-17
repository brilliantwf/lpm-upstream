package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.protocol.Wire

// Service requests only a Mac with `serviceRestart` answers; older ones never reply.

fun LpmClient.restartService(name: String, service: String) =
    send(Wire.restartService(name, service))

fun LpmClient.stopService(name: String, service: String) = send(Wire.stopService(name, service))
