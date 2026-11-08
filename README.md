# FlowNotes (Freenotes Replica for Android)

> **A 100% Offline, Zero-Cost Digital Note-Taking & PDF Annotation App for Android written in Kotlin.**  
> Replicating the core capabilities of **Freenotes** (`ai.note.notepad.memo.journal.freenote`).

---

## 🌟 Highlights & $0 Serverless Architecture

FlowNotes is designed to cost **$0 to host and run**:
- **No Backend / No Servers:** Runs entirely client-side on the user's Android device.
- **No Cloud Database Subscriptions:** All notebooks, strokes, pages, active recall tapes, and voice memos are persisted directly into private device storage via JSON & SQLite.
- **No Paid AI APIs:** Equipped with a **100% on-device AI Study Engine** (`LocalAiStudyEngine.kt`) that generates instant executive summaries, keyword maps, problem-solving breakdowns, and interactive flashcards without making a single network call.
- **Zero Network Dependency:** Fully functional in airplane mode; zero private user data ever leaves the device.

---

## 🚀 Key Features

### 1. Realistic Digital Handwriting & Pen Engine (`CanvasView.kt`)
- **Low-Latency Stylus & Touch Support:** Optimized `MotionEvent` listener tracking coordinate history, pressure, and drawing velocity.
- **Smooth Vector Strokes:** Real-time quadratic Bezier spline interpolation (`StrokeEngine.kt`) eliminating jagged edges.
- **Customizable Pen Dock:**
  - **Ballpoint Pen:** Crisp, uniform line.
  - **Fountain Pen:** Calligraphic stroke with dynamic width based on stylus pressure and drawing speed.
  - **Highlighter:** Semi-transparent ink with soft overlay blending.
  - **Pencil:** Textured graphite stroke.
  - **Stroke Eraser:** Erases whole stroke on touch with collision hit-testing.
  - **Geometric Shape Snapping:** Automatically detects hand-drawn lines, rectangles, squares, and circles (`ShapeRecognizer.kt`).
  - **Full Undo / Redo:** Non-destructive snapshot history stack.

### 2. Signature Active Recall Tape Tool (`ActiveRecallTape.kt`)
- Replicates Freenotes' signature study tool:
  - Drag and drop colored study tape rectangles over keywords, answers, and formulas.
  - Tap any tape to toggle between **Concealed** (solid paper tape) and **Revealed** (transparent dashed boundary).
  - One-tap **Reveal All** and **Conceal All** quick buttons in the floating toolbar for exam self-testing.

### 3. Procedural Paper Templates (`PaperTemplateRenderer.kt`)
High-resolution procedural backgrounds rendered directly onto the Android `Canvas`:
- **Cornell Notes:** Complete 3-section layout (Cue column, Note-taking area, Summary box).
- **5mm Grid / Graph Paper:** Clean engineering and math grid.
- **Dot Grid:** Subtle dot matrix for bullet journaling.
- **Standard Ruled & College Ruled:** Classic lined paper with red margin guide.
- **Weekly Study Planner:** Multi-column schedule and task checklist.
- **Dark Mode & Sepia:** Warm eye-friendly vintage paper and dark theme grid.

### 4. Native On-Device PDF Annotation & Export (`PdfAnnotationManager.kt`)
- **Zero Third-Party PDF Cloud Dependencies:**
  - Uses Android's native `android.graphics.pdf.PdfRenderer` to render imported PDF pages into high-resolution bitmaps directly under the ink layer.
  - Write, highlight, and tape directly over lecture slides and textbook pages.
  - Uses `android.graphics.pdf.PdfDocument` to export annotated multi-page notebooks to standard PDF files for sharing.

### 5. On-Device AI Study Assistant & Flashcards (`LocalAiStudyEngine.kt`)
- **Instant Note Summaries:** Frequency-ranked extractive summarization generating bullet points and key takeaways.
- **Step-by-Step Problem Solver:** Structural decomposition of questions and formulas.
- **Automated Flashcard Generator:** Automatically parses definitions and questions from notes into study decks.
- **Interactive Flashcard Study Mode (`FlashcardStudyActivity.kt`):**
  - 3D card flip animation revealing questions on front and answers on back.
  - Spaced Repetition (Again, Hard, Good, Easy) rating for long-term memory retention.

### 6. Voice Memos Synced to Pages (`AudioMemoManager.kt`)
- Record lectures or voice notes directly on notebook pages using native Android `MediaRecorder` saved as `.m4a` files.
- Inline playback controls (`MediaPlayer`) with zero external streaming services.

---

## 📁 Project Architecture

```
/storage/emulated/0/FlowNotes/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/flownotes/app/
│       │   │   ├── MainActivity.kt               # Library view, folders, notebooks grid
│       │   │   ├── NoteEditorActivity.kt         # Full-screen canvas editor & floating tool dock
│       │   │   ├── FlashcardStudyActivity.kt     # 3D flip card study session with SRS
│       │   │   ├── model/
│       │   │   │   └── Models.kt                 # Stroke, ActiveRecallTape, Page, Notebook, Flashcard
│       │   │   ├── engine/
│       │   │   │   ├── StrokeEngine.kt           # Bezier curve smoothing & hit testing
│       │   │   │   └── ShapeRecognizer.kt        # Geometric shape recognition
│       │   │   ├── view/
│       │   │   │   ├── CanvasView.kt             # Custom hardware-accelerated drawing View
│       │   │   │   └── PaperTemplateRenderer.kt  # Procedural Cornell, Grid, Dotted, Planner
│       │   │   ├── pdf/
│       │   │   │   └── PdfAnnotationManager.kt   # Native Android PdfRenderer & PdfDocument
│       │   │   ├── ai/
│       │   │   │   └── LocalAiStudyEngine.kt     # 100% on-device zero-cost NLP study assistant
│       │   │   ├── audio/
│       │   │   │   └── AudioMemoManager.kt       # Native MediaRecorder voice notes
│       │   │   └── data/
│       │   │       └── NoteRepository.kt         # Local private JSON storage & indexing
│       │   └── res/
│       │       ├── layout/
│       │       │   ├── activity_main.xml
│       │       │   ├── activity_note_editor.xml
│       │       │   ├── activity_flashcards.xml
│       │       │   ├── item_notebook_card.xml
│       │       │   └── dialog_new_notebook.xml
│       │       ├── values/
│       │       │   ├── colors.xml
│       │       │   ├── strings.xml
│       │       │   └── themes.xml
│       │       └── drawable/
│       │           └── bg_floating_dock.xml
│       └── test/java/com/flownotes/app/
│           └── EngineAndAiTest.kt                # Unit test suite verifying core logic
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## 🛠️ Building & Running

### Opening in Android Studio
1. Open Android Studio on your workstation or device.
2. Select **Open an existing Android Studio project** and navigate to `/storage/emulated/0/FlowNotes`.
3. Gradle will sync dependencies automatically (`AndroidX Core`, `Material Components`, `RecyclerView`, `CardView`, `DrawerLayout`).
4. Click **Run** or press `Shift + F10` to install on your target Android device or tablet.

### Running with Gradle CLI
```bash
./gradlew assembleDebug
# Installs directly to connected device:
./gradlew installDebug
```

### Elevated Android Operations (Antigravity Shizuku `rish`)
If running directly inside the Antigravity Android environment:
```bash
# Check device package status:
rish -c "pm list packages | grep flownotes"

# Install assembled debug APK:
rish -c "pm install -r /storage/emulated/0/FlowNotes/app/build/outputs/apk/debug/app-debug.apk"
```

---

## 📜 License
MIT License. Free and open-source for personal study, notes, and PDF markup.
