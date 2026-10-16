package cx.lpm.mobile.core.demo

/**
 * Demo handlers for the Notes notebook: chats, paged notes, posting, editing, deleting,
 * search, and attachment bytes. Everything is mutable fixture state, so a note written
 * in Demo Mode is there when the chat is reopened, and the seeded thread is long enough
 * that paging and a short final page both happen.
 *
 * Notes have no change push on a real Mac either (screens refetch), so nothing here
 * pushes.
 */
internal fun DemoServer.registerNotesHandlers() {
    seedNotesFixtures()

    register("notesChats") { o ->
        val project = o.string("project") ?: return@register
        pushAfter(0.4) {
            ensureNotebook(project)
            mapOf(
                "t" to "notesChats",
                "project" to project,
                "ok" to true,
                "chats" to sortedChats(project).map(::noteChatDict)
            )
        }
    }

    register("notesCreateChat") { o ->
        val project = o.string("project") ?: return@register
        val title = (o.string("title") ?: "").trimmingWhitespacesAndNewlines()
        pushAfter(0.4) {
            if (title.isEmpty()) {
                return@pushAfter mapOf(
                    "t" to "notesCreateChat",
                    "project" to project,
                    "ok" to false,
                    "error" to "Give this chat a name."
                )
            }
            ensureNotebook(project)
            val chat = newChat(project, title)
            mapOf(
                "t" to "notesCreateChat",
                "project" to project,
                "ok" to true,
                "chat" to noteChatDict(chat)
            )
        }
    }

    register("notesRenameChat") { o ->
        val project = o.string("project") ?: return@register
        val chatId = o.string("chatId") ?: return@register
        val title = (o.string("title") ?: "").trimmingWhitespacesAndNewlines()
        pushAfter(0.35) {
            val reply = mutableMapOf<String, Any?>(
                "t" to "notesRenameChat",
                "project" to project,
                "chatId" to chatId
            )
            val chat = world.noteChats[project]?.firstOrNull { it.id == chatId }
            if (chat == null) {
                reply["ok"] = false
                reply["error"] = "That chat is no longer on your Mac."
                return@pushAfter reply
            }
            if (title.isNotEmpty()) chat.title = title
            reply["ok"] = true
            reply
        }
    }

    register("notesDeleteChat") { o ->
        val project = o.string("project") ?: return@register
        val chatId = o.string("chatId") ?: return@register
        pushAfter(0.4) {
            world.noteChats[project]?.removeAll { it.id == chatId }
            world.noteMessages[project]?.removeAll { it.chatId == chatId }
            sweepNoteBlobs()
            mapOf("t" to "notesDeleteChat", "project" to project, "chatId" to chatId, "ok" to true)
        }
    }

    register("notesMessages") { o ->
        val project = o.string("project") ?: return@register
        val chatId = o.string("chatId") ?: return@register
        val limit = o["limit"].numberValue()?.toInt() ?: 50
        val beforeId = o.string("beforeId") ?: ""
        pushAfter(if (beforeId.isEmpty()) 0.35 else 0.5) {
            val page = notePage(project, chatId, limit, beforeId)
            mapOf(
                "t" to "notesMessages",
                "project" to project,
                "chatId" to chatId,
                "beforeId" to beforeId,
                "ok" to true,
                "messages" to page.map(::noteDict)
            )
        }
    }

    register("notesAddMessage") { o ->
        val project = o.string("project") ?: return@register
        val chatId = o.string("chatId") ?: return@register
        val text = o.string("text") ?: ""
        val drafts = o.dicts("attachments") ?: emptyList()
        pushAfter(if (drafts.isEmpty()) 0.5 else 0.9) {
            val reply = mutableMapOf<String, Any?>(
                "t" to "notesAddMessage",
                "project" to project,
                "chatId" to chatId
            )
            when (val result = addNote(project, chatId, text, drafts)) {
                is AddNoteResult.Success -> {
                    reply["ok"] = true
                    reply["message"] = noteDict(result.note)
                }

                is AddNoteResult.Failure -> {
                    reply["ok"] = false
                    reply["error"] = result.message
                }
            }
            reply
        }
    }

    registerNoteEditHandlers()
}

private fun DemoServer.registerNoteEditHandlers() {
    register("notesEditMessage") { o ->
        val project = o.string("project") ?: return@register
        val id = o.string("id") ?: return@register
        val text = o.string("text") ?: ""
        pushAfter(0.4) {
            val reply = mutableMapOf<String, Any?>(
                "t" to "notesEditMessage",
                "project" to project,
                "id" to id
            )
            val note = world.noteMessages[project]?.firstOrNull { it.id == id }
            if (note == null) {
                reply["ok"] = false
                reply["error"] = "That note is no longer on your Mac."
                return@pushAfter reply
            }
            note.text = text
            note.editedAt = nowMillis()
            reply["ok"] = true
            reply
        }
    }

    register("notesDeleteMessage") { o ->
        val project = o.string("project") ?: return@register
        val id = o.string("id") ?: return@register
        pushAfter(0.35) {
            world.noteMessages[project]?.removeAll { it.id == id }
            sweepNoteBlobs()
            mapOf("t" to "notesDeleteMessage", "project" to project, "id" to id, "ok" to true)
        }
    }

    register("notesSearch") { o ->
        val project = o.string("project") ?: return@register
        val query = o.string("query") ?: return@register
        val limit = o["limit"].numberValue()?.toInt() ?: 50
        pushAfter(0.35) {
            mapOf(
                "t" to "notesSearch",
                "project" to project,
                "query" to query,
                "ok" to true,
                "hits" to noteHits(project, query, limit)
            )
        }
    }

    register("notesAttachment") { o ->
        val project = o.string("project") ?: return@register
        val hash = o.string("hash") ?: return@register
        pushAfter(0.5) {
            val reply = mutableMapOf<String, Any?>(
                "t" to "notesAttachment",
                "project" to project,
                "hash" to hash
            )
            val data = world.noteBlobs[hash]
            if (data == null) {
                reply["ok"] = false
                reply["error"] = "That attachment is no longer on your Mac."
                return@pushAfter reply
            }
            reply["ok"] = true
            reply["data"] = data
            reply
        }
    }
}

// wire builders

private fun noteChatDict(chat: DemoWorld.NoteChatRec): Dict = mapOf(
    "id" to chat.id,
    "title" to chat.title,
    "createdAt" to chat.createdAt,
    "updatedAt" to chat.updatedAt
)

private fun noteDict(note: DemoWorld.NoteRec): Dict {
    val o = mutableMapOf<String, Any?>(
        "id" to note.id,
        "chatId" to note.chatId,
        "ts" to note.ts,
        "text" to note.text
    )
    note.editedAt?.let { o["editedAt"] = it }
    if (note.attachments.isNotEmpty()) {
        o["attachments"] = note.attachments.map {
            mapOf(
                "hash" to it.hash,
                "name" to it.name,
                "size" to it.size,
                "mimeType" to it.mimeType
            )
        }
    }
    return o
}
