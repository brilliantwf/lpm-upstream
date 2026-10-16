package cx.lpm.mobile.feature.actions.editor

import cx.lpm.mobile.core.model.Zone

/**
 * Where an action appears in the project's controls: the top or the bottom bar, or
 * a zone in either, at one of its layers when it has them.
 */
sealed interface ActionPlacement {
    data object Header : ActionPlacement

    data object Footer : ActionPlacement

    data class InZone(val zone: String, override val layer: String?) : ActionPlacement

    /** The zone's layer it sits in; null in a bar or a zone without layers. */
    val layer: String? get() = null

    companion object {
        private val Reach = listOf("project", "repo", "global")

        /**
         * From the action's `display` and `layer` as written. A layer naming none of the
         * zone's is its first, where the desktop shows it; a zone the project doesn't
         * have stays as written, so saving doesn't move it.
         */
        fun of(display: String, layer: String?, zones: List<Zone>): ActionPlacement =
            when (display) {
                "", "button", "header" -> Header

                "footer" -> Footer

                else -> {
                    val zone = zones.firstOrNull { it.name == display }
                    if (zone != null) {
                        InZone(display, zone.layer(layer.orEmpty()))
                    } else {
                        InZone(display, layer?.ifEmpty { null })
                    }
                }
            }

        /**
         * The bars, then every zone and layer (zonePlacements in zoneLayers.ts) that
         * shows wherever the action's file applies: a project file's action can go in
         * any zone, a repo or global one only in a zone from a file as wide, so the
         * other projects it shows in still have the zone.
         */
        fun options(
            zones: List<Zone>,
            source: String,
            current: ActionPlacement
        ): List<ActionPlacement> {
            val floor = reach(source)
            val placements = zones.filter { reach(it.source) >= floor }.flatMap { zone ->
                if (zone.layers.isEmpty()) {
                    listOf(InZone(zone.name, null))
                } else {
                    zone.layers.map { InZone(zone.name, it.name) }
                }
            }
            val options = listOf(Header, Footer) + placements
            return if (current in options) options else options + current
        }

        private fun reach(source: String): Int = Reach.indexOf(source).coerceAtLeast(0)
    }
}
