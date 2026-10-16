package cx.lpm.mobile.feature.actions.layout

import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.Zone
import cx.lpm.mobile.core.model.ZoneLayer

/** One of a zone's button lists: a layer, or the whole zone when it has none. */
data class ZoneList(
    val key: String,
    val layer: ZoneLayer?,
    val title: String,
    val actions: List<Action>
)

/** One spot in the header or footer row: a button, or a zone with its lists. */
sealed interface ActionRowItem {
    val id: String

    data class Button(val action: Action) : ActionRowItem {
        override val id: String get() = action.name
    }

    data class Frame(val zone: Zone, val lists: List<ZoneList>) : ActionRowItem {
        override val id: String get() = "@zone/${zone.name}"
    }
}

/**
 * The project's actions laid out as the desktop's header and footer rows show them
 * (buildActionsModel in actionsLayoutModel.ts): a button whose display names no
 * known zone sits in the header, and one naming no layer of its zone in the first
 * layer. A zone without a button is left out, since the phone can't drop one into it.
 */
data class ActionRows(
    val header: List<ActionRowItem> = emptyList(),
    val footer: List<ActionRowItem> = emptyList(),
    val menu: List<Action> = emptyList()
) {
    companion object {
        fun of(actions: List<Action>, zones: List<Zone>): ActionRows {
            // The first zone of a name wins.
            val byName = zones.asReversed().associateBy { it.name }
            val placed = mutableMapOf<String, MutableList<Action>>()
            val header = mutableListOf<Action>()
            val footer = mutableListOf<Action>()
            val menu = mutableListOf<Action>()
            for (action in actions) {
                when (action.display) {
                    "", "header", "button" -> header += action

                    "footer" -> footer += action

                    "menu" -> menu += action

                    else -> {
                        val zone = byName[action.display]
                        if (zone == null) {
                            header += action
                        } else {
                            val key = listKey(zone.name, zone.layer(action.layer))
                            placed.getOrPut(key) { mutableListOf() } += action
                        }
                    }
                }
            }
            return ActionRows(
                header = row(header, zones.filterNot { it.isFooter }, placed),
                footer = row(footer, zones.filter { it.isFooter }, placed),
                menu = menu
            )
        }

        private fun row(
            actions: List<Action>,
            zones: List<Zone>,
            placed: Map<String, List<Action>>
        ): List<ActionRowItem> {
            val buttons = actions.map { Entry(it.name, it.position, ActionRowItem.Button(it)) }
            val frames = zones.mapNotNull { zone ->
                val lists = zone.lists(placed)
                if (lists.all { it.actions.isEmpty() }) return@mapNotNull null
                Entry(zone.name, zone.position, ActionRowItem.Frame(zone, lists))
            }
            return (buttons + frames).sortedWith(Entry.Order).map { it.item }
        }

        private fun Zone.lists(placed: Map<String, List<Action>>): List<ZoneList> {
            if (layers.isEmpty()) {
                return listOf(ZoneList(name, null, label, placed[name].orEmpty()))
            }
            return layers.mapIndexed { index, layer ->
                val key = listKey(name, layer.name)
                ZoneList(key, layer, layer.title(index), placed[key].orEmpty())
            }
        }

        private fun listKey(zone: String, layer: String?): String =
            if (layer == null) zone else "$zone/$layer"
    }

    private class Entry(val name: String, val position: Double?, val item: ActionRowItem) {
        companion object {
            /**
             * Positioned entries first, then by name compared bytewise, as the Mac
             * sorts them (comparePosition in actionsLayoutModel.ts).
             */
            val Order = Comparator<Entry> { a, b ->
                val x = a.position
                val y = b.position
                when {
                    x != null && y != null && x != y -> x.compareTo(y)
                    x != null && y == null -> -1
                    x == null && y != null -> 1
                    else -> compareBytes(a.name, b.name)
                }
            }

            private fun compareBytes(a: String, b: String): Int {
                val x = a.encodeToByteArray()
                val y = b.encodeToByteArray()
                for (i in 0 until minOf(x.size, y.size)) {
                    val diff = x[i].toUByte().compareTo(y[i].toUByte())
                    if (diff != 0) return diff
                }
                return x.size.compareTo(y.size)
            }
        }
    }
}
