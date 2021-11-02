package m.co.rh.id.a_news_provider.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;

import java.io.File;

public class UiUtils {
    public static void shareFile(Context context, File file, String chooserMessage) {
        Uri fileUri = androidx.core.content.
                FileProvider.getUriForFile(
                context,
                "m.co.rh.id.a_news_provider.fileprovider",
                file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType("*/*");
        context.startActivity(Intent.createChooser(shareIntent, chooserMessage));
    }

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
