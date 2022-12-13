package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private long reshuffleTime = 60000;
    private long startTime = System.currentTimeMillis();
    private long currentTime = 0;
    private Thread[] playresThreads;
    private Queue<Integer> toRemove;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playresThreads = new Thread[players.length];
        this.toRemove = new ConcurrentLinkedQueue<Integer>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player, "Player " + player.id);
            playresThreads[player.id] = playerThread;
            playerThread.start();
        }

        Collections.shuffle(deck);
        while (!shouldFinish()) {
            placeCardsOnTable();
            startTime = System.currentTimeMillis();
            timerLoop();
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
            table.hints(); //for debug
        }
        updateTimerDisplay(true);
        if (!shouldFinish()) {
            synchronized(this){
                for (Player player : players) {
                    player.sleepUntilWoken();
                }
                removeAllTokensFromTable();
                removeAllCardsFromTable();
                placeCardsOnTable();
                for (Player player : players) {
                    player.wake();
                }
            }
        }
    }

    public boolean checkSet(int player, List<Integer> set){
        //Turn set to Array for checking
        int[] cards = new int[3];
        int p = 0;
        for (Integer i : set) {
            cards[p] = table.slotToCard[i];
            p++;
        }
        
        //If set is valid add a point to the player and remove the cards
        if(env.util.testSet(cards)){
            players[player].point();
            for (Integer i : set) {
                toRemove.add(i);
            }
            return true;
        } else { //if the set is not valid
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
        while(!toRemove.isEmpty()){
            int card = toRemove.poll();
            table.removeCard(card);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //Acquire table's semaphore permit
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Place cards from the deck on the table
        int slot = 0;
        while (slot < 12 && !deck.isEmpty()) {
            if(table.getCard(slot) == null){
                int card = deck.remove(0);
                table.placeCard(card, slot);
            }
            slot++;
        }

        //Release table's semaphore
        table.semaphore.release();

        System.out.println(deck.size() + " Cards left in deck");
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {};
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
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
        //Acquire table's semaphore permit
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Remove cards from table to the deck
        for (int i = 0; i < 12; i++) {
            Integer c = table.getCard(i);
            table.removeCard(i);
            deck.add(c);
        }
        Collections.shuffle(deck);  //Shuffle the deck

        //Release table's semaphore
        table.semaphore.release();
    }

    public void removeAllTokensFromTable() {
        //Acquire table's semaphore permit
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        //Remove all tokens from the table & release semaphore
        table.resetAllTokens();
        env.ui.removeTokens();
        table.semaphore.release();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int temp = 0;
        for(Player p : players){
            if(p.getScore() > temp){
                temp = p.getScore();
            }
        }
        int numOfPlayers = 0;
        for(Player p : players){
            if(p.getScore() == temp){
                numOfPlayers++;
            }
        }
        int[] winners = new int[numOfPlayers];
        for(Player p : players){
            for(int i = 0; i < numOfPlayers; i++){
                if(p.getScore() == temp){
                    winners[i] = p.id;
                }
            }
        }
        env.ui.announceWinner(winners);

    }
}
