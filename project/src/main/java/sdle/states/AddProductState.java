package sdle.states;

import sdle.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

public class AddProductState implements State {
    private final int listId;
    private final Scanner scanner = new Scanner(System.in);

    public AddProductState(int listId) {
        this.listId = listId;
    }

    @Override
    public State run() {
        // Get input for product details
        System.out.print("Enter the product name: ");
        String productName = scanner.nextLine();

        System.out.print("Enter the quantity: ");
        int quantity = Integer.parseInt(scanner.nextLine());

        // Save the product to the list
        if (addProductToList(listId, productName, quantity)) {
            System.out.println("Product added to the shopping list.");
        } else {
            System.out.println("Failed to add product to the shopping list.");
        }

        // Press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new ListProductsState(listId);
    }

    private boolean addProductToList(int listId, String productName, int quantity) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "INSERT INTO list_products (list_id, product_name, quantity) VALUES (?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, listId);
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
