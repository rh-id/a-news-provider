package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelSV extends StatefulView<Activity> {
    private transient NewRssChannelCmd mNewRssChannelCmd;
    private transient RxDisposer mRxDisposer;
    private String mFeedUrl;

    public void addNewFeed() {
        if (mNewRssChannelCmd != null) {
            mNewRssChannelCmd.execute(mFeedUrl);
        }
    }

    public boolean isValid() {
        if (mNewRssChannelCmd != null) {
            return mNewRssChannelCmd.validUrl(mFeedUrl);
        }
        return false;
    }

    public void clearText(View currentView) {
        mFeedUrl = null;
        EditText editText = currentView.findViewById(R.id.input_text_url);
        editText.setText(null);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.rss_channel_new, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        mNewRssChannelCmd = provider.get(NewRssChannelCmd.class);
        EditText feedUrlEditText = view.findViewById(R.id.input_text_url);
        feedUrlEditText.setText(mFeedUrl);
        feedUrlEditText.addTextChangedListener(new TextWatcher() {
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
                mNewRssChannelCmd.validUrl(editable.toString());
                mFeedUrl = editable.toString();
            }
        });
        mRxDisposer.add("mNewRssChannelCmd", mNewRssChannelCmd.getUrlValidation()
                .observeOn(AndroidSchedulers.mainThread()).subscribe(s ->
                {
                    if (!s.isEmpty()) {
                        feedUrlEditText.setError(s);
                    } else {
                        feedUrlEditText.setError(null);
                    }
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
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }
    }
}
