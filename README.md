# Pixora PDF – Image to PDF Converter (Android, Kotlin + Jetpack Compose)

Open this folder in Android Studio (or build from a terminal with `./gradlew assembleDebug`; on Windows `gradlew.bat assembleDebug`. The first run downloads the Gradle wrapper jar and Gradle itself, so internet is needed) (Ladybug or newer), let Gradle sync, and run on a device/emulator with Android 10+ (API 29).

## Screens / features (matches the mock-up)
1. **Home** – banner, quick tools, recent files
2. **Select Images** – system photo picker (Photos / Albums / Recent), numbered selection, remove, add more
3. **Arrange Pages** – select pages, Rotate, Crop, Delete, Reorder (arrow buttons), Add Images
4. **PDF Settings** – file name, A4 / Letter / Original, orientation, quality slider, page margin
5. **PDF Created** – Open (in-app viewer), Share, Rename, View in Files, Create Another
6. **My PDFs** – All / Recent / Favorites, search, sort, per-file menu (open, share, rename, favourite, delete)
7. **Tools** – Image to PDF, Scan, Compress, Merge, Split, JPG to PDF, PDF to Images, Rotate, Add Password
8. **Crop** – free-form and 1:1, 3:4, 4:3, 16:9 with draggable handles, rotate left/right, reset
9. **Scan Document** – CameraX, Photo / Document (scanner filter) modes, flash, gallery
10. **Settings** – dark mode, default page size & quality, save location, language, rate, share, privacy

PDFs are saved to `Downloads/Pixora PDF` (or `Documents/Pixora PDF`) using MediaStore – no storage permission needed to save.

## Notes
* Merge / Split / Rotate / Password use PdfBox-Android and keep real PDF pages. Compress and PDF-to-Images render pages with `PdfRenderer`.
* Only English strings are included; add translations before enabling more languages.
* Password-protected PDFs can't be previewed in-app (Android's PdfRenderer limitation).
