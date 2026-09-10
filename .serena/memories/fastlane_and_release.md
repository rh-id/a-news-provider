# Fastlane & Release Flow

## Directory: `fastlane/metadata/android/`
Play Store metadata for **11 locales**: `de-DE`, `en-US`, `et`, `fr-FR`, `id`, `is-IS`, `it-IT`, `nb-NO`, `no-NO`, `rm`, `zh-CN`.

Per-locale structure:
- `title.txt` — app title
- `short_description.txt` / `full_description.txt` — store descriptions
- `changelogs/<versionCode>.txt` — per-release changelog notes (en-US has 1.txt … 74.txt; other locales have partial sets)
- `images/icon.png`, `images/featureGraphic.png`, `images/phoneScreenshots/*.png`

## Release Flow (no Fastfile — Gradle + GitHub Actions only)
1. Bump `versionCode`/`versionName` in `app/build.gradle`.
2. Add `fastlane/metadata/android/en-US/changelogs/<newVersionCode>.txt` (release notes; required — the workflow uses it as release body). Update other locales as desired.
3. Commit, tag `v<versionName>` (e.g. `v1.15.0`), push tag.
4. `.github/workflows/android-release.yml` triggers:
   - Builds Debug + Release APKs (release signed from secrets: `SIGNING_KEY` base64 keystore + `KEY_STORE_PASSWORD`/`ALIAS`/`KEY_PASSWORD`).
   - Gradle `afterEvaluate` in `app/build.gradle` copies `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` to `app/build/changelog.txt`.
   - Creates GitHub Release with `app-debug.apk`, `app-release.apk`, `changelog.txt`; release body = the changelog text.

## Notes
- Fastlane structure is maintained for potential store deployment/metadata management, but CI does NOT run `fastlane` — it drives Gradle directly.
- Localized strings in-app (11 languages: en + de, et, fr, in, is, it, nb, nn, rm, zh) live in each module's `res/values-*/strings.xml`; fastlane store text is separate from app resources.
- When adding a new language: add `res/values-<locale>/strings.xml` across modules (`app`, `component-network`) AND a `fastlane/metadata/android/<locale>/` folder. Convention (established when adding Chinese, commit 6f44c33): translate `title.txt`/`short_description.txt`/`full_description.txt`, but COPY `en-US/changelogs/*` and `en-US/images/*` verbatim into the new locale dir.
- Locale dir naming: F-Droid (fdroidserver `update.py`) accepts bare ISO 639-1 codes and RFC5646 tags (glob `[a-z][a-z]*`, no whitelist; examples in docs: en, en-US, ast). Google Play `supply` requires region-qualified codes for Chinese (zh-CN/zh-TW/zh-HK), hence `zh-CN` was chosen for the store dir while in-app resources use generic `values-zh` (Simplified fallback for all zh devices).
