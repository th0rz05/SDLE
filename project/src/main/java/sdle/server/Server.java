package sdle.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import sdle.router.Router;
import sdle.server.utils.CRDT;
import sdle.server.utils.Message;

import static sdle.server.utils.CRDT.toMapPNCounter;

public class Server {

    private static List<Server.Pair<String, Integer>> hashRing = null;

    private static final int SERVER_BASE_PORT = 5000;

    private static final List<Integer> ROUTER_PORTS = new ArrayList<>(List.of(6001, 6002, 6003));

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
            ZMQ.Socket routerSocket = null;

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<ZMQ.Socket> future = executor.submit(() -> {
                for (int routerPort : ROUTER_PORTS) {
                    ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                    socket.connect("tcp://localhost:" + routerPort);
                    socket.setReceiveTimeOut(1000); // Timeout set to 1 second

                    // Sending a test message to check if router responds
                    Message message = new Message();
                    message.setMethod("hello");
                    socket.send(message.toJson().getBytes(ZMQ.CHARSET));


                    byte[] response = socket.recv();
                    if (response != null) {
                        // If response received, the router is responding
                        System.out.println("Connected to router on port " + routerPort);
                        return socket;
                    } else {
                        // Close the socket if no response
                        socket.close();
                    }
                }
                return null;
            });

            try {
                routerSocket = future.get(); // Wait for the future result
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Failed to connect to any router.");
            }

            executor.shutdown(); // Shutdown executor

            if (routerSocket == null) {
                System.err.println("No available routers to connect.");
                return;
            }

            ZMQ.Socket socket = context.createSocket(SocketType.REP);
            socket.bind("tcp://*:" + port);

            System.out.println("Server listening on port " + port + "...");
            System.out.println("Press Ctrl+C to exit.");

            createDatabase(id);

            // call a function when ctrl+c is pressed
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
            }));

            if (joinHashRing) {
                // Send a message to the router indicating participation in the hash ring
                Message message = new Message();
                message.setMethod("joinHashRing");
                message.setServerId(String.valueOf(id));

                routerSocket.send(message.toJson().getBytes(ZMQ.CHARSET));
            }
            else{
                Message message = new Message();
                message.setMethod("createHashRing");

                routerSocket.send(message.toJson().getBytes(ZMQ.CHARSET));

                // Wait for the response from the router
                byte[] response = routerSocket.recv();
                String responseMessage = new String(response, ZMQ.CHARSET);

                message = Message.fromJson(responseMessage);
                handleCreateHashRingMessage(message);

            }

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = socket.recv();
                String receivedMessage = new String(request, ZMQ.CHARSET);

                Message message = Message.fromJson(receivedMessage);

                switch (message.getMethod()) {
                    case "createList" -> {
                        // Call thread to handle create list message
                        new Thread(() -> handleCreateListMessage(id,message,socket)).start();
                    }
                    case "updateList" -> {
                        // Call thread to handle update list message
                        new Thread(() -> handleUpdateListMessage(id,message,socket)).start();
                    }
                    case "getList" -> {
                        // Call thread to handle get list message
                        new Thread(() -> handleGetListMessage(id, message, socket)).start();
                    }
                    case "replicateCreationList" -> {
                        // Call thread to handle replicate creation list message
                        new Thread(() -> handleReplicateCreationListMessage(id,message,socket)).start();
                    }
                    case "replicateUpdateList" -> {
                        // Call thread to handle replicate update list message
                        new Thread(() -> handleReplicateUpdateListMessage(id,message,socket)).start();
                    }
                    case "addServerToHashRing" -> {
                        // Call thread to handle add server to hash ring message
                        new Thread(() -> handleAddServerToHashRingMessage(id,message,socket)).start();
                    }
                    case "removeServerFromHashRing" -> {
                        // Call thread to handle remove server from hash ring message
                        new Thread(() -> handleRemoveServerFromHashRingMessage(id,message,socket)).start();
                    }
                    case "getKeys" ->{
                        // Call thread to handle get keys message
                        new Thread(() -> handleGetKeysMessage(id,message,socket)).start();
                    }
                    case "deleteKeys" -> {
                        // Call thread to handle delete keys message
                        new Thread(() -> handleDeleteKeysMessage(id,socket)).start();
                    }
                    case "replicateKeys" -> {
                        // Call thread to handle replicate keys message
                        new Thread(() -> handleReplicateKeysMessage(id,socket)).start();
                    }
                    default -> {
                        System.out.println("Invalid message type.");
                        String response = "Received message of type: ";
                        socket.send(response.getBytes(ZMQ.CHARSET));
                    }
                }
            }
        }
    }

    private static void updateListToReplicationNodes(int id,String listUUID, String listContent) {
        String nodes = getNodesForReplication(getResponsibleServer(listUUID),2);

        System.out.println("Nodes for replication: " + nodes);

        Message message = new Message();
        message.setMethod("replicateUpdateList");
        message.setListUUID(listUUID);
        message.setListcontent(listContent);

        String[] nodesArray = nodes.split(";");

        for (String node : nodesArray) {

            int port = Integer.parseInt(node.substring(1,2)) + SERVER_BASE_PORT;

            System.out.println("Port: " + port);

            String virtualNode = node.substring(3);

            if(port == 5000 + id) {
                System.out.println("Same server, storing in database...");
                replicateUpdateList(id, virtualNode, listUUID, listContent);
                continue;
            }

            message.setVirtualnode(virtualNode);

            try (ZContext context = new ZContext()) {
                ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                socket.connect("tcp://localhost:" + port);
                System.out.println("connected to port " + port + "...");
                socket.send(message.toJson().getBytes(ZMQ.CHARSET));
                System.out.println("Sent request to server: " + message.toJson());
                byte[] response = socket.recv();
                String responseMessage = new String(response, ZMQ.CHARSET);
                System.out.println("Received response from server: " + responseMessage);
            }
        }

    }

    private static void sendListToReplicationNodes(int id,String listUUID, String listName, String listContent) {
        String nodes = getNodesForReplication(getResponsibleServer(listUUID),2);

        System.out.println("Nodes for replication: " + nodes);

        Message message = new Message();
        message.setMethod("replicateCreationList");
        message.setListUUID(listUUID);
        message.setListname(listName);
        message.setListcontent(listContent);

        String[] nodesArray = nodes.split(";");

        int replicationLevel = 1;

        for (String node : nodesArray) {

            int port = Integer.parseInt(node.substring(1,2)) + SERVER_BASE_PORT;

            System.out.println("Port: " + port);

            String virtualNode = node.substring(3);

            if(port == 5000 + id) {
                System.out.println("Same server, storing in database...");
                replicateCreationList(id, virtualNode, listUUID, listName, listContent,
                        String.valueOf(replicationLevel));
                continue;
            }

            message.setVirtualnode(virtualNode);
            message.setReplicationLevel(String.valueOf(replicationLevel));

            try (ZContext context = new ZContext()) {
                ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                socket.connect("tcp://localhost:" + port);
                System.out.println("connected to port " + port + "...");
                socket.send(message.toJson().getBytes(ZMQ.CHARSET));
                System.out.println("Sent request to server: " + message.toJson());
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

    public static String getPreviousNode(String node) {
        // find node in hash ring
        int index = 0;
        for (Pair<String, Integer> pair : hashRing) {
            if (pair.left().equals(node)) {
                break;
            }
            index++;
        }

        // get the previous node in the hash ring
        int size = hashRing.size();
        return hashRing.get((index + size - 1) % size).left();
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
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "virtualnode_id TEXT,"
                        + "list_uuid TEXT,"
                        + "list_name TEXT,"
                        + "list_content TEXT,"
                        + "replicated INTEGER DEFAULT 0,"
                        + "to_delete INTEGER DEFAULT 0"
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

    private static void handleCreateListMessage(int id,Message message, ZMQ.Socket socket) {
        //create list in database
        createList(id, message.getVirtualnode(), message.getListUUID(), message.getListname(),message.getListcontent());

        //send response to client
        String response = "Created list in server " + id;

        socket.send(response.getBytes(ZMQ.CHARSET));

        sendListToReplicationNodes(id,message.getListUUID(), message.getListname(), message.getListcontent());
    }

    private static void createList(int id, String virtualNode, String listUUID, String listName,String listContent) {
        System.out.println("Creating list...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "INSERT INTO shopping_lists (virtualnode_id, list_uuid, list_name, list_content) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, virtualNode);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, listName);
                    pstmt.setString(4, listContent);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error creating list: " + e.getMessage());
        }
    }

    private static void handleUpdateListMessage(int id,Message message, ZMQ.Socket socket) {
        //update list in database
        updateList(id, message.getVirtualnode(), message.getListUUID(), message.getListcontent());

        //send response to client
        String response = "Updated list in server " + id;

        socket.send(response.getBytes(ZMQ.CHARSET));

       updateListToReplicationNodes(id,message.getListUUID(), message.getListcontent());
    }

    private static void updateList(int id,String virtualNode, String listUUID, String listContent) {
        System.out.println("Updating list...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        String listContentDatabase = getListContent(id,virtualNode,listUUID);

        //transform listCOntent into map pncounter
        CRDT.MapPNCounter mapPNCounter = toMapPNCounter(listContentDatabase);

        //transform listContent into map pncounter
        CRDT.MapPNCounter mapPNCounter2 = toMapPNCounter(listContent);

        //merge both maps
        mapPNCounter.merge(mapPNCounter2);

        //transform map into string
        String listContentMerged = mapPNCounter.toJson();

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "UPDATE shopping_lists SET list_content = ? WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listContentMerged);
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

    private static void handleGetListMessage(int id,Message message, ZMQ.Socket socket) {
        //get list from database
        Message response = getList(id,message.getVirtualnode(), message.getListUUID());

        //send response to client
        socket.send(response.toJson().getBytes(ZMQ.CHARSET));
    }

    private static Message getList(int id,String virtualNode, String listUUID) {
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

        Message message = new Message();
        message.setListUUID(listUUID);
        message.setListname(listName);
        message.setListcontent(listContent);

        return message;
    }

    private static void handleReplicateCreationListMessage(int id,Message message,ZMQ.Socket socket) {

        replicateCreationList(id, message.getVirtualnode(), message.getListUUID(), message.getListname(),
                message.getListcontent(),message.getReplicationLevel());

        //send response to client
        String response = "Replicated creation of list in server " + id;

        socket.send(response.getBytes(ZMQ.CHARSET));
    }

    private static void replicateCreationList(int id, String virtualNode, String listUUID, String listName,
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
    }

    private static void handleReplicateUpdateListMessage(int id,Message message,ZMQ.Socket socket) {

        replicateUpdateList(id, message.getVirtualnode(), message.getListUUID(), message.getListcontent());

        //send response to client
        String response = "Replicated update of list in server " + id;

        socket.send(response.getBytes(ZMQ.CHARSET));
    }

    private static void replicateUpdateList(int id, String virtualNode, String listUUID, String listContent) {
        System.out.println("Replicating update of list...");

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
    }

    private static void handleCreateHashRingMessage(Message message) {
        createHashRing(message.getHashRing());
    }

    private static void createHashRing(String hashRingString) {

        hashRing = getHashRing(hashRingString.split(":"));

        System.out.println("Hash ring created.");

    }

    private static void handleAddServerToHashRingMessage(int id,Message message,ZMQ.Socket socket) {
        addServerToHashRing(id,message.getHashRing(),message.getServerId(),
                message.getNrVirtualNodes());

        //send response to client
        String response = "Added server to hash ring in server " + id;

        socket.send(response.getBytes(ZMQ.CHARSET));
    }

    private static void addServerToHashRing(int id,String hashRingString,String newServerId,
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

                    Message message = new Message();
                    message.setMethod("getKeys");
                    message.setVirtualnode(String.valueOf(virtualNode));
                    message.setReplicationLevel("0");
                    socket.send(message.toJson().getBytes(ZMQ.CHARSET));
                    System.out.println("Sent request to server: " + message.toJson());

                    byte[] response = socket.recv();
                    String responseMessage = new String(response, ZMQ.CHARSET);
                    System.out.println("Received response from server: " + responseMessage);

                    if (responseMessage.isEmpty()) {
                        continue;
                    }

                    String[] keys = responseMessage.split("/");
                    for (String key : keys) {
                        String[] keyParts = key.split(";");
                        String listUUID = keyParts[0];
                        String listName = keyParts[1];
                        String listContent = keyParts[2];
                        if(getResponsibleServer(listUUID).equals(node)){
                            System.out.println("Storing list in database...");
                            createList(id, String.valueOf(virtualNode), listUUID, listName, listContent);
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
                String keys = getKeys(id,String.valueOf(i),"0");
                String [] keysArray = keys.split("/");
                for (String key : keysArray) {
                    String[] keyParts = key.split(";");
                    String listUUID = keyParts[0];
                    if(!getResponsibleServer(listUUID).equals(node)){
                        markListToDelete(id,String.valueOf(i),listUUID);
                    }
                }
            }
        }

        // for every server mark to delete the replication level 1 and level 2

        // go through each virtual node
        for (int i = 1; i <= virtualNodes; i++) {
            String node = "S" + id + "V" + i; // "S0V0"
            System.out.println("Node: " + node);
            // mark to delete the replication level 1 and level 2
            String keys = getKeys(id, String.valueOf(i), "1");
            String[] keysArray = keys.split("/");
            for (String key : keysArray) {
                String[] keyParts = key.split(";");
                String listUUID = keyParts[0];
                markListToDelete(id, String.valueOf(i), listUUID);
            }
            keys = getKeys(id, String.valueOf(i), "2");
            keysArray = keys.split("/");
            for (String key : keysArray) {
                String[] keyParts = key.split(";");
                String listUUID = keyParts[0];
                markListToDelete(id, String.valueOf(i), listUUID);
            }
        }

        System.out.println("Hash ring updated.");
    }

    private static void handleRemoveServerFromHashRingMessage(int id,Message message,ZMQ.Socket socket) {
        removeServerFromHashRing(id,message.getHashRing(),message.getServerId(),
                message.getNrVirtualNodes());

        //send response to client
        String response = "Removed server from hash ring in server " + id;

        socket.send(response.getBytes(ZMQ.CHARSET));
    }

    public static void removeServerFromHashRing(int id,String hashRingString,String serverId,
                                                String virtualNodesPerServer) {
        System.out.println("Updating hash ring...");

        int virtualNodes = Integer.parseInt(virtualNodesPerServer);

        for (int i = 1; i <= virtualNodes; i++) {
            String node = "S" + id + "V" + i; // "S0V0"
            System.out.println("Node: " + node);
            // see if node is after the server to remove
            String previousNode = getPreviousNode(node);
            System.out.println("Previous node: " + previousNode);
            if(previousNode.substring(1,2).equals(serverId)){
                // get the keys from replication level 1 and set them to replication level 0
                String keys = getKeys(id,String.valueOf(i),"1");
                String [] keysArray = keys.split("/");
                for (String key : keysArray) {
                    String[] keyParts = key.split(";");
                    String listUUID = keyParts[0];
                    updateReplicationLevel(id,String.valueOf(i),listUUID,"0");
                }
            }
        }

        // for every server mark to delete the replication level 1 and level 2

        // go through each virtual node
        for (int i = 1; i <= virtualNodes; i++) {
            String node = "S" + id + "V" + i; // "S0V0"
            System.out.println("Node: " + node);
            // mark to delete the replication level 1 and level 2
            String keys = getKeys(id, String.valueOf(i), "1");
            String[] keysArray = keys.split("/");
            for (String key : keysArray) {
                String[] keyParts = key.split(";");
                String listUUID = keyParts[0];
                markListToDelete(id, String.valueOf(i), listUUID);
            }
            keys = getKeys(id, String.valueOf(i), "2");
            keysArray = keys.split("/");
            for (String key : keysArray) {
                String[] keyParts = key.split(";");
                String listUUID = keyParts[0];
                markListToDelete(id, String.valueOf(i), listUUID);
            }
        }

        hashRing = getHashRing(hashRingString.split(":"));

        System.out.println("Hash ring updated.");

    }

    public static void updateReplicationLevel(int id,String virtualNode, String listUUID, String replicationLevel) {
        System.out.println("Updating replication level...");

        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "UPDATE shopping_lists SET replicated = ? WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, replicationLevel);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, virtualNode);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating replication level: " + e.getMessage());
        }
    }

    private static void handleGetKeysMessage(int id,Message message,ZMQ.Socket socket) {
        String keys = getKeys(id,message.getVirtualnode(),message.getReplicationLevel());
        socket.send(keys.getBytes(ZMQ.CHARSET));
    }

    private static String getKeys(int id,String virtualNode,String replicationLevel) {
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

    private static String getAllKeys(int id,String replicationLevel) {
        System.out.println("Getting all keys...");

        // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        StringBuilder keys = new StringBuilder();

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "SELECT list_uuid, list_name, list_content FROM shopping_lists WHERE replicated = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, replicationLevel);
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

    private static void handleDeleteKeysMessage(int id,ZMQ.Socket socket) {
        deleteKeys(id);
        String response = "Keys deleted in server " + id;
        socket.send(response.getBytes(ZMQ.CHARSET));
    }

    private static void deleteKeys(int id) {
        System.out.println("Deleting keys...");

        // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "DELETE FROM shopping_lists WHERE to_delete = 1";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error deleting keys: " + e.getMessage());
        }
    }

    private static void handleReplicateKeysMessage(int id,ZMQ.Socket socket) {
        replicateKeys(id);
        String response = "Keys replicated in server " + id;
        socket.send(response.getBytes(ZMQ.CHARSET));
    }

    private static void replicateKeys(int id) {
        System.out.println("Replicating keys...");

        String keys = getAllKeys(id,"0");

        if (keys.isEmpty()) {
            return;
        }

        String[] keysArray = keys.split("/");

        for (String key : keysArray) {
            String[] keyParts = key.split(";");
            String listUUID = keyParts[0];
            String listName = keyParts[1];
            String listContent = keyParts[2];
            sendListToReplicationNodes(id, listUUID, listName, listContent);
        }
    }

    private static String getListContent(int id,String virtualNode, String listUUID) {
        System.out.println("Getting list content...");

        // get the list name and products and send it to the client
        String url = "jdbc:sqlite:database/server/server_" + id + ".db";
        String listContent = null;

        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String sql = "SELECT list_content FROM shopping_lists WHERE list_uuid = ? AND virtualnode_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.setString(2, virtualNode);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        listContent = rs.getString("list_content");
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting list content: " + e.getMessage());
        }

        return listContent;
    }

    record Pair<L, R>(L left, R right) {
    }
}

