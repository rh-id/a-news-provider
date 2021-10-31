package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import m.co.rh.id.a_news_provider.BuildConfig;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.anavigator.StatefulView;

public class VersionMenuSV extends StatefulView<Activity> {

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_version, container, false);
        TextView textVersion = view.findViewById(R.id.text_version);
        textVersion.setText(BuildConfig.VERSION_NAME + "+" + BuildConfig.VERSION_CODE);
        return view;
    }
}
