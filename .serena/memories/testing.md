# Testing

## Frameworks
- Unit tests: JUnit 4 (4.13.2). Mockito 5.21.0 used in `:app` tests only; `:component-network` tests use hand-written anonymous stubs (no mocking framework).
- Instrumented: AndroidJUnit4 + Espresso 3.7.0, androidx.test.ext:junit 1.3.0, Room `room-testing` 2.7.2.
- kxml2 2.3.0 is a test-only dep of `:component-network` (XmlPullParser implementation for local JVM tests).

## Unit Tests (`gradlew test`)
- **:app** (`app/src/test/java/m/co/rh/id/a_news_provider/app/`):
  - `provider/repository/RssRepositoryTest.java` — Mockito (mock `RssDao`/`Provider`, ArgumentCaptor).
  - `provider/notifier/RssChangeNotifierTest.java`, `provider/notifier/RssChannelStateNotifierTest.java` — Rx notifier behavior.
- **:component-network** (`component-network/src/test/java/m/co/rh/id/a_news_provider/component/network/`):
  - `parser/RssFeedParserTest.java` — 12 tests: RSS 2.0 / Atom / RDF parsing, enclosure image/video, media:content (image/video/medium-video), image+thumbnail, unknown root tag, malformed XML, empty feed, rss-without-channel. Uses silent anonymous `ILogger` stub and anonymous `Provider` stub.
  - `parser/RssDateParserTest.java` — ~18 tests: RFC-822 variants (timezones, missing seconds/tz, day-first), ISO-8601 (Z, offset, millis, date-only), invalid inputs, logger-called-on-failure.
- **:base** — no unit tests.

## Instrumented Tests (`gradlew connectedCheck`)
- **:app** (`app/src/androidTest/java/m/co/rh/id/a_news_provider/`):
  - `AppSvTest.java`, `AppPageTest.java` — page smoke tests (Espresso).
  - `test/TestApplication.java`, `test/AppAndroidJunit4Runner.java`, `test/TestPage.java` — test infra.
  - `provider/IntegrationTestAppProviderModule.java` — replica of `AppProviderModule` for tests: no Navigator, custom test DB name, everything `registerLazy`.
- **:base**: `DbMigrationTest.java` — Room `MigrationTestHelper` + `FrameworkSQLiteOpenHelperFactory`; single test `migrateAll()` creates DB at version 1 and validates through all migrations to v8 (consumes `base/schemas/*.json` as androidTest assets).
- **:component-network**: template `ExampleInstrumentedTest.java` only.

## Notes
- Unit tests don't need a device; parser/date-parser tests are pure JVM.
- When adding a Room migration: bump DB version in `AppDatabase`, add MIGRATION_X_Y to `DbMigration.getAll()`, export schema JSON, and extend `DbMigrationTest` coverage accordingly.
- CI runs instrumented tests on emulators API 23/26/31/36 — keep tests compatible with minSdk 21+ behaviors.
