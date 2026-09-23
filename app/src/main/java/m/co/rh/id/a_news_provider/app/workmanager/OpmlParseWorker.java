package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.util.List;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.provider.service.RssService;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class OpmlParseWorker extends Worker {
    private static final String TAG = OpmlParseWorker.class.getName();
    private static final String LINE_SEPARATOR = "\n";

    public OpmlParseWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String filePath = getInputData().getString(ConstantsKey.KEY_FILE_ABSOLUTE_PATH);
        Context appContext = getApplicationContext();
        Provider provider = BaseApplication.of(appContext).getProvider();
        try {
            File file = new File(filePath);
            List<RssService.AddFeedResult> results = provider.get(RssService.class)
                    .addNewFeeds(provider.get(OpmlParser.class).parse(file));
            // summary-only user-facing message (toast): text toasts are truncated to
            // two lines on Android 12+, so the per-feed detail stays where it belongs -
            // RssService's per-result DEBUG log
            int addedCount = 0;
            for (RssService.AddFeedResult result : results) {
                if (result.kind == RssService.AddFeedResult.Kind.ADDED) {
                    addedCount++;
                }
            }
            int notAddedCount = results.size() - addedCount;
            String message = appContext.getString(R.string.added_rss, addedCount);
            if (notAddedCount > 0) {
                message += LINE_SEPARATOR
                        + appContext.getString(R.string.opml_feeds_not_added, notAddedCount);
            }
            // user-facing log (toast), preserves the completion message of the import
            provider.get(ILogger.class)
                    .i(TAG, message);
        } catch (Throwable throwable) {
            provider.get(ILogger.class)
                    .e(TAG, appContext.getString(R.string.error_parsing_opml_file)
                            , throwable);
        }
        return Result.success();
    }
}
