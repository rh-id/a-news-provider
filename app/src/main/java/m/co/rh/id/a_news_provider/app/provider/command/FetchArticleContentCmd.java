package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.provider.component.dao.ArticleContentDao;
import m.co.rh.id.a_news_provider.base.entity.ArticleContent;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.component.network.ArticleExtractor;
import m.co.rh.id.a_news_provider.component.network.ArticleHtmlRequest;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to fetch, extract and cache the full-text article body of an RSS item
 * (issue #28 phase 1, in-app article reader).
 * <p>
 * Flow: validate the item link, short-circuit on already cached content (unless
 * forced), fetch the page with an {@link ArticleHtmlRequest} bridged onto the
 * executor through a Volley {@link RequestFuture} (the same blocking pattern
 * RssSyncWorker uses for feed fetches), extract the article body with
 * {@link ArticleExtractor}, upsert the result into the link-keyed
 * {@code article_content} Room table, and emit an
 * {@link RssChangeNotifier#articleContentUpdated(String)} event so an open
 * detail page switches to reader mode.
 * <p>
 * Never crashes: every failure (invalid link, network error, timeout,
 * non-HTML response, failed extraction) resolves the Single with
 * {@code false} and is logged. The command is app-scoped (registered lazy in
 * CommandProviderModule), so a popped detail page just misses the event while
 * the content still lands in the database for the next open.
 */
public class FetchArticleContentCmd {
    private static final String TAG = FetchArticleContentCmd.class.getName();

    /**
     * Upper bound for waiting on the Volley future. The request's own
     * timeout is {@link ArticleHtmlRequest}'s 15 s with zero retries, so 20 s
     * leaves head room for queue latency without hanging the caller.
     */
    private static final int FETCH_TIMEOUT_SECONDS = 20;

    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RequestQueue mRequestQueue;
    private final ArticleContentDao mArticleContentDao;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ArticleExtractor mArticleExtractor;
    private final ILogger mLogger;

    public FetchArticleContentCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRequestQueue = provider.get(RequestQueue.class);
        mArticleContentDao = provider.get(ArticleContentDao.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mArticleExtractor = provider.get(ArticleExtractor.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Fetches the full article of the given item unless valid content is
     * already cached for its link (the auto-fetch path: it only wants the
     * content to EXIST). A re-fetch of cached content needs
     * {@link #executeForce(RssItem)}, the manual toolbar action.
     *
     * @param rssItem the item whose link should be fetched
     * @return Single emitting true when content is (now) available for the
     * link, false on any failure
     */
    public Single<Boolean> execute(RssItem rssItem) {
        return execute(rssItem, false);
    }

    /**
     * Fetches the full article of the given item ALWAYS (the manual "Read
     * full article" action), even when valid content is already cached - the
     * fetch refreshes the cached row.
     *
     * @param rssItem the item whose link should be re-fetched
     * @return Single emitting true when content is (now) available for the
     * link, false on any failure
     */
    public Single<Boolean> executeForce(RssItem rssItem) {
        return execute(rssItem, true);
    }

    private Single<Boolean> execute(RssItem rssItem, boolean force) {
        return Single.fromCallable(() -> executeInternal(rssItem, force))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * The command body, run on the executor thread (never the main thread:
     * the Volley future blocks and the Room writes are synchronous).
     */
    private Boolean executeInternal(RssItem rssItem, boolean force) {
        String link = rssItem != null ? rssItem.link : null;
        if (!hasValidLink(link)) {
            mLogger.d(TAG, mAppContext.getString(R.string.full_article_fetch_failed)
                    + ": " + mAppContext.getString(R.string.invalid_url));
            return false;
        }
        try {
            if (!force) {
                ArticleContent cachedContent =
                        mArticleContentDao.findArticleContentByLink(link);
                if (cachedContent != null
                        && cachedContent.content != null
                        && !cachedContent.content.isEmpty()) {
                    return true;
                }
            }
            byte[] rawPageBytes = fetchArticleBytes(link);
            String extractedContent = mArticleExtractor.extract(rawPageBytes, link);
            if (extractedContent == null || extractedContent.isEmpty()) {
                mLogger.d(TAG, mAppContext.getString(R.string.full_article_fetch_failed)
                        + " " + link);
                return false;
            }
            mArticleContentDao.upsertArticleContent(new ArticleContent(link,
                    extractedContent, new Date().getTime()));
            mRssChangeNotifier.articleContentUpdated(link);
            return true;
        } catch (Throwable throwable) {
            mLogger.e(TAG, mAppContext.getString(R.string.full_article_fetch_failed),
                    throwable);
            return false;
        }
    }

    /**
     * Enqueues the article request and blocks on the Volley future until the
     * raw page bytes arrive. Package-visible seam: the unit test overrides
     * this to deliver bytes without a real network stack, keeping the test
     * consistent with the module's DirectExecutorService no-wait convention
     * (blocking a JVM test thread on a mocked Volley queue cannot deliver).
     *
     * @param link the validated article link to fetch
     * @return the raw response bytes
     * @throws Exception on network error, timeout or non-HTML response
     */
    byte[] fetchArticleBytes(String link) throws Exception {
        RequestFuture<byte[]> requestFuture = RequestFuture.newFuture();
        ArticleHtmlRequest articleHtmlRequest =
                new ArticleHtmlRequest(link, requestFuture, requestFuture);
        mRequestQueue.add(articleHtmlRequest);
        return requestFuture.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * A link is fetchable when it is a non-empty http(s) URL - the same
     * scheme guard UrlSanitizer applies to openable links (mailto:, market:
     * and relative links are not article pages).
     *
     * @param link the item link, may be null
     * @return true when the link is a usable http(s) URL
     */
    private static boolean hasValidLink(@Nullable String link) {
        if (link == null || link.trim().isEmpty()) {
            return false;
        }
        String lowerCaseLink = link.trim().toLowerCase(Locale.US);
        return lowerCaseLink.startsWith("http://")
                || lowerCaseLink.startsWith("https://");
    }
}
