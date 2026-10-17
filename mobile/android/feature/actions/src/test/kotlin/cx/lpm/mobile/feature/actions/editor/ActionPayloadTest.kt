package cx.lpm.mobile.feature.actions.editor

import cx.lpm.mobile.core.model.ActionInputDraft
import cx.lpm.mobile.core.model.ActionInputKind
import cx.lpm.mobile.core.model.ActionOptionDraft
import cx.lpm.mobile.feature.actions.json
import cx.lpm.mobile.feature.actions.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionPayloadTest {
    private val zones = listOf(
        zone("""{"name":"agents","label":"Agents","layers":[{"name":"claude"},{"name":"codex"}]}""")
    )

    @Test
    fun `an untouched command action saves back exactly as the desktop wrote it`() {
        val body = json(
            """{"label":"Deploy","emoji":"🚀","cmd":"npm run deploy","cwd":"./web",""" +
                """"type":"terminal","reuse":true,"confirm":true,"display":"footer",""" +
                """"color":"cyan-deep","shortcut":"cmd+d","position":3,"env":{"A":"1"},""" +
                """"inputs":{"env":{"label":"Environment","type":"radio",""" +
                """"required":true,"default":"staging","options":[""" +
                """{"label":"Staging","value":"staging"},"prod"]},""" +
                """"note":{"placeholder":"Why?"}}}"""
        )
        val draft = ActionDraft.from(body, "deploy", zones)
        assertEquals(ActionKind.Command, draft.kind)
        assertEquals(ActionRunIn.Terminal, draft.runIn)
        assertEquals(ActionPlacement.Footer, draft.placement)
        assertEquals(body, draft.payload(editing = true, seedBody = body))
        // Keys keep their place in the mapping.
        val saved = draft.payload(editing = true, seedBody = body)
        assertEquals(body.keys.toList(), saved.keys.toList())
    }

    @Test
    fun `inputs go out whole as the mapping, and empty once all are removed`() {
        val listed = json(
            """{"cmd":"deploy {{tag}}","inputs":[{"key":"tag","type":"select",""" +
                """"options":[{"value":"v1","label":"One"}]},{"key":"env"}]}"""
        )
        val draft = ActionDraft.from(listed, "deploy", zones)
        assertEquals(
            json(
                """{"tag":{"type":"radio","options":[{"label":"One","value":"v1"}],""" +
                    """"position":1},"env":{"position":2}}"""
            ),
            draft.payload(editing = true, seedBody = listed)["inputs"]
        )
        val cleared = draft.copy(inputs = emptyList())
        assertEquals(json("{}"), cleared.payload(editing = true, seedBody = listed)["inputs"])
        assertNull(cleared.payload(editing = false, seedBody = null)["inputs"])
    }

    @Test
    fun `inputs that can't be saved keep Save off`() {
        val draft = ActionDraft(
            name = "deploy",
            cmd = "x",
            inputs = listOf(ActionInputDraft(key = "tag", kind = ActionInputKind.Radio))
        )
        assertEquals(ActionDraftError.Inputs, draft.validationError(emptyList()))
        val fixed = draft.copy(
            inputs = listOf(draft.inputs[0].copy(options = listOf(ActionOptionDraft("v1"))))
        )
        assertNull(fixed.validationError(emptyList()))
    }

    @Test
    fun `an untouched menu action saves back exactly as the desktop wrote it`() {
        val body = json(
            """{"label":"Ship","actions":{"staging":{"label":"Staging","cmd":"make stage",""" +
                """"type":"terminal"},""" +
                """"prod":{"label":"Prod","cmd":"make prod"}},"primary":"prod",""" +
                """"display":"agents","layer":"codex"}"""
        )
        val draft = ActionDraft.from(body, "ship", zones)
        assertEquals(ActionKind.Menu, draft.kind)
        assertEquals(listOf("Prod", "Staging"), draft.children.map { it.label })
        assertEquals(PrimaryChoice.Child(draft.children[0].id), draft.primary)
        assertEquals(ActionPlacement.InZone("agents", "codex"), draft.placement)
        assertEquals(body, draft.payload(editing = true, seedBody = body))
    }

    @Test
    fun `a new action writes only what the form set`() {
        val draft = ActionDraft(name = "deploy", cmd = "  npm run deploy ")
        assertEquals(
            json("""{"cmd":"npm run deploy","type":"terminal"}"""),
            draft.payload(editing = false, seedBody = null)
        )
        val popup =
            draft.copy(runIn = ActionRunIn.Popup, reuse = true, label = "Deploy", color = "red")
        assertEquals(
            json("""{"label":"Deploy","cmd":"npm run deploy","color":"red"}"""),
            popup.payload(editing = false, seedBody = null)
        )
    }

    @Test
    fun `a move writes display and layer, the top bar explicitly`() {
        val body = json("""{"cmd":"x","display":"footer","layer":"old"}""")
        val draft = ActionDraft.from(body, "x", zones)
        val moved = draft.copy(placement = ActionPlacement.Header)
            .payload(editing = true, seedBody = body)
        assertEquals(json("""{"cmd":"x","display":"header"}"""), moved)
        val zoned = draft.copy(placement = ActionPlacement.InZone("agents", "claude"))
            .payload(editing = true, seedBody = body)
        assertEquals(json("""{"cmd":"x","display":"agents","layer":"claude"}"""), zoned)
    }

    @Test
    fun `turning a command into a menu drops its command fields and keys new items`() {
        val body = json(
            """{"cmd":"x","cwd":"y","type":"command","port":3000,""" +
                """"inputs":[{"key":"a"}],"shortcut":"k"}"""
        )
        val draft = ActionDraft.from(body, "x", zones).copy(
            kind = ActionKind.Menu,
            children = listOf(
                ChildDraft(label = "Deploy Prod!", cmd = "a"),
                ChildDraft(label = "deploy prod", cmd = "b", runIn = ActionRunIn.Background),
                ChildDraft(label = "", cmd = "c", runIn = ActionRunIn.Popup),
                ChildDraft(label = "skipped")
            ),
            primary = PrimaryChoice.LastUsed
        )
        assertEquals(
            json(
                """{"shortcut":"k","actions":{"deploy-prod":{"label":"Deploy Prod!","cmd":"a",""" +
                    """"type":"terminal"},""" +
                    """"deploy-prod-2":{"label":"deploy prod","cmd":"b","type":"background"},""" +
                    """"option-3":{"label":"option-3","cmd":"c"}},"primary":"last-used"}"""
            ),
            draft.payload(editing = true, seedBody = body)
        )
    }

    @Test
    fun `a draft can't save without a free name and something to run`() {
        val others = listOf("lint")
        assertEquals(ActionDraftError.NoName, ActionDraft(cmd = "x").validationError(others))
        assertEquals(
            ActionDraftError.Colon,
            ActionDraft(name = "a:b", cmd = "x").validationError(others)
        )
        assertEquals(
            ActionDraftError.NameTaken,
            ActionDraft(name = " lint ", cmd = "x").validationError(others)
        )
        assertEquals(ActionDraftError.NoCommand, ActionDraft(name = "a").validationError(others))
        assertEquals(
            ActionDraftError.NoMenuItem,
            ActionDraft(
                name = "a",
                kind = ActionKind.Menu,
                children = listOf(ChildDraft(label = "x"))
            ).validationError(others)
        )
        assertNull(ActionDraft(name = "a", cmd = "x").validationError(others))
        assertEquals("a-b-c", slugify(" A  b--C! "))
    }
}
