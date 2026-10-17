package cx.lpm.mobile.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionInputsTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun `a mapping reads in prompt order, by position then by name`() {
        val drafts = ActionInputsYaml.drafts(
            json(
                """{"zeta":{},"tag":{"position":2,"type":"radio","label":"tag","default":"v1",
                   "options":["latest",{"label":"Stable","value":"v1"},{"label":"x","value":"x"}]},
                   "env":{"position":1,"type":"password","required":true},"alpha":{"persist":true}}"""
            )
        )
        assertEquals(listOf("env", "tag", "alpha", "zeta"), drafts.map { it.key })
        val tag = drafts[1]
        assertEquals(ActionInputKind.Radio, tag.kind)
        assertEquals("", tag.label)
        assertEquals("v1", tag.defaultValue)
        assertEquals(
            listOf("latest" to "", "v1" to "Stable", "x" to ""),
            tag.options.map {
                it.value to
                    it.label
            }
        )
        assertEquals(ActionInputKind.Password, drafts[0].kind)
        assertEquals(true, drafts[2].persist)
    }

    @Test
    fun `the list earlier phones wrote still reads, its select as a choice`() {
        val drafts = ActionInputsYaml.drafts(
            json("""[{"key":"b","type":"select","options":[{"value":"1"}]},{"key":"a"}]""")
        )
        assertEquals(listOf("b", "a"), drafts.map { it.key })
        assertEquals(ActionInputKind.Radio, drafts[0].kind)
        assertEquals(emptyList<ActionInputDraft>(), ActionInputsYaml.drafts(json("\"x\"")))
    }

    @Test
    fun `the mapping writes what the desktop wizard writes`() {
        val map = ActionInputsYaml.map(
            listOf(
                ActionInputDraft(
                    key = " tag ",
                    label = "Tag",
                    kind = ActionInputKind.Radio,
                    placeholder = "ignored",
                    defaultValue = "v1",
                    persist = true,
                    options = listOf(
                        ActionOptionDraft("latest"),
                        ActionOptionDraft(" v1 ", "Stable"),
                        ActionOptionDraft("same", "same"),
                        ActionOptionDraft(" ")
                    )
                ),
                ActionInputDraft(
                    key = "env",
                    label = "env",
                    kind = ActionInputKind.Password,
                    required = true,
                    persist = true,
                    placeholder = " prod "
                ),
                ActionInputDraft(key = " ")
            )
        )
        assertEquals(
            json(
                """{"tag":{"label":"Tag","type":"radio","default":"v1","persist":true,
                   "options":["latest",{"label":"Stable","value":"v1"},"same"],"position":1},
                   "env":{"type":"password","required":true,"placeholder":"prod","position":2}}"""
            ),
            map
        )
    }

    @Test
    fun `alphabetical inputs need no positions, and none named write nothing`() {
        val map = ActionInputsYaml.map(
            listOf(ActionInputDraft(key = "a"), ActionInputDraft(key = "b"))
        )
        assertEquals(json("""{"a":{},"b":{}}"""), map)
        assertNull(ActionInputsYaml.map(listOf(ActionInputDraft())))
    }

    @Test
    fun `inputs that can't be saved say why`() {
        fun problem(vararg drafts: ActionInputDraft) = ActionInputsYaml.problem(drafts.toList())
        assertEquals(ActionInputsProblem.BadName, problem(ActionInputDraft(key = "a b")))
        assertEquals(ActionInputsProblem.BadName, problem(ActionInputDraft(key = "café")))
        assertEquals(
            ActionInputsProblem.Duplicate("env"),
            problem(ActionInputDraft(key = "env"), ActionInputDraft(key = " env "))
        )
        assertEquals(
            ActionInputsProblem.NoChoices("tag"),
            problem(ActionInputDraft(key = "tag", kind = ActionInputKind.Radio))
        )
        assertEquals(
            ActionInputsProblem.StaleStart("tag"),
            problem(
                ActionInputDraft(
                    key = "tag",
                    kind = ActionInputKind.Radio,
                    defaultValue = "old",
                    options = listOf(ActionOptionDraft("new"))
                )
            )
        )
        assertNull(problem(ActionInputDraft(key = "a.b-c_1"), ActionInputDraft(key = "")))
    }
}
