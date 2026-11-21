package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.model.FeedHealthItem;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

/**
 * Feed-health dashboard list adapter (issue #42): renders the sorted
 * {@link FeedHealthItem} list delivered by FeedHealthCmd. A dumb renderer - it
 * performs NO ordering or status logic, the command emits the final order.
 * Mirrors RssChannelRecyclerViewAdapter: an empty feed list shows the shared
 * no_record layout as the single row, and every visible row is its own
 * StatefulView (FeedHealthItemSV) injected with the required components and
 * tracked for disposal.
 */
public class FeedHealthRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_FEED_HEALTH_ITEM = 0;
    private static final int VIEW_TYPE_EMPTY_TEXT = 1;
    private List<FeedHealthItem> mFeedHealthItemList;
    private INavigator mNavigator;
    private StatefulView mParentSv;
    private List<StatefulView> mCreatedSv;

    public FeedHealthRecyclerViewAdapter(INavigator navigator, StatefulView parent) {
        mNavigator = navigator;
        mParentSv = parent;
        mCreatedSv = new ArrayList<>();
        mFeedHealthItemList = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = UiUtils.getActivity(parent).getLayoutInflater().inflate(R.layout.no_record, parent, false);
            TextView noRecordText = view.findViewById(R.id.text_no_record);
            noRecordText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            return new EmptyViewHolder(view);
        } else {
            Activity activity = UiUtils.getActivity(parent);
            FeedHealthItemSV feedHealthItemSV = new FeedHealthItemSV();
            mNavigator.injectRequired(mParentSv, feedHealthItemSV);
            View view = feedHealthItemSV.buildView(activity, parent);
            mCreatedSv.add(feedHealthItemSV);
            return new FeedHealthViewHolder(view, feedHealthItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FeedHealthViewHolder && position < mFeedHealthItemList.size()) {
            FeedHealthItem currentFeedHealthItem = mFeedHealthItemList.get(position);
            ((FeedHealthViewHolder) holder).setFeedHealthItem(currentFeedHealthItem);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mFeedHealthItemList.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_FEED_HEALTH_ITEM;
    }

    private boolean isEmpty() {
        return mFeedHealthItemList == null || mFeedHealthItemList.isEmpty();
    }

    public void setItems(List<FeedHealthItem> feedHealthItemList) {
        mFeedHealthItemList = feedHealthItemList;
        if (mFeedHealthItemList == null) {
            mFeedHealthItemList = new ArrayList<>();
        }
        notifyDataSetChanged();
    }

    @SuppressWarnings("unchecked")
    public void dispose(Activity activity) {
        if (!mCreatedSv.isEmpty()) {
            for (StatefulView sv : mCreatedSv) {
                sv.dispose(activity);
            }
            mCreatedSv.clear();
        }
    }

    protected static class FeedHealthViewHolder extends RecyclerView.ViewHolder {
        private FeedHealthItemSV mFeedHealthItemSV;

        public FeedHealthViewHolder(@NonNull View itemView, @NonNull FeedHealthItemSV feedHealthItemSV) {
            super(itemView);
            mFeedHealthItemSV = feedHealthItemSV;
        }

        void setFeedHealthItem(FeedHealthItem feedHealthItem) {
            mFeedHealthItemSV.setFeedHealthItem(feedHealthItem);
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
