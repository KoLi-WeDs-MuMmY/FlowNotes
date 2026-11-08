package com.flownotes.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flownotes.app.data.NoteRepository
import com.flownotes.app.model.Notebook
import com.flownotes.app.model.Page
import com.flownotes.app.model.PaperType
import com.flownotes.app.pdf.PdfAnnotationManager
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var repository: NoteRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotebookAdapter
    private var allNotebooks = mutableListOf<Notebook>()
    private var currentFolder = "All Notes"

    companion object {
        private const val REQUEST_PICK_PDF = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = NoteRepository(this)
        setupViews()
        loadNotebooks()
    }

    override fun onResume() {
        super.onResume()
        loadNotebooks()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.rv_notebooks)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = NotebookAdapter(
            onNotebookClick = { nb -> openNotebook(nb) },
            onNotebookLongClick = { nb -> showNotebookOptions(nb) }
        )
        recyclerView.adapter = adapter

        // Folders list
        findViewById<View>(R.id.btn_folder_all).setOnClickListener { setFolder("All Notes") }
        findViewById<View>(R.id.btn_folder_study).setOnClickListener { setFolder("Study") }
        findViewById<View>(R.id.btn_folder_work).setOnClickListener { setFolder("Work") }
        findViewById<View>(R.id.btn_folder_personal).setOnClickListener { setFolder("Personal") }

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

        // Search Bar filter
        findViewById<SearchView>(R.id.search_view).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterNotebooks(newText ?: "")
                return true
            }
        })
    }

    private fun setFolder(folder: String) {
        currentFolder = folder
        findViewById<TextView>(R.id.tv_folder_title).text = folder
        filterNotebooks("")
    }

    private fun loadNotebooks() {
        allNotebooks = repository.getAllNotebooks().toMutableList()
        filterNotebooks("")
    }

    private fun filterNotebooks(query: String) {
        val filtered = allNotebooks.filter { nb ->
            val matchFolder = if (currentFolder == "All Notes") true else nb.folder == currentFolder
            val matchQuery = nb.title.contains(query, ignoreCase = true)
            matchFolder && matchQuery
        }
        adapter.submitList(filtered)
    }

    private fun openNotebook(notebook: Notebook) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            putExtra("EXTRA_NOTEBOOK_ID", notebook.id)
        }
        startActivity(intent)
    }

    private fun showNewNotebookDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_notebook, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_notebook_title)
        val spTemplate = dialogView.findViewById<Spinner>(R.id.sp_paper_template)

        val templates = PaperType.values().map { it.name }
        spTemplate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, templates)

        var selectedColor = 0xFF3B82F6.toInt()
        val colors = listOf(0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF1E293B.toInt())

        AlertDialog.Builder(this)
            .setTitle("Create Digital Notebook")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val title = etTitle.text.toString().ifBlank { "My Notebook" }
                val template = PaperType.values()[spTemplate.selectedItemPosition]
                val newNb = Notebook(
                    title = title,
                    coverColor = selectedColor,
                    folder = if (currentFolder == "All Notes") "Study" else currentFolder,
                    defaultPaperType = template
                ).apply {
                    pages.add(Page(pageNumber = 1, paperType = template))
                }
                repository.saveNotebook(newNb)
                loadNotebooks()
                openNotebook(newNb)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNotebookOptions(notebook: Notebook) {
        val options = arrayOf("Open", "Rename", "Export as PDF", "Delete")
        AlertDialog.Builder(this)
            .setTitle(notebook.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openNotebook(notebook)
                    1 -> showRenameDialog(notebook)
                    2 -> exportNotebookToPdf(notebook)
                    3 -> {
                        repository.deleteNotebook(notebook.id)
                        loadNotebooks()
                        Toast.makeText(this, "Notebook deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showRenameDialog(notebook: Notebook) {
        val input = EditText(this).apply { setText(notebook.title) }
        AlertDialog.Builder(this)
            .setTitle("Rename Notebook")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    notebook.title = newTitle
                    repository.saveNotebook(notebook)
                    loadNotebooks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportNotebookToPdf(notebook: Notebook) {
        val pdfManager = PdfAnnotationManager(this)
        val exportFile = File(getExternalFilesDir(null), "${notebook.title.replace(" ", "_")}.pdf")
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
                pdfPath = destFile.absolutePath
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

            repository.saveNotebook(newNb)
            loadNotebooks()
            openNotebook(newNb)
            Toast.makeText(this, "Imported PDF ($pageCount pages)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to import PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class NotebookAdapter(
    private val onNotebookClick: (Notebook) -> Unit,
    private val onNotebookLongClick: (Notebook) -> Unit
) : RecyclerView.Adapter<NotebookAdapter.ViewHolder>() {

    private var items = listOf<Notebook>()

    fun submitList(list: List<Notebook>) {
        items = list
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
        private val tvPages: TextView = itemView.findViewById(R.id.tv_card_pages)
        private val coverView: View = itemView.findViewById(R.id.view_card_cover)
        private val tvBadge: TextView = itemView.findViewById(R.id.tv_cover_badge)

        fun bind(item: Notebook) {
            tvTitle.text = item.title
            tvPages.text = "${item.pageCount} pages"
            coverView.setBackgroundColor(item.coverColor)
            tvBadge.text = item.title

            itemView.setOnClickListener { onNotebookClick(item) }
            itemView.setOnLongClickListener {
                onNotebookLongClick(item)
                true
            }
        }
    }
}
