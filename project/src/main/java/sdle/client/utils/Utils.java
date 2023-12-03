package sdle.client.utils;

import com.google.gson.Gson;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sdle.client.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Utils {

    private static final Gson gson = new Gson();

    private static final String ROUTER_ADDRESS = "tcp://127.0.0.1:6000";

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
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(ROUTER_ADDRESS);

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
            socket.connect(ROUTER_ADDRESS);

            // Construct the message with message type and content
            String message = "createList;" + shoppingListUUID + ";" + shoppingListName;
            socket.send(message.getBytes(ZMQ.CHARSET));

            // Receive acknowledgment from the server (optional)
            byte[] reply = socket.recv();
            System.out.println("Received reply from server: " + new String(reply, ZMQ.CHARSET));
        }
    }

    public static boolean productExistsInList(String user,String listUUID, String productName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT * FROM list_products WHERE list_uuid = ? AND product_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.setString(2, productName);
                    return pstmt.executeQuery().next();
                }
            }
        } catch (SQLException e) {
            System.out.println("Error checking if product exists in the shopping list: " + e.getMessage());
        }
        return false;
    }

    public static boolean addProductToList(String user, String listUUID, String productName, int quantity) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "INSERT INTO list_products (list_uuid, product_name, quantity) VALUES (?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.setString(2, productName);
                    pstmt.setInt(3, quantity);
                    pstmt.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error adding product to the shopping list: " + e.getMessage());
        }
        return false;
    }

    public static boolean updateProductInList(String user,String listUUID, String productName, int newQuantity) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "UPDATE list_products SET quantity = ? WHERE list_uuid = ? AND product_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, newQuantity);
                    pstmt.setString(2, listUUID);
                    pstmt.setString(3, productName);
                    int rowsAffected = pstmt.executeUpdate();

                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error updating product in the shopping list: " + e.getMessage());
        }
        return false;
    }

    public static boolean removeProductFromList(String user,String listUUID, String productName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "DELETE FROM list_products WHERE list_uuid = ? AND product_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listUUID);
                    pstmt.setString(2, productName);
                    int rowsAffected = pstmt.executeUpdate();

                    return rowsAffected > 0;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error removing product from the shopping list: " + e.getMessage());
        }
        return false;
    }

    public static void getListFromServer(String user, String shoppingListUUID) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(SocketType.REQ);
            socket.connect(ROUTER_ADDRESS);

            // Construct the message with message type and content
            String message = "getList;" + shoppingListUUID;
            socket.send(message.getBytes(ZMQ.CHARSET));

            // Receive acknowledgment from the server (optional)
            byte[] reply = socket.recv();
            String list = new String(reply, ZMQ.CHARSET);

            System.out.println("Received reply from server: " + list);

            //separate the list name from the list content
            String[] listParts = list.split(";");
            String listName = listParts[1];
            list = listParts[2];



            // Save the list in the database
            saveListInDatabase(user,listName,shoppingListUUID, list);

        }
    }

    private static void saveListInDatabase(String user, String listName, String shoppingListUUID, String list) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        //if list doesn't exist, create it (see if name already exists)
        if (!shoppingListExists(user, listName)) {
            try (Connection connection = DriverManager.getConnection(url)) {
                if (connection != null) {
                    String sql = "INSERT INTO shopping_lists (list_uuid, list_name) VALUES (?, ?)";

                    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                        pstmt.setString(1, shoppingListUUID);
                        pstmt.setString(2, listName);
                        pstmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                System.out.println("Error saving Shopping List to the database: " + e.getMessage());
            }
        }

        if(list.equals("[]")) {
            return;
        }

        // separate the list in { and }
        list = list.substring(1, list.length() - 1);

        // separate the products in commas where { is before the comma
        String[] products = list.split(",(?=\\{)");

        // for each product, separate the name and quantity
        for (String product : products) {

            // transform the product into a Product object
            Product productObj = gson.fromJson(product, Product.class);

            // if the product already exists in the list, update it
            if (productExistsInList(user,shoppingListUUID, productObj.getName())) {
                updateProductInList(user,shoppingListUUID, productObj.getName(), productObj.getQuantity());
            } else {
                // if the product doesn't exist in the list, add it
                addProductToList(user,shoppingListUUID, productObj.getName(), productObj.getQuantity());
            }

        }
    }
}

