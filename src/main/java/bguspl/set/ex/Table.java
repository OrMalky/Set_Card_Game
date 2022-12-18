package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    private List<List<Integer>> tokens;

    private List<Integer> usedSlots;

    public Semaphore semaphore;


    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.semaphore = new Semaphore(1, true);
        this.usedSlots = new ArrayList<Integer>();
        this.tokens = new ArrayList<List<Integer>>();
        for(int i = 0; i < env.config.players; i++){
            tokens.add(new ArrayList<Integer>());
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });

        semaphore.release();

    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }
    /**
     * Find the cards currently on the table.
     *
     * @return - the places of cards on the table.
     */
    public List<Integer> getUsedSlots(){
        return usedSlots;
    }
    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        usedSlots.add(slot);
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        for (List<Integer> t : tokens) {
            if(t.contains(slot)){
                t.remove(Integer.valueOf(slot));
            }
        }
        int c = slotToCard[slot];
        slotToCard[slot] = null;
        cardToSlot[c] = null;
        usedSlots.remove(Integer.valueOf(slot));
        env.ui.removeTokens(slot);
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * @return
     */
    public boolean placeToken(int player, int slot) {
        if(tokens.get(player).contains(slot)){
            removeToken(player, slot);
            System.out.println(player + " removed token at " + slot);
        } else {
            tokens.get(player).add(slot);
            env.ui.placeToken(player, slot);
            System.out.println(player + " placed token at " + slot);
        }
        return tokens.get(player).size() == 3;
    }

    /**
     * Returns the given player placed tokens.
     *
     * @param player - the player the tokens belong to.
     *
     * @return - a list of integers represnting the slots on the table the player has tokens on.
     */
    public List<Integer> getPlayerTokens(int player){
        return tokens.get(player);
    }


    /**
     * Getter for the card in a slot.
     * @param slot the place I want to check
     * @return the card in the slot as the integer represent it
     */
    public Integer getCard(int slot) {
        return slotToCard[slot];
    }

    /**
     * removes all the tokens from the table
     */

    public void resetAllTokens() {
        for(List<Integer> t : tokens){
            t.clear();
        }
        env.ui.removeTokens();
    }

    /**
     * Removes all player tokens from a grid slot.
     * @param player - the player the token belongs to.
     */
    public void removePlayerTokens(int player){
        for (Integer token : tokens.get(player)) {
            env.ui.removeToken(player, token);
        }
        tokens.get(player).clear();
    }
    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        int p = tokens.get(player).indexOf(slot);
        if(p > -1){
            tokens.get(player).remove(p);
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
    }

    /**
     * Checks if there are any valid sets on the table.
     *
     * @return - true iff there is at least one valid set on the table.
     */
    public boolean checkForSets(){
        //Generate a list of cards on table
        List<Integer> cards = new ArrayList<Integer>();
        for (Integer i : slotToCard) {
            cards.add(i);
        }

        //checks if there is at least one set on the table
        return env.util.findSets(cards, 1).size() > 0;
    }

    public void setUsedSlots(ArrayList<Integer> usedSlots) {
        this.usedSlots = usedSlots;
    }
}
