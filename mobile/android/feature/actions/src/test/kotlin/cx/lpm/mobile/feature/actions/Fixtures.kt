package cx.lpm.mobile.feature.actions

import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.Zone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

fun action(text: String): Action = Action.parse(json(text))

fun zone(text: String): Zone = Zone.parse(json(text))
