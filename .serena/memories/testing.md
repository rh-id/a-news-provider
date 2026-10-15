# Testing

## Frameworks
- Unit tests: JUnit 4 (4.13.2). Mockito 5.24.0 used in :app tests; :component-network tests use hand-written anonymous stubs (no mocking framework).
- Instrumented: AndroidJUnit4 + Espresso 3.7.0 (core + accessibility), androidx.test.ext:junit 1.3.0, Room `room-testing` 2.7.2.
- kxml2 2.3.0 is a test-only dep of `:component-network` (XmlPullParser implementation for local JVM tests).
- `app/src/sharedTest/java` is compiled into BOTH the unit-test and instrumented-test source sets (configured in `app/build.gradle` sourceSets); currently holds `test/util/DirectExecutorService.java`. Put single-sourcing test doubles there.

## Unit Tests (`gradlew test`)
- **:app** (`app/src/test/java/m/co/rh/id/a_news_provider/`):
  - `provider/notifier/RssChangeNotifierTest.java` — Rx notifier behavior.
  - `provider/command/SearchRssItemsCmdTest.java`, `provider/command/RedirectDuplicateCheckerTest.java`, `provider/command/MarkItemsReadOnScrollCmdTest.java`.
  - `base/util/UrlNormalizerTest.java`, `app/util/NotificationPermissionPolicyTest.java` (test classes live in :app even for :base code).
  - Note: the old unit `RssRepositoryTest` was moved to androidTest (in-memory Room, real DAO queries incl. neighbor queries); `RssChannelStateNotifierTest` was removed.
- **:component-network** (`component-network/src/test/java/m/co/rh/id/a_news_provider/component/network/`):
  - `parser/RssFeedParserTest.java` — 19 tests: RSS 2.0 / Atom / RDF parsing, enclosure image/video, media:content (image/video/medium-video), image+thumbnail, unknown root tag, malformed XML, empty feed, rss-without-channel, categories. Uses silent anonymous `ILogger` stub and anonymous `Provider` stub.
  - `parser/RssDateParserTest.java` — 21 tests: RFC-822 variants (timezones, missing seconds/tz, day-first), ISO-8601 (Z, offset, millis, date-only), invalid inputs, logger-called-on-failure.
- **:base** — no unit tests.

## Instrumented Tests (`gradlew connectedCheck`)
- **:app** (`app/src/androidTest/java/m/co/rh/id/a_news_provider/`):
  - `AppSvTest.java`, `AppPageTest.java` — page smoke tests (Espresso).
  - `AccessibilityAuditTest.java` — TalkBack/Accessibility Scanner audit checks (feed row state descriptions, custom actions, touch targets).
  - `OpmlParserTest.java`, `base/AppSharedPreferencesTest.java`.
  - `app/provider/repository/RssRepositoryTest.java` — in-memory Room via ProviderDbRule; includes next/prev neighbor-query tests (list walking, edges, pubDate fallback, tie-breaks, filter respect).
  - `app/provider/command/PauseRssChannelCmdTest.java`, `app/provider/command/NewRssChannelCmdTest.java`, `app/provider/service/RssServiceTest.java`.
  - Infra: `test/{TestApplication, AppAndroidJunit4Runner, TestPage, NoOpLogger, FakeWorkManager}.java`, `test/util/{ProviderDbRule, RecordingRedirectResolver}.java`, `provider/IntegrationTestAppProviderModule.java` (replica of `AppProviderModule` for tests: no Navigator, custom test DB name, everything `registerLazy`).
- **:base**: `DbMigrationTest.java` — Room `MigrationTestHelper` + `FrameworkSQLiteOpenHelperFactory`; `migrateAll()` creates DB at version 1 and validates through all migrations to v11; dedicated `migrate8to9_mergesLegacyAndCanonicalDuplicate` data-level test.
- **:component-network**: template `ExampleInstrumentedTest.java` only.

## Notes
- Unit tests don't need a device; parser/date-parser tests are pure JVM.
- Tests avoid blocking waits — use DirectExecutorService / FakeWorkManager doubles instead of real threading (commit 7197409).
- When adding a Room migration: bump DB version in `AppDatabase`, add MIGRATION_X_Y to `DbMigration.getAll()`, export schema JSON, and extend `DbMigrationTest` coverage accordingly.
- CI runs instrumented tests on emulators API 26/31/36 (API 23 dropped 2026-10) — keep tests compatible with minSdk 21+ behaviors.