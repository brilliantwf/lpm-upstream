package cx.lpm.mobile.feature.actions.layout

import cx.lpm.mobile.feature.actions.action
import cx.lpm.mobile.feature.actions.zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionRowsTest {
    private val zones = listOf(
        zone("""{"name":"checks","label":"Checks","rows":1,"display":"header","position":1}"""),
        zone(
            """{"name":"agents","label":"Agents","rows":2,"display":"header","position":2,""" +
                """"layers":[{"name":"claude","label":"Claude"},{"name":"codex"}]}"""
        ),
        zone("""{"name":"tools","label":"Tools","display":"footer"}"""),
        zone("""{"name":"empty","label":"Empty","display":"header"}""")
    )

    private fun ActionRowItem.name(): String = when (this) {
        is ActionRowItem.Button -> action.name
        is ActionRowItem.Frame -> "@" + zone.name
    }

    @Test
    fun `buttons go to their bar or zone, and a zone without one is left out`() {
        val rows = ActionRows.of(
            listOf(
                action("""{"name":"test","display":"checks"}"""),
                action("""{"name":"fix","display":"agents","layer":"claude"}"""),
                action("""{"name":"review","display":"agents","layer":"nope"}"""),
                action("""{"name":"codex","display":"agents","layer":"codex"}"""),
                action("""{"name":"deploy","display":"footer"}"""),
                action("""{"name":"clean","display":"menu"}"""),
                action("""{"name":"lost","display":"nowhere"}"""),
                action("""{"name":"plain"}""")
            ),
            zones
        )
        assertEquals(listOf("@checks", "@agents", "lost", "plain"), rows.header.map { it.name() })
        assertEquals(listOf("deploy"), rows.footer.map { it.name() })
        assertEquals(listOf("clean"), rows.menu.map { it.name })
        val agents = rows.header[1] as ActionRowItem.Frame
        assertEquals(listOf("Claude", "Layer 2"), agents.lists.map { it.title })
        // A layer naming none of the zone's lands in its first.
        assertEquals(listOf("fix", "review"), agents.lists[0].actions.map { it.name })
        assertEquals(listOf("codex"), agents.lists[1].actions.map { it.name })
    }

    @Test
    fun `positioned entries lead, then names compare bytewise`() {
        val rows = ActionRows.of(
            listOf(
                action("""{"name":"b"}"""),
                action("""{"name":"Z"}"""),
                action("""{"name":"a","position":5}"""),
                action("""{"name":"c","position":0.5}"""),
                action("""{"name":"é"}""")
            ),
            emptyList()
        )
        assertEquals(listOf("c", "a", "Z", "b", "é"), rows.header.map { it.name() })
    }
}
