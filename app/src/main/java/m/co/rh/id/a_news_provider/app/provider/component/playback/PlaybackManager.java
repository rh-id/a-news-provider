package m.co.rh.id.a_news_provider.app.provider.component.playback;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.Observable;
import m.co.rh.id.a_news_provider.app.provider.component.service.PlaybackService;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * App-scoped playback controller (issue #69): owns the ONE MediaController
 * connection to {@link PlaybackService} and exposes the playback state as an
 * Rx stream. Extracted from the page-scoped Phase-1 mechanics of
 * RssItemDetailPage so playback now OUTLIVES the page that started it - the
 * mini-player bar and picture-in-picture observe the same session while the
 * user browses other articles.
 * <p>
 * State machine (pushed through {@link #state()}):
 * <ul>
 * <li>CLEARED (item=null) - nothing loaded. The first {@link #togglePlay}
 * queues the tap (FULL args, see R8 below) and connects asynchronously
 * (connecting is an internal transient: the tap stays queued until real
 * player state is visible, then runs).</li>
 * <li>playing / paused / buffering - the loaded media plays or waits; the
 * same-URL toggle pauses or resumes IN PLACE (position kept, mediaId
 * identity works across page reopens and notification pauses).</li>
 * <li>ENDED - released entirely (see the release policy below); the next
 * play rebuilds the connection. A tap racing the teardown is queued and then
 * DROPPED by {@code cancelPendingTap()} inside {@code releasePlayback()} -
 * only the NEXT tap reconnects and restarts the media.</li>
 * <li>failed - a fatal SOURCE error left the player IDLE. The controller is
 * KEPT (never released on IDLE): the next same-URL tap re-prepares, a
 * different URL loads fresh.</li>
 * <li>{@link #stop()} tears the session down THROUGH media3 - it is the
 * mini-player's close button: the player goes IDLE (media3 drops the media
 * notification + foreground), the session-side
 * {@code pauseAllPlayersAndStopSelf()} runs (the ONLY teardown that also
 * destroys media3's internal session-side controller - that orphan re-creates
 * a service stopped app-side), the connection is released and an app-side
 * stopService runs as a belt-and-braces no-op; the next play rebuilds
 * everything. Releasing a MediaController alone NEVER stops the session
 * player.</li>
 * </ul>
 * Release policy (R3): release ONLY on {@code STATE_ENDED} and on explicit
 * {@link #stop()}/{@link #dispose(Context)}. NEVER on {@code STATE_IDLE}
 * (post-error keeps the controller so the next tap can retry in place).
 * <p>
 * Pending-tap identity (R8): a tap arriving while the controller is still
 * connecting carries the FULL {item, channel, url, isVideo} arguments; a
 * second tap with the same url is idempotent, a different url overwrites.
 * The queued tap is decided against ITS OWN arguments - never against "the
 * page's current item" - so a page whose displayed item changed while the
 * connection ran cannot hijack the queued playback.
 * <p>
 * Main-thread only: every public entry point hops to the main looper
 * (the app's shared main Handler), all state is touched there only.
 */
public class PlaybackManager implements ProviderDisposable, Player.Listener {

    private static final String TAG = PlaybackManager.class.getName();

    /**
     * Action of the app's custom session teardown command (see
     * {@link #COMMAND_TEARDOWN}). A compile-time constant so the service-side
     * comparison has no runtime coupling to this class.
     */
    public static final String TEARDOWN_ACTION =
            "m.co.rh.id.a_news_provider.playback.TEARDOWN";

    /**
     * Custom session command delivered to {@link PlaybackService}'s
     * MediaSession: the session-side callback runs
     * {@code MediaSessionService.pauseAllPlayersAndStopSelf()} - the ONLY
     * teardown that also destroys media3's internal session-side controller
     * (that orphan re-creates a service stopped app-side, see
     * {@link #stopPlayback()}). Granted to connecting controllers by the
     * service's {@code MediaSession.Callback.onConnect} (media3 denies
     * ungranted custom commands before the callback ever runs). NOTE: the
     * extras bundle is {@code new Bundle()}, NOT {@code Bundle.EMPTY} -
     * {@code Bundle.EMPTY} exists since API 33 only and this app is minSdk 21.
     */
    public static final SessionCommand COMMAND_TEARDOWN =
            new SessionCommand(TEARDOWN_ACTION, new Bundle());

    /**
     * Grace period before a queued tap against a still-EMPTY timeline is
     * settled as a genuinely fresh session (service cold, no prior playback):
     * such a session never fires a transition. Same value as the Phase-1
     * page implementation it replaces.
     */
    private static final long PENDING_TAP_FALLBACK_DELAY_MS = 800L;

    private Provider mProvider;
    private final ILogger mLogger;
    private final Handler mMainHandler;
    private final SerialBehaviorSubject<PlaybackState> mStateSubject;
    private final AtomicBoolean mDisposed;

    private ListenableFuture<MediaController> mControllerFuture;
    private MediaController mMediaController;

    /**
     * The media currently loaded in the session (kept as full args so state
     * snapshots can carry item/channel: the controller's MediaItem only
     * preserves the mediaId across the binder boundary, not the entities).
     */
    private RssItem mCurrentItem;
    private RssChannel mCurrentChannel;
    private String mCurrentUrl;
    private boolean mCurrentIsVideo;

    /**
     * True when the last fatal error for the CURRENT media left the player
     * IDLE; cleared when the media is re-prepared or replaced.
     */
    private boolean mFailed;

    /**
     * A play tap that arrived while the controller was still connecting: the
     * buildAsync future can resolve BEFORE the session's initial PlayerInfo
     * (timeline/current item) is applied to the controller, so the queued tap
     * is only decided once real state is visible - by the player listener's
     * timeline/transition callbacks, or by the fresh-session fallback below.
     */
    private PendingTap mPendingTap;
    private Runnable mPendingTapFallbackCheck;

    /**
     * True while the session teardown handshake ({@link #stopPlayback()} ->
     * session-side pauseAllPlayersAndStopSelf -> completion callback) is in
     * flight: a second close tap or a racing state event is then a no-op, the
     * callback completes the release. Reset by {@link #releasePlayback()}.
     */
    private boolean mTeardownInProgress;

    public PlaybackManager(Provider provider) {
        mProvider = provider;
        mLogger = provider.get(ILogger.class);
        mMainHandler = provider.get(Handler.class);
        mStateSubject = new SerialBehaviorSubject<>(PlaybackState.CLEARED);
        mDisposed = new AtomicBoolean();
    }

    /**
     * Play/pause toggle for the given media (never autoplays): pauses while
     * THIS media is playing, resumes it without restarting while it is merely
     * paused/buffering, restarts when it ENDED, re-prepares after a fatal
     * error and (re)loads + plays in every other case (first play or a
     * different media). The first tap lazily connects the MediaController to
     * {@link PlaybackService}; the tap itself is queued and completes when
     * the connection holds real state. Nothing here ever autoplays on page
     * open - playback is strictly tap-initiated.
     *
     * @param item    the rss item owning the media, must not be null
     * @param channel the rss channel of the item, must not be null
     * @param url     the media enclosure URL (becomes the player mediaId)
     * @param isVideo true for a video enclosure, false for audio
     */
    public void togglePlay(RssItem item, RssChannel channel, String url, boolean isVideo) {
        runOnMainThread(() -> togglePlayInternal(item, channel, url, isVideo));
    }

    /**
     * Toggle on whatever the session currently holds (mini-player bar
     * button): playing pauses, paused resumes, ENDED restarts, a failed
     * media re-prepares. No-op while nothing is loaded.
     */
    public void toggleCurrent() {
        runOnMainThread(() -> {
            PlaybackState state = mStateSubject.getValue();
            if (state == null || state.item == null) {
                return;
            }
            togglePlayInternal(state.item, state.channel, state.url, state.isVideo);
        });
    }

    /**
     * Pauses the current playback without releasing anything (no-op when
     * nothing is playing).
     */
    public void pause() {
        runOnMainThread(() -> {
            MediaController controller = mMediaController;
            if (controller != null && controller.isPlaying()) {
                controller.pause();
            }
        });
    }

    /**
     * Stops playback and tears the session down THROUGH media3 (mini-player
     * close button): the session PLAYER is stopped, then the app's custom
     * teardown command makes the SESSION run {@code
     * pauseAllPlayersAndStopSelf()} (the only teardown that also destroys
     * media3's internal session-side controller, which re-creates a service
     * stopped app-side), then the connection is released and the started
     * service stopped as a belt-and-braces no-op. Pushes the CLEARED state so
     * the bar hides; the NEXT play rebuilds the connection.
     */
    public void stop() {
        runOnMainThread(this::stopPlayback);
    }

    /**
     * Returns the connected controller, or null while nothing is connected.
     * The controller stays OWNED by this manager: callers may only read from
     * it or attach UI (e.g. {@code PlayerView.setPlayer}) - never release it.
     * Call on the main thread only.
     *
     * @return the connected MediaController or null
     */
    public MediaController getControllerIfConnected() {
        return mMediaController;
    }

    /**
     * The playback state stream: replays the current state on subscribe and
     * emits on every change. Observe on the main thread.
     *
     * @return observable of the app-wide playback state
     */
    public Observable<PlaybackState> state() {
        return mStateSubject.getSubject();
    }

    /**
     * The current playback state snapshot, never null (CLEARED when idle).
     *
     * @return the current state
     */
    public PlaybackState getCurrentState() {
        PlaybackState state = mStateSubject.getValue();
        return state == null ? PlaybackState.CLEARED : state;
    }

    private void togglePlayInternal(RssItem item, RssChannel channel, String url, boolean isVideo) {
        if (mDisposed.get() || item == null || url == null) {
            return;
        }
        MediaController controller = mMediaController;
        if (controller == null) {
            // not connected yet: QUEUE the tap (full args - same url idempotent,
            // different url overwrites) and build the connection (a connect is
            // only ever user-initiated). The queued tap is decided by
            // maybeRunPendingTap, NOT on connect directly: the future may
            // resolve before the controller holds the session's state.
            mPendingTap = new PendingTap(item, channel, url, isVideo);
            connect();
            return;
        }
        boolean currentLoaded = isCurrentMediaLoaded(controller, url);
        if (currentLoaded && controller.isPlaying()) {
            controller.pause();
            return;
        }
        if (currentLoaded && controller.getPlaybackState() == Player.STATE_ENDED) {
            // same media finished: restart from the beginning
            loadAndPlay(controller, item, channel, url, isVideo);
            return;
        }
        if (currentLoaded && controller.getPlaybackState() != Player.STATE_IDLE) {
            // same media, playing/paused/buffering - resume in place, the
            // former position is kept (a fresh setMediaItem would restart at
            // zero). The mediaId match keeps this working across page reopens:
            // media paused via the system notification resumes where it
            // stopped. A GONE session (fresh session, null/different mediaId)
            // reloads from zero below - position persistence is issue #70.
            controller.play();
            return;
        }
        // a different media, or a fatal error left IDLE: (re)load and play
        loadAndPlay(controller, item, channel, url, isVideo);
    }

    /**
     * Loads the given media into the shared session and starts playing.
     * Remembered as the manager's current media first, so the state snapshot
     * pushed right after {@code play()} (and every listener-driven update
     * until the first event arrives) already carries the new identity.
     */
    private void loadAndPlay(MediaController controller, RssItem item, RssChannel channel,
                             String url, boolean isVideo) {
        mCurrentItem = item;
        mCurrentChannel = channel;
        mCurrentUrl = url;
        mCurrentIsVideo = isVideo;
        mFailed = false;
        controller.setMediaItem(buildMediaItem(item, url, isVideo));
        controller.prepare();
        controller.play();
        pushCurrentState();
    }

    /**
     * Builds (once) the MediaController connection to {@link PlaybackService}.
     * The connect future is kept because the connection may complete after a
     * release - {@link #releasePlayback()} covers both outcomes through
     * {@link MediaController#releaseFuture(ListenableFuture)}.
     */
    private void connect() {
        if (mControllerFuture != null) {
            // already connecting or connected
            return;
        }
        Context context = mProvider.getContext().getApplicationContext();
        SessionToken sessionToken = new SessionToken(context,
                new ComponentName(context, PlaybackService.class));
        mControllerFuture = new MediaController.Builder(context, sessionToken).buildAsync();
        mControllerFuture.addListener(this::onControllerConnected,
                ContextCompat.getMainExecutor(context));
    }

    /**
     * Runs on the main thread when the MediaController connection resolves:
     * adopts the controller (unless the manager was disposed meanwhile),
     * starts reflecting the playback state onto the Rx stream and decides the
     * queued tap that initiated the connection.
     * <p>
     * ORDERING PITFALL: buildAsync's future may resolve BEFORE the session's
     * initial PlayerInfo (timeline/current item) is applied to the controller -
     * a synchronous state check right here can see an EMPTY player even though
     * a live session holds a loaded item. The queued tap is therefore decided
     * through {@link #maybeRunPendingTap(boolean)}, which waits for the
     * timeline/transition callbacks; the fallback settles the tap when the
     * session is genuinely fresh (empty timeline, never a transition).
     */
    private void onControllerConnected() {
        ListenableFuture<MediaController> future = mControllerFuture;
        if (future == null) {
            // released (or disposed) while the connection was in flight
            return;
        }
        MediaController controller;
        try {
            controller = future.get();
        } catch (CancellationException e) {
            // released the pending connect - nothing to adopt; also clear the
            // future so an EXTERNAL cancellation cannot wedge connect()'s
            // already-connecting guard forever (releasePlayback already
            // nulls it first, so this stays idempotent)
            mControllerFuture = null;
            return;
        } catch (Exception e) {
            mLogger.e(TAG, e.getMessage(), e);
            // the future is cleared so the next tap can retry the connection
            mControllerFuture = null;
            return;
        }
        // releaseFuture may have run between the future completing and this
        // listener executing on the main looper - never adopt a released
        // controller
        if (mControllerFuture == null) {
            return;
        }
        mMediaController = controller;
        controller.addListener(this);
        pushCurrentState();
        // decide the tap that started this connection (waits for real state)
        maybeRunPendingTap(false);
        if (mPendingTap != null) {
            // the timeline was empty at connect: either the initial PlayerInfo
            // is still in flight (the listener re-checks) or the session is
            // genuinely fresh (service cold - empty timeline forever, never a
            // transition). Settle the queued tap after a grace period.
            if (mPendingTapFallbackCheck == null) {
                mPendingTapFallbackCheck = () -> maybeRunPendingTap(true);
            }
            mMainHandler.removeCallbacks(mPendingTapFallbackCheck);
            mMainHandler.postDelayed(mPendingTapFallbackCheck,
                    PENDING_TAP_FALLBACK_DELAY_MS);
        }
    }

    /**
     * Decides a queued play tap once the controller holds REAL player state
     * (see {@link #onControllerConnected()} for the ordering pitfall): a
     * current item in the timeline means this session carries a live item
     * (playing/paused/buffering, or IDLE after an error) and the tap is
     * decided against its own full arguments - pause, resume in place, or a
     * fresh load all route through {@link #togglePlayInternal(RssItem,
     * RssChannel, String, boolean)} (R8: never "the page's current item" -
     * this manager has no page).
     * <p>
     * An EMPTY timeline is NOT decided here: the session's initial PlayerInfo
     * may still be in flight, so this only logs and waits -
     * {@link #onTimelineChanged(Timeline, int)} /
     * {@link #onMediaItemTransition(MediaItem, int)} re-run the check when
     * the session's state arrives.
     *
     * @param fallbackRecheck true when invoked by the delayed fresh-session
     *                        re-check: a still-empty timeline is then settled
     *                        as a genuinely fresh session and the queued tap
     *                        runs the fresh-load branch
     */
    private void maybeRunPendingTap(boolean fallbackRecheck) {
        PendingTap pendingTap = mPendingTap;
        MediaController controller = mMediaController;
        if (pendingTap == null || controller == null) {
            return;
        }
        MediaItem currentItem = controller.getCurrentMediaItem();
        boolean decide = currentItem != null || fallbackRecheck;
        // permanent debug-level diagnostic: confirms on-device whether the
        // queued tap resumed the live session or ran the fresh load
        mLogger.d(TAG, "maybeRunPendingTap: currentItem="
                + (currentItem == null ? "null" : "mediaId=" + currentItem.mediaId)
                + " state=" + controller.getPlaybackState()
                + " expected=" + pendingTap.url
                + " fallback=" + fallbackRecheck
                + " -> " + (decide ? "run queued tap" : "wait for initial PlayerInfo"));
        if (!decide) {
            return;
        }
        mPendingTap = null;
        togglePlayInternal(pendingTap.item, pendingTap.channel,
                pendingTap.url, pendingTap.isVideo);
    }

    /**
     * STOPS the session player and tears the session down THROUGH media3
     * (mini-player close button, natural ENDED): listener off first, the
     * session PLAYER is stopped (releasing a MediaController never touches
     * the session player - a PLAYING session kept the service foreground with
     * its transport notification forever, E2E api24: dumpsys media_session
     * still state=3 after controller release), then the app custom teardown
     * command is SENT to the session and the connection is released only in
     * its completion callback.
     * <p>
     * WHY teardown-through-media3: media3 1.8.1's internal session-side
     * controller (never released to the app, holds the media-button
     * PendingIntent) keeps the service STARTED and RE-CREATES it after any
     * app-side {@code stopService} (E2E api24: app stopService verified
     * executed via logs, yet ServiceRecord persisted and its id CHANGED
     * minutes later - the orphan re-attached and re-started it;
     * {@code pauseAllPlayersAndStopSelf()} on the session side is the only
     * teardown that destroys that orphan, because it releases the session
     * itself). The belt-and-braces app-side stopService stays AFTER the
     * handshake completes (harmless no-op once media3 stopped the service,
     * catches residual edge states). {@link #dispose(Context)} deliberately
     * does NOT take this path - app-level disposal must not kill background
     * playback the user started.
     */
    private void stopPlayback() {
        if (mTeardownInProgress) {
            // a second close tap or a racing state event while the teardown
            // handshake is in flight - the completion callback finishes the
            // release; nothing to do here
            return;
        }
        MediaController controller = mMediaController;
        if (controller == null) {
            // nothing adopted (fresh cleared, or a still-pending connect that
            // never delivered a controller): pure release + the belt-and-
            // braces service stop as before
            releasePlayback();
            stopServiceBelt();
            return;
        }
        mTeardownInProgress = true;
        // listener off FIRST: nothing observes the stop-induced state
        // transitions (the ENDED caller would otherwise re-enter)
        controller.removeListener(this);
        mMediaController = null;
        if (controller.getPlaybackState() != Player.STATE_IDLE) {
            controller.stop();
        }
        // teardown THROUGH media3 (see javadoc for why); the connection is
        // released in the completion callback, which runs whether the session
        // acknowledged success or failure
        ListenableFuture<SessionResult> teardown =
                controller.sendCustomCommand(COMMAND_TEARDOWN, new Bundle());
        teardown.addListener(this::finishTeardown,
                ContextCompat.getMainExecutor(
                        mProvider.getContext().getApplicationContext()));
    }

    /**
     * Completion callback of the session teardown handshake: runs the pure
     * release (resets the in-flight flag) and the belt-and-braces service
     * stop. Main executor.
     */
    private void finishTeardown() {
        try {
            releasePlayback();
        } catch (Exception e) {
            // the teardown callback must NEVER strand mTeardownInProgress=true
            mLogger.e(TAG, e.getMessage(), e);
            mTeardownInProgress = false;
        }
        stopServiceBelt();
    }

    /**
     * Belt-and-braces stop of the STARTED service, run only AFTER the media3
     * teardown handshake completed (see {@link #stopPlayback()} for why the
     * app-side stop alone can never win the orphan re-attach race).
     */
    private void stopServiceBelt() {
        if (mProvider != null) {
            Context appContext = mProvider.getContext().getApplicationContext();
            appContext.stopService(new Intent(appContext, PlaybackService.class));
        }
    }

    /**
     * Pure connection release WITHOUT touching the session player (used by
     * {@link #dispose(Context)} - app-level disposal must NOT kill background
     * playback the user started): the pending fresh-session re-check is
     * unscheduled, a queued tap is dropped, a pending connect future is
     * cancelled and a connected controller is released (releaseFuture covers
     * BOTH a pending and a connected future - releasing only a connected
     * controller would leak the pending connect). Pushes the CLEARED state so
     * bar/PiP consumers hide. The SERVICE stops itself only when the released
     * controller was its last one and the player is idle - a still-playing
     * session legitimately keeps running with its notification. Idempotent.
     */
    private void releasePlayback() {
        cancelPendingTap();
        if (mMediaController != null) {
            mMediaController.removeListener(this);
            mMediaController = null;
        }
        if (mControllerFuture != null) {
            MediaController.releaseFuture(mControllerFuture);
            mControllerFuture = null;
        }
        mCurrentItem = null;
        mCurrentChannel = null;
        mCurrentUrl = null;
        mCurrentIsVideo = false;
        mFailed = false;
        mTeardownInProgress = false;
        if (!mDisposed.get()) {
            mStateSubject.onNext(PlaybackState.CLEARED);
        }
    }

    /**
     * Unschedules the pending fresh-session re-check and drops a queued tap.
     */
    private void cancelPendingTap() {
        if (mPendingTapFallbackCheck != null) {
            mMainHandler.removeCallbacks(mPendingTapFallbackCheck);
        }
        mPendingTap = null;
    }

    private void pushCurrentState() {
        if (mDisposed.get() || mCurrentUrl == null || mMediaController == null) {
            mStateSubject.onNext(PlaybackState.CLEARED);
            return;
        }
        mStateSubject.onNext(new PlaybackState(mCurrentItem, mCurrentChannel, mCurrentUrl,
                mCurrentIsVideo, mMediaController.isPlaying(), mFailed));
    }

    /**
     * True when the controller's current MediaItem is the given media,
     * regardless of its play state. Matched by mediaId FIRST: media3 strips
     * {@code localConfiguration} (the local URI part) when a timeline crosses
     * the controller boundary - it exists only in the player's own process -
     * so a controller that adopted a live session sees an item without it and
     * must be identified via the mediaId, which survives the binder
     * round-trip. The URI comparison stays as a fallback for an item still
     * loaded through this controller's own instance.
     */
    private static boolean isCurrentMediaLoaded(MediaController controller, String url) {
        if (url == null) {
            return false;
        }
        MediaItem currentItem = controller.getCurrentMediaItem();
        if (currentItem == null) {
            return false;
        }
        // mediaId is set to the media URL in buildMediaItem, which makes the
        // equality meaningful - the default mediaId is a random UUID
        if (currentItem.mediaId.equals(url)) {
            return true;
        }
        return currentItem.localConfiguration != null
                && currentItem.localConfiguration.uri.toString().equals(url);
    }

    /**
     * Builds the playback MediaItem for the given enclosure URL. The MIME
     * type is derived from the URL extension when known (the stored enclosure
     * carries no MIME type), falling back to audio/mpeg / video/mp4 - the
     * player also sniffs the container, so the default only primes the media
     * source factory. The mediaId is set to the URL explicitly: media3 strips
     * the local URI part (localConfiguration) when a timeline is synced to a
     * MediaController, so the mediaId is the only stable, binder-preserved
     * identity (see {@link #isCurrentMediaLoaded(MediaController, String)}).
     * The item title becomes the media metadata title (media notification +
     * PiP display). NO MediaMetadata.extras - identity is mediaId-only.
     *
     * @param item    the rss item owning the media (title source)
     * @param url     the media enclosure URL
     * @param isVideo true for a video enclosure, false for audio
     * @return the MediaItem to load into the player
     */
    private MediaItem buildMediaItem(RssItem item, String url, boolean isVideo) {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
        if (mimeType == null || !mimeType.startsWith(isVideo ? "video" : "audio")) {
            mimeType = isVideo ? "video/mp4" : "audio/mpeg";
        }
        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(item.title)
                .build();
        return new MediaItem.Builder()
                .setMediaId(url)
                .setUri(url)
                .setMimeType(mimeType)
                .setMediaMetadata(mediaMetadata)
                .build();
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mMainHandler.post(runnable);
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        pushCurrentState();
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            // R3 release policy: the media finished playing - stop the session
            // player (forces IDLE so media3 deterministically drops the media
            // notification + foreground) and tear the whole connection down
            // (listener off, fallback cancelled, future released, refs nulled,
            // subject cleared). The bar hides; the next play rebuilds the
            // connection. NEVER released on IDLE (post-error) - see
            // releasePlayback().
            stopPlayback();
            return;
        }
        pushCurrentState();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, int reason) {
        maybeRunPendingTap(false);
    }

    @Override
    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
        maybeRunPendingTap(false);
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        mLogger.e(TAG, error.getMessage(), error);
        // onPlayerError always transitions the player to STATE_IDLE: keep the
        // controller (R3 - never release on IDLE), mark the state failed so
        // consumers can toast/revert, the next same-media tap re-prepares
        mFailed = true;
        pushCurrentState();
    }

    /**
     * Provider-disposal hook: releases the controller connection
     * (idempotent, main-thread hop) and completes the state subject. After
     * this, all public entry points are no-ops.
     */
    @Override
    public void dispose(Context context) {
        if (mDisposed.getAndSet(true)) {
            return;
        }
        runOnMainThread(() -> {
            releasePlayback();
            try {
                mStateSubject.getSubject().onComplete();
            } catch (Throwable t) {
                // dispose is terminal lifecycle - must complete cleanly
                mLogger.w(TAG, "Failed to complete playback state subject", t);
            }
            mProvider = null;
        });
    }

    /**
     * Full tap arguments queued while the controller connects (R8): the
     * queued tap is decided against these - same url is idempotent, a
     * different url overwrites, and the decision never consults any page.
     */
    private static class PendingTap {
        private final RssItem item;
        private final RssChannel channel;
        private final String url;
        private final boolean isVideo;

        private PendingTap(RssItem item, RssChannel channel, String url, boolean isVideo) {
            this.item = item;
            this.channel = channel;
            this.url = url;
            this.isVideo = isVideo;
        }
    }
}
