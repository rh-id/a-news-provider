package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class ThemeMenuSV extends StatefulView<Activity> {

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_theme, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        int selectedRadioId = getSelectedRadioId(appSharedPreferences);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        radioGroup.check(selectedRadioId);
        radioGroup.setOnCheckedChangeListener((radioGroup1, i) -> {
            int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (i == R.id.radio_light) {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (i == R.id.radio_dark) {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
            }
            appSharedPreferences
                    .setSelectedTheme(selectedTheme);
        });
        return view;
    }

    private int getSelectedRadioId(AppSharedPreferences appSharedPreferences) {
        int theme = appSharedPreferences.getSelectedTheme();
        int result = R.id.radio_system;
        if (theme == AppCompatDelegate.MODE_NIGHT_NO) {
            result = R.id.radio_light;
        } else if (theme == AppCompatDelegate.MODE_NIGHT_YES) {
            result = R.id.radio_dark;
        }
        return result;
    }
}
