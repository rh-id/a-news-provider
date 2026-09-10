# Codebase Layout (key classes and paths)

Base packages: `m.co.rh.id.a_news_provider.app` (in `app/`), `m.co.rh.id.a_news_provider.base` (in `base/`), `m.co.rh.id.a_news_provider.component.network` (in `component-network/`).

## :app module — `app/src/main/java/m/co/rh/id/a_news_provider/app/`
- **Root:** `MainApplication.java` (extends `BaseApplication`, WorkManager config, Rx error handler, crash handler), `MainActivity.java` (singleTop launcher; activity-scoped RxProviderModule; back/nav/intent delegation).
- **`component/`:** `AppNotificationHandler` (notification intents, posts sync notifications).
- **`constants/`:** `Routes` (route strings), `Shortcuts` (app shortcut action `BuildConfig.APPLICATION_ID + ".shortcut.new_rss_channel"`).
- **`provider/`:** `AppProviderModule` (DI root + navigator config), `CommandProviderModule`, `RxProviderModule`, `StatefulViewProviderModule`, `StatefulViewProvider` (nested per-page provider).
  - **`provider/command/`:** 11 commands (see `architecture` memory).
  - **`provider/event/`:** `AppSharedPreferencesEventHandler`.
  - **`provider/notifier/`:** `RssChangeNotifier`, `RssChannelStateNotifier`.
  - **`provider/parser/`:** `OpmlParser` (OPML import/export).
  - **`provider/repository/`:** `RssRepository` — data facade over DAOs (lives in app module, NOT base).
- **`receiver/`:** `NotificationDeleteReceiver`.
- **`rx/`:** `RxDisposer` (disposable bag).
- **`ui/model/`:** `RssItemModel` (list item UI model).
- **`ui/page/`:** `SplashPage`, `HomePage`, `SettingsPage`, `DonationsPage`, `RssItemDetailPage`; under settings: `LicensesPage`, `LogPage`.
- **`ui/component/`:** `AppBarSV` (shared app bar); **`ui/component/rss/`:** `RssChannelListSV`, `RssChannelItemSV`, `RssItemSV`, `RssItemListSV`, `RssChannelRecyclerViewAdapter`, `RssItemRecyclerViewAdapter`, dialogs `NewRssChannelSVDialog`, `EditRssLinkSVDialog`; **`ui/component/settings/`:** `ThemeMenuSV`, `RssSyncMenuSV`, `OneHandModeMenuSV`, `DownloadImageMenuSV`, `LicensesMenuSV`, `LogMenuSV`, `VersionMenuSV`, `LogLineRecyclerViewAdapter`.
- **`workmanager/`:** `RssSyncWorker`, `RssSyncNotificationWorker`, `RssSyncChangeNotifierWorker`, `PeriodicRssSyncWorker`, `NewRssWorker`, `OpmlParseWorker`, `ConstantsWork` (unique names), `ConstantsKey` (input keys: `KEY_STRING_URL`, `KEY_LONG_CHANNEL_IDS`, `KEY_FILE_ABSOLUTE_PATH`).
- **`util/`:** `UiUtils`.
- Manifest: `FileProvider` authority `m.co.rh.id.a_news_provider.fileprovider`; permissions INTERNET, POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE (maxSdk 28). Room schema export → `app/schemas`.
- Tests: `app/src/test/.../provider/notifier/RssChangeNotifierTest.java`, `RssChannelStateNotifierTest.java`, `provider/repository/RssRepositoryTest.java`; `app/src/androidTest/.../AppPageTest.java`, `AppSvTest.java`, `provider/IntegrationTestAppProviderModule.java`, `test/{TestApplication, AppAndroidJunit4Runner, TestPage}.java`.

## :base module — `base/src/main/java/m/co/rh/id/a_news_provider/base/`
- **Root:** `AppDatabase.java` (RoomDatabase v8, entities {RssChannel, RssItem, AndroidNotification}, `rssDao()` + `androidNotificationDao()`), `AppSharedPreferences.java` (Rx-backed prefs), `BaseApplication.java` (abstract: `getProvider()`/`getNavigator(Activity)`, static `of(context)`).
- **`room/`:** `DbMigration.java` (static `getAll()` → MIGRATION_1_2 … MIGRATION_7_8: pub_date column, image_url, android_notification reset, media_image/media_video, channel_id index, is_favorite), **`room/converter/Converter.java`** (Date↔Long @TypeConverter).
- **`entity/`:** `RssChannel.java` (table `rss_channel`), `RssItem.java` (table `rss_item`, channel_id index), `AndroidNotification.java` (table `android_notification`).
- **`dao/`:** `RssDao.java` (channel/item CRUD, transactions, unread counts, paged `findRssItemsWithLimit/Asc` with channel/read/favorite filters, `markAllRssItemsRead`, by-link read/favorite sync), `AndroidNotificationDao.java` (requestId lookup, count-based id rotation).
- **`model/`:** `RssModel.java` (RssChannel + ArrayList<RssItem>; parser output), `ChannelUnreadCount.java` (query projection).
- **`provider/`:** `BaseProviderModule` (ExecutorService WeightedThreadPool max 5, ScheduledExecutorService, DisposableHandler, ILogger=CompositeLogger[Android+File+Toast], FileHelper, DeviceStatusNotifier, AppSharedPreferences), `DatabaseProviderModule` (async AppDatabase + RssDao/AndroidNotificationDao), `DisposableHandler.java`, `FileHelper.java` (log file `cacheDir/alogger/app.log`, temp root `cacheDir/tmp`), **`provider/notifier/DeviceStatusNotifier.java`**.
- **`ui/`:** `SwipeGestureDetector.java`.
- Room schema exports: `base/schemas/m.co.rh.id.a_news_provider.base.AppDatabase/1.json` … `8.json` (also used as androidTest assets).
- Tests: no unit tests; `base/src/androidTest/.../DbMigrationTest.java` (MigrationTestHelper v1→v8).

## :component-network module — `component-network/src/main/java/m/co/rh/id/a_news_provider/component/network/`
- `network/RssRequest.java` (Volley `Request<RssModel>`; charset decode → parser; ParseError/VolleyError), `network/RssRequestFactory.java`.
- `network/parser/RssFeedParser.java` (XmlPullParser; root-tag dispatch rss/feed/rdf:RDF; media:content, media:thumbnail, enclosure → mediaImage/mediaVideo), `network/parser/RssDateParser.java` (package-private; RFC-822 + ISO-8601), `network/parser/RssMedia.java` (package-private POJO; TYPE_IMAGE/TYPE_VIDEO).
- `network/provider/NetworkProviderModule.java` (BaseHttpStack=HurlStack, BasicNetwork, DiskBasedCache 20MB `cacheDir/volley`, DisposableRequestQueue, ImageLoader LruCache 20, RssRequestFactory, RssFeedParser), `network/provider/volley/DisposableRequestQueue.java`.
- Res: localized strings only (e.g. `unable_to_parse`).
- Tests: `component-network/src/test/.../parser/RssFeedParserTest.java` (12 tests), `parser/RssDateParserTest.java` (~18 tests; needs kxml2 on JVM), `ExampleUnitTest.java`.
