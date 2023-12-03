package sdle.server;

import java.sql.*;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;

public class Server {
    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: java -jar build/libs/server.jar <id>");
            return;
        }

        int id,port;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid id number");
            return;
        }
        port = 5000 + id;

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:" + port);

            System.out.println("Server listening on port " + port + "...");

            // Create the database
            createDatabase(id);

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = socket.recv();
                String[] messageParts = new String(request, ZMQ.CHARSET).split(";");

                String messageType = messageParts[1];

                switch (messageType) {
                    case "createList" -> {
                        // Process create list message
                        handleCreateListMessage(id,messageParts[0], messageParts[2], messageParts[3]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "updateList" -> {
                        // Process update list message
                        handleUpdateListMessage(id,messageParts[2], messageParts[3]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "getList" -> {
                        // Process get list message
                        String list = handleGetListMessage(id,messageParts[2]);
                        socket.send(list.getBytes(ZMQ.CHARSET));
                    }
                    default -> {
                        System.out.println("Invalid message type.");
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                }
            }
        }
    }

    private static void createDatabase(int id) {
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

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

    private static void handleCreateListMessage(int id,String virtualNode,String listUUID, String listName) {
        System.out.println("Creating list...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "INSERT INTO shopping_lists (virtualnode_id, list_uuid, list_name, list_content) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, virtualNode);
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

    private static void handleUpdateListMessage(int id,String listUUID, String listContent) {
        System.out.println("Updating list...");

        //store in database with virtualnode_id set to null and list_content set to an empty JSON object
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

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

    private static String handleGetListMessage(int id,String listUUID) {
        System.out.println("Getting list...");

       // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";
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
}

