# FlowNotes Milestone 1 (M1) Build System & Compilation Pipeline — Handoff Report

## 1. Observation

### 1.1 Project Structure & Existing Codebase
- **Project Root**: `/storage/emulated/0/FlowNotes`
- **Existing Files in Repository**:
  - `start.sh` and `server.py`: Local companion server hosting HTTP & REST API on `127.0.0.1:8080`.
  - `app/src/main/res/values/`:
    - `strings.xml`: Defines `app_name` ("FlowNotes"), `create_notebook`, `import_pdf`, `all_notes`, `study`, `work`, `personal`, `flashcards`, `ai_study_assistant`.
    - `themes.xml`: Defines `Theme.FlowNotes` inheriting from `Theme.MaterialComponents.DayNight.NoActionBar`.
    - `colors.xml`: Defines palette (`primary` `#3B82F6`, `accent` `#8B5CF6`, `tape_yellow` `#FEF08A`, etc.).
    - `bg_floating_dock.xml`: Floating dock background drawable.
  - `app/src/main/java/com/flownotes/app/`:
    - `model/Models.kt`: 121 lines. Defines `PenTool`, `PaperType`, `Point`, `Stroke`, `ActiveRecallTape`, `TextAnnotation`, `StickyNote`, `Page`, `Notebook`, `Flashcard`. Contains **zero** Android platform imports (only `java.io.Serializable` and `java.util.UUID`).
    - `data/NoteRepository.kt`: 299 lines. Uses `android.content.Context` exclusively at lines 17-21 (`context.filesDir`) to locate `notebooks/`, `pdf/`, and `audio/` subdirectories. Serialization uses `org.json.JSONObject` and `org.json.JSONArray`.
    - 10 other Kotlin files: `MainActivity.kt`, `NoteEditorActivity.kt`, `FlashcardStudyActivity.kt`, `CanvasView.kt`, `StrokeEngine.kt`, `ShapeRecognizer.kt`, `PaperTemplateRenderer.kt`, `PdfAnnotationManager.kt`, `AudioMemoManager.kt`, `LocalAiStudyEngine.kt`.
- **Missing Build & Config Files**:
  - Root `settings.gradle.kts` does not exist.
  - Root `build.gradle.kts` does not exist.
  - `app/build.gradle.kts` does not exist.
  - `gradle.properties` and `gradle-wrapper.properties` do not exist.
  - `app/src/main/AndroidManifest.xml` does not exist.
  - `app/src/main/res/layout/` directory (`activity_main.xml`, `activity_note_editor.xml`, `activity_flashcards.xml`, `dialog_new_notebook.xml`, `item_notebook.xml`) does not exist.
  - `app/src/test/` directory does not exist.

### 1.2 Android Environment & Installed Runtimes
Direct observations from command execution:
1. **Host Architecture & Kernel**: Native Android Linux kernel 6.1+, ARM64 (`aarch64`), sandbox root at `/data/user/0/com.agy/files`.
2. **Execution Rules**: Shell commands execute in native userspace; desktop sandbox `bwrap` is unsupported; commands require `BypassSandbox: true`. Shared storage `/storage/emulated/0` is mounted `noexec`.
3. **Available CLI Packages (`pkg`)**:
   - `aapt2`: Version `16.0.0.4-4` was installed and verified in `$PREFIX/bin/aapt2` (`/data/user/0/com.agy/files/usr/bin/aapt2`).
   - `kotlin`: Version `2.4.21` available in `pkg search kotlin`.
   - `openjdk-17`: Version `17.0.21-1` available in `pkg search openjdk`.
   - `openjdk-21`: Version `21.0.13-1` available in `pkg search openjdk`.
   - `d8`: Version `37.0.0` available in `pkg search d8`.
   - `gradle`: Version `1:9.9.0` available in `pkg search gradle`.
   - `python`: Version `3.12+` installed at `/data/user/0/com.agy/files/usr/bin/python`.
4. **Android SDK & Framework Assets on Device**:
   - `/storage/emulated/0/Android/media/com.roxum/Projects/CodeAssist/ide-android/src/main/assets/android.jar`: 27 MB compile-time SDK jar containing valid `.class` files across the entire `android.*` API namespace.
   - `/system/framework/framework.jar`: 49,048,592 bytes system runtime jar containing `classes.dex` through `classes6.dex` directly accessible to ART (`dalvikvm` / `app_process`).

---

## 2. Logic Chain

### 2.1 Gradle Build System Configuration
- **Root `settings.gradle.kts`**:
  FlowNotes is structured with a root directory and a single application module `:app` (`PROJECT.md` line 228). The repositories must include `google()`, `mavenCentral()`, and `gradlePluginPortal()`.
- **Root `build.gradle.kts`**:
  Modern declarative Gradle build requires AGP `com.android.application` (v8.5.2) and Kotlin Android plugin `org.jetbrains.kotlin.android` (v1.9.24 or 2.0.0) applied with `apply false`.
- **`app/build.gradle.kts`**:
  - `namespace = "com.flownotes.app"`
  - `compileSdk = 34` (Android 14): Provides full compatibility with modern canvas APIs, `android.graphics.pdf.PdfRenderer`, `android.graphics.pdf.PdfDocument`, and runtime permissions for audio recording.
  - `minSdk = 26` (Android 8.0 Oreo): Essential baseline for native adaptive icons, Java 8/11/17 desugaring, `java.time`, and hardware-accelerated Bezier curve canvas operations without legacy shims.
  - `targetSdk = 34`
  - `versionCode = 1`, `versionName = "1.0.0"`
  - `buildFeatures { viewBinding = true }`: Ensures type-safe XML layout bindings.
  - Dependencies:
    - `com.google.android.material:material:1.12.0`: Required by `themes.xml` (`Theme.MaterialComponents.DayNight.NoActionBar`).
    - `androidx.appcompat:appcompat:1.7.0`: Core support for AppCompatActivity, vector drawables, and day/night theming.
    - `androidx.core:core-ktx:1.13.1`: Kotlin extensions for core platform features.
    - `androidx.constraintlayout:constraintlayout:2.1.4`: Responsive layout for complex notebook editor toolbars and study cards.
    - `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0` and `lifecycle-runtime-ktx:2.8.0`: Architectural component lifecycle management.
    - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`: Threading for asynchronous note I/O, PDF exporting, and local AI processing.
    - `junit:junit:4.13.2` and `org.jetbrains.kotlin:kotlin-test:1.9.24`: Pure Kotlin unit testing framework.

### 2.2 On-Device Compilation & Execution Pipeline
On this Android ARM64 device, we have two viable compilation and execution pipelines:

#### Pipeline A: Standard Gradle (CI / Android Studio / Termux Gradle)
- Worker writes `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, and `gradle.properties`.
- Can be invoked via standard `gradle test` or `./gradlew test` when OpenJDK is installed.

#### Pipeline B: Lightweight On-Device Direct Toolchain (`kotlinc` + `d8` + `aapt2` + `dalvikvm`)
- **Step 1 (Resource Packaging)**:
  `aapt2 compile --dir app/src/main/res -o build/res.zip`
  `aapt2 link -I <android.jar> --manifest app/src/main/AndroidManifest.xml -o build/app-res.apk --java app/src/main/java build/res.zip`
  Generates `R.java` containing resource IDs.
- **Step 2 (Kotlin Compilation)**:
  `kotlinc -cp "/storage/emulated/0/Android/media/com.roxum/Projects/CodeAssist/ide-android/src/main/assets/android.jar" -d build/classes app/src/main/java/... app/src/test/java/...`
- **Step 3 (D8 Dexing & ART Execution)**:
  `d8 --output build/dex/ --lib /system/framework/framework.jar build/classes/**/*.class`
  `dalvikvm -cp /system/framework/framework.jar:build/dex/classes.dex <TestRunnerClass>`

### 2.3 Unit Testing Strategy for Pure Kotlin Classes
- **Observation on `Models.kt`**:
  `Models.kt` does not import any `android.*` packages. It is 100% pure Kotlin, making it instantly testable under any JVM or test harness without Android framework dependencies.
- **Observation on `NoteRepository.kt`**:
  `NoteRepository` accepts `context: Context`, but only uses `context.filesDir` to create three subdirectories: `notebooks/`, `pdf/`, and `audio/`.
- **Decoupling Strategy**:
  We recommend adding an overloaded constructor to `NoteRepository`:
  ```kotlin
  class NoteRepository(val filesDir: File) {
      constructor(context: Context) : this(context.filesDir)
      ...
  ```
  And making `serializeNotebook(nb: Notebook): JSONObject` and `parseNotebookJson(json: JSONObject): Notebook` accessible (either `internal` or via a companion object/helper `NotebookSerializer`).
  This allows tests in `ModelSerializationTest.kt` and `NoteRepositoryTest.kt` to run in a standalone directory (e.g. `/tmp/test_notes` or `app/build/test_data`) without mocking Android `Context` or spinning up Robolectric/instrumentation.

---

## 3. Caveats

1. **Shared Storage `noexec`**:
   The project is located in `/storage/emulated/0/FlowNotes`, which has the `noexec` mount flag. Shell scripts like `start.sh` must be executed with `bash start.sh` or `sh start.sh`.
2. **Terminal Execution Permissions**:
   Tool execution through Antigravity agent runner requires `BypassSandbox: true` because desktop Linux `bwrap` is not supported by Android SELinux.
3. **Heavy Package Downloads**:
   Installing `openjdk-17` and `kotlin` via `pkg install` downloads ~180MB of deb packages. The Worker can either install them or utilize a Python-based test runner that executes directly alongside the Kotlin test files, while retaining standard Gradle files for IDE/CI builds.
4. **Android SDK Version Compatibility**:
   `compileSdk 34` requires JDK 17. The Gradle configuration specifies `JavaVersion.VERSION_17` and `jvmTarget = "17"`.

---

## 4. Conclusion & Concrete Artifact Specifications

Below are the exact file contents and build configurations to be created by the M1 Worker:

### 4.1 `/storage/emulated/0/FlowNotes/settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FlowNotes"
include(":app")
```

### 4.2 `/storage/emulated/0/FlowNotes/build.gradle.kts`
```kotlin
// FlowNotes Root Build Script
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
```

### 4.3 `/storage/emulated/0/FlowNotes/gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

### 4.4 `/storage/emulated/0/FlowNotes/app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.flownotes.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.flownotes.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // AndroidX & UI Architecture
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.7.0")

    // Lifecycle & Coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")

    // Instrumentation Testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

### 4.5 Recommended Refactoring in `NoteRepository.kt`
Add constructor overloading to support pure file-based operations without Android `Context`:
```kotlin
class NoteRepository(val filesDir: File) {
    constructor(context: Context) : this(context.filesDir)

    private val notebooksDir: File = File(filesDir, "notebooks").apply { mkdirs() }
    val pdfsDir: File = File(filesDir, "pdf").apply { mkdirs() }
    val audioDir: File = File(filesDir, "audio").apply { mkdirs() }
    ...
```
And make `serializeNotebook` and `parseNotebookJson` public or internal within a companion object:
```kotlin
companion object {
    fun serializeNotebook(nb: Notebook): JSONObject { ... }
    fun parseNotebookJson(json: JSONObject): Notebook { ... }
}
```

### 4.6 Unit Test Specifications
The Worker should implement two primary unit test suites in `app/src/test/java/com/flownotes/app/`:
1. `model/ModelSerializationTest.kt`:
   - Validates `Point` initialization, coordinates, pressure, and timestamps.
   - Validates `Stroke` creation across all `PenTool` profiles (`BALLPOINT`, `FOUNTAIN`, `HIGHLIGHTER`, `PENCIL`, `ERASER_STROKE`, `ERASER_PIXEL`).
   - Validates `ActiveRecallTape` bounding box calculation and `contains(x, y)` point containment.
   - Validates `Page` and `Notebook` multi-page structure, paper type assignment, and round-trip JSON serialization equality.
   - Validates `Flashcard` SM-2 parameters (`reviewIntervalDays`, `repetitions`, `easeFactor`).
2. `data/NoteRepositoryTest.kt`:
   - Validates initialization in an isolated test folder (`File("/tmp/flownotes_test_repo")`).
   - Validates `ensureDefaultNotebooks()` populates welcome and science notebooks.
   - Validates `saveNotebook()`, `getNotebook()`, `getAllNotebooks()`, and `deleteNotebook()`.
   - Validates disk persistence across repository instances (simulated process restart).

---

## 5. Verification Method

### 5.1 Syntax & Layout Verification
1. Inspect generated Gradle files:
   `head -n 30 /storage/emulated/0/FlowNotes/settings.gradle.kts`
   `head -n 30 /storage/emulated/0/FlowNotes/build.gradle.kts`
   `head -n 50 /storage/emulated/0/FlowNotes/app/build.gradle.kts`
2. Verify package manager tools:
   `which aapt2` (must return `/data/user/0/com.agy/files/usr/bin/aapt2`).
   `ls -l /storage/emulated/0/Android/media/com.roxum/Projects/CodeAssist/ide-android/src/main/assets/android.jar`
   `ls -l /system/framework/framework.jar`

### 5.2 Standalone Compilation & Unit Test Execution Command
When the Worker creates the unit tests, verify execution using one of the following commands:
- **Via Gradle** (if OpenJDK & Gradle are installed):
  `gradle test --info`
- **Via Direct Kotlin Compiler & DalvikVM**:
  ```bash
  mkdir -p build/test-classes build/dex
  kotlinc -cp "/storage/emulated/0/Android/media/com.roxum/Projects/CodeAssist/ide-android/src/main/assets/android.jar:lib/*" \
    -d build/test-classes \
    app/src/main/java/com/flownotes/app/model/Models.kt \
    app/src/main/java/com/flownotes/app/data/NoteRepository.kt \
    app/src/test/java/com/flownotes/app/model/ModelSerializationTest.kt \
    app/src/test/java/com/flownotes/app/data/NoteRepositoryTest.kt
  d8 --output build/dex/ --lib /system/framework/framework.jar build/test-classes/**/*.class
  dalvikvm -cp /system/framework/framework.jar:build/dex/classes.dex org.junit.runner.JUnitCore com.flownotes.app.model.ModelSerializationTest com.flownotes.app.data.NoteRepositoryTest
  ```
- **Via FlowNotes Local Test Verifier (`python tests/verify_m1.py`)**:
  A lightweight verification runner validating the exact JSON serialization output and repository contract against `data/notebooks/*.json`.

### 5.3 Invalidation Conditions
- Missing Android SDK compile-time jar at the identified path.
- Incompatibility between AGP 8.5.2 and Kotlin version without appropriate plugin mappings.
- Failure of `NoteRepository` to operate without a full Android Activity context if constructor overloading is omitted.
