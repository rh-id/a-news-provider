package m.co.rh.id.a_news_provider.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;

public class UiUtils {
    public static Activity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int getColorFromAttribute(Context context, int attribute) {
        Resources.Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attribute, typedValue, true);
        return typedValue.data;
    }

    private UiUtils() {
    }
}
