package com.yourpackage;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Room implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(Room.class.getName());

    private final String creator;
    private final List<Player> players;

    private transient List<ObjectOutputStream> clientStreams;
    private final int maxPlayers;
    private boolean isGameStarted;
    private List<String> teamA;
    private List<String> teamB;
    private Deck deck;
    private Player master;
    private String hokmSuit;
    private int currentRound;
    private int totalRounds;
    private int currentPlayerIndex;
    private Map<Player, Card> currentTurnCards;
    private int[] teamScores;
    private int[] teamRoundWins;

    public Room(String creator, int maxPlayers, int totalRounds) {
        this.creator = creator;
        this.maxPlayers = maxPlayers;
        this.totalRounds = totalRounds;
        this.players = new ArrayList<>();
        this.clientStreams = new ArrayList<>();
        this.isGameStarted = false;
        this.teamA = new ArrayList<>();
        this.teamB = new ArrayList<>();
        this.deck = new Deck();
        this.currentRound = 0;
        this.currentPlayerIndex = 0;
        this.currentTurnCards = new HashMap<>();
        this.teamScores = new int[]{0, 0};
        this.teamRoundWins = new int[]{0, 0};
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.clientStreams = new ArrayList<>();
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getCreator() {
        return creator;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public boolean isGameStarted() {
        return isGameStarted;
    }

    public synchronized void closeRoom() {
        broadcastMessage("ROOM_CLOSED");
        closeAllConnections();
        clientStreams.clear();
        players.clear();
    }

    public List<String> getTeamA() {
        return teamA;
    }

    public List<String> getTeamB() {
        return teamB;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Player getPlayerByName(String username) {
        return players.stream().filter(p -> p.getName().equals(username)).findFirst().orElse(null);
    }

    public synchronized boolean removePlayer(String username) {
        Player player = getPlayerByName(username);
        if (player != null) {
            players.remove(player);
            clientStreams.remove(player.getOutputStream());
            notifyPlayerKicked(player);
            broadcastUserList();
            return true;
        }
        return false;
    }

    public synchronized void addPlayer(Player player) {
        if (!isFull() && players.stream().noneMatch(p -> p.getName().equals(player.getName()))) {
            players.add(player);
            clientStreams.add(player.getOutputStream());
            addPlayerToTeam(player);
            broadcastMessage(player.getName() + " has joined the room.");
            broadcastUserList();
        }
    }

    public synchronized void addClientStream(ObjectOutputStream out) {
        clientStreams.add(out);
    }

    public synchronized List<ObjectOutputStream> getClientStreams() {
        return new ArrayList<>(clientStreams);
    }

    private void addPlayerToTeam(Player player) {
        if (teamA.size() <= teamB.size()) {
            teamA.add(player.getName());
        } else {
            teamB.add(player.getName());
        }
    }

    public synchronized void startGame() {
        if (!isGameStarted && players.size() == maxPlayers) {
            this.isGameStarted = true;
            LOGGER.info("Starting game with " + maxPlayers + " players");
            deck = new Deck();
            selectMaster();
            dealInitialCards();
            notifyMasterToPickHokm();
        } else {
            broadcastMessage("START_GAME_FAILED");
        }
    }

    private void selectMaster() {
        int masterIndex = (int) (Math.random() * players.size());
        master = players.get(masterIndex);
        broadcastMessage("MASTER_SELECTED:" + master.getName());
    }

    private void notifyMasterToPickHokm() {
        sendMessageToPlayer(master, "SELECT_HOKM");
    }

    public synchronized void setHokmSuit(String hokmSuit) {
        this.hokmSuit = hokmSuit;
        broadcastMessage("HOKM_SELECTED:" + hokmSuit);
        dealRemainingCards();
        startRound();
    }

    private void dealInitialCards() {
        players.forEach(player -> {
            List<Card> initialCards = deck.drawCards(5);
            player.setHand(initialCards);
            sendCardsToPlayer(player);
        });
    }

    private void dealRemainingCards() {
        players.forEach(player -> {
            List<Card> remainingCards = deck.drawCards(13 - player.getHand().size());
            player.getHand().addAll(remainingCards);
            sendCardsToPlayer(player);
        });
    }

    private void sendCardsToPlayer(Player player) {
        sendMessageToPlayer(player, "DEAL_CARDS:" + player.getHand());
    }

    private void startRound() {
        currentPlayerIndex = players.indexOf(master);
        broadcastMessage("ROUND_START:" + currentRound);
        nextTurn();
    }

    private void nextTurn() {
        LOGGER.info("we are in next Turn");
        currentPlayerIndex %= players.size();
        Player currentPlayer = players.get(currentPlayerIndex);
        LOGGER.info(currentPlayer.getName());


        broadcastMessage("PLAYER_TURN:" + currentPlayer.getName());
    }


    public synchronized void playCard(Player player, Card card) {
        if (players.get(currentPlayerIndex).equals(player)) {
            player.getHand().remove(card);
            currentTurnCards.put(player, card);
            broadcastMessage("CARD_PLAYED:" + player.getName() + ":" + card);
            currentPlayerIndex++;
            if (currentTurnCards.size() == players.size()) {
                determineTurnWinner();
                currentTurnCards.clear();
            } else {
                nextTurn();
            }
        } else {
            sendMessageToPlayer(player, "ERROR:Not your turn");
        }
    }

    private void determineTurnWinner() {
        Player winner = null;
        Card winningCard = null;

        for (Map.Entry<Player, Card> entry : currentTurnCards.entrySet()) {
            Player player = entry.getKey();
            Card card = entry.getValue();

            if (winningCard == null || isCardHigher(card, winningCard)) {
                winningCard = card;
                winner = player;
            }
        }

        if (winner != null) {
            String winnerName = winner.getName();
            String winningTeam = teamA.contains(winnerName) ? "Team A" : "Team B";
            int winningTeamIndex = teamA.contains(winnerName) ? 0 : 1;
            teamScores[winningTeamIndex]++;

            broadcastMessage("TURN_WINNER:" + winningTeam);
            broadcastMessage("SCORE_UPDATE:Team A:" + teamScores[0] + ":Team B:" + teamScores[1]);
            broadcastMessage("ROUND_WINS_UPDATE:Team A:" + teamRoundWins[0] + ":Team B:" + teamRoundWins[1]);

            if (teamScores[winningTeamIndex] >= 7) {
                broadcastMessage("TEAM_WINS_ROUND:" + winningTeam);
                resetForNextRound(winningTeamIndex);
            } else {

                master = winner;
                currentPlayerIndex = players.indexOf(master);
                try {

                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    LOGGER.info("Thread was interrupted" + e);
                    Thread.currentThread().interrupt();
                }
                nextTurn();
            }
        }
    }

    private void resetForNextRound(int winningTeamIndex) {
        teamScores[0] = 0;
        teamScores[1] = 0;
        teamRoundWins[winningTeamIndex]++;
        broadcastMessage("ROUND_WINS_UPDATE:Team A:" + teamRoundWins[0] + ":Team B:" + teamRoundWins[1]);

        if (teamRoundWins[winningTeamIndex] >= 7) {
            broadcastMessage("TEAM_WINS_GAME:" + (winningTeamIndex == 0 ? "Team A" : "Team B"));
            endGame();
        } else {
            deck = new Deck();
            selectMaster();
            dealInitialCards();
            notifyMasterToPickHokm();
        }
    }

    private boolean isCardHigher(Card card1, Card card2) {
        if (card1.getSuit().equals(hokmSuit) && !card2.getSuit().equals(hokmSuit)) {
            return true;
        }
        else if (!card1.getSuit().equals(hokmSuit) && card2.getSuit().equals(hokmSuit)) {
            return false;
        }
        else if (!card1.getSuit().equals(card2.getSuit())) {
            return false;
        }
        return card1.getPower() > card2.getPower();
    }

    private void endGame() {
        broadcastMessage("GAME_OVER");
    }

    public synchronized void broadcastMessage(String message) {
        List<ObjectOutputStream> failedStreams = new ArrayList<>();

        for (ObjectOutputStream client : clientStreams) {
            try {
                client.writeObject(message);
                client.flush();
            } catch (IOException e) {
                failedStreams.add(client);
                LOGGER.log(Level.SEVERE, "Error broadcasting message", e);
            }
        }

        clientStreams.removeAll(failedStreams);
    }

    public synchronized void broadcastUserList() {
        broadcastMessage("USER_LIST:" + String.join(",", teamA) + ":" + String.join(",", teamB));
    }

    private void sendMessageToPlayer(Player player, String message) {
        try {
            player.getOutputStream().writeObject(message);
            player.getOutputStream().flush();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending message to player", e);
        }
    }

    private void notifyPlayerKicked(Player player) {
        sendMessageToPlayer(player, "KICKED");
        player.closeConnections();
    }

    private void closeAllConnections() {
        for (Player player : players) {
            try {
                player.getOutputStream().close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing player connection", e);
            }
        }
    }
}
