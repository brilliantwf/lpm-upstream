package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonObject

// Notes wire types. Every timestamp here is unix MILLISECONDS — session memory's
// `updatedAt` is seconds.

/** One notebook chat. `updatedAt` orders the list (most recent first). */
data class NoteChat(val id: String, val title: String, val createdAt: Long, val updatedAt: Long) {
    companion object {
        fun parse(o: JsonObject): NoteChat = NoteChat(
            id = o.string("id").orEmpty(),
            title = o.string("title").orEmpty(),
            createdAt = o.long("createdAt") ?: 0,
            updatedAt = o.long("updatedAt") ?: 0
        )
    }
}

/**
 * One note. The wire key for the timestamp is `ts`; `editedAt` and `attachments`
 * are omitted entirely when unset, hence the nullables.
 */
data class NoteMessage(
    val id: String,
    val chatId: String,
    val ts: Long,
    val text: String,
    val editedAt: Long?,
    val attachments: List<NoteAttachment>?
) {
    companion object {
        fun parse(o: JsonObject): NoteMessage = NoteMessage(
            id = o.string("id").orEmpty(),
            chatId = o.string("chatId").orEmpty(),
            ts = o.long("ts") ?: 0,
            text = o.string("text").orEmpty(),
            editedAt = o.long("editedAt"),
            attachments = o.objects("attachments")?.map(NoteAttachment::parse)
        )
    }
}

/**
 * One attached file. `hash` is the sha256 of the plaintext and the key
 * `notesAttachment` takes; `size` is the real byte length, so the phone can refuse
 * a fetch above the link's per-file cap without asking for it.
 */
data class NoteAttachment(val hash: String, val name: String, val size: Int, val mimeType: String) {
    val id: String get() = hash

    companion object {
        fun parse(o: JsonObject): NoteAttachment = NoteAttachment(
            hash = o.string("hash").orEmpty(),
            name = o.string("name").orEmpty(),
            size = o.int("size") ?: 0,
            mimeType = o.string("mimeType").orEmpty()
        )
    }
}

/** One search result. `id` is the **message** id, not the chat's. */
data class NoteSearchHit(
    val id: String,
    val chatId: String,
    val chatTitle: String,
    val ts: Long,
    val snippet: String
) {
    companion object {
        fun parse(o: JsonObject): NoteSearchHit = NoteSearchHit(
            id = o.string("id").orEmpty(),
            chatId = o.string("chatId").orEmpty(),
            chatTitle = o.string("chatTitle").orEmpty(),
            ts = o.long("ts") ?: 0,
            snippet = o.string("snippet").orEmpty()
        )
    }
}
