package com.flownotes.app.engine

import android.graphics.Path
import android.graphics.RectF
import com.flownotes.app.model.Point
import kotlin.math.*

enum class TriangleType {
    EQUILATERAL,
    RIGHT_ANGLED,
    SCALENE
}

sealed class DetectedShape {
    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val snappedAngleDegrees: Float? = null,
        val isSnapped: Boolean = false
    ) : DetectedShape()

    data class Rectangle(
        val rect: RectF
    ) : DetectedShape()

    data class RoundedRectangle(
        val rect: RectF,
        val cornerRadius: Float
    ) : DetectedShape()

    data class Circle(
        val centerX: Float,
        val centerY: Float,
        val radius: Float
    ) : DetectedShape()

    data class Ellipse(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val rotationDegrees: Float = 0f
    ) : DetectedShape()

    data class Triangle(
        val p1: Point,
        val p2: Point,
        val p3: Point,
        val triangleType: TriangleType = TriangleType.SCALENE
    ) : DetectedShape()

    fun toAndroidPath(): Path {
        val path = Path()
        when (this) {
            is Line -> {
                path.moveTo(startX, startY)
                path.lineTo(endX, endY)
            }
            is Rectangle -> {
                path.addRect(rect, Path.Direction.CW)
            }
            is RoundedRectangle -> {
                path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            is Circle -> {
                path.addCircle(centerX, centerY, radius, Path.Direction.CW)
            }
            is Ellipse -> {
                val steps = 48
                val rad = Math.toRadians(rotationDegrees.toDouble())
                val cosPhi = cos(rad)
                val sinPhi = sin(rad)
                for (i in 0..steps) {
                    val theta = i * 2 * Math.PI / steps
                    val cosT = cos(theta)
                    val sinT = sin(theta)
                    val px = centerX + (radiusX * cosT * cosPhi - radiusY * sinT * sinPhi).toFloat()
                    val py = centerY + (radiusX * cosT * sinPhi + radiusY * sinT * cosPhi).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            is Triangle -> {
                path.moveTo(p1.x, p1.y)
                path.lineTo(p2.x, p2.y)
                path.lineTo(p3.x, p3.y)
                path.close()
            }
        }
        return path
    }
}

/**
 * Advanced Geometric Shape Recognition Engine for FlowNotes.
 * Detects Lines (with angle snapping to 0°, 30°, 45°, 60°, 90°, 180°),
 * Circles, Rectangles, Rounded Rectangles, Ellipses, and Triangles.
 */
object ShapeRecognizer {

    private const val MIN_STROKE_POINTS = 5
    private const val MIN_STROKE_LENGTH = 28f
    private const val ANGLE_SNAP_TOLERANCE_DEG = 6.5f

    private val CANONICAL_SNAP_ANGLES = floatArrayOf(
        0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f,
        180f, 210f, 225f, 240f, 270f, 300f, 315f, 330f, 360f
    )

    fun recognize(points: List<Point>): DetectedShape? {
        if (points.size < MIN_STROKE_POINTS) return null

        val arcLength = computeArcLength(points)
        if (arcLength < MIN_STROKE_LENGTH) return null

        val start = points.first()
        val end = points.last()
        val chordDist = hypot(end.x - start.x, end.y - start.y)

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var sumX = 0.0
        var sumY = 0.0

        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
            sumX += p.x
            sumY += p.y
        }

        val width = maxX - minX
        val height = maxY - minY
        val diagonal = hypot(width, height)
        val centroidX = (sumX / points.size).toFloat()
        val centroidY = (sumY / points.size).toFloat()

        // 1. Check Straight Line (Tortuosity & Collinearity)
        val straightness = chordDist / arcLength
        if (straightness >= 0.91f && chordDist > 25f) {
            val maxPerpDist = computeMaxPerpendicularDistance(points, start, end)
            if (maxPerpDist <= max(12f, 0.08f * chordDist)) {
                return snapLine(points, start, end, chordDist)
            }
        }

        // 2. Check if Shape is Closed (relative to bounding box diagonal, NOT arc length)
        val isClosed = chordDist < diagonal * 0.20f
        if (!isClosed) return null

        // Radial Statistics around Centroid
        var sumR = 0.0
        val radii = DoubleArray(points.size)
        for (i in points.indices) {
            val r = hypot((points[i].x - centroidX).toDouble(), (points[i].y - centroidY).toDouble())
            radii[i] = r
            sumR += r
        }
        val meanR = sumR / points.size
        var varianceR = 0.0
        for (r in radii) {
            val diff = r - meanR
            varianceR += diff * diff
        }
        val stdDevR = sqrt(varianceR / points.size)
        val cvR = (stdDevR / meanR.coerceAtLeast(1.0)).toFloat()
        val aspectRatio = max(width, height) / min(width, height).coerceAtLeast(1f)

        // 3. Circle Check: low radial variation & square aspect ratio
        if (cvR <= 0.10f && aspectRatio <= 1.22f) {
            return DetectedShape.Circle(centroidX, centroidY, meanR.toFloat())
        }

        // 4. Polygon Simplification (Ramer-Douglas-Peucker) for Triangles
        val simplified = simplifyClosedPolygon(points, centroidX, centroidY, diagonal)
        if (simplified.size == 3) {
            return classifyTriangle(simplified[0], simplified[1], simplified[2])
        }

        // 5. Rectangle and Rounded Rectangle Check (Evaluated BEFORE Ellipse)
        val rectBounds = RectF(minX, minY, maxX, maxY)
        val minDim = min(width, height)
        val avgEdgeDist = computeAverageEdgeDistance(points, rectBounds)

        if (avgEdgeDist <= max(6f, minDim * 0.05f)) {
            val cornerOffsets = computeCornerOffsets(points, rectBounds)
            val maxCornerDist = maxOf(cornerOffsets.tl, cornerOffsets.tr, cornerOffsets.br, cornerOffsets.bl)
            if (maxCornerDist <= minDim * 0.45f) {
                val avgCornerOffset = cornerOffsets.average
                if (avgCornerOffset in 8f..(minDim * 0.38f)) {
                    val cornerRadius = (avgCornerOffset * 2.414f).coerceIn(8f, minDim * 0.40f)
                    return DetectedShape.RoundedRectangle(rectBounds, cornerRadius)
                } else if (avgCornerOffset < 8f) {
                    return DetectedShape.Rectangle(rectBounds)
                }
            }
        }

        // 6. Ellipse Check (PCA Covariance & Residual Error)
        val ellipse = fitEllipse(points, centroidX, centroidY)
        if (ellipse != null) {
            return ellipse
        }

        // 7. Fallthrough Rejection: Never return Rectangle by default; reject unknown closed strokes
        return null
    }

    private fun snapLine(points: List<Point>, start: Point, end: Point, chordDist: Float): DetectedShape.Line {
        val rawAngleRad = atan2(end.y - start.y, end.x - start.x)
        var rawAngleDeg = Math.toDegrees(rawAngleRad.toDouble()).toFloat()
        if (rawAngleDeg < 0f) rawAngleDeg += 360f

        var minDiff = Float.MAX_VALUE
        var bestSnap = 0f

        for (snap in CANONICAL_SNAP_ANGLES) {
            val diff = angularDifference(rawAngleDeg, snap)
            if (diff < minDiff) {
                minDiff = diff
                bestSnap = if (snap >= 360f) 0f else snap
            }
        }

        return if (minDiff <= ANGLE_SNAP_TOLERANCE_DEG) {
            when (bestSnap) {
                0f -> {
                    val baselineY = computeBaselineY(points, start)
                    DetectedShape.Line(
                        startX = start.x,
                        startY = baselineY,
                        endX = start.x + chordDist,
                        endY = baselineY,
                        snappedAngleDegrees = 0f,
                        isSnapped = true
                    )
                }
                180f -> {
                    val baselineY = computeBaselineY(points, start)
                    DetectedShape.Line(
                        startX = start.x,
                        startY = baselineY,
                        endX = start.x - chordDist,
                        endY = baselineY,
                        snappedAngleDegrees = 180f,
                        isSnapped = true
                    )
                }
                90f -> {
                    val baselineX = computeBaselineX(points, start)
                    DetectedShape.Line(
                        startX = baselineX,
                        startY = start.y,
                        endX = baselineX,
                        endY = start.y + chordDist,
                        snappedAngleDegrees = 90f,
                        isSnapped = true
                    )
                }
                270f -> {
                    val baselineX = computeBaselineX(points, start)
                    DetectedShape.Line(
                        startX = baselineX,
                        startY = start.y,
                        endX = baselineX,
                        endY = start.y - chordDist,
                        snappedAngleDegrees = 270f,
                        isSnapped = true
                    )
                }
                else -> {
                    val snapRad = Math.toRadians(bestSnap.toDouble())
                    val snappedEndX = start.x + chordDist * cos(snapRad).toFloat()
                    val snappedEndY = start.y + chordDist * sin(snapRad).toFloat()
                    DetectedShape.Line(
                        startX = start.x,
                        startY = start.y,
                        endX = snappedEndX,
                        endY = snappedEndY,
                        snappedAngleDegrees = bestSnap,
                        isSnapped = true
                    )
                }
            }
        } else {
            DetectedShape.Line(
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
                snappedAngleDegrees = null,
                isSnapped = false
            )
        }
    }

    private fun computeBaselineY(points: List<Point>, start: Point): Float {
        val n = points.size
        var sumX = 0.0
        var sumY = 0.0
        for (p in points) {
            sumX += p.x
            sumY += p.y
        }
        val meanX = sumX / n
        val meanY = sumY / n
        var num = 0.0
        var den = 0.0
        for (p in points) {
            val dx = p.x - meanX
            val dy = p.y - meanY
            num += dx * dy
            den += dx * dx
        }
        var base = start.y
        if (den > 1e-4) {
            val slope = num / den
            base = (meanY + slope * (start.x - meanX)).toFloat()
        }
        if (abs(base - round(base)) <= 0.45f) {
            base = round(base)
        }
        return base
    }

    private fun computeBaselineX(points: List<Point>, start: Point): Float {
        val n = points.size
        var sumX = 0.0
        var sumY = 0.0
        for (p in points) {
            sumX += p.x
            sumY += p.y
        }
        val meanX = sumX / n
        val meanY = sumY / n
        var num = 0.0
        var den = 0.0
        for (p in points) {
            val dx = p.x - meanX
            val dy = p.y - meanY
            num += dx * dy
            den += dy * dy
        }
        var base = start.x
        if (den > 1e-4) {
            val slope = num / den
            base = (meanX + slope * (start.y - meanY)).toFloat()
        }
        if (abs(base - round(base)) <= 0.45f) {
            base = round(base)
        }
        return base
    }

    private fun fitEllipse(points: List<Point>, cx: Float, cy: Float): DetectedShape.Ellipse? {
        val n = points.size
        var mxx = 0.0
        var myy = 0.0
        var mxy = 0.0

        for (p in points) {
            val dx = (p.x - cx).toDouble()
            val dy = (p.y - cy).toDouble()
            mxx += dx * dx
            myy += dy * dy
            mxy += dx * dy
        }
        mxx /= n
        myy /= n
        mxy /= n

        val delta = sqrt((mxx - myy) * (mxx - myy) + 4.0 * mxy * mxy)
        val lambda1 = (mxx + myy + delta) / 2.0
        val lambda2 = (mxx + myy - delta) / 2.0

        if (lambda1 <= 0.0 || lambda2 <= 0.0) return null

        val a = sqrt(2.0 * lambda1).toFloat()
        val b = sqrt(2.0 * lambda2).toFloat()
        val phiRad = 0.5 * atan2(2.0 * mxy, mxx - myy)
        val phiDeg = Math.toDegrees(phiRad).toFloat()

        if (a / b.coerceAtLeast(1f) < 1.18f) return null // Too close to circle

        // Compute normalized error in unrotated frame
        val cosP = cos(phiRad)
        val sinP = sin(phiRad)
        var totalError = 0.0

        for (p in points) {
            val dx = p.x - cx
            val dy = p.y - cy
            val u = dx * cosP + dy * sinP
            val v = -dx * sinP + dy * cosP
            val normDist = sqrt((u * u) / (a * a) + (v * v) / (b * b))
            totalError += abs(normDist - 1.0)
        }

        val avgError = (totalError / n).toFloat()
        return if (avgError <= 0.13f) {
            DetectedShape.Ellipse(cx, cy, a, b, phiDeg)
        } else {
            null
        }
    }

    private fun simplifyClosedPolygon(
        points: List<Point>,
        cx: Float,
        cy: Float,
        diagonal: Float
    ): List<Point> {
        // Find anchor furthest from centroid
        var maxDistSq = -1.0
        var anchorIdx = 0
        for (i in points.indices) {
            val dx = (points[i].x - cx).toDouble()
            val dy = (points[i].y - cy).toDouble()
            val dSq = dx * dx + dy * dy
            if (dSq > maxDistSq) {
                maxDistSq = dSq
                anchorIdx = i
            }
        }

        val reordered = ArrayList<Point>(points.size + 1)
        for (i in points.indices) {
            reordered.add(points[(anchorIdx + i) % points.size])
        }
        reordered.add(reordered.first())

        val epsilon = max(12f, 0.055f * diagonal)
        val simplified = rdp(reordered, 0, reordered.size - 1, epsilon)

        // Drop the duplicate closing point
        return if (simplified.size > 1 && simplified.first() == simplified.last()) {
            simplified.dropLast(1)
        } else {
            simplified
        }
    }

    private fun rdp(points: List<Point>, start: Int, end: Int, epsilon: Float): MutableList<Point> {
        var maxDist = 0f
        var index = start
        val p1 = points[start]
        val p2 = points[end]

        for (i in start + 1 until end) {
            val d = perpDist(points[i], p1, p2)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }

        return if (maxDist > epsilon) {
            val rec1 = rdp(points, start, index, epsilon)
            val rec2 = rdp(points, index, end, epsilon)
            rec1.removeAt(rec1.size - 1)
            rec1.addAll(rec2)
            rec1
        } else {
            mutableListOf(p1, p2)
        }
    }

    private fun classifyTriangle(p1: Point, p2: Point, p3: Point): DetectedShape.Triangle {
        val a = hypot(p2.x - p3.x, p2.y - p3.y)
        val b = hypot(p1.x - p3.x, p1.y - p3.y)
        val c = hypot(p1.x - p2.x, p1.y - p2.y)

        val cosA = ((b * b + c * c - a * a) / (2f * b * c)).coerceIn(-1f, 1f)
        val cosB = ((a * a + c * c - b * b) / (2f * a * c)).coerceIn(-1f, 1f)
        val angleA = Math.toDegrees(acos(cosA.toDouble())).toFloat()
        val angleB = Math.toDegrees(acos(cosB.toDouble())).toFloat()
        val angleC = 180f - angleA - angleB

        val maxEquilateralDiff = maxOf(abs(angleA - 60f), abs(angleB - 60f), abs(angleC - 60f))
        val maxSide = maxOf(a, b, c)
        val minSide = minOf(a, b, c)
        val sideRatioDiff = (maxSide - minSide) / maxSide.coerceAtLeast(1f)

        // Tightened equilateral threshold: angle diff <= 5.0° and side ratio diff <= 0.08
        if (maxEquilateralDiff <= 5.0f && sideRatioDiff <= 0.08f) {
            // Equilateral triangle regularized to circumcircle
            val gx = (p1.x + p2.x + p3.x) / 3f
            val gy = (p1.y + p2.y + p3.y) / 3f
            val r = (hypot(p1.x - gx, p1.y - gy) + hypot(p2.x - gx, p2.y - gy) + hypot(p3.x - gx, p3.y - gy)) / 3f
            val theta0 = atan2(p1.y - gy, p1.x - gx)

            val np1 = Point(gx + r * cos(theta0), gy + r * sin(theta0))
            val np2 = Point(gx + r * cos(theta0 + 2.0944f), gy + r * sin(theta0 + 2.0944f))
            val np3 = Point(gx + r * cos(theta0 + 4.1888f), gy + r * sin(theta0 + 4.1888f))
            return DetectedShape.Triangle(np1, np2, np3, TriangleType.EQUILATERAL)
        }

        val hasRightAngle = abs(angleA - 90f) <= 12f || abs(angleB - 90f) <= 12f || abs(angleC - 90f) <= 12f
        return if (hasRightAngle) {
            DetectedShape.Triangle(p1, p2, p3, TriangleType.RIGHT_ANGLED)
        } else {
            DetectedShape.Triangle(p1, p2, p3, TriangleType.SCALENE)
        }
    }

    private data class CornerOffsets(
        val tl: Float,
        val tr: Float,
        val br: Float,
        val bl: Float,
        val average: Float
    )

    private fun computeCornerOffsets(points: List<Point>, bounds: RectF): CornerOffsets {
        var minTL = Float.MAX_VALUE
        var minTR = Float.MAX_VALUE
        var minBR = Float.MAX_VALUE
        var minBL = Float.MAX_VALUE

        for (p in points) {
            minTL = min(minTL, hypot(p.x - bounds.left, p.y - bounds.top))
            minTR = min(minTR, hypot(p.x - bounds.right, p.y - bounds.top))
            minBR = min(minBR, hypot(p.x - bounds.right, p.y - bounds.bottom))
            minBL = min(minBL, hypot(p.x - bounds.left, p.y - bounds.bottom))
        }
        val avg = (minTL + minTR + minBR + minBL) / 4f
        return CornerOffsets(minTL, minTR, minBR, minBL, avg)
    }

    private fun computeAverageEdgeDistance(points: List<Point>, bounds: RectF): Float {
        var sumDist = 0.0
        for (p in points) {
            val dx = min(abs(p.x - bounds.left), abs(p.x - bounds.right))
            val dy = min(abs(p.y - bounds.top), abs(p.y - bounds.bottom))
            sumDist += min(dx, dy)
        }
        return (sumDist / points.size).toFloat()
    }

    private fun computeArcLength(points: List<Point>): Float {
        var len = 0f
        for (i in 0 until points.size - 1) {
            len += hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
        }
        return len
    }

    private fun computeMaxPerpendicularDistance(points: List<Point>, p1: Point, p2: Point): Float {
        var maxD = 0f
        for (p in points) {
            val d = perpDist(p, p1, p2)
            if (d > maxD) maxD = d
        }
        return maxD
    }

    private fun perpDist(p: Point, a: Point, b: Point): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 0.001f) return hypot(p.x - a.x, p.y - a.y)
        return abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x) / len
    }

    private fun angularDifference(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }
}
