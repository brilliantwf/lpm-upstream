package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonObject

/** The port a Mac listens on unless told otherwise (iOS `MacStore.defaultPort`). */
const val DEFAULT_MAC_PORT = 8765

/**
 * One machine the connected Mac itself connects to — a Linux server or another
 * Mac — as listed by the `machines` reply.
 */
data class RemoteMachine(
    val slug: String,
    val name: String,
    val platform: String?,
    /** The id it gives phones, matching `MacRecord.serverId`; null until the Mac knows it. */
    val serverId: String?,
    val state: State,
    /** Its own projects as the Mac's sidebar shows them; null when the Mac didn't send them. */
    val projects: List<Project>?,
    /** The Mac can carry this phone to it, so it opens without being added here. */
    val relay: Boolean
) {
    enum class State(val rawValue: String) {
        /** The Mac is connected to it and can get this phone a pairing code. */
        Ready("ready"),

        /** The Mac isn't connected to it right now. */
        Offline("offline"),

        /** Its lpm is too old to hand out a pairing code on request. */
        Update("update")
        ;

        companion object {
            fun of(rawValue: String?): State? = entries.firstOrNull { it.rawValue == rawValue }
        }
    }

    val id: String get() = slug
    val isLinuxHost: Boolean get() = platform == "linux"
    val isConnected: Boolean get() = state != State.Offline

    companion object {
        fun parse(o: JsonObject): RemoteMachine = RemoteMachine(
            slug = o.string("slug").orEmpty(),
            name = o.string("name").orEmpty(),
            platform = o["platform"].trimmedOrNull(),
            serverId = o["serverId"].trimmedOrNull(),
            state = State.of(o.string("state").orEmpty()) ?: State.Offline,
            projects = o.objects("projects")?.map(Project::parse),
            relay = o.bool("relay") ?: false
        )
    }
}

/**
 * A pairing code one of the Mac's machines armed for this phone, with every address
 * the phone might reach it at and the certificate to pin.
 */
data class MachinePairOffer(
    val slug: String,
    val code: String,
    val hosts: List<String>,
    val port: Int,
    val fingerprint: String?,
    val serverId: String?,
    val name: String,
    val platform: String?
) {
    companion object {
        fun parse(o: JsonObject): MachinePairOffer {
            val port = o.int("port") ?: 0
            return MachinePairOffer(
                slug = o.string("slug").orEmpty(),
                code = o.string("code").orEmpty(),
                hosts = o.strings("hosts").orEmpty(),
                port = if (port > 0) port else DEFAULT_MAC_PORT,
                fingerprint = o["fingerprint"].trimmedOrNull(),
                serverId = o["serverId"].trimmedOrNull(),
                name = o.string("name").orEmpty(),
                platform = o["platform"].trimmedOrNull()
            )
        }
    }
}
