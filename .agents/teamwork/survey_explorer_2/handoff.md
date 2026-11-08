# Technical Architecture & Modular Decomposition Report: FlowNotes

**Author**: survey_explorer_2 (Technical Architecture Explorer)  
**Date**: 2026-11-08  
**Project**: FlowNotes (100% Offline Native Android Digital Note-Taking & PDF Annotation App in Kotlin)  
**Target File**: `/storage/emulated/0/FlowNotes/.agents/teamwork/survey_explorer_2/handoff.md`  
**Reference Document**: `/storage/emulated/0/FlowNotes/.agents/teamwork/ORIGINAL_REQUEST.md`  

---

## 1. Observation

### 1.1 Existing Codebase & Environment Inventory
Direct inspection of `/storage/emulated/0/FlowNotes` revealed the following structural layout:

- **Authoritative Specifications**:
  - `/storage/emulated/0/FlowNotes/.agents/teamwork/ORIGINAL_REQUEST.md`: Explicitly mandates a 100% offline, native Android digital note-taking and PDF annotation app in Kotlin replicating Freenotes (`ai.note.notepad.memo.journal.freenote`) with zero backend, zero hosting costs, and zero external API dependencies.
  - Core requirements include:
    - `R1`: Native Handwriting & Digital Ink Engine (`CanvasView`, Bezier/spline curves, ballpoint, fountain, highlighter, pencil, stroke/pixel eraser, shape recognizer, undo/redo).
    - `R2`: Active Recall Study Tape & Flashcards (drag-to-create tape rectangles, tap-to-reveal/conceal, reveal/conceal all, 3D card flipping, SRS study mode).
    - `R3`: Procedural Paper Templates & PDF Annotation (Cornell, 5mm Grid, Dot Grid, Ruled, College Ruled, Weekly Planner, Sepia, Dark mode; `PdfRenderer` import, `PdfDocument` export).
    - `R4`: Voice Memo Audio Recording (`MediaRecorder` / `MediaPlayer` synced to notebook pages).
    - `R5`: Zero-Cost, 100% Offline Architecture (local JSON/SQLite storage, on-device extractive summarizer, formula extractor, auto flashcard generator).

- **Current Android Kotlin Files** in `/storage/emulated/0/FlowNotes/app/src/main/java/com/flownotes/app/`:
  1. `model/Models.kt` (121 lines): Defines `PenTool`, `PaperType`, `Point`, `Stroke`, `ActiveRecallTape`, `TextAnnotation`, `StickyNote`, `Page`, `Notebook`, `Flashcard`.
  2. `view/CanvasView.kt` (372 lines): Custom `View` implementing `onTouchEvent`, `drawSingleStroke`, `drawActiveRecallTape`, `undo`, `redo`, tape toggling, and shape snapping.
  3. `view/PaperTemplateRenderer.kt` (208 lines): Direct canvas rendering of Blank, Ruled, College Ruled, Grid, Dotted, Cornell, Planner, Dark Grid, Sepia.
  4. `engine/StrokeEngine.kt` (72 lines): Midpoint quadratic Bezier path generation (`createSmoothPath`), stroke intersection detection (`intersectsStroke`), dynamic width calculation (`calculateDynamicWidth`).
  5. `engine/ShapeRecognizer.kt` (66 lines): Heuristic recognition for `Line`, `Rectangle`, `Circle`, `Triangle`.
  6. `data/NoteRepository.kt` (299 lines): Local JSON file-based persistence storing notebooks in `context.filesDir/notebooks/`.
  7. `pdf/PdfAnnotationManager.kt` (111 lines): Native PDF rendering via `PdfRenderer` and export via `PdfDocument`.
  8. `audio/AudioMemoManager.kt` (106 lines): Audio recording via `MediaRecorder` and playback via `MediaPlayer`.
  9. `ai/LocalAiStudyEngine.kt` (212 lines): On-device extractive summarization, rule-based Q&A, and regex-based definition flashcard generation.
  10. `FlashcardStudyActivity.kt` (116 lines): Flashcard study session with flip toggle and SRS rating buttons (Again, Hard, Good, Easy).
  11. `NoteEditorActivity.kt` (304 lines): Note editor activity integrating canvas, pen dock, undo/redo, tape toggles, and AI study drawer.
  12. `MainActivity.kt` (292 lines): Library dashboard with folder filtering, search, notebook creation dialog, and PDF import.

- **Observed Deficiencies & Gaps in Current Codebase**:
  1. **Missing Resource Files**: `app/src/main/res/layout/` contains 0 XML layout files. Activities reference `R.layout.activity_main`, `R.layout.activity_note_editor`, `R.layout.activity_flashcards`, and `R.layout.dialog_new_notebook`, but none exist on disk.
  2. **Missing Manifest**: No `AndroidManifest.xml` exists in `app/src/main/`.
  3. **Missing Build System**: No `build.gradle`, `settings.gradle`, or gradle wrapper exists in the repository.
  4. **Data Model Limitations**:
     - `Point` lacks high-resolution stylus parameters (tilt `azimuthAngle`, `tiltAngle`, tool type `TOOL_TYPE_STYLUS`).
     - `ActiveRecallTape` is limited to page coordinates without scale/zoom normalization.
     - `Flashcard` lacks dedicated `Deck` aggregation and modern SRS state tracking (state enum: `NEW`, `LEARNING`, `REVIEW`, `RELEARNING`, scheduled due timestamp, lapse counter).
     - `AudioMemoManager` records page-level audio files but lacks per-stroke timestamp indexing (`audioOffsetMs`) for stroke-synchronized playback.
  5. **Undo/Redo Scalability**: `CanvasView` currently snapshots entire stroke lists (`undoStack = ArrayDeque<PageStateSnapshot>`), which scales $O(N \times S)$ in memory where $N$ is stroke count and $S$ is snapshot depth (40). This causes memory pressure on large notebooks; a Command Pattern with incremental stroke mutations is needed.
  6. **Canvas Transform & Scaling**: `CanvasView` draws at 1:1 view pixels without a transformation matrix for infinite zoom (pinch-to-zoom) or panning, which is critical for PDF annotation and precision handwriting.

---

## 2. Logic Chain

### 2.1 Principle of Clean Modular Decomposition
To satisfy R1-R5 while maintaining strict offline separation, zero third-party dependencies, and high-performance real-time drawing on Android, the system must be partitioned into 8 decoupled subsystems:

```
+-----------------------------------------------------------------------------------+
|                               UI / Application Layer                              |
|   MainActivity  |  NoteEditorActivity  |  FlashcardStudyActivity  |  Dialogs      |
+-----------------------------------------------------------------------------------+
        |                       |                         |                  |
+-------v---------+   +---------v-----------+   +---------v-------+   +------v------+
|  1. Ink Engine  |   | 2. Canvas/Renderer  |   | 3. Active Tape  |   | 4. SRS Deck |
|  - Splines      |   | - Matrix Pan/Zoom   |   | - Occlusion     |   | - SM-2/FSRS |
|  - Pen Profiles |   | - Layer Composition |   | - Tap Reveal    |   | - Review Qs |
|  - Erasers      |   | - Spatial Culling   |   | - Quiz Engine   |   | - Card Gen  |
+-----------------+   +---------------------+   +-----------------+   +-------------+
        |                       |                         |                  |
+-------v---------+   +---------v-----------+   +---------v-------+   +------v------+
| 5. Paper Gen    |   | 6. PDF Engine       |   | 7. Voice Memo   |   | 8. Storage  |
| - Procedural    |   | - Native Render     |   | - MediaRecorder |   | - SQLite/DB |
| - Cornell/Grid  |   | - Multi-page Export |   | - Stroke Sync   |   | - JSON Pack |
| - Ruled/Planner |   | - DPI Calibration   |   | - Playback Ctrl |   | - Offline AI|
+-----------------+   +---------------------+   +-----------------+   +-------------+
```

### 2.2 Deep Dive: Subsystem Architectural Boundaries

#### Subsystem 1: Ink Engine (`com.flownotes.core.ink`)
- **Responsibilities**:
  - Capture raw input points from Android `MotionEvent` including historical batched points (`event.getHistoricalX()`, `event.getHistoricalY()`, `event.getHistoricalPressure()`).
  - Convert discrete point sequences into smooth curves using **Catmull-Rom splines** or **Midpoint Quadratic/Cubic Bezier curve fitting**.
  - Apply physical pen characteristics:
    - *Ballpoint*: Constant line weight, circular cap, minimal jitter filter.
    - *Fountain*: Dynamic width governed by $w(t) = w_{base} \cdot (p(t) \cdot \alpha - v(t) \cdot \beta)$, clamped to $[0.3 w_{base}, 2.5 w_{base}]$.
    - *Highlighter*: Broad rectangular cap, semi-transparent alpha ($35-45\%$), `PorterDuff.Mode.MULTIPLY` or overlay rendering.
    - *Pencil*: Textured line, micro-stippling, and velocity-responsive density.
  - Stroke Eraser: Segment-to-point Euclidean distance check with bounding box acceleration ($O(1)$ rejection for distant strokes).
  - Shape Recognizer: Geometric analysis of point distribution, aspect ratio, perimeter vs. bounding box, and closure distance to snap rough sketches into perfect Lines, Rectangles, Circles, and Triangles.
  - Command History: Inverted Command Pattern (`AddStrokeCommand`, `RemoveStrokeCommand`, `ModifyTapeCommand`) with reversible deltas rather than monolithic page snapshots.

#### Subsystem 2: Canvas / Renderer (`com.flownotes.core.canvas`)
- **Responsibilities**:
  - Viewport navigation: Pan, pinch-to-zoom ($0.25\times$ to $5.0\times$), two-finger gesture arbitration vs. single-finger/stylus drawing.
  - Coordinate system conversion:
    $$\begin{bmatrix} x_{doc} \\ y_{doc} \\ 1 \end{bmatrix} = \mathbf{M}_{view}^{-1} \begin{bmatrix} x_{view} \\ y_{view} \\ 1 \end{bmatrix}$$
  - Layered Rendering Pipeline (Top-to-Bottom):
    - *Layer 5 (Overlay)*: Live drawing in-progress, shape snap preview, tape drag box, selection lasso.
    - *Layer 4 (Recall)*: Active Recall Tapes (solid when concealed, dashed/tinted when revealed).
    - *Layer 3 (Annotations)*: Text boxes and sticky notes.
    - *Layer 2 (Ink)*: Committed vector strokes (highlighter beneath opaque ink).
    - *Layer 1 (Background)*: Procedural paper background or cached PDF page bitmap.
  - Render Optimization: Offscreen hardware bitmap caching, dirty region invalidation (`invalidate(dirtyRect)`), path pre-generation.

#### Subsystem 3: Active Recall Tape Engine (`com.flownotes.core.tape`)
- **Responsibilities**:
  - Management of rectangular occlusion masks over canvas coordinates.
  - Dual-state visualization:
    - *Concealed State*: Opaque aesthetic pastel block (`#FEF08A`, `#BBF7D0`, `#BAE6FD`, `#FBCFE8`) with subtle inner shadow or border, totally obscuring ink/PDF underneath.
    - *Revealed State*: 15% opacity translucent wash with a dashed border (`DashPathEffect`), revealing text/equations beneath.
  - Interaction: Single-tap hit testing (`rect.contains(x, y)`), toggle all revealed, toggle all concealed.
  - Active Recall Quiz Mode: Step-by-step traversal through page tapes for self-testing.
  - Export Rendering: Configurable rendering flag (e.g., export with tapes concealed for blank practice tests, or revealed for reference keys).

#### Subsystem 4: Flashcard SRS Engine (`com.flownotes.core.srs`)
- **Responsibilities**:
  - Deck and Card entity management with isolation from note pages.
  - **SuperMemo-2 (SM-2) / FSRS spaced repetition scheduling algorithm**:
    - Ratings: `AGAIN` (1), `HARD` (2), `GOOD` (3), `EASY` (4).
    - New Ease Factor: $EF' = EF + (0.1 - (5 - q) \cdot (0.08 + (5 - q) \cdot 0.02))$, clamped at $EF \ge 1.3$.
    - Interval Progression:
      $$I(1) = 1 \text{ day}, \quad I(2) = 6 \text{ days}, \quad I(n) = I(n-1) \cdot EF$$
    - If `AGAIN`: Repetitions reset to 0, interval resets to 1 day.
  - Automated Generation:
    - Synthesizes cards from Active Recall Tapes (occluded region = answer, surrounding context = prompt).
    - Synthesizes cards from on-device NLP definition extractions.
  - Study Session State Machine: Queues cards by `dueDate <= currentTime`, manages 3D flip animation, rating submission, and persistence.

#### Subsystem 5: Procedural Paper Generator (`com.flownotes.core.paper`)
- **Responsibilities**:
  - Pure mathematical vector rendering directly onto `android.graphics.Canvas` without bitmap textures or external assets.
  - Templates:
    - *Cornell Notes Layout*: Header section (Date/Subject), left Cue column ($30\%$ width), right Notes area ($70\%$ width), bottom Summary box ($20\%$ height).
    - *5mm Grid / Graph*: Accurate mm-to-DPI scaled grid lines with major grid line accents every 5 grid units.
    - *Dot Grid*: Precision dot matrix with circular anti-aliased points ($2-3\text{px}$ diameter) for bullet journaling.
    - *Standard & College Ruled*: Calibrated horizontal line heights ($7.1\text{mm}$ standard, $6.3\text{mm}$ college) with left margin vertical line ($1.25\text{ inches}$).
    - *Weekly Study Planner*: 6/7 day partitioned schedule grid with weekly focus boxes.
    - *Dark Mode & Sepia*: High-contrast slate theme (`#1A202C` bg, `#2D3748` lines) and vintage warm parchment (`#FDF6E2` bg, `#E2D7B5` lines).

#### Subsystem 6: PDF Import/Export Engine (`com.flownotes.core.pdf`)
- **Responsibilities**:
  - 100% native Android PDF processing via `android.graphics.pdf.PdfRenderer` and `android.graphics.pdf.PdfDocument`.
  - PDF Import:
    - Reads PDF via `ParcelFileDescriptor`.
    - Converts PDF pages into FlowNotes `Page` objects with `pdfPageIndex`.
    - Asynchronous rasterization pipeline with an LRU memory bitmap cache (`LruCache<Int, Bitmap>`) to prevent OutOfMemory (OOM) on large multi-hundred-page textbooks.
  - PDF Export:
    - Multi-page document generation using `PdfDocument`.
    - High-fidelity vector rendering: draws paper background, renders vector smooth paths at native document scale (72 DPI base points with subpixel precision), renders text annotations, and draws Active Recall Tapes according to export settings.
    - Direct streaming to file with memory-conscious page recycling.

#### Subsystem 7: Voice Memo Engine (`com.flownotes.core.audio`)
- **Responsibilities**:
  - Native audio capture using `android.media.MediaRecorder`:
    - Format: MPEG-4 container, AAC audio codec, 44.1 kHz, 64-128 kbps.
    - File storage: App-private directory (`context.filesDir/audio/`).
  - Native playback using `android.media.MediaPlayer`:
    - Full transport controls: Play, Pause, Resume, Seek, Stop.
    - Playback speed scaling ($0.75\times, 1.0\times, 1.25\times, 1.5\times, 2.0\times$) via `PlaybackParams`.
  - **Stroke-Audio Time Synchronization**:
    - Active recording session maintains `startTimeMs`.
    - Every stroke committed during recording tags each `Point` with `audioOffsetMs = System.currentTimeMillis() - startTimeMs`.
    - During audio playback, a time listener emits current playback position, allowing the canvas to visually highlight or replay the ink that corresponds to that audio moment.

#### Subsystem 8: Local Persistence & Offline Study Assistant (`com.flownotes.core.storage` & `com.flownotes.core.ai`)
- **Responsibilities**:
  - **Storage Architecture (Hybrid Database + File Package)**:
    - SQLite database (`FlowNotesDatabase` / Room or native SQLiteOpenHelper):
      - Tables: `notebooks`, `pages`, `decks`, `flashcards`, `audio_attachments`.
      - Fast querying for notebook listings, folders, search titles, card review queues, and metadata without parsing heavy stroke payloads.
    - File Storage:
      - Stroke vectors, tape coordinates, and sticky notes serialized per page in dedicated compressed JSON files (`notebooks/<notebookId>/page_<pageId>.json`).
      - Avoids SQLite 2MB CursorWindow truncation and keeps app startup instantaneous.
  - **On-Device Offline Study Assistant**:
    - Extractive Summarization: Frequency-weighted sentence ranking (TextRank / TF-IDF scoring of sentences based on content words, excluding standard English stop words).
    - Formula & Keyword Extraction: Heuristic tokenization, regex extraction of mathematical relations ($=, \approx, \le, \ge, \sum, \int, \frac{\cdot}{\cdot}$), and scientific definitions (`X is defined as Y`, `X refers to Y`).
    - Automated Flashcard Generation: Parsing notes into question/answer pairs stored in local flashcard decks.
    - Zero server dependency, zero network permission required.

---

## 3. Data Models & Storage Schemas

### 3.1 Core Entity Definitions (Kotlin)

```kotlin
package com.flownotes.core.model

import java.io.Serializable
import java.util.UUID

enum class PenTool {
    BALLPOINT,
    FOUNTAIN,
    HIGHLIGHTER,
    PENCIL,
    ERASER_STROKE,
    ERASER_PIXEL,
    TAPE,
    SHAPE,
    LASSO,
    LASER
}

enum class PaperType {
    BLANK,
    RULED,
    COLLEGE_RULED,
    GRID,
    DOTTED,
    CORNELL,
    PLANNER,
    DARK_GRID,
    SEPIA
}

enum class CardRating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4)
}

enum class CardState {
    NEW,
    LEARNING,
    REVIEW,
    RELEARNING
}

data class Point(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timestamp: Long = System.currentTimeMillis(),
    val audioOffsetMs: Long? = null
) : Serializable

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val tool: PenTool = PenTool.BALLPOINT,
    val color: Int = 0xFF1E293B.toInt(),
    val strokeWidth: Float = 3.0f,
    val points: MutableList<Point> = mutableListOf(),
    val shapeType: String? = null,
    val isDeleted: Boolean = false
) : Serializable {
    val bounds: RectF by lazy {
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE; var maxY = Float.MIN_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        RectF(minX, minY, maxX, maxY)
    }
}

data class ActiveRecallTape(
    val id: String = UUID.randomUUID().toString(),
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
    val color: Int = 0xFFFEF08A.toInt(),
    var isRevealed: Boolean = false,
    var label: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class TextAnnotation(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var text: String,
    val color: Int = 0xFF1E293B.toInt(),
    val fontSize: Float = 16f,
    val isFormula: Boolean = false
) : Serializable

data class StickyNote(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var width: Float = 200f,
    var height: Float = 180f,
    var content: String = "",
    val color: Int = 0xFFFEF08A.toInt()
) : Serializable

data class AudioAttachment(
    val id: String = UUID.randomUUID().toString(),
    val pageId: String,
    val filePath: String,
    val durationMs: Long,
    val recordedAt: Long = System.currentTimeMillis()
) : Serializable

data class Page(
    val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val pageNumber: Int,
    var paperType: PaperType = PaperType.RULED,
    val strokes: MutableList<Stroke> = mutableListOf(),
    val tapes: MutableList<ActiveRecallTape> = mutableListOf(),
    val textAnnotations: MutableList<TextAnnotation> = mutableListOf(),
    val stickyNotes: MutableList<StickyNote> = mutableListOf(),
    var pdfPageIndex: Int? = null,
    var audioMemoUri: String? = null,
    val width: Float = 1200f,
    val height: Float = 1600f
) : Serializable

data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Untitled Notebook",
    var coverColor: Int = 0xFF3B82F6.toInt(),
    var coverPattern: String = "modern",
    var folder: String = "All Notes",
    var defaultPaperType: PaperType = PaperType.RULED,
    val pages: MutableList<Page> = mutableListOf(),
    var pdfPath: String? = null,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Serializable {
    val pageCount: Int get() = pages.size
}

data class Deck(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var description: String = "",
    var notebookId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Serializable

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val front: String,
    val back: String,
    val hint: String = "",
    val sourceNoteId: String? = null,
    val sourceTapeId: String? = null,
    var intervalDays: Int = 1,
    var repetitions: Int = 0,
    var easeFactor: Float = 2.5f,
    var dueDate: Long = System.currentTimeMillis(),
    var lastReviewedAt: Long? = null,
    var state: CardState = CardState.NEW,
    var lapses: Int = 0
) : Serializable
```

### 3.2 Storage Schemas (SQLite Database)

```sql
-- Notebooks table
CREATE TABLE IF NOT EXISTS notebooks (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    cover_color INTEGER NOT NULL,
    cover_pattern TEXT NOT NULL,
    folder TEXT NOT NULL,
    default_paper_type TEXT NOT NULL,
    pdf_path TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Pages metadata table
CREATE TABLE IF NOT EXISTS pages (
    id TEXT PRIMARY KEY NOT NULL,
    notebook_id TEXT NOT NULL,
    page_number INTEGER NOT NULL,
    paper_type TEXT NOT NULL,
    pdf_page_index INTEGER,
    audio_memo_uri TEXT,
    width REAL NOT NULL DEFAULT 1200.0,
    height REAL NOT NULL DEFAULT 1600.0,
    stroke_count INTEGER NOT NULL DEFAULT 0,
    tape_count INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(notebook_id) REFERENCES notebooks(id) ON DELETE CASCADE
);

-- Decks table
CREATE TABLE IF NOT EXISTS decks (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    notebook_id TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(notebook_id) REFERENCES notebooks(id) ON DELETE SET NULL
);

-- Flashcards table (SRS state)
CREATE TABLE IF NOT EXISTS flashcards (
    id TEXT PRIMARY KEY NOT NULL,
    deck_id TEXT NOT NULL,
    front TEXT NOT NULL,
    back TEXT NOT NULL,
    hint TEXT,
    source_note_id TEXT,
    source_tape_id TEXT,
    interval_days INTEGER NOT NULL DEFAULT 1,
    repetitions INTEGER NOT NULL DEFAULT 0,
    ease_factor REAL NOT NULL DEFAULT 2.5,
    due_date INTEGER NOT NULL,
    last_reviewed_at INTEGER,
    state TEXT NOT NULL DEFAULT 'NEW',
    lapses INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(deck_id) REFERENCES decks(id) ON DELETE CASCADE
);

-- Audio Attachments table
CREATE TABLE IF NOT EXISTS audio_attachments (
    id TEXT PRIMARY KEY NOT NULL,
    page_id TEXT NOT NULL,
    file_path TEXT NOT NULL,
    duration_ms INTEGER NOT NULL,
    recorded_at INTEGER NOT NULL,
    FOREIGN KEY(page_id) REFERENCES pages(id) ON DELETE CASCADE
);

-- Indices for rapid query performance
CREATE INDEX IF NOT EXISTS idx_pages_notebook_id ON pages(notebook_id);
CREATE INDEX IF NOT EXISTS idx_flashcards_deck_id ON flashcards(deck_id);
CREATE INDEX IF NOT EXISTS idx_flashcards_due_date ON flashcards(due_date);
```

---

## 4. Cross-Module Interface Contracts & APIs

### 4.1 Ink Engine Contract (`StrokeEngineContract.kt`)
```kotlin
package com.flownotes.core.ink

import android.graphics.Path
import com.flownotes.core.model.Point
import com.flownotes.core.model.Stroke

interface StrokeEngineContract {
    fun createSmoothPath(points: List<Point>): Path
    fun intersectsStroke(stroke: Stroke, x: Float, y: Float, radius: Float): Boolean
    fun calculateDynamicWidth(baseWidth: Float, current: Point, previous: Point): Float
}
```

### 4.2 Active Recall Tape Contract (`ActiveRecallTapeContract.kt`)
```kotlin
package com.flownotes.core.tape

import android.graphics.Canvas
import com.flownotes.core.model.ActiveRecallTape

interface ActiveRecallTapeContract {
    fun drawTape(canvas: Canvas, tape: ActiveRecallTape)
    fun findTapeAt(tapes: List<ActiveRecallTape>, x: Float, y: Float): ActiveRecallTape?
    fun setAllTapesRevealed(tapes: List<ActiveRecallTape>, revealed: Boolean)
}
```

### 4.3 Spaced Repetition Contract (`SrsEngineContract.kt`)
```kotlin
package com.flownotes.core.srs

import com.flownotes.core.model.CardRating
import com.flownotes.core.model.Flashcard

data class SrsReviewResult(
    val newIntervalDays: Int,
    val newRepetitions: Int,
    val newEaseFactor: Float,
    val newDueDate: Long,
    val newState: com.flownotes.core.model.CardState
)

interface SrsEngineContract {
    fun calculateNextReview(card: Flashcard, rating: CardRating): SrsReviewResult
    fun getDueCards(cards: List<Flashcard>, currentTime: Long = System.currentTimeMillis()): List<Flashcard>
}
```

### 4.4 Paper Generator Contract (`PaperRendererContract.kt`)
```kotlin
package com.flownotes.core.paper

import android.graphics.Canvas
import com.flownotes.core.model.PaperType

interface PaperRendererContract {
    fun drawPaper(canvas: Canvas, width: Float, height: Float, type: PaperType, isDark: Boolean = false)
}
```

### 4.5 PDF Service Contract (`PdfManagerContract.kt`)
```kotlin
package com.flownotes.core.pdf

import android.graphics.Bitmap
import com.flownotes.core.model.Notebook
import java.io.File

interface PdfManagerContract {
    fun openPdf(pdfFile: File): Int
    fun renderPageToBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap?
    fun exportNotebookToPdf(notebook: Notebook, outputFile: File, width: Int = 1200, height: Int = 1600, concealTapes: Boolean = false): Boolean
    fun close()
}
```

### 4.6 Audio Service Contract (`AudioMemoContract.kt`)
```kotlin
package com.flownotes.core.audio

import java.io.File

interface AudioMemoContract {
    val isRecording: Boolean
    val isPlaying: Boolean
    fun startRecording(pageId: String): File?
    fun stopRecording(): File?
    fun playAudio(file: File, onProgress: (positionMs: Int, durationMs: Int) -> Unit, onComplete: () -> Unit)
    fun seekTo(positionMs: Int)
    fun stopPlayback()
    fun release()
}
```

### 4.7 Local AI Study Assistant Contract (`LocalAiStudyContract.kt`)
```kotlin
package com.flownotes.core.ai

import com.flownotes.core.model.Flashcard
import com.flownotes.core.model.Notebook

data class StudySummary(
    val title: String,
    val focusKeywords: List<String>,
    val essentialInsights: List<String>,
    val suggestedActionItems: List<String>
)

interface LocalAiStudyContract {
    fun generateLocalSummary(title: String, rawText: String, pageCount: Int): StudySummary
    fun solveProblemLocally(query: String, contextText: String): String
    fun answerStudyQuestion(question: String, notebook: Notebook): String
    fun generateLocalFlashcards(notebook: Notebook): List<Flashcard>
}
```

---

## 5. Testing Architecture & Verification Harness

To guarantee absolute integrity without relying on an active physical display or live user touches, the testing architecture is built across five distinct validation tiers:

| Tier | Scope | Target Classes | Verification Method |
|---|---|---|---|
| **Tier 1: Algorithmic Unit Tests** | Pure mathematical calculations | `StrokeEngine`, `ShapeRecognizer`, `SrsEngine` | Standard JVM / JUnit 4 tests with synthetic coordinate streams and mathematical assertion tables. |
| **Tier 2: Headless Canvas Verification** | Vector rendering & visual state | `PaperTemplateRenderer`, `CanvasView` drawing pipelines | Allocates offscreen `Bitmap.createBitmap(w, h, ARGB_8888)`, attaches `Canvas(bitmap)`, runs draw passes, asserts non-zero pixel buffers and pixel color coordinates. |
| **Tier 3: PDF Roundtrip Tests** | PDF rendering & serialization | `PdfAnnotationManager` | Generates sample notebook, exports to temporary PDF via `PdfDocument`, verifies file header (`%PDF-`), re-opens with `PdfRenderer`, verifies page counts and rendered dimensions match. |
| **Tier 4: Audio Contract Mocking** | Audio recording & time synchronization | `AudioMemoManager` | Mocks `MediaRecorder` / `MediaPlayer` interface boundaries, verifies timestamp tracking (`Point.audioOffsetMs`), and playback event callbacks. |
| **Tier 5: Local Persistence & AI Tests** | JSON / SQLite storage & NLP | `NoteRepository`, `LocalAiStudyEngine` | Roundtrip save/load assertions of notebooks, pages, strokes, and tapes; extractive summarization assertions and definition regex parser tests. |

### 5.1 Sample Headless Canvas Test Harness
```kotlin
class HeadlessCanvasTest {
    @Test
    fun testPaperTemplateRendersWithoutCrash() {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (type in PaperType.values()) {
            PaperTemplateRenderer.drawPaper(canvas, width.toFloat(), height.toFloat(), type, isDark = false)
            // Assert canvas was modified
            val pixel = bitmap.getPixel(100, 100)
            assertTrue(pixel != 0)
        }
    }
}
```

### 5.2 SRS Mathematical Correctness Test
```kotlin
class SrsEngineTest {
    @Test
    fun testSm2Progression() {
        val srs = SrsEngine()
        var card = Flashcard(deckId = "d1", front = "Q", back = "A")

        // First review: Good (3) -> interval 1 day, rep 1
        var result = srs.calculateNextReview(card, CardRating.GOOD)
        assertEquals(1, result.newIntervalDays)
        assertEquals(1, result.newRepetitions)

        // Second review: Good (3) -> interval 6 days, rep 2
        card = card.copy(intervalDays = result.newIntervalDays, repetitions = result.newRepetitions, easeFactor = result.newEaseFactor)
        result = srs.calculateNextReview(card, CardRating.GOOD)
        assertEquals(6, result.newIntervalDays)
        assertEquals(2, result.newRepetitions)

        // Third review: Again (1) -> interval resets to 1 day, rep 0
        card = card.copy(intervalDays = result.newIntervalDays, repetitions = result.newRepetitions, easeFactor = result.newEaseFactor)
        result = srs.calculateNextReview(card, CardRating.AGAIN)
        assertEquals(1, result.newIntervalDays)
        assertEquals(0, result.newRepetitions)
    }
}
```

---

## 6. Recommended Milestone Decomposition (Conservative Dependency Ordering)

To ensure systematic implementation with zero regressions and clean verification at each stage, the project is structured into **5 sequential milestones**:

```
[Milestone 1: Foundations] ---> [Milestone 2: Canvas & Ink] ---> [Milestone 3: Active Recall & SRS]
                                                                        |
                                                                        v
[Milestone 5: AI & Polish] <--- [Milestone 4: PDF & Voice Memo] <-------+
```

### Milestone 1: Core Architecture, Build Foundations, Data Models & Persistence
- **Focus**: Project build configuration, Android manifest, complete resource XMLs, domain data models, and local persistence repository.
- **Deliverables**:
  - `build.gradle` / project configuration targeting Android SDK.
  - `AndroidManifest.xml` declaring activities (`MainActivity`, `NoteEditorActivity`, `FlashcardStudyActivity`), required permissions (`RECORD_AUDIO`, `READ_EXTERNAL_STORAGE`).
  - Layout resources: `activity_main.xml`, `activity_note_editor.xml`, `activity_flashcards.xml`, `dialog_new_notebook.xml`.
  - Core data models in `com.flownotes.core.model`.
  - Refactored `NoteRepository` with error handling, atomic writes, and data schema validation.
- **Verification**: Project compiles cleanly; headless serialization tests pass (saving/loading sample notebooks).

### Milestone 2: Native Ink Engine, Procedural Paper Generator & Canvas View
- **Focus**: High-performance digital drawing canvas with Bezier splines, pen profiles, stroke eraser, and procedural paper templates.
- **Deliverables**:
  - `StrokeEngine` with Bezier interpolation and dynamic fountain pen width.
  - `ShapeRecognizer` for lines, rectangles, circles, and triangles.
  - `PaperTemplateRenderer` with Cornell, Grid, Dotted, Ruled, Planner, Sepia, and Dark Mode.
  - `CanvasView` with stylus event handling, undo/redo command stack, and viewport rendering.
- **Verification**: Headless canvas rendering tests pass for all paper templates; synthetic stroke creation and intersection detection pass unit tests.

### Milestone 3: Active Recall Tape, Flashcard SRS Engine & Study Mode
- **Focus**: Freenotes signature Active Recall study tape and on-device spaced repetition flashcard study activity.
- **Deliverables**:
  - Active Recall Tape interactive placement, tap-to-reveal/conceal, and bulk reveal/conceal actions.
  - `SrsEngine` implementing SM-2 algorithm.
  - `FlashcardStudyActivity` with 3D card flip animation, progress indicator, and rating buttons (Again, Hard, Good, Easy).
  - Automated flashcard generation from note tape coordinates.
- **Verification**: SM-2 progression unit tests pass; tape hit testing and state toggle tests pass.

### Milestone 4: Native PDF Import/Export & Synchronized Voice Memo Engine
- **Focus**: Multi-page PDF annotation and synchronized audio lecture recording.
- **Deliverables**:
  - `PdfAnnotationManager` using native `PdfRenderer` (import/caching) and `PdfDocument` (multi-page vector/raster export with tape concealment settings).
  - `AudioMemoManager` using native `MediaRecorder` and `MediaPlayer`.
  - Stroke-audio timestamp indexing (`Point.audioOffsetMs`).
- **Verification**: PDF export and re-import roundtrip test passes; audio manager mock lifecycle tests pass.

### Milestone 5: On-Device Study Assistant, System Integration & UI Polish
- **Focus**: Extractive summarization, formula extraction, drawer UI, end-to-end integration, and offline verification.
- **Deliverables**:
  - `LocalAiStudyEngine` with frequency-weighted summarization and formula detection.
  - Note editor AI study drawer integration.
  - UI refinement: Floating dock styling, icons, folder categorization, search filtering.
  - Comprehensive acceptance test suite execution.
- **Verification**: All acceptance criteria from `ORIGINAL_REQUEST.md` pass under 100% offline flight mode.

---

## 7. Recommended Code Layout

```
FlowNotes/
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/
│       │   │   └── com/
│       │   │       └── flownotes/
│       │   │           ├── FlowNotesApp.kt
│       │   │           ├── core/
│       │   │           │   ├── model/
│       │   │           │   │   └── Models.kt
│       │   │           │   ├── ink/
│       │   │           │   │   ├── StrokeEngine.kt
│       │   │           │   │   ├── ShapeRecognizer.kt
│       │   │           │   │   └── StrokeCommandHistory.kt
│       │   │           │   ├── canvas/
│       │   │           │   │   ├── CanvasView.kt
│       │   │           │   │   └── CanvasViewportMatrix.kt
│       │   │           │   ├── tape/
│       │   │           │   │   └── ActiveRecallTapeManager.kt
│       │   │           │   ├── paper/
│       │   │           │   │   └── PaperTemplateRenderer.kt
│       │   │           │   ├── srs/
│       │   │           │   │   └── SrsEngine.kt
│       │   │           │   ├── pdf/
│       │   │           │   │   └── PdfAnnotationManager.kt
│       │   │           │   ├── audio/
│       │   │           │   │   └── AudioMemoManager.kt
│       │   │           │   ├── storage/
│       │   │           │   │   ├── FlowNotesDatabase.kt
│       │   │           │   │   └── NoteRepository.kt
│       │   │           │   └── ai/
│       │   │           │       └── LocalAiStudyEngine.kt
│       │   │           └── ui/
│       │   │               ├── MainActivity.kt
│       │   │               ├── NoteEditorActivity.kt
│       │   │               ├── FlashcardStudyActivity.kt
│       │   │               └── adapter/
│       │   │                   └── NotebookAdapter.kt
│       │   └── res/
│       │       ├── drawable/
│       │       │   ├── bg_floating_dock.xml
│       │       │   ├── bg_card_front.xml
│       │       │   └── bg_card_back.xml
│       │       ├── layout/
│       │       │   ├── activity_main.xml
│       │       │   ├── activity_note_editor.xml
│       │       │   ├── activity_flashcards.xml
│       │       │   ├── dialog_new_notebook.xml
│       │       │   └── item_notebook.xml
│       │       └── values/
│       │           ├── colors.xml
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/
│           └── java/
│               └── com/
│                   └── flownotes/
│                       ├── ink/StrokeEngineTest.kt
│                       ├── srs/SrsEngineTest.kt
│                       ├── paper/PaperTemplateTest.kt
│                       └── ai/LocalAiStudyEngineTest.kt
├── build.gradle
└── settings.gradle
```

---

## 8. Caveats
- **Hardware Acceleration Nuances**: Certain custom `Paint` transfer modes (such as `PorterDuff.Mode.CLEAR` for area eraser) behave differently under Android hardware acceleration (`LAYER_TYPE_HARDWARE`). Layer isolation with a dedicated offscreen bitmap or `Canvas.saveLayer()` is required to prevent clearing the entire window frame.
- **Large PDF Memory Footprint**: In Android's 32-bit/64-bit ART runtime, decoding large 500+ page textbook PDFs directly into bitmaps can exhaust the app heap. The `PdfAnnotationManager` must strictly use an LRU memory cache, releasing offscreen page bitmaps when not actively visible in the editor viewport.
- **Audio Permission Flow**: On modern Android (API 33+), runtime permission for microphone (`android.permission.RECORD_AUDIO`) must be requested with graceful degradation if denied by the user.

---

## 9. Conclusion
FlowNotes possesses a clean, well-conceived algorithmic core in Kotlin that directly addresses the features of Freenotes (`R1`-`R5`). The technical architecture designed herein provides:
1. Complete subsystem decoupling across 8 distinct engines.
2. A scalable data model and storage schema that avoids SQLite CursorWindow pitfalls.
3. Strict 100% offline isolation with zero cloud backend or external API reliance.
4. A verifiable 5-tier testing harness supporting headless verification.
5. A conservative 5-milestone roadmap that guarantees incremental stability from build setup to end-to-end integration.

---

## 10. Verification Method
1. **Model & Interface Verification**:
   - Inspect `/storage/emulated/0/FlowNotes/.agents/teamwork/survey_explorer_2/handoff.md` to ensure all 8 modules have explicit interface contracts and data models defined.
2. **Codebase Cross-Check**:
   - Compare data models against `app/src/main/java/com/flownotes/app/model/Models.kt` to ensure backwards compatibility with existing prototype structures.
3. **Acceptance Criteria Mapping**:
   - Verify every requirement in `/storage/emulated/0/FlowNotes/.agents/teamwork/ORIGINAL_REQUEST.md` (R1-R5) maps to an explicit subsystem and milestone.
