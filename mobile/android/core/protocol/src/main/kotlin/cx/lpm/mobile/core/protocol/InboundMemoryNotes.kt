package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.MemoryList
import cx.lpm.mobile.core.model.MemorySession
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.model.NoteMessage
import cx.lpm.mobile.core.model.NoteSearchHit
import cx.lpm.mobile.core.model.list
import cx.lpm.mobile.core.model.obj

internal val memoryNotesParsers = parsers {
    put("memory") { o ->
        Inbound.Memory(
            project = o.str("project"),
            list = if (o.ok) MemoryList.parse(o) else null,
            error = o.failure("Couldn't load session memory.")
        )
    }
    put("memorySession") { o ->
        Inbound.MemorySession(
            project = o.str("project"),
            name = o.str("name"),
            session = if (o.ok) MemorySession.parse(o) else null,
            error = o.failure("Couldn't load the session.")
        )
    }
    put("memorySave") { o ->
        Inbound.MemorySave(o.str("project"), o.str("name"), o.failure("Couldn't save the session."))
    }
    put("memoryDelete") { o ->
        Inbound.MemoryDelete(
            o.str("project"),
            o.str("name"),
            o.failure("Couldn't delete the session.")
        )
    }
    put("memory-changed") { o -> Inbound.MemoryChanged(o.str("project")) }
    put("notesChats") { o ->
        Inbound.NotesChats(
            project = o.str("project"),
            chats = if (o.ok) o.list("chats", NoteChat::parse) else emptyList(),
            error = o.failure("Couldn't load notes.")
        )
    }
    put("notesCreateChat") { o ->
        Inbound.NotesCreateChat(
            project = o.str("project"),
            chat = if (o.ok) NoteChat.parse(o.obj("chat") ?: emptyObject) else null,
            error = o.failure("Couldn't create the chat.")
        )
    }
    put("notesRenameChat") { o ->
        Inbound.NotesRenameChat(
            o.str("project"),
            o.str("chatId"),
            o.failure("Couldn't rename the chat.")
        )
    }
    put("notesDeleteChat") { o ->
        Inbound.NotesDeleteChat(
            o.str("project"),
            o.str("chatId"),
            o.failure("Couldn't delete the chat.")
        )
    }
    put("notesMessages") { o ->
        Inbound.NotesMessages(
            project = o.str("project"),
            chatId = o.str("chatId"),
            beforeId = o.str("beforeId"),
            messages = if (o.ok) o.list("messages", NoteMessage::parse) else emptyList(),
            error = o.failure("Couldn't load notes.")
        )
    }
    put("notesAddMessage") { o ->
        Inbound.NotesAddMessage(
            project = o.str("project"),
            chatId = o.str("chatId"),
            message = if (o.ok) NoteMessage.parse(o.obj("message") ?: emptyObject) else null,
            error = o.failure("Couldn't add the note.")
        )
    }
    put("notesEditMessage") { o ->
        Inbound.NotesEditMessage(
            o.str("project"),
            o.str("id"),
            o.failure("Couldn't edit the note.")
        )
    }
    put("notesDeleteMessage") { o ->
        Inbound.NotesDeleteMessage(
            o.str("project"),
            o.str("id"),
            o.failure("Couldn't delete the note.")
        )
    }
    put("notesSearch") { o ->
        Inbound.NotesSearch(
            project = o.str("project"),
            query = o.str("query"),
            hits = if (o.ok) o.list("hits", NoteSearchHit::parse) else emptyList(),
            error = o.failure("Couldn't search notes.")
        )
    }
    put("notesAttachment") { o ->
        Inbound.NotesAttachment(
            project = o.str("project"),
            hash = o.str("hash"),
            data = if (o.ok) o.str("data") else null,
            error = o.failure("Couldn't open the attachment.")
        )
    }
}
