package m.co.rh.id.a_news_provider.app.workmanager;

public class ConstantsWork {
    public static final String UNIQUE_RSS_SYNC = "UniqueWork_RssSync";
    public static final String UNIQUE_PERIODIC_RSS_SYNC = "UniqueWork_Periodic_RssSync";
    /**
     * Per-channel sync work name prefix (issue #42 feed-health dashboard):
     * SyncChannelCmd enqueues a targeted sync as
     * {@code UNIQUE_RSS_SYNC_CHANNEL_PREFIX + channelId}. The per-channel name
     * is REQUIRED - one global name shared with the app-wide sync would let a
     * "Sync now" on feed B silently cancel feed A's pending sync
     * (ExistingWorkPolicy.REPLACE kills the older request). DeleteRssChannelCmd
     * cancels this exact name before the DB delete, so no mid-flight fetch of a
     * deleted feed can re-insert it.
     */
    public static final String UNIQUE_RSS_SYNC_CHANNEL_PREFIX = "UniqueWork_RssSyncChannel_";

    private ConstantsWork() {
    }
}
