package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

@SuppressWarnings({"unchecked", "rawtypes"})
public class RssItemRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_NEWS_ITEM = 0;
    private static final int VIEW_TYPE_EMPTY_TEXT = 1;
    private final PagedRssItemsCmd mPagedRssItemsCmd;
    private final INavigator mNavigator;
    private final StatefulView mParentStatefulView;
    private final List<StatefulView> mCreatedSvs;

    public RssItemRecyclerViewAdapter(PagedRssItemsCmd pagedRssItemsCmd, INavigator navigator, StatefulView parentStatefulView) {
        mPagedRssItemsCmd = pagedRssItemsCmd;
        mNavigator = navigator;
        mParentStatefulView = parentStatefulView;
        mCreatedSvs = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (VIEW_TYPE_EMPTY_TEXT == viewType) {
            View view = UiUtils.getActivity(parent).getLayoutInflater().inflate(R.layout.no_record, parent, false);
            return new EmptyViewHolder(view);
        } else {
            Activity activity = UiUtils.getActivity(parent);
            RssItemSV rssItemSV = new RssItemSV();
            mNavigator.injectRequired(mParentStatefulView, rssItemSV);
            mCreatedSvs.add(rssItemSV);
            View view = rssItemSV.buildView(activity, parent);
            return new RssItemViewHolder(view, rssItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RssItemViewHolder) {
            ArrayList<RssItem> rssItems = mPagedRssItemsCmd.getAllRssItems();
            ((RssItemViewHolder) holder).setRssItem(rssItems.get(position));
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mPagedRssItemsCmd.getAllRssItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_NEWS_ITEM;
    }

    public void dispose(Activity activity) {
        if (!mCreatedSvs.isEmpty()) {
            for (StatefulView sv : mCreatedSvs) {
                sv.dispose(activity);
            }
            mCreatedSvs.clear();
        }
    }

    private boolean isEmpty() {
        if (mPagedRssItemsCmd == null) {
            return true;
        }
        return mPagedRssItemsCmd.getAllRssItems().size() == 0;
    }

    protected static class RssItemViewHolder extends RecyclerView.ViewHolder {
        private final RssItemSV mRssItemSV;

        public RssItemViewHolder(@NonNull View itemView, @NonNull RssItemSV rssItemSV) {
            super(itemView);
            mRssItemSV = rssItemSV;
        }

        void setRssItem(RssItem rssItem) {
            mRssItemSV.setRssItem(rssItem);
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
