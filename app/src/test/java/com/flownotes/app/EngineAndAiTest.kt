package com.flownotes.app

import com.flownotes.app.ai.LocalAiStudyEngine
import com.flownotes.app.engine.DetectedShape
import com.flownotes.app.engine.ShapeRecognizer
import com.flownotes.app.engine.StrokeEngine
import com.flownotes.app.model.*
import org.junit.Assert.*
import org.junit.Test

class EngineAndAiTest {

    @Test
    fun testActiveRecallTapeHitDetection() {
        val tape = ActiveRecallTape(
            left = 100f,
            top = 100f,
            right = 300f,
            bottom = 150f,
            isRevealed = false
        )

        // Inside tape bounds
        assertTrue(tape.contains(150f, 120f))
        assertTrue(tape.contains(100f, 100f))
        assertTrue(tape.contains(300f, 150f))

        // Outside tape bounds
        assertFalse(tape.contains(50f, 120f))
        assertFalse(tape.contains(350f, 120f))
        assertFalse(tape.contains(150f, 80f))
        assertFalse(tape.contains(150f, 200f))

        // Test toggle reveal
        tape.isRevealed = !tape.isRevealed
        assertTrue(tape.isRevealed)
    }

    @Test
    fun testStrokeIntersection() {
        val stroke = Stroke(
            points = mutableListOf(
                Point(10f, 10f),
                Point(20f, 20f),
                Point(30f, 30f)
            )
        )

        // Point near stroke
        assertTrue(StrokeEngine.intersectsStroke(stroke, 21f, 21f, radius = 5f))

        // Point far from stroke
        assertFalse(StrokeEngine.intersectsStroke(stroke, 100f, 100f, radius = 5f))
    }

    @Test
    fun testStraightLineShapeRecognition() {
        // Line from (10, 10) to (200, 10)
        val points = mutableListOf<Point>()
        for (x in 10..200 step 10) {
            points.add(Point(x.toFloat(), 10f))
        }

        val shape = ShapeRecognizer.recognize(points)
        assertNotNull(shape)
        assertTrue(shape is DetectedShape.Line)
    }

    @Test
    fun testOnDeviceAiSummarization() {
        val title = "Thermodynamics"
        val rawText = "The first law of thermodynamics is conservation of energy. Heat added equals change in internal energy plus work done. Entropy always increases in irreversible processes."

        val summary = LocalAiStudyEngine.generateLocalSummary(title, rawText, pageCount = 2)
        assertNotNull(summary)
        assertTrue(summary.contains("Instant Study Summary"))
        assertTrue(summary.contains(title))
    }

    @Test
    fun testOnDeviceFlashcardGeneration() {
        val notebook = Notebook(
            title = "Biology Cells",
            pages = mutableListOf(
                Page(
                    pageNumber = 1,
                    textAnnotations = mutableListOf(
                        TextAnnotation(
                            x = 50f,
                            y = 50f,
                            text = "Mitochondria is the powerhouse of the cell."
                        )
                    )
                )
            )
        )

        val cards = LocalAiStudyEngine.generateLocalFlashcards(notebook)
        assertTrue(cards.isNotEmpty())
        assertTrue(cards.any { it.front.contains("Mitochondria", ignoreCase = true) || it.back.contains("powerhouse", ignoreCase = true) })
    }
}
