package bguspl.set.ex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;

import bguspl.set.Env;

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
     * True iff game should be terminated due to an external event.
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

    private long aiDelay = 286;

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
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        toPlace = new ConcurrentLinkedQueue<Integer>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            if(wait || sleepEnd > System.currentTimeMillis()) {
                if(!wait)
                    env.ui.setFreeze(id, sleepEnd - System.currentTimeMillis());
                sleep();
            } else {
                placeTokens();
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                generateKeyPresses();
                try {
                    if(!wait)
                        Thread.sleep(aiDelay);
                } catch (InterruptedException e) {}
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    //Generate random key presses (for AI)
    private void generateKeyPresses(){
            try {
                table.semaphore.acquire();
            } catch (InterruptedException e) {}
            
            List<Integer> options = table.getUsedSlots();
            if(options.size() > 0){
                System.out.println("db");
                Random random = new Random();
                int choice = options.get(random.nextInt(options.size()));
                keyPressed(choice);
            }
            table.semaphore.release();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        if(!human) {
            try {
                aiThread.sleep(100);
            } catch (InterruptedException e) {
            }
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
        if(!human && Thread.currentThread() != aiThread) return; // ignore keybpard input when its a computer player
        if(sleepEnd > System.currentTimeMillis() || wait) return; // if the player is frozen, do nothing
        if(toPlace.size() < 3)
            toPlace.add(slot);
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

    public void sleep(long millies){
        sleepEnd = System.currentTimeMillis() + millies;
    }

    public void sleepUntilWoken(){
        wait = true;
        sleep(10);
    }

    public void wake(){
        wait = false;
    }

    private void sleep(){
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

    //this should be synced to table - i think fair semaphore is a good solution
    private void placeTokens(){
        //Acquire the table's semaphore permit to play to table
        try {
            table.semaphore.acquire();
        } catch (InterruptedException e) {

            e.printStackTrace();
        }

        //Place tokens on table
        boolean isSet = false;
        while(!toPlace.isEmpty() && table.getPlayerTokens(id).size() < 3){
            int slot = toPlace.poll();
            if(table.getCard(slot) != null)
                isSet = table.placeToken(id, slot);
        }
        
        //Release table's semaphore
        table.semaphore.release();

        //If 3 tokens placed check for valid set and remove tokens
        if(isSet){
            dealer.checkSet(id);
        }
    }

    public int getScore() {
        return score;
    }

    public boolean isSleep() {
        return wait;
    }

    public boolean isHuman() {
        return this.human;
    }
}
