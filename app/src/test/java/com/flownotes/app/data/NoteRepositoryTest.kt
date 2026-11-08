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
import java.util.UUID

/**
 * Unit Test Specification: NoteRepository Local Persistence
 * Verifies directory creation, default notebooks initialization, save/load, page addition,
 * deletion, and atomic write handling.
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
}
