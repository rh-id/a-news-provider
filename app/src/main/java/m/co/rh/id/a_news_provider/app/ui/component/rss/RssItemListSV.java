package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tokopedia.showcase.ShowCaseBuilder;
import com.tokopedia.showcase.ShowCaseDialog;
import com.tokopedia.showcase.ShowCaseObject;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemListSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider> {
    private static final String TAG = RssItemListSV.class.getName();

    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient PagedRssItemsCmd mPagedRssItemsCmd;
    private transient Handler mHandler;
    private transient RxDisposer mRxDisposer;
    private transient RecyclerView.OnScrollListener mOnScrollListener;
    private transient RssItemRecyclerViewAdapter mRssItemRecyclerViewAdapter;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        mPagedRssItemsCmd = mSvProvider.get(PagedRssItemsCmd.class);
        mPagedRssItemsCmd.load();
        mHandler = mSvProvider.get(Handler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRssItemRecyclerViewAdapter = new RssItemRecyclerViewAdapter(
                mPagedRssItemsCmd, mNavigator, this);
        if (mOnScrollListener == null) {
            mOnScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (!recyclerView.canScrollVertically(1) &&
                            newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mPagedRssItemsCmd.loadNextPage();
                    }
                }
            };
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_rss_item, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mRssItemRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mOnScrollListener);
        Spinner spinnerFilterBy = view.findViewById(R.id.spinner_filter_by);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity,
                R.array.array_filter_by, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterBy.setAdapter(adapter);
        mPagedRssItemsCmd.getFilterType()
                .ifPresent(spinnerFilterBy::setSelection);
        spinnerFilterBy.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mPagedRssItemsCmd.setFilterType(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mPagedRssItemsCmd.setFilterType(null);
            }
        });
        mRxDisposer.add("mPagedRssItemsCmd.getRssItems",
                mPagedRssItemsCmd.getRssItems()
                        .debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItems -> {
                                    mHandler
                                            .post(mRssItemRecyclerViewAdapter::notifyDataSetChanged);
                                    if (!rssItems.isEmpty()) {
                                        if (!mAppSharedPreferences.isShowCaseRssItemList()) {
                                            mHandler
                                                    .postDelayed(() -> {
                                                        int textColor = R.color.white;
                                                        ShowCaseDialog showCaseDialog = new ShowCaseBuilder()
                                                                .textColorRes(textColor)
                                                                .titleTextColorRes(textColor)
                                                                .shadowColorRes(R.color.daynight_transparent_white_black)
                                                                .titleTextSizeRes(R.dimen.text_nav_menu)
                                                                .spacingRes(R.dimen.spacing_normal)
                                                                .backgroundContentColorRes(R.color.orange_600)
                                                                .circleIndicatorBackgroundDrawableRes(R.drawable.selector_circle_green)
                                                                .prevStringRes(R.string.previous)
                                                                .nextStringRes(R.string.next)
                                                                .finishStringRes(R.string.finish)
                                                                .useCircleIndicator(false)
                                                                .clickable(true)
                                                                .build();
                                                        String title = activity.getString(R.string.title_showcase_rss_item_list);
                                                        String description = activity.getString(R.string.showcase_rss_item_list);
                                                        ArrayList<ShowCaseObject> showCaseList = new ArrayList<>();
                                                        showCaseList.add(new ShowCaseObject(
                                                                recyclerView.getChildAt(0),
                                                                title,
                                                                description));
                                                        showCaseDialog.show(activity, null, showCaseList);
                                                    }, 1000);
                                            mAppSharedPreferences.setShowCaseRssItemList(true);
                                        }
                                    }
                                },
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage()))
                        )
        );
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mRssItemRecyclerViewAdapter != null) {
            mRssItemRecyclerViewAdapter.dispose(activity);
            mRssItemRecyclerViewAdapter = null;
        }
    }

    public void refresh() {
        if (mPagedRssItemsCmd != null) {
            mPagedRssItemsCmd.reload();
        }
    }

    public Flowable<Boolean> getLoadingFlow() {
        if (mPagedRssItemsCmd == null) return null;
        return mPagedRssItemsCmd.getLoadingFlow();
    }
}