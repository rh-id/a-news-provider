package m.co.rh.id.a_news_provider.base;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import m.co.rh.id.a_news_provider.base.room.DbMigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DbMigrationTest {
    private static final String TEST_DB = DbMigrationTest.class.getName()
            + "-migration-test";

    private static final long BASE_TIME = 1700000000000L;


    @Rule
    public MigrationTestHelper helper;

    public DbMigrationTest() {
        helper = new MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
                AppDatabase.class.getCanonicalName(),
                new FrameworkSQLiteOpenHelperFactory());
    }

    @Test
    public void migrateAll() throws IOException {
        // Create earliest version of the database.
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 1);
        db.close();

        // Open latest version of the database. Room will validate the schema
        // once all migrations execute.
        AppDatabase appDb = Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class,
                TEST_DB)
                .addMigrations(DbMigration.getAll()).build();
        appDb.getOpenHelper().getWritableDatabase();
        appDb.close();
    }

    // ==================================================================================
    // MIGRATION_8_9: duplicate rss_channel merge + url canonicalization
    // ==================================================================================

    @Test
    public void migrate8to9_mergesLegacyAndCanonicalDuplicate() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);
        // legacy-spelled row (id 1, keeper because it has the lowest id)
        insertRssChannel(db, 1, "Cool Feed", "https://CoolFeed.com/rss/");
        // canonical-spelled duplicate row (id 2, loser)
        insertRssChannel(db, 2, "Cool Feed II", "https://coolfeed.com/rss");
        // channel 1 items: overlapping link (read on the LEGACY item), a favorite, a plain one
        insertRssItem(db, 11, 1, "https://coolfeed.com/post-1", true, false);
        insertRssItem(db, 12, 1, "https://coolfeed.com/post-2", false, true);
        insertRssItem(db, 13, 1, "https://coolfeed.com/post-3", false, false);
        // channel 2 items: overlapping link (unread), and a loser-only favorite
        insertRssItem(db, 14, 2, "https://coolfeed.com/post-1", false, false);
        insertRssItem(db, 15, 2, "https://coolfeed.com/post-4", false, true);
        // notifications: one pointing at the loser channel (id 2), one at the keeper (id 1)
        insertAndroidNotification(db, 101, 1, "group-101", 2);
        insertAndroidNotification(db, 102, 2, "group-102", 1);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 9,
                true, DbMigration.MIGRATION_8_9);

        try {
            // the duplicate channel row is gone, only the keeper remains
            assertEquals(1, readLong(migrated,
                    "SELECT COUNT(id) FROM rss_channel"));
            assertEquals(1, readLong(migrated,
                    "SELECT COUNT(id) FROM rss_channel WHERE id = 1"));
            // keeper keeps its feedName and gets the canonicalized url
            assertEquals("Cool Feed", readString(migrated,
                    "SELECT feed_name FROM rss_channel WHERE id = 1"));
            assertEquals("https://coolfeed.com/rss", readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 1"));
            // overlapping link: merged into the keeper's item, read state OR-ed, favorite OR-ed
            assertEquals(1, readLong(migrated,
                    "SELECT COUNT(id) FROM rss_item WHERE link = 'https://coolfeed.com/post-1'"));
            assertEquals(11, readLong(migrated,
                    "SELECT id FROM rss_item WHERE link = 'https://coolfeed.com/post-1'"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_read FROM rss_item WHERE id = 11"));
            assertEquals(0, readLong(migrated,
                    "SELECT is_favorite FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 11"));
            // keeper's favorite item is untouched
            assertEquals(1, readLong(migrated,
                    "SELECT is_favorite FROM rss_item WHERE id = 12"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 12"));
            // loser-only favorite item survived, re-pointed to the keeper channel
            assertEquals(15, readLong(migrated,
                    "SELECT id FROM rss_item WHERE link = 'https://coolfeed.com/post-4'"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 15"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_favorite FROM rss_item WHERE id = 15"));
            // total items = union of both channels
            assertEquals(4, readLong(migrated, "SELECT COUNT(id) FROM rss_item"));
            // notification referencing the losing channel is re-pointed to the keeper
            assertEquals(1, readLong(migrated,
                    "SELECT ref_id FROM android_notification WHERE id = 101"));
            // notification referencing the keeper channel is unchanged
            assertEquals(1, readLong(migrated,
                    "SELECT ref_id FROM android_notification WHERE id = 102"));
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate8to9_mergesBothCanonicalDuplicatesKeeperIsLowestId() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);
        // two rows stored with the exact same canonical spelling but different feed names
        insertRssChannel(db, 1, "First Feed", "https://a.com/f");
        insertRssChannel(db, 2, "Second Feed", "https://a.com/f");
        insertRssItem(db, 11, 1, "https://a.com/item-1", false, false);
        insertRssItem(db, 12, 2, "https://a.com/item-2", false, false);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 9,
                true, DbMigration.MIGRATION_8_9);

        try {
            assertEquals(1, readLong(migrated, "SELECT COUNT(id) FROM rss_channel"));
            // keeper = lower id, name comes from the keeper
            assertEquals(1, readLong(migrated, "SELECT id FROM rss_channel"));
            assertEquals("First Feed", readString(migrated,
                    "SELECT feed_name FROM rss_channel WHERE id = 1"));
            assertEquals("https://a.com/f", readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 1"));
            // items of both channels survived under the keeper
            assertEquals(2, readLong(migrated, "SELECT COUNT(id) FROM rss_item"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 12"));
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate8to9_nullUrlRowIsNeverGroupedAndStaysUntouched() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);
        // a row without a url plus a duplicate pair - the null-url row must not join
        // any group and must survive untouched
        insertRssChannel(db, 1, "Null Url Feed", null);
        insertRssChannel(db, 2, "Dup Legacy", "https://B.com/rss/");
        insertRssChannel(db, 3, "Dup Canonical", "https://b.com/rss");
        insertRssItem(db, 11, 1, "https://null.com/item-1", true, false);
        insertRssItem(db, 12, 1, null, false, true);
        insertRssItem(db, 13, 2, "https://b.com/item-1", false, false);
        insertRssItem(db, 14, 3, "https://b.com/item-2", false, false);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 9,
                true, DbMigration.MIGRATION_8_9);

        try {
            // null-url channel untouched: still present, url still null
            assertEquals(1, readLong(migrated,
                    "SELECT COUNT(id) FROM rss_channel WHERE id = 1"));
            assertNull(readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 1"));
            assertEquals("Null Url Feed", readString(migrated,
                    "SELECT feed_name FROM rss_channel WHERE id = 1"));
            // its items untouched, including the null-link one
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_read FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 12"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_favorite FROM rss_item WHERE id = 12"));
            // the duplicate pair still merged
            assertEquals(2, readLong(migrated, "SELECT COUNT(id) FROM rss_channel"));
            assertEquals(1, readLong(migrated,
                    "SELECT COUNT(id) FROM rss_channel WHERE id = 2"));
            assertEquals("https://b.com/rss", readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 2"));
            // union of all four seeded items - none of their links overlapped
            assertEquals(4, readLong(migrated, "SELECT COUNT(id) FROM rss_item"));
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate8to9_cleanDatabaseOnlyCanonicalizesUrl() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);
        // no duplicates - only a legacy-spelled singleton that must be canonicalized
        insertRssChannel(db, 1, "Clean Feed", "https://X.com/F/");
        insertRssChannel(db, 2, "Already Canonical", "https://ok.com/feed");
        insertRssItem(db, 11, 1, "https://x.com/item-1", true, false);
        insertRssItem(db, 12, 1, "https://x.com/item-2", false, true);
        insertRssItem(db, 13, 2, "https://ok.com/item-1", false, false);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 9,
                true, DbMigration.MIGRATION_8_9);

        try {
            assertEquals(2, readLong(migrated, "SELECT COUNT(id) FROM rss_channel"));
            // legacy spelling self-healed: scheme+host lowercased, trailing slash
            // stripped, path case preserved (UrlNormalizer semantics)
            assertEquals("https://x.com/F", readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 1"));
            assertEquals("Clean Feed", readString(migrated,
                    "SELECT feed_name FROM rss_channel WHERE id = 1"));
            // already-canonical row unchanged
            assertEquals("https://ok.com/feed", readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 2"));
            // all items untouched
            assertEquals(3, readLong(migrated, "SELECT COUNT(id) FROM rss_item"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_read FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_favorite FROM rss_item WHERE id = 12"));
        } finally {
            migrated.close();
        }
    }

    @Test
    public void migrate8to9_threeRowGroupCollapsesToOne() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);
        // three spellings of the same feed - all normalize to "https://c.com/feed"
        insertRssChannel(db, 1, "Group Feed", "https://c.com/feed");
        insertRssChannel(db, 2, "Group Feed Upper", "HTTPS://C.COM/feed");
        insertRssChannel(db, 3, "Group Feed Slashes", "https://c.com/feed///");
        // a link present in all three rows with different states to exercise the OR-merge
        insertRssItem(db, 11, 1, "https://c.com/shared", false, false);
        insertRssItem(db, 12, 2, "https://c.com/shared", true, false);
        insertRssItem(db, 13, 3, "https://c.com/shared", false, true);
        // loser-only items
        insertRssItem(db, 14, 2, "https://c.com/two", false, false);
        insertRssItem(db, 15, 3, "https://c.com/three", false, false);
        db.close();

        SupportSQLiteDatabase migrated = helper.runMigrationsAndValidate(TEST_DB, 9,
                true, DbMigration.MIGRATION_8_9);

        try {
            assertEquals(1, readLong(migrated, "SELECT COUNT(id) FROM rss_channel"));
            assertEquals(1, readLong(migrated, "SELECT id FROM rss_channel"));
            assertEquals("Group Feed", readString(migrated,
                    "SELECT feed_name FROM rss_channel WHERE id = 1"));
            assertEquals("https://c.com/feed", readString(migrated,
                    "SELECT url FROM rss_channel WHERE id = 1"));
            // shared link collapsed into the keeper's item with states OR-ed together
            assertEquals(1, readLong(migrated,
                    "SELECT COUNT(id) FROM rss_item WHERE link = 'https://c.com/shared'"));
            assertEquals(11, readLong(migrated,
                    "SELECT id FROM rss_item WHERE link = 'https://c.com/shared'"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_read FROM rss_item WHERE id = 11"));
            assertEquals(1, readLong(migrated,
                    "SELECT is_favorite FROM rss_item WHERE id = 11"));
            // loser-only items re-pointed to the keeper
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 14"));
            assertEquals(1, readLong(migrated,
                    "SELECT channel_id FROM rss_item WHERE id = 15"));
            // total = one shared + two loser-only items
            assertEquals(3, readLong(migrated, "SELECT COUNT(id) FROM rss_item"));
            assertTrue(readLong(migrated,
                    "SELECT COUNT(id) FROM rss_item WHERE channel_id = 1") == 3);
        } finally {
            migrated.close();
        }
    }

    // ==================================================================================
    // helpers: seed rows using the exact version-8 schema, read values after migration
    // ==================================================================================

    private void insertRssChannel(SupportSQLiteDatabase db, long id, String feedName,
                                  String url) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("feed_name", feedName);
        if (url == null) {
            values.putNull("url");
        } else {
            values.put("url", url);
        }
        values.put("title", "Title " + id);
        values.put("link", "https://channel-" + id + ".example");
        values.put("description", "Description " + id);
        values.putNull("image_url");
        values.put("created_date_time", BASE_TIME + id);
        values.put("updated_date_time", BASE_TIME + id);
        assertEquals(id, db.insert("rss_channel", SQLiteDatabase.CONFLICT_FAIL, values));
    }

    private void insertRssItem(SupportSQLiteDatabase db, long id, long channelId,
                               String link, boolean isRead, boolean isFavorite) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("channel_id", channelId);
        values.put("title", "Item " + id);
        if (link == null) {
            values.putNull("link");
        } else {
            values.put("link", link);
        }
        values.put("description", "Description " + id);
        values.put("pub_date", BASE_TIME + id);
        values.putNull("media_image");
        values.putNull("media_video");
        values.put("is_read", isRead ? 1 : 0);
        values.put("is_favorite", isFavorite ? 1 : 0);
        values.put("created_date_time", BASE_TIME + id);
        values.put("updated_date_time", BASE_TIME + id);
        assertEquals(id, db.insert("rss_item", SQLiteDatabase.CONFLICT_FAIL, values));
    }

    private void insertAndroidNotification(SupportSQLiteDatabase db, long id, int requestId,
                                           String groupKey, long refId) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("request_id", requestId);
        values.put("group_key", groupKey);
        values.put("ref_id", refId);
        assertEquals(id, db.insert("android_notification",
                SQLiteDatabase.CONFLICT_FAIL, values));
    }

    private long readLong(SupportSQLiteDatabase db, String sql) {
        Cursor cursor = db.query(sql);
        try {
            assertTrue("expected a row for: " + sql, cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private String readString(SupportSQLiteDatabase db, String sql) {
        Cursor cursor = db.query(sql);
        try {
            assertTrue("expected a row for: " + sql, cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getString(0);
        } finally {
            cursor.close();
        }
    }
}
