package com.flownotes.app.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.flownotes.app.model.PaperType

/**
 * Procedural paper background renderer for Android Canvas.
 * Generates Cornell notes layout, 5mm grid, dot grid, ruled lines, and weekly planner.
 */
object PaperTemplateRenderer {

    fun drawPaper(canvas: Canvas, width: Float, height: Float, type: PaperType, isDark: Boolean = false) {
        when (type) {
            PaperType.BLANK -> drawBlank(canvas, width, height, isDark)
            PaperType.RULED -> drawRuled(canvas, width, height, isDark, lineSpacing = 70f, showMargin = true)
            PaperType.COLLEGE_RULED -> drawRuled(canvas, width, height, isDark, lineSpacing = 52f, showMargin = true)
            PaperType.GRID -> drawGrid(canvas, width, height, isDark, gridSize = 50f)
            PaperType.DOTTED -> drawDotted(canvas, width, height, isDark, spacing = 50f)
            PaperType.CORNELL -> drawCornell(canvas, width, height, isDark)
            PaperType.PLANNER -> drawPlanner(canvas, width, height, isDark)
            PaperType.DARK_GRID -> drawGrid(canvas, width, height, isDark = true, gridSize = 55f)
            PaperType.SEPIA -> drawSepia(canvas, width, height)
        }
    }

    private fun drawBlank(canvas: Canvas, width: Float, height: Float, isDark: Boolean) {
        val bgPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#1A202C") else Color.WHITE
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)
    }

    private fun drawRuled(
        canvas: Canvas,
        width: Float,
        height: Float,
        isDark: Boolean,
        lineSpacing: Float,
        showMargin: Boolean
    ) {
        drawBlank(canvas, width, height, isDark)

        val linePaint = Paint().apply {
            color = if (isDark) Color.parseColor("#2D3748") else Color.parseColor("#E2E8F0")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        var y = 140f
        while (y < height - 60f) {
            canvas.drawLine(60f, y, width - 60f, y, linePaint)
            y += lineSpacing
        }

        if (showMargin) {
            val marginPaint = Paint().apply {
                color = if (isDark) Color.parseColor("#E53E3E") else Color.parseColor("#FCA5A5")
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }
            canvas.drawLine(180f, 60f, 180f, height - 60f, marginPaint)
        }
    }

    private fun drawGrid(canvas: Canvas, width: Float, height: Float, isDark: Boolean, gridSize: Float) {
        drawBlank(canvas, width, height, isDark)

        val gridPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#2D3748") else Color.parseColor("#EDF2F7")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        var x = 60f
        while (x < width - 60f) {
            canvas.drawLine(x, 60f, x, height - 60f, gridPaint)
            x += gridSize
        }

        var y = 60f
        while (y < height - 60f) {
            canvas.drawLine(60f, y, width - 60f, y, gridPaint)
            y += gridSize
        }
    }

    private fun drawDotted(canvas: Canvas, width: Float, height: Float, isDark: Boolean, spacing: Float) {
        drawBlank(canvas, width, height, isDark)

        val dotPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#4A5568") else Color.parseColor("#CBD5E1")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var x = 60f
        while (x < width - 60f) {
            var y = 60f
            while (y < height - 60f) {
                canvas.drawCircle(x, y, 2.5f, dotPaint)
                y += spacing
            }
            x += spacing
        }
    }

    private fun drawCornell(canvas: Canvas, width: Float, height: Float, isDark: Boolean) {
        drawBlank(canvas, width, height, isDark)

        val dividerPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#4A5568") else Color.parseColor("#94A3B8")
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#718096") else Color.parseColor("#94A3B8")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val topHeaderY = 160f
        val summaryY = height - 260f
        val cueColumnX = width * 0.32f

        // Draw boundaries
        canvas.drawLine(60f, topHeaderY, width - 60f, topHeaderY, dividerPaint)
        canvas.drawLine(cueColumnX, topHeaderY, cueColumnX, summaryY, dividerPaint)
        canvas.drawLine(60f, summaryY, width - 60f, summaryY, dividerPaint)

        // Labels
        canvas.drawText("DATE & TOPIC", 70f, 110f, textPaint)
        canvas.drawText("CUES / QUESTIONS", 70f, topHeaderY + 45f, textPaint)
        canvas.drawText("NOTES & LECTURE OUTLINE", cueColumnX + 24f, topHeaderY + 45f, textPaint)
        canvas.drawText("SUMMARY & REFLECTION", 70f, summaryY + 50f, textPaint)

        // Ruled lines in Notes Area
        val linePaint = Paint().apply {
            color = if (isDark) Color.parseColor("#2D3748") else Color.parseColor("#EDF2F7")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        var y = topHeaderY + 80f
        while (y < summaryY - 30f) {
            canvas.drawLine(cueColumnX + 15f, y, width - 60f, y, linePaint)
            y += 50f
        }

        // Ruled lines in Summary Area
        y = summaryY + 95f
        while (y < height - 60f) {
            canvas.drawLine(60f, y, width - 60f, y, linePaint)
            y += 45f
        }
    }

    private fun drawPlanner(canvas: Canvas, width: Float, height: Float, isDark: Boolean) {
        drawBlank(canvas, width, height, isDark)

        val borderPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#4A5568") else Color.parseColor("#CBD5E1")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        val titlePaint = Paint().apply {
            color = if (isDark) Color.WHITE else Color.parseColor("#1E293B")
            textSize = 34f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("WEEKLY STUDY PLANNER", 70f, 100f, titlePaint)

        val days = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "WEEKEND REVIEW")
        val colWidth = (width - 160f) / 2f
        val rowHeight = (height - 200f) / 3f

        val dayPaint = Paint().apply {
            color = if (isDark) Color.parseColor("#A0AEC0") else Color.parseColor("#64748B")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }

        days.forEachIndexed { i, day ->
            val col = i % 2
            val row = i / 2
            val left = 70f + col * (colWidth + 20f)
            val top = 140f + row * (rowHeight + 20f)

            canvas.drawRect(left, top, left + colWidth, top + rowHeight, borderPaint)
            canvas.drawText(day, left + 20f, top + 40f, dayPaint)
        }
    }

    private fun drawSepia(canvas: Canvas, width: Float, height: Float) {
        val bgPaint = Paint().apply {
            color = Color.parseColor("#FDF6E2")
        }
        canvas.drawRect(0f, 0f, width, height, bgPaint)
        drawRuled(canvas, width, height, isDark = false, lineSpacing = 65f, showMargin = true)
    }
}
