package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;

public class LicensesMenuSV extends StatefulView<Activity> implements RequireNavigator {

    private transient INavigator mNavigator;

    public LicensesMenuSV(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_license, container, false);
        Button button = view.findViewById(R.id.menu_licenses);
        button.setOnClickListener(view1 -> mNavigator.push((args, activity1) -> new LicensesPage(), null, null));
        return view;
    }
}
