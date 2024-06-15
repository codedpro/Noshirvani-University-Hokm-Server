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
            LOGGER.info("Initializing streams");
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            String request;
            while ((request = (String) in.readObject()) != null) {
                LOGGER.info("Received request: " + request);
                handleRequest(request);
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Error in client handler", e);
        } finally {
            closeResources();
        }
    }

    private void handleRequest(String request) throws IOException {
        if (request.startsWith("CREATE_ROOM")) {
            String[] parts = request.split(":");
            String creator = parts[1];
            int maxPlayers = Integer.parseInt(parts[2]);
            Room room = server.createRoom(creator, maxPlayers);
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
        } else if (request.equals("GET_ROOMS")) {
            LOGGER.info("Sending room list to client");
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
            LOGGER.info("Streams and socket closed");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error closing resources", e);
        }
    }
}
