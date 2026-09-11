package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;

public class SearchRssItemsCmd extends BaseRssItemsCmd {
    /**
     * Returns the tag used for logging in this command.
     *
     * @return the class name used as the log tag
     */
    @Override
    protected String getTag() {
        return SearchRssItemsCmd.class.getName();
    }

    private final BehaviorSubject<String> mQuerySubject;

    public SearchRssItemsCmd(Provider provider) {
        super(provider, FILTER_BY_NONE, false);
        mQuerySubject = BehaviorSubject.createDefault("");
    }

    /**
     * Sets the search query, then reloads the results from the beginning.
     * Does nothing if the query is unchanged from the current value.
     *
     * @param query the search query to set
     */
    public void setQuery(String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.equals(mQuerySubject.getValue())) {
            return;
        }
        mQuerySubject.onNext(trimmedQuery);
        reload();
    }

    /**
     * Returns the current search query.
     *
     * @return the current trimmed search query
     */
    public String getQuery() {
        return mQuerySubject.getValue();
    }

    public Flowable<ArrayList<RssItem>> getResults() {
        return Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER);
    }

    @NonNull
    @Override
    protected ArrayList<RssItem> loadItems() {
        String query = mQuerySubject.getValue();
        if (query == null || query.isEmpty()) {
            return new ArrayList<>();
        }
        Integer filterType = getFilterTypeValue();
        Integer isRead = toIsRead(filterType);
        Integer isFavorite = toIsFavorite(filterType);
        Integer sortOrder = getSortOrderValue();
        boolean asc = sortOrder != null && sortOrder == SORT_ORDER_OLDEST;
        List<RssItem> list = asc
                ? mRssDao.searchRssItemsWithLimitAsc(escapeLikeQuery(query), null, isRead, isFavorite, mLimit)
                : mRssDao.searchRssItemsWithLimit(escapeLikeQuery(query), null, isRead, isFavorite, mLimit);
        ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            rssItemArrayList.addAll(list);
        }
        return rssItemArrayList;
    }

    private static String escapeLikeQuery(String query) {
        String escapedQuery = query.replace("\\", "\\\\");
        escapedQuery = escapedQuery.replace("%", "\\%");
        escapedQuery = escapedQuery.replace("_", "\\_");
        return escapedQuery;
    }
}
