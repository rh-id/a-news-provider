package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings entry for the "List layout" preference (issue #58): a radio group over
 * the three rss item row variants (standard / compact / card). Same menu pattern
 * as {@link ThemeMenuSV} - a titled RadioGroup (NOT a OneHandModeMenuSV-style
 * switch, since there are three mutually exclusive choices).
 * <p>
 * No event-handler wiring beyond the pref write on purpose: the rss item list
 * adapters observe {@link AppSharedPreferences#getListLayoutFlow()} themselves and
 * switch their row view types live, so this menu only persists the choice.
 */
public class ListLayoutMenuSV extends StatefulView<Activity> {

    private transient AppSharedPreferences mAppSharedPreferences;

    public ListLayoutMenuSV() {
        super();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_list_layout, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        mAppSharedPreferences = provider.get(AppSharedPreferences.class);
        RadioGroup radioGroup = view.findViewById(R.id.radioGroup);
        radioGroup.check(getSelectedRadioId(mAppSharedPreferences));
        radioGroup.setOnCheckedChangeListener((radioGroup1, i) -> {
            int listLayout = AppSharedPreferences.LIST_LAYOUT_STANDARD;
            if (i == R.id.radio_compact) {
                listLayout = AppSharedPreferences.LIST_LAYOUT_COMPACT;
            } else if (i == R.id.radio_card) {
                listLayout = AppSharedPreferences.LIST_LAYOUT_CARD;
            }
            mAppSharedPreferences.setListLayout(listLayout);
        });
        return view;
    }

    private int getSelectedRadioId(AppSharedPreferences appSharedPreferences) {
        int listLayout = appSharedPreferences.getListLayout();
        int result = R.id.radio_standard;
        if (listLayout == AppSharedPreferences.LIST_LAYOUT_COMPACT) {
            result = R.id.radio_compact;
        } else if (listLayout == AppSharedPreferences.LIST_LAYOUT_CARD) {
            result = R.id.radio_card;
        }
        return result;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppSharedPreferences = null;
    }
}
