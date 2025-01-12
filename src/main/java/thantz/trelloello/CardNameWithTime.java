package thantz.trelloello;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardNameWithTime {
    private final String name;
    private LocalTime time = null;

    private CardNameWithTime(String nameString) {
        //Search for titles containing a time in, e.g:
        //  This is a title without a time
        //  This title has a time {14:00}
        String regexPattern = "(.*)\\{(\\d{1,2}:\\d{2})}";

        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(nameString);
        if (matcher.find()) {
            String timeString = null;
            try {
                timeString = matcher.group(2);
                LocalTime startTime = LocalTime.parse(timeString);
                name = matcher.group(1).trim();
                time = startTime;
            }
            catch (Exception e) {
                throw new RuntimeException(String.format("Can't parse time %s", timeString));
            }
        } else {
            name = nameString;
        }
    }

    public static LocalTime getTime(String nameString) {
        CardNameWithTime cnwt = new CardNameWithTime(nameString);
        return cnwt.time;
    }

    public static String getNewName(String currentName, LocalTime newTime) {
        CardNameWithTime cnwt = new CardNameWithTime(currentName);
        cnwt.time = newTime;

        if (cnwt.time == null) {
            return cnwt.name;
        }
        return String.format("%s {%s}", cnwt.name, cnwt.time);
    }
}
