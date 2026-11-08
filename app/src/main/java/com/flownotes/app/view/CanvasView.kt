package com.flownotes.app.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.flownotes.app.engine.DetectedShape
import com.flownotes.app.engine.ShapeRecognizer
import com.flownotes.app.engine.StrokeEngine
import com.flownotes.app.model.*
import com.flownotes.app.model.Point
import java.util.*

/**
 * Custom High-Performance Digital Ink Canvas View for Android.
 * Features stylus pressure sensitivity, Active Recall Tape, stroke eraser, and shape snapping.
 */
class CanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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

    var pdfPageBitmap: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    var currentTool: PenTool = PenTool.BALLPOINT
    var currentColor: Int = Color.parseColor("#1E293B")
    var currentStrokeWidth: Float = 5.0f
    var tapeColor: Int = Color.parseColor("#FEF08A")

    // Undo / Redo stacks
    private val undoStack = ArrayDeque<PageStateSnapshot>()
    private val redoStack = ArrayDeque<PageStateSnapshot>()

    // Current live points
    private val currentPoints = mutableListOf<Point>()
    private var tapeStartPoint: Point? = null

    // Pre-allocated Paints
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val highlighterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
        strokeJoin = Paint.Join.BEVEL
        alpha = 110 // Semi-transparent
    }

    private val tapeSolidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tapeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw Paper Template or PDF page
        if (pdfPageBitmap != null) {
            val destRect = RectF(0f, 0f, w, h)
            canvas.drawBitmap(pdfPageBitmap!!, null, destRect, null)
        } else {
            PaperTemplateRenderer.drawPaper(canvas, w, h, currentPage.paperType, isDarkMode)
        }

        // 2. Draw Committed Strokes
        for (stroke in currentPage.strokes) {
            drawSingleStroke(canvas, stroke)
        }

        // 3. Draw Active Recall Tapes
        for (tape in currentPage.tapes) {
            drawActiveRecallTape(canvas, tape)
        }

        // 4. Draw Current Live Stroke in Progress
        if (currentPoints.size > 1) {
            val tempStroke = Stroke(
                tool = currentTool,
                color = currentColor,
                strokeWidth = currentStrokeWidth,
                points = currentPoints
            )
            drawSingleStroke(canvas, tempStroke)
        }

        // 5. Draw Live Tape Drag Preview
        if (currentTool == PenTool.TAPE && tapeStartPoint != null && currentPoints.isNotEmpty()) {
            val curr = currentPoints.last()
            val left = minOf(tapeStartPoint!!.x, curr.x)
            val top = minOf(tapeStartPoint!!.y, curr.y)
            val right = maxOf(tapeStartPoint!!.x, curr.x)
            val bottom = maxOf(tapeStartPoint!!.y, curr.y)

            tapeSolidPaint.color = tapeColor
            tapeSolidPaint.alpha = 160
            canvas.drawRect(left, top, right, bottom, tapeSolidPaint)
        }
    }

    private fun drawSingleStroke(canvas: Canvas, stroke: Stroke) {
        val paint = if (stroke.tool == PenTool.HIGHLIGHTER) highlighterPaint else inkPaint
        paint.color = stroke.color
        paint.strokeWidth = if (stroke.tool == PenTool.HIGHLIGHTER) stroke.strokeWidth * 3.5f else stroke.strokeWidth

        val path = StrokeEngine.createSmoothPath(stroke.points)
        canvas.drawPath(path, paint)
    }

    private fun drawActiveRecallTape(canvas: Canvas, tape: ActiveRecallTape) {
        if (tape.isRevealed) {
            // Revealed state: dashed outline and subtle highlight
            tapeBorderPaint.color = tape.color
            canvas.drawRect(tape.left, tape.top, tape.right, tape.bottom, tapeBorderPaint)
            tapeSolidPaint.color = tape.color
            tapeSolidPaint.alpha = 30
            canvas.drawRect(tape.left, tape.top, tape.right, tape.bottom, tapeSolidPaint)
        } else {
            // Concealed state: Solid aesthetic study tape covering the text
            tapeSolidPaint.color = tape.color
            tapeSolidPaint.alpha = 255
            canvas.drawRect(tape.left, tape.top, tape.right, tape.bottom, tapeSolidPaint)

            // Inner subtle border
            val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#40000000")
            }
            canvas.drawRect(tape.left + 2f, tape.top + 2f, tape.right - 2f, tape.bottom - 2f, edgePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val pressure = if (event.pressure > 0) event.pressure else 0.5f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                saveUndoState()
                val pt = Point(x, y, pressure)
                currentPoints.clear()
                currentPoints.add(pt)

                if (currentTool == PenTool.TAPE) {
                    // Check if tapping an existing tape to toggle reveal/conceal
                    val tappedTape = currentPage.tapes.lastOrNull { it.contains(x, y) }
                    if (tappedTape != null) {
                        tappedTape.isRevealed = !tappedTape.isRevealed
                        invalidate()
                        return true
                    }
                    tapeStartPoint = pt
                } else if (currentTool == PenTool.ERASER_STROKE) {
                    eraseStrokeAt(x, y)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val pt = Point(x, y, pressure)
                currentPoints.add(pt)

                if (currentTool == PenTool.ERASER_STROKE) {
                    eraseStrokeAt(x, y)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val pt = Point(x, y, pressure)

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
                } else if (currentTool == PenTool.SHAPE && currentPoints.size > 3) {
                    val shape = ShapeRecognizer.recognize(currentPoints)
                    if (shape != null) {
                        val shapeStroke = createShapeStroke(shape)
                        currentPage.strokes.add(shapeStroke)
                    } else if (currentPoints.size > 1) {
                        commitCurrentStroke()
                    }
                } else if (currentPoints.size > 1 && currentTool != PenTool.ERASER_STROKE) {
                    commitCurrentStroke()
                }

                currentPoints.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
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
        when (shape) {
            is DetectedShape.Line -> {
                pts.add(Point(shape.startX, shape.startY))
                pts.add(Point(shape.endX, shape.endY))
            }
            is DetectedShape.Rectangle -> {
                val r = shape.rect
                pts.add(Point(r.left, r.top))
                pts.add(Point(r.right, r.top))
                pts.add(Point(r.right, r.bottom))
                pts.add(Point(r.left, r.bottom))
                pts.add(Point(r.left, r.top))
            }
            is DetectedShape.Circle -> {
                val steps = 36
                for (i in 0..steps) {
                    val angle = (i * 2 * Math.PI / steps).toFloat()
                    val px = shape.centerX + shape.radius * Math.cos(angle.toDouble()).toFloat()
                    val py = shape.centerY + shape.radius * Math.sin(angle.toDouble()).toFloat()
                    pts.add(Point(px, py))
                }
            }
            is DetectedShape.Triangle -> {
                pts.add(shape.p1)
                pts.add(shape.p2)
                pts.add(shape.p3)
                pts.add(shape.p1)
            }
        }
        return Stroke(
            tool = PenTool.SHAPE,
            color = currentColor,
            strokeWidth = currentStrokeWidth,
            points = pts
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

    data class PageStateSnapshot(
        val strokes: List<Stroke>,
        val tapes: List<ActiveRecallTape>
    )
}
