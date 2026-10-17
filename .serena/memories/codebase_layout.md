# Codebase Layout (key classes and paths)

Base packages: `m.co.rh.id.a_news_provider.app` (in `app/`), `m.co.rh.id.a_news_provider.base` (in `base/`), `m.co.rh.id.a_news_provider.component.network` (in `component-network/`).

## :app module — `app/src/main/java/m/co/rh/id/a_news_provider/app/`
- **Root:** `MainApplication.java` (extends `BaseApplication`, WorkManager config, Rx error handler, crash handler), `MainActivity.java` (singleTop launcher; activity-scoped RxProviderModule; back/nav/intent delegation; forwards `onRequestPermissionsResult` to navigator; applies Material You dynamic color via force-applied `ThemeOverlay.Material3.DynamicColors.DayNight` when enabled).
- **`component/`:** `AppNotificationHandler` (notification intents, posts sync notifications).
- **`constants/`:** `Routes` (route strings: SPLASH_PAGE="splash", HOME_PAGE="/", SETTINGS_PAGE="/settings", DONATIONS_PAGE="/donations", RSS_ITEM_DETAIL_PAGE="/rss/item/detail", SEARCH_RSS_PAGE="/rss/search"), `Shortcuts` (app shortcut action `BuildConfig.APPLICATION_ID + ".shortcut.new_rss_channel"`).
- **`provider/`:** `AppProviderModule` (DI root + navigator config), `CommandProviderModule`, `RxProviderModule`, `StatefulViewProviderModule`, `StatefulViewProvider` (nested per-page provider).
  - **`provider/command/`:** 14 commands (see `architecture` memory) + `BaseRssItemsCmd` (shared base) + `RedirectDuplicateChecker` (duplicate/redirect detection on add-feed).
  - **`provider/service/`:** `RssService` — non-UI add-feed core shared by `NewRssChannelCmd` and `OpmlParser` (extracted to decouple OPML import from UI).
  - **`provider/event/`:** `AppSharedPreferencesEventHandler`.
  - **`provider/notifier/`:** `RssChangeNotifier`, `RssChannelStateNotifier`.
  - **`provider/parser/`:** `OpmlParser` (OPML import/export).
  - **`provider/repository/`:** `RssRepository` — data facade over DAOs (lives in app module, NOT base).
- **`receiver/`:** `NotificationDeleteReceiver`.
- **`rx/`:** `RxDisposer` (disposable bag).
- **`ui/model/`:** `RssItemModel` (list item UI model).
- **`ui/page/`:** `SplashPage`, `HomePage`, `SettingsPage`, `DonationsPage`, `RssItemDetailPage` (next/prev article navigation: toolbar actions + 50dp edge-swipe strips, replace()s detail route), `SearchRssPage` (full-text item search).
- **`ui/component/`:** `AppBarSV` (shared app bar); **`ui/component/rss/`:** `RssChannelListSV`, `RssChannelItemSV`, `RssItemSV`, `RssItemListSV`, `SearchRssItemListSV`, `RssItemsProvider` (shared item-list contract), `RssChannelRecyclerViewAdapter`, `RssItemRecyclerViewAdapter`, dialogs `NewRssChannelSVDialog`, `EditRssLinkSVDialog`; **`ui/component/settings/`:** `ThemeMenuSV` (theme + dynamic color toggle), `RssSyncMenuSV`, `OneHandModeMenuSV`, `DownloadImageMenuSV`, `SwipeArticleNavigationMenuSV`, `MarkReadOnScrollMenuSV`, `AutoMarkReadMenuSV`, `LicensesMenuSV`, `LogMenuSV`, `VersionMenuSV`, `LogLineRecyclerViewAdapter`, plus pages `LicensesPage`, `LogPage` (kept in settings pkg).
- **`util/`:** `UiUtils`, `NotificationPermissionPolicy` (POST_NOTIFICATIONS runtime-permission decision, Android 13+), `OpenLinkPolicy` (pure decision logic for the "Open links in" setting), `CustomTabsUrlSpan` (URLSpan subclass routing link taps through `UiUtils.openLink`).
- **`workmanager/`:** `RssSyncWorker`, `RssSyncNotificationWorker`, `RssSyncChangeNotifierWorker`, `PeriodicRssSyncWorker`, `NewRssWorker`, `OpmlParseWorker`, `ConstantsWork` (unique names), `ConstantsKey` (input keys: `KEY_STRING_URL`, `KEY_LONG_CHANNEL_IDS`, `KEY_FILE_ABSOLUTE_PATH`).
- Manifest: `FileProvider` authority `m.co.rh.id.a_news_provider.fileprovider`; permissions INTERNET, POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE (maxSdk 28). Room schema export → `app/schemas`.
- **Tests:** unit (`app/src/test`): `provider/notifier/RssChangeNotifierTest`, `provider/command/SearchRssItemsCmdTest`, `provider/command/BaseRssItemsCmdTest`, `provider/command/PagedRssItemsCmdTest`, `provider/command/RedirectDuplicateCheckerTest`, `provider/command/MarkItemsReadOnScrollCmdTest`, `base/util/UrlNormalizerTest`, `app/util/NotificationPermissionPolicyTest`, `app/util/OpenLinkPolicyTest`, `ExampleUnitTest`. Instrumented (`app/src/androidTest`): `AppPageTest`, `AppSVTest`, `AccessibilityAuditTest`, `OpmlParserTest`, `base/AppSharedPreferencesTest`, `app/provider/command/PauseRssChannelCmdTest`, `app/provider/command/NewRssChannelCmdTest`, `app/provider/repository/RssRepositoryTest` (in-memory Room, incl. next/prev neighbor queries), `app/provider/service/RssServiceTest`; infra `test/{TestApplication, AppAndroidJunit4Runner, TestPage, NoOpLogger, FakeWorkManager}.java`, `test/util/{ProviderDbRule, RecordingRedirectResolver}.java`, `provider/IntegrationTestAppProviderModule.java`. Shared doubles in `app/src/sharedTest/java/.../test/util/DirectExecutorService.java` (compiled into BOTH test source sets).
- **`app/licenses.yml`** + `app/src/main/assets/licenses.html` (generated) feed the Licenses screen.

## :base module — `base/src/main/java/m/co/rh/id/a_news_provider/base/`
- **Root:** `AppDatabase.java` (RoomDatabase v11, entities {RssChannel, RssItem, AndroidNotification}, `rssDao()` + `androidNotificationDao()`), `AppSharedPreferences.java` (Rx-backed prefs via SerialBehaviorSubject), `BaseApplication.java` (abstract: `getProvider()`/`getNavigator(Activity)`, static `of(context)`).
- **`room/`:** `DbMigration.java` (static `getAll()` → MIGRATION_1_2 … through 10_11; includes pub_date, image_url, android_notification reset, media_image/media_video, channel_id index, is_favorite, 8→9 duplicate legacy/canonical channel merge), **`room/converter/Converter.java`** (Date↔Long @TypeConverter).
- **`entity/`:** `RssChannel.java` (table `rss_channel`), `RssItem.java` (table `rss_item`, channel_id index), `AndroidNotification.java` (table `android_notification`).
- **`dao/`:** `RssDao.java` (channel/item CRUD, transactions, unread counts, keyset `findRssItemsPageDesc/Asc` + `searchRssItemsPageDesc/Asc` with channel/read/favorite/pause filters and nullable cursor (null = first page, page size 100 owned by `BaseRssItemsCmd`), `markAllRssItemsRead`, by-link read/favorite sync, compound-key next/prev neighbor anchor queries using `COALESCE(pub_date, created_date_time)` with `id` tiebreaker (also used as `anchorId`)), `AndroidNotificationDao.java` (requestId lookup, count-based id rotation).
- **`model/`:** `RssModel.java` (RssChannel + ArrayList<RssItem>; parser output), `ChannelUnreadCount.java` (query projection).
- **`provider/`:** `BaseProviderModule` (ExecutorService WeightedThreadPool max 5, ScheduledExecutorService, DisposableHandler, ILogger=CompositeLogger[Android+File+Toast], FileHelper, DeviceStatusNotifier, AppSharedPreferences), `DatabaseProviderModule` (async AppDatabase + RssDao/AndroidNotificationDao), `DisposableHandler.java`, `FileHelper.java` (log file `cacheDir/alogger/app.log`, temp root `cacheDir/tmp`), **`provider/notifier/DeviceStatusNotifier.java`**.
- **`util/`:** `UrlNormalizer.java` (canonical feed-URL form for duplicate detection; unit-tested from :app test source set).
- **`ui/`:** `SwipeGestureDetector.java`.
- Room schema exports: `base/schemas/m.co.rh.id.a_news_provider.base.AppDatabase/1.json` … `11.json` (also used as androidTest assets).
- Tests: no unit tests; `base/src/androidTest/.../DbMigrationTest.java` (MigrationTestHelper: migrateAll 1→11 + dedicated migrate8to9 duplicate-merge data assertions).

## :component-network module — `component-network/src/main/java/m/co/rh/id/a_news_provider/component/network/`
- `network/RssRequest.java` (Volley `Request<RssModel>`; charset decode → parser; ParseError/VolleyError), `network/RssRequestFactory.java`.
- `network/parser/RssFeedParser.java` (XmlPullParser; root-tag dispatch rss/feed/rdf:RDF; media:content, media:thumbnail, enclosure → mediaImage/mediaVideo), `network/parser/RssDateParser.java` (package-private; RFC-822 + ISO-8601), `network/parser/RssMedia.java` (package-private POJO; TYPE_IMAGE/TYPE_VIDEO).
- `network/provider/NetworkProviderModule.java` (BaseHttpStack=HurlStack, BasicNetwork, DiskBasedCache 20MB `cacheDir/volley`, DisposableRequestQueue, ImageLoader LruCache 20, RssRequestFactory, RssFeedParser), `network/provider/volley/DisposableRequestQueue.java`.
- Res: localized strings only (e.g. `unable_to_parse`).
- Tests: `component-network/src/test/.../parser/RssFeedParserTest.java` (19 tests), `parser/RssDateParserTest.java` (21 tests; needs kxml2 on JVM), `ExampleUnitTest.java`.