package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private long warningTime;
    private long startTime;
    private long currentTime;
    private boolean timerMode;
    private boolean displayTimer;

    private int tableSize;

    private Queue<Integer> toRemove;
    public Queue<Integer> toCheck;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.toRemove = new ConcurrentLinkedQueue<Integer>();
        this.toCheck = new ArrayBlockingQueue<Integer>(4);

        this.reshuffleTime = env.config.turnTimeoutMillis;
        this.warningTime = env.config.turnTimeoutWarningMillis;
        this.timerMode = reshuffleTime == 0;
        this.displayTimer = reshuffleTime >= 0;
        this.tableSize = env.config.tableSize;

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        for (Player player : players) {
            Thread playerThread = new Thread(player, "Player " + player.id);
            playerThread.start();
        }
        Collections.shuffle(deck);
        while (!shouldFinish()) {
            acquireSemaphore();
            placeCardsOnTable();
            if(env.config.hints){
                table.hints();
            }
            table.semaphore.release();
            startTime = System.currentTimeMillis();
            timerLoop();
            updateTimerDisplay(false);
            //removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!shouldFinish() && (currentTime < reshuffleTime || reshuffleTime <= 0)) {
            sleepUntilWokenOrTimeout();
            acquireSemaphore();
            while(!toCheck.isEmpty()){
                int id = toCheck.poll();
                checkSet(id);
            }
            while((!displayTimer || timerMode) && !table.checkForSets() && !shouldFinish()){
                removeAllCardsFromTable();
                placeCardsOnTable();
            }
            table.semaphore.release();
            updateTimerDisplay(false);
        }
        updateTimerDisplay(true);
        if (!shouldFinish()) {
            acquireSemaphore();
            for (Player player : players) {
                player.sleepUntilWoken();
            }
            do{
                removeAllTokensFromTable();
                removeAllCardsFromTable();
                placeCardsOnTable();
            } while((!displayTimer || timerMode) && !table.checkForSets());
            for (Player player : players) {
                player.wake();
            }
            table.semaphore.release();
        }
        else{
            announceWinners();
        }
    }
    //Acquire table's semaphore permit
    private void acquireSemaphore(){
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    boolean checkSet(int player){
        //Acquire tables semaphre permit
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Get the player's set from the table
        List<Integer> set = table.getPlayerTokens(player);

        //Turn set to Array for checking
        int[] cards = new int[env.config.featureSize];
        int p = 0;
        for (Integer i : set) {
            cards[p] = table.getSlotToCard()[i];
            p++;
        }

        //If set is valid add a point to the player and remove the cards
        if(env.util.testSet(cards)){
            toRemove.addAll(set);
            table.removePlayerTokens(player);
            removeCardsFromTable();
            placeCardsOnTable();
            players[player].wake();
            players[player].point();
            if(env.config.hints){
                table.hints();
            }
            table.semaphore.release();
            return true;
       } else { //if the set is not valid penalize the player
            //table.removePlayerTokens(player);
            players[player].wake();
            players[player].penalty();
            table.semaphore.release();
            return false;
        }
    }
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        acquireSemaphore();
        for (int i = players.length - 1; i >= 0; i--) {
            //players[i].wake();
            players[i].terminate();
        }
        table.semaphore.release();
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    boolean shouldFinish() {
        return terminate || (env.util.findSets(this.getDeck(), 1).size() == 0 && !table.checkForSets());

    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        while(!toRemove.isEmpty()){
            int slot = toRemove.poll();
            for (Player player : players) {
                player.removeFromToPlace(slot);
                if(table.getPlayerTokens(player.getId()).contains(slot)){
                    if(toCheck.remove(player.getId()))
                        player.wake();
                    table.removeToken(player.getId(), slot);
                }
            }
            table.removeCard(slot);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //Place cards from the deck on the table
        int slot = 0;
        ArrayList<Integer> validSlots = new ArrayList<Integer>();
        for (int i = 0; i < tableSize; i++) {
            validSlots.add(i);
        }
        int usedSlots = 0;
        while (usedSlots < tableSize && !deck.isEmpty()) {
            Collections.shuffle(validSlots);
            if(table.getCard(validSlots.get(slot)) == null){
                int card = deck.remove(0);
                table.placeCard(card, validSlots.get(slot));
                //System.out.println(deck.size() + " Cards left in deck");

            }
            validSlots.remove(slot);
            usedSlots++;
        }

        //Release table's semaphore
        table.semaphore.release();

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
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
        if(!displayTimer) return;
        if (reset) {
            currentTime = 0;
            startTime = System.currentTimeMillis();
        }
        currentTime = System.currentTimeMillis() - startTime;
        long t = reshuffleTime - currentTime;
        if(timerMode){
            env.ui.setElapsed(System.currentTimeMillis() - startTime);
        } else {
            env.ui.setCountdown(t, t <= warningTime);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        //Remove cards from table to the deck
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = 0; i < tableSize; i++) {
            if(table.getCard(i) != null){
                slots.add(i);
            }
        }
        while (table.countCards() > 0) {
            Collections.shuffle(slots);
            int slotToRemove = slots.remove(0);
            Integer c = table.getCard(slotToRemove);
            table.removeCard(slotToRemove);
            deck.add(c);
        }
        Collections.shuffle(deck);  //Shuffle the deck
    }

    /**
     * removes all tokens from the table
     */
    public void removeAllTokensFromTable() {
        table.resetAllTokens();
    }
    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int bestScore = 0;
        List<Integer> winnerList = new ArrayList<Integer>();
        for(Player p : players){
            int score = p.score();
            if(score >= bestScore){
                if(score > bestScore){
                    bestScore = p.score();
                    winnerList.clear();
                }
                winnerList.add(p.id);
            }
        }

        int[] winners = winnerList.stream().mapToInt(Integer::intValue).toArray();
        env.ui.announceWinner(winners);
        terminate();
    }

    public Player[] getPlayers(){
        return players;
    }

    public void setPlayers(Player[] players) {
        this.players = players;
    }

    public List<Integer> getDeck() {
        return deck;
    }
}
