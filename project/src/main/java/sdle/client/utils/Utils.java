package sdle.client.utils;

import com.google.gson.Gson;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sdle.client.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final Gson gson = new Gson();

    private static final String SERVER_ADDRESS = "tcp://127.0.0.1:5555";

    public static void clearConsole() {
        // Print multiple new lines to simulate clearing the console
        for (int i = 0; i < 25; i++) {
            System.out.println();
        }
    }

    // Get the products of a shopping list from the database
    public static void displayListProducts(String user, String listUUID) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT product_name, quantity FROM list_products WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    ResultSet rs = pstmt.executeQuery();

                    System.out.println("------ Products ------");
                    while (rs.next()) {
                        String productName = rs.getString("product_name");
                        int productQuantity = rs.getInt("quantity");
                        System.out.println("Name: " + productName + " | Quantity: " + productQuantity);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching Products: " + e.getMessage());
        }
    }

    public static String getListProducts(String user, String listUUID) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        List<Product> products = new ArrayList<Product>();

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT product_name, quantity FROM list_products WHERE list_uuid = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    ResultSet rs = pstmt.executeQuery();

                    System.out.println("------ Products ------");
                    while (rs.next()) {
                        String productName = rs.getString("product_name");
                        int productQuantity = rs.getInt("quantity");
                        Product product = new Product(productName, productQuantity);
                        products.add(product);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching Products: " + e.getMessage());
        }

        return gson.toJson(products);
    }

    public static void updateShoppingListInServer(String user, String shoppingListUUID) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(SERVER_ADDRESS);

            String listProducts = getListProducts(user, shoppingListUUID);

            // Construct the message with message type and content
            String message = "updateList;" + shoppingListUUID + ";" + listProducts;
            socket.send(message.getBytes(ZMQ.CHARSET));

            // Receive acknowledgment from the server (optional)
            byte[] reply = socket.recv();
            System.out.println("Received reply from server: " + new String(reply, ZMQ.CHARSET));
        }
    }

    public static void sendShoppingListToServer(String shoppingListUUID, String shoppingListName) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(SERVER_ADDRESS);

            // Construct the message with message type and content
            String message = "createList;" + shoppingListUUID + ";" + shoppingListName;
            socket.send(message.getBytes(ZMQ.CHARSET));

            // Receive acknowledgment from the server (optional)
            byte[] reply = socket.recv();
            System.out.println("Received reply from server: " + new String(reply, ZMQ.CHARSET));
        }
    }

    public static boolean getListFromServer(String user, String shoppingListUUID) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(SERVER_ADDRESS);

            // Construct the message with message type and content
            String message = "getList;" + shoppingListUUID;
            socket.send(message.getBytes(ZMQ.CHARSET));

            // Receive acknowledgment from the server (optional)
            byte[] reply = socket.recv();
            System.out.println("Received reply from server: " + new String(reply, ZMQ.CHARSET));
            return true;
        }
    }
}

