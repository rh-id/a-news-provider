package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import java.io.Serializable;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.EditRssLinkCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

public class EditRssLinkSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute, RequireComponent<Provider>, View.OnClickListener {
    private static final String TAG = EditRssLinkSVDialog.class.getName();

    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient EditRssLinkCmd mEditRssLinkCmd;
    private SerialBehaviorSubject<String> mUrlSubject;

    private transient TextWatcher mUrlTextWatcher;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mEditRssLinkCmd = mSvProvider.get(EditRssLinkCmd.class);
        if (mUrlSubject == null) {
            String url;
            Args args = getArgs();
            if (args != null) {
                url = args.getRssItem().link;
            } else {
                url = "";
            }
            mUrlSubject = new SerialBehaviorSubject<>(url);
        }
        if (mUrlTextWatcher == null) {
            mUrlTextWatcher = new TextWatcher() {
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
                    mUrlSubject.onNext(url);
                    mEditRssLinkCmd.validUrl(url);
                }
            };
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.edit_rss_link, container, false);
        EditText urlEditText = rootLayout.findViewById(R.id.input_text_url);
        urlEditText.setText(mUrlSubject.getValue());
        urlEditText.addTextChangedListener(mUrlTextWatcher);
        Button cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        Button saveButton = rootLayout.findViewById(R.id.button_save);
        saveButton.setOnClickListener(this);
        Button saveAndOpenButton = rootLayout.findViewById(R.id.button_save_and_open);
        saveAndOpenButton.setOnClickListener(this);
        mRxDisposer.add("createView_editRssLink", mEditRssLinkCmd
                .getUrlValidation()
                .observeOn(AndroidSchedulers.mainThread()).subscribe(s ->
                {
                    if (!s.isEmpty()) {
                        urlEditText.setError(s);
                    } else {
                        urlEditText.setError(null);
                    }
                })
        );
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mUrlTextWatcher = null;
    }

    public Args getArgs() {
        return Args.of(mNavRoute);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_cancel) {
            getNavigator().pop();
        } else if (id == R.id.button_save) {
            saveUrl();
        } else if (id == R.id.button_save_and_open) {
            saveUrl();
            Activity activity = UiUtils.getActivity(view);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUrlSubject.getValue()));
            activity.startActivity(browserIntent);
        }
    }

    private void saveUrl() {
        if (isValid()) {
            Args args = getArgs();
            mRxDisposer
                    .add(".saveUrl.editRssLinkCmd.execute",
                            mEditRssLinkCmd.execute(args.mRssItem.id, mUrlSubject.getValue())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((s, throwable) -> {
                                        if (throwable != null) {
                                            mSvProvider.get(ILogger.class).e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            mSvProvider.get(ILogger.class).i(TAG,
                                                    mSvProvider.getContext().getString(R.string.success_save_link));
                                        }
                                        getNavigator().pop();
                                    })
                    );
        } else {
            String validation = mEditRssLinkCmd.getValidationError();
            mSvProvider.get(ILogger.class).i(TAG, validation);
        }
    }

    private boolean isValid() {
        if (mEditRssLinkCmd != null) {
            return mEditRssLinkCmd.validUrl(mUrlSubject.getValue());
        }
        return false;
    }

    public static class Args implements Serializable {
        public static Args newArgs(RssItem rssItem) {
            Args args = new Args();
            args.mRssItem = rssItem;
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

        private RssItem mRssItem;

        public RssItem getRssItem() {
            return mRssItem;
        }
    }
}
