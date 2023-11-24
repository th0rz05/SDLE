package sdle.states;

import sdle.utils.Utils;

import java.util.Scanner;
import java.util.UUID;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class CreateListState implements State {
    private final Scanner scanner = new Scanner(System.in);

    @Override
    public State run() {
        System.out.println("Creating a new shopping list...");

        // Get the name for the shopping list from the user
        System.out.print("Enter the name for the shopping list: ");
        String shoppingListName = scanner.nextLine();

        // Perform actions for creating a shopping list here
        String shoppingListID = generateUniqueID();
        System.out.println("Shopping List created with ID: " + shoppingListID);

        // Save the shopping list to the database
        if (saveShoppingListToDatabase(shoppingListID, shoppingListName)) {
            System.out.println("Shopping List saved to the database.");
        } else {
            System.out.println("Failed to save Shopping List to the database.");
        }

        // Press enter to continue
        System.out.println("Press enter to continue...");
        scanner.nextLine();

        Utils.clearConsole();

        // Transition back to the menu state
        return new MenuState();
    }

    private String generateUniqueID() {
        return UUID.randomUUID().toString();
    }

    private boolean saveShoppingListToDatabase(String shoppingListID, String shoppingListName) {
        String url = "jdbc:sqlite:database/shopping.db"; // Replace with your database URL

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


