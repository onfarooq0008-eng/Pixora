# Pixora PDF – audit report (package com.pixorapdf)

**Environment limits:** no Android SDK, Gradle or Kotlin compiler was available, so the project was NOT built, linted, unit-tested or run on a device/emulator. Everything below is from reading the source. Nothing here claims the app is bug-free.

## Checked
- All 9 Kotlin files, manifest, Gradle files, resources: brace/paren balance verified with a script; no references to the old package, removed permissions or deleted gallery code remain (grep).
- Manifest: only CAMERA is declared; no INTERNET, no storage/photo-library permissions; no exported components besides the launcher activity.
- Only one app dependency set is used; every declared library is referenced in code (Compose, CameraX, lifecycle, PdfBox-Android).

## Bugs / risks fixed
1. Play-policy risk: READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE removed; Select Images now uses the system photo picker.
2. PickMultipleVisualMedia(100) could throw on devices with a lower picker limit; now uses the default limit.
3. Camera provider was released with a blocking `future.get()` on the UI thread; now unbound through a stored reference.
4. Page-count cache was a plain HashMap used from background threads; now a ConcurrentHashMap.
5. Rapid double tap on "Create PDF" could start two writes; guarded.
6. Temporary scan photos accumulated in cache; files older than 24 h are deleted at startup (background thread).
7. `allowBackup` turned off (no private data should be restored to other devices).

## Performance
- Thumbnails decoded off the main thread, downscaled, kept in an LruCache (1/8 of heap).
- Full-size images are decoded at most to the chosen quality size; each page bitmap is recycled after it is written to the PDF.
- PDF viewer renders one page at a time under a mutex and only for visible items.
- Release build: R8 minify + resource shrinking enabled.

## Removed
- Old MediaStore gallery query and model (dead after photo-picker switch), `toggle`/`removeUris` helpers, unused imports.
- READ_MEDIA_IMAGES and READ_EXTERNAL_STORAGE permissions.

## Remaining / untested (needs you)
- **First Gradle sync + `./gradlew assembleDebug`** – highest compile risk is the PdfBox-Android API usage in `PdfOps.kt` (`JPEGFactory.createFromImage(doc, bitmap, quality)`, `StandardProtectionPolicy`, `importPage`). If a signature differs in 2.0.27.0, adjust that one call.
- `./gradlew lint`, then install and test the **release** APK (R8 can break reflection-based libraries; rules are in `app/proguard-rules.pro`).
- Not tested on any device: photo picker, camera capture, crop gestures, MediaStore saving/renaming/deleting, PdfRenderer viewer, merge/split/rotate/password/compress, dark mode.
- Play target API 36 / 16 KB page-size rule: app now targets API 36 (AGP 8.13.2, Gradle 8.13) and uses CameraX 1.4.2 (1.3.x had a non-16 KB-aligned native lib). The release CI job runs `scripts/check_16kb.py` on the AAB and fails if any 64-bit .so is not 16 KB aligned.
- Portrait is locked (`screenOrientation`); Play may flag this for tablets/foldables as a quality warning.
- Language setting is English only; privacy/terms pages need your name, email, website and country filled in.
- No unit or UI tests exist in the project.

## Commands to run
```
./gradlew assembleDebug
./gradlew lint
./gradlew assembleRelease      # needs keystore.properties for a signed build
./gradlew bundleRelease        # AAB for Play Console
```

## Key files
`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/proguard-rules.pro`, `CreateFlow.kt` (Select screen), `ScanScreen.kt`, `PdfOps.kt`, `Imaging.kt`, `Model.kt`, `MainActivity.kt`.
