package m.co.rh.id.a_news_provider.test.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;

/**
 * JUnit rule owning the provider/database lifecycle for instrumented tests:
 * {@link #create(TestApplication, ProviderModule, String)} builds the provider
 * (mirroring {@code Provider.createProvider}), and after each test the rule
 * closes the Room instance before deleting the database file, disposes the
 * provider, and deletes the database - replacing the previously copy-pasted
 * tearDown block in each test. Null-safe when a test never calls create().
 */
public class ProviderDbRule implements TestRule {
    private TestApplication mTestApplication;
    private Provider mProvider;
    private String mDbName;

    public Provider create(TestApplication testApplication, ProviderModule module,
                           String dbName) {
        // record the app and db name BEFORE creating the provider so a mid-create
        // failure still lets cleanup() delete a partially-created database file
        mTestApplication = testApplication;
        mDbName = dbName;
        mProvider = Provider.createProvider(testApplication, module);
        return mProvider;
    }

    public Provider getProvider() {
        return mProvider;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    base.evaluate();
                } finally {
                    cleanup();
                }
            }
        };
    }

    private void cleanup() {
        if (mProvider != null) {
            try {
                // close the Room instance before deleting its file so the delete
                // cannot race an open database handle
                mProvider.get(AppDatabase.class).close();
            } catch (Throwable ignored) {
                // database may never have been opened
            }
            mProvider.dispose();
        }
        if (mTestApplication != null && mDbName != null) {
            mTestApplication.deleteDatabase(mDbName);
        }
        mTestApplication = null;
        mProvider = null;
        mDbName = null;
    }
}
