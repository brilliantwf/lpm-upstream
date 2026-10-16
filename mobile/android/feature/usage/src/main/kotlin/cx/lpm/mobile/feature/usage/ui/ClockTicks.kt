package cx.lpm.mobile.feature.usage.ui

import java.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** How often the reset countdowns and pace ticks move between readings. */
const val CLOCK_TICK_MS = 30_000L

/** The clock's time now and every [CLOCK_TICK_MS] after, unix millis. */
fun clockTicks(clock: Clock): Flow<Long> = flow {
    while (true) {
        emit(clock.millis())
        delay(CLOCK_TICK_MS)
    }
}
