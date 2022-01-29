package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.rx.SerialBehaviorSubject;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute, RequireComponent<Provider>, DialogInterface.OnClickListener {
    private static final String TAG = NewRssChannelSVDialog.class.getName();

    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient NewRssChannelCmd mNewRssChannelCmd;
    private SerialBehaviorSubject<String> mFeedUrlSubject;

    private transient TextWatcher mFeedUrlTextWatcher;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mNewRssChannelCmd = mSvProvider.get(NewRssChannelCmd.class);
        if (mFeedUrlSubject == null) {
            String url;
            Args args = getArgs();
            if (args != null) {
                url = args.getFeedUrl();
            } else {
                url = "";
            }
            mFeedUrlSubject = new SerialBehaviorSubject<>(url);
        }
        if (mFeedUrlTextWatcher == null) {
            mFeedUrlTextWatcher = new TextWatcher() {
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
                    String url = editable.toString();
                    mFeedUrlSubject.onNext(url);
                    mNewRssChannelCmd.validUrl(url);
                }
            };
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.rss_channel_new, container, false);
        EditText feedUrlEditText = view.findViewById(R.id.input_text_url);
        feedUrlEditText.setText(mFeedUrlSubject.getValue());
        feedUrlEditText.addTextChangedListener(mFeedUrlTextWatcher);
        mRxDisposer.add("mNewRssChannelCmd", mNewRssChannelCmd
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
    protected Dialog createDialog(Activity activity) {
        View dialogView = buildView(activity, null);
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(activity);
        alertBuilder.setView(dialogView);
        alertBuilder.setPositiveButton(R.string.add, this);
        alertBuilder.setNegativeButton(android.R.string.cancel, this);
        return alertBuilder.create();
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int id) {
        if (id == DialogInterface.BUTTON_POSITIVE) {
            if (isValid()) {
                addNewFeed();
            } else {
                String validation = mNewRssChannelCmd.getValidationError();
                mSvProvider.get(ILogger.class).i(TAG, validation);
            }
        } else if (id == DialogInterface.BUTTON_NEGATIVE) {
            mFeedUrlSubject.onNext("");
        }
    }

    private Args getArgs() {
        return Args.of(mNavRoute);
    }

    private void addNewFeed() {
        if (mNewRssChannelCmd != null) {
            mNewRssChannelCmd.execute(mFeedUrlSubject.getValue());
        }
    }

    private boolean isValid() {
        if (mNewRssChannelCmd != null) {
            return mNewRssChannelCmd.validUrl(mFeedUrlSubject.getValue());
        }
        return false;
    }

    public static class Args implements Serializable {
        public static Args newArgs(String feedUrl) {
            Args args = new Args();
            args.mFeedUrl = feedUrl;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private String mFeedUrl;

        public String getFeedUrl() {
            return mFeedUrl;
        }
    }
}
