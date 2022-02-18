package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.DownloadImageMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LicensesMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LogMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.OneHandModeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.RssSyncMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.ThemeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.VersionMenuSV;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class SettingsPage extends StatefulView<Activity> {

    @NavInject
    private transient Provider mProvider; // global provider
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ArrayList<StatefulView> mStatefulViews;

    public SettingsPage() {
        mAppBarSV = new AppBarSV();
        mStatefulViews = new ArrayList<>();
        RssSyncMenuSV rssSyncMenuSV = new RssSyncMenuSV();
        mStatefulViews.add(rssSyncMenuSV);
        ThemeMenuSV themeMenuSV = new ThemeMenuSV();
        mStatefulViews.add(themeMenuSV);
        OneHandModeMenuSV oneHandModeMenuSV = new OneHandModeMenuSV();
        mStatefulViews.add(oneHandModeMenuSV);
        DownloadImageMenuSV downloadImageMenuSV = new DownloadImageMenuSV();
        mStatefulViews.add(downloadImageMenuSV);
        LogMenuSV logMenuSV = new LogMenuSV();
        mStatefulViews.add(logMenuSV);
        LicensesMenuSV licensesMenuSV = new LicensesMenuSV();
        mStatefulViews.add(licensesMenuSV);
        VersionMenuSV versionMenuSV = new VersionMenuSV();
        mStatefulViews.add(versionMenuSV);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_settings;
        AppSharedPreferences appSharedPreferences = mProvider.get(AppSharedPreferences.class);
        if (appSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_settings;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.settings));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        ViewGroup content = view.findViewById(R.id.content);
        for (StatefulView statefulView : mStatefulViews) {
            LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            content.addView(statefulView.buildView(activity, content), lparams);
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mStatefulViews != null && !mStatefulViews.isEmpty()) {
            for (StatefulView statefulView : mStatefulViews) {
                statefulView.dispose(activity);
            }
            mStatefulViews.clear();
            mStatefulViews = null;
        }
        mProvider = null;
    }
}
