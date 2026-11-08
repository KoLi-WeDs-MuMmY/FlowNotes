# FlowNotes Test Infrastructure Specification (TEST_INFRA.md)

## 1. Overview & Test Architecture

This document defines the End-to-End (E2E) Test Architecture and Validation Infrastructure for **FlowNotes** (`ai.note.notepad.memo.journal.freenote` replica). FlowNotes is a 100% offline, zero-cloud, zero-cost digital note-taking and PDF annotation application.

The test infrastructure is designed according to the Dual-Track E2E validation mandate:
- Operates independently from internal implementation variations.
- Validates system behavior against authoritative requirements from `ORIGINAL_REQUEST.md` and interface contracts from `PROJECT.md`.
- Enforces zero remote network dependencies (runs in pure airplane mode / offline Android shell).
- Evaluates behavior across 4 rigorous tiers comprising **437 distinct automated tests** across all 38 cataloged features.

```
+-------------------------------------------------------------------------------------------------+
|                                 FlowNotes E2E Test Suite (437 Tests)                           |
+-------------------------------------------------------------------------------------------------+
|                                                                                                 |
|   +------------------------------------+             +------------------------------------+     |
|   |   Tier 1: Feature Coverage         |             |   Tier 2: Boundary & Corner Cases  |     |
|   |   - 38 Features x 5 Tests = 190    |             |   - 38 Features x 5 Tests = 190    |     |
|   |   - Tests: F01-01 .. F38-05        |             |   - Tests: B01-01 .. B38-05        |     |
|   +------------------------------------+             +------------------------------------+     |
|                             |                                       |                           |
|                             +-------------------+-------------------+                           |
|                                                 |                                               |
|   +------------------------------------+        |    +------------------------------------+     |
|   |   Tier 3: Pairwise Combinations    |        |    |   Tier 4: Real-World Scenarios     |     |
|   |   - Cross-Subsystem Matrix = 38    |        |    |   - E2E Lifecycle Workflows = 19   |     |
|   |   - Tests: C01 .. C38              |        |    |   - Tests: S01 .. S19              |     |
|   +------------------------------------+        |    +------------------------------------+     |
|                                                 v                                               |
|               +------------------------------------------------------------------+              |
|               | Unified Test Runner (`python3 tests/run_all_tests.py`)           |              |
|               | - Opaque-box execution against contract & functional interfaces  |              |
|               | - Structured JSON/TAP output & colored terminal reporting        |              |
|               | - Exit code 0 on 100% pass; non-zero on any failure              |              |
|               +------------------------------------------------------------------+              |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Test Philosophy

1. **Opaque-Box Requirement-Driven Testing**:
   Tests validate verifiable inputs and observable outputs against the documented specification in `PROJECT.md` and `ORIGINAL_REQUEST.md`. Internal private variables and ephemeral UI widgets are decoupled from assertions; tests interact with data models, mathematical contracts, persistence representations, and interface endpoints.
2. **Zero External API & Zero Network Dependency**:
   FlowNotes is strictly zero-cost and 100% offline. The test harness runs without internet access, external mock servers, or cloud credentials. All oracles are derived mathematically, heuristically from documented specifications, or from self-contained fixtures.
3. **Deterministic Mathematical Oracles**:
   - Bezier mid-point spline interpolation is verified via quadratic curve geometry ($B(t) = (1-t)^2 P_0 + 2(1-t)t P_1 + t^2 P_2$).
   - Dynamic fountain pen stroke width is verified against pressure-velocity transfer functions.
   - SuperMemo SM-2 interval calculations ($I_1 = 1, I_2 = 6, I_n = I_{n-1} \times EF$, $EF' = EF + (0.1 - (5-q)(0.08 + (5-q)0.02))$) are verified against the canonical algorithmic standard.
   - Shape recognition is verified against mathematical bounding-box aspect ratios and euclidean distances.
   - Procedural paper layouts (Cornell cues, margins, 5mm grid, 6-box weekly planners) are verified against coordinate geometry bounds.
4. **Adversarial & Fault Injection Hardening**:
   - Boundary values (negative coordinates, zero-length strokes, empty notebooks, NaN/infinite coordinates).
   - Large payload stress (10,000+ stroke points, 100+ pages, long texts).
   - Corrupted and malformed JSON recovery without app crashes.
   - Resource cleanup (temporary files, SQLite connections, stream closings).

---

## 3. Feature Inventory & Traceability Matrix (38 Features)

| Feature # | Feature Name | Primary Subsystem | Source Requirement | Tier 1 Tests | Tier 2 Tests |
|---|---|---|---|---|---|
| **1** | Custom CanvasView Touch Engine | `core.canvas` | R1 §13 | F01-01..05 | B01-01..05 |
| **2** | Bezier / Spline Interpolation | `core.ink` | R1 §14 | F02-01..05 | B02-01..05 |
| **3** | Ballpoint Pen Mode | `core.ink` | R1 §16 | F03-01..05 | B03-01..05 |
| **4** | Fountain Pen Mode | `core.ink` | R1 §17 | F04-01..05 | B04-01..05 |
| **5** | Semi-Transparent Highlighter | `core.ink` | R1 §18 | F05-01..05 | B05-01..05 |
| **6** | Textured Pencil Tool | `core.ink` | R1 §19 | F06-01..05 | B06-01..05 |
| **7** | Stroke Eraser (Whole Stroke) | `core.ink` | R1 §20 | F07-01..05 | B07-01..05 |
| **8** | Pixel / Area Eraser | `core.ink` | R1 §20 | F08-01..05 | B08-01..05 |
| **9** | Geometric Shape Recognizer | `core.ink` | R1 §21 | F09-01..05 | B09-01..05 |
| **10** | Undo / Redo History Stacks | `core.ink` / `canvas` | R1 §22 | F10-01..05 | B10-01..05 |
| **11** | Active Recall Tape Creation | `core.tape` | R2 §25 | F11-01..05 | B11-01..05 |
| **12** | Tap-to-Reveal / Conceal | `core.tape` | R2 §27 | F12-01..05 | B12-01..05 |
| **13** | Visual Concealed State | `core.tape` | R2 §27 | F13-01..05 | B13-01..05 |
| **14** | Visual Revealed State | `core.tape` | R2 §27 | F14-01..05 | B14-01..05 |
| **15** | Quick Action: Reveal All | `core.tape` | R2 §28 | F15-01..05 | B15-01..05 |
| **16** | Quick Action: Conceal All | `core.tape` | R2 §28 | F16-01..05 | B16-01..05 |
| **17** | 3D Card Flipping Study Mode | `core.srs` | R2 §29 | F17-01..05 | B17-01..05 |
| **18** | Answer Hints on Flashcards | `core.srs` | R2 §29 | F18-01..05 | B18-01..05 |
| **19** | Spaced Repetition (SRS) Rating | `core.srs` | R2 §29 | F19-01..05 | B19-01..05 |
| **20** | Cornell Notes Template | `core.paper` | R3 §33 | F20-01..05 | B20-01..05 |
| **21** | 5mm Grid / Graph Paper | `core.paper` | R3 §34 | F21-01..05 | B21-01..05 |
| **22** | Dot Grid Paper (Bullet Journal) | `core.paper` | R3 §35 | F22-01..05 | B22-01..05 |
| **23** | Standard & College Ruled | `core.paper` | R3 §36 | F23-01..05 | B23-01..05 |
| **24** | Weekly Study Planner Template | `core.paper` | R3 §37 | F24-01..05 | B24-01..05 |
| **25** | Dark Mode & Sepia Vintage Paper | `core.paper` | R3 §38 | F25-01..05 | B25-01..05 |
| **26** | Native PDF Import | `core.pdf` | R3 §40 | F26-01..05 | B26-01..05 |
| **27** | PDF Background Layer Rendering | `core.pdf` | R3 §41 | F27-01..05 | B27-01..05 |
| **28** | Multi-Page PDF Document Export | `core.pdf` | R3 §42 | F28-01..05 | B28-01..05 |
| **29** | Synced Voice Lecture Recording | `core.audio` | R4 §45 | F29-01..05 | B29-01..05 |
| **30** | Inline Voice Memo Playback | `core.audio` | R4 §46 | F30-01..05 | B30-01..05 |
| **31** | 100% Local JSON/SQLite Storage | `core.storage` | R5 §49 | F31-01..05 | B31-01..05 |
| **32** | Extractive Study Summarizer | `core.ai` | R5 §50 | F32-01..05 | B32-01..05 |
| **33** | Formula & Definition Extractor | `core.ai` | R5 §50 | F33-01..05 | B33-01..05 |
| **34** | Automated Flashcard Generation | `core.ai` | R5 §50 | F34-01..05 | B34-01..05 |
| **35** | Local Problem Solver Assistant | `core.ai` | R5 §50 | F35-01..05 | B35-01..05 |
| **36** | Notebook Shelf & Folder Manager | `core.storage` | R5 §49 | F36-01..05 | B36-01..05 |
| **37** | Notebook Creation & Cover Picker | `core.storage` | R5 §49 | F37-01..05 | B37-01..05 |
| **38** | Multi-Page Navigation & Insertion | `core.model` | R3 §40 | F38-01..05 | B38-01..05 |

---

## 4. Four-Tier Testing Thresholds

### Tier 1: Core Feature Coverage ($\ge 190$ tests)
Each of the 38 features is subjected to at least 5 targeted, contract-driven tests validating primary functional operations:
- Feature 1 (Canvas Touch): Down/Move/Up lifecycle, pointer tracking, pressure recording, timestamp monotonic ordering, multi-point capture.
- Feature 2 (Bezier Spline): Single-point handling, mid-point quadratic interpolation calculation, path length preservation, smoothness gradient, monotonic progression.
- Feature 3 (Ballpoint): Uniform width cap, opacity preservation, anti-aliasing flag, stroke color preservation, stroke serialization.
- Feature 4 (Fountain): Pressure sensitivity, velocity inverse relationship, minimum clamp factor (0.4x), maximum clamp factor (2.2x), dynamic stroke variability.
- Feature 5 (Highlighter): Alpha overlay (~43% / 110-alpha), chisel width multiplier (3.5x), underline blending, background visibility preservation, color palette.
- Feature 6 (Pencil): Textured sketch profile, pressure response, tactile width, point density, stroke cap.
- Feature 7 (Stroke Eraser): Intersecting point detection, Euclidean radius tolerance, multi-point stroke removal, non-intersecting stroke preservation, single-point stroke eraser.
- Feature 8 (Pixel Eraser): Segment bounding box culling, local radius threshold, partial stroke split, point list pruning, boundary edge retention.
- Feature 9 (Shape Snapper): Straight line snapping, rectangle recognition, circle center & radius detection, minimum point requirement (<4 returns null), aspect ratio classification.
- Feature 10 (Undo/Redo): Stroke push to undo stack, undo pops stroke, redo restores stroke, new action clears redo stack, multi-stroke sequence consistency.
- Feature 11 (Active Recall Tape Creation): Drag coordinate computation (min/max bounds), pastel color assignment, initial concealed state, tape ID uniqueness, tape storage in page list.
- Feature 12 (Tap-to-Reveal / Conceal): Point containment hit-testing, single tap toggle (concealed -> revealed), second tap toggle (revealed -> concealed), miss tap leaves state unchanged, overlapping tape priority.
- Feature 13 (Visual Concealed State): Opaque fill render state, occlusion property true, alpha level 255/1.0, hidden text simulation, styling flags.
- Feature 14 (Visual Revealed State): Translucent fill render state (low alpha), dashed border styling, revealed property true, revealed text readability, border color match.
- Feature 15 (Reveal All): Bulk page tape update, all `isRevealed` set to true, empty page safety, count of revealed tapes verification, idempotent repeated calls.
- Feature 16 (Conceal All): Bulk page tape update, all `isRevealed` set to false, mixed initial states all reset to false, revision readiness, idempotent repeated calls.
- Feature 17 (3D Card Flip): Card front layout initial visibility, flip action toggles front/back visibility, card index tracking, deck completion trigger, progress indicator format.
- Feature 18 (Answer Hints): Hint text rendering when present, fallback hint text when empty, hint visibility without revealing answer, multi-line hint integrity, hint string truncation safety.
- Feature 19 (SRS SM-2 Rating): Rating 1 (Again: interval=1, reps=0), Rating 2 (Hard: interval=1, ease reduced), Rating 3 (Good: interval expands by EF), Rating 4 (Easy: bonus interval & ease boost), Minimum ease factor floor (1.3).
- Feature 20 (Cornell Template): Cue column boundary at 32% width, top date/topic header, bottom summary section, notes ruling spacing, dark slate mode variant.
- Feature 21 (5mm Grid Paper): Orthogonal coordinate step (50f), boundary line clipping, horizontal & vertical grid lines count, dark mode contrast, coordinate bounds compliance.
- Feature 22 (Dot Grid): Dot matrix anti-aliased points, coordinate stepping, dot radius (2.5f), bullet journal margins, slate dark mode dot color.
- Feature 23 (Ruled & College Ruled): Ruled spacing (70f), College Ruled spacing (52f), red margin line at x=180f, header margin clearance, dark mode ruled colors.
- Feature 24 (Weekly Study Planner): 6-box planner grid division, header division, daily task rows, notes & review section, layout coordinate calibration.
- Feature 25 (Dark & Sepia Paper): Dark mode slate background (`#1A202C`), Sepia vintage cream background (`#FDF6E2`), text contrast readability, dark mode grid lines, theme toggle.
- Feature 26 (Native PDF Import): PDF descriptor opening, page count extraction, out-of-bounds page rejection, resource file existence, close descriptor cleanup.
- Feature 27 (PDF Background Rendering): Bitmap target allocation, ARGB_8888 config, white page background erasure, ink layering on top of PDF, multi-page PDF indexing.
- Feature 28 (Multi-Page PDF Export): Document initialization, page builder sizing (1200x1600), stroke rendering to PDF canvas, tape flattening, output file write.
- Feature 29 (Synced Voice Memo): Audio recording file naming format, AAC/M4A format configuration, recording state flag, page binding, stop recording returns valid file.
- Feature 30 (Inline Playback): Playback initiation, completion callback trigger, stop playback resets flag, seeking time offset, nonexistent audio error handling.
- Feature 31 (Local JSON/SQLite Storage): JSON serialization round-trip, SQLite table creation, note record retrieval, file existence in private storage, zero cloud network check.
- Feature 32 (Extractive Summarizer): Stopword filtering, top keyword extraction, sentence score ranking, structured output sections (Core, Insights, Strategy), empty text fallback.
- Feature 33 (Formula & Definition Extractor): Term-definition regex extraction, LaTeX math regex detection ($...$), equation parsing, keyword tokenization, bullet formulation.
- Feature 34 (Automated Flashcard Gen): Synthesis from note title, synthesis from definitions, hint assignment, deck list generation, empty note fallback deck.
- Feature 35 (Local Problem Solver): 3-step structured output (Knowns, Formulation, Synthesis), input query echoing, step labeling, offline computation, markdown formatting.
- Feature 36 (Notebook Shelf & Folder Manager): Notebook listing, folder grouping ("All Notes", "Study", etc.), title search filtering, sorting by `updatedAt` descending, shelf count accuracy.
- Feature 37 (Notebook Creation & Cover): Title configuration, cover color assignment, cover pattern assignment, default paper type setup, initial page insertion.
- Feature 38 (Multi-Page Navigation): Initial page count, page insertion (`addPage`), next/previous page boundary clamps, current page index pointer, page deletion re-indexing.

Total Tier 1 Tests: **190 tests** ($38 \times 5$).

---

### Tier 2: Boundary & Corner Cases ($\ge 190$ tests)
Each of the 38 features is stressed against 5 boundary, edge, adversarial, or resource-constraint scenarios:
- B01 (Canvas): Zero-width/zero-height viewport, 10,000 rapid motion events, negative coordinates, sub-pixel touches, simultaneous multi-touch rejection.
- B02 (Bezier): Collinear points, identical coincident points ($P_0 = P_1 = P_2$), extreme coordinates ($10^6$), zigzag alternating points, single-point degenerate curve.
- B03 (Ballpoint): 0.0f stroke width clamp, negative width, ultra-wide width (100f), transparent color alpha 0, pure black/white contrast.
- B04 (Fountain): Zero time delta ($\Delta t = 0$, division by zero prevention), extreme stylus velocity ($1000$ px/ms), maximum pressure ($1.0$), zero pressure ($0.0$), backward timestamp ordering.
- B05 (Highlighter): 10 overlapping highlighter strokes saturation check, zero stroke width, negative stroke width, highlighter over dark mode paper, alpha persistence across JSON.
- B06 (Pencil): Extreme pressure jitter, 0-point pencil stroke, pencil on dark vs sepia paper, high-density point clustering, memory footprint under 5000 pencil points.
- B07 (Stroke Eraser): Zero-radius eraser touch, massive radius eraser ($10^4$), touch exactly on stroke bounding box edge, eraser on empty page, eraser on already deleted stroke.
- B08 (Pixel Eraser): Eraser with radius smaller than point distance, erase across closed loop, erase entirely outside canvas, erase at canvas origin (0,0), point list memory reallocation.
- B09 (Shape Recognizer): 3 points (below threshold), open polygon with gap just at 35% threshold, perfectly square rectangle vs circle boundary, degenerate line of length 0, wildly irregular scribble.
- B10 (Undo/Redo): 100 consecutive undos on 5-stroke stack, redo on empty stack, new stroke after partial undo truncating future, undo tape creation, undo/redo cycle memory leak check.
- B11 (Tape Creation): Zero-area tape (point tap drag), inverted coordinates ($x_2 < x_1, y_2 < y_1$), tape extending beyond canvas bounds, 100 overlapping tapes, unicode tape metadata.
- B12 (Tap-to-Reveal): Tap on tape corner pixel ($x=left, y=top$), tap 1 pixel outside boundary, double tap within 5ms, tap with multiple tapes exactly stacked, tap on revealed tape.
- B13 (Visual Concealed): Concealed tape over black text contrast, concealed tape on dark mode paper, concealed tape serialization, opacity validation, zero-width tape.
- B14 (Visual Revealed): Revealed tape border dash array boundary, low alpha boundary clamp, revealed state persistence, revealed tape exported to PDF, color luminance.
- B15 (Reveal All): Reveal All on page with 0 tapes, Reveal All on page with 500 tapes, Reveal All when all already revealed, Reveal All across page navigation, Reveal All performance (<10ms).
- B16 (Conceal All): Conceal All on page with 0 tapes, Conceal All on page with 500 tapes, Conceal All when all already concealed, rapid toggle Reveal All / Conceal All, memory stability.
- B17 (3D Card Flip): Rapid card flip 20 times in succession, flip on empty card text, flip on card with 5000-character text, flip past last card boundary, card index clamp.
- B18 (Answer Hints): 1000-word hint text, hint with special markdown & LaTeX characters, empty string hint, whitespace-only hint, emoji-only hint.
- B19 (SRS Rating): Extreme consecutive fail streak (Again 10x), Ease factor floor barrier (never below 1.30), Rating integer out-of-range (0, 5, -1 handling), Due date overflow protection, New card state transitions.
- B20 (Cornell): Minimal canvas size (100x100), ultra-wide canvas (4000x1000), vertical canvas (1000x4000), dark mode text color clipping, zero-height summary area.
- B21 (Grid): Grid size larger than canvas, fractional grid size (3.1415f), 1000x1000 grid density stress, canvas size not evenly divisible by grid size, grid line stroke width precision.
- B22 (Dot Grid): Dot spacing equal to dot radius, canvas smaller than spacing, dot rendering at float precision edges, large canvas dot count stress, memory allocation per dot.
- B23 (Ruled): Height smaller than header margin, negative line spacing guard, 0 line spacing guard, college ruled with odd height, right margin overflow.
- B24 (Planner): 1-pixel height planner box, odd width planner box partitioning, planner on dark slate, planner with zero-size review box, 7-day text label rendering.
- B25 (Dark & Sepia): Rapid paper type switching (20x), dark mode with light strokes contrast, invalid paper type enum fallback, null theme parameter, color hex parsing safety.
- B26 (PDF Import): 0-byte corrupt PDF file, non-PDF file renamed to .pdf, massive 500-page PDF index request, negative page index (-1), file descriptor leak on failure.
- B27 (PDF Background): Target width/height zero, target width/height 8000x8000 (OOM protection), rendering non-existent page index, rendering closed renderer, bitmap recycling safety.
- B28 (PDF Export): Export notebook with 0 pages, export notebook with 50 pages and 5000 strokes, export to read-only directory path, export with concealed tapes, export with empty strokes.
- B29 (Voice Recording): Start recording while already recording, stop recording when not recording, record with invalid directory path, disk full simulation, zero-duration audio file.
- B30 (Audio Playback): Play non-existent file, play corrupted audio file, play while already playing, seek past audio duration, seek to negative position.
- B31 (Local Storage): Read non-existent JSON file, parse truncated/corrupted JSON file, save note with null fields, concurrent read/write to same notebook file, JSON with 10,000 strokes.
- B32 (Summarizer): Empty note text input, single-word note input, 50,000-word dissertation text input, text containing only stop words, text containing unicode symbols & math formulas.
- B33 (Formula Extractor): Text with nested brackets and formulas, malformed LaTeX equation without closing `$`, text with no definitions, text with 100 equations, regex catastrophic backtracking immunity.
- B34 (Flashcard Gen): Generate from notebook with 0 text annotations, generate from 100-page notebook, duplicate terms handling, special characters in term/definition, card generation speed benchmark.
- B35 (Problem Solver): Empty query string, query with 2000 characters, code injection attempt (`<script>`, SQL injection strings), non-English query input, rapid query iteration.
- B36 (Shelf & Folder): Shelf search with special regex meta-characters (`.*+?[]^$`), search with no matches, 1000 notebooks performance benchmark, folder rename cascading, empty folder filter.
- B37 (Notebook Creation): Blank title default assignment, title with 500 characters, invalid cover color integer, unknown paper type string fallback, creation timestamp monotonicity.
- B38 (Page Navigation): Delete the only remaining page (prevents 0 pages), navigate previous on page 1, navigate next on last page, insert page at index 0, insert 100 pages rapidly.

Total Tier 2 Tests: **190 tests** ($38 \times 5$).

---

### Tier 3: Pairwise Cross-Feature Combinations ($\ge 38$ tests)
38 integration tests verifying seamless interaction across distinct subsystem boundaries:
1. `C01`: Active Recall Tape placed directly on top of Cornell Notes Cue column.
2. `C02`: Dynamic Fountain Pen stroke intersecting with Stroke Eraser.
3. `C03`: Semi-transparent Highlighter layered over Ballpoint Pen stroke.
4. `C04`: Undo action reverting a Geometric Shape Recognizer snapping operation.
5. `C05`: Redo action restoring a batch of Active Recall Tapes deleted via Stroke Eraser.
6. `C06`: Voice Memo audio recording synced to a page with 5mm Grid paper template.
7. `C07`: Local AI Extractive Summarizer processing notes extracted across multi-page notebook.
8. `C08`: Automated Flashcard generation creating SM-2 study deck from Active Recall Tape regions.
9. `C09`: Native PDF Import page used as background for Fountain Pen and Highlighter annotations.
10. `C10`: Multi-page PDF Document Export containing procedurally rendered Cornell paper and tapes.
11. `C11`: Local JSON Storage serializing notebook containing strokes, tapes, and audio memo URI.
12. `C12`: Tap-to-Reveal toggling tape on Dark Mode Paper template with contrast validation.
13. `C13`: Quick Action Reveal All executed immediately before Multi-Page PDF Document Export.
14. `C14`: Quick Action Conceal All executed immediately before Flashcard Study Mode launch.
15. `C15`: Pixel Eraser clearing segments of a circle created by Shape Recognizer.
16. `C16`: 3D Flashcard Study Session updating SM-2 repetitions and interval in Local Storage.
17. `C17`: Notebook Shelf search finding notebook by title then navigating to page 3.
18. `C18`: Textured Pencil sketch on Sepia Vintage paper template.
19. `C19`: Formula & Definition Extractor mining terms from text annotations into Flashcards.
20. `C20`: Local Problem Solver breaking down math formula extracted from canvas text.
21. `C21`: Audio playback progress indicator updating while drawing Ballpoint strokes.
22. `C22`: Undo stack unwinding stroke drawing that crossed an Active Recall Tape.
23. `C23`: New page insertion inheriting default paper template from Notebook cover settings.
24. `C24`: PDF export containing mixed page templates (Cornell, Grid, Dotted, PDF background).
25. `C25`: Spaced Repetition scheduler calculating due cards for newly auto-generated cards.
26. `C26`: Tap-to-Reveal hit-testing with multiple overlapping tapes of different pastel colors.
27. `C28`: Highlighter stroke alpha blending with imported PDF document text.
28. `C29`: Active Recall Tape creation on Weekly Study Planner template.
29. `C30`: Full notebook deletion cleaning up associated PDF and audio files.
30. `C31`: Shape recognizer snapping triangle and rectangle on 5mm Grid paper.
31. `C32`: AI Summarizer processing both text annotations and sticky notes on College Ruled paper.
32. `C33`: Ballpoint stroke with audio offset timestamp recorded during active voice memo.
33. `C34`: Conceal All followed by individual tap reveal on Sepia paper.
34. `C35`: Redo stack cleared when new Active Recall Tape is created after Undo.
35. `C36`: Notebook title rename updating JSON persistence and library shelf index.
36. `C37`: Dot Grid bullet journal page with fountain pen calligraphy and active tapes.
37. `C38`: Multi-page PDF import populating page count and generating multi-page notebook.

Total Tier 3 Tests: **38 tests**.

---

### Tier 4: Real-World Application Scenarios ($\ge 19$ scenarios)
19 multi-step realistic student and professional workflows validating end-to-end user journeys:
1. `S01`: **Medical Anatomy Exam Revision**: Create Cornell notebook, write medical terms, draw anatomical sketches, place 6 active recall tapes over Latin names, test recall, run Reveal All.
2. `S02`: **Calculus Lecture & Homework Breakdown**: Import calculus syllabus PDF, take fountain pen lecture notes, extract derivatives, use Local Problem Solver to solve integral step-by-step.
3. `S03`: **Weekly Study Planning & Habit Tracker**: Create planner notebook, populate 6-box study schedule, sketch weekly habit tracker with pencil, record voice memo goals.
4. `S04`: **Language Learning Vocabulary Deck**: Create Ruled notebook with foreign vocabulary, place concealing tapes over translations, auto-generate flashcards, complete SM-2 rating session.
5. `S05`: **Law School Case Briefing**: Cornell paper template, record legal definitions in Cue column, run Extractive Summarizer to produce executive brief, export to multi-page PDF.
6. `S06`: **Physics Formula Sheet Preparation**: 5mm Grid paper, draw geometric diagrams snapped to clean circles/rectangles, highlight key formulas, conceal constants with tape.
7. `S07`: **Offline Airplane Mode Full Study Session**: Complete workflow with zero network connectivity: create notebook, draw 20 strokes, record audio, generate cards, review deck, save to disk.
8. `S08`: **Multi-Page Textbook Annotation & Export**: Import 5-page PDF document, annotate pages 1-3 with highlighter and ballpoint pen, add active recall tapes, export final annotated PDF.
9. `S09`: **Voice Lecture Recording with Synced Stroke Timestamps**: Start audio recording, write lecture notes across 2 minutes, verify per-stroke audio timestamp progression, playback memo inline.
10. `S10`: **High-Intensity Spaced Repetition Mastery**: Review 10 flashcards, cycle through ratings (Again, Hard, Good, Easy), verify interval expansion and due date scheduling over 4 simulated days.
11. `S11`: **Architectural Dot Grid Sketching**: Dot grid bullet journal template, draw layout plans with pencil, snap straight walls with shape recognizer, undo erroneous strokes.
12. `S12`: **Crash Recovery & Persistence Verification**: Populate notebook with strokes, tapes, and annotations, simulate sudden app restart/process kill, re-load notebook and verify bit-for-bit integrity.
13. `S13`: **Comprehensive Library Shelf Management**: Create 10 notebooks across 3 folders ("Biology", "Math", "History"), search for specific title, delete 2 notebooks, verify folder counts.
14. `S14`: **Dark Mode Night Study Session**: Switch to Dark Slate Grid paper, draw light-colored ink strokes, place soft pastel tapes, verify contrast ratios and eye-comfort rendering.
15. `S15`: **Active Recall Exam Self-Test Simulation**: Create 10 tapes over formulas, trigger Conceal All, tap through 7 tapes correctly and 3 incorrectly, reveal all, verify study state.
16. `S16`: **On-Device Research Synthesis**: Paste 3 research paragraphs into note annotations, run Local AI Summarizer, extract keywords, generate instant flashcards.
17. `S18`: **Engineering Problem Set with Shape Snapping**: 5mm Grid paper, sketch rough triangles, rectangles, and coordinate axes, snap to clean vectors, calculate dimensions.
18. `S19`: **Full Lifecycle Notebook Archive**: Create notebook, edit across 3 pages, record voice memo, run AI summary, export PDF, delete from shelf, verify all storage artifacts cleaned.

Total Tier 4 Tests: **19 scenarios**.

---

## 5. Aggregate Test Threshold Summary

$$\begin{array}{|l|r|r|}
\hline
\textbf{Test Tier} & \textbf{Criteria} & \textbf{Total Test Cases} \\
\hline
\text{Tier 1: Feature Coverage} & 38 \text{ features} \times \ge 5 \text{ tests} & \mathbf{190} \\
\text{Tier 2: Boundary \& Corner Cases} & 38 \text{ features} \times \ge 5 \text{ tests} & \mathbf{190} \\
\text{Tier 3: Pairwise Combinations} & \ge 38 \text{ cross-subsystem pairs} & \mathbf{38} \\
\text{Tier 4: Real-World Scenarios} & \ge 19 \text{ end-to-end workflows} & \mathbf{19} \\
\hline
\textbf{Total E2E Test Suite} & \textbf{All Tiers Combined} & \mathbf{437} \\
\hline
\end{array}$$

---

## 6. Execution Command & Harness Specification

The test suite is organized under `/storage/emulated/0/FlowNotes/tests/`:

```
tests/
├── run_all_tests.py                 # Master orchestrator test runner
├── tier1_feature_coverage/          # 38 features x 5 = 190 tests
│   ├── test_f01_f05_canvas_pens.py
│   ├── test_f06_f10_tools_history.py
│   ├── test_f11_f16_active_tape.py
│   ├── test_f17_f19_flashcards_srs.py
│   ├── test_f20_f25_paper_templates.py
│   ├── test_f26_f28_pdf_engine.py
│   ├── test_f29_f30_audio_memo.py
│   ├── test_f31_f35_storage_ai.py
│   └── test_f36_f38_shelf_pages.py
├── tier2_boundary_corner/           # 38 features x 5 = 190 tests
│   ├── test_b01_b05_canvas_pens_edge.py
│   ├── test_b06_b10_tools_history_edge.py
│   ├── test_b11_b16_tape_edge.py
│   ├── test_b17_b19_srs_edge.py
│   ├── test_b20_b25_paper_edge.py
│   ├── test_b26_b28_pdf_edge.py
│   ├── test_b29_b30_audio_edge.py
│   ├── test_b31_b35_storage_ai_edge.py
│   └── test_b36_b38_shelf_pages_edge.py
├── tier3_pairwise_combinations/     # 38 cross-subsystem combination tests
│   └── test_tier3_pairwise.py
└── tier4_real_world_scenarios/      # 19 comprehensive end-to-end scenarios
    └── test_tier4_scenarios.py
```

### Execution Commands
```bash
# Run entire test suite (Tiers 1-4, 437 tests):
python3 tests/run_all_tests.py

# Run specific tier:
python3 tests/run_all_tests.py --tier 1
python3 tests/run_all_tests.py --tier 2
python3 tests/run_all_tests.py --tier 3
python3 tests/run_all_tests.py --tier 4

# Run with verbose output:
python3 tests/run_all_tests.py --verbose
```

Exit code: `0` if all tests pass; non-zero if any test fails.
