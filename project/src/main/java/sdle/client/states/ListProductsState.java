package sdle.client.states;

import sdle.client.utils.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class ListProductsState implements State {
    private final Scanner scanner = new Scanner(System.in);

    private final String user;
    private int listId;

    public ListProductsState(String user, int listId) {
        this.user = user;
        this.listId = listId;
    }

    @Override
    public State run() {

        if(listId == 0) {
            // Get input for the shopping list ID
            System.out.print("Enter the shopping list name: ");
            String listName = scanner.nextLine().trim();

            // See if the shopping list exists
            if (!shoppingListExists(listName)) {
                System.out.println("Shopping list does not exist.");
                System.out.println("Press enter to continue...");
                scanner.nextLine();
                Utils.clearConsole();
                return new MenuState(user);
            }

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
                    return new AddProductState(user,listId);
                }
                case "2" -> {
                    Utils.clearConsole();
                    return new RemoveProductState(user,listId);
                }
                case "3" -> {
                    Utils.clearConsole();
                    return new UpdateProductState(user,listId);
                }
                case "4" -> {
                    Utils.clearConsole();
                    return new DeleteListState(user,listId);
                }
                case "q" -> {
                    Utils.clearConsole();
                    // Transition back to the menu state
                    return new MenuState(user);
                }
                default -> {
                    Utils.clearConsole();
                    System.out.println("Invalid input. Please try again.");
                }
            }
        }
    }

    private int getListId(String listName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

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
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

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

    private boolean shoppingListExists(String shoppingListName) {
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
}
