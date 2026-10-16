package cx.lpm.mobile.feature.automations

import cx.lpm.mobile.core.data.automations.AutomationsStore
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.AutomationHistoryEntry
import cx.lpm.mobile.core.model.AutomationJob
import cx.lpm.mobile.core.testing.TestSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A session in Demo Mode with the sample Mac's first answers in. */
fun TestScope.demoSession(): TestSession =
    TestSession(this, setOf(SessionStore.Factory(::AutomationsStore))).apply {
        enterDemo()
        settle()
    }

/** Let the demo Mac answer everything sent so far. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = 1_000) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

/** A job as the Mac's `jobs` frame describes it; [json] adds or overrides fields. */
fun job(json: String = ""): AutomationJob = AutomationJob.parse(
    merged(
        """{"id":"nightly","project":"storefront","valid":true,"enabled":true,""" +
            """"label":"Nightly tests","schedule":{"mode":"calendar","atMinutes":180}}""",
        json
    )
)

/** A run as `jobHistory` describes it, "completed" unless [json] says otherwise. */
fun entry(at: Long, json: String = ""): AutomationHistoryEntry =
    AutomationHistoryEntry.parse(merged("""{"at":$at,"result":"completed"}""", json))

private fun merged(base: String, extra: String): JsonObject {
    val fields = (Json.parseToJsonElement(base) as JsonObject).toMutableMap()
    if (extra.isNotEmpty()) fields += Json.parseToJsonElement("{$extra}") as JsonObject
    return JsonObject(fields)
}
