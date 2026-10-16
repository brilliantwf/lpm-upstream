package cx.lpm.mobile.feature.actions.editor

import cx.lpm.mobile.core.model.Zone
import cx.lpm.mobile.core.model.bool
import cx.lpm.mobile.core.model.obj
import cx.lpm.mobile.core.model.objects
import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.core.model.trimmingWhitespace
import java.util.UUID
import kotlinx.serialization.json.JsonObject

/** A single command button, or a menu of sub-actions. */
enum class ActionKind { Command, Menu }

/** Where an action runs: the YAML `type`, which a plain pop-up run omits. */
enum class ActionRunIn(val typeValue: String?) {
    Popup(null),
    Terminal("terminal"),
    Command("command"),
    Background("background");

    companion object {
        fun from(type: String?): ActionRunIn = entries.firstOrNull { it.typeValue == type } ?: Popup
    }
}

data class OptionDraft(
    val value: String = "",
    val label: String = "",
    val id: String = UUID.randomUUID().toString()
)

data class InputDraft(
    val key: String = "",
    val label: String = "",
    /** "" (text) or "select". */
    val type: String = "",
    val required: Boolean = false,
    val placeholder: String = "",
    val defaultValue: String = "",
    val options: List<OptionDraft> = emptyList(),
    val id: String = UUID.randomUUID().toString()
) {
    val isSelect: Boolean get() = type == SELECT

    companion object {
        const val SELECT = "select"
    }
}

data class ChildDraft(
    /**
     * The item's YAML key on disk, kept so `primary` and saved choices still point at
     * it after its label changes. Empty for a new item.
     */
    val key: String = "",
    val label: String = "",
    val cmd: String = "",
    val runIn: ActionRunIn = ActionRunIn.Terminal,
    val id: String = UUID.randomUUID().toString()
) {
    val hasCommand: Boolean get() = cmd.trimmingWhitespace().isNotEmpty()
}

/**
 * What tapping a menu action's button does: open the menu, or run one item (the last
 * one used, or a chosen one) with the rest behind a chevron.
 */
sealed interface PrimaryChoice {
    data object Menu : PrimaryChoice

    data object LastUsed : PrimaryChoice

    data class Child(val id: String) : PrimaryChoice
}

/** Why an action can't be saved yet; the form only greys Save out. */
enum class ActionDraftError { NoName, Colon, NameTaken, NoCommand, NoMenuItem }

/** The action form's draft (iOS `ActionDraft`). */
data class ActionDraft(
    val name: String = "",
    val label: String = "",
    val emoji: String = "",
    val kind: ActionKind = ActionKind.Command,
    val cmd: String = "",
    val cwd: String = "",
    val runIn: ActionRunIn = ActionRunIn.Terminal,
    val reuse: Boolean = false,
    val confirm: Boolean = false,
    val placement: ActionPlacement = ActionPlacement.Header,
    /**
     * Where the action sat on disk. An edit that keeps it there leaves `display` and
     * `layer` as written; a move writes both, the top bar as an explicit `header`,
     * since it overrides a footer or zone display from another file.
     */
    val seededPlacement: ActionPlacement = ActionPlacement.Header,
    val inputs: List<InputDraft> = emptyList(),
    val children: List<ChildDraft> = emptyList(),
    val color: String = "",
    val primary: PrimaryChoice = PrimaryChoice.Menu
) {
    fun validationError(otherKeys: List<String>): ActionDraftError? {
        val key = name.trimmingWhitespace()
        return when {
            key.isEmpty() -> ActionDraftError.NoName

            ':' in key -> ActionDraftError.Colon

            key in otherKeys -> ActionDraftError.NameTaken

            kind == ActionKind.Command && cmd.trimmingWhitespace().isEmpty() ->
                ActionDraftError.NoCommand

            kind == ActionKind.Menu && children.none {
                it.hasCommand
            } -> ActionDraftError.NoMenuItem

            else -> null
        }
    }

    companion object {
        /** A draft seeded from the Mac's mapping for the action called [key]. */
        fun from(body: JsonObject, key: String, zones: List<Zone>): ActionDraft {
            val placement = ActionPlacement.of(
                body.string("display").orEmpty(),
                body.string("layer"),
                zones
            )
            val children = body.obj("actions")?.takeIf { it.isNotEmpty() }?.let(::children)
            val draft = ActionDraft(
                name = key,
                label = body.string("label").orEmpty(),
                emoji = body.string("emoji").orEmpty(),
                kind = if (children != null) ActionKind.Menu else ActionKind.Command,
                cmd = body.string("cmd").orEmpty(),
                cwd = body.string("cwd").orEmpty(),
                runIn = ActionRunIn.from(body.string("type")),
                reuse = body.bool("reuse") == true,
                confirm = body.bool("confirm") == true,
                placement = placement,
                seededPlacement = placement,
                inputs = body.objects("inputs")?.map(::input).orEmpty(),
                children = children.orEmpty(),
                color = body.string("color").orEmpty()
            )
            val primary = body.string("primary").orEmpty()
            val child = draft.children.firstOrNull { it.key == primary }
            return draft.copy(
                primary = when {
                    primary == LAST_USED -> PrimaryChoice.LastUsed
                    child != null -> PrimaryChoice.Child(child.id)
                    else -> PrimaryChoice.Menu
                }
            )
        }

        const val LAST_USED = "last-used"

        private fun input(o: JsonObject) = InputDraft(
            key = o.string("key").orEmpty(),
            label = o.string("label").orEmpty(),
            type = if (o.string("type") == InputDraft.SELECT) InputDraft.SELECT else "",
            required = o.bool("required") == true,
            placeholder = o.string("placeholder").orEmpty(),
            defaultValue = o.string("default").orEmpty(),
            options = o.objects("options").orEmpty().map {
                OptionDraft(
                    value = it.string("value").orEmpty(),
                    label = it.string("label").orEmpty()
                )
            }
        )

        private fun children(map: JsonObject): List<ChildDraft> = map.mapNotNull { (name, value) ->
            val v = value as? JsonObject ?: return@mapNotNull null
            ChildDraft(
                key = name,
                label = v.string("label") ?: name,
                cmd = v.string("cmd").orEmpty(),
                runIn = ActionRunIn.from(v.string("type"))
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
