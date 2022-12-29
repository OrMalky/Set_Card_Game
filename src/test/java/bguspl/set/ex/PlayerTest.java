package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;
    private Queue<Integer> toPlace;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, ""), ui, util);
        player = new Player(env, dealer, table, 0, true);
        table.semaphore = new Semaphore(1);
        this.toPlace = new ConcurrentLinkedQueue<>();
        toPlace.add(0);
        player.setToPlace(toPlace);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {

        // force table.countCards to return 3
        //when(table.countCards()).thenReturn(3); // this part is just for demonstration

        // calculate the expected score for later
        int expectedScore = player.score() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.score());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }

    @Test
    void keyPressed(){
        //call the method we are testing
        player.keyPressed(0);

        //check that the table was called with the correct player
        assertEquals(2, player.getToPlace().size());

    }

    @Test
    void removeFromQueue_Valid(){
        //call the method we are testing
        player.removeFromToPlace(0);
        //check that the table was called with the correct player
        assertEquals(0, player.getToPlace().size());
    }

    @Test
    void removeFromQueue_Invalid(){
        //call the method we are testing
        player.removeFromToPlace(1);
        //check that the table was called with the correct player
        assertEquals(1, player.getToPlace().size());
    }
}
