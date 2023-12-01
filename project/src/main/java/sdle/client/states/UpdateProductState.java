package sdle.client.states;

import sdle.client.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class UpdateProductState implements State {
    private final String listUUID;

    private final String user;

    private final Scanner scanner = new Scanner(System.in);

    public UpdateProductState(String user,String listUUID) {
        this.user = user;
        this.listUUID = listUUID;
    }

    @Override
    public State run() {

        // Get input for product details to be updated
        System.out.print("Enter the product name to update: ");
        String productName = scanner.nextLine().trim();

        // See if the product exists in the list
        if (!productExistsInList(listUUID, productName)) {
            System.out.println("Product does not exist in the shopping list.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        System.out.print("Enter the new quantity: ");
        int newQuantity = Integer.parseInt(scanner.nextLine());

        // see if quantity is bigger than 0
        if (newQuantity <= 0) {
            System.out.println("Quantity must be bigger than 0.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new ListProductsState(user,listUUID);
        }

        // Update the product in the list
        if (updateProductInList(listUUID, productName, newQuantity)) {
            Utils.updateShoppingListInServer(user,listUUID);
            System.out.println("Product updated in the shopping list.");
        } else {
            System.out.println("Failed to update product in the shopping list.");
        }

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(user,listUUID);
    }

    private boolean updateProductInList(String listUUID, String productName, int newQuantity) {
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

