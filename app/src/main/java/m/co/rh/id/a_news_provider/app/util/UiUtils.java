package m.co.rh.id.a_news_provider.app.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.FileProvider;

import com.tokopedia.showcase.ShowCaseBuilder;
import com.tokopedia.showcase.ShowCaseDialog;
import com.tokopedia.showcase.ShowCaseObject;

import java.io.File;
import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;

public class UiUtils {
    public static void shareFile(Context context, File file, String chooserMessage) {
        Uri fileUri = FileProvider.getUriForFile(
                context,
                "m.co.rh.id.a_news_provider.fileprovider",
                file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType("*/*");
        shareIntent = Intent.createChooser(shareIntent, chooserMessage);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(shareIntent);
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

    /**
     * Copies the given text to the system clipboard.
     *
     * @param context any context
     * @param label   human-readable label describing what is being copied
     * @param text    the text to place on the clipboard
     * @return {@code true} if the text was copied, {@code false} if the clipboard
     * service was unavailable
     */
    public static boolean copyToClipboard(Context context, CharSequence label, CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        return true;
    }

    /**
     * Show RSS channel list showcase dialog.
     *
     * @param activity   The activity to show the showcase on
     * @param drawerView The drawer view containing the target view
     */
    public static void showRssChannelListShowCase(Activity activity, View drawerView) {
        int textColor = R.color.white;
        ShowCaseDialog showCaseDialog = new ShowCaseBuilder()
                .textColorRes(textColor)
                .titleTextColorRes(textColor)
                .shadowColorRes(R.color.shadow)
                .titleTextSizeRes(R.dimen.text_nav_menu)
                .spacingRes(R.dimen.spacing_normal)
                .backgroundContentColorRes(R.color.orange_600)
                .circleIndicatorBackgroundDrawableRes(R.drawable.selector_circle_green)
                .prevStringRes(R.string.previous)
                .nextStringRes(R.string.next)
                .finishStringRes(R.string.finish)
                .useCircleIndicator(false)
                .clickable(true)
                .build();
        String title = activity.getString(R.string.title_showcase_rss_channel_list);
        String description = activity.getString(R.string.showcase_rss_channel_list);
        ArrayList<ShowCaseObject> showCaseList = new ArrayList<>();
        showCaseList.add(new ShowCaseObject(
                drawerView.findViewById(R.id.container_list_channel),
                title,
                description));
        showCaseDialog.show(activity, null, showCaseList);
    }

    private UiUtils() {
    }
}
