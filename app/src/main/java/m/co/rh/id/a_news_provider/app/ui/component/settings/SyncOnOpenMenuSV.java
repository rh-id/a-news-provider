package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings toggle for "Sync when app opens" (issue #50). Toggling only
 * writes the preference: the trigger itself lives in HomePage's resume hook,
 * which polls {@code isSyncOnAppOpen()} on every resume through the
 * SyncOnOpenPolicy debounce gate, so the very next app open already honors the
 * new value. The row sits right after the periodic-sync entry because both
 * answer "when does content refresh" - the switch never touches the periodic
 * schedule, only the automatic on-open sync request.
 */
public class SyncOnOpenMenuSV extends StatefulView<Activity> implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;

    private transient SwitchMaterial mSwitchMaterial;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(
                R.layout.menu_sync_on_open, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSwitchMaterial = view.findViewById(R.id.switchm_sync_on_open);
        mSwitchMaterial.setChecked(mAppSharedPreferences.isSyncOnAppOpen());
        // register the listener AFTER setChecked so the programmatic init never fires it
        mSwitchMaterial.setOnCheckedChangeListener(this);
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mProvider = null;
        mAppSharedPreferences = null;
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
        mAppSharedPreferences.setSyncOnAppOpen(checked);
    }
}
