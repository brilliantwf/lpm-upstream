package cx.lpm.mobile.core.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Keeps [flow] collected for the rest of the test, so a `WhileSubscribed` state stays live. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.keepCollecting(flow: Flow<T>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

/** Moves virtual time on by [millis] and runs everything that's due. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle(millis: Long = 0) {
    testScheduler.advanceTimeBy(millis)
    testScheduler.runCurrent()
}
