package com.flownotes.app.model

import com.flownotes.app.data.NoteRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit Test Specification: Model Serialization & JSON Fidelity
 * Verifies 100% round-trip serialization between FlowNotes data models and JSON representations.
 */
class ModelSerializationTest {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "flownotes_model_test").apply { mkdirs() }
    private val repository = NoteRepository(tempDir)

    @Test
    fun testNotebookFullRoundTripSerialization() {
        val original = Notebook(
            title = "Advanced Organic Chemistry",
            coverColor = 0xFF8B5CF6.toInt(),
            coverPattern = "vintage",
            folder = "Chemistry 101",
            defaultPaperType = PaperType.CORNELL
        )

        // Page 1: Cornell layout with text, tape, stroke, and sticky note
        val page1 = Page(pageNumber = 1, paperType = PaperType.CORNELL).apply {
            textAnnotations.add(
                TextAnnotation(
                    x = 120.5f,
                    y = 150.0f,
                    text = "Electrophilic Addition Mechanism: C=C + H-Br -> C-C-Br + H",
                    color = 0xFF1E293B.toInt(),
                    fontSize = 18.0f
                )
            )
            // Active Recall Tape concealing reaction product
            tapes.add(
                ActiveRecallTape(
                    left = 320.0f,
                    top = 145.0f,
                    right = 480.0f,
                    bottom = 175.0f,
                    color = 0xFFFEF08A.toInt(),
                    isRevealed = false
                )
            )
            // Ballpoint ink stroke with pressure and timestamp
            val stroke1 = Stroke(
                tool = PenTool.BALLPOINT,
                color = 0xFF0000FF.toInt(),
                strokeWidth = 2.5f
            ).apply {
                points.add(Point(x = 10f, y = 20f, pressure = 0.4f, timestamp = 1000L))
                points.add(Point(x = 15f, y = 25f, pressure = 0.6f, timestamp = 1016L))
                points.add(Point(x = 22f, y = 31f, pressure = 0.8f, timestamp = 1032L))
            }
            strokes.add(stroke1)

            // Geometric snapped rectangle
            val shapeStroke = Stroke(
                tool = PenTool.SHAPE,
                color = 0xFFDC2626.toInt(),
                strokeWidth = 3.0f,
                shapeType = "RECTANGLE"
            ).apply {
                points.add(Point(x = 100f, y = 100f))
                points.add(Point(x = 300f, y = 250f))
            }
            strokes.add(shapeStroke)

            // Sticky Note
            stickyNotes.add(
                StickyNote(
                    x = 600f,
                    y = 200f,
                    width = 210f,
                    height = 170f,
                    content = "Markovnikov rule: H attaches to carbon with more H atoms",
                    color = 0xFFBAE6FD.toInt()
                )
            )
        }

        // Page 2: Grid paper with high-yield Flashcards
        val page2 = Page(pageNumber = 2, paperType = PaperType.GRID)

        original.pages.add(page1)
        original.pages.add(page2)

        // Spaced Repetition Flashcards
        original.flashcards.add(
            Flashcard(
                front = "State Markovnikov's Rule",
                back = "In addition of HX to alkene, H adds to carbon with greater number of hydrogens.",
                hint = "Hydrogen richness",
                reviewIntervalDays = 3,
                repetitions = 2,
                easeFactor = 2.6f
            )
        )

        // 1. Serialize
        val jsonObj = repository.serializeNotebook(original)
        assertNotNull(jsonObj)
        assertEquals("Advanced Organic Chemistry", jsonObj.getString("title"))
        assertEquals("Chemistry 101", jsonObj.getString("folder"))
        assertEquals(2, jsonObj.getJSONArray("pages").length())
        assertEquals(1, jsonObj.getJSONArray("flashcards").length())

        // 2. Deserialize
        val deserialized = repository.parseNotebookJson(jsonObj)
        assertNotNull(deserialized)
        assertEquals(original.id, deserialized.id)
        assertEquals(original.title, deserialized.title)
        assertEquals(original.coverColor, deserialized.coverColor)
        assertEquals(original.coverPattern, deserialized.coverPattern)
        assertEquals(original.folder, deserialized.folder)
        assertEquals(original.defaultPaperType, deserialized.defaultPaperType)
        assertEquals(2, deserialized.pages.size)

        // Verify Page 1 elements
        val dPage1 = deserialized.pages[0]
        assertEquals(PaperType.CORNELL, dPage1.paperType)
        assertEquals(1, dPage1.textAnnotations.size)
        assertEquals("Electrophilic Addition Mechanism: C=C + H-Br -> C-C-Br + H", dPage1.textAnnotations[0].text)
        assertEquals(120.5f, dPage1.textAnnotations[0].x, 0.01f)

        // Verify Tape
        assertEquals(1, dPage1.tapes.size)
        val dTape = dPage1.tapes[0]
        assertEquals(320.0f, dTape.left, 0.01f)
        assertEquals(145.0f, dTape.top, 0.01f)
        assertEquals(480.0f, dTape.right, 0.01f)
        assertEquals(175.0f, dTape.bottom, 0.01f)
        assertFalse(dTape.isRevealed)
        assertTrue(dTape.contains(350f, 160f))
        assertFalse(dTape.contains(100f, 100f))

        // Verify Strokes
        assertEquals(2, dPage1.strokes.size)
        val dStroke1 = dPage1.strokes[0]
        assertEquals(PenTool.BALLPOINT, dStroke1.tool)
        assertEquals(3, dStroke1.points.size)
        assertEquals(0.6f, dStroke1.points[1].pressure, 0.01f)
        assertEquals(1016L, dStroke1.points[1].timestamp)

        val dShape = dPage1.strokes[1]
        assertEquals("RECTANGLE", dShape.shapeType)

        // Verify Sticky Note
        assertEquals(1, dPage1.stickyNotes.size)
        val dSticky = dPage1.stickyNotes[0]
        assertEquals(600f, dSticky.x, 0.01f)
        assertEquals(210f, dSticky.width, 0.01f)
        assertEquals("Markovnikov rule: H attaches to carbon with more H atoms", dSticky.content)

        // Verify Flashcard SRS fields
        assertEquals(1, deserialized.flashcards.size)
        val dCard = deserialized.flashcards[0]
        assertEquals("State Markovnikov's Rule", dCard.front)
        assertEquals(3, dCard.reviewIntervalDays)
        assertEquals(2, dCard.repetitions)
        assertEquals(2.6f, dCard.easeFactor, 0.01f)
    }

    @Test
    fun testWebCompanionTapeFormatInteroperability() {
        // Simulates JSON created by canvas.js web companion using x, y, w, h
        val webJson = JSONObject().apply {
            put("id", "web-nb-1")
            put("title", "Web Generated Note")
            put("coverColor", "#3b82f6") // Web hex color
            put("defaultPaperType", "GRID")
            val pages = org.json.JSONArray().apply {
                val p1 = JSONObject().apply {
                    put("id", "web-page-1")
                    put("pageNumber", 1)
                    put("paperType", "GRID")
                    val tapes = org.json.JSONArray().apply {
                        val t1 = JSONObject().apply {
                            put("id", "tape-web-1")
                            put("x", 150.0)
                            put("y", 220.0)
                            put("w", 200.0)
                            put("h", 40.0)
                            put("color", "#fef08a")
                            put("revealed", true)
                        }
                        put(t1)
                    }
                    put("tapes", tapes)
                }
                put(p1)
            }
            put("pages", pages)
        }

        val deserialized = repository.parseNotebookJson(webJson)
        assertEquals("Web Generated Note", deserialized.title)
        assertEquals(PaperType.GRID, deserialized.defaultPaperType)
        assertEquals(1, deserialized.pages.size)

        val page = deserialized.pages[0]
        assertEquals(1, page.tapes.size)
        val tape = page.tapes[0]
        assertEquals(150.0f, tape.left, 0.01f)
        assertEquals(220.0f, tape.top, 0.01f)
        assertEquals(350.0f, tape.right, 0.01f) // left + w
        assertEquals(260.0f, tape.bottom, 0.01f) // top + h
        assertTrue(tape.isRevealed)
    }

    @Test
    fun testMissingOptionalFieldsResilience() {
        // Minimal JSON with stripped fields
        val minimalJson = JSONObject().apply {
            put("title", "Sparse Note")
        }

        val deserialized = repository.parseNotebookJson(minimalJson)
        assertNotNull(deserialized.id)
        assertTrue(deserialized.id.isNotEmpty())
        assertEquals("Sparse Note", deserialized.title)
        assertEquals(PaperType.RULED, deserialized.defaultPaperType)
        assertNull(deserialized.pdfPath)
        // Auto-generates 1 initial blank page if pages array is omitted
        assertEquals(1, deserialized.pages.size)
        assertEquals(PaperType.RULED, deserialized.pages[0].paperType)
        assertTrue(deserialized.pages[0].strokes.isEmpty())
        assertTrue(deserialized.pages[0].tapes.isEmpty())
        assertTrue(deserialized.pages[0].stickyNotes.isEmpty())
    }

    @Test
    fun testUnicodeAndMathematicalFormulaPreservation() {
        val nb = Notebook(title = "Quantum Physics ⚛️ 物理学")
        val page = Page(pageNumber = 1)
        page.textAnnotations.add(
            TextAnnotation(
                x = 50f,
                y = 100f,
                text = "Schrödinger: iħ ∂Ψ/∂t = ĤΨ; Einstein: E = mc² ± √0"
            )
        )
        nb.pages.add(page)

        val json = repository.serializeNotebook(nb)
        val recovered = repository.parseNotebookJson(json)

        assertEquals("Quantum Physics ⚛️ 物理学", recovered.title)
        assertEquals("Schrödinger: iħ ∂Ψ/∂t = ĤΨ; Einstein: E = mc² ± √0", recovered.pages[0].textAnnotations[0].text)
    }
}
