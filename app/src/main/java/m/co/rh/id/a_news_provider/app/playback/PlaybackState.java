package m.co.rh.id.a_news_provider.app.playback;

import java.io.Serializable;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

/**
 * Immutable snapshot of the app-wide playback state (issue #69): what is
 * loaded in the shared {@link PlaybackManager} session, whether it is playing
 * and whether the last fatal error left it failed. Emitted through the
 * manager's {@code SerialBehaviorSubject<PlaybackState>} and observed by the
 * detail page (FAB/video slot), the mini-player bar and MainActivity (PiP).
 * <p>
 * {@code Serializable} is REQUIRED by the subject's type bound
 * ({@code SerialBehaviorSubject<E extends Serializable>}); instances live
 * only in the subject, they never participate in NavRoute serialization.
 * <p>
 * Identity: {@code url} IS the mediaId used to load the player item
 * (mediaId-first identity - media3 strips {@code localConfiguration} across
 * the controller binder boundary, so the URL-as-mediaId is the only stable
 * identity consumers can compare against). A {@code null} {@code item}
 * marks the CLEARED state (nothing loaded / released after ENDED / stopped).
 */
public class PlaybackState implements Serializable {

    /**
     * The CLEARED state singleton: nothing is loaded, nothing is playing.
     */
    public static final PlaybackState CLEARED =
            new PlaybackState(null, null, null, false, false, false);

    /**
     * The item currently loaded in the shared session, null when cleared.
     */
    public final RssItem item;

    /**
     * The channel the loaded item belongs to, null when cleared. Kept so the
     * mini-player bar can re-open the detail page with complete arguments.
     */
    public final RssChannel channel;

    /**
     * The loaded media URL (audio or video enclosure) - the player mediaId.
     */
    public final String url;

    /**
     * True when the loaded media is a video enclosure, false for audio.
     */
    public final boolean isVideo;

    /**
     * True only while the shared player is actively playing (buffering and
     * paused report false).
     */
    public final boolean isPlaying;

    /**
     * True when the last fatal playback error for the CURRENT media left the
     * player in IDLE. Stays true until the user re-plays the same media or a
     * different media is loaded - the failure toast of the detail page is
     * keyed on this flag.
     */
    public final boolean failed;

    public PlaybackState(RssItem item, RssChannel channel, String url,
                         boolean isVideo, boolean isPlaying, boolean failed) {
        this.item = item;
        this.channel = channel;
        this.url = url;
        this.isVideo = isVideo;
        this.isPlaying = isPlaying;
        this.failed = failed;
    }

    /**
     * True when this state refers to the given media URL (never compares
     * {@code item.link} - the dedup key of feed items is NOT the media
     * identity; different items may share one enclosure URL and one item may
     * carry both audio and video).
     *
     * @param url the media URL to compare, may be null
     * @return true when the state is loaded and both URLs are equal
     */
    public boolean matchesUrl(String url) {
        return this.url != null && this.url.equals(url);
    }

    @Override
    public String toString() {
        return "PlaybackState{url=" + url
                + ", isVideo=" + isVideo
                + ", isPlaying=" + isPlaying
                + ", failed=" + failed
                + ", item=" + (item == null ? "null" : item.id)
                + '}';
    }
}
