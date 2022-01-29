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
import java.util.Map;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

public class RssChannelRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_CHANNEL_ITEM = 0;
    private static final int VIEW_TYPE_EMPTY_TEXT = 1;
    private Map<RssChannel, Integer> mRssChannelCountMap;
    private INavigator mNavigator;
    private StatefulView mParentSv;
    private List<StatefulView> mCreatedSv;

    public RssChannelRecyclerViewAdapter(INavigator navigator, StatefulView parent) {
        mNavigator = navigator;
        mParentSv = parent;
        mCreatedSv = new ArrayList<>();
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
            RssChannelItemSV rssChannelItemSV = new RssChannelItemSV();
            mNavigator.injectRequired(mParentSv, rssChannelItemSV);
            View view = rssChannelItemSV.buildView(activity, parent);
            mCreatedSv.add(rssChannelItemSV);
            return new RssChannelViewHolder(view, rssChannelItemSV);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RssChannelViewHolder) {
            int i = 0;
            Map.Entry<RssChannel, Integer> currentEntry = null;
            for (Map.Entry<RssChannel, Integer> entry : mRssChannelCountMap.entrySet()) {
                if (i == position) {
                    currentEntry = entry;
                    break;
                }
                i++;
            }
            ((RssChannelViewHolder) holder).setRssChannelCount(currentEntry);
        }
    }

    @Override
    public int getItemCount() {
        if (isEmpty()) {
            return 1;
        }
        return mRssChannelCountMap.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (isEmpty()) {
            return VIEW_TYPE_EMPTY_TEXT;
        }
        return VIEW_TYPE_CHANNEL_ITEM;
    }

    private boolean isEmpty() {
        if (mRssChannelCountMap == null) {
            return true;
        }
        return mRssChannelCountMap.isEmpty();
    }

    public void setItems(Map<RssChannel, Integer> rssChannelCountMap) {
        mRssChannelCountMap = rssChannelCountMap;
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

    protected static class RssChannelViewHolder extends RecyclerView.ViewHolder {
        private RssChannelItemSV mRssChannelItemSV;

        public RssChannelViewHolder(@NonNull View itemView, @NonNull RssChannelItemSV rssChannelItemSV) {
            super(itemView);
            mRssChannelItemSV = rssChannelItemSV;
        }

        void setRssChannelCount(Map.Entry<RssChannel, Integer> rssChannelCount) {
            mRssChannelItemSV.setRssChannelCount(rssChannelCount);
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
