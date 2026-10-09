package m.co.rh.id.a_news_provider.test.util;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An {@link java.util.concurrent.ExecutorService} that runs every task inline on
 * the calling thread. It is registered in test Provider modules in place of a real
 * thread pool so that async production code (persist, DAO writes, notifier
 * emissions) completes synchronously before the test's next statement -
 * eliminating Thread.sleep/await-style waits.
 * <p>
 * This file lives in src/sharedTest/java, which is compiled into BOTH the
 * unit-test and the instrumented-test source sets via the sourceSets
 * configuration in app/build.gradle, so one copy serves both test types.
 */
public class DirectExecutorService extends AbstractExecutorService {

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public void shutdown() {
        // no-op
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }
}
