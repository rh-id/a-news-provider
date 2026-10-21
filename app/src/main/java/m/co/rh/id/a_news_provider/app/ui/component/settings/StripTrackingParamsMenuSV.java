package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings toggle for stripping tracking parameters from links (issue #76).
 * Toggling only writes the preference: the sanitized string is built at
 * open/copy time (UiUtils.openLink and the detail page's copy-link menu read
 * the flag at CLICK time), so the very next tap already honors the new value.
 * The stored RssItem.link is never modified either way - only the local copy
 * handed to the browser/clipboard is cleaned.
 */
public class StripTrackingParamsMenuSV extends StatefulView<Activity> implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;

    private transient SwitchMaterial mSwitchMaterial;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(
                R.layout.menu_strip_tracking_params, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSwitchMaterial = view.findViewById(R.id.switchm_strip_tracking_params);
        mSwitchMaterial.setChecked(mAppSharedPreferences.isStripTrackingParamsEnabled());
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
        mAppSharedPreferences.setStripTrackingParamsEnabled(checked);
    }
}
