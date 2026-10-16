package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.NoteAttachment
import cx.lpm.mobile.core.model.NoteChat
import cx.lpm.mobile.core.model.NoteMessage
import cx.lpm.mobile.core.model.NoteSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundMemoryNotesTest {
    @Test
    fun memoryList() {
        val f = decode<Inbound.Memory>(
            """{ "t": "memory", "project": "storefront-2", "ok": true, "exists": true,
                 "dir": "/Users/me/.lpm/memory/storefront",
                 "sessions": [{ "name": "auth-refactor", "title": "Auth refactor", "path": "/Users/me/.lpm/memory/storefront/auth-refactor.md",
                                "updatedAt": 1760000000, "size": 4096, "content": "# Auth refactor" }] }"""
        )
        val list = f.list!!
        assertTrue(list.exists)
        assertEquals("storefront", list.owner)
        assertEquals(1_760_000_000L, list.sessions.single().updatedAt)
        assertEquals("auth-refactor", list.sessions.single().id)
        val empty =
            decode<Inbound.Memory>(
                """{ "t": "memory", "project": "p", "ok": true, "exists": false, "dir": "/x/p" }"""
            )
        assertFalse(empty.list!!.exists)
        assertTrue(empty.list.sessions.isEmpty())
        assertEquals(
            Inbound.Memory("p", null, "Couldn't load session memory."),
            Inbound.parse("""{ "t": "memory", "project": "p" }""")
        )
    }

    @Test
    fun memorySessionReadsTheDocumentFromTheFrame() {
        val f = decode<Inbound.MemorySession>(
            """{ "t": "memorySession", "project": "p", "name": "auth", "ok": true, "title": "Auth",
                 "path": "/x/auth.md", "updatedAt": 5, "size": 12, "content": "# Auth\nnotes" }"""
        )
        assertEquals("auth", f.name)
        assertEquals("auth", f.session!!.name)
        assertEquals("# Auth\nnotes", f.session.content)
        assertEquals(
            Inbound.MemorySession("p", "auth", null, "Couldn't load the session."),
            Inbound.parse("""{ "t": "memorySession", "project": "p", "name": "auth" }""")
        )
    }

    @Test
    fun memoryWritesAndPush() {
        assertEquals(
            Inbound.MemorySave("p", "auth", null),
            Inbound.parse("""{ "t": "memorySave", "project": "p", "name": "auth", "ok": true }""")
        )
        assertEquals(
            Inbound.MemorySave("p", "auth", "modified"),
            Inbound.parse(
                """{ "t": "memorySave", "project": "p", "name": "auth", "ok": false, "error": "modified" }"""
            )
        )
        assertEquals(
            Inbound.MemorySave("p", "auth", "Couldn't save the session."),
            Inbound.parse("""{ "t": "memorySave", "project": "p", "name": "auth" }""")
        )
        assertEquals(
            Inbound.MemoryDelete("p", "auth", "Couldn't delete the session."),
            Inbound.parse(
                """{ "t": "memoryDelete", "project": "p", "name": "auth", "ok": false }"""
            )
        )
        assertEquals(
            Inbound.MemoryChanged("storefront"),
            Inbound.parse("""{ "t": "memory-changed", "project": "storefront" }""")
        )
    }

    @Test
    fun notesChats() {
        assertEquals(
            Inbound.NotesChats(
                "p",
                listOf(NoteChat("c-1", "Ideas", 1_760_000_000_000, 1_760_000_500_000)),
                null
            ),
            Inbound.parse(
                """{ "t": "notesChats", "project": "p", "ok": true,
                     "chats": [{ "id": "c-1", "title": "Ideas", "createdAt": 1760000000000, "updatedAt": 1760000500000 }] }"""
            )
        )
        assertEquals(
            Inbound.NotesChats("p", emptyList(), "Couldn't load notes."),
            Inbound.parse("""{ "t": "notesChats", "project": "p" }""")
        )
        assertEquals(
            Inbound.NotesCreateChat("p", NoteChat("c-2", "Todo", 1, 1), null),
            Inbound.parse(
                """{ "t": "notesCreateChat", "project": "p", "ok": true, "chat": { "id": "c-2", "title": "Todo", "createdAt": 1, "updatedAt": 1 } }"""
            )
        )
        assertEquals(
            NoteChat("", "", 0, 0),
            decode<Inbound.NotesCreateChat>("""{ "t": "notesCreateChat", "ok": true }""").chat
        )
        assertEquals(
            Inbound.NotesCreateChat("p", null, "Couldn't create the chat."),
            Inbound.parse("""{ "t": "notesCreateChat", "project": "p" }""")
        )
        assertEquals(
            Inbound.NotesRenameChat("p", "c-1", null),
            Inbound.parse(
                """{ "t": "notesRenameChat", "project": "p", "chatId": "c-1", "ok": true }"""
            )
        )
        assertEquals(
            Inbound.NotesDeleteChat("p", "c-1", "Couldn't delete the chat."),
            Inbound.parse("""{ "t": "notesDeleteChat", "project": "p", "chatId": "c-1" }""")
        )
        assertEquals(
            "Couldn't rename the chat.",
            decode<Inbound.NotesRenameChat>("""{ "t": "notesRenameChat" }""").error
        )
    }

    @Test
    fun notesMessages() {
        val f = decode<Inbound.NotesMessages>(
            """{ "t": "notesMessages", "project": "p", "chatId": "c-1", "beforeId": "", "ok": true,
                 "messages": [{ "id": "m-1", "chatId": "c-1", "ts": 1760000000000, "text": "Buy milk", "editedAt": 1760000001000,
                                "attachments": [{ "hash": "ab", "name": "list.png", "size": 2048, "mimeType": "image/png" }] }] }"""
        )
        assertEquals("", f.beforeId)
        val m = f.messages.single()
        assertEquals(1_760_000_001_000L, m.editedAt)
        assertEquals(listOf(NoteAttachment("ab", "list.png", 2048, "image/png")), m.attachments)
        assertEquals(
            Inbound.NotesMessages("p", "c-1", "m-9", emptyList(), "Couldn't load notes."),
            Inbound.parse(
                """{ "t": "notesMessages", "project": "p", "chatId": "c-1", "beforeId": "m-9" }"""
            )
        )
    }

    @Test
    fun noteWrites() {
        assertEquals(
            Inbound.NotesAddMessage(
                "p",
                "c-1",
                NoteMessage("m-2", "c-1", 9, "hi", null, null),
                null
            ),
            Inbound.parse(
                """{ "t": "notesAddMessage", "project": "p", "chatId": "c-1", "ok": true,
                     "message": { "id": "m-2", "chatId": "c-1", "ts": 9, "text": "hi" } }"""
            )
        )
        assertEquals(
            Inbound.NotesAddMessage("p", "c-1", null, "Couldn't add the note."),
            Inbound.parse("""{ "t": "notesAddMessage", "project": "p", "chatId": "c-1" }""")
        )
        assertEquals(
            Inbound.NotesEditMessage("p", "m-2", "Couldn't edit the note."),
            Inbound.parse(
                """{ "t": "notesEditMessage", "project": "p", "id": "m-2", "ok": false }"""
            )
        )
        assertEquals(
            Inbound.NotesDeleteMessage("p", "m-2", null),
            Inbound.parse(
                """{ "t": "notesDeleteMessage", "project": "p", "id": "m-2", "ok": true }"""
            )
        )
        assertEquals(
            "Couldn't delete the note.",
            decode<Inbound.NotesDeleteMessage>("""{ "t": "notesDeleteMessage" }""").error
        )
    }

    @Test
    fun notesSearchAndAttachment() {
        assertEquals(
            Inbound.NotesSearch(
                "p",
                "milk",
                listOf(NoteSearchHit("m-1", "c-1", "Ideas", 5, "Buy milk")),
                null
            ),
            Inbound.parse(
                """{ "t": "notesSearch", "project": "p", "query": "milk", "ok": true,
                     "hits": [{ "id": "m-1", "chatId": "c-1", "chatTitle": "Ideas", "ts": 5, "snippet": "Buy milk" }] }"""
            )
        )
        assertEquals(
            Inbound.NotesSearch("p", "milk", emptyList(), "Couldn't search notes."),
            Inbound.parse(
                """{ "t": "notesSearch", "project": "p", "query": "milk", "hits": [{}] }"""
            )
        )
        assertEquals(
            Inbound.NotesAttachment("p", "ab", "aGk=", null),
            Inbound.parse(
                """{ "t": "notesAttachment", "project": "p", "hash": "ab", "ok": true, "data": "aGk=" }"""
            )
        )
        val failed =
            decode<Inbound.NotesAttachment>(
                """{ "t": "notesAttachment", "project": "p", "hash": "ab", "data": "aGk=" }"""
            )
        assertNull(failed.data)
        assertEquals("Couldn't open the attachment.", failed.error)
    }
}
