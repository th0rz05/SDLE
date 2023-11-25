package sdle.states;

import sdle.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class ListProductsState implements State {
    private final Scanner scanner = new Scanner(System.in);
    private int listId;

    public ListProductsState(int listId) {
        this.listId = listId;
    }

    @Override
    public State run() {

        if(listId == 0) {
            // Get input for the shopping list ID
            System.out.print("Enter the shopping list name: ");
            String listName = scanner.nextLine().trim();

            listId = getListId(listName);
        }

        // Display products in the specified list
        displayListProducts(listId);

        while (true) {
            displayOptions();
            String input = scanner.nextLine().trim().toLowerCase();

            switch (input) {
                case "1" -> {
                    Utils.clearConsole();
                    return new AddProductState(listId);
                }
                case "2" -> {
                    Utils.clearConsole();
                    return new RemoveProductState(listId);
                }
                case "3" -> {
                    Utils.clearConsole();
                    return new UpdateProductState(listId);
                }
                case "4" -> {
                    Utils.clearConsole();
                    return new DeleteListState(listId);
                }
                case "q" -> {
                    Utils.clearConsole();
                    // Transition back to the menu state
                    return new MenuState();
                }
                default -> {
                    Utils.clearConsole();
                    System.out.println("Invalid input. Please try again.");
                }
            }
        }
    }

    private int getListId(String listName) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT id FROM shopping_lists WHERE list_name = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, listName);
                    ResultSet rs = pstmt.executeQuery();

                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching shopping list ID: " + e.getMessage());
        }

        return -1;
    }

    private void displayListProducts(int listId) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "SELECT product_name, quantity FROM list_products WHERE list_id = ?";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, listId);
                    ResultSet rs = pstmt.executeQuery();

                    System.out.println("------ Products in the Shopping List ------");
                    while (rs.next()) {
                        String productName = rs.getString("product_name");
                        int quantity = rs.getInt("quantity");
                        System.out.println("Product: " + productName + ", Quantity: " + quantity);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error fetching products in the shopping list: " + e.getMessage());
        }
    }

    private void displayOptions() {
        System.out.println("\n\n\n");
        System.out.println("------ Menu ------");
        System.out.println("1 - Add products");
        System.out.println("2 - Remove products");
        System.out.println("3 - Update products");
        System.out.println("4 - Delete shopping list");
        System.out.println("Enter 'Q' to return to the main menu");
        System.out.print("Your choice: ");
    }
}
