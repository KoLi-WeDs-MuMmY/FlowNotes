package com.flownotes.app.data

import android.content.Context
import com.flownotes.app.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * 100% On-Device Local Repository.
 * Saves notebooks, pages, strokes, and active recall tapes directly to device private storage.
 * Zero server required, zero cloud cost, 100% private and persistent.
 */
class NoteRepository(private val context: Context) {

    private val notebooksDir: File = File(context.filesDir, "notebooks").apply { mkdirs() }
    val pdfsDir: File = File(context.filesDir, "pdf").apply { mkdirs() }
    val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }

    init {
        ensureDefaultNotebooks()
    }

    fun getAllNotebooks(): List<Notebook> {
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

    fun saveNotebook(notebook: Notebook) {
        notebook.updatedAt = System.currentTimeMillis()
        val file = File(notebooksDir, "${notebook.id}.json")
        val json = serializeNotebook(notebook)
        FileOutputStream(file).use { out ->
            out.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
        }
    }

    fun deleteNotebook(id: String): Boolean {
        val file = File(notebooksDir, "$id.json")
        return file.delete()
    }

    private fun ensureDefaultNotebooks() {
        if (notebooksDir.listFiles()?.isEmpty() != false) {
            // Create welcome study notebook
            val welcomeNote = Notebook(
                title = "Welcome to FlowNotes",
                coverColor = 0xFF3B82F6.toInt(),
                folder = "All Notes",
                defaultPaperType = PaperType.CORNELL
            ).apply {
                val p1 = Page(pageNumber = 1, paperType = PaperType.CORNELL)
                p1.textAnnotations.add(
                    TextAnnotation(
                        x = 100f,
                        y = 100f,
                        text = "Freenotes Replica: Realistic Handwriting & Active Recall",
                        fontSize = 20f
                    )
                )
                // Add a sample active recall tape
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
                pages.add(p1)
                pages.add(Page(pageNumber = 2, paperType = PaperType.GRID))
                pages.add(Page(pageNumber = 3, paperType = PaperType.PLANNER))
            }
            saveNotebook(welcomeNote)

            // Create Physics & Math notebook
            val scienceNote = Notebook(
                title = "Mathematics & Physics Notes",
                coverColor = 0xFF8B5CF6.toInt(),
                folder = "Study",
                defaultPaperType = PaperType.GRID
            ).apply {
                pages.add(Page(pageNumber = 1, paperType = PaperType.GRID))
            }
            saveNotebook(scienceNote)
        }
    }

    private fun serializeNotebook(nb: Notebook): JSONObject {
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

            // Tapes
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

            // Text annotations
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

            pagesArr.put(pObj)
        }
        root.put("pages", pagesArr)
        return root
    }

    private fun parseNotebookJson(json: JSONObject): Notebook {
        val nb = Notebook(
            id = json.optString("id"),
            title = json.optString("title", "Untitled Note"),
            coverColor = json.optInt("coverColor", 0xFF3B82F6.toInt()),
            coverPattern = json.optString("coverPattern", "modern"),
            folder = json.optString("folder", "All Notes"),
            defaultPaperType = runCatching { PaperType.valueOf(json.optString("defaultPaperType", "RULED")) }.getOrDefault(PaperType.RULED),
            pdfPath = if (json.isNull("pdfPath")) null else json.optString("pdfPath"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )

        val pagesArr = json.optJSONArray("pages")
        if (pagesArr != null) {
            for (i in 0 until pagesArr.length()) {
                val pObj = pagesArr.getJSONObject(i)
                val page = Page(
                    id = pObj.optString("id"),
                    pageNumber = pObj.optInt("pageNumber", i + 1),
                    paperType = runCatching { PaperType.valueOf(pObj.optString("paperType", "RULED")) }.getOrDefault(PaperType.RULED),
                    pdfPageIndex = if (pObj.isNull("pdfPageIndex")) null else pObj.optInt("pdfPageIndex"),
                    audioMemoUri = if (pObj.isNull("audioMemoUri")) null else pObj.optString("audioMemoUri")
                )

                // parse strokes
                val strokesArr = pObj.optJSONArray("strokes")
                if (strokesArr != null) {
                    for (j in 0 until strokesArr.length()) {
                        val sObj = strokesArr.getJSONObject(j)
                        val stroke = Stroke(
                            id = sObj.optString("id"),
                            tool = runCatching { PenTool.valueOf(sObj.optString("tool", "BALLPOINT")) }.getOrDefault(PenTool.BALLPOINT),
                            color = sObj.optInt("color", 0xFF1E293B.toInt()),
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
                                        pressure = ptObj.optDouble("p", 0.5).toFloat(),
                                        timestamp = ptObj.optLong("t", 0L)
                                    )
                                )
                            }
                        }
                        page.strokes.add(stroke)
                    }
                }

                // parse tapes
                val tapesArr = pObj.optJSONArray("tapes")
                if (tapesArr != null) {
                    for (j in 0 until tapesArr.length()) {
                        val tObj = tapesArr.getJSONObject(j)
                        page.tapes.add(
                            ActiveRecallTape(
                                id = tObj.optString("id"),
                                left = tObj.optDouble("left").toFloat(),
                                top = tObj.optDouble("top").toFloat(),
                                right = tObj.optDouble("right").toFloat(),
                                bottom = tObj.optDouble("bottom").toFloat(),
                                color = tObj.optInt("color", 0xFFFEF08A.toInt()),
                                isRevealed = tObj.optBoolean("revealed", false)
                            )
                        )
                    }
                }

                // parse text annotations
                val txtsArr = pObj.optJSONArray("textAnnotations")
                if (txtsArr != null) {
                    for (j in 0 until txtsArr.length()) {
                        val tObj = txtsArr.getJSONObject(j)
                        page.textAnnotations.add(
                            TextAnnotation(
                                id = tObj.optString("id"),
                                x = tObj.optDouble("x").toFloat(),
                                y = tObj.optDouble("y").toFloat(),
                                text = tObj.optString("text"),
                                color = tObj.optInt("color", 0xFF1E293B.toInt()),
                                fontSize = tObj.optDouble("fontSize", 16.0).toFloat()
                            )
                        )
                    }
                }

                nb.pages.add(page)
            }
        }

        if (nb.pages.isEmpty()) {
            nb.pages.add(Page(pageNumber = 1, paperType = nb.defaultPaperType))
        }

        return nb
    }
}
