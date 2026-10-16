package cx.lpm.mobile.feature.actions.button

import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.feature.actions.action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionTintTest {
    @Test
    fun `named, deep and hex colors read as the desktop paints them`() {
        val claude = ActionTint.of("claude")!!
        assertEquals(Color(0xFFD97757), claude.accent(isDark = false))
        assertEquals(Color(0xFFDE8A68), claude.accent(isDark = true))
        assertEquals(Color(0xFFAE5630), claude.text(isDark = false))
        assertEquals(0.10f, claude.glass(isDark = false).alpha, 0.01f)
        assertEquals(0.22f, claude.glass(isDark = true).alpha, 0.01f)

        val deep = ActionTint.of("claude-deep")!!
        assertEquals(0xD9 / 255f * 0.74f, deep.accent(isDark = false).red, 0.01f)
        // Dark text stays the plain accent.
        assertEquals(Color(0xFFDE8A68), deep.text(isDark = true))

        assertEquals(Color(0xFF112233), ActionTint.of("#112233")!!.text(isDark = true))
        assertNull(ActionTint.of(""))
        assertNull(ActionTint.of("#12345"))
    }

    @Test
    fun `a split group runs its primary child, the remembered one, or the first`() {
        val group = action(
            """{"name":"deploy","primary":"last-used","children":[""" +
                """{"name":"deploy:menu","children":[{"name":"deploy:menu:x"}]},""" +
                """{"name":"deploy:staging"},{"name":"deploy:prod"}]}"""
        )
        assertEquals("deploy:prod", group.primaryChild("prod")?.name)
        assertEquals("deploy:staging", group.primaryChild(null)?.name)
        assertNull(action("""{"name":"g","children":[{"name":"g:a"}]}""").primaryChild(null))
        assertEquals("prod", group.children[2].childKey)
        val ship = action("""{"name":"s","label":"Ship","emoji":"🚀"}""")
        assertEquals("🚀 Ship", ship.displayLabel)
    }
}
