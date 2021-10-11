package m.co.rh.id.a_news_provider.base.room.converter;

import androidx.room.ProvidedTypeConverter;
import androidx.room.TypeConverter;

import java.util.Date;

public class Converter {
    @TypeConverter
    public static Date dateFromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

}
