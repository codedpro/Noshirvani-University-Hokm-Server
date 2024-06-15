# Hokm Game Server

This project is the server-side application for the Hokm game. The server manages game rooms, handles user connections, and facilitates real-time chat within game rooms. It ensures proper synchronization of game state across multiple clients.

## Features

- Manage creation and deletion of game rooms.
- Support for game rooms with configurable number of players (2 or 4).
- Real-time chat functionality within game rooms.
- Room creator can kick users from the room.
- Automatically closes room and notifies all users when the creator leaves.
- Prevents users from joining multiple rooms simultaneously.
- Live updates of room list to display current rooms and players.

## Prerequisites

- Java 11 or higher
- An IDE or text editor (e.g., IntelliJ IDEA, Eclipse, VS Code)

## Running the Server

1. Open your IDE and load the project.
2. Navigate to the `com.yourpackage.Main` class.
3. Run the `Main` class.

## Project Structure

- `com.yourpackage.Main`: Entry point of the server application.
- `com.yourpackage.Server`: Manages incoming connections and room management.
- `com.yourpackage.ClientHandler`: Handles communication with individual clients.
- `com.yourpackage.Room`: Represents a game room with players and chat functionality.

## Contributing

Contributions are welcome! Please submit a pull request with any improvements or bug fixes.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
