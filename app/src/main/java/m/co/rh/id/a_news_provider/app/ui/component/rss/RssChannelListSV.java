package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class RssChannelListSV extends StatefulView<Activity> {

    private transient RxDisposer mRxDisposer;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_rss_channel, container, false);
        RssChannelRecyclerViewAdapter rssChannelRecyclerViewAdapter = new RssChannelRecyclerViewAdapter();
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(rssChannelRecyclerViewAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        mRxDisposer.add("rssChannelUnReadCount",
                provider.get(RssChangeNotifier.class).rssChannelUnReadCount()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelIntegerMap ->
                                rssChannelRecyclerViewAdapter.setItems(rssChannelIntegerMap))
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
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }
    }
}