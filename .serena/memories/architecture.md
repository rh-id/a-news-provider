# Architecture

Single-activity, component-based architecture. Every screen is a `StatefulView` page managed by **a-navigator**; all dependencies come from **a-provider** (Service Locator). Business logic lives in Command classes; async backbone is RxJava 3.

## Module Dependency Graph
```
:app ──> :base <── :component-network
```
- `:base` = foundation: Room DB, entities/DAOs, shared prefs, executors, logger, device-status notifier, `BaseProviderModule` + `DatabaseProviderModule`.
- `:component-network` = depends on `:base`: Volley stack + RSS parsing, `NetworkProviderModule`.
- `:app` = depends on both: UI pages, commands, workers, notifiers, DI root `AppProviderModule`.

## DI (a-provider)
Root: `Provider.createProvider(this, new AppProviderModule(this))` in `MainApplication.onCreate()` (`app/src/main/java/m/co/rh/id/a_news_provider/app/MainApplication.java`).

`AppProviderModule` (`app/.../app/provider/AppProviderModule.java`) registers, in order:
1. Nested: `BaseProviderModule`, `DatabaseProviderModule` (from `:base`), `NetworkProviderModule` (from `:component-network`), `CommandProviderModule`
2. Lazy: AppNotificationHandler, WorkManager, AppSharedPreferencesEventHandler, RssChangeNotifier, RssChannelStateNotifier, OpmlParser, RssRepository
3. `StatefulViewProvider` — pooled nested provider per StatefulView (`StatefulViewProviderModule` = DisposableHandler + CommandProviderModule + RxProviderModule), so page subscriptions auto-dispose on pop
4. `INavigator` last (registered as Application ActivityLifecycleCallbacks + ComponentCallbacks; disposed in `dispose()`)

Static access: `BaseApplication.of(context)` → `getProvider()`.

MainActivity also creates an **activity-scoped** provider (`new RxProviderModule()` → `RxDisposer`), disposed in `onDestroy()`.

## Navigation (a-navigator)
- Routes in `app/.../app/constants/Routes.java`: `SPLASH_PAGE="splash"`, `HOME_PAGE="/"`, `SETTINGS_PAGE="/settings"`, `DONATIONS_PAGE="/donations"`, `RSS_ITEM_DETAIL_PAGE="/rss/item/detail"`.
- Route→page factory map lives in `AppProviderModule.getNavigator()`; `SplashPage` is initial route and immediately navigates to `HOME_PAGE`.
- Back press → `OnBackPressedDispatcher` → `navigator.onBackPressed()`; `onActivityResult` forwarded to navigator.
- Day/night config change: `BehaviorSubject<Boolean>` debounced 100ms → `navigator.reBuildAllRoute()`.
- Intents (notification tap, share-target `SEND text/plain`, OPML `VIEW`) processed by `AppNotificationHandler.processNotification(intent)`.

## Command Pattern (business logic)
11 lazy-registered commands in `app/.../app/provider/command/` (registered by `CommandProviderModule`):
`RssQueryCmd`, `PagedRssItemsCmd`, `NewRssChannelCmd`, `RenameRssFeedCmd`, `EditRssLinkCmd`, `DeleteRssChannelCmd`, `UpdateRssItemIsReadCmd`, `UpdateRssItemIsFavoriteCmd`, `MarkAllReadCmd`, `SyncRssCmd` (enqueues WorkManager sync chain), `OpmlCmd` (import/export via `OpmlParser` + `OpmlParseWorker`).
UI retrieves command from Provider and executes; DB-threaded work runs on a shared WeightedThreadPool (max weight 5).

## Reactive Layer
- `RssChangeNotifier` (`app/.../app/provider/notifier/`) — Rx event hub: new/updated/deleted channels, synced items, updated items, mark-read events.
- `RssChannelStateNotifier` — selected channel + unread-count state.
- `AppSharedPreferences` (`base/.../base/AppSharedPreferences.java`) — exposes prefs (theme, sync interval, one-hand mode, download-image, showcase flags) as Rx flows via `SerialBehaviorSubject`.
- `DeviceStatusNotifier` (`base/.../base/provider/notifier/`) — ConnectivityManager callback → `BehaviorSubject<Boolean>` online state; UI shows SnackBar offline feedback.
- `MainApplication` installs `RxJavaPlugins.setErrorHandler` (logs undeliverable errors) + default uncaught-exception handler (logs, disposes provider).

## Background Work (WorkManager)
Unique work names in `ConstantsWork`; input keys in `ConstantsKey` (`app/.../app/workmanager/`).
- Manual sync: `RssSyncWorker` → `RssSyncChangeNotifierWorker` (unique, `ExistingWorkPolicy.KEEP`, NetworkType.CONNECTED).
- Periodic: `PeriodicRssSyncWorker` (1-24h) chains `RssSyncWorker` → `RssSyncNotificationWorker` (per-channel grouped notifications with unread counts) → `RssSyncChangeNotifierWorker`.
- New feed: one-time `NewRssWorker` (connectivity constraint); OPML import: one-time `OpmlParseWorker` (no constraint).
- WorkManager default initializer removed in AndroidManifest (`tools:node="remove"` on androidx.startup.InitializationProvider); `MainApplication` implements `WorkManager.Configuration.Provider` reusing the provider's ScheduledExecutorService.

## Data Flow (sync example)
User/Scheduler → Command (`SyncRssCmd`) → WorkManager chain → `RssSyncWorker` fetches feed (Volley `RssRequest` → `RssFeedParser` → `RssModel`) → `RssRepository` persists via Room DAOs → notifier workers emit events → `RssChangeNotifier` → UI (StatefulViews) update via Rx subscriptions.
