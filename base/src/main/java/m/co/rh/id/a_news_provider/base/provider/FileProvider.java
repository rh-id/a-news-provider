package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;

import java.io.File;

/**
 * Class to provide files through this app
 */
public class FileProvider {

    private File mLogFile;

    public FileProvider(Context context) {
        mLogFile = new File(context.getCacheDir(), "alogger/app.log");
    }

    public File getLogFile() {
        return mLogFile;
    }
}
