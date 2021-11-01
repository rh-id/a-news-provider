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
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class RssItemListSV extends StatefulView<Activity> {

    private transient PagedRssItemsCmd mPagedRssItemsCmd;
    private transient RxDisposer mRxDisposer;

    public void refresh() {
        if (mPagedRssItemsCmd != null) {
            mPagedRssItemsCmd.reload();
        }
    }

    public Flowable<ArrayList<RssItem>> observeRssItems() {
        if (mPagedRssItemsCmd != null) {
            return mPagedRssItemsCmd.getRssItems();
        }
        return null;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_rss_item, container, false);
        RssItemRecyclerViewAdapter rssItemRecyclerViewAdapter = new RssItemRecyclerViewAdapter();
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(rssItemRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        mPagedRssItemsCmd = provider.get(PagedRssItemsCmd.class);
        mPagedRssItemsCmd.load();
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
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItems ->
                                        rssItemRecyclerViewAdapter.setItems(mPagedRssItemsCmd),
                                throwable ->
                                        Toast.makeText(activity, activity.getString(R.string.error_message, throwable.getMessage()),
                                                Toast.LENGTH_LONG).show()
                        )
        );
        return view;
    }

    private void prepareDisposer(Provider provider) {
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
        }
        mRxDisposer = provider.get(RxDisposer.class);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mPagedRssItemsCmd = null;
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }

    }
}