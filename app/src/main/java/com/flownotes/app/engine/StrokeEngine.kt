package com.flownotes.app.engine

import android.graphics.Path
import com.flownotes.app.model.Point
import com.flownotes.app.model.Stroke
import kotlin.math.hypot

/**
 * High-performance digital ink smoothing engine for Android.
 * Generates smooth C1-continuous Catmull-Rom splines via cubic Bezier curves,
 * dynamic velocity/pressure width smoothing, and stroke collision detection.
 */
object StrokeEngine {

    /**
     * Generates a C1-continuous spline using Catmull-Rom to Cubic Bezier conversion.
     * Passes directly through every sampled user point, eliminating angular facets.
     */
    fun createSmoothPath(points: List<Point>): Path {
        val path = Path()
        val n = points.size
        if (n == 0) return path
        if (n == 1) {
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
            return path
        }
        if (n == 2) {
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[1].x, points[1].y)
            return path
        }

        path.moveTo(points[0].x, points[0].y)

        for (i in 0 until n - 1) {
            val p0 = if (i == 0) {
                Point(2 * points[0].x - points[1].x, 2 * points[0].y - points[1].y)
            } else {
                points[i - 1]
            }
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < n) {
                points[i + 2]
            } else {
                Point(2 * points[n - 1].x - points[n - 2].x, 2 * points[n - 1].y - points[n - 2].y)
            }

            // Catmull-Rom to Cubic Bezier control points
            val cp1X = p1.x + (p2.x - p0.x) / 6f
            val cp1Y = p1.y + (p2.y - p0.y) / 6f
            val cp2X = p2.x - (p3.x - p1.x) / 6f
            val cp2Y = p2.y - (p3.y - p1.y) / 6f

            path.cubicTo(cp1X, cp1Y, cp2X, cp2Y, p2.x, p2.y)
        }
        return path
    }

    /**
     * Checks if a point (e.g. from an eraser touch) intersects a given stroke within a tolerance radius.
     */
    fun intersectsStroke(stroke: Stroke, x: Float, y: Float, radius: Float): Boolean {
        for (pt in stroke.points) {
            val dist = hypot(pt.x - x, pt.y - y)
            if (dist <= radius) {
                return true
            }
        }
        return false
    }

    /**
     * Low-pass filtered dynamic width for Fountain Pen and responsive inking.
     * Uses Exponential Moving Average (EMA) to eliminate discrete pressure and velocity stair-stepping.
     */
    fun calculateDynamicWidthMetric(
        baseWidth: Float,
        current: Point,
        previous: Point,
        prevSmoothedVelocity: Float = 0f,
        prevSmoothedPressure: Float = 0.5f
    ): DynamicStrokeMetric {
        val distance = hypot(current.x - previous.x, current.y - previous.y)
        val dt = (current.timestamp - previous.timestamp).coerceAtLeast(1L).toFloat()
        val instVelocity = (distance / dt).coerceIn(0.1f, 5.0f)
        val instPressure = current.pressure.coerceIn(0.15f, 1.0f)

        // Exponential Moving Average
        val smoothedVelocity = 0.35f * instVelocity + 0.65f * prevSmoothedVelocity
        val smoothedPressure = 0.40f * instPressure + 0.60f * prevSmoothedPressure

        val factor = (smoothedPressure * 1.25f) - (smoothedVelocity * 0.18f)
        val finalWidth = (baseWidth * (0.45f + factor)).coerceIn(baseWidth * 0.35f, baseWidth * 2.3f)

        return DynamicStrokeMetric(finalWidth, smoothedVelocity, smoothedPressure)
    }

    /**
     * Overload returning Float for direct width queries.
     */
    fun calculateDynamicWidth(
        baseWidth: Float,
        current: Point,
        previous: Point
    ): Float {
        return calculateDynamicWidthMetric(baseWidth, current, previous, 0f, 0.5f).width
    }

    data class DynamicStrokeMetric(
        val width: Float,
        val smoothedVelocity: Float,
        val smoothedPressure: Float
    )
}
