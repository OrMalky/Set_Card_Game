package bguspl.set.ex;

import bguspl.set.Env;
//import sun.jvm.hotspot.runtime.Threads;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = 15000;
    private long startTime = System.currentTimeMillis();
    private long currentTime = 0;
    Thread[] playresThreads;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playresThreads = new Thread[players.length];
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player, "Player " + player.id);
            player.setPlayerThread(playerThread);
            playerThread.start();
            playresThreads[player.id] = playerThread;
        }

        Collections.shuffle(deck);
        while (!shouldFinish()) {
            placeCardsOnTable();
            startTime = System.currentTimeMillis();
            timerLoop();
            // updateTimerDisplay(false);
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (currentTime < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            removeCardsFromTable();
            placeCardsOnTable();
            updateTimerDisplay(false);
        }
        updateTimerDisplay(true);
        if (!shouldFinish()) {
            synchronized(this){
                removeAllTokensFromTable();
                removeAllCardsFromTable();
                placeCardsOnTable();
            }
        }
    }

    public boolean checkSet(int player, List<Integer> set){
        int[] cards = new int[3];
        int p = 0;
        for (Integer i : set) {
            cards[p] = table.slotToCard[i];
            p++;
        }
        
        if(env.util.testSet(cards)){
            players[player].point();
            return true;
        }
        else { //if the set is not valid
            long millies = 1000;
            env.ui.setFreeze(player, millies);
            players[player].penalty(); //penalize the player
            return false;
            }
        }


    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        while (table.countCards() < 12) {
            int card = deck.get(0);
            table.placeCard(card, table.countCards());
            deck.remove(0);
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {};
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            currentTime = 0;
            startTime = System.currentTimeMillis();
        }
        currentTime = System.currentTimeMillis() - startTime;
        long t = reshuffleTime - currentTime;
        env.ui.setCountdown(t, t <= 10000);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < 12; i++) {
            Integer c = table.getCard(i);
            table.removeCard(i);
            deck.add(c);
        }
        Collections.shuffle(deck);
    }

    public void removeAllTokensFromTable() {
        table.resetAllTokens();
        for (Player player : players) {
            player.resetTokens();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement

    }
}
