package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.AppSharedPreferences;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LicensesMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LogMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.OneHandModeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.RssSyncMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.ThemeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.VersionMenuSV;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class SettingsPage extends StatefulView<Activity> implements RequireNavigator {

    private AppBarSV mAppBarSV;
    private ArrayList<StatefulView> mStatefulViews;

    @Override
    public void provideNavigator(INavigator navigator) {
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(navigator);
        } else {
            mAppBarSV.provideNavigator(navigator);
        }
        if (mStatefulViews == null) {
            mStatefulViews = new ArrayList<>();
            RssSyncMenuSV rssSyncMenuSV = new RssSyncMenuSV();
            mStatefulViews.add(rssSyncMenuSV);
            ThemeMenuSV themeMenuSV = new ThemeMenuSV();
            mStatefulViews.add(themeMenuSV);
            OneHandModeMenuSV oneHandModeMenuSV = new OneHandModeMenuSV();
            mStatefulViews.add(oneHandModeMenuSV);
            LogMenuSV logMenuSV = new LogMenuSV(navigator);
            mStatefulViews.add(logMenuSV);
            LicensesMenuSV licensesMenuSV = new LicensesMenuSV(navigator);
            mStatefulViews.add(licensesMenuSV);
            VersionMenuSV versionMenuSV = new VersionMenuSV();
            mStatefulViews.add(versionMenuSV);
        } else {
            for (StatefulView statefulView : mStatefulViews) {
                if (statefulView instanceof RequireNavigator) {
                    ((RequireNavigator) statefulView).provideNavigator(navigator);
                }
            }
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_settings;
        Provider provider = BaseApplication.of(activity).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        if (appSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_settings;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.settings));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        ViewGroup content = view.findViewById(R.id.content);
        for (StatefulView statefulView : mStatefulViews) {
            content.addView(statefulView.buildView(activity, content));
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
    }
}
