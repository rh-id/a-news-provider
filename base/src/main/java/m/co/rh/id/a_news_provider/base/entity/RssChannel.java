package m.co.rh.id.a_news_provider.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import java.io.Serializable;
import java.util.Date;

import m.co.rh.id.a_news_provider.base.room.converter.Converter;

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
                '}';
    }
}
