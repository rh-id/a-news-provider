# Project Overview: a-news-provider

RSS aggregator Android app ("A simple and easy to use RSS aggregator that delivers news to your smartphone").
GitHub: https://github.com/rh-id/a-news-provider (repo root package `m.co.rh.id.a_news_provider`).

**Purpose:** production-quality demo app for the author's libraries:
- [a-navigator](https://github.com/rh-id/a-navigator) — StatefulView-based UI navigation (no Fragments)
- [a-provider](https://github.com/rh-id/a-provider) — Service Locator DI (Provider + ProviderModule)

## Tech Stack
- **Language:** Pure Java 17 (NO Kotlin anywhere). Core library desugaring enabled (`desugar_jdk_libs:2.1.5`).
- **Build:** Gradle 9.4.1 wrapper, Android Gradle Plugin 9.2.1, Groovy DSL. JitPack repo for `com.github.rh-id:*` artifacts.
- **Android:** compileSdk 37, targetSdk 37, minSdk 21. versionCode 75, versionName 2.0.0 (upcoming v2.0.0 release, not yet tagged).
- **Async:** RxJava 3 (rxjava 3.1.12 PINNED for minSdk 21 compatibility) + rxandroid 3.0.2.
- **Persistence:** Room 2.7.2 (wired via `annotationProcessor`, NOT kapt/ksp), DB `a-news-provider.db` version 8 with migrations 1→8.
- **Networking:** Volley 1.2.1 (RequestQueue + 20 MiB disk cache, ImageLoader with 20-entry LruCache).
- **Background:** WorkManager 2.10.5 (default initializer removed; manual init in `MainApplication`).
- **Other:** material 1.13.0, recyclerview 1.4.0, LeakCanary plumber-android 2.14, `com.cookpad.android.plugin.license-tools:1.2.8`, `com.github.rh-id:AndroidShowCase:v0.0.1`.

## Gradle Modules (settings.gradle)
- `:app` — applicationId `m.co.rh.id.a_news_provider`; UI (StatefulViews), Commands, WorkManager workers, notifications, DI root.
- `:base` — library; Room DB (entities/DAOs/migrations), AppSharedPreferences, base provider modules, logger wiring, network-status helper.
- `:component-network` — library; Volley `RssRequest` + RSS 2.0/Atom/RDF (RSS 1.0) feed parsers (supports `media:content`, `media:thumbnail`, `enclosure`).

## Key Features (user-facing)
RSS 2.0/Atom/RDF support, OPML import/export, media images/videos (video download via system DownloadManager to `Downloads/<feedName>/`), favorites + All/Unread/Read/Favorites filters, newest/oldest sort, mark-all-read, periodic background sync (1-24h, default 6h), per-channel grouped sync notifications, 11 languages (incl. Simplified Chinese since v1.15.0), themes (system/light/dark), one-hand mode, in-app log viewer, licenses screen. Supports Android 5.0+.

## Supported Feed Parsing
Root tag dispatch: `rss` → RSS 2.0, `feed` → Atom, `rdf:RDF` → RDF/RSS 1.0. Dates parsed best-effort from RFC-822 and ISO-8601 formats (`RssDateParser`).

## Related Serena Memories
- `architecture` — module graph, DI, commands, navigation, reactive flow
- `codebase_layout` — key classes and file paths
- `build_and_commands` — build/test/release commands and CI
- `testing` — test structure per module
- `conventions_and_gotchas` — style rules and pitfalls
- `fastlane_and_release` — store metadata and release flow
