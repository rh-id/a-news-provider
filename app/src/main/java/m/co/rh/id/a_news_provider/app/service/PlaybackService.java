package m.co.rh.id.a_news_provider.app.service;

import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import m.co.rh.id.a_news_provider.app.playback.PlaybackManager;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Media session service backing the podcast playback (issue #69): hosts the
 * ExoPlayer (audio and, since phase 2, inline video through the same player)
 * and the MediaSession the app-scoped PlaybackManager's MediaController
 * connects to. The service is declared in the manifest with the
 * {@code androidx.media3.session.MediaSessionService} intent filter, which lets
 * the media3 controller binding start it on demand; the default media
 * notification provider (media3 1.8.x) promotes the service to the foreground
 * with a media notification while playback runs, and the default
 * {@code onTaskRemoved} already pauses players and stops the service when no
 * session is playing - neither is overridden here.
 * <p>
 * The session also serves ONE app-internal custom command, the teardown
 * command ({@link PlaybackManager#COMMAND_TEARDOWN}): it runs
 * {@code pauseAllPlayersAndStopSelf()} - the only teardown that also destroys
 * media3's internal session-side controller, which otherwise re-creates a
 * service stopped app-side (see {@link PlaybackManager#stopPlayback()}).
 */
public class PlaybackService extends MediaSessionService {

    private static final String TAG = PlaybackService.class.getName();

    private transient ExoPlayer mPlayer;
    private transient MediaSession mMediaSession;
    private transient ILogger mLogger;

    @Override
    public void onCreate() {
        super.onCreate();
        Provider provider = BaseApplication.of(this).getProvider();
        mLogger = provider.get(ILogger.class);
        // player: music attributes with audio focus handling so other apps can
        // duck/pause us, and pause on unplugging the headphones
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        mPlayer = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                // R11 (issue #69 phase 2): WAKE_MODE_NETWORK keeps a WakeLock
                // (and WifiLock for the network transfer) while the player
                // buffers/plays, so podcast STREAMING continues with the
                // screen off - the manifest carries the WAKE_LOCK permission
                // for exactly this
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        mMediaSession = new MediaSession.Builder(this, mPlayer)
                .setCallback(new MediaSession.Callback() {
                    @OptIn(markerClass = UnstableApi.class)
                    @Override
                    public MediaSession.ConnectionResult onConnect(MediaSession mediaSession,
                                                                   MediaSession.ControllerInfo controller) {
                        // media3's default grant (AcceptedResultBuilder ->
                        // DEFAULT_SESSION_COMMANDS) covers only the PREDEFINED
                        // session commands - the stub DENIES ungranted custom
                        // commands with ERROR_PERMISSION_DENIED before the
                        // callback ever runs
                        // (MediaSessionStub.dispatchSessionTaskWithSessionCommand),
                        // so the app's teardown command is granted explicitly
                        // here on top of the full default set
                        SessionCommands sessionCommands =
                                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                        .buildUpon()
                                        .add(PlaybackManager.COMMAND_TEARDOWN)
                                        .build();
                        return MediaSession.ConnectionResult.accept(sessionCommands,
                                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS);
                    }

                    @OptIn(markerClass = UnstableApi.class)
                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(
                            MediaSession mediaSession,
                            MediaSession.ControllerInfo controller,
                            SessionCommand customCommand,
                            Bundle args) {
                        if (PlaybackManager.TEARDOWN_ACTION
                                .equals(customCommand.customAction)) {
                            // the ONLY teardown that also destroys media3's
                            // internal session-side controller: that orphan
                            // re-creates a service killed app-side (see
                            // PlaybackManager.stopPlayback). Main thread by
                            // contract (session application handler).
                            pauseAllPlayersAndStopSelf();
                            return Futures.immediateFuture(
                                    new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        return Futures.immediateFuture(
                                new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
                    }
                })
                .build();
        mLogger.d(TAG, "PlaybackService created");
    }

    /**
     * Returns the single session this service hosts to any connecting
     * controller (including the in-app detail-page controller).
     */
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mMediaSession;
    }

    /**
     * Releases the player and the session after the service base class cleaned
     * up its own registration ({@code onDestroy} is {@code @CallSuper} in
     * media3; the documented example order is super first, then the releases).
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        if (mMediaSession != null) {
            mMediaSession.release();
            mMediaSession = null;
        }
        mLogger = null;
    }
}
