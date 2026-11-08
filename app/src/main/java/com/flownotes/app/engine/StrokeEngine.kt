package com.flownotes.app.engine

import android.graphics.Path
import com.flownotes.app.model.Point
import com.flownotes.app.model.Stroke
import kotlin.math.hypot

/**
 * High-performance digital ink smoothing engine for Android.
 * Generates smooth Bezier curves from raw stylus/touch coordinates and handles stroke intersection.
 */
object StrokeEngine {

    /**
     * Builds a smooth Android Path using quadratic Bezier mid-points from raw points.
     */
    fun createSmoothPath(points: List<Point>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[0].x + 0.1f, points[0].y + 0.1f)
            return path
        }

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size - 1) {
            val pPrev = points[i]
            val pNext = points[i + 1]
            val midX = (pPrev.x + pNext.x) / 2f
            val midY = (pPrev.y + pNext.y) / 2f
            path.quadTo(pPrev.x, pPrev.y, midX, midY)
        }

        val last = points.last()
        path.lineTo(last.x, last.y)
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
     * Calculates dynamic line width for fountain pen based on stylus pressure and drawing speed.
     */
    fun calculateDynamicWidth(
        baseWidth: Float,
        current: Point,
        previous: Point
    ): Float {
        val distance = hypot(current.x - previous.x, current.y - previous.y)
        val timeDiff = (current.timestamp - previous.timestamp).coerceAtLeast(1L)
        val speed = (distance / timeDiff).coerceIn(0.1f, 4.0f)
        val pressure = current.pressure.coerceIn(0.2f, 1.0f)

        // Lower width at high speed, higher width at high pressure
        val dynamicFactor = (pressure * 1.4f) - (speed * 0.15f)
        return (baseWidth * dynamicFactor).coerceIn(baseWidth * 0.4f, baseWidth * 2.2f)
    }
}
