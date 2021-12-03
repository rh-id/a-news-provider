package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class OneHandModeMenuSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;

    private transient SwitchMaterial mSwitchMaterial;

    @Override
    public void provideComponent(Provider provider) {
        mProvider = provider;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_one_hand_mode, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSwitchMaterial = view.findViewById(R.id.switchm_one_hand_mode);
        mSwitchMaterial.setChecked(mAppSharedPreferences.isOneHandMode());
        mSwitchMaterial.setOnCheckedChangeListener(this);
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mProvider = null;
        mSwitchMaterial = null;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.container_menu) {
            mSwitchMaterial.setChecked(!mSwitchMaterial.isChecked());
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        mAppSharedPreferences.setOneHandMode(checked);
    }
}
