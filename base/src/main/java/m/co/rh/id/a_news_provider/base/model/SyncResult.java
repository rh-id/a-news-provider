package m.co.rh.id.a_news_provider.base.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable summary of one complete RSS sync run (issue #50): how many channels
 * were actually attempted and how many of those succeeded, failed or were
 * skipped. Emitted by RssSyncWorker directly at the end of
 * {@code doWork()} (worker-emits-directly, same precedent as
 * {@code autoMarkOldItemsRead}) so the UI can surface sync failures instead of
 * the previous unconditional "Feed sync success" toast that lied whenever any
 * channel failed. Deliberately lives in :base so both the app worker and
 * future consumers (feed-health dashboard, issue #42) can share it.
 * <p>
 * An empty result is NEVER emitted: when no channel was attempted at all
 * (all paused, or an empty database) the worker emits nothing - "Synced 0 of
 * 0" is noise, not information.
 * <p>
 * Like the other base models ({@code RssChannel}), instances are
 * Java-serializable through a-navigator page args, with no serialVersionUID
 * declared per the accepted-risk convention documented there.
 */
public class SyncResult implements Serializable {
    private final int total;
    private final int ok;
    private final int failed;
    private final int skipped;
    private final long syncStartMillis;
    private final List<FailedChannel> failedChannels;

    public SyncResult(int total, int ok, int failed, int skipped,
                      long syncStartMillis, List<FailedChannel> failedChannels) {
        this.total = total;
        this.ok = ok;
        this.failed = failed;
        this.skipped = skipped;
        this.syncStartMillis = syncStartMillis;
        this.failedChannels = Collections.unmodifiableList(
                new ArrayList<>(failedChannels));
    }

    /**
     * Channels actually attempted this sync (not paused, not skipped by
     * backoff). The denominator of the failure ratio shown in the UI.
     */
    public int getTotal() {
        return total;
    }

    /**
     * Channels that synced successfully.
     */
    public int getOk() {
        return ok;
    }

    /**
     * Channels that failed this sync.
     */
    public int getFailed() {
        return failed;
    }

    /**
     * Channels deferred by the backoff window (issue #50): counted for real -
     * RssSyncWorker skips channels whose persisted failure counter puts them
     * inside their {@code SyncBackoffPolicy} quiet period. Skipped channels
     * sit OUTSIDE the attempted total (total = attempted only), so
     * skipped+ok+failed never equals total by design; within total, ok+failed
     * can still fall short after a mid-run cancellation (the unawaited
     * remainder is unaccounted).
     */
    public int getSkipped() {
        return skipped;
    }

    /**
     * Wall-clock start of the sync run, matching the worker's
     * KEY_LONG_SYNC_START_MILLIS output so consumers can correlate this
     * summary with the chained-worker pass that filtered on the same stamp.
     */
    public long getSyncStartMillis() {
        return syncStartMillis;
    }

    /**
     * Per-channel detail of every failed channel of this run.
     */
    public List<FailedChannel> getFailedChannels() {
        return failedChannels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SyncResult that = (SyncResult) o;

        if (total != that.total) return false;
        if (ok != that.ok) return false;
        if (failed != that.failed) return false;
        if (skipped != that.skipped) return false;
        if (syncStartMillis != that.syncStartMillis) return false;
        return failedChannels.equals(that.failedChannels);
    }

    @Override
    public int hashCode() {
        int result = total;
        result = 31 * result + ok;
        result = 31 * result + failed;
        result = 31 * result + skipped;
        result = 31 * result + (int) (syncStartMillis ^ (syncStartMillis >>> 32));
        result = 31 * result + failedChannels.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SyncResult{" +
                "total=" + total +
                ", ok=" + ok +
                ", failed=" + failed +
                ", skipped=" + skipped +
                ", syncStartMillis=" + syncStartMillis +
                ", failedChannels=" + failedChannels +
                '}';
    }

    /**
     * Immutable per-channel failure record carried by
     * {@link SyncResult#getFailedChannels()}. httpStatus is null when the
     * failure had no HTTP layer at all (timeout, no connection, DNS) or an
     * HTTP-layer parse problem, mirroring the nullability of the channel's
     * last_http_status column (issue #50). retryAfterHeader holds the RAW
     * Retry-After response header value when the server sent one with the
     * failure (e.g. a 429) - parsing it into a delay is the backoff policy's job, the
     * transport detail is captured here while it is available.
     */
    public static class FailedChannel implements Serializable {
        private final long channelId;
        private final String feedName;
        private final String url;
        private final Integer httpStatus;
        private final String errorMessage;
        private final String retryAfterHeader;

        public FailedChannel(long channelId, String feedName, String url,
                             Integer httpStatus, String errorMessage,
                             String retryAfterHeader) {
            this.channelId = channelId;
            this.feedName = feedName;
            this.url = url;
            this.httpStatus = httpStatus;
            this.errorMessage = errorMessage;
            this.retryAfterHeader = retryAfterHeader;
        }

        public long getChannelId() {
            return channelId;
        }

        public String getFeedName() {
            return feedName;
        }

        public String getUrl() {
            return url;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }

        /**
         * Truncated (200 chars max) error message, null when the throwable
         * carried no message.
         */
        public String getErrorMessage() {
            return errorMessage;
        }

        /**
         * Raw Retry-After header value sent with the failure response, null
         * when absent.
         */
        public String getRetryAfterHeader() {
            return retryAfterHeader;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FailedChannel that = (FailedChannel) o;

            if (channelId != that.channelId) return false;
            if (feedName != null ? !feedName.equals(that.feedName) : that.feedName != null)
                return false;
            if (url != null ? !url.equals(that.url) : that.url != null)
                return false;
            if (httpStatus != null ? !httpStatus.equals(that.httpStatus) : that.httpStatus != null)
                return false;
            if (errorMessage != null ? !errorMessage.equals(that.errorMessage) : that.errorMessage != null)
                return false;
            return retryAfterHeader != null ? retryAfterHeader.equals(that.retryAfterHeader) : that.retryAfterHeader == null;
        }

        @Override
        public int hashCode() {
            int result = (int) (channelId ^ (channelId >>> 32));
            result = 31 * result + (feedName != null ? feedName.hashCode() : 0);
            result = 31 * result + (url != null ? url.hashCode() : 0);
            result = 31 * result + (httpStatus != null ? httpStatus.hashCode() : 0);
            result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
            result = 31 * result + (retryAfterHeader != null ? retryAfterHeader.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "FailedChannel{" +
                    "channelId=" + channelId +
                    ", feedName='" + feedName + '\'' +
                    ", url='" + url + '\'' +
                    ", httpStatus=" + httpStatus +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", retryAfterHeader='" + retryAfterHeader + '\'' +
                    '}';
        }
    }
}
