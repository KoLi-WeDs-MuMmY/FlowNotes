package com.flownotes.app.model

import java.io.Serializable
import java.util.UUID

/**
 * Data models for FlowNotes (Freenotes Replica for Android)
 */

enum class PenTool {
    BALLPOINT,
    FOUNTAIN,
    HIGHLIGHTER,
    PENCIL,
    ERASER_STROKE,
    ERASER_PIXEL,
    TAPE,
    SHAPE,
    LASSO,
    LASER
}

enum class PaperType {
    BLANK,
    RULED,
    COLLEGE_RULED,
    GRID,
    DOTTED,
    CORNELL,
    PLANNER,
    DARK_GRID,
    SEPIA
}

data class Point(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val tool: PenTool = PenTool.BALLPOINT,
    val color: Int = 0xFF1E293B.toInt(),
    val strokeWidth: Float = 3.0f,
    val points: MutableList<Point> = mutableListOf(),
    val shapeType: String? = null // "LINE", "RECTANGLE", "CIRCLE", etc.
) : Serializable

data class ActiveRecallTape(
    val id: String = UUID.randomUUID().toString(),
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
    val color: Int = 0xFFFEF08A.toInt(), // Soft pastel yellow tape
    var isRevealed: Boolean = false
) : Serializable {
    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }
}

data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var text: String,
    val color: Int = 0xFF1E293B.toInt(),
    val fontSize: Float = 16f
) : Serializable

data class StickyNote(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var width: Float = 200f,
    var height: Float = 180f,
    var content: String = "",
    val color: Int = 0xFFFEF08A.toInt()
) : Serializable

data class Page(
    val id: String = UUID.randomUUID().toString(),
    val pageNumber: Int,
    var paperType: PaperType = PaperType.RULED,
    val strokes: MutableList<Stroke> = mutableListOf(),
    val tapes: MutableList<ActiveRecallTape> = mutableListOf(),
    val textAnnotations: MutableList<TextAnnotation> = mutableListOf(),
    val stickyNotes: MutableList<StickyNote> = mutableListOf(),
    var pdfPageIndex: Int? = null,
    var audioMemoUri: String? = null
) : Serializable

data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Untitled Notebook",
    var coverColor: Int = 0xFF3B82F6.toInt(),
    var coverPattern: String = "modern",
    var folder: String = "All Notes",
    var defaultPaperType: PaperType = PaperType.RULED,
    val pages: MutableList<Page> = mutableListOf(),
    var pdfPath: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Serializable {
    val pageCount: Int
        get() = pages.size
}

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val front: String,
    val back: String,
    val hint: String = "",
    var reviewIntervalDays: Int = 1,
    var repetitions: Int = 0,
    var easeFactor: Float = 2.5f
) : Serializable
