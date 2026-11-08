package com.flownotes.app.data

import com.flownotes.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Adversarial Stress & Chaos Test Suite for NoteRepository & Models.kt.
 *
 * Rigorously stress-tests:
 * 1. Extreme scale notebooks (100+ pages, 15,000+ points).
 * 2. Malformed JSON recovery, corruption isolation, and zero-byte/truncated files.
 * 3. Full Unicode, multi-lingual scripts, emojis, and mathematical/scientific notation.
 * 4. Missing fields, mismatched types, unknown enums, and schema resilience.
 * 5. Sudden process termination simulations (orphaned .tmp files, interrupted atomic renames).
 * 6. Rapid successive atomic saves and numerical boundary conditions.
 */
class NoteRepositoryStressTest {

    private lateinit var testDir: File
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        val candidates = listOf(
            File(System.getProperty("java.io.tmpdir") ?: "/tmp"),
            File("/data/user/0/com.agy/files/usr/tmp"),
            File("build/test_tmp")
        )
        val valid = candidates.firstOrNull { runCatching { it.mkdirs(); it.canWrite() }.getOrDefault(false) } ?: File("build/test_tmp")
        testDir = File(valid, "flownotes_stress_test_${UUID.randomUUID()}").apply {
            mkdirs()
        }
        repository = NoteRepository(testDir)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    /**
     * TEST 1: Large Notebook Scale Stress Test (105 pages, 15,750 points)
     * Verifies persistence fidelity, memory stability, and throughput under heavy load.
     */
    @Test
    fun testLargeNotebookScaleAndPerformance() {
        val largeNotebook = Notebook(
            title = "Massive Comprehensive Organic & Quantum Chemistry Compendium",
            folder = "Compendiums",
            defaultPaperType = PaperType.CORNELL
        )

        val totalPages = 105
        val pointsPerPage = 150
        var totalPointsCreated = 0

        for (p in 1..totalPages) {
            val page = Page(
                pageNumber = p,
                paperType = if (p % 2 == 0) PaperType.GRID else PaperType.CORNELL
            )

            // Add text annotation per page
            page.textAnnotations.add(
                TextAnnotation(
                    x = 100f,
                    y = 120f,
                    text = "Page $p: Quantum State Vector |ψ⟩ = c₀|0⟩ + c₁|1⟩"
                )
            )

            // Add tape per page
            page.tapes.add(
                ActiveRecallTape(
                    left = 200f + (p % 10),
                    top = 300f,
                    right = 450f,
                    bottom = 340f,
                    color = 0xFFFEF08A.toInt(),
                    isRevealed = (p % 3 == 0)
                )
            )

            // Add sticky note per page
            page.stickyNotes.add(
                StickyNote(
                    x = 600f,
                    y = 200f,
                    content = "Study Tip for Page $p: Review before exam",
                    color = 0xFFBAE6FD.toInt()
                )
            )

            // Add strokes with points
            val stroke = Stroke(
                tool = if (p % 2 == 0) PenTool.FOUNTAIN else PenTool.BALLPOINT,
                color = 0xFF1E293B.toInt(),
                strokeWidth = 2.5f
            )
            for (ptIdx in 0 until pointsPerPage) {
                stroke.points.add(
                    Point(
                        x = (ptIdx * 2.5f) % 1000f,
                        y = 400f + (ptIdx * 1.5f) % 800f,
                        pressure = 0.3f + ((ptIdx % 7) * 0.1f),
                        timestamp = 1700000000000L + (p * 1000L) + ptIdx
                    )
                )
                totalPointsCreated++
            }
            page.strokes.add(stroke)
            largeNotebook.pages.add(page)
        }

        // Add 50 Spaced Repetition Flashcards
        for (f in 1..50) {
            largeNotebook.flashcards.add(
                Flashcard(
                    front = "Question $f: What is the significance of wave function collapse?",
                    back = "Answer $f: Transition of quantum superposition into a single definite eigenstate.",
                    hint = "Measurement Problem",
                    reviewIntervalDays = f,
                    repetitions = f % 5,
                    easeFactor = 2.5f + (f * 0.01f)
                )
            )
        }

        assertEquals(105, largeNotebook.pages.size)
        assertEquals(15750, totalPointsCreated)

        // Measure Save Time
        val saveStartTime = System.currentTimeMillis()
        repository.saveNotebook(largeNotebook)
        val saveDurationMs = System.currentTimeMillis() - saveStartTime

        val jsonFile = File(testDir, "notebooks/${largeNotebook.id}.json")
        assertTrue("Saved JSON file must exist", jsonFile.exists())
        val fileSizeBytes = jsonFile.length()
        assertTrue("File size should exceed 500KB for 15,000+ points", fileSizeBytes > 500_000L)

        // Measure Load Time
        val loadStartTime = System.currentTimeMillis()
        val loaded = repository.getNotebook(largeNotebook.id)
        val loadDurationMs = System.currentTimeMillis() - loadStartTime

        assertNotNull("Loaded notebook must not be null", loaded)
        assertEquals(largeNotebook.title, loaded!!.title)
        assertEquals(105, loaded.pages.size)
        assertEquals(50, loaded.flashcards.size)

        var totalLoadedPoints = 0
        for (page in loaded.pages) {
            for (stroke in page.strokes) {
                totalLoadedPoints += stroke.points.size
            }
        }
        assertEquals("Total points loaded must exactly match created points", 15750, totalLoadedPoints)

        // Verify precision on boundary pages: page 1, page 50, page 105
        val p1 = loaded.pages[0]
        assertEquals(1, p1.pageNumber)
        assertEquals(PaperType.CORNELL, p1.paperType)
        assertEquals(150, p1.strokes[0].points.size)
        assertEquals(0.3f, p1.strokes[0].points[0].pressure, 0.001f)
        assertEquals(1700000001000L, p1.strokes[0].points[0].timestamp)

        val p50 = loaded.pages[49]
        assertEquals(50, p50.pageNumber)
        assertEquals(PaperType.GRID, p50.paperType)
        assertEquals(150, p50.strokes[0].points.size)

        val p105 = loaded.pages[104]
        assertEquals(105, p105.pageNumber)
        assertEquals(150, p105.strokes[0].points.size)
        assertEquals(104 * 2.5f % 1000f, p105.strokes[0].points[104].x, 0.01f)

        // Verify flashcards integrity
        assertEquals("Question 1: What is the significance of wave function collapse?", loaded.flashcards[0].front)
        assertEquals("Question 50: What is the significance of wave function collapse?", loaded.flashcards[49].front)
        assertEquals(50, loaded.flashcards[49].reviewIntervalDays)

        // Performance sanity bounds: serialization and loading should each take < 15 seconds on mobile ARM
        assertTrue("Save duration should be < 15s (was ${saveDurationMs}ms)", saveDurationMs < 15000)
        assertTrue("Load duration should be < 15s (was ${loadDurationMs}ms)", loadDurationMs < 15000)
    }

    /**
     * TEST 2: Malformed JSON Recovery & Fault Tolerance
     * Ensures corrupted files in the notebook directory do not crash repository operations.
     */
    @Test
    fun testMalformedJsonRecoveryAndIsolation() {
        val notebooksDir = File(testDir, "notebooks")

        // 1. Zero-byte empty file
        File(notebooksDir, "corrupt_empty.json").writeBytes(ByteArray(0))

        // 2. Truncated JSON
        File(notebooksDir, "corrupt_truncated.json").writeText("{\"id\": \"trunc\", \"title\": \"Incomplete\", \"pages\": [", StandardCharsets.UTF_8)

        // 3. Invalid syntax
        File(notebooksDir, "corrupt_syntax.json").writeText("{ this is definitely not valid json 12345: ### }", StandardCharsets.UTF_8)

        // 4. Raw binary non-UTF8 garbage
        File(notebooksDir, "corrupt_binary.json").writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00.toByte(), 0xBA.toByte(), 0xBE.toByte()))

        // 5. JSON Array instead of JSON Object
        File(notebooksDir, "corrupt_array.json").writeText("[{\"id\": \"arr1\"}, {\"id\": \"arr2\"}]", StandardCharsets.UTF_8)

        // 6. JSON Object with null or corrupted elements in arrays
        val nullPageJson = JSONObject().apply {
            put("id", "corrupt_null_page")
            put("title", "Notebook with Null Page Element")
            val pArr = JSONArray()
            pArr.put(JSONObject.NULL)
            put("pages", pArr)
        }
        File(notebooksDir, "corrupt_null_page.json").writeText(nullPageJson.toString(), StandardCharsets.UTF_8)

        // Now save a clean valid notebook
        val validNotebook = Notebook(
            title = "Pristine Survivor Notebook",
            folder = "Important"
        )
        repository.saveNotebook(validNotebook)

        // Assert getNotebook on corrupted files returns null cleanly without uncaught exception
        assertNull("Empty file must return null", repository.getNotebook("corrupt_empty"))
        assertNull("Truncated file must return null", repository.getNotebook("corrupt_truncated"))
        assertNull("Syntax error must return null", repository.getNotebook("corrupt_syntax"))
        assertNull("Binary garbage must return null", repository.getNotebook("corrupt_binary"))
        assertNull("Array file must return null", repository.getNotebook("corrupt_array"))
        assertNull("Null page array must return null", repository.getNotebook("corrupt_null_page"))

        // Assert getAllNotebooks skips corrupted files and recovers the valid notebook without crashing
        val allNotebooks = repository.getAllNotebooks()
        assertNotNull("getAllNotebooks must return a list", allNotebooks)
        assertTrue("Must contain at least the pristine notebook", allNotebooks.any { it.id == validNotebook.id })
        val survivor = allNotebooks.first { it.id == validNotebook.id }
        assertEquals("Pristine Survivor Notebook", survivor.title)
    }

    /**
     * TEST 3: Unicode, Multi-language Scripts, Emojis, and Math Formulas
     * Verifies that UTF-8 serialization preserves complex multi-byte characters and symbols.
     */
    @Test
    fun testUnicodeAndEmojiPreservationUnderAdversarialInputs() {
        val nb = Notebook(
            title = "Advanced Physics ⚛️ 物理学 & Modern Philosophy 🧠 ✨ 👨‍👩‍👧‍👦",
            coverPattern = "伝統的パターン / 传统图案",
            folder = "ملاحظات الدراسات العليا / מחקר מתקדם"
        )

        val page = Page(pageNumber = 1, paperType = PaperType.SEPIA)

        // Text with mathematical & physics equations, Greek symbols, and quotes
        val mathEquation = "∮ B·dl = μ₀(I_encl + ε₀ dΦ_E/dt) ; ∇ × E = -∂B/∂t ; ∫_{-∞}^{∞} e^{-x²} dx = √π"
        page.textAnnotations.add(
            TextAnnotation(
                x = 50f,
                y = 100f,
                text = mathEquation,
                color = 0xFF1E293B.toInt(),
                fontSize = 18f
            )
        )

        // Sticky note with multi-line text, tabs, escaped quotes, and emoji combinations
        val multiLineContent = "Line 1: Hindi: नमस्ते दुनिया 🙏\nLine 2: Arabic: أهلاً وسهلاً بكم\nLine 3: Special chars: <script>alert('xss')</script> & \"quotes\" and \\slashes\\"
        page.stickyNotes.add(
            StickyNote(
                x = 200f,
                y = 300f,
                content = multiLineContent,
                color = 0xFFBAE6FD.toInt()
            )
        )

        // Flashcards with chemical equation and multilingual definitions
        nb.flashcards.add(
            Flashcard(
                front = "Photosynthesis: 6CO₂ + 6H₂O + hν → C₆H₁₂O₆ + 6O₂ (光合成)",
                back = "Calvin Cycle / カルビン回路 / цитратный цикл",
                hint = "Biological Energetics 🔬 🔥"
            )
        )

        nb.pages.add(page)
        repository.saveNotebook(nb)

        // Re-read from disk
        val loaded = repository.getNotebook(nb.id)
        assertNotNull(loaded)
        assertEquals("Advanced Physics ⚛️ 物理学 & Modern Philosophy 🧠 ✨ 👨‍👩‍👧‍👦", loaded!!.title)
        assertEquals("伝統的パターン / 传统图案", loaded.coverPattern)
        assertEquals("ملاحظات الدراسات العليا / מחקר מתקדם", loaded.folder)

        val loadedPage = loaded.pages[0]
        assertEquals(mathEquation, loadedPage.textAnnotations[0].text)
        assertEquals(multiLineContent, loadedPage.stickyNotes[0].content)
        assertEquals("Photosynthesis: 6CO₂ + 6H₂O + hν → C₆H₁₂O₆ + 6O₂ (光合成)", loaded.flashcards[0].front)
        assertEquals("Calvin Cycle / カルビン回路 / цитратный цикл", loaded.flashcards[0].back)
        assertEquals("Biological Energetics 🔬 🔥", loaded.flashcards[0].hint)
    }

    /**
     * TEST 4: Missing Fields, Mismatched Types & Schema Resilience
     * Verifies that parseNotebookJson handles sparse, partial, or evolving schemas safely.
     */
    @Test
    fun testMissingAndMismatchedFieldsSchemaResilience() {
        val sparseJson = JSONObject().apply {
            // Missing id, coverPattern, folder, defaultPaperType
            put("title", "Sparse Schema Note")
            put("coverColor", "#10b981") // 6-digit hex color
            // pages missing entirely
            // flashcards missing entirely
            put("unknownExtraField", "IgnoreMe")
        }

        val nb1 = repository.parseNotebookJson(sparseJson)
        assertNotNull(nb1.id)
        assertTrue(nb1.id.isNotEmpty())
        assertEquals("Sparse Schema Note", nb1.title)
        assertEquals((0xFF000000 or 0x10b981).toInt(), nb1.coverColor)
        assertEquals("modern", nb1.coverPattern)
        assertEquals("All Notes", nb1.folder)
        assertEquals(PaperType.RULED, nb1.defaultPaperType)
        // Automatically ensures at least 1 page
        assertEquals(1, nb1.pages.size)
        assertEquals(PaperType.RULED, nb1.pages[0].paperType)

        // Test 8-digit hex color, unknown enum paper types, and stroke defaults
        val complexSparseJson = JSONObject().apply {
            put("id", "custom-sparse-id-99")
            put("title", "Complex Sparse Note")
            put("coverColor", "#80DC2626") // 8-digit hex color with alpha
            put("defaultPaperType", "HEXAGONAL_PAPER_NON_EXISTENT") // Unknown enum
            val pages = JSONArray().apply {
                val p = JSONObject().apply {
                    put("pageNumber", 5)
                    put("paperType", "UNKNOWN_PAPER_TYPE")
                    val strokes = JSONArray().apply {
                        val s = JSONObject().apply {
                            put("id", "stroke-missing-pts")
                            put("tool", "NON_EXISTENT_TOOL")
                            put("color", "NOT_A_VALID_HEX")
                            // points array omitted
                        }
                        put(s)
                    }
                    put("strokes", strokes)

                    // Tape with web companion format missing revealed field
                    val tapes = JSONArray().apply {
                        val t = JSONObject().apply {
                            put("id", "web-tape-partial")
                            put("x", 50.0)
                            put("y", 80.0)
                            put("w", 160.0)
                            put("h", 35.0)
                            // "revealed" and "color" omitted
                        }
                        put(t)
                    }
                    put("tapes", tapes)
                }
                put(p)
            }
            put("pages", pages)

            // Flashcard missing hint and review intervals
            val cards = JSONArray().apply {
                val c = JSONObject().apply {
                    put("front", "Front only")
                    put("back", "Back only")
                }
                put(c)
            }
            put("flashcards", cards)
        }

        val nb2 = repository.parseNotebookJson(complexSparseJson)
        assertEquals("custom-sparse-id-99", nb2.id)
        assertEquals(0x80DC2626.toInt(), nb2.coverColor)
        assertEquals(PaperType.RULED, nb2.defaultPaperType) // Safe fallback
        assertEquals(1, nb2.pages.size)

        val page = nb2.pages[0]
        assertEquals(5, page.pageNumber)
        assertEquals(PaperType.RULED, page.paperType) // Safe fallback

        assertEquals(1, page.strokes.size)
        val stroke = page.strokes[0]
        assertEquals(PenTool.BALLPOINT, stroke.tool) // Safe fallback
        assertEquals(0xFF1E293B.toInt(), stroke.color) // Safe fallback for invalid color string
        assertTrue(stroke.points.isEmpty())

        assertEquals(1, page.tapes.size)
        val tape = page.tapes[0]
        assertEquals(50f, tape.left, 0.01f)
        assertEquals(80f, tape.top, 0.01f)
        assertEquals(210f, tape.right, 0.01f) // 50 + 160
        assertEquals(115f, tape.bottom, 0.01f) // 80 + 35
        assertFalse(tape.isRevealed) // Defaulted to false

        assertEquals(1, nb2.flashcards.size)
        val card = nb2.flashcards[0]
        assertEquals("Front only", card.front)
        assertEquals("Back only", card.back)
        assertEquals("", card.hint)
        assertEquals(1, card.reviewIntervalDays)
        assertEquals(2.5f, card.easeFactor, 0.01f)
    }

    /**
     * TEST 5: Simulated Sudden Process Termination During Atomic Write
     * Simulates crashes midway through atomic write and verifies data isolation.
     */
    @Test
    fun testSimulatedCrashDuringAtomicWrite() {
        val nb = Notebook(title = "Atomic Resilience Notebook")
        nb.pages.add(Page(pageNumber = 1, paperType = PaperType.GRID))
        repository.saveNotebook(nb)

        val targetFile = File(testDir, "notebooks/${nb.id}.json")
        val tempFile = File(testDir, "notebooks/${nb.id}.tmp")

        assertTrue("Initial file must exist", targetFile.exists())
        assertFalse("Temp file must not exist after clean save", tempFile.exists())
        val initialLength = targetFile.length()

        // Scenario A: Simulate crash while writing temp file (partial corrupted content in .tmp)
        tempFile.writeText("{\"id\": \"${nb.id}\", \"title\": \"HALFWAY_CRASHED...", StandardCharsets.UTF_8)
        assertTrue("Simulated orphaned temp file exists", tempFile.exists())

        // Verify: getNotebook must read the intact target .json, NOT the corrupted .tmp
        val loadedAfterCrash = repository.getNotebook(nb.id)
        assertNotNull(loadedAfterCrash)
        assertEquals("Atomic Resilience Notebook", loadedAfterCrash!!.title)

        // Verify: getAllNotebooks ignores .tmp and loads the intact .json
        val allAfterCrash = repository.getAllNotebooks()
        val found = allAfterCrash.firstOrNull { it.id == nb.id }
        assertNotNull(found)
        assertEquals("Atomic Resilience Notebook", found!!.title)

        // Scenario B: Recovery on subsequent save — should overwrite the orphaned .tmp cleanly
        nb.title = "Atomic Resilience Notebook Updated v2"
        repository.saveNotebook(nb)

        assertTrue("Target file must exist after recovery save", targetFile.exists())
        assertFalse("Orphaned temp file must be cleaned up after successful rename", tempFile.exists())

        val recovered = repository.getNotebook(nb.id)
        assertNotNull(recovered)
        assertEquals("Atomic Resilience Notebook Updated v2", recovered!!.title)

        // Scenario C: Sudden crash leaving a 0-byte .tmp file without prior .json
        val newId = UUID.randomUUID().toString()
        val newTempFile = File(testDir, "notebooks/$newId.tmp")
        newTempFile.writeBytes(ByteArray(0))

        // getNotebook on newId should safely return null
        assertNull(repository.getNotebook(newId))

        // Saving a notebook with newId should clean up the 0-byte .tmp file
        val brandNew = Notebook(id = newId, title = "Cleaned Up From Zero Byte Tmp")
        repository.saveNotebook(brandNew)

        val newTarget = File(testDir, "notebooks/$newId.json")
        assertTrue(newTarget.exists())
        assertFalse(newTempFile.exists())
        assertEquals("Cleaned Up From Zero Byte Tmp", repository.getNotebook(newId)?.title)
    }

    /**
     * TEST 6: Rapid Successive Saves & Concurrency Stress
     * Simulates intense rapid auto-save cycles (e.g. 50 rapid sequential writes).
     */
    @Test
    fun testRapidSuccessiveSavesIntegrity() {
        val nb = Notebook(title = "Rapid Auto-Save Note")
        val page = Page(pageNumber = 1)
        val stroke = Stroke(tool = PenTool.BALLPOINT)
        page.strokes.add(stroke)
        nb.pages.add(page)

        val iterationCount = 50
        for (i in 1..iterationCount) {
            stroke.points.add(Point(x = i * 5f, y = i * 10f, pressure = 0.5f, timestamp = System.currentTimeMillis()))
            nb.title = "Rapid Auto-Save Note Version $i"
            repository.saveNotebook(nb)
        }

        // Verify no leftover .tmp files
        val tmpFiles = File(testDir, "notebooks").listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals("No .tmp files should remain after 50 rapid saves", 0, tmpFiles.size)

        // Verify final state
        val loaded = repository.getNotebook(nb.id)
        assertNotNull(loaded)
        assertEquals("Rapid Auto-Save Note Version $iterationCount", loaded!!.title)
        assertEquals(iterationCount, loaded.pages[0].strokes[0].points.size)
        val lastPoint = loaded.pages[0].strokes[0].points.last()
        assertEquals(iterationCount * 5f, lastPoint.x, 0.01f)
        assertEquals(iterationCount * 10f, lastPoint.y, 0.01f)
    }

    /**
     * TEST 7: Numerical Boundaries & Coordinate Extremes
     * Tests extreme values (sub-pixel, negative, large coordinates, inverted tapes).
     */
    @Test
    fun testNumericalBoundariesAndInvertedCoordinates() {
        val nb = Notebook(title = "Boundary & Extremes")
        val page = Page(pageNumber = 1)

        // Sub-pixel coordinates, negative coordinates, and huge numbers
        val stroke = Stroke().apply {
            points.add(Point(x = 0.0001f, y = -500.5f, pressure = 0.0f))
            points.add(Point(x = 999999.0f, y = 888888.0f, pressure = 1.0f))
        }
        page.strokes.add(stroke)

        // Inverted Tape coordinates (left > right, top > bottom)
        page.tapes.add(
            ActiveRecallTape(
                left = 500f,
                top = 600f,
                right = 200f,
                bottom = 300f,
                color = 0xFFFEF08A.toInt(),
                isRevealed = false
            )
        )

        nb.pages.add(page)
        repository.saveNotebook(nb)

        val loaded = repository.getNotebook(nb.id)
        assertNotNull(loaded)

        val loadedStroke = loaded!!.pages[0].strokes[0]
        assertEquals(0.0001f, loadedStroke.points[0].x, 0.00001f)
        assertEquals(-500.5f, loadedStroke.points[0].y, 0.01f)
        assertEquals(999999.0f, loadedStroke.points[1].x, 1.0f)

        val loadedTape = loaded.pages[0].tapes[0]
        assertEquals(500f, loadedTape.left, 0.01f)
        assertEquals(600f, loadedTape.top, 0.01f)
        assertEquals(200f, loadedTape.right, 0.01f)
        assertEquals(300f, loadedTape.bottom, 0.01f)
    }
}
