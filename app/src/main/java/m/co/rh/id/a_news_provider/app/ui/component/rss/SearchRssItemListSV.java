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

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
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

    private transient Provider mSvProvider;
    private transient SearchRssItemsCmd mSearchRssItemsCmd;
    private transient RxDisposer mRxDisposer;
    private transient RecyclerView.OnScrollListener mOnScrollListener;
    private transient RssItemRecyclerViewAdapter mRssItemRecyclerViewAdapter;
    private SerialBehaviorSubject<String> mQuerySubject;
    private transient boolean mIsUpdateQueryText;

    /**
     * Suppresses the programmatic spinner callbacks fired when the filter
     * Spinner's saved selection is applied: AdapterView always delivers at
     * least one {@code onItemSelected} during the first layout pass after
     * {@code setAdapter}/{@code setSelection}, regardless of user input.
     * Routing that programmatic callback into {@code setFilterType} would
     * reset the keyset cursor with a FRESH load and collapse the accumulated
     * search results window back to one page. The flag is set in
     * {@link #createView(Activity)} BEFORE the adapter and saved selection
     * are wired, and consumed (reset) by the first spinner callback: a
     * Spinner must be laid out on screen before it can receive user input,
     * so the initial programmatic callback is guaranteed to have fired and
     * cleared the flag before any genuine user selection can reach the
     * listener.
     */
    private transient boolean mSuppressSpinnerSelection;

    public SearchRssItemListSV() {
        this(null);
    }

    /**
     * Creates the search list seeded with the given query. A non-empty seed query is
     * applied to the search command in {@link #provideComponent(Provider)}, which
     * auto-executes the search before any user input; {@link #createView(Activity)}
     * mirrors it into the search text field guarded by {@code mIsUpdateQueryText}
     * so no second search is triggered.
     *
     * @param seedQuery the query to pre-fill the search with, may be null
     */
    public SearchRssItemListSV(String seedQuery) {
        mQuerySubject = new SerialBehaviorSubject<>(seedQuery == null ? "" : seedQuery);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mSearchRssItemsCmd = mSvProvider.get(SearchRssItemsCmd.class);
        String query = mQuerySubject.getValue();
        if (query != null && !query.isEmpty()) {
            mSearchRssItemsCmd.setQuery(query);
        }
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRssItemRecyclerViewAdapter = new RssItemRecyclerViewAdapter(mNavigator, this);
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
        // mark-read (manual and auto retention) changes item visibility, not the
        // list context: refresh the same window instead of resetting pagination
        mRxDisposer.add("mSearchRssItemsCmd.itemsMarkedRead",
                mSvProvider.get(RssChangeNotifier.class).getItemsMarkedRead()
                        .subscribe(channelId -> mSearchRssItemsCmd.load(),
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        mRxDisposer.add("mSearchRssItemsCmd.itemsMarkedReadAuto",
                mSvProvider.get(RssChangeNotifier.class).getItemsMarkedReadAuto()
                        .subscribe(channelId -> mSearchRssItemsCmd.load(),
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        // auto-pruned read items (keep-read-items window) leave visible gaps in
        // windows that show read items: refresh the same window
        mRxDisposer.add("mSearchRssItemsCmd.itemsPrunedAuto",
                mSvProvider.get(RssChangeNotifier.class).getItemsPrunedAuto()
                        .subscribe(channelId -> mSearchRssItemsCmd.load(),
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        // favorite toggle while the favorites filter is active changes list
        // membership, not the context: refresh the same window
        mRxDisposer.add("mRssChangeNotifier.updatedRssItem.favoriteFilter",
                mSvProvider.get(RssChangeNotifier.class).getUpdatedRssItem()
                        .subscribe(rssItem -> {
                            if (mSearchRssItemsCmd.getFilterType()
                                    .orElse(BaseRssItemsCmd.FILTER_BY_NONE)
                                    == BaseRssItemsCmd.FILTER_BY_FAVORITE) {
                                mSearchRssItemsCmd.load();
                            }
                        })
        );
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_search_rss, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(mRssItemRecyclerViewAdapter.getConcatAdapter());
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
        // arm BEFORE the adapter/selection wiring: AdapterView fires at least
        // one programmatic onItemSelected on attach/layout, which must not
        // reach setFilterType (see mSuppressSpinnerSelection)
        mSuppressSpinnerSelection = true;
        spinnerFilterBy.setAdapter(adapter);
        mSearchRssItemsCmd.getFilterType()
                .ifPresent(spinnerFilterBy::setSelection);
        spinnerFilterBy.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mSuppressSpinnerSelection) {
                    mSuppressSpinnerSelection = false;
                    return;
                }
                mSearchRssItemsCmd.setFilterType(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (mSuppressSpinnerSelection) {
                    mSuppressSpinnerSelection = false;
                    return;
                }
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
                        .subscribe(rssItems -> mRssItemRecyclerViewAdapter.submitItems(rssItems),
                                throwable ->
                                        mSvProvider.get(ILogger.class).e(TAG,
                                                mSvProvider.getContext()
                                                        .getString(R.string.error_message, throwable.getMessage())))
        );
        EditText editTextSearch = view.findViewById(R.id.edit_text_search);
        String currentQuery = mQuerySubject.getValue();
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
                mQuerySubject.getSubject()
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

    /**
     * Pull-to-refresh entry: re-queries the current window from the top (items
     * may have been marked read meanwhile) without collapsing the accumulated
     * pagination.
     */
    public void refresh() {
        if (mSearchRssItemsCmd != null) {
            mSearchRssItemsCmd.load();
        }
    }
}
