package com.yourpackage;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Room implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String creator;
    private final List<String> players;
    private final transient List<ObjectOutputStream> clientStreams;
    private final int maxPlayers;
    private boolean isGameStarted;

    private Team teamA;
    private Team teamB;

    public Room(String creator, int maxPlayers) {
        this.creator = creator;
        this.maxPlayers = maxPlayers;
        this.players = new ArrayList<>();
        this.clientStreams = new ArrayList<>();
        this.isGameStarted = false;

        this.teamA = new Team("Team A");
        this.teamB = new Team("Team B");

        if (creator != null) {
            this.players.add(creator);
            this.teamA.addPlayer(creator);
        }
    }

    public boolean isGameStarted() {
        return isGameStarted;
    }

    public synchronized void startGame() {
        if (!isGameStarted) {
            this.isGameStarted = true;
            broadcastMessage("START_GAME");
        }
    }

    public String getCreator() {
        return creator;
    }

    public List<String> getPlayers() {
        return new ArrayList<>(players);
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public synchronized void addPlayer(String player, ObjectOutputStream out) {
        if (!players.contains(player) && players.size() < maxPlayers) {
            players.add(player);
            clientStreams.add(out);
            if (teamA.getPlayers().size() <= teamB.getPlayers().size()) {
                teamA.addPlayer(player);
            } else {
                teamB.addPlayer(player);
            }
            broadcastMessage(player + " has joined the room.");
            broadcastUserList();
        }
    }

    public synchronized void addClientStream(ObjectOutputStream out) {
        if (!clientStreams.contains(out)) {
            clientStreams.add(out);
        }
    }

    public synchronized void removePlayer(String player) {
        int index = players.indexOf(player);
        if (index != -1) {
            players.remove(player);
            if (teamA.getPlayers().contains(player)) {
                teamA.removePlayer(player);
            } else if (teamB.getPlayers().contains(player)) {
                teamB.removePlayer(player);
            }
            if (index < clientStreams.size()) {
                ObjectOutputStream out = clientStreams.remove(index);
                try {
                    out.writeObject("KICKED");
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    System.out.println("IOException in removePlayer");
                }
            }
            broadcastMessage(player + " has left the room.");
            broadcastUserList();
        }
    }

    public synchronized void broadcastMessage(String message) {
        System.out.println("Broadcasting message: " + message);
        List<ObjectOutputStream> closedStreams = new ArrayList<>();
        for (ObjectOutputStream out : clientStreams) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.out.println("IOException caught: " + e);
                closedStreams.add(out);
            }
        }
        clientStreams.removeAll(closedStreams);
    }

    public synchronized void broadcastUserList() {
        List<ObjectOutputStream> closedStreams = new ArrayList<>();
        for (ObjectOutputStream out : clientStreams) {
            try {
                out.writeObject("USER_LIST:" + String.join(",", teamA.getPlayers()) + ":" + String.join(",", teamB.getPlayers()));
                out.flush();
            } catch (IOException e) {
                System.out.println("IOException caught: " + e);
                closedStreams.add(out);
            }
        }
        clientStreams.removeAll(closedStreams);
    }

    public synchronized void closeRoom() {
        for (ObjectOutputStream out : clientStreams) {
            try {
                out.writeObject("ROOM_CLOSED");
                out.flush();
                out.close();
            } catch (IOException e) {
                System.out.println("IOException caught: " + e);
            }
        }
        clientStreams.clear();
        players.clear();
    }

    public List<ObjectOutputStream> getClientStreams() {
        return new ArrayList<>(clientStreams);
    }

    public List<String> getTeamA() {
        return teamA.getPlayers();
    }

    public List<String> getTeamB() {
        return teamB.getPlayers();
    }
}
