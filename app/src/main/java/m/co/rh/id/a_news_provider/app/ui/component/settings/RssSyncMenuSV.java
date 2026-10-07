package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

public class RssSyncMenuSV extends StatefulView<Activity> {

    // transient: never serialize the host page reference (avoids cycles + leaks)
    private transient OnPeriodicSyncEnabledListener mOnPeriodicSyncEnabledListener;

    /**
     * Notified when the user explicitly enables periodic sync
     * (switch turned from OFF to ON by user interaction).
     */
    public interface OnPeriodicSyncEnabledListener {
        void onPeriodicSyncEnabled();
    }

    public void setOnPeriodicSyncEnabledListener(OnPeriodicSyncEnabledListener listener) {
        mOnPeriodicSyncEnabledListener = listener;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_rss_sync, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        TextView subtitleText = view.findViewById(R.id.text_subtitle);
        subtitleText.setText(activity.getString(R.string.sync_every_x_hour, appSharedPreferences.getPeriodicSyncRssHour()));
        SwitchMaterial aSwitch = view.findViewById(R.id.switchm_sync_feed);
        // register the listener AFTER setChecked so the programmatic init never fires it
        aSwitch.setChecked(appSharedPreferences.isEnablePeriodicSync());
        aSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
            boolean previous = appSharedPreferences.isEnablePeriodicSync();
            // the pref save happens regardless of the permission state,
            // the sync setting itself must persist either way
            appSharedPreferences.setEnablePeriodicSync(checked);
            if (checked && !previous && mOnPeriodicSyncEnabledListener != null) {
                mOnPeriodicSyncEnabledListener.onPeriodicSyncEnabled();
            }
        });
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            NumberPicker numberPicker = new NumberPicker(activity);
            numberPicker.setMinValue(1);
            numberPicker.setMaxValue(24);
            MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder.setTitle(activity.getString(R.string.sync_feed_period_in_hour));
            materialAlertDialogBuilder.setView(numberPicker);
            materialAlertDialogBuilder.setPositiveButton(android.R.string.ok, (dialogInterface, i) ->
            {
                appSharedPreferences.setPeriodicSyncRssHour(numberPicker.getValue());
                subtitleText.setText(activity.getString(R.string.sync_every_x_hour, numberPicker.getValue()));
            });
            materialAlertDialogBuilder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
                // leave blank
            });
            materialAlertDialogBuilder.create().show();
        });

        return view;
    }
}
