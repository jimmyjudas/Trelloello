package thantz.trelloello;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.julienvey.trello.Trello;
import com.julienvey.trello.domain.*;
import com.julienvey.trello.impl.TrelloImpl;
import com.julienvey.trello.impl.domaininternal.CardState;
import com.julienvey.trello.impl.http.ApacheHttpClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

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

	private MyLogger logger;

	/*** Local command line entry points ***/
	public static void main(String[] args) {
		SpringApplication.run(TrelloelloApplication.class, args);
	}

	@Override
	public void run(String... args) {
		Logger javaLogger = Logger.getLogger(TrelloelloApplication.class.getName());
		logger = (message, messageArgs) -> javaLogger.info(String.format(message, messageArgs));
		processTrelloCards();
	}
	/***************************************/

	/******* AWS Lambda entry point *******/
	public void handleRequest(Object o, Context context) {
		LambdaLogger lambdaLogger = context.getLogger();
		logger = (message, args) -> lambdaLogger.log(String.format(message, args));
		processTrelloCards();
	}
	/**************************************/

	public void processTrelloCards() {
		Instant startTime = Instant.now();
		logger.log(String.format("Trelloello starting at %s", Helpers.PrettyFormat(startTime)));

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

		List<Label> labels = board.fetchLabels();
		Label label = labels.stream().filter(l -> l.getName().equals(LABEL_NAME)).findFirst()
				.orElseThrow(RuntimeException::new);

		List<TList> lists = board.fetchLists();
		TList holdingList = lists.stream().filter(l -> l.getName().equals(HOLDING_LIST_NAME)).findFirst()
				.orElseThrow(RuntimeException::new);

		reopenHeldCardsPastStartDate(lists.getFirst(), holdingList);

		holdArchivedRepeatingCards(board, holdingList);

		holdCardsWithStartDateInFuture(board, holdingList, label);

		Instant endTime = Instant.now();
		logger.log(String.format("Trelloello ending at %s. Took %s millisecs",
				Helpers.PrettyFormat(startTime),
				ChronoUnit.MILLIS.between(startTime,endTime)));
	}

	/// Find cards in the holding column that are past their start date and move them
	/// into the active column
	private void reopenHeldCardsPastStartDate(TList firstList, TList holdingList) {
		List<Card> cardsPastStartDate = holdingList.fetchCards().stream()
				.filter(card ->
						!card.getName().equals(TEMPLATE_CARD_NAME)
						&& Helpers.pastCardStartDate(card)
				).toList();


		if (!cardsPastStartDate.isEmpty()) {
			logger.log("Reopening %s cards past their start date:", cardsPastStartDate.size());

			for (Card card : cardsPastStartDate) {
				logger.log("\t%s (start date: %s)",
						card.getName(),
						Helpers.PrettyFormat(Helpers.getCardStartDateTime(card)));

				card.setIdList(firstList.getId());
				card.setPos(0);
				card.update();
			}
		}
	}

	/// Find repeating cards that have been archived, unarchive them and move them
	/// to the holding column
	private void holdArchivedRepeatingCards(Board board, TList holdingList) {
		List<Card> archivedLabelledCards = board.fetchFilteredCards(CardState.CLOSED).stream()
				.filter(c -> c.getLabels().stream()
						.anyMatch(label -> label.getName().equals(LABEL_NAME))).toList();

		boolean foundRepeatingCard = false;

		for (Card card : archivedLabelledCards) {
			RepetitionSchedule repetitionSchedule = RepetitionSchedule.fromCardDescription(card.getDesc());
			if (repetitionSchedule != null) {
				if (!foundRepeatingCard)
				{
					foundRepeatingCard = true;

					//Output stage log entry once we know we have at least one repeating card to process
					logger.log("Unarchiving and holding repeating cards:");
				}

				logger.log("\t%s", card.getName());

				LocalDateTime nextStartDateTime = LocalDateTime.now().plus(repetitionSchedule.getDuration().toTemporalAmount()).with(LocalTime.MIN);

				//Trello has no concept of start time, so store it in the title
				card.setName(CardNameWithTime.getNewName(card.getName(), repetitionSchedule.getStartTime()));

				card.setClosed(false); //Unarchive
				card.setIdList(holdingList.getId()); //Move to Holding List
				card.setStart(Helpers.localDateTimeToDate(nextStartDateTime));
				card.update();

				if (repetitionSchedule.getStartTime() != null) {
					nextStartDateTime = nextStartDateTime.with(repetitionSchedule.getStartTime());
				}
				logger.log("\t\tNew start = %s from %s%n", nextStartDateTime, repetitionSchedule.getSourceText());
			}
		}
	}

	/// Find cards which have had their start date set to sometime in the future, and move
	/// them to the holding column
	private void holdCardsWithStartDateInFuture(Board board, TList holdingList, Label label) {
		List<Card> cardsWithStartDateInFuture = board.fetchCards().stream()
				.filter(card -> !card.getIdList().equals(holdingList.getId())
						&& !Helpers.pastCardStartDate(card)
				).toList();

		if (!cardsWithStartDateInFuture.isEmpty()) {
			logger.log("Holding %s cards with start date in the future", cardsWithStartDateInFuture.size());

			for (Card card : cardsWithStartDateInFuture) {
				logger.log("\t%s (start date: %s)",
						card.getName(),
						Helpers.PrettyFormat(Helpers.getCardStartDateTime(card)));

				card.setIdList(holdingList.getId()); //Move to Holding List

				//Could use addLabels here but that makes a separate call to the API
				List<Label> labels = card.getLabels();
				if (!labels.contains(label)) {
					labels.add(label);
					card.setLabels(labels);
				}

				card.update();
			}
		}
	}

	public interface MyLogger {
		void log(String message, Object... args);
	}
}
