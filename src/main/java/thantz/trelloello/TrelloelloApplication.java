package thantz.trelloello;

import com.julienvey.trello.Trello;
import com.julienvey.trello.domain.*;
import com.julienvey.trello.impl.TrelloImpl;
import com.julienvey.trello.impl.domaininternal.CardState;
import com.julienvey.trello.impl.http.ApacheHttpClient;
import net.time4j.Duration;
import net.time4j.IsoUnit;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class TrelloelloApplication implements CommandLineRunner {

	/******* Local Config ********/

	//To get these from Trello, see https://developer.atlassian.com/cloud/trello/guides/rest-api/api-introduction/
	private final String TRELLO_API_KEY = "";
	private final String TRELLO_SERVER_TOKEN = "";

	//The ID of the Board you will be working against. To find this, use the commented out code below
	private final String BOARD_ID = "";
	//Names of various entities used in the implemenation
	private final String HOLDING_LIST_NAME = "Trelloello";
	private final String LABEL_NAME = "Trelloello";
	private final String TEMPLATE_CARD_NAME = "Template";
	/*****************************/

	public static void main(String[] args) {
		SpringApplication.run(TrelloelloApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		Trello trelloApi = new TrelloImpl(TRELLO_API_KEY, TRELLO_SERVER_TOKEN, new ApacheHttpClient());

		/*
		//Getting the BOARD_ID:
		//If you don't have a value for the BOARD_ID constant above, this code will allow you to find
		//it. Finding it once and then hardcoding it into the constant allows you to reduce the number
		//of calls the applications needs to make each time it runs

		String boardName = "TARS"; //If you're a member of more than one board, the name of the exact board you want the ID for

		List<Board> boards = trelloApi.getMemberInformation().getBoards();
		String boardId = boards.stream().filter(b -> StringUtils.isBlank(boardName) || b.getName().equals(boardName))
				.findFirst().orElseThrow(Exception::new).getId();
		*/

		Board board = trelloApi.getBoard(BOARD_ID);

		List<TList> lists = board.fetchLists();
		TList holdingList = lists.stream().filter(l -> l.getName().equals(HOLDING_LIST_NAME)).findFirst()
				.orElseThrow(Exception::new);

		reopenHeldCardsPastStartDate(lists.getFirst(), holdingList);

		holdArchivedRepeatingCards(board, holdingList);

	}

	/// Find cards in the holding column that are past their start date and move them
	/// into the active column
	private void reopenHeldCardsPastStartDate(TList firstList, TList holdingList) {
		List<Card> cardsPastStartDate = holdingList.fetchCards().stream()
				.filter(card ->
						!card.getName().equals(TEMPLATE_CARD_NAME)
						& Helpers.pastCardStartDate(card)
				).toList();

		for (Card card : cardsPastStartDate) {
			card.setIdList(firstList.getId());
			card.setPos(0);
			card.update();
		}
	}

	/// Find repeating cards that have been archived, unarchive them and move them
	/// to the holding column
	private void holdArchivedRepeatingCards(Board board, TList holdingList) throws Exception {
		List<Card> archivedRepeatingCards = board.fetchFilteredCards(CardState.CLOSED).stream()
				.filter(c -> c.getLabels().stream()
						.anyMatch(label -> label.getName().equals(LABEL_NAME))).toList();

		for (Card card : archivedRepeatingCards) {
			//The string we're looking for is an ISO 8601 duration, followed by a time, e.g:
			//	{P3D 18:00}
			//	{P1WT1M 5:21}
			//	{P1Y}
			String regexPattern = "\\{(P\\S{2,})(?:\\s(\\d{1,2}:\\d{2}))?}";

			Pattern pattern = Pattern.compile(regexPattern);
			Matcher matcher = pattern.matcher(card.getDesc());
			if (matcher.find()) {
				LocalDateTime nextStartDateTime;

				String durationString = matcher.group(1);
				Duration<IsoUnit> duration;
				try {
					duration = Duration.parsePeriod(durationString);
				}
				catch (Exception e) {
					throw new RuntimeException(String.format("Can't parse duration %s for card %s", durationString, card.getName()));
				}

				nextStartDateTime = LocalDateTime.now().plus(duration.toTemporalAmount()).with(LocalTime.MIN);

				LocalTime startTime = null;
				String timeString = matcher.group(2);
				if (timeString != null) { //We have a time portion as well
					try {
						startTime = LocalTime.parse(timeString);
					}
					catch (Exception e) {
						throw new RuntimeException(String.format("Can't parse time %s for card %s", timeString, card.getName()));
					}
				}

				card.setName(CardNameWithTime.getNewName(card.getName(), startTime)); //Trello has no concept of start time, so store it in the title
				card.setClosed(false); //Unarchive
				card.setIdList(holdingList.getId()); //Move to Holding List
				card.setStart(Helpers.localDateTimeToDate(nextStartDateTime));

				if (startTime != null) {
					nextStartDateTime = nextStartDateTime.with(startTime);
				}
				System.out.printf("Unarchived card %s. New start = %s from %s%n", card.getName(), nextStartDateTime, matcher.group());
			} else {
				throw new Exception(String.format("Duration regex not found for card %s", card.getName()));
			}

			card.update();
		}
	}
}
