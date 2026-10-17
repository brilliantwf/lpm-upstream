package cx.lpm.mobile.core.tailnet

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject

/** When the app comes on screen and leaves it (iOS's scene phase). */
interface AppVisibility {
    /** Call on the main thread; reports the current state at once. */
    fun observe(onForeground: () -> Unit, onBackground: () -> Unit)
}

class ProcessAppVisibility @Inject constructor() : AppVisibility {
    override fun observe(onForeground: () -> Unit, onBackground: () -> Unit) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = onForeground()

                override fun onStop(owner: LifecycleOwner) = onBackground()
            }
        )
    }
}
