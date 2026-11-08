package com.flownotes.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.flownotes.app.ai.LocalAiStudyEngine
import com.flownotes.app.audio.AudioMemoManager
import com.flownotes.app.data.NoteRepository
import com.flownotes.app.model.*
import com.flownotes.app.pdf.PdfAnnotationManager
import com.flownotes.app.view.CanvasView
import java.io.File

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var repository: NoteRepository
    private lateinit var notebook: Notebook
    private lateinit var canvasView: CanvasView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var audioManager: AudioMemoManager
    private var pdfManager: PdfAnnotationManager? = null

    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        repository = NoteRepository(this)
        audioManager = AudioMemoManager(this)

        val notebookId = intent.getStringExtra("EXTRA_NOTEBOOK_ID")
        val loaded = if (notebookId != null) repository.getNotebook(notebookId) else null
        if (loaded == null) {
            Toast.makeText(this, "Notebook not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        notebook = loaded

        setupViews()
        loadPage(0)
    }

    private fun setupViews() {
        canvasView = findViewById(R.id.canvas_view)
        drawerLayout = findViewById(R.id.drawer_layout)

        // Top Toolbar
        findViewById<View>(R.id.btn_back).setOnClickListener {
            saveAndExit()
        }

        val tvTitle = findViewById<TextView>(R.id.tv_editor_title)
        tvTitle.text = notebook.title
        tvTitle.setOnClickListener {
            showRenameDialog()
        }

        findViewById<View>(R.id.btn_prev_page).setOnClickListener {
            if (currentPageIndex > 0) loadPage(currentPageIndex - 1)
        }

        findViewById<View>(R.id.btn_next_page).setOnClickListener {
            if (currentPageIndex < notebook.pages.size - 1) {
                loadPage(currentPageIndex + 1)
            } else {
                addNewPage()
            }
        }

        findViewById<View>(R.id.btn_add_page).setOnClickListener {
            addNewPage()
        }

        findViewById<View>(R.id.btn_undo).setOnClickListener {
            canvasView.undo()
        }

        findViewById<View>(R.id.btn_redo).setOnClickListener {
            canvasView.redo()
        }

        findViewById<View>(R.id.btn_change_template).setOnClickListener {
            showTemplatePicker()
        }

        findViewById<View>(R.id.btn_export_pdf).setOnClickListener {
            exportToPdf()
        }

        findViewById<View>(R.id.btn_open_ai).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }

        findViewById<View>(R.id.btn_flashcards).setOnClickListener {
            openFlashcardStudy()
        }

        // Floating Pen Dock Tools
        findViewById<View>(R.id.tool_ballpoint).setOnClickListener { selectTool(PenTool.BALLPOINT, it) }
        findViewById<View>(R.id.tool_fountain).setOnClickListener { selectTool(PenTool.FOUNTAIN, it) }
        findViewById<View>(R.id.tool_highlighter).setOnClickListener { selectTool(PenTool.HIGHLIGHTER, it) }
        findViewById<View>(R.id.tool_eraser).setOnClickListener { selectTool(PenTool.ERASER_STROKE, it) }
        findViewById<View>(R.id.tool_tape).setOnClickListener { selectTool(PenTool.TAPE, it) }
        findViewById<View>(R.id.tool_shape).setOnClickListener { selectTool(PenTool.SHAPE, it) }

        // Tape Reveal / Conceal Quick Actions
        findViewById<View>(R.id.btn_reveal_tapes).setOnClickListener {
            canvasView.toggleAllTapes(reveal = true)
            Toast.makeText(this, "All Tapes Revealed", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_conceal_tapes).setOnClickListener {
            canvasView.toggleAllTapes(reveal = false)
            Toast.makeText(this, "All Tapes Concealed (Study Mode)", Toast.LENGTH_SHORT).show()
        }

        // Color Presets
        findViewById<View>(R.id.color_black).setOnClickListener { canvasView.currentColor = Color.parseColor("#1E293B") }
        findViewById<View>(R.id.color_blue).setOnClickListener { canvasView.currentColor = Color.parseColor("#3B82F6") }
        findViewById<View>(R.id.color_purple).setOnClickListener { canvasView.currentColor = Color.parseColor("#8B5CF6") }
        findViewById<View>(R.id.color_green).setOnClickListener { canvasView.currentColor = Color.parseColor("#10B981") }
        findViewById<View>(R.id.color_orange).setOnClickListener { canvasView.currentColor = Color.parseColor("#F59E0B") }
        findViewById<View>(R.id.color_red).setOnClickListener { canvasView.currentColor = Color.parseColor("#EF4444") }

        // Stroke Width Presets
        findViewById<View>(R.id.size_fine).setOnClickListener { canvasView.currentStrokeWidth = 3.0f }
        findViewById<View>(R.id.size_medium).setOnClickListener { canvasView.currentStrokeWidth = 6.0f }
        findViewById<View>(R.id.size_bold).setOnClickListener { canvasView.currentStrokeWidth = 12.0f }

        // Setup On-Device AI Drawer
        setupLocalAiDrawer()
    }

    private fun selectTool(tool: PenTool, view: View) {
        canvasView.currentTool = tool
        val dock = findViewById<LinearLayout>(R.id.floating_pen_dock)
        for (i in 0 until dock.childCount) {
            val child = dock.getChildAt(i)
            child.alpha = if (child == view) 1.0f else 0.65f
        }
        view.alpha = 1.0f
    }

    private fun loadPage(index: Int) {
        if (index !in notebook.pages.indices) return
        currentPageIndex = index
        val page = notebook.pages[index]
        canvasView.currentPage = page

        findViewById<TextView>(R.id.tv_page_indicator).text = "Page ${index + 1} of ${notebook.pages.size}"

        // If page has underlying PDF page, render it
        if (notebook.pdfPath != null && page.pdfPageIndex != null) {
            if (pdfManager == null) {
                pdfManager = PdfAnnotationManager(this)
                pdfManager?.openPdf(File(notebook.pdfPath!!))
            }
            val bmp = pdfManager?.renderPageToBitmap(page.pdfPageIndex!!, 1200, 1600)
            canvasView.pdfPageBitmap = bmp
        } else {
            canvasView.pdfPageBitmap = null
        }
    }

    private fun addNewPage() {
        val newPage = Page(
            pageNumber = notebook.pages.size + 1,
            paperType = notebook.defaultPaperType
        )
        notebook.pages.add(newPage)
        repository.saveNotebook(notebook)
        loadPage(notebook.pages.size - 1)
        Toast.makeText(this, "Page ${notebook.pages.size} added", Toast.LENGTH_SHORT).show()
    }

    private fun showTemplatePicker() {
        val templates = PaperType.values().map { it.name }
        AlertDialog.Builder(this)
            .setTitle("Select Paper Template")
            .setItems(templates.toTypedArray()) { _, which ->
                val selectedType = PaperType.values()[which]
                canvasView.currentPage.paperType = selectedType
                canvasView.invalidate()
                repository.saveNotebook(notebook)
            }
            .show()
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply { setText(notebook.title) }
        AlertDialog.Builder(this)
            .setTitle("Rename Notebook")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    notebook.title = newTitle
                    findViewById<TextView>(R.id.tv_editor_title).text = newTitle
                    repository.saveNotebook(notebook)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportToPdf() {
        val pdfMgr = PdfAnnotationManager(this)
        val exportFile = File(getExternalFilesDir(null), "${notebook.title.replace(" ", "_")}.pdf")
        val success = pdfMgr.exportNotebookToPdf(notebook, exportFile)
        if (success) {
            Toast.makeText(this, "Exported: ${exportFile.name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFlashcardStudy() {
        val intent = Intent(this, FlashcardStudyActivity::class.java).apply {
            putExtra("EXTRA_NOTEBOOK_ID", notebook.id)
        }
        startActivity(intent)
    }

    // 100% On-Device AI Study Drawer Setup (Zero Server, Zero Cost)
    private fun setupLocalAiDrawer() {
        val tvAiOutput = findViewById<TextView>(R.id.tv_ai_output)
        val etAiInput = findViewById<EditText>(R.id.et_ai_question)

        findViewById<View>(R.id.btn_ai_summarize).setOnClickListener {
            val allText = LocalAiStudyEngine.extractNotebookText(notebook)
            val summary = LocalAiStudyEngine.generateLocalSummary(notebook.title, allText, notebook.pageCount)
            tvAiOutput.text = summary
        }

        findViewById<View>(R.id.btn_ai_problem_solver).setOnClickListener {
            val q = etAiInput.text.toString().ifBlank { "Solve problem from current note page" }
            val allText = LocalAiStudyEngine.extractNotebookText(notebook)
            val solution = LocalAiStudyEngine.solveProblemLocally(q, allText)
            tvAiOutput.text = solution
        }

        findViewById<View>(R.id.btn_ai_gen_cards).setOnClickListener {
            val cards = LocalAiStudyEngine.generateLocalFlashcards(notebook)
            tvAiOutput.text = "Generated ${cards.size} On-Device Flashcards!\n\nTap 'Flashcards' in the top bar to start interactive study session."
        }

        findViewById<View>(R.id.btn_ai_ask).setOnClickListener {
            val query = etAiInput.text.toString().trim()
            if (query.isNotEmpty()) {
                val answer = LocalAiStudyEngine.answerStudyQuestion(query, notebook)
                tvAiOutput.text = answer
            }
        }

        findViewById<View>(R.id.btn_insert_ai_note).setOnClickListener {
            val textToInsert = tvAiOutput.text.toString()
            if (textToInsert.isNotEmpty()) {
                canvasView.currentPage.textAnnotations.add(
                    TextAnnotation(
                        x = 100f,
                        y = 200f,
                        text = textToInsert.take(200) + "...",
                        fontSize = 16f
                    )
                )
                canvasView.invalidate()
                drawerLayout.closeDrawer(GravityCompat.END)
                Toast.makeText(this, "Inserted into note", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveAndExit() {
        repository.saveNotebook(notebook)
        finish()
    }

    override fun onPause() {
        super.onPause()
        repository.saveNotebook(notebook)
    }

    override fun onDestroy() {
        super.onDestroy()
        audioManager.release()
        pdfManager?.close()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END)
        } else {
            saveAndExit()
        }
    }
}
