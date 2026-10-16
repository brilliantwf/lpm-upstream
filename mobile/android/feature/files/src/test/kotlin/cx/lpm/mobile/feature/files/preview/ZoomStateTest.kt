package cx.lpm.mobile.feature.files.preview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStateTest {
    private val frame = IntSize(400, 800)

    /** Where a point of the unzoomed content shows now. */
    private fun ZoomState.shown(p: Offset): Offset {
        val center = Offset(frame.width / 2f, frame.height / 2f)
        return center + (p - center) * scale + offset
    }

    @Test
    fun `a pinch keeps the spot between the fingers in place`() {
        val zoom = ZoomState()
        val spot = Offset(300f, 200f)
        zoom.transform(zoom = 2f, pan = Offset.Zero, centroid = spot, size = frame)
        assertEquals(2f, zoom.scale)
        assertEquals(spot, zoom.shown(spot))
    }

    @Test
    fun `zoom stays between whole and five times, the content inside its frame`() {
        val zoom = ZoomState()
        zoom.transform(zoom = 0.5f, pan = Offset(100f, 100f), centroid = Offset.Zero, size = frame)
        assertEquals(1f, zoom.scale)
        assertEquals(Offset.Zero, zoom.offset)
        zoom.transform(
            zoom = 20f,
            pan = Offset(10_000f, -10_000f),
            centroid = Offset.Zero,
            size = frame
        )
        assertEquals(5f, zoom.scale)
        assertEquals(Offset(800f, -1600f), zoom.offset)
    }

    @Test
    fun `a double tap zooms in on the spot, and the next one back out`() {
        val zoom = ZoomState()
        val spot = Offset(100f, 600f)
        zoom.toggle(spot, frame)
        assertTrue(zoom.zoomed)
        assertEquals(spot, zoom.shown(spot))
        zoom.toggle(spot, frame)
        assertFalse(zoom.zoomed)
        assertEquals(Offset.Zero, zoom.offset)
    }
}
