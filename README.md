# a-news-provider

![Languages](https://img.shields.io/github/languages/top/rh-id/a-news-provider)
![Downloads](https://img.shields.io/github/downloads/rh-id/a-news-provider/total)
![GitHub release (by tag)](https://img.shields.io/github/downloads/rh-id/a-news-provider/latest/total)
![Release](https://img.shields.io/github/v/release/rh-id/a-news-provider)
![Android CI](https://github.com/rh-id/a-news-provider/actions/workflows/gradlew-build.yml/badge.svg)
![Release Build](https://github.com/rh-id/a-news-provider/actions/workflows/android-release.yml/badge.svg)
![Emulator Test](https://github.com/rh-id/a-news-provider/actions/workflows/android-emulator-test.yml/badge.svg)

A simple and easy to use RSS aggregator that deliver news to your smartphone.

**For Users:**
<ul>
  <li>Easily add new feed using the home screen shortcut</li>
  <li>Add a feed by sharing a link from your browser or other apps</li>
  <li>Get a warning when adding a feed that is already subscribed</li>
  <li>Open OPML files directly from your file manager, in addition to importing and exporting feeds via OPML</li>
  <li>Support RSS 2.0, Atom, and RDF (RSS 1.0) feed formats, including <code>media:content</code>, <code>media:thumbnail</code>, and <code>enclosure</code> elements for attached images and videos</li>
  <li>Download attached videos to <code>Downloads/&lt;feedName&gt;/</code> via the system download manager; attached images are displayed in the item detail when the "download image" setting is enabled</li>
  <li>Star items as favorites and filter the list by All / Unread / Read / Favorites</li>
  <li>Navigate to the next or previous article from the item detail screen — via toolbar actions or edge swipe (on by default, togglable in Settings)</li>
  <li>Search all feed items by title, description, or category from the home screen search menu — feed-provided categories (RSS 2.0 <code>&lt;category&gt;</code>, Atom <code>&lt;category&gt;</code>, RDF <code>dc:subject</code>) are shown as tappable chips on each item that open a pre-filled search</li>
  <li>Sort items by newest or oldest, and mark all items as read (globally or per channel)</li>
  <li>Configurable periodic background sync with a 1-24 hours interval (default: every 6 hours, can be toggled off)</li>
  <li>Optional unread retention window: auto mark items as read after 7/14/30/90 days at the end of each sync (off by default, favorites are never auto-read)</li>
  <li>Feed sync keeps your history: items that drop out of a feed's current list stay in the app (with their read/favorite state) instead of being deleted on the next sync, and sync notifications reflect the latest feed content; optional keep-read-items window auto-deletes read, non-favorite items that no longer appear in their feed after 7/14/30/90 days (off by default; unread items and favorites are never auto-deleted; applies to paused feeds too)</li>
  <li>Opt-in "mark as read on scroll" setting: items scrolled past in the list are marked as read automatically (off by default)</li>
  <li>Per-channel grouped sync notifications with unread counts (requires the Notifications permission on Android 13+)</li>
  <li>Rename, pause (mute), or delete feeds — paused feeds keep their history but stop syncing and notifying (their unread counts may still decrease via the optional auto mark-read setting, and read items may be pruned via the optional keep-read-items setting)</li>
  <li>Support editing feed item link</li>
  <li>Copy feed item links and channel links to clipboard</li>
  <li>"Open links in" setting: default browser (default) or browser Custom Tabs (opt-in, falls back to the default browser when Custom Tabs isn't supported)</li>
  <li>Offline/online detection with SnackBar feedback; syncs run automatically once the device is back online</li>
  <li>Simple interface to add and read your news</li>
  <li>Theme selection: follow system, light, or dark mode, plus Material You dynamic color on Android 12 and above (on by default)</li>
  <li>Accessibility friendly with one hand mode (bottom-anchored layouts)</li>
  <li>Accessibility friendly on screen reader (audited with TalkBack and Accessibility Scanner on 2026-10-03, see Accessibility notes below)</li>
  <li>View the in-app log file (share and clear supported)</li>
  <li>Open-source licenses screen</li>
  <li>Available in 11 languages: English, Indonesian, German, Estonian, French, Icelandic, Italian, Chinese (Simplified), Norwegian (Bokmål &amp; Nynorsk), and Romansh</li>
  <li>Support Android 5.0 (API 21) and above</li>
</ul>

This project is intended for demo app for [a-navigator](https://github.com/rh-id/a-navigator) and [a-provider](https://github.com/rh-id/a-provider) library usage.
The app still works as production even though it is demo app.

## Architecture

This is a single-activity application built using a component-based architecture leveraging specific libraries for navigation and dependency injection. Every screen is a page managed by [a-navigator](https://github.com/rh-id/a-navigator), and all dependencies are provided by [a-provider](https://github.com/rh-id/a-provider).

### Core Libraries

*   **[a-navigator](https://github.com/rh-id/a-navigator):** Handles the UI navigation and lifecycle.
    *   **StatefulView:** Instead of Fragments or Activities for every screen, the app uses `StatefulView`. This allows for a more flexible and lightweight view hierarchy.
    *   **INavigator:** The interface used to push/replace/pop pages and manage the navigation stack (e.g., `mNavigator.push(Routes.SETTINGS_PAGE)`). Routes are simple path strings (e.g., `HOME_PAGE = "/"`, `SETTINGS_PAGE = "/settings"`).
    *   **Scoped providers:** Each `StatefulView` receives its own scoped `StatefulViewProvider` with its own commands and `RxDisposer`, so page state and subscriptions are disposed automatically when the page is popped.
*   **[a-provider](https://github.com/rh-id/a-provider):** A Service Locator library for Dependency Injection.
    *   **Provider:** Central registry for dependencies.
    *   **Modules:** Dependencies are organized in modules: `AppProviderModule`, `BaseProviderModule`, `DatabaseProviderModule`, `NetworkProviderModule`, `CommandProviderModule`, `RxProviderModule`, and `StatefulViewProviderModule`. UI components implement `RequireComponent<Provider>` to access these dependencies.

### Command Pattern

Business logic is encapsulated using the **Command Pattern**. This decouples the UI from the execution logic.
*   **Commands:** The app currently has 14 commands: `PagedRssItemsCmd`, `NewRssChannelCmd`, `RenameRssFeedCmd`, `SyncRssCmd`, `RssQueryCmd`, `EditRssLinkCmd`, `UpdateRssItemIsReadCmd`, `UpdateRssItemIsFavoriteCmd`, `DeleteRssChannelCmd`, `OpmlCmd`, `MarkAllReadCmd`, `SearchRssItemsCmd`, `PauseRssChannelCmd`, and `MarkItemsReadOnScrollCmd`.
*   **Execution:** The UI retrieves a command instance via `a-provider` and executes it. The add-feed core shared by `NewRssChannelCmd` and `OpmlCmd` lives in a non-UI `RssService`, keeping OPML import decoupled from UI classes.
*   **Threading:** Commands run on a shared weighted thread pool (max weight 5); network work is offloaded to WorkManager.
*   **Benefit:** Keeps UI classes (StatefulViews) clean and testable.

### Reactive Programming

*   **RxJava 3 / RxAndroid:** Used heavily for asynchronous operations and event handling.
*   **Notifiers:** Components like `RssChangeNotifier` (an event hub for new/updated/deleted channels, synced items, updated items, and mark-read events) and `RssChannelStateNotifier` (selected channel and unread-count state) use Rx subjects to emit updates to the UI, while `RssRepository` persists feed data to the local database.
*   **Preferences:** `AppSharedPreferences` exposes user preferences as Rx flows so UI screens react to setting changes.

### Background Work (WorkManager)

Sync operations are executed as WorkManager chains with a `NetworkType.CONNECTED` constraint:

*   **Manual sync:** unique work `RssSyncWorker` chained to `RssSyncChangeNotifierWorker` (enqueued with `ExistingWorkPolicy.KEEP` to avoid duplicate syncs).
*   **Periodic sync:** unique periodic `PeriodicRssSyncWorker` chaining `RssSyncWorker` → `RssSyncNotificationWorker` → `RssSyncChangeNotifierWorker`. The notification worker posts the per-channel grouped notifications with unread counts.
*   **New feed / OPML import:** a one-time `NewRssWorker` job with the connectivity constraint, and a one-time `OpmlParseWorker` job that parses the local OPML file (no constraint).

### Persistence (Room)

*   Room database `a-news-provider.db` (version 11, with migrations from version 1 to 11). Entities: `RssChannel`, `RssItem`, and `AndroidNotification`.
*   Read and favorite state is synced across channels by item link, so the same article shared by multiple feeds keeps its state.
*   Feed lists are paginated with keyset (cursor) queries at a page size of 100 — each page continues strictly after the last loaded row's `(COALESCE(pub_date, created_date_time), id)` position, so page boundaries stay stable under concurrent inserts and there are no LIMIT-offset duplicates or skips (still no androidx.paging).

### Networking (Volley)

*   Feed downloads use a Volley `RequestQueue` (HurlStack) with a 20 MiB disk cache, plus an `ImageLoader` with a 20-entry memory LruCache for feed icons and images.
*   `RssRequest` parses responses via the XmlPullParser-based `RssFeedParser`, which dispatches on the root tag to support RSS 2.0, Atom, and RDF (RSS 1.0), including `media:content`, `media:thumbnail`, and `enclosure` elements.
*   Publication dates are parsed best-effort from common RFC-822 and ISO-8601 formats.

## Logic Flow

The following diagram illustrates the typical data flow when a user interacts with the app (e.g., refreshing the feed):

```mermaid
graph TD
    User[User Interaction] -->|Trigger| UI[StatefulView UI]
    UI -->|Execute| Cmd[Command e.g., SyncRssCmd]
    Cmd -->|Enqueue unique work| Sync[RssSyncWorker]
    Periodic[PeriodicRssSyncWorker 1-24 h] -->|Chained| Sync
    Sync -->|Fetch| Network[RSS Feed URL]
    Sync -->|Parse & Save| DB[(Local Database Room)]
    Sync -->|Chained| Notif[RssSyncNotificationWorker]
    Notif -->|Chained| Change[RssSyncChangeNotifierWorker]
    Change -->|Emit Sync Event| Notifier[RssChangeNotifier]
    Notifier -->|Emit Event| UI
    UI -->|Update View| Display[User Display]
```

1.  **Action:** User triggers an action (e.g., clicks "Sync"), or the periodic `PeriodicRssSyncWorker` fires on schedule.
2.  **Command:** The UI invokes the corresponding Command.
3.  **Background Work:** The Command enqueues WorkManager work (constrained to `NetworkType.CONNECTED`); other work runs on the app's weighted thread pool.
4.  **Data Operation:** The feed is fetched from the network, parsed, and saved to the local Room database.
5.  **Notification:** The chained workers post sync notifications and emit events through `RssChangeNotifier`.
6.  **UI Update:** The UI, listening to the `Notifier` via RxJava, updates the display.

## Development & CI/CD

### Building Locally

This project is a standard multi-module Gradle project (Gradle 9.8.0 wrapper, Android Gradle Plugin 9.4.1, Groovy DSL with a version catalog in `gradle/libs.versions.toml`) with modules: `app`, `base`, and `component-network`.

**Requirements:**
- JDK 17 (Java 17 toolchain with core library desugaring)
- Android SDK (compileSdk 37)

**Commands:**
- `./gradlew build` - Build the project and run unit tests
- `./gradlew test` - Run unit tests only
- `./gradlew connectedCheck` - Run instrumented tests (requires emulator or connected device)

**Release signing:** Release builds are signed when the `SIGNING_KEY` (base64-encoded keystore), `KEY_STORE_PASSWORD`, `ALIAS`, and `KEY_PASSWORD` environment variables are set; otherwise the release build is unsigned.

**Licenses screen:** the open-source licenses content is generated by the project's own `gradle/license-html-generator.gradle` script (task `generateLicenseHtml` in `:app`, which runs automatically before `preBuild`); license name/url overrides live in `app/licenses.yml`.

### Testing

*   **Unit tests:** cover the notifiers, repository, the RSS/Atom/RDF feed parser, and the date parser, using JUnit 4 (Mockito in `:app`, hand-written stubs in `:component-network`).
*   **Instrumented tests:** page smoke tests via Espresso, command/repository/OPML/preferences tests against an in-memory Room database, accessibility audit tests, and a database migration test covering versions 1 through 11 (plus a dedicated 8→9 duplicate-merge case) using Room's `MigrationTestHelper`. Shared test doubles live in `app/src/sharedTest`, which is compiled into both the unit-test and instrumented-test source sets.

### Accessibility

*   Screen reader support was audited with TalkBack and Accessibility Scanner on 2026-10-03: feed rows announce read/unread state (state descriptions on API 30+, a spoken fallback on older versions); feed rows expose custom TalkBack actions (mark as read/unread, add/remove favorites) and drawer feed rows expose edit/rename/delete/open link/mark all read, and pause/unpause actions added on 2026-10-07 (to be covered by the next audit, checklist on issue #87); the list announces the number of new items after a sync; icon touch targets are at least 48dp.
*   The swipe article navigation added on 2026-10-10 ships with toolbar next/previous actions as its non-gesture equivalent, satisfying the swipe-action rule below.
*   **Contributors:** any future swipe-action feature must land its `AccessibilityActionCompat` equivalent in the same PR.
*   **Contributors:** the manual TalkBack/Accessibility Scanner audit checklist is recorded on issue #87.

### GitHub Workflows

The project uses GitHub Actions for CI/CD:

*   **Android CI (`gradlew-build.yml`):**
    *   Triggered on push/pull request to `master`.
    *   Sets up JDK 17 and runs `./gradlew build` to ensure code integrity.
*   **Android Release APKs (`android-release.yml`):**
    *   Triggered when a tag starting with `v*` is pushed.
    *   Builds Debug and Release APKs, signed using repository secrets (`SIGNING_KEY` base64 keystore plus passwords).
    *   Creates a GitHub Release and attaches `app-debug.apk`, `app-release.apk`, and `changelog.txt`. The release body is taken from `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, which the Gradle build copies to `app/build/changelog.txt`.
*   **Android Emulator Test (`android-emulator-test.yml`):**
    *   Triggered on push/pull request to `master`.
    *   Runs `./gradlew connectedCheck` on an emulator matrix (API levels 26, 31, and 36, fail-fast disabled) with KVM enabled and AVD caching. The workflow wakes the screen, dismisses the keyguard, and runs the API 36 emulator as a cold boot.

### Fastlane

The `fastlane/` directory contains store metadata (descriptions, images, and per-version changelogs) for all 11 locales. While the release process currently uses Gradle directly in GitHub Actions, the Fastlane structure is maintained for store deployment and metadata management.

## Project Structure

*   `app/`: Main application module containing UI (StatefulViews), Commands, the non-UI `RssService`, DI configuration, WorkManager workers, and notifications.
*   `base/`: Room database, entities, DAOs, shared preferences, base provider modules, and logger/file/network-status helpers. Exported Room schemas (`base/schemas/m.co.rh.id.a_news_provider.base.AppDatabase/1.json` - `11.json`) live here.
*   `component-network/`: Volley-based `RssRequest` and the RSS/Atom/RDF feed parsers.
*   `fastlane/`: Store metadata for 11 locales and per-version changelogs.
*   `.github/`: GitHub Actions workflow configurations.

## Screenshots
<img src="https://github.com/rh-id/a-news-provider/blob/master/fastlane/metadata/android/en-US/images/featureGraphic.png" width="1024"/>

<img src="https://github.com/rh-id/a-news-provider/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="512"/>
<img src="https://github.com/rh-id/a-news-provider/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" height="512"/>

## Support this project
Consider donation to support this project
<table>
  <tr>
    <td><a href="https://trakteer.id/rh-id">https://trakteer.id/rh-id</a></td>
  </tr>
</table>
