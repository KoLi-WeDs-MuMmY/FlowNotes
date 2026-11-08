# M1 Android Scaffolding & Layout Exploration Report

## 1. Observation

A full static code investigation across `/storage/emulated/0/FlowNotes/` was conducted to catalog all layout references, ID bindings, view classes, and manifest specifications.

### Observation 1.1: `MainActivity.kt` Layout & ID Requirements
Source: `/storage/emulated/0/FlowNotes/app/src/main/java/com/flownotes/app/MainActivity.kt`
- Line 38: `setContentView(R.layout.activity_main)`
- Line 51: `recyclerView = findViewById(R.id.rv_notebooks)` (configured with `GridLayoutManager(this, 2)`)
- Line 60: `findViewById<View>(R.id.btn_folder_all).setOnClickListener { setFolder("All Notes") }`
- Line 61: `findViewById<View>(R.id.btn_folder_study).setOnClickListener { setFolder("Study") }`
- Line 62: `findViewById<View>(R.id.btn_folder_work).setOnClickListener { setFolder("Work") }`
- Line 63: `findViewById<View>(R.id.btn_folder_personal).setOnClickListener { setFolder("Personal") }`
- Line 66: `findViewById<View>(R.id.fab_new_notebook).setOnClickListener { showNewNotebookDialog() }`
- Line 71: `findViewById<View>(R.id.btn_import_pdf).setOnClickListener { ... }`
- Line 80: `findViewById<SearchView>(R.id.search_view).setOnQueryTextListener(...)` (Note: resolves to `android.widget.SearchView` due to `import android.widget.*` on Line 12)
- Line 91: `findViewById<TextView>(R.id.tv_folder_title).text = folder`
- Line 117: `val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_notebook, null)`
- Line 118: `val etTitle = dialogView.findViewById<EditText>(R.id.et_notebook_title)`
- Line 119: `val spTemplate = dialogView.findViewById<Spinner>(R.id.sp_paper_template)`
- Line 261: `val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notebook_card, parent, false)`
- Line 273: `private val tvTitle: TextView = itemView.findViewById(R.id.tv_card_title)`
- Line 274: `private val tvPages: TextView = itemView.findViewById(R.id.tv_card_pages)`
- Line 275: `private val coverView: View = itemView.findViewById(R.id.view_card_cover)`
- Line 276: `private val tvBadge: TextView = itemView.findViewById(R.id.tv_cover_badge)`

### Observation 1.2: `NoteEditorActivity.kt` Layout & ID Requirements
Source: `/storage/emulated/0/FlowNotes/app/src/main/java/com/flownotes/app/NoteEditorActivity.kt`
- Line 33: `setContentView(R.layout.activity_note_editor)`
- Line 52: `canvasView = findViewById(R.id.canvas_view)` (`com.flownotes.app.view.CanvasView`)
- Line 53: `drawerLayout = findViewById(R.id.drawer_layout)` (`androidx.drawerlayout.widget.DrawerLayout`)
- Line 56: `findViewById<View>(R.id.btn_back).setOnClickListener { saveAndExit() }`
- Line 60: `val tvTitle = findViewById<TextView>(R.id.tv_editor_title)`
- Line 66: `findViewById<View>(R.id.btn_prev_page).setOnClickListener`
- Line 70: `findViewById<View>(R.id.btn_next_page).setOnClickListener`
- Line 78: `findViewById<View>(R.id.btn_add_page).setOnClickListener`
- Line 82: `findViewById<View>(R.id.btn_undo).setOnClickListener`
- Line 86: `findViewById<View>(R.id.btn_redo).setOnClickListener`
- Line 90: `findViewById<View>(R.id.btn_change_template).setOnClickListener`
- Line 94: `findViewById<View>(R.id.btn_export_pdf).setOnClickListener`
- Line 98: `findViewById<View>(R.id.btn_open_ai).setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }`
- Line 102: `findViewById<View>(R.id.btn_flashcards).setOnClickListener { openFlashcardStudy() }`
- Line 107-112:
  - `findViewById<View>(R.id.tool_ballpoint)`
  - `findViewById<View>(R.id.tool_fountain)`
  - `findViewById<View>(R.id.tool_highlighter)`
  - `findViewById<View>(R.id.tool_eraser)`
  - `findViewById<View>(R.id.tool_tape)`
  - `findViewById<View>(R.id.tool_shape)`
- Lines 115, 119:
  - `findViewById<View>(R.id.btn_reveal_tapes)`
  - `findViewById<View>(R.id.btn_conceal_tapes)`
- Lines 125-130 (Color swatches):
  - `findViewById<View>(R.id.color_black)`
  - `findViewById<View>(R.id.color_blue)`
  - `findViewById<View>(R.id.color_purple)`
  - `findViewById<View>(R.id.color_green)`
  - `findViewById<View>(R.id.color_orange)`
  - `findViewById<View>(R.id.color_red)`
- Lines 133-135 (Stroke sizes):
  - `findViewById<View>(R.id.size_fine)`
  - `findViewById<View>(R.id.size_medium)`
  - `findViewById<View>(R.id.size_bold)`
- Line 143: `val dock = findViewById<LinearLayout>(R.id.floating_pen_dock)` (Explicit cast to `LinearLayout` where `dock.childCount` and `dock.getChildAt(i)` are manipulated)
- Line 157: `findViewById<TextView>(R.id.tv_page_indicator).text = "Page ${index + 1} of ${notebook.pages.size}"`
- Lines 233-277 (AI Study Drawer, `GravityCompat.END`):
  - Line 233: `val tvAiOutput = findViewById<TextView>(R.id.tv_ai_output)`
  - Line 234: `val etAiInput = findViewById<EditText>(R.id.et_ai_question)`
  - Line 236: `findViewById<View>(R.id.btn_ai_summarize)`
  - Line 242: `findViewById<View>(R.id.btn_ai_problem_solver)`
  - Line 249: `findViewById<View>(R.id.btn_ai_gen_cards)`
  - Line 254: `findViewById<View>(R.id.btn_ai_ask)`
  - Line 262: `findViewById<View>(R.id.btn_insert_ai_note)`

### Observation 1.3: `FlashcardStudyActivity.kt` Layout & ID Requirements
Source: `/storage/emulated/0/FlowNotes/app/src/main/java/com/flownotes/app/FlashcardStudyActivity.kt`
- Line 32: `setContentView(R.layout.activity_flashcards)`
- Line 51: `findViewById<TextView>(R.id.tv_deck_title).text = "$title Flashcards"`
- Line 52: `findViewById<View>(R.id.btn_close_study).setOnClickListener { finish() }`
- Line 54: `tvFront = findViewById(R.id.tv_card_front)`
- Line 55: `tvBack = findViewById(R.id.tv_card_back)`
- Line 56: `tvHint = findViewById(R.id.tv_card_hint)`
- Line 57: `tvProgress = findViewById(R.id.tv_study_progress)`
- Line 59: `cardFrontLayout = findViewById(R.id.card_front_layout)`
- Line 60: `cardBackLayout = findViewById(R.id.card_back_layout)`
- Line 62: `val cardContainer = findViewById<FrameLayout>(R.id.card_container)`
- Lines 67-70 (Spaced Repetition Rating buttons):
  - Line 67: `findViewById<Button>(R.id.btn_rate_again).setOnClickListener { nextCard(score = 1) }`
  - Line 68: `findViewById<Button>(R.id.btn_rate_hard).setOnClickListener { nextCard(score = 2) }`
  - Line 69: `findViewById<Button>(R.id.btn_rate_good).setOnClickListener { nextCard(score = 3) }`
  - Line 70: `findViewById<Button>(R.id.btn_rate_easy).setOnClickListener { nextCard(score = 4) }`

### Observation 1.4: Existing Resources & Scaffolding State
- `app/src/main/res/layout/` does NOT exist yet.
- `app/src/main/res/drawable/bg_floating_dock.xml` exists.
- `app/src/main/res/values/colors.xml`, `strings.xml`, `themes.xml` exist.
- `app/src/main/AndroidManifest.xml` does NOT exist yet.
- Gradle build files (`build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`) do NOT exist yet.
- `AudioMemoManager.kt:37` references `MediaRecorder.AudioSource.MIC` requiring `android.permission.RECORD_AUDIO`.

---

## 2. Logic Chain

1. **Layout ID Matching**:
   - Because `MainActivity.kt`, `NoteEditorActivity.kt`, and `FlashcardStudyActivity.kt` use direct `findViewById<T>(R.id.<id>)` calls, any missing ID or incompatible class cast will trigger a runtime `NullPointerException` or `ClassCastException`.
   - Specifically, `NoteEditorActivity.kt:143` casts `findViewById<LinearLayout>(R.id.floating_pen_dock)`:
     ```kotlin
     val dock = findViewById<LinearLayout>(R.id.floating_pen_dock)
     for (i in 0 until dock.childCount) {
         val child = dock.getChildAt(i)
         child.alpha = if (child == view) 1.0f else 0.65f
     }
     ```
     Therefore, `floating_pen_dock` must be defined as an Android `<LinearLayout>` whose direct children are the tool views (`tool_ballpoint`, `tool_fountain`, `tool_highlighter`, `tool_eraser`, `tool_tape`, `tool_shape`).

2. **Search Widget Resolution**:
   - In `MainActivity.kt:80`, `findViewById<SearchView>(R.id.search_view)` is executed. Since `MainActivity.kt` imports `android.widget.*`, the type is `android.widget.SearchView`. If an AppCompat SearchView were used in the XML without a namespace alias or custom import, Android inflation would cause a class cast mismatch. Therefore, `<SearchView ... />` (`android.widget.SearchView`) is used in `activity_main.xml`.

3. **Item Layout Compatibility**:
   - `MainActivity.kt:261` inflates `R.layout.item_notebook_card`.
   - `PROJECT.md:257` also references `item_notebook.xml`.
   - To guarantee complete compatibility across both `MainActivity.kt` and any project-level tooling or tests expecting `item_notebook.xml`, the Worker must provide both `item_notebook_card.xml` and `item_notebook.xml` with matching view IDs (`view_card_cover`, `tv_cover_badge`, `tv_card_title`, `tv_card_pages`).

4. **Drawer Layout Navigation**:
   - `NoteEditorActivity.kt:98` invokes `drawerLayout.openDrawer(GravityCompat.END)`.
   - Consequently, the AI Study Assistant panel within `activity_note_editor.xml` must declare `android:layout_gravity="end"`.

5. **Manifest & Permission Guardrails**:
   - `AudioMemoManager.kt` records voice memos using Android `MediaRecorder` (`MIC`), necessitating `<uses-permission android:name="android.permission.RECORD_AUDIO" />`.
   - `MainActivity.kt:187` and `NoteEditorActivity.kt:215` export notebook PDFs to external storage (`getExternalFilesDir`), necessitating standard storage permissions.
   - `MainActivity` requires `<intent-filter>` with `MAIN` and `LAUNCHER`.

---

## 3. Caveats

- **Shell command restrictions**: Direct interactive execution via terminal `run_command` encounters `libloader.so` environment restrictions in this subagent sandbox. All verification was conducted through rigorous static inspection, file viewing, and regex search.
- **Worker File Creation**: As an Explorer agent operating in read-only mode, files were authored into `.agents/teamwork/m1_explorer_1/`. The Worker will copy or create these exact target files under `app/src/main/res/layout/`, `app/src/main/res/drawable/`, `app/src/main/AndroidManifest.xml`, and Gradle build files.

---

## 4. Conclusion

All 5 core XML layouts, supporting drawables, Android manifest, and Gradle build configurations have been completely designed and validated against 100% of the Kotlin code references.

### Exact Proposed Artifacts Produced (Available in `m1_explorer_1/`):

1. **`proposed_activity_main.xml`**
   - Target: `app/src/main/res/layout/activity_main.xml`
   - IDs: `rv_notebooks`, `btn_folder_all`, `btn_folder_study`, `btn_folder_work`, `btn_folder_personal`, `tv_folder_title`, `fab_new_notebook`, `btn_import_pdf`, `search_view`.

2. **`proposed_item_notebook_card.xml` & `proposed_item_notebook.xml`**
   - Target: `app/src/main/res/layout/item_notebook_card.xml` and `app/src/main/res/layout/item_notebook.xml`
   - IDs: `view_card_cover`, `tv_cover_badge`, `tv_card_title`, `tv_card_pages`.

3. **`proposed_dialog_new_notebook.xml`**
   - Target: `app/src/main/res/layout/dialog_new_notebook.xml`
   - IDs: `et_notebook_title`, `sp_paper_template`.

4. **`proposed_activity_note_editor.xml`**
   - Target: `app/src/main/res/layout/activity_note_editor.xml`
   - IDs: `drawer_layout`, `canvas_view`, `btn_back`, `tv_editor_title`, `btn_prev_page`, `tv_page_indicator`, `btn_next_page`, `btn_add_page`, `btn_undo`, `btn_redo`, `btn_change_template`, `btn_export_pdf`, `btn_flashcards`, `btn_open_ai`, `btn_reveal_tapes`, `btn_conceal_tapes`, `floating_pen_dock`, `tool_ballpoint`, `tool_fountain`, `tool_highlighter`, `tool_eraser`, `tool_tape`, `tool_shape`, `color_black`, `color_blue`, `color_purple`, `color_green`, `color_orange`, `color_red`, `size_fine`, `size_medium`, `size_bold`, `et_ai_question`, `btn_ai_ask`, `btn_ai_summarize`, `btn_ai_problem_solver`, `btn_ai_gen_cards`, `tv_ai_output`, `btn_insert_ai_note`.

5. **`proposed_activity_flashcards.xml`**
   - Target: `app/src/main/res/layout/activity_flashcards.xml`
   - IDs: `flashcard_header`, `btn_close_study`, `tv_deck_title`, `tv_study_progress`, `card_container`, `card_front_layout`, `tv_card_front`, `tv_card_hint`, `card_back_layout`, `tv_card_back`, `rating_panel`, `btn_rate_again`, `btn_rate_hard`, `btn_rate_good`, `btn_rate_easy`.

6. **`proposed_AndroidManifest.xml`**
   - Target: `app/src/main/AndroidManifest.xml`
   - Permissions: `RECORD_AUDIO`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`.
   - Activities: `MainActivity` (Launcher), `NoteEditorActivity`, `FlashcardStudyActivity`.

7. **`proposed_drawables.md`**
   - Targets: `app/src/main/res/drawable/bg_folder_chip.xml`, `bg_tool_button.xml`, `bg_card_cover.xml`.

8. **Gradle Build Files**:
   - `proposed_settings.gradle.kts` -> `/storage/emulated/0/FlowNotes/settings.gradle.kts`
   - `proposed_root_build.gradle.kts` -> `/storage/emulated/0/FlowNotes/build.gradle.kts`
   - `proposed_app_build.gradle.kts` -> `/storage/emulated/0/FlowNotes/app/build.gradle.kts`

---

## 5. Verification Method

To independently verify this layout and scaffolding blueprint:

1. **Layout ID Completeness Audit**:
   Verify every ID matched in Observation 1.1 - 1.3 exists in the proposed layout files:
   ```bash
   grep -o 'android:id="@+id/[^"]*"' /storage/emulated/0/FlowNotes/.agents/teamwork/m1_explorer_1/proposed_*.xml
   ```

2. **Cross-Check with Kotlin Source**:
   Verify that all `R.id.*` referenced in `MainActivity.kt`, `NoteEditorActivity.kt`, and `FlashcardStudyActivity.kt` match the declared XML elements.

3. **Class Cast Check**:
   - Confirm `R.id.floating_pen_dock` in `proposed_activity_note_editor.xml` is `<LinearLayout>`.
   - Confirm `R.id.canvas_view` is `<com.flownotes.app.view.CanvasView>`.
   - Confirm `R.id.search_view` in `proposed_activity_main.xml` is `<SearchView>`.
   - Confirm `R.id.card_container` in `proposed_activity_flashcards.xml` is `<FrameLayout>`.
   - Confirm `R.id.rv_notebooks` in `proposed_activity_main.xml` is `<androidx.recyclerview.widget.RecyclerView>`.

4. **Invalidation Conditions**:
   - If any Activity in `app/src/main/java/com/flownotes/app/` is refactored to use ViewBinding without matching ID names.
   - If `floating_pen_dock` is changed to ConstraintLayout or FrameLayout, causing `NoteEditorActivity.kt:143` to throw `ClassCastException`.
