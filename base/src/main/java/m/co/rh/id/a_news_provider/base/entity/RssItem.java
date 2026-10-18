package m.co.rh.id.a_news_provider.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_news_provider.base.room.converter.Converter;

@Entity(tableName = "rss_item",
        indices = {@Index(value = {"channel_id"}), @Index(value = {"link"})}
)
public class RssItem implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * RssChannel.id
     */
    @ColumnInfo(name = "channel_id")
    public Long channelId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "link")
    public String link;

    @ColumnInfo(name = "description")
    public String description;

    /**
     * Publication date
     */
    @TypeConverters({Converter.class})
    @ColumnInfo(name = "pub_date")
    public Date pubDate;

    /**
     * media:content URL or enclosure URL
     */
    @ColumnInfo(name = "media_image")
    public String mediaImage;

    /**
     * media:content URL or enclosure URL
     */
    @ColumnInfo(name = "media_video")
    public String mediaVideo;

    /**
     * Feed-provided category terms joined with
     * {@link m.co.rh.id.a_news_provider.base.entity.RssItemCategories#DELIMITER},
     * see {@link m.co.rh.id.a_news_provider.base.entity.RssItemCategories}.
     */
    @ColumnInfo(name = "categories")
    public String categories;

    @ColumnInfo(name = "is_read")
    public boolean isRead;

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    public boolean isFavorite;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

    /**
     * Number of rss_item rows sharing this row's link across ALL channels (the
     * absolute copy count, computed by RssDao.countRssItemsByLinks and filled by
     * PagedRssItemsCmd when the all-channels view runs the link-grouped queries,
     * issue #45). NOT a column: a real column would go stale on every sync, while
     * this field is recomputed per loaded page. {@code transient} keeps it out of
     * the Serializable form (the detail-page args), where it is meaningless; the
     * default 0 means "unknown", the UI only badges values &gt; 1.
     */
    @Ignore
    public transient int duplicateCount;

    @Override
    public String toString() {
        return "RssItem{" +
                "id=" + id +
                ", channelId=" + channelId +
                ", title='" + title + '\'' +
                ", link='" + link + '\'' +
                ", description='" + description + '\'' +
                ", pubDate=" + pubDate +
                ", mediaImage='" + mediaImage + '\'' +
                ", categories='" + categories + '\'' +
                ", isRead=" + isRead +
                ", isFavorite=" + isFavorite +
                ", createdDateTime=" + createdDateTime +
                ", updatedDateTime=" + updatedDateTime +
                ", duplicateCount=" + duplicateCount +
                '}';
    }
}
