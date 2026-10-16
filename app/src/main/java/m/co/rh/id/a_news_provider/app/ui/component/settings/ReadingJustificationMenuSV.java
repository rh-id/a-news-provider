package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings toggle for justifying the article body, i.e. flushing both edges.
 * <p>
 * Justification is a platform feature introduced in API 26
 * ({@code TextView#setJustificationMode}), so below that the whole row is hidden
 * rather than offered and silently ignored. The preference is still written and
 * still read on every device, so upgrading the OS activates a setting the user
 * picked earlier without them having to revisit the screen. It ships off by
 * default because a narrow column without hyphenation opens whitespace rivers.
 */
public class ReadingJustificationMenuSV extends StatefulView<Activity> implements
        View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient SwitchMaterial mSwitchMaterial;
    private transient TextView mSubtitleText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_reading_justification, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSubtitleText = view.findViewById(R.id.text_subtitle);
        mSubtitleText.setText(getSubtitleText(activity, isJustificationEnabled()));
        mSwitchMaterial = view.findViewById(R.id.switch_reading_justification);
        // apply the current value before attaching the listener, so inflating the
        // row does not write back to the shared preferences
        mSwitchMaterial.setChecked(isJustificationEnabled());
        mSwitchMaterial.setOnCheckedChangeListener(this);
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            view.setVisibility(View.GONE);
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mProvider = null;
        mAppSharedPreferences = null;
        mSwitchMaterial = null;
        mSubtitleText = null;
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.container_menu && mSwitchMaterial != null) {
            mSwitchMaterial.setChecked(!mSwitchMaterial.isChecked());
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
        mAppSharedPreferences.setReadingJustification(checked);
        if (mSubtitleText != null) {
            mSubtitleText.setText(getSubtitleText(compoundButton.getContext(), checked));
        }
    }

    private boolean isJustificationEnabled() {
        return mAppSharedPreferences.isReadingJustification();
    }

    private static CharSequence getSubtitleText(Context context, boolean justified) {
        return context.getString(justified
                ? R.string.reading_justification_justified
                : R.string.reading_justification_left);
    }
}