package sdle.states;

import sdle.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class UpdateProductState implements State {
    private final int listId;
    private final Scanner scanner = new Scanner(System.in);

    public UpdateProductState(int listId) {
        this.listId = listId;
    }

    @Override
    public State run() {

        // Get input for product details to be updated
        System.out.print("Enter the product name to update: ");
        String productName = scanner.nextLine().trim();

        // See if the product exists in the list
        if (!productExistsInList(listId, productName)) {
            System.out.println("Product does not exist in the shopping list.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(listId);
        }

        System.out.print("Enter the new quantity: ");
        int newQuantity = Integer.parseInt(scanner.nextLine());

        // see if quantity is bigger than 0
        if (newQuantity <= 0) {
            System.out.println("Quantity must be bigger than 0.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(listId);
        }

        // Update the product in the list
        if (updateProductInList(listId, productName, newQuantity)) {
            System.out.println("Product updated in the shopping list.");
        } else {
            System.out.println("Failed to update product in the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(listId);
    }

    private boolean updateProductInList(int listId, String productName, int newQuantity) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "UPDATE list_products SET quantity = ? WHERE list_id = ? AND product_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, newQuantity);
                    pstmt.setInt(2, listId);
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

