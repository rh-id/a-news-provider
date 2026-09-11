package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.BaseRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SearchRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class SearchRssItemListSV extends StatefulView<Activity> implements RequireComponent<Provider> {
    private static final String TAG = SearchRssItemListSV.class.getName();
    private static final long SEARCH_DEBOUNCE_MILLIS = 300L;

    @NavInject
    private transient INavigator mNavigator;

    private String mQuery;
    private transient Provider mSvProvider;
    private transient SearchRssItemsCmd mSearchRssItemsCmd;
    private transient RxDisposer mRxDisposer;
    private transient RecyclerView.OnScrollListener mOnScrollListener;
    private transient RssItemRecyclerViewAdapter mRssItemRecyclerViewAdapter;
    private transient PublishSubject<String> mQuerySubject;
    private transient boolean mIsUpdateQueryText;

    public SearchRssItemListSV() {
        mQuery = "";
        mQuerySubject = PublishSubject.create();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mSearchRssItemsCmd = mSvProvider.get(SearchRssItemsCmd.class);
        if (mQuery != null && !mQuery.isEmpty()) {
            mSearchRssItemsCmd.setQuery(mQuery);
        }
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRssItemRecyclerViewAdapter = new RssItemRecyclerViewAdapter(
                mSearchRssItemsCmd, mNavigator, this);
        if (mOnScrollListener == null) {
            mOnScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (!recyclerView.canScrollVertically(1) &&
                            newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mSearchRssItemsCmd.loadNextPage();
                    }
                }
            };
        }
        mRxDisposer.add("mSearchRssItemsCmd.itemsMarkedRead",
                mSvProvider.get(RssChangeNotifier.class).getItemsMarkedRead()
                        .subscribe(channelId -> mSearchRssItemsCmd.reload(),
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        mRxDisposer.add("mRssChangeNotifier.updatedRssItem.favoriteFilter",
                mSvProvider.get(RssChangeNotifier.class).getUpdatedRssItem()
                        .subscribe(rssItem -> {
                            if (mSearchRssItemsCmd.getFilterType()
                                    .orElse(BaseRssItemsCmd.FILTER_BY_NONE)
                                    == BaseRssItemsCmd.FILTER_BY_FAVORITE) {
                                mSearchRssItemsCmd.reload();
                            }
                        })
        );
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_search_rss, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mRssItemRecyclerViewAdapter);
        recyclerView.addOnScrollListener(mOnScrollListener);
        RecyclerView.LayoutManager layoutManager;
        if (activity.getResources().getBoolean(R.bool.is_landscape)) {
            layoutManager = new StaggeredGridLayoutManager(2, RecyclerView.VERTICAL);
        } else {
            layoutManager = new LinearLayoutManager(activity);
        }
        recyclerView.setLayoutManager(layoutManager);
        Spinner spinnerFilterBy = view.findViewById(R.id.spinner_filter_by);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity,
                R.array.array_filter_by, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterBy.setAdapter(adapter);
        mSearchRssItemsCmd.getFilterType()
                .ifPresent(spinnerFilterBy::setSelection);
        spinnerFilterBy.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSearchRssItemsCmd.setFilterType(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSearchRssItemsCmd.setFilterType(null);
            }
        });
        ImageButton buttonSortOrder = view.findViewById(R.id.button_sort_order);
        buttonSortOrder.setOnClickListener(v -> {
            Integer sortOrder = mSearchRssItemsCmd.getSortOrder();
            boolean isNewest = sortOrder == null || sortOrder == BaseRssItemsCmd.SORT_ORDER_NEWEST;
            mSearchRssItemsCmd.setSortOrder(isNewest ?
                    BaseRssItemsCmd.SORT_ORDER_OLDEST : BaseRssItemsCmd.SORT_ORDER_NEWEST);
        });
        mRxDisposer.add("mSearchRssItemsCmd.sortOrder",
                mSearchRssItemsCmd.getSortOrderFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(sortOrderOptional -> {
                                    Integer sortOrder = sortOrderOptional.orElse(BaseRssItemsCmd.SORT_ORDER_NEWEST);
                                    if (sortOrder == BaseRssItemsCmd.SORT_ORDER_OLDEST) {
                                        buttonSortOrder.setImageResource(R.drawable.ic_sort_asc_white);
                                        buttonSortOrder.setContentDescription(
                                                activity.getString(R.string.sort_oldest_first));
                                    } else {
                                        buttonSortOrder.setImageResource(R.drawable.ic_sort_desc_white);
                                        buttonSortOrder.setContentDescription(
                                                activity.getString(R.string.sort_newest_first));
                                    }
                                },
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage()))
                        )
        );
        Integer currentSortOrder = mSearchRssItemsCmd.getSortOrder();
        boolean isNewest = currentSortOrder == null || currentSortOrder == BaseRssItemsCmd.SORT_ORDER_NEWEST;
        buttonSortOrder.setImageResource(isNewest ?
                R.drawable.ic_sort_desc_white : R.drawable.ic_sort_asc_white);
        buttonSortOrder.setContentDescription(activity.getString(isNewest ?
                R.string.sort_newest_first : R.string.sort_oldest_first));
        ProgressBar progressBarLoading = view.findViewById(R.id.progress_bar_loading);
        mRxDisposer.add("mSearchRssItemsCmd.isLoading",
                mSearchRssItemsCmd.getLoadingFlow()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(isLoading -> {
                            if (isLoading) {
                                progressBarLoading.setVisibility(View.VISIBLE);
                            } else {
                                progressBarLoading.setVisibility(View.GONE);
                            }
                        })
        );
        mRxDisposer.add("mSearchRssItemsCmd.getResults",
                mSearchRssItemsCmd.getResults()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItems -> mRssItemRecyclerViewAdapter.notifyItemsChanged(),
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        EditText editTextSearch = view.findViewById(R.id.edit_text_search);
        String currentQuery = mSearchRssItemsCmd.getQuery();
        if (currentQuery != null && !currentQuery.isEmpty()) {
            mIsUpdateQueryText = true;
            editTextSearch.setText(currentQuery);
            editTextSearch.setSelection(currentQuery.length());
            mIsUpdateQueryText = false;
        }
        editTextSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                updateQuery(v.getText().toString());
                return true;
            }
            return false;
        });
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Leave blank
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Leave blank
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mIsUpdateQueryText) {
                    return;
                }
                mQuerySubject.onNext(s.toString());
            }
        });
        mRxDisposer.add("mQuerySubject.debounce",
                mQuerySubject
                        .debounce(SEARCH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::updateQuery,
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        return view;
    }

    private void updateQuery(String query) {
        mQuery = query;
        mSearchRssItemsCmd.setQuery(query);
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
        if (mSearchRssItemsCmd != null) {
            mSearchRssItemsCmd.reload();
        }
    }
}
