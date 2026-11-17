package m.co.rh.id.a_news_provider.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_news_provider.base.room.converter.Converter;

/**
 * A subscribed RSS feed channel.
 * <p>
 * Note: instances of this entity are Java-serialized through a-navigator page
 * args. Adding fields without declaring a serialVersionUID is the existing
 * accepted risk - bundles never survive an app update, so a changed serial
 * shape can never be deserialized against an old stream.
 */
@Entity(tableName = "rss_channel")
public class RssChannel implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * User defined feed name
     */
    @ColumnInfo(name = "feed_name")
    public String feedName;

    @ColumnInfo(name = "title")
    public String title;

    /**
     * URL to reach this RSS feed
     */
    @ColumnInfo(name = "url")
    public String url;

    /**
     * HTML Link from RSS feed element
     */
    @ColumnInfo(name = "link")
    public String link;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "image_url")
    public String imageUrl;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    /**
     * True when the user paused this feed. Paused feeds keep their history
     * but are skipped by RssSyncWorker (no sync, no notifications).
     */
    @ColumnInfo(name = "is_paused", defaultValue = "0")
    public boolean isPaused;

    /**
     * Number of consecutive sync failures of this channel, reset to 0 on every
     * successful sync. Drives the sync backoff skip window (issue #50): the
     * more a feed fails in a row, the longer its next sync is deferred.
     */
    @ColumnInfo(name = "consecutive_failures", defaultValue = "0")
    public int consecutiveFailures;

    /**
     * The last HTTP status code observed for this channel, null when the last
     * attempt failed without an HTTP status (timeout, network error, parse
     * error). For display in the future feed-health dashboard (issue #42).
     */
    @ColumnInfo(name = "last_http_status")
    public Integer lastHttpStatus;

    /**
     * The last time an actual fetch attempt was made for this channel. The
     * backoff skip must NOT update it: refreshing the stamp on skipped runs
     * would self-extend the skip window forever.
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "last_sync_attempt_date_time")
    public Date lastSyncAttemptDateTime;

    /**
     * The truncated last error message (~200 chars) of this channel, null when
     * the last sync succeeded or no sync ran yet. For display in the future
     * feed-health dashboard (issue #42).
     */
    @ColumnInfo(name = "last_error")
    public String lastError;

    @Override
    public String toString() {
        return "RssChannel{" +
                "id=" + id +
                ", feedName='" + feedName + '\'' +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", link='" + link + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", updatedDateTime=" + updatedDateTime +
                ", isPaused=" + isPaused +
                ", consecutiveFailures=" + consecutiveFailures +
                ", lastHttpStatus=" + lastHttpStatus +
                ", lastSyncAttemptDateTime=" + lastSyncAttemptDateTime +
                ", lastError='" + lastError + '\'' +
                '}';
    }
}
