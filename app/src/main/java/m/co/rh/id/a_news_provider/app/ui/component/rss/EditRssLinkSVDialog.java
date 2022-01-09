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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.EditRssLinkCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class EditRssLinkSVDialog extends StatefulViewDialog<Activity> implements View.OnClickListener {
    private static final String TAG = EditRssLinkSVDialog.class.getName();

    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private String mUrl;
    private transient BehaviorSubject<String> mUrlSubject;


    public boolean isValid() {
        if (mSvProvider != null) {
            return mSvProvider.get(EditRssLinkCmd.class).validUrl(mUrl);
        }
        return false;
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Args args = getArgs();
        if (args != null) {
            mUrl = args.getRssItem().link;
        } else {
            mUrl = "";
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.edit_rss_link, container, false);
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(StatefulViewProvider.class);
        if (mUrlSubject == null) {
            mUrlSubject = BehaviorSubject.createDefault(mUrl);
        } else {
            mUrlSubject.onNext(mUrl);
        }
        EditText urlEditText = rootLayout.findViewById(R.id.input_text_url);
        urlEditText.setText(mUrl);
        urlEditText.addTextChangedListener(new TextWatcher() {
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
                mSvProvider.get(EditRssLinkCmd.class).validUrl(editable.toString());
                mUrl = editable.toString();
            }
        });
        Button cancelButton = rootLayout.findViewById(R.id.button_cancel);
        cancelButton.setOnClickListener(this);
        Button saveButton = rootLayout.findViewById(R.id.button_save);
        saveButton.setOnClickListener(this);
        Button saveAndOpenButton = rootLayout.findViewById(R.id.button_save_and_open);
        saveAndOpenButton.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class).add("createView_editRssLink", mSvProvider.get(EditRssLinkCmd.class)
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
        mSvProvider.get(RxDisposer.class).add("createView_urlChanged",
                mUrlSubject.subscribe(urlEditText::setText));
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
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
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mUrl));
            activity.startActivity(browserIntent);
        }
    }

    private void saveUrl() {
        if (isValid()) {
            Args args = getArgs();
            mSvProvider.get(RxDisposer.class)
                    .add(".saveUrl.editRssLinkCmd.execute",
                            mSvProvider.get(EditRssLinkCmd.class).execute(args.mRssItem.id, mUrl)
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
            String validation = mSvProvider.get(EditRssLinkCmd.class).getValidationError();
            mSvProvider.get(ILogger.class).i(TAG, validation);
        }
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
