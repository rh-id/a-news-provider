package m.co.rh.id.a_news_provider.app.workmanager;

public class ConstantsKey {
    public static final String KEY_STRING_URL = "KEY_STRING_URL";
    public static final String KEY_LONG_CHANNEL_IDS = "KEY_LONG_CHANNEL_IDS";
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
