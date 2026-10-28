# GitHub Actions setup (debug APK + signed AAB)

The workflow is `.github/workflows/android.yml`. It runs on every push to `main`/`master`, on tags like `v1.0`, on pull requests (debug APK only) and manually (Actions tab → Android build → Run workflow).

## One-time setup (signed AAB)
1. Create your signing key (keep the `.jks` file and both passwords somewhere safe – if you lose them you cannot update your app):
   `keytool -genkeypair -v -keystore pixora-release.jks -alias pixora -keyalg RSA -keysize 2048 -validity 10000`
2. Turn the key into text:
   - Mac/Linux: `base64 -w0 pixora-release.jks` (Mac: `base64 -i pixora-release.jks`)
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("pixora-release.jks"))`
3. On GitHub open your repository → **Settings → Secrets and variables → Actions → New repository secret** and add 4 secrets:

   | Name | Value |
   |---|---|
   | `KEYSTORE_BASE64` | the long text from step 2 |
   | `KEYSTORE_PASSWORD` | keystore password |
   | `KEY_ALIAS` | `pixora` (the alias you used) |
   | `KEY_PASSWORD` | key password |
4. Never commit the `.jks` file or `keystore.properties` (the `.gitignore` already blocks them).

## Getting your files
- Open the repo → **Actions** → click the latest run → **Artifacts** at the bottom:
  - `pixora-debug-apk` – install on a phone to test.
  - `pixora-release-aab` – upload this to Play Console.
- Push a tag (`git tag v1.0 && git push origin v1.0`) and the AAB is also attached to a GitHub Release.

## Notes
- `versionCode` is set automatically from the GitHub run number, so each upload to Play is higher than the last. Change `versionName` in `app/build.gradle.kts` when you want a new visible version.
- The workflow uses Gradle 8.13 through the official setup action, so it works even without `gradle-wrapper.jar` in the repo.
- If the AAB job says "Missing signing secrets", one of the 4 secrets is empty or misspelled.
- Pull requests only build the debug APK (secrets are not available to them).
- Play App Signing is recommended: you upload this AAB, Google re-signs it for users.
