package m.co.rh.id.a_news_provider.base.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Class to provide files through this app
 */
public class FileProvider {
    private Context mAppContext;
    private File mLogFile;
    private File mTempFileRoot;

    public FileProvider(Context context) {
        mAppContext = context.getApplicationContext();
        File cacheDir = context.getCacheDir();
        mLogFile = new File(cacheDir, "alogger/app.log");
        mTempFileRoot = new File(cacheDir, "/tmp");
        mTempFileRoot.mkdirs();
    }

    /**
     * Create temporary file
     *
     * @param fileName file name for this file
     * @param content  content of the file to write to this temp file
     * @return temporary file
     * @throws IOException when failed to create file
     */
    public File createTempFile(String fileName, Uri content) throws IOException {
        File parent = new File(mTempFileRoot, UUID.randomUUID().toString());
        parent.mkdirs();
        String fName = fileName;
        if (fName == null || fName.isEmpty()) {
            fName = UUID.randomUUID().toString();
        }
        File tmpFile = new File(parent, fName);
        tmpFile.createNewFile();

        ContentResolver cr = mAppContext.getContentResolver();
        InputStream inputStream = cr.openInputStream(content);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

        FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        byte[] buff = new byte[2048];
        int b = bufferedInputStream.read(buff);
        while (b != -1) {
            bufferedOutputStream.write(buff);
            b = bufferedInputStream.read(buff);
        }
        bufferedOutputStream.close();
        fileOutputStream.close();
        bufferedInputStream.close();
        inputStream.close();
        return tmpFile;
    }

    public File getLogFile() {
        return mLogFile;
    }
}
