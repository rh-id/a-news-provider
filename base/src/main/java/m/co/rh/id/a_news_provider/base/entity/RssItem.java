package m.co.rh.id.a_news_provider.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_news_provider.base.room.converter.Converter;

@Entity(tableName = "rss_item")
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
     * media:content URL
     */
    @ColumnInfo(name = "media_image")
    public String mediaImage;

    @ColumnInfo(name = "is_read")
    public boolean isRead;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "created_date_time")
    public Date createdDateTime;

    @TypeConverters({Converter.class})
    @ColumnInfo(name = "updated_date_time")
    public Date updatedDateTime;

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
                ", isRead=" + isRead +
                ", createdDateTime=" + createdDateTime +
                ", updatedDateTime=" + updatedDateTime +
                '}';
    }
}
