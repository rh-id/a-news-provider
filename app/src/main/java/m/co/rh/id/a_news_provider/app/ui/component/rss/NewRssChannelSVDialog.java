package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.io.Serializable;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute, RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = NewRssChannelSVDialog.class.getName();

    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient NewRssChannelCmd mNewRssChannelCmd;
    private transient ILogger mLogger;
    private SerialBehaviorSubject<String> mFeedUrlSubject;

    private transient TextWatcher mFeedUrlTextWatcher;
    private final SerialBehaviorSubject<Boolean> mAddInFlightSubject = new SerialBehaviorSubject<>(false);

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mNewRssChannelCmd = mSvProvider.get(NewRssChannelCmd.class);
        mLogger = mSvProvider.get(ILogger.class);
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
        Button cancelButton = view.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        Button addButton = view.findViewById(R.id.button_add);
        addButton.setOnClickListener(this);
        ProgressBar progressBar = view.findViewById(R.id.progress_add_feed);
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
        mRxDisposer.add("mAddInFlightSubject", mAddInFlightSubject
                .getSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(inFlight -> {
                    addButton.setEnabled(!inFlight);
                    progressBar.setVisibility(inFlight ? View.VISIBLE : View.GONE);
                }));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mFeedUrlTextWatcher = null;
        mLogger = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_cancel) {
            mFeedUrlSubject.onNext("");
            getNavigator().pop();
        } else if (id == R.id.button_add) {
            addNewFeed();
        }
    }

    private Args getArgs() {
        return Args.of(mNavRoute);
    }

    private void addNewFeed() {
        if (mNewRssChannelCmd == null) {
            return;
        }
        if (isValid()) {
            // the add is in flight (including the redirect-duplicate network probe) -
            // show a loading indicator and block a second Add tap. Cancel pops and
            // disposes the subscription: a probe that has not started yet is cancelled,
            // but one already running on the executor thread is not interrupted and may
            // still enqueue. Cancel stays enabled so a slow probe can be aborted early.
            // The in-flight state survives rotation because the navigator retains this
            // dialog instance and the createView subscription replays the subject's
            // current value onto the freshly wired views.
            mAddInFlightSubject.onNext(true);
            mRxDisposer.add("mNewRssChannelCmdExecute", mNewRssChannelCmd
                    .execute(mFeedUrlSubject.getValue())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((requestUrl, throwable) -> {
                        if (throwable != null) {
                            // validation/duplicate message already shown inline via getUrlValidation()
                            mAddInFlightSubject.onNext(false);
                            if (mLogger != null) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            }
                        } else {
                            mAddInFlightSubject.onNext(false);
                            mFeedUrlSubject.onNext("");
                            getNavigator().pop();
                        }
                    }));
        } else {
            String validation = mNewRssChannelCmd.getValidationError();
            mLogger.i(TAG, validation);
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
