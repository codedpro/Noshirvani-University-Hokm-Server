package com.yourpackage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler extends Thread {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private final Socket socket;
    private final Server server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            String request;
            while ((request = (String) in.readObject()) != null) {
                handleRequest(request);
            }
        } catch (IOException | ClassNotFoundException ignored) {

        } finally {
            closeResources();
        }
    }

    private void handleRequest(String request) throws IOException {
        if (request.startsWith("CREATE_ROOM")) {
            String[] parts = request.split(":");
            String creator = parts[1];
            int maxPlayers = Integer.parseInt(parts[2]);
            int rounds = Integer.parseInt(parts[3]);
            Room room = server.createRoom(creator, maxPlayers, rounds);
            if (room != null) {
                room.addClientStream(out);
                out.writeObject("ROOM_CREATED:" + room.getCreator());
                LOGGER.info("Room created: " + room.getCreator());
            } else {
                out.writeObject("ROOM_CREATION_FAILED");
            }
            out.flush();
        } else if (request.startsWith("JOIN_ROOM")) {
            String[] parts = request.split(":");
            String roomCreator = parts[1];
            String username = parts[2];
            Room room = server.getRoomByCreator(roomCreator);
            if (room != null && !room.isFull()) {
                room.addPlayer(username, out);
                room.addClientStream(out);
                room.broadcastUserList();
            } else {
                out.writeObject("ROOM_FULL");
                out.flush();
            }
        } else if (request.startsWith("GAME_STARTED")) {
            String[] parts = request.split(":");
            String roomCreator = parts[1];
            String username = parts[2];
            Room room = server.getRoomByCreator(roomCreator);
            if (room.getCreator().equals(username) && !room.isGameStarted()) {
                room.startGame();
                LOGGER.info("Game started by " + username);
            } else {
                LOGGER.warning("Start game command issued by non-creator or game already started.");
            }
        } else if (request.equals("GET_ROOMS")) {
            List<Room> rooms = server.getRooms();
            out.writeObject(rooms);
            out.flush();
        } else if (request.startsWith("CHAT")) {
            String[] parts = request.split(":");
            String roomCreator = parts[1];
            String username = parts[2];
            String message = parts[3];
            Room room = server.getRoomByCreator(roomCreator);
            if (room != null) {
                room.broadcastMessage(username + ": " + message);
            }
        } else if (request.startsWith("KICK_USER")) {
            String[] parts = request.split(":");
            String roomCreator = parts[1];
            String userToKick = parts[2];
            Room room = server.getRoomByCreator(roomCreator);
            if (room != null) {
                room.removePlayer(userToKick);
                room.broadcastMessage(userToKick + " has been kicked from the room.");
                room.broadcastUserList();
            }
        } else if (request.startsWith("LEAVE_ROOM")) {
            String[] parts = request.split(":");
            String roomCreator = parts[1];
            String username = parts[2];
            Room room = server.getRoomByCreator(roomCreator);
            if (room != null) {
                if (roomCreator.equals(username)) {
                    room.broadcastMessage("ROOM_CLOSED");
                    room.closeRoom();
                    server.removeRoom(room);
                } else {
                    room.removePlayer(username);
                    room.broadcastMessage(username + " has left the room.");
                    room.broadcastUserList();
                }
            }
        } else if (request.startsWith("RESET_USER_STATE")) {
            String username = request.split(":")[1];
            Room room = server.getRoomByPlayer(username);
            if (room != null) {
                room.removePlayer(username);
                room.broadcastMessage(username + " has left the room.");
                room.broadcastUserList();
            }
        } else if (request.startsWith("START_GAME")) {
            String[] parts = request.split(":");
            String roomCreator = parts[1];
            String username = parts[2];
            List<String> teamA = List.of(parts[3].split(","));
            List<String> teamB = List.of(parts[4].split(","));
            startGame(roomCreator, teamA, teamB);
        } else if (request.startsWith("PLAYER_LIST")) {
            String roomCreator = request.split(":")[1];
            Room room = server.getRoomByCreator(roomCreator);
            if (room != null) {
                // Get players in Team A and Team B
                List<String> teamAPlayers = room.getTeamA();
                List<String> teamBPlayers = room.getTeamB();

                // Prepare the message to send to clients
                StringBuilder messageBuilder = new StringBuilder("PLAYER_LIST:");

                // Append Team A players
                messageBuilder.append("Team A: ");
                messageBuilder.append(String.join(",", teamAPlayers));

                // Append Team B players
                messageBuilder.append(": Team B: ");
                messageBuilder.append(String.join(",", teamBPlayers));

                String message = messageBuilder.toString();

                // Broadcast PLAYER_LIST message to all clients
                for (ObjectOutputStream client : room.getClientStreams()) {
                    try {
                        client.writeObject(message);
                        client.flush();
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "Error broadcasting PLAYER_LIST message", e);
                    }
                }

                LOGGER.info("Sent PLAYER_LIST to all clients for room: " + roomCreator);
            }


    }
    }

    private void startGame(String roomCreator, List<String> teamA, List<String> teamB) {
        Room room = server.getRoomByCreator(roomCreator);
        if (room != null) {
            for (ObjectOutputStream client : room.getClientStreams()) {
                try {
                    client.writeObject("START_GAME:" + String.join(",", teamA) + ":" + String.join(",", teamB));
                    client.flush();
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error starting game", e);
                }
            }
        }
    }

    private void closeResources() {
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error closing resources", e);
        }
    }
}
