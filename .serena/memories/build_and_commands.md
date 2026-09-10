# Build & Commands

## Requirements
- JDK 17 (Java 17 toolchain + core library desugaring)
- Android SDK, compileSdk 37
- Gradle 9.4.1 wrapper (`gradlew` / `gradlew.bat`), AGP 9.2.1, Groovy DSL

## Common Commands (run from repo root)
- `.\gradlew build` — full build + unit tests
- `.\gradlew test` — unit tests only
- `.\gradlew connectedCheck` — instrumented tests (needs emulator/device; CI uses API 23/26/31/36)
- `.\gradlew assembleDebug` / `assembleRelease` — APKs
- Per-module: append `:module`, e.g. `.\gradlew :component-network:test` (parser unit tests run on JVM; kxml2 provides XmlPullParser)

## Signing (release)
Release builds are signed only when env vars are set, otherwise unsigned:
- `SIGNING_KEY` — base64-encoded keystore
- `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`

## CI/CD (GitHub Actions, `.github/workflows/`)
- **`gradlew-build.yml`** ("Android CI"): push/PR to `master` → JDK 17 → `./gradlew build`.
- **`android-release.yml`**: tag `v*` pushed → builds Debug + Release APKs (signed from repo secrets) → creates GitHub Release with `app-debug.apk`, `app-release.apk`, `changelog.txt`. Release body from `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- **`android-emulator-test.yml`**: push/PR to `master` → `./gradlew connectedCheck` on emulator matrix API 23/26/31/36 (fail-fast disabled, KVM, AVD cache; wakes screen, dismisses keyguard; API 36 runs cold boot).

## Version Bump Ritual (release)
1. Bump `versionCode`/`versionName` in `app/build.gradle`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (+ other locales as needed).
3. Gradle `afterEvaluate` in `app/build.gradle` copies `en-US/changelogs/<versionCode>.txt` to `app/build/changelog.txt` for the release workflow.
4. Tag `v<name>` and push → release workflow runs.

## Other
- `com.cookpad.android.plugin.license-tools` plugin (v1.2.8) manages open-source license list (Licenses screen).
- Repositories: google, mavenCentral, jitpack (root `build.gradle`).
- Room schema location configured in module gradle files (`base/schemas/...`, `app/schemas/...`).
