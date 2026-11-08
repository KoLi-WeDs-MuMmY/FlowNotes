# FlowNotes Codebase & Environment Survey Report

**Author**: survey_explorer_1 (Codebase and Environment Explorer)  
**Date**: 2026-11-08  
**Working Directory**: `/storage/emulated/0/FlowNotes/.agents/teamwork/survey_explorer_1`  
**Reference Document**: `/storage/emulated/0/FlowNotes/.agents/teamwork/ORIGINAL_REQUEST.md`  
**Target Recipient**: Project Orchestrator (`c26bb363-4e34-49ee-b305-ae12e9dfc336`)

---

## 1. Observation

### 1.1 Existing Workspace Inventory (`/storage/emulated/0/FlowNotes`)

A comprehensive filesystem scan of `/storage/emulated/0/FlowNotes` revealed the presence of two complementary stacks: a native Android Kotlin codebase and a web-based companion stack.

```
/storage/emulated/0/FlowNotes/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/flownotes/app/
│           │   ├── FlashcardStudyActivity.kt (116 lines)
│           │   ├── MainActivity.kt (292 lines)
│           │   ├── NoteEditorActivity.kt (304 lines)
│           │   ├── ai/
│           │   │   └── LocalAiStudyEngine.kt (212 lines)
│           │   ├── audio/
│           │   │   └── AudioMemoManager.kt (106 lines)
│           │   ├── data/
│           │   │   └── NoteRepository.kt (299 lines)
│           │   ├── engine/
│           │   │   ├── ShapeRecognizer.kt (66 lines)
│           │   │   └── StrokeEngine.kt (72 lines)
│           │   ├── model/
│           │   │   └── Models.kt (121 lines)
│           │   ├── pdf/
│           │   │   └── PdfAnnotationManager.kt (111 lines)
│           │   └── view/
│           │       ├── CanvasView.kt (372 lines)
│           │       └── PaperTemplateRenderer.kt (208 lines)
│           └── res/
│               ├── drawable/
│               │   └── bg_floating_dock.xml (10 lines)
│               └── values/
│                   ├── colors.xml (14 lines)
│                   ├── strings.xml (14 lines)
│                   └── themes.xml (12 lines)
├── css/
│   └── style.css (1142 lines)
├── js/
│   ├── canvas.js (602 lines)
│   └── templates.js (247 lines)
├── lib/
│   ├── jspdf.umd.min.js (364,463 bytes)
│   ├── lucide.min.js (481,206 bytes)
│   ├── pdf.min.js (320,004 bytes)
│   └── pdf.worker.min.js (1,087,212 bytes)
├── server.py (338 lines)
├── start.sh (6 lines)
└── data/ (created on runtime)
    ├── notebooks/
    └── uploads/
```

### 1.2 Inspection of Native Kotlin Source Code

Every Kotlin file in `app/src/main/java/com/flownotes/app/` was viewed and analyzed:

1. **`model/Models.kt` (121 lines)**:
   - Enums: `PenTool` (`BALLPOINT`, `FOUNTAIN`, `HIGHLIGHTER`, `PENCIL`, `ERASER_STROKE`, `ERASER_PIXEL`, `TAPE`, `SHAPE`, `LASSO`, `LASER`), `PaperType` (`BLANK`, `RULED`, `COLLEGE_RULED`, `GRID`, `DOTTED`, `CORNELL`, `PLANNER`, `DARK_GRID`, `SEPIA`).
   - Data classes:
     - `Point(val x: Float, val y: Float, val pressure: Float = 0.5f, val timestamp: Long = System.currentTimeMillis())`
     - `Stroke(val id: String, val tool: PenTool, val color: Int, val strokeWidth: Float, val points: MutableList<Point>, val shapeType: String?)`
     - `ActiveRecallTape(val id: String, var left: Float, var top: Float, var right: Float, var bottom: Float, val color: Int, var isRevealed: Boolean)` with `contains(x, y)` bounding-box collision detection.
     - `TextAnnotation`, `StickyNote`, `Page`, `Notebook`, `Flashcard` (with SuperMemo parameters: `reviewIntervalDays`, `repetitions`, `easeFactor`).

2. **`engine/StrokeEngine.kt` (72 lines)**:
   - `createSmoothPath(points: List<Point>): Path`: Builds a continuous Android `Path` using quadratic Bezier mid-points (`quadTo(pPrev.x, pPrev.y, midX, midY)`). Emits a 0.1px micro-line segment when `points.size == 1` to render taps.
   - `intersectsStroke(stroke: Stroke, x: Float, y: Float, radius: Float): Boolean`: Computes Euclidean distance `hypot(pt.x - x, pt.y - y) <= radius` for eraser collision.
   - `calculateDynamicWidth(baseWidth: Float, current: Point, previous: Point): Float`: Calculates pressure-velocity dynamic line thickness clamped to $[0.4 w_{base}, 2.2 w_{base}]$.

3. **`engine/ShapeRecognizer.kt` (66 lines)**:
   - Sealed class `DetectedShape`: `Line`, `Rectangle`, `Circle`, `Triangle`.
   - Recognizes shapes with $\ge 4$ points:
     - Straight line: diagonal $> 40$, end-to-end distance $> 0.88 \times \text{diagonal}$.
     - Closed shape: end-to-end distance $< 0.35 \times \text{diagonal}$. Aspect ratio in $[0.75, 1.35]$ snaps to `Circle`; otherwise snaps to `Rectangle`.

4. **`view/CanvasView.kt` (372 lines)**:
   - Hardware-accelerated native Android `View` (`setLayerType(LAYER_TYPE_HARDWARE, null)`).
   - Render pass in `onDraw(canvas)`:
     1. Background: PDF bitmap (`pdfPageBitmap`) or procedural paper template (`PaperTemplateRenderer.drawPaper`).
     2. Committed strokes: `drawSingleStroke` (anti-aliased, round cap/join, or semi-transparent highlighter with alpha 110).
     3. Active recall tapes: `drawActiveRecallTape` (solid pastel block when concealed, dashed outline with low alpha when revealed).
     4. Current live stroke in progress.
     5. Live tape drag rectangle preview.
   - Input tracking in `onTouchEvent(event)`:
     - Tape mode: Taps toggle reveal/conceal on existing tapes; drags create new tape rectangles (minimum $20 \times 15$ px).
     - Eraser mode: Erases intersecting strokes instantly.
     - Pen/Fountain/Highlighter: Smooth stroke collection and commit.
     - Shape mode: Snaps to recognized geometric vectors.
   - Undo/Redo: Snapshot history stack (`ArrayDeque<PageStateSnapshot>`, depth 40).

5. **`view/PaperTemplateRenderer.kt` (208 lines)**:
   - Procedural vector rendering on `Canvas`:
     - `Blank`: White or Dark Slate (`#1A202C`).
     - `Ruled` & `College Ruled`: Horizontal lines with left vertical margin line at $x=180$ px.
     - `Grid`: Orthogonal anti-aliased grid lines ($50$ px spacing).
     - `Dotted`: Circular dots ($2.5$ px radius, $50$ px grid).
     - `Cornell`: Top header, left Cue column ($32\%$ width), right Notes area with ruled lines, bottom Summary box ($20\%$ height).
     - `Planner`: 6-column/row study planner grid (Monday to Sunday/Review).
     - `Sepia`: Vintage cream background (`#FDF6E2`) with soft lines.

6. **`pdf/PdfAnnotationManager.kt` (111 lines)**:
   - `openPdf(pdfFile: File): Int`: Opens `ParcelFileDescriptor` with `PdfRenderer` and queries page count.
   - `renderPageToBitmap(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap?`: Renders PDF page to `ARGB_8888` display bitmap using `PdfRenderer.Page.render()`.
   - `exportNotebookToPdf(notebook: Notebook, outputFile: File): Boolean`: Generates multi-page standard PDF using native `PdfDocument`, compositing paper backgrounds, handwritten ink strokes, and active recall tapes.

7. **`audio/AudioMemoManager.kt` (106 lines)**:
   - `startRecording(pageId: String): File?`: Records AAC/MPEG-4 audio via `MediaRecorder` saved to `context.filesDir/audio/memo_<pageId>_<timestamp>.m4a`.
   - `playAudio(file: File, onComplete: () -> Unit)`: Plays back recorded lecture audio using `MediaPlayer`.

8. **`ai/LocalAiStudyEngine.kt` (212 lines)**:
   - 100% On-device, $0 cost, zero network dependencies.
   - `generateLocalSummary`: Frequency-weighted sentence extraction and keyword ranking.
   - `solveProblemLocally`: 3-step structured breakdown (Knowns, Methodological Formulation, Synthesis).
   - `generateLocalFlashcards`: Regex-based term/definition extraction (`X is Y`, `X refers to Y`, `X = Y`) and active recall cards with SuperMemo parameters.

9. **`data/NoteRepository.kt` (299 lines)**:
   - Stores notebooks as UTF-8 JSON files in `context.filesDir/notebooks/<id>.json`.
   - Automatically initializes a default "Welcome to FlowNotes" notebook with sample Cornell notes, active recall tape, grid, and planner pages.

10. **`MainActivity.kt` (292 lines)**:
    - Library dashboard: `RecyclerView` grid, folder filtering ("All Notes", "Study", "Work", "Personal"), search filtering, FAB new notebook, and PDF import via `Intent.ACTION_GET_CONTENT`.

11. **`NoteEditorActivity.kt` (304 lines)**:
    - Editor activity: Top toolbar, multi-page navigation, template picker, PDF export, drawer layout for on-device AI assistant, floating pen dock with tool and color presets, reveal-all and conceal-all tape toggles.

12. **`FlashcardStudyActivity.kt` (116 lines)**:
    - Spaced repetition study mode: 3D flip card animation, hints, and SuperMemo SM-2 rating buttons (Again, Hard, Good, Easy).

### 1.3 Missing Native Android Assets & Configuration

Critical omissions identified in the native Android project:
- **Missing `AndroidManifest.xml`**: Does not exist under `app/src/main/`.
- **Missing XML Layouts in `app/src/main/res/layout/`**: The directory does not exist. The activities explicitly reference:
  - `R.layout.activity_main` (with IDs: `rv_notebooks`, `btn_folder_*`, `tv_folder_title`, `fab_new_notebook`, `btn_import_pdf`, `search_view`)
  - `R.layout.activity_note_editor` (with IDs: `canvas_view`, `drawer_layout`, `btn_back`, `tv_editor_title`, `btn_prev_page`, `btn_next_page`, `btn_add_page`, `btn_undo`, `btn_redo`, `btn_change_template`, `btn_export_pdf`, `btn_open_ai`, `btn_flashcards`, `floating_pen_dock`, `tool_*`, `btn_reveal_tapes`, `btn_conceal_tapes`, `color_*`, `size_*`, `tv_page_indicator`, `tv_ai_output`, `et_ai_question`, `btn_ai_summarize`, `btn_ai_problem_solver`, `btn_ai_gen_cards`, `btn_ai_ask`, `btn_insert_ai_note`)
  - `R.layout.activity_flashcards` (with IDs: `tv_deck_title`, `btn_close_study`, `tv_card_front`, `tv_card_back`, `tv_card_hint`, `tv_study_progress`, `card_front_layout`, `card_back_layout`, `card_container`, `btn_rate_again`, `btn_rate_hard`, `btn_rate_good`, `btn_rate_easy`)
  - `item_notebook.xml` (for `NotebookAdapter` in `MainActivity`)
- **Missing Build Configuration**: No `settings.gradle`, `build.gradle`, or gradle wrapper exists in the project root.

### 1.4 Web Companion Inspection (`server.py`, `js/`, `css/`, `lib/`)

- `server.py`: Verified functional on `127.0.0.1:8080`. Handlers implement REST API for `/api/status`, `/api/notes`, `/api/upload-pdf`, `/api/ai/chat`, `/api/ai/flashcards`.
- `js/canvas.js` (602 lines): Complete web canvas engine matching all native drawing features (Catmull-Rom smoothing, active recall tapes, stroke eraser, shape preview, laser pointer).
- `js/templates.js` (247 lines): Complete web procedural paper template engine.
- `css/style.css` (1142 lines): Polished CSS stylesheet with light/dark themes, floating pen dock, responsive layouts, flashcards, AI drawer.
- `lib/`: High-quality vendor libraries (`pdf.js`, `jspdf`, `lucide`).
- **Missing**: `index.html` binding the CSS, JS, and vendor libraries for browser/WebView rendering.

### 1.5 Android Environment & Toolchain Diagnostic Results

Direct CLI diagnostics executed on the host system:

| Parameter | Diagnostic Output | Analysis |
|---|---|---|
| OS / Kernel | `Linux localhost 5.10.246-android12-9-00012-g730d06ec05ff-ab15159037 aarch64 Android` | Native Android Linux ARM64 (kernel 5.10) |
| Android OS Version | `ro.build.version.release = 16` | Android 16 |
| Android SDK API Level | `ro.build.version.sdk = 36` | API 36 |
| Memory | `total = 7524 MB, free = 208 MB, buff/cache = 2575 MB, available = 1714 MB, swap = 8191 MB` | 8 GB RAM + 8 GB swap, ample capacity for compilation |
| Storage | `/storage/emulated/0`: 222 GB total, 49 GB available (78% used) | 49 GB free space |
| Python Runtime | `Python 3.14.6` at `/data/user/0/com.agy/files/usr/bin/python3` | Available and verified |
| Package Manager | `pkg selftest` -> PASS (all 4 repos resolve, aarch64/stable) | Fully operational |
| OpenJDK Available | `openjdk-17` (17.0.21-1, 96MB), `openjdk-21` (21.0.13-1, 106MB), `openjdk-25` | Available via `pkg install` |
| Kotlin Available | `kotlin` (2.4.21, 87MB, depends on `openjdk-21`) | Available via `pkg install` |
| Gradle Available | `gradle` (9.9.0, 132MB, depends on `openjdk-21`) | Available via `pkg install` |
| Android Build Tools | `aapt` (16.0.0.4-4), `aapt2` (16.0.0.4-4), `d8` (37.0.0), `apksigner` (37.0.0), `android-tools` (37.0.0p1-3) | Available via `pkg install` |
| System Framework | `/system/framework/framework.jar` (49 MB, containing `classes.dex` - `classes6.dex`) | On-device bootclasspath |
| Network Connectivity | `curl -I https://repo1.maven.org/maven2/` -> HTTP/2 200 | Full access to Maven Central |
| Sandbox Execution | `run_command` requires `BypassSandbox: true` | Standard bwrap is not supported on Android kernel |
| Storage `noexec` | Shell scripts require explicit interpreter (`bash script.sh`, `python script.py`) | Standard Android shared storage constraint |
| Shizuku / `rish` | `rish: rish_shizuku.dex not found` | Elevated ADB not currently configured; standard user-level execution is sufficient |

---

## 2. Logic Chain

### 2.1 From Observations to Architecture Requirements

1. **Observation**: The user request explicitly demands a 100% offline, native Android digital note-taking and PDF annotation app in Kotlin (FlowNotes), replicating Freenotes with $0 hosting cost and 0 cloud dependencies.
2. **Observation**: The workspace already has 12 complete, production-quality Kotlin classes in `app/src/main/java/com/flownotes/app/` covering all five core requirement domains:
   - R1 (Digital Ink): `CanvasView.kt`, `StrokeEngine.kt`, `ShapeRecognizer.kt`, `Models.kt`.
   - R2 (Active Recall): `ActiveRecallTape` in `CanvasView.kt`, `FlashcardStudyActivity.kt`.
   - R3 (Templates & PDF): `PaperTemplateRenderer.kt`, `PdfAnnotationManager.kt`.
   - R4 (Voice Memo): `AudioMemoManager.kt`.
   - R5 (Offline AI & Storage): `LocalAiStudyEngine.kt`, `NoteRepository.kt`.
3. **Inference**: The Kotlin source code is structurally sound and directly fulfills R1-R5. It does not need to be rewritten from scratch; it requires:
   - Creating the missing Android resource XML files (`AndroidManifest.xml`, layout XMLs in `res/layout/`).
   - Establishing standard build definitions (`build.gradle.kts`, `settings.gradle.kts`).
   - Setting up an on-device unit test suite to verify pure algorithmic correctness.

### 2.2 From Environment Constraints to Build & Test Strategy

1. **Observation**: The host is an Android 16 device running native Linux ARM64 with `pkg` providing `openjdk-21`, `kotlin 2.4.21`, `gradle 9.9.0`, `aapt2`, `d8`, and `apksigner`.
2. **Observation**: Running a full standard desktop Gradle Android build on mobile can encounter SDK path issues (Android Gradle Plugin expects a desktop Android SDK directory with `platforms/android-XX` and platform tools).
3. **Observation**: At the same time, `kotlinc`, `aapt2`, `d8`, and `java` can compile, dex, and package Android apps directly via CLI scripts or Gradle with Android stubs. Furthermore, pure JVM unit tests can execute directly on device via `kotlinc` + `java`.
4. **Observation**: A complete web companion stack exists (`server.py`, `js/canvas.js`, `js/templates.js`, `css/style.css`, `lib/lucide.min.js`, `lib/pdf.min.js`), which runs on `http://127.0.0.1:8080`.
5. **Inference**: The most resilient, verifiable engineering approach is a **Dual-Track Build & Verification Strategy**:
   - **Track 1 (Native Android Kotlin Project)**:
     - Provide full Gradle configuration (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`).
     - Provide complete `AndroidManifest.xml` and all XML layouts (`activity_main.xml`, `activity_note_editor.xml`, `activity_flashcards.xml`, `item_notebook.xml`).
     - Implement pure unit tests runnable with Kotlin/Java on device to mathematically verify:
       - Bezier curve calculation and dynamic pen width (`StrokeEngineTest`).
       - Shape recognition heuristics for lines, rectangles, circles, triangles (`ShapeRecognizerTest`).
       - Active Recall Tape bounding-box containment and reveal toggles (`ActiveRecallTapeTest`).
       - On-device extractive summarization, formula extraction, and flashcard generation (`LocalAiStudyEngineTest`).
       - JSON serialization and persistence roundtrip (`NoteRepositoryTest`).
   - **Track 2 (Interactive Companion Verification)**:
     - Provide `index.html` linking `css/style.css`, `lib/`, and `js/` to run the FlowNotes interface on `http://127.0.0.1:8080`.
     - Enables immediate, interactive verification of canvas drawing, active recall tape interaction, and paper templates inside Android browser/WebView with zero build overhead.

---

## 3. Caveats

1. **Android Gradle Plugin (AGP) Heavyweight Requirements**:
   - Running `gradle assembleDebug` on-device with the official AGP plugin requires a full Android SDK directory containing `platforms/android-34/android.jar`. If full AGP proves heavy or dependencies take time to resolve on mobile, the CLI direct compilation pipeline (`kotlinc` + `aapt2` + `d8`) and pure Kotlin unit tests guarantee 100% build and verification coverage without blocking.
2. **Shizuku ADB Elevated Shell**:
   - `rish` is currently non-operational due to missing `rish_shizuku.dex`. This does not hinder compilation, unit testing, or local execution, as all needed operations run within the standard AGY user sandbox.
3. **Audio Permissions at Runtime**:
   - `MediaRecorder` requires user-granted `RECORD_AUDIO` permission on device. The code already gracefully handles permission rejection by returning null without crashing.

---

## 4. Conclusion

1. **Codebase Status**:
   - The core business and graphics logic for FlowNotes is already implemented in 12 clean Kotlin source files adhering to R1-R5.
   - The primary development tasks are:
     - Generating the missing XML layouts in `app/src/main/res/layout/`.
     - Creating `app/src/main/AndroidManifest.xml`.
     - Creating root and module `build.gradle.kts` / `settings.gradle.kts`.
     - Creating `index.html` for the local verification companion.
     - Creating an exhaustive unit test suite for algorithmic and data model verification.
2. **Build & Execution Recommendation**:
   - Adopt a dual-track strategy: Standard Gradle Android structure + CLI on-device test pipeline + local web companion.
   - All tests and verification will run 100% offline with $0 cloud cost.

---

## 5. Verification Method

### 5.1 Independent Codebase Verification Commands

Execute the following commands from `/storage/emulated/0/FlowNotes` (always with `BypassSandbox: true`):

```bash
# 1. Verify existence and line counts of all 12 native Kotlin files
wc -l /storage/emulated/0/FlowNotes/app/src/main/java/com/flownotes/app/*.kt
wc -l /storage/emulated/0/FlowNotes/app/src/main/java/com/flownotes/app/*/*.kt

# 2. Verify existence of web companion assets
ls -lh /storage/emulated/0/FlowNotes/css/style.css
ls -lh /storage/emulated/0/FlowNotes/js/*.js
ls -lh /storage/emulated/0/FlowNotes/lib/*.js

# 3. Verify Python server execution
python3 -c "import server; print('Server module verified, DATA_DIR:', server.DATA_DIR)"

# 4. Verify system toolchain availability via pkg
pkg info openjdk-21 | grep -E "name:|version:|size:"
pkg info kotlin | grep -E "name:|version:|size:"
pkg info gradle | grep -E "name:|version:|size:"
pkg info aapt2 | grep -E "name:|version:|size:"
pkg info d8 | grep -E "name:|version:|size:"
```

### 5.2 Invalidation Conditions

The conclusions herein would be invalidated if:
1. `pkg` repositories become unreachable or fail to provide `openjdk-21` / `kotlin`.
2. The Kotlin codebase in `app/` is removed or modified in an incompatible manner.
3. The device filesystem `/storage/emulated/0` is unmounted or corrupted.
