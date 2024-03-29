package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
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
     * Game Settings (from config file)
     */
    private long reshuffleTime;         //Total round time, time between reshuffles
    private final long warningTime;     //Time where the timer UI changes to red
    private long startTime;             //Current round start time
    private final boolean timerMode;    //Wheter the timer UI will be in countdown mode or elapsed (timer) mode
    private final boolean displayTimer; //Wheter the timer UI should be display or not
    private final int tableSize;        //Total number of card slots on the table

    /**
     * Current turn time. Needed for timer.
     */
    private long currentTime;

    /**
     * UI time offset. For visual comfort only.
     */
    public final long UI_TIME_OFFSET = 0;
    public final long TIME_TICK = 10;

    /**
     * Game management queues
     */
    private Queue<Integer> toRemove;    //Cards waiting to be removed from the table by the dealer
    public Queue<Integer> toCheck;      //Players waiting for their sets to be checked by the dealer


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.toRemove = new ConcurrentLinkedQueue<Integer>();
        this.toCheck = new ArrayBlockingQueue<Integer>(env.config.players);
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

        //Create player threads and start them. Send them to sleep until first cards are dealt.
        for (Player player : players) {
            player.sleepUntilWoken();
            Thread playerThread = new Thread(player, "Player " + player.id);
            playerThread.start();
        }

        Collections.shuffle(deck);
        startTime = System.currentTimeMillis();

        //Main game loop
        while (!shouldFinish()) {
            acquireSemaphore();
            placeCardsOnTable();
            startTime = System.currentTimeMillis();
            if(env.config.hints){
                table.hints();
            }
            table.semaphore.release();
            for (Player player : players) {
                player.wake();
            }
            timerLoop();
            updateTimerDisplay(false);
        }
        
        //GAME END
        removeAllTokensFromTable();
        removeAllCardsFromTable();
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
            players[i].wake();
        }

        announceWinners();
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        //While game is not finished and it's not time to reshuffle yet
        while (!shouldFinish() && (currentTime < reshuffleTime || reshuffleTime <= 0)) {
            sleepUntilWokenOrTimeout();

            //Check sets in queue for checking
            acquireSemaphore();
            //System.out.println(Arrays.toString(toCheck.toArray()));
            while(!toCheck.isEmpty()){
                int id = toCheck.poll();
                if(currentTime < reshuffleTime || reshuffleTime <= 0){
                    checkSet(id);
                } else {
                    table.removePlayerTokens(id);
                    players[id].wake();
                }
                updateTimerDisplay(false);
            }

            //[Elapsed mode or No Timer mode] Makes sure there are legal sets on table after removing checked sets
            while((!displayTimer || timerMode) && !table.checkForSets() && !shouldFinish()){
                removeAllCardsFromTable();
                placeCardsOnTable();
            }
            table.semaphore.release();
            updateTimerDisplay(false);
        }

        //If game wasn't finished but reached reshuffle time
        updateTimerDisplay(true);
        if (!shouldFinish()) {
            acquireSemaphore();

            //All players go to sleep until reshuffle ends
            for (Player player : players) {
                player.sleepUntilWoken();
            }

            //Reshuffle the cards. If in Ealpsed or No Timer modes, repeat until there is a legal set on the table
            do{
                removeAllTokensFromTable();
                removeAllCardsFromTable();
                placeCardsOnTable();
            } while((!displayTimer || timerMode) && !table.checkForSets());

            //Wake all players
            for (Player player : players) {
                player.wake();
            }
            table.semaphore.release();
        }
    }


    /**
     * Acquire the table's smaphore permit
     */
    private void acquireSemaphore(){
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a given player has a valid set
     * 
     * @param player - Id of a player to check.
     * @return true iff the given player has placed tokens on a valid set.
     */
    boolean checkSet(int player){
        acquireSemaphore();
        
        //Get the player's set from the table and make sure it's the right size
        List<Integer> set = table.getPlayerTokens(player);
        if(set.size() < table.SET_SIZE) return false;
        //System.out.println(Arrays.toString(set.toArray()));

        //Turn set to Array for checking
        int[] cards = new int[env.config.featureSize];
        int p = 0;
        for (Integer i : set) {
            if(table.getSlotToCard()[i] != null){
                cards[p] = table.getSlotToCard()[i];
                p++;
            } else {
                players[player].wake();
                players[player].penalty();
                table.semaphore.release();
                return false;
            }
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

        //if the set is not valid penalize the player
        } else { 
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
        acquireSemaphore();
        for (Player player : players){
            player.sleepUntilWoken();
        }
        table.semaphore.release();
        terminate = true;
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
     * Checks if cards should be removed from the table and removes them if they do.
     */
    private void removeCardsFromTable() {
        //Go over the queue of cards to remove
        while(!toRemove.isEmpty()){
            int slot = toRemove.poll();

            //Make sure no other player is using this cards (placed tokens or going to place tokens there)
            for (Player player : players) {
                player.removeFromToPlace(slot);
                if(table.getPlayerTokens(player.getId()).contains(slot)){
                    //If we removed a token from a player waiting for a set check, wake him up (he no longer has a set)
                    if(toCheck.remove(player.getId()))
                        player.wake();
                    table.removeToken(player.getId(), slot);
                    player.wake();
                }
            }
            table.removeCard(slot);
            updateTimerDisplay(false);
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //Place cards from the deck on the table
        long t = System.currentTimeMillis();

        int slot = 0;
        ArrayList<Integer> validSlots = new ArrayList<Integer>();
        for (int i = 0; i < tableSize; i++) {
            validSlots.add(i);
        }
        int usedSlots = 0;
        Collections.shuffle(validSlots);
        while (usedSlots < tableSize && !deck.isEmpty()) {
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

        startTime += System.currentTimeMillis() - t;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(TIME_TICK);
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

        if(t + UI_TIME_OFFSET < 0)
            t = -UI_TIME_OFFSET;

        if(timerMode){
            env.ui.setElapsed(System.currentTimeMillis() - startTime);
        } else {
            env.ui.setCountdown(t + UI_TIME_OFFSET, t <= warningTime);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        //Generate full slots list [for visual effect]
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = 0; i < tableSize; i++) {
            if(table.getCard(i) != null){
                slots.add(i);
            }
        }

        //Remove cards from table to the deck
        while (table.countCards() > 0) {
            Collections.shuffle(slots); //Take from a random slot each time [for visuals only]
            int slotToRemove = slots.remove(0);
            Integer c = table.getCard(slotToRemove);
            table.removeCard(slotToRemove);
            deck.add(c);
        }

        //Shuffle the deck
        Collections.shuffle(deck);
        updateTimerDisplay(true);
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
