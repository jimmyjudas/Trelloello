package thantz.trelloello;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class DateTimeHelpers {
    public static Date localDateTimeToDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }
}
