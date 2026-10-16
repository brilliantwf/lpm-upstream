package cx.lpm.mobile.feature.notes.data

import cx.lpm.mobile.feature.notes.demoSession
import cx.lpm.mobile.feature.notes.notes
import cx.lpm.mobile.feature.notes.settle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store against Demo Mode's notebook, which answers exactly as a Mac does. */
class NotesStoreDemoTest {
    @Test
    fun `the notebook lists its chats newest activity first`() = runTest {
        val t = demoSession()
        t.notes.screenDidOpen("storefront")
        t.settle()
        val s = t.notes.state.value
        assertTrue("storefront" in s.chatsLoaded)
        assertEquals(
            listOf("Checkout redesign", "Ideas", "General"),
            s.chatList("storefront").map { it.title }
        )
    }

    @Test
    fun `a long thread pages backwards until a short page ends it`() = runTest {
        val t = demoSession()
        t.notes.screenDidOpen("storefront")
        t.settle()
        val chat = t.notes.state.value.chatList("storefront").first().id
        t.notes.chatDidOpen("storefront", chat)
        t.settle()
        var s = t.notes.state.value
        assertEquals(NotesStore.PAGE_SIZE, s.orderedMessages("storefront", chat).size)
        assertTrue(s.canLoadMore("storefront", chat))
        assertEquals(
            "Post in the design review channel once the pull request is open.",
            s.orderedMessages("storefront", chat).last().text
        )

        t.notes.loadMoreMessages("storefront", chat)
        t.settle()
        s = t.notes.state.value
        val all = s.orderedMessages("storefront", chat)
        assertEquals(54, all.size)
        assertFalse(s.canLoadMore("storefront", chat))
        assertTrue(all.first().text.startsWith("Kickoff"))
        assertEquals(all.map { it.ts }.sorted(), all.map { it.ts })
    }

    @Test
    fun `a note posts with its attachment, lands first and lifts its chat to the top`() = runTest {
        val t = demoSession()
        t.notes.screenDidOpen("storefront")
        t.settle()
        val ideas = t.notes.state.value.chatList("storefront").first { it.title == "Ideas" }.id
        t.notes.chatDidOpen("storefront", ideas)
        t.settle()
        val bytes = "hello".toByteArray()
        t.notes.addNote(
            "storefront",
            ideas,
            "Gift notes",
            listOf(NoteAttachmentDraft("a.txt", "text/plain", bytes))
        )
        assertTrue(notesKey("storefront", ideas) in t.notes.state.value.sending)
        t.settle()
        val s = t.notes.state.value
        assertTrue(s.sending.isEmpty())
        val posted = s.orderedMessages("storefront", ideas).last()
        assertEquals("Gift notes", posted.text)
        val attachment = posted.attachments!!.single()
        assertEquals(5, attachment.size)
        assertEquals("Ideas", s.chatList("storefront").first().title)

        t.notes.attachments.fetch("storefront", attachment.hash, attachment.size)
        t.settle()
        assertTrue(attachment.hash in t.notes.attachments.state.value.cached)
        assertEquals("hello", t.notes.attachments.data(attachment.hash)?.decodeToString())
    }

    @Test
    fun `edits apply in place and deletes drop the note and its search hit`() = runTest {
        val t = demoSession()
        t.notes.screenDidOpen("storefront")
        t.settle()
        val general = t.notes.state.value.chatList("storefront").first { it.title == "General" }.id
        t.notes.chatDidOpen("storefront", general)
        t.settle()
        val note = t.notes.state.value.orderedMessages("storefront", general).first()
        t.notes.editNote("storefront", note.id, "Deploys go out on Fridays now")
        t.settle()
        val edited = t.notes.state.value.orderedMessages("storefront", general).first()
        assertEquals("Deploys go out on Fridays now", edited.text)
        assertNotNull(edited.editedAt)

        t.notes.search.search("storefront", "Fridays")
        t.settle()
        assertEquals(listOf(note.id), t.notes.search.state.value.hits["storefront"]?.map { it.id })

        t.notes.deleteNote("storefront", note.id)
        t.settle()
        assertTrue(
            t.notes.state.value.orderedMessages("storefront", general).none {
                it.id ==
                    note.id
            }
        )
        assertEquals(
            emptyList<String>(),
            t.notes.search.state.value.hits["storefront"]?.map {
                it.id
            }
        )
    }

    @Test
    fun `a created chat is announced to open, renamed, then deleted`() = runTest {
        val t = demoSession()
        t.notes.screenDidOpen("storefront")
        t.settle()
        t.notes.createChat("storefront", "  Launch plan ")
        assertTrue("storefront" in t.notes.state.value.chatMutating)
        t.settle()
        var s = t.notes.state.value
        val created = s.chatList("storefront").first()
        assertEquals("Launch plan", created.title)
        assertEquals(created.id, s.createdChatId["storefront"])
        t.notes.consumeCreated("storefront")

        t.notes.renameChat("storefront", created.id, "Launch")
        t.settle()
        s = t.notes.state.value
        assertEquals("Launch", s.chatList("storefront").first { it.id == created.id }.title)

        t.notes.deleteChat("storefront", created.id)
        t.settle()
        s = t.notes.state.value
        assertTrue(s.chatList("storefront").none { it.id == created.id })
        assertNull(s.chatError["storefront"])
        assertTrue(s.chatMutating.isEmpty())
    }

    @Test
    fun `an empty title creates nothing and a missing chat reports why`() = runTest {
        val t = demoSession()
        t.notes.createChat("storefront", "   ")
        assertTrue(t.notes.state.value.chatMutating.isEmpty())
        t.notes.renameChat("storefront", "gone", "New")
        t.settle()
        assertEquals(
            "That chat is no longer on your Mac.",
            t.notes.state.value.chatError["storefront"]
        )
        t.notes.dismissChatError("storefront")
        assertNull(t.notes.state.value.chatError["storefront"])
    }
}
