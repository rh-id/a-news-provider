# Conventions & Gotchas

## Language & Build Conventions
- **Pure Java** — never introduce Kotlin. All modules use Java 17 toolchain with core library desugaring (`desugar_jdk_libs:2.1.5`).
- **Room compiler via `annotationProcessor`** — this project has NO kapt and NO ksp. Keep it that way when adding annotation processors.
- RxJava is pinned to **3.1.12** because newer versions raise minSdk above 21 — do not upgrade casually.
- minSdk 21: avoid APIs above that or guard with SDK checks (see `RssDateParser` SDK-aware format lists).
- Gradle Groovy DSL (not Kotlin DSL); AGP 9.2.1; deps resolved from google/mavenCentral/jitpack (`com.github.rh-id:*` for the author's libs).

## Architectural Conventions
- New screens = new `StatefulView` page + route constant in `Routes` + factory entry in `AppProviderModule.getNavigator()`. No Fragments, no second Activity.
- New business logic = new Command class in `app/.../provider/command/` + lazy registration in `CommandProviderModule`. UI never touches DAOs/workers directly — always via `RssRepository` or commands.
- New dependencies go into the matching ProviderModule (`BaseProviderModule` for infra, `DatabaseProviderModule` for DB, `NetworkProviderModule` for HTTP, `CommandProviderModule` for commands). Register async (`registerAsync`) or lazy (`registerLazy`) where construction is expensive.
- Per-page state/subscriptions must be disposed via the page's scoped `StatefulViewProvider` / `RxDisposer` — never leak subscriptions past page pop.
- Background jobs = WorkManager workers with unique names registered in `ConstantsWork`; input data keys in `ConstantsKey`. Sync work uses `NetworkType.CONNECTED` constraint and `ExistingWorkPolicy.KEEP`.

## Localization Conventions (added with Chinese, commit 6f44c33)
- Translated strings files must have the EXACT same `<string name>` keys as `values/strings.xml` (same count/order) and preserve every `%s`/`%d`/`%n$s` placeholder — build breaks or crashes otherwise.
- `app_name` stays "News Provider" untranslated in locale files (matches 9/10 existing locales; only Indonesian translates it).
- Full-width Chinese quotes “” need no XML escaping (unlike English `\"Error: %s\"`).
- No `<resourceConfigurations>` filter and no `localeConfig.xml` exist — new `values-<locale>` dirs are picked up automatically, no Gradle/manifest changes needed.
- Adding a locale = strings in `app` + `component-network` + fastlane dir; store dir naming may differ from resource dir (`values-zh` vs `zh-CN`, see `fastlane_and_release` memory).

## Gotchas
- **WorkManager default initializer is removed** (manifest `tools:node="remove"` on InitializationProvider) — `MainApplication` provides the config manually. Don't re-add default init.
- `MainApplication` installs a crash handler that disposes the whole Provider before delegating — keep provider disposal idempotent.
- Read/favorite state syncs **by item link** across channels (`updateRssItemsIsReadByLink` etc.) — same article in multiple feeds shares state. Preserve this when touching item updates.
- Feed lists use `LIMIT`-based paging (`findRssItemsWithLimit/Asc`) — no androidx.paging.
- `RssDateParser.parsePubDate/parseUpdated` return null on failure and log — callers must null-check.
- `RssFeedParser` throws on unrecognized root tag — new feed formats require explicit parser support + tests.
- Release signing depends on env vars (`SIGNING_KEY` etc.); local unsigned release builds are expected when unset.
- Changelog for release workflow is keyed by **versionCode** file name in `fastlane/metadata/android/en-US/changelogs/`.
- ILogger is a CompositeLogger (Android + File `cacheDir/alogger/app.log` + Toast) — don't use `Log`/`System.out` directly; use the provided `ILogger`.
- Volley cache dir is `cacheDir/volley` (20 MiB); ImageLoader LruCache holds 20 bitmaps — mind memory when adding image surfaces.
- `.serena/` directory is untracked in git (has its own `.gitignore`); keep memory churn out of commits unless asked.
