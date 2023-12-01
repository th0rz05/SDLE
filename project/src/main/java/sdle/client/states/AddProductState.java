package sdle.client.states;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import sdle.client.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class AddProductState implements State {
    private final String listUUID;

    private final String user;
    private final Scanner scanner = new Scanner(System.in);

    public AddProductState(String user, String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {
        // Get input for product details
        System.out.print("Enter the product name: ");
        String productName = scanner.nextLine().trim();

        // See if the product already exists in the list
        if (productExistsInList(listUUID, productName)) {
            System.out.println("Product already exists in the shopping list.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        System.out.print("Enter the quantity: ");
        int quantity = Integer.parseInt(scanner.nextLine());

        // see if quantity is bigger than 0
        if (quantity <= 0) {
            System.out.println("Quantity must be bigger than 0.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        // Save the product to the list
        if (addProductToList(listUUID, productName, quantity)) {
            Utils.updateShoppingListInServer(user,listUUID);
            System.out.println("Product added to the shopping list.");
        } else {
            System.out.println("Failed to add product to the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(user,listUUID);
    }

    private boolean productExistsInList(String listUUID, String productName) {
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

    private boolean addProductToList(String listUUID, String productName, int quantity) {
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

}
