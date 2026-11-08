# Milestone M1 Handoff Report: Persistence & Companion UI

**Agent**: `m1_explorer_3`  
**Milestone**: M1 (Data Persistence & Companion Web Entry)  
**Date**: 2026-11-08T10:05:30Z  
**Target Recipient**: Orchestrator & M1 Workers  

---

## 1. Observation

Direct code observations from the FlowNotes repository:

### 1.1 Data Models (`app/src/main/java/com/flownotes/app/model/Models.kt`)
- `Models.kt:74-82`: Defines `StickyNote` model:
  ```kotlin
  data class StickyNote(
      val id: String = UUID.randomUUID().toString(),
      var x: Float,
      var y: Float,
      var width: Float = 200f,
      var height: Float = 180f,
      var content: String = "",
      val color: Int = 0xFFFEF08A.toInt()
  ) : Serializable
  ```
- `Models.kt:84-94`: `Page` contains `val stickyNotes: MutableList<StickyNote> = mutableListOf()`.
- `Models.kt:96-110`: `Notebook` contains `id`, `title`, `coverColor`, `coverPattern`, `folder`, `defaultPaperType`, `pages`, `pdfPath`, `createdAt`, `updatedAt`. It does **not** contain a list or container for `Flashcard` objects.
- `Models.kt:112-120`: Defines `Flashcard` with Spaced Repetition (SRS) state fields:
  ```kotlin
  data class Flashcard(
      val id: String = UUID.randomUUID().toString(),
      val front: String,
      val back: String,
      val hint: String = "",
      var reviewIntervalDays: Int = 1,
      var repetitions: Int = 0,
      var easeFactor: Float = 2.5f
  ) : Serializable
  ```

### 1.2 Persistence Implementation (`app/src/main/java/com/flownotes/app/data/NoteRepository.kt`)
- `NoteRepository.kt:19-21`:
  ```kotlin
  private val notebooksDir: File = File(context.filesDir, "notebooks").apply { mkdirs() }
  val pdfsDir: File = File(context.filesDir, "pdf").apply { mkdirs() }
  val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }
  ```
- `NoteRepository.kt:54-61`: File writing is **non-atomic**:
  ```kotlin
  fun saveNotebook(notebook: Notebook) {
      notebook.updatedAt = System.currentTimeMillis()
      val file = File(notebooksDir, "${notebook.id}.json")
      val json = serializeNotebook(notebook)
      FileOutputStream(file).use { out ->
          out.write(json.toString(2).toByteArray(StandardCharsets.UTF_8))
      }
  }
  ```
  Directly writing to `${notebook.id}.json` without a `.tmp` file or file descriptor sync leaves the file vulnerable to corruption/truncation if the OS process is killed during backgrounding (`onPause()`).
- `NoteRepository.kt:128-193` (`serializeNotebook`):
  - Serializes `strokes`, `tapes`, `textAnnotations`.
  - **Omission**: `page.stickyNotes` is never iterated or added to `pObj`.
  - **Omission**: `Flashcard` objects are never serialized.
- `NoteRepository.kt:210-290` (`parseNotebookJson`):
  - Parses `strokes`, `tapes`, `textAnnotations`.
  - **Omission**: `stickyNotes` array is never read or populated into `page.stickyNotes`.
  - **Omission**: `flashcards` array is never read.
- `NoteRepository.kt:162-175 & 251-268` (Active Recall Tape coordinates):
  - Kotlin serializes `left`, `top`, `right`, `bottom`.
  - Web companion (`js/canvas.js:35, 165-177`) generates `{ id, x, y, w, h, color, revealed }`.
  - In `parseNotebookJson`, missing `left`/`top` keys causes fallback to `0.0`, breaking coordinate positioning when notes are created or edited in the companion web UI.
- `NoteRepository.kt:17` (`constructor(context: Context)`):
  - Coupling solely to `android.content.Context` prevents running pure JVM unit tests on development systems without mocking the Android framework.

### 1.3 Companion Server & Web UI Assets
- `server.py:18-26`: Server binds to `127.0.0.1:8080`, serves static files from root (`FlowNotes/`), and provides API endpoints:
  - `GET /api/notes`: Lists notes from `FlowNotes/data/notebooks/*.json`
  - `GET /api/notes/<id>`: Returns individual note JSON
  - `POST /api/notes`: Saves note JSON
  - `DELETE /api/notes/<id>`: Deletes note JSON
  - `POST /api/upload-pdf`: Handles multipart PDF file upload
  - `POST /api/ai/chat`: Generates offline study summaries, formula breakdowns, step solver responses
  - `POST /api/ai/flashcards`: Generates high-yield study flashcards
- `server.py:142`: `meta.get("coverColor", "#3b82f6")` reads hex color strings, whereas Android models store signed 32-bit integers (`0xFF3B82F6.toInt()`).
- `css/style.css` (1,142 lines): Fully styled modern Freenotes interface:
  - Library View (`#library-view`, `.sidebar`, `.folder-nav`, `.library-main`, `.notebooks-grid`, `.notebook-card`)
  - Editor View (`#editor-view`, `.editor-header`, `.page-indicator-badge`, `.btn-ai-sparkle`)
  - Floating Pen Dock (`.floating-dock-container`, `.pen-dock`, `.dock-tool`, `.dock-subbar`, `.color-presets`, `.size-presets`)
  - Canvas Workspace (`.canvas-workspace`, `.paper-sheet-container#paper-sheet`, multi-layer canvas: `#bg-canvas`, `#draw-canvas`, `#tape-canvas`, `#ui-canvas`, `#objects-overlay`)
  - Sticky Notes (`.note-object`, `.sticky-note`, `.sticky-header`, `.sticky-content`)
  - Side Drawers (`.side-drawer#ai-drawer`, `.side-drawer#flashcard-drawer`, `.page-manager-drawer#page-manager-drawer`)
  - Modals (`#modal-new-notebook`, `#modal-templates`)
- `js/canvas.js` (602 lines): Canvas engine supporting Ballpoint, Fountain, Highlighter, Pencil, Stroke Eraser, Active Recall Tape (`toggleAllTapes`), shape snapping, lasso, laser pointer, and `exportAsImage()`.
- `js/templates.js` (247 lines): Procedural paper rendering: Cornell, Ruled, College Ruled, Grid, Dotted, Planner, Dark Grid, Sepia, Blank.
- `lib/`: Contains `lucide.min.js`, `jspdf.umd.min.js`, `pdf.min.js`, `pdf.worker.min.js`.
- `index.html`: **Missing from project root**.

---

## 2. Logic Chain

1. **Model Persistence Gap**:
   - `StickyNote` exists in `Models.kt` (lines 74-82) and is used in the UI for user reminders, annotations, and study tips.
   - Because `NoteRepository.serializeNotebook` omits `page.stickyNotes` and `parseNotebookJson` omits reading `"stickyNotes"`, any sticky note added on canvas is lost immediately upon app restart or backgrounding.
   - Therefore, `NoteRepository` must explicitly serialize and deserialize `page.stickyNotes` preserving `id`, `x`, `y`, `width`, `height`, `content`, and `color`.

2. **Flashcard SRS Retention Gap**:
   - `Flashcard` in `Models.kt` defines `reviewIntervalDays`, `repetitions`, and `easeFactor` for SuperMemo SM-2 spaced repetition (Feature #19, #34).
   - In `FlashcardStudyActivity.kt:67-70`, user rating clicks update review intervals.
   - Without storing these cards within `Notebook.flashcards` (or in a repository deck store), every study session regenerates fresh cards with reset intervals (`reviewIntervalDays = 1`, `repetitions = 0`), breaking spaced repetition continuity.
   - Adding `val flashcards: MutableList<Flashcard> = mutableListOf()` to `Notebook` and serializing it into the notebook JSON preserves study progress across restarts.

3. **Data Loss via Non-Atomic Writes**:
   - Android lifecycle triggers `saveNotebook()` in `NoteEditorActivity.onPause()`.
   - Android OS can kill the process at any moment while backgrounded.
   - Directly opening `FileOutputStream(file)` truncates the existing file before writing new data. If interrupted, the file becomes 0 bytes or incomplete JSON, causing unrecoverable data loss.
   - Writing to a `.tmp` file, calling `out.fd.sync()`, and atomically renaming `tempFile.renameTo(targetFile)` guarantees zero corruption.

4. **Web Companion & Android Schema Harmonization**:
   - `canvas.js` tracks tapes with `{ x, y, w, h }`.
   - Android tracks tapes with `{ left, top, right, bottom }`.
   - Supporting both schemas in `parseNotebookJson` (`left = if (has("left")) left else x`, `right = if (has("right")) right else (x + w)`) ensures seamless interoperability between web companion and native Android app.
   - Supporting both integer color codes (`0xFF3B82F6`) and web hex strings (`"#3b82f6"`) prevents `NumberFormatException` when loading notes across platforms.

5. **Testability Decoupling**:
   - Adding a primary constructor `NoteRepository(val baseDir: File)` with secondary constructor `constructor(context: Context) : this(context.filesDir)` allows pure JVM unit tests (`NoteRepositoryTest.kt`) to instantiate the repository using temporary directories (`java.io.tmpdir`), achieving fast, hermetic test execution without Android mocks.

6. **Web Companion Completion**:
   - `server.py`, `style.css`, `canvas.js`, `templates.js`, and vendor libraries are completely functional.
   - The only missing component for running the companion UI at `http://127.0.0.1:8080` is `index.html`.
   - Creating `index.html` connecting all stylesheets, scripts, drawers, floating pen dock, and REST API calls completes the companion web entry.

---

## 3. Caveats

1. **Directory Independence**: Android private storage resides at `/data/user/0/com.flownotes.app/files/notebooks/`, whereas `server.py` reads from `/storage/emulated/0/FlowNotes/data/notebooks/`. They share identical JSON schemas. In Milestone M1, companion web verification is conducted via `server.py` on `127.0.0.1:8080`, while Android APK verification operates in internal app-private storage.
2. **PDF Native Rendering vs PDF.js**: In Android, native `PdfRenderer` is used for canvas page backgrounds (Milestone M4). In the web companion, `lib/pdf.min.js` and `jspdf.umd.min.js` are used for browser rendering and multi-page export.
3. **No direct code write restriction**: In accordance with the Explorer archetype instructions, all proposals are created in `/storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_3/` ready for the Worker to copy/apply.

---

## 4. Conclusion

1. **`NoteRepository.kt` & `Models.kt` Fixes Required**:
   - Add `val flashcards: MutableList<Flashcard> = mutableListOf()` to `Notebook` in `Models.kt`.
   - Update `NoteRepository.kt` to:
     - Serialize and deserialize `StickyNote` on every `Page`.
     - Serialize and deserialize `Flashcard` on every `Notebook`.
     - Implement atomic file writing using `.tmp` file and `fd.sync()` before rename.
     - Add `baseDir: File` constructor for testability.
     - Add flexible color parsing (supporting hex `#RRGGBB` and signed `Int`).
     - Harmonize tape coordinates (`left/top/right/bottom` $\leftrightarrow$ `x/y/w/h`).
   - Implementation artifact ready: `proposed_NoteRepository.kt` and `proposed_Models.kt`.

2. **Web Companion `index.html` Designed & Delivered**:
   - Complete `index.html` authored linking `css/style.css`, `lib/lucide.min.js`, `lib/jspdf.umd.min.js`, `lib/pdf.min.js`, `js/templates.js`, and `js/canvas.js`.
   - Delivers interactive notebook creation, procedural paper rendering, digital ink drawing with 4 pen profiles, Active Recall Tape (tap reveal/conceal, Reveal All, Conceal All), draggable sticky notes, 3D card flip study session with SuperMemo SM-2 ratings, and multi-page PDF export.
   - Implementation artifact ready: `proposed_index.html`.

3. **Unit Test Specifications Defined**:
   - `proposed_ModelSerializationTest.kt`: 4 detailed test methods testing full roundtrip, web schema compatibility, missing fields fallback, and Unicode formula preservation.
   - `proposed_NoteRepositoryTest.kt`: 7 detailed test methods testing directory creation, default notebooks generation, save/get roundtrip, multi-page addition, deletion, updatedAt ordering, and atomic file safety.

---

## 5. Verification Method

### 5.1 Artifact Files to Inspect
Review the generated specification and implementation artifacts in:
- `/storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_3/proposed_index.html`
- `/storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_3/proposed_NoteRepository.kt`
- `/storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_3/proposed_Models.kt`
- `/storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_3/proposed_ModelSerializationTest.kt`
- `/storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_3/proposed_NoteRepositoryTest.kt`

### 5.2 Worker Deployment Instructions
The Worker should execute the following steps:
1. Copy `proposed_Models.kt` to `app/src/main/java/com/flownotes/app/model/Models.kt`.
2. Copy `proposed_NoteRepository.kt` to `app/src/main/java/com/flownotes/app/data/NoteRepository.kt`.
3. Copy `proposed_index.html` to `index.html`.
4. Copy `proposed_ModelSerializationTest.kt` to `app/src/test/java/com/flownotes/app/model/ModelSerializationTest.kt`.
5. Copy `proposed_NoteRepositoryTest.kt` to `app/src/test/java/com/flownotes/app/data/NoteRepositoryTest.kt`.

### 5.3 Local Verification Commands
1. **Companion Web UI Verification**:
   ```bash
   cd /storage/emulated/0/FlowNotes
   python3 server.py
   ```
   Navigate to `http://127.0.0.1:8080/` in browser:
   - Verify notebook shelf displays notebooks.
   - Tap "New Notebook" -> create Cornell notebook.
   - Draw strokes with Ballpoint and Fountain pen.
   - Select Tape Tool -> drag colored active recall tape over text.
   - Tap tape to toggle reveal/conceal.
   - Tap "AI Study" -> test on-device Study Summary.
   - Tap "Flashcards" -> test 3D card flip and SM-2 ratings.
   - Tap "Export PDF" -> verify downloaded multi-page PDF.

2. **Unit Test Verification (JVM / kotlinc)**:
   ```bash
   cd /storage/emulated/0/FlowNotes
   # Compile and run ModelSerializationTest & NoteRepositoryTest with JUnit 4 runner
   kotlinc -cp "app/src/main/java:lib/*" \
     app/src/test/java/com/flownotes/app/model/ModelSerializationTest.kt \
     app/src/test/java/com/flownotes/app/data/NoteRepositoryTest.kt
   ```

### 5.4 Invalidation Conditions
- Any serialization schema that drops `StickyNote` fields or resets `Flashcard` SM-2 intervals.
- Direct non-atomic file writes that truncate files without `.tmp` staging.
- Discrepancies between `js/canvas.js` DOM element IDs and `index.html` IDs.
