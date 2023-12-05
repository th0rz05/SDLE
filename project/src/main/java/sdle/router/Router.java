package sdle.router;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Router {
    private static final int ROUTER_PORT = 6000;
    private static final int SERVER_BASE_PORT = 5000;

    private final List<Pair<String, Integer>> hashRing;

    private final List<Integer> serverIds = new ArrayList<>();

    private final int numberOfServers;

    private final int virtualNodesPerServer;

    public Router(int numberOfServers, int virtualNodesPerServer) {
        this.numberOfServers = numberOfServers;
        this.virtualNodesPerServer = virtualNodesPerServer;
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
            routerSocket.bind("tcp://*:" + ROUTER_PORT);

            System.out.println("Router is running on port " + ROUTER_PORT);

            ZMQ.Socket serverSocket = context.createSocket(SocketType.REQ);

            while (!Thread.currentThread().isInterrupted()) {
                byte[] request = routerSocket.recv();
                String receivedMessage = new String(request, ZMQ.CHARSET);
                System.out.println("Received message from client: " + receivedMessage);

                String[] messageParts = receivedMessage.split(";");

                if(messageParts[0].equals("JoinHashRing")){
                    addServerToHashRing(messageParts[1]);
                    sendHashRingToServers("addServerToHashRing",Integer.parseInt(messageParts[1]),
                            virtualNodesPerServer);
                    continue;
                }

                // Split the received message into parts
                String listUUID = messageParts[1];

                String responsibleServer = getResponsibleServer(listUUID);

                System.out.println("Responsible server: " + responsibleServer);

                String virtualNode = responsibleServer.substring(3);

                String modifiedMessage = virtualNode + ";" + receivedMessage;

                System.out.println("Sending message to server: " + modifiedMessage);

                int serverPort = Integer.parseInt(responsibleServer.substring(1,2)) + SERVER_BASE_PORT;

                serverSocket.connect("tcp://localhost:" + serverPort);

                serverSocket.send(modifiedMessage.getBytes(ZMQ.CHARSET));

                byte[] response = serverSocket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                routerSocket.send(responseMessage.getBytes(ZMQ.CHARSET));

                serverSocket.disconnect("tcp://localhost:" + serverPort);
            }
        }
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

                String message = "null;" + method + ";" + getHashRingAsString();

                if(method.equals("addServerToHashRing")){
                    message+= ";" + newServerId + ";" + numberOfVirtualNodes;
                }

                System.out.println("Sending message to server: " + message);

                socket.send(message.getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);

            }

            if(method.equals("createHashRing")){
                return;
            }

            //for every server send deleteKeys message
            for (Integer serverId : serverIds) {
                int serverPort = serverId + SERVER_BASE_PORT;
                socket.connect("tcp://localhost:" + serverPort);

                String message = "null;deleteKeys";

                System.out.println("Sending message to server: " + message);

                socket.send(message.getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);

            }

            //send replicateKeys message
            for (Integer serverId : serverIds) {
                int serverPort = serverId + SERVER_BASE_PORT;
                socket.connect("tcp://localhost:" + serverPort);

                String message = "null;replicateKeys";

                System.out.println("Sending message to server: " + message);

                socket.send(message.getBytes(ZMQ.CHARSET));

                byte[] response = socket.recv();

                String responseMessage = new String(response, ZMQ.CHARSET);

                System.out.println("Received response from server: " + responseMessage);

                socket.disconnect("tcp://localhost:" + serverPort);

            }

            System.out.println("Hash ring sent to all servers");
        }
    }


    public static void main(String[] args) {
        int numberOfServers = 4; // Change this to the desired number of servers
        int virtualNodesPerServer = 3; // Change this to the desired number of virtual nodes per server

        Router router = new Router(numberOfServers, virtualNodesPerServer);
        router.sendHashRingToServers("createHashRing",0,0);
        router.startRouter();
    }

    record Pair<L, R>(L left, R right) {
    }
}
