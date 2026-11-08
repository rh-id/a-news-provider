package m.co.rh.id.a_news_provider.app.playback;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.ui.page.RssItemDetailPage;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRouteChangedListener;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * App-scoped controller of the mini-player bar (issue #69 phase 2): owns the
 * 56dp strip docked to the BOTTOM of android.R.id.content - a SIBLING of the
 * navigator's NavViewLayout, NEVER a child of it (the navigator only removes
 * its own children on push/pop and wipes ALL content children on
 * reBuildAllRoute, so a bar inside the nav layout would be swept away).
 * <p>
 * Attachment (R9): {@link #attachIfNeeded(Activity)} detaches any existing
 * bar and inflates a FRESH view on EVERY call - theme attrs
 * (selectableItemBackgroundBorderless, day-night colors) must re-resolve
 * after a day-night rebuild. MainActivity calls it in onResume and again
 * right after reBuildAllRoute(); its onDestroy calls
 * {@link #clearViewReference()} so this singleton never holds a dead
 * activity's view.
 * <p>
 * Visibility (R5+R6), recomputed on every playback-state emission and on
 * every route push/pop/restore: bar VISIBLE iff the session holds an item
 * AND the current TOP route is not a RssItemDetailPage whose current item's
 * mediaAudio OR mediaVideo equals the loaded URL (compared by URL, never
 * item.link - deduped feeds share links but carry their own enclosures, and
 * one item may carry both audio and video). While the bar is visible on a
 * detail page, that page lifts its FABs by the bar's height (see
 * RssItemDetailPage#updateFabBottomMargins). The bar is also suppressed in
 * picture-in-picture mode (the video owns the tiny window).
 * <p>
 * Interactions: title tap re-opens the playing item's detail page (main
 * thread, guarded on the navigator having an activity - INavigator.push
 * touches views); play/pause drives {@link PlaybackManager#toggleCurrent()};
 * close stops and releases the session ({@link PlaybackManager#stop()},
 * which also lets the service's media notification go away).
 */
public class MiniPlayerBarController implements ProviderDisposable, NavOnRouteChangedListener {

    private static final String TAG = MiniPlayerBarController.class.getName();

    private final PlaybackManager mPlaybackManager;
    private final INavigator mNavigator;
    private final Handler mMainHandler;
    private final CompositeDisposable mCompositeDisposable;
    private final AtomicBoolean mDisposed;

    /**
     * The currently attached bar view, null when no activity is hosting one
     * (or the host activity was destroyed - see clearViewReference()).
     */
    private MiniPlayerBarView mBarView;

    /**
     * The activity the bar view is currently attached to, kept alongside the
     * view for the PiP suppression check; nulled with the view.
     */
    private Activity mBarActivity;

    public MiniPlayerBarController(Provider provider) {
        mPlaybackManager = provider.get(PlaybackManager.class);
        mNavigator = provider.get(INavigator.class);
        mMainHandler = provider.get(Handler.class);
        mCompositeDisposable = new CompositeDisposable();
        mDisposed = new AtomicBoolean();
        // replay-the-current-value stream: content AND visibility are applied
        // for the first time right here, before any view exists (cached for
        // the next attach)
        mCompositeDisposable.add(mPlaybackManager.state()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaybackStateChanged));
        mNavigator.addOnRouteChangedListener(this);
    }

    /**
     * Attaches a freshly-inflated bar to the given activity's content view
     * (android.R.id.content, Gravity.BOTTOM). Inflates a NEW view every call
     * (R9 - theme attrs must re-resolve after day-night rebuilds), removing
     * any previous bar first. Safe to call repeatedly (onResume, post-rebuild).
     *
     * @param activity the host activity, must not be null
     */
    public void attachIfNeeded(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (mDisposed.get()) {
            return;
        }
        detachView();
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        MiniPlayerBarView barView = (MiniPlayerBarView) LayoutInflater.from(activity)
                .inflate(R.layout.mini_player_bar, content, false);
        barView.setListener(new BarListener());
        // keep the inflated width/height (match_parent x 56dp) and only set the
        // bottom gravity - overriding height with WRAP_CONTENT would collapse
        // the bar onto its content
        ViewGroup.LayoutParams inflatedParams = barView.getLayoutParams();
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                inflatedParams == null
                        ? ViewGroup.LayoutParams.MATCH_PARENT : inflatedParams.width,
                inflatedParams == null
                        ? ViewGroup.LayoutParams.WRAP_CONTENT : inflatedParams.height,
                Gravity.BOTTOM);
        content.addView(barView, layoutParams);
        mBarView = barView;
        mBarActivity = activity;
        applyCurrentState();
    }

    /**
     * Drops the view reference WITHOUT removing the view (MainActivity.onDestroy):
     * the whole window is tearing down, only this singleton's memory of it
     * must go so a dead activity's view is never held or updated.
     */
    public void clearViewReference() {
        mBarView = null;
        mBarActivity = null;
    }

    private void onPlaybackStateChanged(PlaybackState state) {
        applyState(state);
    }

    @Override
    public void onChanged(NavRoute previous, NavRoute current) {
        // fired after every push/pop AND once after a saved-state restore -
        // exactly the events that change whether the top route suppresses the bar
        applyCurrentState();
    }

    /**
     * Applies content + visibility for the CURRENT playback state (post-attach
     * or route change - the state stream may not fire again for a while).
     */
    private void applyCurrentState() {
        if (mDisposed.get()) {
            return;
        }
        applyState(mPlaybackManager.getCurrentState());
    }

    /**
     * One state application pass: content always (cheap), visibility per the
     * R5+R6 rule. No-op when no view is attached - the next attach replays it.
     *
     * @param state the playback state to render
     */
    private void applyState(PlaybackState state) {
        MiniPlayerBarView barView = mBarView;
        if (barView == null) {
            return;
        }
        boolean hasItem = state != null && state.item != null;
        boolean visible = hasItem && !topRouteShowsSameMedia(state) && !isInPictureInPicture();
        barView.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
        if (hasItem) {
            barView.setTitleText(state.item.title);
            barView.setPlayPauseVisual(state.isPlaying);
        }
    }

    /**
     * True when the current TOP route is a RssItemDetailPage whose current
     * item carries the loaded media (audio OR video, compared by URL - never
     * item.link: deduped feeds share links but carry their own enclosures).
     * The page's item is read from the route's args (the detail page always
     * opens/replaces with Args, so the args item tracks the displayed item;
     * the menu_edit_link in-place swap keeps stale args, a cosmetic dedup gap
     * only the edit-link flow can produce). Null-safe at every step.
     */
    private boolean topRouteShowsSameMedia(PlaybackState state) {
        NavRoute currentRoute = mNavigator.getCurrentRoute();
        if (currentRoute == null) {
            return false;
        }
        StatefulView<?> statefulView = currentRoute.getStatefulView();
        if (!(statefulView instanceof RssItemDetailPage)) {
            return false;
        }
        RssItemDetailPage.Args args =
                RssItemDetailPage.Args.of(currentRoute.getRouteArgs());
        if (args == null) {
            return false;
        }
        RssItem routeItem = args.getRssItem();
        if (routeItem == null) {
            return false;
        }
        return state.matchesUrl(routeItem.mediaAudio) || state.matchesUrl(routeItem.mediaVideo);
    }

    /**
     * True while the host activity is in picture-in-picture mode: the video
     * owns the tiny window, the bar would only cover it.
     */
    private boolean isInPictureInPicture() {
        Activity activity = mBarActivity != null ? mBarActivity : mNavigator.getActivity();
        if (activity == null) {
            return false;
        }
        // Activity#isInPictureInPictureMode exists since API 24; PiP is an
        // API 26+ feature so the flag can only be true far above minSdk 21
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && activity.isInPictureInPictureMode();
    }

    /**
     * Removes the bar view from its host (leaving NO stale view behind), but
     * keeps this controller's subscriptions/state for the next attach.
     */
    private void detachView() {
        if (mBarView != null) {
            if (mBarView.getParent() instanceof ViewGroup) {
                ((ViewGroup) mBarView.getParent()).removeView(mBarView);
            }
            mBarView.setListener(null);
            mBarView = null;
        }
        mBarActivity = null;
    }

    private class BarListener implements MiniPlayerBarView.Listener {

        @Override
        public void onTitleClicked() {
            PlaybackState state = mPlaybackManager.getCurrentState();
            if (state == null || state.item == null) {
                return;
            }
            // INavigator.push touches views and NPEs when the navigator has no
            // activity yet - guard AND hop to the main thread
            if (mNavigator.getActivity() == null) {
                return;
            }
            mMainHandler.post(() -> {
                if (mDisposed.get() || mNavigator.getActivity() == null) {
                    return;
                }
                mNavigator.push(Routes.RSS_ITEM_DETAIL_PAGE,
                        RssItemDetailPage.Args.withRss(state.item, state.channel));
            });
        }

        @Override
        public void onPlayPauseClicked() {
            mPlaybackManager.toggleCurrent();
        }

        @Override
        public void onCloseClicked() {
            mPlaybackManager.stop();
        }
    }

    /**
     * Provider-disposal hook: removes the route listener, ends the state
     * subscription and detaches any attached bar. Idempotent.
     */
    @Override
    public void dispose(Context context) {
        if (mDisposed.getAndSet(true)) {
            return;
        }
        mNavigator.removeOnRouteChangedListener(this);
        mCompositeDisposable.dispose();
        detachView();
    }
}
