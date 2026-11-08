---
name: android-ui-and-build
description: Android build stability, Kotlin scoping, and anti-AI-slop professional aesthetic guardrails
trigger: always_on
---

# FlowNotes Android & UI Design Guardrails

## 1. Resource & Vector Standard
- Never reference framework `@android:drawable/*` for custom UI controls. Always define and use project vector drawables in `app/src/main/res/drawable/`.
- Ensure all vector drawables are self-contained and compatible with Android API 26+.

## 2. Kotlin Syntax & Scoping
- In closures with implicit receivers (`apply`, `with`, listeners), qualify member mutations on the enclosing class (`this@ClassName.property = ...`) to prevent collisions with read-only receiver properties (e.g. `MediaPlayer.isPlaying`).
- Always explicitly import project models whose names match Android framework classes (e.g., `import com.flownotes.app.model.Point`) to avoid star-import ambiguity.

## 3. Aesthetics & Theming (Anti-AI Slop)
- **Palette**: Strictly avoid hyper-saturated neon glows, radioactive purples, and generic AI-slop gradients. Use refined, muted, editorial-grade palettes inspired by Scandinavian paper stationery.
- **Three Themes**: Support Light (warm paper), Dark (charcoal/slate), and AMOLED (pitch black `#000000`).
- **Surfaces**: Floating window aesthetic with rounded container cards (16dp-24dp corner radii), subtle elevation drop shadows, and clean divider spacing.

## 4. Strict CI-Only Build & Debug Policy (No Local Compilation)
- **Zero Local Compilation**: NEVER invoke `./gradlew assembleDebug`, `./gradlew build`, `./gradlew compileDebugKotlin`, or execute heavy Android SDK compiler binaries directly on this local device.
- **GitHub Actions CI Exclusively**: All compilation, bytecode verification, and debug logging MUST run via GitHub Actions CI (`.github/workflows/android-build.yml`).
- **Workflow**: Stage and commit code changes, push to GitHub (`origin main`), and debug build outputs and logs via the GitHub CLI (`gh run list`, `gh run view`, `gh run view --log-failed`).
