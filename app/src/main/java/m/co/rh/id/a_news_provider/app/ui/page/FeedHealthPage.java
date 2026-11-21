package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.FeedHealthCmd;
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.component.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.FeedHealthRecyclerViewAdapter;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

/**
 * Feed-health dashboard page (issue #42): one overview of every subscribed
 * feed's sync state - derived status badge, last attempt/success timeline,
 * unread/total counts, recorded errors and the active per-feed backoff
 * schedule - with per-row sync-now/pause/delete actions. Reached from the home
 * drawer.
 * <p>
 * Data flows exclusively through the page-scoped {@link FeedHealthCmd} (a dumb
 * adapter renders what the command emits, errors-first ordered). Reloads are
 * event-driven after the initial load: channel updates (pause/rename),
 * deletions and sync summaries all change what the dashboard must show, so
 * each event triggers a plain command refresh. No window-focus guard is
 * needed here (unlike HomePage's transient snackbars/toasts): this page's
 * reactions are reload-only and the page cannot receive events while popped -
 * its RxDisposer subscriptions die with its StatefulViewProvider.
 */
public class FeedHealthPage extends StatefulView<Activity> implements RequireComponent<Provider> {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient RssChangeNotifier mRssChangeNotifier;
    private transient FeedHealthCmd mFeedHealthCmd;
    private transient FeedHealthRecyclerViewAdapter mFeedHealthRecyclerViewAdapter;

    public FeedHealthPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRssChangeNotifier = mSvProvider.get(RssChangeNotifier.class);
        mFeedHealthCmd = mSvProvider.get(FeedHealthCmd.class);
        mFeedHealthRecyclerViewAdapter =
                new FeedHealthRecyclerViewAdapter(mNavigator, this);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_feed_health,
                container, false);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        mAppBarSV.setTitle(activity.getString(R.string.feed_health));
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setAdapter(mFeedHealthRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity,
                DividerItemDecoration.VERTICAL));

        // subscribe BEFORE the first refresh(): the command emits on a
        // non-replaying processor, subscribing first guarantees the initial
        // load cannot slip past the subscription
        mRxDisposer.add("feedHealthCmd.items",
                mFeedHealthCmd.items()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(mFeedHealthRecyclerViewAdapter::setItems,
                                throwable -> {
                                    // leave blank: the command logs its own failures
                                    // and never errors the processor
                                }));

        // reload-only event wiring (issue #42): every channel-state mutation the
        // rows can trigger (pause via PauseRssChannelCmd, delete via
        // DeleteRssChannelCmd) and every finished sync run (targeted or
        // app-wide) lands here as a plain refresh
        mRxDisposer.add("rssChangeNotifier.updatedRssChannel",
                mRssChangeNotifier.updatedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional ->
                                        mFeedHealthCmd.refresh(),
                                throwable -> {
                                    // leave blank
                                }));
        mRxDisposer.add("rssChangeNotifier.deletedRssChannel",
                mRssChangeNotifier.deletedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional ->
                                        mFeedHealthCmd.refresh(),
                                throwable -> {
                                    // leave blank
                                }));
        mRxDisposer.add("rssChangeNotifier.syncSummary",
                mRssChangeNotifier.liveSyncSummary()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(syncResult ->
                                        mFeedHealthCmd.refresh(),
                                throwable -> {
                                    // leave blank
                                }));

        mFeedHealthCmd.refresh();
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mFeedHealthCmd != null) {
            mFeedHealthCmd.dispose();
            mFeedHealthCmd = null;
        }
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mFeedHealthRecyclerViewAdapter != null) {
            mFeedHealthRecyclerViewAdapter.dispose(activity);
            mFeedHealthRecyclerViewAdapter = null;
        }
        mRssChangeNotifier = null;
    }
}
