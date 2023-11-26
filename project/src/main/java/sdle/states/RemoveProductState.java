package sdle.states;

import sdle.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class RemoveProductState implements State {
    private final int listId;
    private final Scanner scanner = new Scanner(System.in);

    public RemoveProductState(int listId) {
        this.listId = listId;
    }

    @Override
    public State run() {
        // Get input for product details to be removed
        System.out.print("Enter the product name to remove: ");
        String productName = scanner.nextLine().trim();

        // See if the product exists in the list
        if (!productExistsInList(listId, productName)) {
            System.out.println("Product does not exist in the shopping list.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(listId);
        }

        // Remove the product from the list
        if (removeProductFromList(listId, productName)) {
            System.out.println("Product removed from the shopping list.");
        } else {
            System.out.println("Failed to remove product from the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(listId);
    }

    private boolean removeProductFromList(int listId, String productName) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "DELETE FROM list_products WHERE list_id = ? AND product_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, listId);
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

    private boolean productExistsInList(int listId, String productName) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT * FROM list_products WHERE list_id = ? AND product_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, listId);
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

