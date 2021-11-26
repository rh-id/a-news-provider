package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProviderModule;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelSV extends StatefulView<Activity> {
    private transient Provider mSvProvider;
    private String mFeedUrl;

    public void addNewFeed() {
        if (mSvProvider != null) {
            mSvProvider.get(NewRssChannelCmd.class).execute(mFeedUrl);
        }
    }

    public boolean isValid() {
        if (mSvProvider != null) {
            return mSvProvider.get(NewRssChannelCmd.class).validUrl(mFeedUrl);
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
        mSvProvider = Provider.createProvider(activity, new StatefulViewProviderModule(activity));
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
                mSvProvider.get(NewRssChannelCmd.class).validUrl(editable.toString());
                mFeedUrl = editable.toString();
            }
        });
        mSvProvider.get(RxDisposer.class).add("mNewRssChannelCmd", mSvProvider.get(NewRssChannelCmd.class)
                .getUrlValidation()
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

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    public void setFeedUrl(String url) {
        mFeedUrl = url;
    }
}
