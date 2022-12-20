package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Thread.sleep;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private final Dealer dealer;

    private Queue<Integer> toPlace;

    private long sleepEnd;
    private boolean wait = false;

    private long aiDelay = 0;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        toPlace = new ConcurrentLinkedQueue<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if(wait || sleepEnd > System.currentTimeMillis()) {
                if(!wait)
                    env.ui.setFreeze(id, sleepEnd - System.currentTimeMillis());
                else
                    env.ui.setFreeze(id, 0);
                sleep();
            } else {
                placeTokens();
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if(wait || sleepEnd > System.currentTimeMillis()) {
                    sleep();
                } else {
                    generateSmartKeyPresses();
                    try {
                        if(!wait)
                            Thread.sleep(aiDelay);
                    } catch (InterruptedException ignored) {}
                }
            }
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    private void generateSmartKeyPresses(){
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<Integer> presses = new ArrayList<Integer>();
        List<Integer> currentTokens = new ArrayList<Integer>(table.getPlayerTokens(id));

        //If there are 3 tokens on the table - remove them (must have been a missed set)
        //Otherwise, get a random set and place its tokens
        if(currentTokens.size() == 3){
            presses = currentTokens;
        } else{
            presses = table.hintsAI();
        }

        //Call keypresses
        for (Integer p : presses){
            if(p !=null){
                System.out.println("AI " + id + " pressing " + p);
                keyPressed(p);
            }
        }
        
        table.semaphore.release();
    }

    
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        if(!human) {
            if(aiThread.getState() == Thread.State.TIMED_WAITING)
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}
            aiThread.interrupt();
        }
        playerThread.interrupt();
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!human && Thread.currentThread() != aiThread) return; // ignore keyboard input when it's a computer player
        if (sleepEnd > System.currentTimeMillis() || wait) return; // if the player is frozen, do nothing
        if (table.getPlayerTokens(id).size() < env.config.featureSize) {
            if (toPlace.size() < env.config.featureSize) {
                toPlace.add(slot);
            }
        }
        else {
                if (table.getPlayerTokens(id).contains(slot)) {
                    toPlace.add(slot);
                }
            }
        }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        sleep(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        sleep(env.config.penaltyFreezeMillis);
    }

    /**
     * send the player to sleep for a given amount of time
     * @param millies
     */
    public void sleep(long millies){
        sleepEnd = System.currentTimeMillis() + millies;
    }

    /**
     * Send the player to sleep forever, or till someone wakes him up
     */
    public void sleepUntilWoken(){
        wait = true;
        sleep(10);
    }

    /**
     * Wake the player up
     */
    public void wake(){
        wait = false;
    }

    /**
     * Call the Sleep method from the Thread class and sets that up in the UI
     */
    void sleep(){
        try {
            Thread.sleep(10);
            if(!wait){
                if(System.currentTimeMillis() >= sleepEnd){
                    env.ui.setFreeze(id, 0);
                }
            } else {
                sleep(10);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * remove the top card from the deck and place it on the table
     * @param slot - the slot the card in
     */
    public void removeFromToPlace(int slot){
        toPlace.remove(slot);
    }

    /**
     * Place the tokens on the table
     */

    void placeTokens(){
        //Acquire the table's semaphore permit to play to table
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {

            e.printStackTrace();
        }

        //Place tokens on table
        boolean isSet = false;
        while(!toPlace.isEmpty()){
            int slot = toPlace.poll();
            if(table.getCard(slot) != null)
                isSet = table.placeToken(id, slot);
        }

        //Release table's semaphore
        table.semaphore.release();

        //If 3 tokens placed check for valid set and remove tokens
        if(isSet){
            synchronized(dealer){
                dealer.toCheck.add(id);
                this.sleepUntilWoken();
            }
        }
    }

    /**
     * @return - the player's score.
     */
    public int score() {
        return score;
    }

    public int getId() {
        return id;
    }

    public Queue<Integer> getToPlace(){
        return toPlace;
    }

    public void setToPlace(Queue<Integer> toPlace){
        this.toPlace = toPlace;
    }

    public boolean isWait(){
        return this.wait;
    }
}
