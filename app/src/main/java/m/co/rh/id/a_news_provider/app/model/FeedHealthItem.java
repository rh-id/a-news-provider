package m.co.rh.id.a_news_provider.app.model;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Locale;

import m.co.rh.id.a_news_provider.app.util.SyncBackoffPolicy;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;

/**
 * One feed-health dashboard row (issue #42): the channel entity as loaded at
 * refresh time plus its per-channel item counts (see
 * {@code RssDao.countRssItemsByChannel}) and the derived status the UI renders.
 * Pure Java - no android imports beyond the base entity - so the status rules,
 * the errors-first ordering and the backoff-until math are all JVM-testable,
 * the same pure-decision precedent as {@link SyncBackoffPolicy}.
 * <p>
 * Implements Serializable so the row survives the row StatefulView's
 * serialized state inside FeedHealthItemSV's SerialBehaviorSubject (same
 * rationale as RssItem for RssItemSV); every instance field serializes (the
 * RssChannel entity is Serializable, the counts are ints, the Status enum is
 * serializable by default) and like the entities, no serialVersionUID is
 * declared - the existing accepted risk.
 */
public class FeedHealthItem implements Serializable {

    /**
     * Derived health status of the row. Derivation precedence (deliberate):
     * <ol>
     * <li><b>PAUSED</b> when {@code isPaused} - a paused feed is not synced at
     * all, so its failure columns are frozen history, not a live problem.
     * Classifying it ERROR would rank a deliberately silenced feed among the
     * broken ones and offer a "Sync now" the sync worker would silently skip
     * (paused channels are skipped by RssSyncWorker, even on a targeted
     * retry).</li>
     * <li><b>ERROR</b> strictly when {@code lastError != null} OR
     * {@code lastHttpStatus != null}. The failure COUNTER must NOT be part of
     * this decision (issue #50): Retry-After counter-encoding inflates
     * {@code consecutiveFailures} purely as a skip schedule without a real
     * error, while a timeout failure carries a real {@code lastError} but no
     * HTTP status - the two observability columns are the only honest "this
     * feed failed" signal.</li>
     * <li><b>OK</b> otherwise - including a channel whose counter sits above
     * zero from a Retry-After encoding with no recorded error: it is backing
     * off on schedule, not failing.</li>
     * </ol>
     */
    public enum Status {
        OK, PAUSED, ERROR
    }

    public final RssChannel rssChannel;
    public final int totalItems;
    public final int unreadItems;

    public FeedHealthItem(RssChannel rssChannel, int totalItems, int unreadItems) {
        this.rssChannel = rssChannel;
        this.totalItems = totalItems;
        this.unreadItems = unreadItems;
    }

    /**
     * The derived status of this row, following the precedence documented on
     * {@link Status}.
     */
    public Status getStatus() {
        if (rssChannel.isPaused) {
            return Status.PAUSED;
        }
        if (rssChannel.lastError != null || rssChannel.lastHttpStatus != null) {
            return Status.ERROR;
        }
        return Status.OK;
    }

    /**
     * The instant until which this feed stays quiet under its per-feed backoff
     * (issue #50 schedule, issue #42 dashboard display), computed purely from
     * persisted state: last attempt stamp + the effective backoff window of
     * the failure counter (via
     * {@link SyncBackoffPolicy#effectiveBackoffWindow(RssChannel)}). Returns 0
     * when there is no active quiet period: no failures, no attempt stamp, or
     * a window that has already elapsed at {@code nowMillis} - the same strict
     * boundary semantics as {@link SyncBackoffPolicy#shouldSkip(RssChannel,
     * long)} (elapsed &lt; window means quiet). The row UI hides the schedule
     * line on a 0 return instead of rendering a stale "retries after <past
     * date>".
     *
     * @param nowMillis the display time in epoch millis
     * @return the backoff-until instant in epoch millis, 0 when not backing off
     */
    public long getBackoffUntilMillis(long nowMillis) {
        if (rssChannel.consecutiveFailures <= 0 || rssChannel.lastSyncAttemptDateTime == null) {
            return 0;
        }
        long untilMillis = rssChannel.lastSyncAttemptDateTime.getTime()
                + SyncBackoffPolicy.effectiveBackoffWindow(rssChannel);
        if (untilMillis <= nowMillis) {
            return 0;
        }
        return untilMillis;
    }

    /**
     * Dashboard ordering (issue #42): broken feeds first so the user sees what
     * needs attention at the top, then the paused ones (frozen, user-silenced),
     * then the healthy rest. Within the same status rows are ordered by feed
     * name (case-insensitive, null-safe), with the channel id as final
     * tiebreak so equal names keep a stable order. Pure - used by FeedHealthCmd
     * inside its refresh, the adapter stays a dumb renderer.
     */
    public static final Comparator<FeedHealthItem> ERRORS_FIRST_COMPARATOR =
            (o1, o2) -> {
                int statusCompare = Integer.compare(statusRank(o1), statusRank(o2));
                if (statusCompare != 0) {
                    return statusCompare;
                }
                String name1 = o1.rssChannel.feedName;
                String name2 = o2.rssChannel.feedName;
                if (name1 == null && name2 != null) {
                    return 1;
                }
                if (name1 != null && name2 == null) {
                    return -1;
                }
                if (name1 != null) {
                    int nameCompare = name1.compareToIgnoreCase(name2);
                    if (nameCompare != 0) {
                        return nameCompare;
                    }
                }
                long id1 = o1.rssChannel.id != null ? o1.rssChannel.id : 0;
                long id2 = o2.rssChannel.id != null ? o2.rssChannel.id : 0;
                return Long.compare(id1, id2);
            };

    /**
     * Lower rank sorts first: ERROR (0), PAUSED (1), OK (2).
     */
    private static int statusRank(FeedHealthItem feedHealthItem) {
        switch (feedHealthItem.getStatus()) {
            case ERROR:
                return 0;
            case PAUSED:
                return 1;
            default:
                return 2;
        }
    }

    /**
     * The display name of the row: the user's feed name, falling back to the
     * parsed channel title, then to a locale-stable placeholder when both are
     * missing (a nameless feed must still render and sort deterministically).
     */
    public String getDisplayName() {
        if (rssChannel.feedName != null && !rssChannel.feedName.isEmpty()) {
            return rssChannel.feedName;
        }
        if (rssChannel.title != null && !rssChannel.title.isEmpty()) {
            return rssChannel.title;
        }
        return String.format(Locale.ROOT, "#%s",
                rssChannel.id != null ? rssChannel.id.toString() : "?");
    }
}
