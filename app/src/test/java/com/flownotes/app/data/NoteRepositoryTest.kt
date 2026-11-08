package com.flownotes.app.data

import com.flownotes.app.model.ActiveRecallTape
import com.flownotes.app.model.Notebook
import com.flownotes.app.model.Page
import com.flownotes.app.model.PaperType
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Unit Test Specification: NoteRepository Local Persistence & Security Boundary
 * Verifies directory creation, default notebooks initialization, save/load, page addition,
 * deletion, atomic write handling, and strict path traversal prevention.
 */
class NoteRepositoryTest {

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
        testDir = File(valid, "flownotes_repo_test_${UUID.randomUUID()}").apply {
            mkdirs()
        }
        repository = NoteRepository(testDir)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun testRepositoryDirectoryCreation() {
        val notebooksDir = File(testDir, "notebooks")
        val pdfDir = File(testDir, "pdf")
        val audioDir = File(testDir, "audio")

        assertTrue("notebooks/ directory must exist", notebooksDir.exists() && notebooksDir.isDirectory)
        assertTrue("pdf/ directory must exist", pdfDir.exists() && pdfDir.isDirectory)
        assertTrue("audio/ directory must exist", audioDir.exists() && audioDir.isDirectory)
    }

    @Test
    fun testDefaultNotebooksInitializationWhenEmpty() {
        // Initializing with empty folder should auto-create default welcome & science notebooks
        val allNotebooks = repository.getAllNotebooks()
        assertTrue("Should create at least 2 default notebooks", allNotebooks.size >= 2)

        val titles = allNotebooks.map { it.title }
        assertTrue("Must include Welcome notebook", titles.contains("Welcome to FlowNotes"))
        assertTrue("Must include Science notebook", titles.contains("Mathematics & Physics Notes"))

        val welcome = allNotebooks.first { it.title == "Welcome to FlowNotes" }
        assertEquals(PaperType.CORNELL, welcome.defaultPaperType)
        assertEquals(3, welcome.pages.size)

        // Check active recall tape on welcome page 1
        val p1 = welcome.pages[0]
        assertEquals(1, p1.tapes.size)
        assertFalse(p1.tapes[0].isRevealed)
    }

    @Test
    fun testSaveAndRetrieveNotebook() {
        val nb = Notebook(
            title = "Biochemistry Lecture 5",
            folder = "Study",
            defaultPaperType = PaperType.GRID
        )
        val p1 = Page(pageNumber = 1, paperType = PaperType.GRID)
        nb.pages.add(p1)

        repository.saveNotebook(nb)

        // Verify JSON file created on disk
        val jsonFile = File(testDir, "notebooks/${nb.id}.json")
        assertTrue("Notebook JSON file must exist on disk", jsonFile.exists())

        // Retrieve
        val retrieved = repository.getNotebook(nb.id)
        assertNotNull("Retrieved notebook should not be null", retrieved)
        assertEquals("Biochemistry Lecture 5", retrieved!!.title)
        assertEquals("Study", retrieved.folder)
        assertEquals(PaperType.GRID, retrieved.defaultPaperType)
        assertEquals(1, retrieved.pages.size)
    }

    @Test
    fun testPageAdditionAndPersistence() {
        val nb = Notebook(title = "Multi-page Test")
        nb.pages.add(Page(pageNumber = 1, paperType = PaperType.RULED))
        repository.saveNotebook(nb)

        // Add 2 pages
        val loaded = repository.getNotebook(nb.id)!!
        assertEquals(1, loaded.pages.size)

        loaded.pages.add(Page(pageNumber = 2, paperType = PaperType.COLLEGE_RULED))
        loaded.pages.add(Page(pageNumber = 3, paperType = PaperType.PLANNER))
        repository.saveNotebook(loaded)

        // Re-open from disk
        val updated = repository.getNotebook(nb.id)!!
        assertEquals(3, updated.pages.size)
        assertEquals(PaperType.RULED, updated.pages[0].paperType)
        assertEquals(PaperType.COLLEGE_RULED, updated.pages[1].paperType)
        assertEquals(PaperType.PLANNER, updated.pages[2].paperType)
    }

    @Test
    fun testGetAllNotebooksOrderedByUpdatedAt() {
        val nb1 = Notebook(title = "Note A").apply { updatedAt = 1000L }
        val nb2 = Notebook(title = "Note B").apply { updatedAt = 3000L }
        val nb3 = Notebook(title = "Note C").apply { updatedAt = 2000L }

        repository.saveNotebook(nb1)
        repository.saveNotebook(nb2)
        repository.saveNotebook(nb3)

        val list = repository.getAllNotebooks()
        // Default notebooks are also present; verify that latest updated is at the top
        assertTrue(list.size >= 3)
        for (i in 0 until list.size - 1) {
            assertTrue("Must be sorted by updatedAt descending", list[i].updatedAt >= list[i + 1].updatedAt)
        }
    }

    @Test
    fun testDeleteNotebook() {
        val nb = Notebook(title = "To be deleted")
        repository.saveNotebook(nb)

        val jsonFile = File(testDir, "notebooks/${nb.id}.json")
        assertTrue(jsonFile.exists())

        val success = repository.deleteNotebook(nb.id)
        assertTrue("deleteNotebook should return true", success)
        assertFalse("JSON file should no longer exist", jsonFile.exists())
        assertNull("getNotebook should return null for deleted id", repository.getNotebook(nb.id))
    }

    @Test
    fun testAtomicWriteHandling() {
        val nb = Notebook(title = "Atomic Write Test")
        repository.saveNotebook(nb)

        val targetFile = File(testDir, "notebooks/${nb.id}.json")
        val tempFile = File(testDir, "notebooks/${nb.id}.tmp")

        assertTrue("Target file must exist", targetFile.exists())
        assertFalse("Temporary file must NOT be left behind", tempFile.exists())
        assertTrue("Target file should not be empty", targetFile.length() > 0)
    }

    /**
     * TEST 8: Path Traversal Prevention on Relative Paths
     * Verifies that saving a notebook with relative traversal ID ("../../evil")
     * strictly writes inside notebooksDir and NEVER writes outside.
     */
    @Test
    fun testSaveNotebookPreventsRelativePathTraversal() {
        val maliciousId = "../../escaped_relative_note"
        val nb = Notebook(id = maliciousId, title = "Relative Escape Test")
        repository.saveNotebook(nb)

        val escapedFile = File(testDir, "escaped_relative_note.json")
        val escapedTmp = File(testDir, "escaped_relative_note.tmp")
        val rootEscaped = File(testDir.parentFile, "escaped_relative_note.json")

        assertFalse("Must not write escaped .json to parent directory", escapedFile.exists())
        assertFalse("Must not write escaped .tmp to parent directory", escapedTmp.exists())
        if (rootEscaped.parentFile?.canRead() == true) {
            assertFalse("Must not write to ancestor directory", rootEscaped.exists())
        }

        val containedFile = File(repository.notebooksDir, "escaped_relative_note.json")
        assertTrue("Sanitized file must exist inside notebooksDir", containedFile.exists())

        // Verify retrieval via traversal ID reads cleanly from notebooksDir
        val loaded = repository.getNotebook(maliciousId)
        assertNotNull("Should retrieve notebook saved with traversal ID", loaded)
        assertEquals("Relative Escape Test", loaded?.title)

        // Verify deletion via traversal ID only deletes the contained file
        val deleted = repository.deleteNotebook(maliciousId)
        assertTrue("deleteNotebook should succeed", deleted)
        assertFalse("Contained file should be deleted", containedFile.exists())
    }

    /**
     * TEST 9: Path Traversal Prevention on Absolute Paths
     * Verifies that saving a notebook with an absolute path ID ("/.../abs_note")
     * strictly confines file creation to notebooksDir and does not write to the absolute path.
     */
    @Test
    fun testSaveNotebookPreventsAbsolutePathEscape() {
        val absTargetFile = File(testDir, "abs_escaped_target.json")
        val absId = absTargetFile.absolutePath.removeSuffix(".json")
        val nb = Notebook(id = absId, title = "Absolute Escape Test")
        repository.saveNotebook(nb)

        assertFalse("Must NOT create file directly at arbitrary absolute path", absTargetFile.exists())

        val sanitizedBase = File(absId).name
        val containedFile = File(repository.notebooksDir, "$sanitizedBase.json")
        assertTrue("File must be saved strictly within notebooksDir", containedFile.exists())

        // Clean roundtrip retrieval
        val loaded = repository.getNotebook(absId)
        assertNotNull("Should retrieve via absolute path query", loaded)
        assertEquals("Absolute Escape Test", loaded?.title)

        // Clean deletion
        val deleted = repository.deleteNotebook(absId)
        assertTrue(deleted)
        assertFalse(containedFile.exists())
    }

    /**
     * TEST 10: Prevention of Arbitrary File Read (Data Exfiltration via getNotebook)
     * Verifies that getNotebook cannot read canary JSON files outside notebooksDir.
     */
    @Test
    fun testGetNotebookPreventsArbitraryFileReadOutsideNotebooksDir() {
        // Place a sensitive canary JSON file outside notebooksDir
        val canaryOutside = File(testDir, "sensitive_config.json")
        canaryOutside.writeText(
            "{\"id\": \"sensitive_config\", \"title\": \"TOP_SECRET_CREDENTIALS\", \"pages\": []}",
            StandardCharsets.UTF_8
        )

        try {
            // Attempt relative traversal read
            val readRel1 = repository.getNotebook("../sensitive_config")
            assertNull("getNotebook with '../' must not read files outside notebooksDir", readRel1)

            val readRel2 = repository.getNotebook("../../sensitive_config")
            assertNull("getNotebook with '../../' must not read files outside notebooksDir", readRel2)

            // Attempt absolute path read
            val readAbs = repository.getNotebook(canaryOutside.absolutePath.removeSuffix(".json"))
            assertNull("getNotebook with absolute path must not read files outside notebooksDir", readAbs)
        } finally {
            canaryOutside.delete()
        }
    }

    /**
     * TEST 11: Prevention of Arbitrary File Deletion (deleteNotebook)
     * Verifies that deleteNotebook cannot delete canary files outside notebooksDir.
     */
    @Test
    fun testDeleteNotebookPreventsArbitraryFileDeletionOutsideNotebooksDir() {
        // Place a canary file in parent directory
        val canaryFile = File(testDir, "critical_system_file.json")
        canaryFile.writeText("CRITICAL_IMMUTABLE_DATA", StandardCharsets.UTF_8)

        try {
            // Attempt deletion via relative path traversal
            repository.deleteNotebook("../critical_system_file")
            assertTrue("Canary file must NOT be deleted via relative traversal", canaryFile.exists())

            repository.deleteNotebook("../../critical_system_file")
            assertTrue("Canary file must NOT be deleted via deep traversal", canaryFile.exists())

            // Attempt deletion via absolute path
            repository.deleteNotebook(canaryFile.absolutePath.removeSuffix(".json"))
            assertTrue("Canary file must NOT be deleted via absolute path", canaryFile.exists())
        } finally {
            canaryFile.delete()
        }
    }

    /**
     * TEST 12: Complex & Obfuscated Traversal Vectors Sanitization
     * Tests resilience against backslashes, dot combinations, empty IDs, whitespace, and null bytes.
     */
    @Test
    fun testComplexAndObfuscatedTraversalVectorsSanitization() {
        val adversarialVectors = listOf(
            "..\\..\\win_traversal",
            "....//deep_dots",
            "..",
            "../..",
            "",
            "   ",
            "note\u0000poison",
            "sub/dir/nested_id"
        )

        for (vec in adversarialVectors) {
            val nb = Notebook(id = vec, title = "Payload: $vec")
            repository.saveNotebook(nb)

            // Verify all files created remain strictly inside notebooksDir
            val filesInNotebooks = repository.notebooksDir.listFiles() ?: emptyArray()
            for (f in filesInNotebooks) {
                assertEquals(
                    "Every file must have notebooksDir as parent",
                    repository.notebooksDir.canonicalFile,
                    f.canonicalFile.parentFile?.canonicalFile
                )
            }

            // Verify no files escaped into testDir directly
            val filesInTestDir = testDir.listFiles { _, name -> name.endsWith(".json") || name.endsWith(".tmp") } ?: emptyArray()
            assertEquals("No files may escape into testDir for vector: '$vec'", 0, filesInTestDir.size)
        }
    }

    @Test
    fun testFavoriteNotebookPersistence() {
        val nb = Notebook(
            title = "Starred Exam Review",
            folder = "Study",
            isFavorite = true
        )
        repository.saveNotebook(nb)

        val retrieved = repository.getNotebook(nb.id)
        assertNotNull(retrieved)
        assertTrue("Notebook must persist isFavorite flag", retrieved!!.isFavorite)

        // Toggle back to false
        retrieved.isFavorite = false
        repository.saveNotebook(retrieved)

        val updated = repository.getNotebook(nb.id)
        assertNotNull(updated)
        assertFalse("Notebook must persist updated false isFavorite flag", updated!!.isFavorite)
    }
}
