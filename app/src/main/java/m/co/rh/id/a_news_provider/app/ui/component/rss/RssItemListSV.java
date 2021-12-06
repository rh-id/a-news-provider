package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class RssItemListSV extends StatefulView<Activity> {
    @NavInject
    private transient Provider mProvider;

    private transient Provider mSvProvider;

    public void refresh() {
        if (mSvProvider != null) {
            mSvProvider.get(PagedRssItemsCmd.class).reload();
        }
    }

    public Flowable<ArrayList<RssItem>> observeRssItems() {
        if (mSvProvider != null) {
            return mSvProvider.get(PagedRssItemsCmd.class).getRssItems();
        }
        return Flowable.fromSupplier(ArrayList::new);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(StatefulViewProvider.class);
        mSvProvider.get(PagedRssItemsCmd.class).load();
        View view = activity.getLayoutInflater().inflate(R.layout.list_rss_item, container, false);
        RssItemRecyclerViewAdapter rssItemRecyclerViewAdapter = new RssItemRecyclerViewAdapter(mSvProvider.get(PagedRssItemsCmd.class));
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(rssItemRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        Spinner spinnerFilterBy = view.findViewById(R.id.spinner_filter_by);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity,
                R.array.array_filter_by, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterBy.setAdapter(adapter);
        mSvProvider.get(PagedRssItemsCmd.class).getFilterType()
                .ifPresent(spinnerFilterBy::setSelection);
        spinnerFilterBy.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSvProvider.get(PagedRssItemsCmd.class).setFilterType(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSvProvider.get(PagedRssItemsCmd.class).setFilterType(null);
            }
        });
        mSvProvider.get(RxDisposer.class).add("mPagedRssItemsCmd.getRssItems",
                mSvProvider.get(PagedRssItemsCmd.class).getRssItems()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItems ->
                                        rssItemRecyclerViewAdapter.notifyDataSetChanged(),
                                throwable ->
                                        Toast.makeText(activity, activity.getString(R.string.error_message, throwable.getMessage()),
                                                Toast.LENGTH_LONG).show()
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
        mProvider = null;
    }
}