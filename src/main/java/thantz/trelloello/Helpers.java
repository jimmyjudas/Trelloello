package thantz.trelloello;

import com.julienvey.trello.domain.Card;

import java.time.*;
import java.util.Date;

public class Helpers {
    public static Date localDateTimeToDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }

    public static LocalDate dateToLocalDate(Date date) {
        return LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static LocalDateTime dateToLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static boolean pastCardStartDate(Card card)
    {
        if (card.getStart() == null) {
            return true;
        } else {
            //Trello doesn't officially support start TIMES, only dates. Cards' start dates seem to
            //include a time, though we can't be sure what that time will be. Therefore, strip out
            //the time here
            LocalDate startDate = dateToLocalDate(card.getStart());

            //For start time we instead use the value from the card's title
            LocalTime time = CardNameWithTime.getTime(card.getName());
            if (time == null) {
                time = LocalTime.MIN;
            }

            LocalDateTime startDateTime = startDate.atTime(time);

            return LocalDateTime.now().isAfter(startDateTime);
        }
    }
}
