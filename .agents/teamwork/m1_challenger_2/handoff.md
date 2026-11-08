# Milestone 1 (M1) Empirical Challenge Report: Layout Integrity & API Security

**Agent**: `m1_challenger_2` (Empirical Challenger, Critic, Specialist)  
**Milestone**: M1 (Core Foundations, Android Layouts & Local Storage)  
**Target Recipient**: Orchestrator (`c26bb363-4e34-49ee-b305-ae12e9dfc336`)  
**Timestamp**: 2026-11-08T10:49:00Z  
**Verdict**: **REJECT** (Blocking on Critical Path Traversal Vulnerabilities in API & Persistence Layer)

---

## Challenge Summary

**Overall Risk Assessment**: **CRITICAL**

While Android XML layout bindings, drawables, manifests, and AAPT2 resource compilation are in exemplary condition (81/81 view bindings verified 100% type-safe; AAPT2 compilation exited 0), adversarial fuzzing revealed **three severe Path Traversal vulnerabilities in `server.py`** allowing unauthenticated arbitrary file read, arbitrary file write, and arbitrary file deletion across the host storage. A corresponding risk exists in `NoteRepository.kt`.

---

## 1. Observation

### 1.1 Android XML Layouts & Kotlin View Bindings
1. **Verification Harness**: Developed and executed `tests/verify_layout_bindings.py`, which parsed all XML layout files (`activity_main.xml`, `activity_note_editor.xml`, `activity_flashcards.xml`, `dialog_new_notebook.xml`, `item_notebook_card.xml`, `item_notebook.xml`) and checked all `findViewById` references in Kotlin against declared tags and class hierarchies:
   - Evaluated 81 distinct view bindings across `MainActivity.kt`, `NoteEditorActivity.kt`, and `FlashcardStudyActivity.kt`.
   - Result: **0 missing IDs**, **0 type mismatches**, **0 ClassCastException risks**, **0 NullPointerException risks**.
   - Verified that `com.flownotes.app.view.CanvasView` defines `@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr)` in `app/src/main/java/com/flownotes/app/view/CanvasView.kt:18-22`, ensuring inflation from XML will not throw `NoSuchMethodException`.
   - Verified all drawables (`bg_card_cover.xml`, `bg_floating_dock.xml`, `bg_folder_chip.xml`, `bg_tool_button.xml`), colors (`colors.xml`), and strings (`strings.xml`) exist and resolve properly.

### 1.2 AAPT2 Resource Compilation
1. **Command**:
   ```bash
   aapt2 compile --dir app/src/main/res -o build/res.zip
   ```
2. **Result**: Exited with code 0. Generated `build/res.zip` cleanly with zero errors or warnings.

### 1.3 Adversarial HTTP Fuzzing against `server.py`
Developed and executed `tests/fuzz_server_api.py` against live `server.py` on `http://127.0.0.1:8080/`, executing 43 adversarial attack vectors.

#### 1.3.1 Confirmed Path Traversal Vulnerabilities
1. **Arbitrary File Read via GET**:
   - In `server.py:68` & `server.py:155-166`:
     ```python
     if path.startswith("/api/notes/"):
         note_id = path[len("/api/notes/"):]
         self.handle_get_note(note_id)
     ...
     def handle_get_note(self, note_id: str):
         file_path = os.path.join(NOTES_DIR, f"{note_id}.json")
         if not os.path.exists(file_path):
             self.send_json_response({"error": "Note not found"}, status=404)
             return
         with open(file_path, "r", encoding="utf-8") as f:
             data = json.load(f)
         self.send_json_response(data)
     ```
   - **Empirical Test**: Created `/storage/emulated/0/FlowNotes/read_leak_canary.json` with `{"secret": "compromised_data_leak_123"}`. Sent `GET /api/notes/../../read_leak_canary HTTP/1.1`.
   - **Result**: Server responded HTTP 200 OK and dumped verbatim file contents:
     ```
     127.0.0.1 - - [08/Nov/2026 16:16:38] "GET /api/notes/../../read_leak_canary HTTP/1.1" 200 -
     LEAKED DATA VIA GET: {'secret': 'compromised_data_leak_123'}
     ```

2. **Arbitrary File Deletion via DELETE**:
   - In `server.py:117-124`:
     ```python
     if path.startswith("/api/notes/"):
         note_id = path[len("/api/notes/"):]
         file_path = os.path.join(NOTES_DIR, f"{note_id}.json")
         if os.path.exists(file_path):
             os.remove(file_path)
     ```
   - **Empirical Test**: Placed canary file `/storage/emulated/0/FlowNotes/traversal_delete_canary.json`. Sent `DELETE /api/notes/../../traversal_delete_canary HTTP/1.1`.
   - **Result**: Server responded HTTP 200 OK, and `/storage/emulated/0/FlowNotes/traversal_delete_canary.json` was deleted from disk!

3. **Arbitrary File Write / Overwrite via POST**:
   - In `server.py:168-176`:
     ```python
     def handle_save_note(self, data: Dict[str, Any]):
         note_id = data.get("id") or str(uuid.uuid4())
         ...
         file_path = os.path.join(NOTES_DIR, f"{note_id}.json")
         with open(file_path, "w", encoding="utf-8") as f:
             json.dump(data, f, ensure_ascii=False, indent=2)
     ```
   - **Empirical Test A (Relative Traversal)**: Sent `POST /api/notes` with body `{"id": "../../traversal_write_canary", "title": "Evil"}`.
     - **Result**: File `/storage/emulated/0/FlowNotes/traversal_write_canary.json` was created in the parent directory!
   - **Empirical Test B (Absolute Path Escape)**: In Python, `os.path.join(a, b)` discards `a` if `b` begins with `/`. Sent `POST /api/notes` with body `{"id": "/storage/emulated/0/FlowNotes/abs_traversal_canary"}`.
     - **Result**: File `/storage/emulated/0/FlowNotes/abs_traversal_canary.json` was created directly at the absolute path outside `NOTES_DIR`!

#### 1.3.2 Resilience on Other Vectors
- **Malformed JSON Bodies**: Sent non-JSON strings, truncated JSON, empty POST bodies, JSON arrays (`[1, 2, 3]`), primitives (`42`, `true`, `null`), and null bytes. Server safely returned `HTTP 400 Bad Request` without uncaught exceptions or process crashes.
- **Oversized Payloads**: Sent 1 MB, 5 MB, and 10 MB JSON payloads with tens of thousands of coordinate points. Handled cleanly in 0.08s - 0.48s without memory exhaustion.
- **Protocol Violations**: Handled negative Content-Length, non-numeric Content-Length, and unsupported HTTP methods (`TRACE`, `PUT`, `PATCH`, `FOOBAR`) returning HTTP 501 or closing socket without freezing.
- **PDF Upload Endpoint**: Handled missing multipart boundaries, empty bodies, and 2MB random binary streams safely.
- **Liveness**: Post-fuzz health check (`GET /api/status`) responded `HTTP 200 OK` within 5ms.

---

## 2. Logic Chain

1. **Layout Correctness**:
   - Observation 1.1 confirms that all 81 view IDs referenced in `MainActivity.kt`, `NoteEditorActivity.kt`, and `FlashcardStudyActivity.kt` exist and match their declared XML types.
   - Observation 1.2 confirms AAPT2 compiles resources without error.
   - Therefore, the UI layout and view binding layer is robust and free of NPE/CCE defects.

2. **Security Vulnerability in API & Persistence**:
   - The user request and project specification require a reliable, secure local architecture.
   - Observation 1.3 proves that unauthenticated HTTP requests to `server.py` can:
     a) Read any JSON file on the system accessible to the user process.
     b) Delete any JSON file on the system.
     c) Create or overwrite files anywhere on the file system.
   - In `NoteRepository.kt:49`, a similar pattern exists (`File(notebooksDir, "$id.json")`), allowing directory traversal if a malicious note ID is supplied.
   - Because `server.py` binds to `127.0.0.1:8080`, any malicious web page running in the device's browser could perform Cross-Origin or local loopback requests to delete or overwrite arbitrary project files or user data.
   - Therefore, the API endpoint contract cannot be approved until path traversal protections are implemented.

---

## 3. Caveats

- **No Caveats on Verification Scope**: Layout binding analysis, AAPT2 resource compilation, and HTTP fuzzing were executed and empirically validated.
- **Stylus Hardware**: Pressure curves and stylus palm rejection were verified via programmatic motion events and code analysis; physical capacitive stylus behavior on hardware screen requires manual device testing in later milestones.

---

## 4. Conclusion

**Confirmation of Correctness**: **REJECT**

Milestone 1 satisfies layout integrity, view binding safety, and AAPT2 resource compilation. However, `server.py` (and `NoteRepository.kt`) contain **CRITICAL Path Traversal vulnerabilities** that fail the mandate to *"verify safe responses"* under adversarial path traversal attempts on `/api/notes/`.

### Required Remediations Before Approval:
1. **Sanitize `note_id` in `server.py`**:
   - Ensure `note_id` contains only alphanumeric characters, dashes, and underscores (e.g., regex `^[a-zA-Z0-9_-]+$`), OR:
   - Strip directory separators: `note_id = os.path.basename(note_id)`.
   - Verify containment:
     ```python
     safe_filename = os.path.basename(note_id) + ".json"
     file_path = os.path.abspath(os.path.join(NOTES_DIR, safe_filename))
     if not file_path.startswith(os.path.abspath(NOTES_DIR)):
         self.send_json_response({"error": "Invalid note ID"}, status=400)
         return
     ```
   - Apply this check to `handle_get_note`, `handle_save_note`, and `do_DELETE`.
2. **Sanitize `id` in `NoteRepository.kt`**:
   - Enforce `val safeName = File(id).name` or reject IDs containing `/`, `\\`, or `..`.

---

## 5. Verification Method

### 5.1 Run Layout & View Binding Verification Harness
```bash
python3 /storage/emulated/0/FlowNotes/tests/verify_layout_bindings.py
```
*Expected*: Exit code 0, `Total View Binding Checks: 81`, `Errors Found: 0`.

### 5.2 Verify AAPT2 Resource Compilation
```bash
aapt2 compile --dir /storage/emulated/0/FlowNotes/app/src/main/res -o /storage/emulated/0/FlowNotes/build/res.zip
```
*Expected*: Exit code 0, zero compilation errors.

### 5.3 Run Adversarial HTTP Fuzzing Suite
```bash
python3 /storage/emulated/0/FlowNotes/tests/fuzz_server_api.py
```
*Expected after fix*: Zero path traversal reads, writes, or deletions; 100% of traversal vectors return HTTP 400 or HTTP 404 safely.
