package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

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

    /**
     * A reference to the dealer.
     */
    private final Dealer dealer;

    /**
     * A queue of slots to place tokens to.
     */
    private Queue<Integer> toPlace;

    /**
     * Thread management
     */
    private long sleepEnd;              //Time where the Thread should wake up [Used for timed sleep].
    private boolean wait = false;       //Wheter the Thread sleep should be timed or until woken.

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
        toPlace = new ArrayBlockingQueue<Integer>(table.SET_SIZE);
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
            //If Timed/Untimed sleeping
            if(wait || sleepEnd > System.currentTimeMillis()) {
                //Update UI accordingly
                if(!wait)
                    env.ui.setFreeze(id, sleepEnd - System.currentTimeMillis() + dealer.UI_TIME_OFFSET);
                else
                    env.ui.setFreeze(id, 0);
                sleep();    //Send the Thread to sleep
            } else {
                placeTokens();
            }
        }
        //System.out.println("Player " + id + " terminated");
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
                
                //Generate Smart/Random key presses based on settings in config (print hints)
                } else {
                    if(env.config.hints){
                        generateSmartKeyPresses();
                    } else {
                        generateRandomKeyPress();
                    }

                    //Sleep for a Tick for game fluidity and visuals
                    try {
                        if(!wait)
                            Thread.sleep(dealer.TIME_TICK);
                    } catch (InterruptedException ignored) {}
                }
            }
            //System.out.println("AI " + id + " terminated");
            env.logger.info("Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Generates key presses based on the hints from the table.
     */
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
        if(currentTokens.size() == table.SET_SIZE){
            presses = currentTokens;
        } else{
            presses = table.hintsAI();
        }

        //Call keypresses
        for (Integer p : presses){
            //System.out.println("AI " + id + " pressing " + p);
            if(p != null)
                keyPressed(p);
        }
        
        table.semaphore.release();
    }

    /**
     * Generates random key presses.
     */
    private void generateRandomKeyPress(){
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        List<Integer> options = new ArrayList<Integer>(table.getUsedSlots());
        Collections.shuffle(options);
        //System.out.println("AI " + id + " pressing " + options.get(0));
        keyPressed(options.get(0));
        
        table.semaphore.release();
    }

    
    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        try {
            playerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!human && Thread.currentThread() != aiThread) return; // ignore keyboard input when it's a computer player
        if (sleepEnd > System.currentTimeMillis() || wait) return; // if the player is frozen, do nothing

        //Add token to place queue if the player doesn't have more than the valid set size, or if it's a remove
        if (table.getPlayerTokens(id).size() < table.SET_SIZE || table.getPlayerTokens(id).contains(slot)) {
            if (toPlace.size() < table.SET_SIZE) {
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
        //int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        sleep(env.config.pointFreezeMillis);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        sleep(env.config.penaltyFreezeMillis);
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
    }

    /**
     * send the player to sleep for a given amount of time
     * @param millies
     */
    public void sleep(long millies){
        sleepEnd = System.currentTimeMillis() + millies;
    }

    /**
     * Send the player to sleep forever, or untill someone wakes him up
     */
    public void sleepUntilWoken(){
        wait = true;
        sleep(dealer.TIME_TICK);
    }

    /**
     * Wake the player up
     */
    public void wake(){
        wait = false;
    }

    /**
     * Call the Sleep method from the Thread class according to the current settings. Reset the UI if sleep is over.
     */
    void sleep(){
        try {
            Thread.sleep(dealer.TIME_TICK);
            if(!wait){
                if(System.currentTimeMillis() >= sleepEnd){
                    env.ui.setFreeze(id, 0);
                }
            } else {
                sleep(dealer.TIME_TICK);
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

        //Place all tokens from the queue on the table
        boolean isSet = false;
        while(!toPlace.isEmpty()){
            int slot = toPlace.poll();
            if(table.getCard(slot) != null)
                isSet = table.placeToken(id, slot);
        }

        //Release table's semaphore
        table.semaphore.release();

        //If enough tokens placed add set to dealer's check queue and go to sleep
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
