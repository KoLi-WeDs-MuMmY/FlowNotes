package com.flownotes.app.view

import org.junit.Assert.*
import org.junit.Test

class CanvasMathTest {

    @Test
    fun testScreenToDocumentInversion() {
        val scale = 2.5f
        val panX = -200f
        val panY = 150f

        val screenX = 300f
        val screenY = 650f

        // Algebraic inversion formula
        val docX = (screenX - panX) / scale
        val docY = (screenY - panY) / scale

        // docX = (300 - (-200)) / 2.5 = 500 / 2.5 = 200.0f
        // docY = (650 - 150) / 2.5 = 500 / 2.5 = 200.0f
        assertEquals(200.0f, docX, 0.001f)
        assertEquals(200.0f, docY, 0.001f)

        // Forward verification: S = D * scale + pan
        val remappedScreenX = docX * scale + panX
        val remappedScreenY = docY * scale + panY
        assertEquals(screenX, remappedScreenX, 0.001f)
        assertEquals(screenY, remappedScreenY, 0.001f)
    }

    @Test
    fun testPinchFocalPointInvariance() {
        var panX = 0f
        var panY = 0f
        var scale = 1.0f

        val focusX = 400f
        val focusY = 500f

        // Document point directly under the focal point before zoom
        val docFocusX = (focusX - panX) / scale
        val docFocusY = (focusY - panY) / scale

        // Zoom from 1.0x to 3.0x around focus point
        val newScale = 3.0f
        val r = newScale / scale
        panX = focusX - (focusX - panX) * r
        panY = focusY - (focusY - panY) * r
        scale = newScale

        // Verify screen coordinate of the document focus point after zoom
        val newScreenX = docFocusX * scale + panX
        val newScreenY = docFocusY * scale + panY

        assertEquals(focusX, newScreenX, 0.001f)
        assertEquals(focusY, newScreenY, 0.001f)
    }

    @Test
    fun testScaleCoerceBounds() {
        val minScale = CanvasView.MIN_SCALE
        val maxScale = CanvasView.MAX_SCALE

        val tooSmall = 0.2f
        val tooLarge = 8.5f
        val withinRange = 2.4f

        assertEquals(0.5f, tooSmall.coerceIn(minScale, maxScale), 0.001f)
        assertEquals(5.0f, tooLarge.coerceIn(minScale, maxScale), 0.001f)
        assertEquals(2.4f, withinRange.coerceIn(minScale, maxScale), 0.001f)
    }

    @Test
    fun testCatmullRomControlPointMath() {
        // Point sequence: (0, 0), (10, 20), (30, 40), (60, 50)
        val p0x = 0f
        val p0y = 0f
        val p1x = 10f
        val p1y = 20f
        val p2x = 30f
        val p2y = 40f
        val p3x = 60f
        val p3y = 50f

        val cp1X = p1x + (p2x - p0x) / 6f
        val cp1Y = p1y + (p2y - p0y) / 6f
        val cp2X = p2x - (p3x - p1x) / 6f
        val cp2Y = p2y - (p3y - p1y) / 6f

        // cp1X = 10 + 30/6 = 15.0f, cp1Y = 20 + 40/6 = 26.666f
        // cp2X = 30 - 50/6 = 21.666f, cp2Y = 40 - 30/6 = 35.0f
        assertEquals(15.0f, cp1X, 0.001f)
        assertEquals(26.666f, cp1Y, 0.001f)
        assertEquals(21.666f, cp2X, 0.001f)
        assertEquals(35.0f, cp2Y, 0.001f)
    }
}
