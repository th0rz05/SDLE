package sdle.router;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sdle.router.utils.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public class Router {
    private static final int ROUTER_BASE_PORT = 6000;
    private static final int SERVER_BASE_PORT = 5000;

    private final List<Pair<String, Integer>> hashRing;

    private final List<Integer> serverIds = new ArrayList<>();

    private final int numberOfServers;

    private final int virtualNodesPerServer;

    private int id;

    public Router(int id, int numberOfServers, int virtualNodesPerServer) {
        this.numberOfServers = numberOfServers;
        this.virtualNodesPerServer = virtualNodesPerServer;
        this.id = id;
        hashRing = createHashRing(numberOfServers, virtualNodesPerServer);
    }

    private List<Pair<String, Integer>> createHashRing(int numberOfServers, int virtualNodesPerServer) {
        List<Pair<String, Integer>> ring = new ArrayList<>();
        for (int i = 1; i <= numberOfServers; i++) {
            //add id to list
            serverIds.add(i);
            for (int j = 1; j <= virtualNodesPerServer; j++) {
                String serverNode = "S" + i + "V" + j;
                int hash = getSHA256Hash(serverNode) % 1000; // Modulo 100
                ring.add(new Pair<>(serverNode, hash));
            }
        }
        ring.sort(Comparator.comparingInt(Pair::right));

        return ring;
    }

    private int getSHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return byteArrayToInt(hash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int byteArrayToInt(byte[] bytes) {
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    public String getResponsibleServer(String listUUID) {
        int hash = getSHA256Hash(listUUID) % 1000; // Modulo 100
        for (Pair<String, Integer> pair : hashRing) {
            if (hash <= pair.right()) {
                return pair.left();
            }
        }
        return hashRing.get(0).left();
    }

    public void startRouter() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket routerSocket = context.createSocket(SocketType.REP);
            int ROUTER_PORT = ROUTER_BASE_PORT + id;
            routerSocket.bind("tcp://*:" + ROUTER_PORT);

            System.out.println("Router is running on port " + ROUTER_PORT);

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = routerSocket.recv();
                String receivedMessage = new String(request, ZMQ.CHARSET);
                System.out.println("Received message from client: " + receivedMessage);

                Message message = Message.fromJson(receivedMessage);

                switch (message.getMethod()) {
                    case "createHashRing" -> {
                        // ask a thread to send the hash ring to the server
                        new Thread(() -> sendHashRingToServer(routerSocket)).start();
                    }
                    case "joinHashRing" -> {
                        // ask a thread to add the server to the hash ring
                        new Thread(() -> handleJoinHashRing(message)).start();
                    }
                    case "leaveHashRing" -> {
                        // ask a thread to remove the server from the hash ring
                        new Thread(() -> handleLeaveHashRing(message)).start();
                    }
                    case "hello" -> {
                        // ask a thread to send a hello message to the server
                        new Thread(() -> handleHello(routerSocket)).start();
                    }
                    case "getList" -> {
                        // ask a thread to reroute the message
                        new Thread(() -> handleGetList(message,routerSocket)).start();
                    }
                    default -> {
                        //ask a thread to reroute the message
                        new Thread(() -> rerouteMessage(message,routerSocket)).start();
                    }
                }
            }

        }
    }

    private void handleHello(ZMQ.Socket routerSocket) {
        Message responseMessage = new Message();
        responseMessage.setMethod("hello");

        routerSocket.send(responseMessage.toJson().getBytes(ZMQ.CHARSET));
        System.out.println("Sent hello message to server");
    }

    public void handleGetList(Message message, ZMQ.Socket routerSocket) {
        String responsibleServer = getResponsibleServer(message.getListUUID());
        System.out.println("Responsible server: " + responsibleServer);

        String nextNode = getNextNode(responsibleServer);
        String nextNextNode = getNextNode(nextNode);

        // Create list of responsible servers
        List<String> responsibleServers = new ArrayList<>();
        responsibleServers.add(responsibleServer);
        responsibleServers.add(nextNode);
        responsibleServers.add(nextNextNode);

        // Loop through servers
        for (String server : responsibleServers) {
            String virtualNode = server.substring(3);
            int serverPort = Integer.parseInt(server.substring(1, 2)) + SERVER_BASE_PORT;

            message.setVirtualnode(virtualNode);

            //create a socket to send the message to the server
            try (ZContext context = new ZContext()) {
                ZMQ.Socket socket = context.createSocket(SocketType.REQ);
                socket.connect("tcp://localhost:" + serverPort);

                socket.setReceiveTimeOut(2000);

                String messageToSend = message.toJson();

                System.out.println("Sending message to server: " + messageToSend);

                socket.send(messageToSend.getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                if (response == null) {
                    System.out.println("Server " + server + " did not respond");
                    socket.close();
                    continue;
                }

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);

                routerSocket.send(responseMessage.getBytes(ZMQ.CHARSET));

                return;
            }
        }

        // If no server responded, send an error message
        Message responseMessage = new Message();
        responseMessage.setMethod("error");

        routerSocket.send(responseMessage.toJson().getBytes(ZMQ.CHARSET));

        System.out.println("No server responded");
    }

    private void rerouteMessage(Message message, ZMQ.Socket routerSocket) {
        String responsibleServer = getResponsibleServer(message.getListUUID());
        System.out.println("Responsible server: " + responsibleServer);

        String virtualNode = responsibleServer.substring(3);
        message.setVirtualnode(virtualNode);

        int serverPort = Integer.parseInt(responsibleServer.substring(1, 2)) + SERVER_BASE_PORT;

        //create a socket to send the message to the server
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect("tcp://localhost:" + serverPort);

            String messageToSend = message.toJson();

            System.out.println("Sending message to server: " + messageToSend);

            socket.send(messageToSend.getBytes(ZMQ.CHARSET));

            byte[] response = socket.recv();

            String responseMessage = new String(response, ZMQ.CHARSET);

            System.out.println("Received response from server: " + responseMessage);

            socket.disconnect("tcp://localhost:" + serverPort);

            routerSocket.send(responseMessage.getBytes(ZMQ.CHARSET));
        }
    }


    private void handleJoinHashRing(Message message) {
        //sleep for 1 second
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        addServerToHashRing(message.getServerId());
        sendHashRingToServers("addServerToHashRing",Integer.parseInt(message.getServerId()),
                virtualNodesPerServer);
    }

    private void handleLeaveHashRing(Message message) {
        removeServerFromHashRing(message.getServerId());
        sendHashRingToServers("removeServerFromHashRing",Integer.parseInt(message.getServerId()),
                virtualNodesPerServer);
    }

    private void sendHashRingToServer(ZMQ.Socket routerSocket) {
        Message responseMessage = new Message();
        responseMessage.setMethod("createHashRing");
        responseMessage.setHashRing(getHashRingAsString());
        System.out.println("Sending message to client: " + responseMessage.toJson());

        routerSocket.send(responseMessage.toJson().getBytes(ZMQ.CHARSET));
    }

    public void addServerToHashRing(String serverId){
        int serverIdInt = Integer.parseInt(serverId);
        serverIds.add(serverIdInt);
        for (int i = 1; i <= 3; i++) {
            String serverNode = "S" + serverIdInt + "V" + i;
            int hash = getSHA256Hash(serverNode) % 1000; // Modulo 100
            hashRing.add(new Pair<>(serverNode, hash));
        }
        hashRing.sort(Comparator.comparingInt(Pair::right));
    }

    public void removeServerFromHashRing(String serverId){
        Integer serverIdInt = Integer.parseInt(serverId);
        //remove serverId from serverIds
        serverIds.remove(serverIdInt);
        for (int i = 1; i <= 3; i++) {
            String serverNode = "S" + serverIdInt + "V" + i;
            // find the pair with the serverNode
            Pair<String, Integer> pairToRemove = null;
            for (Pair<String, Integer> pair : hashRing) {
                if (pair.left().equals(serverNode)) {
                    pairToRemove = pair;
                    break;
                }
            }
            hashRing.remove(pairToRemove);
        }
        hashRing.sort(Comparator.comparingInt(Pair::right));
    }

    public String getHashRingAsString() {
        StringBuilder sb = new StringBuilder();
        for (Pair<String, Integer> pair : hashRing) {
            sb.append(pair.left()).append(",").append(pair.right()).append(":");
        }
        return sb.toString();
    }

    public void sendHashRingToServers(String method,int newServerId,int numberOfVirtualNodes) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);

            for (Integer serverId : serverIds) {
                int serverPort = serverId + SERVER_BASE_PORT;
                socket.connect("tcp://localhost:" + serverPort);

                Message message = new Message();
                message.setMethod(method);
                message.setHashRing(getHashRingAsString());
                message.setServerId(String.valueOf(newServerId));
                message.setNrVirtualNodes(String.valueOf(numberOfVirtualNodes));

                System.out.println("Sending message to server: " + message.toJson());

                socket.send(message.toJson().getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);
            }

            //for every server send deleteKeys message
            for (Integer serverId : serverIds) {
                int serverPort = serverId + SERVER_BASE_PORT;
                socket.connect("tcp://localhost:" + serverPort);

                Message message = new Message();
                message.setMethod("deleteKeys");

                System.out.println("Sending message to server: " + message);

                socket.send(message.toJson().getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);

            }

            //send replicateKeys message
            for (Integer serverId : serverIds) {
                int serverPort = serverId + SERVER_BASE_PORT;
                socket.connect("tcp://localhost:" + serverPort);

                Message message = new Message();
                message.setMethod("replicateKeys");

                System.out.println("Sending message to server: " + message);

                socket.send(message.toJson().getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);

            }
            System.out.println("Hash ring sent to all servers");
        }
    }

    public String getNextNode(String node) {
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

    public String getPreviousNode(String node) {
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


    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("Usage: java -jar build/libs/router.jar <id>");
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid id number");
            return;
        }

        int numberOfServers = 4; // Change this to the desired number of servers
        int virtualNodesPerServer = 3; // Change this to the desired number of virtual nodes per server

        Router router = new Router(id,numberOfServers, virtualNodesPerServer);
        router.startRouter();
    }

    record Pair<L, R>(L left, R right) {
    }
}
