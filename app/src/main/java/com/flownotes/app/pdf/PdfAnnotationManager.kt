package com.flownotes.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.flownotes.app.engine.StrokeEngine
import com.flownotes.app.model.Notebook
import com.flownotes.app.model.Page
import com.flownotes.app.model.PenTool
import com.flownotes.app.view.PaperTemplateRenderer
import java.io.File
import java.io.FileOutputStream

/**
 * Native Android PDF Manager.
 * Handles rendering PDF pages into high-res bitmaps for annotation and exporting notebooks to PDF.
 */
class PdfAnnotationManager(private val context: Context) {

    private var renderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    fun openPdf(pdfFile: File): Int {
        close()
        fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(fileDescriptor!!)
        return renderer?.pageCount ?: 0
    }

    fun renderPageToBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        val r = renderer ?: return null
        if (pageIndex < 0 || pageIndex >= r.pageCount) return null

        val page = r.openPage(pageIndex)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
    }

    fun exportNotebookToPdf(notebook: Notebook, outputFile: File, width: Int = 1200, height: Int = 1600): Boolean {
        val document = PdfDocument()

        try {
            notebook.pages.forEachIndexed { index, page ->
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                val pdfPage = document.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // 1. Draw paper background or underlying PDF
                PaperTemplateRenderer.drawPaper(canvas, width.toFloat(), height.toFloat(), page.paperType)

                // 2. Draw ink strokes
                val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }

                for (stroke in page.strokes) {
                    inkPaint.color = stroke.color
                    inkPaint.strokeWidth = if (stroke.tool == PenTool.HIGHLIGHTER) stroke.strokeWidth * 3.5f else stroke.strokeWidth
                    if (stroke.tool == PenTool.HIGHLIGHTER) {
                        inkPaint.alpha = 110
                    } else {
                        inkPaint.alpha = 255
                    }
                    val path = StrokeEngine.createSmoothPath(stroke.points)
                    canvas.drawPath(path, inkPaint)
                }

                // 3. Draw active recall tapes (in concealed state for study prints or revealed)
                val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                for (tape in page.tapes) {
                    tapePaint.color = tape.color
                    tapePaint.alpha = if (tape.isRevealed) 40 else 255
                    canvas.drawRect(tape.left, tape.top, tape.right, tape.bottom, tapePaint)
                }

                document.finishPage(pdfPage)
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            document.close()
        }
    }

    fun close() {
        renderer?.close()
        renderer = null
        fileDescriptor?.close()
        fileDescriptor = null
    }
}
