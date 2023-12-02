package sdle.server;

import java.sql.*;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

public class Main {
    public static void main(String[] args) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:5555");

            // Announce that the server is running
            System.out.println("Server is running on port 5555\n\n");

            // Create the database
            createDatabase();

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = socket.recv();
                String[] messageParts = new String(request, ZMQ.CHARSET).split(";");

                String messageType = messageParts[0];

                if (messageType.equals("createList")) {
                    // Process create list message
                    handleCreateListMessage(messageParts[1], messageParts[2]);
                    String response = "Received message of type: " + messageType;
                    socket.send(response.getBytes(ZMQ.CHARSET));
                } else if (messageType.equals("login")) {
                    // Process login message
                    handleLoginMessage(messageParts);
                    String response = "Received message of type: " + messageType;
                    socket.send(response.getBytes(ZMQ.CHARSET));
                } else if(messageType.equals("updateList")) {
                    // Process update list message
                    handleUpdateListMessage(messageParts[1], messageParts[2]);
                    String response = "Received message of type: " + messageType;
                    socket.send(response.getBytes(ZMQ.CHARSET));
                } else if(messageType.equals("getList")){
                    // Process get list message
                    String list = handleGetListMessage(messageParts[1]);
                    socket.send(list.getBytes(ZMQ.CHARSET));
                } else {
                    System.out.println("Invalid message type.");
                    String response = "Received message of type: " + messageType;
                    socket.send(response.getBytes(ZMQ.CHARSET));
                }
            }
        }
    }

    private static void createDatabase() {
        String url = "jdbc:sqlite:database/server/server.db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "CREATE TABLE IF NOT EXISTS shopping_lists ("
                        + "virtualnode_id TEXT,"
                        + "list_uuid TEXT PRIMARY KEY,"
                        + "list_name TEXT,"
                        + "list_content TEXT"
                        + ")";

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    System.out.println("Table created successfully.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error creating database: " + e.getMessage());
        }
    }

    private static void handleCreateListMessage(String listUUID, String listName) {
        System.out.println("Creating list...");

        //store in database with virtualnode_id set to null and list_content set to an empty JSON object
        String url = "jdbc:sqlite:database/server/server.db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "INSERT INTO shopping_lists (virtualnode_id, list_uuid, list_name, list_content) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, null);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, listName);
                    pstmt.setString(4, "[]");
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error creating list: " + e.getMessage());
        }

        // print the message
        System.out.println("Received message: " + String.join(";", listUUID, listName));

    }

    private static void handleUpdateListMessage(String listUUID, String listContent) {
        System.out.println("Updating list...");

        //store in database with virtualnode_id set to null and list_content set to an empty JSON object
        String url = "jdbc:sqlite:database/server/server.db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "UPDATE shopping_lists SET list_content = ? WHERE list_uuid = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listContent);
                    pstmt.setString(2, listUUID);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating list: " + e.getMessage());
        }

        // print the message
        System.out.println("Received message: " + String.join(";", listUUID, listContent));

    }

    private static String handleGetListMessage(String listUUID) {
        System.out.println("Getting list...");

       // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server.db";
        String listContent = null;
        String listName = null;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "SELECT list_name, list_content FROM shopping_lists WHERE list_uuid = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        listName = rs.getString("list_name");
                        listContent = rs.getString("list_content");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting list: " + e.getMessage());
        }

        return String.join(";",listUUID,listName, listContent);
    }

    private static void handleLoginMessage(String[] messageParts) {
        System.out.println("Logging in...");
        // print the message
        System.out.println("Received message: " + String.join(";", messageParts));
    }
}

