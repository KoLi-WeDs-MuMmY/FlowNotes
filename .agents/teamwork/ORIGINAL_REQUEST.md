# Original User Request

## Initial Request — 2026-11-08T09:39:39Z

Build a 100% offline, native Android digital note-taking and PDF annotation app in Kotlin (FlowNotes), replicating the core capabilities of Freenotes (ai.note.notepad.memo.journal.freenote) with zero backend, zero hosting costs, and zero external API dependencies.

Working directory: `/storage/emulated/0/FlowNotes`
Integrity mode: development

## Requirements

### R1. Native Handwriting & Digital Ink Engine
- Implement a custom Android `CanvasView` in Kotlin with low-latency touch and stylus support (`MotionEvent`).
- Deliver smooth Bezier / spline curve interpolation for natural handwriting without jagged edges.
- Provide customizable pens and tools:
  - Ballpoint pen (consistent crisp line)
  - Fountain pen (dynamic stroke width based on pressure and drawing velocity)
  - Highlighter (semi-transparent overlay blending)
  - Pencil (textured aesthetic line)
  - Stroke Eraser (instant removal of entire stroke on touch) and Pixel/Area Eraser
  - Geometric shape recognizer (auto-snaps rough hand-drawn lines, rectangles, and circles)
  - Full Undo & Redo history stacks

### R2. Active Recall Study Tape & Flashcards
- Implement Freenotes' signature Active Recall Tape tool:
  - Allows users to drag and place colored tape rectangles over keywords, answers, and formulas.
  - Interactive tap-to-reveal and tap-to-conceal toggling for active recall testing.
  - Quick action buttons to "Reveal All" and "Conceal All" tapes across the note page for exam revision.
- Deliver an interactive On-Device Flashcard Study mode with 3D card flipping, answer hints, and Spaced Repetition (Again, Hard, Good, Easy) rating.

### R3. Procedural Paper Templates & PDF Annotation
- Procedurally render authentic paper backgrounds directly on canvas:
  - Cornell Notes layout (left Cue column, right Notes section, bottom Summary box)
  - 5mm Grid / Graph paper
  - Dot Grid (bullet journaling)
  - Standard Ruled and College Ruled with left margin
  - Weekly Study Planner grid
  - Dark Mode paper and Sepia vintage paper
- Native PDF handling with zero external cloud dependencies:
  - Import PDF files using Android's native `android.graphics.pdf.PdfRenderer`.
  - Display PDF pages as background layers for handwritten notes, highlighting, and annotations.
  - Export annotated notebooks as multi-page PDF documents using `android.graphics.pdf.PdfDocument`.

### R4. Voice Memo Audio Recording
- Support recording voice lecture notes synced directly to notebook pages using Android's native `MediaRecorder`.
- Provide inline playback controls (`MediaPlayer`) without any external audio services.

### R5. Zero-Cost, 100% Offline Architecture (Zero Server)
- All notebooks, pages, strokes, audio memos, and PDF files must be saved locally on the device (app private storage in JSON / SQLite format).
- Built-in on-device study assistant providing extractive summarization, formula extraction, and automated flashcard generation with zero remote backend, zero cloud hosting fees, and zero API subscriptions.

## Acceptance Criteria

### Build & Architecture Guardrails
- [ ] Compiles cleanly as a native Kotlin Android project targeting modern Android SDK.
- [ ] Requires no backend server, no remote database, and no paid cloud APIs ($0 hosting and runtime cost).
- [ ] Operates 100% functionally in offline / airplane mode.

### Feature Verification
- [ ] Drawing canvas accurately tracks stylus/touch input and renders smooth strokes with pen, fountain, and highlighter modes.
- [ ] Stroke eraser cleanly identifies and deletes intersecting ink strokes.
- [ ] Active Recall Tape draws over canvas regions, toggles visibility on user tap, and hides/reveals text underneath.
- [ ] Page template switcher updates the background between Cornell, Grid, Dotted, Ruled, and Planner layouts.
- [ ] PDF documents can be imported, displayed page-by-page, marked up with ink, and exported to a standard PDF file.
- [ ] Flashcard study activity flips cards on tap and progresses through study decks with spaced repetition scores.
- [ ] Notebooks and pages persist across app restarts using local storage.
