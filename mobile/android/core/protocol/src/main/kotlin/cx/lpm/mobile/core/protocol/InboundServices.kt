package cx.lpm.mobile.core.protocol

internal val serviceParsers = parsers {
    for (t in listOf("restartService", "stopService")) {
        put(t) { o -> Inbound.ServiceOp(error = o.failure("Couldn't change the service.")) }
    }
}
