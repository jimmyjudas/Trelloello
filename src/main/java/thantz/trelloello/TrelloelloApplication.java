package thantz.trelloello;

import com.julienvey.trello.Trello;
import com.julienvey.trello.domain.Board;
import com.julienvey.trello.domain.Card;
import com.julienvey.trello.domain.TList;
import com.julienvey.trello.impl.TrelloImpl;
import com.julienvey.trello.impl.http.ApacheHttpClient;
import io.micrometer.common.util.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;
import java.util.Optional;

@SpringBootApplication
public class TrelloelloApplication implements CommandLineRunner {

	/******* Local Config ********/

	//To get these from Trello, see https://developer.atlassian.com/cloud/trello/guides/rest-api/api-introduction/
	private final String TRELLO_API_KEY = "";
	private final String TRELLO_SERVER_TOKEN = "";

	//The ID of the Board you will be working against. To find this, use the commented out code below
	private final String BOARD_ID = ""; //To get this, see commented out code below
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

		String username = ""; //Your Trello account email address
		String boardName = ""; //If you're a member of more than one board, the name of the exact board you want the ID for

		List<Board> boards = trelloApi.getMemberBoards(username);
		String boardId = boards.stream().filter(b -> StringUtils.isBlank(boardName) || b.getName().equals(boardName))
				.findFirst().orElseThrow(Exception::new).getId();
		*/

		Board board = trelloApi.getBoard(BOARD_ID);

		List<TList> lists = board.fetchLists();

		List<Card> cards = board.fetchCards();
	}
}
