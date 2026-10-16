package cx.lpm.mobile.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkStatusTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun builtInStatusDropsLabelAndEmoji() {
        val s = WorkStatus.parse(
            obj(
                """{"state":"blocked","label":"x","emoji":"y","note":"  waiting on API ","since":5}"""
            )
        )
        assertEquals(WorkStatus(WorkState.Blocked, null, null, "waiting on API", 5), s)
        assertEquals("Blocked", s!!.displayLabel)
        assertEquals("⛔", s.displayEmoji)
        assertEquals("blocked", s.key)
    }

    @Test
    fun customStatusNeedsALabel() {
        assertNull(WorkStatus.parse(obj("""{"state":"custom","label":"   ","emoji":"👀"}""")))
        val s = WorkStatus.parse(
            obj("""{"state":"custom","label":"Review","emoji":" ","note":""}""")
        )!!
        assertNull(s.emoji)
        assertNull(s.note)
        assertEquals(0L, s.since)
        assertEquals("Review", s.displayLabel)
        assertEquals("", s.displayEmoji)
        assertEquals("custom:Review", s.key)
    }

    @Test
    fun unknownStateIsNoStatus() {
        assertNull(WorkStatus.parse(obj("""{"state":"archived"}""")))
        assertNull(WorkStatus.parse(obj("{}")))
    }

    @Test
    fun paletteEntriesNeedATrimmedLabel() {
        assertNull(CustomWorkStatus.parse(obj("""{"label":" ","emoji":"x"}""")))
        assertEquals(
            CustomWorkStatus("Ready", "🚀", withNote = false),
            CustomWorkStatus.parse(obj("""{"label":" Ready ","emoji":"🚀"}"""))
        )
    }

    @Test
    fun inputWireOmitsBlankFields() {
        assertEquals(
            obj("""{"state":"done"}"""),
            WorkStatusInput(WorkState.Done, label = " ", note = "").wire
        )
        assertEquals(
            obj("""{"state":"custom","label":"Review","emoji":"👀","note":"PR #12"}"""),
            WorkStatusInput(
                WorkState.Custom,
                label = "Review ",
                emoji = "👀",
                note = " PR #12"
            ).wire
        )
    }

    @Test
    fun menuFollowsTheDefaultOrderWhenNoneIsSent() {
        val menu = workStatusMenu(defaultWorkStatusPalette, order = emptyList())
        assertEquals(
            listOf(
                "in_progress",
                "custom:Review",
                "custom:Ready",
                "done",
                "blocked",
                "custom:Waiting",
                "custom:Needs decision",
                "custom:Paused"
            ),
            menu.map { it.id }
        )
        assertTrue(menu.first { it.id == "blocked" }.asksNote)
        assertEquals("⏳", menu.first().emoji)
    }

    @Test
    fun unrankedStatusesKeepTheirPlaceAfterRankedOnes() {
        val palette =
            listOf(CustomWorkStatus("Ship", "🚢"), CustomWorkStatus("Hold", "✋", withNote = true))
        val menu = workStatusMenu(palette, order = listOf("custom:Hold", "done", "custom:Hold"))
        assertEquals(
            listOf("custom:Hold", "done", "in_progress", "blocked", "custom:Ship"),
            menu.map {
                it.id
            }
        )
    }

    @Test
    fun existingNoteOnlyForTheSameStatus() {
        val current = WorkStatus(WorkState.Blocked, null, null, "API down", 10)
        assertEquals("API down", existingNote(current, builtInWorkStatuses[1]))
        assertEquals("", existingNote(current, builtInWorkStatuses[0]))
        assertEquals("", existingNote(null, builtInWorkStatuses[1]))
    }

    @Test
    fun nextStatusKeepsTheClockForANoteEdit() {
        val current = WorkStatus(WorkState.Blocked, null, null, "old", 100)
        val next =
            nextWorkStatus(current, WorkStatusInput(WorkState.Blocked, note = " new "), now = 999)
        assertEquals(WorkStatus(WorkState.Blocked, null, null, "new", 100), next)
    }

    @Test
    fun nextStatusRestartsTheClockOnAChangeOrWithoutOne() {
        val current = WorkStatus(WorkState.Blocked, null, null, null, 100)
        assertEquals(
            999L,
            nextWorkStatus(current, WorkStatusInput(WorkState.Done), now = 999)!!.since
        )
        val clockless = current.copy(since = 0)
        assertEquals(
            999L,
            nextWorkStatus(clockless, WorkStatusInput(WorkState.Blocked), now = 999)!!.since
        )
    }

    @Test
    fun nextStatusClearsOrIgnoresAnEmptyCustom() {
        val current = WorkStatus(WorkState.Done, null, null, null, 1)
        assertNull(nextWorkStatus(current, null, now = 2))
        assertEquals(
            current,
            nextWorkStatus(current, WorkStatusInput(WorkState.Custom, label = " "), now = 2)
        )
        val custom =
            nextWorkStatus(
                null,
                WorkStatusInput(WorkState.Custom, label = "Hold", emoji = " "),
                now = 2
            )
        assertEquals(WorkStatus(WorkState.Custom, "Hold", null, null, 2), custom)
    }
}
