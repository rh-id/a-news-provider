package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.Serializable;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelSVDialog extends StatefulViewDialog<Activity> implements DialogInterface.OnClickListener {

    @NavInject
    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private String mFeedUrl;
    private transient BehaviorSubject<String> mFeedUrlSubject;


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

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Args args = getArgs();
        if (args != null) {
            mFeedUrl = args.getFeedUrl();
        } else {
            mFeedUrl = "";
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.rss_channel_new, container, false);
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(StatefulViewProvider.class);
        if (mFeedUrlSubject == null) {
            mFeedUrlSubject = BehaviorSubject.createDefault(mFeedUrl);
        } else {
            mFeedUrlSubject.onNext(mFeedUrl);
        }
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
        mSvProvider.get(RxDisposer.class).add("feedUrlChanged",
                mFeedUrlSubject.subscribe(feedUrlEditText::setText));
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
                String validation = mSvProvider.get(NewRssChannelCmd.class).getLatestUrlValidation();
                Toast.makeText(mSvProvider.getContext(), validation, Toast.LENGTH_LONG).show();
            }
        } else if (id == DialogInterface.BUTTON_NEGATIVE) {
            mFeedUrlSubject.onNext("");
        }
    }

    public Args getArgs() {
        return Args.of(mNavRoute);
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
