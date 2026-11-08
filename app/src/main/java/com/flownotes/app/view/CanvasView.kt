package com.flownotes.app.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.flownotes.app.engine.DetectedShape
import com.flownotes.app.engine.ShapeRecognizer
import com.flownotes.app.engine.StrokeEngine
import com.flownotes.app.model.*
import com.flownotes.app.model.Point
import java.util.*
import kotlin.math.hypot

/**
 * Custom High-Performance Digital Ink Canvas View for Android.
 * Features stylus pressure sensitivity, Active Recall Tape, stroke eraser,
 * two-finger pinch-to-zoom (0.5x-5.0x) with focal point invariance, pan,
 * and 350ms Hold-to-Snap geometric shape recognition.
 */
class CanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 5.0f
        const val DEFAULT_DOC_WIDTH = 1200f
        const val DEFAULT_DOC_HEIGHT = 1600f
    }

    var currentPage: Page = Page(pageNumber = 1)
        set(value) {
            field = value
            invalidate()
        }

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isAmoledMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var pdfPageBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var currentTool: PenTool = PenTool.BALLPOINT
    var currentColor: Int = Color.parseColor("#0F172A") // Jet Black default
    var currentStrokeWidth: Float = 4.0f
    var tapeColor: Int = Color.parseColor("#FEF08A") // Soft yellow

    // Viewport Transformation State
    var scaleFactor: Float = 1.0f
        private set
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set

    var onScaleChangedListener: ((Float) -> Unit)? = null

    // Multi-touch Gesture Arbitration
    private var isTransforming = false
    private var lastFocusX = Float.NaN
    private var lastFocusY = Float.NaN

    // 350ms Hold-to-Snap Configuration
    var isHoldToSnapEnabled: Boolean = true
    var holdToSnapDurationMs: Long = 350L
    var stationaryJitterRadiusPx: Float = 12f

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null
    private var lastStationaryX: Float = 0f
    private var lastStationaryY: Float = 0f
    private var isHoldSnapActive: Boolean = false
    private var previewShape: DetectedShape? = null

    // Undo / Redo Stacks
    private val undoStack = ArrayDeque<PageStateSnapshot>()
    private val redoStack = ArrayDeque<PageStateSnapshot>()

    // Touch points in Document World Coordinates
    private val currentPoints = mutableListOf<Point>()
    private var tapeStartPoint: Point? = null

    // Pre-allocated Paints
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pencilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 205 // Tactile graphite appearance
    }

    private val highlighterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.BEVEL
        alpha = 110 // Semi-transparent overlay
    }

    private val shapePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val tapeSolidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tapeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    // Scale Gesture Detector (Pinch-to-Zoom)
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            this@CanvasView.lastFocusX = detector.focusX
            this@CanvasView.lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor
            val oldScale = this@CanvasView.scaleFactor
            val newScale = (oldScale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            val actualRatio = newScale / oldScale
            this@CanvasView.scaleFactor = newScale

            val focusX = detector.focusX
            val focusY = detector.focusY

            val prevFocusX = if (!this@CanvasView.lastFocusX.isNaN()) this@CanvasView.lastFocusX else focusX
            val prevFocusY = if (!this@CanvasView.lastFocusY.isNaN()) this@CanvasView.lastFocusY else focusY

            this@CanvasView.panX = focusX - (prevFocusX - this@CanvasView.panX) * actualRatio
            this@CanvasView.panY = focusY - (prevFocusY - this@CanvasView.panY) * actualRatio

            this@CanvasView.lastFocusX = focusX
            this@CanvasView.lastFocusY = focusY

            this@CanvasView.clampPan()
            this@CanvasView.invalidate()
            this@CanvasView.onScaleChangedListener?.invoke(this@CanvasView.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            this@CanvasView.lastFocusX = Float.NaN
            this@CanvasView.lastFocusY = Float.NaN
        }
    })

    // Double Tap Detector for 100% Reset / 200% Zoom
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (this@CanvasView.scaleFactor != 1.0f || this@CanvasView.panX != 0f || this@CanvasView.panY != 0f) {
                this@CanvasView.resetZoomAndPan(animated = true)
            } else {
                this@CanvasView.zoomTo(2.0f, e.x, e.y, animated = true)
            }
            return true
        }
    })

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Maps screen touch coordinate to document world coordinate.
     */
    fun screenToDocument(screenX: Float, screenY: Float): Pair<Float, Float> {
        val docX = (screenX - panX) / scaleFactor
        val docY = (screenY - panY) / scaleFactor
        return Pair(docX, docY)
    }

    /**
     * Maps document world coordinate to screen coordinate.
     */
    fun documentToScreen(docX: Float, docY: Float): Pair<Float, Float> {
        val screenX = docX * scaleFactor + panX
        val screenY = docY * scaleFactor + panY
        return Pair(screenX, screenY)
    }

    /**
     * Clamps pan coordinates so the document cannot be scrolled completely off-screen.
     */
    private fun clampPan() {
        val docW = if (width > 0) width.toFloat() else DEFAULT_DOC_WIDTH
        val docH = if (height > 0) height.toFloat() else DEFAULT_DOC_HEIGHT
        val scaledW = docW * scaleFactor
        val scaledH = docH * scaleFactor

        val marginX = docW * 0.4f
        val marginY = docH * 0.4f

        val minPanX = marginX - scaledW
        val maxPanX = docW - marginX
        val minPanY = marginY - scaledH
        val maxPanY = docH - marginY

        panX = panX.coerceIn(minPanX, maxPanX)
        panY = panY.coerceIn(minPanY, maxPanY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val docW = if (width > 0) width.toFloat() else DEFAULT_DOC_WIDTH
        val docH = if (height > 0) height.toFloat() else DEFAULT_DOC_HEIGHT

        // 1. Enter Document World Space
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(scaleFactor, scaleFactor)

        // Render Paper Template or PDF
        if (pdfPageBitmap != null) {
            val destRect = RectF(0f, 0f, docW, docH)
            canvas.drawBitmap(pdfPageBitmap!!, null, destRect, null)
        } else {
            PaperTemplateRenderer.drawPaper(canvas, docW, docH, currentPage.paperType, isDarkMode, isAmoledMode)
        }

        // Render Committed Strokes
        for (stroke in currentPage.strokes) {
            drawSingleStroke(canvas, stroke)
        }

        // Render Active Recall Tapes
        for (tape in currentPage.tapes) {
            drawActiveRecallTape(canvas, tape)
        }

        // Render Live Hold-to-Snap Shape Preview or Live Stroke in Progress
        if (isHoldSnapActive && previewShape != null) {
            shapePreviewPaint.color = currentColor
            shapePreviewPaint.strokeWidth = currentStrokeWidth
            val path = previewShape!!.toAndroidPath()
            canvas.drawPath(path, shapePreviewPaint)
        } else if (currentPoints.size > 1) {
            val tempStroke = Stroke(
                tool = currentTool,
                color = currentColor,
                strokeWidth = currentStrokeWidth,
                points = currentPoints
            )
            drawSingleStroke(canvas, tempStroke)
        }

        // Render Live Tape Drag Preview
        if (currentTool == PenTool.TAPE && tapeStartPoint != null && currentPoints.isNotEmpty()) {
            val curr = currentPoints.last()
            val left = minOf(tapeStartPoint!!.x, curr.x)
            val top = minOf(tapeStartPoint!!.y, curr.y)
            val right = maxOf(tapeStartPoint!!.x, curr.x)
            val bottom = maxOf(tapeStartPoint!!.y, curr.y)

            tapeSolidPaint.color = tapeColor
            tapeSolidPaint.alpha = 150
            canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, tapeSolidPaint)
        }

        // Exit Document World Space
        canvas.restore()
    }

    private fun drawSingleStroke(canvas: Canvas, stroke: Stroke) {
        val paint = when (stroke.tool) {
            PenTool.HIGHLIGHTER -> highlighterPaint
            PenTool.PENCIL -> pencilPaint
            else -> inkPaint
        }
        paint.color = stroke.color
        paint.strokeWidth = when (stroke.tool) {
            PenTool.HIGHLIGHTER -> stroke.strokeWidth * 3.5f
            PenTool.PENCIL -> stroke.strokeWidth * 0.9f
            else -> stroke.strokeWidth
        }

        val path = StrokeEngine.createSmoothPath(stroke.points)
        canvas.drawPath(path, paint)
    }

    private fun drawActiveRecallTape(canvas: Canvas, tape: ActiveRecallTape) {
        val rect = RectF(tape.left, tape.top, tape.right, tape.bottom)
        val cornerRadius = 6f

        if (tape.isRevealed) {
            // Revealed State: Translucent fill with crisp dashed border
            tapeSolidPaint.color = tape.color
            tapeSolidPaint.alpha = 32 // ~12% opacity
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tapeSolidPaint)

            tapeBorderPaint.color = if (isDarkMode || isAmoledMode) Color.parseColor("#94A3B8") else tape.color
            tapeBorderPaint.strokeWidth = 2.5f
            tapeBorderPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tapeBorderPaint)
        } else {
            // Concealed State: Matte paper tape overlay with contrast bevel
            tapeSolidPaint.color = tape.color
            tapeSolidPaint.alpha = 255
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tapeSolidPaint)

            // Bottom bevel shadow (tactile washi tape effect)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#33000000")
            }
            val bevelRect = RectF(tape.left, tape.bottom - 2.5f, tape.right, tape.bottom)
            canvas.drawRoundRect(bevelRect, 2f, 2f, shadowPaint)

            // Contrast hairline border (sharp edges in Light, Dark, and AMOLED)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = if (isDarkMode || isAmoledMode) Color.parseColor("#475569") else Color.parseColor("#25000000")
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Forward to gesture detectors
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelHoldTimer()
                isHoldSnapActive = false
                previewShape = null
                isTransforming = false
                lastStationaryX = event.x
                lastStationaryY = event.y

                val (docX, docY) = screenToDocument(event.x, event.y)
                val pressure = if (event.pressure > 0f) event.pressure else 0.5f
                val pt = Point(docX, docY, pressure, System.currentTimeMillis())

                saveUndoState()
                currentPoints.clear()
                currentPoints.add(pt)

                if (currentTool == PenTool.TAPE) {
                    val tappedTape = currentPage.tapes.lastOrNull { it.contains(docX, docY) }
                    if (tappedTape != null) {
                        tappedTape.isRevealed = !tappedTape.isRevealed
                        invalidate()
                        return true
                    }
                    tapeStartPoint = pt
                } else if (currentTool == PenTool.ERASER_STROKE) {
                    eraseStrokeAt(docX, docY)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger touched: abort inking, enter transform mode
                isTransforming = true
                cancelHoldTimer()
                isHoldSnapActive = false
                previewShape = null

                if (currentPoints.isNotEmpty()) {
                    currentPoints.clear()
                    // Rollback provisional undo snapshot pushed on ACTION_DOWN
                    if (undoStack.isNotEmpty()) undoStack.pop()
                    invalidate()
                }
                tapeStartPoint = null
                lastFocusX = (event.getX(0) + event.getX(1)) / 2f
                lastFocusY = (event.getY(0) + event.getY(1)) / 2f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTransforming) {
                    // Two-finger pan tracking
                    if (!scaleDetector.isInProgress && pointerCount >= 2) {
                        val curFocusX = (event.getX(0) + event.getX(1)) / 2f
                        val curFocusY = (event.getY(0) + event.getY(1)) / 2f
                        if (!lastFocusX.isNaN() && !lastFocusY.isNaN()) {
                            panX += curFocusX - lastFocusX
                            panY += curFocusY - lastFocusY
                            clampPan()
                            invalidate()
                        }
                        lastFocusX = curFocusX
                        lastFocusY = curFocusY
                    }
                    return true
                }

                // Single touch: regular inking / erasing
                val (docX, docY) = screenToDocument(event.x, event.y)
                val pressure = if (event.pressure > 0f) event.pressure else 0.5f
                val pt = Point(docX, docY, pressure, System.currentTimeMillis())
                currentPoints.add(pt)

                if (currentTool == PenTool.ERASER_STROKE) {
                    eraseStrokeAt(docX, docY)
                } else if (isHoldToSnapEnabled && currentTool != PenTool.TAPE) {
                    val dist = hypot(event.x - lastStationaryX, event.y - lastStationaryY)
                    if (dist > stationaryJitterRadiusPx) {
                        // Drawing active - reset stationary anchor and hold countdown
                        lastStationaryX = event.x
                        lastStationaryY = event.y
                        cancelHoldTimer()
                        if (currentPoints.size >= 5) {
                            scheduleHoldTimer()
                        }
                    } else {
                        // Stationary hold at stroke end: keep countdown alive
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                lastFocusX = Float.NaN
                lastFocusY = Float.NaN
                // CRITICAL: remain in transforming state so the remaining finger cannot draw!
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelHoldTimer()

                if (isTransforming) {
                    isTransforming = false
                    currentPoints.clear()
                    tapeStartPoint = null
                    return true
                }

                val (docX, docY) = screenToDocument(event.x, event.y)
                val pressure = if (event.pressure > 0f) event.pressure else 0.5f
                val pt = Point(docX, docY, pressure, System.currentTimeMillis())

                if (currentTool == PenTool.TAPE && tapeStartPoint != null) {
                    val left = minOf(tapeStartPoint!!.x, pt.x)
                    val top = minOf(tapeStartPoint!!.y, pt.y)
                    val right = maxOf(tapeStartPoint!!.x, pt.x)
                    val bottom = maxOf(tapeStartPoint!!.y, pt.y)

                    if ((right - left) > 20f && (bottom - top) > 15f) {
                        currentPage.tapes.add(
                            ActiveRecallTape(
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                                color = tapeColor,
                                isRevealed = false
                            )
                        )
                    }
                    tapeStartPoint = null
                } else if (isHoldSnapActive && previewShape != null) {
                    // Hold-to-Snap committed!
                    val shapeStroke = createShapeStroke(previewShape!!)
                    currentPage.strokes.add(shapeStroke)
                } else if (currentTool == PenTool.SHAPE && currentPoints.size > 3) {
                    val shape = ShapeRecognizer.recognize(currentPoints)
                    if (shape != null) {
                        currentPage.strokes.add(createShapeStroke(shape))
                    } else if (currentPoints.size > 1) {
                        commitCurrentStroke()
                    }
                } else if (currentPoints.size > 1 && currentTool != PenTool.ERASER_STROKE) {
                    commitCurrentStroke()
                }

                isHoldSnapActive = false
                previewShape = null
                currentPoints.clear()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelHoldTimer()
                isTransforming = false
                isHoldSnapActive = false
                previewShape = null
                currentPoints.clear()
                tapeStartPoint = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleHoldTimer() {
        cancelHoldTimer()
        holdRunnable = Runnable {
            if (this@CanvasView.currentPoints.size >= 5) {
                val detected = ShapeRecognizer.recognize(this@CanvasView.currentPoints)
                if (detected != null) {
                    this@CanvasView.isHoldSnapActive = true
                    this@CanvasView.previewShape = detected
                    try {
                        this@CanvasView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    } catch (_: Exception) {}
                    this@CanvasView.invalidate()
                }
            }
        }
        holdHandler.postDelayed(holdRunnable!!, holdToSnapDurationMs)
    }

    private fun cancelHoldTimer() {
        holdRunnable?.let { holdHandler.removeCallbacks(it) }
        holdRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelHoldTimer()
    }

    private fun commitCurrentStroke() {
        val stroke = Stroke(
            tool = currentTool,
            color = currentColor,
            strokeWidth = currentStrokeWidth,
            points = currentPoints.toMutableList()
        )
        currentPage.strokes.add(stroke)
    }

    private fun createShapeStroke(shape: DetectedShape): Stroke {
        val pts = mutableListOf<Point>()
        var shapeName = "SHAPE"
        when (shape) {
            is DetectedShape.Line -> {
                pts.add(Point(shape.startX, shape.startY))
                pts.add(Point(shape.endX, shape.endY))
                shapeName = "LINE"
            }
            is DetectedShape.Rectangle -> {
                val r = shape.rect
                pts.add(Point(r.left, r.top))
                pts.add(Point(r.right, r.top))
                pts.add(Point(r.right, r.bottom))
                pts.add(Point(r.left, r.bottom))
                pts.add(Point(r.left, r.top))
                shapeName = "RECTANGLE"
            }
            is DetectedShape.RoundedRectangle -> {
                val r = shape.rect
                val cr = shape.cornerRadius
                val steps = 8
                fun addArc(cx: Float, cy: Float, startAngleDeg: Double) {
                    for (i in 0..steps) {
                        val rad = Math.toRadians(startAngleDeg + i * 90.0 / steps)
                        pts.add(Point(cx + (cr * kotlin.math.cos(rad)).toFloat(), cy + (cr * kotlin.math.sin(rad)).toFloat()))
                    }
                }
                addArc(r.right - cr, r.top + cr, 270.0)
                addArc(r.right - cr, r.bottom - cr, 0.0)
                addArc(r.left + cr, r.bottom - cr, 90.0)
                addArc(r.left + cr, r.top + cr, 180.0)
                pts.add(pts.first())
                shapeName = "ROUNDED_RECTANGLE"
            }
            is DetectedShape.Circle -> {
                val steps = 40
                for (i in 0..steps) {
                    val angle = (i * 2 * Math.PI / steps).toFloat()
                    val px = shape.centerX + shape.radius * kotlin.math.cos(angle.toDouble()).toFloat()
                    val py = shape.centerY + shape.radius * kotlin.math.sin(angle.toDouble()).toFloat()
                    pts.add(Point(px, py))
                }
                shapeName = "CIRCLE"
            }
            is DetectedShape.Ellipse -> {
                val steps = 48
                val rad = Math.toRadians(shape.rotationDegrees.toDouble())
                val cosPhi = kotlin.math.cos(rad)
                val sinPhi = kotlin.math.sin(rad)
                for (i in 0..steps) {
                    val theta = i * 2 * Math.PI / steps
                    val cosT = kotlin.math.cos(theta)
                    val sinT = kotlin.math.sin(theta)
                    val px = shape.centerX + (shape.radiusX * cosT * cosPhi - shape.radiusY * sinT * sinPhi).toFloat()
                    val py = shape.centerY + (shape.radiusX * cosT * sinPhi + shape.radiusY * sinT * cosPhi).toFloat()
                    pts.add(Point(px, py))
                }
                shapeName = "ELLIPSE"
            }
            is DetectedShape.Triangle -> {
                pts.add(shape.p1)
                pts.add(shape.p2)
                pts.add(shape.p3)
                pts.add(shape.p1)
                shapeName = "TRIANGLE_${shape.triangleType.name}"
            }
        }
        return Stroke(
            tool = currentTool,
            color = currentColor,
            strokeWidth = currentStrokeWidth,
            points = pts,
            shapeType = shapeName
        )
    }

    private fun eraseStrokeAt(x: Float, y: Float) {
        val radius = 35f
        val removed = currentPage.strokes.removeAll { stroke ->
            StrokeEngine.intersectsStroke(stroke, x, y, radius)
        }
        if (removed) {
            invalidate()
        }
    }

    fun toggleAllTapes(reveal: Boolean) {
        saveUndoState()
        currentPage.tapes.forEach { it.isRevealed = reveal }
        invalidate()
    }

    fun clearCanvas() {
        saveUndoState()
        currentPage.strokes.clear()
        currentPage.tapes.clear()
        invalidate()
    }

    private fun saveUndoState() {
        val snapshot = PageStateSnapshot(
            strokes = currentPage.strokes.map { it.copy(points = it.points.toMutableList()) },
            tapes = currentPage.tapes.map { it.copy() }
        )
        undoStack.push(snapshot)
        if (undoStack.size > 40) undoStack.removeLast()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentSnapshot = PageStateSnapshot(
                strokes = currentPage.strokes.map { it.copy(points = it.points.toMutableList()) },
                tapes = currentPage.tapes.map { it.copy() }
            )
            redoStack.push(currentSnapshot)

            val prev = undoStack.pop()
            currentPage.strokes.clear()
            currentPage.strokes.addAll(prev.strokes)
            currentPage.tapes.clear()
            currentPage.tapes.addAll(prev.tapes)
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentSnapshot = PageStateSnapshot(
                strokes = currentPage.strokes.map { it.copy(points = it.points.toMutableList()) },
                tapes = currentPage.tapes.map { it.copy() }
            )
            undoStack.push(currentSnapshot)

            val next = redoStack.pop()
            currentPage.strokes.clear()
            currentPage.strokes.addAll(next.strokes)
            currentPage.tapes.clear()
            currentPage.tapes.addAll(next.tapes)
            invalidate()
        }
    }

    /**
     * Resets viewport to 100% (1.0x) and centers document with animation.
     */
    fun resetZoomAndPan(animated: Boolean = true) {
        if (!animated) {
            scaleFactor = 1.0f
            panX = 0f
            panY = 0f
            clampPan()
            invalidate()
            onScaleChangedListener?.invoke(scaleFactor)
            return
        }

        val startScale = scaleFactor
        val startPanX = panX
        val startPanY = panY

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                this@CanvasView.scaleFactor = startScale + (1.0f - startScale) * f
                this@CanvasView.panX = startPanX + (0f - startPanX) * f
                this@CanvasView.panY = startPanY + (0f - startPanY) * f
                this@CanvasView.invalidate()
                this@CanvasView.onScaleChangedListener?.invoke(this@CanvasView.scaleFactor)
            }
            start()
        }
    }

    /**
     * Smoothly zooms to target scale centered on screen coordinates (focusX, focusY).
     */
    fun zoomTo(targetScale: Float, focusX: Float, focusY: Float, animated: Boolean = true) {
        val clampedTarget = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val targetPanX = focusX - (focusX - panX) * (clampedTarget / scaleFactor)
        val targetPanY = focusY - (focusY - panY) * (clampedTarget / scaleFactor)

        if (!animated) {
            scaleFactor = clampedTarget
            panX = targetPanX
            panY = targetPanY
            clampPan()
            invalidate()
            onScaleChangedListener?.invoke(scaleFactor)
            return
        }

        val startScale = scaleFactor
        val startPanX = panX
        val startPanY = panY

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                this@CanvasView.scaleFactor = startScale + (clampedTarget - startScale) * f
                this@CanvasView.panX = startPanX + (targetPanX - startPanX) * f
                this@CanvasView.panY = startPanY + (targetPanY - startPanY) * f
                this@CanvasView.clampPan()
                this@CanvasView.invalidate()
                this@CanvasView.onScaleChangedListener?.invoke(this@CanvasView.scaleFactor)
            }
            start()
        }
    }

    data class PageStateSnapshot(
        val strokes: List<Stroke>,
        val tapes: List<ActiveRecallTape>
    )
}
