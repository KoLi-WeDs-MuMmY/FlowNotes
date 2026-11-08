package com.flownotes.app.engine

import android.graphics.RectF
import com.flownotes.app.model.Point
import kotlin.math.abs
import kotlin.math.hypot

sealed class DetectedShape {
    data class Line(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : DetectedShape()
    data class Rectangle(val rect: RectF) : DetectedShape()
    data class Circle(val centerX: Float, val centerY: Float, val radius: Float) : DetectedShape()
    data class Triangle(val p1: Point, val p2: Point, val p3: Point) : DetectedShape()
}

/**
 * Real-time heuristic geometric shape recognition for digital notes.
 */
object ShapeRecognizer {

    fun recognize(points: List<Point>): DetectedShape? {
        if (points.size < 4) return null

        val start = points.first()
        val end = points.last()

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (p in points) {
            minX = minX.coerceAtMost(p.x)
            maxX = maxX.coerceAtLeast(p.x)
            minY = minY.coerceAtMost(p.y)
            maxY = maxY.coerceAtLeast(p.y)
        }

        val width = maxX - minX
        val height = maxY - minY
        val diagonal = hypot(width, height)
        val endToEndDist = hypot(end.x - start.x, end.y - start.y)

        // 1. Check if straight line: distance between start and end is close to diagonal
        if (diagonal > 40f && endToEndDist > diagonal * 0.88f) {
            return DetectedShape.Line(start.x, start.y, end.x, end.y)
        }

        // 2. Check if closed shape (start & end are close)
        val isClosed = endToEndDist < diagonal * 0.18f
        if (isClosed && diagonal > 40f) {
            val aspectRatio = width / height.coerceAtLeast(1f)
            // If aspect ratio is close to 1:1, check circle
            if (aspectRatio in 0.75f..1.35f) {
                val cx = minX + width / 2f
                val cy = minY + height / 2f
                val radius = (width + height) / 4f
                return DetectedShape.Circle(cx, cy, radius)
            } else {
                return DetectedShape.Rectangle(RectF(minX, minY, maxX, maxY))
            }
        }

        return null
    }
}
