package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TableTest {

    Table table;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;
    private ArrayList<Integer> usedSlots;
    @Mock
    private Logger logger;
    @Mock
    private UserInterface ui;
    @Mock
    private Util util;
    @Mock
    private Player player;
    @Mock
    private Dealer dealer;

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
        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];
        usedSlots = new ArrayList<>();
        Env env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env, slotToCard, cardToSlot);
        table.setUsedSlots(usedSlots);
        player = new Player(env, dealer, table, 0, false);
        Player[] players = new Player[1];
        players[0] = player;
        dealer.setPlayers(players);
    }

    private int fillSomeSlots() {
        slotToCard[1] = 3;
        slotToCard[2] = 5;
        cardToSlot[3] = 1;
        cardToSlot[5] = 2;
        usedSlots.add(1);
        usedSlots.add(2);
        return 2;
    }

    private void fillAllSlots() {
        for (int i = 0; i < slotToCard.length; ++i) {
            slotToCard[i] = i;
            cardToSlot[i] = i;
            usedSlots.add(i);
        }
    }

    private void placeSomeCardsAndAssert() throws InterruptedException {
        table.placeCard(8, 2);

        assertEquals(8, (int) slotToCard[2]);
        assertEquals(2, (int) cardToSlot[8]);
    }

    @Test
    void countCards_NoSlotsAreFilled() {

        assertEquals(0, table.countCards());
    }

    @Test
    void countCards_SomeSlotsAreFilled() {

        int slotsFilled = fillSomeSlots();
        assertEquals(slotsFilled, table.countCards());
    }

    @Test
    void countCards_AllSlotsAreFilled() {

        fillAllSlots();
        assertEquals(slotToCard.length, table.countCards());
    }

    @Test
    void getUsedSlots_NoSlotsAreFilled() {

        List<Integer> usedSlots = table.getUsedSlots();
        assertEquals(0, usedSlots.size());
    }

    @Test
    void getUsedSlots_SomeSlotsAreFilled() {

        int numOfUsedSlots = fillSomeSlots();
        List<Integer> usedSlots = table.getUsedSlots();
        assertEquals(numOfUsedSlots, usedSlots.size());
        assertEquals(1, (int) usedSlots.get(0));
        assertEquals(2, (int) usedSlots.get(1));
    }

    @Test
    void getUsedSlots_AllSlotsAreFilled() {

        fillAllSlots();
        List<Integer> usedSlots = table.getUsedSlots();
        assertEquals(slotToCard.length, usedSlots.size());
        for (int i = 0; i < usedSlots.size(); ++i) {
            assertEquals(i, (int) usedSlots.get(i));
        }
    }

    @Test
    void placeCard_SomeSlotsAreFilled() throws InterruptedException {

        fillSomeSlots();
        placeSomeCardsAndAssert();
    }

    @Test
    void placeCard_AllSlotsAreFilled() throws InterruptedException {
        fillAllSlots();
        placeSomeCardsAndAssert();
    }

    @Test
    void removeCard_SomeSlotsAreFilled() throws InterruptedException {

        fillSomeSlots();
        table.removeCard(1);
        assertNull(slotToCard[1]);
        assertNull(cardToSlot[3]);
        assertEquals(1, usedSlots.size());
        assertEquals(2, (int) usedSlots.get(0));
    }

    @Test
    void removeCard_AllSlotsAreFilled() {
        fillAllSlots();
        for (int i = 0; i < slotToCard.length; i++) {
            table.removeCard(i);
        }
        assertNull(slotToCard[1]);
        assertNull(cardToSlot[3]);
        assertEquals(0, usedSlots.size());
    }

    @Test
    void placeToken_SomeSlotsAreFilled() {
        fillSomeSlots();
        table.placeToken(0, 2);
        assertEquals(1,table.getPlayerTokens(0).size());
    }

    @Test
    void placeToken_AllSlotsAreFilled() {
        fillAllSlots();
        table.placeToken(0, 2);
        assertNotEquals(0,table.getPlayerTokens(0).size());
    }

    static class MockUserInterface implements UserInterface {
        @Override
        public void dispose() {}
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }

        @Override
        public void spin() {}
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }

}
