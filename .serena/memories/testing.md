# Testing

## Frameworks
- Unit tests: JUnit 4 (4.13.2). Mockito 5.24.0 used in :app UNIT tests only; :component-network tests use hand-written anonymous stubs (no mocking framework). DEVICE (androidTest) tests are Mockito-free everywhere per maintainer decision — mockito-android was evaluated (2026-11-05) and REJECTED: "Mockito is unreliable on ART"; on-device boundaries use test subclasses / real Room instead.
- Instrumented: AndroidJUnit4 + Espresso 3.7.0 (core + accessibility), androidx.test.ext:junit 1.3.0, Room `room-testing` 2.7.2.
- kxml2 2.3.0 is a test-only dep of `:component-network` (XmlPullParser implementation for local JVM tests).
- `app/src/sharedTest/java` is compiled into BOTH the unit-test and instrumented-test source sets (configured in `app/build.gradle` sourceSets); currently holds `test/util/DirectExecutorService.java`. Put single-sourcing test doubles there.

## Unit Tests (`gradlew test`)
- **:app** (`app/src/test/java/m/co/rh/id/a_news_provider/`):
  - `provider/notifier/RssChangeNotifierTest.java` — Rx notifier behavior.
  - `provider/command/SearchRssItemsCmdTest.java`, `provider/command/RedirectDuplicateCheckerTest.java`, `provider/command/MarkItemsReadOnScrollCmdTest.java` (Mockito-mocked Provider/DAO/RequestQueue seam style).
  - Note: `FetchArticleContentCmdTest` MOVED to :app androidTest on 2026-11-05 (see Instrumented Tests below).
  - `base/util/UrlNormalizerTest.java`, `app/util/NotificationPermissionPolicyTest.java` (test classes live in :app even for :base code).
  - `app/util/OpenLinkPolicyTest.java` — "Open links in" decision/fallback logic (pure JVM, no Android deps).
  - Note: the old unit `RssRepositoryTest` was moved to androidTest (in-memory Room, real DAO queries incl. neighbor queries); `RssChannelStateNotifierTest` was removed.
- **:component-network** (`component-network/src/test/java/m/co/rh/id/a_news_provider/component/network/`):
  - `parser/RssFeedParserTest.java` — 19 tests: RSS 2.0 / Atom / RDF parsing, enclosure image/video, media:content (image/video/medium-video), image+thumbnail, unknown root tag, malformed XML, empty feed, rss-without-channel, categories. Uses silent anonymous `ILogger` stub and anonymous `Provider` stub.
  - `parser/RssDateParserTest.java` — 21 tests: RFC-822 variants (timezones, missing seconds/tz, day-first), ISO-8601 (Z, offset, millis, date-only), invalid inputs, logger-called-on-failure.
  - Note (2026-11-05): `ArticleExtractorTest` and `ArticleHtmlRequestTest` MOVED to the :component-network androidTest source set per maintainer decision (see Instrumented Tests below) — the unit source set now holds the parser/date-parser tests only.
- **:base** — no unit tests.

## Instrumented Tests (`gradlew connectedCheck`)
- **:app** (`app/src/androidTest/java/m/co/rh/id/a_news_provider/`):
  - `AppSVTest.java`, `AppPageTest.java` — page smoke tests (Espresso).
  - `AccessibilityAuditTest.java` — TalkBack/Accessibility Scanner audit checks (feed row state descriptions, custom actions, touch targets).
  - `OpmlParserTest.java`, `base/AppSharedPreferencesTest.java`.
  - `app/provider/repository/RssRepositoryTest.java` — in-memory Room via ProviderDbRule; includes next/prev neighbor-query tests (list walking, edges, pubDate fallback, tie-breaks, filter respect).
  - `app/provider/command/PauseRssChannelCmdTest.java`, `app/provider/command/NewRssChannelCmdTest.java`, `app/provider/service/RssServiceTest.java`.
  - `app/provider/command/FetchArticleContentCmdTest.java` (moved from unit source set 2026-11-05, 8 tests): real Room `article_content` rows via ProviderDbRule + real NetworkProviderModule-registered ArticleExtractor + real RssChangeNotifier (TestSubscriber on `getArticleContentUpdated()`), NO Mockito. Network boundary = package-private `TestFetchArticleContentCmd extends FetchArticleContentCmd` overriding the package-private `fetchArticleBytes` seam (fixture bytes / thrown failure / attempt counter), registered FIRST in a `FetchCmdOverrideProviderModule` that wraps `IntegrationTestAppProviderModule` with `DirectExecutorService` + `setSkipSameType(true)` (full synchronous execution); the failed-lookup scenario swaps in a hand-written throwing `FailingLookupDao` subclass of `ArticleContentDao` whose counting upsert records any accidental write.
  - Infra: `test/{TestApplication, AppAndroidJunit4Runner, TestPage, NoOpLogger, FakeWorkManager}.java`, `test/util/{ProviderDbRule, RecordingRedirectResolver}.java`, `provider/IntegrationTestAppProviderModule.java` (replica of `AppProviderModule` for tests: no Navigator, custom test DB name, everything `registerLazy`).
- **:base**: `DbMigrationTest.java` — Room `MigrationTestHelper` + `FrameworkSQLiteOpenHelperFactory`; `migrateAll()` creates DB at version 1 and validates through all migrations to v14; dedicated data-level tests per migration since 8→9 (duplicate merge, is_paused, categories, link index, dedup_key, 13→14 `article_content` CREATE TABLE + REPLACE-by-link round-trip). `RssDaoTest.java` — section 7 covers the `article_content` cache: rows survive an `upsertRssItemsForChannel` re-sync untouched, and `deleteOrphanArticleContent` deletes exactly rows whose link lost its last rss_item (shared links kept).
- **:component-network** (`component-network/src/androidTest/java/m/co/rh/id/a_news_provider/component/network/`): `ArticleExtractorTest.java` — 11 tests over the 7 HTML fixtures now in `src/androidTest/resources` (packaged INTO the androidTest APK and loaded via plain classpath `getResourceAsStream` — no assets needed; simple, noisy boilerplate, hostile script/iframe/javascript:, NPR-style escaped embed-code text + prose-survival, too-short, windows-1252 raw bytes via meta-charset detection, relative-link absolutization + img alt-to-text, null/empty input, determinism, safelist-only tags); instantiates `ArticleExtractor` directly (stateless singleton). `ArticleHtmlRequestTest.java` — 6 static `isHtmlContentType` guard tests (kept from the unit suite) + 4 real-instance tests: constructor wiring (GET, shouldCache false, 15 s/0-retry policy, Uri-derived traffic tag), parseNetworkResponse raw-bytes passthrough (byte-for-byte incl. 0xE9/0x92/0x80 payload, content types text/html / application/xhtml+xml / charset suffix / TEXT/HTML case), non-HTML + missing Content-Type fail-fast as ParseError, deliverResponse into a hand-written recording listener. Plus `ExampleInstrumentedTest.java`, `provider/cache/DiskLruBitmapCacheTest.java`, `ssl/ExtendedTrustManagerTest.java`.

## Notes
- Unit tests don't need a device; parser/date-parser tests are pure JVM.
- Tests avoid blocking waits — use DirectExecutorService / FakeWorkManager doubles instead of real threading (commit 7197409).
- When adding a Room migration: bump DB version in `AppDatabase`, add MIGRATION_X_Y to `DbMigration.getAll()`, export schema JSON, and extend `DbMigrationTest` coverage accordingly.
- CI runs instrumented tests on emulators API 26/31/36 (API 23 dropped 2026-10) — keep tests compatible with minSdk 21+ behaviors.