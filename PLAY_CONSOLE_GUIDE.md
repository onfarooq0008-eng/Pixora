# Pixora PDF – Google Play Console guide (package: com.pixorapdf)

## 0. Website
Developer: Umar Farooq · admin@fastvpnn.pp.ua · https://fastvpnn.pp.ua · Pakistan (already filled in).
Upload the contents of `PixoraPDF-Website/` to your hosting. If you put them in a folder (for example `/pixora/`), the privacy policy URL becomes `https://fastvpnn.pp.ua/pixora/privacy-policy.html`.
Privacy policy URL to paste in Play Console: `https://onfarooq0008-eng.github.io/Pixoraweb/privacy-policy.html`

## 1. Build the release bundle (AAB)
1. Create a signing key once (keep the file and passwords safe, never lose them):
   `keytool -genkeypair -v -keystore pixora-release.jks -alias pixora -keyalg RSA -keysize 2048 -validity 10000`
2. Create `keystore.properties` in the project root (it is git-ignored):
   ```
   storeFile=pixora-release.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=pixora
   keyPassword=YOUR_KEY_PASSWORD
   ```
3. Build: `./gradlew bundleRelease` (Windows: `gradlew.bat bundleRelease`)
   Output: `app/build/outputs/bundle/release/app-release.aab`
4. In Play Console use **Play App Signing** (recommended) and upload the AAB.
Before each update, raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.

## 2. Store listing
- **App name (max 30):** Pixora PDF – Image to PDF
- **Short description (max 80):** Convert photos to PDF, scan documents, merge, split, compress & protect PDFs.
- **Full description (max 4000):**
  Pixora PDF turns your photos into clean, high-quality PDF files in seconds – and gives you the PDF tools you need every day, all on your phone and all offline.

  IMAGE TO PDF
  • Choose photos with the system photo picker (Photos, Albums, Recent)
  • Rotate, crop, delete and reorder pages
  • Choose A4, Letter or original page size, portrait or landscape, margins and image quality

  SCAN DOCUMENT
  • Scan pages with your camera, with a scanner-style Document mode
  • Crop each page and build a multi-page PDF

  PDF TOOLS
  • Merge several PDFs into one
  • Split a PDF by page ranges
  • Compress large PDFs
  • Rotate PDF pages
  • Add a password (AES-128 encryption)
  • Convert PDF pages to JPG images

  MY PDFs
  • Open, share, rename, favourite and delete your PDFs
  • Search and sort your files

  PRIVATE BY DESIGN
  • Works offline – no account, no ads, no tracking
  • No internet permission: your photos and documents never leave your device
  • Dark mode support
- **Category:** Tools (or Productivity). **Tags:** PDF, Document scanner, Converter.
- **Contact details:** email (required), website (optional), phone (optional).
- **Privacy policy URL:** your hosted `privacy-policy.html`.
- **Graphics:** app icon 512×512 PNG (a draft is in `play-assets/`), feature graphic 1024×500 (draft included), 2–8 phone screenshots (min 320px, max 3840px, 16:9 or 9:16). Take screenshots of the real app on a phone or emulator.

## 3. App content (Policy → App content)
| Section | Answer |
|---|---|
| Privacy policy | Your hosted URL |
| Ads | **No**, app does not contain ads |
| App access | All functionality available without login |
| Content rating (IARC) | Category **Utility / Productivity**; answer **No** to violence, sexual content, language, gambling, controlled substances, user-generated content sharing, location sharing. Expected rating: Everyone / PEGI 3 |
| Target audience | **18 and over** (simplest; avoids the Families policy). Not designed for children |
| News app | No |
| COVID-19 / government / financial / health apps | No |
| Data safety – collects or shares data? | **No** (the app has no internet permission and no SDKs that collect data) |
| Data safety – security practices | Data is not transmitted, so "encrypted in transit" can be left as not applicable. Deletion: no account; users delete files on device |
| Account deletion URL | Not required (no accounts). Optional: your `data-deletion.html` |
| Photo and video permissions | **Not declared** – the app uses the system photo picker and has no READ_MEDIA_* permissions |
| Permissions | Only CAMERA (optional, Scan Document) |
| Advertising ID | Not used – answer **No** |

## 4. Testing requirement (personal developer accounts)
If your developer account was created after 13 Nov 2023 you must run a **closed test with at least 12 testers for 14 continuous days** before you can apply for production. Organisation accounts are exempt.

## 5. Release notes (first release)
Welcome to Pixora PDF 1.0! Convert images to PDF, scan documents, merge, split, compress, rotate and protect PDFs.

## 6. Pre-launch checklist
- [ ] `./gradlew assembleDebug` succeeds and the app runs on a real phone
- [ ] `./gradlew lint` reviewed
- [ ] Test the **release** build (`./gradlew assembleRelease`, install the APK): create a PDF, scan, merge, split, compress, rotate, password, PDF to images (minify can break libraries; the proguard rules are in `app/proguard-rules.pro`)
- [ ] Website pages uploaded and the privacy policy URL opens in a browser
- [ ] Screenshots, icon and feature graphic uploaded
