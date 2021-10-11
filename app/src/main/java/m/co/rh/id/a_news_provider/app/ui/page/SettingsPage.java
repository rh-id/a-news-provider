package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.StatefulViewArrayAdapter;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LicensesMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.RssSyncMenuSV;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;

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
            LicensesMenuSV licensesMenuSV = new LicensesMenuSV(navigator);
            mStatefulViews.add(licensesMenuSV);
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
        View view = activity.getLayoutInflater().inflate(R.layout.page_settings, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.settings));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        ListView listView = view.findViewById(R.id.listView);
        listView.setAdapter(new StatefulViewArrayAdapter(mStatefulViews));
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
