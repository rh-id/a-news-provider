package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

public class RssItemRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_NEWS_ITEM = 0;
    private static final int VIEW_TYPE_EMPTY_TEXT = 1;
    private Handler mHandler;
    private PagedRssItemsCmd mPagedRssItemsCmd;

    public RssItemRecyclerViewAdapter(Handler handler, PagedRssItemsCmd pagedRssItemsCmd) {
        mHandler = handler;
        mPagedRssItemsCmd = pagedRssItemsCmd;
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
            rssItemSV.provideNavigator(BaseApplication.of(activity)
                    .getNavigator(activity));
            View view = rssItemSV.buildView(activity, parent);
            return new RssItemViewHolder(view, rssItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RssItemViewHolder) {
            ArrayList<RssItem> rssItems = mPagedRssItemsCmd.getAllRssItems();
            ((RssItemViewHolder) holder).setRssItem(rssItems.get(position));
            if (rssItems.size() - 1 == position) {
                mHandler.post(mPagedRssItemsCmd::loadNextPage);
            }
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

    private boolean isEmpty() {
        if (mPagedRssItemsCmd == null) {
            return true;
        }
        return mPagedRssItemsCmd.getAllRssItems().size() == 0;
    }

    protected static class RssItemViewHolder extends RecyclerView.ViewHolder {
        private RssItemSV mRssItemSV;

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
