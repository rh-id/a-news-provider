package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class DownloadImageMenuSV extends StatefulView<Activity> implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;

    private transient SwitchMaterial mSwitchMaterial;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_download_image, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSwitchMaterial = view.findViewById(R.id.switchm_image_download);
        mSwitchMaterial.setChecked(mAppSharedPreferences.isDownloadImage());
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
        mAppSharedPreferences.setDownloadImage(checked);
        Context context = compoundButton.getContext();
        String message;
        if (checked) {
            message = context.getString(R.string.download_image_enabled);
        } else {
            message = context.getString(R.string.download_image_disabled);
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
