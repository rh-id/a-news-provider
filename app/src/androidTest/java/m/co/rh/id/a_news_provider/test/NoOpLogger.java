package m.co.rh.id.a_news_provider.test;

import m.co.rh.id.alogger.ILogger;

/**
 * Hand-rolled no-op {@link ILogger} double for instrumented tests. Mockito is
 * unreliable on ART, so fakes are implemented directly.
 */
public class NoOpLogger implements ILogger {

    @Override
    public void setLogLevel(int level) {
        // no-op
    }

    @Override
    public void v(String tag, String message) {
        // no-op
    }

    @Override
    public void v(String tag, String message, Throwable throwable) {
        // no-op
    }

    @Override
    public void d(String tag, String message) {
        // no-op
    }

    @Override
    public void d(String tag, String message, Throwable throwable) {
        // no-op
    }

    @Override
    public void i(String tag, String message) {
        // no-op
    }

    @Override
    public void i(String tag, String message, Throwable throwable) {
        // no-op
    }

    @Override
    public void w(String tag, String message) {
        // no-op
    }

    @Override
    public void w(String tag, String message, Throwable throwable) {
        // no-op
    }

    @Override
    public void e(String tag, String message) {
        // no-op
    }

    @Override
    public void e(String tag, String message, Throwable throwable) {
        // no-op
    }
}
