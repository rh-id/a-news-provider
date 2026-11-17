package m.co.rh.id.a_news_provider.app.provider.component.repository;

import androidx.annotation.Nullable;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * The duplicate guard for the permanent-redirect URL rewrite (issue #50):
 * decides whether a parsed model's redirect outcome may survive
 * into {@link RssRepository#persist(RssModel)} or must be cleared. Both
 * workers that persist fetched feeds share this exact decision so the sync
 * path and the add path can never drift apart.
 * <p>
 * <b>Why the guard exists at all.</b> persist() rewrites the stored feed url
 * to the redirect target while keeping the row's identity; without a check,
 * a redirect that lands on an address ALREADY owned by another channel would
 * merge two subscriptions into one row - or in the add path insert a second
 * row carrying the same url. Two realistic triggers: the add-time redirect
 * probe (RedirectDuplicateChecker) deliberately fails OPEN, so a transient
 * probe error lets an add through whose fetch then 301/308-redirects onto an
 * already-added feed; and OPML bulk import can enqueue both the redirector
 * AND its target, where the second fetch to land must not adopt the target's
 * url again.
 * <p>
 * <b>Decision.</b> The final url is normalized once (the same normalizer
 * persist uses) so the duplicate lookup and the write cannot diverge, then
 * looked up against the database with both the raw and normalized variants
 * like every other by-url lookup. A holder whose row is a DIFFERENT channel
 * is a collision: the outcome is cleared from the model - persist then keeps
 * the channel's original url, nothing of the other feed is touched, and the
 * collision is logged at debug level (a .d() log is developer-facing only,
 * so a plain literal is used - no user-facing string resource).
 * For the sync path the caller passes the synced channel, and a holder equal
 * to that channel is allowed (the row already carries the final url spelling,
 * e.g. a normalization-only difference). For the ADD path there is no
 * existing row yet, so the caller passes null and ANY holder counts as a
 * collision - the added feed keeps its original url instead of silently
 * becoming a copy of the target.
 * <p>
 * <b>Failure posture: fail closed.</b> Unlike the add-time probe, a DAO error
 * here propagates to the caller's existing failure handling (the worker
 * records the fetch as failed / the add dialog shows the error) instead of
 * being swallowed: swallowing would silently let an unguarded rewrite
 * through, which is exactly the duplicate this guard prevents. The caller's
 * error-handling flow needs no changes for this class.
 */
public class PermanentRedirectGuard {
    private static final String TAG = PermanentRedirectGuard.class.getName();

    private final RssDao mRssDao;
    private final UrlNormalizer mUrlNormalizer;
    private final ILogger mLogger;

    /**
     * Production constructor: resolves the dependencies from the given
     * {@link Provider}. Instrumented tests wire this constructor through
     * a test ProviderModule whose RssDao resolves to an in-memory Room
     * database - there is deliberately no second, test-only constructor.
     */
    public PermanentRedirectGuard(Provider provider) {
        mRssDao = provider.get(RssDao.class);
        mUrlNormalizer = provider.get(UrlNormalizer.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Applies the duplicate guard to the model's redirect outcome, in place,
     * BEFORE the caller persists the model.
     * <p>
     * Models without an outcome, or with a temporary one (302/303/307 last
     * hop), are left untouched and reported as not-applied - persist keeps
     * normalizing the original url for them.
     *
     * @param existingChannel the channel row this fetch belongs to (sync
     *                        path; a holder equal to this channel is not a
     *                        collision), or null on the add path where every
     *                        holder is a collision
     * @param parsed          the parsed model carrying the outcome (if any);
     *                        on a collision the outcome is cleared on it
     * @return true when the outcome SURVIVED the guard (persist will write
     * the normalized final url), false when there was nothing to apply or the
     * outcome was cleared
     */
    public boolean applyGuard(@Nullable RssChannel existingChannel, RssModel parsed) {
        String finalUrl = parsed.getFinalUrl();
        if (finalUrl == null || !parsed.isPermanentRedirect()) {
            // no outcome (direct response / cache hit) or a temporary last
            // hop: url NOT rewritten
            return false;
        }
        String normalizedFinalUrl = mUrlNormalizer.normalizeUrl(finalUrl);
        // carry the normalized spelling on the model so the duplicate lookup
        // and persist's write see the exact same value
        parsed.setFinalUrl(normalizedFinalUrl);
        RssChannel holder = mRssDao.findRssChannelByUrlVariants(finalUrl, normalizedFinalUrl);
        if (holder != null && holder.id != null
                && (existingChannel == null || existingChannel.id == null
                || !holder.id.equals(existingChannel.id))) {
            // the redirect target is already someone else's feed: rewriting
            // would merge two subscriptions into one row (or insert a second
            // row with the same url on the add path) - keep the original url,
            // clear the outcome so persist cannot write the target
            parsed.setFinalUrl(null);
            parsed.setPermanentRedirect(false);
            mLogger.d(TAG, "Permanent redirect outcome skipped for feed \""
                    + parsed.getRssChannel().feedName
                    + "\": redirect target is already another feed's url");
            return false;
        }
        mLogger.d(TAG, "Permanent redirect applied for feed \""
                + parsed.getRssChannel().feedName + "\": "
                + parsed.getRssChannel().url + " -> " + normalizedFinalUrl);
        return true;
    }
}
