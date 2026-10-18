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
 * Settings toggle for cross-feed duplicate story collapsing (issue #45). Toggling
 * only writes the preference: the all-channels list re-reads the setting on its
 * next load (every fresh/refresh load goes through
 * {@code PagedRssItemsCmd.loadItems}), so the effect appears when the list
 * reloads - returning from settings re-enters the home list and applies it.
 */
public class CollapseDuplicateStoriesMenuSV extends StatefulView<Activity> implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;

    private transient SwitchMaterial mSwitchMaterial;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(
                R.layout.menu_collapse_duplicate_stories, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSwitchMaterial = view.findViewById(R.id.switchm_collapse_duplicate_stories);
        mSwitchMaterial.setChecked(mAppSharedPreferences.isCollapseDuplicateStories());
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
        mAppSharedPreferences.setCollapseDuplicateStories(checked);
    }
}
