package com.flownotes.app.data

import android.content.Context
import com.flownotes.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * 100% On-Device Local Repository.
 * Saves notebooks, pages, strokes, active recall tapes, sticky notes, and flashcards
 * directly to device private storage.
 * Zero server required, zero cloud cost, 100% private and persistent.
 */
class NoteRepository(val baseDir: File) {

    // Secondary constructor for Android Context integration
    constructor(context: Context) : this(context.filesDir)

    val notebooksDir: File = File(baseDir, "notebooks").apply { mkdirs() }
    val pdfsDir: File = File(baseDir, "pdf").apply { mkdirs() }
    val audioDir: File = File(baseDir, "audio").apply { mkdirs() }

    init {
        ensureDefaultNotebooks()
    }

    fun getAllNotebooks(): List<Notebook> {
        if (!notebooksDir.exists()) notebooksDir.mkdirs()
        val list = mutableListOf<Notebook>()
        val files = notebooksDir.listFiles { _, name -> name.endsWith(".json") } ?: return list

        for (f in files) {
            try {
                val jsonStr = f.readText(StandardCharsets.UTF_8)
                val nb = parseNotebookJson(JSONObject(jsonStr))
                list.add(nb)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun getNotebook(id: String): Notebook? {
        val file = File(notebooksDir, "$id.json")
        if (!file.exists()) return null
        return try {
            val jsonStr = file.readText(StandardCharsets.UTF_8)
            parseNotebookJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Atomically saves notebook using a temporary file rename pattern.
     * Prevents file corruption during unexpected app termination or power loss.
     */
    fun saveNotebook(notebook: Notebook) {
        notebook.updatedAt = System.currentTimeMillis()
        if (!notebooksDir.exists()) notebooksDir.mkdirs()

        val targetFile = File(notebooksDir, "${notebook.id}.json")
        val tempFile = File(notebooksDir, "${notebook.id}.tmp")
        val json = serializeNotebook(notebook)

        FileOutputStream(tempFile).use { out ->
            out.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
            out.fd.sync()
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    fun deleteNotebook(id: String): Boolean {
        val file = File(notebooksDir, "$id.json")
        return file.delete()
    }

    private fun ensureDefaultNotebooks() {
        val jsonFiles = notebooksDir.listFiles { _, name -> name.endsWith(".json") }
        if (jsonFiles.isNullOrEmpty()) {
            // 1. Welcome study notebook (demonstrating Cornell layout & Active Recall Tape)
            val welcomeNote = Notebook(
                title = "Welcome to FlowNotes",
                coverColor = 0xFF3B82F6.toInt(),
                coverPattern = "modern",
                folder = "All Notes",
                defaultPaperType = PaperType.CORNELL
            ).apply {
                val p1 = Page(pageNumber = 1, paperType = PaperType.CORNELL)
                p1.textAnnotations.add(
                    TextAnnotation(
                        x = 100f,
                        y = 100f,
                        text = "FlowNotes: Realistic Handwriting & Active Recall",
                        fontSize = 20f
                    )
                )
                // Sample active recall tape concealing key definition
                p1.tapes.add(
                    ActiveRecallTape(
                        left = 180f,
                        top = 320f,
                        right = 520f,
                        bottom = 370f,
                        color = 0xFFFEF08A.toInt(),
                        isRevealed = false
                    )
                )
                // Sample sticky note
                p1.stickyNotes.add(
                    StickyNote(
                        x = 550f,
                        y = 200f,
                        width = 220f,
                        height = 160f,
                        content = "Tip: Tap any tape rectangle to reveal or conceal answers!",
                        color = 0xFFFEF08A.toInt()
                    )
                )
                pages.add(p1)
                pages.add(Page(pageNumber = 2, paperType = PaperType.GRID))
                pages.add(Page(pageNumber = 3, paperType = PaperType.PLANNER))

                // Default flashcards for active recall
                flashcards.add(
                    Flashcard(
                        front = "What is the primary topic of this notebook?",
                        back = "FlowNotes — realistic handwriting & active recall study engine.",
                        hint = "App Introduction"
                    )
                )
                flashcards.add(
                    Flashcard(
                        front = "What is the benefit of Active Recall Tape?",
                        back = "Conceals keywords so you can test your memory retrieval before tapping to reveal.",
                        hint = "Memory retention"
                    )
                )
            }
            saveNotebook(welcomeNote)

            // 2. Physics & Mathematics notebook
            val scienceNote = Notebook(
                title = "Mathematics & Physics Notes",
                coverColor = 0xFF8B5CF6.toInt(),
                coverPattern = "modern",
                folder = "Study",
                defaultPaperType = PaperType.GRID
            ).apply {
                pages.add(Page(pageNumber = 1, paperType = PaperType.GRID))
            }
            saveNotebook(scienceNote)
        }
    }

    fun serializeNotebook(nb: Notebook): JSONObject {
        val root = JSONObject()
        root.put("id", nb.id)
        root.put("title", nb.title)
        root.put("coverColor", nb.coverColor)
        root.put("coverPattern", nb.coverPattern)
        root.put("folder", nb.folder)
        root.put("defaultPaperType", nb.defaultPaperType.name)
        root.put("pdfPath", nb.pdfPath ?: JSONObject.NULL)
        root.put("createdAt", nb.createdAt)
        root.put("updatedAt", nb.updatedAt)

        // Serialize Pages
        val pagesArr = JSONArray()
        for (page in nb.pages) {
            val pObj = JSONObject()
            pObj.put("id", page.id)
            pObj.put("pageNumber", page.pageNumber)
            pObj.put("paperType", page.paperType.name)
            pObj.put("pdfPageIndex", page.pdfPageIndex ?: JSONObject.NULL)
            pObj.put("audioMemoUri", page.audioMemoUri ?: JSONObject.NULL)

            // Strokes
            val strokesArr = JSONArray()
            for (stroke in page.strokes) {
                val sObj = JSONObject()
                sObj.put("id", stroke.id)
                sObj.put("tool", stroke.tool.name)
                sObj.put("color", stroke.color)
                sObj.put("strokeWidth", stroke.strokeWidth.toDouble())
                sObj.put("shapeType", stroke.shapeType ?: JSONObject.NULL)

                val ptsArr = JSONArray()
                for (pt in stroke.points) {
                    val ptObj = JSONObject()
                    ptObj.put("x", pt.x.toDouble())
                    ptObj.put("y", pt.y.toDouble())
                    ptObj.put("p", pt.pressure.toDouble())
                    ptObj.put("t", pt.timestamp)
                    ptsArr.put(ptObj)
                }
                sObj.put("points", ptsArr)
                strokesArr.put(sObj)
            }
            pObj.put("strokes", strokesArr)

            // Active Recall Tapes
            val tapesArr = JSONArray()
            for (tape in page.tapes) {
                val tObj = JSONObject()
                tObj.put("id", tape.id)
                tObj.put("left", tape.left.toDouble())
                tObj.put("top", tape.top.toDouble())
                tObj.put("right", tape.right.toDouble())
                tObj.put("bottom", tape.bottom.toDouble())
                tObj.put("color", tape.color)
                tObj.put("revealed", tape.isRevealed)
                tapesArr.put(tObj)
            }
            pObj.put("tapes", tapesArr)

            // Text Annotations
            val textsArr = JSONArray()
            for (txt in page.textAnnotations) {
                val txtObj = JSONObject()
                txtObj.put("id", txt.id)
                txtObj.put("x", txt.x.toDouble())
                txtObj.put("y", txt.y.toDouble())
                txtObj.put("text", txt.text)
                txtObj.put("color", txt.color)
                txtObj.put("fontSize", txt.fontSize.toDouble())
                textsArr.put(txtObj)
            }
            pObj.put("textAnnotations", textsArr)

            // Sticky Notes (Preserved & serialized)
            val stickiesArr = JSONArray()
            for (sticky in page.stickyNotes) {
                val stObj = JSONObject()
                stObj.put("id", sticky.id)
                stObj.put("x", sticky.x.toDouble())
                stObj.put("y", sticky.y.toDouble())
                stObj.put("width", sticky.width.toDouble())
                stObj.put("height", sticky.height.toDouble())
                stObj.put("content", sticky.content)
                stObj.put("color", sticky.color)
                stickiesArr.put(stObj)
            }
            pObj.put("stickyNotes", stickiesArr)

            pagesArr.put(pObj)
        }
        root.put("pages", pagesArr)

        // Flashcards SRS serialization
        val flashcardsArr = JSONArray()
        for (card in nb.flashcards) {
            val cObj = JSONObject()
            cObj.put("id", card.id)
            cObj.put("front", card.front)
            cObj.put("back", card.back)
            cObj.put("hint", card.hint)
            cObj.put("reviewIntervalDays", card.reviewIntervalDays)
            cObj.put("repetitions", card.repetitions)
            cObj.put("easeFactor", card.easeFactor.toDouble())
            flashcardsArr.put(cObj)
        }
        root.put("flashcards", flashcardsArr)

        return root
    }

    fun parseNotebookJson(json: JSONObject): Notebook {
        val nbId = json.optString("id").ifEmpty { UUID.randomUUID().toString() }
        val nb = Notebook(
            id = nbId,
            title = json.optString("title", "Untitled Note"),
            coverColor = parseColor(json, "coverColor", 0xFF3B82F6.toInt()),
            coverPattern = json.optString("coverPattern", "modern"),
            folder = json.optString("folder", "All Notes"),
            defaultPaperType = runCatching { PaperType.valueOf(json.optString("defaultPaperType", "RULED")) }.getOrDefault(PaperType.RULED),
            pdfPath = if (json.isNull("pdfPath")) null else json.optString("pdfPath"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )

        // Parse Pages
        val pagesArr = json.optJSONArray("pages")
        if (pagesArr != null) {
            for (i in 0 until pagesArr.length()) {
                val pObj = pagesArr.getJSONObject(i)
                val pId = pObj.optString("id").ifEmpty { UUID.randomUUID().toString() }
                val page = Page(
                    id = pId,
                    pageNumber = pObj.optInt("pageNumber", i + 1),
                    paperType = runCatching { PaperType.valueOf(pObj.optString("paperType", "RULED")) }.getOrDefault(PaperType.RULED),
                    pdfPageIndex = if (pObj.isNull("pdfPageIndex")) null else pObj.optInt("pdfPageIndex"),
                    audioMemoUri = if (pObj.isNull("audioMemoUri")) null else pObj.optString("audioMemoUri")
                )

                // Parse strokes
                val strokesArr = pObj.optJSONArray("strokes")
                if (strokesArr != null) {
                    for (j in 0 until strokesArr.length()) {
                        val sObj = strokesArr.getJSONObject(j)
                        val stroke = Stroke(
                            id = sObj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                            tool = runCatching { PenTool.valueOf(sObj.optString("tool", "BALLPOINT")) }.getOrDefault(PenTool.BALLPOINT),
                            color = parseColor(sObj, "color", 0xFF1E293B.toInt()),
                            strokeWidth = sObj.optDouble("strokeWidth", 3.0).toFloat(),
                            shapeType = if (sObj.isNull("shapeType")) null else sObj.optString("shapeType")
                        )
                        val ptsArr = sObj.optJSONArray("points")
                        if (ptsArr != null) {
                            for (k in 0 until ptsArr.length()) {
                                val ptObj = ptsArr.getJSONObject(k)
                                stroke.points.add(
                                    Point(
                                        x = ptObj.optDouble("x").toFloat(),
                                        y = ptObj.optDouble("y").toFloat(),
                                        pressure = ptObj.optDouble("p", ptObj.optDouble("pressure", 0.5)).toFloat(),
                                        timestamp = ptObj.optLong("t", ptObj.optLong("time", 0L))
                                    )
                                )
                            }
                        }
                        page.strokes.add(stroke)
                    }
                }

                // Parse tapes (support both left/top/right/bottom and x/y/w/h web formats)
                val tapesArr = pObj.optJSONArray("tapes")
                if (tapesArr != null) {
                    for (j in 0 until tapesArr.length()) {
                        val tObj = tapesArr.getJSONObject(j)
                        val left = if (tObj.has("left")) tObj.optDouble("left").toFloat() else tObj.optDouble("x", 0.0).toFloat()
                        val top = if (tObj.has("top")) tObj.optDouble("top").toFloat() else tObj.optDouble("y", 0.0).toFloat()
                        val right = if (tObj.has("right")) tObj.optDouble("right").toFloat() else (left + tObj.optDouble("w", 120.0).toFloat())
                        val bottom = if (tObj.has("bottom")) tObj.optDouble("bottom").toFloat() else (top + tObj.optDouble("h", 28.0).toFloat())
                        val isRevealed = if (tObj.has("revealed")) tObj.optBoolean("revealed", false) else tObj.optBoolean("isRevealed", false)

                        page.tapes.add(
                            ActiveRecallTape(
                                id = tObj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                                left = left,
                                top = top,
                                right = right,
                                bottom = bottom,
                                color = parseColor(tObj, "color", 0xFFFEF08A.toInt()),
                                isRevealed = isRevealed
                            )
                        )
                    }
                }

                // Parse text annotations
                val txtsArr = pObj.optJSONArray("textAnnotations")
                if (txtsArr != null) {
                    for (j in 0 until txtsArr.length()) {
                        val tObj = txtsArr.getJSONObject(j)
                        page.textAnnotations.add(
                            TextAnnotation(
                                id = tObj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                                x = tObj.optDouble("x").toFloat(),
                                y = tObj.optDouble("y").toFloat(),
                                text = tObj.optString("text"),
                                color = parseColor(tObj, "color", 0xFF1E293B.toInt()),
                                fontSize = tObj.optDouble("fontSize", 16.0).toFloat()
                            )
                        )
                    }
                }

                // Parse sticky notes
                val stickiesArr = pObj.optJSONArray("stickyNotes")
                if (stickiesArr != null) {
                    for (j in 0 until stickiesArr.length()) {
                        val stObj = stickiesArr.getJSONObject(j)
                        page.stickyNotes.add(
                            StickyNote(
                                id = stObj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                                x = stObj.optDouble("x", 0.0).toFloat(),
                                y = stObj.optDouble("y", 0.0).toFloat(),
                                width = stObj.optDouble("width", 200.0).toFloat(),
                                height = stObj.optDouble("height", 180.0).toFloat(),
                                content = stObj.optString("content", ""),
                                color = parseColor(stObj, "color", 0xFFFEF08A.toInt())
                            )
                        )
                    }
                }

                nb.pages.add(page)
            }
        }

        // Parse Flashcards
        val flashcardsArr = json.optJSONArray("flashcards")
        if (flashcardsArr != null) {
            for (i in 0 until flashcardsArr.length()) {
                val cObj = flashcardsArr.getJSONObject(i)
                nb.flashcards.add(
                    Flashcard(
                        id = cObj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                        front = cObj.optString("front", ""),
                        back = cObj.optString("back", ""),
                        hint = cObj.optString("hint", ""),
                        reviewIntervalDays = cObj.optInt("reviewIntervalDays", 1),
                        repetitions = cObj.optInt("repetitions", 0),
                        easeFactor = cObj.optDouble("easeFactor", 2.5).toFloat()
                    )
                )
            }
        }

        if (nb.pages.isEmpty()) {
            nb.pages.add(Page(pageNumber = 1, paperType = nb.defaultPaperType))
        }

        return nb
    }

    private fun parseColor(obj: JSONObject, key: String, defaultColor: Int): Int {
        if (!obj.has(key)) return defaultColor
        val value = obj.opt(key)
        return when (value) {
            null -> defaultColor
            is Number -> value.toInt()
            is String -> {
                if (value.startsWith("#")) {
                    try {
                        val hex = value.removePrefix("#")
                        if (hex.length == 6) {
                            (0xFF000000 or hex.toLong(16)).toInt()
                        } else {
                            hex.toLong(16).toInt()
                        }
                    } catch (e: Exception) {
                        defaultColor
                    }
                } else defaultColor
            }
            else -> defaultColor
        }
    }
}
