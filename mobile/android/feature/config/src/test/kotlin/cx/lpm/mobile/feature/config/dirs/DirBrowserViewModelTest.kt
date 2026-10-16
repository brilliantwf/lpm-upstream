package cx.lpm.mobile.feature.config.dirs

import cx.lpm.mobile.core.testing.MainDispatcherRule
import cx.lpm.mobile.feature.config.demoSession
import cx.lpm.mobile.feature.config.settle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DirBrowserViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `the browser starts at the Mac's home and walks down and up`() = runTest {
        val t = demoSession()
        val vm = DirBrowserViewModel(t.sessions)
        backgroundScope.launch { vm.uiState.collect() }
        t.settle()
        val home = vm.uiState.value.listing
        assertEquals("/Users/demo", home?.path)
        assertEquals("/Users", home?.parent)
        assertEquals(
            listOf("Desktop", "Developer", "Documents", "Downloads", "Projects"),
            home?.dirs
        )

        vm.openChild("Developer")
        t.settle()
        assertEquals(listOf("experiments", "playground"), vm.uiState.value.listing?.dirs)

        vm.open("/Users/demo/Developer/playground")
        t.settle()
        assertEquals(emptyList<String>(), vm.uiState.value.listing?.dirs)

        vm.open("/")
        t.settle()
        assertEquals(null, vm.uiState.value.listing?.parent)
    }
}
