package cx.lpm.mobile.feature.projects.duplicate

import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.DuplicateOptions
import cx.lpm.mobile.core.model.RunMode
import cx.lpm.mobile.feature.projects.project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateDraftTest {
    private val deploy = Action.parse(
        Json.parseToJsonElement(
            """{"name":"deploy","label":"Deploy","type":"terminal"}"""
        ).jsonObject
    )

    @Test
    fun `a fresh sheet keeps only the desktop's git toggles`() {
        val defaults =
            DuplicateOptions(
                count = 3,
                excludeUncommitted = true,
                pullLatest = false,
                reinstallDeps = true
            )
        val seed = duplicateSeed(project("web"), defaults)
        assertEquals(
            DuplicateOptions(excludeUncommitted = true, pullLatest = false, reinstallDeps = true),
            seed
        )
    }

    @Test
    fun `a prompt from the composer picks a run mode that carries it`() {
        val withAction =
            duplicateSeed(
                project("web", actions = listOf(deploy)),
                DuplicateOptions(),
                "fix it",
                99
            )
        assertEquals(RunMode.Action, withAction.runMode)
        assertEquals("deploy", withAction.actionName)
        assertEquals(MAX_COPIES, withAction.count)
        assertEquals(MAX_COPIES, withAction.labels.size)
        val bare = duplicateSeed(project("web"), DuplicateOptions(), "fix it", 2)
        assertEquals(RunMode.Command, bare.runMode)
        assertEquals("fix it", bare.prompt)
    }

    @Test
    fun `labels follow the count and keep what was typed`() {
        val three = DuplicateOptions(labels = listOf("a")).withCount(3)
        assertEquals(listOf("a", "", ""), three.labels)
        assertEquals(listOf("a"), three.withCount(1).labels)
    }

    @Test
    fun `run modes offer Action only with something to run`() {
        assertEquals(listOf(RunMode.None, RunMode.Command), project("web").duplicateRunModes())
        val web = project("web", actions = listOf(deploy))
        assertEquals(RunMode.entries, web.duplicateRunModes())
        assertEquals("deploy", DuplicateOptions().withRunMode(RunMode.Action, web).actionName)
    }
}
