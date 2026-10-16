package cx.lpm.mobile.core.demo

import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

// Automation schedules (next fire times) and the editor's YAML-shaped job body.

internal fun DemoServer.refreshNextFires() {
    for (j in world.jobs) j.nextFireAt = if (j.enabled) nextFire(j) else null
}

internal fun DemoServer.nextFire(j: DemoWorld.Job): Long? {
    val now = clock.instant()
    when (j.scheduleMode) {
        "manual" -> return null

        "interval" -> {
            if (j.everySecs <= 0) return null
            val gap = if (j.everyMaxSecs > j.everySecs) {
                j.everySecs +
                    (draw(j.id, 0u) % (j.everyMaxSecs - j.everySecs + 1).toULong()).toInt()
            } else {
                j.everySecs
            }
            return now.epochSecond + gap
        }

        else -> {
            val names = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
            val allowed = j.days.ifEmpty { names }.toSet()
            val nowZoned = now.atZone(clock.zone)
            val nowEpoch = now.toEpochMilli() / 1000.0
            for (offset in 0L..7L) {
                val day = nowZoned.plusDays(offset)
                if (names[day.dayOfWeek.value % 7] !in allowed) continue
                val fire = dayFires(j, day).firstOrNull { it > nowEpoch }
                if (fire != null) return fire.toLong()
            }
            return null
        }
    }
}

/**
 * The instants (unix seconds) a calendar job fires at on [day]: one for a set time, or
 * `times` spread across its window the way the Mac spreads them, so the demo's
 * next-run times look like the real thing.
 */
private fun dayFires(j: DemoWorld.Job, day: ZonedDateTime): List<Double> {
    fun at(minutes: Int): Double =
        day.with(LocalTime.of(minOf(23, minutes / 60), minutes % 60)).toEpochSecond().toDouble()
    val start = at(j.atMinutes)
    val until = j.untilMinutes ?: return listOf(start)
    val end = at(until)
    if (end <= start) return listOf(start)
    val times = maxOf(1, j.times)
    val slice = (end - start) / times
    // Calendar.ordinality(of: .day, in: .era): days since 0001-01-01, counting from 1.
    val dayKey = day.toLocalDate().toEpochDay() + 719_163
    return (0 until times).map { k ->
        val span = maxOf(1, slice.toInt() - (if (k + 1 < times) 300 else 0))
        val offset = (draw(j.id, (dayKey * 100 + k).toULong()) % span.toULong()).toInt()
        start + k * slice + offset
    }
}

/**
 * FNV-1a over the job id and an occasion: the demo's stand-in for the Mac's seeded
 * draw, so a window's times hold still between refreshes.
 */
private fun draw(id: String, occasion: ULong): ULong {
    var h = 0xcbf29ce484222325uL
    val occasionBytes = ByteArray(8) { i -> (occasion shr (8 * i)).toByte() }
    for (b in id.toByteArray() + occasionBytes) {
        h = h xor b.toUByte().toULong()
        h *= 0x100000001b3uL
    }
    return h
}

// body <-> job

internal fun applyBody(body: Dict, j: DemoWorld.Job) {
    j.body = body
    j.label = body.string("label") ?: ""
    j.emoji = body.string("emoji") ?: ""
    j.duplicate = body.bool("duplicate") == true

    val schedule = body.dict("schedule") ?: emptyMap()
    val every = schedule["every"]
    if (schedule.bool("manual") == true) {
        j.scheduleMode = "manual"
    } else if (every != null) {
        j.scheduleMode = "interval"
        val (min, max) = parseEveryBand(every)
        j.everySecs = min
        j.everyMaxSecs = max
    } else {
        j.scheduleMode = "calendar"
        j.atMinutes = parseAtMinutes(schedule.string("at") ?: "")
        j.days = schedule.list("days")?.map { describing(it).lowercase() } ?: emptyList()
        j.untilMinutes = schedule.string("until")?.let(::parseAtMinutes)
        j.times = maxOf(1, schedule.int("times") ?: 1)
        j.pickDays = schedule.int("pickDays") ?: 0
    }

    val run = body.dict("run") ?: emptyMap()
    if (!run.string("action").isNullOrEmpty()) {
        j.runKind = "action"
        j.agent = ""
        j.model = ""
        j.effort = ""
    } else if (!run.string("cmd").isNullOrEmpty()) {
        j.runKind = "cmd"
        j.agent = ""
        j.model = ""
        j.effort = ""
    } else {
        j.runKind = "prompt"
        j.agent = (run.string("agent") ?: "").lowercase()
        j.model = run.string("model") ?: ""
        j.effort = (run.string("effort") ?: "").lowercase()
    }
}

internal fun derivedBody(j: DemoWorld.Job): Dict {
    val body = mutableMapOf<String, Any?>("label" to j.label)
    if (j.emoji.isNotEmpty()) body["emoji"] = j.emoji
    when (j.scheduleMode) {
        "manual" -> body["schedule"] = mapOf("manual" to true)

        "interval" -> {
            val hours = maxOf(1, j.everySecs / 3600)
            val every = if (hours % 24 == 0) "${hours / 24}d" else "${hours}h"
            body["schedule"] = if (j.everyMaxSecs > j.everySecs) {
                val maxHours = maxOf(1, j.everyMaxSecs / 3600)
                if (hours % 24 == 0 && maxHours % 24 == 0) {
                    mapOf("every" to "${hours / 24}-${maxHours / 24}d")
                } else {
                    mapOf("every" to "$hours-${maxHours}h")
                }
            } else {
                mapOf("every" to every)
            }
        }

        else -> {
            fun clock(minutes: Int) = String.format(
                Locale.ROOT,
                "%02d:%02d",
                minutes / 60,
                minutes % 60
            )
            val s = mutableMapOf<String, Any?>("at" to clock(j.atMinutes))
            j.untilMinutes?.let { until ->
                s["until"] = clock(until)
                if (j.times > 1) s["times"] = j.times
            }
            if (j.days.isNotEmpty()) s["days"] = j.days
            if (j.pickDays > 0) s["pickDays"] = j.pickDays
            body["schedule"] = s
        }
    }
    body["run"] = when (j.runKind) {
        "action" -> mapOf("action" to "test")

        "cmd" -> mapOf("cmd" to "npm test")

        else -> {
            val run = mutableMapOf<String, Any?>(
                "prompt" to "Check the project and report anything that needs attention."
            )
            if (j.agent.isNotEmpty()) run["agent"] = j.agent
            if (j.model.isNotEmpty()) run["model"] = j.model
            if (j.effort.isNotEmpty()) run["effort"] = j.effort
            run
        }
    }
    if (j.duplicate) body["duplicate"] = true
    return body
}

/**
 * `every` as a band: "4h-8h" (or "4-8h", where the unit is written once on the far
 * end). A plain value comes back as the band whose ends match.
 */
private fun parseEveryBand(every: Any): Pair<Int, Int> {
    every.asLong()?.let { n ->
        val secs = maxOf(1L, n).toInt() * 3600
        return secs to secs
    }
    every.asDouble()?.let { n ->
        val secs = maxOf(1, n.toInt()) * 3600
        return secs to secs
    }
    val fallback = 6 * 3600 to 6 * 3600
    val s = describing(every).trimmingWhitespaces().lowercase()
    val ends = s.swiftSplit('-', maxSplits = 1).map { it.trimmingWhitespaces() }
    val first = ends.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return fallback
    val unit = if (ends.size == 2) ends[1] else first
    val scale = when {
        unit.endsWith("d") -> 86400
        unit.endsWith("m") -> 60
        else -> 3600
    }
    fun count(part: String): Int? {
        val digits = if (part.lastOrNull()?.let { it in "dhm" } == true) part.dropLast(1) else part
        return digits.trimmingWhitespaces().toIntOrNull()?.let { maxOf(1, it) }
    }
    val lo = count(first) ?: return fallback
    val hi = if (ends.size == 2) count(ends[1]) else null
    if (hi == null) return lo * scale to lo * scale
    return lo * scale to maxOf(lo, hi) * scale
}

private fun parseAtMinutes(time: String): Int {
    val parts = time.swiftSplit(':')
    val h = parts.getOrNull(0)?.toIntOrNull()
    val m = parts.getOrNull(1)?.toIntOrNull()
    if (parts.size != 2 || h == null || m == null || h !in 0..23 || m !in 0..59) return 540
    return h * 60 + m
}
