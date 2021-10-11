package m.co.rh.id.a_news_provider.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "android_notification")
public class AndroidNotification implements Serializable {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "request_id")
    public int requestId;

    @ColumnInfo(name = "group_key")
    public String groupKey;

    // ref id can refer to table id, i.e RssChannel.id
    @ColumnInfo(name = "ref_id")
    public long refId;
}
