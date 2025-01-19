package thantz.trelloello;

import com.julienvey.trello.domain.Card;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

public class StartDateTests {
    private static Stream<Arguments> checkStartDatesParams() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime yesterday = today.minusDays(1);
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime endOfToday = today.toLocalDate().atTime(LocalTime.MAX);

        return Stream.of(
                Arguments.of(yesterday, null, true),
                Arguments.of(yesterday, "", true),
                Arguments.of(yesterday, "Title", true),
                Arguments.of(yesterday, "Title {15:00}", true),
                Arguments.of(yesterday, "{15:00}", true),

                Arguments.of(today, "Title", true),
                Arguments.of(today, "Title 23:59", true),
                Arguments.of(today, "Title {23:59}", false),

                Arguments.of(tomorrow, "Title", false),
                Arguments.of(tomorrow, "Title {00:00}", false),

                //We should ignore the time portion of the start date that Trello returns
                //as it officially doesn't support start times
                Arguments.of(endOfToday, "Title", true),
                Arguments.of(endOfToday, "Title {23:59}", false)
        );
    }

    @ParameterizedTest
    @MethodSource("checkStartDatesParams")
    void checkStartDates(LocalDateTime dateTime, String title, boolean result) {
        Card card = new Card();
        card.setStart(Helpers.localDateTimeToDate(dateTime));
        card.setName(title);
        Assert.isTrue(Helpers.pastCardStartDate(card) == result,
                String.format("We should %s past this card's start date (%s, '%s')",
                        result ? "be" : "not be", dateTime, title));
    }
}
