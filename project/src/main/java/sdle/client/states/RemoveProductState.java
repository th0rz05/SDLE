package sdle.client.states;

import sdle.client.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class RemoveProductState implements State {
    private final String listUUID;

    private final String user;
    private final Scanner scanner = new Scanner(System.in);

    public RemoveProductState(String user, String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {
        // Get input for product details to be removed
        System.out.print("Enter the product name to remove: ");
        String productName = scanner.nextLine().trim();

        // See if the product exists in the list
        if (!productExistsInList(listUUID, productName)) {
            System.out.println("Product does not exist in the shopping list.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        // Remove the product from the list
        if (removeProductFromList(listUUID, productName)) {
            Utils.updateShoppingListInServer(user,listUUID);
            System.out.println("Product removed from the shopping list.");
        } else {
            System.out.println("Failed to remove product from the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(user,listUUID);
    }

    private boolean removeProductFromList(String listUUID, String productName) {
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
}

