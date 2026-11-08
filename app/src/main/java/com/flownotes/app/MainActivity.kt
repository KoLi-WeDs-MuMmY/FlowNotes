package com.flownotes.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flownotes.app.data.NoteRepository
import com.flownotes.app.model.ActiveRecallTape
import com.flownotes.app.model.Flashcard
import com.flownotes.app.model.Notebook
import com.flownotes.app.model.Page
import com.flownotes.app.model.PaperType
import com.flownotes.app.model.Point
import com.flownotes.app.model.StickyNote
import com.flownotes.app.model.Stroke
import com.flownotes.app.model.TextAnnotation
import com.flownotes.app.pdf.PdfAnnotationManager
import com.flownotes.app.util.AppTheme
import com.flownotes.app.util.ThemeManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LibraryFilter {
    ALL, RECENT, FAVORITES, PDFS, FLASHCARDS, FOLDER
}

class MainActivity : AppCompatActivity() {

    private lateinit var repository: NoteRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotebookAdapter
    private var allNotebooks = mutableListOf<Notebook>()
    private var currentFilter = LibraryFilter.ALL
    private var activeFolder = "Study"

    companion object {
        private const val REQUEST_PICK_PDF = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        this@MainActivity.repository = NoteRepository(this)
        setupViews()
        loadNotebooks()
    }

    override fun onResume() {
        super.onResume()
        loadNotebooks()
    }

    private fun getThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.rv_notebooks)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = NotebookAdapter(
            onNotebookClick = { nb -> openNotebook(nb) },
            onFavoriteToggle = { nb -> toggleFavorite(nb) },
            onRename = { nb -> showRenameDialog(nb) },
            onDuplicate = { nb -> duplicateNotebook(nb) },
            onExportPdf = { nb -> exportNotebookToPdf(nb) },
            onDelete = { nb -> confirmDeleteNotebook(nb) }
        )
        recyclerView.adapter = adapter

        // Theme Switcher Button
        findViewById<View>(R.id.btn_theme).setOnClickListener {
            showThemePickerDialog()
        }

        // Search Bar filter & clear button
        val etSearch = findViewById<EditText>(R.id.et_search)
        val btnClearSearch = findViewById<ImageView>(R.id.btn_clear_search)

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterNotebooks(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter chips
        setupFilterChips()

        // Empty state create button
        findViewById<View>(R.id.btn_empty_create).setOnClickListener {
            showNewNotebookDialog()
        }

        // New Notebook FAB
        findViewById<View>(R.id.fab_new_notebook).setOnClickListener {
            showNewNotebookDialog()
        }

        // Import PDF button
        findViewById<View>(R.id.btn_import_pdf).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_PICK_PDF)
        }
    }

    private fun showThemePickerDialog() {
        val themes = AppTheme.values()
        val names = themes.map { it.displayName }.toTypedArray()
        val currentTheme = ThemeManager.getTheme(this)
        val checkedItem = themes.indexOf(currentTheme)

        AlertDialog.Builder(this)
            .setTitle("Appearance & Theme")
            .setSingleChoiceItems(names, checkedItem) { dialog, which ->
                val selected = themes[which]
                if (selected != currentTheme) {
                    ThemeManager.setTheme(this, selected)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupFilterChips() {
        val chipAll = findViewById<TextView>(R.id.chip_all)
        val chipRecent = findViewById<TextView>(R.id.chip_recent)
        val chipFavorites = findViewById<TextView>(R.id.chip_favorites)
        val chipPdfs = findViewById<TextView>(R.id.chip_pdfs)
        val chipFlashcards = findViewById<TextView>(R.id.chip_flashcards)
        val chipStudy = findViewById<TextView>(R.id.chip_folder_study)
        val chipWork = findViewById<TextView>(R.id.chip_folder_work)
        val chipPersonal = findViewById<TextView>(R.id.chip_folder_personal)

        val chips = listOf(chipAll, chipRecent, chipFavorites, chipPdfs, chipFlashcards, chipStudy, chipWork, chipPersonal)

        fun highlightChip(selected: TextView) {
            chips.forEach { chip ->
                if (chip == selected) {
                    chip.setBackgroundResource(R.drawable.bg_filter_chip_selected)
                    chip.setTextColor(getThemeColor(R.attr.flownotesChipSelectedText))
                    chip.setTypeface(null, Typeface.BOLD)
                } else {
                    chip.setBackgroundResource(R.drawable.bg_filter_chip)
                    chip.setTextColor(getThemeColor(R.attr.flownotesChipText))
                    chip.setTypeface(null, Typeface.NORMAL)
                }
            }
        }

        chipAll.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.ALL
            highlightChip(chipAll)
            findViewById<TextView>(R.id.tv_folder_title).text = "All Notes"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipRecent.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.RECENT
            highlightChip(chipRecent)
            findViewById<TextView>(R.id.tv_folder_title).text = "Recently Updated"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipFavorites.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.FAVORITES
            highlightChip(chipFavorites)
            findViewById<TextView>(R.id.tv_folder_title).text = "Favorites"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipPdfs.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.PDFS
            highlightChip(chipPdfs)
            findViewById<TextView>(R.id.tv_folder_title).text = "PDF Documents"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipFlashcards.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.FLASHCARDS
            highlightChip(chipFlashcards)
            findViewById<TextView>(R.id.tv_folder_title).text = "Flashcard Decks"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipStudy.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.FOLDER
            this@MainActivity.activeFolder = "Study"
            highlightChip(chipStudy)
            findViewById<TextView>(R.id.tv_folder_title).text = "Folder: Study"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipWork.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.FOLDER
            this@MainActivity.activeFolder = "Work"
            highlightChip(chipWork)
            findViewById<TextView>(R.id.tv_folder_title).text = "Folder: Work"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
        chipPersonal.setOnClickListener {
            this@MainActivity.currentFilter = LibraryFilter.FOLDER
            this@MainActivity.activeFolder = "Personal"
            highlightChip(chipPersonal)
            findViewById<TextView>(R.id.tv_folder_title).text = "Folder: Personal"
            filterNotebooks(findViewById<EditText>(R.id.et_search).text.toString())
        }
    }

    private fun loadNotebooks() {
        this@MainActivity.allNotebooks = repository.getAllNotebooks().toMutableList()
        val query = findViewById<EditText>(R.id.et_search).text.toString()
        filterNotebooks(query)
    }

    private fun filterNotebooks(query: String) {
        val q = query.trim()
        var filtered = when (currentFilter) {
            LibraryFilter.ALL -> allNotebooks
            LibraryFilter.RECENT -> allNotebooks.sortedByDescending { it.updatedAt }
            LibraryFilter.FAVORITES -> allNotebooks.filter { it.isFavorite }
            LibraryFilter.PDFS -> allNotebooks.filter { it.pdfPath != null }
            LibraryFilter.FLASHCARDS -> allNotebooks.filter { it.flashcards.isNotEmpty() }
            LibraryFilter.FOLDER -> allNotebooks.filter { it.folder.equals(activeFolder, ignoreCase = true) }
        }

        if (q.isNotEmpty()) {
            filtered = filtered.filter { it.title.contains(q, ignoreCase = true) }
        }

        adapter.submitList(filtered)

        val countTv = findViewById<TextView>(R.id.tv_notebook_count)
        countTv.text = "${filtered.size} ${if (filtered.size == 1) "notebook" else "notebooks"}"

        val emptyLayout = findViewById<View>(R.id.layout_empty_state)
        val rv = findViewById<RecyclerView>(R.id.rv_notebooks)

        if (filtered.isEmpty()) {
            emptyLayout.visibility = View.VISIBLE
            rv.visibility = View.GONE
            findViewById<TextView>(R.id.tv_empty_title).text = if (q.isNotEmpty()) "No Matching Notebooks" else "No Notebooks Yet"
            findViewById<TextView>(R.id.tv_empty_subtitle).text = if (q.isNotEmpty()) "Try a different search query or clear filter chips." else "Create your first digital notebook or import a PDF to start writing."
        } else {
            emptyLayout.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    private fun openNotebook(notebook: Notebook) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            putExtra("EXTRA_NOTEBOOK_ID", notebook.id)
        }
        startActivity(intent)
    }

    private fun toggleFavorite(notebook: Notebook) {
        notebook.isFavorite = !notebook.isFavorite
        repository.saveNotebook(notebook)
        loadNotebooks()
        val msg = if (notebook.isFavorite) "Added to Favorites" else "Removed from Favorites"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun duplicateNotebook(notebook: Notebook) {
        val copy = Notebook(
            title = "${notebook.title} (Copy)",
            coverColor = notebook.coverColor,
            coverPattern = notebook.coverPattern,
            folder = notebook.folder,
            defaultPaperType = notebook.defaultPaperType,
            isFavorite = notebook.isFavorite
        )
        for (page in notebook.pages) {
            val p = Page(
                pageNumber = page.pageNumber,
                paperType = page.paperType,
                pdfPageIndex = page.pdfPageIndex,
                audioMemoUri = page.audioMemoUri
            )
            for (s in page.strokes) {
                p.strokes.add(s.copy(id = UUID.randomUUID().toString(), points = s.points.toMutableList()))
            }
            for (t in page.tapes) {
                p.tapes.add(t.copy(id = UUID.randomUUID().toString()))
            }
            for (txt in page.textAnnotations) {
                p.textAnnotations.add(txt.copy(id = UUID.randomUUID().toString()))
            }
            for (st in page.stickyNotes) {
                p.stickyNotes.add(st.copy(id = UUID.randomUUID().toString()))
            }
            copy.pages.add(p)
        }
        for (card in notebook.flashcards) {
            copy.flashcards.add(card.copy(id = UUID.randomUUID().toString()))
        }
        repository.saveNotebook(copy)
        loadNotebooks()
        Toast.makeText(this, "Duplicated: ${copy.title}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteNotebook(notebook: Notebook) {
        AlertDialog.Builder(this)
            .setTitle("Delete Notebook")
            .setMessage("Are you sure you want to delete \"${notebook.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteNotebook(notebook.id)
                loadNotebooks()
                Toast.makeText(this, "Notebook deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewNotebookDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_notebook, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_notebook_title)
        val spTemplate = dialogView.findViewById<Spinner>(R.id.sp_paper_template)

        val templates = PaperType.values().map { it.name }
        spTemplate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, templates)

        var selectedColor = 0xFF2563EB.toInt()
        val swatchMap = mapOf(
            R.id.swatch_sapphire to 0xFF2563EB.toInt(),
            R.id.swatch_slate to 0xFF334155.toInt(),
            R.id.swatch_forest to 0xFF059669.toInt(),
            R.id.swatch_amber to 0xFFD97706.toInt(),
            R.id.swatch_crimson to 0xFFDC2626.toInt(),
            R.id.swatch_plum to 0xFF7C3AED.toInt(),
            R.id.swatch_ochre to 0xFFB45309.toInt()
        )

        fun updateSwatchSelection(selectedId: Int) {
            swatchMap.forEach { (id, _) ->
                val v = dialogView.findViewById<View>(id)
                if (id == selectedId) {
                    v.scaleX = 1.25f
                    v.scaleY = 1.25f
                    v.alpha = 1.0f
                } else {
                    v.scaleX = 1.0f
                    v.scaleY = 1.0f
                    v.alpha = 0.65f
                }
            }
        }
        updateSwatchSelection(R.id.swatch_sapphire)

        swatchMap.forEach { (id, color) ->
            dialogView.findViewById<View>(id).setOnClickListener {
                selectedColor = color
                updateSwatchSelection(id)
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val title = etTitle.text.toString().ifBlank { "My Notebook" }
                val template = PaperType.values()[spTemplate.selectedItemPosition]
                val targetFolder = when (this@MainActivity.currentFilter) {
                    LibraryFilter.FOLDER -> this@MainActivity.activeFolder
                    else -> "Study"
                }
                val newNb = Notebook(
                    title = title,
                    coverColor = selectedColor,
                    folder = targetFolder,
                    defaultPaperType = template
                ).apply {
                    pages.add(Page(pageNumber = 1, paperType = template))
                }
                this@MainActivity.repository.saveNotebook(newNb)
                this@MainActivity.loadNotebooks()
                this@MainActivity.openNotebook(newNb)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(notebook: Notebook) {
        val input = EditText(this).apply {
            setText(notebook.title)
            setSelection(notebook.title.length)
        }
        val container = FrameLayout(this).apply {
            setPadding(50, 20, 50, 10)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Notebook")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    notebook.title = newTitle
                    this@MainActivity.repository.saveNotebook(notebook)
                    this@MainActivity.loadNotebooks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportNotebookToPdf(notebook: Notebook) {
        val pdfManager = PdfAnnotationManager(this)
        val safeTitle = notebook.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val exportFile = File(getExternalFilesDir(null), "${safeTitle}.pdf")
        val success = pdfManager.exportNotebookToPdf(notebook, exportFile)
        if (success) {
            Toast.makeText(this, "Exported PDF to: ${exportFile.name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Failed to export PDF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_PDF && resultCode == Activity.RESULT_OK && data?.data != null) {
            importPdfFile(data.data!!)
        }
    }

    private fun importPdfFile(uri: Uri) {
        try {
            val contentResolver = contentResolver
            val fileName = "imported_${System.currentTimeMillis()}.pdf"
            val destFile = File(repository.pdfsDir, fileName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val pdfManager = PdfAnnotationManager(this)
            val pageCount = pdfManager.openPdf(destFile)
            pdfManager.close()

            val title = uri.lastPathSegment?.substringAfterLast("/")?.substringBeforeLast(".") ?: "Imported PDF"
            val newNb = Notebook(
                title = title,
                coverColor = 0xFFEF4444.toInt(),
                folder = "Study",
                pdfPath = destFile.absolutePath,
                defaultPaperType = PaperType.BLANK
            )

            for (i in 0 until pageCount.coerceAtLeast(1)) {
                newNb.pages.add(
                    Page(
                        pageNumber = i + 1,
                        paperType = PaperType.BLANK,
                        pdfPageIndex = i
                    )
                )
            }

            this@MainActivity.repository.saveNotebook(newNb)
            this@MainActivity.loadNotebooks()
            this@MainActivity.openNotebook(newNb)
            Toast.makeText(this, "Imported PDF ($pageCount pages)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to import PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class NotebookAdapter(
    private val onNotebookClick: (Notebook) -> Unit,
    private val onFavoriteToggle: (Notebook) -> Unit,
    private val onRename: (Notebook) -> Unit,
    private val onDuplicate: (Notebook) -> Unit,
    private val onExportPdf: (Notebook) -> Unit,
    private val onDelete: (Notebook) -> Unit
) : RecyclerView.Adapter<NotebookAdapter.ViewHolder>() {

    private var items = listOf<Notebook>()

    fun submitList(list: List<Notebook>) {
        this.items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notebook_card, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_card_title)
        private val tvPageBadge: TextView = itemView.findViewById(R.id.tv_cover_page_badge)
        private val tvFolder: TextView = itemView.findViewById(R.id.tv_card_folder)
        private val tvUpdated: TextView = itemView.findViewById(R.id.tv_card_updated)
        private val coverView: View = itemView.findViewById(R.id.view_card_cover)
        private val tvCoverBadge: TextView = itemView.findViewById(R.id.tv_cover_badge)
        private val ivStar: ImageView = itemView.findViewById(R.id.iv_favorite_star)
        private val btnMenu: ImageView = itemView.findViewById(R.id.btn_card_menu)

        fun bind(item: Notebook) {
            tvTitle.text = item.title
            tvCoverBadge.text = item.title
            coverView.setBackgroundColor(item.coverColor)
            tvPageBadge.text = "${item.pageCount} ${if (item.pageCount == 1) "pg" else "pgs"}"
            tvFolder.text = "${item.folder} • "
            tvUpdated.text = formatRelativeTime(item.updatedAt)
            ivStar.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            itemView.setOnClickListener { onNotebookClick(item) }

            btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "Open")
                popup.menu.add(0, 2, 1, if (item.isFavorite) "Remove from Favorites" else "Add to Favorites")
                popup.menu.add(0, 3, 2, "Rename")
                popup.menu.add(0, 4, 3, "Duplicate")
                popup.menu.add(0, 5, 4, "Export as PDF")
                popup.menu.add(0, 6, 5, "Delete")

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onNotebookClick(item)
                        2 -> onFavoriteToggle(item)
                        3 -> onRename(item)
                        4 -> onDuplicate(item)
                        5 -> onExportPdf(item)
                        6 -> onDelete(item)
                    }
                    true
                }
                popup.show()
            }

            itemView.setOnLongClickListener {
                btnMenu.performClick()
                true
            }
        }

        private fun formatRelativeTime(timestamp: Long): String {
            val diff = System.currentTimeMillis() - timestamp
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days < 7 -> "${days}d ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }
}
