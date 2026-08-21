package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.net.Uri;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.OpmlParseWorker;
import m.co.rh.id.a_news_provider.base.provider.FileHelper;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class OpmlCmd {
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final WorkManager mWorkManager;
    private final FileHelper mFileHelper;
    private final ILogger mLogger;
    private final OpmlParser mOpmlParser;

    public OpmlCmd(Provider provider) {
        mAppContext = provider.getContext();
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.get(WorkManager.class);
        mFileHelper = provider.get(FileHelper.class);
        mLogger = provider.get(ILogger.class);
        mOpmlParser = provider.get(OpmlParser.class);
    }

    /**
     * Export RSS feeds to OPML file.
     *
     * @return Single that emits the exported OPML file
     */
    public Single<File> exportOpml() {
        return Single.fromCallable(mOpmlParser::exportOpml)
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Import RSS feeds from OPML file.
     *
     * @param fileData Uri pointing to the OPML file to import
     */
    public void importOpml(Uri fileData) {
        String errorMessage = mAppContext.getString(R.string.error_failed_to_open_file);
        mExecutorService.execute(() -> {
            try {
                File file = mFileHelper.createTempFile("Feed.opml", fileData);
                OneTimeWorkRequest oneTimeWorkRequest =
                        new OneTimeWorkRequest.Builder(OpmlParseWorker.class)
                                .setInputData(new Data.Builder()
                                        .putString(ConstantsKey.KEY_FILE_ABSOLUTE_PATH,
                                                file.getAbsolutePath())
                                        .build())
                                .build();
                mWorkManager.enqueue(oneTimeWorkRequest);
            } catch (Throwable throwable) {
                mLogger.e(OpmlCmd.class.getName(), errorMessage, throwable);
            }
        });
    }
}