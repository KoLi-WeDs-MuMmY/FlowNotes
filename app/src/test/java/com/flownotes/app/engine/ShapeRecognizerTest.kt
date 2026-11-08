package com.flownotes.app.engine

import com.flownotes.app.model.Point
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ShapeRecognizerTest {

    @Test
    fun testHorizontalLineSnapsToZeroDegrees() {
        // Line with slight 2-degree hand jitter: (20, 100) to (300, 108)
        val points = mutableListOf<Point>()
        for (i in 0..20) {
            val t = i / 20f
            val x = 20f + 280f * t
            val y = 100f + 8f * t + (if (i % 2 == 0) 1f else -1f)
            points.add(Point(x, y))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull("Shape must be recognized", shape)
        assertTrue("Must be a Line", shape is DetectedShape.Line)
        val line = shape as DetectedShape.Line
        assertTrue("Must have angle snapped", line.isSnapped)
        assertEquals(0f, line.snappedAngleDegrees ?: -1f, 0.1f)
        assertEquals(100f, line.startY, 0.1f)
        assertEquals(100f, line.endY, 0.1f) // Snapped horizontal
    }

    @Test
    fun testVerticalLineSnapsTo90Degrees() {
        // Vertical line with 1.5-degree jitter: (150, 30) to (154, 400)
        val points = mutableListOf<Point>()
        for (i in 0..25) {
            val t = i / 25f
            val x = 150f + 4f * t + (if (i % 3 == 0) 1.5f else -1.5f)
            val y = 30f + 370f * t
            points.add(Point(x, y))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue(shape is DetectedShape.Line)
        val line = shape as DetectedShape.Line
        assertTrue(line.isSnapped)
        assertEquals(90f, line.snappedAngleDegrees ?: -1f, 0.1f)
        assertEquals(150f, line.endX, 0.1f) // Snapped vertical
    }

    @Test
    fun testDiagonal45DegreeLineSnapping() {
        // 45-degree diagonal: (50, 50) to (255, 248) ~ 44.2 degrees
        val points = mutableListOf<Point>()
        for (i in 0..20) {
            val t = i / 20f
            val x = 50f + 205f * t
            val y = 50f + 198f * t
            points.add(Point(x, y))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue(shape is DetectedShape.Line)
        val line = shape as DetectedShape.Line
        assertTrue(line.isSnapped)
        assertEquals(45f, line.snappedAngleDegrees ?: -1f, 0.1f)
    }

    @Test
    fun testIsometric30And60DegreeLineSnapping() {
        // 30-degree line (~ 32.5 degrees with noise)
        val points30 = mutableListOf<Point>()
        for (i in 0..20) {
            val t = i / 20f
            val x = 50f + 200f * t
            val y = 50f + 125f * t // atan2(125, 200) = 32.0 degrees
            points30.add(Point(x, y))
        }
        val shape30 = ShapeRecognizer.recognize(points30) as DetectedShape.Line
        assertTrue(shape30.isSnapped)
        assertEquals(30f, shape30.snappedAngleDegrees ?: -1f, 0.1f)

        // 60-degree line (~ 58.5 degrees with noise)
        val points60 = mutableListOf<Point>()
        for (i in 0..20) {
            val t = i / 20f
            val x = 50f + 120f * t
            val y = 50f + 195f * t // atan2(195, 120) = 58.4 degrees
            points60.add(Point(x, y))
        }
        val shape60 = ShapeRecognizer.recognize(points60) as DetectedShape.Line
        assertTrue(shape60.isSnapped)
        assertEquals(60f, shape60.snappedAngleDegrees ?: -1f, 0.1f)
    }

    @Test
    fun testArbitraryLineOutsideToleranceWindowPreservesRawAngle() {
        // 73-degree line: outside tolerance of 60° (+13°) and 90° (-17°)
        val points = mutableListOf<Point>()
        val rad = Math.toRadians(73.0)
        for (i in 0..20) {
            val d = i * 12f
            points.add(Point(100f + (d * cos(rad)).toFloat(), 100f + (d * sin(rad)).toFloat()))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue(shape is DetectedShape.Line)
        val line = shape as DetectedShape.Line
        assertFalse("Must NOT snap angle outside tolerance window", line.isSnapped)
        assertNull(line.snappedAngleDegrees)
    }

    @Test
    fun testZigZagStrokeRejectsLineRecognition() {
        // Non-straight zigzag
        val points = mutableListOf<Point>()
        for (i in 0..40) {
            val x = 20f + i * 5f
            val y = if (i % 4 < 2) 50f else 120f
            points.add(Point(x, y))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNull("Zigzag stroke must not be recognized as a line or shape", shape)
    }

    @Test
    fun testCircleRecognitionWithRadiusFitting() {
        val cx = 200f
        val cy = 200f
        val r = 75f
        val points = mutableListOf<Point>()
        val steps = 36
        for (i in 0..steps) {
            val theta = i * 2 * Math.PI / steps
            // Add slight radial noise (+/- 2px)
            val noise = if (i % 2 == 0) 1.5f else -1.5f
            val px = cx + (r + noise) * cos(theta).toFloat()
            val py = cy + (r + noise) * sin(theta).toFloat()
            points.add(Point(px, py))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue("Must be Circle", shape is DetectedShape.Circle)
        val circle = shape as DetectedShape.Circle
        assertEquals(cx, circle.centerX, 3f)
        assertEquals(cy, circle.centerY, 3f)
        assertEquals(r, circle.radius, 3f)
    }

    @Test
    fun testRotatedEllipseDetection() {
        val cx = 250f
        val cy = 250f
        val a = 120f
        val b = 50f
        val rotDeg = 45f
        val rotRad = Math.toRadians(rotDeg.toDouble())
        val cosP = cos(rotRad)
        val sinP = sin(rotRad)

        val points = mutableListOf<Point>()
        val steps = 40
        for (i in 0..steps) {
            val theta = i * 2 * Math.PI / steps
            val u = a * cos(theta)
            val v = b * sin(theta)
            val px = (cx + u * cosP - v * sinP).toFloat()
            val py = (cy + u * sinP + v * cosP).toFloat()
            points.add(Point(px, py))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue("Must be Ellipse", shape is DetectedShape.Ellipse)
        val ellipse = shape as DetectedShape.Ellipse
        assertEquals(cx, ellipse.centerX, 5f)
        assertEquals(cy, ellipse.centerY, 5f)
        assertEquals(a, ellipse.radiusX, 10f)
        assertEquals(b, ellipse.radiusY, 10f)
        assertEquals(45f, ellipse.rotationDegrees, 8f)
    }

    @Test
    fun testSharpRectangleDetection() {
        // Sharp rectangle: (50, 50) to (300, 180)
        val points = mutableListOf<Point>()
        // Top edge
        for (x in 50..300 step 15) points.add(Point(x.toFloat(), 50f))
        // Right edge
        for (y in 50..180 step 15) points.add(Point(300f, y.toFloat()))
        // Bottom edge
        for (x in 300 downTo 50 step 15) points.add(Point(x.toFloat(), 180f))
        // Left edge
        for (y in 180 downTo 50 step 15) points.add(Point(50f, y.toFloat()))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue("Must be sharp Rectangle", shape is DetectedShape.Rectangle)
        val rect = shape as DetectedShape.Rectangle
        assertEquals(50f, rect.rect.left, 5f)
        assertEquals(300f, rect.rect.right, 5f)
    }

    @Test
    fun testRoundedRectangleDetectionWithCornerRadius() {
        val r = 24f
        val left = 60f
        val top = 60f
        val right = 260f
        val bottom = 180f

        val points = mutableListOf<Point>()
        // Top edge
        for (x in (left + r).toInt()..(right - r).toInt() step 10) points.add(Point(x.toFloat(), top))
        // TR corner arc
        for (deg in 270..360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            points.add(Point(right - r + (r * cos(rad)).toFloat(), top + r + (r * sin(rad)).toFloat()))
        }
        // Right edge
        for (y in (top + r).toInt()..(bottom - r).toInt() step 10) points.add(Point(right, y.toFloat()))
        // BR corner arc
        for (deg in 0..90 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            points.add(Point(right - r + (r * cos(rad)).toFloat(), bottom - r + (r * sin(rad)).toFloat()))
        }
        // Bottom edge
        for (x in (right - r).toInt() downTo (left + r).toInt() step 10) points.add(Point(x.toFloat(), bottom))
        // BL corner arc
        for (deg in 90..180 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            points.add(Point(left + r + (r * cos(rad)).toFloat(), bottom - r + (r * sin(rad)).toFloat()))
        }
        // Left edge
        for (y in (bottom - r).toInt() downTo (top + r).toInt() step 10) points.add(Point(left, y.toFloat()))
        // TL corner arc
        for (deg in 180..270 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            points.add(Point(left + r + (r * cos(rad)).toFloat(), top + r + (r * sin(rad)).toFloat()))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue("Must be RoundedRectangle", shape is DetectedShape.RoundedRectangle)
        val roundRect = shape as DetectedShape.RoundedRectangle
        assertTrue("Corner radius must be positive", roundRect.cornerRadius in 16f..30f)
    }

    @Test
    fun testEquilateralTriangleDetection() {
        val gx = 150f
        val gy = 150f
        val r = 80f
        val p1 = Point(gx, gy - r) // Top
        val p2 = Point(gx + r * cos(Math.toRadians(30.0)).toFloat(), gy + r * sin(Math.toRadians(30.0)).toFloat())
        val p3 = Point(gx - r * cos(Math.toRadians(30.0)).toFloat(), gy + r * sin(Math.toRadians(30.0)).toFloat())

        val points = mutableListOf<Point>()
        // Side 1
        for (i in 0..10) points.add(Point(p1.x + (p2.x - p1.x) * (i / 10f), p1.y + (p2.y - p1.y) * (i / 10f)))
        // Side 2
        for (i in 0..10) points.add(Point(p2.x + (p3.x - p2.x) * (i / 10f), p2.y + (p3.y - p2.y) * (i / 10f)))
        // Side 3
        for (i in 0..10) points.add(Point(p3.x + (p1.x - p3.x) * (i / 10f), p3.y + (p1.y - p3.y) * (i / 10f)))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue("Must be Triangle", shape is DetectedShape.Triangle)
        val tri = shape as DetectedShape.Triangle
        assertEquals(TriangleType.EQUILATERAL, tri.triangleType)
    }

    @Test
    fun testRightAngledTriangleDetection() {
        val p1 = Point(50f, 50f)
        val p2 = Point(250f, 50f)
        val p3 = Point(50f, 200f)

        val points = mutableListOf<Point>()
        for (i in 0..12) points.add(Point(p1.x + (p2.x - p1.x) * (i / 12f), p1.y + (p2.y - p1.y) * (i / 12f)))
        for (i in 0..12) points.add(Point(p2.x + (p3.x - p2.x) * (i / 12f), p2.y + (p3.y - p2.y) * (i / 12f)))
        for (i in 0..12) points.add(Point(p3.x + (p1.x - p3.x) * (i / 12f), p3.y + (p1.y - p3.y) * (i / 12f)))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue(shape is DetectedShape.Triangle)
        val tri = shape as DetectedShape.Triangle
        assertEquals(TriangleType.RIGHT_ANGLED, tri.triangleType)
    }

    @Test
    fun testScaleneTriangleDetection() {
        val p1 = Point(50f, 50f)
        val p2 = Point(220f, 70f)
        val p3 = Point(110f, 230f)

        val points = mutableListOf<Point>()
        for (i in 0..10) points.add(Point(p1.x + (p2.x - p1.x) * (i / 10f), p1.y + (p2.y - p1.y) * (i / 10f)))
        for (i in 0..10) points.add(Point(p2.x + (p3.x - p2.x) * (i / 10f), p2.y + (p3.y - p2.y) * (i / 10f)))
        for (i in 0..10) points.add(Point(p3.x + (p1.x - p3.x) * (i / 10f), p3.y + (p1.y - p3.y) * (i / 10f)))

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue(shape is DetectedShape.Triangle)
        val tri = shape as DetectedShape.Triangle
        assertEquals(TriangleType.SCALENE, tri.triangleType)
    }

    @Test
    fun testHoldToSnapStationaryThresholdGeometry() {
        // Test distance calculation within 12px jitter radius
        val anchor = Point(100f, 100f)
        val slightJitter = Point(106f, 105f) // hypot(6, 5) = 7.8px < 12px
        val largeMove = Point(115f, 120f)    // hypot(15, 20) = 25px > 12px

        val dist1 = hypot(slightJitter.x - anchor.x, slightJitter.y - anchor.y)
        assertTrue("Slight jitter is stationary", dist1 <= 12f)

        val dist2 = hypot(largeMove.x - anchor.x, largeMove.y - anchor.y)
        assertTrue("Large movement triggers active inking reset", dist2 > 12f)
    }
}
