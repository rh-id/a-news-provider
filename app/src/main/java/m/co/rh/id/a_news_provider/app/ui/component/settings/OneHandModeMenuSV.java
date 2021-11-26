package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class OneHandModeMenuSV extends StatefulView<Activity> {

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_one_hand_mode, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        SwitchMaterial aSwitch = view.findViewById(R.id.switchm_one_hand_mode);
        aSwitch.setChecked(appSharedPreferences.isOneHandMode());
        aSwitch.setOnCheckedChangeListener((compoundButton, checked) ->
                appSharedPreferences.setOneHandMode(checked));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 ->
                aSwitch.setChecked(!aSwitch.isChecked()));
        return view;
    }
}
