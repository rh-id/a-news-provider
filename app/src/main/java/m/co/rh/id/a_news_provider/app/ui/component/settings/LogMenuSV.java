package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;

public class LogMenuSV extends StatefulView<Activity> implements View.OnClickListener {

    @NavInject
    private transient INavigator mNavigator;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_log_file, container, false);
        Button button = view.findViewById(R.id.menu_log_file);
        button.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.menu_log_file) {
            mNavigator.push((args, activity1) -> new LogPage());
        }
    }
}
