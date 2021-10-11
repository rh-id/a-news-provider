package m.co.rh.id.a_news_provider.base.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import m.co.rh.id.a_news_provider.base.entity.AndroidNotification;

@Dao
public abstract class AndroidNotificationDao {

    @Query("SELECT * FROM android_notification WHERE request_id = :requestId")
    public abstract AndroidNotification findByRequestId(int requestId);

    @Query("SELECT COUNT(id) FROM android_notification")
    public abstract long count();

    @Query("DELETE FROM android_notification WHERE request_id = :requestId")
    public abstract void deleteByRequestId(int requestId);

    @Transaction
    public void insertNotification(AndroidNotification androidNotification) {
        long count = count();
        androidNotification.requestId = (int) (count % Integer.MAX_VALUE);
        long id = insert(androidNotification);
        androidNotification.id = id;
    }


    @Insert
    protected abstract long insert(AndroidNotification androidNotification);
}
