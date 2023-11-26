package sdle.client.states;

import sdle.client.utils.Utils;

import java.sql.*;
import java.util.Scanner;
import java.util.UUID;

public class CreateListState implements State {
    private final Scanner scanner = new Scanner(System.in);

    private final String user;

    public CreateListState(String user) {
        this.user = user;
    }

    @Override
    public State run() {
        System.out.println("Creating a new shopping list...");

        // Get the name for the shopping list from the user
        System.out.print("Enter the name for the shopping list: ");
        String shoppingListName = scanner.nextLine().trim();


        // See if the shopping list already exists
        if (shoppingListExists(shoppingListName)) {
            System.out.println("Shopping List already exists.");
            System.out.println("Press enter to continue...");
            scanner.nextLine();
            Utils.clearConsole();
            return new MenuState(user);
        }

        // Perform actions for creating a shopping list here
        String shoppingListID = generateUniqueID();

        // Save the shopping list to the database
        if (saveShoppingListToDatabase(shoppingListID, shoppingListName)) {
            System.out.println("Shopping List created with ID: " + shoppingListID);
        } else {
            System.out.println("Failed to save Shopping List to the database.");
        }

        // Press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new MenuState(user);
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

    private String generateUniqueID() {
        return UUID.randomUUID().toString();
    }

    private boolean saveShoppingListToDatabase(String shoppingListID, String shoppingListName) {
        String url = "jdbc:sqlite:database/client/" + user + "_shopping.db";

        try (Connection connection = DriverManager.getConnection(url)) {
            if (connection != null) {
                String sql = "INSERT INTO shopping_lists (list_uuid, list_name) VALUES (?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, shoppingListID);
                    pstmt.setString(2, shoppingListName);
                    pstmt.executeUpdate();
                    return true;
                }
            }
        } catch (SQLException e) {
            System.out.println("Error saving Shopping List to the database: " + e.getMessage());
        }
        return false;
    }
}


