package m.co.rh.id.a_news_provider.app.provider.service;

import android.util.Patterns;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import m.co.rh.id.a_news_provider.app.provider.command.RedirectDuplicateChecker;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.NewRssWorker;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Non-UI service for adding RSS feeds. Extracted from NewRssChannelCmd so that
 * non-UI callers (OPML import) do not depend on a UI-facing command. The add
 * result is returned synchronously as a typed {@link AddFeedResult} - errors are
 * values, never thrown - and the caller owns any user-facing messaging.
 */
public class RssService {
    private static final String TAG = RssService.class.getName();

    private final WorkManager mWorkManager;
    private final RssDao mRssDao;
    private final UrlNormalizer mUrlNormalizer;
    private final RedirectDuplicateChecker mRedirectDuplicateChecker;
    private final ExecutorService mExecutorService;
    private final ILogger mLogger;

    public RssService(Provider provider) {
        mWorkManager = provider.get(WorkManager.class);
        mRssDao = provider.get(RssDao.class);
        mUrlNormalizer = provider.get(UrlNormalizer.class);
        mRedirectDuplicateChecker = provider.get(RedirectDuplicateChecker.class);
        mExecutorService = provider.get(ExecutorService.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Single source of truth for feed URL validation. The checks run in order and
     * the first failure wins.
     *
     * @param url the request URL to validate
     * @return null when the URL is valid, otherwise the first failing check
     */
    public UrlError checkUrl(String url) {
        if (url == null || url.isEmpty()) {
            return UrlError.EMPTY;
        } else if (!Patterns.WEB_URL.matcher(url).matches()) {
            return UrlError.INVALID;
        } else if (url.startsWith("http://")) {
            return UrlError.HTTP_NOT_ALLOWED;
        }
        return null;
    }

    /**
     * Prepends "https://" when the scheme is missing. Unlike {@link #buildRequestUrl(String)}
     * the result is NOT normalized, so the caller's original spelling is preserved.
     *
     * @param url the raw feed URL input
     * @return the scheme-prepended URL, or null when the input is null
     */
    public static String prependScheme(String url) {
        if (url == null) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    /**
     * Builds the actual request URL for the given feed URL input: prepends
     * "https://" when the scheme is missing and normalizes the result.
     *
     * @param url the raw feed URL input
     * @return the normalized request URL
     */
    public String buildRequestUrl(String url) {
        if (url == null) {
            return null;
        }
        return mUrlNormalizer.normalizeUrl(prependScheme(url));
    }

    /**
     * Adds a single feed: validates the URL, checks for an already added duplicate
     * channel, probes whether the feed redirects to an already added channel - a
     * duplicate - and enqueues the fetch worker when the feed is new.
     * This method must be called on a background thread - the redirect probe may
     * block on the network.
     * <p>
     * The redirect probe is best-effort: {@link RedirectDuplicateChecker} failures
     * fail open and the add proceeds to the normal fetch.
     *
     * @param url the raw feed URL input
     * @return the add result - a failed check never enqueues the worker
     */
    public AddFeedResult addNewFeed(String url) {
        String requestUrl = buildRequestUrl(url);
        UrlError urlError = checkUrl(requestUrl);
        if (urlError != null) {
            return AddFeedResult.invalid(requestUrl, urlError);
        }
        RssChannel existing;
        try {
            // first param keeps the raw requested spelling so legacy un-normalized
            // rows still match on identical re-add, second param covers normalized
            // spellings of the same feed
            existing = mRssDao.findRssChannelByUrlVariants(prependScheme(url), requestUrl);
        } catch (Exception e) {
            // duplicate check failed - fail closed rather than risk a duplicate add
            return AddFeedResult.dbError(requestUrl, e);
        }
        if (existing != null) {
            return AddFeedResult.duplicate(existing, requestUrl);
        }
        if (mRedirectDuplicateChecker.isRedirectToExistingChannel(requestUrl)) {
            return AddFeedResult.duplicateRedirect(requestUrl);
        }
        enqueueWorker(requestUrl);
        return AddFeedResult.added(requestUrl);
    }

    /**
     * Bulk add for OPML import: returns one {@link AddFeedResult} per input URL,
     * same order, and blocks until every probe-bearing add has completed.
     * This method must be called on a background thread - the redirect probes may
     * block on the network.
     * <p>
     * A duplicate-check database failure fails closed for the offending URL only -
     * the rest of the list still imports. Surfacing non-ADDED outcomes to the user
     * is the caller's job - the OPML import worker reports their count in its
     * completion toast, this service's DEBUG log stays the only per-feed detail.
     *
     * @param urls the raw feed URL inputs to add
     * @return one add result per input URL, in input order
     */
    public List<AddFeedResult> addNewFeeds(List<String> urls) {
        List<AddFeedResult> results = new ArrayList<>(urls.size());
        // request URL per input index, parallel to results
        List<String> requestUrls = new ArrayList<>(urls.size());
        // request URLs already seen in this list, to skip in-file duplicate outlines
        Set<String> queuedRequestUrls = new HashSet<>();
        // futures of the genuinely new adds, keyed by the result index they belong to
        Map<Integer, Future<AddFeedResult>> futures = new HashMap<>();
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String requestUrl = buildRequestUrl(url);
            requestUrls.add(requestUrl);
            UrlError urlError = checkUrl(requestUrl);
            if (urlError != null) {
                results.add(AddFeedResult.invalid(requestUrl, urlError));
                continue;
            }
            if (!queuedRequestUrls.add(requestUrl)) {
                results.add(AddFeedResult.duplicateInFile(requestUrl));
                continue;
            }
            RssChannel existing;
            try {
                // first param keeps the raw requested spelling so legacy un-normalized
                // rows still match, second param covers normalized spellings
                existing = mRssDao.findRssChannelByUrlVariants(prependScheme(url), requestUrl);
            } catch (Exception e) {
                // duplicate check failed - fail closed for this URL only, the rest
                // of the list still imports
                results.add(AddFeedResult.dbError(requestUrl, e));
                continue;
            }
            if (existing != null) {
                results.add(AddFeedResult.duplicate(existing, requestUrl));
                continue;
            }
            // genuinely new - the redirect probe may block on the network, so run the
            // add on the executor and resolve it after the loop to keep the result order
            futures.put(i, mExecutorService.submit(() -> addNewFeed(url)));
            results.add(null);
        }
        for (Map.Entry<Integer, Future<AddFeedResult>> entry : futures.entrySet()) {
            int index = entry.getKey();
            try {
                results.set(index, entry.getValue().get());
            } catch (Exception e) {
                // a catastrophic task failure must not break the whole list
                results.set(index, AddFeedResult.dbError(requestUrls.get(index), e));
            }
        }
        for (AddFeedResult result : results) {
            if (result.kind == AddFeedResult.Kind.ADDED) {
                continue;
            }
            // the caller owns the user-facing messaging - failures are logged only
            if (result.kind == AddFeedResult.Kind.DB_ERROR) {
                mLogger.d(TAG, result.requestUrl + " -> " + result.kind, result.cause);
            } else {
                mLogger.d(TAG, result.requestUrl + " -> " + result.kind);
            }
        }
        return results;
    }

    /**
     * Display name of the given channel for user-facing messages: feed name when
     * set, then title, then url, then empty.
     *
     * @param rssChannel the channel to name
     * @return the channel's display name
     */
    public static String displayName(RssChannel rssChannel) {
        String displayName = rssChannel.feedName;
        if (displayName == null || displayName.isEmpty()) {
            displayName = rssChannel.title;
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = rssChannel.url;
        }
        if (displayName == null) {
            displayName = "";
        }
        return displayName;
    }

    private void enqueueWorker(String requestUrl) {
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(NewRssWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInputData(new Data.Builder()
                        .putString(ConstantsKey.KEY_STRING_URL, requestUrl)
                        .build()
                ).build();
        mWorkManager.enqueue(oneTimeWorkRequest);
    }

    /**
     * URL validation failure reasons, named after the check that reports them.
     */
    public enum UrlError {
        EMPTY, INVALID, HTTP_NOT_ALLOWED
    }

    /**
     * Typed result of an add-feed request. Errors are values, never thrown -
     * {@link #requestUrl} is set on every result where it is computable (used for
     * logging), the remaining fields carry the details of their {@link Kind}.
     */
    public static class AddFeedResult {
        public final Kind kind;
        public final String requestUrl;
        // DUPLICATE only - the already added channel found
        public final RssChannel existing;
        // INVALID only - why the URL was rejected
        public final UrlError urlError;
        // DB_ERROR only - the database failure cause
        public final Throwable cause;

        private AddFeedResult(Kind kind, String requestUrl,
                              RssChannel existing, UrlError urlError, Throwable cause) {
            this.kind = kind;
            this.requestUrl = requestUrl;
            this.existing = existing;
            this.urlError = urlError;
            this.cause = cause;
        }

        public static AddFeedResult added(String requestUrl) {
            return new AddFeedResult(Kind.ADDED, requestUrl, null, null, null);
        }

        public static AddFeedResult duplicate(RssChannel existing, String requestUrl) {
            // the UI paths format the channel's display name - pin the non-null invariant
            return new AddFeedResult(Kind.DUPLICATE, requestUrl,
                    Objects.requireNonNull(existing), null, null);
        }

        public static AddFeedResult duplicateInFile(String requestUrl) {
            return new AddFeedResult(Kind.DUPLICATE_IN_FILE, requestUrl, null, null, null);
        }

        public static AddFeedResult duplicateRedirect(String requestUrl) {
            return new AddFeedResult(Kind.DUPLICATE_REDIRECT, requestUrl, null, null, null);
        }

        public static AddFeedResult invalid(String requestUrl, UrlError urlError) {
            return new AddFeedResult(Kind.INVALID, requestUrl, null, urlError, null);
        }

        public static AddFeedResult dbError(String requestUrl, Throwable cause) {
            return new AddFeedResult(Kind.DB_ERROR, requestUrl, null, null, cause);
        }

        public enum Kind {
            ADDED, DUPLICATE, DUPLICATE_IN_FILE, DUPLICATE_REDIRECT, INVALID, DB_ERROR
        }
    }
}
