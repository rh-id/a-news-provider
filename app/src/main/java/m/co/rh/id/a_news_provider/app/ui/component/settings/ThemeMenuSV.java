package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class ThemeMenuSV extends StatefulView<Activity> implements CompoundButton.OnCheckedChangeListener {

    private transient AppSharedPreferences mAppSharedPreferences;
    private transient Activity mActivity;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        mActivity = activity;
        View view = activity.getLayoutInflater().inflate(R.layout.menu_theme, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        mAppSharedPreferences = provider.get(AppSharedPreferences.class);
        int selectedRadioId = getSelectedRadioId(mAppSharedPreferences);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        CompoundButton switchDynamicColors = view.findViewById(R.id.switch_dynamic_colors);
        radioGroup.check(selectedRadioId);
        radioGroup.setOnCheckedChangeListener((radioGroup1, i) -> {
            int selectedTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (i == R.id.radio_light) {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (i == R.id.radio_dark) {
                selectedTheme = AppCompatDelegate.MODE_NIGHT_YES;
            }
            mAppSharedPreferences
                    .setSelectedTheme(selectedTheme);
        });
        // apply current value before attaching the listener so initialization
        // does not write back to the shared preferences nor recreate the activity
        switchDynamicColors.setChecked(mAppSharedPreferences.isDynamicColorsEnabled());
        switchDynamicColors.setOnCheckedChangeListener(this);
        // dynamic colors need Android 12+ support, hide the option otherwise
        if (!DynamicColors.isDynamicColorAvailable()) {
            switchDynamicColors.setVisibility(View.GONE);
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mActivity = null;
        mAppSharedPreferences = null;
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        int id = compoundButton.getId();
        if (id == R.id.switch_dynamic_colors) {
            mAppSharedPreferences.setDynamicColorsEnabled(isChecked);
            Activity activity = mActivity;
            if (activity != null) {
                activity.recreate();
            }
        }
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
