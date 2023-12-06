package sdle.client.utils;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sdle.client.utils.CRDT.MapPNCounter;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static sdle.client.utils.CRDT.toMapPNCounter;

public class Utils {

    private static final String ROUTER_ADDRESS = "tcp://127.0.0.1:";

    private static final List<Integer> ROUTER_PORTS = new ArrayList<>(List.of(6001, 6002, 6003));

    public static void clearConsole() {
        // Print multiple new lines to simulate clearing the console
        for (int i = 0; i < 25; i++) {
            System.out.println();
        }
    }

    // Get the products of a shopping list from the database
    public static void displayListProducts(String user, String listUUID) {
        // get current list_content
        String listContent = getListProducts(user, listUUID);

        // transform the list_content into a MapPNCounter object
        MapPNCounter mapPNCounter = toMapPNCounter(listContent);

        //print fancy table header
        System.out.println("-----------------------------------------------------");
        System.out.println("|                       " +  getListName(user,listUUID) + "                       |");
        System.out.println("-----------------------------------------------------");

        // display the products
        mapPNCounter.display();
    }

    public static String getListName(String user, String listUUID) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT list_name FROM shopping_lists WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    ResultSet rs = pstmt.executeQuery();

                    return rs.getString("list_name");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching Products: " + e.getMessage());
        }
        System.out.println("Error fetching Products: " + listUUID);
        return null;
    }

    public static String getListProducts(String user, String listUUID) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT list_content FROM shopping_lists WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    ResultSet rs = pstmt.executeQuery();

                    return rs.getString("list_content");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching Products: " + e.getMessage());
        }
        System.out.println("Error fetching Products: " + listUUID);
        return null;
    }

    public static boolean shoppingListExists(String user, String shoppingListName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT * FROM shopping_lists WHERE list_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, shoppingListName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next();
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking if Shopping List exists: " + e.getMessage());
        }
        return false;
    }

    public static void updateShoppingListInServer(String user, String shoppingListUUID) {
        String listContent = getListProducts(user,shoppingListUUID);

        try (ZContext context = new ZContext()) {
            int routerPort = connectRouter();

            if(routerPort == 0){
                System.out.println("\nCould not connect to router");
                return;
            }

            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(ROUTER_ADDRESS + routerPort);

            Message message = new Message();

            message.setMethod("updateList");
            message.setListUUID(shoppingListUUID);
            message.setListcontent(listContent);

            socket.send(message.toJson().getBytes(ZMQ.CHARSET));

            byte[] reply = socket.recv();
            System.out.println("Received reply from server: " + new String(reply, ZMQ.CHARSET));
        }
    }

    public static void sendShoppingListToServer(String user,String shoppingListUUID) {
        String name = getListName(user,shoppingListUUID);
        String listContent = getListProducts(user,shoppingListUUID);

        try (ZContext context = new ZContext()) {
            int routerPort = connectRouter();

            if(routerPort == 0){
                System.out.println("\nCould not connect to router");
                return;
            }

            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(ROUTER_ADDRESS + routerPort);

            Message message = new Message();

            message.setMethod("createList");
            message.setListUUID(shoppingListUUID);
            message.setListname(name);
            message.setListcontent(listContent);

            socket.send(message.toJson().getBytes(ZMQ.CHARSET));
            //System.out.println("Sent message to server: " + message.toJson());

            byte[] reply = socket.recv();
            System.out.println("Received reply from server: " + new String(reply, ZMQ.CHARSET));
        }
    }

    public static boolean productExistsInList(String user,String listUUID, String productName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        // get current list_content
        String listContent = getListProducts(user, listUUID);

        // transform the list_content into a MapPNCounter object
        MapPNCounter mapPNCounter = toMapPNCounter(listContent);

        // check if the product exists in the MapPNCounter object
        return mapPNCounter.contains(productName);
    }

    public static boolean productCounterIsZero(String user,String listUUID, String productName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        // get current list_content
        String listContent = getListProducts(user, listUUID);

        // transform the list_content into a MapPNCounter object
        MapPNCounter mapPNCounter = toMapPNCounter(listContent);

        // check if the product exists in the MapPNCounter object
        return mapPNCounter.itemValue(productName) == 0;
    }

    public static boolean addProductToList(String user, String listUUID, String productName, int quantity) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        // get current list_content
        String listContent = getListProducts(user, listUUID);
        System.out.println("listContent: " + listContent);
        // transform the list_content into a MapPNCounter object
        MapPNCounter mapPNCounter = toMapPNCounter(listContent);

        // add the product to the MapPNCounter object
        mapPNCounter.insert(productName,user,quantity);

        // transform the MapPNCounter object into a string
        String mapPNCounterString = mapPNCounter.toJson();

        // update the list_ in the database
        return updateListContent(user,listUUID, mapPNCounterString);
    }

    public static boolean updateProductInList(String user,String listUUID, String productName, int newQuantity) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        // get current list_content
        String listContent = getListProducts(user, listUUID);

        // transform the list_content into a MapPNCounter object
        MapPNCounter mapPNCounter = toMapPNCounter(listContent);

        // get current quantity of the product
        int currentQuantity = mapPNCounter.itemValue(productName);

        // get increment/decrement value
        int increment = newQuantity - currentQuantity;
        int decrement = currentQuantity - newQuantity;

        // update the product in the MapPNCounter object
        if (increment > 0) {
            mapPNCounter.increment(productName,user,increment);
        } else {
            mapPNCounter.decrement(productName,user,decrement);
        }

        // transform the MapPNCounter object into a string
        String mapPNCounterString = mapPNCounter.toJson();

        // update the list_content in the database
        return updateListContent(user,listUUID, mapPNCounterString);
    }

    public static boolean removeProductFromList(String user,String listUUID, String productName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        // get current list_content
        String listContent = getListProducts(user, listUUID);

        // transform the list_content into a MapPNCounter object
        MapPNCounter mapPNCounter = toMapPNCounter(listContent);

        // remove the product from the MapPNCounter object
        mapPNCounter.remove(productName,user);

        // transform the MapPNCounter object into a string
        String mapPNCounterString = mapPNCounter.toJson();

        // update the list_content in the database
        return updateListContent(user,listUUID, mapPNCounterString);
    }

    public static boolean updateListContent(String user,String listUUID, String listContent) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "UPDATE shopping_lists SET list_content = ? WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listContent);
                    pstmt.setString(2, listUUID);
                    int rowsAffected = pstmt.executeUpdate();

                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating list content: " + e.getMessage());
        }
        return false;
    }

    public static boolean updateListFromServer(String user, String shoppingListUUID) {
        try (ZContext context = new ZContext()) {
            int routerPort = connectRouter();

            if(routerPort == 0){
                System.out.println("\nCould not connect to router");
                return false;
            }

            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(ROUTER_ADDRESS + routerPort);

            Message message = new Message();
            message.setMethod("getList");
            message.setListUUID(shoppingListUUID);

            socket.send(message.toJson().getBytes(ZMQ.CHARSET));

            byte[] response = socket.recv();
            Message reply = Message.fromJson(new String(response, ZMQ.CHARSET));

            if(reply.getMethod()!= null && reply.getMethod().equals("error")){
                //System.out.println("\nCould not connect to server");
                return false;
            }

            // Save the list in the database
            return updateListInDatabase(user,reply.getListUUID(), reply.getListcontent());
        }
    }

    public static boolean getListFromServer(String user, String shoppingListUUID) {
        try (ZContext context = new ZContext()) {
            int routerPort = connectRouter();

            if(routerPort == 0){
                System.out.println("\nCould not connect to router");
                return false;
            }

            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(ROUTER_ADDRESS + routerPort);

            Message message = new Message();
            message.setMethod("getList");
            message.setListUUID(shoppingListUUID);

            socket.send(message.toJson().getBytes(ZMQ.CHARSET));

            byte[] response = socket.recv();
            Message reply = Message.fromJson(new String(response, ZMQ.CHARSET));

            if(reply.getMethod() !=null && reply.getMethod().equals("error")){
                System.out.println("\nCould not connect to server");
                return false;
            }

            // Save the list in the database
            return saveListInDatabase(user, reply.getListname(), reply.getListUUID(), reply.getListcontent());
        }
    }

    public static boolean saveListInDatabase(String user, String listName, String shoppingListUUID, String listContent) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "INSERT INTO shopping_lists (list_uuid, list_name, list_content) VALUES (?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, shoppingListUUID);
                    pstmt.setString(2, listName);
                    pstmt.setString(3, listContent);
                    int rowsAffected = pstmt.executeUpdate();

                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error saving Shopping List to the database: " + e.getMessage());
        }
        return false;
    }

    public static boolean updateListInDatabase(String user, String shoppingListUUID, String listContent) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "UPDATE shopping_lists SET list_content = ? WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listContent);
                    pstmt.setString(2, shoppingListUUID);
                    int rowsAffected = pstmt.executeUpdate();

                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating Shopping List in the database: " + e.getMessage());
        }
        return false;
    }

    public static int connectRouter() {
        ZMQ.Socket routerSocket = null;
        ZContext context = new ZContext();
        for (int routerPort : ROUTER_PORTS) {
            routerSocket = context.createSocket(SocketType.REQ);
            routerSocket.connect("tcp://localhost:" + routerPort);
            //System.out.println("trying to connect to port " + routerPort + "...");

            routerSocket.setReceiveTimeOut(200);

            sdle.server.utils.Message message = new sdle.server.utils.Message();
            message.setMethod("hello");

            routerSocket.send(message.toJson().getBytes(ZMQ.CHARSET));

            byte[] response = routerSocket.recv();

            if (response == null) {
                //System.out.println("No response from router on port " + routerPort);
                routerSocket.close();
                continue;
            }
            String responseMessage = new String(response, ZMQ.CHARSET);

            sdle.server.utils.Message responseMessageObject = sdle.server.utils.Message.fromJson(responseMessage);

            if (responseMessageObject.getMethod().equals("hello")) {
                //System.out.println("Connected to router on port " + routerPort);
                return routerPort;
            }
        }
        return 0;
    }
}

