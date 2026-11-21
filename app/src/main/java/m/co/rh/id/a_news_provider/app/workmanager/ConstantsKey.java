package m.co.rh.id.a_news_provider.app.workmanager;

public class ConstantsKey {
    public static final String KEY_STRING_URL = "KEY_STRING_URL";
    public static final String KEY_LONG_CHANNEL_IDS = "KEY_LONG_CHANNEL_IDS";
    /**
     * Single channel id for a TARGETED sync (issue #42 feed-health dashboard):
     * set as INPUT of RssSyncWorker by SyncChannelCmd ("Sync now" on one feed),
     * absent (0) for the app-wide sync runs. RssSyncWorker filters its channel
     * list to this id and bypasses the per-feed backoff skip for the run -
     * an explicit user retry is one deliberate request, not the schedule.
     */
    public static final String KEY_LONG_CHANNEL_ID = "KEY_LONG_CHANNEL_ID";
    /**
     * Wall-clock millis captured by {@code RssSyncWorker} when its work started.
     * Chained workers (RssSyncNotificationWorker, RssSyncChangeNotifierWorker) use it
     * to restrict their queries to rows the just-finished sync touched
     * ({@code updated_date_time >= syncStart}) so retained items (issue #37
     * upsert retention) are not re-announced as unread/new forever.
     */
    public static final String KEY_LONG_SYNC_START_MILLIS = "KEY_LONG_SYNC_START_MILLIS";
    public static final String KEY_FILE_ABSOLUTE_PATH = "KEY_FILE_ABSOLUTE_PATH";
    private ConstantsKey(){}
}
