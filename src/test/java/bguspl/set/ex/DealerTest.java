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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class DealerTest {
    Dealer dealer;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;
    @Mock
    private Player player;
    private Player[] players;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    void assertInvariants() {
        for(int i = 0; i < players.length; i++) {
            assertTrue(players[i].id >= 0);
            assertTrue(players[i].score() >= 0);
        }
    }

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        Config config = new Config(logger, properties);
        Env env = new Env(logger, config, ui, util);
        players = new Player[3];
        for(int i = 0; i < players.length; i++) {
            players[i] = new Player(env, dealer, table, i, true);
        }
        table.semaphore = new Semaphore(1);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];
        for(int i = 0; i < slotToCard.length; i++) {
            slotToCard[i] = i;
        }
        for(int i = 0; i < cardToSlot.length; i++) {
            cardToSlot[i] = i;
        }
        dealer = new Dealer(env, table, players);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    /**
     * Tests that check when the dealer return that a set is valid set
     */
    @Test
    void checkSet_ValidSet(){
        when(table.getSlotToCard()).thenReturn(slotToCard);
        when(table.getPlayerTokens(0)).thenReturn(new ArrayList<>(Arrays.asList(1, 2, 3)));
        when(util.testSet(new int[]{1,2,3})).thenReturn(true);
        assertEquals(true, dealer.checkSet(0));
    }

    @Test
    void checkSet_InvalidSet(){
        when(table.getSlotToCard()).thenReturn(slotToCard);
        when(table.getPlayerTokens(0)).thenReturn(new ArrayList<>(Arrays.asList(1, 2, 3)));
        when(util.testSet(new int[]{1,2,3})).thenReturn(false);
        assertFalse(dealer.checkSet(0));
    }
    /**
     * Tests that check if the dealer should finish the game or not
     */
    @Test
    void shouldFinish_No(){
        when(table.checkForSets()).thenReturn(true);
        when(util.findSets(dealer.getDeck(),1)).thenReturn(new ArrayList<>());
        assertFalse(dealer.shouldFinish());
    }

    @Test
    void shouldFinish_Yes(){
        when(table.checkForSets()).thenReturn(false);
        when(util.findSets(dealer.getDeck(),1)).thenReturn(new ArrayList<>());
        assertEquals(true, dealer.shouldFinish());
    }

    @Test
    void shouldFinish_No2(){
        when(table.checkForSets()).thenReturn(true);
        assertEquals(false, dealer.shouldFinish());
    }

    @Test
    void shouldFinish_No3(){
        List<int[]> sets = new ArrayList<>();
        sets.add(new int[]{1,2,3});
        when(util.findSets(dealer.getDeck(),1)).thenReturn(sets);
        assertEquals(false, dealer.shouldFinish());
    }
}
