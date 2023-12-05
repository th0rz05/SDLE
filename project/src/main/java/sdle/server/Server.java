package sdle.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import sdle.router.Router;

public class Server {

    private static List<Server.Pair<String, Integer>> hashRing = null;

    private static final int SERVER_BASE_PORT = 5000;

    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("Usage: java -jar build/libs/server.jar <id> [joinHashRing]");
            return;
        }

        int id, port;
        boolean joinHashRing = false;
        try {
            id = Integer.parseInt(args[0]);
            if (args.length > 1 && args[1].equals("joinHashRing")) {
                joinHashRing = true;
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid id number");
            return;
        }

        port = 5000 + id;

        try (ZContext context = new ZContext()) {

            ZMQ.Socket routerSocket = context.createSocket(SocketType.REQ);
            routerSocket.connect("tcp://localhost:6000"); // Connect to the router

            if (joinHashRing) {
                // Send a message to the router indicating participation in the hash ring
                String message = "JoinHashRing;" + id;
                routerSocket.send(message.getBytes(ZMQ.CHARSET));
            }

            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:" + port);


            System.out.println("Server listening on port " + port + "...");

            // Create the database
            createDatabase(id);

            // call a function when ctrl+c is pressed
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                // Send a message to the router indicating leaving the hash ring
                // String message = "LeaveHashRing;" + id;
                // routerSocket.send(message.getBytes(ZMQ.CHARSET));
            }));

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = socket.recv();
                String[] messageParts = new String(request, ZMQ.CHARSET).split(";");

                String messageType = messageParts[1];

                switch (messageType) {
                    case "createList" -> {
                        // Process create list message
                        handleCreateListMessage(id, messageParts[0], messageParts[2], messageParts[3]);
                        sendListToReplicationNodes(id,messageParts[2], messageParts[3],"[]");
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "updateList" -> {
                        // Process update list message
                        handleUpdateListMessage(id,messageParts[0], messageParts[2], messageParts[3]);
                        updateListToReplicationNodes(id,messageParts[2], messageParts[3]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "getList" -> {
                        // Process get list message
                        String list = handleGetListMessage(id,messageParts[0],messageParts[2]);
                        socket.send(list.getBytes(ZMQ.CHARSET));
                    }
                    case "replicateCreationList" -> {
                        // Process replicate list message
                        handleReplicateCreationListMessage(id, messageParts[0], messageParts[2], messageParts[3],
                                messageParts[4],messageParts[5]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "replicateUpdateList" -> {
                        // Process replicate list message
                        handleReplicateUpdateListMessage(id, messageParts[0], messageParts[2], messageParts[3]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "createHashRing" -> {
                        // Process create hash ring message
                        handleCreateHashRingMessage(messageParts[2]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "addServerToHashRing" -> {
                        // Process add hash ring message
                        handleAddServerToHashRingMessage(id,messageParts[2],messageParts[3],messageParts[4]);
                        String response = "Received message of type: " + messageType;
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                    case "getKeys" ->{
                        // Process get keys message
                        String keys = handleGetKeysMessage(id,messageParts[0],messageParts[2]);
                        socket.send(keys.getBytes(ZMQ.CHARSET));
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

    private static void updateListToReplicationNodes(int id,String listUUID, String listContent) {
        String nodes = getNodesForReplication(getResponsibleServer(listUUID),2);

        System.out.println("Nodes for replication: " + nodes);

        String request = "replicateUpdateList;" + listUUID + ";" + listContent;

        String[] nodesArray = nodes.split(";");

        for (String node : nodesArray) {
            int port = Integer.parseInt(node.substring(1,2)) + SERVER_BASE_PORT;

            System.out.println("Port: " + port);

            String virtualNode = node.substring(3);

            if(port == 5000 + id) {
                System.out.println("Same server, updating in database...");
                handleReplicateUpdateListMessage(id, virtualNode, listUUID, listContent);
                continue;
            }

            String modifiedRequest = virtualNode + ";" + request;

            try (ZContext context = new ZContext()) {
                ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                socket.connect("tcp://localhost:" + port);
                System.out.println("connected to port " + port + "...");
                socket.send(modifiedRequest);
                System.out.println("Sent request to server: " + modifiedRequest);
                byte[] response = socket.recv();
                String responseMessage = new String(response, ZMQ.CHARSET);
                System.out.println("Received response from server: " + responseMessage);
            }
        }

    }

    private static void sendListToReplicationNodes(int id,String listUUID, String listName, String listContent) {
        String nodes = getNodesForReplication(getResponsibleServer(listUUID),2);

        System.out.println("Nodes for replication: " + nodes);

        String request = "replicateCreationList;" + listUUID + ";" + listName + ";" + listContent;

        String[] nodesArray = nodes.split(";");

        int replicationLevel = 1;

        for (String node : nodesArray) {

            int port = Integer.parseInt(node.substring(1,2)) + SERVER_BASE_PORT;

            System.out.println("Port: " + port);

            String virtualNode = node.substring(3);

            if(port == 5000 + id) {
                System.out.println("Same server, storing in database...");
                handleReplicateCreationListMessage(id, virtualNode, listUUID, listName, listContent,
                        String.valueOf(replicationLevel));
                continue;
            }

            String modifiedRequest = virtualNode + ";" + request + ";" + replicationLevel;

            try (ZContext context = new ZContext()) {
                ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                socket.connect("tcp://localhost:" + port);
                System.out.println("connected to port " + port + "...");
                socket.send(modifiedRequest);
                System.out.println("Sent request to server: " + modifiedRequest);
                byte[] response = socket.recv();
                String responseMessage = new String(response, ZMQ.CHARSET);
                System.out.println("Received response from server: " + responseMessage);
            }

            replicationLevel++;
        }

    }

    private static int getSHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return byteArrayToInt(hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static int byteArrayToInt(byte[] bytes) {
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    public static String getResponsibleServer(String listUUID) {
        int hash = getSHA256Hash(listUUID) % 1000; // Modulo 100
        for (Pair<String, Integer> pair : hashRing) {
            if (hash <= pair.right()) {
                return pair.left();
            }
        }
        return hashRing.get(0).left();
    }

    public static String getNextNode(String node) {
        // find node in hash ring
        int index = 0;
        for (Pair<String, Integer> pair : hashRing) {
            if (pair.left().equals(node)) {
                break;
            }
            index++;
        }

        // get the next node in the hash ring
        return hashRing.get((index + 1) % hashRing.size()).left();
    }

    public static String getNodesForReplication(String node, int numberOfNodes) {
        // find node in hash ring
        int index = 0;
        for (Pair<String, Integer> pair : hashRing) {
            if (pair.left().equals(node)) {
                break;
            }
            index++;
        }

        // get the next numberOfNodes nodes in the hash ring
        StringBuilder nodes = new StringBuilder();
        for (int i = 1; i <= numberOfNodes; i++) {
            nodes.append(hashRing.get((index + i) % hashRing.size()).left()).append(";");
        }
        return nodes.toString();

    }

    private static List<Pair<String, Integer>> getHashRing(String[] hashRingParts) {
        List<Pair<String, Integer>> hashRing = new ArrayList<>();
        for (String hashRingPart : hashRingParts) {
            String[] pair = hashRingPart.split(",");
            hashRing.add(new Pair<>(pair[0], Integer.parseInt(pair[1])));
        }
        return hashRing;
    }

    private static void createDatabase(int id) {
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "CREATE TABLE IF NOT EXISTS shopping_lists ("
                        + "virtualnode_id TEXT,"
                        + "list_uuid TEXT,"
                        + "list_name TEXT,"
                        + "list_content TEXT,"
                        + "replicated INTEGER DEFAULT 0,"
                        + "to_delete INTEGER DEFAULT 0,"
                        + "PRIMARY KEY (virtualnode_id, list_uuid)"
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

    //mark list as to delete
    private static void markListToDelete(int id,String virtualNode, String listUUID) {
        System.out.println("Marking list to delete...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "UPDATE shopping_lists SET to_delete = 1 WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.setString(2, virtualNode);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error marking list to delete: " + e.getMessage());
        }
    }

    private static void handleCreateListMessage(int id, String virtualNode, String listUUID, String listName) {
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

    private static void handleUpdateListMessage(int id,String virtualNode, String listUUID, String listContent) {
        System.out.println("Updating list...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "UPDATE shopping_lists SET list_content = ? WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listContent);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, virtualNode);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating list: " + e.getMessage());
        }

        // print the message
        System.out.println("Received message: " + String.join(";", listUUID, listContent));

    }

    private static String handleGetListMessage(int id,String virtualNode, String listUUID) {
        System.out.println("Getting list...");

        // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";
        String listContent = null;
        String listName = null;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "SELECT list_name, list_content FROM shopping_lists WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.setString(2, virtualNode);
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

        return String.join(";", listUUID, listName, listContent);
    }

    private static void handleReplicateCreationListMessage(int id, String virtualNode, String listUUID, String listName,
                                                           String listContent, String replicationLevel) {
        System.out.println("Replicating creation of list...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "INSERT INTO shopping_lists (virtualnode_id, list_uuid, list_name, list_content, replicated)" +
                        " VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, virtualNode);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, listName);
                    pstmt.setString(4, listContent);
                    pstmt.setInt(5, Integer.parseInt(replicationLevel));
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error replicating list: " + e.getMessage());
        }

        // print the message
        System.out.println("Received message: " + String.join(";", listUUID, listName, listContent));

    }

    private static void handleReplicateUpdateListMessage(int id, String virtualNode, String listUUID, String listContent) {
        System.out.println("Replicating update of list...");

        //store in database with virtualnode_id set to null and list_content set to an empty JSON object
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "UPDATE shopping_lists SET list_content = ? WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listContent);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, virtualNode);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error replicating list: " + e.getMessage());
        }

        // print the message
        System.out.println("Received message: " + String.join(";", listUUID, listContent));

    }

    private static void handleCreateHashRingMessage(String hashRingString) {
        System.out.println("Creating hash ring...");

        hashRing = getHashRing(hashRingString.split(":"));

    }

    private static void handleAddServerToHashRingMessage(int id,String hashRingString,String newServerId,
                                                         String virtualNodesPerServer) {
        System.out.println("Updating hash ring...");

        hashRing = getHashRing(hashRingString.split(":"));

        int virtualNodes = Integer.parseInt(virtualNodesPerServer);

        //check if new server
        if(id == Integer.parseInt(newServerId)){
            // go through each virtual node and ask the next node for the list of lists
            for (int i = 1; i <= virtualNodes; i++) {
                String node = "S" + id + "V" + i; // "S0V0"
                System.out.println("Node: " + node);
                String nextNode = getNextNode(node);
                System.out.println("Next node: " + nextNode);
                // connect to the next node
                int port = Integer.parseInt(nextNode.substring(1,2)) + SERVER_BASE_PORT;
                int virtualNode = Integer.parseInt(nextNode.substring(3));
                try (ZContext context = new ZContext()) {
                    ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                    socket.connect("tcp://localhost:" + port);
                    System.out.println("connected to port " + port + "...");
                    String request = virtualNode + ";getKeys;0"; // "0" is the replication level
                    socket.send(request.getBytes(ZMQ.CHARSET));
                    System.out.println("Sent request to server: " + request);
                    byte[] response = socket.recv();
                    String responseMessage = new String(response, ZMQ.CHARSET);
                    System.out.println("Received response from server: " + responseMessage);
                    String[] keys = responseMessage.split("/");
                    for (String key : keys) {
                        String[] keyParts = key.split(";");
                        String listUUID = keyParts[0];
                        String listName = keyParts[1];
                        String listContent = keyParts[2];
                        if(getResponsibleServer(listUUID).equals(node)){
                            System.out.println("Storing list in database...");
                            handleCreateListMessage(id, String.valueOf(virtualNode), listUUID, listName);
                            handleUpdateListMessage(id, String.valueOf(virtualNode), listUUID, listContent);
                        }
                    }
                }

            }
        }
        else{
            // go through each virtual node
            for (int i = 1; i <= virtualNodes; i++) {
                String node = "S" + id + "V" + i; // "S0V0"
                System.out.println("Node: " + node);
                // see if level 0 keys are still responsible for this node
                String keys = handleGetKeysMessage(id,String.valueOf(i),"0");
                String [] keysArray = keys.split("/");
                for (String key : keysArray) {
                    String[] keyParts = key.split(";");
                    String listUUID = keyParts[0];
                    if(!getResponsibleServer(listUUID).equals(node)){
                        // mark list to delete from database
                        markListToDelete(id,String.valueOf(i),listUUID);
                    }
                }
            }
        }

        System.out.println("Hash ring updated.");
    }

    private static String handleGetKeysMessage(int id,String virtualNode,String replicationLevel) {
        System.out.println("Getting keys...");

        // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        StringBuilder keys = new StringBuilder();

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "SELECT list_uuid, list_name, list_content FROM shopping_lists WHERE virtualnode_id = ? " +
                        "AND replicated = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, virtualNode);
                    pstmt.setString(2, replicationLevel);
                    ResultSet rs = pstmt.executeQuery();
                    while (rs.next()) {
                        keys.append(rs.getString("list_uuid")).append(";").append(rs.getString
                                ("list_name")).append(";").append(rs.getString("list_content")).append("/");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting keys: " + e.getMessage());
        }

        return keys.toString();
    }

    record Pair<L, R>(L left, R right) {
    }
}

