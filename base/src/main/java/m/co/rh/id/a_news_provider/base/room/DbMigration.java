package m.co.rh.id.a_news_provider.base.room;

import android.database.Cursor;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;

public class DbMigration {
    public static Migration[] getAll() {
        return new Migration[]{MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9};
    }

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `rss_item` ADD COLUMN pub_date INTEGER");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `rss_channel` ADD COLUMN image_url TEXT");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // cleanup android notification that are not deleted due to bug
            database.execSQL("DELETE FROM `android_notification`");
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `rss_item` ADD COLUMN media_image TEXT");
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `rss_item` ADD COLUMN media_video TEXT");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_rss_item_channel_id` ON `rss_item` (`channel_id`)");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `rss_item` ADD COLUMN `is_favorite` INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Proper fix for the duplicate rss_channel rows created before URL normalization
     * existed (issue #43 residual): channels whose stored URLs differ only by spelling
     * (trailing slashes, scheme/host casing, surrounding whitespace) are merged into
     * the oldest row (lowest id, the user's original feedName/createdDateTime), and
     * every stored channel URL is rewritten to its normalized spelling.
     * <p>
     * Item merging per duplicate group: an item of a losing channel whose link already
     * exists in the keeper channel is deleted while its is_read/is_favorite state is
     * OR-ed into the keeper item; any other item (including items with a null link,
     * which are never deduplicated) is re-pointed to the keeper channel with all its
     * columns preserved. android_notification rows referencing a losing channel are
     * re-pointed to the keeper so they are not orphaned. After the items and
     * notifications are handled the losing channel row is deleted.
     * <p>
     * Room 2.7.2 does not wrap {@link Migration#migrate(SupportSQLiteDatabase)} in a
     * transaction, so the transaction is created explicitly to keep the merge atomic.
     */
    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.beginTransaction();
            try {
                mergeDuplicateChannels(db);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    };

    private static void mergeDuplicateChannels(SupportSQLiteDatabase db) {
        // group channel ids by normalized url; the ORDER BY id ASC makes the first row
        // of every group the keeper (lowest id = the user's original feed)
        Map<String, List<Long>> groupsByNormalizedUrl = new LinkedHashMap<>();
        Cursor cursor = db.query("SELECT id, url FROM rss_channel ORDER BY id ASC");
        try {
            int idIndex = cursor.getColumnIndexOrThrow("id");
            int urlIndex = cursor.getColumnIndexOrThrow("url");
            while (cursor.moveToNext()) {
                long channelId = cursor.getLong(idIndex);
                String url = cursor.isNull(urlIndex) ? null : cursor.getString(urlIndex);
                if (url == null) {
                    // rows without a url are never grouped - each stays untouched
                    continue;
                }
                String normalizedUrl = new UrlNormalizer().normalizeUrl(url);
                List<Long> group = groupsByNormalizedUrl.get(normalizedUrl);
                if (group == null) {
                    group = new ArrayList<>();
                    groupsByNormalizedUrl.put(normalizedUrl, group);
                }
                group.add(channelId);
            }
        } finally {
            cursor.close();
        }
        // cursor is closed before any modification - safe to update and delete here
        for (Map.Entry<String, List<Long>> groupEntry : groupsByNormalizedUrl.entrySet()) {
            List<Long> channelIds = groupEntry.getValue();
            long keeperChannelId = channelIds.get(0);
            // canonicalize the keeper's url - also self-heals singleton groups whose
            // stored spelling was legacy (casing, whitespace, trailing slashes)
            db.execSQL("UPDATE rss_channel SET url = ? WHERE id = ?",
                    new Object[]{groupEntry.getKey(), keeperChannelId});
            for (int i = 1; i < channelIds.size(); i++) {
                long loserChannelId = channelIds.get(i);
                mergeRssItems(db, loserChannelId, keeperChannelId);
                // notifications referencing the losing channel are re-pointed to the
                // keeper, otherwise the orphaned ref_id self-deletes them on tap
                db.execSQL("UPDATE android_notification SET ref_id = ? WHERE ref_id = ?",
                        new Object[]{keeperChannelId, loserChannelId});
                // all loser items are deleted or re-pointed by now
                db.execSQL("DELETE FROM rss_channel WHERE id = ?", new Object[]{loserChannelId});
            }
        }
    }

    private static void mergeRssItems(SupportSQLiteDatabase db, long loserChannelId,
                                      long keeperChannelId) {
        // read all loser items first and close the cursor before modifying the table
        List<LoserRssItem> loserRssItems = new ArrayList<>();
        Cursor cursor = db.query(
                "SELECT id, link, is_read, is_favorite FROM rss_item WHERE channel_id = ?",
                new Object[]{loserChannelId});
        try {
            int idIndex = cursor.getColumnIndexOrThrow("id");
            int linkIndex = cursor.getColumnIndexOrThrow("link");
            int isReadIndex = cursor.getColumnIndexOrThrow("is_read");
            int isFavoriteIndex = cursor.getColumnIndexOrThrow("is_favorite");
            while (cursor.moveToNext()) {
                loserRssItems.add(new LoserRssItem(
                        cursor.getLong(idIndex),
                        cursor.isNull(linkIndex) ? null : cursor.getString(linkIndex),
                        cursor.getInt(isReadIndex) != 0,
                        cursor.getInt(isFavoriteIndex) != 0));
            }
        } finally {
            cursor.close();
        }
        for (LoserRssItem loserRssItem : loserRssItems) {
            if (loserRssItem.link == null) {
                // null-link items can never match a keeper item - always re-point
                repointRssItem(db, loserRssItem.id, keeperChannelId);
                continue;
            }
            long keeperItemId = -1;
            boolean keeperIsRead = false;
            boolean keeperIsFavorite = false;
            Cursor keeperCursor = db.query(
                    "SELECT id, is_read, is_favorite FROM rss_item WHERE channel_id = ? AND link = ?",
                    new Object[]{keeperChannelId, loserRssItem.link});
            try {
                if (keeperCursor.moveToFirst()) {
                    keeperItemId = keeperCursor.getLong(0);
                    keeperIsRead = keeperCursor.getInt(1) != 0;
                    keeperIsFavorite = keeperCursor.getInt(2) != 0;
                }
            } finally {
                keeperCursor.close();
            }
            if (keeperItemId == -1) {
                // no counterpart in the keeper channel - re-point with all columns preserved
                repointRssItem(db, loserRssItem.id, keeperChannelId);
            } else {
                // duplicate link - OR the states so no read/favorite state is lost
                db.execSQL("UPDATE rss_item SET is_read = ?, is_favorite = ? WHERE id = ?",
                        new Object[]{loserRssItem.isRead || keeperIsRead,
                                loserRssItem.isFavorite || keeperIsFavorite, keeperItemId});
                db.execSQL("DELETE FROM rss_item WHERE id = ?", new Object[]{loserRssItem.id});
            }
        }
    }

    private static void repointRssItem(SupportSQLiteDatabase db, long rssItemId,
                                       long keeperChannelId) {
        db.execSQL("UPDATE rss_item SET channel_id = ? WHERE id = ?",
                new Object[]{keeperChannelId, rssItemId});
    }

    private static final class LoserRssItem {
        private final long id;
        private final String link;
        private final boolean isRead;
        private final boolean isFavorite;

        private LoserRssItem(long id, String link, boolean isRead, boolean isFavorite) {
            this.id = id;
            this.link = link;
            this.isRead = isRead;
            this.isFavorite = isFavorite;
        }
    }
}
