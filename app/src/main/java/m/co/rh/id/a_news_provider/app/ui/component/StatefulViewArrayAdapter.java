package m.co.rh.id.a_news_provider.app.ui.component;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.anavigator.StatefulView;

public class StatefulViewArrayAdapter extends BaseAdapter {
    private List<StatefulView> mStatefulViewList;

    public StatefulViewArrayAdapter(List<StatefulView> statefulViewList) {
        mStatefulViewList = statefulViewList;
    }

    @Override
    public int getCount() {
        return mStatefulViewList.size();
    }

    @Override
    public StatefulView getItem(int position) {
        return mStatefulViewList.get(position);
    }

    @Override
    public int getItemViewType(int position) {
        // each statefulView have different view type
        return position;
    }

    @Override
    public long getItemId(int position) {
        // each stateful view is different
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        StatefulView statefulView = getItem(position);
        return statefulView.buildView(UiUtils.getActivity(parent), parent);
    }
}
