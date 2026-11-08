# Project: FlowNotes

100% Offline, Native Android Digital Note-Taking and PDF Annotation App in Kotlin replicating the core capabilities of Freenotes (`ai.note.notepad.memo.journal.freenote`) with zero backend, zero hosting costs, and zero external API dependencies.

## Architecture

FlowNotes follows a clean, decoupled modular architecture composed of 8 primary subsystems:

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

### Core Subsystems
1. **Ink Engine (`com.flownotes.core.ink`)**: Midpoint quadratic Bezier curve fitting, dynamic pressure-velocity width, physical pen profiles (Ballpoint, Fountain, Highlighter, Pencil), stroke/pixel eraser, geometric shape snapping (Line, Rectangle, Circle, Triangle), undo/redo command stack.
2. **Canvas / Viewport (`com.flownotes.core.canvas`)**: Hardware-accelerated native Android View with layered rendering (Paper/PDF background, Ink, Annotations, Active Recall Tapes, In-progress live previews).
3. **Active Recall Tape Engine (`com.flownotes.core.tape`)**: Draggable occlusion rectangles, tap-to-reveal/conceal, visual concealed state (opaque pastel), visual revealed state (translucent dashed border), bulk Reveal All / Conceal All revision actions.
4. **Flashcard SRS Engine (`com.flownotes.core.srs`)**: Deck and card entities, automated card generation from tapes/notes, SuperMemo SM-2 spaced repetition scheduler (Again, Hard, Good, Easy), 3D flip card study session with hints.
5. **Procedural Paper Generator (`com.flownotes.core.paper`)**: Mathematical vector canvas rendering without external bitmap textures: Cornell Notes, 5mm Grid, Dot Grid, Ruled, College Ruled, Weekly Study Planner, Dark Slate Mode, Sepia Vintage Paper.
6. **PDF Engine (`com.flownotes.core.pdf`)**: Native `android.graphics.pdf.PdfRenderer` import and page bitmap caching; multi-page standard PDF export via `android.graphics.pdf.PdfDocument`.
7. **Voice Memo Engine (`com.flownotes.core.audio`)**: Native `android.media.MediaRecorder` AAC/M4A lecture recording synced to pages, inline `android.media.MediaPlayer` playback, per-stroke audio timestamp indexing (`Point.audioOffsetMs`).
8. **Local Persistence & Offline AI Assistant (`com.flownotes.core.storage` & `com.flownotes.core.ai`)**: Hybrid JSON + SQLite storage in app-private directory; frequency-weighted extractive summarization, formula extraction, definition mining, structured local problem solver with $0 cloud cost and 0 external APIs.

---

## Feature Inventory

Every feature identified during the Survey Phase is cataloged below with its assigned milestone.

| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Custom CanvasView Touch Engine | Hardware-accelerated native Android View tracking touch and stylus MotionEvent | M2 | ORIGINAL_REQUEST §R1 |
| 2 | Bezier / Spline Interpolation | Smooth vector paths via midpoint quadratic Bezier curve fitting | M2 | ORIGINAL_REQUEST §R1 |
| 3 | Ballpoint Pen Mode | Crisp uniform line width with anti-aliasing and round caps | M2 | ORIGINAL_REQUEST §R1 |
| 4 | Fountain Pen Mode | Dynamic line width based on real-time stylus pressure and velocity | M2 | ORIGINAL_REQUEST §R1 |
| 5 | Semi-Transparent Highlighter | Chisel stroke overlay with ~43% alpha preserving underlying text | M2 | ORIGINAL_REQUEST §R1 |
| 6 | Textured Pencil Tool | Grain-aesthetic tactile line rendering for sketching | M2 | ORIGINAL_REQUEST §R1 |
| 7 | Stroke Eraser (Whole Stroke) | Instant removal of entire stroke upon touch intersection | M2 | ORIGINAL_REQUEST §R1 |
| 8 | Pixel / Area Eraser | Brush-based localized stroke segment clearing | M2 | ORIGINAL_REQUEST §R1 |
| 9 | Geometric Shape Recognizer | Snaps hand-drawn lines, rectangles, circles, triangles to clean vectors | M2 | ORIGINAL_REQUEST §R1 |
| 10 | Undo / Redo History Stacks | Multi-level snapshot undo/redo history for strokes and tape changes | M2 | ORIGINAL_REQUEST §R1 |
| 11 | Active Recall Tape Creation | Draggable colored tape rectangles to conceal keywords, answers, formulas | M3 | ORIGINAL_REQUEST §R2 |
| 12 | Tap-to-Reveal / Conceal | Interactive single-tap toggling of tape visibility | M3 | ORIGINAL_REQUEST §R2 |
| 13 | Visual Concealed State | Opaque pastel rectangle blocking underlying content | M3 | ORIGINAL_REQUEST §R2 |
| 14 | Visual Revealed State | Translucent low-alpha fill with dashed border for self-testing | M3 | ORIGINAL_REQUEST §R2 |
| 15 | Quick Action: Reveal All | Uncovers all active recall tapes on current page | M3 | ORIGINAL_REQUEST §R2 |
| 16 | Quick Action: Conceal All | Hides all active recall tapes on current page for exam revision | M3 | ORIGINAL_REQUEST §R2 |
| 17 | 3D Card Flipping Study Mode | Interactive flashcard activity with 3D perspective flip transition | M3 | ORIGINAL_REQUEST §R2 |
| 18 | Answer Hints on Flashcards | Collapsible hint text aiding formula and term recall | M3 | ORIGINAL_REQUEST §R2 |
| 19 | Spaced Repetition (SRS) Rating | SuperMemo SM-2 rating buttons (Again, Hard, Good, Easy) scheduling intervals | M3 | ORIGINAL_REQUEST §R2 |
| 20 | Cornell Notes Template | Procedural Cornell layout: Header, Cue column, Notes section, Summary box | M2 | ORIGINAL_REQUEST §R3 |
| 21 | 5mm Grid / Graph Paper | Procedural orthogonal grid with 5mm equivalent spacing | M2 | ORIGINAL_REQUEST §R3 |
| 22 | Dot Grid Paper (Bullet Journal) | Procedural dot matrix with circular anti-aliased points | M2 | ORIGINAL_REQUEST §R3 |
| 23 | Standard & College Ruled | Horizontal notebook ruled lines with left vertical margin line | M2 | ORIGINAL_REQUEST §R3 |
| 24 | Weekly Study Planner Template | 6-box study planner layout (Monday-Sunday + Review) | M2 | ORIGINAL_REQUEST §R3 |
| 25 | Dark Mode & Sepia Vintage Paper | Dark Slate (`#1A202C`) and Sepia Cream (`#FDF6E2`) procedural backgrounds | M2 | ORIGINAL_REQUEST §R3 |
| 26 | Native PDF Import | Local PDF import via native `android.graphics.pdf.PdfRenderer` | M4 | ORIGINAL_REQUEST §R3 |
| 27 | PDF Background Layer Rendering | High-resolution bitmap background rendering beneath ink annotations | M4 | ORIGINAL_REQUEST §R3 |
| 28 | Multi-Page PDF Document Export | Flattens notes, paper/PDF, strokes, and tapes via `PdfDocument` | M4 | ORIGINAL_REQUEST §R3 |
| 29 | Synced Voice Lecture Recording | Native `MediaRecorder` AAC/M4A audio recording synced to page | M4 | ORIGINAL_REQUEST §R4 |
| 30 | Inline Voice Memo Playback | Native `MediaPlayer` playback with progress tracking | M4 | ORIGINAL_REQUEST §R4 |
| 31 | 100% Local JSON/SQLite Storage | App-private storage serialization for notebooks, pages, strokes, tapes | M1 | ORIGINAL_REQUEST §R5 |
| 32 | Extractive Study Summarizer | On-device frequency-weighted sentence ranking summary | M5 | ORIGINAL_REQUEST §R5 |
| 33 | Formula & Definition Extractor | Regex parsing of terms, definitions, and equations from notes | M5 | ORIGINAL_REQUEST §R5 |
| 34 | Automated Flashcard Generation | Automatic synthesis of study cards from note definitions and tape regions | M5 | ORIGINAL_REQUEST §R5 |
| 35 | Local Problem Solver Assistant | 3-step structured problem breakdown (Knowns, Formulation, Synthesis) | M5 | ORIGINAL_REQUEST §R5 |
| 36 | Notebook Shelf & Folder Manager | Library dashboard with folder categorization and real-time title search | M1 | ORIGINAL_REQUEST §Acceptance |
| 37 | Notebook Creation & Cover Picker | Modal dialog to configure title, cover color, and default paper template | M1 | ORIGINAL_REQUEST §Acceptance |
| 38 | Multi-Page Navigation & Insertion | Page navigation controls (Prev, Next, Page Indicator, Add Page) | M1 | ORIGINAL_REQUEST §Acceptance |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Core Foundations, Android Layouts & Local Storage | Build setup (`build.gradle.kts`), `AndroidManifest.xml`, XML layouts (`activity_main.xml`, `activity_note_editor.xml`, `activity_flashcards.xml`, `dialog_new_notebook.xml`, `item_notebook.xml`), Data models & `NoteRepository` persistence, Local companion UI | none | PLANNED |
| M2 | Native Ink Engine & Procedural Paper Generator | Custom `CanvasView`, Bezier smoothing, 4 pen profiles (Ballpoint, Fountain, Highlighter, Pencil), stroke/pixel eraser, shape snapping, undo/redo stacks, procedural paper templates (Cornell, Grid, Dotted, Ruled, Planner, Sepia, Dark) | M1 | PLANNED |
| M3 | Active Recall Tape & Flashcard SRS Engine | Interactive tape creation, tap reveal/conceal, Reveal All / Conceal All, SuperMemo SM-2 SRS engine, `FlashcardStudyActivity` 3D card flip with hints and rating buttons | M2 | PLANNED |
| M4 | Native PDF Import/Export & Synced Voice Memo | `PdfAnnotationManager` with native `PdfRenderer` and `PdfDocument` export, `AudioMemoManager` with `MediaRecorder` and `MediaPlayer`, stroke-audio timestamp indexing | M2 | PLANNED |
| M5 | On-Device AI Study Assistant & Final E2E Pass | `LocalAiStudyEngine` extractive summarizer, formula extractor, auto-flashcards, problem solver drawer, 100% pass of E2E test suite (Tiers 1-4), adversarial hardening (Tier 5) | M3, M4 | PLANNED |

---

## Dual Track: E2E Testing Track

Running in parallel with the Implementation Track:
- **Scope**: Requirements-driven, opaque-box test suite independent of implementation internals.
- **Artifacts**: `TEST_INFRA.md` (test architecture, tier definitions), test suites for Tier 1 (Feature Coverage $\ge 5 \times 38 = 190$), Tier 2 (Boundary & Corner $\ge 5 \times 38 = 190$), Tier 3 (Cross-Feature Combinations $\ge 38$), Tier 4 (Real-World Application Scenarios $\ge 19$).
- **Output**: Publishes `TEST_READY.md` upon completion.
- **Gate**: Milestone M5 cannot complete until 100% of the E2E tests pass cleanly.

---

## Interface Contracts

### 1. `StrokeEngineContract` (`com.flownotes.core.ink`)
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

### 2. `ActiveRecallTapeContract` (`com.flownotes.core.tape`)
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

### 3. `SrsEngineContract` (`com.flownotes.core.srs`)
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

### 4. `PaperRendererContract` (`com.flownotes.core.paper`)
```kotlin
package com.flownotes.core.paper
import android.graphics.Canvas
import com.flownotes.core.model.PaperType

interface PaperRendererContract {
    fun drawPaper(canvas: Canvas, width: Float, height: Float, type: PaperType, isDark: Boolean = false)
}
```

### 5. `PdfManagerContract` (`com.flownotes.core.pdf`)
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

### 6. `AudioMemoContract` (`com.flownotes.core.audio`)
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

### 7. `LocalAiStudyContract` (`com.flownotes.core.ai`)
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

## Code Layout

```
FlowNotes/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/flownotes/app/
│       │   │   ├── FlowNotesApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── NoteEditorActivity.kt
│       │   │   ├── FlashcardStudyActivity.kt
│       │   │   ├── model/Models.kt
│       │   │   ├── engine/
│       │   │   │   ├── StrokeEngine.kt
│       │   │   │   └── ShapeRecognizer.kt
│       │   │   ├── view/
│       │   │   │   ├── CanvasView.kt
│       │   │   │   └── PaperTemplateRenderer.kt
│       │   │   ├── pdf/PdfAnnotationManager.kt
│       │   │   ├── audio/AudioMemoManager.kt
│       │   │   ├── data/NoteRepository.kt
│       │   │   └── ai/LocalAiStudyEngine.kt
│       │   └── res/
│       │       ├── layout/
│       │       │   ├── activity_main.xml
│       │       │   ├── activity_note_editor.xml
│       │       │   ├── activity_flashcards.xml
│       │       │   ├── dialog_new_notebook.xml
│       │       │   └── item_notebook.xml
│       │       ├── drawable/
│       │       │   └── bg_floating_dock.xml
│       │       └── values/
│       │           ├── colors.xml
│       │           ├── strings.xml
│       │           └── themes.xml
│       └── test/java/com/flownotes/app/
│           ├── model/ModelSerializationTest.kt
│           ├── engine/StrokeEngineTest.kt
│           ├── engine/ShapeRecognizerTest.kt
│           ├── tape/ActiveRecallTapeTest.kt
│           ├── paper/PaperTemplateTest.kt
│           ├── srs/SrsEngineTest.kt
│           └── ai/LocalAiStudyEngineTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── index.html
├── css/style.css
├── js/
│   ├── canvas.js
│   └── templates.js
├── lib/
├── server.py
└── start.sh
```
