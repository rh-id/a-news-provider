package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.command.RenameRssFeedCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class RssChannelItemSV extends StatefulView<Activity> {

    private transient BehaviorSubject<Map.Entry<RssChannel, Integer>> mRssChannelCountSubject;
    private transient BehaviorSubject<Boolean> mEditModeSubject;
    private transient RxDisposer mRxDisposer;

    public RssChannelItemSV() {
        mRssChannelCountSubject = BehaviorSubject.create();
        mEditModeSubject = BehaviorSubject.createDefault(false);
    }

    public void setRssChannelCount(Map.Entry<RssChannel, Integer> rssChannelCount) {
        if (mRssChannelCountSubject == null) {
            mRssChannelCountSubject = BehaviorSubject.createDefault(rssChannelCount);
        } else {
            mRssChannelCountSubject.onNext(rssChannelCount);
        }
    }


    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_channel, container, false);
        TextView textName = view.findViewById(R.id.text_name);
        TextView textCount = view.findViewById(R.id.text_count);
        EditText editName = view.findViewById(R.id.input_text_name);
        Button buttonRename = view.findViewById(R.id.button_rename);
        Button buttonDelete = view.findViewById(R.id.button_delete);
        Button buttonCancel = view.findViewById(R.id.button_cancel);

        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        RenameRssFeedCmd renameRssFeedCmd = provider.get(RenameRssFeedCmd.class);
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        view.setOnClickListener(view1 -> {
            Map.Entry<RssChannel, Integer> entry = mRssChannelCountSubject.getValue();
            if (entry != null) {
                rssChangeNotifier.selectRssChannel(entry.getKey());
            }
        });
        view.setLongClickable(true);
        view.setOnLongClickListener(view12 -> {
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
            return true;
        });
        editName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                // leave blank
            }

            @Override
            public void afterTextChanged(Editable editable) {
                renameRssFeedCmd.validName(editable.toString());
            }
        });
        buttonRename.setOnClickListener(view13 ->
        {
            String feedName = editName.getText().toString();
            if (renameRssFeedCmd.validName(feedName)) {
                Map.Entry<RssChannel, Integer> rssChannelCount = mRssChannelCountSubject.getValue();
                if (rssChannelCount != null) {
                    renameRssFeedCmd.execute(rssChannelCount.getKey().id, feedName);
                }
            }
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
        });
        buttonDelete.setOnClickListener(view13 ->
        {
            Map.Entry<RssChannel, Integer> rssChannelCount = mRssChannelCountSubject.getValue();
            if (rssChannelCount != null) {
                rssChangeNotifier.deleteRssChannel(rssChannelCount.getKey());
            }
            mEditModeSubject.onNext(!mEditModeSubject.getValue());
        });
        buttonCancel.setOnClickListener(view13 ->
                mEditModeSubject.onNext(!mEditModeSubject.getValue()));
        mRxDisposer.add("renameRssFeedCmd.getNameValidation",
                renameRssFeedCmd.liveNameValidation()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(s -> {
                            if (!s.isEmpty()) {
                                editName.setError(s);
                            } else {
                                editName.setError(null);
                            }
                        })
        );
        mRxDisposer.add("renameRssFeedCmd.getRssChannel",
                renameRssFeedCmd.liveRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannel -> Toast
                                        .makeText(activity,
                                                activity.getString(R.string.rename_feed_to, rssChannel.feedName)
                                                , Toast.LENGTH_LONG).show(),
                                throwable -> Toast
                                        .makeText(activity,
                                                throwable.getMessage()
                                                , Toast.LENGTH_LONG).show())
        );
        mRxDisposer.add("mEditModeSubject", mEditModeSubject.subscribe(editMode -> {
                    if (editMode) {
                        textName.setVisibility(View.GONE);
                        editName.setVisibility(View.VISIBLE);
                        buttonRename.setVisibility(View.VISIBLE);
                        buttonDelete.setVisibility(View.VISIBLE);
                        buttonCancel.setVisibility(View.VISIBLE);
                    } else {
                        textName.setVisibility(View.VISIBLE);
                        editName.setVisibility(View.GONE);
                        buttonRename.setVisibility(View.GONE);
                        buttonDelete.setVisibility(View.GONE);
                        buttonCancel.setVisibility(View.GONE);
                    }
                })
        );
        mRxDisposer.add("rssChannelUiChange", Flowable.combineLatest(
                Flowable.fromObservable(mRssChannelCountSubject, BackpressureStrategy.BUFFER)
                        .observeOn(AndroidSchedulers.mainThread()),
                rssChangeNotifier.selectedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread()),
                (rssChannelCountEntry, rssChannelOptional) -> {
                    RssChannel rssChannel = rssChannelCountEntry.getKey();
                    if (rssChannel.title != null) {
                        textName.setText(rssChannel.feedName);
                        editName.setText(rssChannel.feedName);
                    }
                    textCount.setText(rssChannelCountEntry.getValue().toString());

                    Map.Entry<RssChannel, Integer> entry = rssChannelCountEntry;
                    int selectedColor = UiUtils.getColorFromAttribute(activity, R.attr.colorOnPrimary);
                    if (entry != null && rssChannelOptional.isPresent()) {
                        if (rssChannelOptional.get().id.equals(entry.getKey().id)) {
                            selectedColor = activity.getResources().getColor(R.color.daynight_gray_300_gray_600);
                        }
                    }
                    view.setBackgroundColor(selectedColor);
                    return true;
                }).subscribe(aBoolean -> {
                })
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
        if (mRssChannelCountSubject != null) {
            mRssChannelCountSubject.onComplete();
            mRssChannelCountSubject = null;
        }
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }
    }
}
